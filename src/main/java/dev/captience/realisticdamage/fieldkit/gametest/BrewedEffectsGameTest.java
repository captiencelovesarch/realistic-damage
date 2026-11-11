package dev.captience.realisticdamage.fieldkit.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.fieldkit.FieldKitTab;
import dev.captience.realisticdamage.fieldkit.Kit;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.BrewedPotions;
import dev.captience.realisticdamage.fieldkit.mixin.BrewingStandAccessor;
import dev.captience.realisticdamage.gametest.TestPlayer;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import dev.captience.realisticdamage.systems.BleedingSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The brewed effects, and every switch check proved on a real field kit item
 * (2.0, phase 3, round 1: Haemorrhage and Adrenaline, both Weapons).
 *
 * <p>Phase 2's checks were built before there was anything to refuse; here
 * each one refuses a real potion, through the route the game uses: a brewing
 * stand, a crafting grid, a loot table, a villager's trade, the use key, an
 * arrow in flight, milk. Each asks with the switch off, then on, with nothing
 * reloaded between.
 *
 * <p>Every test touching the switches puts them back, saved. The ones that
 * change a switch and restore it within one tick share the isolated batch; the
 * ones that hold a switch across ticks get a test environment (and so a batch)
 * of their own, because the switches are one setting for the whole server and
 * two tests holding them different ways at once would each see the other's.
 */
public class BrewedEffectsGameTest {
	private interface Body {
		void run(ServerConfig config) throws Exception;
	}

	private static void withSwitches(GameTestHelper helper, Body body) {
		ServerConfig config = ServerConfig.get();
		boolean weapons = config.enableWeapons;
		boolean medicine = config.enableMedicine;
		try {
			body.run(config);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		} finally {
			config.enableWeapons = weapons;
			config.enableMedicine = medicine;
			ServerConfig.save();
		}
	}

	private static ItemStack potion(net.minecraft.world.item.Item form,
			net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
		return PotionContents.createItemStack(form, potion);
	}

	/**
	 * A real, connected player in survival, standing in the test: adding an
	 * effect or showing an action-bar line sends a packet, which vanilla's bare
	 * mock has no connection for, and vanilla's connected mock is hard-wired to
	 * creative, which the body systems ignore. Disconnect it when done.
	 */
	private static TestPlayer player(GameTestHelper helper, Vec3 relative, String name) {
		return TestPlayer.spawn(helper.getLevel(), helper.absoluteVec(relative), 0.0F, name);
	}

