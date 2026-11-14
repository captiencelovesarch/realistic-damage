package dev.captience.realisticdamage.damage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.ragdoll.CorpseLaunch;
import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;

/**
 * A dead body keeps taking body damage until it comes to rest (2.0).
 *
 * <p>Death used to end the body's story. Vanilla refuses to hurt the dead, so
 * the trauma pipeline never ran again; the one thing that still happened to a
 * corpse was {@link LethalBlast}, in the instant of the death itself. Someone
 * shot out of the air above a ravine landed in one piece however far down the
 * ravine went.
 *
 * <p>The difficulty is that the landing was nobody's to judge. The fall of a
 * dead body is a ragdoll, and ragdolls ran only on the clients watching, each
 * its own copy; the server, which is the only place a limb can be lost for
 * everyone at once, never knew what the body hit. So now the server runs a
 * copy too ({@link CorpseLaunch#serverBody}), for the deaths where it could
 * matter: a body that was really travelling when it died, or that has a long
 * way to fall. It is started by the same code from the same packet as the
 * clients' copies, and it is followed through the same blocks until it lies
 * still or the server lets the body go ({@code Balance.CORPSE_HOLD_TICKS}).
 *
 * <p>The server's copy is not the one anybody sees, and the copies are not
 * identical (the server has no renderer: no pose, no skin). What they agree
 * on, measured in {@code CorpseAgreementTest}, is when the body comes down and
 * how fast: within two ticks and an eighth of the speed, on every copy. What
 * they do not agree on is which arm flails into the floor a moment later. So
 * the rule is built on the first and not the second ({@link HumanoidRagdoll#strike}):
 * a limb is judged at the speed the body as a whole arrived at, never at the
 * speed the limb alone was whipping.
 *
 * <p>The rule itself is the living fall's, on a dead body's terms
 * ({@link CorpseImpactMath}): speed becomes the drop that would have caused
 * it, the drop becomes fall-trauma, the surface and the armour over the limb
 * take their share, and the result is held against fixed bars. Over one bar
 * the bone breaks; over a higher one the limb comes off, through
 * {@link DestructionLogic#destroy} like every other lost limb, so the severed
 * limb, the stump and the snapshot are the same ones the living get. Limbs
 * only: a head never comes off this way, and nothing here can change whether
 * or how the body died.
 *
 * <p>Deterministic: no RNG, the hardest strike first and ties in a fixed
 * order. Bounded: at most {@code Balance.CORPSE_IMPACT_MAX_BODIES} bodies at
 * once, each for at most the hold. Humanoid bodies only (players, and mobs
 * with a humanoid body plan): the ragdoll being followed is a humanoid one.
 * Server thread only.
 */
public final class CorpseImpacts {
	private CorpseImpacts() {}

	/** What a limb taken off by a landing bleeds: taken off in one go. */
	private static final dev.captience.realisticdamage.body.WoundSeverity STUMP =
			dev.captience.realisticdamage.body.WoundSeverity.CRITICAL;
	/** A body arriving with at least this much sideways travel, blocks a second, throws the limb that way. */
	private static final double THROW_ALONG = 2.0;

	/** Something the server saw and has not applied yet. */
	private record Pending(int due, int part, boolean sever, Vec3 along) {}

	/** One body being followed. */
	private static final class Followed {
		final LivingEntity body;
		final HumanoidRagdoll ragdoll;
		final LevelPhysicsWorld world;
		final List<Pending> pending = new ArrayList<>(2);
		/** Per ragdoll part: already judged to come off, or to break, by this rule. */
		final boolean[] leaving = new boolean[HumanoidRagdoll.PARTS];
		final boolean[] breaking = new boolean[HumanoidRagdoll.PARTS];
		final double[] strikes = new double[HumanoidRagdoll.PARTS];
		/** Ticks since the death, and limbs this rule has taken off this body. */
		int age;
		int taken;

		Followed(LivingEntity body, HumanoidRagdoll ragdoll) {
			this.body = body;
			this.ragdoll = ragdoll;
			this.world = new LevelPhysicsWorld(body.level());
		}
	}

	private static final List<Followed> FOLLOWED = new ArrayList<>();

