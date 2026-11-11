package dev.captience.realisticdamage.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RDComponents;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.Decapitation;
import dev.captience.realisticdamage.entity.SeveredHead;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Heads come off (2.0): an edge on a ruined head takes it, anything else still
 * crushes it; the head is a thing you can pick up, carry and place but never
 * wear, and it stays that way through being placed and broken; a wither
 * skeleton's skull hisses and then burns away or drops; no totem gives a head back; and a zombie walks on
 * without one for a few seconds before it drops.
 *
 * <p>Each case cleans up the bodies and heads it made before it returns.
 */
public class DecapitationGameTest {
	private static final BlockPos AT = new BlockPos(1, 1, 1);

	private static ServerPlayer attackerFacing(GameTestHelper helper, LivingEntity target, ItemStack weapon) {
		ServerPlayer attacker = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		// Two blocks north of it, looking south along the level: the swing meets the head.
		attacker.setPos(target.getX(), target.getY() + target.getEyeHeight() - attacker.getEyeHeight(), target.getZ() - 2.0);
		attacker.setYRot(0.0F);
		attacker.setYHeadRot(0.0F);
		attacker.setXRot(0.0F);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return attacker;
	}

	private static void ruinHead(RealisticDamageData data) {
		data.part(BodyPart.HEAD).setCondition(0.0);
	}

	private static List<SeveredHead> headsNear(GameTestHelper helper, LivingEntity body) {
		// Its own head only: tests run side by side, and a neighbour's head can roll into range.
		return helper.getLevel().getEntitiesOfClass(SeveredHead.class, body.getBoundingBox().inflate(10.0),
				head -> body.getUUID().equals(head.ownerId()));
	}

