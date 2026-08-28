package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unconscious players lie prone using the vanilla swimming/crawling pose
 * (spec §26). Server-authoritative state; clients (own and remote) read the
 * synced snapshot attachment so every side computes the same desired pose.
 * Movement/interaction locks live elsewhere — this is presentation + hitbox.
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
	@Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$unconsciousProne(CallbackInfoReturnable<Pose> cir) {
		Player self = (Player) (Object) this;
		boolean unconscious;
		if (self instanceof ServerPlayer serverPlayer) {
			unconscious = RDAttachments.get(serverPlayer).unconscious();
		} else {
			BodySnapshot snapshot = self.getAttached(RDAttachments.SNAPSHOT);
			unconscious = snapshot != null && snapshot.unconscious();
		}
		if (unconscious) {
			cir.setReturnValue(Pose.SWIMMING);
		}
	}
}
