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
 * Draws a lost limb where it fell (1.6.2): the region's own cube in its
 * owner's skin with the skin's second layer over it, a cut face at the
 * shoulder or hip end, and a blood shell that dries as the trickle runs out.
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

	private final ModelPart[] wide;
	private final ModelPart[] slim;
	private final ModelPart[] wideOverlay;
	private final ModelPart[] slimOverlay;
	private final ModelPart[] wideCap;
	private final ModelPart[] slimCap;
	private final ModelPart[] wideBlood;
	private final ModelPart[] slimBlood;

	public static long submits() {
		return submitCount;
	}

	public SeveredLimbRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.2F;
		this.wide = LimbMeshes.full(false, 0.0F);
		this.slim = LimbMeshes.full(true, 0.0F);
		this.wideOverlay = LimbMeshes.overlays(false);
		this.slimOverlay = LimbMeshes.overlays(true);
		this.wideCap = LimbMeshes.severedCaps(false);
		this.slimCap = LimbMeshes.severedCaps(true);
		this.wideBlood = LimbMeshes.full(false, BloodBodyLayer.SKIN_OFFSET);
		this.slimBlood = LimbMeshes.full(true, BloodBodyLayer.SKIN_OFFSET);
	}

	@Override
	public SeveredLimbRenderState createRenderState() {
		return new SeveredLimbRenderState();
	}

	@Override
	public void extractRenderState(SeveredLimb entity, SeveredLimbRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.part = entity.part();
		PlayerSkin skin = skinOf(entity);
		state.skin = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
		state.yaw = entity.getYRot();
		state.tumble = entity.tumble(partialTicks);
		state.lying = entity.lying(partialTicks);
		double ticksLeft = entity.remaining() * Balance.LIMB_LIFETIME_TICKS;
		state.alpha = (float) Math.clamp(ticksLeft / 40.0, 0.0, 1.0);
		state.burn = (float) entity.burn();
		// Wet while it bleeds, drying as the trickle runs out; never fully clean,
		// because blood dries on rather than rinsing off.
		double flow = entity.bleedRate() / WoundSeverity.CRITICAL.bloodPerSecond();
		state.blood = (float) Math.clamp(0.35 + 0.65 * flow, 0.0, 1.0);
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
		ModelPart[] skinParts = state.slim ? slim : wide;
		ModelPart[] overlayParts = state.slim ? slimOverlay : wideOverlay;
		ModelPart[] caps = state.slim ? slimCap : wideCap;
		ModelPart[] shells = state.slim ? slimBlood : wideBlood;
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
		poseStack.translate(-centreX(part, state.slim) / 16.0F, -centreY(part) / 16.0F, 0.0F);

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
					ScarLevel.NONE, state.blood, strength, light, overlay);
		}
		poseStack.popPose();
		submitCount++;
		super.submit(state, poseStack, collector, camera);
	}

	/** Horizontal centre of the region's cube, model pixels, from PlayerModel.createMesh. */
	private static float centreX(BodyPart part, boolean slim) {
		float width = slim ? 3.0F : 4.0F;
		return switch (part) {
			case LEFT_ARM -> -1.0F + width / 2.0F;
			case RIGHT_ARM -> (slim ? -2.0F : -3.0F) + width / 2.0F;
			default -> 0.0F;
		};
	}

	/** Vertical centre: arms run -2..10, legs 0..12. */
	private static float centreY(BodyPart part) {
		return part.isArm() ? 4.0F : 6.0F;
	}
}
