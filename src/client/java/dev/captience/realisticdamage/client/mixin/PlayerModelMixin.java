package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.DestroyedLimbHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A destroyed limb is gone — hide it on the model, along with its outer layer.
 * Injected after vanilla's own visibility pass so it always wins.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	// leftArm/rightArm/leftLeg/rightLeg are declared on HumanoidModel, not
	// PlayerModel, so they cannot be @Shadow'd here — reach them by cast below.
	@Shadow
	public ModelPart leftSleeve;
	@Shadow
	public ModelPart rightSleeve;
	@Shadow
	public ModelPart leftPants;
	@Shadow
	public ModelPart rightPants;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void realisticdamage$hideDestroyedLimbs(AvatarRenderState state, CallbackInfo ci) {
		int mask = ((DestroyedLimbHolder) state).realisticdamage$destroyedMask();
		if (mask == 0) {
			return;
		}
		HumanoidModel<?> body = (HumanoidModel<?>) (Object) this;
		if (gone(mask, BodyPart.LEFT_ARM)) {
			body.leftArm.visible = false;
			this.leftSleeve.visible = false;
		}
		if (gone(mask, BodyPart.RIGHT_ARM)) {
			body.rightArm.visible = false;
			this.rightSleeve.visible = false;
		}
		if (gone(mask, BodyPart.LEFT_LEG)) {
			body.leftLeg.visible = false;
			this.leftPants.visible = false;
		}
		if (gone(mask, BodyPart.RIGHT_LEG)) {
			body.rightLeg.visible = false;
			this.rightPants.visible = false;
		}
	}

	private static boolean gone(int mask, BodyPart part) {
		return (mask & (1 << part.ordinal())) != 0;
	}
}
