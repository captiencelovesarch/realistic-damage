package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The client's view of the server-authoritative state (spec §47). Reads the
 * synced snapshot attachment and smooths the vitals so effects fade instead of
 * stepping. Never decides anything — presentation only.
 */
public final class ClientState {
	private ClientState() {}

	private static double smoothedConsciousness = Balance.CONSCIOUSNESS_MAX;
	private static double smoothedBlood = Balance.BLOOD_MAX;
	/** Rises while unconscious, falls after waking — drives the blackout fade. */
	private static double unconsciousFade;

	@Nullable
	public static BodySnapshot snapshot() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : player.getAttached(RDAttachments.SNAPSHOT);
	}

	/** Called each client tick: eases the smoothed values toward the synced ones. */
	public static void tick() {
		BodySnapshot snapshot = snapshot();
		if (snapshot == null) {
			smoothedConsciousness = Balance.CONSCIOUSNESS_MAX;
			smoothedBlood = Balance.BLOOD_MAX;
			unconsciousFade = 0.0;
			return;
		}
		smoothedConsciousness += (snapshot.consciousness() - smoothedConsciousness) * 0.15;
		smoothedBlood += (snapshot.blood() - smoothedBlood) * 0.15;
		double target = snapshot.unconscious() ? 1.0 : 0.0;
		unconsciousFade += (target - unconsciousFade) * 0.12;
	}

	public static double consciousness() {
		return smoothedConsciousness;
	}

	public static double blood() {
		return smoothedBlood;
	}

	public static double unconsciousFade() {
		return unconsciousFade;
	}

	public static boolean unconscious() {
		BodySnapshot snapshot = snapshot();
		return snapshot != null && snapshot.unconscious();
	}

	/**
	 * Ramp of a symptom that begins at {@code threshold} and reaches full
	 * strength at {@code full}: 0 above the threshold, 1 at or below full.
	 * Smooth and deterministic — no flicker (spec §24).
	 */
	public static double ramp(double threshold, double full) {
		double consciousness = smoothedConsciousness;
		if (consciousness >= threshold) {
			return 0.0;
		}
		if (consciousness <= full) {
			return 1.0;
		}
		return (threshold - consciousness) / (threshold - full);
	}

	/**
	 * How close to death the player is on the ordinary health bar, 0 (fine) to 1
	 * (about to die). Read straight from vanilla client state — no sync needed.
	 */
	public static double lowHealth() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0.0;
		}
		double max = player.getMaxHealth();
		if (max <= 0.0) {
			return 0.0;
		}
		double fraction = player.getHealth() / max;
		if (fraction >= Balance.LOW_HP_FRACTION) {
			return 0.0;
		}
		return Math.clamp(1.0 - fraction / Balance.LOW_HP_FRACTION, 0.0, 1.0);
	}

	/** Whether Realistic Damage effects should show at all right now. */
	public static boolean active() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && !client.player.isCreative() && !client.player.isSpectator();
	}
}
