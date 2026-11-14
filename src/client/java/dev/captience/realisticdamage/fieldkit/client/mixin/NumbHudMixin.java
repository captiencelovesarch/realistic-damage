package dev.captience.realisticdamage.fieldkit.client.mixin;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.BodyHud;
import dev.captience.realisticdamage.fieldkit.brew.Numbing;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Numb, your body diagram stops telling you how bad things are, because you
 * can't feel them (2.0, phase 3): every region the same pale frost, no fracture
 * or wound marks, no status icons, and the caption just says "Numb". The blood
 * gauge stays: you can still see what is coming out of you.
 *
 * <p>Only your own body. The triage view draws a patient with the same code,
 * and a numb rescuer must still read a patient as they are.
 */
@Mixin(BodyHud.class)
abstract class NumbHudMixin {
	@Unique
	private static boolean realisticdamage$numb;

	@Inject(method = "render", at = @At("HEAD"))
	private static void realisticdamage$begin(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		realisticdamage$numb = player != null && Numbing.numbClient(player);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private static void realisticdamage$end(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		realisticdamage$numb = false;
	}

	@Inject(method = "box", at = @At("HEAD"), cancellable = true)
	private static void realisticdamage$frost(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			BodySnapshot snapshot, BodyPart part, int alpha, CallbackInfo ci) {
		if (!realisticdamage$numb) {
			return;
		}
		graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x00203848 | alpha);
		graphics.fill(x, y, x + w, y + h, 0x00B4E2F2 | alpha);
		dev.captience.realisticdamage.fieldkit.client.NumbHud.drew();
		ci.cancel();
	}

	@Inject(method = "drawStatusIcons", at = @At("HEAD"), cancellable = true)
	private static void realisticdamage$noIcons(GuiGraphicsExtractor graphics, BodySnapshot snapshot, int x, int y,
			int alpha, CallbackInfoReturnable<Integer> cir) {
		if (realisticdamage$numb) {
			cir.setReturnValue(y);
		}
	}

	@Inject(method = "caption", at = @At("RETURN"), cancellable = true)
	private static void realisticdamage$numbCaption(BodySnapshot snapshot, CallbackInfoReturnable<Component> cir) {
		if (realisticdamage$numb) {
			cir.setReturnValue(Component.translatable("realisticdamage.fieldkit.numb"));
		}
	}
}
