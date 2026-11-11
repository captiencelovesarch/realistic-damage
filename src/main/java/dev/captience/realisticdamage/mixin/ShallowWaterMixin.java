package dev.captience.realisticdamage.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.captience.realisticdamage.damage.ShallowWater;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two places vanilla forgets a fall because of water (2.0), seen just
 * before it does, with the fall still on the body (see {@link ShallowWater}).
 *
 * <ul>
 * <li>Touching water at all: {@code updateFluidInteraction}, on the tick the
 * body first touches it. Every body, players included, comes through here.</li>
 * <li>Falling fast enough to pass through a whole block in one tick:
 * {@code move} looks ahead along the motion for water and forgets the fall if
 * it finds any, before the body has even arrived.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class ShallowWaterMixin {
	@Shadow
	protected boolean wasTouchingWater;
	@Shadow
	protected boolean firstTick;
	@Shadow
	public double fallDistance;

	@Inject(method = "updateFluidInteraction",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;resetFallDistance()V"))
	private void realisticdamage$touchedWater(CallbackInfoReturnable<Boolean> cir) {
		if (!this.wasTouchingWater && !this.firstTick && this.fallDistance > 0.0
				&& (Object) this instanceof LivingEntity body) {
			ShallowWater.entered(body, this.fallDistance, body.blockPosition());
		}
	}

	@ModifyExpressionValue(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
	private BlockHitResult realisticdamage$fallingIntoWater(BlockHitResult hit) {
		if (hit.getType() == HitResult.Type.BLOCK && this.fallDistance > 0.0
				&& (Object) this instanceof LivingEntity body
				&& body.level().getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)) {
			ShallowWater.entered(body, this.fallDistance, hit.getBlockPos());
		}
		return hit;
	}
}
