package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
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
	private static ImpairmentSound heartbeat;
	private static float duckAmount;

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		SoundManager sounds = client.getSoundManager();
		ClientEffectsConfig config = ClientEffectsConfig.get();
		boolean active = ClientState.active() && ClientState.snapshot() != null;

		double ringLevel = active
				? ClientState.ramp(Balance.SYMPTOM_RINGING, Balance.SYMPTOM_SEVERE) * config.ringingVolume
				: 0.0;
		double heartLevel = active ? heartbeatLevel() * config.heartbeatVolume : 0.0;

		ringing = update(sounds, ringing, SoundEvents.BELL_RESONATE, (float) ringLevel * 0.7F, 1.9F);
		heartbeat = update(sounds, heartbeat, SoundEvents.WARDEN_HEARTBEAT, (float) heartLevel * 1.4F, 1.0F);

		// Muffle everything else as consciousness fades; restore smoothly.
		float targetDuck = (float) (ringLevel > 0 ? Math.min(0.8, ringLevel * 0.8) : 0.0);
		duckAmount = Mth.lerp(0.05F, duckAmount, targetDuck);
		float gain = 1.0F - duckAmount;
		for (SoundSource source : SoundSource.values()) {
			// Our own cues ride on AMBIENT and stay audible.
			sounds.updateCategoryVolume(source, source == SoundSource.AMBIENT ? 1.0F : gain);
		}
	}

	/** Heartbeat rises with either low consciousness or heavy blood loss (spec §23). */
	private static double heartbeatLevel() {
		double fromConsciousness = ClientState.ramp(Balance.SYMPTOM_HEARTBEAT, Balance.SYMPTOM_SEVERE);
		double blood = ClientState.blood();
		double fromBlood = blood >= Balance.BLOOD_SERIOUS ? 0.0
				: Math.min(1.0, (Balance.BLOOD_SERIOUS - blood) / Balance.BLOOD_SERIOUS);
		return Math.max(fromConsciousness, fromBlood);
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
		if (heartbeat != null) {
			heartbeat.fadeOut();
			heartbeat = null;
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
