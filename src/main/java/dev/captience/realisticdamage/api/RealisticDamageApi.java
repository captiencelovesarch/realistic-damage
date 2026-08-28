package dev.captience.realisticdamage.api;

import java.util.ArrayList;
import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The entry point for other mods (1.3).
 *
 * <p>Three things a mod might need, and nothing else:
 *
 * <ul>
 * <li>say what kind of trauma its damage is, when a datapack tag is not enough
 * ({@link #registerClassifier}),</li>
 * <li>say exactly where on the body it landed, for weapons that do their own
 * raycasting ({@link #registerImpactPoints}),</li>
 * <li>read what state a body is in ({@link #bodyOf}).</li>
 * </ul>
 *
 * <p>Most integrations need none of this. Classifying a weapon is usually a
 * datapack tag — see {@code data/realisticdamage/tags/damage_type/} — and that
 * route needs no dependency on this mod at all. Reach for the API only when the
 * answer depends on runtime state a tag cannot express, such as which calibre
 * was loaded.
 *
 * <p>Everything here is server-side. Call it from your mod initialiser;
 * registrations are permanent and are consulted in registration order.
 */
public final class RealisticDamageApi {
	private RealisticDamageApi() {}

	private static final List<DamageClassifier> CLASSIFIERS = new ArrayList<>();
	private static final List<ImpactPointProvider> IMPACT_POINTS = new ArrayList<>();

	/**
	 * Adds a classifier, consulted before both the datapack tags and the
	 * built-in table. Return null to defer.
	 */
	public static void registerClassifier(DamageClassifier classifier) {
		CLASSIFIERS.add(classifier);
	}

	/**
	 * Adds an impact-point provider, consulted before the mod works the location
	 * out for itself. Return null to defer.
	 *
	 * <p>For a firearm this is the whole integration: you already raycast, so
	 * handing over the point you hit is strictly better than having us
	 * reconstruct one from a projectile that may not exist.
	 */
	public static void registerImpactPoints(ImpactPointProvider provider) {
		IMPACT_POINTS.add(provider);
	}

	/** Read-only body state, or null for a player the mod is not tracking. */
	@Nullable
	public static BodyView bodyOf(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)
				|| serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return null;
		}
		return new AttachedBodyView(RDAttachments.get(serverPlayer));
	}

	// --- Internal dispatch ---

	/** @return the first non-null external classification, or null. */
	@Nullable
	public static ExternalClassification classifyExternally(ServerPlayer victim, DamageSource source) {
		for (DamageClassifier classifier : CLASSIFIERS) {
			ExternalClassification result = classifier.classify(victim, source);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/** @return the first supplied impact point, or null. */
	@Nullable
	public static Vec3 externalImpactPoint(ServerPlayer victim, DamageSource source) {
		for (ImpactPointProvider provider : IMPACT_POINTS) {
			Vec3 point = provider.impactPoint(victim, source);
			if (point != null) {
				return point;
			}
		}
		return null;
	}

	/** True when nobody has registered anything, so the hot path can skip the loop. */
	public static boolean hasClassifiers() {
		return !CLASSIFIERS.isEmpty();
	}

	public static boolean hasImpactPoints() {
		return !IMPACT_POINTS.isEmpty();
	}

	private record AttachedBodyView(RealisticDamageData data) implements BodyView {
		@Override
		public dev.captience.realisticdamage.body.BodyState state(
				dev.captience.realisticdamage.body.BodyPart part) {
			return data.part(part).destroyed()
					? dev.captience.realisticdamage.body.BodyState.DESTROYED
					: data.part(part).state();
		}

		@Override
		public double condition(dev.captience.realisticdamage.body.BodyPart part) {
			return data.part(part).fraction();
		}

		@Override
		public boolean broken(dev.captience.realisticdamage.body.BodyPart part) {
			return data.part(part).broken();
		}

		@Override
		public boolean destroyed(dev.captience.realisticdamage.body.BodyPart part) {
			return data.part(part).destroyed();
		}

		@Override
		public boolean bleeding() {
			return data.bleeding();
		}

		@Override
		public boolean unconscious() {
			return data.unconscious();
		}

		@Override
		public boolean concussed() {
			return data.concussion();
		}

		@Override
		public boolean burned() {
			return data.burned();
		}

		@Override
		public int bloodBand() {
			return data.bloodBand();
		}

		@Override
		public double blood() {
			return data.blood();
		}

		@Override
		public double consciousness() {
			return data.consciousness();
		}
	}
}
