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
 * <p>{@code /rdmg ragdoll <kind> <part> <kick> walking|sprinting
 * [toward|away|left|right]}: the same, of a body that was going somewhere
 * (stumbling deaths, IK phase 1): a placed mannequin faces that way as you
 * see it (toward you if not given) and dies as if walking or sprinting there;
 * a body under the crosshair dies as if going the way it faces.
 *
 * <p>{@code /rdmg ragdoll shove [speed] [away|toward|left|right] [seconds]}:
 * the same body becomes the stepper's test (stumbling deaths, IK phase 0):
 * on its feet, shoved at that many blocks a second (3 if not given) that way
 * as you see it, and stumbling after its balance for that many seconds at
 * most (1.6 if not given) before it goes down. With 0 seconds it does not
 * weaken: it catches itself and is dropped once it has stood still.
 *
 * <p>{@code /rdmg ragdoll drop [height]}: a husk is dropped from that many
 * blocks up (30 if not given) a little in front of you, and killed in the air
 * halfway down (2.0: a dead body keeps taking body damage until it comes to
 * rest). It reaches the ground dead; from high enough the landing takes a
 * leg. A husk and not a mannequin, because a husk's lost limb is one the
 * client can draw lying on the ground.
 *
 * <p>A mannequin that has only just been placed is killed a second later:
 * the death has to reach clients that already know the body is there.
 */
public final class RagdollTestCommand {
	private RagdollTestCommand() {}

	private static final double REACH = 24.0;
	/** How long the stepper's test body has on its feet when the command does not say. */
	private static final float ON_FEET = (float) dev.captience.realisticdamage.ragdoll.Stand.ON_FEET;
	/** Ticks between placing a mannequin and killing it. */
	private static final int PLACE_DELAY = 20;

	/** How fast a player walks and sprints, blocks a second. */
	private static final double WALKING = 4.317;
	private static final double SPRINTING = 5.612;