	private static void gone(ServerPlayer... players) {
		for (ServerPlayer player : players) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));
		}
	}

	// --- brewing ---

	/**
	 * A real brewing stand with awkward potions, sweet berries and blaze powder
	 * does not start while Weapons is off, and starts once it is on.
	 */
	@GameTest(environment = "realisticdamage:fieldkit_stand", maxTicks = 40)
	public void theStandBrewsOnlyWhileWeaponsIsOn(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean weapons = config.enableWeapons;
		config.enableWeapons = false;
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, Blocks.BREWING_STAND);
		BrewingStandBlockEntity stand = helper.getBlockEntity(pos, BrewingStandBlockEntity.class);
		for (int slot = 0; slot < 3; slot++) {
			stand.setItem(slot, potion(Items.POTION, Potions.AWKWARD));
		}
		stand.setItem(3, new ItemStack(Items.SWEET_BERRIES));
		stand.setItem(4, new ItemStack(Items.BLAZE_POWDER, 4));
		ServerLevel level = helper.getLevel();
		helper.assertFalse(level.potionBrewing().hasMix(potion(Items.POTION, Potions.AWKWARD),
				new ItemStack(Items.SWEET_BERRIES)), "Weapons off: awkward + sweet berries must not be a mix");
		helper.runAfterDelay(5, () -> {
			int idle = ((BrewingStandAccessor) stand).realisticdamage$brewTime();
			config.enableWeapons = true;
			helper.runAfterDelay(5, () -> {
				int brewing = ((BrewingStandAccessor) stand).realisticdamage$brewTime();
				ItemStack made = level.potionBrewing().mix(new ItemStack(Items.SWEET_BERRIES),
						potion(Items.POTION, Potions.AWKWARD));
				config.enableWeapons = weapons;
				ServerConfig.save();
				helper.assertTrue(idle == 0, "Weapons off: the stand must not start, brew time " + idle);
				helper.assertTrue(brewing > 0, "Weapons on: the same stand must start brewing");
				helper.assertTrue(made.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
								PotionContents.EMPTY).is(BrewedPotions.HAEMORRHAGE),
						"the mix must make Haemorrhage, got " + made);
				helper.succeed();
			});
		});
	}

	// --- crafting ---

	/** Eight arrows round a drinkable field kit potion make eight tipped arrows, and the bottle comes back. */
	@GameTest(environment = "realisticdamage:isolated")
	public void tippedArrowsCraftFromADrinkablePotion(GameTestHelper helper) {
		withSwitches(helper, config -> {
			ServerLevel level = helper.getLevel();
			CraftingInput ours = grid(potion(Items.POTION, BrewedPotions.HAEMORRHAGE));
			CraftingInput vanilla = grid(potion(Items.POTION, Potions.POISON));

			config.enableWeapons = false;
			helper.assertTrue(craft(level, ours).isEmpty(), "Weapons off: no tipped arrows");

			config.enableWeapons = true;
			Optional<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> recipe = craft(level, ours);
			helper.assertTrue(recipe.isPresent(), "Weapons on: the arrows must craft");
			ItemStack arrows = recipe.get().value().assemble(ours);
			helper.assertTrue(arrows.is(Items.TIPPED_ARROW) && arrows.getCount() == 8
							&& arrows.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS)
									.is(BrewedPotions.HAEMORRHAGE),
					"eight arrows of Haemorrhage, got " + arrows);
			helper.assertTrue(recipe.get().value().getRemainingItems(ours).get(4).is(Items.GLASS_BOTTLE),
					"the bottle comes back");
			helper.assertTrue(craft(level, vanilla).isEmpty(),
					"vanilla's drinkable potions must not take this route: vanilla's arrows don't change");
		});
		helper.succeed();
	}

	private static CraftingInput grid(ItemStack centre) {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			items.add(i == 4 ? centre.copy() : new ItemStack(Items.ARROW));
		}
		return CraftingInput.of(3, 3, items);
	}

	private static Optional<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> craft(
			ServerLevel level, CraftingInput input) {
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
	}

	// --- loot, trades, the tab ---

	/**
	 * A loot table that drops Haemorrhage with no condition of its own: the net
	 * takes it out while Weapons is off.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void theLootNetStripsASwitchedOffPotion(GameTestHelper helper) {
		withSwitches(helper, config -> {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
					RealisticDamage.id("test/field_kit_potion"));
			LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
			LootParams params = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);

			config.enableWeapons = false;
			helper.assertTrue(table.getRandomItems(params, 3L).isEmpty(), "Weapons off: the potion must not drop");

			config.enableWeapons = true;
			List<ItemStack> drops = table.getRandomItems(params, 3L);
			helper.assertTrue(drops.size() == 1 && Kit.halves(drops.getFirst()).size() == 1,
					"Weapons on: the potion must drop, got " + drops);
		});
		helper.succeed();
	}

	/**
	 * A trade selling Haemorrhage with no predicate of its own is never rolled
	 * while Weapons is off; one already on a villager's list gives nothing.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void tradesInHaemorrhageFollowTheSwitch(GameTestHelper helper) {
		withSwitches(helper, config -> {
			String json = """
					{ "wants": { "id": "minecraft:emerald", "count": 1 },
					  "gives": { "id": "minecraft:potion", "count": 1,
					             "components": { "minecraft:potion_contents": { "potion": "realisticdamage:haemorrhage" } } } }
					""";
			VillagerTrade trade = VillagerTrade.CODEC.parse(
							helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
							JsonParser.parseString(json))
					.getOrThrow(AssertionError::new);
			Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
			villager.setNoAi(true);
			LootContext context = new LootContext.Builder(new LootParams.Builder(helper.getLevel())
					.withParameter(LootContextParams.ORIGIN, villager.position())
					.withParameter(LootContextParams.THIS_ENTITY, villager)
					.withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
					.create(LootContextParamSets.VILLAGER_TRADE)).create(Optional.empty());

			config.enableWeapons = false;
			helper.assertTrue(trade.getOffer(context) == null, "Weapons off: the trade must not roll");
			config.enableWeapons = true;
			helper.assertTrue(trade.getOffer(context) != null, "Weapons on: the trade rolls");

			villager.getOffers().clear();
			villager.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 1),
					potion(Items.POTION, BrewedPotions.HAEMORRHAGE), 10, 1, 0.0F));
			MerchantContainer counter = new MerchantContainer(villager);
			config.enableWeapons = false;
			counter.setItem(0, new ItemStack(Items.EMERALD));
			helper.assertTrue(counter.getItem(2).isEmpty(), "Weapons off: the offer already on the list gives nothing");
			config.enableWeapons = true;
			counter.setItem(0, new ItemStack(Items.EMERALD));
			helper.assertTrue(!counter.getItem(2).isEmpty(), "Weapons on: the same offer gives the potion");
			villager.discard();
		});
		helper.succeed();
	}

	/** The creative tab's contents follow the switch: every form of every brew, or nothing. */
	@GameTest(environment = "realisticdamage:isolated")
	public void theCreativeTabFollowsTheSwitch(GameTestHelper helper) {
		withSwitches(helper, config -> {
			config.enableWeapons = false;
			config.enableMedicine = true;
			helper.assertTrue(FieldKitTab.contents().stream().noneMatch(stack -> Kit.halves(stack)
							.contains(dev.captience.realisticdamage.fieldkit.Half.WEAPONS)),
					"Weapons off: no Weapons stack in the tab");
			config.enableWeapons = true;
			int forms = FieldKitTab.contents().size();
			helper.assertTrue(forms >= Kit.potions().size() * 4,
					"Weapons on: every form of every brew, got " + forms);
		});
		helper.succeed();
	}

	// --- use, effects, milk ---

	/** The use key on a switched-off potion does nothing; switched on, it starts the drink. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aSwitchedOffPotionCannotBeUsed(GameTestHelper helper) {
		withSwitches(helper, config -> {
			TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-use");
			ItemStack stack = potion(Items.POTION, BrewedPotions.HAEMORRHAGE);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			config.enableWeapons = false;
			InteractionResult off = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(off == InteractionResult.FAIL, "Weapons off: the drink must be refused, got " + off);
			config.enableWeapons = true;
			InteractionResult on = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(on != InteractionResult.FAIL, "Weapons on: the drink starts, got " + on);
			gone(player);
		});
		helper.succeed();
	}

	/**
	 * A switched-off effect is refused, and one already running ends on the
	 * next tick when its switch goes off — on a real, ticking zombie.
	 */
	@GameTest(environment = "realisticdamage:fieldkit_effects", maxTicks = 40)
	public void effectsAreRefusedOffAndEndWhenSwitchedOff(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean weapons = config.enableWeapons;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.setNoAi(true);
		config.enableWeapons = false;
		boolean refused = !zombie.addEffect(new MobEffectInstance(BrewedEffects.HAEMORRHAGE, 400));
		config.enableWeapons = true;
		boolean taken = zombie.addEffect(new MobEffectInstance(BrewedEffects.HAEMORRHAGE, 400));
		config.enableWeapons = false;
		helper.runAfterDelay(3, () -> {
			boolean ended = !zombie.hasEffect(BrewedEffects.HAEMORRHAGE);
			config.enableWeapons = weapons;
			ServerConfig.save();
			helper.assertTrue(refused, "Weapons off: Haemorrhage must be refused");
			helper.assertTrue(taken, "Weapons on: Haemorrhage must be taken");
			helper.assertTrue(ended, "switched off, a running Haemorrhage must end at once");
			zombie.discard();
			helper.succeed();
		});
	}

	/** Milk clears Haemorrhage, and the bleed it opened stays open: what it did stays done. */
	@GameTest(environment = "realisticdamage:isolated")
	public void milkClearsItButTheBleedStays(GameTestHelper helper) {
		withSwitches(helper, config -> {
			config.enableWeapons = true;
			TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-milk");
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			data.clearWounds();
			player.addEffect(new MobEffectInstance(BrewedEffects.HAEMORRHAGE, 400));
			data.addWound(BodyPart.LEFT_ARM, WoundSeverity.MODERATE);
			new ItemStack(Items.MILK_BUCKET).finishUsingItem(helper.getLevel(), player);
			helper.assertFalse(player.hasEffect(BrewedEffects.HAEMORRHAGE), "milk must clear Haemorrhage");
			helper.assertTrue(data.bleeding(), "the bleed it opened must stay open");
			gone(player);
		});
		helper.succeed();
	}

	// --- Haemorrhage ---

	private static TestPlayer swordAttacker(GameTestHelper helper) {
		TestPlayer attacker = player(helper, new Vec3(1.5, 2.0, -0.5), "fk-sword");
		attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		return attacker;
	}

	/**
	 * The glancing slash that opens nothing on a clean body (1.5 trauma, under
	 * the 2.0 minor threshold) opens a bleed under Haemorrhage — and a step
	 * worse than minor.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aGlancingSlashBleedsUnderHaemorrhage(GameTestHelper helper) {
		withSwitches(helper, config -> {
			config.enableWeapons = true;
			TestPlayer attacker = swordAttacker(helper);
			DamageSource slash = helper.getLevel().damageSources().playerAttack(attacker);

			TestPlayer clean = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-clean");
			RealisticDamageData cleanData = RDAttachments.get(clean);
			cleanData.reset();
			cleanData.clearWounds();
			TraumaPipeline.handle(clean, cleanData, slash, 1.5F, 1.5F, 1.5, false);
			helper.assertFalse(cleanData.bleeding(), "without Haemorrhage the glancing slash must not bleed");

			TestPlayer dosed = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-dosed");
			RealisticDamageData dosedData = RDAttachments.get(dosed);
			dosedData.reset();
			dosedData.clearWounds();
			dosed.addEffect(new MobEffectInstance(BrewedEffects.HAEMORRHAGE, 400));
			TraumaPipeline.handle(dosed, dosedData, slash, 1.5F, 1.5F, 1.5, false);
			helper.assertTrue(dosedData.worstWound() == WoundSeverity.MODERATE,
					"under Haemorrhage it must open, a step worse than minor, got " + dosedData.worstWound());

			// Blunt still never bleeds: the threshold is lowered, not invented.
			TestPlayer clubbed = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-clubbed");
			RealisticDamageData clubbedData = RDAttachments.get(clubbed);
			clubbedData.reset();
			clubbedData.clearWounds();
			clubbed.addEffect(new MobEffectInstance(BrewedEffects.HAEMORRHAGE, 400));
			TraumaPipeline.handle(clubbed, clubbedData, helper.getLevel().damageSources().mace(attacker),
					10, 10, 10, false);
			helper.assertFalse(clubbedData.bleeding(), "a blunt blow must not bleed even under Haemorrhage");
			gone(attacker, clean, dosed, clubbed);
		});
		helper.succeed();
	}

	/**
	 * Eight critical wounds bleed at the usual cap normally and half as much
	 * again under Haemorrhage; a hand pressed on them holds back less.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void itBleedsPastTheCapAndFightsAHandOff(GameTestHelper helper) {
		withSwitches(helper, config -> {
			config.enableWeapons = true;
			double normal = drain(helper, false, false);
			double dosed = drain(helper, true, false);
			// A body has six regions, and a second wound on a region deepens the
			// first; six critical wounds stack to more than the usual cap but
			// less than Haemorrhage's, so the ratio is the stack over the cap.
			double[] six = new double[6];
			java.util.Arrays.fill(six, WoundSeverity.CRITICAL.bloodPerSecond());
			double expected = dev.captience.realisticdamage.fieldkit.brew.HaemorrhageMath.stackedRate(six,
					dev.captience.realisticdamage.fieldkit.brew.HaemorrhageMath.cap(0))
					/ dev.captience.realisticdamage.systems.WoundMath.effectiveBleedRate(six);
			helper.assertTrue(expected > 1.3 && Math.abs(dosed / normal - expected) < 0.02,
					"Haemorrhage must let six critical wounds run past the cap: ratio " + dosed / normal
							+ ", expected " + expected);
			double pressedNormal = drain(helper, false, true);
			double pressedDosed = drain(helper, true, true);
			helper.assertTrue(pressedDosed / dosed > pressedNormal / normal + 0.1,
					"a hand must hold back less under Haemorrhage: " + pressedNormal / normal + " vs "
							+ pressedDosed / dosed);
		});
		helper.succeed();
	}

	/** Blood lost over one second from a critical wound in every region. */
	private static double drain(GameTestHelper helper, boolean haemorrhage, boolean pressed) {
		TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-drain");
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		for (BodyPart part : BodyPart.values()) {
			data.addWound(part, WoundSeverity.CRITICAL);
		}
		if (haemorrhage) {
			player.addEffect(new MobEffectInstance(BrewedEffects.HAEMORRHAGE, 400));
		}
		double before = data.blood();
		for (int tick = 0; tick < 20; tick++) {
			if (pressed) {
				data.refreshPressure(true);
			}
			BleedingSystem.tick(player, data);
		}
		double lost = before - data.blood();
		gone(player);
		return lost;
	}

	/**
	 * A Haemorrhage arrow, actually shot, opens a bleed where it hits a zombie;
	 * a plain arrow shot the same way opens less. With Weapons off it is a plain
	 * arrow.
	 */
	@GameTest(environment = "realisticdamage:fieldkit_held_on", maxTicks = 60)
	public void aHaemorrhageArrowOpensABleedWhereItHits(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean weapons = config.enableWeapons;
		config.enableWeapons = true;
		Zombie plainTarget = target(helper, new BlockPos(1, 2, 5));
		Zombie dosedTarget = target(helper, new BlockPos(5, 2, 5));
		shoot(helper, plainTarget, new ItemStack(Items.ARROW));
		shoot(helper, dosedTarget, potion(Items.TIPPED_ARROW, BrewedPotions.HAEMORRHAGE));
		helper.runAfterDelay(20, () -> {
			WoundSeverity plain = RDAttachments.get(plainTarget).worstWound();
			WoundSeverity dosed = RDAttachments.get(dosedTarget).worstWound();
			config.enableWeapons = weapons;
			ServerConfig.save();
			helper.assertTrue(dosed != null && (plain == null || dosed.ordinal() > plain.ordinal()),
					"the Haemorrhage arrow must open a worse bleed than a plain one: " + plain + " vs " + dosed);
			helper.assertTrue(dosed.ordinal() >= WoundSeverity.MODERATE.ordinal(),
					"at least the arrow's own moderate wound, got " + dosed);
			plainTarget.discard();
			dosedTarget.discard();
			helper.succeed();
		});
	}

	private static Zombie target(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setNoAi(true);
		// Bare, so both targets take the same arrow the same way.
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		return zombie;
	}

	private static void shoot(GameTestHelper helper, Zombie target, ItemStack ammo) {
		Vec3 aim = target.position().add(0.0, 1.2, 0.0);
		Vec3 from = aim.add(0.0, 0.0, -2.5);
		Arrow arrow = new Arrow(helper.getLevel(), from.x, from.y, from.z, ammo, null);
		arrow.setDeltaMovement(0.0, 0.0, 1.2);
		helper.getLevel().addFreshEntity(arrow);
	}

	// --- Adrenaline ---

	/**
	 * Drinking it fires the surge now, the crash follows when it ends, and a
	 * second dose before the heart has recovered is refused.
	 */
	@GameTest(environment = "realisticdamage:fieldkit_held_on", maxTicks = 40)
	public void adrenalineSurgesOnDemandAndCannotBeChained(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean weapons = config.enableWeapons;
		config.enableWeapons = true;
		TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-adrenaline");
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.adrenaline().reset();
		boolean took = player.addEffect(new MobEffectInstance(BrewedEffects.ADRENALINE, 400));
		AdrenalineMath.Phase during = data.adrenaline().phase;
		int refractory = data.adrenaline().refractory;
		helper.runAfterDelay(3, () -> {
			AdrenalineMath.Phase stillSurging = data.adrenaline().phase;
			// It ends (milk, here; running out is the same path): the crash follows.
			player.removeEffect(BrewedEffects.ADRENALINE);
			helper.runAfterDelay(3, () -> {
				AdrenalineMath.Phase after = data.adrenaline().phase;
				boolean again = player.addEffect(new MobEffectInstance(BrewedEffects.ADRENALINE, 400));
				config.enableWeapons = weapons;
				ServerConfig.save();
				gone(player);
				helper.assertTrue(took && during == AdrenalineMath.Phase.SURGE,
						"the dose must fire the surge at full health, got " + during);
				helper.assertTrue(refractory > 0, "it must spend the surge's refractory");
				helper.assertTrue(stillSurging == AdrenalineMath.Phase.SURGE,
						"the surge must last while the effect does, got " + stillSurging);
				helper.assertTrue(after == AdrenalineMath.Phase.CRASH, "the crash must follow, got " + after);
				helper.assertFalse(again, "a second dose before the heart recovers must be refused");
				helper.succeed();
			});
		});
	}

	/** Under Adrenaline the same wound bleeds harder. */
	@GameTest(environment = "realisticdamage:isolated")
	public void adrenalineMakesWoundsBleedHarder(GameTestHelper helper) {
		withSwitches(helper, config -> {
			config.enableWeapons = true;
			double calm = oneWound(helper, false);
			double racing = oneWound(helper, true);
			helper.assertTrue(Math.abs(racing / calm - dev.captience.realisticdamage.config.Balance.ADRENALINE_BLEED_FACTOR)
					< 0.02, "a racing heart must bleed harder by its factor, ratio " + racing / calm);
		});
		helper.succeed();
	}

	private static double oneWound(GameTestHelper helper, boolean adrenaline) {
		TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-wound");
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
		if (adrenaline) {
			player.addEffect(new MobEffectInstance(BrewedEffects.ADRENALINE, 400));
		}
		double before = data.blood();
		for (int tick = 0; tick < 20; tick++) {
			BleedingSystem.tick(player, data);
		}
		double lost = before - data.blood();
		gone(player);
		return lost;
	}
}
