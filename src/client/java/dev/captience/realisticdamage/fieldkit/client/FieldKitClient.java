package dev.captience.realisticdamage.fieldkit.client;

import java.util.List;
import java.util.Optional;

import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import dev.captience.realisticdamage.fieldkit.RulesPayload;
import dev.captience.realisticdamage.fieldkit.RulesView;
import dev.captience.realisticdamage.fieldkit.client.mixin.CreativeScreenInvoker;
import dev.captience.realisticdamage.fieldkit.mixin.CreativeTabsAccessor;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The field kit's client half (2.0): the server's switches arriving, the
 * creative tab following them, a switched-off item saying so, and the
 * first-launch question.
 */
public final class FieldKitClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(RulesPayload.TYPE, (payload, context) -> {
			boolean weapons = RulesView.on(Half.WEAPONS);
			boolean medicine = RulesView.on(Half.MEDICINE);
			boolean first = !RulesView.received();
			RulesView.accept(payload);
			if (first || weapons != RulesView.on(Half.WEAPONS) || medicine != RulesView.on(Half.MEDICINE)) {
				refreshCreativeTabs();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			RulesView.clear();
			HeavyEyelids.reset();
			// No creative screen outlives the world, and the next world brings its
			// own registries: let vanilla build the tabs afresh when it next needs them.
			CreativeTabsAccessor.realisticdamage$setCachedParameters(null);
		}));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Optional<Half> off = Kit.offHalf(stack, RulesView::on);
			off.ifPresent(half -> lines.add(Component.translatable("realisticdamage.fieldkit.off.tooltip",
					half.title()).withStyle(ChatFormatting.RED)));
		});
		ClientTickEvents.END_CLIENT_TICK.register(Welcome::tick);
		ClientTickEvents.END_CLIENT_TICK.register(FieldKitClient::loosenTick);
		// A blood bag says how long it keeps, from the world's clock (phase 4).
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!stack.is(dev.captience.realisticdamage.fieldkit.medicine.Medicine.BLOOD_BAG)) {
				return;
			}
			net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
			Long drawn = stack.get(dev.captience.realisticdamage.fieldkit.medicine.Medicine.DRAWN_AT);
			if (level == null || drawn == null) {
				return;
			}
			long age = level.getGameTime() - drawn;
			long keeps = dev.captience.realisticdamage.config.Balance.BLOOD_BAG_KEEPS_TICKS;
			lines.add(age > keeps
					? Component.translatable("realisticdamage.fieldkit.blood_bag.spoiled.tooltip")
							.withStyle(ChatFormatting.DARK_RED)
					: Component.translatable("realisticdamage.fieldkit.blood_bag.keeps",
							Math.max(1L, (keeps - age + 23999L) / 24000L)).withStyle(ChatFormatting.GRAY));
		});
		// Sedation's eyelids (2.0, phase 3): the stage arrives, the lids follow.
		ClientPlayNetworking.registerGlobalReceiver(dev.captience.realisticdamage.fieldkit.brew.SedationPayload.TYPE,
				(payload, context) -> HeavyEyelids.setStage(payload.stage()));
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
				dev.captience.realisticdamage.RealisticDamage.id("sedation_eyelids"), HeavyEyelids::render);
	}

	private static boolean useWasDown;

	/**
	 * Sneak and press use in the air with an empty hand: take your tourniquet
	 * off (phase 4). One request per press; the server knows whether there is
	 * one. Pressing on a wound is a held empty hand too, and goes on as before.
	 */
	private static void loosenTick(Minecraft client) {
		boolean down = client.options.keyUse.isDown();
		if (down && !useWasDown && client.player != null && client.gui.screen() == null
				&& client.player.isShiftKeyDown() && client.player.getMainHandItem().isEmpty()
				&& (client.hitResult == null || client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
				&& RulesView.on(Half.MEDICINE)
				&& ClientPlayNetworking.canSend(dev.captience.realisticdamage.fieldkit.medicine.LoosenPayload.TYPE)) {
			ClientPlayNetworking.send(dev.captience.realisticdamage.fieldkit.medicine.LoosenPayload.INSTANCE);
		}
		useWasDown = down;
	}

	/**
	 * Rebuilds every creative tab with the parameters already in use, so the
	 * Field kit tab asks the switches again, then brings an open creative screen
	 * (and the search tab's index) up to date: the tab's list is redrawn, and a
	 * tab that just emptied hands over to the default one, as vanilla does when
	 * a tab empties for its own reasons.
	 */
	public static void refreshCreativeTabs() {
		CreativeModeTab.ItemDisplayParameters parameters = CreativeTabsAccessor.realisticdamage$cachedParameters();
		if (parameters == null) {
			return; // never built yet: the first build will ask the switches
		}
		CreativeTabsAccessor.realisticdamage$buildAllTabContents(parameters);
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			List<ItemStack> searchable = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
			connection.searchTrees().updateCreativeTooltips(parameters.holders(), searchable);
			connection.searchTrees().updateCreativeTags(searchable);
		}
		if (client.gui.screen() instanceof CreativeModeInventoryScreen screen) {
			CreativeModeTab selected = ((FabricCreativeModeInventoryScreen) screen).getSelectedTab();
			if (selected.getType() == CreativeModeTab.Type.CATEGORY && !selected.shouldDisplay()) {
				((FabricCreativeModeInventoryScreen) screen).setSelectedTab(CreativeModeTabs.getDefaultTab());
			} else {
				((CreativeScreenInvoker) screen).realisticdamage$selectTab(selected);
			}
		}
	}
}
