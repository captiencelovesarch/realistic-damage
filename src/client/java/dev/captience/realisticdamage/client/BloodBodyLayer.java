package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.ScarMask;
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
 * Blood drawn onto the player model, one region at a time (1.5) — and, since
 * 1.6, the scars underneath it.
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
 * child, at {@link #SKIN_OFFSET} so they sit on the skin rather than fighting
 * it: blood on the body, under the clothes, where it soaked in.
 *
 * <p>Uses a translucent render type rather than the cutout one the vanilla
 * damage overlays use, because the stain fades in as it accumulates and dries
 * off slowly afterwards; cutout is alpha-tested, so it can only be fully on or
 * fully off and the whole gradient would step.
 *
 * <h2>The two shells, and the bucket contract (1.6)</h2>
 *
 * <p>Scars are a second submit of the same six regions from a second baked pair
 * at {@link #SCAR_OFFSET}, half the blood shell's stand-off. That nests three
 * surfaces in a fixed order — skin, then scars, then blood — so a seam sits on
 * the body and the blood that runs over it covers it, which is what "an old
 * mark, and fresh blood on top of it" has to look like. The two shells are
 * separate meshes rather than one submitted twice because coincident geometry
 * in a translucent pass has no defined winner; a hair of separation makes the
 * ordering a property of the model instead of a property of the sort.
 *
 * <p>The buckets then have to agree with the geometry: scars go in
 * {@link #ORDER_SCARS} and blood in {@link #ORDER_BLOOD}, and the collector
 * walks buckets in ascending order, so the inner shell is submitted first and
 * the outer one blends over it. Do not move blood back into the scar bucket to
 * save a number. Inside one bucket, submits sort by a camera distance that
 * every part of a single entity shares, so the resulting order between two
 * passes over the same entity is unspecified — and an unspecified order between
 * two translucent shells a fraction of a pixel apart is a flicker.
 *
 * <p>The parts are also no longer posed in place. A layer instance is shared by
 * every player using the renderer, and a submit stores the {@code ModelPart} by
 * reference while the frame's prepare phase reads it back later, after every
 * entity has submitted — so two visible players used to draw each other's limb
 * poses. The live part's transform now goes onto the {@code PoseStack}, which
 * <em>is</em> copied per submit, and our own parts stay at rest at
 * {@link PartPose#ZERO}. That is arithmetically the same thing the old
 * pose-copy did: {@code translateAndRotate} is exactly what {@code
 * ModelPart.render} applies internally before compiling its cubes.
 */
public class BloodBodyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/blood_body.png");
	/** Old injuries, in the same vanilla skin UV layout as the blood overlay. */
	private static final Identifier SCAR_TEXTURE =
			Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/scar_body.png");
	/** Below this the stain is not worth a draw call. */
	private static final float VISIBLE = 0.02F;
	/**
	 * How far off the skin the blood sits, in model units. Slightly proud so it
	 * cannot z-fight the base layer, and far inside the overlay's 0.25 so it can
	 * never read as clothing.
	 */
	private static final float SKIN_OFFSET = 0.01F;
	/**
	 * Where the scars sit: half the blood's stand-off, so the seam is on the
	 * body and the blood shell encloses it rather than sharing a surface with
	 * it. Half rather than some smaller sliver because the gap has to survive
	 * being multiplied by the model's scale and squashed into a depth buffer.
	 */
	private static final float SCAR_OFFSET = SKIN_OFFSET / 2.0F;

	/** Translucent buckets, drawn in ascending order. See the class javadoc. */
	private static final int ORDER_SCARS = 1;
	private static final int ORDER_BLOOD = 2;

	// A mark is faint; a region that was lost and came back is not. Both are
	// still shares of the Blood on Bodies strength, never absolutes.
	private static final float SCAR_ALPHA_BROKEN = 0.70F;
	private static final float SCAR_ALPHA_DESTROYED = 0.88F;
	// Pale and pinkish for a healed break, darker and duller for the region that
	// was once gone — old scar tissue holds less colour the worse it was.
	private static final float[] SCAR_TONE_BROKEN = {0.86F, 0.68F, 0.62F};
	private static final float[] SCAR_TONE_DESTROYED = {0.62F, 0.42F, 0.40F};

	/** Overlay-free body parts, one set per arm width. Posed via the matrix stack. */
	private final ModelPart[] wide;
	private final ModelPart[] slim;
	/** The same six regions again, worn closer to the skin, for the scar pass. */
	private final ModelPart[] scarWide;
	private final ModelPart[] scarSlim;

	private static long scarSubmitCount;

	/**
	 * How many scar submits have been drawn this session. Monotonic, and sampled
	 * as a delta over a fixed window by the client gametest: a render loop that
	 * has stalled then fails a "greater than zero" assertion instead of passing
	 * one, which is the safe direction for a test whose subject is whether
	 * anything was drawn at all.
	 */
	public static long scarSubmits() {
		return scarSubmitCount;
	}

	public BloodBodyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.wide = bake(false, SKIN_OFFSET);
		this.slim = bake(true, SKIN_OFFSET);
		this.scarWide = bake(false, SCAR_OFFSET);
		this.scarSlim = bake(true, SCAR_OFFSET);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		ClientEffectsConfig config = ClientEffectsConfig.get();
		// One gate for both passes: a scar on a body is paint on a body, so the
		// panic button silences it without ever needing to learn it exists.
		double strength = config.bloodIntensity() * config.bloodBodyIntensity;
		if (strength <= 0.0) {
			return;
		}
		BloodStainHolder holder = (BloodStainHolder) state;
		float[] stains = holder.realisticdamage$bloodStains();
		int scars = config.showScars ? holder.realisticdamage$scarMask() : 0;
		if (stains == null && scars == 0) {
			return;   // a scarred but clean player still has to draw
		}
		HumanoidModel<?> model = getParentModel();
		boolean slimArms = state.skin.model() == PlayerModelType.SLIM;
		ModelPart[] bloodParts = slimArms ? slim : wide;
		ModelPart[] scarParts = slimArms ? scarSlim : scarWide;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

		for (BodyPart part : BodyPart.values()) {
			ScarLevel scar = ScarMask.level(scars, part);
			float level = stains == null ? 0.0F : stains[part.ordinal()];
			if (scar == ScarLevel.NONE && level < VISIBLE) {
				continue;
			}
			ModelPart source = partFor(model, part);
			if (source == null || !source.visible) {
				continue;   // a destroyed limb is hidden: there is nothing to paint on
			}
			poseStack.pushPose();
			// The pose goes on the stack, which is copied per submit, rather than
			// onto our shared meshes, which are not.
			source.translateAndRotate(poseStack);
			if (scar != ScarLevel.NONE) {
				boolean lost = scar == ScarLevel.DESTROYED;
				float[] tone = lost ? SCAR_TONE_DESTROYED : SCAR_TONE_BROKEN;
				float alpha = (float) (strength * (lost ? SCAR_ALPHA_DESTROYED : SCAR_ALPHA_BROKEN));
				collector.order(ORDER_SCARS).submitModelPart(scarParts[part.ordinal()], poseStack,
						RenderTypes.entityTranslucent(SCAR_TEXTURE), lightCoords, overlay, null,
						ARGB.colorFromFloat(alpha, tone[0], tone[1], tone[2]), null, 0);
				scarSubmitCount++;
			}
			if (level >= VISIBLE) {
				// Blood on a body dries where it lands. Wetness trails the amount,
				// so a limb that is actively bleeding looks fresh and one that
				// stopped an hour ago looks like it stopped an hour ago.
				float[] colour = BloodPalette.colour(Math.min(1.0, level * 1.4));
				int alpha = (int) Math.clamp(level * strength * 235.0, 0.0, 235.0);
				if (alpha > 2) {
					collector.order(ORDER_BLOOD).submitModelPart(bloodParts[part.ordinal()], poseStack,
							RenderTypes.entityTranslucent(TEXTURE), lightCoords, overlay, null,
							ARGB.colorFromFloat(alpha / 255.0F, colour[0], colour[1], colour[2]), null, 0);
				}
			}
			poseStack.popPose();
		}
	}

	/**
	 * Vanilla's own body cubes — same sizes, pivots and skin UVs as
	 * {@code PlayerModel.createMesh} — with every overlay child left out and the
	 * whole set worn {@code offset} proud of the skin. Indexed by
	 * {@code BodyPart.ordinal()}.
	 *
	 * <p>The offset is a parameter rather than a constant so the blood and scar
	 * shells can be the same mesh at two stand-offs: nothing else about the two
	 * differs, and the moment they are described by two copies of this method
	 * they can drift apart in a way no test would notice.
	 */
	private static ModelPart[] bake(boolean slimArms, float offset) {
		CubeDeformation g = new CubeDeformation(offset);
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
