package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Haemorrhage arrow opens a bleed where it hits (2.0, phase 3), on anything
 * that bleeds: a skeleton shrugs it off, a zombie does not. The part is the
 * one the arrow actually struck, found the way the trauma pipeline finds it,
 * and the wound is an ordinary one, so it is stained, sprayed and drained like
 * any other.
 */
@Mixin(Arrow.class)
abstract class HaemorrhageArrowMixin {
	@Shadow
	private PotionContents getPotionContents() {
		throw new AssertionError();
	}

	@Inject(method = "doPostHurtEffects", at = @At("HEAD"))
	private void realisticdamage$openBleed(LivingEntity mob, CallbackInfo ci) {
		Arrow self = (Arrow) (Object) this;
		if (self.level().isClientSide() || !mob.isAlive() || !Half.WEAPONS.on()
				|| !ServerConfig.get().enableBleeding || !MobAnatomy.participates(mob) || !MobAnatomy.bleeds(mob)) {
			return;
		}
		boolean haemorrhage = false;
		for (MobEffectInstance effect : getPotionContents().getAllEffects()) {
			haemorrhage |= effect.is(BrewedEffects.HAEMORRHAGE);
		}
		if (!haemorrhage) {
			return;
		}
		BodyPart part = BodyHitResolver.resolve(mob, mob.damageSources().arrow(self, self.getOwner()));
		if (!MobAnatomy.plan(mob).has(part)) {
			part = BodyPart.TORSO;
		}
		RealisticDamageData data = RDAttachments.get(mob);
		data.addWound(part, Balance.HAEMORRHAGE_ARROW_WOUND);
		BleedingSystem.spray(mob, part, Balance.HAEMORRHAGE_ARROW_WOUND, self.getDeltaMovement());
		MobAnatomy.track(mob);
		RDAttachments.pushSnapshot(mob);
	}
}
