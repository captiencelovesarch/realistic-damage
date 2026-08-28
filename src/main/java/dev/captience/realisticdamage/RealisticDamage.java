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
		RDAttachments.init();
		dev.captience.realisticdamage.systems.TickDispatcher.init();
		dev.captience.realisticdamage.systems.ImpairmentSystem.init();
		dev.captience.realisticdamage.systems.ConsciousnessSystem.init();
		dev.captience.realisticdamage.damage.DamageHooks.init();
		dev.captience.realisticdamage.command.RealisticDamageCommands.init();
		LOGGER.info("Realistic Damage initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
