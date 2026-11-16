package dev.captience.realisticdamage.systems;

import java.util.ArrayList;
import java.util.List;

import dev.captience.realisticdamage.body.WoundSeverity;
import org.jspecify.annotations.Nullable;

/**
 * The numbers behind blood on a held item (2.0): how much a blow leaves on
 * the thing that dealt it, how marks pile up, how long they stay wet and how
 * fast water takes them off. Pure, so the rules can be tested without a game.
 *
 * <p>An item carries a short list of marks, oldest first. Each has its own
 * shape, colour and age, so a sword that has been through a zombie, an
 * enderman and a player shows all three. Marks only go up with blows and
 * down with water: dried blood does not flake off by itself, which is the
 * point of it.
 */
public final class BloodiedMath {
	private BloodiedMath() {}

	/** The most one mark holds. */
	public static final float MAX = 1.0F;
	/** The most marks an item keeps; a new one past this pushes the oldest, lowest one out. */
	public static final int LAYERS_MAX = 6;
	/** Amounts move in sixteenths, so an item's look changes in steps and not every tick. */
	public static final float STEP = 1.0F / 16.0F;
	/** How many looks an item has between clean and soaked, for the word in its tooltip. */
	public static final int STAGES = 4;
	/** Ticks from fresh to fully dried: half a minute. */
	public static final int DRY_TICKS = 600;
	/** How many shades a mark passes through as it dries. */
	public static final int WET_STEPS = 8;
	/** What a second in water or rain takes off a wet mark, and off a dried one. */
	public static final float WASH_WET = 0.34F;
	public static final float WASH_DRY = 0.17F;
	/** A blow into a wound that was already open smears; it does not pour. */
	public static final float INTO_OPEN_WOUND = 0.4F;
	/** What the blow that kills leaves, when it opened no wound of its own to judge by. */
	public static final float KILLING_BLOW = 0.34F;
	/** What taking a limb or a head off leaves. */
	public static final float SEVERING_BLOW = 0.6F;
	/** A mark that is still this wet takes more blood of its own kind and colour instead of a new mark starting. */
	public static final double JOINS_WHILE_WETTER_THAN = 0.25;
	/** How full the grip gets from the wielder's own arm. */
	public static final float GRIP_MAX = 0.6F;

	/** What one blow leaves on the weapon, by the wound it opened. */
	public static float fromWound(@Nullable WoundSeverity wound) {
		if (wound == null) {
			return 0.0F;
		}
		return switch (wound) {
			case MINOR -> 0.12F;
			case MODERATE -> 0.22F;
			case SEVERE -> 0.34F;
			case CRITICAL -> 0.50F;
		};
	}

	/** The specks a hard-bleeding wound throws back onto the weapon as it opens; none for a small one. */
	public static float spatter(@Nullable WoundSeverity wound) {
		if (wound == WoundSeverity.SEVERE) {
			return 0.12F;
		}
		return wound == WoundSeverity.CRITICAL ? 0.22F : 0.0F;
	}

	/** What a second of bleeding from the wielder's own arm runs onto the grip. */
	public static float gripPerSecond(@Nullable WoundSeverity armWound) {
		if (armWound == null || armWound == WoundSeverity.MINOR) {
			return 0.0F;
		}
		return switch (armWound) {
			case MODERATE -> 0.02F;
			case SEVERE -> 0.035F;
			default -> 0.05F;
		};
	}

	/** 1 just spilled, 0 long dry. A time the game has not reached yet reads as fresh. */
	public static double wetness(long now, long wetAt) {
		long age = now - wetAt;
		if (age <= 0L) {
			return 1.0;
		}
		return Math.clamp(1.0 - age / (double) DRY_TICKS, 0.0, 1.0);
	}

	/** Wetness as one of {@link #WET_STEPS} shades, {@code WET_STEPS} fresh down to 0 dry. */
	public static int wetStep(long now, long wetAt) {
		return (int) Math.ceil(wetness(now, wetAt) * WET_STEPS - 1.0e-9);
	}

