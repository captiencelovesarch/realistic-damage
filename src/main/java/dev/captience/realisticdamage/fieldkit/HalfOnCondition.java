package dev.captience.realisticdamage.fieldkit;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * {@code realisticdamage:field_kit_on} (2.0): passes while a half is switched on.
 *
 * <pre>{@code { "condition": "realisticdamage:field_kit_on", "half": "medicine" } }</pre>
 *
 * <p>One condition for both places the plan names, because 26.2 rolls a
 * villager's trade through a loot context too: it goes on a loot pool or entry,
 * and on a trade as its {@code merchant_predicate}. Either way it is asked when
 * the loot or the trade is rolled. {@link LootGate} is the safety net behind it,
 * for loot someone else's datapack adds without asking.
 */
public record HalfOnCondition(Half half) implements LootItemCondition {
	public static final MapCodec<HalfOnCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
					Half.CODEC.fieldOf("half").forGetter(HalfOnCondition::half))
			.apply(i, HalfOnCondition::new));

	@Override
	public MapCodec<HalfOnCondition> codec() {
		return MAP_CODEC;
	}

	@Override
	public boolean test(LootContext context) {
		return half.on();
	}
}
