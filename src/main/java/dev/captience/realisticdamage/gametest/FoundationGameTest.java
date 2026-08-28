package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Milestone 1 in-server checks: attachment lifecycle on a real ServerPlayer. */
public class FoundationGameTest {
	@GameTest
	public void attachmentLifecycle(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		RealisticDamageData data = RDAttachments.get(player);
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).state() == BodyState.HEALTHY, "fresh player must be HEALTHY");

		data.part(BodyPart.LEFT_LEG).damage(12.5); // 3.5/16 -> CRITICAL
		data.addWound(BodyPart.LEFT_LEG, WoundSeverity.MODERATE);
		data.setBlood(42.0);
		helper.assertTrue(RDAttachments.get(player).part(BodyPart.LEFT_LEG).state() == BodyState.CRITICAL,
				"attachment must be the same instance across get() calls");

		RDAttachments.pushSnapshot(player);
		var snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		helper.assertTrue(snapshot != null && snapshot.state(BodyPart.LEFT_LEG) == BodyState.CRITICAL,
				"snapshot must reflect the damaged leg");
		helper.assertTrue(snapshot.wound(BodyPart.LEFT_LEG) == WoundSeverity.MODERATE,
				"snapshot must carry the worst wound per region");

		data.reset();
		helper.assertTrue(RDAttachments.get(player).blood() == 100.0, "reset must restore blood");
		helper.succeed();
	}
}
