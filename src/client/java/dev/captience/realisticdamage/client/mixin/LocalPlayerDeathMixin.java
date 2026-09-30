package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.DeathCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the dead body in the world (1.7, see {@link DeathCamera}).
 *
 * <p>Vanilla removes the local player from the client level twenty ticks after
 * death, which is what froze the camera. With Death Camera on, the death clock
 * keeps counting (vanilla's tilt and zoom read it, capped at twenty) but the
 * removal never happens; the respawn replaces the entity by id as it always
 * does. The body's movement stays on this client: a dead player sending
 * positions would drag the server's corpse along, portals included.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDeathMixin {
	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$keepBody(CallbackInfo ci) {
		if (DeathCamera.enabled()) {
			((LocalPlayer) (Object) this).deathTime++;
			ci.cancel();
		}
	}

	@Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$corpseStaysLocal(CallbackInfo ci) {
		if (DeathCamera.enabled() && ((LocalPlayer) (Object) this).isDeadOrDying()) {
			ci.cancel();
		}
	}
}
