package dev.captience.realisticdamage.fieldkit.client.mixin;

import dev.captience.realisticdamage.fieldkit.client.GearHolder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The field kit gear a body wears, carried on its render state (items.7). See {@link GearHolder}. */
@Mixin(LivingEntityRenderState.class)
abstract class GearRenderStateMixin implements GearHolder {
	@Unique
	private int realisticdamage$bandaged;
	@Unique
	private int realisticdamage$soaked;
	@Unique
	private int realisticdamage$tourniquets;
	@Unique
	private int realisticdamage$splints;
	@Unique
	private int realisticdamage$slings;
	@Unique
	private boolean realisticdamage$goggles;
	@Unique
	private boolean realisticdamage$listening;

	@Override
	public int realisticdamage$bandaged() {
		return realisticdamage$bandaged;
	}

	@Override
	public int realisticdamage$soaked() {
		return realisticdamage$soaked;
	}

	@Override
	public int realisticdamage$tourniquets() {
		return realisticdamage$tourniquets;
	}

	@Override
	public int realisticdamage$splints() {
		return realisticdamage$splints;
	}

	@Override
	public int realisticdamage$slings() {
		return realisticdamage$slings;
	}

	@Override
	public boolean realisticdamage$goggles() {
		return realisticdamage$goggles;
	}

	@Override
	public boolean realisticdamage$listening() {
		return realisticdamage$listening;
	}

	@Override
	public void realisticdamage$setGear(int bandaged, int soaked, int tourniquets, int splints, int slings,
			boolean goggles, boolean listening) {
		this.realisticdamage$bandaged = bandaged;
		this.realisticdamage$soaked = soaked;
		this.realisticdamage$tourniquets = tourniquets;
		this.realisticdamage$splints = splints;
		this.realisticdamage$slings = slings;
		this.realisticdamage$goggles = goggles;
		this.realisticdamage$listening = listening;
	}
}
