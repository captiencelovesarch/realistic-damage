package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.net.TreatingPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The patient's body, shown only while you are actually treating them (1.2).
 *
 * <p>Deliberately not a permanent overlay above every injured player. Triage
 * should cost you something — you have to reach them and commit a hand before
 * you learn what is wrong, which is the same bargain the rest of the mod makes
 * about information.
 *
 * <p>Drawn from the target's synced snapshot; 1.0 already broadcast those to
 * every client so remote players could be posed prone, so nothing extra is sent
 * for this beyond a single entity id.
 */
public final class TriageHud {
	private TriageHud() {}

	private static int targetId = TreatingPayload.NOBODY;
	private static double visibility;

	public static void setTarget(int id) {
		targetId = id;
	}

	public static void reset() {
		targetId = TreatingPayload.NOBODY;
		visibility = 0.0;
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientEffectsConfig.get().showBodyHud) {
			return;
		}
		Player patient = resolve();
		double target = patient == null ? 0.0 : 1.0;
		double rate = target > visibility ? 0.6 : 0.25;
		visibility += (target - visibility)
				* Math.min(1.0, deltaTracker.getGameTimeDeltaTicks() * rate);
		if (visibility < 0.02 || patient == null) {
			return;
		}
		BodySnapshot snapshot = patient.getAttached(RDAttachments.SNAPSHOT);
		if (snapshot == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int alpha = (int) (Math.min(1.0, visibility) * 255.0) << 24;
		// Far enough right that the patient's caption clears your own, which can
		// run to about a hundred pixels ("Left Leg: Injured").
		int x = 6 + 5 + 110;
		int y = graphics.guiHeight() / 2 - BodyHud.bodyHeight() / 2;

		int legY = BodyHud.drawBody(graphics, snapshot, x, y, alpha);
		graphics.text(client.font, patient.getName(), x - 5, y - 11, 0x00FFD9D9 | alpha, true);

		// Region colours alone are not triage. A patient can be unconscious and
		// bleeding to death with every region still green, which reads as "fine"
		// at exactly the moment it matters most — so the icons and the caption
		// come along too.
		int iconY = legY + 12;
		if (ClientEffectsConfig.get().showStatusIcons) {
			iconY = BodyHud.drawStatusIcons(graphics, snapshot, x - 5, iconY, alpha);
		}
		Component caption = snapshot.unconscious()
				? Component.translatable("realisticdamage.triage.unconscious")
				: BodyHud.caption(snapshot);
		if (caption != null) {
			graphics.text(client.font, caption, x - 5, iconY + 1, 0x00FFFFFF | alpha, true);
		}
	}

	private static Player resolve() {
		if (targetId == TreatingPayload.NOBODY) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return null;
		}
		Entity entity = client.level.getEntity(targetId);
		return entity instanceof Player player && player != client.player ? player : null;
	}
}
