package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DamageHooks;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.damage.RDDamageTypes;
import dev.captience.realisticdamage.systems.DeathHold;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * The server half of death violence (1.5): scoring the fatal blow.
 *
 * <p>This scoring hangs off ALLOW_DEATH, not the trauma pipeline, and the
 * distinction was found the hard way: Fabric's AFTER_DAMAGE is documented not
 * to fire when the damage kills, so the pipeline never sees a killing blow at
 * all. A first version stashed the violence there, its gametest "verified" it
 * by pre-setting health to zero and calling the pipeline directly — a path the
 * live game never takes — and the end-to-end client test came back with
 * violence 0.0. These tests call the real scoring entry instead, with real
 * damage sources; the event plumbing above it is covered by the client test,
 * where the payload either arrives or does not.
 */
public class DeathGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	@GameTest
	public void aMassiveKillingBlowScoresFullViolence(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().generic(), 60.0F);
		helper.assertTrue(violence >= 0.99,
				"sixty raw damage against twenty max health should be a cut, got " + violence);
		helper.assertTrue(data.consumeDeathViolence(player.tickCount) >= 0.99,
				"the score must also be stashed for AFTER_DEATH to send");
		helper.succeed();
	}

	/** A weak finishing blow is a quiet end, not an execution. */
	@GameTest
	public void aFinishingPokeScoresAlmostNothing(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().generic(), 2.0F);
		helper.assertTrue(violence < 0.1,
				"two damage that happens to kill should stay a slow ending, got " + violence);
		helper.succeed();
	}

	/** Falls score by height, not by their deliberately compressed HP damage. */
	@GameTest
	public void aHugeLethalFallScoresByItsHeight(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setPendingFall(47.0, 1.0, true);

		// The fatal damage figure for this fall is modest by design (1.4's
		// lethality compression) — the violence must come from the height.
		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().fall(), 12.0F);
		helper.assertTrue(violence >= 0.99,
				"a 50-block drop should be a cut whatever its HP number, got " + violence);
		data.clearPendingFall();
		helper.succeed();
	}

	/** The void, /kill and starving are ignored by the classifier: slow ending. */
	@GameTest
	public void deathsTheClassifierIgnoresScoreNothing(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
		helper.assertTrue(violence == 0.0,
				"falling out of the world is not a blow, got " + violence);
		helper.assertTrue(data.consumeDeathViolence(player.tickCount) == 0.0,
				"...and must stash nothing");
		helper.succeed();
	}

	/**
	 * The totem seam. ALLOW_DEATH fires before the totem check, so the stash is
	 * tick-stamped: one orphaned by a refused death cannot colour a later,
	 * calmer one — consuming on any other tick gets nothing, and the stash does
	 * not survive the attempt.
	 */
	@GameTest
	public void anOrphanedStashExpiresInsteadOfHaunting(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.stashDeathViolence(1.0, player.tickCount);

		helper.assertTrue(data.consumeDeathViolence(player.tickCount + 600) == 0.0,
				"a stale stash coloured a death thirty seconds later");
		helper.assertTrue(data.consumeDeathViolence(player.tickCount) == 0.0,
				"the stash survived being consumed");
		helper.succeed();
	}

	// --- 1.7: what kind of death it was, and keeping the body for the camera ---

	/**
	 * The death camera moves by kind of death, and the kind comes from the
	 * killing source. Specific before general: lava is fire and must read as
	 * lava, a blast is not a blow, a bleed-out is the faint.
	 */
	@GameTest
	public void deathKindsFollowTheKillingSource(GameTestHelper helper) {
		var sources = helper.getLevel().damageSources();
		assertKind(helper, DeathKind.LAVA, sources.lava(), "lava");
		assertKind(helper, DeathKind.FIRE, sources.onFire(), "burning");
		assertKind(helper, DeathKind.EXPLOSION, sources.explosion(null, null), "a blast");
		assertKind(helper, DeathKind.FALL, sources.fall(), "a fall");
		assertKind(helper, DeathKind.FALL, sources.flyIntoWall(), "an elytra into a wall");
		assertKind(helper, DeathKind.CRUSH, sources.inWall(), "suffocating in a wall");
		assertKind(helper, DeathKind.DROWNING, sources.drown(), "drowning");
		assertKind(helper, DeathKind.FREEZE, sources.freeze(), "powder snow");
		assertKind(helper, DeathKind.SHOCK, sources.lightningBolt(), "lightning");
		assertKind(helper, DeathKind.VOID, sources.fellOutOfWorld(), "the void");
		assertKind(helper, DeathKind.FADE, sources.starve(), "starving");
		assertKind(helper, DeathKind.FADE, sources.wither(), "withering");
		assertKind(helper, DeathKind.FADE, RDDamageTypes.bleedOut(helper.getLevel()), "bleeding out");
		assertKind(helper, DeathKind.PROJECTILE, sources.source(DamageTypes.ARROW), "an arrow");
		assertKind(helper, DeathKind.BLOW, sources.generic(), "a plain blow");
		assertKind(helper, DeathKind.BLOW,
				RDDamageTypes.coreDestroyed(helper.getLevel(), BodyPart.TORSO), "a crushed chest");
		helper.succeed();
	}

	private static void assertKind(GameTestHelper helper, DeathKind expected, DamageSource source, String what) {
		DeathKind got = DeathKind.of(source);
		helper.assertTrue(got == expected, what + " should die as " + expected + ", got " + got);
	}

	/**
	 * "Crushed beyond saving" by an iron golem is the mod's own damage type, so
	 * the violence scoring skipped it and the most violent death in the mod got
	 * a bleed-out's slow fade. Through the two Fabric events the live death
	 * fires, in order — a mock player has no connection, so it cannot be hurt
	 * for real, but these are the handlers a real kill reaches.
	 */
	@GameTest
	public void aCrushedChestIsAsViolentAsDeathGets(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RDAttachments.get(player).reset();
		DamageSource crushed = RDDamageTypes.coreDestroyed(helper.getLevel(), BodyPart.TORSO);
		helper.assertTrue(ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(player, crushed, Float.MAX_VALUE),
				"nothing should refuse this death");
		ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(player, crushed);
		helper.assertTrue(DamageHooks.lastDeathViolence() >= 0.99,
				"a crushed chest went out with violence " + DamageHooks.lastDeathViolence());
		helper.assertTrue(DamageHooks.lastDeathKind() == DeathKind.BLOW,
				"and it is a blow, got " + DamageHooks.lastDeathKind());
		helper.succeed();
	}

	/**
	 * The freeze. Vanilla removes a dead player twenty ticks in, which takes
	 * every entity off their screen and stops every update; with the death camera
	 * watching, that removal must wait for the release — and then happen at
	 * once, exactly as vanilla's would have.
	 */
	@GameTest
	public void aWatchedBodyStaysInTheWorldUntilReleased(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		player.setHealth(0.0F);   // dead; a mock player has no connection to be hurt through

		DeathHold.request(player, true);
		for (int i = 0; i < 40; i++) {
			player.baseTick();
		}
		helper.assertTrue(!player.isRemoved(),
				"a body the death camera is watching was removed after " + player.deathTime + " ticks");
		helper.assertTrue(player.deathTime >= 40, "the death clock stopped while held: " + player.deathTime);

		DeathHold.request(player, false);
		player.baseTick();
		helper.assertTrue(player.isRemoved(), "released, the body must go on the next tick as vanilla's does");
		helper.succeed();
	}

	/** Without a watcher, vanilla exactly: gone at twenty. And a living player cannot ask to be held. */
	@GameTest
	public void anUnwatchedBodyGoesAtTwentyTicks(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		DeathHold.request(player, true);   // alive: ignored
		player.setHealth(0.0F);
		for (int i = 0; i < 20; i++) {
			player.baseTick();
		}
		helper.assertTrue(player.isRemoved(),
				"an unwatched body outlived vanilla's twenty ticks: deathTime " + player.deathTime);
		helper.succeed();
	}

	/**
	 * A client that never releases cannot keep a dead player in the level
	 * forever — a held corpse counts against everyone else's night skip.
	 * Checked at a stated game time rather than by waiting, so the body stays
	 * inside one tick like every test here.
	 */
	@GameTest
	public void aForgottenHoldLapses(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		player.setHealth(0.0F);
		DeathHold.request(player, true);
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(DeathHold.holdingAt(player, now + Balance.DEATH_HOLD_MAX_TICKS),
				"the hold lapsed before its cap");
		helper.assertTrue(!DeathHold.holdingAt(player, now + Balance.DEATH_HOLD_MAX_TICKS + 1),
				"a hold outlived its cap");
		for (int i = 0; i < 20; i++) {
			player.baseTick();
		}
		helper.assertTrue(player.isRemoved(), "a lapsed hold must leave the body to vanilla's removal");
		helper.succeed();
	}
}
