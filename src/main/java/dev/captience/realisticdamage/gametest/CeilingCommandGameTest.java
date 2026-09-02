package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.dummy.PracticeDummy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /rdmg ceiling} — the admin handle on long-term injuries (1.6). It
 * bypasses the toggle, the way {@code /rdmg destroy} bypasses limb destruction,
 * so a balancer can set a body up without turning the mode on for everyone.
 *
 * <p>These drive a {@link PracticeDummy} rather than a mock player because
 * {@code EntityArgument.player()} resolves names through the player list, and
 * {@code makeMockServerPlayer} never joins it — the command would fail to find
 * its target and the test would pass or fail for a reason that has nothing to do
 * with ceilings. The dummy is a real player as far as the server is concerned,
 * which is exactly why it must be disconnected again in {@code finally}.
 *
 * <p><b>Everything happens inside one synchronous method body</b> — spawn,
 * command, assert, disconnect — with no delays and no sequences. Gametests share
 * a world with roughly fifty others and the player list is global; a dummy that
 * outlives its own body is a dummy some other test can see. The
 * {@code realisticdamage:isolated} environment keeps this class in its own batch
 * for the same reason.
 */
public class CeilingCommandGameTest {
	private PracticeDummy spawnDummy(GameTestHelper helper, String name) {
		Vec3 pos = helper.absoluteVec(new Vec3(1.0, 2.0, 1.0));
		return PracticeDummy.spawn(helper.getLevel(), pos, 0.0F, name);
	}

	private void run(GameTestHelper helper, String command) {
		MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(
				server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	/** Set one part, then clear the whole body. Assertions are on state, never on feedback text. */
	@GameTest(environment = "realisticdamage:isolated")
	public void theCeilingCommandSetsAndClears(GameTestHelper helper) {
		PracticeDummy dummy = spawnDummy(helper, "CeilingSet");
		try {
			RealisticDamageData data = RDAttachments.get(dummy);
			data.reset();
			String name = dummy.getName().getString();

			run(helper, "rdmg ceiling " + name + " left_leg injured");
			helper.assertTrue(data.part(BodyPart.LEFT_LEG).ceilingBand() == 2,
					"'injured' is band 2, got " + data.part(BodyPart.LEFT_LEG).ceilingBand());
			helper.assertTrue(Math.abs(data.part(BodyPart.LEFT_LEG).condition() - 8.0) < 1.0e-9,
					"a new ceiling pulls the condition under it, got " + data.part(BodyPart.LEFT_LEG).condition());
			helper.assertTrue(data.part(BodyPart.RIGHT_LEG).ceilingBand() == 0, "and touches nothing else");

			run(helper, "rdmg ceiling " + name + " all max");
			for (BodyPart part : BodyPart.values()) {
				helper.assertTrue(data.part(part).ceilingBand() == 0,
						"'max' is the one way back (" + part.id() + ")");
			}
		} finally {
			dummy.connection.disconnect(Component.literal("removed"));
		}
		helper.succeed();
	}

	/** The per-part floor is enforced in the model, so the command cannot get under it either. */
	@GameTest(environment = "realisticdamage:isolated")
	public void theCeilingCommandHonoursTheHeadFloor(GameTestHelper helper) {
		PracticeDummy dummy = spawnDummy(helper, "CeilingFloor");
		try {
			RealisticDamageData data = RDAttachments.get(dummy);
			data.reset();

			run(helper, "rdmg ceiling " + dummy.getName().getString() + " head critical");
			helper.assertTrue(data.part(BodyPart.HEAD).ceilingBand() == 1,
					"a head can never be pinned under Hurt, whoever asks, got "
							+ data.part(BodyPart.HEAD).ceilingBand());
		} finally {
			dummy.connection.disconnect(Component.literal("removed"));
		}
		helper.succeed();
	}
}
