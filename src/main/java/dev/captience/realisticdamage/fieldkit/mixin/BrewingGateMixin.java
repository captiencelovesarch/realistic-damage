package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.fieldkit.Kit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The brewing stand asks the switch (2.0). The mixes are registered always —
 * vanilla builds the brewing table once per server, and a table that changed
 * with a switch would need a restart — and refused at brew time instead: a mix
 * whose result is a switched-off potion is not a mix. The stand then simply
 * does not start, as with any ingredient that does nothing.
 *
 * <p>Both questions are answered, because vanilla asks both: {@code hasMix}
 * decides whether the stand runs, {@code mix} makes the result.
 */
@Mixin(PotionBrewing.class)
abstract class BrewingGateMixin {
	@Shadow
	public abstract ItemStack mix(ItemStack ingredient, ItemStack source);

	@Inject(method = "hasMix", at = @At("RETURN"), cancellable = true)
	private void realisticdamage$gateHasMix(ItemStack source, ItemStack ingredient,
			CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		// A real mix always makes a new stack; getting the source back means
		// the gate below refused it.
		ItemStack result = mix(ingredient, source);
		if (result == source || Kit.blocked(result)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "mix", at = @At("RETURN"), cancellable = true)
	private void realisticdamage$gateMix(ItemStack ingredient, ItemStack source,
			CallbackInfoReturnable<ItemStack> cir) {
		ItemStack result = cir.getReturnValue();
		if (result != source && Kit.blocked(result)) {
			cir.setReturnValue(source);
		}
	}
}
