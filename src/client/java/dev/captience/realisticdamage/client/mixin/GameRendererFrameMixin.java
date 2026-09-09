package dev.captience.realisticdamage.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.client.Afterimage;
import dev.captience.realisticdamage.client.CameraMotion;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two per-frame hooks into the world render (1.6.1).
 *
 * <p>The afterimage runs as its own post chain on the main target, right after
 * the level has finished drawing and before vanilla's single post-effect slot
 * (which the greyout already occupies). Running it here rather than from the
 * HUD means it sees the world and the hand and not the interface, and that it
 * keeps running with the HUD hidden.
 *
 * <p>The camera motion rides vanilla's own hurt tilt: {@code bobHurt} is the
 * one place a rotation is applied to the view and the first-person hand alike,
 * and never to the player's aim. Everything {@link CameraMotion} does is a
 * matrix on top of where the player is actually looking, which is what lets it
 * be violent without ever moving the crosshair.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFrameMixin {
	@Shadow
	@Final
	private RenderTarget mainRenderTarget;
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void realisticdamage$afterimage(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		Afterimage.frame(deltaTracker.getGameTimeDeltaTicks());
		Afterimage.process(this.mainRenderTarget, this.resourcePool, deltaTracker.getRealtimeDeltaTicks());
		// The spring is stepped here, after this frame's bobHurt has read it, so
		// the picture lags the impulse by one frame and never by a partial one.
		CameraMotion.frame(deltaTracker.getGameTimeDeltaTicks());
	}

	// RETURN, not TAIL: bobHurt returns early whenever the player is not in a
	// hurt animation, which is almost always, and TAIL only fires at the last
	// return. The first build injected at TAIL and the sway ran only while
	// the player was being hit — which the screenshot caught and the internal
	// readout did not, hence CameraMotion.applications().
	@Inject(method = "bobHurt", at = @At("RETURN"))
	private void realisticdamage$cameraMotion(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		CameraMotion.apply(poseStack);
	}
}
