package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import dev.captience.realisticdamage.systems.LimpMath;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * How a death becomes a falling body: the one launch every copy of a ragdoll
 * starts from (2.0).
 *
 * <p>Until 2.0 this lived on the client, because only clients ran ragdolls.
 * Now the server runs one too, for a body that died falling or thrown, to see
 * what it hits ({@code damage/CorpseImpacts}); a limb can only come off for
 * everyone if the server is the one that decides, and it can only decide what
 * it has followed. So the launch is here, in one place, and the client's
 * ragdoll and the server's are started by the same code from the same packet.
 * They still differ in what only a client has (the model's exact shape, the
 * pose the body was drawn in), which is why the server's copy is trusted for
 * when and how hard a body lands and not for the look of it.
 *
 * <p>No RNG anywhere, like the rest of the ragdoll.
 */
public final class CorpseLaunch {
	private CorpseLaunch() {}

	/** Seconds per tick. */
	public static final double DT = 0.05;

	/** {@code BodyPart} ordinal to ragdoll part: the two enums list the sides in opposite orders. */
	public static final int[] FROM_BODY_PART = {
			HumanoidRagdoll.HEAD, HumanoidRagdoll.TORSO,
			HumanoidRagdoll.LEFT_ARM, HumanoidRagdoll.RIGHT_ARM,
			HumanoidRagdoll.LEFT_LEG, HumanoidRagdoll.RIGHT_LEG,
	};

	/** Ragdoll part to {@code BodyPart}. */
	public static final BodyPart[] TO_BODY_PART = {
			BodyPart.HEAD, BodyPart.TORSO, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG,
	};

	/** A body pushed slower than this, blocks a second, gets a nudge to go over the way it faced. */
	private static final double QUIET_SPEED = 0.8;
	/** A body with no muscle left drops where it stands; this only picks the way it goes. Radians a second. */
	private static final double DROP_NUDGE = 0.35;
	/** A body letting go slowly sways before it sinks. */
	private static final double SINK_NUDGE = 0.5;
	/** The nudge: radians a second about the feet, at violence 0 and 1. */
	private static final double TOPPLE_MIN = 0.9;
	private static final double TOPPLE_MAX = 2.2;
	/** A last-tick move faster than this, blocks a second, was a teleport, not motion. */
	private static final double TELEPORT_SPEED = 25.0;
	/** The fastest inherited travel, blocks a second: a sprint-jump. */
	private static final double MAX_INHERITED_SPEED = 9.0;

	/** Which open wound a dying hand goes to, most serious region first. */
	private static final BodyPart[] WOUND_ORDER = {BodyPart.TORSO, BodyPart.HEAD, BodyPart.RIGHT_LEG,
			BodyPart.LEFT_LEG, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM};

	/**
	 * What a body was seen doing in its last tick, as the motion its ragdoll
	 * inherits, blocks a second (changed in place and returned). A jump bigger
	 * than any run is a teleport or a correction, not motion; the fastest kept
	 * is a sprint-jump. Travel faster than that reaches the ragdoll through the
	 * packet's own launch, which the server sends because it knows it.
	 */
	public static Vector3d inherited(Vector3d seen) {
		if (seen.length() > TELEPORT_SPEED) {
			seen.zero();
		} else if (seen.length() > MAX_INHERITED_SPEED) {
			seen.normalize(MAX_INHERITED_SPEED);
		}
		return seen;
	}

	/**
	 * How it died, turned into motion: the server's launch for the whole body,
	 * the killing blow's kick on the region it landed on, a nudge over for a
	 * death that pushed nothing, and the stiffness its kind of death leaves.
	 *
	 * @param bodyYaw the way the body faced, vanilla's body yaw in degrees
	 * @param feet    where it stood, world
	 * @param seen    the motion it already has (see {@link #inherited}), blocks a second
	 */
	public static void launch(HumanoidRagdoll ragdoll, float bodyYaw, CorpsePayload payload, Vector3d feet,
			Vector3d seen, Muscles muscles) {
		double violence = Math.clamp(payload.violence(), 0.0F, 1.0F);
		// What was already seen of it is in the inherited motion; the server's
		// launch (a blast, a knockback not applied yet, a fall faster than any
		// run) adds whatever is beyond that.
		Vector3d sent = new Vector3d(payload.vx(), payload.vy(), payload.vz()).mul(20.0);
		if (sent.lengthSquared() > seen.lengthSquared()) {
			ragdoll.push(sent.x - seen.x, sent.y - seen.y, sent.z - seen.z);
		}
		Vector3d moving = seen.lengthSquared() > sent.lengthSquared() ? seen : sent;

		if (payload.hitPart() >= 0 && payload.hitPart() < FROM_BODY_PART.length && payload.kick() > 0.0F) {
			double k = payload.kick() * 20.0;
			ragdoll.kick(FROM_BODY_PART[payload.hitPart()], payload.hx() * k, payload.hy() * k, payload.hz() * k);
		}

		// Over the way it was pushed, or forward onto its face when nothing
		// pushed it: head toward dir is a turn about up x dir. A body whose
		// muscles are gone, or going, needs no shove: it drops under its own
		// weight, and the turn only says which way.
		double yaw = Math.toRadians(bodyYaw);
		Vector3d forward = new Vector3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vector3d flat = new Vector3d(moving.x, 0.0, moving.z);
		boolean quiet = flat.length() < QUIET_SPEED && payload.kind() != DeathKind.EXPLOSION;
		Vector3d dir = flat.length() > 1.0e-3 ? flat.normalize() : forward;
		double rate;
		if (muscles.stringsCut) {
			rate = DROP_NUDGE;
		} else if (payload.kind() == DeathKind.FADE || payload.kind() == DeathKind.DROWNING) {
			rate = SINK_NUDGE;
		} else if (quiet) {
			rate = TOPPLE_MIN + (TOPPLE_MAX - TOPPLE_MIN) * violence;
		} else {
			rate = 0.6;
		}
		ragdoll.spin(new Vector3d(dir.z, 0.0, -dir.x).mul(rate), feet);
	}

