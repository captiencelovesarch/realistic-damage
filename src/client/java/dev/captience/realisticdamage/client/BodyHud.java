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
			0xFF4CAF50, 0xFFCDDC39, 0xFFFF9800, 0xFFE53935, 0xFF4A4A4A
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

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientEffectsConfig.get().showBodyHud || !ClientState.active()) {
			return;
		}
		BodySnapshot snapshot = ClientState.snapshot();
		if (snapshot == null) {
			return;
		}
		boolean anythingWrong = ClientEffectsConfig.get().bodyHudAlwaysVisible || isHurt(snapshot);
		visibility += ((anythingWrong ? 1.0 : 0.0) - visibility)
				* Math.min(1.0, deltaTracker.getGameTimeDeltaTicks() * 0.2);
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

		// Worst status caption, so the player always knows the broad reason (spec §58).
		Component caption = caption(snapshot);
		if (caption != null && visibility > 0.5) {
			graphics.text(net.minecraft.client.Minecraft.getInstance().font, caption,
					MARGIN, legY + LEG_H + 3, 0x00FFFFFF | alpha, true);
		}
	}

	private static void box(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			BodySnapshot snapshot, BodyPart part, int alpha) {
		BodyState state = snapshot.state(part);
		graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTLINE & (alpha | 0x00FFFFFF));
		graphics.fill(x, y, x + w, y + h, (STATE_COLORS[state.ordinal()] & 0x00FFFFFF) | alpha);
		if (snapshot.broken(part)) {
			// A white fracture line across the segment.
			graphics.fill(x, y + h / 2, x + w, y + h / 2 + 1, (BROKEN_MARK & 0x00FFFFFF) | alpha);
		}
		WoundSeverity wound = snapshot.wound(part);
		if (wound != null) {
			// A dark red pip whose height tracks severity.
			int pip = 1 + wound.ordinal();
			graphics.fill(x, y, x + 1, y + Math.min(h, pip), (BLEED_MARK & 0x00FFFFFF) | alpha);
		}
	}

	private static boolean isHurt(BodySnapshot snapshot) {
		if (snapshot.brokenMask() != 0 || snapshot.woundMask() != 0 || snapshot.statusFlags() != 0) {
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
