package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;

/**
 * A mob that is down does nothing of its own: out cold or knocked flat, a
 * zombie strikes at nothing, a skeleton looses no arrow and a creeper's fuse
 * dies away. It still falls: a mob with its AI switched off would hang in the
 * air.
 *
 * <p>Each case calls the mob's own methods inside its own body and cleans up
 * before it returns (see {@code InjuryAggroGameTest} for why).
 */
public class MobDownGameTest {
	private static final BlockPos AT = new BlockPos(1, 1, 1);

	private static void out(net.minecraft.world.entity.LivingEntity mob) {
		RealisticDamageData data = RDAttachments.get(mob);
		data.setConsciousness(0.0);
		data.setUnconscious(true);
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anUnconsciousCreepersFuseDiesAway(GameTestHelper helper) {
		Creeper creeper = helper.spawn(EntityTypes.CREEPER, AT);
		try {
			// Lit with its AI off: vanilla's own goal puts out a fuse that has
			// no one to blow up.
			creeper.setNoAi(true);
			creeper.setSwellDir(1);
			for (int i = 0; i < 20; i++) {
				creeper.tick();
			}
			creeper.setNoAi(false);
			helper.assertTrue(creeper.isAlive(), "the fuse was meant to be half burnt, not done");
			float lit = creeper.getSwelling(1.0F);
			helper.assertTrue(lit > 0.3F, "the fuse never caught: " + lit);
			out(creeper);
			helper.assertTrue(MobAnatomy.down(creeper), "an unconscious creeper is not down");
			for (int i = 0; i < 60; i++) {
				creeper.tick();
			}
			helper.assertTrue(creeper.isAlive(), "an unconscious creeper went off");
			helper.assertTrue(creeper.getSwelling(1.0F) < 0.01F, "its fuse is still lit: " + creeper.getSwelling(1.0F));
		} finally {
			creeper.discard();
		}
		helper.succeed();
	}

	/**
	 * A mob on the floor has its hitbox on the floor too (items.6): low and
	 * wide while it is out, standing again once it is up. The standing column
	 * over a body lying on the ground was what arrows hit and hands missed.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aDownZombieLiesInALowBox(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 1));
		try {
			zombie.setNoAi(true);
			double standing = zombie.getBbHeight();
			out(zombie);
			zombie.tick();
			double lying = zombie.getBbHeight();
			double wide = zombie.getBbWidth();
			helper.assertTrue(lying < 0.6 && Math.abs(wide - 0.6) < 1.0e-4,
					"an unconscious zombie's box is still standing, or got wider: " + wide + " x " + lying);
			helper.assertTrue(zombie.getPickRadius() > 0.3F,
					"a zombie lying down can't be picked out along its body: " + zombie.getPickRadius());
			RDAttachments.get(villager).knockDown(40);
			villager.tick();
			helper.assertTrue(villager.getBbHeight() < 0.6, "a knocked-down villager's box is still standing");
			RealisticDamageData data = RDAttachments.get(zombie);
			data.setUnconscious(false);
			data.setConsciousness(100.0);
			zombie.tick();
			helper.assertTrue(Math.abs(zombie.getBbHeight() - standing) < 1.0e-4 && zombie.getPickRadius() == 0.0F,
					"a zombie back on its feet has its box back: " + zombie.getBbHeight() + " of " + standing);
		} finally {
			zombie.discard();
			villager.discard();
		}
		helper.succeed();
	}

	/**
	 * The ragdoll is the body (items.8): where the nearest watcher's ragdoll
	 * comes to rest, the server puts the downed mob. Not a mob on its feet,
	 * not one moved farther than a ragdoll could have thrown it, and not
	 * into a wall.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aDownedMobIsPutWhereItsRagdollLies(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		TestPlayer watcher = TestPlayer.spawn(helper.getLevel(), helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1.0, 6.5)),
				0.0F, "rd-ragdoll-watcher");
		try {
			zombie.setNoAi(true);
			net.minecraft.world.phys.Vec3 at = zombie.position();
			net.minecraft.world.phys.Vec3 lying = at.add(1.5, 0.2, 0.5);
			boolean standing = dev.captience.realisticdamage.systems.RagdollRest.place(helper.getLevel(), zombie, watcher, lying);
			helper.assertTrue(!standing, "a mob on its feet was moved to a ragdoll");
			out(zombie);
			zombie.tick();
			boolean far = dev.captience.realisticdamage.systems.RagdollRest.place(helper.getLevel(), zombie, watcher,
					at.add(12.0, 0.2, 0.0));
			helper.assertTrue(!far, "a report twelve blocks off was believed");
			helper.setBlock(new BlockPos(5, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
			helper.setBlock(new BlockPos(5, 2, 2), net.minecraft.world.level.block.Blocks.STONE);
			helper.setBlock(new BlockPos(5, 3, 2), net.minecraft.world.level.block.Blocks.STONE);
			boolean wall = dev.captience.realisticdamage.systems.RagdollRest.place(helper.getLevel(), zombie, watcher,
					helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1.2, 2.5)));
			helper.assertTrue(!wall, "a body was put inside stone");
			boolean moved = dev.captience.realisticdamage.systems.RagdollRest.place(helper.getLevel(), zombie, watcher, lying);
			helper.assertTrue(moved && zombie.position().distanceTo(lying.subtract(0.0, 0.2, 0.0)) < 0.05,
					"the downed zombie was not put where its ragdoll lies: " + zombie.position() + " for " + lying);
		} finally {
			zombie.discard();
			watcher.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aKnockedDownZombieStrikesAtNothing(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
		try {
			RDAttachments.get(zombie).knockDown(40);
			helper.assertTrue(MobAnatomy.down(zombie), "a knocked-down zombie is not down");
			float health = villager.getHealth();
			boolean struck = zombie.doHurtTarget(helper.getLevel(), villager);
			helper.assertTrue(!struck && villager.getHealth() == health,
					"a zombie lying on the ground hit the villager next to it");
			// And on its feet again, it does.
			RDAttachments.get(zombie).clearKnockdown();
			helper.assertTrue(!MobAnatomy.down(zombie), "a zombie back on its feet is still down");
		} finally {
			zombie.discard();
			villager.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anUnconsciousSkeletonLoosesNoArrow(GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, AT);
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(5, 1, 1));
		AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0);
		try {
			out(skeleton);
			int before = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around).size();
			skeleton.performRangedAttack(villager, 1.0F);
			int after = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around).size();
			helper.assertTrue(after == before, "an unconscious skeleton shot");
		} finally {
			skeleton.discard();
			villager.discard();
			for (AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around)) {
				arrow.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anUnconsciousMobStillFalls(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 5, 1));
		try {
			out(zombie);
			double from = zombie.getY();
			for (int i = 0; i < 10; i++) {
				zombie.tick();
			}
			helper.assertTrue(zombie.getY() < from - 0.5, "an unconscious zombie hung in the air: fell "
					+ (from - zombie.getY()));
			helper.assertTrue(!zombie.isNoAi(), "its AI was switched off rather than skipped");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}
}
