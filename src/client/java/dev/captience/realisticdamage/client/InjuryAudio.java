package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.net.InjurySoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Fracture, destruction and collapse, heard from the right place.
 *
 * <p>Your own injuries play non-positionally at full volume — they are
 * happening inside your own body, so hearing them arrive from a point in the
 * world at walking-distance volume was always wrong. Everyone else's play at
 * their position and quieter, which keeps them as a usable signal that a
 * teammate is in trouble without competing with your own.
 *
 * <p>The style variant is picked here rather than server-side, so two players
 * on the same server can disagree about how visceral their game should be.
 */
public final class InjuryAudio {
	private InjuryAudio() {}

	public static void play(InjurySoundPayload payload) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		float volume = (float) config.injurySoundVolume;
		if (volume <= 0.0F || (payload.sound().isScream() && !config.screams)) {
			return;
		}
		if (payload.self() && payload.sound().isScream()) {
			// Your own voice sits under your own bones breaking: you hear the
			// crack through your skull and the yell through the air.
			volume *= Balance.SCREAM_SELF_VOLUME;
		}
		SoundEvent event = event(payload.sound(), config.visceralInjurySounds);
		if (event == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (payload.self()) {
			// AMBIENT, like the mod's other cues: ConsciousnessAudio ducks every
			// other category as you fade, and your own body breaking is the last
			// thing that should get quieter for it.
			client.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(event, 1.0F, volume));
		} else if (client.level != null) {
			client.level.playLocalSound(payload.x(), payload.y(), payload.z(), event,
					SoundSource.PLAYERS, volume * (payload.sound().isScream()
							? Balance.SCREAM_OTHERS_VOLUME : Balance.INJURY_SOUND_OTHERS_VOLUME), 1.0F, false);
		}
	}

	/** Falls back to the muted variant if the visceral one somehow failed to register. */
	private static SoundEvent event(InjurySound sound, boolean visceral) {
		SoundEvent event = RealisticDamage.injurySound(sound, visceral);
		return event != null ? event : RealisticDamage.injurySound(sound, false);
	}
}
