package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.dummy.PracticeDummy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The practice dummy is a real ServerPlayer, so the point of these is to prove
 * the mod genuinely cannot tell the difference — if it could, the dummy would
 * be useless for testing the mechanics it exists to test.
 */
public class PracticeDummyGameTest {
	private PracticeDummy spawn(GameTestHelper helper) {
		Vec3 pos = helper.absoluteVec(new Vec3(1.0, 2.0, 1.0));
		return PracticeDummy.spawn(helper.getLevel(), pos, 0.0F, "TestDummy");
	}

	private void remove(PracticeDummy dummy) {
		dummy.connection.disconnect(Component.literal("test over"));
	}

	@GameTest
	public void spawnsAsARealPlayer(GameTestHelper helper) {
		PracticeDummy dummy = spawn(helper);
		try {
			helper.assertTrue(
					helper.getLevel().getServer().getPlayerList().getPlayers().contains(dummy),
					"the dummy must join the player list, or nothing will tick it");
			helper.assertTrue(dummy.connection != null,
					"connection must be set — ServerPlayer dereferences it unguarded all over");
			helper.assertTrue(dummy.isAlive() && dummy.getHealth() > 0.0F, "and be alive");
			// The 1.1 aggro research turned this up: an unloaded fake client would
			// make the dummy permanently invulnerable and useless as a target.
			helper.assertTrue(!dummy.isInvulnerableTo(helper.getLevel(),
							helper.getLevel().damageSources().generic()),
					"a dummy that cannot be hurt is not a practice target");
		} finally {
			remove(dummy);
		}
		helper.succeed();
	}

	/** The whole point: the body system applies to it with no code aware it is not a person. */
	@GameTest
	public void takesLocationalTraumaLikeAnyPlayer(GameTestHelper helper) {
		PracticeDummy dummy = spawn(helper);
		try {
			RealisticDamageData data = RDAttachments.get(dummy);
			data.reset();
			double before = data.part(BodyPart.HEAD).condition();
			dev.captience.realisticdamage.damage.TraumaPipeline.handle(dummy, data,
					helper.getLevel().damageSources().generic(), 8, 8, 8, false);
			helper.assertTrue(data.part(BodyPart.HEAD).condition() < before
							|| data.part(BodyPart.TORSO).condition() < 20.0,
					"the dummy must take real body trauma, not just hearts");
		} finally {
			remove(dummy);
		}
		helper.succeed();
	}

	@GameTest
	public void restorePutsItBackTogether(GameTestHelper helper) {
		PracticeDummy dummy = spawn(helper);
		try {
			RealisticDamageData data = RDAttachments.get(dummy);
			data.part(BodyPart.LEFT_LEG).setCondition(1.0);
			data.part(BodyPart.LEFT_LEG).setBroken(true);
			data.setBlood(20.0);
			dummy.setHealth(2.0F);

			dummy.restore();

			RealisticDamageData restored = RDAttachments.get(dummy);
			helper.assertTrue(!restored.part(BodyPart.LEFT_LEG).broken(), "the leg must be mended");
			helper.assertTrue(restored.blood() > 90.0, "blood must be back");
			helper.assertTrue(dummy.getHealth() == dummy.getMaxHealth(), "and so must health");
		} finally {
			remove(dummy);
		}
		helper.succeed();
	}

	@GameTest
	public void fightBackIsOffUntilAskedFor(GameTestHelper helper) {
		PracticeDummy dummy = spawn(helper);
		try {
			helper.assertTrue(!dummy.fightBack(), "a dummy stands still until told otherwise");
			dummy.setFightBack(true);
			helper.assertTrue(dummy.fightBack(), "and swings once it is");
		} finally {
			remove(dummy);
		}
		helper.succeed();
	}

	/** Dummies must not be mistaken for people by the mod's own player sweeps. */
	@GameTest
	public void removalLeavesNothingBehind(GameTestHelper helper) {
		PracticeDummy dummy = spawn(helper);
		remove(dummy);
		helper.assertTrue(PracticeDummy.all(helper.getLevel().getServer()).isEmpty(),
				"removing a dummy must take it out of the player list");
		helper.succeed();
	}
}
