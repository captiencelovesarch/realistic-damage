package dev.captience.realisticdamage.damage;

import java.util.List;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.systems.WoundMath;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Limb destruction (Arcade preset). A part that is already Disabled and takes
 * another serious blow is gone for good: no healing, no golden apple, nothing
 * short of death restores it. Deterministic like every other injury — the same
 * blow on the same ruined limb always destroys it.
 *
 * <p>Destroying the head or torso kills outright, which is the point: those are
 * the two regions you cannot live without.
 *
 * <p>Since 1.6 a blast can also take a healthy limb outright; that rule lives in
 * {@link ExplosiveDismemberment} and runs first, so on an explosion event the
 * finish-off rule below only ever completes a limb the blast was not worth, or a
 * core part.
 *
 * <p>{@link #destroy} is the single choke point every way of losing a limb goes
 * through — the finish-off rule below, {@code /rdmg destroy}, and explosive
 * dismemberment — which is why the permanent consequences live inside it rather
 * than at the call sites: the scar (raised inside {@code setDestroyed}) and,
 * since 1.6, the stump wound. A limb coming off opens a wound is then one rule,
 * not one rule per way of losing it.
 */
public final class DestructionLogic {
	private DestructionLogic() {}

	/**
	 * What the player is told the limb went to. The state change, the scar, the
	 * stump and the sound are identical either way; only the sentence differs,
	 * and it differs because a rule with counterplay has to be learnable from the
	 * line that announces it. "Your left leg is destroyed" names the state; "The
	 * blast takes your left leg" names the cause, and distance, cover and Blast
	 * Protection are all answers to a cause.
	 *
	 * <p>It is a parameter rather than something inferred from the damage source
	 * because the choke point should not have to know what a {@code DamageSource}
	 * is to pick a noun.
	 */
	public enum Cause {
		/** Ground down, finished off, or taken by fiat: {@code /rdmg destroy}. */
		LOST,
		/** Torn off by an explosion (1.6). */
		BLAST
	}

	/**
	 * The finish-off rule. Returns true when a part was destroyed this event, so
	 * the caller can keep the destruction sentence the last line written on the
	 * frame — {@code sendOverlayMessage} means the last line is the only one the
	 * player reads.
	 */
	public static boolean evaluate(LivingEntity player, RealisticDamageData data,
			List<TraumaPipeline.AppliedTrauma> traumas, Vec3 blowDirection) {
		boolean destroyed = false;
		for (TraumaPipeline.AppliedTrauma trauma : traumas) {
			if (!trauma.primary()) {
				continue;
			}
			BodyPartData part = data.part(trauma.part());
			if (part.destroyed() || part.state() != BodyState.DISABLED) {
				continue;
			}
			// The part is at zero, so it absorbed none of this hit — judge the blow
			// by what it was worth, not by what the part could still lose.
			if (trauma.attempted() < threshold(trauma.part())) {
				continue;
			}
			// ...and judge the stump by the other half of the same record: what the
			// part did lose says whether the limb was still standing when the blow
			// landed. See WoundMath.stumpSeverity.
			destroy(player, data, trauma.part(),
					WoundMath.stumpSeverity(trauma.conditionDamage()), blowDirection);
			destroyed = true;
			if (trauma.part().isCore()) {
				return true; // player is dead; further destruction is meaningless
			}
		}
		return destroyed;
	}

	private static double threshold(BodyPart part) {
		if (part.isArm()) {
			return Balance.DESTROY_TRAUMA_ARM;
		}
		return part.isLeg() ? Balance.DESTROY_TRAUMA_LEG : Balance.DESTROY_TRAUMA_CORE;
	}

	/**
	 * Loses the part to something that was not a blow — {@code /rdmg destroy},
	 * and any future caller with nothing to point at. The stump is
	 * {@link WoundSeverity#CRITICAL}, because "taken off" is what a limb removed
	 * by fiat most resembles, and the spray goes straight up for want of a
	 * direction.
	 */
	public static void destroy(LivingEntity player, RealisticDamageData data, BodyPart part) {
		destroy(player, data, part, WoundSeverity.CRITICAL, Vec3.ZERO, Cause.LOST);
	}

	/** The ordinary loss: something wore the limb down and something finished it. */
	public static void destroy(LivingEntity player, RealisticDamageData data, BodyPart part,
			WoundSeverity stump, Vec3 blowDirection) {
		destroy(player, data, part, stump, blowDirection, Cause.LOST);
	}

	/**
	 * Marks a part destroyed and applies its permanent consequences: the scar,
	 * the action-bar line, the sound, and — for a limb — the stump.
	 *
	 * <p>The stump is what a lost limb was missing until 1.6. Destruction can
	 * only fire on a part already at zero condition, so the wound rule that keys
	 * on how much condition a hit removed returns nothing for it: the mod's
	 * strongest injury fell out of the bottom of the bleeding system and cost no
	 * Blood at all. Opening it here, at the choke point, means no future
	 * destruction path can forget it.
	 *
	 * <p>It is survivable, and deliberately so: pressure is body-wide and never
	 * looks at whether a region still exists, and a wound decays with food and
	 * time as its own object even though the limb never comes back. The limb
	 * stays gone, the bleeding stops.
	 *
	 * @param stump         what the stump bleeds; the caller decides, because a limb
	 *                      taken off in one blow and one ground down over a fight
	 *                      are different events (see {@link WoundMath#stumpSeverity})
	 * @param blowDirection direction the killing blow travelled, or {@code Vec3.ZERO}
	 *                      when there was none
	 * @param cause         which sentence the moment gets; see {@link Cause}
	 */
	public static void destroy(LivingEntity player, RealisticDamageData data, BodyPart part,
			WoundSeverity stump, Vec3 blowDirection, Cause cause) {
		data.part(part).setDestroyed(true);
		if (part.isCore()) {
			// A destroyed head or torso is not survivable, and the two read as
			// different deaths in chat. No stump: a wound on a corpse is noise, and
			// no blast can reach here — ExplosiveDismemberment is limbs only.
			Notifier.destroyed(player, part, false);
			ServerLevel level = (ServerLevel) player.level();
			player.hurtServer(level, RDDamageTypes.coreDestroyed(level, part), Float.MAX_VALUE);
			return;
		}
		boolean bleeds = ServerConfig.get().enableBleeding && MobAnatomy.bleeds(player);
		if (bleeds) {
			data.addWound(part, stump);
			BleedingSystem.spray(player, part, stump, blowDirection, true);
		}
		// The limb itself (1.6.2), thrown along the blow: here, at the choke
		// point, so no way of losing a limb can forget to leave one behind.
		SeveredLimb.throwFrom(player, part, blowDirection, cause == Cause.BLAST);
		// Last, so the sentence the player reads is this one — and, when it is the
		// stump wording, it only claims a bleeding stump when one was opened. The
		// blast wording names a cause rather than a wound, so it needs no such
		// guard: the limb is gone whether or not anything bled.
		if (cause == Cause.BLAST) {
			Notifier.dismembered(player, part);
		} else {
			Notifier.destroyed(player, part, bleeds);
		}
	}
}
