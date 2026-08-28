package dev.captience.realisticdamage;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealisticDamage implements ModInitializer {
	public static final String MOD_ID = "realisticdamage";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		dev.captience.realisticdamage.config.ServerConfig.load();
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.captience.realisticdamage.net.ImpactPayload.TYPE,
						dev.captience.realisticdamage.net.ImpactPayload.CODEC);
		RDAttachments.init();
		dev.captience.realisticdamage.systems.TickDispatcher.init();
		dev.captience.realisticdamage.systems.ImpairmentSystem.init();
		dev.captience.realisticdamage.systems.ConsciousnessSystem.init();
		dev.captience.realisticdamage.systems.HealingSystem.init();
		dev.captience.realisticdamage.damage.DamageHooks.init();
		dev.captience.realisticdamage.command.RealisticDamageCommands.init();
		TINNITUS = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				id("tinnitus"),
				net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id("tinnitus")));
		LOGGER.info("Realistic Damage initialized");
	}

	/**
	 * Carrier for the synthesised tinnitus tone. Declared streaming in
	 * sounds.json: Fabric's procedural-audio hook only replaces the streaming
	 * buffer fetch, so a non-streaming event would silently play its own sample
	 * instead of our generated one.
	 */
	public static net.minecraft.sounds.SoundEvent TINNITUS;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
