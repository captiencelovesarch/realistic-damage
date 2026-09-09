package dev.captience.realisticdamage.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.client.mixin.PostChainAccessor;
import dev.captience.realisticdamage.client.mixin.PostPassAccessor;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The afterimages, done for real (1.6.1): an onion skin of the frames before
 * this one, blended under the frame you are seeing.
 *
 * <p>What shipped before under this name was a tinted copy of vanilla's nausea
 * texture, scaled up a little and pulsed. It never contained the previous frame
 * — it could not, the HUD has no access to one — so it was a vignette with a
 * heartbeat, and the playtest report that afterimages "still don't work" was
 * simply correct. This is a post chain on the world render target with a
 * <em>persistent</em> internal target: each frame the chain writes
 * {@code mix(now, history, keep)} to both the screen and the history, so what
 * you saw a moment ago trails behind what you see now and dies away
 * exponentially. Anything that moves — you, a mob, your own hand — leaves
 * itself behind. That is what seeing double when dazed actually looks like.
 *
 * <p>Vanilla bakes a post pass's uniforms once from the chain file; the
 * strength has to change every frame, so this swaps its own writable buffer into
 * the pass's uniform map (see {@link PostPassAccessor}) and rewrites it before
 * each run. The retention is raised to the power of the real frame delta so the
 * trail lasts the same fraction of a second at any frame rate — a ghost that
 * lasted three times longer at 60 fps than at 180 would be a ghost tuned for one
 * monitor.
 *
 * <p>Runs before the greyout chain rather than after it, so the history holds
 * full-colour frames and a colour-drain level changing under it cannot leave a
 * two-tone smear.
 */
public final class Afterimage {
	private Afterimage() {}

	private static final Identifier CHAIN = RealisticDamage.id("afterimage");
	/** The uniform block, and the pass it belongs to, as written in afterimage.json. */
	private static final String PARAMS_BLOCK = "AfterimageConfig";
	private static final int PARAMS_PASS = 0;

	/** Below this nothing is drawn; a 1% blend is a wasted three passes. */
	private static final double MIN_STRENGTH = 0.01;

	/**
	 * Smoothed deep-band haze: rises instantly so a blow lands at once, then
	 * falls linearly, because the vanilla blur it hands over to is
	 * all-or-nothing and needs a ghost to cover its exit. Lived in
	 * {@code ConsciousnessEffects} until the ghost moved here; the blur there
	 * still reads it.
	 */
	private static double haze;
	/** Haze lost per tick on the way down: a touch under a second and a half. */
	private static final double HAZE_FALLOFF = 0.035;

	/** What this frame asked for, 0 to 1. Held frozen while dying. */
	private static double strength;
	/** Whether the chain ran last frame; if not, the history is stale and must be reseeded. */
	private static boolean ranLastFrame;
	private static int lastWidth;
	private static int lastHeight;
	/** Frames the chain actually processed this session; read by the client gametest. */
	private static long framesProcessed;

