package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class RealisticDamageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RagdollDebug.init();
		ClientEffectsConfig.load();
		BodyInspector.init();
		InjuryMessageFilter.register();

		// The stain field and the client-side emitter draw from the same sprite
		// definition as the drop itself, so binding happens inside the provider —
		// this is the only point at which a SpriteSet for it is handed out.
		net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(
				dev.captience.realisticdamage.RDParticles.BLOOD, BloodDrop.Provider::new);
		// Shader packs swap pipelines by name, and ours has to be on their list.
		BloodStainLayer.registerWithShaderLoader();
		ModelBlood.registerWithShaderLoader();

		// Blood on the body, for every player renderer including the skin variants.
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT
				.register((entityType, renderer, helper, context) -> {
					if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
						@SuppressWarnings("unchecked")
						var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<
								net.minecraft.client.renderer.entity.state.AvatarRenderState,
								net.minecraft.client.model.player.PlayerModel>) avatar;
						helper.register(new BloodBodyLayer<>(parent));
					} else if (renderer instanceof net.minecraft.client.renderer.entity.HumanoidMobRenderer<?, ?, ?> humanoid) {
						// Every humanoid mob renderer (1.7): the layer returns at once
						// for a body with nothing on it, so the cost is one null read
						// per frame for the mobs the server never tagged.
						@SuppressWarnings("unchecked")
						var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<
								net.minecraft.client.renderer.entity.state.HumanoidRenderState,
								net.minecraft.client.model.HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState>>) humanoid;
						helper.register(new BloodBodyLayer<>(parent));
					} else if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> living
							&& living.getModel() instanceof net.minecraft.client.model.monster.illager.IllagerModel<?>) {
						// Illagers (1.7): their own cubes, found by name.
						@SuppressWarnings("unchecked")
						var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<
								net.minecraft.client.renderer.entity.state.LivingEntityRenderState,
								net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState>>) living;
						helper.register(new FigureBloodLayer<>(parent, LimbMeshes.Kind.ILLAGER));
					} else if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> living
							&& (living.getModel() instanceof net.minecraft.client.model.monster.witch.WitchModel
									|| living.getModel() instanceof net.minecraft.client.model.npc.VillagerModel)) {
						@SuppressWarnings("unchecked")
						var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<
								net.minecraft.client.renderer.entity.state.LivingEntityRenderState,
								net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState>>) living;
						helper.register(new FigureBloodLayer<>(parent, LimbMeshes.Kind.VILLAGER));
					} else if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> living) {
						// Everything else (1.7 slice 3): the model's own parts, by name.
						@SuppressWarnings("unchecked")
						var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<
								net.minecraft.client.renderer.entity.state.LivingEntityRenderState,
								net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState>>) living;
						helper.register(new ModelBloodLayer<>(parent));
					}
				});

		// The lost limb lying where it fell (1.6.2).
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				dev.captience.realisticdamage.RDEntities.SEVERED_LIMB, SeveredLimbRenderer::new);
		// ...and a head (2.0), which can be taken.
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				dev.captience.realisticdamage.RDEntities.SEVERED_HEAD, SeveredHeadRenderer::new);
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (dev.captience.realisticdamage.RDComponents.severed(stack)) {
				lines.add(net.minecraft.network.chat.Component.translatable("item.realisticdamage.severed")
						.withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		});

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.ImpactPayload.TYPE,
				(payload, context) -> ImpactEffect.trigger(payload.magnitude(), payload.yaw(),
						ImpactFamily.of(payload.resolved())));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.LandingPayload.TYPE,
				(payload, context) -> LandingEffect.trigger(payload.severity(), payload.hardness()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.InjurySoundPayload.TYPE,
				(payload, context) -> InjuryAudio.play(payload));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.TreatingPayload.TYPE,
				(payload, context) -> TriageHud.setTarget(payload.targetId()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.DeathPayload.TYPE,
				(payload, context) -> {
					ClientState.setDeathViolence(payload.violence());
					ClientState.setDeathKind(payload.kind());
					ClientState.setHeadless(payload.headless());
				});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.CorpsePayload.TYPE,
				(payload, context) -> RemoteCorpses.start(payload));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.KnockdownPayload.TYPE,
				(payload, context) -> LiveRagdolls.start(payload));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.FlinchPayload.TYPE,
				(payload, context) -> Flinches.hit(payload));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.StandTestPayload.TYPE,
				(payload, context) -> RagdollCorpse.standTest(payload));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				dev.captience.realisticdamage.net.MovingStartPayload.TYPE,
				(payload, context) -> RagdollCorpse.movingStart(payload));

		// Reachable without Mod Menu, per the "no config-file editing" rule.
		KeyMapping.Category category = KeyMapping.Category.register(RealisticDamage.id("main"));
		KeyMapping openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.realisticdamage.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

		// Sensory effects draw with vanilla's own camera overlays; the body
		// diagram sits above the status bars but below chat/titles (spec §42).
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				RealisticDamage.id("consciousness_effects"), ConsciousnessEffects::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
				RealisticDamage.id("body_hud"), BodyHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
				RealisticDamage.id("triage_hud"), TriageHud::render);
		// The blackout last of all (1.6.2), so going dark covers the hotbar, the
		// hearts and the body diagram too, and not just the world behind them.
		HudElementRegistry.addLast(RealisticDamage.id("blackout"), ConsciousnessEffects::renderBlackout);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!noiseListening) {
				// The sound manager is not ready when this initialiser runs; it is
				// by the first tick. Hears every sound, for the concussed wince.
				client.getSoundManager().addListener(ConcussionEffects.NOISE);
				noiseListening = true;
			}
			ClientState.tick();
			VrHaptics.tick();
			DeathCamera.tick();
			RemoteCorpses.tick();
			ConsciousnessAudio.tick();
			ConcussionEffects.tick();
			AirHunger.tick();
			Breathing.tick();
			MouseInertia.tickFallback();
			VisionEffects.tick();
			RescueInput.tick(client);
			// Blood last, and in this order: the emitter reads the heart that
			// ClientState.tick just advanced, and the lens is fed by the emitter.
			BloodEmitter.tick();
			BloodBody.tick();
			BloodStains.tick();
			BloodLens.tick();
			while (openSettings.consumeClick()) {
				client.gui.setScreen(new AccessibilityScreen(client.gui.screen()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ConsciousnessAudio.reset();
			VisionEffects.reset();
			MouseInertia.reset();
			ImpactEffect.reset();
			LandingEffect.reset();
			StumbleInput.reset();
			TriageHud.reset();
			BloodEmitter.reset();
			BloodBody.reset();
			BloodStains.reset();
			BloodLens.reset();
			Afterimage.reset();
			VrLens.reset();
			CameraMotion.reset();
			DeathCamera.reset();
			BlastShock.reset();
			ConcussionEffects.reset();
			AirHunger.reset();
			Breathing.reset();
			LowHealthEffects.reset();
		});
	}

	private static boolean noiseListening;
}
