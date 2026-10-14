package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.config.Balance;

/**
 * The last two hearts (1.7). Dropping that low floods you with adrenaline for a
 * few seconds — quicker, pain pushed to the back — and then you pay for it: a
 * crash of heavy legs and a breath that runs out after a few seconds of
 * sprinting. Pure state machine, no Minecraft types, so the transitions are
 * pinned by unit tests.
 *
 * <p>Deterministic like everything else: the surge fires on the crossing, not
 * on a roll, and it will not fire again until both the refractory time has
 * passed and health has climbed back over the heartbeat line — otherwise
 * hovering at two hearts would chain surges forever.
 */
public final class AdrenalineMath {
	private AdrenalineMath() {}

	public enum Phase {
		NONE,
		SURGE,
		CRASH;

		public int wire() {
			return ordinal();
		}

		public static Phase fromWire(int value) {
			Phase[] all = values();
			return value >= 0 && value < all.length ? all[value] : NONE;
		}
	}

	/** Mutable per-player state. Transient: nobody is mid-surge after a restart. */
	public static final class State {
		public Phase phase = Phase.NONE;
		/** Ticks left in the surge, or in the minimum crash. */
		public int ticks;
		/** Ticks until another surge may fire. */
		public int refractory;
		/** Health has been back above the heartbeat line since the last surge. */
		public boolean rearmed = true;
		/** Sprinting budget during the crash, and whether it has run out. */
		public double breath = Balance.CRASH_BREATH_MAX;
		public boolean winded;

		public void reset() {
			phase = Phase.NONE;
			ticks = 0;
			refractory = 0;
			rearmed = true;
			breath = Balance.CRASH_BREATH_MAX;
			winded = false;
		}
	}

	/**
	 * One tick.
	 *
	 * @param healthFraction health / max health
	 * @param sprinting      the player is sprinting this tick
	 * @return true on the tick a surge begins
	 */
	public static boolean tick(State state, double healthFraction, boolean sprinting) {
		boolean low = healthFraction <= Balance.ADRENALINE_HP_FRACTION && healthFraction > 0.0;
		if (healthFraction > Balance.LOW_HP_FRACTION) {
			state.rearmed = true;
		}
		if (state.refractory > 0) {
			state.refractory--;
		}
		boolean surged = false;
		switch (state.phase) {
			case NONE -> {
				if (low && state.rearmed && state.refractory == 0) {
					state.phase = Phase.SURGE;
					state.ticks = Balance.ADRENALINE_SURGE_TICKS;
					state.refractory = Balance.ADRENALINE_REFRACTORY_TICKS;
					state.rearmed = false;
					surged = true;
				}
			}
			case SURGE -> {
				if (--state.ticks <= 0) {
					state.phase = Phase.CRASH;
					state.ticks = Balance.ADRENALINE_CRASH_TICKS;
					state.breath = Balance.CRASH_BREATH_MAX;
					state.winded = false;
				}
			}
			case CRASH -> {
				if (state.ticks > 0) {
					state.ticks--;
				}
				if (state.ticks == 0 && !low) {
					state.phase = Phase.NONE;
					state.breath = Balance.CRASH_BREATH_MAX;
					state.winded = false;
				}
			}
		}
		// The breath budget only means anything during the crash; outside it the
		// budget sits full so a fresh crash always starts with a full chest.
		if (state.phase == Phase.CRASH) {
			if (sprinting && !state.winded) {
				state.breath -= Balance.CRASH_BREATH_SPEND;
				if (state.breath <= 0.0) {
					state.breath = 0.0;
					state.winded = true;
				}
			} else {
				state.breath = Math.min(Balance.CRASH_BREATH_MAX, state.breath + Balance.CRASH_BREATH_RECOVER);
				if (state.winded && state.breath >= Balance.CRASH_BREATH_MAX) {
					state.winded = false;
				}
			}
		}
		return surged;
	}
}
