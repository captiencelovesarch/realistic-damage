package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.VillagerLikeBody;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers lose legs and limp (1.7), should a pack give them bodies. See {@link VillagerLikeBody}. */
@Mixin(VillagerModel.class)
public abstract class VillagerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V", at = @At("TAIL"))
	private void realisticdamage$body(VillagerRenderState state, CallbackInfo ci) {
		VillagerLikeBody.apply((Model) (Object) this, state);
	}
}
