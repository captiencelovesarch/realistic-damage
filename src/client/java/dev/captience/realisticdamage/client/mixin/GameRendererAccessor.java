package dev.captience.realisticdamage.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code setPostEffect} is private; we need it to drive the greyout chain. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("setPostEffect")
	void realisticdamage$setPostEffect(Identifier id);
}
