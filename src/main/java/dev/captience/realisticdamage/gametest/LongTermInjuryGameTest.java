package dev.captience.realisticdamage.gametest;

import java.util.function.Predicate;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.HealingSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Long-term injuries (1.6, hardcore): the ceiling comes down, food and time take
 * you to it and stop, and it follows you through death.
 *
 * <p><b>Every test here spawns, mutates, acts, asserts, restores and disconnects
 * inside one synchronous method body.</b> No {@code runAfterDelay}, no
 * {@code onEachTick}, no {@code setupTicks}, no sequences, and the whole body
 * sits inside {@code try}/{@code finally}. That is not style: gametests are
 * batched roughly fifty at a time into one shared world, {@code ServerConfig} is
 * a process-wide singleton and the player list is world-global, so the only
 * thing that keeps one test out of another's way is that its body runs to
 * completion within a single server tick. The moment a body spans two ticks,
 * another test's mutations are interleaved with it and the suite goes
 * nondeterministically red at three in the morning. The
 * {@code realisticdamage:isolated} environment on each test is the other half:
 * it puts these in their own batch, so the 1.5 suite keeps running in the world
 * it was proven green in.
 */
public class LongTermInjuryGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	/**
	 * The applied damage used by every blow here that has to break or remove a leg.
	 *
	 * <p>Chosen deliberately, and it is worth writing down why. HEAVY_BLUNT is
	 * ×1.4 trauma, so 4.8 lands 6.72 on the leg — over {@code BREAK_IMPACT_LEG}
	 * 6.5 and over {@code DESTROY_TRAUMA_LEG} 6.0. But concussion is scored off
	 * the whole blow rather than off where it landed (×1.2 consciousness factor,
	 * threshold 6.0), so anything from 5.0 upward also concusses — and the
	 * concussion line is written <em>after</em> the ceiling line by the pipeline,
	 * which would leave these tests asserting against a sentence about the head.
	 * 4.8 × 1.2 = 5.76 clears the break and stays under the concussion, so the
	 * ceiling line really is the last thing the player reads.
	 */
	private static final double LIMB_BREAKING_BLOW = 4.8;

	/** The iron-golem-class blow of {@code ImpairmentGameTest}, aimed low so it lands on a leg. */
	private DamageSource legMace(GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setXRot((float) Math.toDegrees(Math.atan2(1.62 - 0.3, 1.7)));
		return helper.getLevel().damageSources().mace(attacker);
	}

	/** The same blow at eye height, which {@code ConsciousnessGameTest} uses to reach the head. */
	private DamageSource headMace(GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setXRot(0.0F);
		return helper.getLevel().damageSources().mace(attacker);
	}

	/**
	 * Which leg took the blow. Asserting that exactly one did is the cheap guard
	 * against an aim that drifted onto the torso, where every threshold differs.
	 */
	private BodyPart hitLeg(GameTestHelper helper, RealisticDamageData data, Predicate<BodyPartData> hit) {
		BodyPart found = null;
		for (BodyPart leg : new BodyPart[] {BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
			if (hit.test(data.part(leg))) {
				helper.assertTrue(found == null, "the blow must land on exactly one leg");
				found = leg;
			}
		}
		helper.assertTrue(found != null, "the blow must land on a leg at all");
		return found;
	}

	private BodyPart otherLeg(BodyPart leg) {
		return leg == BodyPart.LEFT_LEG ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
	}

	private void assertNear(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < 1.0e-9,
				what + ": expected " + expected + ", got " + actual);
	}

	private void heal(ServerPlayer player, int times) {
		for (int i = 0; i < times; i++) {
			HealingSystem.onVanillaHeal(player, 1.0F);
		}
	}

	// --- The three events, and the one-rung rule ---

	/** A part driven to exactly zero costs a rung, and reads as having given out. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aLegZeroedByTraumaLosesOneRung(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			data.part(BodyPart.LEFT_LEG).setCondition(0.5);
			data.part(BodyPart.RIGHT_LEG).setCondition(0.5);

			Notifier.clearLastMessageKey();
			TraumaPipeline.handle(player, data, legMace(helper), 1, 1, 1, false);
			BodyPart leg = hitLeg(helper, data, part -> part.state() == BodyState.DISABLED);
			BodyPartData hit = data.part(leg);

			helper.assertTrue(!hit.destroyed(), "1.4 attempted is far under the destruction threshold");
			helper.assertTrue(hit.ceilingBand() == 1, "zeroing a leg must cost exactly one rung");
			assertNear(helper, 12.0, hit.ceiling(), "a band-1 leg tops out at three quarters of max");
			helper.assertTrue(data.part(otherLeg(leg)).ceilingBand() == 0, "the untouched leg keeps its full range");
			helper.assertTrue(("realisticdamage.msg.ceiling.gave." + leg.id()).equals(Notifier.lastMessageKey()),
					"a part that gave out must read as having given out, got " + Notifier.lastMessageKey());

			heal(player, 40);
			assertNear(helper, 12.0, hit.condition(), "food and time take the leg to its ceiling");
			helper.assertTrue(hit.state() == BodyState.HURT, "and no further, got " + hit.state());
			assertNear(helper, 0.0, hit.missing(), "a leg at its ceiling is missing nothing");
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	/** A break costs a rung, and the mending line has to say it only mended that far. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBrokenLegLosesOneRungAndMendsAsFarAsItCan(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();

			Notifier.clearLastMessageKey();
			TraumaPipeline.handle(player, data, legMace(helper),
					LIMB_BREAKING_BLOW, LIMB_BREAKING_BLOW, LIMB_BREAKING_BLOW, false);
			BodyPart leg = hitLeg(helper, data, BodyPartData::broken);
			BodyPartData hit = data.part(leg);

			helper.assertTrue(!hit.destroyed(), "a healthy leg is broken by this blow, not removed");
			helper.assertTrue(hit.ceilingBand() == 1, "a break must cost exactly one rung");
			helper.assertTrue(("realisticdamage.msg.ceiling.broken." + leg.id()).equals(Notifier.lastMessageKey()),
					"the break line must carry the ceiling with it, got " + Notifier.lastMessageKey());

			Notifier.clearLastMessageKey();
			heal(player, 40);
			helper.assertTrue(!hit.broken(), "reaching the ceiling mends the break");
			assertNear(helper, 12.0, hit.condition(), "and stops there");
			helper.assertTrue(hit.state() == BodyState.HURT, "a mended-to-ceiling leg still reads Hurt");
			helper.assertTrue(("realisticdamage.msg.mended_ceiling." + leg.id()).equals(Notifier.lastMessageKey()),
					"mended at a ceiling is not mended whole, got " + Notifier.lastMessageKey());
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	/** Break and zeroing in one blow is still one rung: the cost is per event, not per symptom. */
	@GameTest(environment = "realisticdamage:isolated")
	public void oneBlowCostsAtMostOneRung(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			data.part(BodyPart.LEFT_LEG).setCondition(1.2);
			data.part(BodyPart.RIGHT_LEG).setCondition(1.2);

			TraumaPipeline.handle(player, data, legMace(helper), 1, 1, 1, false);
			BodyPart leg = hitLeg(helper, data, BodyPartData::broken);
			BodyPartData hit = data.part(leg);

			helper.assertTrue(hit.state() == BodyState.DISABLED, "the blow both broke and emptied the leg");
			helper.assertTrue(hit.ceilingBand() == 1,
					"two symptoms in one event are still one rung, got band " + hit.ceilingBand());
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	/** Losing the limb outright records the rung for the next body, and says nothing extra. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aLostLimbIsMarkedForTheNextBody(GameTestHelper helper) {
		boolean previousMode = ServerConfig.get().enableLongTermInjuries;
		boolean previousDestruction = ServerConfig.get().enableLimbDestruction;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerConfig.get().enableLimbDestruction = true;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			// Already at zero, so this blow deals no condition damage at all and the
			// only event it can be is the destruction.
			data.part(BodyPart.LEFT_LEG).setCondition(0.0);
			data.part(BodyPart.RIGHT_LEG).setCondition(0.0);

			Notifier.clearLastMessageKey();
			TraumaPipeline.handle(player, data, legMace(helper),
					LIMB_BREAKING_BLOW, LIMB_BREAKING_BLOW, LIMB_BREAKING_BLOW, false);
			BodyPart leg = hitLeg(helper, data, BodyPartData::destroyed);
			BodyPartData hit = data.part(leg);

			helper.assertTrue(hit.ceilingBand() == 1, "the rung under a lost limb is what the next body inherits");
			assertNear(helper, 0.0, hit.effectiveMax(), "a destroyed part still recovers to nothing");
			assertNear(helper, 0.0, hit.missing(), "and is excluded from every healing budget");
			helper.assertTrue(data.part(otherLeg(leg)).ceilingBand() == 0, "the untouched leg keeps its full range");
			helper.assertTrue(BodySnapshot.of(data).ceilingBand(leg) == 0,
					"a crossed-out region sends no band; the band underneath is not the client's business");
			// The destruction line still owns the moment; since 1.6 it is the stump
			// wording, because this leg was already at zero and the blow that took
			// it opened a wound. A ceiling line here would overwrite it.
			helper.assertTrue(("realisticdamage.msg.stump." + leg.id()).equals(Notifier.lastMessageKey()),
					"the destruction line owns that moment, got " + Notifier.lastMessageKey());
		} finally {
			ServerConfig.get().enableLongTermInjuries = previousMode;
			ServerConfig.get().enableLimbDestruction = previousDestruction;
		}
		helper.succeed();
	}

	/** With the toggle off this is 1.5 exactly, down to which sentence the player reads. */
	@GameTest(environment = "realisticdamage:isolated")
	public void withTheModeOffNothingIsEverCapped(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = false;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();

			TraumaPipeline.handle(player, data, legMace(helper),
					LIMB_BREAKING_BLOW, LIMB_BREAKING_BLOW, LIMB_BREAKING_BLOW, false);
			BodyPart leg = hitLeg(helper, data, BodyPartData::broken);
			BodyPartData hit = data.part(leg);
			for (BodyPart part : BodyPart.values()) {
				helper.assertTrue(data.part(part).ceilingBand() == 0,
						"the toggle is off; nothing anywhere may be capped (" + part.id() + ")");
			}

			Notifier.clearLastMessageKey();
			heal(player, 40);
			helper.assertTrue(!hit.broken(), "the break mends");
			assertNear(helper, Balance.LEG_MAX, hit.condition(), "all the way back to new");
			helper.assertTrue(hit.state() == BodyState.HEALTHY, "and reads Healthy again");
			helper.assertTrue(("realisticdamage.msg.mended." + leg.id()).equals(Notifier.lastMessageKey()),
					"with no ceiling the 1.5 wording must survive verbatim, got " + Notifier.lastMessageKey());
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	// --- The head: one rung, and never under the symptom ladder ---

	/** The head's floor is Hurt, and a second blow at the floor cannot push it lower. */
	@GameTest(environment = "realisticdamage:isolated")
	public void theHeadStopsAtHurt(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			BodyPartData head = data.part(BodyPart.HEAD);
			head.lowerCeilingTo(3);
			helper.assertTrue(head.ceilingBand() == 1, "asking for Critical on a head must land at Hurt");
			head.setCondition(0.5);

			// 1.4 attempted, far under DESTROY_TRAUMA_CORE, so the mock is not killed.
			TraumaPipeline.handle(player, data, headMace(helper), 1, 1, 1, false);
			helper.assertTrue(!head.destroyed(), "this blow must not be lethal");
			helper.assertTrue(head.ceilingBand() == 1,
					"the head is already at its floor; there is no second rung, got " + head.ceilingBand());

			// A head at its Hurt ceiling still clears the symptom ladder, which is the
			// whole reason the core floor is one rung and not three.
			head.heal(Balance.HEAD_MAX);
			assertNear(helper, 15.0, head.condition(), "food takes the head to its ceiling");
			helper.assertTrue(head.state() == BodyState.HURT, "and it reads Hurt, got " + head.state());
			data.setConsciousness(Balance.CONSCIOUSNESS_MAX);
			assertNear(helper, Balance.HEAD_CAP_HURT, data.consciousness(),
					"a capped head caps consciousness at the Hurt figure and no lower");
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	/** A concussion clears when the head is as good as it will get — not only when it reads Healthy. */
	@GameTest(environment = "realisticdamage:isolated")
	public void concussionClearsOnceTheHeadReachesItsCeiling(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		BodyPartData head = data.part(BodyPart.HEAD);
		head.lowerCeilingTo(1);
		head.setCondition(15.0);
		data.setConcussion(true);
		data.setConsciousness(Balance.CONSCIOUSNESS_MAX);
		data.setConcussionStableTicks(Balance.CONCUSSION_STABLE_TICKS - 1);

		HealingSystem.tick(player, data);
		helper.assertTrue(!data.concussion(),
				"a head sitting at its ceiling is as mended as it can be, so the clock must finish");

		// Control: one point short of the ceiling is still recovering, and the clock
		// restarts rather than finishing.
		ServerPlayer controlPlayer = mockPlayer(helper);
		RealisticDamageData control = RDAttachments.get(controlPlayer);
		control.reset();
		BodyPartData controlHead = control.part(BodyPart.HEAD);
		controlHead.lowerCeilingTo(1);
		controlHead.setCondition(11.0);
		control.setConcussion(true);
		control.setConsciousness(Balance.CONSCIOUSNESS_MAX);
		control.setConcussionStableTicks(Balance.CONCUSSION_STABLE_TICKS - 1);

		HealingSystem.tick(controlPlayer, control);
		helper.assertTrue(control.concussion(), "a head still short of its ceiling stays concussed");
		helper.assertTrue(control.concussionStableTicks() == 0, "and the stability clock goes back to zero");
		helper.succeed();
	}

	// --- What "healed" means once there is a ceiling ---

	/** A fully capped body stops paying exhaustion for a repair that can never land. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aCappedBodyHasNothingLeftToHeal(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.LEFT_LEG).lowerCeilingTo(2);
		heal(player, 40);

		helper.assertTrue(data.blood() >= Balance.BLOOD_MAX, "the body is otherwise whole");
		helper.assertTrue(!HealingSystem.hasAnythingToHeal(data),
				"a body at every ceiling has nothing left to repair");
		data.part(BodyPart.LEFT_LEG).damage(0.5);
		helper.assertTrue(HealingSystem.hasAnythingToHeal(data),
				"...until something drops below one, and then it has");
		helper.succeed();
	}

	/** Sleep and the notch apple are the strongest medicine there is, and they stop at the ceiling too. */
	@GameTest(environment = "realisticdamage:isolated")
	public void sleepAndTheNotchAppleStopAtTheCeiling(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		BodyPartData leg = data.part(BodyPart.LEFT_LEG);
		leg.lowerCeilingTo(2);          // ceiling 8.0
		leg.setCondition(2.0);

		HealingSystem.applySleepRecovery(player, data);
		assertNear(helper, 5.0, leg.condition(), "sleep repairs half of what is missing, measured to the ceiling");

		ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
		HealingSystem.onEat(player, apple, apple.get(net.minecraft.core.component.DataComponents.FOOD));
		assertNear(helper, 7.4, leg.condition(), "the notch apple repairs four fifths of what is missing");

		HealingSystem.onEat(player, apple, apple.get(net.minecraft.core.component.DataComponents.FOOD));
		assertNear(helper, 7.88, leg.condition(), "and a second one gets closer without ever arriving");
		helper.assertTrue(leg.condition() < 8.0, "nothing in the game raises a ceiling");
		helper.assertTrue(leg.ceilingBand() == 2, "and nothing in the game clears one either");
		helper.succeed();
	}

	// --- Death ---

	/** The headline mechanic: the marks are what a death does not take. */
	@GameTest(environment = "realisticdamage:isolated")
	public void ceilingsFollowYouThroughDeathWhenTheModeIsOn(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerPlayer old = mockPlayer(helper);
			ServerPlayer fresh = mockPlayer(helper);
			RealisticDamageData before = RDAttachments.get(old);
			before.reset();
			before.part(BodyPart.LEFT_LEG).lowerCeilingTo(2);
			before.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
			before.setBlood(40.0);
			before.setConcussion(true);
			before.part(BodyPart.RIGHT_ARM).setDestroyed(true);

			Notifier.clearLastMessageKey();
			fresh.restoreFrom(old, false);
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old, fresh, false);

			RealisticDamageData after = fresh.getAttached(RDAttachments.DATA);
			helper.assertTrue(after != null, "the new body must exist");
			helper.assertTrue(after != before, "and must not be the old one carried over wholesale");
			helper.assertTrue(after.part(BodyPart.LEFT_LEG).ceilingBand() == 2, "the band came with you");
			assertNear(helper, 8.0, after.part(BodyPart.LEFT_LEG).condition(),
					"and the new leg starts at that ceiling: full, for the body it now is");
			helper.assertTrue(after.part(BodyPart.LEFT_LEG).state() == BodyState.INJURED,
					"which reads Injured, because that is what the band means");
			helper.assertTrue(!after.part(BodyPart.RIGHT_ARM).destroyed(), "you get the arm back");
			assertNear(helper, Balance.BLOOD_MAX, after.blood(), "blood is forgotten");
			helper.assertTrue(after.wounds().isEmpty(), "wounds are forgotten");
			helper.assertTrue(!after.concussion(), "the concussion is forgotten");
			helper.assertTrue("realisticdamage.msg.ceiling.carried".equals(Notifier.lastMessageKey()),
					"the reborn body sits at its ceilings, so this line is the only thing that tells you, got "
							+ Notifier.lastMessageKey());
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	/** Only the carry is gated: the listener still hands out a fresh body either way. */
	@GameTest(environment = "realisticdamage:isolated")
	public void ceilingsDieWithYouWhenTheModeIsOff(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = false;
			ServerPlayer old = mockPlayer(helper);
			ServerPlayer fresh = mockPlayer(helper);
			RealisticDamageData before = RDAttachments.get(old);
			before.reset();
			// Set directly, as if the mode had been on earlier in this world's life.
			before.part(BodyPart.LEFT_LEG).lowerCeilingTo(2);
			before.setBlood(40.0);

			Notifier.clearLastMessageKey();
			fresh.restoreFrom(old, false);
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old, fresh, false);

			RealisticDamageData after = fresh.getAttached(RDAttachments.DATA);
			helper.assertTrue(after != null, "the listener always attaches a body; only the carry is gated");
			for (BodyPart part : BodyPart.values()) {
				helper.assertTrue(after.part(part).ceilingBand() == 0, "no band survives (" + part.id() + ")");
				assertNear(helper, part.maxCondition(), after.part(part).condition(), part.id() + " is new");
			}
			assertNear(helper, Balance.BLOOD_MAX, after.blood(), "and so is everything else");
			helper.assertTrue(!"realisticdamage.msg.ceiling.carried".equals(Notifier.lastMessageKey()),
					"nothing was carried, so nothing may claim it was");
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	/** An End return is not a death: Fabric's own transfer keeps the whole body, instance and all. */
	@GameTest(environment = "realisticdamage:isolated")
	public void endReturnKeepsTheWholeBody(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerPlayer old = mockPlayer(helper);
			ServerPlayer fresh = mockPlayer(helper);
			RealisticDamageData before = RDAttachments.get(old);
			before.reset();
			before.part(BodyPart.LEFT_LEG).setCondition(4.0);
			before.setBlood(40.0);

			fresh.restoreFrom(old, true);
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old, fresh, true);

			helper.assertTrue(fresh.getAttached(RDAttachments.DATA) == before,
					"the alive guard must leave Fabric's wholesale transfer completely alone");
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}

	// --- The one new permanent penalty a ceiling could have invented, and did not ---

	/**
	 * An arm pinned at Critical would otherwise re-arm a five-second repair pause
	 * on every swing and every mined block, for the rest of that character's life,
	 * and {@code HealingMath.distributeBudget} would skip it every single time.
	 * Nothing else in the suite can see this.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aCappedArmIsNotPermanentlyStrained(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));

			ServerPlayer capped = mockPlayer(helper);
			RealisticDamageData cappedData = RDAttachments.get(capped);
			cappedData.reset();
			BodyPartData cappedArm = cappedData.part(ImpairmentSystem.armFor(capped, true));
			cappedArm.lowerCeilingTo(3);        // 3.25 of 13, which reads CRITICAL exactly
			helper.assertTrue(cappedArm.atCeiling(), "the arm is as good as it will ever be");
			helper.assertTrue(cappedArm.state() == BodyState.CRITICAL,
					"and still reads Critical, which is the trap, got " + cappedArm.state());

			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(
					helper.getLevel(), capped, pos, Blocks.STONE.defaultBlockState(), null);
			helper.assertTrue(cappedArm.strainPauseTicks() == 0,
					"a limb at its ceiling has no recovering left to pause, got " + cappedArm.strainPauseTicks());

			// Control: one point below the ceiling, still Critical, and it strains.
			ServerPlayer below = mockPlayer(helper);
			RealisticDamageData belowData = RDAttachments.get(below);
			belowData.reset();
			BodyPartData belowArm = belowData.part(ImpairmentSystem.armFor(below, true));
			belowArm.lowerCeilingTo(3);
			belowArm.damage(1.0);
			helper.assertTrue(belowArm.state() == BodyState.CRITICAL, "still Critical");
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(
					helper.getLevel(), below, pos, Blocks.STONE.defaultBlockState(), null);
			helper.assertTrue(belowArm.strainPauseTicks() == Balance.ARM_STRAIN_PAUSE_TICKS,
					"an arm with repairing left to do is still paused by strenuous use");

			// Second control: no ceiling at all, which is 1.5 exactly.
			ServerPlayer uncapped = mockPlayer(helper);
			RealisticDamageData uncappedData = RDAttachments.get(uncapped);
			uncappedData.reset();
			BodyPartData uncappedArm = uncappedData.part(ImpairmentSystem.armFor(uncapped, true));
			uncappedArm.setCondition(2.0);
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(
					helper.getLevel(), uncapped, pos, Blocks.STONE.defaultBlockState(), null);
			helper.assertTrue(uncappedArm.strainPauseTicks() == Balance.ARM_STRAIN_PAUSE_TICKS,
					"the 1.5 behaviour must be untouched");
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
		}
		helper.succeed();
	}
}
