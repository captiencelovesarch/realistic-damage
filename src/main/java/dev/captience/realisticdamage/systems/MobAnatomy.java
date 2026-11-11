package dev.captience.realisticdamage.systems;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Anatomy for everyone (1.7): the bookkeeping that lets a mob run through the
 * same body pipeline a player does.
 *
 * <p>Which mobs is data: the {@link #HAS_ANATOMY} entity type tag, shipped with
 * the humanoid families and open to any datapack. {@link #participates} is the
 * one question every hook asks — a non-creative player, or a tagged mob while
 * the rule is on — so there is exactly one place the answer can be wrong.
 *
 * <h2>Performance</h2>
 *
 * <p>A body attachment is created on first damage and never before, and the
 * only mobs that tick are the ones in {@link #injured}: added when hurt (or
 * loaded with a body), dropped when dead, unloaded, or settled — no wounds,
 * blood full, consciousness at its cap, nothing broken or missing. A server of
 * healthy zombies iterates nothing.
 *
 * <h2>What a mob does differently</h2>
 *
 * <p>It is silent (no action bar), it has no food, sleep, rescue, knockdown,
 * microsleeps or ceilings, and when it collapses it stops thinking:
 * {@link #collapse} sets NoAI for the unconscious window and {@link #wake}
 * gives it back, leaving alone any mob that was already NoAI for its own
 * reasons. A concussed mob forgets its target every couple of seconds and
 * finds it again — dazed, not blind.
 */
public final class MobAnatomy {
	private MobAnatomy() {}

	public static final TagKey<EntityType<?>> HAS_ANATOMY =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("has_anatomy"));
	/** Types whose severed limb the client can draw; the rest keep the stump only. */
	public static final TagKey<EntityType<?>> THROWS_LIMBS =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("throws_limbs"));
	/**
	 * Bodies with nothing in them to spill (1.7): the skeleton family. They
	 * break and lose limbs like anyone, but no wound is ever opened on them and
	 * nothing they lose bleeds — a skeleton that bled was the first playtest
	 * report of the mob work.
	 */
	public static final TagKey<EntityType<?>> BLOODLESS =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("bloodless"));
	/** What colour a body bleeds; the client reads these. Red is the default. */
	public static final TagKey<EntityType<?>> BLOOD_DARK =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_dark"));
	public static final TagKey<EntityType<?>> BLOOD_PURPLE =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_purple"));
	/** Slice three's colours (1.7): slime, magma, hemocyanin, glow, insect, sculk, sulfur. */
	public static final TagKey<EntityType<?>> BLOOD_GREEN =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_green"));
	public static final TagKey<EntityType<?>> BLOOD_MAGMA =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_magma"));
	public static final TagKey<EntityType<?>> BLOOD_BLUE =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_blue"));
	public static final TagKey<EntityType<?>> BLOOD_GLOW =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_glow"));
	public static final TagKey<EntityType<?>> BLOOD_INSECT =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_insect"));
	public static final TagKey<EntityType<?>> BLOOD_SCULK =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_sculk"));
	public static final TagKey<EntityType<?>> BLOOD_SULFUR =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("blood_sulfur"));
	/**
	 * No mind to lose (1.7 slice 3): constructs, cubes and elementals. No
	 * concussion, no knockout, no hypoxia — there is nothing in an iron golem
	 * for a blow to the head to switch off.
	 */
	public static final TagKey<EntityType<?>> MINDLESS =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("mindless"));
	/**
	 * Bosses (1.7 slice 3): they bleed, break and stain like anything else, but
	 * cannot be knocked out, dazed, knocked down, or lose a limb or a head. A
	 * boss fight stays a fight rather than one lucky headshot.
	 */
	public static final TagKey<EntityType<?>> BOSS =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("boss"));
	/** A shell over the torso (1.7 slice 3): it takes {@link Balance#SHELL_TORSO_REDUCTION} of torso trauma. */
	public static final TagKey<EntityType<?>> SHELLED =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("shelled"));

	/** One tag per non-humanoid plan, {@code realisticdamage:plan/<id>}; untagged bodies are humanoid. */
	private static final java.util.Map<BodyPlan, TagKey<EntityType<?>>> PLAN_TAGS = planTags();
	/** Plan by type, filled on first ask; tags can change on /reload, so it is cleared then. */
	private static final java.util.Map<EntityType<?>, BodyPlan> planCache =
			java.util.Collections.synchronizedMap(new IdentityHashMap<>());

	private static java.util.Map<BodyPlan, TagKey<EntityType<?>>> planTags() {
		java.util.Map<BodyPlan, TagKey<EntityType<?>>> tags = new java.util.EnumMap<>(BodyPlan.class);
		for (BodyPlan plan : BodyPlan.values()) {
			if (plan != BodyPlan.HUMANOID) {
				tags.put(plan, TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("plan/" + plan.id())));
			}
		}
		return tags;
	}

	/** The plan tag for one plan, for the gametests; null for humanoid, which is the default. */
	public static TagKey<EntityType<?>> planTag(BodyPlan plan) {
		return PLAN_TAGS.get(plan);
	}

	/** Forgets cached plans; tags were (re)loaded. */
	public static void tagsReloaded() {
		planCache.clear();
	}

	/** How this body is laid out. Players are humanoid; mobs by tag, humanoid when untagged. */
	public static BodyPlan plan(LivingEntity entity) {
		if (entity instanceof Player) {
			return BodyPlan.HUMANOID;
		}
		return plan(entity.getType());
	}

	public static BodyPlan plan(EntityType<?> type) {
		BodyPlan cached = planCache.get(type);
		if (cached != null) {
			return cached;
		}
		BodyPlan found = BodyPlan.HUMANOID;
		var holder = type.builtInRegistryHolder();
		for (var entry : PLAN_TAGS.entrySet()) {
			if (holder.is(entry.getValue())) {
				found = entry.getKey();
				break;
			}
		}
		planCache.put(type, found);
		return found;
	}

	/**
	 * How much bigger than a person this body is (1.7 slice 3): its max health
	 * over twenty, never below one. Trauma and blood loss are divided by it and
	 * the final bleed-out multiplied, so a hundred-HP golem is not dismembered by
	 * the stroke that takes a zombie's arm, and a dragon still bleeds out on a
	 * clock that ends.
	 */
	public static double vitalityScale(LivingEntity entity) {
		if (entity instanceof Player) {
			return 1.0;
		}
		return Math.max(1.0, entity.getMaxHealth() / Balance.VITALITY_BASELINE_HP);
	}

	public static boolean boss(LivingEntity entity) {
		return !(entity instanceof Player) && entity.is(BOSS);
	}

	public static boolean shelled(LivingEntity entity) {
		return !(entity instanceof Player) && entity.is(SHELLED);
	}

	/**
	 * Whether consciousness applies to this body at all: everyone but the
	 * mindless and the bosses. A boss can be hurt in every other way.
	 */
	public static boolean feelsConsciousness(LivingEntity entity) {
		return entity instanceof Player || !(entity.is(MINDLESS) || entity.is(BOSS));
	}

	/** Whether this body can lose a part at all; bosses cannot. */
	public static boolean canLoseParts(LivingEntity entity) {
		return !boss(entity);
	}

	private static final Set<LivingEntity> injured = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Mobs whose AI this class switched off, so waking switches on only those. */
	private static final Set<Mob> putOut = Collections.newSetFromMap(new IdentityHashMap<>());

	/** Whether this living thing has a body the mod maintains. */
	public static boolean participates(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator();
		}
		return isMobWithBody(entity);
	}

	/** A tagged, living, server-side mob while the rule is on. */
	public static boolean isMobWithBody(LivingEntity entity) {
		// The vanilla mannequin counts (1.7): it replaced the mod's own practice
		// dummy as the thing you hit to see what a hit does.
		return (entity instanceof Mob || entity instanceof net.minecraft.world.entity.decoration.Mannequin)
				&& !(entity instanceof Player)
				&& !entity.level().isClientSide()
				&& ServerConfig.get().enableMobAnatomy
				&& entity.is(HAS_ANATOMY);
	}

	public static boolean throwsLimbs(LivingEntity entity) {
		return entity.is(THROWS_LIMBS);
	}

	/** Whether this body has blood to lose: every player, and every mob not tagged bloodless. */
	public static boolean bleeds(LivingEntity entity) {
		return entity instanceof Player || !entity.is(BLOODLESS);
	}

	/** The same question about a type alone, for a limb whose owner is gone. */
	public static boolean bleeds(EntityType<?> type) {
		return !type.builtInRegistryHolder().is(BLOODLESS);
	}

	/** Begins ticking this body; a no-op for players and untagged mobs. */
	public static void track(LivingEntity entity) {
		if (isMobWithBody(entity)) {
			injured.add(entity);
		}
	}

	/** How many mob bodies are being ticked right now; read by the gametests. */
	public static int tracked() {
		return injured.size();
	}

	public static boolean isTracked(Mob mob) {
		return injured.contains(mob);
	}

	/** One server tick over the injured set. */
	public static void tick() {
		if (injured.isEmpty()) {
			return;
		}
		Iterator<LivingEntity> it = injured.iterator();
		while (it.hasNext()) {
			LivingEntity mob = it.next();
			if (mob.isRemoved() || !mob.isAlive() || !isMobWithBody(mob)) {
				if (!mob.isRemoved() && mob.isAlive()) {
					ImpairmentSystem.clear(mob);
					wake(mob);
				}
				it.remove();
				continue;
			}
			RealisticDamageData data = RDAttachments.get(mob);
			tickBody(mob, data);
			if (settled(data)) {
				ImpairmentSystem.clear(mob);
				it.remove();
			}
		}
	}

	/** The per-tick systems a mob body runs, in the same order a player's does. */
	public static void tickBody(LivingEntity mob, RealisticDamageData data) {
		if (data.part(BodyPart.HEAD).destroyed()) {
			// No living body keeps a lost head (2.0): a headless zombie walks its
			// few seconds out and drops; anything else (one saved mid-stride, whose
			// walk was never saved) dies now.
			if (data.headlessTicks() > 0 && mob instanceof Mob walker) {
				walkHeadless(walker, data);
			} else {
				net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) mob.level();
				mob.hurtServer(level, data.headSevered()
						? dev.captience.realisticdamage.damage.RDDamageTypes.decapitated(level, null)
						: dev.captience.realisticdamage.damage.RDDamageTypes.coreDestroyed(level, BodyPart.HEAD),
						Float.MAX_VALUE);
				return;
			}
		}
		for (BodyPartData part : data.allParts()) {
			part.tickStrain();
		}
		data.tickUnconscious();
		data.tickKnockdown(Balance.KNOCKDOWN_RECOVERY_TICKS);
		data.tickWhiplash();
		BleedingSystem.tick(mob, data);
		// The mindless and the bosses have no consciousness to run (1.7 slice 3).
		if (feelsConsciousness(mob)) {
			ConsciousnessSystem.tick(mob, data);
			ConcussionSystem.tick(mob, data);
		}
		HealingSystem.tick(mob, data);
		ImpairmentSystem.tick(mob, data);
		if (mob instanceof Mob thinking) {
			daze(thinking, data);
			ground(thinking, data);
		}
		RDAttachments.pushSnapshot(mob);
	}

	/**
	 * Blind, and still going (2.0): no target, no path, arms out, lurching on
	 * along a heading that weaves from side to side. {@link #down} keeps its
	 * own AI from moving or striking; the push here is the only thing that does.
	 */
	private static void walkHeadless(Mob mob, RealisticDamageData data) {
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.setAggressive(true);
		float yaw = mob.getYRot() + (float) (Math.sin((mob.tickCount + mob.getId()) * 0.35) * Balance.HEADLESS_WANDER);
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		if (mob.onGround() || mob.isInWater()) {
			net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, yaw)
					.scale(Balance.HEADLESS_WALK_SPEED);
			net.minecraft.world.phys.Vec3 v = mob.getDeltaMovement();
			mob.setDeltaMovement(ahead.x, v.y, ahead.z);
		}
		data.setHeadlessTicks(data.headlessTicks() - 1);
	}

	/** Whether this mob is walking on without its head; for the gametests. */
	public static boolean headless(LivingEntity mob) {
		RealisticDamageData data = mob.getAttached(RDAttachments.DATA);
		return data != null && data.headlessTicks() > 0;
	}

	/**
	 * A flyer that has lost a wing comes down (1.7 slice 3). Gravity back on and
	 * a floor under how fast it may rise, every tick, after its own AI has had
	 * its say — so a bee or parrot that keeps trying to climb only sinks slower.
	 * Bats and phantoms fly by AI alone and have no flight speed to take away, so
	 * they stop thinking for good and drop: a bat with one wing is on the floor.
	 */
	private static void ground(Mob mob, RealisticDamageData data) {
		if (!ServerConfig.get().enableLimbDestruction || !ImpairmentSystem.wingsGone(plan(mob), data)) {
			return;
		}
		if (mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED) == null
				&& !mob.isNoAi()) {
			mob.setNoAi(true);
			mob.setTarget(null);
		}
		mob.setNoGravity(false);
		if (!mob.onGround() && !mob.isInWater()) {
			var motion = mob.getDeltaMovement();
			if (motion.y > -Balance.GROUNDED_FALL_SPEED) {
				mob.setDeltaMovement(motion.x * 0.8, -Balance.GROUNDED_FALL_SPEED, motion.z * 0.8);
				mob.hurtMarked = true;
			}
		}
	}

	/** Whether a destroyed wing has this body on the ground; for the gametests. */
	public static boolean grounded(Mob mob) {
		return isMobWithBody(mob) && ImpairmentSystem.wingsGone(plan(mob), RDAttachments.get(mob));
	}

	/**
	 * Nothing left to tick: every region whole or at least intact, no wound,
	 * blood full, consciousness at its cap and the mob awake. A mob with a
	 * broken or missing limb never settles, because the attribute penalty for it
	 * has to keep being asserted.
	 */
	public static boolean settled(RealisticDamageData data) {
		if (data.bleeding() || data.unconscious() || data.concussion() || data.knockedDown() || data.whiplashed()
				|| data.blood() < Balance.BLOOD_MAX - Balance.CONDITION_EPSILON
				|| data.consciousness() < data.consciousnessCap() - Balance.CONDITION_EPSILON) {
			return false;
		}
		for (BodyPart part : BodyPart.values()) {
			BodyPartData region = data.part(part);
			if (region.broken() || region.destroyed() || region.missing() > Balance.CONDITION_EPSILON) {
				return false;
			}
		}
		return true;
	}

	/** A concussed mob loses the thread: it forgets its target every so often and has to find it again. */
	private static void daze(Mob mob, RealisticDamageData data) {
		if (data.concussion() && mob.getTarget() != null && mob.tickCount % Balance.MOB_DAZE_FORGET_TICKS == 0) {
			mob.setTarget(null);
		}
	}

	/**
	 * Consciousness gone: the mob stops thinking until it comes round ({@link
	 * #down}), and forgets what it was after.
	 */
	public static void collapse(LivingEntity entity) {
		if (entity instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
	}

	/**
	 * Awake again: its AI comes back by itself ({@link #down}). A mob put out
	 * by a version that switched its AI off instead gets it back here.
	 */
	public static void wake(LivingEntity entity) {
		if (entity instanceof Mob mob && putOut.remove(mob)) {
			mob.setNoAi(false);
		}
	}

	/**
	 * Out cold or knocked flat: a mob that is down does nothing of its own (no
	 * goals, no brain, no strikes, no shots, no fuse), though it still falls,
	 * slides and is thrown. Switching its AI off would stop those too: a mob
	 * with no AI does not move at all. Server side; players never.
	 */
	public static boolean down(LivingEntity entity) {
		if (!(entity instanceof Mob) || entity.level().isClientSide()) {
			return false;
		}
		RealisticDamageData data = entity.getAttached(RDAttachments.DATA);
		return data != null && (data.unconscious() || data.knockedDown() || data.headlessTicks() > 0);
	}

	/** Can this mob still swing at something? Not with both arms gone. */
	public static boolean canStrike(Mob mob) {
		if (!isMobWithBody(mob) || !ServerConfig.get().enableLimbDestruction) {
			return true;
		}
		// A body without hands strikes with its head — a wolf with a broken
		// foreleg still bites — so only hands can be lost to this rule.
		if (!plan(mob).hasHands()) {
			return true;
		}
		RealisticDamageData data = RDAttachments.get(mob);
		return !(data.part(BodyPart.LEFT_ARM).destroyed() && data.part(BodyPart.RIGHT_ARM).destroyed());
	}

	/** Can this mob still draw a bow or aim a crossbow? Not without its main arm. */
	public static boolean canShoot(Mob mob) {
		if (!isMobWithBody(mob) || !ServerConfig.get().enableLimbDestruction) {
			return true;
		}
		if (!plan(mob).hasHands()) {
			return true;
		}
		RealisticDamageData data = RDAttachments.get(mob);
		return !data.part(ImpairmentSystem.armFor(mob, true)).destroyed();
	}

	/**
	 * How much wider a ranged mob's shots spread (1.7): a concussed head cannot
	 * aim, and a broken drawing arm cannot hold the draw steady. Multiplies the
	 * inaccuracy vanilla already applies, so difficulty still counts.
	 */
	public static double aimSpread(Mob mob) {
		if (!isMobWithBody(mob)) {
			return 1.0;
		}
		RealisticDamageData data = RDAttachments.get(mob);
		double spread = 1.0;
		if (data.concussion()) {
			spread *= Balance.MOB_DAZE_AIM_SPREAD;
		}
		if (plan(mob).hasHands() && data.part(ImpairmentSystem.armFor(mob, true)).broken()) {
			spread *= Balance.MOB_BROKEN_ARM_AIM_SPREAD;
		}
		// A stiff neck cannot follow the target (2.0).
		spread *= NeckMath.mobAimSpread(data.whiplash());
		return spread;
	}

	/**
	 * What a mob's head-turn speed is multiplied by (2.0): a whiplashed neck
	 * turns slowly. 1 for anything without a body or a sound neck.
	 */
	public static double turnFactor(Mob mob) {
		if (mob.level().isClientSide()) {
			return 1.0;
		}
		RealisticDamageData data = mob.getAttached(RDAttachments.DATA);
		return data == null || !data.whiplashed() ? 1.0 : NeckMath.mobTurnFactor(data.whiplash());
	}

	/** Server stop or world unload: forget everything. */
	public static void reset() {
		injured.clear();
		putOut.clear();
	}
}
