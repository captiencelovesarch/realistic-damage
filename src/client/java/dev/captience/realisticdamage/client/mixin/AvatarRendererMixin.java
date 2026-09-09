package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.client.FirstPersonBlood;
import dev.captience.realisticdamage.client.LimbStateExtractor;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies the synced destroyed-limb and broken-limb masks, the limp rungs read
 * off them, the accumulated blood stains and the synced scar mask onto the
 * render state each frame.
 *
 * <p>Scars come off the snapshot rather than out of a client-side accumulator
 * the way blood does, and deliberately: entity ids are reused across a respawn
 * on both sides, so anything keyed by id would hand a fresh body the previous
 * one's marks. The server already carries the history through death; the client
 * only has to read it.
 *
 * <p>The broken mask has been on the wire since 1.0 ({@code BodySnapshot} sends
 * it to every tracking client) and was simply never read on this side. Reading
 * it costs one field: there is no packet, save or protocol change here, and a
 * 1.5 client and a 1.6 client see the same bytes.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$carryDestroyedLimbs(Avatar entity, AvatarRenderState state, float partialTicks,
			CallbackInfo ci) {
		// Shared with every humanoid mob renderer since 1.7; see LimbStateExtractor.
		LimbStateExtractor.extract(entity, state);
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		int destroyedMask = snapshot == null ? 0 : snapshot.destroyedMask();
		// The player-only part: an item being used from a hand that is gone.
		for (HumanoidArm arm : HumanoidArm.values()) {
			BodyPart part = arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
			if ((destroyedMask & (1 << part.ordinal())) == 0) {
				continue;
			}
			InteractionHand hand = arm == state.mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
			if (state.isUsingItem && state.useItemHand == hand) {
				state.heldOnHead.clear();
			}
		}
	}

	/**
	 * The first-person hand (1.6.1). Vanilla submits the bare arm part here and
	 * nothing else — no layers run — so the blood and scars on it are painted
	 * on afterwards, from the same meshes the third-person layer uses. See
	 * {@link FirstPersonBlood}.
	 */
	@Inject(method = "renderHand", at = @At("TAIL"))
	private void realisticdamage$paintFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
		PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
		FirstPersonBlood.submit(poseStack, collector, lightCoords, arm, arm == model.rightArm);
	}

	/**
	 * A destroyed arm in first person (1.6.2) is a stump, not an absence: the
	 * whole-arm submit is cancelled and the raised stump drawn in its place.
	 * {@code ItemInHandRendererMixin} is what lets this method be reached at all
	 * for a destroyed arm — it used to cancel the hand path outright.
	 */
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$stumpInsteadOfArm(PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
		PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
		boolean rightArm = arm == model.rightArm;
		if (FirstPersonBlood.armDestroyed(rightArm)) {
			arm.resetPose();
			arm.zRot = rightArm ? 0.1F : -0.1F;
			FirstPersonBlood.submitStump(poseStack, collector, lightCoords, arm, rightArm, skinTexture);
			ci.cancel();
		}
	}

}
