package dev.captience.realisticdamage.fieldkit.client;

import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.Half;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The first-launch question (2.0): the field kit's two switches, a sentence
 * each, and "you can change these later". Shown once, over the title screen,
 * the first time it appears with 2.0 (see {@link Welcome}).
 *
 * <p>Both start off, so leaving without touching anything keeps the mod as it
 * was: food and time are the only cure. What it sets is this install's own
 * settings, the ones every singleplayer world uses; a server keeps its own.
 */
public class WelcomeScreen extends Screen {
	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 40, 33);
	private CycleButton<Boolean> weapons;
	private CycleButton<Boolean> medicine;

	public WelcomeScreen(Screen parent) {
		super(Component.translatable("realisticdamage.fieldkit.welcome.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addTitleHeader(title, font);
		LinearLayout body = layout.addToContents(LinearLayout.vertical().spacing(10));
		int textWidth = Math.min(360, width - 40);
		body.addChild(new MultiLineTextWidget(
				Component.translatable("realisticdamage.fieldkit.welcome.intro"), font)
				.setMaxWidth(textWidth).setCentered(true), s -> s.alignHorizontallyCenter());
		weapons = row(body, Half.WEAPONS, textWidth);
		medicine = row(body, Half.MEDICINE, textWidth);
		body.addChild(new MultiLineTextWidget(
				Component.translatable("realisticdamage.fieldkit.welcome.later").withStyle(s -> s.withColor(0xA0A0A0)),
				font).setMaxWidth(textWidth).setCentered(true), s -> s.alignHorizontallyCenter());
		layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private CycleButton<Boolean> row(LinearLayout body, Half half, int textWidth) {
		LinearLayout row = body.addChild(LinearLayout.horizontal().spacing(10), s -> s.alignHorizontallyCenter());
		CycleButton<Boolean> button = row.addChild(CycleButton.onOffBuilder(half.on(ServerConfig.get()))
				.create(0, 0, 130, 20, half.title(),
						(b, value) -> LocalRules.write(field(half), Boolean.toString(value))),
				s -> s.alignVerticallyMiddle());
		row.addChild(new MultiLineTextWidget(
				Component.translatable("realisticdamage.fieldkit.welcome." + half.id()), font)
				.setMaxWidth(textWidth - 140), s -> s.alignVerticallyMiddle());
		return button;
	}

	private static java.lang.reflect.Field field(Half half) {
		try {
			return ServerConfig.class.getField(half.field());
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	/** The two buttons, for the game test. */
	public CycleButton<Boolean> weaponsButton() {
		return weapons;
	}

	public CycleButton<Boolean> medicineButton() {
		return medicine;
	}

	@Override
	public void onClose() {
		Welcome.answered();
		minecraft.gui.setScreen(parent);
	}
}
