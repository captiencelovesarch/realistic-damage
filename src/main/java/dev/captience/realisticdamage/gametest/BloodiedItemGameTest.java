package dev.captience.realisticdamage.gametest;

import java.util.List;

import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BloodiedItem;
import dev.captience.realisticdamage.systems.BloodKind;
import dev.captience.realisticdamage.systems.BloodLayer;
import dev.captience.realisticdamage.systems.BloodiedMath;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Blood on the weapon that drew it (2.0), through the real path: a held item,
 * a real {@code hurtServer}, and whatever the damage hooks then write onto
 * the item.
 *
 * <p>Everything happens inside one synchronous method body, like the command
 * tests: the rule being switched is server-wide, and a test that held it
 * across a tick would switch it under its neighbours.
 */
public class BloodiedItemGameTest {
	private static final BlockPos AT = new BlockPos(1, 1, 1);

	/** A player standing beside the body with this in hand. */
	private static ServerPlayer armed(GameTestHelper helper, LivingEntity target, ItemStack weapon) {
		ServerPlayer attacker = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		// Two blocks north of it, looking south along the level: the swing meets the body.
		attacker.setPos(target.getX(), target.getY(), target.getZ() - 2.0);
		attacker.setYRot(0.0F);
		attacker.setYHeadRot(0.0F);
		attacker.setXRot(0.0F);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return attacker;
	}

