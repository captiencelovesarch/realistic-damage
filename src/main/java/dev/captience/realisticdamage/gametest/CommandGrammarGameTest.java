package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The one grammar of {@code /rdmg} (2.0): the verb, then who, then which
 * part, then how much; on a mob as readily as on a player. Asserted on what
 * the body is afterwards, never on what the command said.
 */
public class CommandGrammarGameTest {
	private static final BlockPos AT = new BlockPos(1, 1, 1);

	private static void run(GameTestHelper helper, String command) {
		MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	/** Whether the command tree reads the whole of this line as a command it can run. */
	private static boolean understood(GameTestHelper helper, String command) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		var parsed = server.getCommands().getDispatcher().parse(command, source);
		return !parsed.getReader().canRead() && parsed.getExceptions().isEmpty()
				&& parsed.getContext().getCommand() != null;
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void everyVerbReadsVerbWhoPartAmount(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		try {
			zombie.setNoAi(true);
			String who = zombie.getStringUUID();
			RealisticDamageData data = RDAttachments.get(zombie);

			run(helper, "rdmg set " + who + " blood 40");
			helper.assertTrue(Math.abs(data.blood() - 40.0) < 1.0e-6, "set ... blood 40 left " + data.blood());
			run(helper, "rdmg set " + who + " left_leg 1");
			helper.assertTrue(Math.abs(data.part(BodyPart.LEFT_LEG).condition() - 1.0) < 1.0e-6,
					"set ... left_leg 1 left " + data.part(BodyPart.LEFT_LEG).condition());
			run(helper, "rdmg set " + who + " blood 400");
			helper.assertTrue(Math.abs(data.blood() - 40.0) < 1.0e-6, "blood past 100 should be refused whole");

			double torso = data.part(BodyPart.TORSO).condition();
			run(helper, "rdmg damage " + who + " torso 2");
			helper.assertTrue(data.part(BodyPart.TORSO).condition() < torso, "damage ... torso 2 dealt nothing");

			run(helper, "rdmg break " + who + " left_arm");
			helper.assertTrue(data.part(BodyPart.LEFT_ARM).broken(), "break ... left_arm broke nothing");
			run(helper, "rdmg break " + who + " torso");
			helper.assertTrue(!data.part(BodyPart.TORSO).broken() && zombie.isAlive(), "a torso has nothing to break");

			run(helper, "rdmg bleed " + who + " torso severe");
			helper.assertTrue(data.bleeding(), "bleed ... torso severe opened nothing");
			run(helper, "rdmg burn " + who);
			helper.assertTrue(data.burned(), "burn did nothing");
			helper.assertTrue(dev.captience.realisticdamage.systems.MobAnatomy.isTracked(zombie),
					"a mob a command injured should start being ticked like one a blow injured");

			run(helper, "rdmg heal " + who);
			helper.assertTrue(!data.bleeding() && !data.burned() && !data.part(BodyPart.LEFT_ARM).broken()
					&& Math.abs(data.blood() - 100.0) < 1.0e-6, "heal did not restore the body");

			// The head's break is the neck: the verb that took over from breakneck.
			run(helper, "rdmg break " + who + " head");
			helper.assertTrue(!zombie.isAlive() && data.neckBroken(), "break ... head did not break the neck");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theOldSpellingsAreGoneAndTheNewOnesParse(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, AT);
		try {
			zombie.setNoAi(true);
			String who = zombie.getStringUUID();
			for (String gone : new String[] {"rdmg breakneck " + who, "rdmg setpart " + who + " head 1",
					"rdmg damagepart " + who + " head 1", "rdmg setblood " + who + " 50",
					"rdmg setconsciousness " + who + " 50"}) {
				helper.assertTrue(!understood(helper, gone), "the old spelling still runs: /" + gone);
			}
			for (String kept : new String[] {"rdmg break " + who + " head", "rdmg set " + who + " head 1",
					"rdmg set " + who + " consciousness 50", "rdmg damage " + who + " head 1",
					"rdmg destroy " + who + " left_arm", "rdmg behead " + who, "rdmg whiplash " + who + " 0.5",
					"rdmg concussion " + who + " mild", "rdmg blast " + who + " left_leg 3",
					"rdmg heal " + who, "rdmg info " + who, "rdmg bleed @e[type=zombie,limit=1] torso minor"}) {
				helper.assertTrue(understood(helper, kept), "this should be a command: /" + kept);
			}
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aThingWithNoBodyIsLeftAlone(GameTestHelper helper) {
		var stand = helper.spawn(EntityTypes.ARMOR_STAND, AT);
		try {
			run(helper, "rdmg break " + stand.getStringUUID() + " left_arm");
			run(helper, "rdmg bleed " + stand.getStringUUID() + " torso severe");
			run(helper, "rdmg heal " + stand.getStringUUID());
			helper.assertTrue(stand.isAlive(), "an armour stand should be untouched");
			helper.assertTrue(!stand.hasAttached(RDAttachments.DATA), "and should not have been given a body");
		} finally {
			stand.discard();
		}
		helper.succeed();
	}
}
