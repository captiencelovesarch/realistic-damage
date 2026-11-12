package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob that is down (knocked off its feet or out cold) has a hitbox lying
 * down, low, where its body is (items.6). It kept its standing box
 * before: a column of air over a body on the floor, that arrows and blows hit
 * and a hand reaching for the body missed.
 *
 * <p>Players already lie in vanilla's crawling pose ({@code PlayerPoseMixin}),
 * and keep its box; they get only the longer pick ({@code LyingPickMixin}), so
 * a rescuer's hand finds a downed friend's body where it lies. Each side decides from what it knows, the server from the body and a client
 * from the synced snapshot, the way that pose does, and the box is rebuilt the
 * tick that changes.
 */
@Mixin(LivingEntity.class)
public abstract class DownedHitboxMixin implements dev.captience.realisticdamage.util.LyingBody {
	@Unique
	private boolean realisticdamage$lying;

	@Override
	public boolean realisticdamage$lying() {
		return realisticdamage$lying;
	}

	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
	private void realisticdamage$lyingBox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (realisticdamage$lying && pose != Pose.SLEEPING && !((Object) this instanceof Player)) {
			cir.setReturnValue(lying(cir.getReturnValue()));
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void realisticdamage$watchDown(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		boolean down = down(self);
		if (down != realisticdamage$lying) {
			realisticdamage$lying = down;
			if (!(self instanceof Player)) {
				self.refreshDimensions();
			}
		}
	}

	@Unique
	private static boolean down(LivingEntity self) {
		if (self.isDeadOrDying()) {
			return false;
		}
		if (self.level().isClientSide()) {
			BodySnapshot snapshot = self.getAttached(RDAttachments.SNAPSHOT);
			return snapshot != null && (snapshot.unconscious() || snapshot.knockedDown());
		}
		RealisticDamageData data = self.getAttached(RDAttachments.DATA);
		return data != null && (data.unconscious() || data.knockedDown());
	}

	/**
	 * A standing body's box laid down: a tall one (a zombie, a villager) keeps
	 * its width and drops to a little under it in height (at least 0.45); a
	 * long low one (a pig, a spider) already lies the way it stands. Not
	 * wider: a body knocked down in a one-wide tunnel, or falling limp into a
	 * one-wide pool, must still fit. Its pick radius reaches out along the body
	 * instead ({@code LyingPickMixin}), for the eye and the hand.
	 */
	@Unique
	private static EntityDimensions lying(EntityDimensions standing) {
		float w = standing.width();
		float h = standing.height();
		if (h <= w * 1.2F) {
			return standing;
		}
		float height = Math.min(h, Math.max(0.45F, w * 0.8F));
		return new EntityDimensions(w, height, height * 0.85F,
				standing.attachments().scale(1.0F, height / h, 1.0F), false);
	}
}
