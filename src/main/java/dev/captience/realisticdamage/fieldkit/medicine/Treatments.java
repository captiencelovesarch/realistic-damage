package dev.captience.realisticdamage.fieldkit.medicine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.captience.realisticdamage.config.Balance;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Who is treating whom, while a medic holds the use key (2.0, phase 4).
 *
 * <p>A treatment is vanilla's own "using an item": the medic holds use, the
 * animation plays, and it lands when the item's time is up. This only
 * remembers the patient, so the treatment knows whose wound it is when it
 * lands, and lets it be lost: the patient wanders out of reach, either of them
 * dies, or — for the delicate ones, like stitching — either of them is hurt.
 */
public final class Treatments {
	private Treatments() {}

	private static final Map<UUID, UUID> PATIENTS = new HashMap<>();

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0F || entity.level().isClientSide()) {
				return;
			}
			if (entity instanceof Player medic && interrupted(medic)) {
				return;
			}
			for (Map.Entry<UUID, UUID> session : Map.copyOf(PATIENTS).entrySet()) {
				if (session.getValue().equals(entity.getUUID())
						&& ((ServerLevel) entity.level()).getEntity(session.getKey()) instanceof Player medic) {
					interrupted(medic);
				}
			}
		});
	}

	private static boolean interrupted(Player medic) {
		if (medic.isUsingItem() && medic.getUseItem().getItem() instanceof TreatmentItem item && item.interruptible()
				&& PATIENTS.containsKey(medic.getUUID())) {
			medic.stopUsingItem();
			PATIENTS.remove(medic.getUUID());
			medic.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.treat.interrupted")
					.withStyle(ChatFormatting.GRAY));
			return true;
		}
		return false;
	}

	public static void begin(Player medic, LivingEntity patient) {
		PATIENTS.put(medic.getUUID(), patient.getUUID());
	}

	/** The patient this medic is treating, if still there and in reach. */
	public static @Nullable LivingEntity patient(Player medic) {
		UUID id = PATIENTS.get(medic.getUUID());
		if (id == null || !(medic.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity found = id.equals(medic.getUUID()) ? medic : level.getEntity(id);
		if (!(found instanceof LivingEntity patient) || !patient.isAlive()
				|| patient.distanceTo(medic) > Balance.TREATMENT_REACH + 0.5) {
			return null;
		}
		return patient;
	}

	public static void end(Player medic) {
		PATIENTS.remove(medic.getUUID());
	}

	/** Whether this medic has a patient on the go. */
	public static boolean treating(Player medic) {
		return PATIENTS.containsKey(medic.getUUID());
	}
}
