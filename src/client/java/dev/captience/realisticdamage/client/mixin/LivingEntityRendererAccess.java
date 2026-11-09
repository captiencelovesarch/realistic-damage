package dev.captience.realisticdamage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The renderer's own model scale (the player's 0.9375, a husk's 1.0625), for
 * sizing a ragdoll to the body that is actually drawn rather than its hitbox.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccess {
	@Invoker("scale")
	void realisticdamage$scale(LivingEntityRenderState state, PoseStack poseStack);
}
