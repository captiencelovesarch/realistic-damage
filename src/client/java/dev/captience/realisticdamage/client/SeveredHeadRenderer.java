package dev.captience.realisticdamage.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.entity.SeveredHead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Draws a head off its body (2.0): the owner's own head, from the model its
 * renderer uses, in its own texture, turning end over end as it rolls.
 *
 * <p>The head part is baked here from the same layer the mob is drawn with, so
 * hats, hoods, noses and a player's second skin layer come with it (they are
 * the head's children). A type this has no layer for is not drawn: better a
 * head that is not there than a zombie's on a villager.
 *
 * <p>Your own head is not drawn while the camera rides it: the camera is inside
 * it, and from inside a head all you would see is the back of your face.
 */
public class SeveredHeadRenderer extends EntityRenderer<SeveredHead, SeveredHeadRenderer.State> {
	/** Heads submitted this session; read by the client gametest. */
	private static long submitCount;

	public static long submits() {
		return submitCount;
	}

	public static final class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
		public @Nullable ModelPart head;
		public Identifier skin = DefaultPlayerSkin.getDefaultSkin().body().texturePath();
		/** Model pixels from the head's pivot (the neck) up to its middle. */
		public float centre = 4.0F;
		public float yaw;
		public float tumble;
		public float alpha = 1.0F;
		public float burn;
		/** 0 to 1: how purple a hissing wither skull is flashing right now. */
		public float flash;
	}

	private record Layer(ModelLayerLocation location, float centre) {}

	private static final Map<String, Layer> LAYERS = new HashMap<>();

	static {
		layer("zombie", ModelLayers.ZOMBIE, 4.0F);
		layer("husk", ModelLayers.HUSK, 4.0F);
		layer("drowned", ModelLayers.DROWNED, 4.0F);
		layer("giant", ModelLayers.GIANT, 4.0F);
		layer("zombie_villager", ModelLayers.ZOMBIE_VILLAGER, 5.0F);
		layer("zombified_piglin", ModelLayers.ZOMBIFIED_PIGLIN, 4.0F);
		layer("skeleton", ModelLayers.SKELETON, 4.0F);
		layer("stray", ModelLayers.STRAY, 4.0F);
		layer("wither_skeleton", ModelLayers.WITHER_SKELETON, 4.0F);
		layer("bogged", ModelLayers.BOGGED, 4.0F);
		layer("parched", ModelLayers.PARCHED, 4.0F);
		layer("piglin", ModelLayers.PIGLIN, 4.0F);
		layer("piglin_brute", ModelLayers.PIGLIN_BRUTE, 4.0F);
		layer("pillager", ModelLayers.PILLAGER, 5.0F);
		layer("vindicator", ModelLayers.VINDICATOR, 5.0F);
		layer("evoker", ModelLayers.EVOKER, 5.0F);
		layer("illusioner", ModelLayers.ILLUSIONER, 5.0F);
		layer("villager", ModelLayers.VILLAGER, 5.0F);
		layer("wandering_trader", ModelLayers.WANDERING_TRADER, 5.0F);
		layer("witch", ModelLayers.WITCH, 5.0F);
	}

	private static void layer(String type, ModelLayerLocation location, float centre) {
		LAYERS.put("minecraft:" + type, new Layer(location, centre));
	}

	private final Map<String, ModelPart> heads = new HashMap<>();
	private final ModelPart wide;
	private final ModelPart slim;

	public SeveredHeadRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.25F;
		for (Map.Entry<String, Layer> entry : LAYERS.entrySet()) {
			heads.put(entry.getKey(), loose(context.bakeLayer(entry.getValue().location()).getChild("head")));
		}
		wide = loose(context.bakeLayer(ModelLayers.PLAYER).getChild("head"));
		slim = loose(context.bakeLayer(ModelLayers.PLAYER_SLIM).getChild("head"));
	}

	/** The head about its own neck: the pivot it had on the body means nothing off it. */
	private static ModelPart loose(ModelPart head) {
		head.x = 0.0F;
		head.y = 0.0F;
		head.z = 0.0F;
		head.xRot = 0.0F;
		head.yRot = 0.0F;
		head.zRot = 0.0F;
		return head;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(SeveredHead entity, Frustum frustum, double x, double y, double z) {
		if (ownHeadRidden(entity)) {
			return false;
		}
		return super.shouldRender(entity, frustum, x, y, z);
	}

	/** This is your head and the view is riding it. */
	static boolean ownHeadRidden(SeveredHead entity) {
		Player me = Minecraft.getInstance().player;
		return me != null && me.getUUID().equals(entity.ownerId()) && HeadView.riding();
	}

	@Override
	public void extractRenderState(SeveredHead entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		EntityType<?> ownerType = entity.ownerType();
		if (ownerType == null) {
			PlayerSkin skin = skinOf(entity);
			state.skin = skin.body().texturePath();
			state.head = skin.model() == PlayerModelType.SLIM ? slim : wide;
			state.centre = 4.0F;
		} else {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(ownerType).toString();
			state.head = heads.get(id);
			Layer layer = LAYERS.get(id);
			state.centre = layer == null ? 4.0F : layer.centre();
			state.skin = textureOf(ownerType);
		}
		state.yaw = entity.getYRot();
		state.tumble = entity.tumble(partialTicks);
		double ticksLeft = entity.remaining() * (entity.trophy() ? Balance.HEAD_LIFETIME_TICKS : Balance.LIMB_LIFETIME_TICKS);
		state.alpha = (float) Math.clamp(ticksLeft / 40.0, 0.0, 1.0);
		state.burn = (float) entity.burn();
		state.flash = entity.hissing()
				? (float) (0.5 + 0.5 * Math.sin((entity.age() + partialTicks) * 1.3)) : 0.0F;
	}

	private static Identifier textureOf(EntityType<?> type) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
		return switch (id) {
			case "villager" -> Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
			case "wandering_trader" -> Identifier.withDefaultNamespace("textures/entity/wandering_trader/wandering_trader.png");
			case "witch" -> Identifier.withDefaultNamespace("textures/entity/witch/witch.png");
			case "giant" -> Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
			default -> MobLooks.lookFor(type).texture();
		};
	}

	private static PlayerSkin skinOf(SeveredHead entity) {
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
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		ModelPart head = state.head;
		if (head != null) {
			poseStack.pushPose();
			poseStack.translate(0.0F, state.boundingBoxHeight / 2.0F, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
			poseStack.mulPose(Axis.XP.rotationDegrees(state.tumble));
			float scale = 1.0F - 0.6F * state.burn;
			poseStack.scale(scale, scale, scale);
			// Vanilla's model flip, then the head's middle to the origin so it
			// turns about itself and not about the neck.
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, state.centre / 16.0F, 0.0F);
			float shade = 1.0F - 0.7F * state.burn;
			// A hissing wither skull pulses toward the Wither's purple.
			float f = state.flash;
			int tint = ARGB.colorFromFloat(state.alpha, shade * (1.0F - 0.25F * f), shade * (1.0F - 0.7F * f), shade);
			collector.submitModelPart(head, poseStack, RenderTypes.entityTranslucent(state.skin),
					state.lightCoords, OverlayTexture.NO_OVERLAY, null, tint, null, 0);
			poseStack.popPose();
			submitCount++;
		}
		super.submit(state, poseStack, collector, camera);
	}
}
