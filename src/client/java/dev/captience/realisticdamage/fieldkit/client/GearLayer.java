package dev.captience.realisticdamage.fieldkit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.LimbMeshes;
import dev.captience.realisticdamage.fieldkit.client.GearMeshes.Box;
import dev.captience.realisticdamage.fieldkit.client.GearMeshes.Region;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The field kit's gear on the body (items.7): bandages, tourniquets, splints,
 * slings, the tinted goggles and the stethoscope in the ears, drawn on every
 * player and every humanoid, illager and villager-shaped mob, from what
 * {@code GearExtractMixin} put on the render state. The geometry is
 * {@link GearMeshes}'; this puts each piece on the live part it is worn on.
 *
 * <p>Two things are drawn between parts rather than on one: a sling's strap,
 * from the pouch on the slung arm up over the other shoulder, and the
 * stethoscope's tube, from the ears down the front of the chest. Their ends are
 * found through the parts' live poses, so they stay attached as the arm and
 * head move.
 */
public class GearLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	/** Pieces drawn this session, for the client gametest. */
	private static long submits;

	public static long submits() {
		return submits;
	}

	public GearLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot,
			float xRot) {
		if (state.isInvisible || !(state instanceof GearHolder gear)) {
			return;
		}
		int bandaged = gear.realisticdamage$bandaged();
		int tourniquets = gear.realisticdamage$tourniquets();
		int splints = gear.realisticdamage$splints();
		int slings = gear.realisticdamage$slings();
		boolean goggles = gear.realisticdamage$goggles();
		boolean listening = gear.realisticdamage$listening();
		if (bandaged == 0 && tourniquets == 0 && splints == 0 && slings == 0 && !goggles && !listening) {
			return;
		}
		ModelPart root = getParentModel().root();
		LimbMeshes.Kind kind = kindOf(state);
		GearMeshes.Set set = GearMeshes.of(kind);
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		RenderType solid = RenderTypes.entityCutout(GearMeshes.TEXTURE);

		for (BodyPart part : BodyPart.values()) {
			ModelPart source = partFor(root, part);
			if (source == null || !source.visible) {
				continue;
			}
			boolean bandage = GearHolder.has(bandaged, part);
			boolean tourniquet = GearHolder.has(tourniquets, part) && set.tourniquet.containsKey(part);
			boolean splint = GearHolder.has(splints, part) && set.splint.containsKey(part);
			boolean sling = GearHolder.has(slings, part) && set.pouch.containsKey(part);
			if (!bandage && !tourniquet && !splint && !sling) {
				continue;
			}
			poseStack.pushPose();
			source.translateAndRotate(poseStack);
			if (bandage) {
				ModelPart wrap = (GearHolder.has(gear.realisticdamage$soaked(), part) ? set.soaked : set.bandage).get(part);
				if (wrap != null) {
					submit(collector, wrap, poseStack, solid, lightCoords, overlay);
				}
			}
			if (tourniquet) {
				submit(collector, set.tourniquet.get(part), poseStack, solid, lightCoords, overlay);
			}
			if (splint) {
				submit(collector, set.splint.get(part), poseStack, solid, lightCoords, overlay);
			}
			if (sling) {
				submit(collector, set.pouch.get(part), poseStack, solid, lightCoords, overlay);
			}
			poseStack.popPose();
			if (sling) {
				slingStrap(collector, poseStack, root, set, part, solid, lightCoords, overlay);
			}
		}

		ModelPart head = partFor(root, BodyPart.HEAD);
		if (head != null && head.visible && (goggles || listening)) {
			poseStack.pushPose();
			head.translateAndRotate(poseStack);
			if (goggles) {
				submit(collector, set.goggleStrap, poseStack, solid, lightCoords, overlay);
				submit(collector, set.lenses, poseStack, RenderTypes.entityTranslucent(GearMeshes.TEXTURE),
						lightCoords, overlay);
			}
			if (listening) {
				submit(collector, set.earpieces, poseStack, solid, lightCoords, overlay);
			}
			poseStack.popPose();
			if (listening) {
				stethoscope(collector, poseStack, root, set, solid, lightCoords, overlay);
			}
		}
	}

	private static void submit(SubmitNodeCollector collector, ModelPart part, PoseStack poseStack, RenderType type,
			int light, int overlay) {
		collector.submitModelPart(part, poseStack, type, light, overlay, null);
		submits++;
	}

	/**
	 * The sling's strap, as a triangular sling is tied: from the wrist end of
	 * the pouch up to the other shoulder, over it, round the back of the neck,
	 * over this shoulder and down to the elbow end of the pouch.
	 */
	private static void slingStrap(SubmitNodeCollector collector, PoseStack poseStack, ModelPart root,
			GearMeshes.Set set, BodyPart arm, RenderType type, int light, int overlay) {
		ModelPart armPart = partFor(root, arm);
		ModelPart body = partFor(root, BodyPart.TORSO);
		Box a = set.limbs.get(arm);
		Box b = set.body;
		if (armPart == null || body == null || a == null || b == null) {
			return;
		}
		// The other shoulder: a right arm hangs from the left one (+x).
		float other = arm == BodyPart.RIGHT_ARM ? 1.0F : -1.0F;
		float front = b.z() - 0.45F;
		float back = b.z() + b.d() + 0.45F;
		float mid = b.z() + b.d() / 2.0F;
		Vector3f wrist = at(armPart, a.x() + a.w() / 2.0F, a.y() + 11.0F, a.z() - 0.5F);
		Vector3f elbow = at(armPart, a.x() + a.w() / 2.0F, a.y() + 6.0F, a.z() - 0.5F);
		Vector3f[] loop = {
				wrist,
				at(body, other * 2.2F, 0.4F, front),
				at(body, other * 2.2F, -0.5F, mid),
				at(body, other * 2.2F, 0.4F, back),
				at(body, -other * 2.2F, 0.4F, back),
				at(body, -other * 2.2F, -0.5F, mid),
				at(body, -other * 2.2F, 0.4F, front),
				elbow,
		};
		ModelPart strap = set.unit.get(Region.CLOTH);
		for (int i = 0; i + 1 < loop.length; i++) {
			segment(collector, poseStack, strap, loop[i], loop[i + 1], 1.3F, 0.5F, type, light, overlay);
		}
	}

	/**
	 * The stethoscope in the ears: a metal tube down from each earpiece to
	 * under the chin, and the rubber tube from there down the front of the
	 * chest towards the hand holding the chest piece.
	 */
	private static void stethoscope(SubmitNodeCollector collector, PoseStack poseStack, ModelPart root,
			GearMeshes.Set set, RenderType type, int light, int overlay) {
		ModelPart head = partFor(root, BodyPart.HEAD);
		ModelPart body = partFor(root, BodyPart.TORSO);
		Box h = set.head;
		Box b = set.body;
		if (head == null || body == null) {
			return;
		}
		float eye = GearMeshes.eyes(h);
		Vector3f leftEar = at(head, h.x() + h.w() + 0.25F, eye + 0.65F, 0.25F);
		Vector3f rightEar = at(head, h.x() - 0.25F, eye + 0.65F, 0.25F);
		Vector3f chin = at(head, 0.0F, h.y() + h.h() + 0.8F, h.z() + 0.6F);
		Vector3f throat = at(body, 0.0F, 0.6F, b.z() - 0.6F);
		Vector3f belly = at(body, 0.8F, 7.5F, b.z() - 0.7F);
		ModelPart metal = set.unit.get(Region.METAL);
		ModelPart tube = set.unit.get(Region.TUBE);
		segment(collector, poseStack, metal, leftEar, chin, 0.45F, 0.45F, type, light, overlay);
		segment(collector, poseStack, metal, rightEar, chin, 0.45F, 0.45F, type, light, overlay);
		segment(collector, poseStack, tube, chin, throat, 0.7F, 0.7F, type, light, overlay);
		segment(collector, poseStack, tube, throat, belly, 0.7F, 0.7F, type, light, overlay);
	}

	/** A point in a part's own pixels, carried through its live pose into the model's pixels. */
	private static Vector3f at(ModelPart part, float x, float y, float z) {
		PoseStack scratch = new PoseStack();
		part.translateAndRotate(scratch);
		Vector3f p = scratch.last().pose().transformPosition(x / 16.0F, y / 16.0F, z / 16.0F, new Vector3f());
		return p.mul(16.0F);
	}

	/** A one-pixel cube stretched from {@code from} to {@code to}, {@code width} by {@code depth} across. */
	private static void segment(SubmitNodeCollector collector, PoseStack poseStack, ModelPart unit, Vector3f from,
			Vector3f to, float width, float depth, RenderType type, int light, int overlay) {
		Vector3f along = new Vector3f(to).sub(from);
		float length = along.length();
		if (length < 1.0e-3F) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(from.x / 16.0F, from.y / 16.0F, from.z / 16.0F);
		poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, along.x, along.y, along.z));
		poseStack.scale(width, length, depth);
		submit(collector, unit, poseStack, type, light, overlay);
		poseStack.popPose();
	}

	/** Which build this body is, for the gear's sizes. */
	private LimbMeshes.Kind kindOf(S state) {
		if (state instanceof AvatarRenderState avatar) {
			return avatar.skin.model() == PlayerModelType.SLIM ? LimbMeshes.Kind.SLIM : LimbMeshes.Kind.WIDE;
		}
		EntityModel<?> model = getParentModel();
		if (model instanceof net.minecraft.client.model.monster.skeleton.SkeletonModel<?>) {
			return LimbMeshes.Kind.THIN;
		}
		if (model instanceof net.minecraft.client.model.monster.zombie.ZombieVillagerModel<?>) {
			return LimbMeshes.Kind.ZOMBIE_VILLAGER;
		}
		if (model instanceof net.minecraft.client.model.monster.illager.IllagerModel<?>) {
			return LimbMeshes.Kind.ILLAGER;
		}
		if (model instanceof net.minecraft.client.model.npc.VillagerModel
				|| model instanceof net.minecraft.client.model.monster.witch.WitchModel) {
			return LimbMeshes.Kind.VILLAGER;
		}
		return LimbMeshes.Kind.WIDE;
	}

	/** The live part a region is worn on, by vanilla's part names. */
	static @Nullable ModelPart partFor(ModelPart root, BodyPart part) {
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
