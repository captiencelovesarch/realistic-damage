package dev.captience.realisticdamage.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.client.VRRenderingAPI;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.api.data.VRBodyPart;

/**
 * Everything the client asks about Vivecraft (1.7 alpha.10), and the only class
 * that touches its API. Vivecraft is optional: every call here answers as flat
 * play would when it is absent, and its classes are only reached through
 * {@link Api}, which the JVM does not load until a method of it runs — which
 * it never does unless the mod is there.
 *
 * <p>What VR changes for this mod, and why each answer below exists:
 * <ul>
 * <li>Vivecraft renders the world once per eye (and again for the desktop
 * mirror or the handheld camera), calling {@code GameRenderer.render} for each.
 * Anything that advances per rendered frame must advance on the first of those
 * passes only — {@link #firstPass()} — or it runs at twice the speed.</li>
 * <li>Anything with a history (the afterimage) must keep one per eye —
 * {@link #eye()} — or each eye is shown the other's last frame.</li>
 * <li>The HUD is drawn once, onto a panel floating in the world. Effects that
 * belong on the eye rather than on the HUD are recorded there and composited
 * onto each eye instead (see {@code VrLens}).</li>
 * <li>The camera is the headset. Vivecraft cancels the hurt bob and the view
 * bob, which is where the camera motion and death camera apply, and the
 * vanilla first-person hand, which is where the arm poses apply; nothing may
 * move the view a player's own head is not moving.</li>
 * </ul>
 */
public final class Vr {
	private Vr() {}

	/** Which history an eye pass keeps; see {@link #eye()}. */
	public static final int LEFT = 0;
	public static final int RIGHT = 1;
	/** A pass that is not an eye: the desktop mirror, the handheld camera, a scope, the GUI. */
	public static final int OTHER = -1;

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("vivecraft");

	/** True if Vivecraft is installed, whether or not VR is running. */
	public static boolean loaded() {
		return LOADED;
	}

	/** True while Vivecraft is rendering to a headset. */
	public static boolean active() {
		return LOADED && Api.active();
	}

	/**
	 * True on the one world render per frame that per-frame integrators may run
	 * on. Always true in flat play.
	 */
	public static boolean firstPass() {
		return !active() || Api.firstPass();
	}

	/**
	 * The eye this world render is for: {@link #LEFT} in flat play (one view, one
	 * history), {@link #LEFT} or {@link #RIGHT} for the two eye passes, and
	 * {@link #OTHER} for everything else Vivecraft renders.
	 */
	public static int eye() {
		return active() ? Api.eye() : LEFT;
	}

	/** True if Vivecraft's "reverse hands" is on: the main hand is held in the left controller. */
	public static boolean handsSwapped() {
		return active() && Api.handsSwapped();
	}

	/**
	 * A haptic pulse on one controller or both. Ignored in flat play, seated
	 * play (Vivecraft refuses it), and for an amplitude too small to feel.
	 *
	 * @param seconds how long the motor runs
	 * @param frequency the motor frequency, Hz
	 * @param amplitude 0 to 1 (clamped)
	 */
	public static void pulse(Hand hand, float seconds, float frequency, float amplitude) {
		if (!active() || amplitude < 0.02F || seconds <= 0.0F) {
			return;
		}
		Api.pulse(hand, seconds, frequency, Math.min(1.0F, amplitude));
	}

	/**
	 * Where the headset is facing in the room (2.0), unit; null in flat play.
	 * The room, not the world: Vivecraft turning the world is not the player
	 * turning their head.
	 */
	public static net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 headDirection() {
		return active() ? Api.headDirection() : null;
	}

		public enum Hand {
		MAIN, OFF, BOTH
	}

	/** The only references to Vivecraft's classes. Loaded on first use, i.e. never without it. */
	private static final class Api {
		static boolean active() {
			return VRClientAPI.instance().isVRActive();
		}

		static boolean handsSwapped() {
			return VRClientAPI.instance().isLeftHanded();
		}

		static boolean firstPass() {
			return VRRenderingAPI.instance().isFirstRenderPass();
		}

		static int eye() {
			RenderPass pass = VRRenderingAPI.instance().getCurrentRenderPass();
			if (pass == RenderPass.LEFT) {
				return LEFT;
			}
			if (pass == RenderPass.RIGHT) {
				return RIGHT;
			}
			// CENTER is the single undistorted view a mirror mode may add; it is
			// not seen in the headset, so it keeps no history of its own.
			return OTHER;
		}

		static net.minecraft.world.phys.Vec3 headDirection() {
			var pose = VRClientAPI.instance().getLatestRoomPose();
			return pose == null ? null : pose.getHead().getDir();
		}

		static void pulse(Hand hand, float seconds, float frequency, float amplitude) {
			VRClientAPI api = VRClientAPI.instance();
			if (hand != Hand.OFF) {
				api.triggerHapticPulse(VRBodyPart.MAIN_HAND, seconds, frequency, amplitude, 0.0F);
			}
			if (hand != Hand.MAIN) {
				api.triggerHapticPulse(VRBodyPart.OFF_HAND, seconds, frequency, amplitude, 0.0F);
			}
		}
	}
}
