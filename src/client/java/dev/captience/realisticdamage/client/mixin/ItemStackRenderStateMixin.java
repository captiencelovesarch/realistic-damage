package dev.captience.realisticdamage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.client.BloodiedItems;
import dev.captience.realisticdamage.client.BloodiedState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The item's render state carries its blood (2.0), and for the length of its
 * own submit holds it up for the draws being made, which is the only way to
 * get it onto them: a submitted item is a list of quads and a pose, with
 * nowhere to say anything else.
 */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements BloodiedState {
	@Unique
	private BloodiedItems.@Nullable Look realisticdamage$blood;

	@Override
	public BloodiedItems.@Nullable Look realisticdamage$blood() {
		return realisticdamage$blood;
	}

	@Override
	public void realisticdamage$setBlood(BloodiedItems.@Nullable Look look) {
		realisticdamage$blood = look;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void realisticdamage$clean(CallbackInfo ci) {
		realisticdamage$blood = null;
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void realisticdamage$hold(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		BloodiedItems.submitting = realisticdamage$blood;
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void realisticdamage$letGo(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		BloodiedItems.submitting = null;
	}
}
