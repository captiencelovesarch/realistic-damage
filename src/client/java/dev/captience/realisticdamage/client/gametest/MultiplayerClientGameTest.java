package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
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

				// Injure the player server-side and watch it arrive. One mutation
				// touching several fields at once on purpose: this is the only
				// assertion in the suite that crosses a real PacketDecoder, and
				// therefore the only place a mismatch between what the snapshot
				// codec writes and what it reads shows up as anything other than a
				// silently wrong number. The decoder's "bytes left over" check is
				// what catches it, so the more of the record carries a distinctive
				// value here the more of it that check is actually guarding.
				server.runOnServer(minecraftServer -> {
					for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
						RealisticDamageData data = RDAttachments.get(player);
						data.part(BodyPart.LEFT_LEG).setCondition(4.0);
						data.part(BodyPart.LEFT_LEG).setBroken(true);
						data.part(BodyPart.LEFT_LEG).lowerCeilingTo(2);
						data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
						data.setBlood(38.0);
						RDAttachments.pushSnapshot(player);
					}
				});
				context.waitTicks(20);
				boolean injuryArrived = context.computeOnClient(client -> {
					BodySnapshot snapshot = client.player.getAttached(RDAttachments.SNAPSHOT);
					return snapshot != null && snapshot.broken(BodyPart.LEFT_LEG)
							&& snapshot.woundMask() != 0
							&& snapshot.ceilingBand(BodyPart.LEFT_LEG) == 2
							&& snapshot.scar(BodyPart.LEFT_LEG) == ScarLevel.BROKEN;
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

				// Two scarred bodies in one frame. This one cannot assert — there
				// is no counter that can tell whose seam landed on whom — and it
				// is here precisely because of that: the layer is a single
				// instance shared by every player the renderer draws, and until
				// 1.6 it posed its own meshes in place, so with two visible
				// players each drew the other's limb angles. The pose now goes on
				// the matrix stack, which is copied per submit. The fix and the
				// picture that would show it failing ship together.
				server.runOnServer(minecraftServer -> {
					for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
						RealisticDamageData data = RDAttachments.get(player);
						data.part(BodyPart.RIGHT_ARM).setScar(ScarLevel.DESTROYED);
						data.part(BodyPart.LEFT_LEG).setScar(ScarLevel.BROKEN);
						RDAttachments.pushSnapshot(player);
					}
				});
				context.runOnClient(client ->
						client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
				context.waitTicks(30);
				context.takeScreenshot("rd_mp_02_two_scarred");
				context.runOnClient(client ->
						client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
				context.waitTicks(5);

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
