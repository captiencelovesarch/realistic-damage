package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.ragdoll.GenericRagdoll;
import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.systems.MobAnatomy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A body that is not a humanoid, as a ragdoll (active ragdolls, phase 4): a
 * cow, a pig, a wolf, a spider, a chicken, a creeper, a villager, an iron
 * golem. Built from the model that draws it: every top-level part becomes a
 * box ({@link GenericRagdoll}), parts that share a pivot and a rest angle (a
 * chicken's head, beak and wattle) one box between them.
 *
 * <p>Four legs and more keel over onto the side; the upright ones topple the
 * way they were pushed. Bosses, and bodies that would make no sense as jointed
 * boxes (a slime, a fish, a ghast, a guardian and its spikes), keep vanilla's
 * death.
 */
final class GenericCorpse extends SimulatedCorpse {
	private static final double DT = 0.05;
	/** Kilograms a cubic pixel: a cow comes out about sixty. */
	private static final double DENSITY = 0.05;
	private static final int MAX_PARTS = 14;
	private static final double TELEPORT_SPEED = 25.0;
	private static final double MAX_INHERITED_SPEED = 9.0;

	final GenericRagdoll body;
	/** Each body's model parts, by name. */
	private final List<List<String>> names;
	private final float modelScale;
	private final McPhysicsWorld world;

	private GenericCorpse(LivingEntity entity, GenericRagdoll body, List<List<String>> names, float modelScale,
			ClientLevel level) {
		super(entity);
		this.body = body;
		this.names = names;
		this.modelScale = modelScale;
		this.world = new McPhysicsWorld(level);
	}

	/** The shapes this rig is for; anything else keeps vanilla's death. */
	private static boolean shaped(BodyPlan plan) {
		return plan == BodyPlan.QUADRUPED || plan == BodyPlan.ARTHROPOD || plan == BodyPlan.AVIAN
				|| plan == BodyPlan.UPRIGHT || plan == BodyPlan.HUMANOID || plan == BodyPlan.STRIDER;
	}

	/** Lies on its side to die: four legs or more, or wings and two legs. */
	private static boolean keels(BodyPlan plan) {
		return plan == BodyPlan.QUADRUPED || plan == BodyPlan.ARTHROPOD || plan == BodyPlan.AVIAN
				|| plan == BodyPlan.UPRIGHT;
	}

	static @Nullable GenericCorpse create(LivingEntity entity, CorpsePayload payload, ClientLevel level) {
		BodyPlan plan = MobAnatomy.plan(entity);
		if (!shaped(plan) || MobAnatomy.boss(entity)) {
			return null;
		}
		EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
		if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)) {
			return null;
		}
		Model<?> model = living.getModel();
		if (model instanceof HumanoidModel<?>) {
			return null;
		}
		ModelPart root = model.root();
		// Measured from rest: the shared model holds whatever was posed last, and
		// is posed afresh before the next draw anyway.
		root.getAllParts().forEach(ModelPart::resetPose);
		Map<String, ModelPart> top = new LinkedHashMap<>();
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			if (path.length() > 1) {
				String name = path.substring(1).split("/", 2)[0];
				if (!top.containsKey(name) && root.hasChild(name)) {
					top.put(name, root.getChild(name));
				}
			}
		});
		// Parts that share a pivot and a rest angle move as one.
		List<GenericRagdoll.Part> parts = new ArrayList<>();
		List<List<String>> names = new ArrayList<>();
		List<PartPose> poses = new ArrayList<>();
		List<double[]> boxes = new ArrayList<>();
		for (Map.Entry<String, ModelPart> e : top.entrySet()) {
			ModelPart part = e.getValue();
			PartPose rest = part.getInitialPose();
			double[] box = localBox(part, rest);
			if (box == null) {
				continue;
			}
			int same = -1;
			for (int i = 0; i < poses.size(); i++) {
				if (samePose(poses.get(i), rest)) {
					same = i;
					break;
				}
			}
			if (same >= 0) {
				double[] b = boxes.get(same);
				for (int k = 0; k < 3; k++) {
					b[k] = Math.min(b[k], box[k]);
					b[k + 3] = Math.max(b[k + 3], box[k + 3]);
				}
				names.get(same).add(e.getKey());
			} else {
				poses.add(rest);
				boxes.add(box);
				List<String> group = new ArrayList<>();
				group.add(e.getKey());
				names.add(group);
			}
		}
		if (poses.size() < 2 || poses.size() > MAX_PARTS) {
			return null;
		}
		for (int i = 0; i < poses.size(); i++) {
			PartPose p = poses.get(i);
			double[] b = boxes.get(i);
			parts.add(new GenericRagdoll.Part(p.x(), p.y(), p.z(), p.xRot(), p.yRot(), p.zRot(),
					b[0], b[1], b[2], b[3], b[4], b[5]));
		}
		float modelScale = RagdollCorpse.modelScaleOf(living);
		double unit = entity.getScale() * modelScale / 16.0;
		Quaterniond qm = HumanoidRagdoll.modelRotation(entity.yBodyRot);
		Vector3d feet = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
		Vector3d origin = qm.transform(new Vector3d(0.0, -24.016 * unit, 0.0)).add(feet);
		GenericRagdoll ragdoll = new GenericRagdoll(parts, origin, qm, unit, DENSITY);
		launch(ragdoll, entity, payload, plan, feet, names);
		return new GenericCorpse(entity, ragdoll, names, modelScale, level);
	}

	private static boolean samePose(PartPose a, PartPose b) {
		return Math.abs(a.x() - b.x()) < 1.0e-4 && Math.abs(a.y() - b.y()) < 1.0e-4 && Math.abs(a.z() - b.z()) < 1.0e-4
				&& Math.abs(a.xRot() - b.xRot()) < 1.0e-4 && Math.abs(a.yRot() - b.yRot()) < 1.0e-4
				&& Math.abs(a.zRot() - b.zRot()) < 1.0e-4;
	}

	/**
	 * The box around everything a top-level part draws (its children included),
	 * in its own frame, pixels; null when it draws nothing.
	 */
	private static double @Nullable [] localBox(ModelPart part, PartPose rest) {
		double[] b = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		part.loadPose(PartPose.ZERO);
		try {
			part.visit(new PoseStack(), (pose, path, index, cube) -> {
				Matrix4f m = pose.pose();
				for (ModelPart.Polygon polygon : cube.polygons) {
					for (ModelPart.Vertex v : polygon.vertices()) {
						Vector3f p = m.transformPosition(v.worldX(), v.worldY(), v.worldZ(), new Vector3f());
						b[0] = Math.min(b[0], p.x * 16.0);
						b[1] = Math.min(b[1], p.y * 16.0);
						b[2] = Math.min(b[2], p.z * 16.0);
						b[3] = Math.max(b[3], p.x * 16.0);
						b[4] = Math.max(b[4], p.y * 16.0);
						b[5] = Math.max(b[5], p.z * 16.0);
					}
				}
			});
		} finally {
			part.loadPose(rest);
		}
		return b[0] > b[3] ? null : b;
	}

	/** The server's launch, the blow's kick, and the fall: onto its side, or over the way it was pushed. */
	private static void launch(GenericRagdoll ragdoll, LivingEntity entity, CorpsePayload payload, BodyPlan plan,
			Vector3d feet, List<List<String>> names) {
		Vector3d seen = new Vector3d(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo)
				.div(DT);
		if (seen.length() > TELEPORT_SPEED) {
			seen.zero();
		} else if (seen.length() > MAX_INHERITED_SPEED) {
			seen.normalize(MAX_INHERITED_SPEED);
		}
		Vector3d sent = new Vector3d(payload.vx(), payload.vy(), payload.vz()).mul(20.0);
		Vector3d moving = sent.lengthSquared() > seen.lengthSquared() ? sent : seen;
		ragdoll.push(moving.x, moving.y, moving.z);
		if (payload.kick() > 0.0F) {
			int part = ragdoll.torso;
			if (payload.hitPart() == 0) {
				for (int i = 0; i < names.size(); i++) {
					if (names.get(i).contains("head")) {
						part = i;
						break;
					}
				}
			}
			double k = payload.kick() * 20.0;
			ragdoll.kick(part, payload.hx() * k, payload.hy() * k, payload.hz() * k);
		}
		double yaw = Math.toRadians(entity.yBodyRot);
		Vector3d forward = new Vector3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vector3d flat = new Vector3d(moving.x, 0.0, moving.z);
		if (keels(plan)) {
			// Onto the side the push sends it, or the side its position picks.
			Vector3d right = new Vector3d(forward).cross(0.0, 1.0, 0.0);
			double side = flat.lengthSquared() > 1.0e-4 ? Math.signum(flat.dot(right))
					: (((int) Math.floor(feet.x) + (int) Math.floor(feet.z)) & 1) == 0 ? 1.0 : -1.0;
			if (side == 0.0) {
				side = 1.0;
			}
			// Falling toward d is a roll about up x d.
			Vector3d toward = new Vector3d(right).mul(side);
			ragdoll.keel(new Vector3d(0.0, 1.0, 0.0).cross(toward), feet);
		} else {
			Vector3d dir = flat.length() > 0.8 ? new Vector3d(flat).normalize() : forward;
			double rate = payload.kind() == DeathKind.EXPLOSION ? 0.6 : 1.6;
			ragdoll.spin(new Vector3d(dir.z, 0.0, -dir.x).mul(rate), feet);
		}
	}

	@Override
	void tick(double timeScale) {
		age++;
		if (orphanAge >= 0) {
			orphanAge++;
		}
		if (!body.broken()) {
			body.tick(world, DT * timeScale);
		}
	}

	@Override
	boolean expired() {
		return body.broken();
	}

	@Override
	Frame frame(float partialTicks) {
		Quaterniond q = body.modelRotation(partialTicks, new Quaterniond());
		Vector3d origin = body.modelOrigin(partialTicks, q, new Vector3d());
		Quaterniond inverse = new Quaterniond(q).conjugate();
		float[] poses = new float[body.parts() * 6];
		Quaterniond part = new Quaterniond();
		Vector3d p = new Vector3d();
		Vector3f euler = new Vector3f();
		for (int i = 0; i < body.parts(); i++) {
			body.partOrigin(i, partialTicks, p).sub(origin);
			inverse.transform(p).div(body.unit);
			inverse.mul(body.orientation(i, partialTicks, part), part);
			new Quaternionf((float) part.x, (float) part.y, (float) part.z, (float) part.w).getEulerAnglesZYX(euler);
			int o = i * 6;
			poses[o] = (float) p.x;
			poses[o + 1] = (float) p.y;
			poses[o + 2] = (float) p.z;
			poses[o + 3] = euler.x;
			poses[o + 4] = euler.y;
			poses[o + 5] = euler.z;
		}
		Quaternionf rotation = new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w).rotateZ(Mth.PI);
		return new Frame(new Vec3(origin.x, origin.y, origin.z), rotation, modelScale, names, poses);
	}

	/** One frame: the model's root and each named part's pose, as {@link RagdollCorpse.Frame} does for a humanoid. */
	record Frame(Vec3 root, Quaternionf rotation, float modelScale, List<List<String>> names, float[] poses)
			implements RemoteCorpses.Frame {
		@Override
		public void applyRotation(PoseStack poseStack) {
			poseStack.mulPose(rotation);
			poseStack.translate(0.0F, -1.501F * modelScale, 0.0F);
		}

		@Override
		public void pose(Model<?> model) {
			ModelPart root = model.root();
			for (int i = 0; i < names.size(); i++) {
				int o = i * 6;
				for (String name : names.get(i)) {
					if (root.hasChild(name)) {
						ModelPart part = root.getChild(name);
						part.setPos(poses[o], poses[o + 1], poses[o + 2]);
						part.setRotation(poses[o + 3], poses[o + 4], poses[o + 5]);
					}
				}
			}
		}
	}

	// --- RemoteCorpses.View and the shared bits -----------------------------------------

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

	@Override
	Iterable<Body> bodies() {
		List<Body> all = new ArrayList<>();
		for (int i = 0; i < body.parts(); i++) {
			all.add(body.body(i));
		}
		return all;
	}

	@Override
	boolean asleep() {
		return body.asleep();
	}

	@Override
	double gap() {
		return body.worstSeparation();
	}

	@Override
	Vector3d centreOf() {
		return body.centre();
	}
}
