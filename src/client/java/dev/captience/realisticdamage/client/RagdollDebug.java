package dev.captience.realisticdamage.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Ragdoll tuning tools (active ragdolls phase 0), client side:
 * {@code /rdragdoll debug} draws every ragdoll's physics boxes over the body
 * (grey once it has gone to sleep), {@code /rdragdoll slowmo <0.05..1>} slows
 * the ragdoll clock, and {@code /rdragdoll status} says how many there are.
 * The server half, killing a body to order, is {@code /rdmg ragdoll}.
 */
public final class RagdollDebug {
	private RagdollDebug() {}

	static boolean enabled;

	/** Lines emitted since start, for the client gametest (screenshots cannot show gizmos). */
	private static long lines;

	public static long lines() {
		return lines;
	}

	/** Edge colours by part, head to left leg. */
	private static final int[] COLOURS = {
			0xFFFFD84A, 0xFFFFFFFF, 0xFFFF5A5A, 0xFF5AA0FF, 0xFFFF9F40, 0xFF40D0FF,
	};
	private static final int ASLEEP = 0xFF808080;

	/** Corner index pairs of a box's twelve edges. */
	private static final int[] EDGES = {0, 1, 1, 3, 3, 2, 2, 0, 4, 5, 5, 7, 7, 6, 6, 4, 0, 4, 1, 5, 2, 6, 3, 7};

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("rdragdoll")
						.then(literal("debug").executes(ctx -> {
							enabled = !enabled;
							ctx.getSource().sendFeedback(Component.literal("Ragdoll boxes " + (enabled ? "on" : "off")));
							return 1;
						}))
						.then(literal("slowmo")
								.executes(ctx -> {
									RemoteCorpses.timeScale = 1.0;
									ctx.getSource().sendFeedback(Component.literal("Ragdolls at real time"));
									return 1;
								})
								.then(argument("speed", FloatArgumentType.floatArg(0.05F, 1.0F)).executes(ctx -> {
									RemoteCorpses.timeScale = FloatArgumentType.getFloat(ctx, "speed");
									ctx.getSource().sendFeedback(Component.literal("Ragdolls at " + RemoteCorpses.timeScale + "x"));
									return 1;
								})))
						.then(literal("status").executes(ctx -> {
							int asleep = 0;
							int all = 0;
							for (SimulatedCorpse r : RemoteCorpses.ragdolls()) {
								all++;
								if (r.asleep()) {
									asleep++;
								}
							}
							ctx.getSource().sendFeedback(Component.literal(all + " ragdolls (" + asleep + " at rest), "
									+ (RemoteCorpses.count() - all) + " simple bodies, clock " + RemoteCorpses.timeScale + "x"));
							return 1;
						}))));
	}

	/** From the end of the debug renderer's gizmo pass, every frame. */
	public static void emit(float partialTicks) {
		if (!enabled) {
			return;
		}
		Quaterniond q = new Quaterniond();
		Vector3d c = new Vector3d();
		Vector3d corner = new Vector3d();
		Vec3[] corners = new Vec3[8];
		for (SimulatedCorpse ragdoll : RemoteCorpses.ragdolls()) {
			boolean asleep = ragdoll.asleep();
			int index = 0;
			for (Body b : ragdoll.bodies()) {
				q.set(b.tickQ).slerp(b.q, partialTicks);
				c.set(b.tickX).lerp(b.x, partialTicks);
				for (int k = 0; k < 8; k++) {
					corner.set((k & 1) == 0 ? -b.hx : b.hx, (k & 2) == 0 ? -b.hy : b.hy, (k & 4) == 0 ? -b.hz : b.hz);
					q.transform(corner).add(c);
					corners[k] = new Vec3(corner.x, corner.y, corner.z);
				}
				int colour = asleep ? ASLEEP : COLOURS[index++ % COLOURS.length];
				for (int e = 0; e < EDGES.length; e += 2) {
					Gizmos.line(corners[EDGES[e]], corners[EDGES[e + 1]], colour, 2.0F).setAlwaysOnTop();
					lines++;
				}
			}
		}
	}
}
