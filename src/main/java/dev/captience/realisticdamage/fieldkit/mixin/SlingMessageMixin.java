package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * When it's a sling that stops a hand (2.0, phase 4 round 2), the line says so
 * ("Your right arm is in a sling.") instead of "won't close around it".
 */
@Mixin(ConsciousnessSystem.class)
abstract class SlingMessageMixin {
	@WrapOperation(method = "handGone", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/util/Notifier;message(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;)V"))
	private static void realisticdamage$slingLine(LivingEntity victim, String key, Operation<Void> original,
			@Local(argsOnly = true) Player player, @Local(argsOnly = true) InteractionHand hand) {
		if (player instanceof ServerPlayer server) {
			InteractionHand blocked = ImpairmentSystem.gripBlocked(server, hand);
			if (blocked != null) {
				var arm = ImpairmentSystem.armFor(server, blocked == InteractionHand.MAIN_HAND);
				if (Dressings.slung(server, arm)) {
					server.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.sling.held",
							Dressings.partName(arm)).withStyle(ChatFormatting.GRAY));
					return;
				}
			}
		}
		original.call(victim, key);
	}
}
