package dev.captience.realisticdamage.client;

import java.util.HashMap;
import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * stays bloody after the wound closes because blood dries on rather than
 * rinsing off.
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
		double[] levels = stains.computeIfAbsent(player.getId(), id -> new double[BodyPart.values().length]);
		BodyPart part = regionAtHeight(player, atY);
		levels[part.ordinal()] = Math.min(Balance.BODY_STAIN_MAX,
				levels[part.ordinal()] + Balance.BODY_STAIN_PER_DROP * (0.4 + 0.6 * dropScale));
	}

	/**
	 * Which region sits at the given world height on this player. Uses the live
	 * bounding box rather than fixed offsets so it stays right while crawling or
	 * swimming, when the whole body is barely half a block tall.
	 */
	private static BodyPart regionAtHeight(Player player, double atY) {
		var box = player.getBoundingBox();
		double height = box.getYsize();
		if (height <= 1.0e-6) {
			return BodyPart.TORSO;
		}
		double fraction = Math.clamp((atY - box.minY) / height, 0.0, 1.0);
		if (fraction > 0.82) {
			return BodyPart.HEAD;
		}
		if (fraction > 0.44) {
			return BodyPart.TORSO;
		}
		return BodyPart.LEFT_LEG;
	}

	/**
	 * Grows staining where a player is bleeding and lets it dry off everywhere
	 * else. Called once per client tick for every player in the level.
	 */
	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			stains.clear();
			return;
		}
		if (ClientEffectsConfig.get().bloodIntensity() <= 0.0) {
			if (!stains.isEmpty()) {
				stains.clear();
			}
			return;
		}
		// Players and, since 1.7, every mob near enough to matter that carries a
		// body snapshot. Sixty-four blocks is well past where a stain is visible.
		Vec3 here = client.player == null ? Vec3.ZERO : client.player.position();
		for (LivingEntity player : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(here, here).inflate(64.0))) {
			BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
			if (snapshot == null) {
				continue;
			}
			double[] levels = stains.get(player.getId());
			for (BodyPart part : BodyPart.values()) {
				WoundSeverity wound = snapshot.wound(part);
				double current = levels == null ? 0.0 : levels[part.ordinal()];
				double next;
				// A corpse's snapshot freezes with its wounds still in it; without
				// the isAlive gate the dead would quietly go on soaking.
				if (wound != null && !snapshot.underPressure() && player.isAlive()) {
					next = Math.min(Balance.BODY_STAIN_MAX,
							current + wound.bloodPerSecond() * Balance.BODY_STAIN_GAIN / 20.0);
				} else {
					next = Math.max(0.0, current - Balance.BODY_STAIN_DECAY / 20.0);
				}
				if (next != current) {
					if (levels == null) {
						levels = stains.computeIfAbsent(player.getId(),
								id -> new double[BodyPart.values().length]);
					}
					levels[part.ordinal()] = next;
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
	}

	public static void reset() {
		stains.clear();
	}
}
