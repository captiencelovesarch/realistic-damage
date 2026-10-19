package dev.captience.realisticdamage.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.PendingExplosion;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.ExplosionMath;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A blast that takes a limb (1.6). Explosions were the one kind of violence the
 * body model read off the health bar: the better your armour the less a creeper
 * going off against your leg was allowed to mean, and a totem that dragged you
 * back from a stick of TNT handed the mod a body that read as merely bruised.
 * The rule now judges the blast vanilla itself computed — pre-difficulty and
 * pre-armour — through the same localized armour reduction every other category
 * gets, against a bar worth 45% of the limb.
 *
 * <p>45% because the real gate is survival: {@code AFTER_DAMAGE} never fires on
 * a killing blow, so the feature only exists in the stretch of distance where
 * you both live and are maimed. See {@code Balance.EXPLOSIVE_DESTROY_FRACTION}
 * for the window table; the distances quoted in this file are read off it.
 *
 * <p>Half of these tests exist to pin the <em>edges</em> rather than the headline:
 * a blast the rule is not worth, a raised shield, the toggle, the armour cap, the
 * share gate that keeps one end crystal from costing four limbs, and the core
 * exclusion — because a blast that could take a skull would fire almost only on
 * totem saves and kill the player one call after their totem popped.
 *
 * <p><b>Every test here spawns, mutates, acts, asserts, restores and disconnects
 * inside one synchronous method body.</b> No {@code runAfterDelay}, no
 * {@code onEachTick}, no {@code setupTicks}, no sequences, and any body that
 * touches {@code ServerConfig} or spawns a dummy sits inside {@code try}/
 * {@code finally}. That is not style: gametests are batched roughly fifty at a
 * time into one shared world, {@code ServerConfig} is a process-wide singleton
 * and the player list is world-global, so the only thing that keeps one test out
 * of another's way is that its body runs to completion within a single server
 * tick. In particular the real-detonation tests below need no delay at all —
 * {@code level.explode(...)} calls {@code hurtServer} synchronously inside the
 * call, so the flag a listener sets is already set when {@code explode} returns.
 * The {@code realisticdamage:isolated} environment is the other half: it puts
 * these in their own batch, so the 1.5 suite keeps running in the world it was
 * proven green in.
 */
public class ExplosiveDismembermentGameTest {
	/** Beside the shins, a block away: what a creeper on the same floor is. */
	private static final Vec3 LEG_BLAST = new Vec3(0.0, 0.2, 1.0);
	/** At head height, a block away: what something on the ledge above you is. */
	private static final Vec3 HEAD_BLAST = new Vec3(0.0, 1.7, 1.0);

	/**
	 * What a point-blank creeper costs a player who lives through it.
	 *
	 * <p>{@code intensity(3.0, 1.02, 1.0)} is 32.9, which is more than a whole
	 * health bar, and Fabric's {@code AFTER_DAMAGE} never fires on the blow that
	 * kills (see {@code DamageHooks}) — so the largest applied figure this
	 * pipeline can ever be handed by that creeper is a shade under 20, off
	 * somebody a totem, an absorption ring or a golden apple carried through it.
	 * 19 is that blow.
	 *
	 * <p>It replaces the 5.0 the message-asserting tests used to pass, and that
	 * number deserves naming: it was not a survivor's figure, it was exactly 1.0
	 * under {@code CONCUSSION_HEAD_TRAUMA_THRESHOLD}. The concussion line is
	 * written after the limb-loss line and used to eat it, so the only applied
	 * damage at which these assertions could pass was one that could not concuss
	 * — a value from the middle of the range, in a release whose lesson has
	 * repeatedly been to test the end of it. The suppression now lives in
	 * {@code ConsciousnessSystem.maybeConcuss}; the number is raised here so the
	 * tests exercise the blow the feature is actually about.
	 */
	private static final double SURVIVED_POINT_BLANK = 19.0;

	/**
	 * What a creeper 2.408 blocks away costs a full-diamond player on Normal:
	 * vanilla's intensity of 21.10, through {@code Player.hurtServer} (Normal is
	 * x1) and then {@code CombatRules.getDamageAfterAbsorb} with 20 armour and 8
	 * toughness — {@code realArmor = clamp(20 - 21.10/4, 4, 20) = 14.73}, so the
	 * blast keeps {@code 1 - 14.73/25 = 0.411} of itself, and 21.10 x 0.411 = 8.67.
	 *
	 * <p>Under ten, which is the entire reason the destroy fraction moved: this is
	 * the blow that takes the leg off a player at half health and leaves them
	 * standing. The arithmetic is re-derived from vanilla's own formulas in
	 * {@code ExplosionMathTest}; the figure is spelled out here so the seeded blow
	 * is a real survivor's figure rather than a number that made a test pass.
	 */
	private static final double PLAIN_DIAMOND_HP_AT_TWO_AND_A_HALF = 8.67;

	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	/**
	 * A blast without a detonation: the amount is vanilla's own curve at this
	 * distance, seeded exactly where the mixin would have put it. Distance is
	 * measured to the player's <em>position</em> — their feet — because that is
	 * what {@code Entity.distanceToSqr(Vec3)} measures and therefore what vanilla
	 * priced the blast on.
	 */
	private void blast(GameTestHelper helper, ServerPlayer player, RealisticDamageData data,
			double radius, Vec3 center, double appliedHp, boolean blocked) {
		blast(helper, player, data, radius, center, appliedHp, blocked,
				helper.getLevel().damageSources().badRespawnPointExplosion(center));
	}

