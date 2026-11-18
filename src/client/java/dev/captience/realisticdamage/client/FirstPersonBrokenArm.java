package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.client.compat.Vr;
import dev.captience.realisticdamage.fieldkit.client.SlungArm;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.BrokenArmHold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A broken arm in first person (2.0): the game-facing half of
 * {@link BrokenArmHold}. Decides whether a hand gets the hold at all, weighs
 * what it carries, and puts the result on the pose stack. The mixin that wraps
 * vanilla's first-person arm draw only calls {@link #pose} and {@link #apply}.
 *
 * <p>Drawing only. Not in VR: there the hand is the controller, and a hand
 * drawn sagging away from where the player is holding it would fight it.
 */
public final class FirstPersonBrokenArm {
	private FirstPersonBrokenArm() {}

	/** Where vanilla rests a first-person hand, view blocks (right hand). Rotations turn about this point. */
	private static final float HAND_X = 0.56F;
	private static final float HAND_Y = -0.52F;
	private static final float HAND_Z = -0.72F;

	/** Hands posed since launch, and the last pose given, for the client gametest. */
	private static long applications;
	private static BrokenArmHold.@Nullable Pose lastPose;

	public static long applications() {
		return applications;
	}

	public static BrokenArmHold.@Nullable Pose lastPose() {
		return lastPose;
	}

	/**
	 * The hold for this hand this frame, or null if the arm is not broken (or
	 * is gone, slung, someone else's, in VR, or the setting is off).
	 *
	 * @param attack vanilla's swing progress for this hand
	 */
	public static BrokenArmHold.@Nullable Pose pose(AbstractClientPlayer player, HumanoidArm arm, ItemStack held,
			float attack, float frameInterp) {
		if (!ClientEffectsConfig.get().firstPersonInjury || Vr.active()
				|| player != Minecraft.getInstance().player) {
			return null;
		}
		BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		BodyPart part = arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		if (snapshot == null || !snapshot.broken(part) || snapshot.destroyed(part) || SlungArm.slung(player, arm)) {
			return null;
		}
		// A Disabled arm is already drawn hanging (the "will not grip" droop in
		// ItemInHandRendererMixin); the sag is left out so the two do not stack.
		boolean hanging = snapshot.state(part) == BodyState.DISABLED && ClientEffectsConfig.get().showInjuryPose;
		return BrokenArmHold.pose(weight(held), attack, player.isSprinting(), player.tickCount + frameInterp, hanging);
	}

	/** Puts a hold on the stack. The caller pushes before and pops after. */
	public static void apply(PoseStack poseStack, BrokenArmHold.Pose pose, HumanoidArm arm) {
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		poseStack.translate(side * pose.shakeX(), pose.shakeY() - pose.drop(), 0.0F);
		// About the hand, not the camera: turned about the camera a few degrees
		// swings the whole arm off the screen.
		poseStack.translate(side * HAND_X, HAND_Y, HAND_Z);
		// Positive about the view axis is anticlockwise on screen, which for a
		// right hand is in toward the middle.
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * pose.roll() + pose.shakeTwist()));
		poseStack.mulPose(Axis.XP.rotationDegrees(-pose.tip()));
		poseStack.translate(side * -HAND_X, -HAND_Y, -HAND_Z);
		applications++;
		lastPose = pose;
	}

	/** How heavy a held thing is to a broken arm. */
	public static BrokenArmHold.Weight weight(ItemStack held) {
		if (held.isEmpty()) {
			return BrokenArmHold.Weight.EMPTY;
		}
		if (held.is(ItemTags.AXES) || held.is(ItemTags.SPEARS) || held.is(Items.MACE) || held.is(Items.TRIDENT)
				|| held.is(Items.CROSSBOW) || held.has(DataComponents.BLOCKS_ATTACKS)) {
			return BrokenArmHold.Weight.HEAVY;
		}
		if (held.is(ItemTags.SWORDS) || held.is(ItemTags.PICKAXES) || held.is(ItemTags.SHOVELS)
				|| held.is(ItemTags.HOES) || held.is(Items.BOW)) {
			return BrokenArmHold.Weight.MEDIUM;
		}
		return BrokenArmHold.Weight.LIGHT;
	}
}
