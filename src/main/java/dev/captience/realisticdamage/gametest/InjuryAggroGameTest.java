package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Predators pick off the wounded (1.6): the substitution at {@code Mob.setTarget}.
 *
 * <p>Every case drives {@code setTarget} directly rather than waiting on mob AI,
 * for the reason {@code UnconsciousAggroGameTest}'s javadoc gives and one more:
 * vanilla's acquisition is RNG-gated (a one-in-five roll on even ticks) and a
 * mob spawned with no free will has no goals at all. The mod's <em>outcome</em>
 * is deterministic; vanilla's <em>timing</em> is not, and only the first of
 * those is ours to assert.
 *
 * <p><b>Every test here spawns, mutates, acts, asserts, restores and disconnects
 * inside one synchronous method body.</b> No {@code runAfterDelay}, no
 * {@code onEachTick}, no {@code setupTicks}, no sequences, and the whole body —
 * not just the act — inside {@code try}/{@code finally}. That is not style. This
 * is the class that made the rule necessary: the resolver scans the
 * <em>world-global</em> player list, a practice dummy is a real entry in it, and
 * a test zombie's follow range is exactly 35 blocks, which reaches several
 * neighbouring 8x8x8 structures. Batches run roughly fifty tests at a time in
 * one shared world, so a dummy that outlives its own body is a dummy some other
 * test's zombie can see — and the failure lands over there, in
 * {@code UnconsciousAggroGameTest}, pointing at a veto regression that is not
 * there. A body that finishes inside its own tick is a body no other test can
 * ever observe; that is the entire safety argument, and the
 * {@code realisticdamage:isolated} environment is the other half of it.
 *
 * <p>{@code UnconsciousAggroGameTest} is deliberately <em>not</em> in that
 * environment and deliberately unmodified: its six cases are the proof that
 * 1.1's veto survived this rewrite, and they only mean that while they keep
 * running in the world they were proven green in.
 */
public class InjuryAggroGameTest {
	/** Structure-relative, so the mob lands inside the test bounds and gets cleaned up. */
	private static final BlockPos MOB_AT = new BlockPos(1, 2, 1);

	/**
	 * Far enough that {@code NEARBY} refuses a body there, and near enough that the
	 * body is still inside this structure's own 8x8x8 box. The obvious choice —
	 * the radius plus three, which is what the 1.1 tests use — puts the dummy
	 * outside the structure, where the surrounding world is not the flat empty
	 * template and the eye-to-eye ray does not survive the trip. That fails the
	 * ALL case for a reason that has nothing to do with the policy under test.
	 */
	private static final double OUTSIDE_NEARBY = Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS + 0.4;

	/**
	 * Every case starts here. A dummy that outlived somebody else's body is
	 * visible to this structure's zombie from 35 blocks, so this fails fast and
	 * names the real cause instead of letting a targeting assertion fail for a
	 * reason nobody would look for.
	 */
	private void assertNoLeakedDummies(GameTestHelper helper) {
		helper.assertTrue(TestPlayer.all(helper.getLevel().getServer()).isEmpty(),
				"a previous test leaked a dummy; every case must disconnect in a finally");
	}

