package dev.captience.realisticdamage.persist;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import net.minecraft.util.Util;

/**
 * Serialization boundary: the body-model classes stay Minecraft-free, so all
 * codecs live here, converting through small immutable DTO records. Persisted
 * under the attachment key {@code fabric:attachments -> realisticdamage:data}.
 */
public final class RDCodecs {
	private RDCodecs() {}

	public static final Codec<BodyPart> BODY_PART = Codec.STRING.comapFlatMap(
			id -> {
				BodyPart part = BodyPart.byId(id);
				return part == null
						? DataResult.<BodyPart>error(() -> "Unknown body part: " + id)
						: DataResult.success(part);
			},
			BodyPart::id);

	public static final Codec<WoundSeverity> WOUND_SEVERITY = Codec.STRING.comapFlatMap(
			name -> {
				try {
					return DataResult.success(WoundSeverity.valueOf(name.toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					return DataResult.error(() -> "Unknown wound severity: " + name);
				}
			},
			severity -> severity.name().toLowerCase(Locale.ROOT));

	private record PartState(double condition, boolean broken, int strain, boolean destroyed, int ceiling, int scar) {
		static final Codec<PartState> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.DOUBLE.fieldOf("condition").forGetter(PartState::condition),
				Codec.BOOL.optionalFieldOf("broken", false).forGetter(PartState::broken),
				Codec.INT.optionalFieldOf("strain", 0).forGetter(PartState::strain),
				Codec.BOOL.optionalFieldOf("destroyed", false).forGetter(PartState::destroyed),
				// optionalFieldOf with a fixed default, never fieldOf and never Optional:
				// a 1.5 save has no such key and must decode silently, a pristine 1.6
				// body must write no key at all, and a malformed one must WARN and
				// default rather than drop the whole body back to pristine.
				Codec.INT.optionalFieldOf("ceiling", 0).forGetter(PartState::ceiling),
				// Same reasoning, and the same fixed default: an unscarred body must
				// write no key, so a pristine 1.6 save stays byte-identical to a
				// pristine 1.5 one.
				Codec.INT.optionalFieldOf("scar", 0).forGetter(PartState::scar)
		).apply(i, PartState::new));

		static PartState of(BodyPartData data) {
			return new PartState(data.condition(), data.broken(), data.strainPauseTicks(), data.destroyed(),
					data.ceilingBand(), data.scar().ordinal());
		}
	}

	private record WoundState(BodyPart part, WoundSeverity severity, double decay) {
		static final Codec<WoundState> CODEC = RecordCodecBuilder.create(i -> i.group(
				BODY_PART.fieldOf("part").forGetter(WoundState::part),
				WOUND_SEVERITY.fieldOf("severity").forGetter(WoundState::severity),
				Codec.DOUBLE.optionalFieldOf("decay", 0.0).forGetter(WoundState::decay)
		).apply(i, WoundState::new));
	}

	private record DataState(
			Map<BodyPart, PartState> body,
			double blood,
			double consciousness,
			boolean unconscious,
			int unconsciousTicks,
			boolean concussion,
			double concussionSeverity,
			boolean burned,
			List<WoundState> wounds
	) {
		static final Codec<DataState> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(BODY_PART, PartState.CODEC).fieldOf("body").forGetter(DataState::body),
				Codec.DOUBLE.fieldOf("blood").forGetter(DataState::blood),
				Codec.DOUBLE.fieldOf("consciousness").forGetter(DataState::consciousness),
				Codec.BOOL.optionalFieldOf("unconscious", false).forGetter(DataState::unconscious),
				Codec.INT.optionalFieldOf("unconscious_ticks", 0).forGetter(DataState::unconsciousTicks),
				Codec.BOOL.optionalFieldOf("concussion", false).forGetter(DataState::concussion),
				// 1.7: the concussion has a severity. "concussion" is still written so
				// an older build reading a newer save at least knows you are concussed;
				// a save from before 1.7 has no severity and gets the default.
				Codec.DOUBLE.optionalFieldOf("concussion_severity", 0.0).forGetter(DataState::concussionSeverity),
				Codec.BOOL.optionalFieldOf("burned", false).forGetter(DataState::burned),
				WoundState.CODEC.listOf().optionalFieldOf("wounds", List.of()).forGetter(DataState::wounds)
		).apply(i, DataState::new));
	}

	public static final Codec<RealisticDamageData> DATA = DataState.CODEC.xmap(RDCodecs::toData, RDCodecs::toState);

	private static DataState toState(RealisticDamageData data) {
		Map<BodyPart, PartState> body = Util.makeEnumMap(BodyPart.class, part -> PartState.of(data.part(part)));
		List<WoundState> wounds = data.wounds().stream()
				.map(w -> new WoundState(w.part(), w.severity(), w.decayProgress()))
				.toList();
		return new DataState(body, data.blood(), data.consciousness(), data.unconscious(),
				data.unconsciousTicks(), data.concussion(), data.concussionSeverity(), data.burned(), wounds);
	}

	private static RealisticDamageData toData(DataState state) {
		RealisticDamageData data = new RealisticDamageData();
		for (BodyPart part : BodyPart.values()) {
			// Missing parts (e.g. future model changes) default to full condition.
			PartState saved = state.body().get(part);
			if (saved != null) {
				BodyPartData target = data.part(part);
				// Ceiling first: setCondition clamps to it, so a save whose condition
				// sits above its ceiling decodes to the ceiling rather than throwing.
				// lowerCeilingTo sanitises, so a hand-edited band cannot get in here.
				target.lowerCeilingTo(saved.ceiling());
				// Condition next: setDestroyed pins the part at zero afterwards.
				target.setCondition(saved.condition());
				target.setBroken(saved.broken());
				target.strain(saved.strain());
				target.setDestroyed(saved.destroyed());
				// Last, and as a max — which makes it order-independent. setBroken
				// and setDestroyed above have already raised whatever scar a 1.5
				// save implies, so an existing world's currently-broken limb wakes
				// up marked for free; a written scar can never be lowered by a
				// load; and byOrdinal clamps, so a hand-edited value cannot throw.
				target.markScar(ScarLevel.byOrdinal(saved.scar()));
			}
		}
		data.setBlood(state.blood());
		if (state.concussionSeverity() > 0.0) {
			data.setConcussionSeverity(state.concussionSeverity());
		} else {
			data.setConcussion(state.concussion());
		}
		data.setBurned(state.burned());
		data.setUnconscious(state.unconscious());
		data.setUnconsciousTicks(state.unconsciousTicks());
		for (WoundState wound : state.wounds()) {
			data.wounds().add(new BleedWound(wound.part(), wound.severity(), wound.decay()));
		}
		// Consciousness last: its clamp depends on head condition and concussion.
		data.setConsciousness(state.consciousness());
		return data;
	}
}
