package dev.captience.realisticdamage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * A larger, inspectable body panel beside the inventory. Hovering a region
 * names its state and lists what is wrong with it, which the compact HUD
 * diagram has no room to say.
 *
 * <p>By default still no raw numbers: a filled bar shows roughly how intact a
 * region is, matching the rest of the mod. "Exact Numbers" opts into condition
 * figures here and nowhere else — the HUD stays wordless, because it is the HUD
 * being wordless that makes the mod readable at a glance.
 *
 * <p>Blood and Consciousness are named by band even then, never as figures. A
 * player who could read their exact blood level would never be surprised by
 * one, and being surprised by one is the entire point of hiding it (spec §42).
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

		int centerX() {
			return x + w / 2;
		}

		int centerY() {
			return y + h / 2;
		}
	}

	/**
	 * Where each region was last drawn, and which one the cursor was over.
	 * Recorded so a client gametest can drive a real cursor onto a real region
	 * and check the hover resolved — the hover path shipped in 1.0 untested
	 * because it was believed to be untestable.
	 */
	private static final java.util.Map<BodyPart, int[]> lastCenters =
			new java.util.EnumMap<>(BodyPart.class);
	@org.jspecify.annotations.Nullable
	private static BodyPart lastHovered;
	private static List<String> lastDetailKeys = List.of();

	/** Screen position of a region as of the last frame, or null if not drawn. */
	@org.jspecify.annotations.Nullable
	public static int[] regionCenter(BodyPart part) {
		return lastCenters.get(part);
	}

	/** Which region the cursor was over as of the last frame. */
	@org.jspecify.annotations.Nullable
	public static BodyPart hoveredPart() {
		return lastHovered;
	}

	/**
	 * Every translation key the hover box put on screen last frame, in the order
	 * it printed them; empty when no box was drawn (1.6).
	 *
	 * <p>The detail box is the only place the mod ever explains an injury in
	 * words, and a screenshot cannot tell a client gametest which words those
	 * were — nor, more importantly, which ones were absent. Keys rather than
	 * rendered text because the assertion is about the branch the panel took, not
	 * about the English somebody wrote for it.
	 */
	public static List<String> lastDetailKeys() {
		return lastDetailKeys;
	}

	/**
	 * Nothing was drawn this frame, so the recorded positions describe a panel
	 * that is no longer on screen. Held stale, they would have a caller believe
	 * a region is still under the cursor after the panel was hidden.
	 */
	private static void forget() {
		lastCenters.clear();
		lastHovered = null;
		lastDetailKeys = List.of();
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
			forget();
			return;
		}
		BodySnapshot snapshot = ClientState.snapshot();
		if (snapshot == null) {
			forget();
			return;
		}

		List<VitalLine> vitals = ClientEffectsConfig.get().showNumericStats
				? vitalLines(snapshot) : List.of();

		Font font = Minecraft.getInstance().font;
		int bodyW = TORSO_W + 2 * (SEG + PAD);
		int bodyH = HEAD_H + PAD + TORSO_H + PAD + LEG_H;
		int textW = 0;
		for (VitalLine line : vitals) {
			textW = Math.max(textW, font.width(line.text()));
		}
		// The vitals lines are far wider than the body. Grow the panel leftward
		// into empty screen rather than sizing the panel to the text and
		// positioning from that: doing the latter shoved the body sideways the
		// moment numbers were switched on, and slid the whole panel under the
		// inventory window at narrower GUI widths.
		int overhang = Math.max(0, textW - bodyW);
		int panelH = bodyH + (vitals.isEmpty() ? 0 : 6 + vitals.size() * 10);
		int guiLeft = (screen.width - GUI_WIDTH) / 2;
		// Sit just left of the inventory, or hug the screen edge if it will not
		// fit. Overlapping the inventory is the one outcome worth avoiding at any
		// width, so that bound wins over keeping the whole text on screen; below
		// about 356 scaled pixels the panel is simply clipped at the left edge.
		int bodyX = Math.min(guiLeft - bodyW - 8,
				Math.max(10 + overhang, guiLeft - bodyW - 26));
		int panelY = (screen.height - GUI_HEIGHT) / 2 + 6;
		int panelLeft = Math.max(0, bodyX - overhang - 6);
		int panelRight = bodyX + bodyW + 6;

		graphics.fill(panelLeft, panelY - 6, panelRight, panelY + panelH + 6, PANEL_BG);
		graphics.fill(panelLeft, panelY - 6, panelRight, panelY - 5, PANEL_EDGE);
		graphics.fill(panelLeft, panelY + panelH + 5, panelRight, panelY + panelH + 6, PANEL_EDGE);

		for (int i = 0; i < vitals.size(); i++) {
			VitalLine line = vitals.get(i);
			graphics.text(font, line.text(), panelLeft + 6, panelY + bodyH + 6 + i * 10,
					line.color(), true);
		}

		List<Region> regions = layout(bodyX, panelY);
		Region hovered = null;
		for (Region region : regions) {
			drawRegion(graphics, snapshot, region, region.contains(mouseX, mouseY));
			lastCenters.put(region.part(), new int[] {region.centerX(), region.centerY()});
			if (region.contains(mouseX, mouseY)) {
				hovered = region;
			}
		}
		lastHovered = hovered == null ? null : hovered.part();
		if (hovered == null) {
			// No box on screen, so there are no lines to report. Left standing they
			// would describe a region the cursor has already left.
			lastDetailKeys = List.of();
		} else {
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
		// Over the drain, so the two edges can be compared at a glance: at a
		// ceiling they land on the same row and the region reads "this is as far
		// back as it comes" with no word for it. Nothing is capped on a region
		// that is gone — a destroyed part sends no band at all.
		if (!snapshot.destroyed(region.part())) {
			BodyHud.drawCeilingCap(graphics, region.x(), region.y(), region.w(), region.h(),
					snapshot.ceilingBand(region.part()), 0xFF000000);
		}
		if (snapshot.destroyed(region.part())) {
			StatusIcons.draw(graphics, StatusIcons.DESTROYED,
					region.x() + region.w() / 2 - StatusIcons.SIZE / 2,
					region.y() + region.h() / 2 - StatusIcons.SIZE / 2, 0xFF000000);
		}
	}

	/** One line of the vitals block: dim for the label, bright for the reading. */
	private record VitalLine(Component text, int color) {}

	private static final int VITAL_LABEL = 0xFF8A8A8A;
	private static final int VITAL_VALUE = 0xFFDDDDDD;

	/**
	 * The two hidden meters, named but never numbered. Only rendered when the
	 * player has opted into numbers at all.
	 *
	 * <p>Label above reading rather than "Blood: significant loss" on one line.
	 * Minecraft's automatic GUI scale keeps the scaled width near 320-450 however
	 * large the window is, so there is never much room left of the inventory: a
	 * single-line form was wider than the body and dragged the whole panel
	 * sideways to make space for itself.
	 */
	private static List<VitalLine> vitalLines(BodySnapshot snapshot) {
		return List.of(
				new VitalLine(Component.translatable("realisticdamage.vitals.blood"), VITAL_LABEL),
				new VitalLine(Component.translatable("realisticdamage.vitals.blood."
						+ Vitals.bloodBand(snapshot.blood())), VITAL_VALUE),
				new VitalLine(Component.translatable("realisticdamage.vitals.consciousness"), VITAL_LABEL),
				new VitalLine(Component.translatable("realisticdamage.vitals.consciousness."
						+ Vitals.consciousnessBand(snapshot.consciousness(), snapshot.unconscious())),
						VITAL_VALUE));
	}

	private static void drawDetail(GuiGraphicsExtractor graphics, BodySnapshot snapshot, BodyPart part,
			int mouseX, int mouseY, int screenWidth) {
		Font font = Minecraft.getInstance().font;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("realisticdamage.part." + part.id()));
		lines.add(Component.translatable("realisticdamage.state."
				+ snapshot.state(part).name().toLowerCase(Locale.ROOT)));
		// Directly under the state word, because it qualifies it: "Injured", and
		// this is as far as it will ever come back. A band, never a figure — the
		// ceiling is the one quantity the mod refuses to number even here, where
		// condition itself is allowed one. /rdmg info remains the exact channel.
		int band = snapshot.ceilingBand(part);
		if (band > 0) {
			lines.add(Component.translatable("realisticdamage.detail.ceiling."
					+ snapshot.ceilingState(part).name().toLowerCase(Locale.ROOT)));
		}
		if (ClientEffectsConfig.get().showNumericStats && !snapshot.destroyed(part)) {
			// Derived from a snapshot quantized to 1%, so one decimal is as much
			// precision as actually exists. /rdmg info remains the exact source.
			double max = part.maxCondition();
			lines.add(Component.translatable("realisticdamage.detail.condition",
					String.format(Locale.ROOT, "%.1f", snapshot.fraction(part) * max),
					String.format(Locale.ROOT, "%.0f", max),
					Math.round(snapshot.fraction(part) * 100.0f)));
		}
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

		List<String> keys = new ArrayList<>(lines.size());
		for (Component line : lines) {
			if (line.getContents() instanceof TranslatableContents translatable) {
				keys.add(translatable.getKey());
			}
		}
		lastDetailKeys = List.copyOf(keys);

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
