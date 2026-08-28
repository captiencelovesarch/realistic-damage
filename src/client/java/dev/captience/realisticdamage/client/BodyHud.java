package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A compact six-region body diagram (spec §42). No numbers — colored segments
 * arranged like a body, plus small injury markers. Fades away entirely when the
 * player is unhurt unless pinned in the config.
 */
public final class BodyHud {
	private BodyHud() {}

	// Colors per state: green, yellow, orange, red, dark grey.
	private static final int[] STATE_COLORS = {
			0xFF4CAF50, 0xFFCDDC39, 0xFFFF9800, 0xFFE53935, 0xFF4A4A4A, 0xFF120000
	};
	private static final int OUTLINE = 0x80000000;
	private static final int BROKEN_MARK = 0xFFFFFFFF;
	private static final int BLEED_MARK = 0xFF8A0303;

	private static final int SEG = 4;      // limb thickness
	private static final int TORSO_W = 8;
	private static final int HEAD_H = 5;
	private static final int TORSO_H = 9;
	private static final int LEG_H = 8;
	private static final int PAD = 1;
	private static final int MARGIN = 6;

	private static double visibility;

	/** Shared with the inventory inspector so both read the same palette. */
	public static int stateColor(BodyState state) {
		return STATE_COLORS[state.ordinal()];
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientEffectsConfig.get().showBodyHud || !ClientState.active()) {
			return;
		}
		BodySnapshot snapshot = ClientState.snapshot();
		if (snapshot == null) {
			return;
		}
		boolean anythingWrong = ClientEffectsConfig.get().bodyHudAlwaysVisible || isHurt(snapshot);
		double target = anythingWrong ? 1.0 : 0.0;
		// Snap in when something goes wrong; drift out once it is over.
		double rate = target > visibility ? 0.7 : 0.12;
		visibility += (target - visibility)
				* Math.min(1.0, deltaTracker.getGameTimeDeltaTicks() * rate);
		if (visibility < 0.02) {
			return;
		}

		int totalW = TORSO_W + 2 * (SEG + PAD);
		int x = MARGIN + (SEG + PAD);           // left edge of the torso column
		int y = graphics.guiHeight() / 2 - (HEAD_H + TORSO_H + LEG_H) / 2;
		int alpha = (int) (Math.min(1.0, visibility) * 255.0) << 24;

		// Head
		box(graphics, x + 1, y, TORSO_W - 2, HEAD_H, snapshot, BodyPart.HEAD, alpha);
		int torsoY = y + HEAD_H + PAD;
		// Arms flank the torso; screen-left is the player's right arm (mirror view).
		box(graphics, x - SEG - PAD, torsoY, SEG, TORSO_H, snapshot, BodyPart.RIGHT_ARM, alpha);
		box(graphics, x, torsoY, TORSO_W, TORSO_H, snapshot, BodyPart.TORSO, alpha);
		box(graphics, x + TORSO_W + PAD, torsoY, SEG, TORSO_H, snapshot, BodyPart.LEFT_ARM, alpha);
		int legY = torsoY + TORSO_H + PAD;
		box(graphics, x, legY, SEG, LEG_H, snapshot, BodyPart.RIGHT_LEG, alpha);
		box(graphics, x + TORSO_W - SEG, legY, SEG, LEG_H, snapshot, BodyPart.LEFT_LEG, alpha);

		// Blood gauge: a column beside the body that drains as you bleed, so you
		// can see bleed-out coming instead of being surprised by it. Still no
		// numbers — it is a level, not a readout.
		int gaugeX = x + TORSO_W + PAD + SEG + 3;
		int gaugeTop = y;
		int gaugeHeight = (HEAD_H + PAD + TORSO_H + PAD + LEG_H);
		drawBloodGauge(graphics, snapshot, gaugeX, gaugeTop, gaugeHeight, alpha);

		int iconY = legY + LEG_H + 4;
		if (ClientEffectsConfig.get().showStatusIcons) {
			iconY = drawStatusIcons(graphics, snapshot, MARGIN, iconY, alpha);
		}

