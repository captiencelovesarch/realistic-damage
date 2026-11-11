package dev.captience.realisticdamage.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.captience.realisticdamage.client.StiffNeck;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Every turn of your own view goes through {@code Entity.turn}: the mouse, a
 * controller mod, the mouse's momentum. A stiff neck (2.0) gets a say in all
 * of them here, at the one place they meet; the turns the mod makes for you
 * (a blow's kick, a landing's dip) step around it ({@link StiffNeck#turnInvoluntarily}).
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
	@WrapMethod(method = "turn")
	private void realisticdamage$stiffNeck(double xo, double yo, Operation<Void> original) {
		if ((Object) this == Minecraft.getInstance().player) {
			double[] filtered = StiffNeck.filter(xo, yo);
			if (filtered != null) {
				original.call(filtered[0], filtered[1]);
				return;
			}
		}
		original.call(xo, yo);
	}
}
