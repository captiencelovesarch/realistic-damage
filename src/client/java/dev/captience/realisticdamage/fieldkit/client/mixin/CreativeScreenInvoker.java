package dev.captience.realisticdamage.fieldkit.client.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Re-lists the open creative tab after its contents were rebuilt under it (2.0). */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenInvoker {
	@Invoker("selectTab")
	void realisticdamage$selectTab(CreativeModeTab tab);
}
