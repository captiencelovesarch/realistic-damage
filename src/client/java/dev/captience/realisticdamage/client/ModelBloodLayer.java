package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;

/**
 * Blood on every body without baked shells (1.7 slice 3): cows, spiders,
 * golems, endermen, the dragon. Each stained region is submitted as a
 * {@link RegionModel} of the renderer's own model, drawn with {@link
 * ModelBlood}'s equal-depth pass, in the colour that body bleeds and the same
 * wet-to-dried tones and alpha {@link BloodBodyLayer#paint} gives a person.
 */
public class ModelBloodLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	/** Region submits this session; read by the client gametest. */
	private static long submitCount;

	public static long submits() {
		return submitCount;
	}

	public ModelBloodLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		if (state.isInvisible || state.entityType == null) {
			return;
		}
		float[] stains = ((BloodStainHolder) state).realisticdamage$bloodStains();
		if (stains == null) {
			return;
		}
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodBodyIntensity;
		if (strength <= 0.0) {
			return;
		}
		BodyPlan plan = MobAnatomy.plan(state.entityType);
		BloodTint tint = BloodTint.ofType(state.entityType);
		int destroyed = ((LimbStateHolder) state).realisticdamage$destroyedMask();
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		M model = getParentModel();
		for (BodyPart part : BodyPart.values()) {
			float level = stains[part.ordinal()];
			if (level < BloodBodyLayer.VISIBLE || !plan.has(part) || LimbStateExtractor.gone(destroyed, part)) {
				continue;
			}
			float[] colour = BloodPalette.world(Math.min(1.0, level * 1.4), tint);
			int alpha = (int) Math.clamp(level * strength * 235.0, 0.0, 235.0);
			if (alpha <= 2) {
				continue;
			}
			collector.order(BloodBodyLayer.ORDER_BLOOD).submitModel(RegionModel.of(model, part), state, poseStack,
					ModelBlood.TYPE, lightCoords, overlay,
					ARGB.colorFromFloat(alpha / 255.0F, colour[0], colour[1], colour[2]), null, 0, null);
			submitCount++;
		}
	}
}
