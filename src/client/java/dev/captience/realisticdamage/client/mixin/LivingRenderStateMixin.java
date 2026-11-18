package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.BloodStainHolder;
import dev.captience.realisticdamage.client.LimbStateHolder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Storage slots for the destroyed-limb mask, the broken-limb mask, the packed
 * limp rungs, blood staining, scars and the down flags on every living render
 * state — the player's, and since 1.7 every mob's, humanoid or not.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingRenderStateMixin implements LimbStateHolder, BloodStainHolder, dev.captience.realisticdamage.client.CorpseHolder,
		dev.captience.realisticdamage.client.FlinchHolder, dev.captience.realisticdamage.client.EntityIdHolder {
	@Unique
	private int realisticdamage$entityId = -1;

	@Override
	public int realisticdamage$entityId() {
		return this.realisticdamage$entityId;
	}

	@Override
	public void realisticdamage$setEntityId(int id) {
		this.realisticdamage$entityId = id;
	}

	@Unique
	private float @org.jspecify.annotations.Nullable [] realisticdamage$flinch;

	@Override
	public float @org.jspecify.annotations.Nullable [] realisticdamage$flinch() {
		return this.realisticdamage$flinch;
	}

	@Override
	public void realisticdamage$setFlinch(float @org.jspecify.annotations.Nullable [] flinch) {
		this.realisticdamage$flinch = flinch;
	}

	@Unique
	private dev.captience.realisticdamage.client.RemoteCorpses.@org.jspecify.annotations.Nullable Frame realisticdamage$corpse;

	@Override
	public dev.captience.realisticdamage.client.RemoteCorpses.@org.jspecify.annotations.Nullable Frame realisticdamage$corpse() {
		return this.realisticdamage$corpse;
	}

	@Override
	public void realisticdamage$setCorpse(dev.captience.realisticdamage.client.RemoteCorpses.@org.jspecify.annotations.Nullable Frame pose) {
		this.realisticdamage$corpse = pose;
	}

	@Unique
	private int realisticdamage$destroyedMask;
	@Unique
	private int realisticdamage$brokenMask;
	@Unique
	private int realisticdamage$limbRungs;
	@Unique
	private float[] realisticdamage$bloodStains;
	@Unique
	private dev.captience.realisticdamage.client.BloodTint[] realisticdamage$bloodTints;
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
	public dev.captience.realisticdamage.client.BloodTint[] realisticdamage$bloodTints() {
		return this.realisticdamage$bloodTints;
	}

	@Override
	public void realisticdamage$setBloodTints(dev.captience.realisticdamage.client.BloodTint[] tints) {
		this.realisticdamage$bloodTints = tints;
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

	@Unique
	private int realisticdamage$downMask;

	@Override
	public int realisticdamage$downMask() {
		return this.realisticdamage$downMask;
	}

	@Override
	public void realisticdamage$setDownMask(int mask) {
		this.realisticdamage$downMask = mask;
	}
}
