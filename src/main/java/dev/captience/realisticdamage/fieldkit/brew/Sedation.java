package dev.captience.realisticdamage.fieldkit.brew;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import dev.captience.realisticdamage.systems.HealingSystem;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * Sedation on a body (2.0, phase 3): how much is in it, what stage that is, and
 * what each stage does. The numbers are {@link SedationMath}'s.
 *
 * <ol>
 *   <li><b>Drowsy:</b> consciousness held under {@code SEDATION_DROWSY_CAP}, a
 *   little slower; the existing symptoms of a fading mind do the rest.</li>
 *   <li><b>Stumbling:</b> held just over the wake bar and under the microsleep
 *   one, so a player nods off in episodes; slower still, and any blow puts
 *   the body down.</li>
 *   <li><b>Out:</b> held at zero: the existing collapse. A mob drops into its
 *   knockdown ragdoll, and can be dragged.</li>
 *   <li><b>Overdose:</b> breathing stops. The hypoxia debt builds as if under
 *   water, and once it is all run up the body dies of it.</li>
 * </ol>
 *
 * <p>It wears off with time, and a body that was out wakes back up through
 * the stages, groggy. Milk clears what is in the body; consciousness already
 * lost comes back the ordinary way. Bosses and the mindless are immune.
 *
 * <p>The drinkable potion is the sleeping draught: you go out where you stand,
 * and waking from it is a night's sleep ({@link HealingSystem#applySleepRecovery}),
 * lying on the floor where everything hunts first.
 */
public final class Sedation {
	private Sedation() {}