	/**
	 * No free will rather than no AI: {@code removeFreeWill} strips every goal
	 * while {@code serverAiStep} keeps running, which is what keeps
	 * {@code Sensing.tick()} clearing the line-of-sight cache. A mob under
	 * {@code setNoAi(true)} would keep its first answer about a pair forever.
	 */
	private Zombie zombie(GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, MOB_AT);
		zombie.setTarget(null);
		return zombie;
	}

	/** On the mob's own x/z line, dz further along +z, so the eye-to-eye ray runs straight down z. */
	private Vec3 at(GameTestHelper helper, double dz) {
		return helper.absoluteVec(new Vec3(1.5, 2.0, 1.5 + dz));
	}

	private TestPlayer dummyAt(GameTestHelper helper, double dz, String name) {
		TestPlayer dummy = TestPlayer.spawn(helper.getLevel(), at(helper, dz), 0.0F, name);
		RDAttachments.get(dummy).reset();
		return dummy;
	}

	private void remove(TestPlayer dummy) {
		dummy.connection.disconnect(Component.literal("test over"));
	}

	/**
	 * The <em>requested</em> target. A mock player is not in {@code level.players()},
	 * which makes it useless as a candidate and perfect as the thing vanilla asked
	 * for. It must be SURVIVAL, or {@code asValidTarget} nulls it and every
	 * "stayed on the healthy one" assertion passes for the wrong reason.
	 */
	private ServerPlayer mockAt(GameTestHelper helper, double dz) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = at(helper, dz);
		player.setPos(pos.x, pos.y, pos.z);
		RDAttachments.get(player).reset();
		return player;
	}

	private void breakLeg(ServerPlayer player) {                                              // 200
		RDAttachments.get(player).part(BodyPart.LEFT_LEG).setBroken(true);
	}

	private void hurtLeg(ServerPlayer player) {                                               // 11/16, Hurt, 0
		RDAttachments.get(player).part(BodyPart.LEFT_LEG).setCondition(11.0);
	}

	private void injureLeg(ServerPlayer player) {                                             // 7/16, Injured, 0
		RDAttachments.get(player).part(BodyPart.LEFT_LEG).setCondition(7.0);
	}

	private void critLeg(ServerPlayer player) {                                               // 3/16, Critical, 60
		RDAttachments.get(player).part(BodyPart.LEFT_LEG).setCondition(3.0);
	}

	private void scratch(ServerPlayer player) {                                               // MINOR, 0
		RDAttachments.get(player).addWound(BodyPart.RIGHT_ARM, WoundSeverity.MINOR);
	}

	private void bleed(ServerPlayer player) {                                                 // MODERATE, 10
		RDAttachments.get(player).addWound(BodyPart.RIGHT_ARM, WoundSeverity.MODERATE);
	}

	/** Exactly {@code UnconsciousAggroGameTest.knockOut}, so "down" means the same thing here. */
	private void knockOut(GameTestHelper helper, ServerPlayer player) {
		RealisticDamageData data = RDAttachments.get(player);
		data.drainConsciousness(Balance.CONSCIOUSNESS_MAX);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "the player must actually be down");
	}

	/**
	 * Both fields, always, and both restored — {@code ServerConfig.get()} is
	 * process-wide across every test in the run, and the default
	 * {@code unconsciousAggro} is NEARBY, which three cases here depend on.
	 */
	private void withPrey(ServerConfig.PreyOnWounded prey, ServerConfig.UnconsciousAggro aggro, Runnable body) {
		ServerConfig config = ServerConfig.get();
		ServerConfig.PreyOnWounded previousPrey = config.preyOnWounded;
		ServerConfig.UnconsciousAggro previousAggro = config.unconsciousAggro;
		config.preyOnWounded = prey;
		config.unconsciousAggro = aggro;
		try {
			body.run();
		} finally {
			config.preyOnWounded = previousPrey;
			config.unconsciousAggro = previousAggro;
		}
	}

	// --- Acquisition, the off switch, and clearing ---

	/** The whole feature in one assertion: the nearer healthy player is not the one it takes. */
	@GameTest(environment = "realisticdamage:isolated")
	public void prefersTheBrokenLeggedPlayerOnAcquisition(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper1");
			try {
				breakLeg(wounded);
				ServerPlayer healthy = mockAt(helper, 2.0);
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == wounded,
						"a healthy acquisition must resolve to the visibly hurt player in range, "
								+ "even though the healthy one is nearer");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void offLeavesVanillaAlone(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.OFF, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper2");
			try {
				breakLeg(wounded);
				ServerPlayer healthy = mockAt(helper, 2.0);
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == healthy,
						"OFF must be 1.5 behaviour exactly: the nearest player wins");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	/** {@code TargetGoal.stop()} and {@code NeutralMob.stopBeingAngry} both depend on this. */
	@GameTest(environment = "realisticdamage:isolated")
	public void clearingAlwaysClears(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper3");
			try {
				breakLeg(wounded);
				ServerPlayer healthy = mockAt(helper, 2.0);
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == wounded, "engaged with the limper first");

				zombie.setTarget(null);
				helper.assertTrue(zombie.getTarget() == null && zombie.getTargetUnchecked() == null,
						"clearing must always clear, whatever is limping nearby");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	// --- What counts as visibly wounded ---

	@GameTest(environment = "realisticdamage:isolated")
	public void aScratchIsNotPreyButSteadyBleedingIs(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Bleeder1");
			try {
				scratch(wounded);
				ServerPlayer healthy = mockAt(helper, 2.0);
				Zombie first = zombie(helper);
				first.setTarget(healthy);
				helper.assertTrue(first.getTarget() == healthy,
						"a scratch seeps below the rate that even makes a drop; it is not a signal");

				bleed(wounded);
				Zombie second = zombie(helper);
				second.setTarget(healthy);
				helper.assertTrue(second.getTarget() == wounded,
						"a wound that is actually running is one");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	/**
	 * The rung cut, in the game. Also proves a rung change takes an
	 * <em>engaged</em> mob: by the third stage the slot already holds the healthy
	 * player, which is what makes the last assertion a switch rather than an
	 * acquisition.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aHurtOrInjuredLegIsNotALimpButACriticalOneIs(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper4");
			try {
				ServerPlayer healthy = mockAt(helper, 2.0);
				Zombie zombie = zombie(helper);

				hurtLeg(wounded);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == healthy,
						"a Hurt leg costs under 5% of your speed; nobody can see that");

				injureLeg(wounded);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == healthy,
						"and an Injured one under 13%, whose bottom end is indistinguishable from Hurt");

				critLeg(wounded);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == wounded,
						"a Critical leg is where the limp starts to show, and where it starts to count");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	/** "Visibly" is enforced by vanilla's own line-of-sight test, and this is the proof. */
	@GameTest(environment = "realisticdamage:isolated")
	public void outOfSightIsNotVisiblyWounded(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			// The wall goes up before any setTarget: Sensing caches the answer for a
			// pair once per tick, so a wall built afterwards would not be consulted.
			for (int x = 0; x <= 2; x++) {
				for (int y = 2; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
				}
			}
			TestPlayer wounded = dummyAt(helper, 4.0, "Hidden1");
			try {
				breakLeg(wounded);
				ServerPlayer healthy = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
				Vec3 beside = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
				healthy.setPos(beside.x, beside.y, beside.z);
				RDAttachments.get(healthy).reset();

				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == healthy,
						"a limper the mob cannot see is not visibly wounded");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	// --- Hysteresis and tiers ---

	@GameTest(environment = "realisticdamage:isolated")
	public void hysteresisKeepsAnEngagementAgainstBloodAlone(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer held = dummyAt(helper, 2.0, "Held1");
			TestPlayer slightlyWorse = dummyAt(helper, 4.0, "Worse1");
			try {
				breakLeg(held);                                     // 200, against a sound bystander
				Zombie zombie = zombie(helper);
				zombie.setTarget(held);
				helper.assertTrue(zombie.getTarget() == held, "engaged with the broken leg first");

				// Now the bystander is hurt slightly worse than the player being
				// fought: 210 against 200, one wound step, under the margin. A
				// fresh acquisition would take it — case acquisitionHasNoMargin
				// asserts exactly that — and an engagement must not.
				breakLeg(slightlyWorse);
				bleed(slightlyWorse);
				zombie.setTarget(held);                             // the canContinueToUse shape
				helper.assertTrue(zombie.getTarget() == held,
						"a blood difference below the switch margin must not flip an engaged mob");
			} finally {
				remove(held);
				remove(slightlyWorse);
			}
		});
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aTierChangeTakesAnEngagedMob(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer held = dummyAt(helper, 2.0, "Held2");
			TestPlayer worse = dummyAt(helper, 4.0, "Worse2");
			try {
				breakLeg(held);                                     // 200
				Zombie zombie = zombie(helper);
				zombie.setTarget(held);
				helper.assertTrue(zombie.getTarget() == held, "engaged with the nearer break");

				breakLeg(worse);
				bleed(worse);                                       // 210 against 225: not enough
				zombie.setTarget(held);
				helper.assertTrue(zombie.getTarget() == held, "and blood alone does not move it");

				RDAttachments.get(worse).part(BodyPart.RIGHT_LEG).setDestroyed(true);   // 310 > 225
				zombie.setTarget(held);
				helper.assertTrue(zombie.getTarget() == worse,
						"a whole tier must take an engaged mob, or the margin swallows the ladder");
			} finally {
				remove(held);
				remove(worse);
			}
		});
		helper.succeed();
	}

	/**
	 * A fresh acquisition is loyal to nobody: the slot is empty, so there is
	 * nothing to be loyal to. Note the second dummy has to be a fresh one —
	 * {@code setDestroyed(false)} does not put the condition back.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void acquisitionHasNoMargin(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer requested = dummyAt(helper, 2.0, "Asked1");
			TestPlayer barelyWorse = dummyAt(helper, 4.0, "Barely1");
			try {
				breakLeg(requested);                                // 200
				breakLeg(barelyWorse);
				bleed(barelyWorse);                                 // 210: one wound step, under the margin
				Zombie zombie = zombie(helper);
				zombie.setTarget(requested);
				helper.assertTrue(zombie.getTarget() == barelyWorse,
						"with an empty slot any strictly better candidate wins");
			} finally {
				remove(requested);
				remove(barelyWorse);
			}
		});
		helper.succeed();
	}

	// --- Composition with the 1.1 unconscious policy, mode by mode ---

	@GameTest(environment = "realisticdamage:isolated")
	public void aDistantBodyIsNotChosenUnderNearby(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			// Knocked out before the zombie exists: a freshly spawned dummy runs
			// keepMobsInterested once on its own tick 0, and doing it first is the
			// safe habit even here, where under NEARBY that sweep finds nothing.
			TestPlayer down = dummyAt(helper, OUTSIDE_NEARBY, "Down1");
			TestPlayer wounded = dummyAt(helper, 3.0, "Limper5");
			try {
				knockOut(helper, down);                             // 600, and out of NEARBY reach
				breakLeg(wounded);                                  // 200
				ServerPlayer healthy = mockAt(helper, 2.0);
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == wounded,
						"a body beyond the NEARBY radius must be skipped by the composed policy "
								+ "even though it out-scores everyone");
			} finally {
				remove(down);
				remove(wounded);
			}
		});
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void underAllTheBodyWinsByTier(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.ALL, () -> {
			TestPlayer down = dummyAt(helper, OUTSIDE_NEARBY, "Down2");
			TestPlayer wounded = dummyAt(helper, 3.0, "Limper6");
			try {
				knockOut(helper, down);
				breakLeg(wounded);
				ServerPlayer healthy = mockAt(helper, 2.0);
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == down,
						"under ALL the same body is allowed, and a body on the floor tops the ladder");
			} finally {
				remove(down);
				remove(wounded);
			}
		});
		helper.succeed();
	}

	/**
	 * The 1.1 meaning of ATTACKERS and NONE, pinned so that changing it has to be
	 * deliberate: they say hostiles lose interest in <em>you</em>, not that they
	 * pick somebody else. The refused path does not even scan.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aVetoedRequestIsNotReplacedByALesserCandidate(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.ATTACKERS, () -> {
			TestPlayer down = dummyAt(helper, 2.0, "Down3");
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper7");
			try {
				knockOut(helper, down);
				breakLeg(wounded);
				Zombie zombie = zombie(helper);
				zombie.setTarget(down);
				helper.assertTrue(zombie.getTarget() == null,
						"ATTACKERS means lose interest in you, not pick someone else; the slot stays empty");
			} finally {
				remove(down);
				remove(wounded);
			}
		});
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void noneRefusesTheBodyAndSubstitutesNobody(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NONE, () -> {
			TestPlayer down = dummyAt(helper, 2.0, "Down4");
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper8");
			try {
				knockOut(helper, down);
				breakLeg(wounded);
				Zombie zombie = zombie(helper);
				zombie.setTarget(down);
				helper.assertTrue(zombie.getTarget() == null,
						"NONE must refuse the body outright, limper present or not");
			} finally {
				remove(down);
				remove(wounded);
			}
		});
		helper.succeed();
	}

	/**
	 * I1 in the world: nothing a limper can be is worth leaving a body for.
	 *
	 * <p>The body here is <em>knocked down</em> rather than unconscious, and that
	 * is what makes this a substitution test instead of a coincidence. An
	 * unconscious dummy pulls mobs onto itself through
	 * {@code ConsciousnessSystem.keepMobsInterested}, which fires on a freshly
	 * spawned dummy's own tick 0 and writes the body into the slot before
	 * anything here asks for it — after which cancelling and substituting are
	 * indistinguishable, and a veto-only resolver passes every assertion below.
	 * A knocked-down player scores the same {@code PREY_TIER_DOWN}, triggers no
	 * sweep, and is not subject to {@code unconsciousAggro} at all, so the only
	 * thing that can move the slot off the limper is the write. It is also the
	 * only case in this class that reads {@code knockedDown()}; the unconscious
	 * half of "down" is covered four times over by the composition cases above.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBodyOnTheFloorCannotBeWalkedAwayFrom(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper9");
			TestPlayer down = dummyAt(helper, 2.0, "Down5");
			try {
				breakLeg(wounded);
				Zombie zombie = zombie(helper);
				zombie.setTarget(wounded);
				helper.assertTrue(zombie.getTarget() == wounded, "engaged with the limper first");

				// The re-assert comes before anything writes the body into the
				// slot, which is the whole point: the mob is engaged with the
				// limper and the only thing that can take it away is a write.
				RDAttachments.get(down).knockDown(20);
				zombie.setTarget(wounded);      // a goal re-asserting the limper it is fighting
				helper.assertTrue(zombie.getTarget() == down,
						"a body on the floor must take the slot off a limper the mob is already "
								+ "engaged with, which only a write can do");

				zombie.setTarget(down);
				helper.assertTrue(zombie.getTarget() == down, "and the body keeps the slot");

				zombie.setTarget(wounded);      // and again, now that it is held
				helper.assertTrue(zombie.getTarget() == down,
						"a body on the floor cannot be walked away from by re-asserting a limper");
			} finally {
				remove(wounded);
				remove(down);
			}
		});
		helper.succeed();
	}

	// --- The revenge decision ---

	/**
	 * The explicit answer to "who does the mob keep?": hit it and it stays on you
	 * against any limp and any blood, and loses you to a broken leg. One number
	 * in both places, because the grudge replaces the switch margin rather than
	 * stacking on it — which is what makes the first and second calls here agree.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void revengeHoldsAgainstALimpButNotABrokenLeg(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper10");
			try {
				critLeg(wounded);
				bleed(wounded);                                     // 60 + 10 = 70, under the grudge
				ServerPlayer attacker = mockAt(helper, 2.0);
				Zombie zombie = zombie(helper);
				zombie.setLastHurtByMob(attacker);

				zombie.setTarget(attacker);
				helper.assertTrue(zombie.getTarget() == attacker,
						"hit something and it comes for you, limper or no limper");
				zombie.setTarget(attacker);                         // the engaged re-assert
				helper.assertTrue(zombie.getTarget() == attacker,
						"and the grudge reads the same on the re-assert as on the swing");

				breakLeg(wounded);                                  // 200 + 10 = 210 > 175
				zombie.setTarget(attacker);
				helper.assertTrue(zombie.getTarget() == wounded,
						"but a broken leg outranks a grudge, so a pack cannot be taunted off one");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	/** The one you hit turns on you; the rest of the pack goes for your limping friend. */
	@GameTest(environment = "realisticdamage:isolated")
	public void thePackPeelsOffToTheLimperButTheOneYouHitStaysOnYou(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper11");
			try {
				critLeg(wounded);                                   // 60, over nothing but under the grudge
				ServerPlayer attacker = mockAt(helper, 2.0);
				Zombie struck = zombie(helper);
				struck.setLastHurtByMob(attacker);
				Zombie packMate = zombie(helper);

				struck.setTarget(attacker);
				packMate.setTarget(attacker);                       // the alertOthers shape
				helper.assertTrue(struck.getTarget() == attacker,
						"the one you hit remembers you");
				helper.assertTrue(packMate.getTarget() == wounded,
						"and the grudge is its own, not the pack's");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	// --- Which mobs hunt this way ---

	/**
	 * The {@link Enemy} gate, isolated — and it has to be isolated deliberately,
	 * because {@code Wolf implements NeutralMob}. Left un-angered, a wolf is
	 * refused by the per-candidate anger guard and never reaches the marker check
	 * at all, so the obvious version of this test passes with the {@code Enemy}
	 * gate deleted and proves nothing about it. Making the wolf angry at the
	 * limper satisfies the anger guard, and then the gate is the only thing left
	 * standing between a wolf and a bystander.
	 *
	 * <p>That is the trap the class javadoc warns about, from the other side: the
	 * two guards are separate fixes for separate bugs, and a mob that is both
	 * hides one behind the other. This case and
	 * {@code aNeutralPiglinOnlyPrefersSomeoneItIsAngryAt} together make deleting
	 * either line fail.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aTamedWolfIsNotRedirectedOntoABystander(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper12");
			try {
				breakLeg(wounded);
				ServerPlayer healthy = mockAt(helper, 2.0);
				Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, MOB_AT);
				wolf.setTarget(null);
				// Angry at the limper on purpose: this is what takes the NeutralMob
				// guard out of the picture, so a pass here can only mean the Enemy
				// gate held. An angry piglin in the same position IS redirected.
				wolf.setPersistentAngerTarget(EntityReference.of((LivingEntity) wounded));
				wolf.setTarget(healthy);
				helper.assertTrue(wolf.getTarget() == healthy,
						"only hostiles hunt this way; a wolf avenging its owner goes for the one who hit "
								+ "them, however badly somebody else is limping");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	/**
	 * A neutral mob is both {@code Enemy} and {@code NeutralMob}, so the marker
	 * gate does not cover this and the per-candidate anger guard is a second,
	 * separate fix. Its own selector lives in the goal's targeting conditions,
	 * where this call cannot see it.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aNeutralPiglinOnlyPrefersSomeoneItIsAngryAt(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper13");
			try {
				breakLeg(wounded);
				ServerPlayer healthy = mockAt(helper, 2.0);

				ZombifiedPiglin calm = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIFIED_PIGLIN, MOB_AT);
				calm.setTarget(null);
				calm.setTarget(healthy);
				helper.assertTrue(calm.getTarget() == healthy,
						"provoking one piglin must not set it on a player who never touched it");

				ZombifiedPiglin angry = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIFIED_PIGLIN, MOB_AT);
				angry.setTarget(null);
				angry.setPersistentAngerTarget(EntityReference.of((LivingEntity) wounded));
				angry.setTarget(healthy);
				helper.assertTrue(angry.getTarget() == wounded,
						"but it may still prefer the more wounded of the players it is angry at");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}

	// --- Modes below VISIBLE ---

	@GameTest(environment = "realisticdamage:isolated")
	public void limpingModeIgnoresBleeding(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		TestPlayer wounded = dummyAt(helper, 4.0, "Bleeder2");
		try {
			bleed(wounded);
			ServerPlayer healthy = mockAt(helper, 2.0);
			withPrey(ServerConfig.PreyOnWounded.LIMPING, ServerConfig.UnconsciousAggro.NEARBY, () -> {
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == healthy,
						"LIMPING must not read blood");
			});
			withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == wounded,
						"and VISIBLE must, on the very same body");
			});
		} finally {
			remove(wounded);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void downedModeIgnoresLegsAndBlood(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		TestPlayer wounded = dummyAt(helper, 4.0, "Limper14");
		try {
			breakLeg(wounded);
			bleed(wounded);
			ServerPlayer healthy = mockAt(helper, 2.0);
			withPrey(ServerConfig.PreyOnWounded.DOWNED, ServerConfig.UnconsciousAggro.NEARBY, () -> {
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == healthy,
						"DOWNED prefers a body on the floor and nothing else");
			});
			withPrey(ServerConfig.PreyOnWounded.DOWNED, ServerConfig.UnconsciousAggro.NEARBY, () -> {
				knockOut(helper, wounded);
				Zombie zombie = zombie(helper);
				zombie.setTarget(healthy);
				helper.assertTrue(zombie.getTarget() == wounded,
						"and it does prefer that");
			});
		} finally {
			remove(wounded);
		}
		helper.succeed();
	}

	/**
	 * A non-player request is vanilla's business. This is also the exit that makes
	 * clearing always clear, so it is worth an assertion of its own rather than
	 * being covered incidentally.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aMobVersusMobTargetIsNeverSubstituted(GameTestHelper helper) {
		assertNoLeakedDummies(helper);
		withPrey(ServerConfig.PreyOnWounded.VISIBLE, ServerConfig.UnconsciousAggro.NEARBY, () -> {
			TestPlayer wounded = dummyAt(helper, 4.0, "Limper15");
			try {
				breakLeg(wounded);
				Mob other = helper.spawnWithNoFreeWill(EntityTypes.WOLF, MOB_AT);
				Zombie zombie = zombie(helper);
				zombie.setTarget(other);
				// The raw slot, because that is the claim: whatever vanilla wrote is
				// still what is in there. getTarget() would re-run asValidTarget and
				// answer a different question.
				helper.assertTrue(zombie.getTargetUnchecked() == other,
						"mob-versus-mob stays vanilla's; 1.7 gives mobs bodies and can revisit");
			} finally {
				remove(wounded);
			}
		});
		helper.succeed();
	}
}