		// Worst status caption, so the player always knows the broad reason (spec §58).
		Component caption = caption(snapshot);
		if (caption != null && visibility > 0.5) {
			graphics.text(net.minecraft.client.Minecraft.getInstance().font, caption,
					MARGIN, iconY + 1, 0x00FFFFFF | alpha, true);
		}
	}

	/** Game time in ticks, for the bleed throb. */
	private static double pulseTime() {
		var level = net.minecraft.client.Minecraft.getInstance().level;
		return level == null ? 0.0 : level.getGameTime() % 100000L;
	}

	private static void drawBloodGauge(GuiGraphicsExtractor graphics, BodySnapshot snapshot,
			int x, int y, int height, int alpha) {
		float blood = snapshot.blood();
		if (blood >= 99.5f) {
			return;   // full: nothing to warn about
		}
		int filled = (int) Math.round(height * Math.clamp(blood / 100.0f, 0.0f, 1.0f));
		graphics.fill(x - 1, y - 1, x + 4, y + height + 1, OUTLINE & (alpha | 0x00FFFFFF));
		graphics.fill(x, y, x + 3, y + height, 0x00140404 | alpha);
		// Deepens toward black-red as it empties; flashes once it is genuinely dire.
		int color = blood < 30.0f ? 0x00FF2B2B : (blood < 50.0f ? 0x00C1121F : 0x008A0303);
		int fillAlpha = alpha;
		if (blood < 30.0f) {
			double flash = 0.55 + 0.45 * Math.sin(pulseTime() / 3.0);
			fillAlpha = (int) (Math.min(1.0, visibility) * flash * 255.0) << 24;
		}
		graphics.fill(x, y + (height - filled), x + 3, y + height, color | fillAlpha);
	}

	/** Draws one icon per active status; returns the y below the row. */
	private static int drawStatusIcons(GuiGraphicsExtractor graphics, BodySnapshot snapshot, int x, int y, int alpha) {
		int cursor = x;
		boolean any = false;
		if (snapshot.woundMask() != 0) {
			StatusIcons.draw(graphics, StatusIcons.BLEED, cursor, y, alpha);
			cursor += StatusIcons.SIZE + 2;
			any = true;
		}
		if (snapshot.brokenMask() != 0) {
			StatusIcons.draw(graphics, StatusIcons.BROKEN, cursor, y, alpha);
			cursor += StatusIcons.SIZE + 2;
			any = true;
		}
		if (snapshot.destroyedMask() != 0) {
			StatusIcons.draw(graphics, StatusIcons.DESTROYED, cursor, y, alpha);
			cursor += StatusIcons.SIZE + 2;
			any = true;
		}
		if (snapshot.concussion()) {
			StatusIcons.draw(graphics, StatusIcons.CONCUSSION, cursor, y, alpha);
			cursor += StatusIcons.SIZE + 2;
			any = true;
		}
		if (snapshot.burned()) {
			StatusIcons.draw(graphics, StatusIcons.BURNED, cursor, y, alpha);
			any = true;
		}
		return any ? y + StatusIcons.SIZE + 2 : y;
	}

	private static void box(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			BodySnapshot snapshot, BodyPart part, int alpha) {
		BodyState state = snapshot.state(part);
		graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTLINE & (alpha | 0x00FFFFFF));
		graphics.fill(x, y, x + w, y + h, (STATE_COLORS[state.ordinal()] & 0x00FFFFFF) | alpha);
		if (snapshot.destroyed(part)) {
			// Gone: a hollow outline, no fill worth marking.
			graphics.fill(x, y + h / 2, x + w, y + h / 2 + 1, (BLEED_MARK & 0x00FFFFFF) | alpha);
			graphics.fill(x + w / 2, y, x + w / 2 + 1, y + h, (BLEED_MARK & 0x00FFFFFF) | alpha);
		} else if (snapshot.broken(part)) {
			// A white fracture line across the segment.
			graphics.fill(x, y + h / 2, x + w, y + h / 2 + 1, (BROKEN_MARK & 0x00FFFFFF) | alpha);
		}
		WoundSeverity wound = snapshot.wound(part);
		if (wound != null) {
			// A pulsing red ring around the whole region — a one-pixel pip was far
			// too easy to miss, which is the point of showing bleeding at all.
			double throb = 0.55 + 0.45 * Math.sin(pulseTime() / (7.0 - wound.ordinal()));
			int woundAlpha = (int) (Math.min(1.0, visibility) * throb * 255.0) << 24;
			int ring = (StatusIcons.BLEED.mid() & 0x00FFFFFF) | woundAlpha;
			graphics.fill(x - 1, y - 1, x + w + 1, y, ring);
			graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, ring);
			graphics.fill(x - 1, y, x, y + h, ring);
			graphics.fill(x + w, y, x + w + 1, y + h, ring);
		}
	}

	private static boolean isHurt(BodySnapshot snapshot) {
		if (snapshot.brokenMask() != 0 || snapshot.woundMask() != 0 || snapshot.statusFlags() != 0
				|| snapshot.destroyedMask() != 0 || snapshot.blood() < 99.5f) {
			return true;
		}
		for (BodyPart part : BodyPart.values()) {
			if (snapshot.state(part) != BodyState.HEALTHY) {
				return true;
			}
		}
		return false;
	}

	/** The single most important thing wrong right now, or null. */
	private static Component caption(BodySnapshot snapshot) {
		BodyPart worstPart = null;
		BodyState worstState = BodyState.HEALTHY;
		for (BodyPart part : BodyPart.values()) {
			BodyState state = snapshot.state(part);
			if (state.isAtLeastAsBadAs(BodyState.HURT) && state.ordinal() > worstState.ordinal()) {
				worstState = state;
				worstPart = part;
			}
		}
		if (snapshot.concussion()) {
			return Component.translatable("realisticdamage.hud.concussion");
		}
		if (worstPart == null) {
			return snapshot.burned() ? Component.translatable("realisticdamage.hud.burned") : null;
		}
		Component partName = Component.translatable("realisticdamage.part." + worstPart.id());
		Component stateName = Component.translatable(
				"realisticdamage.state." + worstState.name().toLowerCase(java.util.Locale.ROOT));
		return Component.translatable("realisticdamage.hud.line", partName, stateName);
	}
}
