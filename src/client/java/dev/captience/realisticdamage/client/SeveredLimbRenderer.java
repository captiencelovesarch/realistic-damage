package dev.captience.realisticdamage.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.entity.SeveredLimb;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Draws a lost limb where it fell (1.6.2): the part of the region below the
 * cut, in its owner's skin with the skin's second layer over it, a cut face
 * at the cut end, and a blood shell that dries as the trickle runs out.
 * All four come from {@link LimbMeshes}, so the limb on the floor is the same
 * limb that was on the body a moment ago.
 *
 * <p>The skin is resolved here rather than sent: the server does not know a
 * skin's arm width, the client does. The owner, while present, supplies it;
 * once they have gone the default skin for their UUID stands in, which is what
 * vanilla shows for that player too.
 *
 * <p>Spins in flight, settles flat once it stops, fades over its last two
 * seconds, and shrinks as it burns. Vanilla's model flip is applied so the
 * cube is textured the right way round, the same as on the body.
 */
public class SeveredLimbRenderer extends EntityRenderer<SeveredLimb, SeveredLimbRenderState> {
	/** Limbs submitted this session; read by the client gametest. */
	private static long submitCount;

	private final ModelPart[][] pieces = new ModelPart[LimbMeshes.Kind.values().length][];
	private final ModelPart[][] overlays = new ModelPart[LimbMeshes.Kind.values().length][];
	private final ModelPart[][] caps = new ModelPart[LimbMeshes.Kind.values().length][];
	private final ModelPart[][] blood = new ModelPart[LimbMeshes.Kind.values().length][];

	public static long submits() {
		return submitCount;
	}

