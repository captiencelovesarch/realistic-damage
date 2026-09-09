package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.LimbStateExtractor;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A humanoid mob's body onto its render state (1.7), the same way
 * {@link AvatarRendererMixin} does the player's: masks, rungs, blood, and an
 * emptied hand where the arm is gone. Mobs without a snapshot pay a null read.
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$carryBody(Mob entity, HumanoidRenderState state, float partialTicks, CallbackInfo ci) {
		LimbStateExtractor.extract(entity, state);
	}
}
