package dev.captience.realisticdamage.fieldkit.brew;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.FieldKitEffect;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The brewed effects (2.0, phase 3). Each is a status effect plus vanilla
 * potions, so vanilla supplies the drinkable, splash, lingering and
 * tipped-arrow forms, and milk clears them like any other effect. What an
 * effect already did stays done.
 *
 * <p>The effects themselves carry almost no behaviour: they are markers the
 * body's own systems ask about (see the {@code fieldkit.mixin} hooks), so a
 * Haemorrhage bleed is the same bleed as any other, only easier to open.
 */
public final class BrewedEffects {
	private BrewedEffects() {}

	/** The blood stops clotting: bleeds open easier, worse, run past the cap and fight a hand off. */
	public static final Holder<MobEffect> HAEMORRHAGE =
			Kit.effect("haemorrhage", new Haemorrhage(), Half.WEAPONS);

	/** The last-two-hearts surge when you choose, the crash after it, and a racing heart. */
	public static final Holder<MobEffect> ADRENALINE =
			Kit.effect("adrenaline", new Adrenaline(), Half.WEAPONS);

	public static void init() {
		// Class loading registers the holders above.
	}

	/** The level a body is under an effect at (0 = I), or -1 when it is not. */
	public static int level(LivingEntity entity, Holder<MobEffect> effect) {
		MobEffectInstance instance = entity.getEffect(effect);
		return instance == null ? -1 : instance.getAmplifier();
	}

	/** Under this effect, with its half on: the question every hook asks. */
	public static boolean active(LivingEntity entity, Holder<MobEffect> effect) {
		return entity.hasEffect(effect) && Kit.halfOf(effect).map(Half::on).orElse(false);
	}

	static final class Haemorrhage extends FieldKitEffect {
		Haemorrhage() {
			super(Half.WEAPONS, MobEffectCategory.HARMFUL, 0x8A0F14);
		}
	}

	/**
	 * The surge is the body's own ({@code AdrenalineMath}), started on demand: a
	 * player who drinks it surges for as long as the effect lasts and crashes
	 * after, exactly as they would at two hearts. It shares that surge's
	 * refractory, so a second dose before the heart has recovered is refused,
	 * and a natural surge spends it too. Mobs have no surge; on them it is only
	 * the racing heart.
	 */
	static final class Adrenaline extends FieldKitEffect {
		Adrenaline() {
			super(Half.WEAPONS, MobEffectCategory.NEUTRAL, 0xF2711C);
		}

		@Override
		public void onEffectStarted(LivingEntity mob, int amplifier) {
			if (mob.level().isClientSide() || !(mob instanceof ServerPlayer player) || !half().on()) {
				return;
			}
			MobEffectInstance instance = player.getEffect(ADRENALINE);
			int ticks = instance == null ? Balance.ADRENALINE_POTION_TICKS : instance.getDuration();
			AdrenalineMath.State state = RDAttachments.get(player).adrenaline();
			if (state.phase == AdrenalineMath.Phase.SURGE) {
				state.ticks = Math.max(state.ticks, ticks);
				return;
			}
			state.phase = AdrenalineMath.Phase.SURGE;
			state.ticks = ticks;
			state.refractory = Balance.ADRENALINE_REFRACTORY_TICKS;
			state.rearmed = false;
			Notifier.message(player, "realisticdamage.msg.adrenaline");
		}
	}

	/**
	 * Whether a body turns a dose of Adrenaline away: a player whose heart is
	 * still recovering from a surge, drunk or natural, and not already on one.
	 */
	public static boolean refusesAdrenaline(LivingEntity entity) {
		return entity instanceof ServerPlayer player && !player.hasEffect(ADRENALINE)
				&& RDAttachments.get(player).adrenaline().refractory > 0;
	}

	/** The effect ended (ran out, milk, a switch): a drunk surge ends with it and the crash begins. */
	public static void adrenalineRemoved(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			AdrenalineMath.State state = RDAttachments.get(player).adrenaline();
			if (state.phase == AdrenalineMath.Phase.SURGE) {
				state.ticks = 1;
			}
		}
	}
}
