package dev.captience.realisticdamage.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * The villager-shaped bodies (1.7): the witch, and villagers should a pack tag
 * them. Legs are hidden when lost and limp when hurt; the arms are one crossed
 * piece and stay — there is no half of it to hide, and a witch with no arms at
 * all would be the stranger picture. Shared by the two model mixins, which
 * cannot be one because their setupAnim methods take different state types.
 */
public final class VillagerLikeBody {
	private VillagerLikeBody() {}

	public static void apply(Model model, LivingEntityRenderState state) {
		ModelPart root = model.root();
		ModelPart leftLeg = root.getChild("left_leg");
		ModelPart rightLeg = root.getChild("right_leg");
		LimbStateHolder holder = (LimbStateHolder) state;
		HiddenLimbs.apply((HiddenLimbs) model, holder.realisticdamage$destroyedMask(), null, null, leftLeg, rightLeg);
		InjuryPose.applyParts(holder, null, null, leftLeg, rightLeg, 1.0F);
	}
}
