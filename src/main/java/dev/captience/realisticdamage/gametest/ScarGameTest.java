package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.HealingSystem;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;

/**
 * Scarring (1.6): the mark a healed body keeps, and everything it must not do.
 *
 * <p>A scar is written by the real pipeline, survives the mend that erases the
 * injury itself, crosses the wire, and follows you through death — and it
 * changes no outcome anywhere, which is why half the assertions here are about
 * what is still <em>zero</em> on a scarred body.
 *
 * <p><b>Every test here spawns, mutates, acts, asserts, restores and disconnects
 * inside one synchronous method body.</b> No {@code runAfterDelay}, no
 * {@code onEachTick}, no {@code setupTicks}, no sequences. That is not style:
 * gametests are batched roughly fifty at a time into one shared world, the
 * player list is world-global and {@code ServerConfig} is a process-wide
 * singleton, so the only thing keeping one test out of another's way is that its
 * body runs to completion within a single server tick. The moment a body spans
 * two ticks, another test's mutations interleave with it and the suite goes
 * nondeterministically red with nobody awake to read it. The
 * {@code realisticdamage:isolated} environment is the other half: it puts these
 * in their own batch, so the 1.5 suite keeps running in the world it was proven
 * green in. This class needs the environment least of all — its mock players
 * never join {@code level.players()} and it touches no config — and carries it
 * anyway, because "every new 1.6 class is isolated" is a rule an implementer can
 * follow without judgment and "the ones that need it" is not.
 */
