package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.StumbleInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bends the movement vector away from what was actually pressed as
 * consciousness fades (1.4). See {@link StumbleInput} for why it wanders the way
 * it does.
 *
 * <p>Targets {@link KeyboardInput} rather than its base class because
 * {@code ClientInput.tick} is empty and overridden — injecting into the base
 * would never fire. The field itself is inherited, hence the shadow.
 *
 * <p>Presentation-adjacent but not presentation: this changes where the player
 * goes. It stays client-side anyway, because the alternative — nudging velocity
 * from the server — arrives as a rubber-band, and a control that fights you
 * reads as a broken game rather than a failing body.
 */
@Mixin(KeyboardInput.class)
public abstract class ClientInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void realisticdamage$stumble(CallbackInfo ci) {
		this.moveVector = StumbleInput.apply(this.moveVector);
	}
}
