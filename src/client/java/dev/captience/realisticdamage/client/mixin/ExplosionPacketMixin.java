package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.BlastShock;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every explosion the client hears about, judged for what it does to the ears
 * (1.7). At the tail, so vanilla has already started the bang: the deafness
 * lands a moment after it, not on top of it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ExplosionPacketMixin {
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void realisticdamage$blastShock(ClientboundExplodePacket packet, CallbackInfo ci) {
		BlastShock.onExplosion(packet.center(), packet.radius());
	}
}
