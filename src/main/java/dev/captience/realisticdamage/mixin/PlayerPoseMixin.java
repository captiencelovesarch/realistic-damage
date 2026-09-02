package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players lie prone using the vanilla swimming/crawling pose when unconscious,
 * when knocked off their feet by a hard landing (1.4), or when both legs are
 * destroyed and crawling is all that is left (spec §26). Server-authoritative
 * state; clients (own and remote) read the synced snapshot attachment so every
 * side computes the same desired pose. Movement/interaction locks live
 * elsewhere — this is presentation + hitbox.
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
	@Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$unconsciousProne(CallbackInfoReturnable<Pose> cir) {
		Player self = (Player) (Object) this;
		boolean prone;
		if (self instanceof ServerPlayer serverPlayer) {
			var data = RDAttachments.get(serverPlayer);
			prone = data.unconscious() || data.legless() || data.knockedDown();
		} else {
			BodySnapshot snapshot = self.getAttached(RDAttachments.SNAPSHOT);
			prone = snapshot != null && (snapshot.unconscious() || snapshot.knockedDown()
					|| (snapshot.destroyed(BodyPart.LEFT_LEG) && snapshot.destroyed(BodyPart.RIGHT_LEG)));
		}
		if (prone) {
			cir.setReturnValue(Pose.SWIMMING);
		}
	}
}
