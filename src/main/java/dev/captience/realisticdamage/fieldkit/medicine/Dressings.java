package dev.captience.realisticdamage.fieldkit.medicine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What is on a body's wounds (2.0, phase 4): bandages and tourniquets.
 *
 * <p><b>A bandage</b> sits on one region's wound and lets through only part of
 * it: a quarter of a minor wound, two fifths of a moderate one. A moderate
 * wound soaks it through in {@code BANDAGE_SOAK_TICKS} and it needs changing.
 * An arterial wound it cannot hold at all; one that turns arterial under it
 * pushes it off.
 *
 * <p><b>A tourniquet</b> on a limb stops that limb's bleed dead, and the limb
 * is Disabled while it is on: its condition is held at zero and what was held
 * is given back when it comes off. Left on for {@code TOURNIQUET_LIMIT_TICKS}
 * the limb dies, through the core's own destruction, stump and scar and all
 * (when limb destruction is on; under Grounded it stays Disabled).
 *
 * <p>Saved with the body. Under the Medicine switch: switched off, every
 * tourniquet comes off and gives its limb back, and bandages do nothing.
 *
 * <p><b>Never change the attached state's maps in place.</b> A sync to the
 * clients encodes it later, on the network thread, and a bandage put on or
 * taken off under it breaks the packet and drops the player. What comes on or
 * off goes through {@link #change}, on a copy that is then set. Only counters
 * and flags tick in place: a sync that reads one mid-change sends an old number.
 */
public final class Dressings {
	private Dressings() {}

	public static final class Bandage {
		public int ticks;
		public boolean soaked;

		public Bandage() {}

		Bandage(int ticks, boolean soaked) {
			this.ticks = ticks;
			this.soaked = soaked;
		}
	}

	public static final class Tourniquet {
		public int ticks;
		/** Condition taken off the limb to hold it Disabled, given back when it comes off. */
		public double held;
		public boolean warned;

		public Tourniquet() {}

		Tourniquet(int ticks, double held, boolean warned) {
			this.ticks = ticks;
			this.held = held;
			this.warned = warned;
		}
	}

	/** A body part by its id ("left_arm"). */
	static final Codec<BodyPart> PART_CODEC = Codec.STRING.comapFlatMap(id -> {
		BodyPart part = BodyPart.byId(id);
		return part != null ? com.mojang.serialization.DataResult.success(part)
				: com.mojang.serialization.DataResult.error(() -> "unknown body part " + id);
	}, BodyPart::id);

	private record BandageEntry(BodyPart part, int ticks, boolean soaked) {
		static final Codec<BandageEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
						PART_CODEC.fieldOf("part").forGetter(BandageEntry::part),
						Codec.INT.optionalFieldOf("ticks", 0).forGetter(BandageEntry::ticks),
						Codec.BOOL.optionalFieldOf("soaked", false).forGetter(BandageEntry::soaked))
				.apply(i, BandageEntry::new));
	}

	private record TourniquetEntry(BodyPart part, int ticks, double held, boolean warned) {
		static final Codec<TourniquetEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
						PART_CODEC.fieldOf("part").forGetter(TourniquetEntry::part),
						Codec.INT.optionalFieldOf("ticks", 0).forGetter(TourniquetEntry::ticks),
						Codec.DOUBLE.optionalFieldOf("held", 0.0).forGetter(TourniquetEntry::held),
						Codec.BOOL.optionalFieldOf("warned", false).forGetter(TourniquetEntry::warned))
				.apply(i, TourniquetEntry::new));
	}

	public static final class State {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
						BandageEntry.CODEC.listOf().optionalFieldOf("bandages", List.of()).forGetter(State::bandageList),
						TourniquetEntry.CODEC.listOf().optionalFieldOf("tourniquets", List.of())
								.forGetter(State::tourniquetList))
				.apply(i, State::new));

		public final Map<BodyPart, Bandage> bandages = new EnumMap<>(BodyPart.class);
		public final Map<BodyPart, Tourniquet> tourniquets = new EnumMap<>(BodyPart.class);

		public State() {}

		private State(List<BandageEntry> bandageList, List<TourniquetEntry> tourniquetList) {
			for (BandageEntry entry : bandageList) {
				bandages.put(entry.part(), new Bandage(entry.ticks(), entry.soaked()));
			}
			for (TourniquetEntry entry : tourniquetList) {
				tourniquets.put(entry.part(), new Tourniquet(entry.ticks(), entry.held(), entry.warned()));
			}
		}

		private List<BandageEntry> bandageList() {
			return bandages.entrySet().stream()
					.map(e -> new BandageEntry(e.getKey(), e.getValue().ticks, e.getValue().soaked)).toList();
		}

		private List<TourniquetEntry> tourniquetList() {
			return tourniquets.entrySet().stream()
					.map(e -> new TourniquetEntry(e.getKey(), e.getValue().ticks, e.getValue().held,
							e.getValue().warned))
					.toList();
		}

		public boolean empty() {
			return bandages.isEmpty() && tourniquets.isEmpty();
		}

		/** A deep copy: its maps and entries are its own. */
		public State copy() {
			State copy = new State();
			bandages.forEach((part, b) -> copy.bandages.put(part, new Bandage(b.ticks, b.soaked)));
			tourniquets.forEach((part, t) -> copy.tourniquets.put(part, new Tourniquet(t.ticks, t.held, t.warned)));
			return copy;
		}
	}

	/**
	 * Synced to every client, like the body snapshot: a watcher's blood drops
	 * from a bandaged wound slower and from a tourniqueted limb not at all. A
	 * change is published by setting a fresh copy ({@link #publish}): Fabric
	 * sends an attachment when it is set, not when it is mutated.
	 */
	public static final AttachmentType<State> STATE = AttachmentRegistry.create(RealisticDamage.id("dressings"),
			builder -> builder.initializer(State::new).persistent(State.CODEC)
					.syncWith(net.minecraft.network.codec.ByteBufCodecs.fromCodec(State.CODEC),
							net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));

	/** Bodies with anything on them, ticked each server tick. */
	private static final Set<LivingEntity> DRESSED = Collections.newSetFromMap(new IdentityHashMap<>());

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Dressings::tick);
	}

	public static @Nullable State peek(LivingEntity body) {
		return body.getAttached(STATE);
	}


	/** Players log in with their dressings saved; start ticking them. */
	public static void loggedIn(ServerPlayer player) {
		State state = peek(player);
		if (state != null && !state.empty()) {
			DRESSED.add(player);
		}
	}

	/** Whether a wound can take a bandage at all: an arterial one cannot. */
	public static boolean bandageable(WoundSeverity severity) {
		return !BloodFlow.arterial(severity.bloodPerSecond());
	}

	public static boolean limb(BodyPart part) {
		return part.isLimb();
	}

	/** What this region's wound lets through, given what is on it. 1 when nothing is, or Medicine is off. */
	public static double factor(LivingEntity body, BleedWound wound) {
		return Half.MEDICINE.on() ? factor(peek(body), wound.part(), wound.severity()) : 1.0;
	}

	/** The same, from a state alone: shared by the server's bleeding and a client's drawing of it. */
	public static double factor(@Nullable State state, BodyPart part, WoundSeverity severity) {
		if (state == null || severity == null) {
			return 1.0;
		}
		if (state.tourniquets.containsKey(part)) {
			return 0.0;
		}
		Bandage bandage = state.bandages.get(part);
		if (bandage == null || bandage.soaked || !bandageable(severity)) {
			return 1.0;
		}
		return severity == WoundSeverity.MINOR ? Balance.BANDAGE_MINOR_FACTOR : Balance.BANDAGE_MODERATE_FACTOR;
	}

	/**
	 * Changes a body's dressings: on a copy of them, which is then set, so a
	 * sync still encoding the old state never sees it change and Fabric sends
	 * the new one.
	 */
	public static State change(LivingEntity body, Consumer<State> edit) {
		State current = peek(body);
		State next = current != null ? current.copy() : new State();
		edit.accept(next);
		body.setAttached(STATE, next);
		return next;
	}

	/** Sends a body's dressings to the clients that see it again (after a counter or flag changed). */
	public static void publish(LivingEntity body) {
		if (peek(body) != null) {
			change(body, state -> {});
		}
	}

	// --- putting things on and taking them off ---

	public static void bandage(LivingEntity body, BodyPart part) {
		change(body, state -> state.bandages.put(part, new Bandage()));
		DRESSED.add(body);
	}

	/** Takes the bandage off a region (stitched or burnt shut under it). False when there was none. */
	public static boolean unbandage(LivingEntity body, BodyPart part) {
		State state = peek(body);
		if (state == null || !state.bandages.containsKey(part)) {
			return false;
		}
		change(body, next -> next.bandages.remove(part));
		return true;
	}

	public static void tourniquet(LivingEntity body, BodyPart part) {
		State state = peek(body);
		if (state == null || !state.tourniquets.containsKey(part)) {
			change(body, next -> {
				Tourniquet tourniquet = new Tourniquet();
				hold(body, tourniquet, part);
				next.tourniquets.put(part, tourniquet);
			});
			DRESSED.add(body);
		}
	}

	/** Takes a tourniquet off and gives the limb back what was held. False when there was none. */
	public static boolean loosen(LivingEntity body, BodyPart part) {
		State state = peek(body);
		Tourniquet tourniquet = state != null ? state.tourniquets.get(part) : null;
		if (tourniquet == null) {
			return false;
		}
		change(body, next -> next.tourniquets.remove(part));
		BodyPartData limb = RDAttachments.get(body).part(part);
		if (!limb.destroyed()) {
			limb.setCondition(Math.min(limb.condition() + tourniquet.held, limb.ceiling()));
		}
		RDAttachments.pushSnapshot(body);
		return true;
	}

	/** The limb whose tourniquet has been on the longest, or null. */
	public static @Nullable BodyPart oldestTourniquet(LivingEntity body) {
		State state = peek(body);
		if (state == null) {
			return null;
		}
		BodyPart oldest = null;
		int most = -1;
		for (Map.Entry<BodyPart, Tourniquet> entry : state.tourniquets.entrySet()) {
			if (entry.getValue().ticks > most) {
				most = entry.getValue().ticks;
				oldest = entry.getKey();
			}
		}
		return oldest;
	}

	/** Holds a tourniqueted limb at Disabled, keeping what was taken. */
	private static void hold(LivingEntity body, Tourniquet tourniquet, BodyPart part) {
		BodyPartData limb = RDAttachments.get(body).part(part);
		if (limb.condition() > 0.0) {
			tourniquet.held += limb.condition();
			limb.setCondition(0.0);
			RDAttachments.pushSnapshot(body);
		}
	}

	// --- each tick ---

	private static void tick(MinecraftServer server) {
		if (DRESSED.isEmpty()) {
			return;
		}
		boolean on = Half.MEDICINE.on();
		Iterator<LivingEntity> it = DRESSED.iterator();
		while (it.hasNext()) {
			LivingEntity body = it.next();
			State state = peek(body);
			if (body.isRemoved() || !body.isAlive() || state == null) {
				it.remove();
				continue;
			}
			if (!on) {
				// Switched off: everything comes off at once, and every limb gets back what was held.
				for (BodyPart part : List.copyOf(state.tourniquets.keySet())) {
					loosen(body, part);
				}
				if (!peek(body).bandages.isEmpty()) {
					change(body, next -> next.bandages.clear());
				}
				it.remove();
				continue;
			}
			tickBody(body, state);
			State now = peek(body);
			if (now == null || now.empty()) {
				it.remove();
			}
		}
	}

	/** One tick of one dressed body. Public for the game tests, which drive bodies nothing else ticks. */
	public static void tickBody(LivingEntity body, State state) {
		RealisticDamageData data = RDAttachments.get(body);
		boolean changed = false;
		// Counters and flags tick in place; what comes off is gathered and taken off a copy.
		List<BodyPart> bandagesOff = new ArrayList<>();
		List<BodyPart> tourniquetsOff = new ArrayList<>();
		for (Map.Entry<BodyPart, Bandage> entry : state.bandages.entrySet()) {
			BleedWound wound = woundOn(data, entry.getKey());
			Bandage bandage = entry.getValue();
			bandage.ticks++;
			if (wound == null) {
				// Healed under it: the bandage has done its job.
				bandagesOff.add(entry.getKey());
				continue;
			}
			if (!bandageable(wound.severity())) {
				bandagesOff.add(entry.getKey());
				say(body, "realisticdamage.fieldkit.bandage.burst", entry.getKey());
				continue;
			}
			if (!bandage.soaked && wound.severity() != WoundSeverity.MINOR && bandage.ticks >= Balance.BANDAGE_SOAK_TICKS) {
				bandage.soaked = true;
				changed = true;
				say(body, "realisticdamage.fieldkit.bandage.soaked", entry.getKey());
			}
		}
		for (Map.Entry<BodyPart, Tourniquet> entry : List.copyOf(state.tourniquets.entrySet())) {
			BodyPart part = entry.getKey();
			Tourniquet tourniquet = entry.getValue();
			BodyPartData limb = data.part(part);
			if (limb.destroyed()) {
				tourniquetsOff.add(part);
				continue;
			}
			tourniquet.ticks++;
			hold(body, tourniquet, part);
			if (!tourniquet.warned && tourniquet.ticks >= Balance.TOURNIQUET_WARN_TICKS) {
				tourniquet.warned = true;
				say(body, "realisticdamage.fieldkit.tourniquet.cold", part);
			}
			if (tourniquet.ticks >= Balance.TOURNIQUET_LIMIT_TICKS && ServerConfig.get().enableLimbDestruction) {
				tourniquetsOff.add(part);
				say(body, "realisticdamage.fieldkit.tourniquet.dead", part);
				DestructionLogic.destroy(body, data, part);
				RDAttachments.pushSnapshot(body);
			}
		}
		if (!bandagesOff.isEmpty() || !tourniquetsOff.isEmpty()) {
			change(body, next -> {
				bandagesOff.forEach(next.bandages::remove);
				tourniquetsOff.forEach(next.tourniquets::remove);
			});
		} else if (changed) {
			publish(body);
		}
	}

	public static @Nullable BleedWound woundOn(RealisticDamageData data, BodyPart part) {
		for (BleedWound wound : data.wounds()) {
			if (wound.part() == part) {
				return wound;
			}
		}
		return null;
	}

	static void say(LivingEntity body, String key, BodyPart part) {
		if (body instanceof Player player && !player.level().isClientSide()) {
			player.sendOverlayMessage(Component.translatable(key, partName(part)).withStyle(ChatFormatting.GRAY));
		}
	}

	public static Component partName(BodyPart part) {
		return Component.translatable("realisticdamage.fieldkit.part." + part.id());
	}
}