	public SeveredLimbRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.2F;
		// The piece below the cut, not the whole limb: the body keeps the top
		// five pixels as its stump, so what lies on the floor is the other seven.
		// One set per limb build (1.7): a player's wide or slim arm, a skeleton's bone.
		for (LimbMeshes.Kind kind : LimbMeshes.Kind.values()) {
			pieces[kind.ordinal()] = LimbMeshes.severed(kind, 0.0F);
			overlays[kind.ordinal()] = LimbMeshes.severedOverlays(kind);
			caps[kind.ordinal()] = LimbMeshes.severedCaps(kind);
			blood[kind.ordinal()] = LimbMeshes.severed(kind, BloodBodyLayer.SKIN_OFFSET);
		}
	}

	@Override
	public SeveredLimbRenderState createRenderState() {
		return new SeveredLimbRenderState();
	}

	@Override
	public void extractRenderState(SeveredLimb entity, SeveredLimbRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.part = entity.part();
		net.minecraft.world.entity.EntityType<?> ownerType = entity.ownerType();
		if (ownerType != null) {
			// A mob's limb (1.7): its renderer's texture and its limb build.
			MobLooks.Look look = MobLooks.lookFor(ownerType);
			state.skin = look.texture();
			state.kind = look.kind();
		} else {
			PlayerSkin skin = skinOf(entity);
			state.skin = skin.body().texturePath();
			state.kind = skin.model() == PlayerModelType.SLIM ? LimbMeshes.Kind.SLIM : LimbMeshes.Kind.WIDE;
		}
		state.yaw = entity.getYRot();
		state.tumble = entity.tumble(partialTicks);
		state.lying = entity.lying(partialTicks);
		double ticksLeft = entity.remaining() * Balance.LIMB_LIFETIME_TICKS;
		state.alpha = (float) Math.clamp(ticksLeft / 40.0, 0.0, 1.0);
		state.burn = (float) entity.burn();
		// Wet while it bleeds, drying as the trickle runs out; never fully clean,
		// because blood dries on rather than rinsing off.
		double flow = entity.bleedRate() / WoundSeverity.CRITICAL.bloodPerSecond();
		// A bone off a skeleton (1.7) carries no blood at all.
		state.blood = entity.bloodless() ? 0.0F : (float) Math.clamp(0.35 + 0.65 * flow, 0.0, 1.0);
		state.tint = BloodTint.ofType(ownerType);
	}

	private static PlayerSkin skinOf(SeveredLimb entity) {
		UUID owner = entity.ownerId();
		if (owner == null) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
		Player player = entity.level().getPlayerByUUID(owner);
		if (player instanceof AbstractClientPlayer clientPlayer) {
			return clientPlayer.getSkin();
		}
		return DefaultPlayerSkin.get(owner);
	}

	@Override
	public void submit(SeveredLimbRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		BodyPart part = state.part;
		int kind = state.kind.ordinal();
		ModelPart[] skinParts = pieces[kind];
		ModelPart[] overlayParts = overlays[kind];
		ModelPart[] caps = this.caps[kind];
		ModelPart[] shells = blood[kind];
		poseStack.pushPose();
		// About the middle of its own hitbox, spinning while it flies and lying
		// down once it has stopped, shrinking as it burns.
		poseStack.translate(0.0F, state.boundingBoxHeight / 2.0F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.tumble));
		poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F * state.lying));
		float scale = 1.0F - 0.6F * state.burn;
		poseStack.scale(scale, scale, scale);
		// Vanilla's model flip: model +y runs down the limb, world +y runs up.
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		// The cube's own centre to the origin, so the rotations above are about
		// the limb and not about its shoulder.
		poseStack.translate(-centreX(part, state.kind) / 16.0F, -centreY(part) / 16.0F, 0.0F);

		int light = state.lightCoords;
		int overlay = OverlayTexture.NO_OVERLAY;
		// Burning: the flesh darkens toward char as it shrinks.
		float shade = 1.0F - 0.7F * state.burn;
		int tint = ARGB.colorFromFloat(state.alpha, shade, shade, shade);
		collector.submitModelPart(skinParts[part.ordinal()], poseStack, RenderTypes.entityTranslucent(state.skin),
				light, overlay, null, tint, null, 0);
		collector.submitModelPart(overlayParts[part.ordinal()], poseStack, RenderTypes.entityTranslucent(state.skin),
				light, overlay, null, tint, null, 0);
		collector.submitModelPart(caps[part.ordinal()], poseStack, RenderTypes.entityTranslucent(LimbMeshes.CUT_TEXTURE),
				light, overlay, null, tint, null, 0);
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodBodyIntensity * state.alpha;
		if (strength > 0.0) {
			BloodBodyLayer.paint(collector, poseStack, caps[part.ordinal()], shells[part.ordinal()],
					ScarLevel.NONE, state.blood, strength, light, overlay, state.tint);
		}
		poseStack.popPose();
		submitCount++;
		super.submit(state, poseStack, collector, camera);
	}

	/** Horizontal centre of the region's cube, model pixels, from the vanilla mesh of each build. */
	private static float centreX(BodyPart part, LimbMeshes.Kind kind) {
		if (kind == LimbMeshes.Kind.THIN) {
			return 0.0F;   // a bone is -1..1 whichever limb it is
		}
		if (part.isLeg() && kind != LimbMeshes.Kind.WIDE && kind != LimbMeshes.Kind.SLIM) {
			return 0.0F;   // illager and villager legs are centred on their pivot
		}
		float width = kind == LimbMeshes.Kind.SLIM ? 3.0F : 4.0F;
		return switch (part) {
			case LEFT_ARM -> -1.0F + width / 2.0F;
			case RIGHT_ARM -> (kind == LimbMeshes.Kind.SLIM ? -2.0F : -3.0F) + width / 2.0F;
			default -> 0.0F;
		};
	}

	/** Vertical centre of the piece below the cut: arms run -2..10, legs 0..12, the cut at five. */
	private static float centreY(BodyPart part) {
		float top = part.isArm() ? -2.0F : 0.0F;
		return top + LimbMeshes.STUMP_LENGTH + (12.0F - LimbMeshes.STUMP_LENGTH) / 2.0F;
	}
}
