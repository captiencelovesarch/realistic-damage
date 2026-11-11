package dev.captience.realisticdamage.fieldkit.client.mixin;

import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Adrenaline races your own heart (2.0, phase 3): Cardio is fed at least the
 * health distress of a body in real trouble, so the beat quickens and the spurts
 * from an open artery come faster, without the rest of the low-health warnings
 * (the vignette reads {@code distress()}, which is left alone).
 */
@Mixin(ClientState.class)
abstract class HeartRaceMixin {
	@ModifyArg(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/Cardio;tick(DDDD)V"), index = 0)
	private static double realisticdamage$race(double fromHealth) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.hasEffect(BrewedEffects.ADRENALINE)
				? Math.max(fromHealth, Balance.ADRENALINE_HEART_DISTRESS) : fromHealth;
	}
}
