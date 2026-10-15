package dev.captience.realisticdamage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a nearby explosion does to your ears and eyes (1.7).
 *
 * <p>Until 1.7 a blast was only felt if it <em>hurt</em> you: the cue rode the
 * damage payload, so a creeper that went off three blocks away behind a Blast
 * Protection chestplate, or TNT detonating across a ravine, arrived as nothing
 * but vanilla's bang. And even a point-blank one only fed its "deafness" into the
 * tinnitus tone — the world itself stayed perfectly audible underneath the ring.
 *
 * <p>Now every explosion the client is told about is judged on its own: how big
 * it was, how far away, and whether there was a wall in between. Close enough,
 * and it takes your hearing on the spot — the world drops to a dull, distant
 * thud under a loud ring, holds there, and comes back over many seconds. The
 * bang itself is let through first: the cut lands a few ticks after the
 * explosion, because the crack you heard just before everything went quiet is
 * the half of the experience that sells the other half. With it come a flash,
 * a hard shake, a haze of dust, and for the closest blasts a moment of seeing
 * double.
 *
 * <p>Presentation only, client-side, from the vanilla explosion packet: the
 * server decides nothing here, and a server without the mod still deafens a
 * client that has it.
 */
public final class BlastShock {
	private BlastShock() {}

	/** Ticks between the explosion and the hearing going, so the bang is heard first. */
	private static final double ONSET_DELAY = 3.0;

	/** Hearing loss, 0 (none) to 1 (nothing but the ring). */
	private static double deafness;
	/** Ticks the loss stays at full before it starts to come back. */
	private static double hold;
	/** Ticks for full hearing to return from full loss, set by the worst blast. */
	private static double recovery = 200.0;
	/** A loss waiting out the onset delay, and how long it has left to wait. */
	private static double pending;
	private static double pendingDelay;
	private static double pendingRecovery;
	private static double pendingHold;

	/** The bright flash of a blast with line of sight to it. */
	private static double flash;
	/** Dust and smoke in the air after a close one. */
	private static double dust;
	/** Seeing double for a moment after a very close one. */
	private static double daze;

	/** How many blasts were judged loud enough to do anything; read by the client gametest. */
	private static long shocks;

	public static long shocks() {
		return shocks;
	}

	/**
	 * How loud a blast of this radius is at this distance, 0 to 1, before any
	 * wall in the way. Full within about one radius of it, falling off over the
	 * next three or four: TNT (radius 4) takes your hearing outright at four
	 * blocks, leaves you badly muffled at eight, and is merely a bang at twenty.
	 */
	public static double loudness(double radius, double distance) {
		if (!(radius > 0.0)) {
			return 0.0;
		}
		return Mth.clamp(1.8 * radius / Math.max(distance, 0.5) - 0.3, 0.0, 1.0);
	}

	/** Called from the explosion packet handler, after vanilla has played the bang. */
	public static void onExplosion(Vec3 center, float radius) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || !ClientState.active()) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		double distance = eye.distanceTo(center);
		double loud = loudness(radius, distance);
		if (loud < 0.05) {
			return;
		}
		// A wall between you and it takes the edge off the pressure wave and
		// hides the flash entirely. Sound bends round corners; light does not.
		BlockHitResult hit = client.level.clip(new ClipContext(center, eye,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean seen = hit.getType() == HitResult.Type.MISS;
		if (!seen) {
			loud *= 0.55;
		}
		shock(loud, seen, center);
	}

	/**
	 * A blast that hurt you (the damage payload): at least this loud, whatever
	 * the packet said — the packet can arrive for a blast you were judged to be
	 * shielded from and still be wrong about how close the shrapnel came.
	 */
	public static void onBlastHit(double magnitude) {
		if (magnitude > 0.05) {
			shock(Math.min(1.0, 0.4 + magnitude * 0.6), true, null);
		}
	}

	private static void shock(double loud, boolean seen, @org.jspecify.annotations.Nullable Vec3 center) {
		shocks++;
		VrHaptics.blast(loud);
		double hearing = ClientEffectsConfig.get().blastDeafness();
		// The loss itself waits for the bang; everything else lands now.
		if (loud * hearing > pending) {
			pending = loud * hearing;
			pendingDelay = ONSET_DELAY;
			// Bigger blasts hold longer and take longer to wear off: half a
			// second to two seconds at the floor, then up to twenty seconds back.
			pendingHold = 10.0 + 30.0 * loud;
			pendingRecovery = 80.0 + 320.0 * loud * loud;
		}
		double visual = ClientEffectsConfig.get().impactIntensity * hearing;
		if (visual <= 0.0) {
			return;
		}
		if (seen) {
			flash = Math.max(flash, loud);
		}
		dust = Math.max(dust, Math.clamp((loud - 0.15) / 0.6, 0.0, 1.0));
		if (loud > 0.55) {
			daze = Math.max(daze, (loud - 0.55) / 0.45);
		}
		CameraMotion.blast(loud, visual);
	}

	/** Advances everything; once per rendered frame, game-time delta. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0) {
			return;
		}
		if (pending > 0.0) {
			pendingDelay -= deltaTicks;
			if (pendingDelay <= 0.0) {
				if (pending >= deafness) {
					hold = Math.max(hold, pendingHold);
					recovery = pendingRecovery;
				}
				deafness = Math.max(deafness, pending);
				pending = 0.0;
			}
		}
		if (hold > 0.0) {
			hold -= deltaTicks;
		} else if (deafness > 0.0) {
			deafness = Math.max(0.0, deafness - deltaTicks / recovery);
		}
		flash = Math.max(0.0, flash - deltaTicks * 0.06);
		dust = Math.max(0.0, dust - deltaTicks * 0.008);
		daze = Math.max(0.0, daze - deltaTicks * 0.02);
	}

	/**
	 * Hearing loss for the audio layer, 0 to 1. Already scaled by the Blast
	 * Deafening control at the moment of the blast.
	 */
	public static double deafness() {
		return ClientState.active() ? deafness : 0.0;
	}

	/** The ring that fills the silence: loudest at the start, gone with the deafness. */
	public static double ring() {
		double d = deafness();
		return d <= 0.0 ? 0.0 : Math.pow(d, 0.7);
	}

	public static double flash() {
		return flash;
	}

	public static double dust() {
		return dust;
	}

	/** Seeing double, for the afterimage. */
	public static double daze() {
		return daze;
	}

	public static void reset() {
		deafness = 0.0;
		hold = 0.0;
		pending = 0.0;
		pendingDelay = 0.0;
		flash = 0.0;
		dust = 0.0;
		daze = 0.0;
	}
}
