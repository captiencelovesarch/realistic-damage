package dev.captience.realisticdamage.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.PendingExplosion;
import dev.captience.realisticdamage.body.RealisticDamageData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * What a blast was, recorded on the player it is about to hurt (1.6).
 *
 * <p>By the time the damage event reaches the trauma pipeline the blast has been
 * through difficulty, a shield, armour, Blast Protection and absorption, and the
 * number that arrives describes the health bar rather than the explosion — a
 * creeper against the leg of someone in Blast IV netherite reads as a scratch,
 * and a totem that drags you back from a stick of TNT hands the mod a body that
 * looks merely bruised. Vanilla computes the real figure one expression before
 * it hurts the entity ({@code ExplosionDamageCalculator.getEntityDamageAmount}:
 * radius-aware, exposure-aware, before difficulty and before armour); this wraps
 * that call and keeps the figure for exactly as long as the call lasts.
 *
 * <p>Cleared in a {@code finally} because a refused hit — peaceful, i-frames,
 * creative, invulnerable — never reaches the pipeline at all, and a record left
 * lying around would re-score the next unrelated blow as though the player had
 * been standing on a barrel of TNT. Nested explosions set and clear their own
 * value inside their own call, so there is no static and no stack.
 *
 * <p>No config check here on purpose: the stash is two field writes, and the
 * rules that read it are the pipeline's to decide. A mixin that also decided
 * eligibility would be a second copy of that policy.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin implements Explosion {
	@WrapOperation(method = "hurtEntities",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean realisticdamage$recordBlast(Entity entity, ServerLevel level, DamageSource source,
			float amount, Operation<Boolean> original) {
		if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)
				|| !dev.captience.realisticdamage.systems.MobAnatomy.participates(living) || !(amount > 0.0F)) {
			return original.call(entity, level, source, amount);
		}
		RealisticDamageData data = RDAttachments.get(living);
		Vec3 center = this.center();
		data.setPendingExplosion(new PendingExplosion(amount, center.x, center.y, center.z));
		try {
			return original.call(entity, level, source, amount);
		} finally {
			data.clearPendingExplosion();
		}
	}
}
