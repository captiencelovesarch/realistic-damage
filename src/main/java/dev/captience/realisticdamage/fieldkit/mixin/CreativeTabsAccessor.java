package dev.captience.realisticdamage.fieldkit.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets the creative tabs be rebuilt when a switch flips (2.0). Vanilla rebuilds
 * them only when the feature flags, permissions or registries change.
 *
 * <p>Rebuilt in place with the parameters already in use, never by forgetting
 * them: Fabric's tab pages read the cached parameters on every frame of an open
 * creative screen and do not expect them to be missing (forgetting them was a
 * client crash the moment a switch went off under an open Field kit tab).
 */
@Mixin(CreativeModeTabs.class)
public interface CreativeTabsAccessor {
	@Accessor("CACHED_PARAMETERS")
	static CreativeModeTab.ItemDisplayParameters realisticdamage$cachedParameters() {
		throw new AssertionError();
	}

	@Accessor("CACHED_PARAMETERS")
	static void realisticdamage$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
		throw new AssertionError();
	}

	@Invoker("buildAllTabContents")
	static void realisticdamage$buildAllTabContents(CreativeModeTab.ItemDisplayParameters parameters) {
		throw new AssertionError();
	}
}
