package dev.captience.realisticdamage.fieldkit;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

/**
 * Loot never hands out a switched-off item (2.0).
 *
 * <p>Our own loot entries carry {@link HalfOnCondition}, which keeps them from
 * rolling at all. This is the net under that: whatever a loot table produced,
 * from whichever datapack, any field kit stack whose half is off is taken out
 * of the drops before they reach a chest, a mob's death or a block. Asked when
 * the loot is rolled, so a flipped switch is felt by the next chest opened.
 */
public final class LootGate {
	private LootGate() {}

	public static void init() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> drops.removeIf(Kit::blocked));
	}
}
