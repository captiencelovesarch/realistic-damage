package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.ragdoll.Behaviour;
import dev.captience.realisticdamage.ragdoll.Muscles;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import dev.captience.realisticdamage.systems.LimpMath;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Someone else's body as a real ragdoll (active ragdolls, phase 0): six
 * jointed boxes cut from the model that drew it, launched the way the server
 * says it died, falling through the blocks around it.
 *
 * <p>Built from the renderer's own model, so a slim-armed player, a zombie
 * and a skeleton each fall as the shape they are. Only {@link HumanoidModel}s:
 * the joints assume a head, a torso, two arms and two legs.
 *
 * <p>Deterministic (no RNG): everything follows the launch, the model and the
 * blocks, so every client watching sees the same fall in its broad strokes.
 */
final class RagdollCorpse implements RemoteCorpses.View {
	/** Seconds per tick. */
	private static final double DT = 0.05;
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
	/** How much of the walk cycle's limb swing a body keeps; vanilla's legs swing far too fast. */
	private static final double LIMB_SWING_SHARE = 0.35;
	/** How long a body keeps vanilla's red death tint, ticks. */
	private static final int RED_TICKS = 8;

	/** {@code BodyPart} ordinal to ragdoll part: the two enums list the sides in opposite orders. */
	private static final int[] FROM_BODY_PART = {
			HumanoidRagdoll.HEAD, HumanoidRagdoll.TORSO,
			HumanoidRagdoll.LEFT_ARM, HumanoidRagdoll.RIGHT_ARM,
			HumanoidRagdoll.LEFT_LEG, HumanoidRagdoll.RIGHT_LEG,
	};

	final int id;
	final HumanoidRagdoll body;
	/** The entity this body was, to notice it gone or replaced (a respawn reuses the id). */
	final java.lang.ref.WeakReference<LivingEntity> entity;
	/**
	 * How it was last drawn (phase 4: bodies that stay). Minecraft makes a
	 * fresh state every frame, so holding the last one is safe; once the
	 * entity is gone, this is what the body keeps looking like.
	 */
	net.minecraft.client.renderer.entity.state.@Nullable LivingEntityRenderState lastState;
	/** Ticks since the entity went away, or -1 while it is still there. */
	int orphanAge = -1;
	/** The renderer's model scale (vanilla's {@code scale(state, poseStack)}). */
	private final float modelScale;
	private final McPhysicsWorld world;
	private final ClientLevel level;
	int age;
	/** The torso's fall speed last tick, blocks a second, and ticks since the last thud. */
	private double lastFall;
	private int sinceThud = 100;
	/** A torso stopping from at least this fast, blocks a second, lands with a sound. */
	private static final double THUD_SPEED = 3.5;

	private RagdollCorpse(LivingEntity entity, HumanoidRagdoll body, float modelScale, ClientLevel level) {
		this.id = entity.getId();
		this.entity = new java.lang.ref.WeakReference<>(entity);
		this.body = body;
		this.modelScale = modelScale;
		this.world = new McPhysicsWorld(level);
		this.level = level;
	}

