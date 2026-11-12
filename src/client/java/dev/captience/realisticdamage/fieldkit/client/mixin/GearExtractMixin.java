package dev.captience.realisticdamage.fieldkit.client.mixin;

import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.fieldkit.client.GearHolder;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.fieldkit.medicine.Goggles;
import dev.captience.realisticdamage.fieldkit.medicine.Medicine;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What a body wears from the field kit, onto its render state each frame
 * (items.7), from the dressings every client is already sent. Worn gear is
 * drawn whatever the Medicine switch says: a splint left on with Medicine off
 * does nothing, but it is still there.
 *
 * <p>Tinted goggles on the head are drawn by {@code GearLayer} as goggles, so
 * vanilla's flat picture of the item on the head is taken off the state here.
 */
@Mixin(LivingEntityRenderer.class)
abstract class GearExtractMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$wornGear(LivingEntity entity, LivingEntityRenderState state, float partialTicks,
			CallbackInfo ci) {
		int bandaged = 0;
		int soaked = 0;
		int tourniquets = 0;
		int splints = 0;
		int slings = 0;
		Dressings.State dressings = entity.getAttached(Dressings.STATE);
		if (dressings != null) {
			for (Map.Entry<BodyPart, Dressings.Bandage> bandage : dressings.bandages.entrySet()) {
				bandaged |= 1 << bandage.getKey().ordinal();
				if (bandage.getValue().soaked) {
					soaked |= 1 << bandage.getKey().ordinal();
				}
			}
			for (BodyPart part : dressings.tourniquets.keySet()) {
				tourniquets |= 1 << part.ordinal();
			}
			for (BodyPart part : dressings.splints) {
				splints |= 1 << part.ordinal();
			}
			for (BodyPart part : dressings.slings) {
				slings |= 1 << part.ordinal();
			}
		}
		boolean goggles = Goggles.is(entity.getItemBySlot(EquipmentSlot.HEAD));
		if (goggles) {
			state.headItem.clear();
		}
		boolean listening = entity.isUsingItem() && entity.getUseItem().is(Medicine.STETHOSCOPE);
		((GearHolder) state).realisticdamage$setGear(bandaged, soaked, tourniquets, splints, slings, goggles, listening);
	}
}
