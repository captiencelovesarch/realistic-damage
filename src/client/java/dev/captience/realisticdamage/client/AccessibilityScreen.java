package dev.captience.realisticdamage.client;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Accessibility settings (spec §44): every sensory effect can be attenuated or
 * switched off without disabling unconsciousness itself. All of it is editable
 * here — the JSON on disk is storage, never the interface.
 *
 * <p>Built on vanilla's scrolling options list rather than a hand-placed grid.
 * The grid ran off the bottom of the screen at large GUI scales once there were
 * enough controls to matter, and every release adds more.
 */
public class AccessibilityScreen extends OptionsSubScreen {
	public AccessibilityScreen(@Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options,
				Component.translatable("realisticdamage.config.title"));
	}

	@Override
	protected void addOptions() {
		ClientEffectsConfig config = ClientEffectsConfig.get();

		// The server's settings (2.0): their own page, because they are not
		// this player's to change on someone else's server.
		this.list.addBig(Button.builder(Component.translatable("realisticdamage.rules.open"),
						button -> this.minecraft.gui.setScreen(
								new dev.captience.realisticdamage.fieldkit.client.WorldRulesScreen(this)))
				.build());

		this.list.addSmall(
				Button.builder(Component.translatable("realisticdamage.config.preset")
								.append(": ").append(Component.translatable("realisticdamage.config.preset.arcade")),
						button -> { config.applyArcadePreset(); rebuildWidgets(); })
						.width(150).build(),
				Button.builder(Component.translatable("realisticdamage.config.preset")
								.append(": ").append(Component.translatable("realisticdamage.config.preset.grounded")),
						button -> { config.applyGroundedPreset(); rebuildWidgets(); })
						.width(150).build());

		this.list.addHeader(Component.translatable("realisticdamage.config.section.display"));
		this.list.addSmall(
				CycleButton.onOffBuilder(config.showBodyHud)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.body_hud"),
								(button, value) -> config.showBodyHud = value),
				CycleButton.onOffBuilder(config.bodyHudAlwaysVisible)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.body_hud_always"),
								(button, value) -> config.bodyHudAlwaysVisible = value));
		this.list.addSmall(
				CycleButton.onOffBuilder(config.showStatusIcons)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.status_icons"),
								(button, value) -> config.showStatusIcons = value),
				CycleButton.onOffBuilder(config.showNumericStats)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.numeric_stats"),
								(button, value) -> config.showNumericStats = value));
		// Display rather than Blood, even though it also gates the pass on the
		// player model: what this switch turns off is a piece of information
		// about your body, and the place a player will look for it is beside the
		// other two switches that decide what the body diagram says.
		//
		// The injury text shares the row for the same reason. It is not a sound
		// and not a screen effect — it is the fourth thing that decides what the
		// mod tells you about your body, so it belongs with the other three
		// rather than in a section of its own.
		this.list.addSmall(
				CycleButton.onOffBuilder(config.showScars)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.scars"),
								(button, value) -> config.showScars = value),
				CycleButton.onOffBuilder(config.showInjuryMessages)
						.create(0, 0, 150, 20,
								Component.translatable("realisticdamage.config.injury_messages"),
								(button, value) -> config.showInjuryMessages = value));
		// And the fifth: how an injured limb is carried on the model. Display
		// rather than Vision for the same reason as the four above — it is the
		// body telling you what is wrong with it, not something happening to
		// your eyes — and it sits next to Scars because the two are the only
		// controls over what the player model itself says.
		this.list.addSmall(
				CycleButton.onOffBuilder(config.showInjuryPose)
						.create(0, 0, 150, 20,
								Component.translatable("realisticdamage.config.injury_pose"),
								(button, value) -> config.showInjuryPose = value),
				// Its first-person half (2.0): the hold of a broken arm and the
				// limp of a broken leg, as you see them yourself.
				CycleButton.onOffBuilder(config.firstPersonInjury)
						.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
								Component.translatable("realisticdamage.config.first_person_injury.tooltip")))
						.create(0, 0, 150, 20,
								Component.translatable("realisticdamage.config.first_person_injury"),
								(button, value) -> config.firstPersonInjury = value));

		this.list.addHeader(Component.translatable("realisticdamage.config.section.vision"));
		this.list.addSmall(
				slider("realisticdamage.config.vignette",
						() -> config.vignetteIntensity, value -> config.vignetteIntensity = value),
				slider("realisticdamage.config.desaturation",
						() -> config.desaturationIntensity, value -> config.desaturationIntensity = value));
		this.list.addSmall(
				slider("realisticdamage.config.distortion",
						() -> config.distortionIntensity, value -> config.distortionIntensity = value),
				slider("realisticdamage.config.afterimages",
						() -> config.afterimageIntensity, value -> config.afterimageIntensity = value));
		this.list.addSmall(
				slider("realisticdamage.config.exposure",
						() -> config.exposureIntensity, value -> config.exposureIntensity = value),
				CycleButton.onOffBuilder(config.fullBlackUnconscious)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.full_black"),
								(button, value) -> config.fullBlackUnconscious = value));
		this.list.addSmall(
				slider("realisticdamage.config.death",
						config::deathIntensity, value -> config.deathIntensityValue = value),
				CycleButton.booleanBuilder(
								Component.translatable("realisticdamage.config.death_style.realistic"),
								Component.translatable("realisticdamage.config.death_style.cinematic"),
								config.realisticDeath)
						.create(0, 0, 150, 20,
								Component.translatable("realisticdamage.config.death_style"),
								(button, value) -> config.realisticDeath = value));
		this.list.addSmall(
				CycleButton.onOffBuilder(config.deathCamera)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.death_camera"),
								(button, value) -> config.deathCamera = value),
				CycleButton.<ClientEffectsConfig.RagdollMode>builder(
								mode -> Component.translatable("realisticdamage.config.ragdolls."
										+ mode.name().toLowerCase(java.util.Locale.ROOT)),
								config.ragdolls)
						.withValues(java.util.List.of(ClientEffectsConfig.RagdollMode.values()))
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.ragdolls"),
								(button, value) -> config.ragdolls = value));
		this.list.addSmall(
				CycleButton.booleanBuilder(
								Component.translatable("realisticdamage.config.death_view.ragdoll"),
								Component.translatable("realisticdamage.config.death_view.classic"),
								config.ownRagdoll)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.death_view"),
								(button, value) -> config.ownRagdoll = value),
				CycleButton.onOffBuilder(config.knockdownRagdolls)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.knockdown_ragdolls"),
								(button, value) -> config.knockdownRagdolls = value));
		this.list.addSmall(
				CycleButton.booleanBuilder(
								Component.translatable("realisticdamage.config.decapitation_view.head"),
								Component.translatable("realisticdamage.config.decapitation_view.black"),
								config.decapitationView)
						.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
								Component.translatable("realisticdamage.config.decapitation_view.tooltip")))
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.decapitation_view"),
								(button, value) -> config.decapitationView = value),
				CycleButton.onOffBuilder(config.stiffNeck)
						.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
								Component.translatable("realisticdamage.config.stiff_neck.tooltip")))
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.stiff_neck"),
								(button, value) -> config.stiffNeck = value));
		this.list.addSmall(
				CycleButton.onOffBuilder(config.hitFlinches)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.hit_flinches"),
								(button, value) -> config.hitFlinches = value),
				CycleButton.<ClientEffectsConfig.DeathStumble>builder(
						style -> Component.translatable("realisticdamage.config.death_stumble."
								+ style.name().toLowerCase(java.util.Locale.ROOT)), config.deathStumble())
						.withValues(java.util.List.of(ClientEffectsConfig.DeathStumble.values()))
						.withTooltip(style -> net.minecraft.client.gui.components.Tooltip.create(
								Component.translatable("realisticdamage.config.death_stumble.tooltip")))
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.death_stumble"),
								(button, value) -> config.deathStumble = value));
		this.list.addSmall(
				CycleButton.<Integer>builder(
								limit -> Component.literal(Integer.toString(limit)),
								config.ragdollLimit)
						.withValues(java.util.List.of(4, 8, 16, 32))
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.ragdoll_limit"),
								(button, value) -> config.ragdollLimit = value),
				CycleButton.<Integer>builder(
								seconds -> Component.translatable("realisticdamage.config.bodies_stay.seconds", seconds),
								config.bodiesStay)
						.withValues(java.util.List.of(5, 30, 120, 300))
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.bodies_stay"),
								(button, value) -> config.bodiesStay = value));

		// The four things that happen to you rather than to a limb (1.7). Their
		// own section: none of them is a vision effect, a sound or an aim effect
		// alone — each one is all three at once, and a player who hates one of
		// them wants one control for it, not three.
		this.list.addHeader(Component.translatable("realisticdamage.config.section.afflictions"));
		this.list.addSmall(
				slider("realisticdamage.config.blast_deafness",
						config::blastDeafness, value -> config.blastDeafnessValue = value),
				slider("realisticdamage.config.concussion_effects",
						config::concussionEffects, value -> config.concussionEffectsValue = value));
		this.list.addSmall(
				slider("realisticdamage.config.low_health",
						config::lowHealthEffects, value -> config.lowHealthEffectsValue = value),
				// Sedation's eyelids (2.0 field kit): something that happens to you.
				slider("realisticdamage.config.sedation_effects",
						config::sedationEffects, value -> config.sedationEffectsValue = value));

		this.list.addHeader(Component.translatable("realisticdamage.config.section.blood"));
		// The master first and on its own row: everything under it is a share of
		// this one, so a player who wants no blood at all has a single control to
		// find rather than four to hunt down.
		this.list.addSmall(
				slider("realisticdamage.config.blood",
						config::bloodIntensity, value -> config.bloodIntensityValue = value),
				CycleButton.booleanBuilder(
								Component.translatable("realisticdamage.config.blood_style.visceral"),
								Component.translatable("realisticdamage.config.blood_style.muted"),
								config.visceralBlood)
						.create(0, 0, 150, 20,
								Component.translatable("realisticdamage.config.blood_style"),
								(button, value) -> config.visceralBlood = value));
		this.list.addSmall(
				slider("realisticdamage.config.blood_stains",
						() -> config.bloodStainIntensity, value -> config.bloodStainIntensity = value),
				slider("realisticdamage.config.blood_lens",
						() -> config.bloodLensIntensity, value -> config.bloodLensIntensity = value));
		this.list.addSmall(
				slider("realisticdamage.config.blood_body",
						() -> config.bloodBodyIntensity, value -> config.bloodBodyIntensity = value),
				null);

		this.list.addHeader(Component.translatable("realisticdamage.config.section.aim"));
		this.list.addSmall(
				slider("realisticdamage.config.sensitivity",
						() -> config.sensitivityReductionIntensity,
						value -> config.sensitivityReductionIntensity = value),
				slider("realisticdamage.config.inertia",
						config::lookInertia, value -> config.lookInertiaIntensity = value));
		this.list.addSmall(
				slider("realisticdamage.config.camera_drift",
						() -> config.cameraDriftIntensity, value -> config.cameraDriftIntensity = value),
				slider("realisticdamage.config.impact",
						() -> config.impactIntensity, value -> config.impactIntensity = value));
		// Its own row: this is the only slider here that changes where the player
		// physically ends up, so it should not read as one more screen effect.
		// Head Sway shares it because it is the opposite statement — the one
		// camera effect that never touches the aim — and the two belong side by
		// side for a player deciding how much of their control to give up.
		this.list.addSmall(
				slider("realisticdamage.config.stumble",
						config::stumbleIntensity, value -> config.stumbleIntensityValue = value),
				slider("realisticdamage.config.camera_sway",
						config::cameraSway, value -> config.cameraSwayIntensityValue = value));
		// The heavy head on its own row, alone: the one camera effect most likely
		// to disagree with someone's stomach should not be hiding beside a sibling.
		this.list.addSmall(
				slider("realisticdamage.config.camera_lag",
						config::cameraLag, value -> config.cameraLagIntensityValue = value),
				null);

		this.list.addHeader(Component.translatable("realisticdamage.config.section.sound"));
		this.list.addSmall(
				slider("realisticdamage.config.ringing",
						() -> config.ringingVolume, value -> config.ringingVolume = value),
				CycleButton.booleanBuilder(
								Component.translatable("realisticdamage.config.tinnitus"),
								Component.translatable("realisticdamage.config.tinnitus.bell"),
								config.tinnitusTone)
						.create(0, 0, 150, 20, Component.empty(),
								(button, value) -> config.tinnitusTone = value));
		this.list.addSmall(
				slider("realisticdamage.config.heartbeat",
						() -> config.heartbeatVolume, value -> config.heartbeatVolume = value),
				slider("realisticdamage.config.muffling",
						() -> config.mufflingIntensity, value -> config.mufflingIntensity = value));
		this.list.addSmall(
				slider("realisticdamage.config.injury_sounds",
						() -> config.injurySoundVolume, value -> config.injurySoundVolume = value),
				CycleButton.<dev.captience.realisticdamage.net.SoundSet>builder(
								set -> Component.translatable("realisticdamage.config.sound_set." + set.id()),
								config.soundSet())
						.withValues(java.util.List.of(dev.captience.realisticdamage.net.SoundSet.values()))
						.withTooltip(set -> net.minecraft.client.gui.components.Tooltip.create(
								Component.translatable("realisticdamage.config.sound_set." + set.id() + ".tooltip")))
						.create(0, 0, 150, 20,
								Component.translatable("realisticdamage.config.sound_set"),
								(button, value) -> config.soundSetValue = value));
		this.list.addSmall(
				CycleButton.onOffBuilder(config.screams)
						.create(0, 0, 150, 20, Component.translatable("realisticdamage.config.screams"),
								(button, value) -> config.screams = value),
				slider("realisticdamage.config.breathing",
						config::breathingVolume, value -> config.breathingVolumeValue = value));

		// Only with Vivecraft installed (1.7 alpha.10): a control that can do
		// nothing is clutter for everyone playing flat.
		if (dev.captience.realisticdamage.client.compat.Vr.loaded()) {
			this.list.addHeader(Component.translatable("realisticdamage.config.section.vr"));
			this.list.addSmall(
					slider("realisticdamage.config.vr_haptics",
							config::vrHaptics, value -> config.vrHapticsValue = value),
					null);
		}

		this.list.addHeader(Component.translatable("realisticdamage.config.section.everything"));
		this.list.addBig(Button.builder(Component.translatable("realisticdamage.config.disable_all_effects"),
						button -> { config.disableAllEffects(); rebuildWidgets(); })
				.build());
	}

	/**
	 * Vanilla's options screens never rebuild, so their layout keeps whatever
	 * was added to it. The presets and the panic button rebuild this one, and
	 * each rebuild stacked another list on the last: the new one drew on top
	 * while the wheel and the clicks went to the stale one underneath, so after
	 * a preset the page would not scroll and its buttons seemed not to change.
	 * Start from empty.
	 */
	@Override
	protected void init() {
		this.layout.removeChildren();
		super.init();
	}

	/**
	 * The title and, under it, what these settings are for. A header of its own
	 * height and a column rather than two title headers: those share one frame
	 * and were drawn on top of each other.
	 */
	@Override
	protected void addTitle() {
		this.layout.setHeaderHeight(44);
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(5));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		header.addChild(new StringWidget(Component.translatable("realisticdamage.config.note").withStyle(
				style -> style.withColor(0xA0A0A0)), this.font));
	}

	@Override
	public void removed() {
		// Vanilla's would save Minecraft's own options; ours is the one that changed.
		ClientEffectsConfig.save();
	}

	@Override
	public void onClose() {
		ClientEffectsConfig.save();
		super.onClose();
	}

	private static IntensitySlider slider(String labelKey, DoubleSupplier getter, DoubleConsumer setter) {
		return new IntensitySlider(labelKey, getter, setter);
	}

	/** A 0-100% slider bound to one config field. */
	private static final class IntensitySlider extends AbstractSliderButton {
		private final String labelKey;
		private final DoubleConsumer setter;

		private IntensitySlider(String labelKey, DoubleSupplier getter, DoubleConsumer setter) {
			super(0, 0, 150, 20, Component.empty(), Math.clamp(getter.getAsDouble(), 0.0, 1.0));
			this.labelKey = labelKey;
			this.setter = setter;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			int percent = (int) Math.round(this.value * 100.0);
			setMessage(percent == 0
					? Component.translatable(labelKey).append(": ").append(CommonComponents.OPTION_OFF)
					: Component.translatable(labelKey).append(": " + percent + "%"));
		}

		@Override
		protected void applyValue() {
			setter.accept(this.value);
		}
	}
}
