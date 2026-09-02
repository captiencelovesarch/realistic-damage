package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/** The 1.4 fall rework: height-keyed severity, asymmetric legs, knockdown (spec §7, §13, §29-30). */
public class LandingGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	private DamageSource fall(GameTestHelper helper) {
		return helper.getLevel().damageSources().fall();
	}

	/**
	 * Records a landing the way the fall mixin would, then runs the pipeline.
	 * {@code height} is blocks beyond the safe fall distance.
	 */
	private void land(GameTestHelper helper, ServerPlayer player, RealisticDamageData data,
			double height, double surface, boolean leadingLeft, double appliedHp) {
		data.setPendingFall(height, surface, leadingLeft);
		TraumaPipeline.handle(player, data, fall(helper), appliedHp, appliedHp, appliedHp, false);
		data.clearPendingFall();
	}

	/**
	 * The bug the rework exists to fix. Good armour used to delete the event
	 * entirely: post-armour HP drove the trauma, so a long drop in diamond touched
	 * nothing. Height is what the skeleton experiences.
	 */
	@GameTest
	public void armourNoLongerHidesALongDrop(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		// A forty-block drop that vanilla, after armour and Feather Falling, has
		// reduced to a single point of damage.
		land(helper, player, data, 37.0, 1.0, true, 1.0);
		helper.assertTrue(data.part(BodyPart.HEAD).fraction() < 1.0,
				"a forty-block drop must reach the head even through armour");
		helper.assertTrue(data.concussion(), "...hard enough to rattle the brain");
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).state().isAtLeastAsBadAs(BodyState.INJURED),
				"...and must wreck the leading leg, got " + data.part(BodyPart.LEFT_LEG).state());
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).broken(), "...breaking it outright");
		helper.succeed();
	}

	@GameTest
	public void shortDropsStayALegInjury(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		land(helper, player, data, 4.0, 1.0, true, 4.0);
		helper.assertTrue(data.part(BodyPart.HEAD).fraction() == 1.0f
						|| data.part(BodyPart.HEAD).fraction() > 0.999,
				"a seven-block drop must not touch the head");
		helper.assertTrue(data.part(BodyPart.TORSO).fraction() > 0.999,
				"...nor the torso");
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).fraction() < 1.0, "but it must reach the legs");
		helper.succeed();
	}

	/** Nobody lands on both feet equally, and which foot it was must be reproducible. */
	@GameTest
	public void oneLegTakesTheBruntAndItIsTheLeadingOne(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		land(helper, player, data, 18.0, 1.0, true, 10.0);
		double left = data.part(BodyPart.LEFT_LEG).fraction();
		double right = data.part(BodyPart.RIGHT_LEG).fraction();
		helper.assertTrue(left < right, "the leading (left) leg must take the worse of it");

		// The mirror image, on a fresh body, must be exactly mirrored.
		data.reset();
		land(helper, player, data, 18.0, 1.0, false, 10.0);
		helper.assertTrue(Math.abs(data.part(BodyPart.RIGHT_LEG).fraction() - left) < 1e-6,
				"landing on the other foot must be the same injury on the other side");
		helper.succeed();
	}

	@GameTest
	public void softGroundGivesBack(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		land(helper, player, data, 15.0, Balance.LANDING_SURFACE_HARD, true, 12.0);
		double onStone = data.part(BodyPart.LEFT_LEG).fraction();

		data.reset();
		land(helper, player, data, 15.0, Balance.LANDING_SURFACE_SOFT, true, 12.0);
		double onHay = data.part(BodyPart.LEFT_LEG).fraction();
		helper.assertTrue(onHay > onStone,
				"hay must hurt less than stone: " + onHay + " vs " + onStone);
		helper.succeed();
	}

	@GameTest
	public void aHardLandingPutsYouOnTheGround(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		land(helper, player, data, 18.0, 1.0, true, 12.0);
		helper.assertTrue(data.knockedDown(), "a twenty-one-block drop must take your legs out");

		ImpairmentSystem.tick(player, data);
		helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.001,
				"a knocked-down player must not be able to walk it off");
		helper.succeed();
	}

	@GameTest
	public void routineDropsDoNotKnockYouOver(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		land(helper, player, data, Balance.KNOCKDOWN_MIN_HEIGHT - 1.0, 1.0, true, 5.0);
		helper.assertTrue(!data.knockedDown(), "ordinary drops must stay unremarkable");
		helper.succeed();
	}

	@GameTest
	public void hayLeavesYouStanding(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		// The same drop that floors you on stone.
		land(helper, player, data, 10.0, Balance.LANDING_SURFACE_SOFT, true, 8.0);
		helper.assertTrue(!data.knockedDown(),
				"cushioning must keep you on your feet, not merely hurt less");
		data.reset();
		land(helper, player, data, 10.0, Balance.LANDING_SURFACE_HARD, true, 8.0);
		helper.assertTrue(data.knockedDown(), "...whereas the same drop onto stone floors you");
		helper.succeed();
	}

	@GameTest
	public void youGetBackUpOnYourOwn(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		land(helper, player, data, 40.0, 1.0, true, 15.0);
		helper.assertTrue(data.knockedDown(), "floored by the worst landing in the game");

		for (int i = 0; i < Balance.KNOCKDOWN_TICKS_MAX + 1; i++) {
			data.tickKnockdown(Balance.KNOCKDOWN_RECOVERY_TICKS);
		}
		helper.assertTrue(!data.knockedDown(), "the knockdown must always end on its own");
		helper.assertTrue(data.knockdownRecoveryTicks() > 0,
				"...and roll into an unsteady tail rather than snapping back to full");

		// The tail must also drain, or the penalty would be permanent.
		for (int i = 0; i < Balance.KNOCKDOWN_RECOVERY_TICKS + 1; i++) {
			data.tickKnockdown(Balance.KNOCKDOWN_RECOVERY_TICKS);
		}
		helper.assertTrue(data.knockdownRecoveryTicks() == 0, "the unsteadiness must wear off too");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void aKnockedDownPlayerLiesOnTheFloor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(
				new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.knockDown(40);
		player.doTick();
		helper.assertTrue(player.getPose() == Pose.SWIMMING,
				"a knocked-down player must be visibly on the ground, got " + player.getPose());
		helper.succeed();
	}

	/** Injured legs make the next fall worse (spec §13) — still true through the new routing. */
	@GameTest
	public void alreadyHurtLegsMakeTheNextLandingWorse(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		land(helper, player, data, 10.0, 1.0, true, 8.0);
		double freshLoss = 1.0 - data.part(BodyPart.LEFT_LEG).fraction();

		// Only lightly pre-damaged, deliberately. Condition clamps at zero, so a
		// leg started near the bottom would appear to *lose less* than a fresh one
		// purely because it had less left to give — which measures the clamp
		// rather than the amplifier.
		data.reset();
		data.part(BodyPart.LEFT_LEG).damage(BodyPart.LEFT_LEG.maxCondition() * 0.3);
		double before = data.part(BodyPart.LEFT_LEG).fraction();
		land(helper, player, data, 10.0, 1.0, true, 8.0);
		double injuredLoss = before - data.part(BodyPart.LEFT_LEG).fraction();
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).fraction() > 0.0,
				"the leg must not bottom out, or this measures clamping instead of amplification");
		helper.assertTrue(injuredLoss > freshLoss,
				"a wrecked leg must fare worse on the next landing: " + injuredLoss + " vs " + freshLoss);
		helper.succeed();
	}
}
