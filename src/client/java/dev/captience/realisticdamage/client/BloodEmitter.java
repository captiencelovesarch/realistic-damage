package dev.captience.realisticdamage.client;

import java.util.HashMap;
import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.BloodFlow;
import dev.captience.realisticdamage.systems.Cardio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where the blood actually comes from (1.5).
 *
 * <p>Runs entirely on the client, for every player it can see, driven by the
 * body snapshot that was already being synced. That is a straight improvement
 * on the old arrangement in three ways: it costs nothing on the wire where the
 * server used to send particle packets, the rate can follow the wound's real
 * Blood/sec instead of a fixed 25-tick timer, and — because the client owns the
 * cardiac clock — an arterial wound can throw its blood on the same beat the
 * player is hearing and feeling in the vignette.
 *
 * <p>The only blood the server still sends is the one-shot spray when a blow
 * opens a wound, because that is an event with a direction and the snapshot
 * carries neither.
 */
public final class BloodEmitter {
	private BloodEmitter() {}

	/**
	 * Sprites 0-3 of {@code blood.json} are drops, 4-7 the splats a stain is born
	 * with, 8-11 the round pool shapes a stain swaps to once it has been fed enough
	 * to stop being a splat (see {@link BloodStain#grow}).
	 *
	 * <p>Every call site passes {@code SPRITE_COUNT - 1} as the {@code max} argument
	 * of {@link net.minecraft.client.particle.SpriteSet#get}, whose implementation
	 * is {@code sprites.get(index * (size - 1) / max)} — so with {@code max} one
	 * short of the number of sprites the expression collapses to {@code index} and
	 * the atlas can be addressed directly. Adding sprites without moving this number
	 * skews every index silently, which is why {@code BloodStains} no longer keeps a
	 * copy of its own.
	 */
	public static final int DROP_SPRITE_COUNT = 4;
	/** First splat sprite, and first pool sprite. Public since the water cloud borrows the pool shape. */
	public static final int SPLAT_SPRITE_OFFSET = 4;
	public static final int POOL_SPRITE_OFFSET = 8;
	public static final int SPRITE_COUNT = 12;
	/** Beyond this, someone else's bleeding is not worth the particles. */
	private static final double EMIT_RANGE = 28.0;

	private static @Nullable SpriteSet sprites;
	private static final Map<Integer, Bleeder> bleeders = new HashMap<>();
	/** Fractional-drop carry per severed limb on the floor (1.6.2), by entity id. */
	private static final Map<Integer, Double> limbCarry = new HashMap<>();
	private static long limbEmitted;
	private static long limbDrops;

	/** Drops that have left a severed limb this session; read by the client gametest. */
	public static long limbDrops() {
		return limbDrops;
	}

	/** Per-player emission state: a fractional-drop carry per region, and a heart. */
	private static final class Bleeder {
		private final Cardio heart = new Cardio();
		private final double[] carry = new double[BodyPart.values().length];
		private final double[] contactCarry = new double[BodyPart.values().length];
		private long emitted;
		private long transferred;
	}

