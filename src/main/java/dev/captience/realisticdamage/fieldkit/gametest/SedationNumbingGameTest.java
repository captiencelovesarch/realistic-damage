package dev.captience.realisticdamage.fieldkit.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.Knockdowns;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.BrewedPotions;
import dev.captience.realisticdamage.fieldkit.brew.MobDrag;
import dev.captience.realisticdamage.fieldkit.brew.Numbing;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import dev.captience.realisticdamage.gametest.TestPlayer;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/**
 * Sedation and Numbing (2.0, phase 3, round 2), on real bodies.
 *
 * <p>Husks rather than zombies where it matters: they don't burn in the
 * daylight a test may run in, and a body on fire is a body being hurt.
 *
 * <p>Most of these run in {@code realisticdamage:fieldkit_held_on}, whose setup
 * and teardown functions turn Weapons on before the batch and off after it, so
 * no test in it touches that switch. They used to save and restore it each,
 * and one that had saved "off" and finished early switched it off under the
 * others mid-test.
 */
public class SedationNumbingGameTest {
	/** One whole dose, the size of a tipped arrow's. */
	private static MobEffectInstance arrowDose() {
		return new MobEffectInstance(BrewedEffects.SEDATION, Balance.SEDATION_DOSE_TICKS);
	}

	private static void absorbAll(GameTestHelper helper, net.minecraft.world.entity.LivingEntity body) {
		for (int tick = 0; tick < Balance.SEDATION_ABSORB_TICKS * 6; tick++) {
			Sedation.tick(helper.getLevel(), body);
			Sedation.State state = Sedation.peek(body);
			if (state == null || state.pending <= 0.0) {
				return;
			}
		}
	}

	private static TestPlayer player(GameTestHelper helper, Vec3 at, String name) {
		return TestPlayer.spawn(helper.getLevel(), helper.absoluteVec(at), 0.0F, name);
	}

