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
import dev.captience.realisticdamage.client.compat.Vr;
import dev.captience.realisticdamage.client.mixin.PostChainAccessor;
import dev.captience.realisticdamage.client.mixin.PostPassAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * The screen effects on the eye rather than on the HUD, for VR (1.7 alpha.10).
 *
 * <p>Vivecraft draws the HUD once a frame onto a panel in the world. Every
 * {@link Lens} call made while drawing it is recorded here instead of drawn;
 * {@link #publish()} at the end of the HUD hands the recording over, and each
 * eye's world render replays it through the {@code vr_lens} post chain (see
 * {@code vr_lens.fsh}) as the last thing before Vivecraft's own per-eye pass.
 * Both eyes see the same lens, as both eyes see the same HUD in flat play.
 *
 * <p>The replay covers the HUD panel and any open screen too, as the blackout
 * covers the HUD in flat play. A screen in flat play draws above the blackout
 * so the way out — the death screen's Respawn — always stays readable; in VR it
 * cannot, so while a screen is open the darkness is held to
 * {@link #SCREEN_BLACK_CAP}.
 */
public final class VrLens {
	private VrLens() {}

	private static final Identifier CHAIN = RealisticDamage.id("vr_lens");
	private static final String PARAMS_BLOCK = "LensConfig";
	private static final int PARAMS_PASS = 0;
	/** Must match MAX_OPS in vr_lens.fsh and the entry count in vr_lens.json. */
	static final int MAX_OPS = 48;
	private static final int FLOATS_PER_OP = 16;
	private static final int BUFFER_BYTES = (1 + MAX_OPS * 4) * 16;
	/** How dark the lens may make an open screen; see the class note. */
	static final double SCREEN_BLACK_CAP = 0.7;
	/** A recording older than this many frames is not replayed: the HUD stopped drawing (F1, a loading screen). */
	private static final long STALE_FRAMES = 3;

	private static final int MODE_FILL = 0;
	private static final int MODE_VIGNETTE = 1;
	private static final int MODE_DROP = 2;

	/** Being written by the HUD this frame. */
	private static final float[] recording = new float[MAX_OPS * FLOATS_PER_OP];
	private static int recordingOps;
	private static double recordingBlur;
	private static double recordingAspect = 16.0 / 9.0;
	private static boolean capturing;
	private static boolean overflowed;

	/** The last whole recording, replayed onto each eye. */
	private static final float[] published = new float[MAX_OPS * FLOATS_PER_OP];
	private static int publishedOps;
	private static double publishedBlur;
	private static double publishedAspect = 16.0 / 9.0;
	private static long publishedFrame = Long.MIN_VALUE / 2;

	/** World frames rendered, counted on the first pass of each. */
	private static long frame;
	private static long eyesProcessed;
	private static boolean forcedInFlat;

	private static @Nullable GpuBuffer params;
	private static final ByteBuffer SCRATCH = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());

	/** Whether the lens is in use: in VR, or forced on in flat play by a client gametest. */
	public static boolean enabled() {
		return forcedInFlat || Vr.active();
	}

	/** True while the HUD's screen effects are being recorded rather than drawn. */
	public static boolean capturing() {
		return capturing;
	}

	/**
	 * Routes the lens through the flat-play render too, so a client gametest can
	 * see what a headset would without one.
	 */
	public static void forceInFlat(boolean forced) {
		forcedInFlat = forced;
	}

	/** Eye passes the chain actually ran on; read by the client gametests. */
	public static long eyesProcessed() {
		return eyesProcessed;
	}

	/** The world frame the last recording was published on; read by the client gametests. */
	public static long publishedFrame() {
		return publishedFrame;
	}

	/** World frames counted (first passes only); read by the client gametests. */
	public static long frames() {
		return frame;
	}

	/** Ops in the recording last handed to the eyes; read by the client gametests. */
	public static int publishedOps() {
		return publishedOps;
	}

	/** Starts this frame's recording; the screen effects call this first. */
	public static void begin(GuiGraphicsExtractor graphics) {
		capturing = enabled();
		recordingOps = 0;
		recordingBlur = 0.0;
		overflowed = false;
		recordingAspect = graphics.guiHeight() > 0 ? (double) graphics.guiWidth() / graphics.guiHeight() : 16.0 / 9.0;
	}

	/** Hands the recording to the eyes; the last HUD element calls this. */
	public static void publish() {
		if (!capturing) {
			return;
		}
		capturing = false;
		System.arraycopy(recording, 0, published, 0, recordingOps * FLOATS_PER_OP);
		publishedOps = recordingOps;
		publishedBlur = recordingBlur;
		publishedAspect = recordingAspect;
		publishedFrame = frame;
		if (overflowed) {
			RealisticDamage.LOGGER.debug("VR lens recording overflowed {} ops; the rest were dropped", MAX_OPS);
		}
	}

	/** How dark the blackout may be drawn this frame; see the class note. */
	public static double blackCap() {
		return capturing && Minecraft.getInstance().gui.screen() != null ? SCREEN_BLACK_CAP : 1.0;
	}

	static void vignette(float red, float green, float blue) {
		int at = slot();
		if (at < 0) {
			return;
		}
		put(at, 0.0F, 0.0F, 1.0F, 1.0F);
		put(at + 4, red, green, blue, 1.0F);
		put(at + 8, 0.0F, 0.0F, 0.0F, 0.0F);
		put(at + 12, MODE_VIGNETTE, 0.0F, 0.0F, 0.0F);
	}

	static void fill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int top, int bottom) {
		if (ARGB.alpha(top) == 0 && ARGB.alpha(bottom) == 0) {
			return;
		}
		int at = slot();
		if (at < 0) {
			return;
		}
		rect(graphics, at, x0, y0, x1, y1);
		colour(at + 4, top);
		colour(at + 8, bottom);
		put(at + 12, MODE_FILL, 0.0F, 0.0F, 0.0F);
	}

	static void drop(GuiGraphicsExtractor graphics, int x, int y, int size, int variant, int argb) {
		int at = slot();
		if (at < 0) {
			return;
		}
		rect(graphics, at, x, y, x + size, y + size);
		colour(at + 4, argb);
		put(at + 8, 0.0F, 0.0F, 0.0F, 0.0F);
		float cell = (float) Lens.CELL / Lens.ATLAS;
		put(at + 12, MODE_DROP, (variant % 2) * cell, (variant / 2) * cell, cell);
	}

	static void blur() {
		recordingBlur = 1.0;
	}

	private static int slot() {
		if (recordingOps >= MAX_OPS) {
			overflowed = true;
			return -1;
		}
		return recordingOps++ * FLOATS_PER_OP;
	}

	private static void rect(GuiGraphicsExtractor graphics, int at, int x0, int y0, int x1, int y1) {
		float width = Math.max(1, graphics.guiWidth());
		float height = Math.max(1, graphics.guiHeight());
		put(at, Math.min(x0, x1) / width, Math.min(y0, y1) / height,
				Math.max(x0, x1) / width, Math.max(y0, y1) / height);
	}

	private static void colour(int at, int argb) {
		put(at, ARGB.red(argb) / 255.0F, ARGB.green(argb) / 255.0F, ARGB.blue(argb) / 255.0F, ARGB.alpha(argb) / 255.0F);
	}

	private static void put(int at, float a, float b, float c, float d) {
		recording[at] = a;
		recording[at + 1] = b;
		recording[at + 2] = c;
		recording[at + 3] = d;
	}

	/** Counts a world frame; called on the first world pass of each. */
	public static void frame() {
		frame++;
	}

	/**
	 * Replays the last recording onto this eye's world target. {@code eye} is
	 * {@code Vr.eye()}: the desktop mirror and the handheld camera get no lens.
	 */
	public static void process(RenderTarget main, GraphicsResourceAllocator allocator, int eye) {
		if (!enabled() || eye == Vr.OTHER || (publishedOps == 0 && publishedBlur <= 0.0)
				|| frame - publishedFrame > STALE_FRAMES) {
			return;
		}
		PostChain chain = Minecraft.getInstance().getShaderManager().getPostChain(CHAIN, LevelTargetBundle.MAIN_TARGETS);
		if (chain == null || !writeParams(chain, main)) {
			return;
		}
		chain.process(main, allocator);
		eyesProcessed++;
	}

	/** The recording into the pass's uniform block, over vanilla's baked one; see Afterimage.writeParams. */
	private static boolean writeParams(PostChain chain, RenderTarget main) {
		List<PostPass> passes = ((PostChainAccessor) chain).realisticdamage$passes();
		if (passes.size() <= PARAMS_PASS) {
			return false;
		}
		Map<String, GpuBuffer> uniforms = ((PostPassAccessor) passes.get(PARAMS_PASS)).realisticdamage$customUniforms();
		GpuBuffer current = uniforms.get(PARAMS_BLOCK);
		if (current == null) {
			return false;
		}
		if (params == null || params.isClosed() || current != params) {
			params = RenderSystem.getDevice().createBuffer(() -> "realisticdamage vr lens params",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
			uniforms.put(PARAMS_BLOCK, params);
			if (current != params) {
				current.close();
			}
		}
		SCRATCH.clear();
		SCRATCH.putFloat(publishedOps).putFloat((float) publishedBlur).putFloat((float) publishedAspect).putFloat(0.0F);
		for (int i = 0; i < MAX_OPS * FLOATS_PER_OP; i++) {
			SCRATCH.putFloat(i < publishedOps * FLOATS_PER_OP ? published[i] : 0.0F);
		}
		SCRATCH.flip();
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(params.slice(), SCRATCH);
		return true;
	}

	/** Disconnect: no lens carried into the next world. */
	public static void reset() {
		capturing = false;
		recordingOps = 0;
		publishedOps = 0;
		publishedBlur = 0.0;
	}
}
