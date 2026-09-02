package dev.captience.realisticdamage.client;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
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
		this.list.addSmall(
				slider("realisticdamage.config.stumble",
						config::stumbleIntensity, value -> config.stumbleIntensityValue = value),
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
				CycleButton.booleanBuilder(
								Component.translatable("realisticdamage.config.sound_style.visceral"),
								Component.translatable("realisticdamage.config.sound_style.muted"),
								config.visceralInjurySounds)
						.create(0, 0, 150, 20,
								Component.translatable("realisticdamage.config.sound_style"),
								(button, value) -> config.visceralInjurySounds = value));

		this.list.addHeader(Component.translatable("realisticdamage.config.section.everything"));
		this.list.addBig(Button.builder(Component.translatable("realisticdamage.config.disable_all_effects"),
						button -> { config.disableAllEffects(); rebuildWidgets(); })
				.build());
	}

	@Override
	protected void addTitle() {
		super.addTitle();
		this.layout.addTitleHeader(Component.translatable("realisticdamage.config.note").withStyle(
				style -> style.withColor(0xA0A0A0)), this.font);
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
