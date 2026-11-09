package dev.captience.realisticdamage.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.damage.Corpses;
import dev.captience.realisticdamage.damage.DeathKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /rdmg ragdoll <kind> [part] [kick]}: kills the body under the
 * crosshair (or a mannequin placed in front of you) as if by that kind of
 * death, struck on that part from where you stand. For tuning ragdolls without
 * dying for every look at one.
 *
 * <p>{@code /rdmg ragdoll shove [speed] [away|toward|left|right]}: the same
 * body becomes the stepper's test (stumbling deaths, IK phase 0): held on its
 * feet, shoved at that many blocks a second (3 if not given) that way as you
 * see it, stepping to catch itself, and dropped once it has stood still for
 * three seconds.
 *
 * <p>A mannequin that has only just been placed is killed a second later:
 * the death has to reach clients that already know the body is there.
 */
public final class RagdollTestCommand {
	private RagdollTestCommand() {}

	private static final double REACH = 24.0;
	/** Ticks between placing a mannequin and killing it. */
	private static final int PLACE_DELAY = 20;

	private record Pending(ServerLevel level, LivingEntity body, Corpses.Script script, int dueTick,
			@org.jspecify.annotations.Nullable Vec3 shove) {}

	private static final List<Pending> PENDING = new ArrayList<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}
			int now = server.getTickCount();
			PENDING.removeIf(p -> {
				if (now < p.dueTick()) {
					return false;
				}
				if (p.body().isAlive()) {
					if (p.shove() != null) {
						announceStand(p.body(), p.shove());
					}
					kill(p.level(), p.body(), p.script());
				}
				return true;
			});
		});
	}

	static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return literal("ragdoll")
				.then(literal("shove")
						.executes(ctx -> shove(ctx, 3.0F, "away"))
						.then(argument("speed", FloatArgumentType.floatArg(0.0F, 8.0F))
								.executes(ctx -> shove(ctx, FloatArgumentType.getFloat(ctx, "speed"), "away"))
								.then(argument("direction", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
												List.of("away", "toward", "left", "right"), b))
										.executes(ctx -> shove(ctx, FloatArgumentType.getFloat(ctx, "speed"),
												StringArgumentType.getString(ctx, "direction"))))))
				.then(argument("kind", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
								Arrays.stream(DeathKind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)), b))
						.executes(ctx -> run(ctx, false, false))
						.then(argument("part", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
										Arrays.stream(BodyPart.values()).map(BodyPart::id), b))
								.executes(ctx -> run(ctx, true, false))
								.then(argument("kick", FloatArgumentType.floatArg(0.0F, 2.0F))
										.executes(ctx -> run(ctx, true, true)))));
	}

	private static int run(CommandContext<CommandSourceStack> ctx, boolean withPart, boolean withKick)
			throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		String kindName = StringArgumentType.getString(ctx, "kind").toUpperCase(Locale.ROOT);
		DeathKind kind;
		try {
			kind = DeathKind.valueOf(kindName);
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("Unknown death kind '" + kindName.toLowerCase(Locale.ROOT) + "'"));
			return 0;
		}
		BodyPart part = null;
		if (withPart) {
			part = BodyPart.byId(StringArgumentType.getString(ctx, "part").toLowerCase(Locale.ROOT));
			if (part == null) {
				ctx.getSource().sendFailure(Component.literal("Unknown body part"));
				return 0;
			}
		}
		double kick = withKick ? FloatArgumentType.getFloat(ctx, "kick") : 0.3;

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
				new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity l && l.isAlive() && e != player,
				REACH * REACH);
		Corpses.Script script = new Corpses.Script(kind, part, new Vec3(look.x, 0.0, look.z), kick, 0.7);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			kill(level, target, script);
			ctx.getSource().sendSuccess(() -> Component.literal("Ragdolled " + target.getName().getString()
					+ " (" + kind.name().toLowerCase(Locale.ROOT) + ")"), false);
			return 1;
		}
		Mannequin mannequin = place(player, level);
		if (mannequin == null) {
			ctx.getSource().sendFailure(Component.literal("Could not place a mannequin"));
			return 0;
		}
		PENDING.add(new Pending(level, mannequin, script, level.getServer().getTickCount() + PLACE_DELAY, null));
		ctx.getSource().sendSuccess(() -> Component.literal("Placed a mannequin; it dies in a second ("
				+ kind.name().toLowerCase(Locale.ROOT) + ")"), false);
		return 1;
	}

	/** A mannequin three blocks in front of the player, facing them. */
	private static @org.jspecify.annotations.Nullable Mannequin place(ServerPlayer player, ServerLevel level) {
		Mannequin mannequin = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
		if (mannequin == null) {
			return null;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		Vec3 at = player.position().add(flat.scale(3.0));
		// Facing you, so a blow "from you" lands on its front.
		float yaw = player.getYRot() + 180.0F;
		mannequin.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		mannequin.setYHeadRot(yaw);
		mannequin.setYBodyRot(yaw);
		level.addFreshEntity(mannequin);
		return mannequin;
	}

	private static int shove(CommandContext<CommandSourceStack> ctx, float speed, String direction)
			throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 away = new Vec3(look.x, 0.0, look.z);
		away = away.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		// As you see it: your left is the turn of "away" a quarter anticlockwise from above.
		Vec3 dir = switch (direction.toLowerCase(Locale.ROOT)) {
			case "toward" -> away.scale(-1.0);
			case "left" -> new Vec3(away.z, 0.0, -away.x);
			case "right" -> new Vec3(-away.z, 0.0, away.x);
			case "away" -> away;
			default -> null;
		};
		if (dir == null) {
			ctx.getSource().sendFailure(Component.literal("Direction is away, toward, left or right"));
			return 0;
		}
		Vec3 shove = dir.scale(speed);
		Corpses.Script script = new Corpses.Script(DeathKind.BLOW, null, dir, 0.0, 0.0);
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(look.scale(REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
				new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity l && l.isAlive() && e != player,
				REACH * REACH);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			announceStand(target, shove);
			kill(level, target, script);
			ctx.getSource().sendSuccess(() -> Component.literal("Shoved " + target.getName().getString()
					+ String.format(Locale.ROOT, " at %.1f blocks a second", speed)), false);
			return 1;
		}
		Mannequin mannequin = place(player, level);
		if (mannequin == null) {
			ctx.getSource().sendFailure(Component.literal("Could not place a mannequin"));
			return 0;
		}
		PENDING.add(new Pending(level, mannequin, script, level.getServer().getTickCount() + PLACE_DELAY, shove));
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Placed a mannequin; in a second it is shoved %s at %.1f blocks a second", direction, speed)), false);
		return 1;
	}

	/** Tells everyone watching that this body's death is the stepper's test, before the death itself. */
	private static void announceStand(LivingEntity body, Vec3 shove) {
		var payload = new dev.captience.realisticdamage.net.StandTestPayload(body.getId(), (float) shove.x, (float) shove.z);
		for (ServerPlayer watcher : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(body)) {
			if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(watcher,
					dev.captience.realisticdamage.net.StandTestPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(watcher, payload);
			}
		}
	}

	private static void kill(ServerLevel level, LivingEntity body, Corpses.Script script) {
		Corpses.script(body, script);
		body.invulnerableTime = 0;
		body.kill(level);
	}
}
