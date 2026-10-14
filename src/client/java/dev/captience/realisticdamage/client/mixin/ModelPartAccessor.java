package dev.captience.realisticdamage.client.mixin;

import java.util.Map;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A part's named children, for finding a body's regions by name in any model (1.7 slice 3). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("children")
	Map<String, ModelPart> realisticdamage$children();
}
