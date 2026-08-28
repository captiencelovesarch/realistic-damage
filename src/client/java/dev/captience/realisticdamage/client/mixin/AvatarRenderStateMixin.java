package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.DestroyedLimbHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Storage slot for the destroyed-limb mask on the player's render state. */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements DestroyedLimbHolder {
	@Unique
	private int realisticdamage$destroyedMask;

	@Override
	public int realisticdamage$destroyedMask() {
		return this.realisticdamage$destroyedMask;
	}

	@Override
	public void realisticdamage$setDestroyedMask(int mask) {
		this.realisticdamage$destroyedMask = mask;
	}
}
