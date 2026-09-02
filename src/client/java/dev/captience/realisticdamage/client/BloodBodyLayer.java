package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Blood drawn onto the player model, one region at a time (1.5).
 *
 * <p>Rendered per region rather than as one pass over the whole model, which is
 * the point of the layer: a cut left arm has to be a bloody left arm and a
 * clean right one, and a single tinted pass could only ever say "this player is
 * bloody" — the same failure of resolution the old redstone particles had.
 *
 * <p>It draws its <em>own</em> geometry rather than re-submitting the model's
 * parts. The first version submitted {@code model.head} and friends directly,
 * which read as blood floating in a shell off the body — because in the current
 * player model the skin's overlay layer is a <em>child</em> of each base part
 * (the hat sits +0.5 off the head, jacket and sleeves +0.25), and submitting a
 * part renders its children too. So the blood was being painted onto the
 * inflated overlay cubes as well, and the outermost copy is the one you see —
 * hovering above a skin whose overlay pixels may not even be drawn there. The
 * parts below are baked from vanilla's own cube definitions minus every overlay
 * child, at +0.01 so they sit on the skin rather than fighting it: blood on the
 * body, under the clothes, where it soaked in.
 *
 * <p>Uses a translucent render type rather than the cutout one the vanilla
 * damage overlays use, because the stain fades in as it accumulates and dries
 * off slowly afterwards; cutout is alpha-tested, so it can only be fully on or
 * fully off and the whole gradient would step.
 */
public class BloodBodyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/blood_body.png");
	/** Below this the stain is not worth a draw call. */
	private static final float VISIBLE = 0.02F;
	/**
	 * How far off the skin the blood sits, in model units. Slightly proud so it
	 * cannot z-fight the base layer, and far inside the overlay's 0.25 so it can
	 * never read as clothing.
	 */
	private static final float SKIN_OFFSET = 0.01F;

	/** Overlay-free body parts, one set per arm width. Poses are copied per frame. */
	private final ModelPart[] wide;
	private final ModelPart[] slim;

	public BloodBodyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.wide = bake(false);
		this.slim = bake(true);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
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
		HumanoidModel<?> model = getParentModel();
		ModelPart[] parts = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

		for (BodyPart part : BodyPart.values()) {
			float level = stains[part.ordinal()];
			if (level < VISIBLE) {
				continue;
			}
			ModelPart source = partFor(model, part);
			if (source == null || !source.visible) {
				continue;
			}
			// Blood on a body dries where it lands. Wetness trails the amount, so
			// a limb that is actively bleeding looks fresh and one that stopped an
			// hour ago looks like it stopped an hour ago.
			float[] colour = BloodPalette.colour(Math.min(1.0, level * 1.4));
			int alpha = (int) Math.clamp(level * strength * 235.0, 0.0, 235.0);
			if (alpha <= 2) {
				continue;
			}
			ModelPart target = parts[part.ordinal()];
			copyPose(source, target);
			collector.order(1).submitModelPart(target, poseStack,
					RenderTypes.entityTranslucent(TEXTURE), lightCoords, overlay, null,
					ARGB.colorFromFloat(alpha / 255.0F, colour[0], colour[1], colour[2]), null, 0);
		}
	}

	/**
	 * The pose the animation gave the live part this frame, applied to ours.
	 * Copied rather than shared because the live part comes with the overlay
	 * children this layer exists to escape.
	 */
	private static void copyPose(ModelPart from, ModelPart to) {
		to.x = from.x;
		to.y = from.y;
		to.z = from.z;
		to.xRot = from.xRot;
		to.yRot = from.yRot;
		to.zRot = from.zRot;
		to.xScale = from.xScale;
		to.yScale = from.yScale;
		to.zScale = from.zScale;
	}

	/**
	 * Vanilla's own body cubes — same sizes, pivots and skin UVs as
	 * {@code PlayerModel.createMesh} — with every overlay child left out and the
	 * whole set worn {@link #SKIN_OFFSET} proud of the skin. Indexed by
	 * {@code BodyPart.ordinal()}.
	 */
	private static ModelPart[] bake(boolean slimArms) {
		CubeDeformation g = new CubeDeformation(SKIN_OFFSET);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, g),
				PartPose.ZERO);
		root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, g),
				PartPose.ZERO);
		if (slimArms) {
			root.addOrReplaceChild("left_arm",
					CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, g),
					PartPose.ZERO);
			root.addOrReplaceChild("right_arm",
					CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, g),
					PartPose.ZERO);
		} else {
			root.addOrReplaceChild("left_arm",
					CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, g),
					PartPose.ZERO);
			root.addOrReplaceChild("right_arm",
					CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, g),
					PartPose.ZERO);
		}
		root.addOrReplaceChild("left_leg",
				CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, g),
				PartPose.ZERO);
		root.addOrReplaceChild("right_leg",
				CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, g),
				PartPose.ZERO);

		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		ModelPart[] byRegion = new ModelPart[BodyPart.values().length];
		byRegion[BodyPart.HEAD.ordinal()] = baked.getChild("head");
		byRegion[BodyPart.TORSO.ordinal()] = baked.getChild("body");
		byRegion[BodyPart.LEFT_ARM.ordinal()] = baked.getChild("left_arm");
		byRegion[BodyPart.RIGHT_ARM.ordinal()] = baked.getChild("right_arm");
		byRegion[BodyPart.LEFT_LEG.ordinal()] = baked.getChild("left_leg");
		byRegion[BodyPart.RIGHT_LEG.ordinal()] = baked.getChild("right_leg");
		return byRegion;
	}

	/**
	 * The live model part a body region rides — for its pose and visibility, not
	 * its geometry.
	 */
	private static ModelPart partFor(HumanoidModel<?> model, BodyPart part) {
		return switch (part) {
			case HEAD -> model.head;
			case TORSO -> model.body;
			case LEFT_ARM -> model.leftArm;
			case RIGHT_ARM -> model.rightArm;
			case LEFT_LEG -> model.leftLeg;
			case RIGHT_LEG -> model.rightLeg;
		};
	}
}
