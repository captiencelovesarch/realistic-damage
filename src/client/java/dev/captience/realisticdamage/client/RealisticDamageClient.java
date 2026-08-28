package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class RealisticDamageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientEffectsConfig.load();
		BodyInspector.init();

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.ImpactPayload.TYPE,
				(payload, context) -> ImpactEffect.trigger(payload.magnitude(), payload.yaw()));

		// Reachable without Mod Menu, per the "no config-file editing" rule.
		KeyMapping.Category category = KeyMapping.Category.register(RealisticDamage.id("main"));
		KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.realisticdamage.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

		// Sensory effects draw with vanilla's own camera overlays; the body
		// diagram sits above the status bars but below chat/titles (spec §42).
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				RealisticDamage.id("consciousness_effects"), ConsciousnessEffects::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
				RealisticDamage.id("body_hud"), BodyHud::render);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientState.tick();
			ConsciousnessAudio.tick();
			MouseInertia.tickFallback();
			VisionEffects.tick();
			while (openSettings.consumeClick()) {
				client.gui.setScreen(new AccessibilityScreen(client.gui.screen()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ConsciousnessAudio.reset();
			VisionEffects.reset();
			MouseInertia.reset();
			ImpactEffect.reset();
		});
	}
}
