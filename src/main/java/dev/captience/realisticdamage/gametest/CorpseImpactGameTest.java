package dev.captience.realisticdamage.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.CorpseImpacts;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 2.0: a dead body keeps taking body damage until it comes to rest
 * ({@link CorpseImpacts}). The server follows a body that died with real
 * energy down to the ground and judges what it lands on.
 *
 * <p>Most of these drive the follower by hand ({@link #run}), so a whole fall
 * is judged inside one game tick and a server switch can be flipped and put
 * back without any other test seeing it. The first one waits on real server
 * ticks instead: it is the one that proves the follower is wired into the
 * server's own tick.
 */
public class CorpseImpactGameTest {
	private static int limbsLost(RealisticDamageData data) {
		int n = 0;
		for (BodyPart part : BodyPart.values()) {
			if (part.isLimb() && data.part(part).destroyed()) {
				n++;
			}
		}
		return n;
	}

	private static int limbsBroken(RealisticDamageData data) {
		int n = 0;
		for (BodyPart part : BodyPart.values()) {
			if (part.isLimb() && data.part(part).broken()) {
				n++;
			}
		}
		return n;
	}

	/** A floor of one block under the test, so a body has something to land on whatever the test world is. */
	private static void floor(GameTestHelper helper, Block block) {
		for (int x = -2; x <= 5; x++) {
			for (int z = -2; z <= 5; z++) {
				helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)), block.defaultBlockState());
			}
		}
	}

	/** A zombie that does nothing of its own, with its feet {@code up} blocks above the floor. */
	private static Zombie zombie(GameTestHelper helper, double up) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		zombie.setNoAi(true);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0 + up, 2.5));
		zombie.snapTo(at.x, at.y, at.z);
		// The dice a spawn rolls must not decide a test: no armour, no babies.
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			zombie.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
		}
		zombie.setBaby(false);
		return zombie;
	}

	/** Kills it with something that has no direction and no blast: only how it was moving is left to matter. */
	private static void kill(GameTestHelper helper, Zombie zombie) {
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 1000.0F);
		helper.assertTrue(!zombie.isAlive(), "the zombie is dead");
	}

	/** Runs the follower by hand for a whole hold, as the server's ticks would. */
	private static void run(GameTestHelper helper) {
		for (int i = 0; i < Balance.CORPSE_HOLD_TICKS + 5 && CorpseImpacts.following() > 0; i++) {
			CorpseImpacts.tick(helper.getLevel().getServer());
		}
	}

	private static List<SeveredLimb> limbsNear(GameTestHelper helper, Vec3 at, double within) {
		return helper.getLevel().getEntitiesOfClass(SeveredLimb.class, new AABB(at, at).inflate(within));
	}

	/**
	 * The user's own case, on the server's own ticks: killed in the air on the
	 * way down, it reaches the ground dead, and the landing takes a leg. The
	 * limb lies where the body landed, not twenty blocks up where the entity
	 * was when it died.
	 */
	@GameTest(environment = "realisticdamage:isolated", maxTicks = 200)
	public void aBodyKilledMidFallLandsHardAndLosesALimb(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		Zombie zombie = zombie(helper, 20.0);
		Vec3 ground = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		zombie.setDeltaMovement(0.0, -1.5, 0.0);
		kill(helper, zombie);
		RealisticDamageData data = RDAttachments.get(zombie);
		helper.assertTrue(CorpseImpacts.following(zombie), "a body killed falling at thirty blocks a second is followed");
		helper.assertTrue(limbsLost(data) == 0, "and nothing has happened to it yet: it is still in the air");
		helper.succeedWhen(() -> {
			helper.assertTrue(limbsLost(data) >= 1, "it has not lost a limb yet");
			helper.assertTrue(data.part(BodyPart.LEFT_LEG).destroyed() || data.part(BodyPart.RIGHT_LEG).destroyed(),
					"it came down feet first, so the first limb to go is a leg");
			helper.assertTrue(!data.part(BodyPart.HEAD).destroyed() && !data.part(BodyPart.TORSO).destroyed(),
					"limbs only: a landing never takes the head or the torso");
			if (MobAnatomy.throwsLimbs(zombie)) {
				// Wherever the body came down (the test's own roof of barriers is in
				// the way of the floor), the limb is down there with it: a long way
				// under where the entity hung when it died.
				List<SeveredLimb> limbs = limbsNear(helper, ground, 40.0);
				helper.assertTrue(!limbs.isEmpty(), "a severed limb is in the world");
				for (SeveredLimb limb : limbs) {
					helper.assertTrue(limb.getY() < zombie.getY() - 8.0,
							"the limb is where the body landed, not up where the entity died: limb at "
									+ limb.position() + ", entity at " + zombie.position());
				}
			}
			for (SeveredLimb limb : limbsNear(helper, ground, 40.0)) {
				limb.discard();
			}
			zombie.discard();
		});
	}

	/** An ordinary death where it stood: not followed at all, so nothing can happen to it. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBodyKilledWhereItStoodLosesNothing(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		Zombie zombie = zombie(helper, 0.0);
		try {
			kill(helper, zombie);
			helper.assertTrue(!CorpseImpacts.following(zombie), "a body that died standing still on the ground is not followed");
			run(helper);
			RealisticDamageData data = RDAttachments.get(zombie);
			helper.assertTrue(limbsLost(data) == 0 && limbsBroken(data) == 0,
					"and it lost " + limbsLost(data) + " limbs and broke " + limbsBroken(data));
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	/**
	 * Killed by the landing itself: on the ground already, still going down at
	 * thirty blocks a second. One leg comes off and the other breaks, which is
	 * what the bars in Balance say thirty is worth.
	 */
	@GameTest(environment = "realisticdamage:isolated")
	public void aLandingAtThirtyTakesOneLegAndBreaksTheOther(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		Zombie zombie = zombie(helper, 0.0);
		Vec3 ground = zombie.position();
		try {
			zombie.setDeltaMovement(0.0, -1.5, 0.0);
			kill(helper, zombie);
			helper.assertTrue(CorpseImpacts.following(zombie), "followed");
			run(helper);
			RealisticDamageData data = RDAttachments.get(zombie);
			boolean left = data.part(BodyPart.LEFT_LEG).destroyed();
			boolean right = data.part(BodyPart.RIGHT_LEG).destroyed();
			helper.assertTrue(limbsLost(data) == 1 && (left || right), "exactly one leg comes off, lost " + limbsLost(data));
			helper.assertTrue(data.part(left ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG).broken(), "and the other one breaks");
			BodyPart lost = left ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
			helper.assertTrue(data.wounds().stream().anyMatch(wound -> wound.part() == lost),
					"the stump is a wound, as it is for the living");
		} finally {
			for (SeveredLimb limb : limbsNear(helper, ground, 8.0)) {
				limb.discard();
			}
			zombie.discard();
		}
		helper.succeed();
	}

	/** The same landing on hay: the surface gives, as it does for the living, and the body keeps its legs. */
	@GameTest(environment = "realisticdamage:isolated")
	public void softGroundKeepsADeadBodyWhole(GameTestHelper helper) {
		floor(helper, Blocks.HAY_BLOCK);
		Zombie zombie = zombie(helper, 0.0);
		try {
			zombie.setDeltaMovement(0.0, -1.5, 0.0);
			kill(helper, zombie);
			run(helper);
			helper.assertTrue(limbsLost(RDAttachments.get(zombie)) == 0,
					"hay takes nearly half of it: no limb is lost, lost " + limbsLost(RDAttachments.get(zombie)));
		} finally {
			floor(helper, Blocks.STONE);
			zombie.discard();
		}
		helper.succeed();
	}

	/** With limb destruction off on the server nothing comes off, however hard the landing; bones still break. */
	@GameTest(environment = "realisticdamage:isolated")
	public void noLimbIsLostWhenLimbDestructionIsOff(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		ServerConfig config = ServerConfig.get();
		boolean previous = config.enableLimbDestruction;
		Zombie zombie = zombie(helper, 0.0);
		try {
			config.enableLimbDestruction = false;
			zombie.setDeltaMovement(0.0, -2.0, 0.0);
			kill(helper, zombie);
			run(helper);
			RealisticDamageData data = RDAttachments.get(zombie);
			helper.assertTrue(limbsLost(data) == 0, "the switch is off and it lost " + limbsLost(data));
			helper.assertTrue(limbsBroken(data) >= 2, "but a landing at forty still breaks both legs, broke " + limbsBroken(data));
		} finally {
			config.enableLimbDestruction = previous;
			zombie.discard();
		}
		helper.succeed();
	}

	/** The hardest lethal blast there is, on flat ground: LethalBlast takes what it takes, and the fall after adds nothing. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBlastDeathIsNoMoreViolentThanItWas(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		Zombie zombie = zombie(helper, 0.0);
		Vec3 ground = zombie.position();
		try {
			// Far enough off that the blast kills (the zombie is weakened) and leaves limbs on it to lose.
			zombie.setHealth(2.0F);
			helper.getLevel().explode(null, zombie.getX() + 3.6, zombie.getY() + 0.3, zombie.getZ(), 3.0F,
					net.minecraft.world.level.Level.ExplosionInteraction.NONE);
			helper.assertTrue(!zombie.isAlive(), "the blast kills the weakened zombie");
			RealisticDamageData data = RDAttachments.get(zombie);
			int byBlast = limbsLost(data);
			int brokenBefore = limbsBroken(data);
			run(helper);
			helper.assertTrue(limbsLost(data) == byBlast,
					"the blast took " + byBlast + " and the landing after it must take none, has now lost " + limbsLost(data));
			helper.assertTrue(limbsBroken(data) == brokenBefore, "nor break anything the blast did not");
		} finally {
			for (SeveredLimb limb : limbsNear(helper, ground, 12.0)) {
				limb.discard();
			}
			zombie.discard();
		}
		helper.succeed();
	}

	/**
	 * The test command a person tries the rule with: {@code /rdmg ragdoll drop}
	 * puts a husk in the air, kills it halfway down, and the landing takes a
	 * leg. Run as a real player, on the server's own ticks.
	 */
	@GameTest(environment = "realisticdamage:isolated", maxTicks = 300)
	public void theDropCommandKillsAHuskInTheAirAndItsLandingTakesALeg(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		Vec3 stand = helper.absoluteVec(new Vec3(2.5, 1.0, -1.5));
		// Facing along +Z, so the husk comes down six blocks further on, over the test.
		TestPlayer player = TestPlayer.spawn(helper.getLevel(), stand, 0.0F, "corpse-drop");
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player)
				.withPosition(player.position()).withSuppressedOutput(), "rdmg ragdoll drop 45");
		AABB above = new AABB(stand, stand).inflate(12.0, 60.0, 12.0);
		List<net.minecraft.world.entity.monster.zombie.Husk> husks = helper.getLevel()
				.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, above);
		helper.assertTrue(husks.size() == 1, "the command put one husk in the air, found " + husks.size());
		net.minecraft.world.entity.monster.zombie.Husk husk = husks.getFirst();
		helper.assertTrue(husk.getY() > stand.y + 40.0, "forty-five blocks up, at " + husk.position());
		helper.succeedWhen(() -> {
			helper.assertTrue(!husk.isAlive(), "the husk has not been killed yet");
			RealisticDamageData data = RDAttachments.get(husk);
			helper.assertTrue(data.part(BodyPart.LEFT_LEG).destroyed() || data.part(BodyPart.RIGHT_LEG).destroyed(),
					"killed in the air, it has not lost a leg to the landing yet");
			for (SeveredLimb limb : helper.getLevel().getEntitiesOfClass(SeveredLimb.class, above)) {
				limb.discard();
			}
			husk.discard();
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("removed"));
		});
	}

	/** The follower lets a body go once it lies still, and never holds more than its limit. */
	@GameTest(environment = "realisticdamage:isolated")
	public void bodiesAreLetGoAndTheNumberFollowedIsBounded(GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		java.util.List<Zombie> zombies = new java.util.ArrayList<>();
		try {
			// Whatever another test in this batch has in the air counts toward the
			// same limit, and is run to the end with these: nothing here lets it go early.
			for (int i = 0; i < Balance.CORPSE_IMPACT_MAX_BODIES + 3; i++) {
				Zombie zombie = zombie(helper, 0.0);
				zombies.add(zombie);
				zombie.setDeltaMovement(0.0, -1.0, 0.0);
				kill(helper, zombie);
			}
			helper.assertTrue(CorpseImpacts.following() == Balance.CORPSE_IMPACT_MAX_BODIES,
					"no more than the limit are followed at once, following " + CorpseImpacts.following());
			run(helper);
			helper.assertTrue(CorpseImpacts.following() == 0, "and every one is let go by the end of the hold");
		} finally {
			for (Zombie zombie : zombies) {
				zombie.discard();
			}
		}
		helper.succeed();
	}
}
