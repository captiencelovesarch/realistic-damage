package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.client.TriageHud;
import dev.captience.realisticdamage.dummy.PracticeDummy;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Boots a real dedicated server, connects a real client to it, and checks the
 * things that only break over a network.
 *
 * <p>Everything else in this suite runs against an integrated server, where
 * client and server share a process and a great deal can appear to work that
 * would not survive a socket. Body state reaching a remote client, a second
 * player's state reaching the first, and the 1.2 rescue view all depend on the
 * sync layer actually being right.
 */
public class MultiplayerClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				context.waitTicks(40);
				context.takeScreenshot("rd_mp_00_connected");

				// The client must be receiving its own body over the wire, not
				// falling back on a local default.
				boolean synced = context.computeOnClient(client ->
						client.player != null && client.player.getAttached(RDAttachments.SNAPSHOT) != null);
				if (!synced) {
					throw new AssertionError("no body snapshot reached the client over a real connection");
				}

				// Injure the player server-side and watch it arrive.
				server.runOnServer(minecraftServer -> {
					for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
						RealisticDamageData data = RDAttachments.get(player);
						data.part(BodyPart.LEFT_LEG).setCondition(4.0);
						data.part(BodyPart.LEFT_LEG).setBroken(true);
						data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
						data.setBlood(38.0);
						RDAttachments.pushSnapshot(player);
					}
				});
				context.waitTicks(20);
				boolean injuryArrived = context.computeOnClient(client -> {
					BodySnapshot snapshot = client.player.getAttached(RDAttachments.SNAPSHOT);
					return snapshot != null && snapshot.broken(BodyPart.LEFT_LEG)
							&& snapshot.woundMask() != 0;
				});
				if (!injuryArrived) {
					throw new AssertionError("injuries did not survive the trip to a remote client");
				}
				context.takeScreenshot("rd_mp_01_injured_remote");

				// A second player, so the parts that only exist between two people
				// can be checked: remote body state, and the treating view.
				server.runOnServer(minecraftServer -> {
					ServerPlayer real = minecraftServer.getPlayerList().getPlayers().stream()
							.filter(p -> !(p instanceof PracticeDummy)).findFirst().orElseThrow();
					Vec3 beside = real.position().add(1.0, 0.0, 0.0);
					PracticeDummy dummy = PracticeDummy.spawn(real.level(), beside, 0.0F, "Patient");
					RealisticDamageData data = RDAttachments.get(dummy);
					data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
					data.drainConsciousness(200.0);
					data.setUnconscious(true);
					RDAttachments.pushSnapshot(dummy);
				});
				context.waitTicks(40);

				// The other player's body must be readable on this client — that is
				// what the whole triage view is built on.
				boolean otherVisible = context.computeOnClient(client -> {
					for (Player other : client.level.players()) {
						if (other != client.player) {
							BodySnapshot snapshot = other.getAttached(RDAttachments.SNAPSHOT);
							if (snapshot != null && snapshot.unconscious()) {
								return true;
							}
						}
					}
					return false;
				});
				if (!otherVisible) {
					throw new AssertionError(
							"a second player's body state never reached this client");
				}
				context.takeScreenshot("rd_mp_02_second_player");

				// And the triage panel must draw once the server says who we are on.
				int patientId = context.computeOnClient(client -> {
					for (Player other : client.level.players()) {
						if (other != client.player) {
							return other.getId();
						}
					}
					return -1;
				});
				context.runOnClient(client -> TriageHud.setTarget(patientId));
				context.waitTicks(10);
				context.takeScreenshot("rd_mp_03_triage_view");
				context.runOnClient(client -> TriageHud.reset());
				context.waitTicks(5);
			}
		}
	}
}
