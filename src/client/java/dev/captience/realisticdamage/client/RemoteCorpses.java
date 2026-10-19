package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.net.CorpsePayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Other people's bodies, falling the way they were hit (1.7).
 *
 * <p>The video-recording playtest: a friend blown up by a creeper flew off in
 * one piece and did vanilla's stiff ninety-degree tip onto their side. Two
 * reasons. A dead player's own client stops sending its position (the death
 * camera keeps that body local), so everyone else saw the server's copy, which
 * barely moves; and vanilla's death animation is one rigid roll about the feet,
 * whatever killed you.
 *
 * <p>So each watching client runs a small rigid-body simulation from the one
 * launch the server sends ({@link CorpsePayload}): the body's centre flies
 * under gravity and drag, collides with blocks, slides to a stop; it tumbles
 * end over end while a blast carries it and lands flat on its back or its
 * front — whichever it was closer to — with a bounce; an ordinary death topples
 * the way the blow pushed it, accelerating like something falling over rather
 * than a hinge. Humanoid bodies only (players, zombies, skeletons, piglins):
 * the pivot and the "lying flat" rule assume a tall, thin body. Everything
 * else keeps vanilla's roll.
 *
 * <p>Deterministic: no RNG, every choice follows the launch.
 */
public final class RemoteCorpses {
	private RemoteCorpses() {}

	private static final double GRAVITY = 0.08;
	private static final double AIR_DRAG = 0.98;
	private static final double GROUND_FRICTION = 0.55;
	/** Half the thickness of a lying body, blocks. */
	private static final double HALF_THICKNESS = 0.16;
	/** Below this push, blocks a tick, a body crumples forward instead of falling with it. */
	private static final double PUSH_MIN = 0.04;
	/** A blast's spin, degrees a tick, at violence 0 and 1. */
	private static final double SPIN_MIN = 9.0;
	private static final double SPIN_MAX = 34.0;
	/** The landing spring: stiffness per tick squared, damping per tick. */
	private static final double LAND_STIFFNESS = 0.22;
	private static final double LAND_DAMPING = 0.42;
	/** How long a corpse is simulated at most, ticks; after that it is left where it is. */
	private static final int MAX_AGE = 600;
	/** How long a body keeps vanilla's red death tint, ticks. */
	private static final int RED_TICKS = 8;

	private static final Int2ObjectOpenHashMap<Corpse> CORPSES = new Int2ObjectOpenHashMap<>();

