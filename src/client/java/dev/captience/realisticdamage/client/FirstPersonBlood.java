package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.ScarMask;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * Blood and scars on the arm you are looking down at (1.6.1).
 *
 * <p>{@link BloodBodyLayer} is a render layer on the player renderer, and the
 * first-person hand is not drawn through the player renderer's layers at all:
 * {@code ItemInHandRenderer} asks {@code AvatarRenderer.renderRightHand} to
 * submit the bare arm part and nothing else. So a player who looked down at a
 * bleeding arm saw a clean sleeve, while everyone else on the server saw the
 * blood — the one viewpoint the effect was most needed from was the one it
 * never reached. This paints the same shells, from the same baked meshes and
 * through the same {@link BloodBodyLayer#paint} call, onto the arm vanilla just
 * submitted, so the two views cannot disagree.
 *
 * <p>Deliberately stateless beyond its meshes: which arm this is, how bloody it
 * is and whether it carries a scar are all read off the local player at the
 * moment the hand is drawn, exactly as the third-person layer reads them off
 * the render state.
 */
public final class FirstPersonBlood {
	private FirstPersonBlood() {}

	private static @Nullable ModelPart[] wideBlood;
	private static @Nullable ModelPart[] slimBlood;
	private static @Nullable ModelPart[] wideScar;
	private static @Nullable ModelPart[] slimScar;

	private static @Nullable ModelPart[] wideStump;
	private static @Nullable ModelPart[] slimStump;
	private static @Nullable ModelPart[] wideCap;
	private static @Nullable ModelPart[] slimCap;
	private static @Nullable ModelPart[] wideStumpBlood;
	private static @Nullable ModelPart[] slimStumpBlood;

	/** Blood shells submitted onto the first-person arm this session; read by the client gametest. */
	private static long bloodSubmits;
	/** Stumps drawn in first person this session; read by the client gametest. */
	private static long stumpSubmits;

	public static long bloodSubmits() {
		return bloodSubmits;
	}

	public static long stumpSubmits() {
		return stumpSubmits;
	}

	/** True when the local player's arm on this side is gone, so the hand path draws a stump instead. */
	public static boolean armDestroyed(boolean rightArm) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		return snapshot != null && snapshot.destroyed(rightArm ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM);
	}

	/**
	 * The stump, in first person (1.6.2). Vanilla positions the arm so that the
	 * hand fills the corner of the screen and the shoulder sits behind the
	 * camera — which is exactly where a five-pixel stump would be, and so it was
	 * invisible and the arm "just disappeared". It is raised by the length it
	 * lost, so the cut end sits where the hand used to: the player holding what
	 * is left of the arm up in front of them, which is what people do.
	 */
	public static void submitStump(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			ModelPart arm, boolean rightArm, net.minecraft.resources.Identifier skin) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		BodyPart part = rightArm ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		ModelPart[] stumps = slim ? (slimStump == null ? slimStump = LimbMeshes.stumps(true, 0.0F) : slimStump)
				: (wideStump == null ? wideStump = LimbMeshes.stumps(false, 0.0F) : wideStump);
		ModelPart[] caps = slim ? (slimCap == null ? slimCap = LimbMeshes.stumpCaps(true) : slimCap)
				: (wideCap == null ? wideCap = LimbMeshes.stumpCaps(false) : wideCap);
		ModelPart[] shells = slim
				? (slimStumpBlood == null ? slimStumpBlood = LimbMeshes.stumps(true, BloodBodyLayer.SKIN_OFFSET) : slimStumpBlood)
				: (wideStumpBlood == null ? wideStumpBlood = LimbMeshes.stumps(false, BloodBodyLayer.SKIN_OFFSET) : wideStumpBlood);
		float[] stains = BloodBody.stains(player.getId());
		float level = stains == null ? 0.0F : stains[part.ordinal()];
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodBodyIntensity;
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		// Raised by most of what it lost, along the arm (model units are
		// sixteenths): the cut end sits three pixels short of where the hand
		// ended, so the stump's own skin shows beside the cut instead of the cut
		// alone filling the corner of the screen.
		poseStack.translate(0.0F, (12.0F - LimbMeshes.STUMP_LENGTH - 3.0F) / 16.0F, 0.0F);
		BloodBodyLayer.submitStump(collector, poseStack, skin, stumps[part.ordinal()], caps[part.ordinal()],
				shells[part.ordinal()], level, strength, lightCoords, OverlayTexture.NO_OVERLAY, BloodTint.RED);
		poseStack.popPose();
		stumpSubmits++;
	}

	/**
	 * Called from the tail of vanilla's first-person hand submit, with the arm
	 * part it just drew. The pose stack is positioned exactly where vanilla
	 * left it: at the arm's parent, with the arm's own offset and rotation
	 * still to be applied — which is what {@code translateAndRotate} does here,
	 * the same call the third-person layer makes.
	 */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			ModelPart arm, boolean rightArm) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodBodyIntensity;
		if (strength <= 0.0) {
			return;
		}
		BodyPart part = rightArm ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		float[] stains = BloodBody.stains(player.getId());
		float level = stains == null ? 0.0F : stains[part.ordinal()];
		BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		ScarLevel scar = ScarLevel.NONE;
		// The same two rules the layer applies: scars are their own switch, and a
		// bone that is broken right now is a condition, not history.
		if (config.showScars && snapshot != null && !snapshot.broken(part)) {
			scar = ScarMask.level(snapshot.scarMask(), part);
		}
		if (scar == ScarLevel.NONE && level < BloodBodyLayer.VISIBLE) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		ModelPart[] blood = bloodParts(slim);
		ModelPart[] scars = scarParts(slim);
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		BloodBodyLayer.paint(collector, poseStack, scars[part.ordinal()], blood[part.ordinal()],
				scar, level, strength, lightCoords, OverlayTexture.NO_OVERLAY, BloodTint.RED);
		poseStack.popPose();
		if (level >= BloodBodyLayer.VISIBLE) {
			bloodSubmits++;
		}
	}

	private static ModelPart[] bloodParts(boolean slim) {
		if (slim) {
			if (slimBlood == null) {
				slimBlood = BloodBodyLayer.bake(true, BloodBodyLayer.SKIN_OFFSET);
			}
			return slimBlood;
		}
		if (wideBlood == null) {
			wideBlood = BloodBodyLayer.bake(false, BloodBodyLayer.SKIN_OFFSET);
		}
		return wideBlood;
	}

	private static ModelPart[] scarParts(boolean slim) {
		if (slim) {
			if (slimScar == null) {
				slimScar = BloodBodyLayer.bake(true, BloodBodyLayer.SCAR_OFFSET);
			}
			return slimScar;
		}
		if (wideScar == null) {
			wideScar = BloodBodyLayer.bake(false, BloodBodyLayer.SCAR_OFFSET);
		}
		return wideScar;
	}
}
