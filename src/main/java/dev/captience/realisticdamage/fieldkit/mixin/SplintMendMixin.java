package dev.captience.realisticdamage.fieldkit.mixin;

import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.systems.HealingSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Healing a limb in a splint or a sling (2.0, phase 4 round 2). A slung broken
 * arm takes twice as much from every heal. And when a splinted or slung bone
 * knits, the field kit says so itself as the dressing comes off, so the core's
 * own "mended" line would be a second one.
 */
@Mixin(HealingSystem.class)
abstract class SplintMendMixin {
	@ModifyArg(method = "trackBrokenHeal", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/BodyPartData;heal(D)D"))
	private static double realisticdamage$restingArm(double amount, @Local(argsOnly = true) LivingEntity body,
			@Local(argsOnly = true) BodyPartData part) {
		return part.broken() && Dressings.slung(body, part.part()) ? amount * Balance.SLING_HEAL_FACTOR : amount;
	}

	@WrapWithCondition(method = "trackBrokenHeal", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/util/Notifier;message(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;)V"))
	private static boolean realisticdamage$dressingSaysIt(LivingEntity victim, String key,
			@Local(argsOnly = true) BodyPartData part) {
		Dressings.State state = Dressings.peek(victim);
		return state == null || !dev.captience.realisticdamage.fieldkit.Half.MEDICINE.on()
				|| (!state.splints.contains(part.part()) && !state.slings.contains(part.part()));
	}
}
