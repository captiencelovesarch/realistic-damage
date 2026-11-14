package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Whose body a medic may do something drastic to (2.0, phase 4): drawing
 * blood, burning a wound shut. Your own; a mob's; a player's only while they
 * are out cold and can't object. A conscious player draws and cauterizes for
 * themselves — so neither is a weapon in a fight.
 */
final class Consent {
	private Consent() {}

	static boolean given(Player medic, LivingEntity patient) {
		return patient == medic || !(patient instanceof Player) || RDAttachments.get(patient).unconscious();
	}
}
