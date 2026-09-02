package dev.captience.realisticdamage.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.ClassifiedDamage.Routing;
import dev.captience.realisticdamage.systems.ImpairmentMath;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * The trauma stage of the damage pipeline (spec §4-5): vanilla has already
 * damaged ordinary HP; this converts the post-mitigation amount into localized
 * body-condition damage. Deterministic throughout.
 */
public final class TraumaPipeline {
	private TraumaPipeline() {}

	/**
	 * One localized application of condition damage. {@code conditionDamage} is
	 * what the part actually lost; {@code attempted} is what the hit was worth
	 * before clamping, which is what limb destruction reads — a part already at
	 * zero condition can absorb no more, yet the blow still landed.
	 */
	public record AppliedTrauma(BodyPart part, double conditionDamage, double attempted, boolean primary) {}

	/**
	 * @param baseDamageTaken pre-shield damage as reported by AFTER_DAMAGE
	 * @param damageTaken     post-shield/freeze/helmet damage as reported by AFTER_DAMAGE
	 * @param appliedDamage   health actually lost (post ALL vanilla mitigation, absorption excluded)
	 * @return the localized traumas dealt this event (empty when routed to nothing)
	 */
	public static List<AppliedTrauma> handle(ServerPlayer player, RealisticDamageData data, DamageSource source,
			double baseDamageTaken, double damageTaken, double appliedDamage, boolean blocked) {
		ClassifiedDamage classified = DamageClassifier.classify(player, source);
		if (classified == null) {
			return List.of();
		}
		ServerConfig config = ServerConfig.get();
		DamageProfile profile = DamageProfiles.of(classified.category());
		List<AppliedTrauma> result = new ArrayList<>(4);

		// Captured before anything in this event can break or destroy a part — including
		// explosive dismemberment (1.6), which runs before the health-derived trauma.
		// "Before" has to mean before the whole event, or a limb torn off by a blast is
		// not a new event by the time the ceiling rule looks.
		int brokenBefore = CeilingLogic.mask(data, BodyPartData::broken);
		int destroyedBefore = CeilingLogic.mask(data, BodyPartData::destroyed);

		// A fully successful block prevents body injury, but extremely heavy
		// blunt/explosive hits transfer reduced shock to the shield arm (spec §32).
		if (blocked) {
			double blockedAmount = Math.max(0.0, baseDamageTaken - damageTaken);
			boolean heavy = classified.category() == DamageCategory.HEAVY_BLUNT
					|| classified.category() == DamageCategory.EXPLOSION;
			if (heavy && blockedAmount >= Balance.SHIELD_SHOCK_MIN_DAMAGE) {
				BodyPart shieldArm = shieldArm(player);
				double shock = blockedAmount * profile.traumaMultiplier()
						* Balance.SHIELD_HEAVY_SHOCK_FRACTION * config.bodyDamageMultiplier;
				double dealt = data.part(shieldArm).damage(shock);
				if (shock > 0) {
					result.add(new AppliedTrauma(shieldArm, dealt, shock, false));
				}
			}
		}

		// A fall is scored on how far you dropped, not on what your boots let
		// through (1.4). Everything downstream — shares, breaks, concussion — then
		// reads the same units it always did.
		boolean fall = classified.category() == DamageCategory.FALL;
		double fallHeight = fall ? data.pendingFallHeight() : 0.0;
		double traumaSource = fall && fallHeight > 0.0
				? FallMath.traumaEquivalent(fallHeight, data.pendingFallSurface())
				: appliedDamage;

		if (traumaSource <= 0.0) {
			return result;
		}
		double baseTrauma = traumaSource * profile.traumaMultiplier()
				* classified.extraMultiplier() * config.bodyDamageMultiplier;
		if (source.getEntity() instanceof ServerPlayer) {
			baseTrauma *= config.pvpTraumaMultiplier;
		}
		if (fall) {
			baseTrauma *= config.fallTraumaMultiplier;
		}
		if (baseTrauma <= 0.0) {
			return result;
		}

		switch (classified.routing()) {
			case NONE -> {}
			case LOCATIONAL -> {
				BodyPart part = config.enableHitLocation
						? BodyHitResolver.resolve(player, source)
						: BodyPart.TORSO;
				double localized = baseTrauma * (1.0 - ArmorModifier.reduction(player, part, classified.category()));
				double dealt = data.part(part).damage(localized);
				result.add(new AppliedTrauma(part, dealt, localized, true));
				// Direct core hits shock the limbs a little (spec §8).
				if (part.isCore() && localized > 0) {
					for (BodyPart limb : BodyPart.values()) {
						if (limb.isLimb()) {
							double attempted = localized * Balance.CORE_HIT_LIMB_SHOCK;
							double shock = data.part(limb).damage(attempted);
							if (attempted > 0) {
								result.add(new AppliedTrauma(limb, shock, attempted, false));
							}
						}
					}
				}
			}
			case EXPLOSION_DIRECTIONAL -> {
				Vec3 center = source.getSourcePosition();
				Map<BodyPart, Double> shares = center != null
						? BodyHitResolver.explosionShares(player, center)
						: EnvironmentalRouter.shares(Routing.HEAD_TORSO);
				applyShares(player, data, classified, baseTrauma, shares, result);
			}
			case FALL_LEGS -> applyShares(player, data, classified, baseTrauma,
					FallMath.shares(fallHeight, data.pendingFallLeadingLeft()), result);
			default -> applyShares(player, data, classified, baseTrauma,
					EnvironmentalRouter.shares(classified.routing()), result);
		}

		// Sustained fire and any lava contact leave the player Burned (spec §27-28).
		if (config.enableBurns && !data.burned()
				&& (classified.category() == DamageCategory.LAVA
						|| (classified.category() == DamageCategory.FIRE
								&& player.getRemainingFireTicks() >= Balance.BURNED_APPLY_FIRE_TICKS))) {
			data.setBurned(true);
			Notifier.message(player, "realisticdamage.msg.burned");
		}

		if (config.enableBrokenLimbs) {
			BreakLogic.evaluate(player, data, profile, result);
		}
		if (config.enableLimbDestruction) {
			DestructionLogic.evaluate(player, data, result);
		}
		if (config.enableLongTermInjuries) {
			CeilingLogic.evaluate(player, data, result, brokenBefore, destroyedBefore);
		}
		if (config.enableBleeding) {
			createWounds(player, data, classified, profile, result, source);
		}
		// The impact term reads the same height-derived figure as the trauma, for
		// the same reason: whether a landing rattles your brain is a question
		// about the deceleration, not about how much padding was between you and
		// it.
		dev.captience.realisticdamage.systems.ConsciousnessSystem.onTrauma(
				player, data, classified, profile, result, traumaSource);
		if (fall && fallHeight > 0.0) {
			landed(player, data, fallHeight, config);
		} else {
			// A landing gets its own cue below; firing the generic one as well
			// would flash twice and kick the view sideways off a vertical impact.
			sendImpact(player, source, result, traumaSource, profile, classified.category());
		}
		return result;
	}