	/** An amount on the sixteenth it is drawn at; anything above nothing is at least one. */
	public static int amountStep(float amount) {
		if (amount <= 0.0F) {
			return 0;
		}
		return Math.clamp(Math.round(amount / STEP), 1, 16);
	}

	/**
	 * The marks after more blood lands. It joins the newest mark of the same
	 * kind and colour that is still wet, wherever that is in the pile, so a
	 * flurry of cuts into one zombie is one growing streak and not six;
	 * anything else starts a new mark on top, in its own place.
	 */
	public static List<BloodLayer> added(List<BloodLayer> layers, BloodKind kind, String tint, float amount,
			int seed, long now) {
		if (amount <= 0.0F) {
			return layers;
		}
		List<BloodLayer> out = new ArrayList<>(layers);
		float cap = kind == BloodKind.GRIP ? GRIP_MAX : MAX;
		for (int i = out.size() - 1; i >= 0; i--) {
			BloodLayer layer = out.get(i);
			if (layer.kind() == kind && layer.tint().equals(tint)
					&& wetness(now, layer.wetAt()) > JOINS_WHILE_WETTER_THAN) {
				out.set(i, new BloodLayer(kind, tint, Math.min(cap, layer.amount() + amount), layer.seed(), now));
				return out;
			}
		}
		out.add(new BloodLayer(kind, tint, Math.min(cap, amount), seed, now));
		while (out.size() > LAYERS_MAX) {
			out.remove(0);
		}
		return out;
	}

	/**
	 * The grip's own mark, wherever it is in the pile: blood running down an
	 * arm keeps feeding the same mark instead of starting a new one every
	 * time something else lands on the blade in between.
	 */
	public static List<BloodLayer> grip(List<BloodLayer> layers, String tint, float amount, int seed, long now) {
		if (amount <= 0.0F) {
			return layers;
		}
		List<BloodLayer> out = new ArrayList<>(layers);
		for (int i = 0; i < out.size(); i++) {
			BloodLayer layer = out.get(i);
			if (layer.kind() == BloodKind.GRIP && layer.tint().equals(tint)) {
				out.set(i, new BloodLayer(BloodKind.GRIP, tint, Math.min(GRIP_MAX, layer.amount() + amount),
						layer.seed(), now));
				return out;
			}
		}
		return added(out, BloodKind.GRIP, tint, Math.min(GRIP_MAX, amount), seed, now);
	}

	/** One second's rinse: wet blood goes twice as fast as dried, and a mark rinsed to nothing is gone. */
	public static List<BloodLayer> washed(List<BloodLayer> layers, long now) {
		List<BloodLayer> out = new ArrayList<>(layers.size());
		for (BloodLayer layer : layers) {
			float left = layer.amount() - (wetness(now, layer.wetAt()) > 0.0 ? WASH_WET : WASH_DRY);
			if (left >= STEP * 0.5F) {
				out.add(layer.withAmount(left));
			}
		}
		return out;
	}

	/** All the blood on the item as one figure, 0 to 1, for the word a tooltip uses. */
	public static float total(List<BloodLayer> layers) {
		float sum = 0.0F;
		for (BloodLayer layer : layers) {
			sum += layer.amount();
		}
		return Math.min(MAX, sum);
	}

	/** Which of the tooltip's words this much blood gets: 0 clean, up to {@link #STAGES} soaked. */
	public static int stage(float amount) {
		if (amount <= 0.0F) {
			return 0;
		}
		if (amount < 0.30F) {
			return 1;
		}
		if (amount < 0.60F) {
			return 2;
		}
		if (amount < 0.90F) {
			return 3;
		}
		return 4;
	}

	/** Whether any mark is still wet. */
	public static boolean anyWet(List<BloodLayer> layers, long now) {
		for (BloodLayer layer : layers) {
			if (wetness(now, layer.wetAt()) > 0.0) {
				return true;
			}
		}
		return false;
	}
}
