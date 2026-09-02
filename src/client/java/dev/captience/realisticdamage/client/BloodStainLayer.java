package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The translucent particle layer, minus the depth write (1.6 fix).
 *
 * <p>A stain is a decal. {@link BloodStains#land} places every mark on a given
 * block face at the same {@link dev.captience.realisticdamage.config.Balance#STAIN_SURFACE_OFFSET}
 * along that face's normal, from a landing coordinate that is the
 * collision-resolved face itself — so every mark on one floor sits at a
 * bit-identical height, and every pair of marks in a pool is exactly coplanar.
 * That shared offset is deliberate; it is what stops a caller getting the lift
 * subtly different. What it also guarantees is that two overlapping marks have
 * mathematically equal depth, and two coplanar quads with different vertices do
 * not interpolate that plane to the same low bits. {@code GREATER_THAN_OR_EQUAL}
 * settles the exact ties in favour of whoever drew last and flips per pixel on
 * the near-ties, which at a grazing angle is a stipple across the whole pool and
 * head-on is a hard-edged lens with a seam.
 *
 * <p>The lift is not the problem and raising it would not help: at three blocks
 * a reversed-Z D32_FLOAT buffer with a 0.05 near plane resolves about 0.34 µm,
 * so 0.011 blocks is already some 33,000 depth units of clearance against the
 * ground — and in any case both marks are raised by the same amount, so their
 * fight with each other is untouched by it.
 *
 * <p>So the fix is to stop consulting the depth a stain writes rather than to
 * try to separate the values. A mark lies barely a centimetre in front of a
 * surface that has already written depth in the terrain pass; nothing can ever
 * be correctly occluded by the mark that is not already occluded by the block
 * under it, so its own depth write buys nothing and costs the entire coplanar
 * fight. Overlap then resolves by draw order alone, and the particle engine
 * draws its group in FIFO insertion order — oldest mark first, newest last — so
 * the freshest blood lands on top, deterministically, every frame. That is both
 * stable and correct.
 *
 * <p>Vanilla makes exactly this trade one screen away in the same file:
 * {@code RenderPipelines.WEATHER_NO_DEPTH_WRITE} is the weather snippet with
 * {@code writeDepth} turned off, for rain falling in front of the world.
 *
 * <p>Deliberately not fixed by reducing overlap. Lowering the mark cap,
 * shrinking the pool radius or raising the merge distance would each bring back
 * the splat bug the pooling work fixed, and {@code BloodClientGameTest}'s
 * accretion assertions exist to catch precisely that. Ink area, mark count and
 * growth rate are untouched here: this is a change to how one quad is submitted
 * to the GPU and to nothing else.
 *
 * <p><b>Nothing asserts this, and nothing can.</b> Every number the suite can
 * query — {@code count()}, {@code inkArea()}, {@code largestMark()} — is
 * byte-identical either way; the state that is wrong lives in a depth buffer
 * that has already been resolved. The one automated form that could see it is a
 * screenshot heuristic, and it was measured rather than assumed. Counting
 * 4-connected components of the dried-blood colour in
 * {@code rd_blood_08_pool.png} gives 31 components (24 of them under 50 px) on
 * one run of the broken build and 2 on the fixed one — a margin wide enough to
 * assert on. But the same measurement on an earlier run of the same broken
 * build gave 4 components, because which marks overlap in the frame the
 * screenshot happens to catch varies run to run. An assertion built on that
 * would fail green builds and pass broken ones by turns, which is worse than no
 * assertion; and the artifact is rasteriser-dependent besides, with the suite on
 * llvmpipe and the player on RADV. The acceptance check is a human at a grazing
 * angle: bleed heavily standing still for ~15 s, walk three or four blocks off,
 * and look back with the floor near eye level. From directly above the defect
 * collapses into a clean lens and is invisible.
 */
final class BloodStainLayer {
	private BloodStainLayer() {}

	/**
	 * Registered rather than merely constructed, so that it is precompiled and
	 * validated alongside vanilla's own on every resource reload. Registration is
	 * not what makes it usable — {@code GlRenderPass} compiles an unknown pipeline
	 * lazily through {@code getOrCompilePipeline}, which is what carries this one
	 * through the first reload, since the class does not load until the first
	 * stain lands and that is long after {@code getStaticPipelines()} was read.
	 * It is what makes it behave like the pipelines around it thereafter.
	 *
	 * <p>The location is built through {@link RealisticDamage#id} because
	 * {@code withLocation(String)} is {@code Identifier.withDefaultNamespace} —
	 * it would put our path under {@code minecraft:} and throw on the colon.
	 */
	static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
					.withLocation(RealisticDamage.id("pipeline/blood_stain"))
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	/**
	 * {@code SingleQuadParticle.Layer.TRANSLUCENT} with our pipeline in place of
	 * {@code TRANSLUCENT_PARTICLE}: same atlas, same translucent bucket, same
	 * blend. Only the depth write differs, so the other two components are read
	 * off vanilla's own layer rather than restated — a stain draws from whichever
	 * atlas an ordinary particle draws from, and saying so this way cannot drift
	 * from it (and does not touch the deprecated {@code TextureAtlas} constant).
	 */
	static final SingleQuadParticle.Layer LAYER = new SingleQuadParticle.Layer(
			SingleQuadParticle.Layer.TRANSLUCENT.translucent(),
			SingleQuadParticle.Layer.TRANSLUCENT.textureAtlasLocation(),
			PIPELINE);
}
