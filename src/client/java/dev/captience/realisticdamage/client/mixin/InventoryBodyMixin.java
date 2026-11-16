package dev.captience.realisticdamage.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.captience.realisticdamage.client.BodyInspector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The player box in the survival inventory shows the body (2.0). Wrapped
 * round vanilla's one call that draws the turning model there, and only that
 * one: every other screen that borrows the same routine (the smithing table,
 * the horse, another mod's wardrobe) still gets the model.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryBodyMixin {
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"))
	private void realisticdamage$body(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size,
			float offsetY, float mouseX, float mouseY, LivingEntity entity, Operation<Void> original) {
		if (!BodyInspector.draw(graphics, x0, y0, x1, y1, mouseX, mouseY)) {
			original.call(graphics, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, entity);
		}
		BodyInspector.drawButton(graphics, x0, y0, x1, y1, mouseX, mouseY);
	}
}
