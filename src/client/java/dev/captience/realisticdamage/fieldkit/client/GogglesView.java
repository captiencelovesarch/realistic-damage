package dev.captience.realisticdamage.fieldkit.client;

import dev.captience.realisticdamage.client.Lens;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.medicine.Goggles;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The view through tinted goggles (2.0, phase 4 round 2): the world a shade
 * darker and a touch violet, as through tinted glass, and darker still at the
 * rims. First person only, like vanilla's pumpkin. Drawn through the core's
 * lens, so a VR headset gets it in each eye.
 */
public final class GogglesView {
	private GogglesView() {}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !client.options.getCameraType().isFirstPerson() || !Goggles.worn(client.player)) {
			return;
		}
		int tint = (Balance.GOGGLES_TINT_ALPHA << 24) | 0x1A1024;
		Lens.fill(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), tint);
		Lens.vignette(graphics, 0.55F, 0.6F, 0.5F);
	}
}
