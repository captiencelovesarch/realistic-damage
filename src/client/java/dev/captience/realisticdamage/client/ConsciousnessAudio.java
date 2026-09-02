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
		boolean dying = ClientState.dying();
		if (dying) {
			// Death has a sound of its own now (1.5): the failing heart, a swell
			// of ringing, and the world sinking toward quiet underneath both.
			tickDying(sounds, config);
			return;
		}
		if (!active && (ringing != null || tinnitus != null)) {
			// Respawn screen left, gamemode switch, disconnect: stop immediately
			// rather than letting the loop trail on.
			reset();
		}

		// Ringing has two sources now: a fading mind, and a blast that has just
		// gone off next to you (1.5). Both feed the same tone, so the one control
		// that silences ear-ringing silences it whatever caused it.
		double ringLevel = active
				? Math.max(ClientState.ramp(Balance.SYMPTOM_RINGING, Balance.SYMPTOM_SEVERE),
						ImpactEffect.deafness()) * config.ringingVolume
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

		// One thump per beat, on the same clock the vignette pulses to and the
		// same clock an open artery spurts on, so sound, screen and blood all
		// land together.
		double heartLevel = active ? ClientState.distress() * config.heartbeatVolume : 0.0;
		if (heartLevel > 0.05 && ClientState.beatJustFired()) {
			// Amplitude carries the arrhythmia (1.5): an ectopic beat is a weak
			// early tap and the one after the pause is a hard thump. Volume alone
			// would make the pattern audible but flat, so the pitch drops on the
			// heavy beat as well — a bigger stroke is a lower sound.
			double amplitude = ClientState.beatAmplitude();
			float pitch = (float) Math.clamp(
					(0.85 + 0.35 * ClientState.distress()) / Math.max(0.35, Math.pow(amplitude, 0.45)),
					0.5, 2.0);
			float volume = (float) Math.clamp(heartLevel * 1.6 * amplitude, 0.0, 1.0);
			if (volume > 0.02F) {
				sounds.play(SimpleSoundInstance.forLocalAmbience(
						SoundEvents.WARDEN_HEARTBEAT, pitch, volume));
			}
		}

		// Muffle everything else as consciousness fades; restore smoothly.
		// Gentle now: the low-pass filter does the real muffling.
		float targetDuck = (float) (ringLevel > 0 ? Math.min(0.45, ringLevel * 0.45) : 0.0);
		duckAmount = Mth.lerp(0.05F, duckAmount, targetDuck);
		float gain = 1.0F - duckAmount;
		for (SoundSource source : SoundSource.values()) {
			// Our own cues ride on AMBIENT and stay audible.
			sounds.updateCategoryVolume(source, source == SoundSource.AMBIENT ? 1.0F : gain);
		}
	}

	/**
	 * The audio of the seconds after death (1.5). The heart gives its last few
	 * beats — slow, heavy, each softer than the one before, on the same moribund
	 * clock the vignette squeezes to — while a ring of tinnitus swells and then
	 * fades with everything else, and the world's own audio sinks toward quiet.
	 * By the end of the fade there is almost nothing left to hear, which is the
	 * point.
	 */
	private static void tickDying(SoundManager sounds, ClientEffectsConfig config) {
		double fade = ClientState.deathFade();
		double intensity = config.deathIntensity();
		boolean realistic = config.realisticDeath;

		// The ring: up fast to its peak, then gone. The realistic style ends it
		// early so the last stretch of the fade is true silence — black screen,
		// stopped heart, nothing — where the cinematic one lets it trail to the
		// end. The same tone the living symptom uses, so one control governs both.
		double ringEnd = realistic
				? dev.captience.realisticdamage.config.Balance.DEATH_RING_END_REALISTIC : 1.0;
		double curve = fade < dev.captience.realisticdamage.config.Balance.DEATH_RING_PEAK
				? fade / dev.captience.realisticdamage.config.Balance.DEATH_RING_PEAK
				: Math.max(0.0, 1.0 - (fade - dev.captience.realisticdamage.config.Balance.DEATH_RING_PEAK)
						/ (ringEnd - dev.captience.realisticdamage.config.Balance.DEATH_RING_PEAK));
		float ringLevel = (float) (curve * 0.7 * intensity * config.ringingVolume);
		if (config.tinnitusTone) {
			if (ringing != null) {
				ringing.fadeOut();
				ringing = null;
			}
			tinnitus = updateTone(sounds, tinnitus, ringLevel * 0.5F);
		} else {
			if (tinnitus != null) {
				tinnitus.fadeOut();
				tinnitus = null;
			}
			ringing = update(sounds, ringing, SoundEvents.BELL_RESONATE, ringLevel * 0.7F, 1.9F);
		}

		// The last beats. Amplitude is already draining inside the moribund
		// heart; the pitch sinks with the fade, so each beat lands lower and
		// heavier than the one before it.
		if (ClientState.beatJustFired() && config.heartbeatVolume > 0.0 && intensity > 0.0) {
			double amplitude = ClientState.beatAmplitude();
			float pitch = (float) Math.clamp(0.8 - 0.35 * fade, 0.4, 1.0);
			float volume = (float) Math.clamp(amplitude * intensity * config.heartbeatVolume, 0.0, 1.0);
			if (volume > 0.02F) {
				sounds.play(SimpleSoundInstance.forLocalAmbience(
						SoundEvents.WARDEN_HEARTBEAT, pitch, volume));
			}
		}

		// The world sinks toward quiet underneath. The realistic style takes it
		// all the way to nothing before the fade ends; the cinematic one leaves a
		// residue of world under the screen. Eased through the same duck the
		// living muffle uses, so recovery on respawn is the ordinary lerp back
		// rather than a hard cut to full volume.
		float targetDuck = (float) ((realistic
				? Math.min(1.0, fade / dev.captience.realisticdamage.config.Balance.DEATH_DUCK_FULL_AT)
				: Math.min(1.0, fade * dev.captience.realisticdamage.config.Balance.DEATH_DUCK))
				* intensity);
		// The ease has to keep up with the fade it follows: a violent death
		// compresses the whole sequence into a second or so, and the leisurely
		// living-muffle rate would still be letting the world through after the
		// screen had already gone.
		float duckRate = (float) (0.08 + 0.45 * ClientState.deathViolence());
		duckAmount = Mth.lerp(duckRate, duckAmount, targetDuck);
		float gain = 1.0F - duckAmount;
		for (SoundSource source : SoundSource.values()) {
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
		AudioMuffle.invalidate();
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
