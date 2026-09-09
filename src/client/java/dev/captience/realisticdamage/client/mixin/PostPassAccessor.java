package dev.captience.realisticdamage.client.mixin;

import java.util.Map;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A pass's uniform blocks, keyed by block name. Vanilla bakes these from the
 * chain JSON once and never writes them again; the afterimage swaps its own
 * writable buffer into the map so the strength can change every frame.
 */
@Mixin(PostPass.class)
public interface PostPassAccessor {
	@Accessor("customUniforms")
	Map<String, GpuBuffer> realisticdamage$customUniforms();
}
