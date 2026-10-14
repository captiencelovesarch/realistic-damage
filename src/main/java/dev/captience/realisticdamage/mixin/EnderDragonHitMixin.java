package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.damage.DragonHits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers which dragon part a blow went through, for the hit resolver (1.7 slice 3). */
@Mixin(EnderDragon.class)
public abstract class EnderDragonHitMixin {
	@Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"))
	private void realisticdamage$whichPart(ServerLevel level, EnderDragonPart part, DamageSource source, float damage,
			CallbackInfoReturnable<Boolean> cir) {
		DragonHits.stash((EnderDragon) (Object) this, part);
	}
}
