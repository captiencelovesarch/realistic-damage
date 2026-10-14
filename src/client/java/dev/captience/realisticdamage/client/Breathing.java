package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * Your own breathing (1.7): the gasp when you come up from too long under, and
 * the heaving that follows it or the adrenaline crash. Synthesised breaths, in
 * and out, played on one clock so they alternate — two sounds on independent
 * timers drift into a breath-in-breath-in stutter within seconds.
 *
 * <p>Rate and depth come from whichever cause is worst right now: a gasp
 * decaying away, the crash, or being winded in it. All three end in the same
 * sound, because they are the same body doing the same thing.
 */
public final class Breathing {
	private Breathing() {}

	/** What is left of the heaving after a gasp, 0 to 1, decaying over seconds. */
	private static double recovery;
	/** Ticks until the next half-breath, and whether it is an inhale. */
	private static double countdown;
	private static boolean inhale = true;
	/** Variant cycling, so consecutive breaths are not the same sample. */
	private static int variant;
	private static long breaths;
	private static long gasps;

	/** Gasps so far; read by the client gametest. */
	public static long gasps() {
		return gasps;
	}

	/** Breaths played so far; read by the client gametest. */
	public static long breaths() {
		return breaths;
	}

	/** Surfaced, or out of the wall: a sharp gasp now, and a while of heaving. */
	public static void gasp(double depth) {
		double volume = ClientEffectsConfig.get().breathingVolume();
		recovery = Math.max(recovery, Math.min(1.0, depth));
		gasps++;
		inhale = false;   // the gasp is the inhale; the next sound breathes out
		countdown = 10.0;
		if (volume > 0.0) {
			play(RealisticDamage.GASP, (float) (volume * (0.55 + 0.45 * depth)), (float) (1.0 + 0.05 * depth));
		}
	}

	/** Once per client tick. */
	public static void tick() {
		if (!ClientState.active()) {
			recovery = 0.0;
			return;
		}
		recovery = Math.max(0.0, recovery - 1.0 / 160.0);
		double crash = ClientState.adrenaline() == AdrenalineMath.Phase.CRASH
				? (ClientState.winded() ? 0.9 : 0.5) : 0.0;
		double surge = ClientState.adrenaline() == AdrenalineMath.Phase.SURGE ? 0.35 : 0.0;
		double drive = Math.max(recovery, Math.max(crash, surge)) * ClientEffectsConfig.get().lowHealthEffects();
		drive = Math.max(drive, recovery);   // a gasp is not an adrenaline effect
		if (drive < 0.05 || AirHunger.hunger() > 0.2 || Minecraft.getInstance().player.isUnderWater()) {
			countdown = Math.min(countdown, 6.0);
			return;
		}
		countdown -= 1.0;
		if (countdown > 0.0) {
			return;
		}
		// Calm-ish at the bottom (a breath in or out every 1.3 s), heaving at the
		// top (every 0.4 s). Exhales a touch longer than inhales, as they are.
		double half = 26.0 - 18.0 * drive;
		countdown = inhale ? half : half * 1.15;
		double volume = ClientEffectsConfig.get().breathingVolume() * (0.25 + 0.6 * drive);
		if (volume > 0.01) {
			variant++;
			float pitch = (float) (0.94 + 0.04 * (variant % 3) + 0.06 * drive);
			play(inhale ? RealisticDamage.BREATH_IN : RealisticDamage.BREATH_OUT, (float) volume, pitch);
			breaths++;
		}
		inhale = !inhale;
	}

	private static void play(net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forLocalAmbience(event, pitch, volume));
	}

	public static void reset() {
		recovery = 0.0;
		countdown = 0.0;
		inhale = true;
	}
}
