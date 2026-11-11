package dev.captience.realisticdamage.fieldkit.brew;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Kit;
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The brewed effects' potions (2.0, phase 3), and how to brew them.
 *
 * <p>Each starts from an awkward potion, as vanilla's do; redstone makes the
 * long brew and glowstone the strong one where the effect has a level to
 * raise. Gunpowder and dragon's breath make the splash and lingering forms
 * through vanilla's own container recipes, and 8 arrows around a drinkable
 * one make 8 tipped arrows ({@link FieldKitArrowRecipe}).
 *
 * <p>The mixes are always registered; the brewing stand asks the switch when
 * it brews (see {@code BrewingGateMixin}).
 */
public final class BrewedPotions {
	private BrewedPotions() {}

	public static final Holder<Potion> HAEMORRHAGE = Kit.potion("haemorrhage",
			new Potion("haemorrhage", new MobEffectInstance(BrewedEffects.HAEMORRHAGE, Balance.HAEMORRHAGE_TICKS)));
	public static final Holder<Potion> LONG_HAEMORRHAGE = Kit.potion("long_haemorrhage",
			new Potion("haemorrhage", new MobEffectInstance(BrewedEffects.HAEMORRHAGE, Balance.HAEMORRHAGE_LONG_TICKS)));
	public static final Holder<Potion> STRONG_HAEMORRHAGE = Kit.potion("strong_haemorrhage",
			new Potion("haemorrhage", new MobEffectInstance(BrewedEffects.HAEMORRHAGE,
					Balance.HAEMORRHAGE_STRONG_TICKS, 1)));

	/** No strong brew: the surge is the body's own and has no level to raise. */
	public static final Holder<Potion> ADRENALINE = Kit.potion("adrenaline",
			new Potion("adrenaline", new MobEffectInstance(BrewedEffects.ADRENALINE, Balance.ADRENALINE_POTION_TICKS)));
	public static final Holder<Potion> LONG_ADRENALINE = Kit.potion("long_adrenaline",
			new Potion("adrenaline", new MobEffectInstance(BrewedEffects.ADRENALINE,
					Balance.ADRENALINE_POTION_LONG_TICKS)));

	/**
	 * One whole dose to drink (the sleeping draught), throw or tip; the strong
	 * brew is two. No long brew: how long it lasts is the body's business.
	 */
	public static final Holder<Potion> SEDATION = Kit.potion("sedation",
			new Potion("sedation", new MobEffectInstance(BrewedEffects.SEDATION, Balance.SEDATION_TICKS)));
	public static final Holder<Potion> STRONG_SEDATION = Kit.potion("strong_sedation",
			new Potion("sedation", new MobEffectInstance(BrewedEffects.SEDATION, Balance.SEDATION_TICKS, 1)));

	/** About a minute, or two from the long brew. No strong one: numb is numb. */
	public static final Holder<Potion> NUMBING = Kit.potion("numbing",
			new Potion("numbing", new MobEffectInstance(BrewedEffects.NUMBING, Balance.NUMBING_TICKS)));
	public static final Holder<Potion> LONG_NUMBING = Kit.potion("long_numbing",
			new Potion("numbing", new MobEffectInstance(BrewedEffects.NUMBING, Balance.NUMBING_LONG_TICKS)));

	public static void init() {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.POPPY), SEDATION);
			builder.registerPotionRecipe(SEDATION, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_SEDATION);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.PACKED_ICE), NUMBING);
			builder.registerPotionRecipe(NUMBING, Ingredient.of(Items.REDSTONE), LONG_NUMBING);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.SWEET_BERRIES), HAEMORRHAGE);
			builder.registerPotionRecipe(HAEMORRHAGE, Ingredient.of(Items.REDSTONE), LONG_HAEMORRHAGE);
			builder.registerPotionRecipe(HAEMORRHAGE, Ingredient.of(Items.GLOWSTONE_DUST), STRONG_HAEMORRHAGE);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.GLOW_BERRIES), ADRENALINE);
			builder.registerPotionRecipe(ADRENALINE, Ingredient.of(Items.REDSTONE), LONG_ADRENALINE);
		});
	}
}
