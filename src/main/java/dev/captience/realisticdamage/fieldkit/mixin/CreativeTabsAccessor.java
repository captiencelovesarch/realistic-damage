package dev.captience.realisticdamage.fieldkit.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the creative tabs be rebuilt when a switch flips (2.0). Vanilla rebuilds
 * them only when the feature flags, permissions or registries change; forgetting
 * the cached parameters makes the next open screen (or the open one's next tick)
 * build them again, with the Field kit tab asking the switches afresh.
 */
@Mixin(CreativeModeTabs.class)
public interface CreativeTabsAccessor {
	@Accessor("CACHED_PARAMETERS")
	static void realisticdamage$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
		throw new AssertionError();
	}
}
