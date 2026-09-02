package dev.captience.realisticdamage.body;

/**
 * The blast that is about to be scored (1.6). Recorded by
 * {@code ServerExplosionMixin} immediately before vanilla hurts the player and
 * consumed exactly once by the trauma pipeline inside that same call, so a limb
 * can be judged on what the blast was rather than on what the health bar let
 * through.
 *
 * <p>Three doubles rather than a {@code Vec3} because {@code body/} imports
 * nothing from Minecraft. No radius: nothing reads it, and an unused field in a
 * record that exists to be exact is a small lie.
 *
 * @param amount vanilla's {@code getEntityDamageAmount} for this player —
 *               radius-aware, exposure-aware, before difficulty scaling and
 *               before armour
 * @param x      the true blast centre, not the direct entity's feet
 */
public record PendingExplosion(double amount, double x, double y, double z) {
	public PendingExplosion {
		// Vanilla's formula ends in "+ 1.0", so a captured amount is always at
		// least 1.0. This is a programmer-error assertion, not a branch the game
		// can reach: a blast with no amount and no centre could only come from a
		// caller that invented one.
		if (!(amount > 0.0) || !Double.isFinite(amount)
				|| !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("a blast has a positive, finite amount and a real centre");
		}
	}
}
