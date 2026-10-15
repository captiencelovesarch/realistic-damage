package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.StumbleTarget;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stumbles the movement once the input has ticked; see {@link ClientInputMixin}. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerInputMixin {
	@Inject(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
	private void realisticdamage$stumble(CallbackInfo ci) {
		ClientInput input = ((LocalPlayer) (Object) this).input;
		((StumbleTarget) input).realisticdamage$stumble();
	}
}
