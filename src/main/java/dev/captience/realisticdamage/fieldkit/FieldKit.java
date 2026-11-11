package dev.captience.realisticdamage.fieldkit;

import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The field kit (2.0): about forty survival items in two halves, Weapons and
 * Medicine, each behind its own server switch.
 *
 * <p>Its own entrypoint, after the core mod's, and everything it adds lives in
 * this package: the core mod does not know the field kit exists, and with both
 * switches off the game plays exactly as the core mod says — food and time are
 * the only cure.
 *
 * <p>What makes "off" work without a restart (see {@code 2.0_PLAN.md}, "The two
 * switches"): everything is always registered, and every route to an item asks
 * its half at the moment it is taken — a crafting recipe ({@link SwitchedRecipe}),
 * the brewing stand, a loot roll ({@link HalfOnCondition}, {@link LootGate}), a
 * trade roll, a use ({@link Gate}) and an effect's every tick
 * ({@link FieldKitEffect}).
 */
public final class FieldKit implements ModInitializer {
	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(RulesPayload.TYPE, RulesPayload.CODEC);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, RealisticDamage.id("switched"),
				SwitchedRecipe.SERIALIZER);
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, RealisticDamage.id("field_kit_on"),
				HalfOnCondition.MAP_CODEC);
		FieldKitTab.init();
		LootGate.init();
		RulesSync.init();
	}
}
