package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Blood drawn onto a model's own surface (1.7 slice 3).
 *
 * <p>The humanoid families get baked shells a hair proud of the skin. Every
 * other model is drawn again, region by region, with the blood texture and a
 * depth test of <em>equal</em> — the technique vanilla uses for armour trims —
 * so it lands exactly on the surface the model just wrote, with no per-model
 * geometry and no z-fighting. Anything in front of the surface (a sheep's
 * wool, a horse's armour) wins the depth test, so blood is under the wool, as
 * it should be.
 *
 * <p>Translucent blend, entity lighting and overlay, no culling and no depth
 * write: the entity translucent pipeline with one comparison changed.
 */
public final class ModelBlood {
	private ModelBlood() {}

	static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
					.withLocation(RealisticDamage.id("pipeline/model_blood"))
					.withShaderDefine("ALPHA_CUTOUT", 0.1F)
					.withShaderDefine("PER_FACE_LIGHTING")
					.withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withCull(false)
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	static final RenderType TYPE = RenderType.create("realisticdamage_model_blood",
			RenderSetup.builder(PIPELINE)
					.withTexture("Sampler0", BloodBodyLayer.TEXTURE)
					.useLightmap()
					.useOverlay()
					.sortOnUpload()
					.createRenderSetup());

	/** Tells Iris to draw this as entity geometry; see {@code BloodStainLayer.registerWithShaderLoader}. */
	static void registerWithShaderLoader() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
			return;
		}
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object iris = api.getMethod("getInstance").invoke(null);
			Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			api.getMethod("assignPipeline", RenderPipeline.class, program)
					.invoke(iris, PIPELINE, enumConstant(program, "ENTITIES_TRANSLUCENT"));
			Class<?> shadowProgram = Class.forName("net.irisshaders.iris.api.v0.IrisShadowProgram");
			api.getMethod("assignPipelineShadow", RenderPipeline.class, shadowProgram)
					.invoke(iris, PIPELINE, enumConstant(shadowProgram, "SHADOW"));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			RealisticDamage.LOGGER.warn("Could not register the model blood pipeline with Iris; "
					+ "blood on animals may not draw with a shader pack on", e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumConstant(Class<?> type, String name) {
		return Enum.valueOf((Class) type, name);
	}
}
