package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.HiddenLimbs;
import dev.captience.realisticdamage.client.InjuryPose;
import dev.captience.realisticdamage.client.LimbStateExtractor;
import dev.captience.realisticdamage.client.LimbStateHolder;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Illagers lose limbs and limp too (1.7). Vanilla shows either the crossed
 * {@code arms} piece or the two separate arms depending on the pose; with an
 * arm gone the crossed piece is retired and the surviving arm shown on its
 * own, since half of a crossed pair is not a thing the model can draw.
 */
@Mixin(IllagerModel.class)
public abstract class IllagerModelMixin implements HiddenLimbs {
	@Unique
	private int realisticdamage$hiddenLimbs;

	@Override
	public int realisticdamage$hidden() {
		return this.realisticdamage$hiddenLimbs;
	}

	@Override
	public void realisticdamage$setHidden(int mask) {
		this.realisticdamage$hiddenLimbs = mask;
	}

	@Shadow
	@Final
	private ModelPart arms;
	@Shadow
	@Final
	private ModelPart leftLeg;
	@Shadow
	@Final
	private ModelPart rightLeg;
	@Shadow
	@Final
	private ModelPart rightArm;
	@Shadow
	@Final
	private ModelPart leftArm;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;)V", at = @At("TAIL"))
	private void realisticdamage$body(IllagerRenderState state, CallbackInfo ci) {
		LimbStateHolder holder = (LimbStateHolder) state;
		int destroyed = holder.realisticdamage$destroyedMask();
		boolean leftGone = LimbStateExtractor.gone(destroyed, BodyPart.LEFT_ARM);
		boolean rightGone = LimbStateExtractor.gone(destroyed, BodyPart.RIGHT_ARM);
		if ((leftGone || rightGone) && this.arms.visible) {
			this.arms.visible = false;
			this.leftArm.visible = true;
			this.rightArm.visible = true;
			this.leftArm.xRot = 0.0F;
			this.rightArm.xRot = 0.0F;
			this.leftArm.zRot = -0.1F;
			this.rightArm.zRot = 0.1F;
		}
		// Arms are re-shown by vanilla every pose; legs are not, so the mask
		// remembers what was hidden and restores it for the next body.
		HiddenLimbs.apply(this, destroyed, ((net.minecraft.client.model.monster.illager.IllagerModel<?>) (Object) this).getHead(),
				this.leftArm, this.rightArm, this.leftLeg, this.rightLeg);
		InjuryPose.applyParts(holder, this.arms.visible ? null : this.leftArm, this.arms.visible ? null : this.rightArm,
				this.leftLeg, this.rightLeg, state.isRiding ? 0.0F : 1.0F);
	}
}
