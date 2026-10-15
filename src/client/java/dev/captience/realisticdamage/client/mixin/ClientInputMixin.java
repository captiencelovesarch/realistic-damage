package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.StumbleInput;
import dev.captience.realisticdamage.client.StumbleTarget;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Bends the movement vector away from what was actually pressed as
 * consciousness fades (1.4). See {@link StumbleInput} for why it wanders the way
 * it does.
 *
 * <p>Applied from {@link LocalPlayerInputMixin}, straight after the input's own
 * tick, rather than at the tail of {@code KeyboardInput.tick} as it was until
 * 1.7 alpha.10: Vivecraft sets the move vector from the thumbstick at that same
 * tail, and two TAIL injections run in an order neither mod controls — in VR
 * the stumble was simply overwritten. After the call, every mod's input has
 * landed and the stumble bends the result.
 *
 * <p>Presentation-adjacent but not presentation: this changes where the player
 * goes. It stays client-side anyway, because the alternative — nudging velocity
 * from the server — arrives as a rubber-band, and a control that fights you
 * reads as a broken game rather than a failing body.
 */
@Mixin(ClientInput.class)
public abstract class ClientInputMixin implements StumbleTarget {
	@Shadow
	protected Vec2 moveVector;

	@Override
	public void realisticdamage$stumble() {
		this.moveVector = StumbleInput.apply(this.moveVector);
	}
}
