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
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(dev.captience.realisticdamage.net.CorpsePayload.TYPE,
						dev.captience.realisticdamage.net.CorpsePayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
				.register(dev.captience.realisticdamage.net.PressurePayload.TYPE,
						dev.captience.realisticdamage.net.PressurePayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.PressurePayload.TYPE,
				(payload, context) -> dev.captience.realisticdamage.systems.RescueSystem
						.onPressureRequest(context.player(), payload.targetId()));
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
				.register(dev.captience.realisticdamage.net.DeathCameraPayload.TYPE,
						dev.captience.realisticdamage.net.DeathCameraPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.DeathCameraPayload.TYPE,
				(payload, context) -> dev.captience.realisticdamage.systems.DeathHold
						.request(context.player(), payload.watching()));
		RDAttachments.init();
		RDParticles.init();
		RDEntities.init();
		dev.captience.realisticdamage.systems.TickDispatcher.init();
		dev.captience.realisticdamage.systems.ImpairmentSystem.init();
		dev.captience.realisticdamage.systems.ConsciousnessSystem.init();
		dev.captience.realisticdamage.systems.ConcussionSystem.init();
		dev.captience.realisticdamage.systems.HealingSystem.init();
		dev.captience.realisticdamage.systems.RescueSystem.init();
		dev.captience.realisticdamage.systems.RespawnCarryover.init();
		dev.captience.realisticdamage.systems.DeathHold.init();
		dev.captience.realisticdamage.damage.DamageHooks.init();
		dev.captience.realisticdamage.command.RealisticDamageCommands.init();
		// Body plans are cached per type; a /reload or a client's tag sync can change them.
		net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents.TAGS_LOADED.register(
				(registries, client) -> dev.captience.realisticdamage.systems.MobAnatomy.tagsReloaded());
		TINNITUS = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				id("tinnitus"),
				net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id("tinnitus")));
		registerInjurySounds();
		GASP = registerSound("gasp");
		BREATH_IN = registerSound("breath_in");
		BREATH_OUT = registerSound("breath_out");
		LOGGER.info("Realistic Damage initialized");
	}

	/**
	 * Carrier for the synthesised tinnitus tone. Declared streaming in
	 * sounds.json: Fabric's procedural-audio hook only replaces the streaming
	 * buffer fetch, so a non-streaming event would silently play its own sample
	 * instead of our generated one.
	 */
	public static net.minecraft.sounds.SoundEvent TINNITUS;

	/** Your own breathing (1.7): the gasp on surfacing, and the two halves of a heaving breath. */
	public static net.minecraft.sounds.SoundEvent GASP;
	public static net.minecraft.sounds.SoundEvent BREATH_IN;
	public static net.minecraft.sounds.SoundEvent BREATH_OUT;

	private static net.minecraft.sounds.SoundEvent registerSound(String path) {
		return net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				id(path),
				net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id(path)));
	}

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
				if (INJURY_SOUNDS.containsKey(path)) {
					continue;   // an unstyled sound (the screams) has one path for both
				}
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
