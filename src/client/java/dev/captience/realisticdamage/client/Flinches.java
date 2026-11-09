package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.net.FlinchPayload;
import dev.captience.realisticdamage.ragdoll.Flinch;
import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Hit and still standing: the flinch (active ragdolls, phase 5). The struck
 * part jerks with the blow and the body rocks away from it, on top of
 * whatever vanilla's animation is doing, and springs back inside half a second
 * ({@link Flinch}). Humanoid models only; nothing on a body that is a ragdoll.
 *
 * <p>The server says where the blow landed and which way it went
 * ({@link FlinchPayload}); every watching client plays it. Your own body shows
 * it in third person (first person has its own camera shake already).
 */
public final class Flinches {
	private Flinches() {}

	/** {@code BodyPart} ordinal to ragdoll part. */
	private static final int[] FROM_BODY_PART = {
			HumanoidRagdoll.HEAD, HumanoidRagdoll.TORSO,
			HumanoidRagdoll.LEFT_ARM, HumanoidRagdoll.RIGHT_ARM,
			HumanoidRagdoll.LEFT_LEG, HumanoidRagdoll.RIGHT_LEG,
	};
	/** The flinch is a rock about the hips: this high up the body, of its height. */
	private static final double HIP_HEIGHT = 0.42;

	private static final Int2ObjectOpenHashMap<Flinch> FLINCHES = new Int2ObjectOpenHashMap<>();
	private static long played;

	static boolean enabled() {
		return ClientEffectsConfig.get().hitFlinches;
	}

	public static void hit(FlinchPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !enabled() || payload.part() < 0 || payload.part() >= FROM_BODY_PART.length) {
			return;
		}
		Entity entity = level.getEntity(payload.entityId());
		if (!(entity instanceof LivingEntity body) || !body.isAlive()) {
			return;
		}
		FLINCHES.computeIfAbsent(body.getId(), id -> new Flinch()).hit(FROM_BODY_PART[payload.part()],
				new Vector3d(payload.hx(), payload.hy(), payload.hz()), payload.strength(), body.yBodyRot);
		played++;
	}

	static void tick() {
		var it = FLINCHES.int2ObjectEntrySet().fastIterator();
		while (it.hasNext()) {
			Flinch f = it.next().getValue();
			f.tick(0.05);
			if (f.done()) {
				it.remove();
			}
		}
	}

	static void clear() {
		FLINCHES.clear();
	}

	/**
	 * From the end of {@code extractRenderState}: this frame's flinch onto the
	 * state, the lean's axis turned into the body's own frame.
	 */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partial) {
		FlinchHolder holder = (FlinchHolder) state;
		Flinch f = FLINCHES.isEmpty() ? null : FLINCHES.get(entity.getId());
		if (f == null || ((CorpseHolder) state).realisticdamage$corpse() != null) {
			holder.realisticdamage$setFlinch(null);
			return;
		}
		float[] out = new float[Flinch.CHANNELS + 2];
		for (int i = 0; i < Flinch.CHANNELS; i++) {
			out[i] = (float) f.value(i, partial);
		}
		// Into the frame setupRotations leaves: turned by 180 - bodyRot about Y.
		double turn = Math.toRadians(180.0 - state.bodyRot);
		Vector3d axis = f.leanAxis();
		double c = Math.cos(-turn);
		double s = Math.sin(-turn);
		out[Flinch.CHANNELS] = (float) (axis.x * c + axis.z * s);
		out[Flinch.CHANNELS + 1] = (float) (-axis.x * s + axis.z * c);
		holder.realisticdamage$setFlinch(out);
	}

	/** From the end of {@code setupRotations}: the body rocks about its hips, and dips. */
	public static void applyRotation(LivingEntityRenderState state, PoseStack poseStack) {
		float[] f = ((FlinchHolder) state).realisticdamage$flinch();
		if (f == null) {
			return;
		}
		float hip = (float) (state.boundingBoxHeight * HIP_HEIGHT);
		poseStack.translate(0.0F, hip - f[Flinch.DIP] / 16.0F, 0.0F);
		poseStack.mulPose(new Quaternionf().rotationAxis(f[Flinch.LEAN], f[Flinch.CHANNELS], 0.0F,
				f[Flinch.CHANNELS + 1]));
		poseStack.translate(0.0F, -hip, 0.0F);
	}

	/** After the model is posed for the state: the struck parts on top of vanilla's pose. */
	public static void pose(Model<?> model, @Nullable Object state) {
		if (!(model instanceof HumanoidModel<?> humanoid) || !(state instanceof FlinchHolder holder)) {
			return;
		}
		float[] f = holder.realisticdamage$flinch();
		if (f == null) {
			return;
		}
		humanoid.head.xRot += f[Flinch.HEAD_X];
		humanoid.head.yRot += f[Flinch.HEAD_Y];
		humanoid.rightArm.xRot += f[Flinch.RIGHT_ARM_X];
		humanoid.rightArm.zRot += f[Flinch.RIGHT_ARM_Z];
		humanoid.leftArm.xRot += f[Flinch.LEFT_ARM_X];
		humanoid.leftArm.zRot += f[Flinch.LEFT_ARM_Z];
		humanoid.rightLeg.xRot += f[Flinch.RIGHT_LEG_X];
		humanoid.leftLeg.xRot += f[Flinch.LEFT_LEG_X];
	}

	/** For the client gametest: flinches playing now, and played since start. */
	public static int active() {
		return FLINCHES.size();
	}

	public static long played() {
		return played;
	}

	/** For the client gametest: how far this entity's struck arm is thrown now, radians. */
	public static double armSwing(Entity entity) {
		Flinch f = FLINCHES.get(entity.getId());
		return f == null ? 0.0 : Math.max(Math.abs(f.value(Flinch.RIGHT_ARM_X, 1.0F)),
				Math.abs(f.value(Flinch.LEFT_ARM_X, 1.0F)));
	}
}
