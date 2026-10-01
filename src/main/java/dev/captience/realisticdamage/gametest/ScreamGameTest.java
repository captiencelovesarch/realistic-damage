package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Who screams, and when (1.7).
 *
 * <p>The playtest: "breaking a limb isn't really portrayed well". The fracture
 * cue is the bone; the scream is the person. These pin the rules that keep it
 * from being noise: players only, never the unconscious, never a skull (that
 * is a daze, not a yell), one scream at a time, and a lost limb outranking a
 * broken one. What the client then plays is a TF2 line imported from the
 * player's own install; the server only decides that a scream happens.
 *
 * <p>Synchronous bodies, like every test that touches the shared Notifier
 * hooks: gametests run batched in one world, and the last-scream hook is
 * process-wide.
 */
public class ScreamGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
		player.setPos(at.x, at.y, at.z);
		RDAttachments.get(player).reset();
		return player;
	}

	@GameTest
	public void aBrokenArmIsAYell(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		Notifier.clearLastScream();
		Notifier.limbBreaks(player, BodyPart.LEFT_ARM);
		helper.assertTrue(Notifier.lastScream() == InjurySound.SCREAM_SHARP,
				"a broken arm should yell, got " + Notifier.lastScream());
		helper.succeed();
	}

	/** Through the real destruction path, so a future way of losing a limb cannot forget the scream. */
	@GameTest
	public void aLostLegIsAScream(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		Notifier.clearLastScream();
		DestructionLogic.destroy(player, RDAttachments.get(player), BodyPart.LEFT_LEG,
				WoundSeverity.CRITICAL, new Vec3(0.0, 0.0, 1.0));
		helper.assertTrue(Notifier.lastScream() == InjurySound.SCREAM_SEVERE,
				"losing a leg should scream, got " + Notifier.lastScream());
		discardLimbs(helper, player);
		helper.succeed();
	}

	/** A blast that breaks three bones is one scream, but a lost limb may cut in on a yell. */
	@GameTest
	public void oneVoiceAndTheWorseInjuryWins(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		Notifier.clearLastScream();
		Notifier.limbBreaks(player, BodyPart.LEFT_ARM);
		Notifier.clearLastScreamHook();
		Notifier.limbBreaks(player, BodyPart.RIGHT_ARM);
		helper.assertTrue(Notifier.lastScream() == null,
				"a second break in the same moment yelled over the first");

		DestructionLogic.destroy(player, RDAttachments.get(player), BodyPart.RIGHT_LEG,
				WoundSeverity.CRITICAL, new Vec3(1.0, 0.0, 0.0));
		helper.assertTrue(Notifier.lastScream() == InjurySound.SCREAM_SEVERE,
				"losing a limb must cut in on a yell, got " + Notifier.lastScream());

		Notifier.clearLastScreamHook();
		Notifier.limbBreaks(player, BodyPart.LEFT_LEG);
		helper.assertTrue(Notifier.lastScream() == null, "a yell cut in on a scream");
		discardLimbs(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theUnconsciousMakeNoSound(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.setUnconscious(true);
		Notifier.clearLastScream();
		Notifier.limbBreaks(player, BodyPart.LEFT_LEG);
		helper.assertTrue(Notifier.lastScream() == null, "an unconscious player screamed");
		data.setUnconscious(false);
		helper.succeed();
	}

	@GameTest
	public void aCrackedSkullIsADazeNotAYell(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		Notifier.clearLastScream();
		Notifier.limbBreaks(player, BodyPart.HEAD);
		helper.assertTrue(Notifier.lastScream() == null, "a skull fracture yelled");
		helper.succeed();
	}

	@GameTest
	public void aZombieHasNoPlayersVoice(GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
		zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(zombie);
		Notifier.clearLastScream();
		Notifier.limbBreaks(zombie, BodyPart.LEFT_ARM);
		helper.assertTrue(Notifier.lastScream() == null, "a zombie screamed with a player's voice");
		zombie.discard();
		helper.succeed();
	}

	private static void discardLimbs(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getEntitiesOfClass(SeveredLimb.class,
				new AABB(player.position(), player.position()).inflate(12.0)).forEach(SeveredLimb::discard);
	}
}
