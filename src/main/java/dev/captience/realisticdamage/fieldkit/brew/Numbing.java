package dev.captience.realisticdamage.fieldkit.brew;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Numbing on a body (2.0, phase 3): for about a minute you feel nothing, and
 * everything is still there.
 *
 * <p>The pain goes: no limp slows you, no blow makes you flinch, no hit blurs
 * the view, and your body diagram stops telling you how bad things are,
 * because you can't feel them (see the {@code fieldkit.mixin} and client
 * hooks). The injuries stay, and using a hurt limb as if it were whole makes
 * it worse: sprinting on a broken leg wears that leg down, and swinging with a
 * broken arm wears that arm.
 */
public final class Numbing {
	private Numbing() {}

	public static void init() {
		// A swing with a broken arm, felt by nobody.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && numb(player)) {
				BodyPartData arm = RDAttachments.get(player).part(ImpairmentSystem.armFor(player, true));
				if (arm.broken()) {
					arm.damage(Balance.NUMB_SWING_ARM_COST);
					RDAttachments.pushSnapshot(player);
				}
			}
			return InteractionResult.PASS;
		});
	}

	/** Numb, with Medicine on. */
	public static boolean numb(LivingEntity entity) {
		return BrewedEffects.active(entity, BrewedEffects.NUMBING);
	}

	/** One tick of a numbed body: a sprint on a broken leg costs that leg. */
	public static void tick(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !player.isSprinting()) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		boolean worse = false;
		for (BodyPart leg : new BodyPart[] {BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
			BodyPartData part = data.part(leg);
			if (part.broken() && !part.destroyed()) {
				part.damage(Balance.NUMB_SPRINT_LEG_COST / 20.0);
				worse = true;
			}
		}
		if (worse) {
			RDAttachments.pushSnapshot(player);
		}
	}

	/** Whether a player is numb, from the client's side: its own effects are synced to it. */
	public static boolean numbClient(Player player) {
		return player.hasEffect(BrewedEffects.NUMBING);
	}
}
