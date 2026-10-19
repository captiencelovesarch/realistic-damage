package dev.captience.realisticdamage.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.captience.realisticdamage.RDAttachments;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A real {@link ServerPlayer} with a connection to nowhere, for gametests that
 * need a whole player rather than vanilla's mock (which has no connection and
 * NPEs on {@code hurtServer}). Test scaffolding only: the in-game practice
 * dummy it grew out of was removed in 1.7, since vanilla's own mannequin
 * ({@code minecraft:mannequin}) does that job.
 */
public class TestPlayer extends ServerPlayer {
	/** Swings roughly as often as a player can meaningfully attack. */
	private static final int ATTACK_INTERVAL_TICKS = 30;
	/** Vanilla melee reach, near enough. */
	private static final double ATTACK_REACH = 3.5;

	private boolean fightBack;
	private int attackCooldown;

	private TestPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
		super(server, level, profile, ClientInformation.createDefault());
	}

	public static TestPlayer spawn(ServerLevel level, Vec3 pos, float yaw, String name) {
		MinecraftServer server = level.getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		TestPlayer dummy = new TestPlayer(server, level, profile);

		// Fully qualified: ServerPlayer implements WaypointTransmitter, whose
		// nested Connection type shadows the import inside a subclass.
		net.minecraft.network.Connection connection =
				new net.minecraft.network.Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, dummy,
				CommonListenerCookie.createInitial(profile, false));

		// No client is ever going to report in, so waive the load grace or the
		// dummy shrugs off everything for its first three seconds.
		((dev.captience.realisticdamage.mixin.ClientLoadTimeoutAccessor) dummy.connection)
				.realisticdamage$setClientLoadedTimeout(0);
		dummy.setGameMode(GameType.SURVIVAL);
		dummy.snapTo(pos.x, pos.y, pos.z);
		dummy.setYRot(yaw);
		dummy.setYHeadRot(yaw);
		dummy.yBodyRot = yaw;
		dummy.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), yaw, 0.0F, false);
		return dummy;
	}

	public static List<TestPlayer> all(MinecraftServer server) {
		List<TestPlayer> found = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player instanceof TestPlayer dummy) {
				found.add(dummy);
			}
		}
		return found;
	}

	public void restore() {
		setHealth(getMaxHealth());
		getFoodData().setFoodLevel(20);
		RDAttachments.get(this).reset();
		RDAttachments.pushSnapshot(this);
	}
}