	/** What is in a body. Saved with it: logging out is not a cure. */
	public static final class State {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
						Codec.DOUBLE.optionalFieldOf("pending", 0.0).forGetter(s -> s.pending),
						Codec.DOUBLE.optionalFieldOf("level", 0.0).forGetter(s -> s.level),
						Codec.BOOL.optionalFieldOf("draught", false).forGetter(s -> s.draught),
						Codec.BOOL.optionalFieldOf("was_out", false).forGetter(s -> s.wasOut))
				.apply(i, State::new));

		/** Doses given and not yet taken hold. */
		public double pending;
		/** Doses taken hold and not yet cleared. */
		public double level;
		/** The last dose was drunk: waking from it is a night's sleep. */
		public boolean draught;
		/** Out cold last tick. */
		public boolean wasOut;
		/** The stage last told to the player, and last applied to the body's speed. */
		transient Stage told = Stage.NONE;
		transient Stage applied = Stage.NONE;

		public State() {}

		State(double pending, double level, boolean draught, boolean wasOut) {
			this.pending = pending;
			this.level = level;
			this.draught = draught;
			this.wasOut = wasOut;
		}

		public boolean clear() {
			return pending + level <= 1.0e-6;
		}
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(RealisticDamage.id("sedation"),
			builder -> builder.initializer(State::new).persistent(State.CODEC));

	/** "%1$s stopped breathing": the overdose. */
	public static final ResourceKey<DamageType> OVERDOSE = ResourceKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath(RealisticDamage.MOD_ID, "sedative_overdose"));

	private static final Identifier SLOW = RealisticDamage.id("sedation_slow");

	public static void init() {
		// Class loading registers the attachment above.
	}

	public static @Nullable State peek(LivingEntity entity) {
		return entity.getAttached(STATE);
	}

	public static Stage stage(LivingEntity entity) {
		State state = peek(entity);
		return state == null ? Stage.NONE : SedationMath.stage(state.level, MobAnatomy.vitalityScale(entity));
	}

	/** Bosses, the mindless and anything without a body to sedate turn it away. */
	public static boolean refuses(LivingEntity entity) {
		return !MobAnatomy.participates(entity) || !MobAnatomy.feelsConsciousness(entity);
	}

	/** Breathing has stopped: read by the consciousness system's "starved" test. */
	public static boolean overdosed(LivingEntity entity) {
		return Half.WEAPONS.on() && stage(entity) == Stage.OVERDOSE;
	}

	/** One application: an arrow, a drink, a splash, a breath of the cloud. */
	public static void dose(LivingEntity entity, MobEffectInstance applied) {
		State state = entity.getAttachedOrCreate(STATE);
		state.pending += SedationMath.dose(applied.getAmplifier(), applied.getDuration());
		MobAnatomy.track(entity);
	}

	/** A drunk dose: the sleeping draught. */
	public static void drunk(ServerPlayer player) {
		player.getAttachedOrCreate(STATE).draught = true;
	}

	/** One tick of a sedated body, from the effect's own tick. False once it is clear. */
	public static boolean tick(ServerLevel level, LivingEntity entity) {
		State state = entity.getAttachedOrCreate(STATE);
		double taken = SedationMath.absorbed(state.pending);
		state.pending -= taken;
		state.level += taken;
		double scale = MobAnatomy.vitalityScale(entity);
		state.level -= SedationMath.cleared(state.pending, state.level, scale);
		Stage stage = SedationMath.stage(state.level, scale);
		slow(entity, state, stage);
		// Its body has to be ticked for the stage's hold on consciousness to
		// bite, injured or not.
		MobAnatomy.track(entity);

		RealisticDamageData data = RDAttachments.get(entity);
		boolean out = data.unconscious();
		if (state.wasOut && !out && state.draught && entity instanceof ServerPlayer player) {
			// Slept it off, on the floor, wherever that was.
			HealingSystem.applySleepRecovery(player, data);
			state.draught = false;
			Notifier.message(player, "realisticdamage.fieldkit.sedation.slept");
		}
		state.wasOut = out;

		if (stage == Stage.OVERDOSE && data.hypoxia() >= Balance.HYPOXIA_DEBT_MAX
				&& level.getGameTime() % 20L == 0L) {
			entity.hurtServer(level, level.damageSources().source(OVERDOSE),
					(float) (Balance.SEDATION_OVERDOSE_DAMAGE_PER_SEC * MobAnatomy.vitalityScale(entity)));
		}

		if (entity instanceof ServerPlayer player && stage != state.told) {
			state.told = stage;
			if (ServerPlayNetworking.canSend(player, SedationPayload.TYPE)) {
				ServerPlayNetworking.send(player, new SedationPayload(stage.ordinal()));
			}
		}

		if (state.clear()) {
			return false;
		}
		// The effect lasts as long as the drug does: a potion's own duration
		// is only how long the dose takes to give.
		MobEffectInstance effect = entity.getEffect(BrewedEffects.SEDATION);
		int left = SedationMath.ticksToClear(state.pending, state.level, scale);
		if (effect != null && left > effect.getDuration() + 20) {
			entity.forceAddEffect(new MobEffectInstance(BrewedEffects.SEDATION, left, effect.getAmplifier(),
					effect.isAmbient(), effect.isVisible(), effect.showIcon()), null);
		}
		return true;
	}

	/** The effect ended — worn off, milk, a switch: the drug is gone; what it did stays done. */
	public static void removed(LivingEntity entity) {
		State state = peek(entity);
		if (state == null) {
			return;
		}
		state.pending = 0.0;
		state.level = 0.0;
		state.draught = false;
		state.wasOut = false;
		slow(entity, state, Stage.NONE);
		if (entity instanceof ServerPlayer player && state.told != Stage.NONE) {
			state.told = Stage.NONE;
			if (ServerPlayNetworking.canSend(player, SedationPayload.TYPE)) {
				ServerPlayNetworking.send(player, new SedationPayload(Stage.NONE.ordinal()));
			}
		}
	}

	private static void slow(LivingEntity entity, State state, Stage stage) {
		if (stage == state.applied) {
			return;
		}
		state.applied = stage;
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(SLOW);
		double slow = SedationMath.slow(stage);
		if (slow > 0.0) {
			speed.addTransientModifier(new AttributeModifier(SLOW, -slow,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