	private void blast(GameTestHelper helper, ServerPlayer player, RealisticDamageData data,
			double radius, Vec3 center, double appliedHp, boolean blocked, DamageSource source) {
		double amount = ExplosionMath.intensity(radius, center.distanceTo(player.position()), 1.0);
		helper.assertTrue(amount > 0.0, "the seeded blast must actually reach the player");
		data.setPendingExplosion(new PendingExplosion(amount, center.x, center.y, center.z));
		TraumaPipeline.handle(player, data, source, appliedHp, appliedHp, appliedHp, blocked);
	}

	/** Which limb came off; exactly one must have, or the share gate has gone. */
	private BodyPart takenLimb(GameTestHelper helper, RealisticDamageData data) {
		BodyPart found = null;
		for (BodyPart part : BodyPart.values()) {
			if (!data.part(part).destroyed()) {
				continue;
			}
			helper.assertTrue(part.isLimb(), "a blast may never take the " + part.id());
			helper.assertTrue(found == null, "one blast, one limb — got " + found + " and " + part);
			found = part;
		}
		helper.assertTrue(found != null, "the blast must have taken a limb at all");
		return found;
	}

	private void assertNothingDestroyed(GameTestHelper helper, RealisticDamageData data, String why) {
		for (BodyPart part : BodyPart.values()) {
			helper.assertTrue(!data.part(part).destroyed(), why + " (" + part.id() + " is gone)");
		}
	}

	private List<BleedWound> woundsOn(RealisticDamageData data, BodyPart part) {
		return data.wounds().stream().filter(wound -> wound.part() == part).toList();
	}

	/** Both toggles at once, restored in {@code finally} — the singleton is shared. */
	private void withToggles(boolean destruction, boolean explosive, Runnable body) {
		ServerConfig config = ServerConfig.get();
		boolean previousDestruction = config.enableLimbDestruction;
		boolean previousExplosive = config.enableExplosiveDismemberment;
		try {
			config.enableLimbDestruction = destruction;
			config.enableExplosiveDismemberment = explosive;
			body.run();
		} finally {
			config.enableLimbDestruction = previousDestruction;
			config.enableExplosiveDismemberment = previousExplosive;
			ServerConfig.save();
		}
	}

	/** Diamond, no enchantments: leg reduction 0.45, arm 0.18. */
	private void plainSet(LivingEntity entity) {
		entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
	}

