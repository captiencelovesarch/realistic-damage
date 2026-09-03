package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig.PreyOnWounded;

/**
 * How attractive a player is as prey, from what a mob can see (1.6).
 *
 * <p>A ladder rather than a sum of weights: a body on the floor outranks a lost
 * leg outranks a broken one outranks a limp outranks blood, and the constants in
 * {@link Balance} are sized so that no amount of the lower rungs — stacked, with
 * a grudge and the switch margin on top — can reach the rung above. The ordering
 * is the rule the player learns, so it has to be a property and not a tendency,
 * and {@code PreyMathTest} enumerates it rather than trusting the arithmetic in
 * this javadoc.
 *
 * <p>Every input is a field the body snapshot already carries to every client in
 * range, so "visibly wounded" is literally true and the two hidden meters, Blood
 * and Consciousness, are excluded by construction rather than by care. Scars are
 * excluded too: a scar is history, not a wound, and a mark the mobs could smell
 * would turn the one purely cosmetic feature in the release into a penalty.
 * Ceilings are not read either — a long-term ceiling makes you prey only when it
 * pins a leg at Critical, and that falls out of the band rule below rather than
 * from a special case. None of it is a die roll, so two identical bodies are
 * always ranked identically.
 *
 * <p><b>The limp is not defined here.</b> It is defined once, in
 * {@link LimpMath}, and this class reads the same rung the player model poses
 * from. That is the whole reason the pose and the prey score shipped together:
 * the roadmap promises hostiles prefer someone <em>visibly</em> limping, and the
 * promise is only true while what a mob reads and what a player sees are the
 * same statement about the same leg. Two definitions would agree on the day they
 * were written and drift the first time one of them was retuned.
 *
 * <p>The one place the two deliberately differ is {@link LimpMath#RUNG_LOST}: the
 * model draws nothing, because the limb is gone and absence is the louder cue,
 * while the score ranks it second only to a body on the floor. That is not an
 * inconsistency — the mob is reading the same body, and a missing leg is the
 * most visible thing on it.
 *
 * <p>{@code MobTargetMixin} is what applies this; this class never sees a mob.
 */
public final class PreyMath {
	private PreyMath() {}

	/**
	 * Wound rung: 0 for nothing and for MINOR, then MODERATE 1, SEVERE 2,
	 * CRITICAL 3.
	 *
	 * <p>A scratch scores nothing on purpose. A MINOR wound seeps 0.25 Blood/s,
	 * below the {@code DROPS_PER_SECOND_MIN} of 0.8 that produces a falling drop
	 * — the mod's own line between a wound that seeps and one that runs. A
	 * skeleton arrow is 3 damage x TRAUMA_PUNCTURE 1.10 = 3.3, under the 3.5 that
	 * opens a MODERATE puncture, so under a MINOR-counts rule every player who
	 * had ever been shot once would bias every acquisition on the server.
	 *
	 * @param worstWoundOrdinal {@code WoundSeverity.ordinal()}, or -1 if not bleeding
	 */
	public static int woundStep(int worstWoundOrdinal) {
		return Math.max(0, worstWoundOrdinal);
	}

	/**
	 * What the legs are worth, from the shared limp rung.
	 *
	 * <p>Exclusive by construction rather than by a chain of {@code else if}:
	 * the ladder already orders "gone" above "broken" above "worn down", so the
	 * score is a switch on the rung and the two documents cannot disagree about
	 * which case wins. The band rungs are multiples of {@link
	 * Balance#PREY_LIMP_BAND} because they are literally a count of how many
	 * bands past visible the leg has fallen.
	 */
	public static int legTerm(int rung) {
		return switch (rung) {
			case LimpMath.RUNG_LOST -> Balance.PREY_TIER_LEG_DESTROYED;
			case LimpMath.RUNG_FRACTURED -> Balance.PREY_TIER_LEG_BROKEN;
			default -> rung * Balance.PREY_LIMP_BAND;
		};
	}