	private record Pending(ServerLevel level, LivingEntity body, Corpses.Script script, int dueTick,
			@org.jspecify.annotations.Nullable Vec3 shove, float onFeet, @org.jspecify.annotations.Nullable Vec3 moving) {}

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
						announceStand(p.body(), p.shove(), p.onFeet());
					}
					if (p.moving() != null) {
						announceMoving(p.body(), p.moving());
					}
					kill(p.level(), p.body(), p.script());
				}
				return true;
			});
		});
	}

	static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return literal("ragdoll")
				.then(literal("drop")
						.executes(ctx -> drop(ctx, DROP_HEIGHT))
						.then(argument("height", FloatArgumentType.floatArg(6.0F, 200.0F))
								.executes(ctx -> drop(ctx, FloatArgumentType.getFloat(ctx, "height")))))
				.then(literal("shove")
						.executes(ctx -> shove(ctx, 3.0F, "away", ON_FEET))
						.then(argument("speed", FloatArgumentType.floatArg(0.0F, 8.0F))
								.executes(ctx -> shove(ctx, FloatArgumentType.getFloat(ctx, "speed"), "away", ON_FEET))
								.then(argument("direction", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
												List.of("away", "toward", "left", "right"), b))
										.executes(ctx -> shove(ctx, FloatArgumentType.getFloat(ctx, "speed"),
												StringArgumentType.getString(ctx, "direction"), ON_FEET))
										.then(argument("seconds", FloatArgumentType.floatArg(0.0F, 8.0F))
												.executes(ctx -> shove(ctx, FloatArgumentType.getFloat(ctx, "speed"),
														StringArgumentType.getString(ctx, "direction"),
														FloatArgumentType.getFloat(ctx, "seconds")))))))
				.then(argument("kind", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
								Arrays.stream(DeathKind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)), b))
						.executes(ctx -> run(ctx, false, false, null, null))
						.then(argument("part", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
										Arrays.stream(BodyPart.values()).map(BodyPart::id), b))
								.executes(ctx -> run(ctx, true, false, null, null))
								.then(argument("kick", FloatArgumentType.floatArg(0.0F, 2.0F))
										.executes(ctx -> run(ctx, true, true, null, null))
										.then(argument("gait", StringArgumentType.word())
												.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
														List.of("walking", "sprinting"), b))
												.executes(ctx -> run(ctx, true, true,
														StringArgumentType.getString(ctx, "gait"), "toward"))
												.then(argument("direction", StringArgumentType.word())
														.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
																List.of("toward", "away", "left", "right"), b))
														.executes(ctx -> run(ctx, true, true,
																StringArgumentType.getString(ctx, "gait"),
																StringArgumentType.getString(ctx, "direction"))))))));
	}

	/** A direction as the player sees it, along the ground, or null for a word that is not one. */
	private static @org.jspecify.annotations.Nullable Vec3 direction(Vec3 look, String word) {
		Vec3 away = new Vec3(look.x, 0.0, look.z);
		away = away.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		// Your left is the turn of "away" a quarter anticlockwise from above.
		return switch (word.toLowerCase(Locale.ROOT)) {
			case "toward" -> away.scale(-1.0);
			case "left" -> new Vec3(away.z, 0.0, -away.x);
			case "right" -> new Vec3(-away.z, 0.0, away.x);
			case "away" -> away;
			default -> null;
		};
	}

	private static int run(CommandContext<CommandSourceStack> ctx, boolean withPart, boolean withKick,
			@org.jspecify.annotations.Nullable String gait, @org.jspecify.annotations.Nullable String direction)
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
		double speed = 0.0;
		Vec3 going = null;
		if (gait != null) {
			speed = switch (gait.toLowerCase(Locale.ROOT)) {
				case "walking" -> WALKING;
				case "sprinting" -> SPRINTING;
				default -> 0.0;
			};
			going = direction(look, direction == null ? "toward" : direction);
			if (speed == 0.0 || going == null) {
				ctx.getSource().sendFailure(Component.literal(
						"After the kick: walking or sprinting, then toward, away, left or right"));
				return 0;
			}
		}
		Vec3 end = eye.add(look.scale(REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
				new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity l && l.isAlive() && e != player,
				REACH * REACH);
		Corpses.Script script = new Corpses.Script(kind, part, new Vec3(look.x, 0.0, look.z), kick, 0.7);
		String how = gait == null ? "" : ", " + gait.toLowerCase(Locale.ROOT);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			if (going != null) {
				// A body that is already there goes the way it faces.
				double yaw = Math.toRadians(target.yBodyRot);
				announceMoving(target, new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).scale(speed));
			}
			kill(level, target, script);
			ctx.getSource().sendSuccess(() -> Component.literal("Ragdolled " + target.getName().getString()
					+ " (" + kind.name().toLowerCase(Locale.ROOT) + how + ")"), false);
			return 1;
		}
		Mannequin mannequin = place(player, level, going);
		if (mannequin == null) {
			ctx.getSource().sendFailure(Component.literal("Could not place a mannequin"));
			return 0;
		}
		PENDING.add(new Pending(level, mannequin, script, level.getServer().getTickCount() + PLACE_DELAY, null, 0.0F,
				going == null ? null : going.scale(speed)));
		ctx.getSource().sendSuccess(() -> Component.literal("Placed a mannequin; it dies in a second ("
				+ kind.name().toLowerCase(Locale.ROOT) + how + ")"), false);
		return 1;
	}

	/**
	 * A mannequin three blocks in front of the player, facing them, or facing
	 * the way it is to be going.
	 */
	private static @org.jspecify.annotations.Nullable Mannequin place(ServerPlayer player, ServerLevel level,
			@org.jspecify.annotations.Nullable Vec3 going) {
		Mannequin mannequin = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
		if (mannequin == null) {
			return null;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		Vec3 at = player.position().add(flat.scale(3.0));
		// Facing you, so a blow "from you" lands on its front.
		float yaw = going == null ? player.getYRot() + 180.0F
				: (float) Math.toDegrees(Math.atan2(-going.x, going.z));
		mannequin.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		mannequin.setYHeadRot(yaw);
		mannequin.setYBodyRot(yaw);
		level.addFreshEntity(mannequin);
		return mannequin;
	}

	/** How far up the drop test's body starts when the command does not say, blocks. */
	private static final float DROP_HEIGHT = 30.0F;
	/** How far in front of the player it comes down, blocks. */
	private static final double DROP_AHEAD = 6.0;

	private static int drop(CommandContext<CommandSourceStack> ctx, float height) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		net.minecraft.world.entity.monster.zombie.Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		if (husk == null) {
			ctx.getSource().sendFailure(Component.literal("Could not place a body"));
			return 0;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 at = player.position().add(flat.scale(DROP_AHEAD)).add(0.0, height, 0.0);
		float yaw = player.getYRot() + 180.0F;
		husk.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		husk.setYHeadRot(yaw);
		husk.setYBodyRot(yaw);
		husk.setBaby(false);
		level.addFreshEntity(husk);
		// Killed once it has fallen half the way, by vanilla's own fall (0.08 a
		// tick squared, 2% drag): never sooner than clients can have met it.
		int ticks = 0;
		double speed = 0.0;
		for (double fallen = 0.0; fallen < height * 0.5; ticks++) {
			speed = (speed + 0.08) * 0.98;
			fallen += speed;
		}
		int wait = Math.max(5, ticks);
		// No blow, no direction: all that is left to throw it is the fall it is in.
		Corpses.Script script = new Corpses.Script(DeathKind.BLOW, null, Vec3.ZERO, 0.0, 0.5);
		PENDING.add(new Pending(level, husk, script, level.getServer().getTickCount() + wait, null, 0.0F, null));
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Dropped a husk from %.0f blocks; it dies in the air after %.1f seconds", height, wait / 20.0)), false);
		return 1;
	}

	private static int shove(CommandContext<CommandSourceStack> ctx, float speed, String direction, float onFeet)
			throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 dir = direction(look, direction);
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
			announceStand(target, shove, onFeet);
			kill(level, target, script);
			ctx.getSource().sendSuccess(() -> Component.literal("Shoved " + target.getName().getString()
					+ String.format(Locale.ROOT, " at %.1f blocks a second, ", speed) + onFeet(onFeet)), false);
			return 1;
		}
		Mannequin mannequin = place(player, level, null);
		if (mannequin == null) {
			ctx.getSource().sendFailure(Component.literal("Could not place a mannequin"));
			return 0;
		}
		PENDING.add(new Pending(level, mannequin, script, level.getServer().getTickCount() + PLACE_DELAY, shove,
				onFeet, null));
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Placed a mannequin; in a second it is shoved %s at %.1f blocks a second, ", direction, speed)
				+ onFeet(onFeet)), false);
		return 1;
	}

	private static String onFeet(float seconds) {
		return seconds <= 0.0F ? "at full strength until it stands still"
				: String.format(Locale.ROOT, "%.1f seconds on its feet at most", seconds);
	}

	/** Tells everyone watching that this body's death is the stepper's test, before the death itself. */
	private static void announceStand(LivingEntity body, Vec3 shove, float onFeet) {
		var payload = new dev.captience.realisticdamage.net.StandTestPayload(body.getId(), (float) shove.x,
				(float) shove.z, onFeet);
		for (ServerPlayer watcher : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(body)) {
			if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(watcher,
					dev.captience.realisticdamage.net.StandTestPayload.TYPE)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(watcher, payload);
			}
		}
	}

	/** Tells everyone watching that this body's death is of a body going somewhere, before the death itself. */
	private static void announceMoving(LivingEntity body, Vec3 moving) {
		var payload = new dev.captience.realisticdamage.net.MovingStartPayload(body.getId(), (float) moving.x,
				(float) moving.z);
		for (ServerPlayer watcher : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(body)) {
			if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(watcher,
					dev.captience.realisticdamage.net.MovingStartPayload.TYPE)) {
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
