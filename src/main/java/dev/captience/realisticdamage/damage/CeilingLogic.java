package dev.captience.realisticdamage.damage;

import java.util.List;
import java.util.function.Predicate;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.CeilingMath;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Long-term injuries (1.6, hardcore). A part driven to zero, a limb broken, a
 * limb lost: each costs that part one rung of ceiling, and from then on food and
 * time heal it only that far. Deterministic like every other injury here — the
 * same blow on the same body always costs the same rung — and one rung per part
 * per blow, however many things that blow did to it.
 */
public final class CeilingLogic {
	private CeilingLogic() {}

	/** Bit per part.ordinal() matching the predicate; allocation-free, captured before anything can injure. */
	public static int mask(RealisticDamageData data, Predicate<BodyPartData> test) {
		int mask = 0;
		for (BodyPart part : BodyPart.values()) {
			if (test.test(data.part(part))) {
				mask |= 1 << part.ordinal();
			}
		}
		return mask;
	}

	/**
	 * Runs after DestructionLogic, so a part lost this blow is filed as DESTROYED
	 * and a hit on a part that was already at zero is not a new event. Events are
	 * ranked destroyed, then broken, then zeroed: the rung is the same either way,
	 * and the ranking decides which sentence the player reads.
	 */
	public static void evaluate(ServerPlayer player, RealisticDamageData data,
			List<TraumaPipeline.AppliedTrauma> traumas, int brokenBefore, int destroyedBefore) {
		int handled = 0;
		for (TraumaPipeline.AppliedTrauma trauma : traumas) {
			int bit = 1 << trauma.part().ordinal();
			if ((handled & bit) != 0) {
				continue;   // a shield shock and a share can both land on one arm: one rung, not two
			}
			BodyPartData part = data.part(trauma.part());
			CeilingMath.Event event;
			if (part.destroyed() && (destroyedBefore & bit) == 0) {
				event = CeilingMath.Event.DESTROYED;
			} else if (part.broken() && (brokenBefore & bit) == 0) {
				event = CeilingMath.Event.BROKEN;
			} else if (!part.destroyed() && trauma.conditionDamage() > 0.0
					&& part.state() == BodyState.DISABLED) {
				event = CeilingMath.Event.DISABLED;     // went above zero to exactly zero this event
			} else {
				continue;
			}
			handled |= bit;                             // even at the floor: never evaluated twice
			if (part.lowerCeilingTo(CeilingMath.lowered(part.ceilingBand(), event, trauma.part()))) {
				Notifier.ceilingLowered(player, trauma.part(), event);
			}
		}
	}
}
