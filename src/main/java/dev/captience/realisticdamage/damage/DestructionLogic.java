package dev.captience.realisticdamage.damage;

import java.util.List;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Limb destruction (Arcade preset). A part that is already Disabled and takes
 * another serious blow is gone for good: no healing, no golden apple, nothing
 * short of death restores it. Deterministic like every other injury — the same
 * blow on the same ruined limb always destroys it.
 *
 * <p>Destroying the head or torso kills outright, which is the point: those are
 * the two regions you cannot live without.
 */
public final class DestructionLogic {
	private DestructionLogic() {}

	public static void evaluate(ServerPlayer player, RealisticDamageData data,
			List<TraumaPipeline.AppliedTrauma> traumas) {
		for (TraumaPipeline.AppliedTrauma trauma : traumas) {
			if (!trauma.primary()) {
				continue;
			}
			BodyPartData part = data.part(trauma.part());
			if (part.destroyed() || part.state() != BodyState.DISABLED) {
				continue;
			}
			// The part is at zero, so it absorbed none of this hit — judge the blow
			// by what it was worth, not by what the part could still lose.
			if (trauma.attempted() < threshold(trauma.part())) {
				continue;
			}
			destroy(player, data, trauma.part());
			if (trauma.part().isCore()) {
				return; // player is dead; further destruction is meaningless
			}
		}
	}

	private static double threshold(BodyPart part) {
		if (part.isArm()) {
			return Balance.DESTROY_TRAUMA_ARM;
		}
		return part.isLeg() ? Balance.DESTROY_TRAUMA_LEG : Balance.DESTROY_TRAUMA_CORE;
	}

	/** Marks a part destroyed and applies its permanent consequence. */
	public static void destroy(ServerPlayer player, RealisticDamageData data, BodyPart part) {
		data.part(part).setDestroyed(true);
		Notifier.destroyed(player, part);
		if (part.isCore()) {
			// A destroyed head or torso is not survivable, and the two read as
			// different deaths in chat.
			player.hurtServer(player.level(),
					RDDamageTypes.coreDestroyed(player.level(), part), Float.MAX_VALUE);
		}
	}
}
