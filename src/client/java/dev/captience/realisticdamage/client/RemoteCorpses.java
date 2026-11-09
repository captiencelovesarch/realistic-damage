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

	/** Ragdolls (active ragdolls phase 0) by entity id; see {@link RagdollCorpse}. */
	private static final Int2ObjectOpenHashMap<SimulatedCorpse> RAGDOLLS = new Int2ObjectOpenHashMap<>();

	/** Farther than this from the camera, blocks, a body falls the simple way: nobody can tell. */
	private static final double RAGDOLL_RANGE = 64.0;

	/** The ragdoll clock, for slow-motion tuning ({@code /rdragdoll slowmo}); 1 is real time. */
	static double timeScale = 1.0;

	/** The packet: start simulating that body, if this client can see it and it is a shape we know. */
	public static void start(CorpsePayload payload) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		ClientEffectsConfig config = ClientEffectsConfig.get();
		if (level == null || config.ragdolls == ClientEffectsConfig.RagdollMode.OFF) {
			return;
		}
		Entity entity = level.getEntity(payload.entityId());
		if (!(entity instanceof LivingEntity body)) {
			return;
		}
		boolean own = body == client.player;
		if (own && !(config.ownRagdoll && config.ragdolls == ClientEffectsConfig.RagdollMode.FULL
				&& DeathCamera.enabled() && !dev.captience.realisticdamage.client.compat.Vr.active())) {
			// Your own death: the classic death camera, or vanilla.
			return;
		}
		CORPSES.remove(body.getId());
		RAGDOLLS.remove(body.getId());
		// Knocked down and now dead where it lies: the same ragdoll, so nothing jumps.
		RagdollCorpse taken = LiveRagdolls.takeForDeath(body, payload);
		if (taken != null) {
			RAGDOLLS.put(body.getId(), taken);
			return;
		}
		if (config.ragdolls == ClientEffectsConfig.RagdollMode.FULL && (own || RAGDOLLS.size() < config.ragdollLimit
				&& client.gameRenderer.mainCamera().position().distanceTo(body.position()) < RAGDOLL_RANGE)) {
			RagdollCorpse ragdoll = RagdollCorpse.create(body, payload, level);
			if (ragdoll != null) {
				RAGDOLLS.put(body.getId(), ragdoll);
				return;
			}
			// Not a humanoid: a cow, a spider, a chicken, built from its own model (phase 4).
			GenericCorpse generic = own ? null : GenericCorpse.create(body, payload, level);
			if (generic != null) {
				RAGDOLLS.put(body.getId(), generic);
				return;
			}
		}
		if (own) {
			return;
		}
		CORPSES.put(body.getId(), new Corpse(body, payload));
	}

	/** Once per client tick. */
	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			CORPSES.clear();
			RAGDOLLS.clear();
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
		if (level != lastLevel) {
			// A new world or dimension: nothing lying in the old one is here.
			RAGDOLLS.clear();
			ORPHANS.clear();
			LiveRagdolls.clear();
			lastLevel = level;
		}
		LiveRagdolls.tick(level, timeScale);
		Flinches.tick();
		int stay = Math.max(0, ClientEffectsConfig.get().bodiesStay) * 20;
		var rit = RAGDOLLS.int2ObjectEntrySet().fastIterator();
		while (rit.hasNext()) {
			SimulatedCorpse ragdoll = rit.next().getValue();
			Entity entity = level.getEntity(ragdoll.id);
			boolean still = entity instanceof LivingEntity body && entity == ragdoll.entity.get() && !body.isRemoved()
					&& body.isDeadOrDying();
			if (ragdoll.expired()) {
				rit.remove();
				continue;
			}
			if (!still) {
				rit.remove();
				// The server is done with it (or a respawn took its id). Kept here,
				// lying where it fell, if this client wants bodies to stay.
				if (ragdoll.lastState != null && ragdoll.age < stay) {
					ragdoll.orphanAge = 0;
					ORPHANS.put(nextOrphan++, ragdoll);
					while (ORPHANS.size() > MAX_ORPHANS) {
						ORPHANS.removeFirst();
					}
				}
				continue;
			}
			ragdoll.tick(timeScale);
			if (entity == Minecraft.getInstance().player) {
				// Your own body is not drawn in first person, so nothing would
				// remember how it looked for the body you leave behind: ask for a
				// render state now and then (the extraction keeps it, see extract).
				captureOwn((LivingEntity) entity);
			}
		}
		var oit = ORPHANS.int2ObjectEntrySet().fastIterator();
		while (oit.hasNext()) {
			SimulatedCorpse ragdoll = oit.next().getValue();
			if (ragdoll.expired() || ragdoll.age >= stay + SINK_TICKS) {
				oit.remove();
				continue;
			}
			ragdoll.tick(timeScale);
		}
	}

	/** Your own ragdoll while you are dead, or null. */
	static @Nullable RagdollCorpse ownRagdoll(LivingEntity player) {
		SimulatedCorpse r = RAGDOLLS.isEmpty() ? null : RAGDOLLS.get(player.getId());
		return r instanceof RagdollCorpse ragdoll && r.entity.get() == player ? ragdoll : null;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void captureOwn(LivingEntity player) {
		var renderer = (net.minecraft.client.renderer.entity.EntityRenderer) Minecraft.getInstance()
				.getEntityRenderDispatcher().getRenderer(player);
		renderer.createRenderState(player, 1.0F);
	}

	/** Bodies whose entity is gone, kept by this client (phase 4), oldest first. */
	private static final it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<SimulatedCorpse> ORPHANS =
			new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<>();
	private static int nextOrphan;
	private static @Nullable ClientLevel lastLevel;
	/** Kept bodies at most; past this the oldest goes first. */
	private static final int MAX_ORPHANS = 64;
	/** How long a kept body takes to sink into the ground at the end, ticks. */
	private static final int SINK_TICKS = 40;
	/** How far it sinks, blocks: out of sight under the surface. */
	private static final double SINK_DEPTH = 0.7;
	private static long orphanDraws;

	/**
	 * After the frame's entities are gathered: the kept bodies, drawn as they
	 * last looked and posed where their ragdoll lies, sinking at the end.
	 */
	public static void extractOrphans(java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState> out,
			float partialTicks) {
		ClientLevel level = Minecraft.getInstance().level;
		if (ORPHANS.isEmpty() || level == null) {
			return;
		}
		int stay = Math.max(0, ClientEffectsConfig.get().bodiesStay) * 20;
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		for (SimulatedCorpse ragdoll : ORPHANS.values()) {
			LivingEntityRenderState state = ragdoll.lastState;
			if (state == null) {
				continue;
			}
			Frame frame = ragdoll.frame(partialTicks);
			double sinking = Mth.clamp((ragdoll.age + partialTicks - stay) / SINK_TICKS, 0.0, 1.0);
			state.x = frame.root().x;
			state.y = frame.root().y - SINK_DEPTH * sinking * sinking;
			state.z = frame.root().z;
			state.bodyRot = 180.0F;
			state.yRot = 0.0F;
			state.xRot = 0.0F;
			state.deathTime = 0.0F;
			state.hasRedOverlay = false;
			state.nameTag = null;
			state.scoreText = null;
			state.leashStates = null;
			state.displayFireAnimation = false;
			state.shadowRadius = 0.0F;
			state.shadowPieces.clear();
			state.distanceToCameraSq = camera.distanceToSqr(state.x, state.y, state.z);
			net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(frame.root());
			state.lightCoords = net.minecraft.util.LightCoordsUtil.pack(
					level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at),
					level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at));
			((CorpseHolder) state).realisticdamage$setCorpse(frame);
			out.add(state);
			orphanDraws++;
		}
	}

	/** For the client gametest: kept bodies, and how often one has been drawn. */
	public static int orphanCount() {
		return ORPHANS.size();
	}

	public static long orphanDraws() {
		return orphanDraws;
	}

	/**
	 * Whether this entity's body is a ragdoll this client will keep after the
	 * server removes it: then vanilla's poof of smoke where it died is wrong
	 * twice over (the body is not there, and it is not going).
	 */
	public static boolean keeping(Entity entity) {
		SimulatedCorpse r = RAGDOLLS.isEmpty() ? null : RAGDOLLS.get(entity.getId());
		return r != null && r.entity.get() == entity && ClientEffectsConfig.get().bodiesStay * 20 > r.age;
	}

	public static void clear() {
		CORPSES.clear();
		RAGDOLLS.clear();
		ORPHANS.clear();
		LiveRagdolls.clear();
		Flinches.clear();
	}

	/** How many bodies are being simulated, either way; for the gametest. */
	public static int count() {
		return CORPSES.size() + RAGDOLLS.size();
	}

	/** How many of them are full ragdolls; for the gametest. */
	public static int ragdollCount() {
		return RAGDOLLS.size();
	}

	/** The simulated body for this entity, or null. */
	public static @Nullable View of(Entity entity) {
		LiveRagdolls.Live live = LiveRagdolls.of(entity);
		if (live != null) {
			return live.ragdoll;
		}
		if (!RAGDOLLS.isEmpty()) {
			SimulatedCorpse ragdoll = RAGDOLLS.get(entity.getId());
			if (ragdoll != null && ragdoll.entity.get() == entity) {
				return ragdoll;
			}
		}
		return CORPSES.isEmpty() ? null : CORPSES.get(entity.getId());
	}

	/**
	 * For the client gametest: how a ragdoll is lying, as
	 * {deepest point inside a block, worst joint gap, lean in degrees, 1 if at
	 * rest, torso centre y}; null when this body is not a ragdoll.
	 */
	public static double @Nullable [] ragdollCheck(Entity entity) {
		SimulatedCorpse r = RAGDOLLS.get(entity.getId());
		ClientLevel level = Minecraft.getInstance().level;
		if (r == null || level == null) {
			return null;
		}
		double deepest = 0.0;
		dev.captience.realisticdamage.ragdoll.physics.BoxList boxes = new dev.captience.realisticdamage.ragdoll.physics.BoxList();
		McPhysicsWorld world = new McPhysicsWorld(level);
		org.joml.Vector3d p = new org.joml.Vector3d();
		for (var b : r.bodies()) {
			for (int k = 0; k < 8; k++) {
				p.set((k & 1) == 0 ? -b.hx : b.hx, (k & 2) == 0 ? -b.hy : b.hy, (k & 4) == 0 ? -b.hz : b.hz);
				b.q.transform(p).add(b.x);
				boxes.clear();
				world.collect(p.x - 0.01, p.y - 0.01, p.z - 0.01, p.x + 0.01, p.y + 0.01, p.z + 0.01, boxes);
				deepest = Math.max(deepest, boxes.deepest(p.x, p.y, p.z));
			}
		}
		return new double[] {deepest, r.gap(), r.angle(), r.asleep() ? 1.0 : 0.0, r.centreOf().y};
	}

	/** For the client gametest: whether this body is simulated by the any-model rig. */
	public static boolean generic(Entity entity) {
		return RAGDOLLS.get(entity.getId()) instanceof GenericCorpse;
	}

	/** For the client gametest: the reactions a ragdoll died with, or null. */
	public static java.util.@Nullable Set<dev.captience.realisticdamage.ragdoll.Behaviour.Reaction> ragdollReactions(
			Entity entity) {
		return RAGDOLLS.get(entity.getId()) instanceof RagdollCorpse r && r.body.behaviour() != null
				? r.body.behaviour().reactions() : null;
	}

	/** For the client gametest: the muscles a ragdoll died with, or null. */
	public static dev.captience.realisticdamage.ragdoll.@Nullable Muscles ragdollMuscles(Entity entity) {
		return RAGDOLLS.get(entity.getId()) instanceof RagdollCorpse r ? r.body.muscles() : null;
	}

	/** Every ragdoll, kept ones too, for the debug view. */
	static Iterable<SimulatedCorpse> ragdolls() {
		java.util.List<SimulatedCorpse> all = new java.util.ArrayList<>(RAGDOLLS.values());
		all.addAll(ORPHANS.values());
		for (RagdollCorpse r : LiveRagdolls.ragdolls()) {
			all.add(r);
		}
		return all;
	}

	/** What callers outside the simulation may ask a body: where it is and where its wounds are. */
	public interface View {
		/** Where a point on the standing body (as the server has it) is on this one. */
		Vec3 place(LivingEntity body, Vec3 upright);

		/** Its centre. */
		Vec3 position();

		/** Degrees from upright; 90 is lying. */
		double angle();

		/** How far the limbs are from rest, radians summed. */
		double limbSpread();
	}

	/** One frame of a simulated body, for the renderer: the root's turn and the parts' pose. */
	public interface Frame {
		/** Where the model's root is put in the world (a ragdoll's frame sets the render state there). */
		default Vec3 root() {
			return Vec3.ZERO;
		}

		/** From the end of {@code setupRotations}. */
		void applyRotation(PoseStack poseStack);

		/** After a model has been posed for this state, the last word on its parts. */
		void pose(net.minecraft.client.model.Model<?> model);
	}

	/**
	 * From the end of {@code extractRenderState}: puts the body where the
	 * simulation has it and swaps vanilla's roll for the simulation's.
	 */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		LiveRagdolls.Live live = state instanceof HumanoidRenderState ? LiveRagdolls.of(entity) : null;
		if (live != null) {
			// Knocked down (phase 4): the ragdoll, or part of the way back up from it.
			HumanoidRenderState humanoid = (HumanoidRenderState) state;
			RagdollCorpse.Frame frame = live.ragdoll.frame(partialTicks, new Vec3(state.x, state.y, state.z),
					state.bodyRot, live.blend(partialTicks));
			humanoid.swimAmount = 0.0F;
			humanoid.isFallFlying = false;
			humanoid.isVisuallySwimming = false;
			humanoid.isCrouching = false;
			state.isAutoSpinAttack = false;
			state.isUpsideDown = false;
			state.pose = net.minecraft.world.entity.Pose.STANDING;
			state.x = frame.root().x;
			state.y = frame.root().y;
			state.z = frame.root().z;
			state.bodyRot = 180.0F;
			state.yRot = 0.0F;
			state.xRot = 0.0F;
			state.walkAnimationSpeed *= 1.0F - frame.blend();
			state.deathTime = 0.0F;
			state.shadowRadius = 0.0F;
			state.shadowPieces.clear();
			((CorpseHolder) state).realisticdamage$setCorpse(frame);
			return;
		}
		if (CORPSES.isEmpty() && RAGDOLLS.isEmpty()) {
			((CorpseHolder) state).realisticdamage$setCorpse(null);
			return;
		}
		// Whatever the body was doing when it died (swimming, gliding, crouching,
		// asleep) must not add its own turn: the player renderer applies those
		// after the base rotations this hangs off, on top of the corpse's.
		boolean simulated = RAGDOLLS.containsKey(entity.getId()) || CORPSES.containsKey(entity.getId());
		if (simulated) {
			state.isAutoSpinAttack = false;
			state.isUpsideDown = false;
			state.pose = net.minecraft.world.entity.Pose.STANDING;
			if (state instanceof HumanoidRenderState humanoid) {
				humanoid.swimAmount = 0.0F;
				humanoid.isFallFlying = false;
				humanoid.isVisuallySwimming = false;
				humanoid.isCrouching = false;
			}
		}
		SimulatedCorpse ragdoll = RAGDOLLS.get(entity.getId());
		if (ragdoll != null && ragdoll.entity.get() != entity) {
			// A respawn reused the id: this is someone alive.
			ragdoll = null;
		}
		if (ragdoll != null) {
			Frame frame = ragdoll.frame(partialTicks);
			state.x = frame.root().x;
			state.y = frame.root().y;
			state.z = frame.root().z;
			// Facing 180 makes vanilla's yaw turn the identity; the frame's own turn
			// is the whole orientation.
			state.bodyRot = 180.0F;
			state.yRot = 0.0F;
			state.xRot = 0.0F;
			state.walkAnimationSpeed = 0.0F;
			state.deathTime = 0.0F;
			state.hasRedOverlay = ragdoll.red();
			state.nameTag = null;
			// Vanilla's shadow is worked out at the entity's own spot, which a
			// thrown body has left; a body on the ground does not need the blob.
			state.shadowRadius = 0.0F;
			state.shadowPieces.clear();
			((CorpseHolder) state).realisticdamage$setCorpse(frame);
			ragdoll.lastState = state;
			return;
		}
		Corpse corpse = state instanceof HumanoidRenderState ? CORPSES.get(entity.getId()) : null;
		if (corpse == null) {
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
				(float) corpse.halfHeight, corpse.limbs(partialTicks)));
	}

	/** From the end of {@code setupRotations}: the body's orientation about its own centre. */
	public static boolean applyRotation(LivingEntityRenderState state, PoseStack poseStack) {
		Frame frame = ((CorpseHolder) state).realisticdamage$corpse();
		if (frame == null) {
			return false;
		}
		frame.applyRotation(poseStack);
		return true;
	}

	/**
	 * What one frame of a simple corpse needs: its orientation about its centre,
	 * and its limb angles (see {@link LimbPhysics} for the order).
	 */
	public record Pose(Quaternionf rotation, float pivot, float[] limbs) implements Frame {
		@Override
		public void applyRotation(PoseStack poseStack) {
			poseStack.translate(0.0F, pivot, 0.0F);
			poseStack.mulPose(rotation);
			poseStack.translate(0.0F, -pivot, 0.0F);
		}

		/**
		 * The limbs of a body with nobody in it, as {@link LimbPhysics} left them.
		 * Set outright rather than added to: whatever vanilla had them doing (a
		 * swing, holding a bow) stopped when the player did.
		 */
		@Override
		public void pose(net.minecraft.client.model.Model<?> any) {
			if (!(any instanceof net.minecraft.client.model.HumanoidModel<?> model)) {
				return;
			}
			float[] l = limbs;
			model.head.xRot = l[LimbPhysics.HEAD_X];
			model.head.yRot = 0.0F;
			model.head.zRot = 0.0F;
			model.rightArm.xRot = l[LimbPhysics.R_ARM_X];
			model.rightArm.yRot = 0.0F;
			model.rightArm.zRot = l[LimbPhysics.R_ARM_Z];
			model.leftArm.xRot = l[LimbPhysics.L_ARM_X];
			model.leftArm.yRot = 0.0F;
			model.leftArm.zRot = l[LimbPhysics.L_ARM_Z];
			model.rightLeg.xRot = l[LimbPhysics.R_LEG_X];
			model.rightLeg.yRot = 0.0F;
			model.rightLeg.zRot = l[LimbPhysics.R_LEG_Z];
			model.leftLeg.xRot = l[LimbPhysics.L_LEG_X];
			model.leftLeg.yRot = 0.0F;
			model.leftLeg.zRot = l[LimbPhysics.L_LEG_Z];
		}
	}

	/** From the end of HumanoidModel setupAnim. */
	public static void limp(LivingEntityRenderState state, net.minecraft.client.model.HumanoidModel<?> model) {
		Frame frame = ((CorpseHolder) state).realisticdamage$corpse();
		if (frame != null) {
			frame.pose(model);
		}
	}

	/**
	 * After any model was posed for a render state, wherever that happened: at
	 * the deferred draw, and in the renderer before its layers. A subclass's
	 * {@code setupAnim} (a zombie's raised arms) runs after HumanoidModel's tail,
	 * so this is the only place the corpse's pose reliably has the last word.
	 */
	public static void pose(net.minecraft.client.model.Model<?> model, @Nullable Object state) {
		if (state instanceof CorpseHolder holder) {
			Frame frame = holder.realisticdamage$corpse();
			if (frame != null) {
				frame.pose(model);
			}
		}
	}

	/** One body. */
	public static final class Corpse implements View {
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
		final LimbPhysics limbs;

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
			} else {
				this.angVel = 1.5 + 4.0 * violence;
				this.toppling = true;
			}
			this.limbs = new LimbPhysics(id);
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

		float[] limbs(float partialTicks) {
			return limbs.at(partialTicks);
		}

		/** For the client gametest: how far the limbs are from rest, radians, summed. */
		public double limbSpread() {
			return limbs.spread();
		}

		/** A world vector in the model frame of the body as it lies now. */
		private Vec3 toModel(Vec3 v) {
			double t = Math.toRadians(180.0 - bodyYaw);
			Vec3 b = new Vec3(v.x * Math.cos(t) - v.z * Math.sin(t), v.y, v.x * Math.sin(t) + v.z * Math.cos(t));
			// Undo the corpse's own rotation about its body-frame axis.
			Vec3 k = new Vec3(axisX, 0.0, axisZ);
			double a = -Math.toRadians(angle);
			Vec3 rb = b.scale(Math.cos(a)).add(k.cross(b).scale(Math.sin(a)))
					.add(k.scale(k.dot(b) * (1.0 - Math.cos(a))));
			// Vanilla's scale(-1, -1, 1): model y points down the body.
			return new Vec3(-rb.x, -rb.y, rb.z);
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
			Vec3 velBefore = vel;
			double angVelBefore = angVel;

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

			// --- Limbs ---
			// What the body did this tick is what the limbs feel: gravity minus the
			// body's own acceleration. Free fall is weightless; a landing is a slam.
			Vec3 accel = vel.subtract(velBefore);
			Vec3 effective = new Vec3(0.0, -GRAVITY, 0.0).subtract(accel);
			double spin = Math.toRadians(angVel);
			double spinAccel = Math.toRadians(angVel - angVelBefore);
			Vec3 axisModel = new Vec3(-axisX, 0.0, axisZ);
			boolean lying = onGround && Math.abs(Math.sin(Math.toRadians(angle))) > 0.7;
			limbs.tick(toModel(effective), toModel(new Vec3(0.0, -1.0, 0.0)), axisModel, spin, spinAccel, lying);
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

	/**
	 * The corpse's limbs as pendulums on its joints (1.7 ragdoll). Each joint
	 * angle is a damped spring pulled toward hanging along the effective gravity
	 * at that joint (gravity, minus the body's acceleration, plus the
	 * centrifugal pull of a tumble), with a weak return to a relaxed rest pose,
	 * joint limits, and a floor the limbs cannot pass through once the body lies
	 * on it. So a body that simply topples keeps its arms nearly where they
	 * were and flops them once when it lands; a thrown one goes slack in the air
	 * and spreads out as it spins. No noise, no phases: every motion has a cause.
	 */
	static final class LimbPhysics {
		static final int HEAD_X = 0;
		static final int R_ARM_X = 1;
		static final int R_ARM_Z = 2;
		static final int L_ARM_X = 3;
		static final int L_ARM_Z = 4;
		static final int R_LEG_X = 5;
		static final int R_LEG_Z = 6;
		static final int L_LEG_X = 7;
		static final int L_LEG_Z = 8;
		private static final int N = 9;

		/** Joint limits, radians: x then z per limb (z is outward-positive for the right side). */
		private static final double[] MIN = {-0.7, -3.0, -0.15, -3.0, -2.6, -1.7, -0.1, -1.7, -1.0};
		private static final double[] MAX = {0.9, 1.0, 2.6, 1.0, 0.15, 0.6, 1.0, 0.6, 0.1};
		/** Where a slack body's limbs settle with nothing pulling them. */
		private static final double[] REST = {0.1, -0.1, 0.12, -0.05, -0.12, 0.0, 0.05, 0.0, -0.05};

		/** Pull toward hanging, per tick squared at one g. */
		private static final double HANG = 0.10;
		/** Pull toward rest (muscle tone gone, but joints are not hinges in a vacuum). */
		private static final double TONE = 0.04;
		/** Velocity kept per tick. */
		private static final double DAMPING = 0.26;
		/** How strongly the body's angular acceleration swings the limbs. */
		private static final double INERTIA = 0.8;
		/** Joints from the body's centre, model units along the spine (down positive). */
		private static final double SHOULDER = -0.4;
		private static final double HIP = 0.15;
		/** A floor-limited joint may sink this far into the side the body lies on. */
		private static final double FLOOR_SLACK = 0.12;

		private final double[] angle = new double[N];
		private final double[] prev = new double[N];
		private final double[] vel = new double[N];

		LimbPhysics(int seed) {
			System.arraycopy(REST, 0, angle, 0, N);
			// A little asymmetry by body, so two corpses do not lie identically.
			angle[R_ARM_X] += ((seed * 37) % 7 - 3) * 0.06;
			angle[L_ARM_X] += ((seed * 53) % 7 - 3) * 0.06;
			System.arraycopy(angle, 0, prev, 0, N);
		}

		float[] at(float partialTicks) {
			float[] out = new float[N];
			for (int i = 0; i < N; i++) {
				out[i] = (float) Mth.lerp(partialTicks, prev[i], angle[i]);
			}
			return out;
		}

		double spread() {
			double sum = 0.0;
			for (int i = 0; i < N; i++) {
				sum += Math.abs(angle[i] - REST[i]);
			}
			return sum;
		}

		/**
		 * @param gravity  effective gravity in the model frame, blocks per tick squared
		 * @param down     the true down in the model frame, unit (which side is on the floor)
		 * @param axis     the body's fall axis in the model frame, unit
		 * @param spin     the body's angular speed about it, radians per tick
		 * @param spinAccel its angular acceleration, radians per tick squared
		 * @param lying    the body is flat on the floor
		 */
		void tick(Vec3 gravity, Vec3 down, Vec3 axis, double spin, double spinAccel, boolean lying) {
			System.arraycopy(angle, 0, prev, 0, N);
			Vec3 armPull = gravity.add(centrifugal(axis, spin, SHOULDER));
			Vec3 legPull = gravity.add(centrifugal(axis, spin, HIP));
			if (lying) {
				// The floor carries whatever pushes into it.
				Vec3 n = new Vec3(0.0, 0.0, Math.signum(down.z));
				armPull = armPull.subtract(n.scale(armPull.dot(n)));
				legPull = legPull.subtract(n.scale(legPull.dot(n)));
			}
			swing(R_ARM_X, R_ARM_Z, armPull, axis, spinAccel);
			swing(L_ARM_X, L_ARM_Z, armPull, axis, spinAccel);
			swing(R_LEG_X, R_LEG_Z, legPull, axis, spinAccel);
			swing(L_LEG_X, L_LEG_Z, legPull, axis, spinAccel);
			// The head hangs the other way up: it sits above its joint, so gravity
			// toward the back tips the chin up.
			double headTarget = Mth.clamp(-Math.atan2(armPull.z, Math.max(Math.abs(armPull.y), 1.0e-4)) * 0.6,
					MIN[HEAD_X], MAX[HEAD_X]);
			double headWeight = Math.min(4.0, armPull.length() / GRAVITY);
			step(HEAD_X, headTarget, headWeight, -spinAccel * axis.x * INERTIA * 0.5);

			if (lying) {
				// Limbs cannot go through the floor on the side the body lies on.
				boolean onBack = down.z > 0.0;
				for (int i : new int[] {R_ARM_X, L_ARM_X, R_LEG_X, L_LEG_X}) {
					if (onBack && angle[i] > FLOOR_SLACK) {
						angle[i] = FLOOR_SLACK;
						vel[i] = Math.min(0.0, vel[i]) * -0.2;
					} else if (!onBack && angle[i] < -FLOOR_SLACK) {
						angle[i] = -FLOOR_SLACK;
						vel[i] = Math.max(0.0, vel[i]) * -0.2;
					}
				}
			}
		}

		/** Centrifugal pull at a joint on the spine, from spinning about the fall axis. */
		private static Vec3 centrifugal(Vec3 axis, double spin, double along) {
			Vec3 joint = new Vec3(0.0, along, 0.0);
			Vec3 perpendicular = joint.subtract(axis.scale(axis.dot(joint)));
			return perpendicular.scale(spin * spin);
		}

		private void swing(int xi, int zi, Vec3 pull, Vec3 axis, double spinAccel) {
			double weight = Math.min(4.0, pull.length() / GRAVITY);
			// Hang direction as an x swing in the y-z plane, then a z tilt out of it.
			double hangX = Math.atan2(pull.z, pull.y);
			double hangZ = Math.atan2(-pull.x, Math.hypot(pull.y, pull.z));
			// A body that starts or stops turning leaves its limbs turning.
			step(xi, hangX, weight, -spinAccel * axis.x * INERTIA);
			step(zi, hangZ, weight, -spinAccel * axis.z * INERTIA);
		}

		private void step(int i, double target, double weight, double drive) {
			double toHang = Mth.wrapDegrees(Math.toDegrees(target - angle[i])) * Mth.DEG_TO_RAD;
			vel[i] += HANG * weight * toHang + TONE * (REST[i] - angle[i]) + drive;
			vel[i] *= 1.0 - DAMPING;
			angle[i] += vel[i];
			if (angle[i] < MIN[i]) {
				angle[i] = MIN[i];
				vel[i] = Math.max(0.0, vel[i]) * 0.3;
			} else if (angle[i] > MAX[i]) {
				angle[i] = MAX[i];
				vel[i] = Math.min(0.0, vel[i]) * 0.3;
			}
		}
	}
}
