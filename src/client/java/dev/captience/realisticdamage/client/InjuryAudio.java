package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.client.audio.SoundDirector;
import dev.captience.realisticdamage.net.InjurySoundPayload;
import dev.captience.realisticdamage.net.SoundSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Fracture, destruction and collapse, heard from the right place.
 *
 * <p>Your own injuries play non-positionally at full volume — they are
 * happening inside your own body, so hearing them arrive from a point in the
 * world at walking-distance volume was always wrong. Everyone else's play at
 * their position and quieter, which keeps them as a usable signal that a
 * teammate is in trouble without competing with your own.
 *
 * <p>The sound set is picked here rather than server-side, so two players on
 * the same server can disagree about how their game should sound. Since 2.0
 * the body's own sounds are recipes played by the {@link SoundDirector}; the
 * screams are still plain sound events, because a voice is not a recipe.
 */
public final class InjuryAudio {
	private InjuryAudio() {}

	public static void play(InjurySoundPayload payload) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		float volume = (float) config.injurySoundVolume;
		if (volume <= 0.0F || (payload.sound().isScream() && !config.screams)) {
			return;
		}
		if (!payload.sound().isScream()) {
			// 2.0: the body's own sounds are recipes, in the listener's sound set
			// unless a test command named one.
			SoundSet set = payload.forcedSet() != null ? payload.forcedSet() : config.soundSet();
			if (SoundDirector.play(payload.sound(), set, payload.self(),
					new Vec3(payload.x(), payload.y(), payload.z()), payload.strength(), payload.material(), volume)) {
				return;
			}
			// No recipe at all (a resource pack took them out): the 1.x sound, if
			// this event ever had one.
			if (!payload.sound().hasSoundEvents()) {
				return;
			}
		}
		if (payload.self() && payload.sound().isScream()) {
			// Your own voice sits under your own bones breaking: you hear the
			// crack through your skull and the yell through the air.
			volume *= Balance.SCREAM_SELF_VOLUME;
		}
		SoundEvent event = event(payload.sound(), config.soundSet() == SoundSet.GROUNDED);
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
