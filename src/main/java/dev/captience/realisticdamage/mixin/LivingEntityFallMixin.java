package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.FallMath;
import dev.captience.realisticdamage.damage.LandingSurface;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two things a landing needs that the damage event alone cannot tell us
 * (1.4): how far the player — or, since 1.7, the mob — actually fell, and what they hit.
 *
 * <p>By the time the trauma pipeline sees a fall it has only a post-armour
 * health figure, and that figure is a poor description of the accident — it is
 * what vanilla decided the boots were worth. Diamond armour with Feather
 * Falling IV turns a forty-block drop into a scratch, so the legs it should
 * have shattered were never touched and the head impact never happened at all.
 * Recording the height here, on the tick the ground arrives, lets the pipeline
 * score the fall on what the skeleton experienced.
 *
 * <p>The same hook compresses vanilla's lethal tail, because height is only
 * worth measuring if surviving is on the table.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallMixin {
	@Inject(method = "causeFallDamage", at = @At("HEAD"))
	private void realisticdamage$recordLanding(double fallDistance, float damageModifier,
			DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!MobAnatomy.participates(self)) {
			return;
		}
		double safe = self.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		double overSafe = fallDistance - safe;
		if (overSafe <= 0.0) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(self);
		double surface = LandingSurface.factor(self.getBlockStateOn());
		data.setPendingFall(overSafe, surface, leadingLegIsLeft(self));
		// Head first or feet first (2.0): a dive into shallow water says which
		// itself, having looked before the water stopped the glide.
		data.setPendingFallHeadFirst(data.diving()
				? data.divingHeadFirst()
				: dev.captience.realisticdamage.damage.BrokenNeck.headFirst(self, data));
	}

	/**
	 * Which foot was forward when the ground arrived. Read off the walk cycle
	 * rather than chosen at random: it is the leg that was actually leading, it
	 * is the same answer on client and server, and replaying the same fall
	 * therefore breaks the same ankle — which is the whole no-RNG promise applied
	 * to a place where a coin flip would have been the easy option.
	 *
	 * <p>Standing perfectly still leaves the animation parked, so the dominant
	 * side stands in: you land on the leg you favour.
	 */
	private static boolean leadingLegIsLeft(LivingEntity self) {
		double phase = self.walkAnimation.position();
		if (phase <= 0.0) {
			return self.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
		}
		return Math.sin(phase) >= 0.0;
	}

	/**
	 * Vanilla fall damage is linear, so lethality arrives at a cliff edge: 23
	 * blocks is exactly twenty points and exactly death, and one block less is a
	 * walk-away. Compressing the tail (spec 1.4) trades that for consequence —
	 * you live through drops that used to kill you, and you live through them
	 * concussed with a shattered leg. Short falls are untouched; they are
	 * vanilla's business and meddling with them would be felt everywhere.
	 */
	@Inject(method = "calculateFallDamage", at = @At("RETURN"), cancellable = true)
	private void realisticdamage$softenLethalFalls(double fallDistance, float damageModifier,
			CallbackInfoReturnable<Integer> cir) {
		if (!MobAnatomy.participates((LivingEntity) (Object) this)) {
			return;
		}
		if (!ServerConfig.get().softenFallLethality) {
			return;
		}
		int vanilla = cir.getReturnValue();
		int softened = FallMath.compressLethality(vanilla);
		if (softened != vanilla) {
			cir.setReturnValue(softened);
		}
	}

	/**
	 * A fall that ends up dealing no damage at all still has to drop its record,
	 * or the height sits in the attachment waiting to re-score whatever hits the
	 * player next as though they had come off a cliff.
	 */
	@Inject(method = "causeFallDamage", at = @At("RETURN"))
	private void realisticdamage$dropStaleLanding(double fallDistance, float damageModifier,
			DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (MobAnatomy.participates(self)) {
			RDAttachments.get(self).clearPendingFall();
		}
	}
}
