package dev.captience.realisticdamage.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The ragdoll debug boxes ({@code /rdragdoll debug}), drawn with vanilla's own gizmos. */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
	@Inject(method = "emitGizmos", at = @At("TAIL"))
	private void realisticdamage$ragdollBoxes(Frustum frustum, double camX, double camY, double camZ,
			float partialTicks, CallbackInfo ci) {
		dev.captience.realisticdamage.client.RagdollDebug.emit(partialTicks);
	}
}
