package dev.captience.realisticdamage.client.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A renderer's adult and baby models, for a ragdoll of a body this client has not drawn yet. */
@Mixin(AgeableMobRenderer.class)
public interface AgeableMobRendererAccess {
	@Accessor("adultModel")
	EntityModel<?> realisticdamage$adultModel();

	@Accessor("babyModel")
	EntityModel<?> realisticdamage$babyModel();
}
