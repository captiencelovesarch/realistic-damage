package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.LimbStateExtractor;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An illager's destroyed arm holds no crossbow or axe (1.7); emptied after vanilla fills the hands. */
@Mixin(IllagerRenderer.class)
public abstract class IllagerRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$emptyLostHands(AbstractIllager entity, IllagerRenderState state, float partialTicks, CallbackInfo ci) {
		LimbStateExtractor.hideDestroyedHands(entity, state);
	}
}
