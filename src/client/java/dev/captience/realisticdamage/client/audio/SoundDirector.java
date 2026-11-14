package dev.captience.realisticdamage.client.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.audio.SoundRecipe;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.net.SoundMaterial;
import dev.captience.realisticdamage.net.SoundSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Plays injury sounds from recipes (2.0): the one place that turns "a bone
 * broke over there, this badly" into sound.
 *
 * <p>Yours play inside your own head, with no position, in the AMBIENT
 * category that ConsciousnessAudio leaves alone as everything else fades: you
 * hear your own bones through your skull. Everybody else's play where they
 * happened, and quieter, in the PLAYERS category.
 */
public final class SoundDirector {
	private SoundDirector() {}

	/** How many small sounds (blood drops) may be going at once. */
	public static final int SMALL_BUDGET = 8;
	/** How long one small sound counts against the budget, in ticks. */
	private static final int SMALL_HOLD_TICKS = 16;

	private static final RandomSource RANDOM = RandomSource.create();
	/** Which variant each event last played, so the same one never comes twice running. */
	private static final Map<String, Integer> lastVariant = new HashMap<>();
	private static final List<Small> smalls = new ArrayList<>();

	/** One small sound in flight: when it started, how much it matters, what to stop. */
	private record Small(long tick, float weight, List<SoundInstance> layers) {}

	/** A layer waiting for its tick. */
	private record Pending(long due, SoundInstance layer) {}

	/**
	 * Layers that have not started yet. Held here rather than handed to the
	 * game's own delay queue, which offers no way to take one back: a small
	 * sound stopped to make room has to lose the layers it has not played too.
	 */
	private static final List<Pending> pending = new ArrayList<>();
	/** Client ticks that were not paused, counted here so it is the same clock in every world. */
	private static long ticks;

	/** Once per client tick. */
	public static void tick(Minecraft client) {
		if (client.isPaused()) {
			return;   // a delay is game time, as the game's own are
		}
		ticks++;
		if (pending.isEmpty()) {
			return;
		}
		for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
			Pending next = it.next();
			if (next.due() <= ticks) {
				it.remove();
				client.getSoundManager().play(next.layer());
			}
		}
	}

	/** What the last {@link #play} did; the tests read this, like Notifier's hooks. */
	public record Played(InjurySound event, SoundSet set, int variant, List<Identifier> files, List<SoundInstance> layers) {}

	private static @Nullable Played last;

	/** How many small sounds are counted against the budget right now; for the tests. */
	public static int smallsInFlight() {
		return smalls.size();
	}

	/** Forgets every small sound in flight without stopping it; for the tests. */
	public static void clearSmalls() {
		smalls.clear();
	}

	/** Whether this layer is still waiting for its tick; for the tests. */
	public static boolean isPending(SoundInstance layer) {
		for (Pending waiting : pending) {
			if (waiting.layer() == layer) {
				return true;
			}
		}
		return false;
	}

	public static @Nullable Played last() {
		return last;
	}

	public static void clearLast() {
		last = null;
	}

	/**
	 * @param set      the set to play; if it has no recipe for this event the
	 *                 other set's is used, so an unfinished set is never silent
	 * @param self     the listener's own body
	 * @param at       where it happened; unused when {@code self}
	 * @param strength 0 to 1
	 * @param volume   the listener's Injury Volume setting
	 * @return false if there was no recipe at all, and the caller should fall back
	 */
	public static boolean play(InjurySound event, SoundSet set, boolean self, Vec3 at, float strength,
			SoundMaterial material, float volume) {
		SoundRecipe recipe = SoundRecipes.get(set, event);
		SoundSet used = set;
		if (recipe == null) {
			for (SoundSet other : SoundSet.values()) {
				recipe = SoundRecipes.get(other, event);
				if (recipe != null) {
					used = other;
					break;
				}
			}
		}
		if (recipe == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		float level = volume * recipe.volumeAt(strength) * (self ? 1.0F : Balance.INJURY_SOUND_OTHERS_VOLUME);
		if (level <= 0.0F) {
			return true;
		}
		float weight = level / (1.0F + (self || client.player == null ? 0.0F
				: (float) client.player.position().distanceTo(at)));
		if (recipe.small() && !makeRoom(client.getSoundManager(), weight)) {
			return true;   // heard, as far as the caller is concerned: it was just one drop too many
		}

		List<SoundRecipe.Variant> variants = recipe.variantsFor(material);
		String key = used.id() + "/" + event.id() + "/" + material.id();
		int index = SoundRecipe.pickVariant(variants.size(), lastVariant.get(key), RANDOM);
		lastVariant.put(key, index);

		Identifier name = RealisticDamage.id("recipe/" + used.id() + "/" + event.id());
		SoundManager sounds = client.getSoundManager();
		List<SoundInstance> started = new ArrayList<>();
		List<Identifier> files = new ArrayList<>();
		// The subtitle rides on the first layer that plays and on no other, or
		// three layers would be three lines of the same text.
		String subtitle = recipe.subtitle();
		for (SoundRecipe.Layer layer : SoundRecipe.layersAt(variants.get(index), strength)) {
			if (layer.chance() < 1.0F && RANDOM.nextFloat() >= layer.chance()) {
				continue;
			}
			LayerSound sound = new LayerSound(name, layer.file(self), subtitle,
					self ? SoundSource.AMBIENT : SoundSource.PLAYERS,
					level * layer.volume(RANDOM), layer.pitch(RANDOM), (int) Balance.INJURY_SOUND_RADIUS);
			if (!self) {
				sound.at(at.x, at.y, at.z);
			}
			subtitle = null;
			if (layer.delay() > 0) {
				pending.add(new Pending(ticks + layer.delay(), sound));
			} else {
				sounds.play(sound);
			}
			started.add(sound);
			files.add(sound.file());
		}
		if (recipe.small()) {
			smalls.add(new Small(ticks, weight, started));
		}
		last = new Played(event, used, index, List.copyOf(files), List.copyOf(started));
		return true;
	}

	/**
	 * The budget for small sounds: a bad bleed makes hundreds of drops, and
	 * hundreds of drops is rain. When the budget is full, the one that matters
	 * least (quietest and farthest) is stopped to make room, unless the new one
	 * matters even less.
	 *
	 * @return whether the new sound may play
	 */
	private static boolean makeRoom(SoundManager sounds, float weight) {
		smalls.removeIf(small -> ticks - small.tick() > SMALL_HOLD_TICKS);
		if (smalls.size() < SMALL_BUDGET) {
			return true;
		}
		Small least = smalls.get(0);
		for (Small small : smalls) {
			if (small.weight() < least.weight()) {
				least = small;
			}
		}
		if (least.weight() >= weight) {
			return false;
		}
		for (SoundInstance layer : least.layers()) {
			sounds.stop(layer);
		}
		List<SoundInstance> stopped = least.layers();
		pending.removeIf(waiting -> stopped.contains(waiting.layer()));
		smalls.remove(least);
		return true;
	}
}