	/**
	 * What the server knows a body was doing as it died, blocks a tick: its own
	 * motion, or, for a player who was really travelling, what their client last
	 * reported. The server does not move players, so a player's own motion on
	 * the server is next to nothing even at the bottom of a forty-block fall;
	 * their last reported move is the fall. Only taken when it is faster than
	 * any run or jump ({@code Balance.CORPSE_IMPACT_FOLLOW_SPEED}), so a death
	 * on foot is launched exactly as it always was.
	 */
	public static Vec3 travel(LivingEntity body) {
		Vec3 own = body.getDeltaMovement();
		if (body instanceof ServerPlayer player) {
			Vec3 known = player.getKnownMovement();
			if (known.length() >= Balance.CORPSE_IMPACT_FOLLOW_SPEED && known.lengthSqr() > own.lengthSqr()) {
				return known;
			}
		}
		return own;
	}

	/**
	 * From {@link Corpses}, once the death has been sent: starts following this
	 * body if it is one the rule covers and its death had the energy to matter.
	 */
	public static void follow(LivingEntity body, CorpsePayload payload) {
		if (!(body.level() instanceof ServerLevel) || MobAnatomy.plan(body) != BodyPlan.HUMANOID) {
			return;
		}
		for (Followed f : FOLLOWED) {
			if (f.body == body) {
				return;
			}
		}
		if (FOLLOWED.size() >= Balance.CORPSE_IMPACT_MAX_BODIES) {
			return;
		}
		Vec3 motion = travel(body);
		double launch = Math.sqrt(payload.vx() * payload.vx() + payload.vy() * payload.vy() + payload.vz() * payload.vz());
		if (!CorpseImpactMath.worthFollowing(Math.max(launch, motion.length()), clearDrop(body))) {
			return;
		}
		BodySnapshot snapshot = BodySnapshot.of(RDAttachments.get(body));
		double height = body.getType().getDimensions().height() * body.getScale();
		HumanoidRagdoll ragdoll = CorpseLaunch.serverBody(new Vector3d(body.getX(), body.getY(), body.getZ()),
				body.yBodyRot, height, payload, snapshot, new Vector3d(motion.x, motion.y, motion.z).mul(20.0), body.getId());
		FOLLOWED.add(new Followed(body, ragdoll));
	}

