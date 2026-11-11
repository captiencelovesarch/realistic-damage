package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.systems.NeckMath;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Necks (2.0): the one that snaps and the one that is only wrenched.
 *
 * <p>Every cause becomes a strain ({@link NeckMath}). At 1 the neck breaks:
 * instant death through a damage type no totem stops, a crack, and a ragdoll
 * whose head hangs with nothing holding it. Above the whiplash bar it is
 * wrenched instead, and stays stiff for a while: a player's view turns slower
 * (client side) and a mob's head and aim go slow and wide.
 *
 * <p>The causes, and where they are read:
 * <ul>
 * <li>a limp or gliding body landing head first, and a dive into water too
 * shallow to stop it (the trauma pipeline's landing, fed by the fall mixin and
 * {@code ShallowWaterMixin});</li>
 * <li>an elytra into a wall at speed ({@code FLY_INTO_WALL});</li>
 * <li>a huge blunt blow to the head;</li>
 * <li>and, for whiplash only, a landing or a blow hard enough to knock you
 * flat.</li>
 * </ul>
 *
 * <p>A blow that kills by itself never reaches the pipeline (Fabric's
 * AFTER_DAMAGE skips it), so {@link #lethal} looks at the killing blow too:
 * the body dies of what killed it, and its neck goes with it.
 */
public final class BrokenNeck {
	private BrokenNeck() {}

	/** Whether this body has a neck to break or wrench, right now. */
	public static boolean can(LivingEntity body) {
		if (!MobAnatomy.participates(body) || MobAnatomy.boss(body) || !MobAnatomy.plan(body).has(BodyPart.HEAD)) {
			return false;
		}
		RealisticDamageData data = RDAttachments.get(body);
		if (data.headSevered() || data.part(BodyPart.HEAD).destroyed()) {
			return false;
		}
		return !(body instanceof Player player) || !(player.isCreative() || player.isSpectator());
	}

	/**
	 * Limp: nothing is holding the body's head up as it lands. The server has
	 * no idea which way a ragdoll is facing, so a body that is out cold, knocked
	 * flat, in a microsleep or gliding counts as landing head first.
	 */
	public static boolean headFirst(LivingEntity body, RealisticDamageData data) {
		return data.unconscious() || data.knockedDown() || data.microsleeping() || body.isFallFlying();
	}

	/**
	 * One strain on a living body's neck: snaps it at 1, wrenches it above the
	 * whiplash bar, does nothing below that.
	 *
	 * @param by who did it, if anyone: credited with the kill
	 * @return whether the neck snapped (the body is dead)
	 */
	public static boolean strain(LivingEntity body, RealisticDamageData data, double strain, @Nullable Entity by) {
		if (strain < Balance.WHIPLASH_MIN_STRAIN || !body.isAlive() || !can(body)) {
			return false;
		}
		ServerConfig config = ServerConfig.get();
		// A totem that just pulled you back from this very blow has spent its
		// death: the neck is only wrenched, as badly as a neck can be.
		if (NeckMath.breaks(strain) && config.enableBrokenNecks && !data.totemSaved(body.tickCount)) {
			snap(body, data, by);
			return true;
		}
		wrench(body, data, NeckMath.severity(Math.min(strain, 0.999)));
		return false;
	}

	/** Whiplash of this severity (0 to 1), if whiplash is on. */
	public static void wrench(LivingEntity body, RealisticDamageData data, double severity) {
		if (!ServerConfig.get().enableWhiplash || !body.isAlive() || !can(body)) {
			return;
		}
		boolean had = data.whiplashed();
		if (data.wrench(severity)) {
			Notifier.message(body, had ? "realisticdamage.msg.whiplash_worse" : "realisticdamage.msg.whiplash");
			MobAnatomy.track(body);
			RDAttachments.pushSnapshot(body);
		}
	}

	/** The neck snaps: the crack, the loose head, and death. */
	public static void snap(LivingEntity body, RealisticDamageData data, @Nullable Entity by) {
		data.setNeckBroken(true);
		data.clearWhiplash();
		Notifier.injurySound(body, InjurySound.NECK_CRACK);
		RDAttachments.pushSnapshot(body);
		ServerLevel level = (ServerLevel) body.level();
		body.hurtServer(level, RDDamageTypes.neckBroken(level, by), Float.MAX_VALUE);
	}

	/**
	 * At ALLOW_DEATH: would the blow that is killing this body have snapped its
	 * neck as well? Stashed rather than applied, because a totem can still say
	 * no. {@link #died} applies it.
	 */
	public static void lethal(LivingEntity body, DamageSource source, float amount) {
		if (!ServerConfig.get().enableBrokenNecks || !can(body) || source.is(RDDamageTypes.INTERNAL)) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(body);
		double strain = 0.0;
		ClassifiedDamage classified = DamageClassifier.classify(body, source);
		if (source.is(DamageTypes.FLY_INTO_WALL)) {
			strain = NeckMath.wallImpact(amount);
		} else if (classified != null && classified.category() == DamageCategory.FALL
				&& data.pendingFallHeight() > 0.0 && data.pendingFallHeadFirst()) {
			strain = NeckMath.limpLanding(data.pendingFallHeight() * data.pendingFallSurface());
		} else if (classified != null && classified.category() == DamageCategory.HEAVY_BLUNT
				&& classified.routing() == ClassifiedDamage.Routing.LOCATIONAL
				&& ServerConfig.get().enableHitLocation
				&& BodyHitResolver.resolve(body, source) == BodyPart.HEAD) {
			// Scored the way the pipeline would have scored it, had it lived to see it.
			double violence = DeathMath.violenceFromBlow(amount, body.getMaxHealth());
			strain = violence >= Balance.NECK_KILL_VIOLENCE ? NeckMath.BREAKS : 0.0;
		}
		if (NeckMath.breaks(strain)) {
			data.stashNeck(body.tickCount);
		}
	}

	/**
	 * At AFTER_DEATH, before the corpse packet goes: the neck that went with
	 * the death, so the ragdoll's head hangs. The death message stays what
	 * killed it (the fall, the wall).
	 */
	public static void died(LivingEntity body, RealisticDamageData data) {
		if (!data.consumeNeck(body.tickCount) || data.neckBroken() || data.headSevered()) {
			return;
		}
		data.setNeckBroken(true);
		Notifier.injurySound(body, InjurySound.NECK_CRACK);
		RDAttachments.pushSnapshot(body);
	}
}