	private static void strike(GameTestHelper helper, LivingEntity target, ServerPlayer attacker, float damage) {
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), damage);
	}

	private static float total(ItemStack stack) {
		return BloodiedMath.total(BloodiedItem.read(stack));
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aSwordThatOpensAWoundKeepsTheBlood(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		ServerPlayer attacker = null;
		try {
			zombie.setNoAi(true);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			attacker = armed(helper, zombie, sword);
			helper.assertTrue(BloodiedItem.read(sword).isEmpty(), "a new sword is clean");
			strike(helper, zombie, attacker, 7.0F);
			helper.assertTrue(zombie.isAlive(), "seven points should not kill a zombie");
			List<BloodLayer> marks = BloodiedItem.read(attacker.getMainHandItem());
			helper.assertTrue(!marks.isEmpty(), "a sword that cut a zombie open came away clean");
			helper.assertTrue(marks.get(0).kind() == BloodKind.STREAK,
					"a slash leaves a streak, got " + marks.get(0).kind());
			helper.assertTrue(marks.get(0).tint().equals("dark"),
					"a zombie's blood is the dark kind, got " + marks.get(0).tint());
			helper.assertTrue(marks.get(0).wetAt() == helper.getLevel().getGameTime(), "and it is wet as of now");

			// A second cut straight after joins the streak that is there.
			float before = total(attacker.getMainHandItem());
			zombie.invulnerableTime = 0;
			strike(helper, zombie, attacker, 4.0F);
			List<BloodLayer> again = BloodiedItem.read(attacker.getMainHandItem());
			helper.assertTrue(total(attacker.getMainHandItem()) > before, "a second cut added nothing");
			long streaks = again.stream().filter(mark -> mark.kind() == BloodKind.STREAK).count();
			helper.assertTrue(streaks == 1, "two cuts into one zombie should be one streak, got " + streaks);
		} finally {
			zombie.discard();
			if (attacker != null) {
				attacker.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void differentBloodsSitOnOneBlade(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		EnderMan enderman = helper.spawn(EntityTypes.ENDERMAN, new BlockPos(3, 1, 1));
		ServerPlayer attacker = null;
		try {
			zombie.setNoAi(true);
			enderman.setNoAi(true);
			attacker = armed(helper, zombie, new ItemStack(Items.IRON_SWORD));
			strike(helper, zombie, attacker, 7.0F);
			strike(helper, enderman, attacker, 9.0F);
			List<BloodLayer> marks = BloodiedItem.read(attacker.getMainHandItem());
			helper.assertTrue(marks.stream().anyMatch(mark -> mark.tint().equals("dark")), "no zombie blood on it");
			helper.assertTrue(marks.stream().anyMatch(mark -> mark.tint().equals("purple")),
					"no enderman blood on it: " + marks);
			helper.assertTrue(marks.stream().map(BloodLayer::seed).distinct().count() == marks.size(),
					"every mark has its own place");
		} finally {
			zombie.discard();
			enderman.discard();
			if (attacker != null) {
				attacker.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theKillingBlowBloodiesTheBladeToo(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		ServerPlayer attacker = null;
		try {
			zombie.setNoAi(true);
			attacker = armed(helper, zombie, new ItemStack(Items.IRON_SWORD));
			strike(helper, zombie, attacker, 100.0F);
			helper.assertTrue(!zombie.isAlive(), "a hundred points should kill a zombie");
			helper.assertTrue(total(attacker.getMainHandItem()) >= BloodiedMath.KILLING_BLOW - 1.0e-4F,
					"the blow that killed left the sword clean: " + BloodiedItem.read(attacker.getMainHandItem()));
		} finally {
			zombie.discard();
			if (attacker != null) {
				attacker.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aMaceLeavesASplatAndASpearATip(GameTestHelper helper) {
		Zombie one = helper.spawn(EntityTypes.ZOMBIE, AT);
		Zombie two = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
		ServerPlayer blunt = null;
		ServerPlayer point = null;
		try {
			one.setNoAi(true);
			two.setNoAi(true);
			blunt = armed(helper, one, new ItemStack(Items.MACE));
			// Bleeding already, so a blunt blow has blood to land in.
			dev.captience.realisticdamage.RDAttachments.get(one).addWound(
					dev.captience.realisticdamage.body.BodyPart.TORSO, dev.captience.realisticdamage.body.WoundSeverity.SEVERE);
			strike(helper, one, blunt, 100.0F);
			List<BloodLayer> onMace = BloodiedItem.read(blunt.getMainHandItem());
			helper.assertTrue(onMace.stream().anyMatch(mark -> mark.kind() == BloodKind.SPLAT),
					"a mace should leave a splat: " + onMace);
			point = armed(helper, two, new ItemStack(Items.TRIDENT));
			strike(helper, two, point, 100.0F);
			List<BloodLayer> onTrident = BloodiedItem.read(point.getMainHandItem());
			helper.assertTrue(onTrident.stream().anyMatch(mark -> mark.kind() == BloodKind.TIP),
					"a thrust should bloody the tip: " + onTrident);
		} finally {
			one.discard();
			two.discard();
			if (blunt != null) {
				blunt.discard();
			}
			if (point != null) {
				point.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void stacksBonesAndASwitchedOffRuleStayClean(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(3, 1, 1));
		ServerPlayer attacker = null;
		boolean rule = ServerConfig.get().enableBloodiedItems;
		try {
			zombie.setNoAi(true);
			skeleton.setNoAi(true);
			// A stack: sixty-four sticks do not turn red for one blow.
			attacker = armed(helper, zombie, new ItemStack(Items.STICK, 64));
			dev.captience.realisticdamage.RDAttachments.get(zombie).addWound(
					dev.captience.realisticdamage.body.BodyPart.TORSO, dev.captience.realisticdamage.body.WoundSeverity.SEVERE);
			strike(helper, zombie, attacker, 3.0F);
			helper.assertTrue(attacker.getMainHandItem().get(DataComponents.CUSTOM_DATA) == null,
					"a stack of sticks took blood");
			// A skeleton has none to give.
			attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			strike(helper, skeleton, attacker, 100.0F);
			helper.assertTrue(BloodiedItem.read(attacker.getMainHandItem()).isEmpty(), "bones bled onto a sword");
			// The rule off: the same cut that bloodied the sword in the first test leaves it clean.
			ServerConfig.get().enableBloodiedItems = false;
			zombie.invulnerableTime = 0;
			strike(helper, zombie, attacker, 7.0F);
			helper.assertTrue(BloodiedItem.read(attacker.getMainHandItem()).isEmpty(),
					"the rule was off and the sword took blood anyway");
		} finally {
			ServerConfig.get().enableBloodiedItems = rule;
			zombie.discard();
			skeleton.discard();
			if (attacker != null) {
				attacker.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void bloodIsOnTheItemItselfAndWaterTakesItOff(GameTestHelper helper) {
		long now = helper.getLevel().getGameTime();
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		BloodiedItem.add(sword, BloodKind.STREAK, "red", 0.5F, 11, now);
		BloodiedItem.add(sword, BloodKind.TIP, "purple", 0.3F, 12, now);

		// Saved and loaded the way a chest saves it: the marks come back as they were.
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, sword).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(BloodiedItem.read(loaded).equals(BloodiedItem.read(sword)),
				"the marks did not survive a save: " + BloodiedItem.read(loaded));
		// And it is vanilla's own custom_data that carries them, nothing of the mod's.
		helper.assertTrue(saved.toString().contains("minecraft:custom_data") && saved.toString().contains(BloodiedItem.KEY),
				"the blood is not in custom_data: " + saved);

		// The hand does not treat a bloodied sword as a different sword...
		helper.assertTrue(BloodiedItem.sameButForBlood(new ItemStack(Items.DIAMOND_SWORD), sword),
				"a bloodied sword should be the same sword as a clean one");
		// ...but an enchanted one is.
		ItemStack sharp = new ItemStack(Items.DIAMOND_SWORD);
		sharp.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS), 1);
		helper.assertTrue(!BloodiedItem.sameButForBlood(sharp, sword), "an enchanted sword is not the same sword");

		// Water: gone in a few seconds, and the item is exactly what it was before.
		int seconds = 0;
		while (!BloodiedItem.read(sword).isEmpty() && seconds < 20) {
			BloodiedItem.wash(sword, now);
			seconds++;
		}
		helper.assertTrue(seconds == 2, "half a streak, wet, should rinse off in two seconds, took " + seconds);
		helper.assertTrue(sword.get(DataComponents.CUSTOM_DATA) == null,
				"a rinsed sword should have nothing left on it at all");
		helper.assertTrue(ItemStack.isSameItemSameComponents(sword, new ItemStack(Items.DIAMOND_SWORD)),
				"a rinsed sword should be a clean sword again");
		helper.succeed();
	}
}
