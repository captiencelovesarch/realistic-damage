package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.InjuryPose;
import dev.captience.realisticdamage.client.LimbStateExtractor;
import dev.captience.realisticdamage.client.LimbStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The last word on a player's limb rotations, so an injured limb is carried
 * like one.
 *
 * <p>Injected here rather than into {@code PlayerModel} because the armour
 * layer renders a <em>separate</em> {@code HumanoidModel} and hands it the same
 * render state, so a pose applied only to the player's own model would rotate
 * the arm and leave its sleeve behind. At the cradle's 17° a 12-unit arm
 * displaces about 3.5 units, far outside armour's 1.0 inflation, and the limb
 * would poke straight through the plate. One injection point catches both.
 *
 * <p>{@code PlayerModel.setupAnim} calls {@code super} <em>last</em>, so this
 * tail is the final word before the model is submitted; nothing downstream
 * writes a limb rotation again. The one thing that does run after us is
 * {@code PlayerModelMixin}, which hides destroyed limbs — which is why the pose
 * reads the destroyed rung off the synced mask rather than off
 * {@code part.visible}, a flag that is still vanilla's at this point.
 *
 * <p>Since 1.7 this deliberately <em>does</em> touch mob rendering: every
 * humanoid render state carries the limb holders, and a mob with a body limps
 * and loses limbs on its model exactly as a player does. A state with no body
 * carries zero masks and costs two reads.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	// The erased descriptor is explicit because HumanoidModel<T extends
	// HumanoidRenderState> generates a bridge method, and an unqualified name
	// would be ambiguous between the two.
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
			at = @At("TAIL"))
	private void realisticdamage$carryInjuredLimbs(HumanoidRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		InjuryPose.apply(state, model);
		// A destroyed limb is gone on every humanoid (1.7). The player model
		// hides its sleeves and pants as well in PlayerModelMixin, which runs
		// after this; here is the base part, which is all a mob has.
		int destroyed = ((LimbStateHolder) state).realisticdamage$destroyedMask();
		if (destroyed != 0) {
			if (LimbStateExtractor.gone(destroyed, BodyPart.LEFT_ARM)) {
				model.leftArm.visible = false;
			}
			if (LimbStateExtractor.gone(destroyed, BodyPart.RIGHT_ARM)) {
				model.rightArm.visible = false;
			}
			if (LimbStateExtractor.gone(destroyed, BodyPart.LEFT_LEG)) {
				model.leftLeg.visible = false;
			}
			if (LimbStateExtractor.gone(destroyed, BodyPart.RIGHT_LEG)) {
				model.rightLeg.visible = false;
			}
		}
	}
}
