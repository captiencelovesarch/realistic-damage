package dev.captience.realisticdamage.systems;

/**
 * One mark of blood on an item (2.0): what shape, whose blood, how much,
 * where exactly (the seed), and when it was last wet.
 *
 * @param kind   the shape, from how it got there
 * @param tint   whose blood, as the name of a client palette entry
 * @param amount 0 to 1
 * @param seed   places the mark; two marks of one kind with different seeds sit in different spots
 * @param wetAt  the game time it was last wetted, which is what it dries from
 */
public record BloodLayer(BloodKind kind, String tint, float amount, int seed, long wetAt) {
	public BloodLayer withAmount(float value) {
		return new BloodLayer(kind, tint, value, seed, wetAt);
	}
}
