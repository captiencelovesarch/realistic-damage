package dev.captience.realisticdamage.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.damage.BloodiedItem;
import dev.captience.realisticdamage.systems.BloodFlow;
import dev.captience.realisticdamage.systems.BloodLayer;
import dev.captience.realisticdamage.systems.BloodiedMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Blood falling off a wet weapon (2.0).
 *
 * <p>A blade that has just come out of something drips for a while. Whoever
 * is holding an item with a heavy, wet mark on it sheds the same drops a
 * wound does, in that mark's colour, from about where the item is: they
 * fall, land and leave the same marks on the floor. It slows as the blood
 * dries and stops well before it is dry, and a fleck never drips at all.
 *
 * <p>Seen, not kept: dripping takes nothing off the item. Client-side like
 * the rest of the bleeding, from the marks every client already has.
 */
public final class BloodiedDrips {
	private BloodiedDrips() {}

	/** Beyond this nobody would see a drop the size of a pixel. */
	private static final double RANGE = 24.0;
	/** A mark drips while it is wetter than this and at least this heavy. */
	private static final double WET_ENOUGH = 0.45;
	private static final float HEAVY_ENOUGH = 0.25F;
	/** Drops a second from a full, fresh mark. */
	private static final double DROPS_PER_SECOND = 1.6;

	private static final Map<Integer, Double> carry = new HashMap<>();
	private static long emitted;

	/** Drops that have fallen off held items this session; read by the client gametest. */
	public static long emitted() {
		return emitted;
	}

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		SpriteSet sprites = BloodEmitter.sprites();
		if (level == null || client.player == null || sprites == null
				|| ClientEffectsConfig.get().bloodIntensity() <= 0.0) {
			carry.clear();
			return;
		}
		long now = level.getGameTime();
		Vec3 here = client.player.position();
		for (LivingEntity holder : level.getEntitiesOfClass(LivingEntity.class, new AABB(here, here).inflate(RANGE))) {
			if (!holder.isAlive() || holder.isSpectator()) {
				continue;
			}
			ItemStack held = holder.getMainHandItem();
			BloodLayer dripping = held.isEmpty() ? null : wettest(BloodiedItem.read(held), now);
			if (dripping == null) {
				carry.remove(holder.getId());
				continue;
			}
			double wetness = BloodiedMath.wetness(now, dripping.wetAt());
			double rate = DROPS_PER_SECOND * dripping.amount() * (wetness - WET_ENOUGH) / (1.0 - WET_ENOUGH);
			double had = carry.getOrDefault(holder.getId(), 0.0);
			int drops = BloodFlow.dropsThisTick(had, rate);
			carry.put(holder.getId(), BloodFlow.carryAfter(had, rate));
			for (int i = 0; i < drops; i++) {
				spawn(client, level, sprites, holder, BloodTint.byName(dripping.tint()), emitted++);
			}
		}
		carry.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	/** The heaviest mark that is still wet enough to drip, or null. */
	private static @Nullable BloodLayer wettest(List<BloodLayer> layers, long now) {
		BloodLayer best = null;
		for (BloodLayer layer : layers) {
			if (layer.amount() >= HEAVY_ENOUGH && BloodiedMath.wetness(now, layer.wetAt()) > WET_ENOUGH
					&& (best == null || layer.amount() > best.amount())) {
				best = layer;
			}
		}
		return best;
	}

	private static void spawn(Minecraft client, ClientLevel level, SpriteSet sprites, LivingEntity holder,
			BloodTint tint, long index) {
		// About where a held item's working end is: out to the weapon side, a
		// little ahead, at the height of a hanging hand. In first person the
		// item is drawn low and to the side of the view, so the drop starts
		// there instead and is seen leaving it.
		boolean firstPerson = holder == client.player && client.options.getCameraType().isFirstPerson();
		double yaw = Math.toRadians(firstPerson ? holder.getYRot() : holder.yBodyRot);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		double side = holder.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
		double width = holder.getBbWidth();
		Vec3 at = firstPerson
				? holder.getEyePosition().add(forward.scale(0.55)).add(right.scale(0.36 * side)).add(0.0, -0.42, 0.0)
				: holder.position().add(forward.scale(width * 0.75)).add(right.scale(width * 0.62 * side))
						.add(0.0, holder.getBbHeight() * 0.52, 0.0);
		at = at.add(BloodFlow.jitterSigned(index, 21) * 0.05, BloodFlow.jitterSigned(index, 22) * 0.05,
				BloodFlow.jitterSigned(index, 23) * 0.05);
		Vec3 moving = holder.getDeltaMovement();
		BloodDrop drop = new BloodDrop(level, at.x, at.y, at.z,
				moving.x * 0.5 + BloodFlow.jitterSigned(index, 24) * 0.01,
				-0.03,
				moving.z * 0.5 + BloodFlow.jitterSigned(index, 25) * 0.01,
				holder.getId(),
				sprites.get((int) (BloodFlow.jitter(index, 26) * BloodEmitter.DROP_SPRITE_COUNT), BloodEmitter.SPRITE_COUNT - 1),
				tint);
		client.particleEngine.add(drop);
	}

	public static void reset() {
		carry.clear();
	}
}
