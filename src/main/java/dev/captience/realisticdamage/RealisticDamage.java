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
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.captience.realisticdamage.net.LandingPayload.TYPE,
						dev.captience.realisticdamage.net.LandingPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.captience.realisticdamage.net.InjurySoundPayload.TYPE,
						dev.captience.realisticdamage.net.InjurySoundPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.captience.realisticdamage.net.TreatingPayload.TYPE,
						dev.captience.realisticdamage.net.TreatingPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.captience.realisticdamage.net.DeathPayload.TYPE,
						dev.captience.realisticdamage.net.DeathPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
				.register(dev.captience.realisticdamage.net.PressurePayload.TYPE,
						dev.captience.realisticdamage.net.PressurePayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.PressurePayload.TYPE,
				(payload, context) -> dev.captience.realisticdamage.systems.RescueSystem
						.onPressureRequest(context.player(), payload.targetId()));
		RDAttachments.init();
		RDParticles.init();
		RDEntities.init();
		dev.captience.realisticdamage.systems.TickDispatcher.init();
		dev.captience.realisticdamage.systems.ImpairmentSystem.init();
		dev.captience.realisticdamage.systems.ConsciousnessSystem.init();
		dev.captience.realisticdamage.systems.HealingSystem.init();
		dev.captience.realisticdamage.systems.RescueSystem.init();
		dev.captience.realisticdamage.systems.RespawnCarryover.init();
		dev.captience.realisticdamage.damage.DamageHooks.init();
		dev.captience.realisticdamage.command.RealisticDamageCommands.init();
		TINNITUS = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				id("tinnitus"),
				net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id("tinnitus")));
		registerInjurySounds();
		LOGGER.info("Realistic Damage initialized");
	}

	/**
	 * Carrier for the synthesised tinnitus tone. Declared streaming in
	 * sounds.json: Fabric's procedural-audio hook only replaces the streaming
	 * buffer fetch, so a non-streaming event would silently play its own sample
	 * instead of our generated one.
	 */
	public static net.minecraft.sounds.SoundEvent TINNITUS;

	/**
	 * Both style variants of every injury sound, keyed by asset path. Both are
	 * registered on the server even though only one plays: which one is a client
	 * decision, and the registry has to be identical on both ends regardless.
	 */
	private static final java.util.Map<String, net.minecraft.sounds.SoundEvent> INJURY_SOUNDS =
			new java.util.HashMap<>();

	private static void registerInjurySounds() {
		for (dev.captience.realisticdamage.net.InjurySound sound
				: dev.captience.realisticdamage.net.InjurySound.values()) {
			for (boolean visceral : new boolean[] {false, true}) {
				String path = sound.assetPath(visceral);
				INJURY_SOUNDS.put(path, net.minecraft.core.Registry.register(
						net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
						id(path),
						net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id(path))));
			}
		}
	}

	public static net.minecraft.sounds.SoundEvent injurySound(
			dev.captience.realisticdamage.net.InjurySound sound, boolean visceral) {
		return INJURY_SOUNDS.get(sound.assetPath(visceral));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
