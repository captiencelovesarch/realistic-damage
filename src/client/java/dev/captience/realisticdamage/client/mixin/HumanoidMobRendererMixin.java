package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.LimbStateExtractor;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A humanoid mob's destroyed arm holds nothing (1.7); emptied after vanilla fills the hands. */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$emptyLostHands(Mob entity, HumanoidRenderState state, float partialTicks, CallbackInfo ci) {
		LimbStateExtractor.hideDestroyedHands(entity, state);
	}
}