	private static @Nullable GpuBuffer params;
	private static final ByteBuffer SCRATCH = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());

	/** The doubled-world strength asked for on the last frame, 0 to 1. */
	public static double strength() {
		return strength;
	}

	/** The deep-band haze, 0 to 0.95, before the intensity slider; the blur gate reads it. */
	public static double haze() {
		return haze;
	}

	public static long framesProcessed() {
		return framesProcessed;
	}

	/**
	 * Decides this frame's strength. Called once per rendered frame from the
	 * world render, so it keeps running with the HUD hidden.
	 */
	public static void frame(double deltaTicks) {
		if (ClientState.dying()) {
			// Frozen where death found it: the dying pass inherits the ladder
			// rather than clearing it (see ConsciousnessEffects.renderDeath).
			return;
		}
		ClientEffectsConfig config = ClientEffectsConfig.get();
		if (!ClientState.active() || ClientState.snapshot() == null) {
			haze = 0.0;
			strength = 0.0;
			return;
		}
		boolean deep = ClientState.effectiveConsciousness() < Balance.SYMPTOM_BLUR;
		double hazeTarget = deep ? 0.95 : 0.0;
		if (hazeTarget >= haze) {
			haze = hazeTarget;   // getting worse lands immediately
		} else {
			haze = Math.max(hazeTarget, haze - HAZE_FALLOFF * Math.max(0.0, deltaTicks));
		}
		// Three sources, one symptom. The ladder's own curve carries the 45
		// rung and deepens to 18, where the haze takes over on the way into the
		// blur; a concussion holds a floor of it at any consciousness; and a club
		// to the head adds a burst on top — the same symptom arriving suddenly
		// rather than settling in. All three are behind the one slider.
		double raw = Math.max(haze, Math.max(
				Vitals.afterimageStrength(ClientState.effectiveConsciousness(), ClientState.concussed()),
				ImpactEffect.doubledRaw() * 0.8));
		strength = Math.clamp(raw * config.afterimageIntensity, 0.0, 1.0);
	}

	/**
	 * Runs the chain on the world target if there is anything to show.
	 * {@code realtimeDeltaTicks} is the wall-clock frame delta, which is what the
	 * retention is corrected by.
	 */
	public static void process(RenderTarget main, GraphicsResourceAllocator allocator, double realtimeDeltaTicks) {
		if (strength < MIN_STRENGTH) {
			ranLastFrame = false;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		PostChain chain = client.getShaderManager().getPostChain(CHAIN, LevelTargetBundle.MAIN_TARGETS);
		if (chain == null) {
			ranLastFrame = false;
			return;
		}
		boolean resized = main.width != lastWidth || main.height != lastHeight;
		lastWidth = main.width;
		lastHeight = main.height;
		// A stale history — the effect was off, or the window changed size and
		// the persistent target was reallocated blank — is reseeded with a clean
		// copy rather than blended in: the first frame of a ghost must not be a
		// ghost of nothing.
		double keep = 0.0;
		if (ranLastFrame && !resized) {
			double perTick = Mth.lerp(strength, Balance.AFTERIMAGE_KEEP_MIN, Balance.AFTERIMAGE_KEEP_MAX);
			keep = Math.pow(perTick, Math.max(0.05, realtimeDeltaTicks));
		}
		if (!writeParams(chain, keep)) {
			ranLastFrame = false;
			return;
		}
		chain.process(main, allocator);
		framesProcessed++;
		ranLastFrame = true;
	}

	/**
	 * Puts {@code keep} into the pass's uniform block, installing our own
	 * writable buffer over vanilla's baked one the first time (and again after
	 * a resource reload has rebuilt the chain).
	 */
	private static boolean writeParams(PostChain chain, double keep) {
		List<PostPass> passes = ((PostChainAccessor) chain).realisticdamage$passes();
		if (passes.size() <= PARAMS_PASS) {
			return false;
		}
		Map<String, GpuBuffer> uniforms = ((PostPassAccessor) passes.get(PARAMS_PASS)).realisticdamage$customUniforms();
		GpuBuffer current = uniforms.get(PARAMS_BLOCK);
		if (current == null) {
			return false;   // the chain file no longer declares the block
		}
		if (params == null || params.isClosed() || current != params) {
			// Vanilla's buffer is uniform-only; ours also accepts copies. The one
			// we replace is closed here, and the pass closes ours when the chain
			// is rebuilt, which is why the closed check above has to come first.
			params = RenderSystem.getDevice().createBuffer(() -> "realisticdamage afterimage params",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
			uniforms.put(PARAMS_BLOCK, params);
			if (current != params) {
				current.close();
			}
		}
		SCRATCH.clear();
		SCRATCH.putFloat((float) keep).putFloat(0.0F).putFloat(0.0F).putFloat(0.0F);
		SCRATCH.flip();
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(params.slice(), SCRATCH);
		return true;
	}

	/** Disconnect: forget the history so a new world does not open on the last one's ghost. */
	public static void reset() {
		haze = 0.0;
		strength = 0.0;
		ranLastFrame = false;
	}
}
