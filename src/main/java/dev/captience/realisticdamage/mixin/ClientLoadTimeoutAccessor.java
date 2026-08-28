package dev.captience.realisticdamage.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the practice dummy declare its non-existent client already loaded.
 *
 * <p>Vanilla starts a 60-tick grace when a connection is created and treats the
 * player as invulnerable until it elapses or the client reports in, which
 * protects a real player whose world is still loading. A dummy has no client to
 * report in, so it would spend its first three seconds ignoring every hit — the
 * one thing a practice target must never do.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ClientLoadTimeoutAccessor {
	@Accessor("clientLoadedTimeoutTimer")
	void realisticdamage$setClientLoadedTimeout(int ticks);
}