	/** A ragdoll for this body, or null when its model is not a humanoid one. */
	static @Nullable RagdollCorpse create(LivingEntity entity, CorpsePayload payload, ClientLevel level) {
		EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)
				|| !(living.getModel() instanceof HumanoidModel<?> model)) {
			return null;
		}
		HumanoidRagdoll.Part[] parts = {
				part(model.head), part(model.body), part(model.rightArm),
				part(model.leftArm), part(model.rightLeg), part(model.leftLeg),
		};
		float modelScale = modelScale(living);
		double unit = entity.getScale() * modelScale / 16.0;

		// Lost limbs take no part in the fall.
		boolean[] present = {true, true, true, true, true, true};
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		if (snapshot != null) {
			for (int bp = 0; bp < FROM_BODY_PART.length; bp++) {
				if ((snapshot.destroyedMask() & (1 << bp)) != 0) {
					present[FROM_BODY_PART[bp]] = false;
				}
			}
		}

		float yaw = entity.yBodyRot;
		Vector3d feet = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
		// What this client saw it doing in its last tick. A jump bigger than any
		// run is a teleport or a correction, not motion.
		Vector3d seen = new Vector3d(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo)
				.div(DT);
		if (seen.length() > TELEPORT_SPEED) {
			seen.zero();
		} else if (seen.length() > MAX_INHERITED_SPEED) {
			seen.normalize(MAX_INHERITED_SPEED);
		}
		// The stride: never more than the body was actually moving. A watching
		// client's walk animation for someone else can read full stride while
		// they stand still (it follows position corrections), and a corpse
		// launched mid-stride from a standstill kicks its legs out. Vanilla's
		// own rule is four times the distance a tick, capped at one.
		float stride = Math.min(entity.walkAnimation.speed(),
				(float) Math.min(1.0, Math.hypot(seen.x, seen.z) * DT * 4.0));
		float[] now = deathPose(entity, 1.0F, 0.0F, stride);
		float[] before = deathPose(entity, 1.0F, stride, stride);
		HumanoidRagdoll ragdoll = new HumanoidRagdoll(parts, feet, yaw, unit, now, present);
		ragdoll.inheritMotion(parts, before, seen, LIMB_SWING_SHARE, DT);
		Muscles muscles = Muscles.forDeath(musclesInput(payload, snapshot));
		ragdoll.setMuscles(muscles);
		ragdoll.setBehaviour(Behaviour.choose(behaviourInput(entity, payload, snapshot, muscles)));
		launch(ragdoll, entity, payload, feet, seen, muscles);
		if (System.getenv("RD_RAGDOLL_TRACE") != null) {
			System.out.printf(java.util.Locale.ROOT,
					"[rd] ragdoll launch %s: kind %s violence %.2f sent (%.2f %.2f %.2f) seen (%.2f %.2f %.2f) part %d kick %.2f yaw %.1f stride %.2f%n",
					entity.getName().getString(), payload.kind(), payload.violence(), payload.vx() * 20, payload.vy() * 20,
					payload.vz() * 20, seen.x, seen.y, seen.z, payload.hitPart(), payload.kick() * 20, yaw, stride);
		}
		return new RagdollCorpse(entity, ragdoll, modelScale, level);
	}

	/** A part's pivot and the bounds of its own cubes (not its overlay children). */
	private static HumanoidRagdoll.Part part(ModelPart part) {
		var pose = part.getInitialPose();
		final float[] b = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		PoseStack scratch = new PoseStack();
		part.visit(scratch, (p, path, index, cube) -> {
			if (!path.isEmpty()) {
				return;
			}
			b[0] = Math.min(b[0], cube.minX);
			b[1] = Math.min(b[1], cube.minY);
			b[2] = Math.min(b[2], cube.minZ);
			b[3] = Math.max(b[3], cube.maxX);
			b[4] = Math.max(b[4], cube.maxY);
			b[5] = Math.max(b[5], cube.maxZ);
		});
		if (b[0] > b[3]) {
			// No cubes of its own (a model that hides a part by emptying it): a nub.
			System.arraycopy(new float[] {-1, -1, -1, 1, 1, 1}, 0, b, 0, 6);
		}
		return new HumanoidRagdoll.Part(pose.x(), pose.y(), pose.z(), b[0], b[1], b[2], b[3], b[4], b[5]);
	}

	/** The renderer's own scale, read off what its {@code scale} call does to an empty stack. */
	private static float modelScale(LivingEntityRenderer<?, ?, ?> renderer) {
		try {
			PoseStack stack = new PoseStack();
			((dev.captience.realisticdamage.client.mixin.LivingEntityRendererAccess) renderer)
					.realisticdamage$scale(renderer.createRenderState(), stack);
			float s = stack.last().pose().getScale(new Vector3f()).x;
			return s > 0.01F && s < 100.0F ? s : 1.0F;
		} catch (RuntimeException e) {
			// A renderer whose scale reads state that a blank one lacks.
			return 1.0F;
		}
	}

	/**
	 * The parts' rotations in the body's last moment, the way vanilla's
	 * {@code HumanoidModel.setupAnim} would have posed them: head where it was
	 * looking, arms and legs where the walk cycle had them. {@code back} is how
	 * far to wind the walk back, for the pose a tick earlier.
	 */
	private static float[] deathPose(LivingEntity entity, float partialTicks, float back, float stride) {
		float[] r = new float[18];
		float pos = entity.walkAnimation.position(partialTicks) - back;
		float speed = Math.max(0.0F, Math.min(1.0F, stride));
		r[HumanoidRagdoll.HEAD * 3] = Mth.clamp(entity.getXRot(), -50.0F, 45.0F) * Mth.DEG_TO_RAD;
		r[HumanoidRagdoll.HEAD * 3 + 1] = Mth.clamp(Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot), -70.0F, 70.0F)
				* Mth.DEG_TO_RAD;
		r[HumanoidRagdoll.RIGHT_ARM * 3] = Mth.cos(pos * 0.6662F + Mth.PI) * speed;
		r[HumanoidRagdoll.LEFT_ARM * 3] = Mth.cos(pos * 0.6662F) * speed;
		r[HumanoidRagdoll.RIGHT_ARM * 3 + 2] = 0.06F;
		r[HumanoidRagdoll.LEFT_ARM * 3 + 2] = -0.06F;
		r[HumanoidRagdoll.RIGHT_LEG * 3] = Mth.cos(pos * 0.6662F) * 1.4F * speed;
		r[HumanoidRagdoll.LEFT_LEG * 3] = Mth.cos(pos * 0.6662F + Mth.PI) * 1.4F * speed;
		r[HumanoidRagdoll.RIGHT_LEG * 3 + 2] = 0.005F;
		r[HumanoidRagdoll.LEFT_LEG * 3 + 2] = -0.005F;
		return r;
	}

	/**
	 * How it died, turned into motion: the server's launch for the whole body,
	 * the killing blow's kick on the region it landed on, a nudge over for a
	 * death that pushed nothing, and the stiffness its kind of death leaves.
	 */
	private static void launch(HumanoidRagdoll ragdoll, LivingEntity entity, CorpsePayload payload,
			Vector3d feet, Vector3d seen, Muscles muscles) {
		double violence = Mth.clamp(payload.violence(), 0.0F, 1.0F);
		// What this client already saw it doing is in the inherited motion; the
		// server's launch (a blast, a knockback this client has not applied yet)
		// adds whatever is beyond that.
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
		double yaw = Math.toRadians(entity.yBodyRot);
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

	/** Which open wound a dying hand goes to, most serious region first; -1 for none. */
	private static final BodyPart[] WOUND_ORDER = {BodyPart.TORSO, BodyPart.HEAD, BodyPart.RIGHT_LEG,
			BodyPart.LEFT_LEG, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM};

	/** What the reflexes need: the kind of death, where it struck, the worst wound, the push. */
	private static Behaviour.Input behaviourInput(LivingEntity entity, CorpsePayload payload,
			@Nullable BodySnapshot snapshot, Muscles muscles) {
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
		return new Behaviour.Input(payload.kind(), payload.violence(), hit, wound, muscles, push, entity.getId());
	}

	/** What the body knew about itself as it died, from its synced snapshot, in ragdoll part order. */
	private static Muscles.Input musclesInput(CorpsePayload payload, @Nullable BodySnapshot snapshot) {
		int hit = payload.hitPart() >= 0 && payload.hitPart() < FROM_BODY_PART.length
				? FROM_BODY_PART[payload.hitPart()] : -1;
		if (snapshot == null) {
			return Muscles.Input.healthy(payload.kind(), payload.violence(), hit);
		}
		boolean[] broken = new boolean[HumanoidRagdoll.PARTS];
		boolean[] limp = new boolean[HumanoidRagdoll.PARTS];
		int rungs = LimbStateExtractor.limbRungs(snapshot);
		for (BodyPart part : BodyPart.values()) {
			int i = FROM_BODY_PART[part.ordinal()];
			broken[i] = snapshot.broken(part);
			limp[i] = LimpMath.rungAt(rungs, part) == LimpMath.RUNG_RUINED;
		}
		AdrenalineMath.Phase adrenaline = snapshot.adrenaline();
		return new Muscles.Input(payload.kind(), payload.violence(), hit, snapshot.consciousness(),
				snapshot.unconscious(), broken, limp, snapshot.concussionGrade(),
				adrenaline == AdrenalineMath.Phase.SURGE, adrenaline == AdrenalineMath.Phase.CRASH);
	}

	/** Once per client tick. */
	void tick(double timeScale) {
		age++;
		if (orphanAge >= 0) {
			orphanAge++;
		}
		if (!body.broken()) {
			body.tick(world, DT * timeScale);
		}
		// The thud: the torso stopping hard against something. Sound and floor
		// dust, as the old plank had (RemoteCorpses.thud).
		double fall = -body.body(HumanoidRagdoll.TORSO).v.y;
		sinceThud++;
		if (lastFall > THUD_SPEED && fall < lastFall * 0.35 && sinceThud > 6) {
			Vector3d c = body.centre();
			var hit = level.clip(new net.minecraft.world.level.ClipContext(new Vec3(c.x, c.y, c.z),
					new Vec3(c.x, c.y - 0.8, c.z), net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				RemoteCorpses.thud(level, hit, lastFall / 20.0);
				sinceThud = 0;
			}
		}
		lastFall = fall;
		if (TRACE && (age <= 40 || age % 5 == 0) && age <= 80) {
			System.out.printf(java.util.Locale.ROOT,
					"[rd] ragdoll %d t%02d lean %.1f spread %.2f gap %.3f motion %.2f y %.2f asleep %s%n",
					id, age, body.lean(), body.spread(), body.worstSeparation(), body.sim.maxMotion(), body.centre().y,
					body.asleep());
		}
	}

	private static final boolean TRACE = System.getenv("RD_RAGDOLL_TRACE") != null;

	boolean expired() {
		return body.broken();
	}

	// --- RemoteCorpses.View ---------------------------------------------------------

	@Override
	public Vec3 place(LivingEntity entity, Vec3 upright) {
		Vector3d p = body.place(new Vector3d(upright.x, upright.y, upright.z));
		return new Vec3(p.x, p.y, p.z);
	}

	@Override
	public Vec3 position() {
		Vector3d c = body.centre();
		return new Vec3(c.x, c.y, c.z);
	}

	@Override
	public double angle() {
		return body.lean();
	}

	@Override
	public double limbSpread() {
		return body.spread();
	}

	boolean red() {
		return age < RED_TICKS;
	}

	// --- Drawing ---------------------------------------------------------------------

	/**
	 * This frame of the body: the torso as the model's root, every other part
	 * relative to it, in the model's own terms (pixels and ZYX angles).
	 */
	Frame frame(float partialTicks) {
		Quaterniond torsoQ = body.orientation(HumanoidRagdoll.TORSO, partialTicks, new Quaterniond());
		Vector3d torsoPivot = body.pivot(HumanoidRagdoll.TORSO, partialTicks, new Vector3d());
		Quaterniond inverse = new Quaterniond(torsoQ).conjugate();
		float[] parts = new float[HumanoidRagdoll.PARTS * 6];
		Quaterniond q = new Quaterniond();
		Vector3d p = new Vector3d();
		Vector3f euler = new Vector3f();
		for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
			if (i == HumanoidRagdoll.TORSO) {
				continue;
			}
			body.pivot(i, partialTicks, p).sub(torsoPivot);
			inverse.transform(p).div(body.unit);
			inverse.mul(body.orientation(i, partialTicks, q), q);
			new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w).getEulerAnglesZYX(euler);
			int o = i * 6;
			parts[o] = (float) p.x;
			parts[o + 1] = (float) p.y;
			parts[o + 2] = (float) p.z;
			parts[o + 3] = euler.x;
			parts[o + 4] = euler.y;
			parts[o + 5] = euler.z;
		}
		// The model's root turn: the torso, undoing vanilla's Rz(180) that follows.
		Quaternionf root = new Quaternionf((float) torsoQ.x, (float) torsoQ.y, (float) torsoQ.z, (float) torsoQ.w)
				.rotateZ(Mth.PI);
		return new Frame(new Vec3(torsoPivot.x, torsoPivot.y, torsoPivot.z), root, modelScale, parts);
	}

	/**
	 * One frame of a ragdoll: where the torso's pivot is (the render state's
	 * position), how the model root turns, and each part's offset and angles.
	 */
	record Frame(Vec3 root, Quaternionf rotation, float modelScale, float[] parts) implements RemoteCorpses.Frame {
		@Override
		public void applyRotation(PoseStack poseStack) {
			// After this, vanilla does Rz(180), its renderer scale and a 1.501
			// drop; the drop is cancelled here, in the frame it will happen in,
			// so the model's origin lands on the torso's pivot.
			poseStack.mulPose(rotation);
			poseStack.translate(0.0F, -1.501F * modelScale, 0.0F);
		}

		@Override
		public void pose(Model<?> model) {
			if (!(model instanceof HumanoidModel<?> humanoid)) {
				return;
			}
			humanoid.body.setPos(0.0F, 0.0F, 0.0F);
			humanoid.body.setRotation(0.0F, 0.0F, 0.0F);
			set(humanoid.head, HumanoidRagdoll.HEAD);
			set(humanoid.rightArm, HumanoidRagdoll.RIGHT_ARM);
			set(humanoid.leftArm, HumanoidRagdoll.LEFT_ARM);
			set(humanoid.rightLeg, HumanoidRagdoll.RIGHT_LEG);
			set(humanoid.leftLeg, HumanoidRagdoll.LEFT_LEG);
		}

		private void set(ModelPart part, int index) {
			int o = index * 6;
			part.setPos(parts[o], parts[o + 1], parts[o + 2]);
			part.setRotation(parts[o + 3], parts[o + 4], parts[o + 5]);
		}
	}
}
