package dev.captience.realisticdamage.fieldkit.client;

import java.util.EnumMap;
import java.util.Map;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.LimbMeshes;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The field kit's gear as it is worn (items.7), as cubes: a bandage is a wrap
 * of gauze round the middle of a limb (or the chest, or the forehead), a
 * tourniquet a leather strap high on a limb with its windlass across the
 * front, a splint two sticks down either side bound with string in three
 * places, a sling a cloth pouch round the forearm end of the arm, the goggles
 * a strap round the head with two tinted lenses at the eyes, and the
 * stethoscope's earpieces in the ears.
 *
 * <p>Each piece sits in its region's own space, the same coordinates as the
 * vanilla cube it is worn over, at {@link PartPose#ZERO}: whoever draws it puts
 * the live part's pose on the stack first, as the blood layers do, so the gear
 * moves with the limb. Sizes are per build ({@link LimbMeshes.Kind}): a
 * skeleton's bones are two pixels, a villager has no separate arms.
 *
 * <p>Worn over the skin's second layer (it sits 0.25 out) and under armour
 * (0.5 for leggings, 1.0 for the rest): a bandage under an iron sleeve is
 * hidden, as it would be.
 */
public final class GearMeshes {
	private GearMeshes() {}

	public static final Identifier TEXTURE = RealisticDamage.id("textures/entity/field_kit_gear.png");

	/** Where each material is on {@link #TEXTURE}; tools/generate_gear_art.py draws them there. */
	enum Region {
		GAUZE(0, 0), GAUZE_SOAKED(32, 0), LEATHER(0, 12), CLOTH(32, 12), WOOD(0, 24), STRING(8, 24),
		METAL(32, 24), TUBE(48, 24), COPPER(8, 32), GOGGLE_STRAP(0, 40), LENS(32, 40);

		final int u;
		final int v;

		Region(int u, int v) {
			this.u = u;
			this.v = v;
		}
	}

	/** A region's cube: its near corner and size, in model pixels. */
	record Box(float x, float y, float z, float w, float h, float d) {}

	/** Everything one build wears, posed by nobody. */
	static final class Set {
		final Map<BodyPart, ModelPart> bandage = new EnumMap<>(BodyPart.class);
		final Map<BodyPart, ModelPart> soaked = new EnumMap<>(BodyPart.class);
		final Map<BodyPart, ModelPart> tourniquet = new EnumMap<>(BodyPart.class);
		final Map<BodyPart, ModelPart> splint = new EnumMap<>(BodyPart.class);
		final Map<BodyPart, ModelPart> pouch = new EnumMap<>(BodyPart.class);
		ModelPart goggleStrap;
		ModelPart lenses;
		ModelPart earpieces;
		/** A one-pixel cube of each material, stretched between two points: straps and tubes. */
		final Map<Region, ModelPart> unit = new EnumMap<>(Region.class);
		Box head;
		Box body;
		final Map<BodyPart, Box> limbs = new EnumMap<>(BodyPart.class);
	}

	private static final Set[] SETS = new Set[LimbMeshes.Kind.values().length];

	static Set of(LimbMeshes.Kind kind) {
		Set set = SETS[kind.ordinal()];
		if (set == null) {
			set = build(kind);
			SETS[kind.ordinal()] = set;
		}
		return set;
	}

	/** Where each region's vanilla cube is, for a build; null for a limb it has no part for. */
	static @Nullable Box box(LimbMeshes.Kind kind, BodyPart part) {
		boolean villagerShaped = kind == LimbMeshes.Kind.VILLAGER || kind == LimbMeshes.Kind.ILLAGER
				|| kind == LimbMeshes.Kind.ZOMBIE_VILLAGER;
		return switch (part) {
			case HEAD -> villagerShaped ? new Box(-4, -10, -4, 8, 10, 8) : new Box(-4, -8, -4, 8, 8, 8);
			case TORSO -> villagerShaped ? new Box(-4, 0, -3, 8, 12, 6) : new Box(-4, 0, -2, 8, 12, 4);
			case RIGHT_ARM -> switch (kind) {
				case VILLAGER -> null;
				case THIN -> new Box(-1, -2, -1, 2, 12, 2);
				case SLIM -> new Box(-2, -2, -2, 3, 12, 4);
				default -> new Box(-3, -2, -2, 4, 12, 4);
			};
			case LEFT_ARM -> switch (kind) {
				case VILLAGER -> null;
				case THIN -> new Box(-1, -2, -1, 2, 12, 2);
				case SLIM -> new Box(-1, -2, -2, 3, 12, 4);
				default -> new Box(-1, -2, -2, 4, 12, 4);
			};
			case LEFT_LEG, RIGHT_LEG -> kind == LimbMeshes.Kind.THIN ? new Box(-1, 0, -1, 2, 12, 2)
					: new Box(-2, 0, -2, 4, 12, 4);
		};
	}

	/** Eye level on the head, model pixels: four and a half down from its crown. */
	static float eyes(Box head) {
		return head.y() + 4.5F;
	}

	private static Set build(LimbMeshes.Kind kind) {
		Set set = new Set();
		set.head = box(kind, BodyPart.HEAD);
		set.body = box(kind, BodyPart.TORSO);
		for (BodyPart part : BodyPart.values()) {
			Box b = box(kind, part);
			if (b == null) {
				continue;
			}
			if (part.isLimb()) {
				set.limbs.put(part, b);
			}
			set.bandage.put(part, bake(bandage(b, part, Region.GAUZE)));
			set.soaked.put(part, bake(bandage(b, part, Region.GAUZE_SOAKED)));
			if (part.isLimb()) {
				set.tourniquet.put(part, bake(tourniquet(b)));
				set.splint.put(part, bake(splint(b)));
			}
			if (part.isArm()) {
				set.pouch.put(part, bake(pouch(b)));
			}
		}
		Box head = set.head;
		float eye = eyes(head);
		set.goggleStrap = bake(CubeListBuilder.create()
				.texOffs(Region.GOGGLE_STRAP.u, Region.GOGGLE_STRAP.v)
				.addBox(head.x(), eye - 0.9F, head.z(), head.w(), 1.6F, head.d(), new CubeDeformation(0.6F))
				// The bridge between the lenses, over the nose.
				.texOffs(Region.GOGGLE_STRAP.u, Region.GOGGLE_STRAP.v)
				.addBox(-0.8F, eye - 0.6F, head.z() - 1.3F, 1.6F, 0.9F, 0.8F));
		set.lenses = bake(CubeListBuilder.create()
				.texOffs(Region.LENS.u, Region.LENS.v)
				.addBox(0.35F, eye - 1.6F, head.z() - 1.7F, 3.2F, 3.2F, 1.2F)
				.texOffs(Region.LENS.u, Region.LENS.v)
				.addBox(-3.55F, eye - 1.6F, head.z() - 1.7F, 3.2F, 3.2F, 1.2F));
		set.earpieces = bake(CubeListBuilder.create()
				.texOffs(Region.TUBE.u, Region.TUBE.v)
				.addBox(head.x() + head.w() - 0.2F, eye + 0.2F, -0.2F, 0.9F, 0.9F, 0.9F)
				.texOffs(Region.TUBE.u, Region.TUBE.v)
				.addBox(head.x() - 0.7F, eye + 0.2F, -0.2F, 0.9F, 0.9F, 0.9F));
		for (Region region : Region.values()) {
			set.unit.put(region, bake(CubeListBuilder.create().texOffs(region.u, region.v)
					.addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F)));
		}
		return set;
	}

	/** Gauze round the middle of a limb, the chest, or the forehead. */
	private static CubeListBuilder bandage(Box b, BodyPart part, Region gauze) {
		CubeListBuilder cubes = CubeListBuilder.create().texOffs(gauze.u, gauze.v);
		return switch (part) {
			// Round the forehead, just above the eyes (four and a half down from the crown).
			case HEAD -> cubes.addBox(b.x(), b.y() + 2.2F, b.z(), b.w(), 1.8F, b.d(), new CubeDeformation(0.6F));
			case TORSO -> cubes.addBox(b.x(), b.y() + 3.0F, b.z(), b.w(), 5.0F, b.d(), new CubeDeformation(0.35F));
			default -> cubes.addBox(b.x(), b.y() + 4.0F, b.z(), b.w(), 5.0F, b.d(), new CubeDeformation(0.35F));
		};
	}

	/** A strap high on the limb, and the windlass stick across its front. */
	private static CubeListBuilder tourniquet(Box b) {
		return CubeListBuilder.create()
				.texOffs(Region.LEATHER.u, Region.LEATHER.v)
				.addBox(b.x(), b.y() + 1.5F, b.z(), b.w(), 1.5F, b.d(), new CubeDeformation(0.5F))
				.texOffs(Region.WOOD.u, Region.WOOD.v)
				.addBox(b.x() - 0.9F, b.y() + 1.75F, b.z() - 1.5F, b.w() + 1.8F, 1.0F, 1.0F);
	}

	/** Two sticks down either side, bound in three places. */
	private static CubeListBuilder splint(Box b) {
		float stick = b.w() <= 2.0F ? 0.8F : 1.0F;
		float zMid = b.z() + b.d() / 2.0F - stick / 2.0F;
		CubeListBuilder cubes = CubeListBuilder.create()
				.texOffs(Region.WOOD.u, Region.WOOD.v)
				.addBox(b.x() - 0.3F - stick, b.y() + 0.5F, zMid, stick, 11.0F, stick)
				.texOffs(Region.WOOD.u, Region.WOOD.v)
				.addBox(b.x() + b.w() + 0.3F, b.y() + 0.5F, zMid, stick, 11.0F, stick);
		for (float at : new float[] {2.0F, 6.0F, 10.0F}) {
			cubes.texOffs(Region.STRING.u, Region.STRING.v)
					.addBox(b.x() - 0.5F - stick, b.y() + at, b.z() - 0.4F, b.w() + 1.0F + 2.0F * stick, 0.9F,
							b.d() + 0.8F);
		}
		return cubes;
	}

	/** The cloth round the hand end of a slung arm. */
	private static CubeListBuilder pouch(Box b) {
		return CubeListBuilder.create().texOffs(Region.CLOTH.u, Region.CLOTH.v)
				.addBox(b.x(), b.y() + 5.5F, b.z(), b.w(), 6.5F, b.d(), new CubeDeformation(0.4F));
	}

	private static ModelPart bake(CubeListBuilder cubes) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("gear", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("gear");
	}
}
