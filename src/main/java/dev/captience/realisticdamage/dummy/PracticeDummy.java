package dev.captience.realisticdamage.dummy;

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
 * A player-equivalent practice target.
 *
 * <p>It is a genuine {@link ServerPlayer} rather than a mob wearing a player
 * skin, which is the whole point: every system in the mod is typed against
 * ServerPlayer, so a real one inherits the entire body model — hit location,
 * six regions, bleeding, broken limbs, consciousness, limb destruction — with
 * no code aware that it is not a person. The alternative, generalising the
 * pipeline to LivingEntity, is the 1.4 mob-anatomy work and would have meant
 * refactoring twelve files to get one test target.
 *
 * <p>Constructed the same way the vanilla gametest harness builds its mock
 * players: an {@link EmbeddedChannel} gives the connection somewhere harmless
 * to write to, and {@code placeNewPlayer} does the rest — including setting
 * {@code connection}, which is what keeps ServerPlayer's many unguarded
 * {@code this.connection} dereferences from throwing, and broadcasting the
 * player-info entry that clients require before they will render a player
 * entity at all.
 */
public class PracticeDummy extends ServerPlayer {
	/** Swings roughly as often as a player can meaningfully attack. */
	private static final int ATTACK_INTERVAL_TICKS = 30;
	/** Vanilla melee reach, near enough. */
	private static final double ATTACK_REACH = 3.5;

	private boolean fightBack;
	private int attackCooldown;

	private PracticeDummy(MinecraftServer server, ServerLevel level, GameProfile profile) {
		super(server, level, profile, ClientInformation.createDefault());
	}

	public static PracticeDummy spawn(ServerLevel level, Vec3 pos, float yaw, String name) {
		MinecraftServer server = level.getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		PracticeDummy dummy = new PracticeDummy(server, level, profile);

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

	public static List<PracticeDummy> all(MinecraftServer server) {
		List<PracticeDummy> found = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player instanceof PracticeDummy dummy) {
				found.add(dummy);
			}
		}
		return found;
	}

	public void setFightBack(boolean value) {
		fightBack = value;
	}

	public boolean fightBack() {
		return fightBack;
	}

	/** Full health and a pristine body, without having to respawn it. */
	public void restore() {
		setHealth(getMaxHealth());
		getFoodData().setFoodLevel(20);
		RDAttachments.get(this).reset();
		RDAttachments.pushSnapshot(this);
	}

	@Override
	public void tick() {
		super.tick();
		if (!fightBack || !isAlive() || RDAttachments.get(this).unconscious()) {
			return;
		}
		if (attackCooldown > 0) {
			attackCooldown--;
			return;
		}
		ServerPlayer victim = nearestRealPlayer();
		if (victim == null) {
			return;
		}
		// Face them first: the mod reconstructs a melee hit location from the
		// attacker's look direction, so a dummy that never turns its head would
		// land every blow in the same place.
		lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, victim.getEyePosition());
		yBodyRot = getYRot();
		attack(victim);
		swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		attackCooldown = ATTACK_INTERVAL_TICKS;
	}

	private ServerPlayer nearestRealPlayer() {
		ServerPlayer best = null;
		double bestDistance = ATTACK_REACH * ATTACK_REACH;
		for (ServerPlayer candidate : level().getServer().getPlayerList().getPlayers()) {
			if (candidate instanceof PracticeDummy || candidate.isSpectator()
					|| candidate.isCreative() || !candidate.isAlive()
					|| candidate.level() != level()) {
				continue;
			}
			double distance = candidate.distanceToSqr(this);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}
}
