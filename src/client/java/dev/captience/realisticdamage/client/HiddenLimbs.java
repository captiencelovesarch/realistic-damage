package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.client.model.geom.ModelPart;

/**
 * What this mod hid on a shared model the last time it was posed (1.7.0-alpha.3).
 *
 * <p>One model instance draws every mob of its type, and vanilla's humanoid,
 * illager and villager models never reset part visibility between bodies —
 * only the player model does. So a leg hidden for one zombie stayed hidden
 * for every zombie drawn after it, which is exactly the playtest report. Each
 * model now remembers the mask it hid, restores those parts at the top of the
 * next pose, and hides afresh for whichever body it is posing now.
 *
 * <p>Only parts that were visible when hidden are recorded, so a part
 * vanilla itself keeps hidden is never switched on by the restore.
 */
public interface HiddenLimbs {
	int realisticdamage$hidden();

	void realisticdamage$setHidden(int mask);

	/** Restores what was hidden last time, then hides {@code destroyed} limbs, recording them. Parts may be null. */
	static void apply(HiddenLimbs model, int destroyed, ModelPart leftArm, ModelPart rightArm,
			ModelPart leftLeg, ModelPart rightLeg) {
		int previous = model.realisticdamage$hidden();
		if (previous != 0) {
			restore(previous, BodyPart.LEFT_ARM, leftArm);
			restore(previous, BodyPart.RIGHT_ARM, rightArm);
			restore(previous, BodyPart.LEFT_LEG, leftLeg);
			restore(previous, BodyPart.RIGHT_LEG, rightLeg);
		}
		int hidden = 0;
		hidden |= hide(destroyed, BodyPart.LEFT_ARM, leftArm);
		hidden |= hide(destroyed, BodyPart.RIGHT_ARM, rightArm);
		hidden |= hide(destroyed, BodyPart.LEFT_LEG, leftLeg);
		hidden |= hide(destroyed, BodyPart.RIGHT_LEG, rightLeg);
		model.realisticdamage$setHidden(hidden);
	}

	private static void restore(int previous, BodyPart part, ModelPart modelPart) {
		if (modelPart != null && LimbStateExtractor.gone(previous, part)) {
			modelPart.visible = true;
		}
	}

	private static int hide(int destroyed, BodyPart part, ModelPart modelPart) {
		if (modelPart == null || !LimbStateExtractor.gone(destroyed, part) || !modelPart.visible) {
			return 0;
		}
		modelPart.visible = false;
		return 1 << part.ordinal();
	}
}
