package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.BleedingSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The 1.5 blood rework, at the seam a unit test cannot reach.
 *
 * <p>Most of the blood system moved to the client in 1.5, so most of it is
 * covered by {@code BloodFlowTest} against pure arithmetic. What remains on the
 * server is the burst thrown when a blow opens a wound and the snapshot the
 * client draws everything else from — and both of those touch a live level,
 * which is what this is for.
 */
public class BloodGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	/**
	 * The spray goes through {@code ServerLevel.sendParticles} with a per-drop
	 * velocity, which is a different call from the batched one the old bleeding
	 * used and the only place a mistake there would surface.
	 */
	@GameTest
	public void openingAWoundThrowsBloodWithoutBlowingUp(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		for (WoundSeverity severity : WoundSeverity.values()) {
			for (BodyPart part : BodyPart.values()) {
				BleedingSystem.spray(player, part, severity, new Vec3(1.0, 0.0, 0.0));
			}
		}
		// A wound with nothing to be thrown away from — poison, a fall, drowning.
		BleedingSystem.spray(player, BodyPart.TORSO, WoundSeverity.CRITICAL, Vec3.ZERO);
		helper.succeed();
	}

	/** A real hit has to reach the spray, not merely be able to if called directly. */
	@GameTest
	public void aSlashThatWoundsAlsoSprays(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		DamageSource slash = helper.getLevel().damageSources().playerAttack(attacker);

		TraumaPipeline.handle(player, data, slash, 6.0F, 6.0F, 6.0, false);
		helper.assertTrue(data.bleeding(), "the blow should have opened a wound to spray from");
		helper.succeed();
	}

	/**
	 * Blood is drawn client-side from this snapshot now, so a hand held over a
	 * wound has to be visible in it. Without the flag the client would carry on
	 * drawing an artery pumping while the number behind it had already slowed,
	 * and the one piece of first aid in the mod would look like it did nothing.
	 */
	@GameTest
	public void pressureReachesTheClient(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);

		helper.assertTrue(!BodySnapshot.of(data).underPressure(),
				"nobody is holding the wound yet");
		data.refreshPressure(true);
		helper.assertTrue(BodySnapshot.of(data).underPressure(),
				"pressure must be synced, or the client cannot stop the bleeding it draws");
		helper.succeed();
	}

	/**
	 * Every wound the client might have to draw has to survive the round trip
	 * through the snapshot's three-bit-per-region packing.
	 */
	@GameTest
	public void everyWoundSurvivesTheSnapshot(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		for (WoundSeverity severity : WoundSeverity.values()) {
			for (BodyPart part : BodyPart.values()) {
				data.reset();
				data.clearWounds();
				data.addWound(part, severity);
				BodySnapshot snapshot = BodySnapshot.of(data);
				helper.assertTrue(snapshot.wound(part) == severity,
						"a " + severity + " wound on the " + part + " came back as " + snapshot.wound(part));
				for (BodyPart other : BodyPart.values()) {
					if (other != part) {
						helper.assertTrue(snapshot.wound(other) == null,
								"wounding the " + part + " also marked the " + other);
					}
				}
			}
		}
		helper.succeed();
	}
}
