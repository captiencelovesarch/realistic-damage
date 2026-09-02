package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.client.DestroyedLimbHolder;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies the synced destroyed-limb mask onto the render state each frame. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$carryDestroyedLimbs(Avatar entity, AvatarRenderState state, float partialTicks,
			CallbackInfo ci) {
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		((DestroyedLimbHolder) state).realisticdamage$setDestroyedMask(
				snapshot == null ? 0 : snapshot.destroyedMask());
		((dev.captience.realisticdamage.client.BloodStainHolder) state)
				.realisticdamage$setBloodStains(
						dev.captience.realisticdamage.client.BloodBody.stains(entity.getId()));
	}
}
