package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Ringing, heartbeat, and muffling (spec §43-44). Two looping instances whose
 * volume tracks consciousness/blood, plus category ducking for the muffled
 * feel — no lowpass filter exists, so this is the honest approximation.
 */
public final class ConsciousnessAudio {
	private ConsciousnessAudio() {}

	private static ImpairmentSound ringing;
	private static TinnitusSound tinnitus;
	private static float duckAmount;

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		SoundManager sounds = client.getSoundManager();
		ClientEffectsConfig config = ClientEffectsConfig.get();
		boolean active = ClientState.active() && ClientState.snapshot() != null;
		if (!active && (ringing != null || tinnitus != null)) {
			// Death, respawn screen, gamemode switch: stop immediately rather than
			// letting the loop trail on.
			reset();
		}

		double ringLevel = active
				? ClientState.ramp(Balance.SYMPTOM_RINGING, Balance.SYMPTOM_SEVERE) * config.ringingVolume
				: 0.0;
		if (config.tinnitusTone) {
			if (ringing != null) {
				ringing.fadeOut();
				ringing = null;
			}
			tinnitus = updateTone(sounds, tinnitus, (float) ringLevel * 0.5F);
		} else {
			if (tinnitus != null) {
				tinnitus.fadeOut();
				tinnitus = null;
			}
			ringing = update(sounds, ringing, SoundEvents.BELL_RESONATE, (float) ringLevel * 0.7F, 1.9F);
		}

		// One thump per beat, on the same clock the vignette pulses to, so the
		// sound and the visual land together.
		double heartLevel = active ? ClientState.distress() * config.heartbeatVolume : 0.0;
		if (heartLevel > 0.05 && ClientState.beatJustFired()) {
			// Pitch climbs a little as the heart races — it reads as panic.
			float pitch = (float) (0.85 + 0.35 * ClientState.distress());
			sounds.play(SimpleSoundInstance.forLocalAmbience(
					SoundEvents.WARDEN_HEARTBEAT, pitch, (float) Math.min(1.0, heartLevel * 1.6)));
		}

		// Muffle everything else as consciousness fades; restore smoothly.
		float targetDuck = (float) (ringLevel > 0 ? Math.min(0.8, ringLevel * 0.8) : 0.0);
		duckAmount = Mth.lerp(0.05F, duckAmount, targetDuck);
		float gain = 1.0F - duckAmount;
		for (SoundSource source : SoundSource.values()) {
			// Our own cues ride on AMBIENT and stay audible.
			sounds.updateCategoryVolume(source, source == SoundSource.AMBIENT ? 1.0F : gain);
		}
	}

	private static TinnitusSound updateTone(SoundManager sounds, TinnitusSound instance, float volume) {
		if (volume <= 0.01F) {
			if (instance != null) {
				instance.fadeOut();
			}
			return null;
		}
		if (instance == null || !sounds.isActive(instance)) {
			instance = new TinnitusSound();
			sounds.play(instance);
		}
		instance.setTargetVolume(volume);
		return instance;
	}

	private static ImpairmentSound update(SoundManager sounds, ImpairmentSound instance,
			SoundEvent event, float volume, float pitch) {
		if (volume <= 0.01F) {
			if (instance != null) {
				instance.fadeOut();
			}
			return null;
		}
		if (instance == null || !sounds.isActive(instance)) {
			instance = new ImpairmentSound(event, pitch);
			sounds.play(instance);
		}
		instance.setTargetVolume(volume);
		return instance;
	}

	/** Called on disconnect/respawn so nothing lingers. */
	public static void reset() {
		SoundManager sounds = Minecraft.getInstance().getSoundManager();
		if (ringing != null) {
			ringing.fadeOut();
			ringing = null;
		}
		if (tinnitus != null) {
			tinnitus.fadeOut();
			tinnitus = null;
		}
		duckAmount = 0.0F;
		for (SoundSource source : SoundSource.values()) {
			sounds.updateCategoryVolume(source, 1.0F);
		}
	}

	private static final class ImpairmentSound extends AbstractTickableSoundInstance {
		private float targetVolume;
		private boolean fadingOut;

		private ImpairmentSound(SoundEvent event, float pitch) {
			super(event, SoundSource.AMBIENT, net.minecraft.util.RandomSource.create());
			this.looping = true;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
			this.delay = 0;
			this.volume = 0.0F;
			this.pitch = pitch;
		}

		void setTargetVolume(float target) {
			this.targetVolume = target;
		}

		void fadeOut() {
			this.fadingOut = true;
			this.targetVolume = 0.0F;
		}

		/**
		 * Essential: these instances start at volume 0 and fade in. Without this,
		 * SoundEngine.play rejects them outright ("volume was zero") and they never
		 * begin at all — which is why the ringing and heartbeat were silent.
		 */
		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			this.volume = Mth.lerp(0.08F, this.volume, this.targetVolume);
			if (this.fadingOut && this.volume < 0.01F) {
				this.stop();
			}
		}
	}
}