	/**
	 * The worse leg's rung, from the same three facts the model poses from.
	 *
	 * <p>Reads as "either leg gone, else either leg broken, else the worse leg's
	 * band", which is the sentence the design states — and is the same function
	 * as {@code LimpMath.worse(rung(left), rung(right))}, because the ladder is
	 * ordered. {@code LimpMathTest} pins that equivalence so neither form can be
	 * written twice.
	 */
	public static int legRung(boolean legDestroyed, boolean legBroken, double worseLegFraction) {
		return LimpMath.rung(legDestroyed, legBroken, worseLegFraction);
	}

	/**
	 * Higher is better prey. Pure and deterministic; exactly 0 when nothing
	 * counts under {@code mode}.
	 *
	 * @param down              unconscious, knocked down, or legless — prone, on the floor
	 * @param legDestroyed      either leg destroyed
	 * @param legBroken         either leg broken
	 * @param worseLegFraction  condition fraction of the worse leg, against its static max
	 * @param worstWoundOrdinal {@code WoundSeverity.ordinal()} of the worst wound, or -1
	 */
	public static int score(PreyOnWounded mode, boolean down, boolean legDestroyed, boolean legBroken,
			double worseLegFraction, int worstWoundOrdinal) {
		if (mode == PreyOnWounded.OFF) {
			return 0;
		}
		int score = 0;
		if (down) {
			score += Balance.PREY_TIER_DOWN;
		}
		if (mode.includesLimp()) {
			score += legTerm(legRung(legDestroyed, legBroken, worseLegFraction));
		}
		if (mode.includesBleed()) {
			score += woundStep(worstWoundOrdinal) * Balance.PREY_WOUND_STEP;
		}
		return score;
	}

	/** The same score read straight off a body. Still Minecraft-type-free. */
	public static int score(RealisticDamageData data, PreyOnWounded mode) {
		BodyPartData left = data.part(BodyPart.LEFT_LEG);
		BodyPartData right = data.part(BodyPart.RIGHT_LEG);
		WoundSeverity worst = data.worstWound();
		return score(mode,
				data.unconscious() || data.knockedDown() || data.legless(),
				left.destroyed() || right.destroyed(),
				left.broken() || right.broken(),
				data.worseLegFraction(),
				worst == null ? -1 : worst.ordinal());
	}

	/**
	 * How much better a candidate has to be before it takes the slot.
	 *
	 * <p>Two different loyalties, and the larger one wins outright rather than
	 * stacking. A mob that is merely engaged is loyal by {@link
	 * Balance#PREY_SWITCH_MARGIN}; one that is nursing a grudge is loyal by
	 * {@link Balance#PREY_REVENGE_BONUS} <em>instead</em>. That is the answer to
	 * the question the design left open — when the player who just hit a mob is
	 * healthy and a limping bystander is nearby, who does the mob keep? — and the
	 * answer is: <em>the one who hit it, against any limp and any blood, and not
	 * against a broken leg.</em>
	 *
	 * <p>It replaces rather than stacks so that the rule is one number in both
	 * places. Stacked, a grudge would mean "clear 175 on the swing that made it
	 * and 200 two ticks later when the engaged goal re-asserts", which is correct
	 * and unlearnable — the same fight would end differently depending on which
	 * tick the leg gave out on. As a replacement it reads the same either way,
	 * and it expires exactly when vanilla forgets who hit it, a hundred ticks
	 * later, so the grudge a player can feel is the grudge the game is keeping.
	 */
	public static int hold(boolean engaged, boolean revenge) {
		return Math.max(engaged ? Balance.PREY_SWITCH_MARGIN : 0,
				revenge ? Balance.PREY_REVENGE_BONUS : 0);
	}

	/** The score a candidate must strictly beat to take the slot. */
	public static int threshold(int requestedScore, boolean engaged, boolean revenge) {
		return requestedScore + hold(engaged, revenge);
	}

	/** Strict, so a tie keeps whoever vanilla asked for. */
	public static boolean prefers(int candidateScore, int requestedScore, boolean engaged, boolean revenge) {
		return candidateScore > threshold(requestedScore, engaged, revenge);
	}
}
