package dev.captience.realisticdamage.fieldkit.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.RulesSync;
import dev.captience.realisticdamage.fieldkit.SwitchedRecipe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The field kit's switch checks, on a running server (2.0, phase 2).
 *
 * <p>Each check is driven through the same code the game uses — a recipe parsed
 * from the JSON a datapack would ship and matched against a real crafting grid,
 * a loot table rolled, a villager trade rolled from a real villager — and asked
 * twice, once per switch position, with nothing reloaded in between. That second
 * half is the whole promise: no restart, no {@code /reload}.
 *
 * <p>Phase 2 ships no field kit items yet, so these use vanilla items behind the
 * field kit's own switch machinery. The checks that need a field kit item to
 * refuse (brewing, use, effects, the loot and trade nets) are proved in phase 3
 * with the first real ones.
 *
 * <p>{@code ServerConfig} is one object for the whole run, so every test here
 * runs in the isolated batch and puts both switches back, saved, in
 * {@code finally} — exactly as {@code ConfigCommandGameTest} does and for the
 * same reason.
 */
public class FieldKitSwitchGameTest {
	private static final String SWITCHED_RECIPE = """
			{ "type": "realisticdamage:switched", "switch": "medicine",
			  "recipe": { "type": "minecraft:crafting_shapeless",
			              "ingredients": [ "minecraft:paper", "minecraft:string" ],
			              "result": { "id": "minecraft:map", "count": 3 } } }
			""";

	private static final String SWITCHED_LOOT = """
			{ "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "minecraft:paper",
			    "conditions": [ { "condition": "realisticdamage:field_kit_on", "half": "weapons" } ] } ] } ] }
			""";

	private static final String SWITCHED_TRADE = """
			{ "wants": { "id": "minecraft:emerald", "count": 1 },
			  "gives": { "id": "minecraft:paper", "count": 4 },
			  "merchant_predicate": { "condition": "realisticdamage:field_kit_on", "half": "medicine" } }
			""";

	private interface Body {
		void run(ServerConfig config) throws Exception;
	}

	/** Runs with both switches as found afterwards, whatever the body did or threw. */
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
		helper.succeed();
	}

	private static <T> T parse(GameTestHelper helper, com.mojang.serialization.Codec<T> codec, String json) {
		DynamicOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		return codec.parse(ops, JsonParser.parseString(json)).getOrThrow(message -> new AssertionError(message));
	}

	/**
	 * A wrapped recipe crafts while its half is on and is not a match while it
	 * is off — the same grid, the same loaded recipe, only the switch moved.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aSwitchedRecipeCraftsOnlyWhileItsHalfIsOn(GameTestHelper helper) {
		withSwitches(helper, config -> {
			Recipe<?> parsed = parse(helper, Recipe.CODEC, SWITCHED_RECIPE);
			helper.assertTrue(parsed instanceof SwitchedRecipe, "the type must be registered, got " + parsed);
			SwitchedRecipe recipe = (SwitchedRecipe) parsed;
			helper.assertTrue(recipe.half() == Half.MEDICINE, "the switch is read from the JSON");
			CraftingInput grid = CraftingInput.of(2, 1, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.STRING)));
			CraftingInput wrong = CraftingInput.of(2, 1, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.STICK)));
			ServerLevel level = helper.getLevel();

			config.enableMedicine = false;
			config.enableWeapons = true;
			helper.assertFalse(recipe.matches(grid, level), "with Medicine off the grid must not match");

			config.enableMedicine = true;
			helper.assertTrue(recipe.matches(grid, level), "with Medicine on the same grid must match");
			helper.assertFalse(recipe.matches(wrong, level), "the inner recipe still decides what matches");
			ItemStack made = recipe.assemble(grid);
			helper.assertTrue(made.is(Items.MAP) && made.getCount() == 3, "the inner recipe makes the result, got " + made);

			config.enableWeapons = false;
			helper.assertTrue(recipe.matches(grid, level), "the other half's switch has nothing to do with it");

			config.enableMedicine = false;
			helper.assertFalse(recipe.matches(grid, level), "and off again, without a reload");
		});
	}

	/** Only a crafting recipe can be switched: anything else is refused when the datapack loads. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aSwitchedRecipeWrapsOnlyCrafting(GameTestHelper helper) {
		DynamicOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		String smelting = """
				{ "type": "realisticdamage:switched", "switch": "weapons",
				  "recipe": { "type": "minecraft:smelting", "ingredient": "minecraft:iron_ore",
				              "result": { "id": "minecraft:iron_ingot" }, "cookingtime": 200 } }
				""";
		helper.assertTrue(Recipe.CODEC.parse(ops, JsonParser.parseString(smelting)).isError(),
				"a smelting recipe inside a switched one must be a load error, not a silent pass");
		helper.succeed();
	}

	/** A loot entry carrying the condition drops while its half is on and not while it is off. */
	@GameTest(environment = "realisticdamage:isolated")
	public void lootIsCheckedWhenItIsRolled(GameTestHelper helper) {
		withSwitches(helper, config -> {
			LootTable table = parse(helper, LootTable.DIRECT_CODEC, SWITCHED_LOOT);
			LootParams params = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);

			config.enableWeapons = false;
			config.enableMedicine = true;
			helper.assertTrue(table.getRandomItems(params, 7L).isEmpty(), "Weapons off: nothing must drop");

			config.enableWeapons = true;
			List<ItemStack> drops = table.getRandomItems(params, 7L);
			helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Items.PAPER),
					"Weapons on: the entry must drop, got " + drops);
		});
	}

	/**
	 * A trade carrying the condition as its merchant predicate is offered while
	 * its half is on and never rolled while it is off — through a real
	 * villager's trade context, the one {@code AbstractVillager} builds.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void tradesAreCheckedWhenTheyAreRolled(GameTestHelper helper) {
		withSwitches(helper, config -> {
			VillagerTrade trade = parse(helper, VillagerTrade.CODEC, SWITCHED_TRADE);
			Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
			Vec3 at = villager.position();
			LootContext context = new LootContext.Builder(new LootParams.Builder(helper.getLevel())
					.withParameter(LootContextParams.ORIGIN, at)
					.withParameter(LootContextParams.THIS_ENTITY, villager)
					.withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
					.create(LootContextParamSets.VILLAGER_TRADE))
					.create(Optional.empty());

			config.enableMedicine = false;
			helper.assertTrue(trade.getOffer(context) == null, "Medicine off: the trade must not be offered");

			config.enableMedicine = true;
			MerchantOffer offer = trade.getOffer(context);
			helper.assertTrue(offer != null && offer.getResult().is(Items.PAPER),
					"Medicine on: the trade must be offered, got " + offer);
			villager.discard();
		});
	}

	/**
	 * What the clients are told is what the server holds: the same words the
	 * command prints, for every setting, and a flipped switch shows at once.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void theClientsAreToldEverySetting(GameTestHelper helper) {
		withSwitches(helper, config -> {
			config.enableWeapons = false;
			config.enableMedicine = false;
			Map<String, String> before = RulesSync.values();
			helper.assertTrue("false".equals(before.get("enableWeapons")) && "false".equals(before.get("enableMedicine")),
					"both switches must read off, got " + before);
			MinecraftServer server = helper.getLevel().getServer();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					"rdmg config enableMedicine true");
			helper.assertTrue(config.enableMedicine, "/rdmg config must reach the new switch");
			Map<String, String> after = RulesSync.values();
			helper.assertTrue("true".equals(after.get("enableMedicine")), "the payload must follow, got " + after);
			helper.assertTrue(!before.equals(after), "a change must make a different payload, or nobody is told");
		});
	}
}