	/** The clear air straight down from a body's feet, blocks, up to the distance that matters. */
	private static double clearDrop(LivingEntity body) {
		Vec3 feet = body.position();
		double reach = Balance.CORPSE_IMPACT_FOLLOW_DROP;
		BlockHitResult hit = body.level().clip(new ClipContext(feet.add(0.0, 0.1, 0.0), feet.add(0.0, -reach, 0.0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK ? Math.max(0.0, feet.y - hit.getLocation().y) : reach;
	}

	/** Once a server tick, after everything else has moved. */
	public static void tick(MinecraftServer server) {
		if (FOLLOWED.isEmpty()) {
			return;
		}
		Iterator<Followed> it = FOLLOWED.iterator();
		while (it.hasNext()) {
			Followed f = it.next();
			LivingEntity body = f.body;
			// Gone (respawned, unloaded, another server's), no longer dead (a
			// respawn reusing the object cannot happen, but alive is alive), let
			// go by the server, or a simulation that gave out: nothing more is
			// owed to it, and what was still waiting is dropped with it.
			if (body.isRemoved() || !body.isDeadOrDying() || body.level().getServer() != server
					|| f.age >= Balance.CORPSE_HOLD_TICKS || f.ragdoll.broken()) {
				it.remove();
				continue;
			}
			f.age++;
			apply(f);
			if (f.ragdoll.asleep() && f.pending.isEmpty()) {
				// At rest: the window is over.
				it.remove();
				continue;
			}
			f.ragdoll.tick(f.world, CorpseLaunch.DT);
			judge(f);
		}
	}

	/** What this tick's strikes do, hardest first. Nothing is applied here; see {@link #apply}. */
	private static void judge(Followed f) {
		HumanoidRagdoll r = f.ragdoll;
		for (int part = 0; part < HumanoidRagdoll.PARTS; part++) {
			boolean limb = part >= HumanoidRagdoll.RIGHT_ARM;
			f.strikes[part] = limb && r.present(part) && !f.leaving[part] ? r.strike(part) : 0.0;
		}
		int[] order = CorpseImpactMath.hardestFirst(f.strikes);
		if (order.length == 0) {
			return;
		}
		LivingEntity body = f.body;
		ServerConfig config = ServerConfig.get();
		RealisticDamageData data = RDAttachments.get(body);
		BodyPlan plan = MobAnatomy.plan(body);
		boolean canSever = config.enableLimbDestruction && MobAnatomy.canLoseParts(body);
		double scale = config.bodyDamageMultiplier / MobAnatomy.vitalityScale(body);
		Vector3d arriving = r.arriving();
		Vec3 along = Math.hypot(arriving.x, arriving.z) >= THROW_ALONG ? new Vec3(arriving.x, 0.0, arriving.z) : Vec3.ZERO;
		for (int part : order) {
			BodyPart region = CorpseLaunch.TO_BODY_PART[part];
			if (!plan.has(region) || data.part(region).destroyed()) {
				continue;
			}
			Body limb = r.body(part);
			// The block it struck: a little way into the surface from the point of contact.
			BlockPos struck = BlockPos.containing(limb.impactAt.x - limb.impactNormal.x * 0.05,
					limb.impactAt.y - limb.impactNormal.y * 0.05, limb.impactAt.z - limb.impactNormal.z * 0.05);
			double surface = LandingSurface.factor(body.level().getBlockState(struck));
			double armour = ArmorModifier.reduction(body, region, DamageCategory.FALL);
			double trauma = CorpseImpactMath.trauma(f.strikes[part], surface, armour, scale);
			if (canSever && CorpseImpactMath.severs(region, trauma, f.taken)) {
				f.taken++;
				f.leaving[part] = true;
				f.pending.add(new Pending(f.age + Balance.CORPSE_IMPACT_DELAY_TICKS, part, true, along));
			} else if (!f.breaking[part] && !data.part(region).broken() && CorpseImpactMath.breaks(region, trauma)) {
				f.breaking[part] = true;
				f.pending.add(new Pending(f.age + Balance.CORPSE_IMPACT_DELAY_TICKS, part, false, Vec3.ZERO));
			}
		}
	}

	/**
	 * Applies what has waited long enough: the bone, or the limb, for everyone.
	 * The server's own copy changes here too and not when the strike was seen,
	 * so it goes on falling as the clients' copies do (theirs lose the limb when
	 * the news reaches them, which is now).
	 */
	private static void apply(Followed f) {
		if (f.pending.isEmpty()) {
			return;
		}
		LivingEntity body = f.body;
		RealisticDamageData data = RDAttachments.get(body);
		boolean changed = false;
		Iterator<Pending> it = f.pending.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			if (p.due() > f.age) {
				continue;
			}
			it.remove();
			BodyPart region = CorpseLaunch.TO_BODY_PART[p.part()];
			if (data.part(region).destroyed()) {
				continue;
			}
			if (!p.sever()) {
				if (!data.part(region).broken()) {
					// No Notifier line and no scream: a corpse is not told what broke.
					data.part(region).setBroken(true);
					f.ragdoll.breakLimb(p.part());
					changed = true;
				}
				continue;
			}
			// Switched off, or the body stopped qualifying, while this waited.
			if (!ServerConfig.get().enableLimbDestruction || !MobAnatomy.canLoseParts(body)) {
				continue;
			}
			// Where the limb is on the server's copy now: where it leaves the body
			// on everyone's, near enough. Lifted a little, so it starts on the
			// ground and not in it.
			Body limb = f.ragdoll.body(p.part());
			Vec3 at = new Vec3(limb.x.x, limb.x.y + 0.1, limb.x.z);
			f.ragdoll.detach(p.part());
			DestructionLogic.destroy(body, data, region, STUMP, p.along(), DestructionLogic.Cause.LOST, at);
			changed = true;
		}
		if (changed) {
			RDAttachments.pushSnapshot(body);
		}
	}

	/** How many bodies are being followed; for the gametests. */
	public static int following() {
		return FOLLOWED.size();
	}

	/** Whether this body is being followed; for the gametests. */
	public static boolean following(LivingEntity body) {
		for (Followed f : FOLLOWED) {
			if (f.body == body) {
				return true;
			}
		}
		return false;
	}
}
