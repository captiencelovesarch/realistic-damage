package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.HealingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes every real vanilla heal (natural regeneration, Regeneration, Instant
 * Health, /heal) and converts it into body recovery (spec §37, §39). Fabric has
 * no heal event, so this is the narrowest hook available.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
	@Inject(method = "heal", at = @At("HEAD"))
	private void realisticdamage$observeHeal(float heal, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player)) {
			// A mob vanilla heals mends by the same amount (1.7 slice 3): a fed
			// wolf, a horse given an apple, an iron golem patched with iron. Food
			// and time are the cure for them too.
			LivingEntity self = (LivingEntity) (Object) this;
			if (self.getHealth() > 0.0F && self.hasAttached(dev.captience.realisticdamage.RDAttachments.DATA)
					&& dev.captience.realisticdamage.systems.MobAnatomy.isMobWithBody(self)) {
				float effective = Math.min(heal, self.getMaxHealth() - self.getHealth());
				if (effective > 0.0F) {
					HealingSystem.onMobHeal(self, effective);
				}
			}
			return;
		}
		// Mirror vanilla's own clamping so overheal never inflates the body budget.
		float health = player.getHealth();
		if (health <= 0.0F) {
			return;
		}
		float effective = Math.min(heal, player.getMaxHealth() - health);
		if (effective > 0.0F) {
			HealingSystem.onVanillaHeal(player, effective);
		}
	}
}
