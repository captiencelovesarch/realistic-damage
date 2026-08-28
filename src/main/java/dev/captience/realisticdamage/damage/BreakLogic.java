package dev.captience.realisticdamage.damage;

import java.util.List;

import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Deterministic limb breaking (spec §15): a single qualifying heavy impact can
 * snap a healthy limb; repeated meaningful hits break one only once it is
 * CRITICAL/DISABLED. No fracture rolls, ever.
 */
public final class BreakLogic {
	private BreakLogic() {}

	/** A hit must itself be meaningful to break via accumulation — weak spam never snaps bones. */
	private static final double MEANINGFUL_HIT = 1.0;

	public static void evaluate(ServerPlayer player, RealisticDamageData data, DamageProfile profile,
			List<TraumaPipeline.AppliedTrauma> traumas) {
		for (TraumaPipeline.AppliedTrauma trauma : traumas) {
			if (!trauma.part().isLimb() || !trauma.primary()) {
				continue;
			}
			BodyPartData part = data.part(trauma.part());
			if (part.broken()) {
				continue;
			}
			double impactThreshold = trauma.part().isArm() ? Balance.BREAK_IMPACT_ARM : Balance.BREAK_IMPACT_LEG;
			boolean massiveImpact = profile.breakOnImpact() && trauma.conditionDamage() >= impactThreshold;
			boolean accumulated = profile.breakOnAccumulation()
					&& trauma.conditionDamage() >= MEANINGFUL_HIT
					&& part.state().isAtLeastAsBadAs(BodyState.CRITICAL);
			if (massiveImpact || accumulated) {
				part.setBroken(true);
				Notifier.limbBreaks(player, trauma.part());
			}
		}
	}
}
