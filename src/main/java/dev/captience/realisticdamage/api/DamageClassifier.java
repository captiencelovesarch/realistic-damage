package dev.captience.realisticdamage.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.Nullable;

/**
 * Decides what kind of trauma a damage source represents (1.3).
 *
 * <p>Only needed when a datapack tag cannot answer — when the category depends
 * on runtime state, such as which round a gun has chambered. If the answer is
 * the same every time, tag the damage type instead and skip the dependency.
 */
@FunctionalInterface
public interface DamageClassifier {
	/**
	 * @return how to treat this hit, or null to let the next classifier — and
	 *         ultimately the datapack tags and built-in table — decide
	 */
	@Nullable
	ExternalClassification classify(ServerPlayer victim, DamageSource source);
}
