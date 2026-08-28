package dev.captience.realisticdamage.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The raw OpenAL source handle, needed to attach a low-pass filter to it. */
@Mixin(Channel.class)
public interface ChannelAccessor {
	@Accessor("source")
	int realisticdamage$source();
}
