package dev.captience.realisticdamage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.client.DownPose;
import dev.captience.realisticdamage.client.LimbStateExtractor;
import dev.captience.realisticdamage.client.LimbStateHolder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every living thing's body onto its render state (1.7), and a body that is
 * down laid out on the ground.
 *
 * <p>The extraction runs for players and mobs alike; a living thing without a
 * snapshot pays a null read. The pose is mobs only: a player's prone pose is
 * the server's business (PlayerPoseMixin), and vanilla's own death fall uses
 * the same roll about the feet, so a collapsed zombie lies exactly where a
 * dead one would, and does not double the roll on the tick it actually dies.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Shadow
	protected abstract float getFlipDegrees();

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$carryBody(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		((dev.captience.realisticdamage.client.EntityIdHolder) state).realisticdamage$setEntityId(entity.getId());
		LimbStateExtractor.extract(entity, state);
		dev.captience.realisticdamage.client.RemoteCorpses.extract(entity, state, partialTicks);
		dev.captience.realisticdamage.client.Flinches.extract(entity, state, partialTicks);
	}

	@org.spongepowered.asm.mixin.Shadow
	protected net.minecraft.client.model.EntityModel<?> model;

	/** The model posed for the layers (held items, the head slot): the corpse's pose must be there too. */
	@Inject(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
					shift = At.Shift.AFTER))
	private void realisticdamage$poseForLayers(LivingEntityRenderState state, PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector collector,
			net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		// The model this body is drawn with, for its ragdoll (a baby's, a variant's).
		dev.captience.realisticdamage.client.ModelMemory.remember(
				((dev.captience.realisticdamage.client.EntityIdHolder) state).realisticdamage$entityId(), this.model);
		dev.captience.realisticdamage.client.RemoteCorpses.pose(this.model, state);
		dev.captience.realisticdamage.client.Flinches.pose(this.model, state);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void realisticdamage$layOut(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		if (dev.captience.realisticdamage.client.RemoteCorpses.applyRotation(state, poseStack)) {
			return;
		}
		dev.captience.realisticdamage.client.Flinches.applyRotation(state, poseStack);
		if (state instanceof AvatarRenderState || state.deathTime > 0.0F) {
			return;
		}
		int down = ((LimbStateHolder) state).realisticdamage$downMask();
		if (down != 0) {
			float flip = getFlipDegrees();
			if (flip > 135.0F) {
				// Onto its back (a spider): rolled about its feet it would lie
				// under the ground, so it is lifted by its height first, the way
				// vanilla turns an upside-down body over.
				poseStack.translate(0.0F, state.boundingBoxHeight / scale, 0.0F);
			}
			poseStack.mulPose(Axis.ZP.rotationDegrees(flip));
			DownPose.applied();
		}
	}
}
