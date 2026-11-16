package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.BloodiedItems;
import dev.captience.realisticdamage.client.BloodiedState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One submitted draw of an item remembers the blood of the item it came from (2.0). */
@Mixin(ItemFeatureRenderer.Submit.class)
public abstract class ItemSubmitMixin implements BloodiedState {
	@Unique
	private BloodiedItems.@Nullable Look realisticdamage$blood;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void realisticdamage$take(CallbackInfo ci) {
		realisticdamage$blood = BloodiedItems.submitting;
	}

	@Override
	public BloodiedItems.@Nullable Look realisticdamage$blood() {
		return realisticdamage$blood;
	}

	@Override
	public void realisticdamage$setBlood(BloodiedItems.@Nullable Look look) {
		realisticdamage$blood = look;
	}
}
