package dev.captience.realisticdamage.client;

import java.util.HashMap;
import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.BodyStainMix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Blood on the body itself (1.5), tracked per region per player.
 *
 * <p>The layer that answers "look down and see it", and the only one that is
 * legible without watching for a drop at the right moment: a cut left arm
 * becomes a bloody left arm, on your own model and on everyone else's, and it
 * stays bloody after the wound closes because blood dries on - until water
 * takes it off: wading rinses the legs, swimming all of it, rain all of it
 * slowly (see {@link BodyStainMix#washPerSecond}).
 *
 * <p>Purely client-side and derived from the synced snapshot, so it costs
 * nothing on the wire. It is deliberately <em>not</em> in the snapshot itself:
 * how filthy a limb has got is an accumulation over time, and syncing it would
 * mean either sending it constantly or quantising it into visible steps.
 */
public final class BloodBody {
	private BloodBody() {}

	/** Per player, one accumulated stain level per region. */
	private static final Map<Integer, double[]> stains = new HashMap<>();

	/**
	 * Per player, how much of each region's stain is somebody else's blood, and
	 * whose (2.0). Only bodies that have been splashed have an entry. See
	 * {@link BodyStainMix} for how the two are told apart when drawn.
	 */
	private static final Map<Integer, double[]> foreign = new HashMap<>();
	private static final Map<Integer, BloodTint[]> foreignTint = new HashMap<>();

	/**
	 * The colour each region's stain is drawn in where it is not the body's own,
	 * or null when all of it is: a null entry means "this body's own blood".
	 */
	public static BloodTint @org.jspecify.annotations.Nullable [] tints(int entityId) {
		double[] share = foreign.get(entityId);
		double[] levels = stains.get(entityId);
		if (share == null || levels == null) {
			return null;
		}
		BloodTint[] whose = foreignTint.get(entityId);
		BloodTint[] out = null;
		for (int i = 0; i < share.length; i++) {
			if (whose[i] != null && BodyStainMix.foreignShows(levels[i], share[i])) {
				if (out == null) {
					out = new BloodTint[share.length];
				}
				out[i] = whose[i];
			}
		}
		return out;
	}

	/** The colour one region's stain is drawn in: somebody else's when most of it is theirs, else {@code own}. */
	public static BloodTint tint(int entityId, BodyPart part, BloodTint own) {
		BloodTint[] tints = tints(entityId);
		return tints == null || tints[part.ordinal()] == null ? own : tints[part.ordinal()];
	}

	/** How bloody a region looks, 0 to 1. */
	public static double stain(int entityId, BodyPart part) {
		double[] levels = stains.get(entityId);
		return levels == null ? 0.0 : levels[part.ordinal()];
	}

	/**
	 * Every region's stain level for the render state to carry, or null when this
	 * player is clean — which is the common case, and null lets the render layer
	 * return before it touches anything.
	 */
	public static float @org.jspecify.annotations.Nullable [] stains(int entityId) {
		double[] levels = stains.get(entityId);
		if (levels == null) {
			return null;
		}
		float[] out = null;
		for (int i = 0; i < levels.length; i++) {
			if (levels[i] > 0.004) {
				if (out == null) {
					out = new float[levels.length];
				}
				out[i] = (float) levels[i];
			}
		}
		return out;
	}

	/**
	 * A drop landed on this player. Attributed to whichever region owns the
	 * height it struck, so blood thrown at someone's legs lands on their legs.
	 */
	public static void splash(Player player, double atY, double dropScale) {
		splash(player, atY, dropScale, BloodTint.RED);
	}

	/** As above, keeping whose blood it was: a drop always comes from somebody else (see {@code BloodDrop}). */
	public static void splash(Player player, double atY, double dropScale, BloodTint tint) {
		splash(player, player.getX(), atY, player.getZ(), dropScale, tint);
	}

	/** As above, from where the drop actually struck, so its side of the body is known too. */
	public static void splash(Player player, double atX, double atY, double atZ, double dropScale, BloodTint tint) {
		double[] levels = stains.computeIfAbsent(player.getId(), id -> new double[BodyPart.values().length]);
		int part = regionAt(player, atX, atY, atZ).ordinal();
		double before = levels[part];
		levels[part] = Math.min(Balance.BODY_STAIN_MAX,
				before + Balance.BODY_STAIN_PER_DROP * (0.4 + 0.6 * dropScale));
		double[] share = foreign.computeIfAbsent(player.getId(), id -> new double[BodyPart.values().length]);
		BloodTint[] whose = foreignTint.computeIfAbsent(player.getId(), id -> new BloodTint[BodyPart.values().length]);
		share[part] = Math.min(levels[part], share[part] + (levels[part] - before));
		// The newest blood names the colour: two creatures' worth on one arm is the later one's.
		whose[part] = tint;
	}

	/**
	 * Which region sits at the given world height on this player. Uses the live
	 * bounding box rather than fixed offsets so it stays right while crawling or
	 * swimming, when the whole body is barely half a block tall.
	 */
	private static BodyPart regionAt(Player player, double atX, double atY, double atZ) {
		var box = player.getBoundingBox();
		double height = box.getYsize();
		if (height <= 1.0e-6) {
			return BodyPart.TORSO;
		}
		double fraction = Math.clamp((atY - box.minY) / height, 0.0, 1.0);
		// The body's own right, from the way its chest faces: facing south
		// (yaw 0, +z) the right hand is to the west (-x).
		double yaw = Math.toRadians(player.yBodyRot);
		double toRight = -(atX - player.getX()) * Math.cos(yaw) - (atZ - player.getZ()) * Math.sin(yaw);
		double halfWidth = Math.max(1.0e-6, box.getXsize() / 2.0);
		return BodyStainMix.splashRegion(fraction, Math.clamp(toRight / halfWidth, -1.0, 1.0));
	}

	/**
	 * Grows staining where a player is bleeding and lets it dry off everywhere
	 * else. Called once per client tick for every player in the level.
	 */
	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			reset();
			return;
		}
		if (ClientEffectsConfig.get().bloodIntensity() <= 0.0) {
			if (!stains.isEmpty()) {
				reset();
			}
			return;
		}
		// Players and, since 1.7, every mob near enough to matter that carries a
		// body snapshot. Sixty-four blocks is well past where a stain is visible.
		Vec3 here = client.player == null ? Vec3.ZERO : client.player.position();
		for (LivingEntity player : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(here, here).inflate(64.0))) {
			BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
			double[] levels = stains.get(player.getId());
			// A body with no snapshot has no wounds to bleed from, but may still be
			// carrying somebody else's blood: that dries and washes like any other.
			if (snapshot == null && levels == null) {
				continue;
			}
			// How far up the body the water comes, and whether rain is on it.
			double height = player.getBbHeight();
			double waterLine = player.isUnderWater() ? 1.0
					: height <= 1.0e-6 ? 0.0 : Math.clamp(player.getFluidHeight(FluidTags.WATER) / height, 0.0, 1.0);
			boolean rain = !player.isInWater() && player.isInWaterOrRain();
			for (BodyPart part : BodyPart.values()) {
				WoundSeverity wound = snapshot == null ? null : snapshot.wound(part);
				double current = levels == null ? 0.0 : levels[part.ordinal()];
				double next;
				// A corpse's snapshot freezes with its wounds still in it; without
				// the isAlive gate the dead would quietly go on soaking.
				if (wound != null && !snapshot.underPressure() && player.isAlive()) {
					// An open wound keeps the region red however wet it is.
					next = Math.min(Balance.BODY_STAIN_MAX,
							current + wound.bloodPerSecond() * Balance.BODY_STAIN_GAIN / 20.0);
				} else {
					next = Math.max(0.0, current - (Balance.BODY_STAIN_DECAY
							+ BodyStainMix.washPerSecond(part, waterLine, rain)) / 20.0);
				}
				if (next != current) {
					if (levels == null) {
						levels = stains.computeIfAbsent(player.getId(),
								id -> new double[BodyPart.values().length]);
					}
					levels[part.ordinal()] = next;
					double[] share = foreign.get(player.getId());
					if (share != null) {
						share[part.ordinal()] = BodyStainMix.foreignAfter(share[part.ordinal()], current, next);
					}
				}
			}
		}
		// Players that have left, and bodies that have come fully clean.
		stains.entrySet().removeIf(entry -> {
			if (level.getEntity(entry.getKey()) == null) {
				return true;
			}
			for (double level2 : entry.getValue()) {
				if (level2 > 0.0) {
					return false;
				}
			}
			return true;
		});
		foreign.keySet().retainAll(stains.keySet());
		foreignTint.keySet().retainAll(stains.keySet());
	}

	public static void reset() {
		stains.clear();
		foreign.clear();
		foreignTint.clear();
	}
}
