package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.BloodStainHolder;
import dev.captience.realisticdamage.client.LimbStateHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Storage slots for the destroyed-limb mask, the broken-limb mask, the packed
 * limp rungs, blood staining and scars on the player's render state.
 */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements LimbStateHolder, BloodStainHolder {
	@Unique
	private int realisticdamage$destroyedMask;
	@Unique
	private int realisticdamage$brokenMask;
	@Unique
	private int realisticdamage$limbRungs;
	@Unique
	private float[] realisticdamage$bloodStains;
	@Unique
	private int realisticdamage$scarMask;

	@Override
	public int realisticdamage$destroyedMask() {
		return this.realisticdamage$destroyedMask;
	}

	@Override
	public void realisticdamage$setDestroyedMask(int mask) {
		this.realisticdamage$destroyedMask = mask;
	}

	@Override
	public int realisticdamage$brokenMask() {
		return this.realisticdamage$brokenMask;
	}

	@Override
	public void realisticdamage$setBrokenMask(int mask) {
		this.realisticdamage$brokenMask = mask;
	}

	@Override
	public int realisticdamage$limbRungs() {
		return this.realisticdamage$limbRungs;
	}

	@Override
	public void realisticdamage$setLimbRungs(int rungs) {
		this.realisticdamage$limbRungs = rungs;
	}

	@Override
	public float[] realisticdamage$bloodStains() {
		return this.realisticdamage$bloodStains;
	}

	@Override
	public void realisticdamage$setBloodStains(float[] stains) {
		this.realisticdamage$bloodStains = stains;
	}

	@Override
	public int realisticdamage$scarMask() {
		return this.realisticdamage$scarMask;
	}

	@Override
	public void realisticdamage$setScarMask(int mask) {
		this.realisticdamage$scarMask = mask;
	}
}
