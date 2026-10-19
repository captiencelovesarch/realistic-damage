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
		LimbStateExtractor.extract(entity, state);
		dev.captience.realisticdamage.client.RemoteCorpses.extract(entity, state, partialTicks);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void realisticdamage$layOut(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		if (dev.captience.realisticdamage.client.RemoteCorpses.applyRotation(state, poseStack)) {
			return;
		}
		if (state instanceof AvatarRenderState || state.deathTime > 0.0F) {
			return;
		}
		int down = ((LimbStateHolder) state).realisticdamage$downMask();
		if (down != 0) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(getFlipDegrees()));
			DownPose.applied();
		}
	}
}
