package dev.captience.realisticdamage.systems;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
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

	private static final Set<Mob> injured = Collections.newSetFromMap(new IdentityHashMap<>());
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
		return entity instanceof Mob && !(entity instanceof Player)
				&& !entity.level().isClientSide()
				&& ServerConfig.get().enableMobAnatomy
				&& entity.is(HAS_ANATOMY);
	}

	public static boolean throwsLimbs(LivingEntity entity) {
		return entity.is(THROWS_LIMBS);
	}

	/** Begins ticking this body; a no-op for players and untagged mobs. */
	public static void track(LivingEntity entity) {
		if (entity instanceof Mob mob && isMobWithBody(mob)) {
			injured.add(mob);
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
		Iterator<Mob> it = injured.iterator();
		while (it.hasNext()) {
			Mob mob = it.next();
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
	public static void tickBody(Mob mob, RealisticDamageData data) {
		for (BodyPartData part : data.allParts()) {
			part.tickStrain();
		}
		data.tickUnconscious();
		BleedingSystem.tick(mob, data);
		ConsciousnessSystem.tick(mob, data);
		HealingSystem.tick(mob, data);
		ImpairmentSystem.tick(mob, data);
		daze(mob, data);
		RDAttachments.pushSnapshot(mob);
	}

	/**
	 * Nothing left to tick: every region whole or at least intact, no wound,
	 * blood full, consciousness at its cap and the mob awake. A mob with a
	 * broken or missing limb never settles, because the attribute penalty for it
	 * has to keep being asserted.
	 */
	public static boolean settled(RealisticDamageData data) {
		if (data.bleeding() || data.unconscious() || data.concussion()
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

	/** Consciousness gone: the mob stops thinking until it comes round. */
	public static void collapse(LivingEntity entity) {
		if (entity instanceof Mob mob && !(entity instanceof Player) && !mob.isNoAi()) {
			mob.setNoAi(true);
			mob.setTarget(null);
			putOut.add(mob);
		}
	}

	/** Awake again: AI back, but only if this class took it away. */
	public static void wake(LivingEntity entity) {
		if (entity instanceof Mob mob && putOut.remove(mob)) {
			mob.setNoAi(false);
		}
	}

	/** Can this mob still swing at something? Not with both arms gone. */
	public static boolean canStrike(Mob mob) {
		if (!isMobWithBody(mob) || !ServerConfig.get().enableLimbDestruction) {
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
		RealisticDamageData data = RDAttachments.get(mob);
		return !data.part(ImpairmentSystem.armFor(mob, true)).destroyed();
	}

	/** Server stop or world unload: forget everything. */
	public static void reset() {
		injured.clear();
		putOut.clear();
	}
}
