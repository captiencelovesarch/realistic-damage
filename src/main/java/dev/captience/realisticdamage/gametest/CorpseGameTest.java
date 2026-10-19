package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * 1.7: a blast that kills takes the body apart (LethalBlast, from AFTER_DEATH,
 * which the survivor-only ExplosiveDismemberment never saw), and vanilla's
 * mannequin has a body now that the practice dummy is gone.
 */
public class CorpseGameTest {
	private static int destroyed(RealisticDamageData data) {
		int n = 0;
		for (BodyPart part : BodyPart.values()) {
			if (part.isLimb() && data.part(part).destroyed()) {
				n++;
			}
		}
		return n;
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aMannequinHasABody(GameTestHelper helper) {
		Mannequin mannequin = helper.spawn(EntityTypes.MANNEQUIN, new BlockPos(1, 1, 1));
		try {
			helper.assertTrue(MobAnatomy.participates(mannequin), "the mannequin is in the anatomy system");
			ServerPlayer attacker = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			attacker.setPos(mannequin.getX(), mannequin.getY(), mannequin.getZ() - 2.0);
			mannequin.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 6.0F);
			RealisticDamageData data = mannequin.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null, "a blow gives the mannequin a body");
			boolean hurt = false;
			for (BodyPart part : BodyPart.values()) {
				hurt |= data.part(part).fraction() < 1.0;
			}
			helper.assertTrue(hurt, "the blow landed on a region");
			helper.assertTrue(MobAnatomy.tracked() > 0, "and the body is ticked");
			MobAnatomy.tickBody(mannequin, data); // the generalised tick takes a non-mob
		} finally {
			mannequin.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aBlastAtTheFeetTakesEveryLimb(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		try {
			helper.getLevel().explode(null, zombie.getX(), zombie.getY() + 0.1, zombie.getZ(), 4.0F,
					Level.ExplosionInteraction.NONE);
			helper.assertTrue(!zombie.isAlive(), "point-blank TNT kills a zombie");
			RealisticDamageData data = zombie.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null, "the corpse has a body");
			helper.assertTrue(destroyed(data) == 4,
					"and the blast that killed it took all four limbs, took " + destroyed(data));
			helper.assertTrue(!data.hasPendingExplosion(), "the stash is clear afterwards");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aDistantLethalBlastTakesOnlyTheNearLimb(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		try {
			// Low on health, so a modest blast is the one that kills.
			zombie.setHealth(2.0F);
			helper.getLevel().explode(null, zombie.getX() + 3.6, zombie.getY() + 0.3, zombie.getZ(), 3.0F,
					Level.ExplosionInteraction.NONE);
			helper.assertTrue(!zombie.isAlive(), "the blast kills the weakened zombie");
			RealisticDamageData data = zombie.getAttached(RDAttachments.DATA);
			int n = data == null ? 0 : destroyed(data);
			helper.assertTrue(n >= 0 && n <= 1, "a weak, distant killing blast takes at most the near limb, took " + n);
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aDeathByBlowIsNotABlast(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		try {
			ServerPlayer attacker = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			attacker.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 2.0);
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 100.0F);
			helper.assertTrue(!zombie.isAlive(), "killed");
			RealisticDamageData data = zombie.getAttached(RDAttachments.DATA);
			helper.assertTrue(data == null || destroyed(data) == 0, "a sword kill is not a blast: no limbs lost to LethalBlast");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	/** Vanilla removes a dead body at one second; a ragdolled one has to last long enough to land. */
	@GameTest(environment = "realisticdamage:isolated", maxTicks = 200)
	public void aRagdolledBodyOutlastsVanillasSecond(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		helper.getLevel().explode(null, zombie.getX(), zombie.getY() + 0.1, zombie.getZ(), 4.0F,
				Level.ExplosionInteraction.NONE);
		helper.assertTrue(!zombie.isAlive(), "the blast kills the zombie");
		helper.runAfterDelay(40, () -> helper.assertTrue(!zombie.isRemoved(),
				"two seconds after death the body is still there, deathTime " + zombie.deathTime));
		helper.runAfterDelay(dev.captience.realisticdamage.config.Balance.CORPSE_HOLD_TICKS + 10, () -> {
			helper.assertTrue(zombie.isRemoved(), "and once the hold is over vanilla removes it");
			helper.succeed();
		});
	}
}