	private static void clearHeads(GameTestHelper helper, LivingEntity body) {
		for (SeveredHead head : headsNear(helper, body)) {
			head.discard();
		}
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anAxeOnARuinedHeadTakesItOff(GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, AT);
		try {
			skeleton.setNoAi(true);
			RealisticDamageData data = RDAttachments.get(skeleton);
			ruinHead(data);
			helper.assertTrue(data.part(BodyPart.HEAD).state() == BodyState.DISABLED, "the head was meant to be ruined");
			ServerPlayer attacker = attackerFacing(helper, skeleton, new ItemStack(Items.DIAMOND_AXE));
			skeleton.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 15.0F);
			helper.assertTrue(!skeleton.isAlive(), "a skeleton lived through losing its head");
			helper.assertTrue(data.headSevered(), "the axe crushed the head instead of taking it");
			List<SeveredHead> heads = headsNear(helper, skeleton);
			helper.assertTrue(heads.size() == 1, "expected one head on the floor, found " + heads.size());
			SeveredHead head = heads.getFirst();
			helper.assertTrue(head.trophy(), "a skeleton's skull can be taken");
			ItemStack skull = head.stack();
			helper.assertTrue(skull != null && skull.is(Items.SKELETON_SKULL), "the head is not a skeleton skull: " + skull);
			helper.assertTrue(RDComponents.severed(skull), "the skull is not marked severed");
			helper.assertTrue(!skull.has(DataComponents.EQUIPPABLE), "a severed skull can still be worn");
		} finally {
			clearHeads(helper, skeleton);
			skeleton.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aFistOnARuinedHeadStillCrushesIt(GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, AT);
		try {
			skeleton.setNoAi(true);
			RealisticDamageData data = RDAttachments.get(skeleton);
			ruinHead(data);
			ServerPlayer attacker = attackerFacing(helper, skeleton, ItemStack.EMPTY);
			skeleton.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 15.0F);
			helper.assertTrue(!data.headSevered(), "a fist took a head off");
			helper.assertTrue(headsNear(helper, skeleton).isEmpty(), "a fist left a head on the floor");
		} finally {
			clearHeads(helper, skeleton);
			skeleton.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aHeadlessZombieWalksOnBlindAndThenDrops(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 1));
		try {
			RealisticDamageData data = RDAttachments.get(zombie);
			ruinHead(data);
			Decapitation.decapitate(zombie, data, new Vec3(0.0, 0.0, 1.0), null);
			helper.assertTrue(zombie.isAlive(), "the zombie dropped at once instead of walking on");
			helper.assertTrue(MobAnatomy.headless(zombie) && MobAnatomy.down(zombie), "it is not walking headless");
			helper.assertTrue(!zombie.doHurtTarget(helper.getLevel(), villager), "a headless zombie struck the villager");
			villager.discard();
			Vec3 from = zombie.position();
			int walked = 0;
			while (zombie.isAlive() && walked < 200) {
				// The level advances the clock before it ticks an entity; a test has to as well.
				zombie.tickCount++;
				zombie.tick();
				MobAnatomy.tickBody(zombie, data);
				walked++;
				helper.assertTrue(zombie.getTarget() == null, "a headless zombie found a target");
			}
			helper.assertTrue(!zombie.isAlive(), "it never dropped");
			helper.assertTrue(walked >= 55 && walked <= 105, "it walked for " + walked + " ticks, not three to five seconds");
			double moved = zombie.position().subtract(from).horizontalDistance();
			helper.assertTrue(moved > 0.5, "it stood still instead of walking: moved " + moved);
		} finally {
			clearHeads(helper, zombie);
			zombie.discard();
			villager.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theBeheadCommandTakesAHead(GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, AT);
		try {
			skeleton.setNoAi(true);
			var server = helper.getLevel().getServer();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
					"rdmg behead " + skeleton.getStringUUID());
			helper.assertTrue(!skeleton.isAlive(), "the command left the skeleton alive");
			helper.assertTrue(RDAttachments.get(skeleton).headSevered(), "the command did not take its head");
			helper.assertTrue(headsNear(helper, skeleton).size() == 1, "no head on the floor");
		} finally {
			clearHeads(helper, skeleton);
			skeleton.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anyBlowEndsAHeadlessWalk(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		try {
			RealisticDamageData data = RDAttachments.get(zombie);
			ruinHead(data);
			Decapitation.decapitate(zombie, data, Vec3.ZERO, null);
			ServerPlayer attacker = attackerFacing(helper, zombie, ItemStack.EMPTY);
			zombie.invulnerableTime = 0;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 1.0F);
			MobAnatomy.tickBody(zombie, data);
			helper.assertTrue(!zombie.isAlive(), "a headless zombie that was hit walked on");
		} finally {
			clearHeads(helper, zombie);
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aHeadIsPickedUpButNeverWorn(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		zombie.setNoAi(true);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		try {
			RealisticDamageData data = RDAttachments.get(zombie);
			ruinHead(data);
			SeveredHead head = Decapitation.sever(zombie, data, Vec3.ZERO, false, null);
			helper.assertTrue(head != null, "no head was thrown");
			head.playerTouch(player);
			helper.assertTrue(!head.isRemoved(), "the head was taken before it had landed");
			head.tickCount = 40;
			head.playerTouch(player);
			helper.assertTrue(head.isRemoved(), "walking over the head did not pick it up");
			ItemStack taken = ItemStack.EMPTY;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(Items.ZOMBIE_HEAD)) {
					taken = player.getInventory().getItem(i);
				}
			}
			helper.assertTrue(!taken.isEmpty(), "no zombie head in the inventory");
			helper.assertTrue(RDComponents.severed(taken), "the head picked up is not severed");
			helper.assertTrue(player.getEquipmentSlotForItem(taken) != EquipmentSlot.HEAD,
					"a severed head still goes on the head");
			helper.assertTrue(new ItemStack(Items.ZOMBIE_HEAD).has(DataComponents.EQUIPPABLE),
					"an ordinary zombie head was meant to be wearable");
		} finally {
			clearHeads(helper, zombie);
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aPlacedSeveredHeadComesBackSevered(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		ItemStack severed = RDComponents.sever(new ItemStack(Items.ZOMBIE_HEAD));
		helper.getLevel().setBlock(pos, Blocks.ZOMBIE_HEAD.defaultBlockState(), Block.UPDATE_ALL);
		BlockEntity skull = helper.getLevel().getBlockEntity(pos);
		helper.assertTrue(skull != null, "no skull block entity");
		skull.applyComponentsFromItemStack(severed);
		helper.assertTrue(skull.components().has(RDComponents.SEVERED), "the placed head forgot it was severed");
		List<ItemStack> drops = Block.getDrops(skull.getBlockState(), helper.getLevel(), pos, skull);
		helper.assertTrue(!drops.isEmpty(), "breaking the head dropped nothing");
		for (ItemStack drop : drops) {
			helper.assertTrue(RDComponents.severed(drop), "the head came back unsevered");
			helper.assertTrue(!drop.has(DataComponents.EQUIPPABLE), "the head came back wearable");
		}
		// An ordinary head still drops an ordinary head.
		helper.getLevel().setBlock(pos.east(), Blocks.ZOMBIE_HEAD.defaultBlockState(), Block.UPDATE_ALL);
		BlockEntity plain = helper.getLevel().getBlockEntity(pos.east());
		for (ItemStack drop : Block.getDrops(plain.getBlockState(), helper.getLevel(), pos.east(), plain)) {
			helper.assertTrue(!RDComponents.severed(drop) && drop.has(DataComponents.EQUIPPABLE),
					"an ordinary head dropped as a severed one");
		}
		helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		helper.getLevel().setBlock(pos.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		helper.succeed();
	}

	/** Ticks a wither skull until it has burnt away or dropped; the skull items it left, if any. */
	private static List<net.minecraft.world.entity.item.ItemEntity> playOut(GameTestHelper helper, SeveredHead head) {
		helper.assertTrue(head.hissing(), "a fresh wither skull is not hissing");
		helper.assertTrue(!head.trophy(), "a wither skull can be taken straight off the floor");
		int ticks = 0;
		while (!head.isRemoved() && ticks < 200) {
			head.tickCount++;
			head.tick();
			ticks++;
		}
		helper.assertTrue(head.isRemoved(), "the wither skull never burnt or dropped");
		helper.assertTrue(ticks >= Balance.WITHER_SKULL_HISS_TICKS, "it was gone before it had hissed: " + ticks);
		return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				head.getBoundingBox().inflate(4.0), item -> item.getItem().is(Items.WITHER_SKELETON_SKULL));
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aWitherSkullHissesThenBurnsOrDrops(GameTestHelper helper) {
		var skeleton = helper.spawn(EntityTypes.WITHER_SKELETON, AT);
		skeleton.setNoAi(true);
		List<net.minecraft.world.entity.item.ItemEntity> left = List.of();
		try {
			RealisticDamageData data = RDAttachments.get(skeleton);
			ruinHead(data);
			// Taken by no one: vanilla would drop no skull, and this one burns away.
			SeveredHead burns = Decapitation.sever(skeleton, data, Vec3.ZERO, false, null);
			helper.assertTrue(burns != null, "no skull was thrown");
			left = playOut(helper, burns);
			helper.assertTrue(left.isEmpty(), "a skull no player took still dropped");
			// One that comes through drops as a skull, severed, to be picked up.
			data.setHeadSevered(false);
			data.part(BodyPart.HEAD).setDestroyed(false);
			SeveredHead drops = Decapitation.sever(skeleton, data, Vec3.ZERO, false, null);
			drops.setSkullSurvives(true);
			left = playOut(helper, drops);
			helper.assertTrue(left.size() == 1, "the skull that came through dropped " + left.size() + " skulls");
			helper.assertTrue(RDComponents.severed(left.getFirst().getItem()), "the dropped skull is not severed");
			helper.assertTrue(!left.getFirst().getItem().has(DataComponents.EQUIPPABLE), "the dropped skull can be worn");
		} finally {
			for (var item : left) {
				item.discard();
			}
			clearHeads(helper, skeleton);
			skeleton.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void noTotemGivesAHeadBack(GameTestHelper helper) {
		// A player that can die: the lethal hurt reads the connection, which a bare mock has none of.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		// It logs in at the world's origin, which need not be loaded: bring it into the test.
		Vec3 inside = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.setPos(inside.x, inside.y, inside.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		RealisticDamageData data = RDAttachments.get(player);
		try {
			ruinHead(data);
			// Where they stood: a dead player may not stay there to be searched around.
			net.minecraft.world.phys.AABB around = player.getBoundingBox().inflate(10.0);
			Decapitation.decapitate(player, data, Vec3.ZERO, null);
			helper.assertTrue(player.isDeadOrDying(), "a totem kept a player alive without a head");
			// A totem that had worked would have left them alive: dying is the proof it did not.
			helper.assertTrue(data.headSevered(), "the player's head was not marked as taken");
			List<SeveredHead> heads = helper.getLevel().getEntitiesOfClass(SeveredHead.class, around,
					head -> player.getUUID().equals(head.ownerId()));
			helper.assertTrue(heads.size() == 1 && heads.getFirst().stack() != null
					&& heads.getFirst().stack().is(Items.PLAYER_HEAD)
					&& heads.getFirst().stack().has(DataComponents.PROFILE),
					"the player's head is not a player head with their face on it: " + heads.size() + " heads, "
							+ (heads.isEmpty() ? "none" : heads.getFirst().ownerType() + " " + heads.getFirst().stack()));
		} finally {
			for (SeveredHead head : helper.getLevel().getEntitiesOfClass(SeveredHead.class, player.getBoundingBox().inflate(64.0),
					head -> player.getUUID().equals(head.ownerId()))) {
				head.discard();
			}
		}
		helper.succeed();
	}
}
