package dev.captience.realisticdamage.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.entity.SeveredLimb;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The severed limb entity (1.6.2), through the production choke point.
 *
 * <p>The entity is ticked by hand where a wait would cost real seconds — a
 * minute of lifetime, a second of burning — because the assertions are about
 * counts of ticks, not about wall time, and the entity's tick is the same
 * method either way.
 */
public class SeveredLimbGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
		player.setPos(at.x, at.y, at.z);
		player.yBodyRot = 0.0F;
		return player;
	}

	/** Stone under and around the test origin, so a thrown limb has a floor to land on. */
	private static void floor(GameTestHelper helper) {
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
						Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static List<SeveredLimb> limbsNear(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(SeveredLimb.class,
				new AABB(player.position(), player.position()).inflate(12.0));
	}

	private static SeveredLimb loseLeftLeg(GameTestHelper helper, ServerPlayer player, Vec3 blow) {
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		int before = limbsNear(helper, player).size();
		DestructionLogic.destroy(player, data, BodyPart.LEFT_LEG, WoundSeverity.CRITICAL, blow);
		List<SeveredLimb> after = limbsNear(helper, player);
		helper.assertTrue(after.size() == before + 1,
				"losing one leg must leave exactly one limb, went from " + before + " to " + after.size());
		return after.getLast();
	}

	/**
	 * Ticks the limb by hand the way the level does: tickCount is advanced by
	 * {@code ServerLevel.tickNonPassenger}, not by {@code Entity.tick}, so a
	 * loop that only calls tick() has a limb that never ages — the first run of
	 * the lifetime test found it still bleeding "on tick 0" after twelve
	 * hundred of them.
	 */
	private static void tick(SeveredLimb limb, int ticks) {
		for (int i = 0; i < ticks && !limb.isRemoved(); i++) {
			limb.tickCount++;
			limb.tick();
		}
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aLostLegLeavesOneLimbBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = mockPlayer(helper);
		SeveredLimb limb = loseLeftLeg(helper, player, new Vec3(0.0, 0.0, 1.0));
		try {
			helper.assertTrue(limb.part() == BodyPart.LEFT_LEG, "it is the leg that was lost, got " + limb.part());
			helper.assertTrue(player.getUUID().equals(limb.ownerId()), "and it knows whose it is");
			Vec3 thrown = limb.getDeltaMovement();
			helper.assertTrue(thrown.z > 0.1, "thrown along the blow, got " + thrown);
			helper.assertTrue(thrown.y > 0.0, "with a lift, got " + thrown);
			double startY = limb.getY();
			// Two seconds is more than enough to arc, bounce and stop on stone.
			tick(limb, 40);
			helper.assertTrue(!limb.isRemoved(), "it must still be there two seconds in");
			helper.assertTrue(limb.onGround(), "and on the ground, at " + limb.position());
			helper.assertTrue(limb.getY() < startY, "below where it left the body");
			helper.assertTrue(limb.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4,
					"lying still rather than sliding, moving " + limb.getDeltaMovement());
			helper.assertTrue(limb.bleedRate() > 0.0, "and still bleeding");
		} finally {
			limb.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aBlastThrowsHarderAndNoDirectionDrops(GameTestHelper helper) {
		Vec3 along = new Vec3(1.0, 0.0, 0.0);
		Vec3 blow = SeveredLimb.throwVelocity(along, false, 7L);
		Vec3 blast = SeveredLimb.throwVelocity(along, true, 7L);
		Vec3 drop = SeveredLimb.throwVelocity(Vec3.ZERO, false, 7L);
		helper.assertTrue(blast.horizontalDistance() > blow.horizontalDistance() * 2.0,
				"a blast throws far harder than a blow: " + blast + " vs " + blow);
		helper.assertTrue(blow.x > 0.2 && blast.x > 0.7, "both along the blow");
		helper.assertTrue(drop.horizontalDistance() <= Balance.LIMB_THROW_SCATTER * 1.5,
				"no direction means the limb drops where it was, got " + drop);
		helper.assertTrue(Math.abs(drop.y - Balance.LIMB_DROP_LIFT) < 1.0e-9, "with only the small clearing lift");
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theThrowIsDeterministic(GameTestHelper helper) {
		Vec3 along = new Vec3(0.0, 0.0, -1.0);
		helper.assertTrue(SeveredLimb.throwVelocity(along, true, 42L).equals(SeveredLimb.throwVelocity(along, true, 42L)),
				"the same event throws the same way twice");
		helper.assertTrue(!SeveredLimb.throwVelocity(along, true, 42L).equals(SeveredLimb.throwVelocity(along, true, 43L)),
				"and two limbs from one blast do not land in the same spot");
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void fireBurnsTheLimbAway(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = mockPlayer(helper);
		SeveredLimb limb = loseLeftLeg(helper, player, Vec3.ZERO);
		try {
			limb.igniteForSeconds(30.0F);
			tick(limb, Balance.LIMB_BURN_TICKS + 5);
			helper.assertTrue(limb.isRemoved(), "a burning limb is gone inside " + Balance.LIMB_BURN_TICKS + " ticks");
		} finally {
			limb.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theLimbIsGoneAfterItsMinute(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = mockPlayer(helper);
		SeveredLimb limb = loseLeftLeg(helper, player, Vec3.ZERO);
		try {
			tick(limb, Balance.LIMB_LIFETIME_TICKS - 5);
			helper.assertTrue(!limb.isRemoved(), "still there just short of its minute");
			helper.assertTrue(limb.bleedRate() == 0.0, "but long since dry");
			tick(limb, 10);
			helper.assertTrue(limb.isRemoved(), "and gone once the minute is up");
		} finally {
			limb.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void noLimbWhenTheRuleIsOff(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean previous = config.spawnSeveredLimbs;
		ServerPlayer player = mockPlayer(helper);
		try {
			config.spawnSeveredLimbs = false;
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			int before = limbsNear(helper, player).size();
			DestructionLogic.destroy(player, data, BodyPart.RIGHT_ARM, WoundSeverity.CRITICAL, new Vec3(1.0, 0.0, 0.0));
			helper.assertTrue(data.part(BodyPart.RIGHT_ARM).destroyed(), "the arm is still lost");
			helper.assertTrue(limbsNear(helper, player).size() == before, "but nothing is thrown");
		} finally {
			config.spawnSeveredLimbs = previous;
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void coreRegionsAreNeverLimbs(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		helper.assertTrue(SeveredLimb.throwFrom(player, BodyPart.TORSO, Vec3.ZERO, false) == null,
				"a torso is not a limb");
		helper.assertTrue(SeveredLimb.throwFrom(player, BodyPart.HEAD, Vec3.ZERO, true) == null,
				"nor is a head, whatever took it");
		helper.succeed();
	}
}
