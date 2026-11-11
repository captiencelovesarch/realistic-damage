package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.damage.BrokenNeck;
import dev.captience.realisticdamage.damage.RDDamageTypes;
import dev.captience.realisticdamage.damage.ShallowWater;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Broken necks and whiplash (2.0), in a real world: bodies dropped for real
 * onto stone and into water, struck for real with a mace, crashed into a wall.
 *
 * <p>The falls are physics, not a faked landing record: a body is spawned
 * high above a pool or a floor built for it and left to fall, so the mixins
 * that see the water and the ground are what is under test, not a shortcut
 * around them.
 */
public class BrokenNeckGameTest {
	private static final BlockPos AT = new BlockPos(1, 1, 1);

	/**
	 * Where the scenes are built, blocks over the test's own floor. Up in open
	 * air: a test's box is cleared only as high as the structure, and above it
	 * is the world's own ground, which a dropped body would land on instead.
	 */
	private static final int BASE = 24;

	/**
	 * A one-block-wide pool {@code depth} deep, walled and floored in stone,
	 * at column (x, z), up at {@link #BASE}; returns the column's top water block.
	 */
	private static BlockPos pool(GameTestHelper helper, int x, int z, int depth) {
		helper.setBlock(new BlockPos(x, BASE, z), Blocks.STONE);
		for (int y = BASE + 1; y <= BASE + depth; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					helper.setBlock(new BlockPos(x + dx, y, z + dz), dx == 0 && dz == 0 ? Blocks.WATER : Blocks.STONE);
				}
			}
		}
		return new BlockPos(x, BASE + depth, z);
	}

	/** Takes a scene built by {@link #pool} or {@link #floor} down again: it is outside the test's box. */
	private static void clear(GameTestHelper helper, int x, int z, int depth) {
		for (int y = BASE; y <= BASE + depth; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					helper.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR);
				}
			}
		}
	}

	/** One block of stone to land on, up at {@link #BASE}; returns the block. */
	private static BlockPos floor(GameTestHelper helper, int x, int z) {
		BlockPos at = new BlockPos(x, BASE, z);
		helper.setBlock(at, Blocks.STONE);
		return at;
	}

	/** A zombie this high over (x, z), out cold if {@code limp}: nothing is holding its head up. */
	private static Zombie dropped(GameTestHelper helper, int x, int z, double y, boolean limp) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(x + 0.5, y, z + 0.5));
		zombie.setPersistenceRequired();
		RealisticDamageData data = RDAttachments.get(zombie);
		if (limp) {
			data.setConsciousness(0.0);
			data.setUnconscious(true);
		}
		return zombie;
	}

	/**
	 * Died, and through the neck. Not read off the last damage source: the snap
	 * kills from inside the blow that caused it, and that outer blow is written
	 * down as the last source once it returns.
	 */
	private static boolean diedOfIt(LivingEntity body) {
		return !body.isAlive() && RDAttachments.get(body).neckBroken();
	}

	private static boolean legsHurt(RealisticDamageData data) {
		return data.part(BodyPart.LEFT_LEG).fraction() < 1.0 || data.part(BodyPart.RIGHT_LEG).fraction() < 1.0;
	}

	/** A whole player that can take damage, die and be scored (vanilla's in-level mock always counts as creative). */
	private static TestPlayer player(GameTestHelper helper, double x, double z, String name) {
		return TestPlayer.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(x, 2.0, z)), 0.0F, name);
	}

	private static void gone(TestPlayer... players) {
		for (TestPlayer player : players) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("removed"));
		}
	}

	@GameTest(environment = "realisticdamage:isolated", maxTicks = 200)
	public void aLimpBodyDivingIntoShallowWaterBreaksItsNeck(GameTestHelper helper) {
		BlockPos top = pool(helper, 2, 2, 1);
		Zombie zombie = dropped(helper, 2, 2, top.getY() + 23.0, true);
		helper.succeedWhen(() -> {
			helper.assertTrue(!zombie.isAlive(), "still falling, or it lived");
			helper.assertTrue(RDAttachments.get(zombie).neckBroken() && diedOfIt(zombie),
					"a limp body hit the bottom of a one-block pool and did not die of its neck: "
							+ zombie.getLastDamageSource());
			clear(helper, 2, 2, 1);
			zombie.discard();
		});
	}

	@GameTest(environment = "realisticdamage:isolated", maxTicks = 200)
	public void feetFirstIntoShallowWaterTheLegsTakeIt(GameTestHelper helper) {
		BlockPos top = pool(helper, 2, 2, 1);
		Zombie zombie = dropped(helper, 2, 2, top.getY() + 23.0, false);
		RealisticDamageData data = RDAttachments.get(zombie);
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.isInWater() || zombie.onGround(), "still falling");
			helper.assertTrue(zombie.isAlive(), "an upright dive into a shallow pool killed the zombie");
			helper.assertTrue(legsHurt(data), "the bottom of a one-block pool did nothing to the legs");
			helper.assertTrue(!data.neckBroken(), "feet first broke the neck");
			clear(helper, 2, 2, 1);
			zombie.discard();
		});
	}

	@GameTest(environment = "realisticdamage:isolated", maxTicks = 200)
	public void deepWaterStillStopsAFall(GameTestHelper helper) {
		BlockPos top = pool(helper, 2, 2, 3);
		Zombie zombie = dropped(helper, 2, 2, top.getY() + 23.0, true);
		RealisticDamageData data = RDAttachments.get(zombie);
		helper.assertTrue(ShallowWater.depth(helper.getLevel(), helper.absolutePos(top)) == 3,
				"the pool is not three deep: " + ShallowWater.depth(helper.getLevel(), helper.absolutePos(top)));
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.isInWater(), "not in the water yet");
			helper.assertTrue(zombie.isAlive() && !data.neckBroken() && !data.whiplashed() && !legsHurt(data),
					"three blocks of water did not stop the fall");
			clear(helper, 2, 2, 3);
			zombie.discard();
		});
	}

	@GameTest(environment = "realisticdamage:isolated", maxTicks = 200)
	public void aLimpBodyLandingHeadFirstOnStoneBreaksItsNeck(GameTestHelper helper) {
		BlockPos stone = floor(helper, 2, 2);
		Zombie zombie = dropped(helper, 2, 2, stone.getY() + 1.0 + 15.0, true);
		helper.succeedWhen(() -> {
			helper.assertTrue(!zombie.isAlive(), "still falling, or it lived");
			helper.assertTrue(diedOfIt(zombie), "a limp fall of fifteen blocks did not break the neck: "
					+ zombie.getLastDamageSource());
			clear(helper, 2, 2, 0);
			zombie.discard();
		});
	}

	@GameTest(environment = "realisticdamage:isolated", maxTicks = 200)
	public void aShortLimpFallOnlyWrenchesTheNeck(GameTestHelper helper) {
		BlockPos stone = floor(helper, 2, 2);
		Zombie zombie = dropped(helper, 2, 2, stone.getY() + 1.0 + 7.0, true);
		RealisticDamageData data = RDAttachments.get(zombie);
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.onGround(), "still falling");
			helper.assertTrue(zombie.isAlive() && !data.neckBroken(), "seven blocks limp broke the neck");
			helper.assertTrue(data.whiplashed(), "seven blocks limp did not even wrench it");
			clear(helper, 2, 2, 0);
			zombie.discard();
		});
	}

	private static ServerPlayer swinging(GameTestHelper helper, LivingEntity target, ItemStack weapon) {
		ServerPlayer attacker = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		// Two blocks north of it, looking south along the level: the swing meets the head.
		attacker.setPos(target.getX(), target.getY() + target.getEyeHeight() - attacker.getEyeHeight(), target.getZ() - 2.0);
		attacker.setYRot(0.0F);
		attacker.setYHeadRot(0.0F);
		attacker.setXRot(0.0F);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return attacker;
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aHugeMaceBlowToTheHeadBreaksTheNeck(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		try {
			zombie.setNoAi(true);
			ServerPlayer attacker = swinging(helper, zombie, new ItemStack(Items.MACE));
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 12.0F);
			// Twelve: enough to snap the neck, not enough to empty the head and crush the skull instead.
			helper.assertTrue(diedOfIt(zombie), "a twelve-point mace blow to the head did not break the neck");
			helper.assertTrue(zombie.getLastDamageSource().getEntity() == attacker, "the kill is not the swinger's");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aLighterMaceBlowOnlyWrenchesIt(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		try {
			zombie.setNoAi(true);
			RealisticDamageData data = RDAttachments.get(zombie);
			ServerPlayer attacker = swinging(helper, zombie, new ItemStack(Items.MACE));
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 6.0F);
			helper.assertTrue(zombie.isAlive() && !data.neckBroken(), "a six-point blow broke the neck");
			helper.assertTrue(data.whiplashed(), "a six-point blow to the head did not wrench it");
			// An edge never wrenches a neck, however hard.
			Zombie other = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
			try {
				other.setNoAi(true);
				ServerPlayer swordsman = swinging(helper, other, new ItemStack(Items.DIAMOND_SWORD));
				other.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(swordsman), 6.0F);
				helper.assertTrue(!RDAttachments.get(other).whiplashed(), "a sword cut wrenched a neck");
			} finally {
				other.discard();
			}
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anElytraIntoAWallAtSpeedBreaksTheNeck(GameTestHelper helper) {
		TestPlayer fast = player(helper, 1.5, 1.5, "Fast");
		TestPlayer slow = player(helper, 3.5, 1.5, "Slow");
		try {
			fast.hurtServer(helper.getLevel(), helper.getLevel().damageSources().flyIntoWall(), 14.0F);
			helper.assertTrue(diedOfIt(fast), "fourteen points into a wall did not break the neck");
			slow.hurtServer(helper.getLevel(), helper.getLevel().damageSources().flyIntoWall(), 6.0F);
			helper.assertTrue(slow.isAlive() && !RDAttachments.get(slow).neckBroken(), "six points into a wall killed");
			helper.assertTrue(RDAttachments.get(slow).whiplashed(), "six points into a wall did not wrench the neck");
		} finally {
			gone(fast, slow);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aCrashThatKillsByItselfStillTakesTheNeck(GameTestHelper helper) {
		TestPlayer player = player(helper, 1.5, 1.5, "Crashed");
		try {
			// More than the whole health bar: vanilla kills them, so the pipeline never sees it.
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().flyIntoWall(), 40.0F);
			helper.assertTrue(player.isDeadOrDying(), "forty points into a wall did not kill");
			helper.assertTrue(RDAttachments.get(player).neckBroken(), "the neck did not go with the death");
			helper.assertTrue(player.getLastDamageSource() != null
							&& player.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL),
					"the death should still read as the wall: " + player.getLastDamageSource());
		} finally {
			gone(player);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void noTotemSavesABrokenNeck(GameTestHelper helper) {
		TestPlayer player = player(helper, 1.5, 1.5, "Totem");
		try {
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			BrokenNeck.snap(player, RDAttachments.get(player), null);
			// A totem that had worked would have left them alive: dying is the proof it did not.
			helper.assertTrue(player.isDeadOrDying(), "a totem saved a broken neck");
		} finally {
			gone(player);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aTotemThatSavesYouFromAFallLeavesYourNeckWhole(GameTestHelper helper) {
		TestPlayer player = player(helper, 1.5, 1.5, "Saved");
		try {
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().flyIntoWall(), 40.0F);
			helper.assertTrue(player.isAlive(), "the totem did not save them from the wall");
			helper.assertTrue(!RDAttachments.get(player).neckBroken(),
					"a neck that was never broken hangs loose on a body that got up again");
		} finally {
			gone(player);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void bossesHaveNoNeckToBreak(GameTestHelper helper) {
		LivingEntity wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		LivingEntity dragon = EntityTypes.ENDER_DRAGON.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(!BrokenNeck.can(wither) && !BrokenNeck.can(dragon), "a boss can break its neck");
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aWhiplashedMobTurnsItsHeadSlowlyAndAimsWide(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		try {
			zombie.setNoAi(true);
			double healthy = turnOnce(zombie);
			RealisticDamageData data = RDAttachments.get(zombie);
			helper.assertTrue(MobAnatomy.aimSpread(zombie) == 1.0, "a sound zombie aims wide");
			data.wrench(1.0);
			double stiff = turnOnce(zombie);
			helper.assertTrue(healthy > 5.0, "the look did not turn the head at all: " + healthy);
			helper.assertTrue(stiff < healthy * 0.3, "a whiplashed head turned " + stiff + " degrees against "
					+ healthy + " for a sound one");
			helper.assertTrue(MobAnatomy.aimSpread(zombie) > 2.0, "a whiplashed zombie aims true");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	/** Faces it north, asks it to look south, and runs its look control once: degrees turned. */
	private static double turnOnce(Zombie zombie) {
		zombie.setYHeadRot(0.0F);
		zombie.yHeadRotO = 0.0F;
		zombie.getLookControl().setLookAt(zombie.getX() + 3.0, zombie.getEyeY(), zombie.getZ());
		zombie.getLookControl().tick();
		return Math.abs(net.minecraft.util.Mth.wrapDegrees(zombie.getYHeadRot()));
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theCommandsWrenchAndBreakNecks(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		try {
			zombie.setNoAi(true);
			var server = helper.getLevel().getServer();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
					"rdmg whiplash " + zombie.getStringUUID() + " 0.5");
			helper.assertTrue(RDAttachments.get(zombie).whiplashed(), "/rdmg whiplash did nothing");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
					"rdmg breakneck " + zombie.getStringUUID());
			helper.assertTrue(diedOfIt(zombie), "/rdmg breakneck did not kill through the neck");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}
}