	static void bindSprites(SpriteSet spriteSet) {
		sprites = spriteSet;
	}

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || sprites == null) {
			bleeders.clear();
			return;
		}
		if (ClientEffectsConfig.get().bloodIntensity() <= 0.0) {
			bleeders.clear();
			return;
		}
		Vec3 here = client.player == null ? Vec3.ZERO : client.player.position();
		// Every living thing with a body (1.7): players, and the mobs the server
		// gave snapshots to. The range box does the culling; the null read does
		// the rest.
		for (LivingEntity player : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(here, here).inflate(EMIT_RANGE))) {
			BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
			if (snapshot == null || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			boolean local = player == client.player;
			emitFor(player, snapshot, local);
		}
		bleeders.keySet().removeIf(id -> level.getEntity(id) == null);

		// Limbs on the floor (1.6.2) bleed too: a steady trickle from the cut end
		// that runs dry over the limb's life. No heart in a limb, so no spurts.
		AABB reach = new AABB(here, here).inflate(EMIT_RANGE);
		for (SeveredLimb limb : level.getEntitiesOfClass(SeveredLimb.class, reach)) {
			double rate = limb.bleedRate();
			if (rate <= 0.0) {
				limbCarry.remove(limb.getId());
				continue;
			}
			double perSecond = BloodFlow.dropsPerSecond(rate);
			double carry = limbCarry.getOrDefault(limb.getId(), 0.0);
			int drops = BloodFlow.dropsThisTick(carry, perSecond);
			limbCarry.put(limb.getId(), BloodFlow.carryAfter(carry, perSecond));
			for (int i = 0; i < drops; i++) {
				spawnFromLimb(limb, rate, limbEmitted++);
			}
		}
		limbCarry.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	private static void spawnFromLimb(SeveredLimb limb, double bloodPerSecond, long index) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || sprites == null) {
			return;
		}
		// Out of the middle of the thing, scattered across it: the cut end moves
		// with the tumble and a drop that misses the limb by a centimetre is not
		// worth resolving it for.
		Vec3 at = limb.position().add(
				BloodFlow.jitterSigned(index, 11) * 0.12,
				limb.getBbHeight() * 0.5 + BloodFlow.jitterSigned(index, 12) * 0.08,
				BloodFlow.jitterSigned(index, 13) * 0.12);
		double speed = BloodFlow.dropSpeed(bloodPerSecond, false) / 20.0;
		Vec3 direction = new Vec3(BloodFlow.jitterSigned(index, 14) * 0.3, -1.0,
				BloodFlow.jitterSigned(index, 15) * 0.3).normalize();
		BloodDrop drop = new BloodDrop(level, at.x, at.y, at.z,
				direction.x * speed, direction.y * speed, direction.z * speed,
				limb.getId(),
				sprites.get((int) (BloodFlow.jitter(index, 16) * DROP_SPRITE_COUNT), SPRITE_COUNT - 1),
				BloodTint.ofType(limb.ownerType()));
		client.particleEngine.add(drop);
		limbDrops++;
	}

	private static void emitFor(LivingEntity player, BodySnapshot snapshot, boolean local) {
		Bleeder bleeder = bleeders.computeIfAbsent(player.getId(), id -> new Bleeder());

		// The local player's spurts have to land on the heart the player is
		// actually hearing, so they borrow ClientState's clock rather than
		// running a second one that would drift out of step with the sound.
		Cardio heart = local ? ClientState.heart() : advanceRemoteHeart(player, snapshot, bleeder);
		boolean beat = heart.beatJustFired();

		double lensWeighted = 0.0;
		for (BodyPart part : BodyPart.values()) {
			WoundSeverity wound = snapshot.wound(part);
			if (wound == null) {
				bleeder.carry[part.ordinal()] = 0.0;
				bleeder.contactCarry[part.ordinal()] = 0.0;
				continue;
			}
			double rate = wound.bloodPerSecond();
			lensWeighted += rate * BloodFlow.lensWeight(part);
			if (snapshot.underPressure()) {
				// A hand over the wound slows what is coming out; it does not close
				// it. Choking the visible flow to nothing would overstate the one
				// piece of first aid in the mod, so a seep still gets through.
				rate *= 0.15;
			}

			double contactRate = BloodFlow.contactTransfersPerSecond(rate);
			int transfers = BloodFlow.dropsThisTick(bleeder.contactCarry[part.ordinal()], contactRate);
			bleeder.contactCarry[part.ordinal()] = BloodFlow.carryAfter(
					bleeder.contactCarry[part.ordinal()], contactRate);
			double contactScale = BloodFlow.dropScale(Balance.DROP_SEEP_SPEED / 20.0);
			for (int i = 0; i < transfers; i++) {
				BloodStains.transfer(player, part, contactScale, bleeder.transferred++);
			}

			boolean arterial = BloodFlow.arterial(wound.bloodPerSecond()) && !snapshot.underPressure();
			double share = arterial ? BloodFlow.spurtShare(wound.bloodPerSecond()) : 0.0;
			double trickle = BloodFlow.dropsPerSecond(rate * (1.0 - share));

			int drops = BloodFlow.dropsThisTick(bleeder.carry[part.ordinal()], trickle);
			bleeder.carry[part.ordinal()] = BloodFlow.carryAfter(bleeder.carry[part.ordinal()], trickle);
			for (int i = 0; i < drops; i++) {
				spawn(player, part, wound.bloodPerSecond(), false, bleeder.emitted++);
			}

			if (arterial && beat) {
				double perBeat = BloodFlow.dropsPerSecond(wound.bloodPerSecond()) * share
						* 60.0 / heart.bpm();
				// The cap moved to Balance: it is the exact bound
				// STAIN_POOL_SPRITE_FEEDS has to beat, and a pure test cannot
				// check that against a private in a client class.
				int burst = (int) Math.clamp(Math.round(perBeat), 1L, Balance.ARTERIAL_SPURT_DROPS_MAX);
				for (int i = 0; i < burst; i++) {
					spawn(player, part, wound.bloodPerSecond(), true, bleeder.emitted++);
				}
			}
		}

		if (local && lensWeighted > 0.0 && !snapshot.underPressure()) {
			BloodLens.feed(lensWeighted);
		}
	}

	/**
	 * Someone else's heart, so their arterial wounds spurt in their own rhythm
	 * rather than in yours. Computed from their synced vitals, which every client
	 * has, so two people watching the same casualty see the same thing.
	 */
	private static Cardio advanceRemoteHeart(LivingEntity player, BodySnapshot snapshot, Bleeder bleeder) {
		double effective = Vitals.effectiveConsciousness(snapshot.consciousness(), snapshot.concussionGrade(),
				snapshot.concussionFlare());
		double maxHealth = player.getMaxHealth();
		double healthFraction = maxHealth <= 0.0 ? 1.0 : player.getHealth() / maxHealth;
		bleeder.heart.tick(
				Vitals.distressFromHealth(healthFraction),
				Vitals.distressFromBlood(snapshot.blood()),
				Vitals.distressFromMind(effective),
				ClientState.breath());
		return bleeder.heart;
	}

	private static void spawn(LivingEntity player, BodyPart part, double bloodPerSecond, boolean onBeat, long index) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || sprites == null) {
			return;
		}
		Vec3 at = BodyHitResolver.regionCenter(player, part);
		RemoteCorpses.View corpse = RemoteCorpses.of(player);
		if (corpse != null) {
			at = corpse.place(player, at);
		}
		// Scatter the origin across the region rather than emitting from a single
		// point: a wound is somewhere on a limb, not at its exact centre.
		double spreadX = BloodFlow.jitterSigned(index, 1) * 0.11;
		double spreadY = BloodFlow.jitterSigned(index, 2) * 0.11;
		double spreadZ = BloodFlow.jitterSigned(index, 3) * 0.11;

		double speed = BloodFlow.dropSpeed(bloodPerSecond, onBeat) / 20.0;
		Vec3 direction = onBeat
				? spurtDirection(player, index)
				: new Vec3(BloodFlow.jitterSigned(index, 4) * 0.35, -1.0,
						BloodFlow.jitterSigned(index, 5) * 0.35).normalize();

		BloodDrop drop = new BloodDrop(level,
				at.x + spreadX, at.y + spreadY, at.z + spreadZ,
				direction.x * speed, direction.y * speed, direction.z * speed,
				player.getId(),
				sprites.get((int) (BloodFlow.jitter(index, 6) * DROP_SPRITE_COUNT), SPRITE_COUNT - 1),
				BloodTint.of(player));
		client.particleEngine.add(drop);
	}

	/**
	 * A mouthful of vomit (1.7 concussion): the drop physics, the landing and
	 * the stain are blood's, only the colour and where it comes from differ.
	 * Out of the mouth, forward and down the way the head is bowed.
	 */
	public static void spawnBile(LivingEntity body, long index) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || sprites == null) {
			return;
		}
		Vec3 look = body.getViewVector(1.0F);
		Vec3 forward = new Vec3(look.x, 0.0, look.z);
		forward = forward.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
		// Out past the chin rather than at the lips: spawned at the mouth, the
		// first frames of every drop sat inside the first-person camera's near
		// field and filled the screen with brown blocks.
		Vec3 mouth = body.getEyePosition().add(forward.scale(0.55)).add(0.0, -0.5, 0.0);
		double speed = 0.08 + BloodFlow.jitter(index, 1) * 0.06;
		Vec3 direction = new Vec3(
				forward.x + BloodFlow.jitterSigned(index, 2) * 0.25,
				-0.55 + BloodFlow.jitterSigned(index, 3) * 0.2,
				forward.z + BloodFlow.jitterSigned(index, 4) * 0.25).normalize();
		BloodDrop drop = new BloodDrop(level,
				mouth.x, mouth.y, mouth.z,
				direction.x * speed, direction.y * speed, direction.z * speed,
				body.getId(),
				sprites.get((int) (BloodFlow.jitter(index, 6) * DROP_SPRITE_COUNT), SPRITE_COUNT - 1),
				BloodTint.BILE);
		client.particleEngine.add(drop);
	}

	/**
	 * Where a spurt goes: broadly the way the body is facing, in a narrow cone
	 * tilted slightly up so it arcs. Narrow because this is pressure escaping a
	 * hole, not an aerosol — a wide cone reads as a spray can.
	 */
	private static Vec3 spurtDirection(LivingEntity player, long index) {
		double yaw = Math.toRadians(player.yBodyRot)
				+ Math.toRadians(BloodFlow.jitterSigned(index, 8) * Balance.ARTERIAL_SPURT_SPREAD);
		double pitch = Math.toRadians(-18.0 + BloodFlow.jitterSigned(index, 9) * Balance.ARTERIAL_SPURT_SPREAD);
		double horizontal = Math.cos(pitch);
		return new Vec3(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal);
	}

	public static void reset() {
		bleeders.clear();
		limbCarry.clear();
	}
}
