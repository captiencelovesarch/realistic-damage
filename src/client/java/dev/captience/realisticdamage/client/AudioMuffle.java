package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.client.mixin.ChannelAccessor;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.util.Mth;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

/**
 * Genuine low-pass muffling as consciousness fails: the world goes dull and
 * underwater rather than merely quiet.
 *
 * <p>Minecraft has no filter API of its own, but {@code Channel} keeps its raw
 * OpenAL source handle and LWJGL ships the EFX bindings, so we attach an
 * {@code AL_FILTER_LOWPASS} as each source's direct filter and drive its
 * high-frequency gain from the player's state.
 *
 * <p>EFX is an optional OpenAL extension. Rather than probing the device we
 * simply try to create the filter once and check for an error; if anything goes
 * wrong we disable ourselves permanently and the existing per-category volume
 * ducking carries the effect alone. Audio must never break because of this.
 */
public final class AudioMuffle {
	private AudioMuffle() {}

	private static final int UNSET = -1;
	private static final int UNAVAILABLE = -2;

	private static int filter = UNSET;
	/** Last high-frequency gain uploaded, so we only touch the driver on change. */
	private static float appliedGainHf = 1.0f;
	private static boolean attached;

	/** 0 = clear hearing, 1 = fully muffled. */
	private static double severity() {
		double intensity = ClientEffectsConfig.get().mufflingIntensity;
		if (intensity <= 0.0 || !ClientState.active()) {
			return 0.0;
		}
		return ClientState.ramp(Balance.SYMPTOM_RINGING, 0.0) * Math.min(1.0, intensity);
	}

	public static void tick(ChannelAccess channelAccess) {
		if (filter == UNAVAILABLE) {
			return;
		}
		double severity = severity();
		if (severity <= 0.001 && !attached) {
			return;   // nothing to do and nothing to undo
		}
		if (!ensureFilter()) {
			return;
		}

		// Keep some highs even at the worst, or it sounds broken rather than dull.
		float gainHf = (float) Mth.lerp(severity, 1.0, 0.08);
		float gain = (float) Mth.lerp(severity, 1.0, 0.75);
		if (Math.abs(gainHf - appliedGainHf) > 0.005f) {
			EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
			EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
			appliedGainHf = gainHf;
		}

		boolean wanted = severity > 0.001;
		int handle = wanted ? filter : EXTEfx.AL_FILTER_NULL;
		channelAccess.executeOnChannels(channels -> channels.forEach(channel ->
				AL10.alSourcei(((ChannelAccessor) (Object) channel).realisticdamage$source(),
						EXTEfx.AL_DIRECT_FILTER, handle)));
		AL10.alGetError();   // never let a stray error leak into vanilla's checks
		attached = wanted;
	}

	/** Creates the filter on first use; disables the feature for good on failure. */
	private static boolean ensureFilter() {
		if (filter >= 0) {
			return true;
		}
		AL10.alGetError();
		int created = EXTEfx.alGenFilters();
		if (AL10.alGetError() != AL10.AL_NO_ERROR) {
			RealisticDamage.LOGGER.info("OpenAL EFX unavailable; muffling falls back to volume ducking");
			filter = UNAVAILABLE;
			return false;
		}
		EXTEfx.alFilteri(created, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
		if (AL10.alGetError() != AL10.AL_NO_ERROR) {
			EXTEfx.alDeleteFilters(created);
			AL10.alGetError();
			RealisticDamage.LOGGER.info("OpenAL low-pass filter unsupported; muffling falls back to volume ducking");
			filter = UNAVAILABLE;
			return false;
		}
		filter = created;
		return true;
	}

	/** Sound engine torn down: forget the handle, it belongs to a dead context. */
	public static void invalidate() {
		filter = UNSET;
		appliedGainHf = 1.0f;
		attached = false;
	}
}
