package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.ScarLevel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Blood and stumps on a body that is not a {@code HumanoidModel} (1.7): the
 * illagers, and the villager-shaped witch. The parts are found by name off the
 * model's root, the shells come from {@link LimbMeshes} in the matching build,
 * and the painting itself is {@link BloodBodyLayer#paint}, so a pillager's
 * bloody arm and a zombie's are the same statement.
 *
 * <p>A model whose arms are one crossed piece (the villager build) takes the
 * worse of its two arm wounds onto that piece, and loses no arm to a stump —
 * there is no half of a crossed pair to remove.
 */
public class FigureBloodLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private final RenderLayerParent<S, M> parent;
	private final LimbMeshes.Kind kind;
	private final ModelPart[] blood;
	private final ModelPart[] scars;
	private final ModelPart[] stumps;
	private final ModelPart[] caps;
	private final ModelPart[] stumpBlood;
	private final ModelPart crossedArmsBlood;

	/** Submits onto a non-humanoid body this session; read by the client gametest. */
	private static long submitCount;

	public static long submits() {
		return submitCount;
	}

	public FigureBloodLayer(RenderLayerParent<S, M> parent, LimbMeshes.Kind kind) {
		super(parent);
		this.parent = parent;
		this.kind = kind;
		this.blood = LimbMeshes.full(kind, BloodBodyLayer.SKIN_OFFSET);
		this.scars = LimbMeshes.full(kind, BloodBodyLayer.SCAR_OFFSET);
		this.stumps = LimbMeshes.stumps(kind, 0.0F);
		this.caps = LimbMeshes.stumpCaps(kind);
		this.stumpBlood = LimbMeshes.stumps(kind, BloodBodyLayer.SKIN_OFFSET);
		this.crossedArmsBlood = kind == LimbMeshes.Kind.VILLAGER ? LimbMeshes.crossedArms(BloodBodyLayer.SKIN_OFFSET) : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodBodyIntensity;
		if (strength <= 0.0) {
			return;
		}
		BloodStainHolder holder = (BloodStainHolder) state;
		float[] stains = holder.realisticdamage$bloodStains();
		int destroyed = ((LimbStateHolder) state).realisticdamage$destroyedMask();
		if (stains == null && destroyed == 0) {
			return;
		}
		ModelPart root = getParentModel().root();
		Identifier skin = parent instanceof LivingEntityRenderer<?, ?, ?> living
				? ((LivingEntityRenderer<?, S, M>) living).getTextureLocation(state)
				: net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		BloodTint tint = BloodTint.ofType(state.entityType);
		boolean crossed = crossedArmsBlood != null;
		for (BodyPart part : BodyPart.values()) {
			float level = stains == null ? 0.0F : stains[part.ordinal()];
			if (crossed && part.isArm()) {
				continue;   // painted once, below, onto the crossed piece
			}
			ModelPart source = partFor(root, part);
			if (source == null) {
				continue;
			}
			if (LimbStateExtractor.gone(destroyed, part) && part.isLimb()) {
				poseStack.pushPose();
				source.translateAndRotate(poseStack);
				BloodBodyLayer.submitStump(collector, poseStack, skin, stumps[part.ordinal()], caps[part.ordinal()],
						stumpBlood[part.ordinal()], level, strength, lightCoords, overlay, tint);
				poseStack.popPose();
				submitCount++;
				continue;
			}
			if (level < BloodBodyLayer.VISIBLE || !source.visible) {
				continue;
			}
			poseStack.pushPose();
			source.translateAndRotate(poseStack);
			BloodBodyLayer.paint(collector, poseStack, scars[part.ordinal()], blood[part.ordinal()],
					ScarLevel.NONE, level, strength, lightCoords, overlay, tint);
			poseStack.popPose();
			submitCount++;
		}
		if (crossed && stains != null) {
			float level = Math.max(stains[BodyPart.LEFT_ARM.ordinal()], stains[BodyPart.RIGHT_ARM.ordinal()]);
			ModelPart arms = root.getChild("arms");
			if (level >= BloodBodyLayer.VISIBLE && arms.visible) {
				poseStack.pushPose();
				arms.translateAndRotate(poseStack);
				BloodBodyLayer.paint(collector, poseStack, crossedArmsBlood, crossedArmsBlood,
						ScarLevel.NONE, level, strength, lightCoords, overlay, tint);
				poseStack.popPose();
				submitCount++;
			}
		}
	}

	private static ModelPart partFor(ModelPart root, BodyPart part) {
		String name = switch (part) {
			case HEAD -> "head";
			case TORSO -> "body";
			case LEFT_ARM -> "left_arm";
			case RIGHT_ARM -> "right_arm";
			case LEFT_LEG -> "left_leg";
			case RIGHT_LEG -> "right_leg";
		};
		return root.hasChild(name) ? root.getChild(name) : null;
	}
}
