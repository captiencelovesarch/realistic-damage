package dev.captience.realisticdamage.fieldkit.client.gametest;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.captience.realisticdamage.client.AccessibilityScreen;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.RulesView;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import dev.captience.realisticdamage.fieldkit.client.HeavyEyelids;
import dev.captience.realisticdamage.fieldkit.client.LocalRules;
import dev.captience.realisticdamage.fieldkit.client.NumbHud;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import dev.captience.realisticdamage.fieldkit.client.Welcome;
import dev.captience.realisticdamage.fieldkit.client.WelcomeScreen;
import dev.captience.realisticdamage.fieldkit.client.WorldRulesScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import dev.captience.realisticdamage.fieldkit.FieldKitTab;
import dev.captience.realisticdamage.fieldkit.Kit;
import org.lwjgl.glfw.GLFW;

/**
 * The field kit's switches, as a player meets them (2.0, phase 2): the
 * first-launch question, and the World rules page in singleplayer and on a
 * real dedicated server, read-only and then as an operator.
 *
 * <p>Clicks are real clicks at the widget's place on screen, and Escape is the
 * real key, so a button that draws but does not answer fails here. Every
 * setting this class touches is put back and saved in {@code finally}: the
 * config is one object for the whole session.
 */
public class FieldKitClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		boolean weapons = context.computeOnClient(client -> ServerConfig.get().enableWeapons);
		boolean medicine = context.computeOnClient(client -> ServerConfig.get().enableMedicine);
		ServerConfig.PreyOnWounded prey = context.computeOnClient(client -> ServerConfig.get().preyOnWounded);
		try {
			firstLaunch(context);
			singleplayer(context);
			dedicatedServer(context);
		} finally {
			context.runOnClient(client -> {
				ServerConfig.get().enableWeapons = weapons;
				ServerConfig.get().enableMedicine = medicine;
				ServerConfig.get().preyOnWounded = prey;
				ServerConfig.save();
			});
		}
	}

	// --- the first-launch question ---

	private void firstLaunch(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ServerConfig.get().enableWeapons = false;
			ServerConfig.get().enableMedicine = false;
			Welcome.armForTest();
		});
		int shownBefore = context.computeOnClient(client -> Welcome.shownCount());

		// A fresh install reaching the title screen is asked.
		context.setScreen(TitleScreen::new);
		context.waitForScreen(WelcomeScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("rd_fieldkit_00_first_launch");
		boolean bothOffShown = context.computeOnClient(client -> {
			WelcomeScreen screen = (WelcomeScreen) client.gui.screen();
			return !screen.weaponsButton().getValue() && !screen.medicineButton().getValue();
		});
		if (!bothOffShown) {
			throw new AssertionError("both switches must be shown off on first launch");
		}

		// A real click on Weapons turns this install's Weapons on, and only that.
		click(context, client -> ((WelcomeScreen) client.gui.screen()).weaponsButton());
		context.waitTick();
		boolean[] after = context.computeOnClient(client ->
				new boolean[] {ServerConfig.get().enableWeapons, ServerConfig.get().enableMedicine});
		if (!after[0] || after[1]) {
			throw new AssertionError("clicking Weapons should turn on Weapons alone, got weapons="
					+ after[0] + " medicine=" + after[1]);
		}
		context.takeScreenshot("rd_fieldkit_01_first_launch_weapons_on");

		// Escape answers it, and says so on disk.
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);
		Path file = FabricLoader.getInstance().getConfigDir().resolve("realisticdamage-fieldkit.json");
		try {
			if (!Files.readString(file).contains("\"welcomeShown\": true")) {
				throw new AssertionError("leaving the question must remember it was asked, file says "
						+ Files.readString(file));
			}
		} catch (java.io.IOException e) {
			throw new AssertionError("the first-launch file was not written", e);
		}

		// And it is never asked again: a later title screen stays a title screen.
		context.setScreen(TitleScreen::new);
		context.waitTicks(10);
		boolean stillTitle = context.computeOnClient(client -> client.gui.screen() instanceof TitleScreen);
		int shownAfter = context.computeOnClient(client -> Welcome.shownCount());
		if (!stillTitle || shownAfter != shownBefore + 1) {
			throw new AssertionError("the question must show exactly once, shown " + (shownAfter - shownBefore)
					+ " times, title screen now " + stillTitle);
		}
		context.runOnClient(client -> {
			ServerConfig.get().enableWeapons = false;
			ServerConfig.save();
		});
	}

	// --- World rules, singleplayer ---

	private void singleplayer(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ServerConfig.get().enableWeapons = false;
			ServerConfig.get().enableMedicine = false;
			ServerConfig.get().preyOnWounded = ServerConfig.PreyOnWounded.VISIBLE;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitFor(client -> RulesView.received(), 100);
			context.waitTicks(10);

			// Reached the way a player reaches it: the mod's settings, then the button.
			context.setScreen(() -> new AccessibilityScreen(null));
			context.waitTicks(3);
			click(context, client -> find(client.gui.screen(), "World rules..."));
			context.waitForScreen(WorldRulesScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("rd_fieldkit_02_world_rules");

			boolean local = context.computeOnClient(client -> !LocalRules.remote() && LocalRules.canEdit());
			if (!local) {
				throw new AssertionError("singleplayer must edit the settings directly");
			}
			int total = context.computeOnClient(client -> ((WorldRulesScreen) client.gui.screen()).controls().size());
			int settings = context.computeOnClient(client ->
					dev.captience.realisticdamage.command.RealisticDamageCommands.settingFields().size());
			if (total != settings) {
				throw new AssertionError("the page must show every setting: " + total + " of " + settings);
			}

			// Medicine on, from the page: the server's own setting moves, and the
			// server tells the client, which is what the creative tab follows.
			click(context, client -> control(client, "enableMedicine"));
			context.waitFor(client -> ServerConfig.get().enableMedicine, 20);
			context.waitFor(client -> RulesView.on(Half.MEDICINE), 40);
			boolean weaponsStill = context.computeOnClient(client -> ServerConfig.get().enableWeapons);
			if (weaponsStill) {
				throw new AssertionError("turning Medicine on moved Weapons");
			}
			context.waitTicks(5);
			boolean shown = context.computeOnClient(client ->
					((CycleButton<?>) control(client, "enableMedicine")).getValue() == Boolean.TRUE);
			if (!shown) {
				throw new AssertionError("the page must show the change once the server confirms it");
			}

			// An enum setting cycles to its next value. It is further down the
			// page, so scroll to it with the wheel first, as a player would.
			reveal(context, "preyOnWounded");
			click(context, client -> control(client, "preyOnWounded"));
			context.waitFor(client -> ServerConfig.get().preyOnWounded != ServerConfig.PreyOnWounded.VISIBLE, 20);
			context.waitFor(client -> "off".equals(RulesView.values().get("preyOnWounded")), 40);
			context.waitTicks(5);
			// The server's answer updates the page where it stands: a page that
			// jumped back to the top after every click would be unusable.
			double scroll = context.computeOnClient(client -> ((WorldRulesScreen) client.gui.screen()).scrollAmount());
			Object shownValue = context.computeOnClient(client ->
					((CycleButton<?>) control(client, "preyOnWounded")).getValue());
			if (scroll <= 0 || shownValue != ServerConfig.PreyOnWounded.OFF) {
				throw new AssertionError("after a change the page must keep its place and show the new value, scroll="
						+ scroll + " shown=" + shownValue);
			}
			context.takeScreenshot("rd_fieldkit_03_world_rules_changed");
			context.setScreen(() -> null);
			context.waitTicks(3);
			creativeTabAndIcons(context, singleplayer);
			sedationAndNumbing(context, singleplayer);
			bleedingKit(context, singleplayer);
			bonesAndSenses(context, singleplayer);
			foodAndDrink(context, singleplayer);
		}
		context.waitTicks(5);
		boolean cleared = context.computeOnClient(client -> !RulesView.received());
		if (!cleared) {
			throw new AssertionError("leaving the world must forget the server's settings");
		}
		context.runOnClient(client -> {
			ServerConfig.get().enableMedicine = false;
			ServerConfig.get().preyOnWounded = ServerConfig.PreyOnWounded.VISIBLE;
			ServerConfig.save();
		});
	}

	/**
	 * The Field kit tab follows the switch (2.0, phase 3): with Weapons on it
	 * holds every form of every Weapons brew; switched off while the tab is
	 * open, the tab empties, disappears and the screen falls back to the
	 * default tab. A switched-off potion's tooltip says why it does nothing. And
	 * the new effect icons, as the inventory draws them.
	 */
	private void creativeTabAndIcons(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		CreativeModeTab tab = context.computeOnClient(client ->
				net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getValue(FieldKitTab.KEY));
		singleplayer.getServer().runCommand("rdmg config enableWeapons true");
		singleplayer.getServer().runCommand("rdmg config enableMedicine false");
		context.waitFor(client -> RulesView.on(Half.WEAPONS) && !RulesView.on(Half.MEDICINE), 60);
		singleplayer.getServer().runCommand("gamemode creative @a");
		singleplayer.getServer().runCommand("effect give @a realisticdamage:haemorrhage 120");
		singleplayer.getServer().runCommand("effect give @a realisticdamage:adrenaline 20");
		context.waitTicks(10);

		context.setScreen(() -> {
			LocalPlayer player = Minecraft.getInstance().player;
			return new CreativeModeInventoryScreen(player, player.connection.enabledFeatures(), false);
		});
		context.waitTicks(5);
		boolean selected = context.computeOnClient(client ->
				((FabricCreativeModeInventoryScreen) client.gui.screen()).setSelectedTab(tab));
		context.waitTicks(5);
		int shown = context.computeOnClient(client ->
				((CreativeModeInventoryScreen) client.gui.screen()).getMenu().items.size());
		int expected = context.computeOnClient(client -> FieldKitTab.contents().size());
		long weaponsForms = context.computeOnClient(client -> Kit.potions().stream()
				.flatMap(potion -> Kit.potionForms(potion).stream())
				.filter(stack -> Kit.halves(stack).contains(Half.WEAPONS)).count());
		boolean noMedicine = context.computeOnClient(client -> FieldKitTab.contents().stream()
				.noneMatch(stack -> Kit.halves(stack).contains(Half.MEDICINE)));
		if (!selected || shown != expected || expected != weaponsForms || !noMedicine) {
			throw new AssertionError("Weapons on, Medicine off: the Field kit tab must hold every form of every"
					+ " Weapons brew and nothing else, selected=" + selected + " shown=" + shown + " expected="
					+ expected + " weapons forms=" + weaponsForms + " no medicine=" + noMedicine);
		}
		context.takeScreenshot("rd_fieldkit_06_creative_tab");

		// Switched off with the tab open: it empties, and the screen leaves it.
		singleplayer.getServer().runCommand("rdmg config enableWeapons false");
		context.waitFor(client -> !RulesView.on(Half.WEAPONS), 60);
		context.waitTicks(5);
		boolean left = context.computeOnClient(client ->
				((FabricCreativeModeInventoryScreen) client.gui.screen()).getSelectedTab() != tab
						&& !tab.shouldDisplay());
		if (!left) {
			throw new AssertionError("Weapons off: the Field kit tab must empty and go away");
		}
		context.takeScreenshot("rd_fieldkit_07_creative_tab_gone");

		// A potion already held says why it does nothing.
		boolean says = context.computeOnClient(client -> PotionContents.createItemStack(Items.POTION,
						dev.captience.realisticdamage.fieldkit.brew.BrewedPotions.HAEMORRHAGE)
				.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL).stream()
				.anyMatch(line -> line.getString().contains("Weapons is switched off")));
		if (!says) {
			throw new AssertionError("a switched-off potion's tooltip must say Weapons is off");
		}

		// The icons, in the survival inventory's effect list.
		singleplayer.getServer().runCommand("rdmg config enableWeapons true");
		context.waitFor(client -> RulesView.on(Half.WEAPONS), 60);
		singleplayer.getServer().runCommand("gamemode survival @a");
		singleplayer.getServer().runCommand("effect give @a realisticdamage:haemorrhage 120");
		singleplayer.getServer().runCommand("effect give @a realisticdamage:adrenaline 20");
		context.waitTicks(10);
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(5);
		boolean both = context.computeOnClient(client ->
				client.player.hasEffect(dev.captience.realisticdamage.fieldkit.brew.BrewedEffects.HAEMORRHAGE)
						&& client.player.hasEffect(dev.captience.realisticdamage.fieldkit.brew.BrewedEffects.ADRENALINE));
		if (!both) {
			throw new AssertionError("both brewed effects should be on the player for the icon screenshot");
		}
		context.takeScreenshot("rd_fieldkit_08_effect_icons");
		context.setScreen(() -> null);
		singleplayer.getServer().runCommand("effect clear @a");
		singleplayer.getServer().runCommand("rdmg config enableWeapons false");
		context.waitTicks(3);
	}

	/**
	 * Sedation from behind your own eyes, and Numbing on your body diagram (2.0,
	 * phase 3, round 2). The level is set on the server directly (the test's
	 * server runs in this process) so each stage can be held still for its
	 * screenshot; everything after that — the stage reaching the client, the
	 * lids, the blackout, the numb diagram — is the real path.
	 */
	private void sedationAndNumbing(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("rdmg config enableWeapons true");
		singleplayer.getServer().runCommand("rdmg config enableMedicine true");
		context.waitFor(client -> RulesView.on(Half.WEAPONS) && RulesView.on(Half.MEDICINE), 60);
		singleplayer.getServer().runCommand("gamemode survival @a");
		context.setScreen(() -> null);
		int framesBefore = context.computeOnClient(client -> HeavyEyelids.frames());

		sedate(singleplayer, 0.3);
		context.waitFor(client -> HeavyEyelids.stage() == Stage.DROWSY, 60);
		context.waitFor(client -> HeavyEyelids.shown() > 0.6, 240);
		context.takeScreenshot("rd_fieldkit_09_drowsy_blink");

		sedate(singleplayer, 0.6);
		context.waitFor(client -> HeavyEyelids.stage() == Stage.STUMBLING, 60);
		context.waitTicks(20);
		double droop = context.computeOnClient(client -> HeavyEyelids.shown());
		if (droop < 0.25) {
			throw new AssertionError("stumbling, the lids must hang part-closed between blinks, got " + droop);
		}
		context.takeScreenshot("rd_fieldkit_10_stumbling");

		sedate(singleplayer, 1.0);
		context.waitFor(client -> HeavyEyelids.stage() == Stage.OUT
				&& dev.captience.realisticdamage.client.ClientState.snapshot() != null
				&& dev.captience.realisticdamage.client.ClientState.snapshot().unconscious(), 100);
		context.waitTicks(20);
		context.takeScreenshot("rd_fieldkit_11_out");
		int framesAfter = context.computeOnClient(client -> HeavyEyelids.frames());
		if (framesAfter <= framesBefore) {
			throw new AssertionError("the eyelids never drew");
		}

		// Milk: the drug is gone at once and the lids lift; the blackout it
		// caused is the ordinary one and lifts the ordinary way.
		singleplayer.getServer().runCommand("effect clear @a");
		context.waitFor(client -> HeavyEyelids.stage() == Stage.NONE, 60);
		String name = context.computeOnClient(client -> client.player.getName().getString());
		singleplayer.getServer().runCommand("rdmg heal " + name);
		context.waitTicks(20);

		// Numb with a broken leg: the diagram shows frost, not the break.
		int regionsBefore = context.computeOnClient(client -> NumbHud.regions());
		singleplayer.getServer().runCommand("rdmg break " + name + " left_leg");
		singleplayer.getServer().runCommand("effect give @a realisticdamage:numbing 60");
		context.waitFor(client -> client.player.hasEffect(dev.captience.realisticdamage.fieldkit.brew.BrewedEffects.NUMBING),
				60);
		context.waitTicks(20);
		int regionsAfter = context.computeOnClient(client -> NumbHud.regions());
		if (regionsAfter <= regionsBefore) {
			throw new AssertionError("numb, the body diagram must be drawn as frost");
		}
		context.takeScreenshot("rd_fieldkit_12_numb_hud");

		singleplayer.getServer().runCommand("effect clear @a");
		singleplayer.getServer().runCommand("rdmg heal " + name);
		singleplayer.getServer().runCommand("rdmg config enableWeapons false");
		singleplayer.getServer().runCommand("rdmg config enableMedicine false");
		context.waitTicks(5);
	}

	/**
	 * The bleeding kit from the player's chair (2.0, phase 4): hold the real use
	 * key with a bandage on a bleeding arm until it lands, and see a tourniquet
	 * the server put on reach this client.
	 */
	private void bleedingKit(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("rdmg config enableMedicine true");
		context.waitFor(client -> RulesView.on(Half.MEDICINE), 60);
		String name = context.computeOnClient(client -> client.player.getName().getString());
		singleplayer.getServer().runCommand("rdmg heal " + name);
		singleplayer.getServer().runCommand("rdmg bleed " + name + " left_arm minor");
		singleplayer.getServer().runCommand("item replace entity " + name + " weapon.mainhand with realisticdamage:bandage 2");
		context.setScreen(() -> null);
		context.waitTicks(10);

		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(dev.captience.realisticdamage.config.Balance.BANDAGE_USE_TICKS / 2);
		context.takeScreenshot("rd_fieldkit_13_bandaging");
		context.waitTicks(dev.captience.realisticdamage.config.Balance.BANDAGE_USE_TICKS / 2 + 15);
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitFor(client -> {
			dev.captience.realisticdamage.fieldkit.medicine.Dressings.State state =
					client.player.getAttached(dev.captience.realisticdamage.fieldkit.medicine.Dressings.STATE);
			return state != null && state.bandages.containsKey(dev.captience.realisticdamage.body.BodyPart.LEFT_ARM);
		}, 60);
		int left = context.computeOnClient(client -> client.player.getMainHandItem().getCount());
		if (left != 1) {
			throw new AssertionError("holding use with a bandage on a bleeding arm should use one up, " + left + " left");
		}

		singleplayer.getServer().runCommand("rdmg bleed " + name + " left_leg critical");
		singleplayer.getServer().runOnServer(server -> dev.captience.realisticdamage.fieldkit.medicine.Dressings.tourniquet(
				server.getPlayerList().getPlayers().getFirst(), dev.captience.realisticdamage.body.BodyPart.LEFT_LEG));
		context.waitFor(client -> {
			dev.captience.realisticdamage.fieldkit.medicine.Dressings.State state =
					client.player.getAttached(dev.captience.realisticdamage.fieldkit.medicine.Dressings.STATE);
			return state != null && state.tourniquets.containsKey(dev.captience.realisticdamage.body.BodyPart.LEFT_LEG);
		}, 60);
		context.takeScreenshot("rd_fieldkit_14_dressed");

		singleplayer.getServer().runCommand("rdmg heal " + name);
		singleplayer.getServer().runOnServer(server -> dev.captience.realisticdamage.fieldkit.medicine.Medicine.loosen(
				server.getPlayerList().getPlayers().getFirst(), server.getPlayerList().getPlayers().getFirst()));
		singleplayer.getServer().runCommand("item replace entity " + name + " weapon.mainhand with air");
		singleplayer.getServer().runCommand("rdmg config enableMedicine false");
		context.waitTicks(5);
	}

	/**
	 * Bones, head and senses from the player's chair (2.0, phase 4 round 2):
	 * hold the real use key with a stethoscope and hear your own heart; wear
	 * tinted goggles and see the world darken; see a sling the server put on a
	 * broken arm reach this client.
	 */
	private void bonesAndSenses(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("rdmg config enableMedicine true");
		context.waitFor(client -> RulesView.on(Half.MEDICINE), 60);
		String name = context.computeOnClient(client -> client.player.getName().getString());
		singleplayer.getServer().runCommand("rdmg heal " + name);
		singleplayer.getServer().runCommand("item replace entity " + name + " weapon.mainhand with realisticdamage:stethoscope");
		context.setScreen(() -> null);
		context.waitTicks(10);

		int before = context.computeOnClient(client -> dev.captience.realisticdamage.fieldkit.client.StethoscopeEar.beatsHeard);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(100);
		context.getInput().releaseKey(options -> options.keyUse);
		int heard = context.computeOnClient(client -> dev.captience.realisticdamage.fieldkit.client.StethoscopeEar.beatsHeard)
				- before;
		if (heard < 2) {
			throw new AssertionError("five seconds on your own chest should hear a calm heart a few times, heard " + heard);
		}

		singleplayer.getServer().runCommand("item replace entity " + name + " weapon.mainhand with air");
		singleplayer.getServer().runCommand("item replace entity " + name + " armor.head with realisticdamage:tinted_goggles");
		context.waitFor(client -> dev.captience.realisticdamage.fieldkit.medicine.Goggles.worn(client.player), 60);
		context.waitTicks(5);
		context.takeScreenshot("rd_fieldkit_15_goggles");
		singleplayer.getServer().runCommand("item replace entity " + name + " armor.head with air");

		singleplayer.getServer().runCommand("rdmg break " + name + " right_arm");
		singleplayer.getServer().runOnServer(server -> dev.captience.realisticdamage.fieldkit.medicine.Dressings.sling(
				server.getPlayerList().getPlayers().getFirst(), dev.captience.realisticdamage.body.BodyPart.RIGHT_ARM));
		context.waitFor(client -> dev.captience.realisticdamage.fieldkit.medicine.Dressings.slung(client.player,
				dev.captience.realisticdamage.body.BodyPart.RIGHT_ARM), 60);

		singleplayer.getServer().runOnServer(server -> dev.captience.realisticdamage.fieldkit.medicine.Dressings.unsling(
				server.getPlayerList().getPlayers().getFirst(), dev.captience.realisticdamage.body.BodyPart.RIGHT_ARM));
		singleplayer.getServer().runCommand("rdmg heal " + name);
		singleplayer.getServer().runCommand("rdmg config enableMedicine false");
		context.waitTicks(5);
	}

	/**
	 * Phase 4, round 3: drinking for real, with the use key. Spirits double the
	 * world through the core's afterimage; coffee comes back as its bottle and
	 * leaves you Caffeinated.
	 */
	private void foodAndDrink(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("rdmg config enableMedicine true");
		context.waitFor(client -> RulesView.on(Half.MEDICINE), 60);
		String name = context.computeOnClient(client -> client.player.getName().getString());
		singleplayer.getServer().runCommand("rdmg heal " + name);
		singleplayer.getServer().runCommand("effect clear " + name);
		singleplayer.getServer().runCommand("item replace entity " + name
				+ " weapon.mainhand with minecraft:potion[potion_contents={potion:\"realisticdamage:spirits\"}]");
		context.setScreen(() -> null);
		context.waitTicks(10);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitFor(client -> client.player.hasEffect(dev.captience.realisticdamage.fieldkit.medicine.Provisions.TIPSY),
				80);
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(5);
		double floor = dev.captience.realisticdamage.config.Balance.SPIRITS_AFTERIMAGE
				* dev.captience.realisticdamage.client.ClientEffectsConfig.get().afterimageIntensity;
		double doubled = context.computeOnClient(client -> dev.captience.realisticdamage.client.Afterimage.strength());
		if (doubled < floor - 0.01) {
			throw new AssertionError("tipsy, the world should double at " + floor + ", got " + doubled);
		}
		singleplayer.getServer().runCommand("effect clear " + name);
		context.waitFor(client -> dev.captience.realisticdamage.client.Afterimage.strength() < 0.05, 60);

		singleplayer.getServer().runCommand("item replace entity " + name + " weapon.mainhand with realisticdamage:coffee");
		context.waitTicks(5);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitFor(client -> client.player.hasEffect(
				dev.captience.realisticdamage.fieldkit.medicine.Provisions.CAFFEINATED), 80);
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitFor(client -> client.player.getMainHandItem().is(net.minecraft.world.item.Items.GLASS_BOTTLE), 20);

		singleplayer.getServer().runCommand("effect clear " + name);
		singleplayer.getServer().runCommand("item replace entity " + name + " weapon.mainhand with air");
		singleplayer.getServer().runCommand("rdmg config enableMedicine false");
		// The world is left straight after this stage: let the switch's own
		// sync (every 10 ticks) land first, so the leaving is all that's tested.
		context.waitFor(client -> !RulesView.on(Half.MEDICINE), 60);
		context.waitTicks(5);
	}

	/** Holds the local player at a Sedation level (doses over body size), on the in-process server. */
	private static void sedate(TestSingleplayerContext singleplayer, double level) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			if (!player.hasEffect(dev.captience.realisticdamage.fieldkit.brew.BrewedEffects.SEDATION)) {
				player.addEffect(new MobEffectInstance(dev.captience.realisticdamage.fieldkit.brew.BrewedEffects.SEDATION,
						100));
			}
			Sedation.State state = Sedation.peek(player);
			state.pending = 0.0;
			state.level = level;
		});
	}

	// --- World rules, on a server ---

	private void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			context.waitFor(client -> RulesView.received(), 100);
			String name = context.computeOnClient(client -> client.player.getName().getString());
			server.runCommand("deop " + name);
			server.runCommand("rdmg config enableWeapons false");
			context.waitFor(client -> RulesView.received() && !RulesView.canEdit() && !RulesView.on(Half.WEAPONS), 60);

			// A player who is not an operator reads the server's rules and can change none.
			context.setScreen(() -> new WorldRulesScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("rd_fieldkit_04_server_read_only");
			boolean readOnly = context.computeOnClient(client -> {
				WorldRulesScreen screen = (WorldRulesScreen) client.gui.screen();
				return LocalRules.remote() && screen.controls().stream().noneMatch(widget -> widget.active);
			});
			if (!readOnly) {
				throw new AssertionError("a non-operator on a server must see every control locked");
			}
			click(context, client -> control(client, "enableWeapons"));
			context.waitTicks(30);
			boolean unchanged = server.computeOnServer(s -> !ServerConfig.get().enableWeapons);
			if (!unchanged) {
				throw new AssertionError("a locked page changed the server's setting");
			}

			// Made an operator, the page unlocks by itself, and a change goes
			// through /rdmg config and comes back as the server's new value.
			server.runCommand("op " + name);
			context.waitFor(client -> RulesView.canEdit(), 60);
			context.waitTicks(5);
			boolean unlocked = context.computeOnClient(client -> control(client, "enableWeapons").active);
			if (!unlocked) {
				throw new AssertionError("the page must unlock when the player is made an operator");
			}
			context.takeScreenshot("rd_fieldkit_05_server_operator");
			click(context, client -> control(client, "enableWeapons"));
			server.waitFor(s -> ServerConfig.get().enableWeapons, 60);
			context.waitFor(client -> RulesView.on(Half.WEAPONS), 60);
			context.setScreen(() -> null);
			server.runCommand("rdmg config enableWeapons false");
			server.runCommand("deop " + name);
			context.waitTicks(10);
		}
	}

	// --- helpers ---

	private static AbstractWidget control(Minecraft client, String name) {
		AbstractWidget widget = ((WorldRulesScreen) client.gui.screen()).control(name);
		if (widget == null) {
			throw new AssertionError("no control for " + name);
		}
		return widget;
	}

	/**
	 * Rolls the mouse wheel over the page until a setting's control is wholly in
	 * view: down first, then back up. Not by comparing positions, because a list
	 * only places the rows it draws, and a row that has never been on screen
	 * still reports where it was built, at the top.
	 */
	private static void reveal(ClientGameTestContext context, String name) {
		for (int turn = 0; turn < 80; turn++) {
			int[] where = context.computeOnClient(client -> {
				WorldRulesScreen screen = (WorldRulesScreen) client.gui.screen();
				AbstractWidget widget = control(client, name);
				int[] area = screen.listArea();
				return new int[] {widget.getY(), widget.getY() + widget.getHeight(), area[0], area[1],
						screen.width / 2, (area[0] + area[1]) / 2};
			});
			if (where[0] >= where[2] && where[1] <= where[3]) {
				return;
			}
			double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			context.getInput().setCursorPos(where[4] * scale, where[5] * scale);
			context.getInput().scroll(turn < 40 ? -1 : 1);
			context.waitTicks(2);
		}
		throw new AssertionError("could not scroll " + name + " into view");
	}

	/** A real left click at the middle of a widget, in window pixels. */
	private static void click(ClientGameTestContext context,
			java.util.function.Function<Minecraft, AbstractWidget> widget) {
		double[] at = context.computeOnClient(client -> {
			AbstractWidget target = widget.apply(client);
			if (target == null) {
				throw new AssertionError("nothing to click");
			}
			double scale = client.getWindow().getGuiScale();
			return new double[] {(target.getX() + target.getWidth() / 2.0) * scale,
					(target.getY() + target.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static Button find(GuiEventListener at, String text) {
		if (at instanceof Button button && button.getMessage().getString().equals(text)) {
			return button;
		}
		if (at instanceof ContainerEventHandler container) {
			for (GuiEventListener child : container.children()) {
				Button button = find(child, text);
				if (button != null) {
					return button;
				}
			}
		}
		return null;
	}
}
