package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

/**
 * Mobs with bodies (1.7), through the production hooks: the same damage events
 * a player's body listens to, the same per-tick systems run by hand where a
 * wait would cost real seconds.
 */
public class MobAnatomyGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, -2.0);
		return player;
	}

	private <T extends Mob> T spawnStill(GameTestHelper helper, EntityType<T> type) {
		T mob = helper.spawn(type, new BlockPos(1, 1, 1));
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		return mob;
	}

	private static void tick(Mob mob, RealisticDamageData data, int ticks) {
		for (int i = 0; i < ticks && mob.isAlive(); i++) {
			mob.tickCount++;
			MobAnatomy.tickBody(mob, data);
		}
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aTaggedMobTakesRegionalTrauma(GameTestHelper helper) {
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			ServerPlayer attacker = mockPlayer(helper);
			helper.assertTrue(zombie.getAttached(RDAttachments.DATA) == null, "a fresh zombie carries no body until it is hurt");
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 6.0F);
			RealisticDamageData data = zombie.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null, "a blow gives a tagged mob a body");
			boolean hurtSomewhere = false;
			for (BodyPart part : BodyPart.values()) {
				hurtSomewhere |= data.part(part).fraction() < 1.0;
			}
			helper.assertTrue(hurtSomewhere, "and the blow landed on a region");
			helper.assertTrue(MobAnatomy.isTracked(zombie), "and the mob is being ticked");
			helper.assertTrue(zombie.getAttached(RDAttachments.SNAPSHOT) != null, "and its snapshot exists for the clients");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	/**
	 * Since slice three every vanilla mob is in the tag, so "untagged" is tested
	 * on the one living thing that is not a mob: an armour stand.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void anythingOutsideTheTagIsLeftAlone(GameTestHelper helper) {
		var stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(1, 1, 1));
		try {
			stand.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(mockPlayer(helper)), 0.5F);
			helper.assertTrue(stand.getAttached(RDAttachments.DATA) == null, "an armour stand has no body");
			helper.assertTrue(!MobAnatomy.participates(stand), "and takes no part");
		} finally {
			stand.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theRuleOffLeavesMobsVanilla(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean previous = config.enableMobAnatomy;
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			config.enableMobAnatomy = false;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(mockPlayer(helper)), 6.0F);
			helper.assertTrue(zombie.getAttached(RDAttachments.DATA) == null, "with the rule off no mob ever gets a body");
		} finally {
			config.enableMobAnatomy = previous;
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aBleedingMobBleedsOut(GameTestHelper helper) {
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			RealisticDamageData data = RDAttachments.get(zombie);
			data.reset();
			data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
			data.setBlood(2.0);
			float before = zombie.getHealth();
			tick(zombie, data, 100);
			helper.assertTrue(data.blood() <= 0.0, "a critical wound drains what little blood was left, at " + data.blood());
			helper.assertTrue(zombie.getHealth() < before, "and then the hearts, " + before + " to " + zombie.getHealth());
			tick(zombie, data, 1500);
			helper.assertTrue(!zombie.isAlive(), "and a mob that bleeds long enough bleeds out");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aDestroyedLegSlowsTheMob(GameTestHelper helper) {
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			RealisticDamageData data = RDAttachments.get(zombie);
			data.reset();
			double whole = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
			data.part(BodyPart.LEFT_LEG).setDestroyed(true);
			tick(zombie, data, 1);
			double crippled = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
			helper.assertTrue(crippled < whole, "a destroyed leg must slow the mob, " + whole + " to " + crippled);
			data.part(BodyPart.RIGHT_LEG).setDestroyed(true);
			tick(zombie, data, 1);
			double legless = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
			helper.assertTrue(legless < crippled, "and losing both is worse, " + crippled + " to " + legless);
			helper.assertTrue(!MobAnatomy.settled(data), "a mob missing a limb never settles out of the tick set");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void noArmsNoStrike(GameTestHelper helper) {
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			ServerPlayer target = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(zombie);
			data.reset();
			helper.assertTrue(MobAnatomy.canStrike(zombie), "two arms can swing");
			data.part(BodyPart.LEFT_ARM).setDestroyed(true);
			helper.assertTrue(MobAnatomy.canStrike(zombie), "one arm can still swing");
			data.part(BodyPart.RIGHT_ARM).setDestroyed(true);
			helper.assertTrue(!MobAnatomy.canStrike(zombie), "no arms, no swing");
			helper.assertTrue(!zombie.doHurtTarget(helper.getLevel(), target), "and the strike itself is refused");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void noMainArmNoShot(GameTestHelper helper) {
		Skeleton skeleton = spawnStill(helper, EntityTypes.SKELETON);
		try {
			RealisticDamageData data = RDAttachments.get(skeleton);
			data.reset();
			helper.assertTrue(MobAnatomy.canShoot(skeleton), "a whole skeleton can shoot");
			data.part(ImpairmentSystem.armFor(skeleton, false)).setDestroyed(true);
			helper.assertTrue(MobAnatomy.canShoot(skeleton), "the off arm gone, it still draws");
			data.part(ImpairmentSystem.armFor(skeleton, true)).setDestroyed(true);
			helper.assertTrue(!MobAnatomy.canShoot(skeleton), "the drawing arm gone, no more arrows");
		} finally {
			skeleton.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aConcussedMobForgetsItsTarget(GameTestHelper helper) {
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			ServerPlayer target = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(zombie);
			data.reset();
			data.setConcussion(true);
			zombie.setTarget(target);
			zombie.tickCount = Balance.MOB_DAZE_FORGET_TICKS - 1;
			tick(zombie, data, 1);
			helper.assertTrue(zombie.getTarget() == null, "a dazed mob loses the thread on the beat");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void collapseStopsTheMobThinkingAndWakingStartsIt(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setPersistenceRequired();
		try {
			RealisticDamageData data = RDAttachments.get(zombie);
			data.reset();
			helper.assertTrue(!zombie.isNoAi(), "the zombie thinks to begin with");
			data.drainConsciousness(Balance.CONSCIOUSNESS_MAX);
			tick(zombie, data, 1);
			helper.assertTrue(data.unconscious(), "at zero it is out");
			helper.assertTrue(zombie.isNoAi(), "and it stops thinking");
			// Long enough out, and enough consciousness back, and it comes round.
			data.setUnconsciousTicks(Balance.MIN_UNCONSCIOUS_TICKS);
			data.restoreConsciousness(Balance.CONSCIOUSNESS_MAX);
			tick(zombie, data, 1);
			helper.assertTrue(!data.unconscious(), "awake again");
			helper.assertTrue(!zombie.isNoAi(), "and thinking again");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anIllagerHasABodyToo(GameTestHelper helper) {
		Pillager pillager = spawnStill(helper, EntityTypes.PILLAGER);
		try {
			pillager.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(mockPlayer(helper)), 6.0F);
			RealisticDamageData data = pillager.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null, "a pillager is in the tag and gets a body");
			helper.assertTrue(MobAnatomy.bleeds(pillager), "and it has blood to lose");
		} finally {
			pillager.discard();
		}
		helper.succeed();
	}

	/** Bones do not bleed: a skeleton breaks and loses limbs like anyone, but no wound is ever opened on it. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aSkeletonNeverBleeds(GameTestHelper helper) {
		Skeleton skeleton = spawnStill(helper, EntityTypes.SKELETON);
		try {
			helper.assertTrue(!MobAnatomy.bleeds(skeleton), "the skeleton family is tagged bloodless");
			ServerPlayer attacker = mockPlayer(helper);
			attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
			for (int i = 0; i < 4; i++) {
				skeleton.invulnerableTime = 0;
				skeleton.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 9.0F);
			}
			RealisticDamageData data = skeleton.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null, "it still has a body");
			helper.assertTrue(data.wounds().isEmpty(), "but four sword blows opened no wound on it: " + data.wounds());
			helper.assertTrue(data.blood() >= Balance.BLOOD_MAX - 1.0e-6, "and it lost no blood");
			// Losing a limb opens no stump wound either, and its limb bleeds nothing.
			DestructionLogic.destroy(skeleton, data, BodyPart.LEFT_LEG, WoundSeverity.CRITICAL, new Vec3(0.0, 0.0, 1.0));
			helper.assertTrue(data.part(BodyPart.LEFT_LEG).destroyed(), "the leg still comes off");
			helper.assertTrue(data.wounds().isEmpty(), "with no stump wound");
			for (SeveredLimb limb : helper.getLevel().getEntitiesOfClass(SeveredLimb.class,
					skeleton.getBoundingBox().inflate(8.0))) {
				helper.assertTrue(limb.bloodless(), "and the bone on the floor is bloodless");
				helper.assertTrue(limb.bleedRate() == 0.0, "and never bleeds");
				limb.discard();
			}
		} finally {
			skeleton.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aDazedOrBrokenSkeletonShootsWide(GameTestHelper helper) {
		Skeleton skeleton = spawnStill(helper, EntityTypes.SKELETON);
		try {
			RealisticDamageData data = RDAttachments.get(skeleton);
			data.reset();
			helper.assertTrue(MobAnatomy.aimSpread(skeleton) == 1.0, "a whole skeleton shoots as vanilla does");
			data.setConcussion(true);
			helper.assertTrue(Math.abs(MobAnatomy.aimSpread(skeleton) - Balance.MOB_DAZE_AIM_SPREAD) < 1.0e-9,
					"a rattled head spreads the shot, got " + MobAnatomy.aimSpread(skeleton));
			data.part(ImpairmentSystem.armFor(skeleton, true)).setBroken(true);
			helper.assertTrue(Math.abs(MobAnatomy.aimSpread(skeleton)
					- Balance.MOB_DAZE_AIM_SPREAD * Balance.MOB_BROKEN_ARM_AIM_SPREAD) < 1.0e-9,
					"and a broken drawing arm compounds it");
		} finally {
			skeleton.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aHardLandingKnocksAMobDown(GameTestHelper helper) {
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			RealisticDamageData data = RDAttachments.get(zombie);
			data.reset();
			zombie.causeFallDamage(14.0, 1.0F, helper.getLevel().damageSources().fall());
			helper.assertTrue(data.knockedDown(), "a fourteen-block landing puts a zombie on the ground");
			helper.assertTrue(data.part(BodyPart.LEFT_LEG).fraction() < 1.0 || data.part(BodyPart.RIGHT_LEG).fraction() < 1.0,
					"and hurts its legs");
			helper.assertTrue(data.pendingFallHeight() == 0.0, "and the landing record is spent");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aSettledMobLeavesTheTickSet(GameTestHelper helper) {
		Zombie zombie = spawnStill(helper, EntityTypes.ZOMBIE);
		try {
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(mockPlayer(helper)), 2.0F);
			helper.assertTrue(MobAnatomy.isTracked(zombie), "hurt, it is ticked");
			RealisticDamageData data = RDAttachments.get(zombie);
			data.reset();   // as good as new: nothing left to tick
			MobAnatomy.tick();
			helper.assertTrue(!MobAnatomy.isTracked(zombie), "whole, it is dropped from the set");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}
}