	/**
	 * Being taken off your feet by the ground (1.4). Vanilla lets you drop from
	 * any height and keep walking, which is the single largest gap between what a
	 * fall looks like and what it costs — the injuries were already there, but
	 * nothing about the moment of landing was.
	 *
	 * <p>Cushioning applies to the knockdown as well as to the trauma: landing in
	 * hay should not merely hurt less, it should leave you on your feet.
	 */
	private static void landed(ServerPlayer player, RealisticDamageData data, double fallHeight,
			ServerConfig config) {
		double effectiveHeight = fallHeight * data.pendingFallSurface();
		if (config.enableKnockdown) {
			int ticks = FallMath.knockdownTicks(effectiveHeight);
			if (ticks > 0) {
				data.knockDown(ticks);
				player.stopUsingItem();
				if (player.isSprinting()) {
					player.setSprinting(false);
				}
				Notifier.message(player, "realisticdamage.msg.knocked_down");
			}
		}
		sendLanding(player, effectiveHeight, data.pendingFallSurface());
	}

	/** The thump, the dip and the crunch, on the frame the ground arrives. */
	private static void sendLanding(ServerPlayer player, double effectiveHeight, double surfaceFactor) {
		if (player.connection == null
				|| !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
						player, dev.captience.realisticdamage.net.LandingPayload.TYPE)) {
			return;
		}
		double severity = Math.clamp(effectiveHeight / Balance.KNOCKDOWN_FULL_HEIGHT, 0.0, 1.0);
		double hardness = Math.clamp(
				(surfaceFactor - Balance.LANDING_SURFACE_SOFT)
						/ (Balance.LANDING_SURFACE_HARD - Balance.LANDING_SURFACE_SOFT),
				0.0, 1.0);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
				new dev.captience.realisticdamage.net.LandingPayload((float) severity, (float) hardness));
	}

	/**
	 * Tells the client a heavy blow just landed, so it can flash and kick on the
	 * exact frame rather than easing into it.
	 */
	private static void sendImpact(ServerPlayer player, DamageSource source,
			List<AppliedTrauma> traumas, double appliedDamage, DamageProfile profile,
			DamageCategory category) {
		double worst = 0.0;
		for (AppliedTrauma trauma : traumas) {
			worst = Math.max(worst, trauma.attempted());
		}
		// Scale off the bigger of the localized trauma and the raw blow, so both a
		// pinpoint headshot and a whole-body slam register.
		double magnitude = Math.max(worst / 12.0, appliedDamage * profile.traumaMultiplier() / 18.0);
		if (magnitude < 0.22) {
			return;
		}
		// Mock players (gametests) have no connection, and a vanilla client that
		// lacks the mod never negotiated the channel.
		if (player.connection == null
				|| !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
						player, dev.captience.realisticdamage.net.ImpactPayload.TYPE)) {
			return;
		}
		float yaw = player.getYRot();
		Vec3 from = source.getSourcePosition();
		if (from != null) {
			Vec3 delta = player.position().subtract(from);
			if (delta.horizontalDistanceSqr() > 1.0e-4) {
				yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
			}
		}
		// A vanilla client on a modded server has no receiver for this.
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
				player, dev.captience.realisticdamage.net.ImpactPayload.TYPE)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
					new dev.captience.realisticdamage.net.ImpactPayload(
							(float) Math.min(1.0, magnitude), yaw, category.ordinal()));
		}
	}

	/** Deterministic wound creation from localized trauma thresholds (spec §16). */
	private static void createWounds(ServerPlayer player, RealisticDamageData data, ClassifiedDamage classified,
			DamageProfile profile, List<AppliedTrauma> traumas, DamageSource source) {
		DamageProfile woundProfile = classified.woundAs() != null
				? DamageProfiles.of(classified.woundAs())
				: profile;
		if (!woundProfile.canBleed()) {
			return;
		}
		DamageCategory woundCategory = classified.woundAs() != null ? classified.woundAs() : classified.category();
		var worstBefore = data.worstWound();
		for (AppliedTrauma trauma : traumas) {
			if (!trauma.primary()) {
				continue;
			}
			var severity = woundProfile.woundFor(trauma.conditionDamage());
			if (severity == null) {
				continue;
			}
			severity = ArmorModifier.downgradeWound(player, trauma.part(), woundCategory, severity);
			if (severity != null) {
				data.addWound(trauma.part(), severity);
				// Opening a wound is an event, and it now looks like one (1.5).
				// Thrown along the blow so it reads as having been knocked out of
				// you rather than as having welled up on a timer.
				dev.captience.realisticdamage.systems.BleedingSystem.spray(
						player, trauma.part(), severity, blowDirection(player, source));
			}
		}
		var worstAfter = data.worstWound();
		if (worstAfter != null && (worstBefore == null || worstAfter.ordinal() > worstBefore.ordinal())) {
			Notifier.message(player, "realisticdamage.msg.bleeding."
					+ worstAfter.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static void applyShares(ServerPlayer player, RealisticDamageData data, ClassifiedDamage classified,
			double baseTrauma, Map<BodyPart, Double> shares, List<AppliedTrauma> result) {
		double primaryShare = shares.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
		for (Map.Entry<BodyPart, Double> entry : shares.entrySet()) {
			BodyPart part = entry.getKey();
			double amount = baseTrauma * entry.getValue()
					* (1.0 - ArmorModifier.reduction(player, part, classified.category()));
			if (classified.category() == DamageCategory.FALL && part.isLeg()) {
				// Damaged legs make further falls much more dangerous (spec §13).
				amount *= ImpairmentMath.fallTraumaAmplifier(data.part(part).fraction());
			}
			double dealt = data.part(part).damage(amount);
			if (amount > 0) {
				result.add(new AppliedTrauma(part, dealt, amount, entry.getValue() >= primaryShare));
			}
		}
	}

	/**
	 * The direction the blow travelled, from the source toward the victim. Zero
	 * when there is no position to work from — a fall, poison, drowning — and the
	 * spray falls back to going straight up, which is the honest answer for a
	 * wound that nothing threw anything at.
	 */
	private static Vec3 blowDirection(ServerPlayer player, DamageSource source) {
		Vec3 from = source == null ? null : source.getSourcePosition();
		if (from == null) {
			return Vec3.ZERO;
		}
		Vec3 delta = player.position().subtract(from);
		return delta.lengthSqr() < 1.0e-6 ? Vec3.ZERO : delta;
	}

	private static BodyPart shieldArm(Player player) {
		InteractionHand hand = player.isBlocking() ? player.getUsedItemHand() : InteractionHand.OFF_HAND;
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND
				? player.getMainArm()
				: player.getMainArm().getOpposite();
		return arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
	}
}
