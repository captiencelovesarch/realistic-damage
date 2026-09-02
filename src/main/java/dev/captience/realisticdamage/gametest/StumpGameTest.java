package dev.captience.realisticdamage.gametest;

import java.util.function.Predicate;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.systems.HealingSystem;
import dev.captience.realisticdamage.systems.RescueSystem;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * The stump (1.6): what losing a limb costs now that it costs something.
 *
 * <p>Until this landed, the mod's most permanent injury was also its quietest —
 * destruction can only fire on a part already at zero condition, so the wound
 * rule, which keys on how much condition a hit removed, returned nothing for it.
 * A severed leg cost one line of text and no Blood at all. The wound is opened
 * inside {@code DestructionLogic.destroy} for the same reason the scar is raised
 * inside {@code setDestroyed}: every way of losing a limb goes through there, so
 * none of them can forget it.
 *
 * <p>Half of what these tests pin is that it is <em>survivable</em>. A destroyed
 * part never heals, so a stump that could not be pressed or closed would be a
 * death sentence with no counterplay — which is why pressure and food get a test
 * each, and why the limb staying gone while the bleeding stops is asserted in
 * the same breath.
 *
 * <p><b>Every test here spawns, mutates, acts, asserts, restores and disconnects
 * inside one synchronous method body.</b> No {@code runAfterDelay}, no
 * {@code onEachTick}, no {@code setupTicks}, no sequences, and any body that
 * touches {@code ServerConfig} sits inside {@code try}/{@code finally}. That is
 * not style: gametests are batched roughly fifty at a time into one shared
 * world, {@code ServerConfig} is a process-wide singleton and the player list is
 * world-global, so the only thing that keeps one test out of another's way is
 * that its body runs to completion within a single server tick. The moment a
 * body spans two ticks, another test's mutations interleave with it and the
 * suite goes nondeterministically red with nobody awake to read it. The
 * {@code realisticdamage:isolated} environment is the other half: it puts these
 * in their own batch, so the 1.5 suite keeps running in the world it was proven
 * green in.
 */