	/** Netherite with Blast Protection IV on every piece: leg reduction hits the 0.60 cap. */
	private void blastIv(GameTestHelper helper, LivingEntity entity) {
		Holder<Enchantment> blast = helper.getLevel().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BLAST_PROTECTION);
		equip(entity, EquipmentSlot.HEAD, Items.NETHERITE_HELMET, blast);
		equip(entity, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, blast);
		equip(entity, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, blast);
		equip(entity, EquipmentSlot.FEET, Items.NETHERITE_BOOTS, blast);
	}

	private void equip(LivingEntity entity, EquipmentSlot slot,
			net.minecraft.world.item.Item item, Holder<Enchantment> enchantment) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(enchantment, 4);
		entity.setItemSlot(slot, stack);
	}

	private void strip(LivingEntity entity) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			entity.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	// --- Direct-call tests: the rule itself, seeded exactly as the mixin seeds it ---

	/**
	 * The headline. A creeper on the same floor, a block from your shins, takes
	 * the leg it went off beside — and takes it whole, from a body that had
	 * nothing wrong with it a moment earlier.
	 *
	 * <p>Three cross-feature assertions ride along, because this is the cheapest
	 * place to make them. The scar proves {@code DestructionLogic.destroy} is
	 * still the single choke point every route goes through; the message key is
	 * the only way anything can observe this feature's four new lang keys at all
	 * (a mock player receives no packets), and it doubles as the proof that the
	 * dismemberment sentence is structurally the last line written on the frame —
	 * {@code sendOverlayMessage} means the last line is the only one read; and the
	 * cleared stash proves a blast is spent exactly once.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aPointBlankCreeperTakesTheLegItWentOffBeside(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		Notifier.clearLastMessageKey();
		blast(helper, player, data, 3.0, LEG_BLAST, SURVIVED_POINT_BLANK, false);
		BodyPart leg = takenLimb(helper, data);

		helper.assertTrue(leg.isLeg(), "the blast was beside the shins, so it is a leg: got " + leg.id());
		helper.assertTrue(data.part(leg).scar() == ScarLevel.DESTROYED,
				"the choke point must still raise the scar, got " + data.part(leg).scar());
		helper.assertTrue(("realisticdamage.msg.dismembered." + leg.id()).equals(Notifier.lastMessageKey()),
				"the player must be told what took it, got " + Notifier.lastMessageKey());
		helper.assertTrue(!data.hasPendingExplosion(), "and the blast is spent");
		helper.succeed();
	}

	/**
	 * The blow that rattles your skull still tells you which leg went.
	 *
	 * <p>{@code ConsciousnessSystem.onTrauma} runs last in the event — after the
	 * destruction line, after the ceiling, after {@code createWounds} — and every
	 * blast worth a limb is worth several times the 6.0 concussion bar. Before
	 * this was fixed, the concussion sentence was therefore the last line written
	 * on every dismemberment frame there had ever been, and
	 * {@code sendOverlayMessage} means the last line is the only one read: the
	 * sentence that exists to make distance, cover and Blast Protection learnable
	 * reached the player essentially never.
	 *
	 * <p>Both halves are asserted, because the fix is a suppression and a
	 * suppression is only right if it suppresses exactly one thing. The player
	 * <em>is</em> concussed — the status icon says so, the body panel says so,
	 * the slowed recovery and the extra head drain all still run — and the line
	 * they read is the one about the leg.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aConcussingBlastStillSaysWhichLimbWent(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		Notifier.clearLastMessageKey();
		// EXPLOSION is x1.0 consciousness and concussion-capable, so the applied
		// figure is the concussion figure verbatim: 19.0 against a bar of 6.0.
		blast(helper, player, data, 3.0, LEG_BLAST, SURVIVED_POINT_BLANK, false);
		BodyPart leg = takenLimb(helper, data);

		helper.assertTrue(data.concussion(),
				"the blow must really have concussed, or this test proves nothing");
		helper.assertTrue(("realisticdamage.msg.dismembered." + leg.id()).equals(Notifier.lastMessageKey()),
				"losing a limb outranks being concussed, so the limb-loss line keeps the frame: got "
						+ Notifier.lastMessageKey());
		helper.succeed();
	}

	/**
	 * One stump, not two wounds. The blast runs before the health-derived trauma,
	 * so by the time {@code createWounds} looks the limb is at zero and destroyed
	 * and there is nothing left for it to add — which matters, because a second
	 * wound arriving after the stump would make a stump bleed harder or softer as
	 * a function of how much armour the victim wore, and escaping exactly that is
	 * why the feature exists.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void theStumpCarriesExactlyOneCriticalWound(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		// Applied 12 puts 8.1 of ordinary trauma on that same leg — squarely in the
		// EXPLOSION profile's wound bands — so if the ordering ever slipped, this
		// is the blow that would grow the second wound.
		blast(helper, player, data, 3.0, LEG_BLAST, 12.0, false);
		BodyPart leg = takenLimb(helper, data);

		helper.assertTrue(woundsOn(data, leg).size() == 1,
				"the stump is the only wound on a limb that is gone, got " + woundsOn(data, leg).size());
		helper.assertTrue(woundsOn(data, leg).getFirst().severity() == WoundSeverity.CRITICAL,
				"a limb torn off is the worst bleed the model has, got "
						+ woundsOn(data, leg).getFirst().severity());
		helper.assertTrue(data.effectiveBleedRate() == Balance.BLEED_RATE_CRITICAL,
				"and it bleeds at exactly that rate, not the higher figure a stacked pair gives: "
						+ data.effectiveBleedRate());
		helper.succeed();
	}

	/**
	 * Not every creeper is a dismemberment. A plain-diamond leg comes off out to
	 * 2.633 blocks, so this is read a tenth of a block past that far edge rather
	 * than somewhere comfortable in the middle.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBlastThatIsNotExtremeEnoughLeavesTheLeg(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		plainSet(player);
		try {
			// d = 2.757, I = 18.48, leg trauma 6.86 against a bar of 7.2.
			blast(helper, player, data, 3.0, new Vec3(0.0, 0.2, 2.75), 8.0, false);

			assertNothingDestroyed(helper, data, "6.86 is not 7.2");
			boolean legHurt = data.part(BodyPart.LEFT_LEG).condition() < BodyPart.LEFT_LEG.maxCondition()
					|| data.part(BodyPart.RIGHT_LEG).condition() < BodyPart.RIGHT_LEG.maxCondition();
			helper.assertTrue(legHurt, "the ordinary trauma must still have landed, or this proves nothing");
		} finally {
			strip(player);
		}
		helper.succeed();
	}

	/** No RNG in outcomes: the same blast twice is the same injury. */
	@GameTest(environment = "realisticdamage:isolated")
	public void theSameBlastTwiceIsTheSameInjury(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		blast(helper, player, data, 3.0, LEG_BLAST, 5.0, false);
		int firstDestroyed = BodySnapshot.of(data).destroyedMask();
		int firstWounds = BodySnapshot.of(data).woundMask();

		data.reset();
		blast(helper, player, data, 3.0, LEG_BLAST, 5.0, false);

		helper.assertTrue(BodySnapshot.of(data).destroyedMask() == firstDestroyed,
				"the same blast must take the same limb");
		helper.assertTrue(BodySnapshot.of(data).woundMask() == firstWounds,
				"and open the same wound in the same place");
		helper.succeed();
	}

	/**
	 * The case the whole feature is for. Absorption is a shield on the health bar;
	 * the leg was still there. With nothing at all taken off the hearts, 1.5 would
	 * have reported that nothing happened.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void absorptionDoesNotHideTheBlast(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		blast(helper, player, data, 3.0, LEG_BLAST, 0.0, false);

		BodyPart leg = takenLimb(helper, data);
		helper.assertTrue(leg.isLeg(), "got " + leg.id());
		helper.succeed();
	}

	/**
	 * The one thing that does stand between you and it. Vanilla sets
	 * {@code blocked} for any block including a partial one, and "I raised my
	 * shield and still lost the leg" is the least readable outcome available here
	 * — the pipeline already charges the shield arm for blocking a blast.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aRaisedShieldKeepsTheLeg(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		blast(helper, player, data, 3.0, LEG_BLAST, 5.0, true);

		assertNothingDestroyed(helper, data, "a raised shield keeps the limb");
		helper.succeed();
	}

	/**
	 * Off, the 1.5 rule is exactly what is left: a blast can still finish off a
	 * limb something else had already ruined, on the health-derived figure and
	 * with the 1.5 wording.
	 *
	 * <p>Note what "the 1.5 rule" means since the stump landed earlier in this
	 * release: a finished-off limb <em>does</em> now bleed, and it bleeds SEVERE
	 * rather than CRITICAL, because a limb ground to zero over a fight was dead
	 * weight before this hit arrived. That is the finish-off rule, correctly, and
	 * the sentence the player reads is the distinguishable thing.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void theToggleRestoresTheOldFinishOffRule(GameTestHelper helper) {
		withToggles(true, false, () -> {
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();

			// A healthy leg, and a blast that would certainly have taken it.
			blast(helper, player, data, 3.0, LEG_BLAST, 5.0, false);
			assertNothingDestroyed(helper, data, "with the toggle off no blast takes a limb");

			// Now the 1.5 rule's own input: a leg already at zero, and enough on the
			// health bar for attempted (12 x 1.35 x 0.50 = 8.1) to clear the 6.0 bar.
			data.reset();
			data.part(BodyPart.LEFT_LEG).setCondition(0.0);
			data.part(BodyPart.RIGHT_LEG).setCondition(0.0);
			// 12 HP concusses — nothing that clears the finish-off bar (8.9 HP)
			// could ever stay under the concussion bar (6.0), which is why this
			// used to pre-set the concussion flag so that no line would be written.
			// It no longer has to: losing a limb outranks being concussed, so the
			// destruction sentence keeps the frame and the concussion is still set
			// underneath it. Asserted here as well, because this is the finish-off
			// path rather than the blast one and the suppression has to cover both.
			Notifier.clearLastMessageKey();
			blast(helper, player, data, 3.0, LEG_BLAST, 12.0, false);
			BodyPart leg = takenLimb(helper, data);

			helper.assertTrue(woundsOn(data, leg).size() == 1
							&& woundsOn(data, leg).getFirst().severity() == WoundSeverity.SEVERE,
					"a limb ground down and then finished off bleeds as a grind, got " + woundsOn(data, leg));
			helper.assertTrue(data.concussion(),
					"12 HP of blast concusses, and the state is never what yields");
			helper.assertTrue(("realisticdamage.msg.stump." + leg.id()).equals(Notifier.lastMessageKey()),
					"and it is the destruction sentence, not the blast one, got " + Notifier.lastMessageKey());
		});
		helper.succeed();
	}

	/**
	 * The nested gate. {@code enableLimbDestruction} is what makes a Destroyed
	 * limb exist at all — the hand lock and the offhand block key on it — so a
	 * blast must never create one while the Grounded preset says there is no
	 * destruction.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void noLimbDestructionMeansNoDismemberment(GameTestHelper helper) {
		withToggles(false, true, () -> {
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();

			blast(helper, player, data, 3.0, LEG_BLAST, 5.0, false);

			assertNothingDestroyed(helper, data, "Grounded promises no destruction and must keep it");
		});
		helper.succeed();
	}

	/**
	 * The armour sentence as it now reads, end to end through the real armour code:
	 * Blast Protection IV is a shorter reach, not immunity. Point blank, 43 x 0.27
	 * = 11.61 against a bar of 7.2 and the leg goes; past 1.72 blocks it does not.
	 * The old sentence — "Blast IV walks away from a creeper" — held only because
	 * the 0.60 cap used to need an intensity of 44.44 and a creeper peaks at 43.
	 *
	 * <p>All three legs of the sentence are read at one distance, 2.2 blocks, which
	 * is the only way "what the enchantment buys" is a statement about the
	 * enchantment rather than about two unrelated blasts: there Blast IV keeps the
	 * leg (6.12), plain diamond does not (8.41), and a stick of TNT takes it
	 * through the cap anyway (9.70).
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void blastProtectionFourIsAShorterReachNotImmunity(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		blastIv(helper, player);
		try {
			// d = 0.2, I = 40.92, leg trauma through the 0.60 cap 11.05 against 7.2.
			blast(helper, player, data, 3.0, new Vec3(0.0, 0.2, 0.0), 4.0, false);
			helper.assertTrue(takenLimb(helper, data).isLeg(),
					"a creeper at your feet goes through Blast IV netherite now");

			// d = 2.209, I = 22.65, and through the cap that is 6.12: it holds.
			data.reset();
			blast(helper, player, data, 3.0, new Vec3(0.0, 0.2, 2.2), 4.0, false);
			assertNothingDestroyed(helper, data, "6.12 is not 7.2 — the reach ends inside 1.72 blocks");

			// The same blast, the same distance, plain diamond: 8.41, and it goes.
			// This is what the enchantment is worth, stated as a difference.
			strip(player);
			plainSet(player);
			data.reset();
			blast(helper, player, data, 3.0, new Vec3(0.0, 0.2, 2.2), 4.0, false);
			helper.assertTrue(takenLimb(helper, data).isLeg(),
					"and plain diamond loses the leg where Blast IV kept it");

			// TNT was never stopped by it at any threshold: I = 35.94, cap 9.70.
			strip(player);
			blastIv(helper, player);
			data.reset();
			blast(helper, player, data, 4.0, new Vec3(0.0, 0.2, 2.2), 4.0, false);
			helper.assertTrue(takenLimb(helper, data).isLeg(),
					"TNT at the same distance goes through it");
		} finally {
			strip(player);
		}
		helper.succeed();
	}

	/**
	 * <b>The case the threshold was lowered for.</b> A plain-armoured player at ten
	 * hearts, on Normal, and a creeper two and a half blocks away: the leg comes off
	 * and the player is still standing.
	 *
	 * <p>At 2.408 blocks the blast is 21.10 — 7.83 of leg trauma against a bar of
	 * 7.2, asserted below — and vanilla's own chain (Normal is x1, then
	 * {@code CombatRules.getDamageAfterAbsorb} on 20 armour and 8 toughness) turns
	 * that 21.10 into 8.67 hit points, which is the figure seeded here. That
	 * arithmetic is derived and checked in {@code ExplosionMathTest}; what this test
	 * adds is that it runs through the real {@code ArmorModifier} on a real diamond
	 * set, and that 8.67 is a survivable blow at ten hearts — because if it were
	 * not, {@code AFTER_DAMAGE} would never fire and this pipeline would never be
	 * asked.
	 *
	 * <p>At the shipped 0.75 the same blast was worth 7.83 against a bar of 12.0
	 * and this player kept the leg at every distance they could live through.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void plainArmourAtTenHeartsLosesTheLegAndLives(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		plainSet(player);
		try {
			helper.assertTrue(PLAIN_DIAMOND_HP_AT_TWO_AND_A_HALF < 10.0,
					"the blow has to be survivable at ten hearts or the mod is never called: "
							+ PLAIN_DIAMOND_HP_AT_TWO_AND_A_HALF);
			blast(helper, player, data, 3.0, new Vec3(0.0, 0.2, 2.4),
					PLAIN_DIAMOND_HP_AT_TWO_AND_A_HALF, false);

			BodyPart leg = takenLimb(helper, data);
			helper.assertTrue(leg.isLeg(), "a creeper at the shins takes a leg, got " + leg.id());
			helper.assertTrue(data.part(leg).scar() == ScarLevel.DESTROYED,
					"through the same choke point as every other route");
			helper.assertTrue(!player.isDeadOrDying(),
					"and the player lives, which is the whole point of the retune");
		} finally {
			strip(player);
		}
		helper.succeed();
	}

	/**
	 * Only the region the blast was nearest, in both directions the rule has one.
	 *
	 * <p>First: a charged creeper at head height takes nothing. The head and chest
	 * are excluded by rule, and the other four regions are on a 0.05 remainder
	 * share the gate refuses to look at.
	 *
	 * <p>Then the half that the retune made real. At 0.75 the share gate was a belt
	 * over braces — a 0.05 remainder is worth {@code I x 1.35 x 0.05}, so it needed
	 * an intensity of 9.75 / 0.0675 = 144 and nothing in the game reaches that. At
	 * 0.45 the arm bar is 5.85 and the remainder crosses it at 86.67, which a
	 * wither's death blast (14 x 7 + 1 = 99) clears outright and a charged creeper
	 * or end crystal (85) clears once {@code bodyDamageMultiplier} passes 1.02. So
	 * the second half fires a wither-sized blast at the shins: the leg it went off
	 * beside goes, and both arms — on 6.43 against a bar of 5.85 — stay on because
	 * the gate never asks about them. Without it, one blast would cost three limbs
	 * at stock settings.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void onlyTheRegionTheBlastWasNearestCanGo(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		blast(helper, player, data, 6.0, HEAD_BLAST, 6.0, false);

		assertNothingDestroyed(helper, data, "a head-primary blast takes nothing");
		helper.assertTrue(!player.isDeadOrDying(), "and above all it does not kill through the body model");

		// d = 0.361, I = 95.25: 64.3 on the leg and 6.43 on each remainder region.
		data.reset();
		blast(helper, player, data, 7.0, new Vec3(0.0, 0.2, 0.3), 6.0, false);

		// takenLimb itself asserts exactly one — which is the whole assertion here.
		helper.assertTrue(takenLimb(helper, data).isLeg(), "the biggest blast in the game still takes one leg");
		helper.assertTrue(!data.part(BodyPart.LEFT_ARM).destroyed()
						&& !data.part(BodyPart.RIGHT_ARM).destroyed(),
				"and the arms stay on, on a remainder worth 6.43 against a bar of 5.85");
		helper.succeed();
	}

	/**
	 * A limb already Disabled meets the same bar as a healthy one — there is no
	 * second, cheaper tier on blast trauma (which is unbounded by armour and by
	 * remaining hearts, unlike the figure the 1.5 threshold was tuned against).
	 * The first half proves the tier is absent; the second proves the rule was
	 * running at all, so the first half cannot pass for the wrong reason.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aDisabledLimbKeepsTheOldFinishOffNumbers(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.LEFT_LEG).setCondition(0.0);
		data.part(BodyPart.RIGHT_LEG).setCondition(0.0);

		// d = 4.10, I = 9.73, leg trauma 6.57: past the 1.5 leg threshold of 6.0 and
		// short of 7.2. Applied 0, so the health path cannot act either. The gap
		// between those two bars is the whole test, and at 0.45 it is 1.2 points
		// wide, so this distance is chosen to sit inside it rather than beyond it.
		blast(helper, player, data, 3.0, new Vec3(0.0, 0.2, 4.1), 0.0, false);
		assertNothingDestroyed(helper, data, "6.57 is not 7.2, on a Disabled limb as much as on a whole one");

		data.reset();
		data.part(BodyPart.LEFT_LEG).setCondition(0.0);
		data.part(BodyPart.RIGHT_LEG).setCondition(0.0);
		blast(helper, player, data, 3.0, LEG_BLAST, 0.0, false);
		BodyPart leg = takenLimb(helper, data);

		helper.assertTrue(woundsOn(data, leg).size() == 1
						&& woundsOn(data, leg).getFirst().severity() == WoundSeverity.CRITICAL,
				"a blast never grinds a limb down — it takes it off, got " + woundsOn(data, leg));
		helper.succeed();
	}

	/**
	 * A server that softens PvP softens every consequence of player-lit TNT, not
	 * only the hearts. {@code pvpTraumaMultiplier = 0} must mean no PvP
	 * dismemberment at all.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void pvpMultiplierZeroSparesTheLimb(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, 4.0);
		DamageSource source = helper.getLevel().damageSources().explosion(attacker, attacker);
		double previous = ServerConfig.get().pvpTraumaMultiplier;
		try {
			RealisticDamageData data = RDAttachments.get(player);

			ServerConfig.get().pvpTraumaMultiplier = 0.0;
			data.reset();
			blast(helper, player, data, 3.0, LEG_BLAST, 5.0, false, source);
			assertNothingDestroyed(helper, data, "PvP trauma scaled to nothing takes no limbs");

			ServerConfig.get().pvpTraumaMultiplier = 1.0;
			data.reset();
			blast(helper, player, data, 3.0, LEG_BLAST, 5.0, false, source);
			helper.assertTrue(takenLimb(helper, data).isLeg(), "and at 1.0 the same blast takes the leg");
		} finally {
			ServerConfig.get().pvpTraumaMultiplier = previous;
			ServerConfig.save();
		}
		helper.succeed();
	}

	/**
	 * The routing fix, which is geometry rather than an injury rule and is
	 * therefore <em>not</em> behind the dismemberment toggle. An ownerless
	 * explosion has no source position at all, and 1.5 fell back to splitting the
	 * trauma between the head and chest whatever side the blast came from — so an
	 * arm or a leg could never be primary. The captured centre is the point
	 * vanilla measured distance and exposure from, so it is the honest one.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void theCapturedCentreRoutesAnOwnerlessBlast(GameTestHelper helper) {
		withToggles(true, false, () -> {
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();

			// Deliberately out at 3.3 blocks and with the toggle off, so nothing can
			// come off and the only thing under test is where the trauma landed.
			blast(helper, player, data, 3.0, new Vec3(0.0, 0.2, 3.3), 6.0, false,
					helper.getLevel().damageSources().explosion(null, null));

			// Everything takes something from a blast — the four non-primary regions
			// share a 0.05 remainder each — so the question is not whether the head
			// was touched but whether the leg took the primary half. Under the old
			// fallback the legs would be on zero and the head on the larger share,
			// so the comparison is the whole assertion.
			double headLost = BodyPart.HEAD.maxCondition() - data.part(BodyPart.HEAD).condition();
			double legLost = Math.max(
					BodyPart.LEFT_LEG.maxCondition() - data.part(BodyPart.LEFT_LEG).condition(),
					BodyPart.RIGHT_LEG.maxCondition() - data.part(BodyPart.RIGHT_LEG).condition());
			helper.assertTrue(legLost > 0.0, "a blast beside the shins must reach a leg at all");
			helper.assertTrue(legLost > headLost,
					"the leg the blast went off beside must be primary, not the head-and-chest fallback: leg lost "
							+ legLost + ", head lost " + headLost);
		});
		helper.succeed();
	}

	/** Only an explosion spends a blast. Anything else must walk past it untouched. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aStaleStashScoresNothingElse(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setPendingExplosion(new PendingExplosion(43.0, 0.0, 0.2, 1.0));
		try {
			TraumaPipeline.handle(player, data, helper.getLevel().damageSources().generic(),
					4.0, 4.0, 4.0, false);

			assertNothingDestroyed(helper, data, "a punch is not a blast");
			helper.assertTrue(data.hasPendingExplosion(),
					"and it must not have been consumed by one either");
		} finally {
			data.clearPendingExplosion();
		}
		helper.succeed();
	}

	/**
	 * The seam with long-term injuries. A limb torn off by a blast is destroyed
	 * before the health-derived trauma runs, which is precisely why the ceiling
	 * rule captures its "before" masks at the very top of the event — otherwise
	 * the limb would already be gone by the time it looked, no new event would be
	 * filed, and the limb would come back on the next body at a full ceiling.
	 *
	 * <p>Silently, and deliberately: the destruction line and its sound already
	 * own that moment, so the ceiling adds a rung and no sentence.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBlastTakenLimbLowersTheCeilingByOneRung(GameTestHelper helper) {
		boolean previous = ServerConfig.get().enableLongTermInjuries;
		try {
			ServerConfig.get().enableLongTermInjuries = true;
			ServerPlayer player = mockPlayer(helper);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();

			Notifier.clearLastMessageKey();
			blast(helper, player, data, 3.0, LEG_BLAST, SURVIVED_POINT_BLANK, false);
			BodyPart leg = takenLimb(helper, data);
			BodyPartData part = data.part(leg);

			helper.assertTrue(part.ceilingBand() == 1,
					"a limb the blast took is one rung of ceiling, got " + part.ceilingBand());
			helper.assertTrue(data.part(otherLeg(leg)).ceilingBand() == 0,
					"and only that limb — the other leg keeps its full range");
			helper.assertTrue(("realisticdamage.msg.dismembered." + leg.id()).equals(Notifier.lastMessageKey()),
					"the rung comes down silently: the blast sentence must survive it, got "
							+ Notifier.lastMessageKey());
		} finally {
			ServerConfig.get().enableLongTermInjuries = previous;
			ServerConfig.save();
		}
		helper.succeed();
	}

	private BodyPart otherLeg(BodyPart leg) {
		return leg == BodyPart.LEFT_LEG ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
	}

	// --- Real detonations: through ServerExplosion, the mixin and the damage hooks ---

	private static volatile boolean listening;
	private static volatile double observedBaseDamage;

	static {
		// Registered once, for the life of the process, and gated on a flag: the
		// event has no unregister, and adding one purely for a test would be a
		// worse API. Read and written inside a single synchronous body.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (listening && entity instanceof TestPlayer) {
				observedBaseDamage = baseDamageTaken;
			}
		});
	}

	private TestPlayer dummy(GameTestHelper helper) {
		TestPlayer dummy = TestPlayer.spawn(helper.getLevel(),
				helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)), 0.0F, "Blasted");
		// Two hundred hearts so it survives a stick of TNT on any difficulty; the
		// dismemberment assertions are difficulty-independent by construction, and
		// a dead dummy fires no AFTER_DAMAGE at all.
		dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		dummy.setHealth(200.0F);
		RDAttachments.get(dummy).reset();
		return dummy;
	}

	/**
	 * Puts a dummy back where it was, whole, and — the part that is easy to miss —
	 * outside its own invulnerability window. Two detonations inside one
	 * synchronous body land on the same tick, and vanilla refuses a second hit of
	 * equal or smaller size while {@code invulnerableTime > 10}, so without this
	 * the second blast fields no damage event at all and the test would pass or
	 * fail for a reason that has nothing to do with the rule.
	 */
	private void resetForAnotherBlast(TestPlayer dummy, Vec3 home) {
		dummy.restore();
		dummy.snapTo(home.x, home.y, home.z);
		dummy.setDeltaMovement(Vec3.ZERO);   // the knockback would otherwise move it, and d decides everything
		dummy.setHealth(200.0F);
		dummy.invulnerableTime = 0;
		dummy.hurtTime = 0;
	}

	private void requireRealDifficulty(GameTestHelper helper) {
		helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL,
				"the gametest world must not be peaceful — a peaceful explosion deals nothing, "
						+ "no event fires, and every assertion below would pass for the wrong reason");
	}

	/**
	 * The whole path, for real: a stick of TNT beside a live player's leg, through
	 * {@code ServerExplosion}, the mixin's stash and Fabric's damage events.
	 *
	 * <p>It also cross-checks the restated curve against vanilla's own number. The
	 * gametest server runs on Easy, where {@code Player.hurtServer} reports
	 * {@code min(d/2 + 1, d)} — so the figure the event carries is the blast put
	 * through difficulty, and the figure the rule used is the blast itself. Both
	 * are derived here from the same {@code intensity} call, which is what proves
	 * the stash is genuinely pre-difficulty.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void tntAgainstTheLegOfARealPlayer(GameTestHelper helper) {
		requireRealDifficulty(helper);
		TestPlayer dummy = dummy(helper);
		try {
			listening = true;
			observedBaseDamage = -1.0;
			double distance = 1.0;
			helper.getLevel().explode(null, dummy.getX() + distance, dummy.getY(), dummy.getZ(),
					4.0F, Level.ExplosionInteraction.NONE);

			RealisticDamageData data = RDAttachments.get(dummy);
			BodyPart leg = takenLimb(helper, data);
			helper.assertTrue(leg.isLeg(), "TNT at the feet takes a leg, got " + leg.id());
			helper.assertTrue(woundsOn(data, leg).size() == 1
							&& woundsOn(data, leg).getFirst().severity() == WoundSeverity.CRITICAL,
					"and it bleeds like a stump, got " + woundsOn(data, leg));
			helper.assertTrue(!data.hasPendingExplosion(), "the mixin's finally must have run");

			double raw = ExplosionMath.intensity(4.0, distance, 1.0);
			double expected = switch (helper.getLevel().getDifficulty()) {
				case EASY -> Math.min(raw / 2.0 + 1.0, raw);
				case HARD -> raw * 3.0 / 2.0;
				default -> raw;
			};
			helper.assertTrue(Math.abs(observedBaseDamage - expected) < 0.5,
					"the restated curve must be vanilla's: expected about " + expected
							+ " on " + helper.getLevel().getDifficulty() + ", saw " + observedBaseDamage);
		} finally {
			listening = false;
			dummy.connection.disconnect(Component.literal("test over"));
		}
		helper.succeed();
	}

	/**
	 * Determinism through the live path, which is where it could most easily be
	 * lost: the knockback moves the dummy, so it is put back before the second
	 * detonation. Given the blast, the mod's answer is a pure function — the
	 * randomness a TNT minecart has lives in vanilla's radius, upstream of us.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void detonatingTwiceIsDeterministic(GameTestHelper helper) {
		requireRealDifficulty(helper);
		TestPlayer dummy = dummy(helper);
		try {
			Vec3 home = dummy.position();
			helper.getLevel().explode(null, home.x + 1.0, home.y, home.z, 4.0F, Level.ExplosionInteraction.NONE);
			int firstDestroyed = BodySnapshot.of(RDAttachments.get(dummy)).destroyedMask();
			int firstWounds = BodySnapshot.of(RDAttachments.get(dummy)).woundMask();
			helper.assertTrue(firstDestroyed != 0, "the first detonation must have done something");

			resetForAnotherBlast(dummy, home);
			helper.getLevel().explode(null, home.x + 1.0, home.y, home.z, 4.0F, Level.ExplosionInteraction.NONE);

			helper.assertTrue(BodySnapshot.of(RDAttachments.get(dummy)).destroyedMask() == firstDestroyed,
					"the same detonation must take the same limb");
			helper.assertTrue(BodySnapshot.of(RDAttachments.get(dummy)).woundMask() == firstWounds,
					"and open the same wound");
		} finally {
			dummy.connection.disconnect(Component.literal("test over"));
		}
		helper.succeed();
	}

	/**
	 * End-to-end proof of the armour sentence, on a real body and a real detonation:
	 * at two blocks Blast Protection IV netherite keeps the leg and the same body
	 * without it does not.
	 *
	 * <p>Two blocks rather than the half-block this used to use, because half a
	 * block is now inside the cap's reach — 10.23 against a bar of 7.2 — and the
	 * old assertion would be asserting the opposite of the truth. At two blocks the
	 * intensity is 24.33, worth 6.57 through the 0.60 cap and 16.43 bare, so the
	 * armoured side holds for <em>any</em> exposure (it is already under the bar at
	 * exposure 1.0) and the bare side needs only 7.2 / 16.43 = 0.44 of it, which is
	 * the margin a real explosion's ray casting has to clear.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void blastFourNetheriteShortensACreepersReach(GameTestHelper helper) {
		requireRealDifficulty(helper);
		TestPlayer dummy = dummy(helper);
		try {
			Vec3 home = dummy.position();
			blastIv(helper, dummy);
			helper.getLevel().explode(null, home.x + 2.0, home.y, home.z, 3.0F, Level.ExplosionInteraction.NONE);
			assertNothingDestroyed(helper, RDAttachments.get(dummy), "Blast IV netherite keeps the leg at two blocks");
			helper.assertTrue(!RDAttachments.get(dummy).hasPendingExplosion(), "and the stash is clear");

			strip(dummy);
			resetForAnotherBlast(dummy, home);
			helper.getLevel().explode(null, home.x + 2.0, home.y, home.z, 3.0F, Level.ExplosionInteraction.NONE);

			helper.assertTrue(takenLimb(helper, RDAttachments.get(dummy)).isLimb(),
					"and the same creeper against a bare body takes the limb");
		} finally {
			dummy.connection.disconnect(Component.literal("test over"));
		}
		helper.succeed();
	}
}
