package dev.captience.realisticdamage.fieldkit.brew;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Hauling an unconscious mob (2.0, phase 3): sneak and use one with an empty
 * hand to drag it, again to let go. Tranq a villager and drag it home. The
 * same pull as dragging a downed player ({@code RescueSystem}), with its own
 * grip, because that one only knows players; under the Weapons switch.
 *
 * <p>It lets go when the mob wakes or dies, when you go down, when it is torn
 * out of reach, and when Weapons is switched off.
 */
public final class MobDrag {
	private MobDrag() {}

	private static final Identifier SLOW = RealisticDamage.id("fieldkit_drag");
	/** Dragger to mob. */
	private static final Map<UUID, UUID> GRIPS = new HashMap<>();

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer dragger) || !(entity instanceof Mob mob)
					|| !dragger.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
				return InteractionResult.PASS;
			}
			return toggle(dragger, mob) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(MobDrag::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> release(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GRIPS.clear());
	}

	/** Takes hold of an unconscious mob, or lets go of the one held. False when there was nothing to do. */
	public static boolean toggle(ServerPlayer dragger, Mob mob) {
		UUID held = GRIPS.get(dragger.getUUID());
		if (mob.getUUID().equals(held)) {
			release(dragger, true);
			return true;
		}
		if (held != null || !Half.WEAPONS.on() || !RDAttachments.get(mob).unconscious()
				|| RDAttachments.get(dragger).unconscious() || GRIPS.containsValue(mob.getUUID())) {
			return false;
		}
		GRIPS.put(dragger.getUUID(), mob.getUUID());
		AttributeInstance speed = dragger.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW);
			speed.addTransientModifier(new AttributeModifier(SLOW, -Balance.DRAG_MOVEMENT_PENALTY,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		Notifier.message(dragger, "realisticdamage.msg.drag.start");
		return true;
	}

	public static boolean dragging(ServerPlayer dragger, Mob mob) {
		return mob.getUUID().equals(GRIPS.get(dragger.getUUID()));
	}

	private static void release(ServerPlayer dragger, boolean say) {
		if (GRIPS.remove(dragger.getUUID()) == null) {
			return;
		}
		AttributeInstance speed = dragger.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW);
		}
		if (say) {
			Notifier.message(dragger, "realisticdamage.msg.drag.stop");
		}
	}

	private static void tick(MinecraftServer server) {
		if (GRIPS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, UUID>> it = GRIPS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, UUID> grip = it.next();
			ServerPlayer dragger = server.getPlayerList().getPlayer(grip.getKey());
			if (dragger == null) {
				it.remove();
				continue;
			}
			Entity found = dragger.level().getEntity(grip.getValue());
			if (!(found instanceof Mob mob) || !mob.isAlive() || !RDAttachments.get(mob).unconscious()
					|| !dragger.isAlive() || RDAttachments.get(dragger).unconscious() || !Half.WEAPONS.on()) {
				it.remove();
				unslow(dragger);
				continue;
			}
			double distance = dragger.distanceTo(mob);
			if (distance > Balance.DRAG_BREAK_DISTANCE) {
				it.remove();
				unslow(dragger);
				Notifier.message(dragger, "realisticdamage.msg.drag.lost");
				continue;
			}
			if (distance <= Balance.DRAG_FOLLOW_DISTANCE) {
				continue;
			}
			// The same pull as a downed player gets: to just behind you, along the
			// ground, leaving the fall and the steps to gravity.
			Vec3 anchor = dragger.position().subtract(dragger.getLookAngle().multiply(1.0, 0.0, 1.0).normalize()
					.scale(Balance.DRAG_FOLLOW_DISTANCE));
			Vec3 pull = anchor.subtract(mob.position());
			double length = pull.horizontalDistance();
			if (length < 1.0e-3) {
				continue;
			}
			Vec3 step = pull.multiply(1.0, 0.0, 1.0).normalize().scale(Math.min(Balance.DRAG_PULL_SPEED, length));
			mob.setDeltaMovement(step.x, mob.getDeltaMovement().y, step.z);
			mob.hurtMarked = true;
		}
	}

	private static void unslow(ServerPlayer dragger) {
		AttributeInstance speed = dragger.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW);
		}
	}
}