	/** What the reflexes need: the kind of death, where it struck, the worst wound, the push. */
	public static Behaviour.Input behaviourInput(int entityId, CorpsePayload payload, @Nullable BodySnapshot snapshot,
			Muscles muscles) {
		int hit = payload.hitPart() >= 0 && payload.hitPart() < FROM_BODY_PART.length
				? FROM_BODY_PART[payload.hitPart()] : -1;
		int wound = -1;
		if (snapshot != null) {
			for (BodyPart part : WOUND_ORDER) {
				if ((snapshot.woundMask() & (1 << part.ordinal())) != 0) {
					wound = FROM_BODY_PART[part.ordinal()];
					break;
				}
			}
		}
		Vector3d push = new Vector3d(payload.hx(), 0.0, payload.hz());
		if (push.lengthSquared() < 1.0e-6) {
			push.set(payload.vx(), 0.0, payload.vz());
		}
		return new Behaviour.Input(payload.kind(), payload.violence(), hit, wound, muscles, push, entityId);
	}

	/** The muscles a body dies with, its neck included (2.0). */
	public static Muscles deathMuscles(CorpsePayload payload, @Nullable BodySnapshot snapshot) {
		Muscles muscles = Muscles.forDeath(musclesInput(payload, snapshot));
		return snapshot != null && snapshot.neckBroken() ? muscles.withBrokenNeck() : muscles;
	}

	/** What the body knew about itself as it died, from its snapshot, in ragdoll part order. */
	private static Muscles.Input musclesInput(CorpsePayload payload, @Nullable BodySnapshot snapshot) {
		int hit = payload.hitPart() >= 0 && payload.hitPart() < FROM_BODY_PART.length
				? FROM_BODY_PART[payload.hitPart()] : -1;
		if (snapshot == null) {
			return Muscles.Input.healthy(payload.kind(), payload.violence(), hit);
		}
		boolean[] broken = new boolean[HumanoidRagdoll.PARTS];
		boolean[] limp = new boolean[HumanoidRagdoll.PARTS];
		for (BodyPart part : BodyPart.values()) {
			int i = FROM_BODY_PART[part.ordinal()];
			broken[i] = snapshot.broken(part);
			// The same ladder the limp and the prey score read (LimpMath); a core
			// region has no rung.
			limp[i] = part.isLimb() && LimpMath.rung(snapshot.destroyed(part), snapshot.broken(part),
					snapshot.fraction(part)) == LimpMath.RUNG_RUINED;
		}
		AdrenalineMath.Phase adrenaline = snapshot.adrenaline();
		return new Muscles.Input(payload.kind(), payload.violence(), hit, snapshot.consciousness(),
				snapshot.unconscious(), broken, limp, snapshot.concussionGrade(),
				adrenaline == AdrenalineMath.Phase.SURGE, adrenaline == AdrenalineMath.Phase.CRASH);
	}

	/** Which ragdoll parts a body still has, by its snapshot: lost limbs take no part in the fall. */
	public static boolean[] present(@Nullable BodySnapshot snapshot) {
		boolean[] present = {true, true, true, true, true, true};
		if (snapshot != null) {
			for (int bp = 0; bp < FROM_BODY_PART.length; bp++) {
				if ((snapshot.destroyedMask() & (1 << bp)) != 0) {
					present[FROM_BODY_PART[bp]] = false;
				}
			}
		}
		return present;
	}

	/** A standing player is this tall, blocks, and its model is drawn at this many blocks a pixel. */
	private static final double PLAYER_HEIGHT = 1.8;
	private static final double PLAYER_UNIT = 0.9375 / 16.0;

	/**
	 * The server's copy of a death (2.0): the same launch on a body the server
	 * can build without a renderer. It is a player's shape, scaled to the height
	 * of whatever died, standing straight with its arms at its sides; the client
	 * has the real model and the pose it was drawn in. Close enough to say when
	 * the body lands and how hard, which is all the server asks of it.
	 *
	 * @param feet    where it stood, world
	 * @param bodyYaw vanilla's body yaw, degrees
	 * @param height  the standing body's height, blocks
	 * @param motion  what the server knows it was doing in its last tick, blocks a second
	 */
	public static HumanoidRagdoll serverBody(Vector3d feet, float bodyYaw, double height, CorpsePayload payload,
			@Nullable BodySnapshot snapshot, Vector3d motion, int entityId) {
		HumanoidRagdoll.Part[] parts = HumanoidRagdoll.playerParts(false);
		double unit = PLAYER_UNIT * height / PLAYER_HEIGHT;
		HumanoidRagdoll ragdoll = new HumanoidRagdoll(parts, feet, bodyYaw, unit, new float[18], present(snapshot));
		Vector3d seen = inherited(new Vector3d(motion));
		ragdoll.push(seen.x, seen.y, seen.z);
		Muscles muscles = deathMuscles(payload, snapshot);
		ragdoll.setMuscles(muscles);
		ragdoll.setBehaviour(Behaviour.choose(behaviourInput(entityId, payload, snapshot, muscles)));
		launch(ragdoll, bodyYaw, payload, feet, seen, muscles);
		return ragdoll;
	}
}
