package dev.captience.realisticdamage.client;

import java.util.EnumSet;

import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * The player's six regions as bare cubes, in every form the mod draws them
 * (1.6.2): whole, cut short into a stump, the piece below the cut that came
 * off, the cut face itself, and the skin's second layer for that piece.
 *
 * <p>All of it is baked from vanilla's own cube definitions in
 * {@code PlayerModel.createMesh} — and, since 1.7, {@code SkeletonModel}'s for
 * the thin build — with the overlay children left out. The reasoning is
 * {@code BloodBodyLayer}'s: a submitted part renders its children, and the
 * overlay shell is inflated, so anything painted onto a vanilla part lands on
 * the hat and sleeves as well. Every caller that wants a limb-shaped thing gets
 * it from here, so the arm on the body, the stump where the arm was, the shell
 * of blood on either, and the limb on the floor all agree about what an arm is.
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

	/**
	 * The limb builds the mod draws (1.7): the player's wide and slim arms (and
	 * the zombie and piglin families, which use the player's cubes), the
	 * skeleton family's two-pixel bones, the illagers, the villager-shaped
	 * witch whose arms are one crossed piece, and the zombie villager. Each
	 * carries its head height and body depth, which are the two dimensions the
	 * families disagree on besides the limbs.
	 */
	public enum Kind {
		WIDE(8, 16, 4), SLIM(8, 16, 4), THIN(8, 16, 4), ILLAGER(10, 20, 6), VILLAGER(10, 20, 6), ZOMBIE_VILLAGER(10, 20, 6);

		final float headHeight;
		final int bodyV;
		final float bodyDepth;

		Kind(float headHeight, int bodyV, float bodyDepth) {
			this.headHeight = headHeight;
			this.bodyV = bodyV;
			this.bodyDepth = bodyDepth;
		}

		public static Kind of(boolean slimArms) {
			return slimArms ? SLIM : WIDE;
		}
	}

	/**
	 * Where each limb's cube sits and how wide it is, per build: x of the cube's
	 * near edge, its width, and its depth (a bone is two by two). UVs are
	 * vanilla's; a build mirrors its left limbs where vanilla does. Null for a
	 * limb the build has no separate part for (a villager's crossed arms).
	 */
	private record Limb(int u, int v, float x, float width, float depth, boolean mirror) {}

	/** Whole regions, worn {@code offset} proud of the skin. */
	public static ModelPart[] full(boolean slimArms, float offset) {
		return full(Kind.of(slimArms), offset);
	}

	public static ModelPart[] full(Kind kind, float offset) {
		return bake(kind, offset, 12.0F, false);
	}

	/** The upper {@link #STUMP_LENGTH} pixels of each limb (core regions are whole). */
	public static ModelPart[] stumps(boolean slimArms, float offset) {
		return stumps(Kind.of(slimArms), offset);
	}

	public static ModelPart[] stumps(Kind kind, float offset) {
		return bake(kind, offset, STUMP_LENGTH, false);
	}

	/** The cut face of each stump: a thin cube at the cut, textured with {@link #CUT_TEXTURE}. */
	public static ModelPart[] stumpCaps(boolean slimArms) {
		return stumpCaps(Kind.of(slimArms));
	}

	public static ModelPart[] stumpCaps(Kind kind) {
		return bake(kind, 0.0F, STUMP_LENGTH, true);
	}

	/**
	 * The piece that came off: the lower {@code 12 - STUMP_LENGTH} pixels of
	 * each limb, so the stump on the body and the limb on the floor add up to
	 * one limb. The first build threw a whole twelve-pixel leg while the body
	 * kept five of them — "the entire leg is visible", as reported.
	 *
	 * <p>Two cubes per limb when unoffset: the sides with their texture rows
	 * shifted down by the stump's length, and a full-height cube showing only
	 * its bottom face, so the sole and the palm keep the texture rows they had.
	 * No end face at the cut at all: the cap is what is seen there. An offset
	 * shell (the blood) is one cube, since it has no sole worth the second.
	 */
	public static ModelPart[] severed(boolean slimArms, float offset) {
		return severed(Kind.of(slimArms), offset);
	}

	public static ModelPart[] severed(Kind kind, float offset) {
		return bakeLower(kind, offset, false);
	}

	/** The skin's second layer on the piece that came off, inflated as vanilla inflates it; none for a bone. */
	public static ModelPart[] severedOverlays(boolean slimArms) {
		return severedOverlays(Kind.of(slimArms));
	}

	public static ModelPart[] severedOverlays(Kind kind) {
		return bakeLower(kind, OVERLAY_INFLATE, true);
	}

	/**
	 * The cut face at the top of the piece that came off: the very same cap as
	 * the stump's, straddling the cut plane. The first build pushed it a cap's
	 * thickness lower, which put it inside the piece — where the piece's own
	 * skin-textured end face hid it, and the arm on the ground showed its top.
	 */
	public static ModelPart[] severedCaps(boolean slimArms) {
		return stumpCaps(slimArms);
	}

	public static ModelPart[] severedCaps(Kind kind) {
		return stumpCaps(kind);
	}

	/** The skin's second layer for each whole limb, inflated as vanilla inflates it; core regions and bones empty. */
	public static ModelPart[] overlays(boolean slimArms) {
		return overlays(Kind.of(slimArms));
	}

	public static ModelPart[] overlays(Kind kind) {
		CubeDeformation g = new CubeDeformation(OVERLAY_INFLATE);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		for (BodyPart part : BodyPart.values()) {
			if (!part.isLimb()) {
				continue;
			}
			Limb limb = limb(kind, part, true);
			if (limb == null) {
				root.addOrReplaceChild(name(part), CubeListBuilder.create(), PartPose.ZERO);
				continue;
			}
			root.addOrReplaceChild(name(part), CubeListBuilder.create().texOffs(limb.u(), limb.v())
					.addBox(limb.x(), top(part), -limb.depth() / 2.0F, limb.width(), 12.0F, limb.depth(), g), PartPose.ZERO);
		}
		return index(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	private static Limb limb(Kind kind, BodyPart part, boolean overlay) {
		switch (kind) {
			case THIN -> {
				// SkeletonModel: no second layer, both arms and both legs from one UV
				// each, the left side mirrored.
				return switch (part) {
					case LEFT_ARM -> new Limb(40, 16, -1.0F, 2.0F, 2.0F, true);
					case RIGHT_ARM -> new Limb(40, 16, -1.0F, 2.0F, 2.0F, false);
					case LEFT_LEG -> new Limb(0, 16, -1.0F, 2.0F, 2.0F, true);
					default -> new Limb(0, 16, -1.0F, 2.0F, 2.0F, false);
				};
			}
			case ILLAGER, ZOMBIE_VILLAGER, VILLAGER -> {
				// IllagerModel / ZombieVillagerModel / VillagerModel: legs from one
				// UV mirrored on the left; arms from (40,46) for illagers and (44,22)
				// for zombie villagers; a villager's arms are one crossed piece.
				if (overlay) {
					return null;
				}
				if (part.isLeg()) {
					return new Limb(0, 22, -2.0F, 4.0F, 4.0F, part == BodyPart.LEFT_LEG);
				}
				if (kind == Kind.VILLAGER) {
					return null;
				}
				int u = kind == Kind.ILLAGER ? 40 : 44;
				int v = kind == Kind.ILLAGER ? 46 : 22;
				return part == BodyPart.LEFT_ARM ? new Limb(u, v, -1.0F, 4.0F, 4.0F, true) : new Limb(u, v, -3.0F, 4.0F, 4.0F, false);
			}
			default -> {
				float armWidth = kind == Kind.SLIM ? 3.0F : 4.0F;
				float rightArmX = kind == Kind.SLIM ? -2.0F : -3.0F;
				return switch (part) {
					case LEFT_ARM -> new Limb(overlay ? 48 : 32, 48, -1.0F, armWidth, 4.0F, false);
					case RIGHT_ARM -> new Limb(40, overlay ? 32 : 16, rightArmX, armWidth, 4.0F, false);
					case LEFT_LEG -> new Limb(overlay ? 0 : 16, 48, -2.0F, 4.0F, 4.0F, false);
					default -> new Limb(0, overlay ? 32 : 16, -2.0F, 4.0F, 4.0F, false);
				};
			}
		}
	}

	/**
	 * The villager build's arms: one crossed piece, as VillagerModel has them,
	 * worn {@code offset} proud so a shell of blood can sit on it. Posed by the
	 * model's own {@code arms} part.
	 */
	public static ModelPart crossedArms(float offset) {
		CubeDeformation g = new CubeDeformation(offset);
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("arms", CubeListBuilder.create()
				.texOffs(44, 22).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, g)
				.texOffs(44, 22).mirror().addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, g).mirror(false)
				.texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F, g), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("arms");
	}

	/** Arms hang from -2, legs from 0, in vanilla's model space. */
	private static float top(BodyPart part) {
		return part.isArm() ? -2.0F : 0.0F;
	}

	private static String name(BodyPart part) {
		return switch (part) {
			case LEFT_ARM -> "left_arm";
			case RIGHT_ARM -> "right_arm";
			case LEFT_LEG -> "left_leg";
			default -> "right_leg";
		};
	}

	/**
	 * One mesh, four shapes. {@code length} is how much of each limb's twelve
	 * pixels to keep, from the top; {@code cap} swaps the limb cube for a thin
	 * cube at that length, on a texture whose every 4x4 cell is the cut pattern
	 * so any face of it reads right.
	 */
	private static ModelPart[] bake(Kind kind, float offset, float length, boolean cap) {
		CubeDeformation g = new CubeDeformation(offset);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		if (cap) {
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		} else {
			root.addOrReplaceChild("head",
					CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -kind.headHeight, -4.0F, 8.0F, kind.headHeight, 8.0F, g),
					PartPose.ZERO);
			root.addOrReplaceChild("body",
					CubeListBuilder.create().texOffs(16, kind.bodyV)
							.addBox(-4.0F, 0.0F, -kind.bodyDepth / 2.0F, 8.0F, 12.0F, kind.bodyDepth, g),
					PartPose.ZERO);
		}
		for (BodyPart part : BodyPart.values()) {
			if (!part.isLimb()) {
				continue;
			}
			Limb limb = limb(kind, part, false);
			if (limb == null) {
				root.addOrReplaceChild(name(part), CubeListBuilder.create(), PartPose.ZERO);
			} else {
				limb(root, name(part), limb, top(part), length, cap, g);
			}
		}
		int size = cap ? 16 : 64;
		return index(LayerDefinition.create(mesh, size, size).bakeRoot());
	}

	private static void limb(PartDefinition root, String name, Limb limb, float top,
			float length, boolean cap, CubeDeformation g) {
		float z = -limb.depth() / 2.0F;
		CubeListBuilder cubes = CubeListBuilder.create();
		if (limb.mirror()) {
			cubes.mirror();
		}
		if (cap) {
			// Straddling the cut plane: half inside the stump, half inside the
			// piece that came off, so whichever of the two is drawn, the cap's
			// outer face is the one past its end.
			float y = top + length - CAP_THICKNESS * 0.5F;
			cubes.texOffs(0, 0).addBox(limb.x(), y, z, limb.width(), CAP_THICKNESS, limb.depth());
		} else {
			cubes.texOffs(limb.u(), limb.v()).addBox(limb.x(), top, z, limb.width(), length, limb.depth(), g);
		}
		root.addOrReplaceChild(name, cubes, PartPose.ZERO);
	}

	private static ModelPart[] bakeLower(Kind kind, float offset, boolean overlay) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		for (BodyPart part : BodyPart.values()) {
			if (!part.isLimb()) {
				continue;
			}
			Limb limb = limb(kind, part, overlay);
			if (limb == null) {
				root.addOrReplaceChild(name(part), CubeListBuilder.create(), PartPose.ZERO);
				continue;
			}
			lower(root, name(part), limb, top(part), offset);
		}
		return index(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	private static void lower(PartDefinition root, String name, Limb limb, float top, float offset) {
		float length = 12.0F - STUMP_LENGTH;
		float cut = top + STUMP_LENGTH;
		float z = -limb.depth() / 2.0F;
		CubeListBuilder cubes = CubeListBuilder.create();
		if (limb.mirror()) {
			cubes.mirror();
		}
		if (offset == 0.0F) {
			cubes.texOffs(limb.u(), limb.v() + (int) STUMP_LENGTH).addBox(limb.x(), cut, z, limb.width(), length, limb.depth(),
					EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
			cubes.texOffs(limb.u(), limb.v()).addBox(limb.x(), top, z, limb.width(), 12.0F, limb.depth(), EnumSet.of(Direction.DOWN));
		} else {
			cubes.texOffs(limb.u(), limb.v() + (int) STUMP_LENGTH).addBox(limb.x(), cut, z, limb.width(), length, limb.depth(),
					new CubeDeformation(offset));
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
