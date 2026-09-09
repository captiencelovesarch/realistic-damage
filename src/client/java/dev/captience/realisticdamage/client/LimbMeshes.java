package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The player's six regions as bare cubes, in every form the mod draws them
 * (1.6.2): whole, cut short into a stump, the cut face itself, and the skin's
 * second layer for a limb lying on the ground.
 *
 * <p>All of it is baked from vanilla's own cube definitions in
 * {@code PlayerModel.createMesh} with the overlay children left out — the same
 * reasoning as {@code BloodBodyLayer}: a submitted part renders its children,
 * and the overlay shell is inflated, so anything painted onto a vanilla part
 * lands on the hat and sleeves as well. Every caller that wants a limb-shaped
 * thing gets it from here, so the arm on the player, the stump where the arm
 * was, the shell of blood on either, and the limb on the floor all agree about
 * what an arm is.
 *
 * <p>Indexed by {@code BodyPart.ordinal()}. Parts sit at {@link PartPose#ZERO}
 * and are posed through the {@code PoseStack} by whoever submits them.
 */
public final class LimbMeshes {
	private LimbMeshes() {}

	/** The cross section drawn where a limb ends: see tools/generate_stump.py. */
	public static final Identifier CUT_TEXTURE =
			Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/stump_end.png");

	/**
	 * How much of a lost limb is left, in model pixels of its twelve. Five sits
	 * just above where an elbow or knee would be: enough to read as a limb that
	 * was there, short enough that nothing about it looks usable.
	 */
	public static final float STUMP_LENGTH = 5.0F;
	/** Thickness of the cut-face cube; a hair, so it never reads as a slab. */
	private static final float CAP_THICKNESS = 0.08F;
	/** The skin's second layer sits this far proud, as in vanilla. */
	private static final float OVERLAY_INFLATE = 0.25F;

	/** Whole regions, worn {@code offset} proud of the skin. */
	public static ModelPart[] full(boolean slimArms, float offset) {
		return bake(slimArms, offset, 12.0F, false);
	}

	/** The upper {@link #STUMP_LENGTH} pixels of each limb (core regions are whole). */
	public static ModelPart[] stumps(boolean slimArms, float offset) {
		return bake(slimArms, offset, STUMP_LENGTH, false);
	}

	/** The cut face of each stump: a thin cube at the cut, textured with {@link #CUT_TEXTURE}. */
	public static ModelPart[] stumpCaps(boolean slimArms) {
		return bake(slimArms, 0.0F, STUMP_LENGTH, true);
	}

	/** The cut face at the top (shoulder or hip end) of a whole limb lying on the floor. */
	public static ModelPart[] severedCaps(boolean slimArms) {
		return bake(slimArms, 0.0F, 0.0F, true);
	}

	/** The skin's second layer for each limb, inflated as vanilla inflates it; core regions empty. */
	public static ModelPart[] overlays(boolean slimArms) {
		CubeDeformation g = new CubeDeformation(OVERLAY_INFLATE);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		float armWidth = slimArms ? 3.0F : 4.0F;
		float rightArmX = slimArms ? -2.0F : -3.0F;
		root.addOrReplaceChild("left_arm",
				CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, armWidth, 12.0F, 4.0F, g),
				PartPose.ZERO);
		root.addOrReplaceChild("right_arm",
				CubeListBuilder.create().texOffs(40, 32).addBox(rightArmX, -2.0F, -2.0F, armWidth, 12.0F, 4.0F, g),
				PartPose.ZERO);
		root.addOrReplaceChild("left_leg",
				CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, g),
				PartPose.ZERO);
		root.addOrReplaceChild("right_leg",
				CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, g),
				PartPose.ZERO);
		return index(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	/**
	 * One mesh, four shapes. {@code length} is how much of each limb's twelve
	 * pixels to keep, from the top; {@code cap} swaps the limb cube for a thin
	 * cube at that length (or at the top when the length is zero), on a texture
	 * whose every 4x4 cell is the cut pattern so any face of it reads right.
	 */
	private static ModelPart[] bake(boolean slimArms, float offset, float length, boolean cap) {
		CubeDeformation g = new CubeDeformation(offset);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		if (cap) {
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		} else {
			root.addOrReplaceChild("head",
					CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, g),
					PartPose.ZERO);
			root.addOrReplaceChild("body",
					CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, g),
					PartPose.ZERO);
		}
		float armWidth = slimArms ? 3.0F : 4.0F;
		float rightArmX = slimArms ? -2.0F : -3.0F;
		limb(root, "left_arm", 32, 48, -1.0F, -2.0F, armWidth, length, cap, g);
		limb(root, "right_arm", 40, 16, rightArmX, -2.0F, armWidth, length, cap, g);
		limb(root, "left_leg", 16, 48, -2.0F, 0.0F, 4.0F, length, cap, g);
		limb(root, "right_leg", 0, 16, -2.0F, 0.0F, 4.0F, length, cap, g);
		int size = cap ? 16 : 64;
		return index(LayerDefinition.create(mesh, size, size).bakeRoot());
	}

	private static void limb(PartDefinition root, String name, int u, int v, float x, float top,
			float width, float length, boolean cap, CubeDeformation g) {
		CubeListBuilder cubes = CubeListBuilder.create();
		if (cap) {
			// A hair inside the limb's extent on the cut plane so the cap and the
			// stump's own bottom face cannot share a surface.
			float y = length <= 0.0F ? top - CAP_THICKNESS * 0.5F : top + length - CAP_THICKNESS * 0.5F;
			cubes.texOffs(0, 0).addBox(x, y, -2.0F, width, CAP_THICKNESS, 4.0F);
		} else {
			cubes.texOffs(u, v).addBox(x, top, -2.0F, width, length, 4.0F, g);
		}
		root.addOrReplaceChild(name, cubes, PartPose.ZERO);
	}

	private static ModelPart[] index(ModelPart baked) {
		ModelPart[] byRegion = new ModelPart[BodyPart.values().length];
		byRegion[BodyPart.HEAD.ordinal()] = baked.getChild("head");
		byRegion[BodyPart.TORSO.ordinal()] = baked.getChild("body");
		byRegion[BodyPart.LEFT_ARM.ordinal()] = baked.getChild("left_arm");
		byRegion[BodyPart.RIGHT_ARM.ordinal()] = baked.getChild("right_arm");
		byRegion[BodyPart.LEFT_LEG.ordinal()] = baked.getChild("left_leg");
		byRegion[BodyPart.RIGHT_LEG.ordinal()] = baked.getChild("right_leg");
		return byRegion;
	}
}
