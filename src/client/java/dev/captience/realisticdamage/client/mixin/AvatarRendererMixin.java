package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.client.BloodBody;
import dev.captience.realisticdamage.client.BloodStainHolder;
import dev.captience.realisticdamage.client.LimbStateHolder;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies the synced destroyed-limb and broken-limb masks, the accumulated blood
 * stains and the synced scar mask onto the render state each frame.
 *
 * <p>Scars come off the snapshot rather than out of a client-side accumulator
 * the way blood does, and deliberately: entity ids are reused across a respawn
 * on both sides, so anything keyed by id would hand a fresh body the previous
 * one's marks. The server already carries the history through death; the client
 * only has to read it.
 *
 * <p>The broken mask has been on the wire since 1.0 ({@code BodySnapshot} sends
 * it to every tracking client) and was simply never read on this side. Reading
 * it costs one field: there is no packet, save or protocol change here, and a
 * 1.5 client and a 1.6 client see the same bytes.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$carryDestroyedLimbs(Avatar entity, AvatarRenderState state, float partialTicks,
			CallbackInfo ci) {
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		((LimbStateHolder) state).realisticdamage$setDestroyedMask(
				snapshot == null ? 0 : snapshot.destroyedMask());
		((LimbStateHolder) state).realisticdamage$setBrokenMask(
				snapshot == null ? 0 : snapshot.brokenMask());
		((BloodStainHolder) state).realisticdamage$setBloodStains(BloodBody.stains(entity.getId()));
		((BloodStainHolder) state).realisticdamage$setScarMask(
				snapshot == null ? 0 : snapshot.scarMask());
	}
}
