package dev.captience.realisticdamage.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.StringJoiner;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Debug/admin commands (spec §54). These may expose exact hidden values —
 * they exist so balancing is not miserable.
 */
public final class RealisticDamageCommands {
	private RealisticDamageCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(tree("realisticdamage"));
			dispatcher.register(tree("rdmg"));
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
		return literal(name)
				.requires(PermissionPredicates.require(RealisticDamage.id("command/debug"), PermissionLevel.GAMEMASTERS))
				.then(literal("heal")
						.executes(ctx -> heal(ctx, ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> heal(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("info")
						.executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("setpart")
						.then(argument("player", EntityArgument.player())
								.then(partArg()
										.then(argument("value", DoubleArgumentType.doubleArg(0.0))
												.executes(RealisticDamageCommands::setPart)))))
				.then(literal("damagepart")
						.then(argument("player", EntityArgument.player())
								.then(partArg()
										.then(argument("amount", DoubleArgumentType.doubleArg(0.0))
												.executes(RealisticDamageCommands::damagePart)))))
				.then(literal("setblood")
						.then(argument("player", EntityArgument.player())
								.then(argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
										.executes(RealisticDamageCommands::setBlood))))
				.then(literal("setconsciousness")
						.then(argument("player", EntityArgument.player())
								.then(argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
										.executes(RealisticDamageCommands::setConsciousness))))
				.then(literal("break")
						.then(argument("player", EntityArgument.player())
								.then(partArg().executes(RealisticDamageCommands::breakPart))))
				.then(literal("bleed")
						.then(argument("player", EntityArgument.player())
								.then(partArg()
										.then(argument("severity", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
														Arrays.stream(WoundSeverity.values())
																.map(s -> s.name().toLowerCase(Locale.ROOT)),
														builder))
												.executes(RealisticDamageCommands::bleed)))))
				.then(literal("concussion")
						.then(argument("player", EntityArgument.player())
								.executes(RealisticDamageCommands::concussion)))
				.then(literal("burn")
						.then(argument("player", EntityArgument.player())
								.executes(RealisticDamageCommands::burn)));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> partArg() {
		return argument("part", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						Arrays.stream(BodyPart.values()).map(BodyPart::id), builder));
	}

	private static BodyPart parsePart(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "part").toLowerCase(Locale.ROOT);
		BodyPart part = BodyPart.byId(id);
		if (part == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown body part '" + id + "' (use: "
					+ String.join(", ", Arrays.stream(BodyPart.values()).map(BodyPart::id).toList()) + ")"));
		}
		return part;
	}

	private static int heal(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		RDAttachments.get(player).reset();
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Fully restored Realistic Damage state of " + player.getName().getString()), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		RealisticDamageData data = RDAttachments.get(player);
		StringJoiner out = new StringJoiner("\n");
		out.add("Realistic Damage — " + player.getName().getString());
		for (BodyPart part : BodyPart.values()) {
			BodyPartData partData = data.part(part);
			StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  %-9s %5.2f/%4.1f  %s",
					part.id(), partData.condition(), part.maxCondition(), partData.state()));
			if (partData.broken()) {
				line.append(" BROKEN");
			}
			if (partData.strainPauseTicks() > 0) {
				line.append(" strained(").append(partData.strainPauseTicks()).append("t)");
			}
			out.add(line.toString());
		}
		out.add(String.format(Locale.ROOT, "  blood: %.1f/100   consciousness: %.1f (cap %.0f)",
				data.blood(), data.consciousness(), data.consciousnessCap()));
		StringJoiner statuses = new StringJoiner(", ");
		if (data.unconscious()) {
			statuses.add("UNCONSCIOUS (" + data.unconsciousTicks() + "t)");
		}
		if (data.concussion()) {
			statuses.add("CONCUSSION");
		}
		if (data.burned()) {
			statuses.add("BURNED");
		}
		if (statuses.length() > 0) {
			out.add("  statuses: " + statuses);
		}
		if (data.bleeding()) {
			StringJoiner wounds = new StringJoiner(", ");
			for (BleedWound wound : data.wounds()) {
				wounds.add(wound.part().id() + ":" + wound.severity().name().toLowerCase(Locale.ROOT));
			}
			out.add(String.format(Locale.ROOT, "  wounds: %s  (effective %.2f blood/s)", wounds, data.effectiveBleedRate()));
		}
		ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
		return 1;
	}

	private static int setPart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BodyPart part = parsePart(ctx);
		if (part == null) {
			return 0;
		}
		double value = DoubleArgumentType.getDouble(ctx, "value");
		RDAttachments.get(player).part(part).setCondition(value);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + " " + part.id()
				+ " condition set to " + RDAttachments.get(player).part(part).condition()), true);
		return 1;
	}

	private static int damagePart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BodyPart part = parsePart(ctx);
		if (part == null) {
			return 0;
		}
		double amount = DoubleArgumentType.getDouble(ctx, "amount");
		double dealt = RDAttachments.get(player).part(part).damage(amount);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Dealt %.2f condition damage to %s of %s (now %s)", dealt, part.id(),
				player.getName().getString(), RDAttachments.get(player).part(part).state())), true);
		return 1;
	}

	private static int setBlood(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		double value = DoubleArgumentType.getDouble(ctx, "value");
		RDAttachments.get(player).setBlood(value);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + " blood set to " + value), true);
		return 1;
	}

	private static int setConsciousness(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		double value = DoubleArgumentType.getDouble(ctx, "value");
		RealisticDamageData data = RDAttachments.get(player);
		data.setConsciousness(value);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"%s consciousness set to %.1f (cap %.0f)", player.getName().getString(),
				data.consciousness(), data.consciousnessCap())), true);
		return 1;
	}

	private static int breakPart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BodyPart part = parsePart(ctx);
		if (part == null) {
			return 0;
		}
		if (!part.isLimb()) {
			ctx.getSource().sendFailure(Component.literal("Only arms and legs can be Broken in 1.0"));
			return 0;
		}
		RDAttachments.get(player).part(part).setBroken(true);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Broke " + part.id() + " of " + player.getName().getString()), true);
		return 1;
	}

	private static int bleed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BodyPart part = parsePart(ctx);
		if (part == null) {
			return 0;
		}
		String severityName = StringArgumentType.getString(ctx, "severity");
		WoundSeverity severity;
		try {
			severity = WoundSeverity.valueOf(severityName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("Unknown severity '" + severityName + "'"));
			return 0;
		}
		RealisticDamageData data = RDAttachments.get(player);
		data.addWound(part, severity);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Added %s wound to %s of %s (effective %.2f blood/s)", severity.name().toLowerCase(Locale.ROOT),
				part.id(), player.getName().getString(), data.effectiveBleedRate())), true);
		return 1;
	}

	private static int concussion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		RDAttachments.get(player).setConcussion(true);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Concussed " + player.getName().getString()), true);
		return 1;
	}

	private static int burn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		RDAttachments.get(player).setBurned(true);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Applied Burned to " + player.getName().getString()), true);
		return 1;
	}
}
