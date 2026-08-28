package dev.captience.realisticdamage.client;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Accessibility settings (spec §44): every sensory effect can be attenuated or
 * switched off without disabling unconsciousness itself. All of it is editable
 * here — the JSON on disk is storage, never the interface.
 */
public class AccessibilityScreen extends Screen {
	@Nullable
	private final Screen parent;

	public AccessibilityScreen(@Nullable Screen parent) {
		super(Component.translatable("realisticdamage.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		int columnLeft = this.width / 2 - 155;
		int columnRight = this.width / 2 + 5;
		int y = 30;

		addRenderableWidget(Button.builder(
						Component.translatable("realisticdamage.config.preset")
								.append(": ").append(Component.translatable("realisticdamage.config.preset.arcade")),
						button -> { config.applyArcadePreset(); rebuildWidgets(); })
				.bounds(columnLeft, y, 150, 20).build());
		addRenderableWidget(Button.builder(
						Component.translatable("realisticdamage.config.preset")
								.append(": ").append(Component.translatable("realisticdamage.config.preset.grounded")),
						button -> { config.applyGroundedPreset(); rebuildWidgets(); })
				.bounds(columnRight, y, 150, 20).build());
		y += 28;

		addRenderableWidget(CycleButton.onOffBuilder(config.showBodyHud)
				.create(columnLeft, y, 150, 20, Component.translatable("realisticdamage.config.body_hud"),
						(button, value) -> config.showBodyHud = value));
		addRenderableWidget(CycleButton.onOffBuilder(config.bodyHudAlwaysVisible)
				.create(columnRight, y, 150, 20, Component.translatable("realisticdamage.config.body_hud_always"),
						(button, value) -> config.bodyHudAlwaysVisible = value));
		y += 28;

		addRenderableWidget(CycleButton.onOffBuilder(config.showStatusIcons)
				.create(columnLeft, y, 150, 20, Component.translatable("realisticdamage.config.status_icons"),
						(button, value) -> config.showStatusIcons = value));
		y += 28;

		addRenderableWidget(new IntensitySlider(columnLeft, y, "realisticdamage.config.vignette",
				() -> config.vignetteIntensity, value -> config.vignetteIntensity = value));
		addRenderableWidget(new IntensitySlider(columnRight, y, "realisticdamage.config.sensitivity",
				() -> config.sensitivityReductionIntensity, value -> config.sensitivityReductionIntensity = value));
		y += 24;

		addRenderableWidget(new IntensitySlider(columnLeft, y, "realisticdamage.config.afterimages",
				() -> config.afterimageIntensity, value -> config.afterimageIntensity = value));
		addRenderableWidget(new IntensitySlider(columnRight, y, "realisticdamage.config.exposure",
				() -> config.exposureIntensity, value -> config.exposureIntensity = value));
		y += 24;

		addRenderableWidget(new IntensitySlider(columnLeft, y, "realisticdamage.config.ringing",
				() -> config.ringingVolume, value -> config.ringingVolume = value));
		addRenderableWidget(new IntensitySlider(columnRight, y, "realisticdamage.config.heartbeat",
				() -> config.heartbeatVolume, value -> config.heartbeatVolume = value));
		y += 24;

		addRenderableWidget(new IntensitySlider(columnLeft, y, "realisticdamage.config.desaturation",
				() -> config.desaturationIntensity, value -> config.desaturationIntensity = value));
		addRenderableWidget(new IntensitySlider(columnRight, y, "realisticdamage.config.distortion",
				() -> config.distortionIntensity, value -> config.distortionIntensity = value));
		y += 24;

		addRenderableWidget(CycleButton.booleanBuilder(
						Component.translatable("realisticdamage.config.tinnitus"),
						Component.translatable("realisticdamage.config.tinnitus.bell"),
						config.tinnitusTone)
				.create(columnLeft, y, 150, 20, Component.empty(),
						(button, value) -> config.tinnitusTone = value));
		addRenderableWidget(new IntensitySlider(columnRight, y, "realisticdamage.config.camera_drift",
				() -> config.cameraDriftIntensity, value -> config.cameraDriftIntensity = value));
		y += 24;

		addRenderableWidget(CycleButton.onOffBuilder(config.fullBlackUnconscious)
				.create(columnLeft, y, 150, 20, Component.translatable("realisticdamage.config.full_black"),
						(button, value) -> config.fullBlackUnconscious = value));
		y += 28;

		addRenderableWidget(Button.builder(Component.translatable("realisticdamage.config.disable_all_effects"),
						button -> {
							config.vignetteIntensity = 0.0;
							config.sensitivityReductionIntensity = 0.0;
							config.afterimageIntensity = 0.0;
							config.exposureIntensity = 0.0;
							config.ringingVolume = 0.0;
							config.heartbeatVolume = 0.0;
							config.cameraDriftIntensity = 0.0;
							config.desaturationIntensity = 0.0;
							config.distortionIntensity = 0.0;
							rebuildWidgets();
						})
				.bounds(this.width / 2 - 155, y, 310, 20).build());
		y += 28;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(this.width / 2 - 100, y, 200, 20).build());
	}

	@Override
	public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("realisticdamage.config.note"),
				this.width / 2, 26, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		ClientEffectsConfig.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	/** A 0-100% slider bound to one config field. */
	private static final class IntensitySlider extends AbstractSliderButton {
		private final String labelKey;
		private final DoubleConsumer setter;

		private IntensitySlider(int x, int y, String labelKey, DoubleSupplier getter, DoubleConsumer setter) {
			super(x, y, 150, 20, Component.empty(), Math.clamp(getter.getAsDouble(), 0.0, 1.0));
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
