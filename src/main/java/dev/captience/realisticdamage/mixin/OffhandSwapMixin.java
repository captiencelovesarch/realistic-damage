package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With a destroyed off-hand arm you can no longer put anything in that hand
 * (Arcade preset). Blocking the swap server-side keeps it authoritative.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class OffhandSwapMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$blockOffhandSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
				|| !ServerConfig.get().enableLimbDestruction) {
			return;
		}
		if (ImpairmentSystem.handDisabled(this.player, net.minecraft.world.InteractionHand.OFF_HAND)) {
			Notifier.message(this.player, "realisticdamage.msg.offhand_gone");
			ci.cancel();
		}
	}
}
