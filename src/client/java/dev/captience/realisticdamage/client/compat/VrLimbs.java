package dev.captience.realisticdamage.client.compat;

import dev.captience.realisticdamage.body.BodyPart;
import org.vivecraft.client.render.VRPlayerModel_WithArms;

/** Lost limbs on Vivecraft's split-limb player models; used by the mixins in {@code compat.mixin}. */
public final class VrLimbs {
	private VrLimbs() {}

	public static void hideLostHands(VRPlayerModel_WithArms model, int destroyedMask) {
		if (gone(destroyedMask, BodyPart.LEFT_ARM)) {
			model.leftArm.visible = false;
			model.leftSleeve.visible = false;
			model.leftHand.visible = false;
			model.leftHandSleeve.visible = false;
		}
		if (gone(destroyedMask, BodyPart.RIGHT_ARM)) {
			model.rightArm.visible = false;
			model.rightSleeve.visible = false;
			model.rightHand.visible = false;
			model.rightHandSleeve.visible = false;
		}
	}

	public static boolean gone(int destroyedMask, BodyPart part) {
		return (destroyedMask & (1 << part.ordinal())) != 0;
	}
}
