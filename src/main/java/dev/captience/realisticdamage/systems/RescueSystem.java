package dev.captience.realisticdamage.systems;

import java.util.UUID;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.PressurePayload;
import dev.captience.realisticdamage.net.TreatingPayload;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Getting someone out (1.2). Three things one player can do for another, none
 * of which involve an item that has to be crafted or carried:
 *
 * <ul>
 * <li><b>Drag</b> — grab a downed player and haul them somewhere safer. They
 * are pulled along the ground by velocity rather than being teleported or
 * mounted, so a body snags on terrain, slides down slopes and can be lost over
 * a ledge. Hauling costs you half your speed and both hands.</li>
 * <li><b>Pressure</b> — hold a wound shut with nothing but your hands. Works on
 * yourself, but doing it pins you in place: you can stop your own bleeding or
 * you can keep fighting, not both.</li>
 * <li><b>Feed</b> — put food into someone who cannot use their own hands. Food
 * is still the cure; 1.2 only lets somebody else administer it.</li>
 * </ul>
 *
 * <p>Nothing here heals directly. Pressure buys time and feeding routes through
 * the ordinary eating path, so the spec's bet — that the danger is whether you
 * have enough food and enough time — survives intact.
 */
public final class RescueSystem {
	private RescueSystem() {}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer rescuer)
					|| !(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}
			return interact(rescuer, target, player.getItemInHand(hand));
		});

		// Both hands are on somebody else. Everything that needs a hand is out.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				handsFull(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				handsFull(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
				handsFull(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				handsFull(player) ? InteractionResult.FAIL : InteractionResult.PASS);

		// A grip must not outlive the player on either end of it.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> detach(handler.getPlayer()));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
				(entity, source) -> {
					if (entity instanceof ServerPlayer player) {
						detach(player);
					}
				});
	}

	/** True while this player is hauling a body or holding a wound shut. */
	public static boolean handsFull(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)
				|| serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return false;
		}
		RealisticDamageData data = RDAttachments.get(serverPlayer);
		return data.dragging() != null || data.pressureTarget() != null;
	}

	/** Pressing a wound means kneeling over it; you do not walk while doing it. */
	public static boolean rooted(RealisticDamageData data) {
		return data.pressureTarget() != null;
	}

	private static InteractionResult interact(ServerPlayer rescuer, ServerPlayer target, ItemStack held) {
		if (rescuer == target || rescuer.isSpectator()) {
			return InteractionResult.PASS;
		}
		RealisticDamageData targetData = RDAttachments.get(target);
		if (!targetData.unconscious()) {
			// Only a body that cannot help itself is a rescue target; otherwise
			// this is just two players bumping into each other.
			return InteractionResult.PASS;
		}
		RealisticDamageData rescuerData = RDAttachments.get(rescuer);

		if (rescuer.isShiftKeyDown()) {
			if (rescuerData.dragging() != null) {
				releaseDrag(rescuer, rescuerData);
			} else {
				beginDrag(rescuer, rescuerData, target, targetData);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		if (isFood(held)) {
			return feed(rescuer, target, held);
		}
		return InteractionResult.PASS;
	}

	// --- Dragging ---

	private static void beginDrag(ServerPlayer rescuer, RealisticDamageData rescuerData,
			ServerPlayer target, RealisticDamageData targetData) {
		if (targetData.draggedBy() != null || rescuerData.unconscious()) {
			return;
		}
		rescuerData.setDragging(target.getUUID());
		targetData.setDraggedBy(rescuer.getUUID());
		// Pressing a wound and hauling at the same time is one hand too many.
		stopPressure(rescuer, rescuerData);
		Notifier.message(rescuer, "realisticdamage.msg.drag.start");
	}

	public static void releaseDrag(ServerPlayer rescuer, RealisticDamageData rescuerData) {
		UUID victimId = rescuerData.dragging();
		rescuerData.setDragging(null);
		if (victimId == null) {
			return;
		}
		if (rescuer.level().getEntity(victimId) instanceof ServerPlayer victim) {
			RDAttachments.get(victim).setDraggedBy(null);
		}
		Notifier.message(rescuer, "realisticdamage.msg.drag.stop");
	}

	/**
	 * Hauls the body along behind the dragger. Velocity rather than teleport, so
	 * it collides with the world on the way: a corpse dragged off a ledge falls,
	 * and one dragged into a wall stops until you come back for it.
	 */
	private static void tickDrag(ServerPlayer dragger, RealisticDamageData data) {
		UUID victimId = data.dragging();
		if (victimId == null) {
			return;
		}
		// Level-scoped rather than via the player list: it is the right question
		// to ask (a body in another dimension is not being dragged anywhere), and
		// it keeps the system testable with entities the player list never saw.
		if (!(dragger.level().getEntity(victimId) instanceof ServerPlayer victim)
				|| victim.isRemoved() || !RDAttachments.get(victim).unconscious()
				|| data.unconscious()) {
			releaseDrag(dragger, data);
			return;
		}
		double distance = dragger.distanceTo(victim);
		if (distance > Balance.DRAG_BREAK_DISTANCE) {
			releaseDrag(dragger, data);
			Notifier.message(dragger, "realisticdamage.msg.drag.lost");
			return;
		}
		if (distance <= Balance.DRAG_FOLLOW_DISTANCE) {
			return;
		}
		Vec3 anchor = dragger.position().subtract(
				dragger.getLookAngle().multiply(1.0, 0.0, 1.0).normalize()
						.scale(Balance.DRAG_FOLLOW_DISTANCE));
		Vec3 pull = anchor.subtract(victim.position());
		double length = pull.horizontalDistance();
		if (length < 1.0e-3) {
			return;
		}
		Vec3 step = pull.multiply(1.0, 0.0, 1.0).normalize()
				.scale(Math.min(Balance.DRAG_PULL_SPEED, length));
		// Leave vertical motion to gravity so the body still falls and steps.
		victim.setDeltaMovement(step.x, victim.getDeltaMovement().y, step.z);
		victim.hurtMarked = true;
	}

	// --- Pressure ---

	/**
	 * Claims (or renews) a pressure hold. Called every tick the player keeps the
	 * key down, so letting go simply stops the renewals and the effect lapses.
	 */
	public static void applyPressure(ServerPlayer presser, @Nullable ServerPlayer target) {
		RealisticDamageData data = RDAttachments.get(presser);
		if (presser.isCreative() || presser.isSpectator() || data.unconscious()) {
			stopPressure(presser, data);
			return;
		}
		ServerPlayer subject = target == null ? presser : target;
		if (subject != presser) {
			if (presser.distanceTo(subject) > Balance.RESCUE_REACH
					|| !RDAttachments.get(subject).unconscious()) {
				stopPressure(presser, data);
				return;
			}
		}
		if (data.dragging() != null) {
			// One job at a time.
			return;
		}
		RealisticDamageData subjectData = RDAttachments.get(subject);
		if (subjectData.wounds().isEmpty()) {
			stopPressure(presser, data);
			return;
		}
		if (data.pressureTarget() == null) {
			Notifier.message(presser, subject == presser
					? "realisticdamage.msg.pressure.self" : "realisticdamage.msg.pressure.other");
		}
		data.setPressureTarget(subject.getUUID());
		data.refreshPressureHold();
		subjectData.refreshPressure(subject == presser);
	}

	public static void stopPressure(ServerPlayer presser, RealisticDamageData data) {
		data.setPressureTarget(null);
	}

	// --- Feeding ---

	private static boolean isFood(ItemStack stack) {
		return stack.has(DataComponents.FOOD) && stack.has(DataComponents.CONSUMABLE);
	}

	/**
	 * Puts food into someone who cannot feed themselves. Routed through vanilla's
	 * own consume path on the recipient, so hunger, saturation, golden-apple
	 * effects and the mod's Blood restoration all land exactly as if they had
	 * eaten it — no parallel healing rule to keep in sync.
	 */
	private static InteractionResult feed(ServerPlayer rescuer, ServerPlayer target, ItemStack held) {
		Consumable consumable = held.get(DataComponents.CONSUMABLE);
		if (consumable == null) {
			return InteractionResult.PASS;
		}
		if (!consumable.canConsume(target, held)) {
			Notifier.message(rescuer, "realisticdamage.msg.feed.full");
			return InteractionResult.FAIL;
		}
		// A detached single item, because onConsume shrinks the stack against the
		// *eater's* creative flag — and the eater is not the one whose inventory
		// this came out of. The real stack is decremented against the rescuer.
		consumable.onConsume(target.level(), target, held.copyWithCount(1));
		if (!rescuer.isCreative()) {
			held.shrink(1);
		}
		Notifier.message(rescuer, "realisticdamage.msg.feed.given");
		Notifier.message(target, "realisticdamage.msg.feed.received");
		return InteractionResult.SUCCESS_SERVER;
	}

	// --- Tick ---

	public static void tick(ServerPlayer player, RealisticDamageData data) {
		data.tickPressure();
		// The hold lapses on its own. A client that stops asking (released the
		// key, disconnected, crashed) must never leave someone rooted forever.
		if (data.pressureTarget() != null && (!data.tickPressureHold() || !player.isAlive())) {
			stopPressure(player, data);
		}
		tickDrag(player, data);
		healGrip(player, data);
		publishTreating(player, data);
	}

	/**
	 * Repairs the held player's half of the grip from their own side.
	 *
	 * <p>Relying on the dragger to let go is not enough: if they change
	 * dimension, unload, or otherwise cannot be resolved, the flag would stay
	 * set and nobody could ever pick this body up again. The dragger's claim is
	 * the single source of truth, and anyone can check it.
	 */
	private static void healGrip(ServerPlayer player, RealisticDamageData data) {
		UUID draggerId = data.draggedBy();
		if (draggerId == null) {
			return;
		}
		boolean stillClaimed = player.level().getEntity(draggerId) instanceof ServerPlayer dragger
				&& player.getUUID().equals(RDAttachments.get(dragger).dragging());
		if (!stillClaimed) {
			data.setDraggedBy(null);
		}
	}

	/**
	 * Tells the client whose body to draw beside its own. Only on change — the
	 * body state itself is already synced to every client, so this is one int
	 * whenever you pick someone up or let them go.
	 */
	private static void publishTreating(ServerPlayer player, RealisticDamageData data) {
		UUID subject = data.pressureTarget() != null ? data.pressureTarget() : data.dragging();
		int id = TreatingPayload.NOBODY;
		if (subject != null && player.level().getEntity(subject) instanceof ServerPlayer other
				&& other != player) {
			id = other.getId();
		}
		if (id != data.lastTreatingSent()) {
			data.setLastTreatingSent(id);
			if (player.connection != null
					&& net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
							player, TreatingPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
						player, new TreatingPayload(id));
			}
		}
	}

	/** Handles one client "still pressing" message. Everything is re-validated here. */
	public static void onPressureRequest(ServerPlayer presser, int targetId) {
		if (targetId == PressurePayload.SELF) {
			applyPressure(presser, null);
			return;
		}
		if (presser.level().getEntity(targetId) instanceof ServerPlayer target) {
			applyPressure(presser, target);
		} else {
			stopPressure(presser, RDAttachments.get(presser));
		}
	}

	/** Death, respawn and disconnect must not leave a grip on a player who is gone. */
	public static void detach(ServerPlayer player) {
		RealisticDamageData data = RDAttachments.get(player);
		if (data.dragging() != null) {
			releaseDrag(player, data);
		}
		UUID draggerId = data.draggedBy();
		if (draggerId != null) {
			if (player.level().getEntity(draggerId) instanceof ServerPlayer dragger) {
				RDAttachments.get(dragger).setDragging(null);
			}
			data.setDraggedBy(null);
		}
		data.clearRescue();
	}
}