	private static void gone(ServerPlayer... players) {
		for (ServerPlayer player : players) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));
		}
	}

	private static boolean restoreWeapons(boolean weapons) {
		ServerConfig.get().enableWeapons = weapons;
		ServerConfig.save();
		return true;
	}

	/**
	 * One Arrow of Sedation, actually shot, puts a husk out: drowsy, then
	 * stumbling, then down, through the consciousness system's own collapse.
	 */
	@GameTest(environment = "realisticdamage:fieldkit_held_on", maxTicks = 300)
	public void oneArrowPutsAHuskOut(GameTestHelper helper) {
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 2, 5));
		husk.setNoAi(true);
		Vec3 aim = husk.position().add(0.0, 1.0, 0.0);
		Arrow arrow = new Arrow(helper.getLevel(), aim.x, aim.y, aim.z - 2.5,
				PotionContents.createItemStack(Items.TIPPED_ARROW, BrewedPotions.SEDATION), null);
		arrow.setDeltaMovement(0.0, 0.0, 1.2);
		helper.getLevel().addFreshEntity(arrow);
		helper.runAfterDelay(40, () -> {
			Stage early = Sedation.stage(husk);
			helper.runAfterDelay(200, () -> {
				boolean out = RDAttachments.get(husk).unconscious();
				Stage late = Sedation.stage(husk);
				husk.discard();
				helper.assertTrue(early == Stage.NONE || early == Stage.DROWSY,
						"two seconds in it is still taking hold, got " + early);
				helper.assertTrue(late == Stage.OUT, "once it has taken hold, out: got " + late);
				helper.assertTrue(out, "and the husk is down, unconscious");
				helper.succeed();
			});
		});
	}

	/** A ravager is five times the body: one dose barely registers, five put it out. */
	@GameTest(environment = "realisticdamage:fieldkit_held_on")
	public void aRavagerTakesSeveral(GameTestHelper helper) {
		Ravager ravager = helper.spawn(EntityTypes.RAVAGER, new BlockPos(2, 2, 2));
		ravager.setNoAi(true);
		ravager.addEffect(arrowDose());
		absorbAll(helper, ravager);
		Stage one = Sedation.stage(ravager);
		for (int i = 0; i < 4; i++) {
			ravager.addEffect(arrowDose());
		}
		absorbAll(helper, ravager);
		Stage five = Sedation.stage(ravager);
		ravager.discard();
		helper.assertTrue(one == Stage.NONE, "one arrow barely registers on a ravager, got " + one);
		helper.assertTrue(five == Stage.OUT, "five put it out, got " + five);
		helper.succeed();
	}

	/**
	 * Three doses stop the breathing: the body counts as starved of air, the
	 * hypoxia debt builds, and once it has all run up the overdose kills.
	 */
	@GameTest(environment = "realisticdamage:fieldkit_held_on", maxTicks = 120)
	public void anOverdoseStopsTheBreathingAndKills(GameTestHelper helper) {
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 2, 2));
		husk.setNoAi(true);
		for (int i = 0; i < 3; i++) {
			husk.addEffect(arrowDose());
		}
		absorbAll(helper, husk);
		boolean overdosed = Sedation.stage(husk) == Stage.OVERDOSE;
		boolean starved = ConsciousnessSystem.starved(husk);
		RealisticDamageData data = RDAttachments.get(husk);
		data.setHypoxia(Balance.HYPOXIA_DEBT_MAX);
		husk.setHealth(2.0F);
		helper.runAfterDelay(80, () -> {
			boolean dead = !husk.isAlive();
			helper.assertTrue(overdosed, "three doses in one body is an overdose");
			helper.assertTrue(starved, "an overdosed body is starved of air though it is not under water");
			helper.assertTrue(dead, "with the whole debt run up, the overdose kills");
			helper.succeed();
		});
	}

	/** Milk clears Sedation, and a body it put out stays out until it wakes the ordinary way. */
	@GameTest(environment = "realisticdamage:fieldkit_held_on", maxTicks = 40)
	public void milkClearsTheDrugNotTheBlackout(GameTestHelper helper) {
		TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-milk-sed");
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		player.addEffect(arrowDose());
		absorbAll(helper, player);
		helper.runAfterDelay(3, () -> {
			boolean wentOut = data.unconscious();
			new ItemStack(Items.MILK_BUCKET).finishUsingItem(helper.getLevel(), player);
			boolean cleared = !player.hasEffect(BrewedEffects.SEDATION) && Sedation.stage(player) == Stage.NONE;
			boolean stillOut = data.unconscious();
			gone(player);
			helper.assertTrue(wentOut, "a whole dose puts a player out");
			helper.assertTrue(cleared, "milk clears the drug");
			helper.assertTrue(stillOut, "but the blackout it caused stays until the body wakes");
			helper.succeed();
		});
	}

	/** Drinking it is the sleeping draught: waking from it is a night's sleep. */
	@GameTest(environment = "realisticdamage:fieldkit_held_on", maxTicks = 100)
	public void theSleepingDraughtHealsLikeABed(GameTestHelper helper) {
		TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-draught");
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setBlood(30.0);
		PotionContents.createItemStack(Items.POTION, BrewedPotions.SEDATION)
				.finishUsingItem(helper.getLevel(), player);
		absorbAll(helper, player);
		helper.runAfterDelay(3, () -> {
			Sedation.tick(helper.getLevel(), player);
			boolean out = data.unconscious();
			// Let the drug wear down to drowsy and the body come round.
			Sedation.State state = Sedation.peek(player);
			state.level = 0.3;
			data.setUnconsciousTicks(Balance.MIN_UNCONSCIOUS_TICKS);
			data.setConsciousness(Balance.WAKE_THRESHOLD + 5.0);
			helper.runAfterDelay(3, () -> {
				boolean awake = !data.unconscious();
				Sedation.tick(helper.getLevel(), player);
				double blood = data.blood();
				gone(player);
				helper.assertTrue(out, "the draught puts you out where you stand");
				helper.assertTrue(awake, "and you come round as the drug wears down");
				helper.assertTrue(blood >= 30.0 + Balance.SLEEP_BLOOD_RESTORE - 1.0,
						"waking from it heals like a night in a bed: blood " + blood);
				helper.succeed();
			});
		});
	}

	/** Bosses and the mindless can't be sedated; a golem shrugs it off. */
	@GameTest(environment = "realisticdamage:fieldkit_held_on")
	public void theMindlessAreImmune(GameTestHelper helper) {
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 2, 2));
		golem.setNoAi(true);
		boolean took = golem.addEffect(arrowDose());
		golem.discard();
		helper.assertFalse(took, "an iron golem must refuse Sedation");
		helper.succeed();
	}

	/** Stumbling on it, a blow too light to fell a sober body puts you down. */
	@GameTest(environment = "realisticdamage:fieldkit_held_on")
	public void stumblingBodiesGoDownToSmallThings(GameTestHelper helper) {
		TestPlayer attacker = player(helper, new Vec3(1.5, 2.0, -0.5), "fk-pusher");
		TestPlayer sober = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-sober");
		TestPlayer dazed = player(helper, new Vec3(3.5, 2.0, 1.5), "fk-dazed");
		try {
			RDAttachments.get(sober).reset();
			RDAttachments.get(dazed).reset();
			dazed.addEffect(arrowDose());
			Sedation.peek(dazed).pending = 0.0;
			Sedation.peek(dazed).level = 0.6;
			var blow = helper.getLevel().damageSources().playerAttack(attacker);
			Knockdowns.afterHit(sober, RDAttachments.get(sober), blow, 2.0F);
			Knockdowns.afterHit(dazed, RDAttachments.get(dazed), blow, 2.0F);
			helper.assertTrue(Sedation.stage(dazed) == Stage.STUMBLING, "set up stumbling, got " + Sedation.stage(dazed));
			helper.assertFalse(RDAttachments.get(sober).knockedDown(), "a light blow must not fell a sober body");
			helper.assertTrue(RDAttachments.get(dazed).knockedDown(), "the same blow fells a stumbling one");
		} finally {
			gone(attacker, sober, dazed);
		}
		helper.succeed();
	}

	/** An unconscious mob is hauled after you, and let go of when it wakes. */
	@GameTest(environment = "realisticdamage:fieldkit_held_on", maxTicks = 60)
	public void anUnconsciousMobCanBeDragged(GameTestHelper helper) {
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 2, 2));
		RealisticDamageData data = RDAttachments.get(husk);
		data.setConsciousness(0.0);
		data.setUnconscious(true);
		dev.captience.realisticdamage.systems.MobAnatomy.track(husk);
		TestPlayer dragger = player(helper, new Vec3(2.5, 2.0, 4.5), "fk-dragger");
		boolean took = MobDrag.toggle(dragger, husk);
		double before = flat(dragger, husk);
		helper.runAfterDelay(30, () -> {
			double after = flat(dragger, husk);
			boolean still = MobDrag.dragging(dragger, husk);
			data.setUnconscious(false);
			helper.runAfterDelay(2, () -> {
				boolean letGo = !MobDrag.dragging(dragger, husk);
				gone(dragger);
				husk.discard();
				helper.assertTrue(took, "sneak-use on an unconscious mob takes hold of it");
				helper.assertTrue(still && after < before - 0.5,
						"it is hauled after you: " + before + " -> " + after);
				helper.assertTrue(letGo, "a mob that wakes is let go of");
				helper.succeed();
			});
		});
	}

	/**
	 * Distance along the ground: nothing ticks a test player, so it hangs where
	 * it spawned while the husk drops to the floor under it.
	 */
	private static double flat(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}

	// --- Numbing ---

	/**
	 * Numb, a broken leg costs no speed, and sprinting on it wears it down;
	 * with Medicine off, Numbing is refused while Weapons brews still work.
	 */
	@GameTest(environment = "realisticdamage:fieldkit_held_on")
	public void numbingTakesThePainNotTheInjury(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean weapons = config.enableWeapons;
		boolean medicine = config.enableMedicine;
		TestPlayer player = player(helper, new Vec3(1.5, 2.0, 1.5), "fk-numb");
		try {
			config.enableMedicine = true;
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			BodyPartData leg = data.part(BodyPart.LEFT_LEG);
			leg.setCondition(4.0);
			leg.setBroken(true);
			ImpairmentSystem.tick(player, data);
			double limping = speedPenalty(player);
			player.addEffect(new MobEffectInstance(BrewedEffects.NUMBING, Balance.NUMBING_TICKS));
			ImpairmentSystem.tick(player, data);
			double numbed = speedPenalty(player);
			helper.assertTrue(limping < 0.0 && numbed == 0.0,
					"numb, the broken leg costs no speed: " + limping + " -> " + numbed);
			helper.assertTrue(leg.broken(), "and it is still broken");

			double before = leg.condition();
			player.setSprinting(true);
			for (int tick = 0; tick < 20; tick++) {
				Numbing.tick(player);
			}
			helper.assertTrue(leg.condition() < before - 0.9 * Balance.NUMB_SPRINT_LEG_COST,
					"a second's sprint on it wears it down: " + before + " -> " + leg.condition());

			player.removeAllEffects();
			config.enableMedicine = false;
			config.enableWeapons = true;
			helper.assertFalse(player.addEffect(new MobEffectInstance(BrewedEffects.NUMBING, 100)),
					"Medicine off: Numbing is refused");
			helper.assertTrue(player.addEffect(new MobEffectInstance(BrewedEffects.HAEMORRHAGE, 100)),
					"while Weapons, on, still works: the halves are separate");
		} finally {
			config.enableWeapons = weapons;
			config.enableMedicine = medicine;
			ServerConfig.save();
			gone(player);
		}
		helper.succeed();
	}

	/** The leg's share of the movement modifier, by the core's own id; 0 when there is none. */
	private static double speedPenalty(ServerPlayer player) {
		var modifier = player.getAttribute(Attributes.MOVEMENT_SPEED)
				.getModifier(dev.captience.realisticdamage.RealisticDamage.id("leg_speed"));
		return modifier == null ? 0.0 : modifier.amount();
	}

	/** With Weapons off, an Arrow of Sedation is just an arrow. */
	@GameTest(environment = "realisticdamage:isolated")
	public void switchedOffSedationDoesNothing(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean weapons = config.enableWeapons;
		try {
			config.enableWeapons = false;
			Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 2, 2));
			husk.setNoAi(true);
			boolean took = husk.addEffect(arrowDose());
			Sedation.State state = Sedation.peek(husk);
			husk.discard();
			helper.assertFalse(took, "Weapons off: the dose is refused");
			helper.assertTrue(state == null || state.clear(), "and nothing builds up in the body");
		} finally {
			restoreWeapons(weapons);
		}
		helper.succeed();
	}
}
