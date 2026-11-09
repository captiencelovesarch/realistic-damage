package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

/**
 * Thrown off your feet without dying (active ragdolls phase 4): a blast or a
 * heavy blow that does not kill knocks the body down, through the same
 * AFTER_DAMAGE path a real hit takes. Small blasts and ordinary blows do not.
 */
public class KnockdownGameTest {
	private static Zombie zombie(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		return zombie;
	}

	private static boolean down(Zombie zombie) {
		RealisticDamageData data = zombie.getAttached(RDAttachments.DATA);
		return data != null && data.knockedDown();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aBlastThatDoesNotKillKnocksTheBodyDown(GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		try {
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().explosion(null, null), 9.0F);
			helper.assertTrue(zombie.isAlive(), "the blast was meant not to kill");
			helper.assertTrue(down(zombie), "a nine-damage blast left the zombie on its feet");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aSmallBlastDoesNot(GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		try {
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().explosion(null, null), 3.0F);
			helper.assertTrue(!down(zombie), "a three-damage blast knocked the zombie down");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aGolemsBlowKnocksTheBodyDown(GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(3, 1, 1));
		golem.setNoAi(true);
		try {
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(golem), 5.0F);
			helper.assertTrue(zombie.isAlive(), "the blow was meant not to kill");
			helper.assertTrue(down(zombie), "an iron golem's blow left the zombie on its feet");
		} finally {
			zombie.discard();
			golem.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anOrdinaryBlowHasToBeHuge(GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		Zombie hitter = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
		hitter.setNoAi(true);
		try {
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(hitter), 6.0F);
			helper.assertTrue(!down(zombie), "an ordinary six-damage blow knocked the zombie down");
			zombie.invulnerableTime = 0;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(hitter), 14.0F);
			helper.assertTrue(zombie.isAlive(), "the big blow was meant not to kill");
			helper.assertTrue(down(zombie), "a fourteen-damage blow left the zombie on its feet");
		} finally {
			zombie.discard();
			hitter.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theRuleTurnsItOff(GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		ServerConfig config = ServerConfig.get();
		boolean before = config.knockdownFromBlows;
		try {
			config.knockdownFromBlows = false;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().explosion(null, null), 9.0F);
			helper.assertTrue(!down(zombie), "with the rule off a blast still knocked the zombie down");
		} finally {
			config.knockdownFromBlows = before;
			zombie.discard();
		}
		helper.succeed();
	}
}
