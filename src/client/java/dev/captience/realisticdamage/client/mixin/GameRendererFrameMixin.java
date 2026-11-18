package dev.captience.realisticdamage.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.client.Afterimage;
import dev.captience.realisticdamage.client.CameraMotion;
import dev.captience.realisticdamage.client.DeathCamera;
import dev.captience.realisticdamage.client.VrLens;
import dev.captience.realisticdamage.client.compat.Vr;
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
 *
 * <p>Under Vivecraft (1.7 alpha.10) {@code render} runs once per eye, and again
 * for the desktop mirror or the handheld camera. The integrators step on the
 * first of those passes only, the afterimage keeps a history per eye, and the
 * VR lens is laid over each eye last. Vivecraft cancels {@code bobHurt} itself
 * in VR — the headset is the camera — and the hook below stands down too, so
 * that stays true whichever of the two runs first.
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
		boolean first = Vr.firstPass();
		int eye = Vr.eye();
		if (first) {
			VrLens.frame();
			Afterimage.frame(deltaTracker.getGameTimeDeltaTicks());
		}
		Afterimage.process(this.mainRenderTarget, this.resourcePool, deltaTracker.getRealtimeDeltaTicks(), eye);
		VrLens.process(this.mainRenderTarget, this.resourcePool, eye);
		if (first) {
			// The spring is stepped here, after this frame's bobHurt has read it, so
			// the picture lags the impulse by one frame and never by a partial one.
			CameraMotion.frame(deltaTracker.getGameTimeDeltaTicks());
			dev.captience.realisticdamage.client.FirstPersonLimp.frame(deltaTracker.getGameTimeDeltaTicks());
			DeathCamera.frame(deltaTracker.getGameTimeDeltaTicks());
			if (!DeathCamera.active()) {
				// Knocked down (phase 4): the same ragdoll view, without the death.
				dev.captience.realisticdamage.client.RagdollView.frame(deltaTracker.getGameTimeDeltaTicks());
			}
		}
	}

	// RETURN, not TAIL: bobHurt returns early whenever the player is not in a
	// hurt animation, which is almost always, and TAIL only fires at the last
	// return. The first build injected at TAIL and the sway ran only while
	// the player was being hit — which the screenshot caught and the internal
	// readout did not, hence CameraMotion.applications().
	@Inject(method = "bobHurt", at = @At("RETURN"))
	private void realisticdamage$cameraMotion(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (Vr.active()) {
			return;
		}
		CameraMotion.apply(poseStack);
		// A broken leg (2.0): the dip and lean of each step taken on it.
		dev.captience.realisticdamage.client.FirstPersonLimp.apply(poseStack);
		// The collapse on top (1.7): a dead body's view rolls over and sags.
		DeathCamera.apply(poseStack);
		if (!DeathCamera.active() && dev.captience.realisticdamage.client.RagdollView.active()) {
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
					(float) dev.captience.realisticdamage.client.RagdollView.roll()));
		}
	}

	// A body on the floor is not holding its hand up in front of its face.
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$deadHand(CameraRenderState cameraState, float deltaPartialTick,
			org.joml.Matrix4fc modelViewMatrix, CallbackInfo ci) {
		dev.captience.realisticdamage.client.FirstPersonLimp.handPass();
		if (DeathCamera.hideHand() || dev.captience.realisticdamage.client.RagdollView.active()) {
			ci.cancel();
		}
	}
}
