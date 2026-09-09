package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.VillagerLikeBody;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A witch loses legs and limps (1.7); her crossed arms stay. See {@link VillagerLikeBody}. */
@Mixin(WitchModel.class)
public abstract class WitchModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/WitchRenderState;)V", at = @At("TAIL"))
	private void realisticdamage$body(WitchRenderState state, CallbackInfo ci) {
		VillagerLikeBody.apply((Model) (Object) this, state);
	}
}