public class StumpGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	/**
	 * A sword blow aimed low, the way {@code LongTermInjuryGameTest} aims its
	 * mace: the pitch goes on the ATTACKER, because that is what aims the ray.
	 *
	 * <p>SLASH rather than blunt on purpose. It is ×1.0 trauma, so the applied
	 * damage below <em>is</em> the figure the leg is judged by, and it is one of
	 * the categories that can also bleed — which is what makes the ordering test
	 * below able to fail.
	 */
	private DamageSource legSlash(GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		attacker.setXRot((float) Math.toDegrees(Math.atan2(1.62 - 0.3, 1.7)));
		return helper.getLevel().damageSources().playerAttack(attacker);
	}

	/**
	 * Comfortably over {@code DESTROY_TRAUMA_LEG} (6.0), which is the only thing
	 * this figure has to be. A sword cannot concuss at all — SLASH's profile is
	 * not concussion-capable — and since 1.6 it would not matter if it could:
	 * losing a limb outranks being concussed, so the concussion line yields the
	 * frame to the destruction line (see
	 * {@code ConsciousnessSystem.maybeConcuss}). The concussion-clearing blow is
	 * tested where it can actually happen, in
	 * {@code ExplosiveDismembermentGameTest}.
	 */
	private static final double LIMB_TAKING_BLOW = 8.0;

	/** Which leg took the blow; exactly one must have, or the aim drifted onto the torso. */
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

	/**
	 * Both legs, so the test does not care which one the ray picked. 1.5 is
	 * deliberately under SLASH's minor bleed threshold of 2.0: the only wound
	 * that can exist afterwards is the stump.
	 */
	private RealisticDamageData legsAt(GameTestHelper helper, ServerPlayer player, double condition) {
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.LEFT_LEG).setCondition(condition);
		data.part(BodyPart.RIGHT_LEG).setCondition(condition);
		return data;
	}

	/**
	 * A leg still on the body when the blow lands is taken off by it: critical.
	 *
	 * <p>This is the headline. One test proves the whole finding — the limb is
	 * gone, and for the first time it costs Blood.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aLimbTakenInOneBlowOpensACriticalStump(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = legsAt(helper, player, 1.5);

		Notifier.clearLastMessageKey();
		TraumaPipeline.handle(player, data, legSlash(helper),
				LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, false);
		BodyPart leg = hitLeg(helper, data, BodyPartData::destroyed);

		helper.assertTrue(data.wounds().size() == 1,
				"the stump is the one wound this blow opens, got " + data.wounds().size());
		helper.assertTrue(data.wounds().getFirst().part() == leg, "and it is on the limb that is gone");
		helper.assertTrue(data.worstWound() == WoundSeverity.CRITICAL,
				"a limb still standing is taken off, not ground down: got " + data.worstWound());
		helper.assertTrue(data.effectiveBleedRate() == Balance.BLEED_RATE_CRITICAL,
				"and it bleeds at the critical rate, got " + data.effectiveBleedRate());
		helper.assertTrue(BodySnapshot.of(data).wound(leg) != null,
				"the client must be told, or the missing limb emits nothing and the HUD draws no ring");
		helper.assertTrue(("realisticdamage.msg.stump." + leg.id()).equals(Notifier.lastMessageKey()),
				"the destruction line owns this frame and must say the stump bleeds, got "
						+ Notifier.lastMessageKey());
		helper.succeed();
	}

	/** A leg the fight already emptied is only detached: severe. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aLimbGroundDownOverAFightOpensASevereStump(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		// Already at zero, so this blow takes no condition at all: every point of
		// it is clamped away, which is exactly what "the limb was dead weight
		// before this hit arrived" looks like in the numbers.
		RealisticDamageData data = legsAt(helper, player, 0.0);

		Notifier.clearLastMessageKey();
		TraumaPipeline.handle(player, data, legSlash(helper),
				LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, false);
		BodyPart leg = hitLeg(helper, data, BodyPartData::destroyed);

		helper.assertTrue(data.wounds().size() == 1,
				"still exactly one wound, got " + data.wounds().size());
		helper.assertTrue(data.worstWound() == WoundSeverity.SEVERE,
				"a limb ground to zero over a fight bleeds less than one taken off, got " + data.worstWound());
		helper.assertTrue(data.effectiveBleedRate() == Balance.BLEED_RATE_SEVERE,
				"got " + data.effectiveBleedRate());
		helper.assertTrue(("realisticdamage.msg.stump." + leg.id()).equals(Notifier.lastMessageKey()),
				"and it is still the destruction line the player reads, got " + Notifier.lastMessageKey());
		helper.succeed();
	}

	/**
	 * The ordering claim, which is the one thing about this that can break
	 * silently. {@code Notifier.message} is {@code sendOverlayMessage}, so the
	 * last line written on a frame is the only line the player reads — and
	 * {@code createWounds} runs after destruction and announces an escalation.
	 *
	 * <p>The blow here really does cut as well as take: 6.0 of condition lost is
	 * over SLASH's moderate threshold of 4.0, so an ordinary wound is opened on
	 * the same frame as the stump. Losing a limb is never the smaller piece of
	 * news, so the destruction sentence must survive it.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void noBleedingLineOverwritesTheDestructionLine(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = legsAt(helper, player, 6.0);

		Notifier.clearLastMessageKey();
		TraumaPipeline.handle(player, data, legSlash(helper),
				12.0, 12.0, 12.0, false);
		BodyPart leg = hitLeg(helper, data, BodyPartData::destroyed);

		helper.assertTrue(data.wounds().size() > 1,
				"the blow must also have cut, or this test proves nothing: " + data.wounds().size());
		helper.assertTrue(("realisticdamage.msg.stump." + leg.id()).equals(Notifier.lastMessageKey()),
				"the destruction sentence must be the last thing written on the frame, got "
						+ Notifier.lastMessageKey());
		helper.succeed();
	}

	/**
	 * Losing a core region kills outright, and a wound on a corpse is noise. The
	 * lethal blow is refused on a mock player — it is invulnerable until a client
	 * has loaded — which is what makes the negative assertable at all.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	@SuppressWarnings("removal")
	public void aCrushedCoreLeavesNoStump(GameTestHelper helper) {
		for (BodyPart core : new BodyPart[] {BodyPart.HEAD, BodyPart.TORSO}) {
			// The lethal blow goes through hurtServer, which reads the connection:
			// a bare mock player has none. This is the same player shape
			// BleedingGameTest uses for bleed-out, and for the same reason.
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			player.connection.handleAcceptPlayerLoad(
					new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();

			Notifier.clearLastMessageKey();
			DestructionLogic.destroy(player, data, core);

			helper.assertTrue(data.part(core).destroyed(), core.id() + " is gone");
			helper.assertTrue(player.isDeadOrDying(),
					"losing the " + core.id() + " is not survivable — that is why it opens nothing");
			helper.assertTrue(data.wounds().isEmpty(),
					"a wound on a corpse is noise, got " + data.wounds().size());
			helper.assertTrue(("realisticdamage.msg.destroyed." + core.id()).equals(Notifier.lastMessageKey()),
					"and the core wording must survive verbatim, got " + Notifier.lastMessageKey());
		}
		helper.succeed();
	}

	/**
	 * The rescue system answers the stump. Pressure is body-wide and never asks
	 * whether the region it is holding still exists — so a hand on the stump
	 * works exactly as it does on any other wound, and that is the counterplay
	 * that keeps a permanent injury from being a death sentence.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void pressureSlowsAStump(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		DestructionLogic.destroy(player, data, BodyPart.LEFT_LEG);
		helper.assertTrue(data.bleeding(), "the stump is what there is to press");

		// Measured first, and before anything presses: refreshPressure only lapses
		// through RescueSystem.tick, so a hand laid on early would still be there
		// for the control run and both figures would come out the same.
		data.setBlood(Balance.BLOOD_MAX);
		for (int i = 0; i < 20; i++) {
			BleedingSystem.tick(player, data);
		}
		double lostFreely = Balance.BLOOD_MAX - data.blood();

		RescueSystem.applyPressure(player, null);
		helper.assertTrue(RescueSystem.rooted(data),
				"a destroyed region is still a region you can hold a hand over");

		data.setBlood(Balance.BLOOD_MAX);
		for (int i = 0; i < 20; i++) {
			data.refreshPressure(true);
			BleedingSystem.tick(player, data);
		}
		double lostUnderPressure = Balance.BLOOD_MAX - data.blood();

		helper.assertTrue(lostFreely > 0.0, "an unpressed stump must cost Blood: " + lostFreely);
		helper.assertTrue(lostUnderPressure < lostFreely,
				"pressure must slow it (" + lostUnderPressure + " vs " + lostFreely + ")");
		helper.assertTrue(lostUnderPressure > 0.0,
				"but never stop it — pressure buys time, it is not a cure");
		helper.succeed();
	}

	/**
	 * The test a future reviewer will look for. Food and time close the wound
	 * even though the limb never comes back: the wound is a separate object from
	 * the part for exactly this reason, so "food and time are the only cure"
	 * stays true of the bleeding without ever becoming a cure for the limb.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aStumpClosesWithFoodAndTimeButTheLimbNeverComesBack(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		DestructionLogic.destroy(player, data, BodyPart.LEFT_LEG);
		helper.assertTrue(data.worstWound() == WoundSeverity.CRITICAL, "a limb taken by fiat is taken off");

		// CRITICAL is four severity steps at WOUND_DECAY_PER_REGEN 0.34 a point,
		// so ~11.8 regenerated HP closes it. Forty is comfortably past that and
		// still nowhere near enough to bring anything back.
		for (int i = 0; i < 40; i++) {
			HealingSystem.onVanillaHeal(player, 1.0F);
		}

		helper.assertTrue(data.wounds().isEmpty(), "the stump closes, got " + data.wounds().size());
		helper.assertTrue(!data.bleeding(), "and the Blood drain stops with it");
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).destroyed(), "the leg is still gone");
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).condition() == 0.0,
				"and still at nothing, got " + data.part(BodyPart.LEFT_LEG).condition());
		helper.assertTrue(HealingSystem.hasAnythingToHeal(data) || data.blood() >= Balance.BLOOD_MAX,
				"nothing may leave the body stuck with a wound and no way to work on it");
		helper.succeed();
	}

	/**
	 * With bleeding off there is no stump, and — just as important — no sentence
	 * claiming one. A line that describes a wound the server never opened is
	 * worse than no line at all.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void theStumpRespectsTheBleedingToggle(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableBleeding;
		try {
			ServerConfig.get().enableBleeding = false;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = legsAt(helper, player, 1.5);

			Notifier.clearLastMessageKey();
			TraumaPipeline.handle(player, data, legSlash(helper),
					LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, false);
			BodyPart leg = hitLeg(helper, data, BodyPartData::destroyed);

			helper.assertTrue(data.wounds().isEmpty(),
					"bleeding is off, so nothing bleeds, got " + data.wounds().size());
			helper.assertTrue(("realisticdamage.msg.destroyed." + leg.id()).equals(Notifier.lastMessageKey()),
					"and the 1.5 wording must survive verbatim, got " + Notifier.lastMessageKey());
		} finally {
			ServerConfig.get().enableBleeding = previous;
			ServerConfig.save();
		}
		helper.succeed();
	}

	/** No RNG in outcomes: the same killing blow twice is the same injury. */
	@GameTest(environment = "realisticdamage:isolated")
	public void theSameKillingBlowTwiceIsTheSameInjury(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = legsAt(helper, player, 1.5);
		TraumaPipeline.handle(player, data, legSlash(helper),
				LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, false);
		int firstWounds = BodySnapshot.of(data).woundMask();
		int firstDestroyed = BodySnapshot.of(data).destroyedMask();
		WoundSeverity firstSeverity = data.worstWound();

		legsAt(helper, player, 1.5);
		TraumaPipeline.handle(player, data, legSlash(helper),
				LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, LIMB_TAKING_BLOW, false);

		helper.assertTrue(BodySnapshot.of(data).woundMask() == firstWounds,
				"the same blow must open the same wound in the same place");
		helper.assertTrue(BodySnapshot.of(data).destroyedMask() == firstDestroyed,
				"and take the same limb");
		helper.assertTrue(data.worstWound() == firstSeverity,
				"at the same severity, got " + data.worstWound() + " after " + firstSeverity);
		helper.succeed();
	}
}
