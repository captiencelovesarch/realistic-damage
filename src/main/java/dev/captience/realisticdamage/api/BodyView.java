package dev.captience.realisticdamage.api;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;

/**
 * A read-only look at one player's body (1.3). Snapshot semantics: read what
 * you need now rather than holding on to it.
 *
 * <p>{@link #blood()} and {@link #consciousness()} are here despite the mod
 * hiding both from players. Withholding them would be theatre — an integrating
 * mod can read the attachment directly — and a first-aid or HUD mod has honest
 * reasons to want them. The design rule is about what a <em>player</em> is
 * shown, so if you surface these, show a band and not a figure.
 */
public interface BodyView {
	BodyState state(BodyPart part);

	/** 0 to 1, how intact the region is. */
	double condition(BodyPart part);

	boolean broken(BodyPart part);

	boolean destroyed(BodyPart part);

	boolean bleeding();

	boolean unconscious();

	boolean concussed();

	boolean burned();

	/**
	 * 0 none, 1 pinned at Hurt, 2 at Injured, 3 at Critical (1.6 long-term
	 * injuries). A band, like {@link #bloodBand()}. There is deliberately no
	 * accessor for the ceiling as a figure — {@link #condition(BodyPart)} already
	 * gives you the raw fraction if you need arithmetic.
	 */
	int ceilingBand(BodyPart part);

	/** 0 stable through 4 bleeding out — the banding the mod itself presents. */
	int bloodBand();

	/** 0 to 100. Prefer {@link #bloodBand()} for anything a player will see. */
	double blood();

	/** 0 to 100. Prefer a band for anything a player will see. */
	double consciousness();
}
