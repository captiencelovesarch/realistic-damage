package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.AudioMuffle;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-applies the muffle filter to every live channel each engine tick. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMuffleMixin {
	@Shadow
	@Final
	private ChannelAccess channelAccess;

	@Inject(method = "tick(Z)V", at = @At("TAIL"))
	private void realisticdamage$muffle(boolean paused, CallbackInfo ci) {
		AudioMuffle.tick(this.channelAccess);
	}
}
