package dev.captience.realisticdamage.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bodies that stay (active ragdolls phase 4): once the server has removed a
 * dead entity, there is nothing for vanilla to draw, so the kept bodies are
 * added to the frame's entity list here, as they last looked.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "extractVisibleEntities", at = @At("TAIL"))
	private void realisticdamage$keptBodies(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
			LevelRenderState output, CallbackInfo ci) {
		dev.captience.realisticdamage.client.RemoteCorpses.extractOrphans(output.entityRenderStates,
				deltaTracker.getGameTimeDeltaPartialTick(false));
	}
}
