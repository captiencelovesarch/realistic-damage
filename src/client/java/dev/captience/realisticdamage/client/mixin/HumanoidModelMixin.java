package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.InjuryPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The last word on a player's limb rotations, so an injured limb is carried
 * like one.
 *
 * <p>Injected here rather than into {@code PlayerModel} because the armour
 * layer renders a <em>separate</em> {@code HumanoidModel} and hands it the same
 * render state, so a pose applied only to the player's own model would rotate
 * the arm and leave its sleeve behind. At the cradle's 17° a 12-unit arm
 * displaces about 3.5 units, far outside armour's 1.0 inflation, and the limb
 * would poke straight through the plate. One injection point catches both.
 *
 * <p>{@code PlayerModel.setupAnim} calls {@code super} <em>last</em>, so this
 * tail is the final word before the model is submitted; nothing downstream
 * writes a limb rotation again. The one thing that does run after us is
 * {@code PlayerModelMixin}, which hides destroyed limbs — which is why the pose
 * reads the destroyed rung off the synced mask rather than off
 * {@code part.visible}, a flag that is still vanilla's at this point.
 *
 * <p><strong>The instanceof guard is the single point of failure for "does this
 * mod touch mob rendering".</strong> {@code HumanoidModel} is the class every
 * humanoid mob and every armour set instantiates — a wider target than anything
 * else this mod mixes into — and {@code AvatarRenderState} is exactly a player,
 * because nothing in {@code entity/state} extends it. If that ever stops being
 * true, this is the line that has to change.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	// The erased descriptor is explicit because HumanoidModel<T extends
	// HumanoidRenderState> generates a bridge method, and an unqualified name
	// would be ambiguous between the two.
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
			at = @At("TAIL"))
	private void realisticdamage$carryInjuredLimbs(HumanoidRenderState state, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar) {
			InjuryPose.apply(avatar, (HumanoidModel<?>) (Object) this);
		}
	}
}