public class ScarGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	/**
	 * The whole feature in one test: a real blow through the real pipeline breaks
	 * a leg, food and time mend it completely, and the mark is still there.
	 *
	 * <p>Nothing here writes the scar deliberately. It has to fall out of
	 * {@code setBroken}, which is the reason the mark lives inside the setter
	 * rather than at the call sites — {@code BreakLogic} never mentions it.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBreakThroughThePipelineLeavesAScarTheMendDoesNotErase(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		// The iron-golem-class blow of ImpairmentGameTest, aimed low. The pitch goes
		// on the ATTACKER, not the victim: that is what aims the ray at a leg.
		ServerPlayer golemStandIn = mockPlayer(helper);
		golemStandIn.setPos(0.0, 0.0, -2.0);
		DamageSource mace = helper.getLevel().damageSources().mace(golemStandIn);
		golemStandIn.setXRot((float) Math.toDegrees(Math.atan2(1.62 - 0.3, 1.7)));
		TraumaPipeline.handle(player, data, mace, 8, 8, 8, false);

		BodyPart leg = data.part(BodyPart.LEFT_LEG).broken() ? BodyPart.LEFT_LEG
				: data.part(BodyPart.RIGHT_LEG).broken() ? BodyPart.RIGHT_LEG : null;
		helper.assertTrue(leg != null, "the blow must have broken a leg for this test to mean anything");
		helper.assertTrue(data.part(leg).scar() == ScarLevel.BROKEN,
				"a break must record itself, got " + data.part(leg).scar());

		// Sustained regeneration mends it with no bandages, exactly as HealingGameTest
		// proves — and clearing Broken is precisely when the history would be lost.
		for (int i = 0; i < 40; i++) {
			HealingSystem.onVanillaHeal(player, 1.0F);
		}
		BodyPartData mended = data.part(leg);
		helper.assertTrue(!mended.broken(), "the leg must actually mend, condition = " + mended.condition());
		helper.assertTrue(mended.state() == BodyState.HEALTHY,
				"and mend all the way, got " + mended.state());
		helper.assertTrue(mended.scar() == ScarLevel.BROKEN, "the mend must not take the memory with it");

		BodySnapshot snapshot = BodySnapshot.of(data);
		helper.assertTrue(snapshot.scar(leg) == ScarLevel.BROKEN, "and the mark must reach the client");
		helper.assertTrue(!snapshot.broken(leg), "while the injury itself does not");
		helper.succeed();
	}

	/**
	 * Every way a limb is lost goes through {@code DestructionLogic.destroy} —
	 * {@code /rdmg destroy} today, explosive dismemberment next — so marking
	 * inside {@code setDestroyed} covers all of them with no code at the callers.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void destructionLeavesTheHeavierScar(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		DestructionLogic.destroy(player, data, BodyPart.LEFT_ARM);
		BodyPartData arm = data.part(BodyPart.LEFT_ARM);
		helper.assertTrue(arm.destroyed(), "the arm is gone");
		helper.assertTrue(arm.scar() == ScarLevel.DESTROYED,
				"losing it must record the heavier mark, got " + arm.scar());

		arm.markScar(ScarLevel.BROKEN);
		helper.assertTrue(arm.scar() == ScarLevel.DESTROYED,
				"and nothing lighter may overwrite it afterwards");
		helper.assertTrue(BodySnapshot.of(data).scar(BodyPart.LEFT_ARM) == ScarLevel.DESTROYED,
				"a destroyed region still sends its scar: the band is suppressed there, the mark is not");
		helper.succeed();
	}

	/** Every region, every level, over the real packing rather than the unit test's. */
	@GameTest(environment = "realisticdamage:isolated")
	public void everyScarSurvivesTheSnapshot(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		for (ScarLevel level : new ScarLevel[] {ScarLevel.BROKEN, ScarLevel.DESTROYED}) {
			for (BodyPart part : BodyPart.values()) {
				data.reset();
				data.part(part).setScar(level);
				BodySnapshot snapshot = BodySnapshot.of(data);
				helper.assertTrue(snapshot.scar(part) == level,
						"a " + level + " scar on the " + part.id() + " came back as " + snapshot.scar(part));
				for (BodyPart other : BodyPart.values()) {
					if (other != part) {
						helper.assertTrue(snapshot.scar(other) == ScarLevel.NONE,
								"marking the " + part.id() + " also marked the " + other.id());
					}
				}
			}
		}
		helper.succeed();
	}

	/**
	 * The single most important negative in the feature. Scarring is cosmetic, so
	 * a body carrying every mark it can carry must be indistinguishable from a
	 * healthy one in every input the HUD fade reads — including blood, which
	 * {@code BodyHud.hurt} also consults.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aScarredBodyIsNotAHurtBody(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		for (BodyPart part : BodyPart.values()) {
			data.part(part).markScar(ScarLevel.DESTROYED);
		}

		BodySnapshot snapshot = BodySnapshot.of(data);
		String why = "the HUD fade must not be able to see a scar — these are exactly the inputs it reads";
		helper.assertTrue(snapshot.brokenMask() == 0, why + " (broken)");
		helper.assertTrue(snapshot.destroyedMask() == 0, why + " (destroyed)");
		helper.assertTrue(snapshot.woundMask() == 0, why + " (wounds)");
		helper.assertTrue(snapshot.statusFlags() == 0, why + " (statuses)");
		helper.assertTrue(snapshot.ceilingMask() == 0, why + " (ceilings)");
		helper.assertTrue(snapshot.blood() == (float) Balance.BLOOD_MAX, why + " (blood)");
		for (BodyPart part : BodyPart.values()) {
			helper.assertTrue(snapshot.state(part) == BodyState.HEALTHY, why + " (" + part.id() + ")");
			helper.assertTrue(!snapshot.belowCeiling(part), why + " (" + part.id() + " ceiling)");
		}
		// And the marks really are all there — otherwise the negatives above would
		// pass because nothing was written in the first place.
		helper.assertTrue(snapshot.scarMask() == 0xAAA,
				"six lost regions, two bits each, got " + Integer.toHexString(snapshot.scarMask()));
		helper.succeed();
	}

	/** {@code /rdmg heal} and the practice dummy are full restores: the history goes too. */
	@GameTest(environment = "realisticdamage:isolated")
	public void debugHealForgetsTheScars(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		for (BodyPart part : BodyPart.values()) {
			data.part(part).markScar(ScarLevel.DESTROYED);
		}

		data.reset();

		for (BodyPart part : BodyPart.values()) {
			helper.assertTrue(data.part(part).scar() == ScarLevel.NONE,
					"reset() is the pristine-body primitive, so the " + part.id() + " forgets too");
		}
		helper.assertTrue(BodySnapshot.of(data).scarMask() == 0, "and the client is told so");
		helper.succeed();
	}

	/**
	 * Death, through the real respawn events rather than a direct call.
	 *
	 * <p>Deliberately its own method rather than assertions bolted onto
	 * {@code LongTermInjuryGameTest.ceilingsFollowYouThroughDeathWhenTheModeIsOn}:
	 * ceilings carry only under the hardcore toggle and scars carry always, and a
	 * single test asserting both would go on passing for the wrong reason if the
	 * toggle ever leaked into the scar line. This one never touches the toggle.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void scarsOutliveTheBody(GameTestHelper helper) {
		ServerPlayer old = mockPlayer(helper);
		ServerPlayer fresh = mockPlayer(helper);
		RealisticDamageData before = RDAttachments.get(old);
		before.reset();
		DestructionLogic.destroy(old, before, BodyPart.LEFT_ARM);
		before.part(BodyPart.RIGHT_LEG).setCondition(2.0);
		before.part(BodyPart.RIGHT_LEG).setBroken(true);
		before.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
		before.setBlood(40.0);

		// restoreFrom fires Fabric's COPY_FROM at its TAIL, which is our listener;
		// AFTER_RESPAWN then runs Fabric's own death transfer exactly as
		// PlayerList.respawn would. Never PlayerList.respawn itself on a mock.
		fresh.restoreFrom(old, false);
		ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old, fresh, false);

		RealisticDamageData after = fresh.getAttached(RDAttachments.DATA);
		helper.assertTrue(after != null, "the new body must exist");
		helper.assertTrue(after != before, "and must not be the old one carried over wholesale");
		helper.assertTrue(after.part(BodyPart.LEFT_ARM).scar() == ScarLevel.DESTROYED,
				"a limb lost for good is the one mark you could never otherwise see again");
		helper.assertTrue(!after.part(BodyPart.LEFT_ARM).destroyed(), "you get the arm back");
		helper.assertTrue(after.part(BodyPart.LEFT_ARM).condition() == BodyPart.LEFT_ARM.maxCondition(),
				"whole, and new");
		helper.assertTrue(after.part(BodyPart.RIGHT_LEG).scar() == ScarLevel.BROKEN, "the break came too");
		helper.assertTrue(!after.part(BodyPart.RIGHT_LEG).broken(), "but not the fracture");
		helper.assertTrue(after.blood() == Balance.BLOOD_MAX, "blood is forgotten");
		helper.assertTrue(after.wounds().isEmpty(), "wounds are forgotten");
		for (BodyPart part : BodyPart.values()) {
			// Unconditional means unconditional: no config was touched anywhere above.
			helper.assertTrue(after.part(part).ceilingBand() == 0,
					"and no ceiling rode in on the scar (" + part.id() + ")");
		}

		// The alive path — an End return, or a living body would be wiped by a portal.
		ServerPlayer back = mockPlayer(helper);
		back.restoreFrom(old, true);
		ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(old, back, true);
		helper.assertTrue(back.getAttached(RDAttachments.DATA) == before,
				"coming back alive keeps the very same body, marks and injuries alike");
		helper.succeed();
	}
}
