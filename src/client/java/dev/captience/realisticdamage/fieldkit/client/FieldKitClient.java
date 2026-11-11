package dev.captience.realisticdamage.fieldkit.client;

import java.util.Optional;

import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import dev.captience.realisticdamage.fieldkit.RulesPayload;
import dev.captience.realisticdamage.fieldkit.RulesView;
import dev.captience.realisticdamage.fieldkit.mixin.CreativeTabsAccessor;
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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			RulesView.clear();
			refreshCreativeTabs();
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Optional<Half> off = Kit.offHalf(stack, RulesView::on);
			off.ifPresent(half -> lines.add(Component.translatable("realisticdamage.fieldkit.off.tooltip",
					half.title()).withStyle(ChatFormatting.RED)));
		});
		ClientTickEvents.END_CLIENT_TICK.register(Welcome::tick);
	}

	/** Forget vanilla's cached tab contents, so the Field kit tab asks the switches again. */
	public static void refreshCreativeTabs() {
		CreativeTabsAccessor.realisticdamage$setCachedParameters(null);
	}
}
