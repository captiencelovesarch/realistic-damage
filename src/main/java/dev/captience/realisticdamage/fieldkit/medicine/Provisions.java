package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.FieldKitEffect;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The Medicine half's food and drink (2.0, phase 4, round 3): coffee, spirits,
 * the blood sausage and the iron tonic.
 *
 * <p>Like the brewed effects, the drinks' effects are markers the body's own
 * systems ask about (the {@code fieldkit.mixin} hooks); what they did stays
 * done when they wear off, and milk clears them like any other.
 *
 * <ul>
 *   <li><b>Coffee</b> (cocoa beans, a water bottle and sugar): Caffeinated for
 *   three minutes. Sedation counts for less, microsleeps don't come, and the
 *   heart runs faster, so open wounds bleed more.</li>
 *   <li><b>Spirits</b> (a water bottle and a potato, brewed): Tipsy. Half the
 *   limp and the sore arm, the world doubles as it moves, and wounds bleed a
 *   little more.</li>
 *   <li><b>Blood sausage</b> (a raw porkchop and a blood bag, cooked): food
 *   that gives back more Blood than any other.</li>
 *   <li><b>Iron tonic</b> (an awkward potion and an iron nugget, brewed): Blood
 *   comes back twice as fast while it lasts.</li>
 * </ul>
 */
public final class Provisions {
	private Provisions() {}

	/** Wide awake: Sedation counts for less, no microsleeps, a faster heart. */
	public static final Holder<MobEffect> CAFFEINATED =
			Kit.effect("caffeinated", new Effect(MobEffectCategory.BENEFICIAL, 0x6B4226), Half.MEDICINE);
	/** A weaker numbing, a doubled world and a thinner blood. */
	public static final Holder<MobEffect> TIPSY =
			Kit.effect("tipsy", new Effect(MobEffectCategory.NEUTRAL, 0xD9962B), Half.MEDICINE);
	/** Blood comes back faster. */
	public static final Holder<MobEffect> IRON_TONIC =
			Kit.effect("iron_tonic", new Effect(MobEffectCategory.BENEFICIAL, 0xA8432F), Half.MEDICINE);

	/** Brewed from a water bottle; no long or strong brew, a drink is a drink. */
	public static final Holder<Potion> SPIRITS = Kit.potion("spirits",
			new Potion("spirits", new MobEffectInstance(TIPSY, Balance.SPIRITS_TICKS)));
	public static final Holder<Potion> IRON_TONIC_POTION = Kit.potion("iron_tonic",
			new Potion("iron_tonic", new MobEffectInstance(IRON_TONIC, Balance.IRON_TONIC_TICKS)));
	public static final Holder<Potion> LONG_IRON_TONIC = Kit.potion("long_iron_tonic",
			new Potion("iron_tonic", new MobEffectInstance(IRON_TONIC, Balance.IRON_TONIC_LONG_TICKS)));

	/** Drunk like honey and handed back as the bottle; no particles, the icon says it. */
	public static final Item COFFEE = Kit.item("coffee", Item::new, new Item.Properties()
			.stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink()
					.onConsume(new ApplyStatusEffectsConsumeEffect(
							new MobEffectInstance(CAFFEINATED, Balance.COFFEE_TICKS, 0, false, false, true)))
					.build())
			.usingConvertsTo(Items.GLASS_BOTTLE), Half.MEDICINE);
	/** Edible raw, like the pork in it, and no more Blood than that. */
	public static final Item RAW_BLOOD_SAUSAGE = Kit.item("raw_blood_sausage", Item::new,
			new Item.Properties().food(Foods.PORKCHOP), Half.MEDICINE);
	/** A cooked porkchop's worth of food, and Blood on top of it. */
	public static final Item BLOOD_SAUSAGE = Kit.item("blood_sausage", BloodSausageItem::new,
			new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()),
			Half.MEDICINE);

	public static void init() {
		FabricPotionBrewingBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.WATER, Ingredient.of(Items.POTATO), SPIRITS);
			builder.registerPotionRecipe(Potions.AWKWARD, Ingredient.of(Items.IRON_NUGGET), IRON_TONIC_POTION);
			builder.registerPotionRecipe(IRON_TONIC_POTION, Ingredient.of(Items.REDSTONE), LONG_IRON_TONIC);
		});
	}

	/** On coffee, with Medicine on. */
	public static boolean caffeinated(LivingEntity entity) {
		return BrewedEffects.active(entity, CAFFEINATED);
	}

	/** Tipsy, with Medicine on. */
	public static boolean tipsy(LivingEntity entity) {
		return BrewedEffects.active(entity, TIPSY);
	}

	/** On an iron tonic, with Medicine on. */
	public static boolean tonic(LivingEntity entity) {
		return BrewedEffects.active(entity, IRON_TONIC);
	}

	/** The drinks' effects carry no work of their own: the hooks ask whether they're on. */
	static final class Effect extends FieldKitEffect {
		Effect(MobEffectCategory category, int colour) {
			super(Half.MEDICINE, category, colour);
		}
	}
}
