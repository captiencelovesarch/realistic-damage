package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A whiplashed mob turns its head slowly (2.0). Every look a goal asks for
 * comes through this one overload with the turn speeds the goal chose, so
 * scaling them here slows the head whoever asked; the body follows the head.
 */
@Mixin(LookControl.class)
public abstract class MobLookWhiplashMixin {
	@Shadow
	@Final
	protected Mob mob;

	@ModifyVariable(method = "setLookAt(DDDFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float realisticdamage$stiffYaw(float yMaxRotSpeed) {
		return (float) (yMaxRotSpeed * MobAnatomy.turnFactor(this.mob));
	}

	@ModifyVariable(method = "setLookAt(DDDFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float realisticdamage$stiffPitch(float xMaxRotAngle) {
		return (float) (xMaxRotAngle * MobAnatomy.turnFactor(this.mob));
	}
}
