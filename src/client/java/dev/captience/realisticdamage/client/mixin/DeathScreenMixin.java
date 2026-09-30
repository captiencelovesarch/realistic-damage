package dev.captience.realisticdamage.client.mixin;

import java.util.List;

import dev.captience.realisticdamage.client.DeathCamera;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The death screen fades in (1.7, see {@link DeathCamera}) instead of landing
 * on the death tick at full strength. Gradient, text and buttons share one
 * opacity; the buttons stay asleep until the screen is nearly there.
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {
	@Shadow
	private int delayTicker;
	@Shadow
	@Final
	private List<Button> exitButtons;

	/** The frame's opacity, set before anything on the screen draws. */
	private static float realisticdamage$alpha = 1.0F;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void realisticdamage$fade(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		realisticdamage$alpha = DeathCamera.screenAlpha(a);
		for (Button button : this.exitButtons) {
			button.setAlpha(realisticdamage$alpha);
		}
	}

	@Inject(method = "extractBackground", at = @At("HEAD"))
	private void realisticdamage$fadeBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		realisticdamage$alpha = DeathCamera.screenAlpha(a);
	}

	@ModifyArg(method = "extractDeathBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"), index = 4)
	private static int realisticdamage$gradientTop(int colour) {
		return realisticdamage$scale(colour);
	}

	@ModifyArg(method = "extractDeathBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"), index = 5)
	private static int realisticdamage$gradientBottom(int colour) {
		return realisticdamage$scale(colour);
	}

	private static int realisticdamage$scale(int colour) {
		return ARGB.color(Math.round(ARGB.alpha(colour) * realisticdamage$alpha), colour);
	}

	@Inject(method = "visitText", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$fadeText(ActiveTextCollector output, CallbackInfo ci) {
		float alpha = DeathCamera.screenAlpha(1.0F);
		if (alpha < 0.02F) {
			ci.cancel();   // nothing to see, and nothing to click either
			return;
		}
		output.defaultParameters(output.defaultParameters().withOpacity(alpha));
	}

	/** Holds the vanilla wake-up at one tick short until the screen is visible. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void realisticdamage$holdButtons(CallbackInfo ci) {
		if (!DeathCamera.buttonsReady() && this.delayTicker >= 18) {
			this.delayTicker = 18;
		}
	}
}
