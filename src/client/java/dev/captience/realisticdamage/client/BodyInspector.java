package dev.captience.realisticdamage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * A larger, inspectable body panel beside the inventory. Hovering a region
 * names its state and lists what is wrong with it, which the compact HUD
 * diagram has no room to say.
 *
 * <p>Deliberately still no raw numbers: a filled bar shows roughly how intact a
 * region is, matching the rest of the mod. Exact values remain an admin thing
 * via {@code /rdmg info}.
 */
public final class BodyInspector {
	private BodyInspector() {}

	// Vanilla survival inventory is a fixed 176x166, centred.
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 166;

	private static final int SCALE = 3;
	private static final int SEG = 4 * SCALE;
	private static final int TORSO_W = 8 * SCALE;
	private static final int HEAD_H = 5 * SCALE;
	private static final int TORSO_H = 9 * SCALE;
	private static final int LEG_H = 8 * SCALE;
	private static final int PAD = SCALE;

	private static final int PANEL_BG = 0xE0100C0C;
	private static final int PANEL_EDGE = 0xFF3A3030;

	private record Region(BodyPart part, int x, int y, int w, int h) {
		boolean contains(int mx, int my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				ScreenEvents.afterExtract(screen).register(BodyInspector::render);
			}
		});
	}

	private static void render(net.minecraft.client.gui.screens.Screen screen, GuiGraphicsExtractor graphics,
			int mouseX, int mouseY, float tickProgress) {
		if (!ClientEffectsConfig.get().showBodyHud || !ClientState.active()) {
			return;
		}
		BodySnapshot snapshot = ClientState.snapshot();
		if (snapshot == null) {
			return;
		}

		int bodyW = TORSO_W + 2 * (SEG + PAD);
		int bodyH = HEAD_H + PAD + TORSO_H + PAD + LEG_H;
		int guiLeft = (screen.width - GUI_WIDTH) / 2;
		// Sit just left of the inventory, or hug the screen edge if it will not fit.
		int panelX = Math.max(4, guiLeft - bodyW - 26);
		int panelY = (screen.height - GUI_HEIGHT) / 2 + 6;

		graphics.fill(panelX - 6, panelY - 6, panelX + bodyW + 6, panelY + bodyH + 6, PANEL_BG);
		graphics.fill(panelX - 6, panelY - 6, panelX + bodyW + 6, panelY - 5, PANEL_EDGE);
		graphics.fill(panelX - 6, panelY + bodyH + 5, panelX + bodyW + 6, panelY + bodyH + 6, PANEL_EDGE);

		List<Region> regions = layout(panelX, panelY);
		Region hovered = null;
		for (Region region : regions) {
			drawRegion(graphics, snapshot, region, region.contains(mouseX, mouseY));
			if (region.contains(mouseX, mouseY)) {
				hovered = region;
			}
		}
		if (hovered != null) {
			drawDetail(graphics, snapshot, hovered.part(), mouseX, mouseY, screen.width);
		}
	}

	private static List<Region> layout(int x0, int y0) {
		int torsoX = x0 + SEG + PAD;
		int torsoY = y0 + HEAD_H + PAD;
		int legY = torsoY + TORSO_H + PAD;
		List<Region> regions = new ArrayList<>(6);
		regions.add(new Region(BodyPart.HEAD, torsoX + SCALE, y0, TORSO_W - 2 * SCALE, HEAD_H));
		regions.add(new Region(BodyPart.RIGHT_ARM, x0, torsoY, SEG, TORSO_H));
		regions.add(new Region(BodyPart.TORSO, torsoX, torsoY, TORSO_W, TORSO_H));
		regions.add(new Region(BodyPart.LEFT_ARM, torsoX + TORSO_W + PAD, torsoY, SEG, TORSO_H));
		regions.add(new Region(BodyPart.RIGHT_LEG, torsoX, legY, SEG, LEG_H));
		regions.add(new Region(BodyPart.LEFT_LEG, torsoX + TORSO_W - SEG, legY, SEG, LEG_H));
		return regions;
	}

	private static void drawRegion(GuiGraphicsExtractor graphics, BodySnapshot snapshot, Region region,
			boolean hovered) {
		BodyState state = snapshot.state(region.part());
		int color = BodyHud.stateColor(state);
		graphics.fill(region.x() - 1, region.y() - 1, region.x() + region.w() + 1, region.y() + region.h() + 1,
				hovered ? 0xFFFFFFFF : 0x80000000);
		graphics.fill(region.x(), region.y(), region.x() + region.w(), region.y() + region.h(), color);

		// A fill line showing roughly how intact it is, drained from the top.
		float fraction = snapshot.fraction(region.part());
		int empty = (int) ((1.0f - Math.clamp(fraction, 0.0f, 1.0f)) * region.h());
		if (empty > 0) {
			graphics.fill(region.x(), region.y(), region.x() + region.w(), region.y() + empty, 0x66000000);
		}
		if (snapshot.destroyed(region.part())) {
			StatusIcons.draw(graphics, StatusIcons.DESTROYED,
					region.x() + region.w() / 2 - StatusIcons.SIZE / 2,
					region.y() + region.h() / 2 - StatusIcons.SIZE / 2, 0xFF000000);
		}
	}

	private static void drawDetail(GuiGraphicsExtractor graphics, BodySnapshot snapshot, BodyPart part,
			int mouseX, int mouseY, int screenWidth) {
		Font font = Minecraft.getInstance().font;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("realisticdamage.part." + part.id()));
		lines.add(Component.translatable("realisticdamage.state."
				+ snapshot.state(part).name().toLowerCase(Locale.ROOT)));
		if (snapshot.destroyed(part)) {
			lines.add(Component.translatable("realisticdamage.detail.destroyed"));
		}
		if (snapshot.broken(part)) {
			lines.add(Component.translatable("realisticdamage.detail.broken"));
		}
		WoundSeverity wound = snapshot.wound(part);
		if (wound != null) {
			lines.add(Component.translatable("realisticdamage.detail.bleeding."
					+ wound.name().toLowerCase(Locale.ROOT)));
		}
		if (part == BodyPart.HEAD && snapshot.concussion()) {
			lines.add(Component.translatable("realisticdamage.detail.concussion"));
		}
		if (snapshot.burned()) {
			lines.add(Component.translatable("realisticdamage.detail.burned"));
		}

		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		int height = lines.size() * 10 + 4;
		int x = Math.min(mouseX + 10, screenWidth - width - 10);
		int y = mouseY - 4;

		graphics.fill(x - 4, y - 4, x + width + 4, y + height, PANEL_BG);
		graphics.fill(x - 4, y - 4, x + width + 4, y - 3, PANEL_EDGE);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x, y + i * 10, i == 0 ? 0xFFFFFFFF : 0xFFBDBDBD, true);
		}
	}
}
