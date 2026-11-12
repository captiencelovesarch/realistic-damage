package dev.captience.realisticdamage.fieldkit.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.fieldkit.client.SlungArm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * An arm in a sling, in first person (items.7): not raised into view holding
 * something, but low across the bottom of it, wrapped in the sling, holding
 * nothing (that hand can't use anything anyway). A wrap, like the core's lost
 * arm, so it is decided before anyone else draws the hand; not in VR, where
 * the hands are the controllers.
 */
@Mixin(ItemInHandRenderer.class)
abstract class SlingFirstPersonMixin {
	@WrapMethod(method = "submitArmWithItem")
	private void realisticdamage$slungArm(AbstractClientPlayer player, float frameInterp, float xRot,
			InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords, Operation<Void> original) {
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		if (player == Minecraft.getInstance().player && !player.isScoping() && !player.isInvisible()
				&& SlungArm.slung(player, arm)) {
			SlungArm.firstPerson(player, arm, inverseArmHeight, poseStack, submitNodeCollector, lightCoords);
			return;
		}
		original.call(player, frameInterp, xRot, hand, attack, itemStack, inverseArmHeight, poseStack,
				submitNodeCollector, lightCoords);
	}
}
