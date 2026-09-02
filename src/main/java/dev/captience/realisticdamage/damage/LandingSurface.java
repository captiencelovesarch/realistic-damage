package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What you landed on (1.4). Vanilla already forgives a few surfaces on the
 * health bar — hay eats most of the damage, slime bounces you, water cancels it
 * outright — but it has nothing to say about the rest, and nothing at all to say
 * about your skeleton. Landing in a ploughed field and landing on a deepslate
 * floor are not the same event, and the body is where that difference belongs.
 *
 * <p>Deliberately a small hand-written table rather than a hardness lookup:
 * block hardness sorts glass beside wool and iron beside stone, which is a
 * mineability ordering, not a give-underfoot one.
 */
public final class LandingSurface {
	private LandingSurface() {}

	/** Multiplier on body trauma for the block a player came down on. */
	public static double factor(BlockState state) {
		if (state.isAir()) {
			return Balance.LANDING_SURFACE_NEUTRAL;
		}
		if (soft(state)) {
			return Balance.LANDING_SURFACE_SOFT;
		}
		if (yielding(state)) {
			return Balance.LANDING_SURFACE_YIELDING;
		}
		if (hard(state)) {
			return Balance.LANDING_SURFACE_HARD;
		}
		return Balance.LANDING_SURFACE_NEUTRAL;
	}

	/** Things that genuinely absorb an impact. */
	private static boolean soft(BlockState state) {
		return state.is(Blocks.HAY_BLOCK)
				|| state.is(Blocks.SLIME_BLOCK)
				|| state.is(Blocks.HONEY_BLOCK)
				|| state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.COBWEB)
				|| state.is(Blocks.SPONGE)
				|| state.is(Blocks.WET_SPONGE)
				|| state.is(BlockTags.BEDS)
				|| state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.SNOW)
				|| state.is(BlockTags.WOOL)
				|| state.is(BlockTags.WOOL_CARPETS);
	}

	/** Ground that at least moves when you hit it. */
	private static boolean yielding(BlockState state) {
		return state.is(Blocks.GRAVEL)
				|| state.is(Blocks.CLAY)
				|| state.is(Blocks.SOUL_SAND)
				|| state.is(Blocks.SOUL_SOIL)
				|| state.is(Blocks.FARMLAND)
				|| state.is(Blocks.MUD)
				|| state.is(Blocks.MUDDY_MANGROVE_ROOTS)
				|| state.is(BlockTags.DIRT)
				|| state.is(BlockTags.SAND)
				|| state.is(BlockTags.MOSS_BLOCKS)
				|| state.is(BlockTags.LOGS)
				|| state.is(BlockTags.PLANKS)
				|| state.is(BlockTags.WART_BLOCKS);
	}

	/** Nothing gives. Your legs do the whole job. */
	private static boolean hard(BlockState state) {
		return state.is(Blocks.OBSIDIAN)
				|| state.is(Blocks.CRYING_OBSIDIAN)
				|| state.is(Blocks.BEDROCK)
				|| state.is(Blocks.IRON_BLOCK)
				|| state.is(Blocks.GOLD_BLOCK)
				|| state.is(Blocks.DIAMOND_BLOCK)
				|| state.is(Blocks.NETHERITE_BLOCK)
				|| state.is(Blocks.ANVIL)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.BASE_STONE_NETHER)
				|| state.is(BlockTags.STONE_BRICKS)
				|| state.is(BlockTags.TERRACOTTA)
				|| state.is(BlockTags.CONCRETE);
	}
}