	/** The packet: start simulating that body, if this client can see it and it is a shape we know. */
	public static void start(CorpsePayload payload) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || !ClientEffectsConfig.get().deathCamera) {
			return;
		}
		Entity entity = level.getEntity(payload.entityId());
		if (!(entity instanceof LivingEntity body) || body == client.player) {
			return;
		}
		CORPSES.put(body.getId(), new Corpse(body, payload));
	}

	/** Once per client tick. */
	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			CORPSES.clear();
			return;
		}
		var it = CORPSES.int2ObjectEntrySet().fastIterator();
		while (it.hasNext()) {
			Corpse corpse = it.next().getValue();
			Entity entity = level.getEntity(corpse.id);
			if (!(entity instanceof LivingEntity body) || body.isRemoved() || !body.isDeadOrDying()
					|| corpse.age > MAX_AGE) {
				it.remove();
				continue;
			}
			corpse.tick(level);
		}
	}

	public static void clear() {
		CORPSES.clear();
	}

	/** How many bodies are being simulated; for the gametest. */
	public static int count() {
		return CORPSES.size();
	}

	/** The simulated body for this entity, or null. */
	public static @Nullable Corpse of(Entity entity) {
		return CORPSES.isEmpty() ? null : CORPSES.get(entity.getId());
	}

	/**
	 * From the end of {@code extractRenderState}: puts the body where the
	 * simulation has it and swaps vanilla's roll for the simulation's.
	 */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Corpse corpse = of(entity);
		if (corpse == null || !(state instanceof HumanoidRenderState)) {
			((CorpseHolder) state).realisticdamage$setCorpse(null);
			return;
		}
		Vec3 centre = corpse.centre(partialTicks);
		state.x = centre.x;
		state.y = centre.y - corpse.halfHeight * state.scale;
		state.z = centre.z;
		state.bodyRot = corpse.bodyYaw;
		state.yRot = 0.0F;
		state.walkAnimationSpeed = 0.0F;
		state.deathTime = 0.0F; // vanilla's roll off
		// Vanilla tints a dead body red for the whole second it has; this one
		// lasts as long as its owner's death screen takes, so the flash is the
		// hit, not the corpse.
		state.hasRedOverlay = corpse.age < RED_TICKS;
		// The name would stay hanging where they died while the body flew off.
		state.nameTag = null;
		((CorpseHolder) state).realisticdamage$setCorpse(new Pose(corpse.rotation(partialTicks),
				(float) corpse.halfHeight, corpse.flail(partialTicks), corpse.age + partialTicks, corpse.id));
	}

	/** From the end of {@code setupRotations}: the body's orientation about its own centre. */
	public static boolean applyRotation(LivingEntityRenderState state, PoseStack poseStack) {
		Pose pose = ((CorpseHolder) state).realisticdamage$corpse();
		if (pose == null) {
			return false;
		}
		poseStack.translate(0.0F, pose.pivot(), 0.0F);
		poseStack.mulPose(pose.rotation());
		poseStack.translate(0.0F, -pose.pivot(), 0.0F);
		return true;
	}

	/**
	 * What one frame of a corpse needs: its orientation about its centre, and
	 * how hard its limbs are flailing (1 in the air, 0 lying still).
	 */
	public record Pose(Quaternionf rotation, float pivot, float flail, float time, int seed) {}

	/**
	 * The limbs of a body with nobody in it (from the end of HumanoidModel
	 * setupAnim): thrown about while it flies, splayed where it lands. Arms are
	 * set outright rather than added to, because whatever vanilla had them doing
	 * (a swing, holding a bow) stopped when the player did.
	 */
	public static void limp(LivingEntityRenderState state, net.minecraft.client.model.HumanoidModel<?> model) {
		Pose pose = ((CorpseHolder) state).realisticdamage$corpse();
		if (pose == null) {
			return;
		}
		float f = pose.flail();
		float t = pose.time();
		// Each body flails on its own phase, from its id: deterministic, not in step.
		float p = (pose.seed() % 17) * 0.37F;
		float splay = 0.45F + 0.75F * f;
		model.head.xRot = 0.25F * (1.0F - f) + 0.6F * f * Mth.sin(t * 0.9F + p);
		model.head.yRot = 0.35F * Mth.sin(p * 3.0F) * (1.0F - f);
		model.rightArm.xRot = -0.4F + f * 1.8F * Mth.sin(t * 1.1F + p);
		model.rightArm.yRot = 0.0F;
		model.rightArm.zRot = splay + 0.25F * f * Mth.cos(t * 1.3F + p);
		model.leftArm.xRot = -0.25F + f * 1.8F * Mth.sin(t * 0.95F + p + 1.7F);
		model.leftArm.yRot = 0.0F;
		model.leftArm.zRot = -splay - 0.25F * f * Mth.cos(t * 1.2F + p + 0.9F);
		float legSplay = 0.12F + 0.25F * f;
		model.rightLeg.xRot = f * 0.9F * Mth.sin(t * 0.8F + p + 0.5F);
		model.rightLeg.yRot = 0.0F;
		model.rightLeg.zRot = legSplay;
		model.leftLeg.xRot = f * 0.9F * Mth.sin(t * 0.85F + p + 2.4F);
		model.leftLeg.yRot = 0.0F;
		model.leftLeg.zRot = -legSplay;
	}

	/** One body. */
	public static final class Corpse {
		final int id;
		final float bodyYaw;
		/** Half the standing height, unscaled model units (blocks at scale 1). */
		final double halfHeight;
		final double scale;
		/** The fall axis, in the body's own frame (after vanilla's yaw), unit length. */
		final float axisX;
		final float axisZ;
		/** The same axis in the world, for placing things on the body. */
		final Vec3 worldAxis;
		final boolean blast;
		Vec3 pos;
		Vec3 posO;
		Vec3 vel;
		double angle;
		double angleO;
		double angVel;
		boolean onGround;
		boolean landed;
		boolean toppling;
		int age;
		double flail;
		double flailO;

		Corpse(LivingEntity body, CorpsePayload payload) {
			this.id = body.getId();
			this.bodyYaw = body.yBodyRot;
			this.scale = body.getScale();
			this.halfHeight = body.getType().getDimensions().height() * 0.5;
			this.blast = payload.kind() == DeathKind.EXPLOSION;
			this.pos = body.position().add(0.0, halfHeight * scale, 0.0);
			this.posO = pos;
			this.vel = new Vec3(payload.vx(), payload.vy(), payload.vz());

			// Which way it falls: with the push, or forward onto its face when
			// nothing pushed it (a bleed-out, a drowning, starving).
			double yaw = Math.toRadians(bodyYaw);
			Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
			Vec3 push = new Vec3(vel.x, 0.0, vel.z);
			Vec3 dir = push.length() > PUSH_MIN ? push.normalize() : forward;
			// Head goes toward dir: rotate about up x dir. Expressed in the body's
			// frame, which vanilla has already turned by 180 - bodyYaw about Y.
			Vec3 worldAxis = new Vec3(dir.z, 0.0, -dir.x);
			this.worldAxis = worldAxis;
			double turn = Math.toRadians(180.0 - bodyYaw);
			double cos = Math.cos(turn);
			double sin = Math.sin(turn);
			// Inverse of a +turn rotation about Y.
			this.axisX = (float) (worldAxis.x * cos - worldAxis.z * sin);
			this.axisZ = (float) (worldAxis.x * sin + worldAxis.z * cos);

			double violence = Mth.clamp(payload.violence(), 0.0F, 1.0F);
			if (blast) {
				this.angVel = SPIN_MIN + (SPIN_MAX - SPIN_MIN) * violence;
				this.toppling = false;
				this.flail = 1.0;
			} else {
				this.angVel = 1.5 + 4.0 * violence;
				this.toppling = true;
			}
		}

		/**
		 * Where a point on the standing body is on the simulated one: the blood
		 * emitter places wounds on the body the server knows about, which stays
		 * where it died while this one flies.
		 */
		public Vec3 place(LivingEntity body, Vec3 upright) {
			Vec3 rel = upright.subtract(body.position().add(0.0, halfHeight * scale, 0.0));
			double a = Math.toRadians(angle);
			// Rodrigues, about a unit horizontal axis.
			Vec3 k = worldAxis;
			Vec3 rotated = rel.scale(Math.cos(a))
					.add(k.cross(rel).scale(Math.sin(a)))
					.add(k.scale(k.dot(rel) * (1.0 - Math.cos(a))));
			return pos.add(rotated);
		}

		/** For the client gametest. */
		public Vec3 position() {
			return pos;
		}

		/** Degrees from upright about the fall axis; for the client gametest. */
		public double angle() {
			return angle;
		}

		/** 1 while thrown, easing to 0 once it lies still. */
		float flail(float partialTicks) {
			return (float) Mth.lerp(partialTicks, flailO, flail);
		}

		Vec3 centre(float partialTicks) {
			return posO.lerp(pos, partialTicks);
		}

		Quaternionf rotation(float partialTicks) {
			float a = (float) Math.toRadians(Mth.lerp(partialTicks, angleO, angle));
			return new Quaternionf().rotateAxis(a, axisX, 0.0F, axisZ);
		}

		/** Centre height above the floor for the current tilt. */
		private double halfExtent() {
			double r = Math.toRadians(angle);
			return halfHeight * scale * Math.abs(Math.cos(r)) + HALF_THICKNESS * scale * Math.abs(Math.sin(r));
		}

		/** The nearest lying angle: 90 (on the back or front, by the fall) plus whole turns. */
		private double lyingTarget() {
			return 90.0 + 180.0 * Math.round((angle - 90.0) / 180.0);
		}

		void tick(ClientLevel level) {
			age++;
			posO = pos;
			angleO = angle;
			flailO = flail;
			// Limbs follow the body's motion: thrown about while it moves or turns,
			// still once it stops. A topple gets a little of it on the way down.
			double motion = Math.min(1.0, vel.length() * 2.0 + Math.abs(angVel) * 0.05);
			flail = Math.max(motion, flail * 0.9);

			// --- Flight ---
			vel = new Vec3(vel.x * AIR_DRAG, (vel.y - GRAVITY) * AIR_DRAG, vel.z * AIR_DRAG);
			Vec3 target = pos.add(vel);
			BlockHitResult hit = level.clip(new ClipContext(pos, target, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, CollisionContextHolder.EMPTY));
			if (hit.getType() == HitResult.Type.BLOCK) {
				Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
				target = hit.getLocation().add(normal.scale(0.02));
				double into = vel.dot(normal);
				vel = vel.subtract(normal.scale(into * 1.3)); // a little bounce off walls
			}
			pos = target;

			// --- Floor under the body, for its current tilt ---
			double extent = halfExtent();
			BlockHitResult floor = level.clip(new ClipContext(pos, pos.add(0.0, -extent - 0.02, 0.0),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContextHolder.EMPTY));
			boolean wasOnGround = onGround;
			double fallSpeed = -vel.y;
			onGround = floor.getType() == HitResult.Type.BLOCK;
			if (onGround) {
				double floorY = floor.getLocation().y;
				pos = new Vec3(pos.x, Math.max(pos.y, floorY + extent), pos.z);
				if (!wasOnGround) {
					if (blast) {
						// The landing kicks the spin toward flat.
						angVel *= 0.45;
					}
					thud(level, floor, Math.max(fallSpeed, toppling ? 0.0 : 0.25));
				}
				vel = new Vec3(vel.x * GROUND_FRICTION, Math.max(0.0, vel.y) * 0.2, vel.z * GROUND_FRICTION);
			}

			// --- Orientation ---
			if (toppling) {
				// Falling over: slow at first, fast at the end, like a plank.
				angVel += 0.9 + 5.5 * Math.sin(Math.toRadians(Math.max(angle, 0.0)));
				angle += angVel;
				if (angle >= 90.0) {
					angle = 90.0;
					if (onGround) {
						BlockHitResult under = level.clip(new ClipContext(pos, pos.add(0.0, -1.0, 0.0),
								ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContextHolder.EMPTY));
						if (under.getType() == HitResult.Type.BLOCK) {
							thud(level, under, 0.3 + angVel * 0.02);
						}
					}
					angVel = -angVel * 0.22; // the bounce off the floor
					toppling = false;
					landed = true;
				}
			} else if (blast && !onGround && !landed) {
				angle += angVel;
				angVel *= 0.985;
			} else {
				landed = true;
				double target2 = lyingTarget();
				angVel += (target2 - angle) * LAND_STIFFNESS - angVel * LAND_DAMPING;
				angle += angVel;
			}
		}
	}

	/** A body hitting the floor: the sound of it, and the floor's dust kicked up. */
	static void thud(ClientLevel level, BlockHitResult floor, double speed) {
		double strength = Mth.clamp((speed - 0.15) / 0.9, 0.0, 1.0);
		if (strength <= 0.0) {
			return;
		}
		Vec3 at = floor.getLocation();
		var state = level.getBlockState(floor.getBlockPos());
		level.playLocalSound(at.x, at.y, at.z,
				strength > 0.55 ? net.minecraft.sounds.SoundEvents.PLAYER_BIG_FALL
						: net.minecraft.sounds.SoundEvents.PLAYER_SMALL_FALL,
				net.minecraft.sounds.SoundSource.PLAYERS, (float) (0.6 + 0.6 * strength),
				(float) (0.75 - 0.15 * strength), false);
		if (state.isAir()) {
			return;
		}
		level.playLocalSound(at.x, at.y, at.z, state.getSoundType().getFallSound(),
				net.minecraft.sounds.SoundSource.BLOCKS, (float) (0.5 + 0.5 * strength), 0.8F, false);
		var particle = new net.minecraft.core.particles.BlockParticleOption(
				net.minecraft.core.particles.ParticleTypes.BLOCK, state);
		int count = (int) (6 + 24 * strength);
		for (int i = 0; i < count; i++) {
			// Deterministic ring: the dust goes out evenly, not by dice.
			double t = (i + 0.5) / count * Math.PI * 2.0 * 3.0;
			double r = 0.2 + 0.6 * ((i * 0.618034) % 1.0);
			level.addParticle(particle, at.x + Math.cos(t) * r, at.y + 0.05, at.z + Math.sin(t) * r,
					Math.cos(t) * 0.12 * strength, 0.08 + 0.12 * strength, Math.sin(t) * 0.12 * strength);
		}
	}

	/** {@code CollisionContext.empty()}, held once. */
	private static final class CollisionContextHolder {
		static final net.minecraft.world.phys.shapes.CollisionContext EMPTY =
				net.minecraft.world.phys.shapes.CollisionContext.empty();
	}
}
