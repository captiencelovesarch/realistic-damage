package dev.captience.realisticdamage.client.mixin;

import java.util.List;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The passes of a loaded chain, so the afterimage can reach its own uniform. */
@Mixin(PostChain.class)
public interface PostChainAccessor {
	@Accessor("passes")
	List<PostPass> realisticdamage$passes();
}
