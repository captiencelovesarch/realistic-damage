package dev.captience.realisticdamage.fieldkit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.LimbMeshes;
import dev.captience.realisticdamage.client.compat.Vr;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * An arm in a sling (items.7): how it is held on the body, and how it looks
 * from behind your own eyes.
 *
 * <p>A Minecraft arm has no elbow, so "the forearm across the belly" is the
 * whole arm angled forward and in, its hand end in front of the stomach, where
 * the sling's pouch holds it. From first person that arm is low across the
 * bottom of the view, wrapped in the pouch, where a glance down would find it.
 */
public final class SlungArm {
	private SlungArm() {}

	/** Forward of hanging, radians: the hand end comes up in front of the stomach. */
	static final float FORWARD = -0.62F;
	/** Turned in across the body, radians. */
	static final float ACROSS = 0.5F;

	/**
	 * The first-person placement, in view blocks and degrees: where the
	 * shoulder sits (off the bottom corner of the view), how far the arm turns
	 * forward from lying straight across, and how far it is rolled about its
	 * own length to show its upper side. Public so the client gametest can
	 * judge alternatives.
	 */
	public static float fpX = 0.4F;
	public static float fpY = -0.24F;
	public static float fpZ = -0.7F;
	public static float fpForward = 25.0F;
	public static float fpTwist = 30.0F;

	/** Draws for the client gametest. */
	private static long firstPersonDraws;

	public static long firstPersonDraws() {
		return firstPersonDraws;
	}

	/** The third-person pose: forward, across, and leaning with the body when it bends. */
	public static void pose(ModelPart arm, float bodyLean, boolean right) {
		if (!arm.visible) {
			return;
		}
		arm.xRot = FORWARD + bodyLean;
		arm.yRot = right ? -ACROSS : ACROSS;
		arm.zRot = 0.0F;
	}

	/** Whether this player's arm is in a sling, to draw (whatever Medicine says: it is still on). */
	public static boolean slung(AbstractClientPlayer player, HumanoidArm arm) {
		if (Vr.active()) {
			return false;
		}
		Dressings.State state = Dressings.peek(player);
		return state != null && state.slings.contains(arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM);
	}

	/** The slung arm from behind your own eyes: low across the view, in its pouch, holding nothing. */
	public static void firstPerson(AbstractClientPlayer player, HumanoidArm arm, float inverseArmHeight,
			PoseStack poseStack, SubmitNodeCollector collector, int light) {
		boolean right = arm == HumanoidArm.RIGHT;
		float invert = right ? 1.0F : -1.0F;
		poseStack.pushPose();
		// The shoulder off the bottom corner of the view, the arm lying across
		// toward the middle and a little forward (an arm's length is its
		// model +y: rolled a quarter turn it points across), turned on its
		// length to show its upper side.
		// Not lowered by the swap or the attack cooldown, as a wielding hand is:
		// it holds nothing and swings nothing.
		poseStack.translate(invert * fpX, fpY, fpZ);
		poseStack.mulPose(Axis.YP.rotationDegrees(-invert * fpForward));
		poseStack.mulPose(Axis.ZP.rotationDegrees(invert * 90.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(invert * fpTwist));
		// renderHand puts the arm at its shoulder pivot: undo that, so the
		// shoulder is the point above.
		poseStack.translate(invert * 5.0F / 16.0F, -2.0F / 16.0F, 0.0F);
		AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher()
				.getPlayerRenderer(player);
		Identifier skin = player.getSkin().body().texturePath();
		ModelPart armPart;
		if (right) {
			renderer.renderRightHand(poseStack, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
			armPart = renderer.getModel().rightArm;
		} else {
			renderer.renderLeftHand(poseStack, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
			armPart = renderer.getModel().leftArm;
		}
		LimbMeshes.Kind kind = player.getSkin().model() == PlayerModelType.SLIM ? LimbMeshes.Kind.SLIM : LimbMeshes.Kind.WIDE;
		ModelPart pouch = GearMeshes.of(kind).pouch.get(right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM);
		if (pouch != null) {
			poseStack.pushPose();
			armPart.translateAndRotate(poseStack);
			collector.submitModelPart(pouch, poseStack, RenderTypes.entityCutout(GearMeshes.TEXTURE), light,
					OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
		poseStack.popPose();
		firstPersonDraws++;
	}
}
