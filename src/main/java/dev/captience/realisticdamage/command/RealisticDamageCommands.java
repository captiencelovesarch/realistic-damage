package dev.captience.realisticdamage.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import com.mojang.brigadier.arguments.BoolArgumentType;
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
import dev.captience.realisticdamage.dummy.PracticeDummy;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

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
				.then(literal("destroy")
						.then(argument("player", EntityArgument.player())
								.then(partArg().executes(RealisticDamageCommands::destroyPart))))
				.then(literal("preset")
						.then(literal("arcade").executes(ctx -> preset(ctx, true)))
						.then(literal("grounded").executes(ctx -> preset(ctx, false))))
				.then(literal("burn")
						.then(argument("player", EntityArgument.player())
								.executes(RealisticDamageCommands::burn)))
				.then(literal("dummy")
						.then(literal("spawn").executes(RealisticDamageCommands::dummySpawn))
						.then(literal("remove").executes(RealisticDamageCommands::dummyRemove))
						.then(literal("heal").executes(RealisticDamageCommands::dummyHeal))
						.then(literal("equip").executes(RealisticDamageCommands::dummyEquip))
						.then(literal("fightback")
								.then(argument("enabled", BoolArgumentType.bool())
										.executes(RealisticDamageCommands::dummyFightBack))));
	}

	// --- Practice dummy (1.2) ---

	private static int dummySpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer source = ctx.getSource().getPlayerOrException();
		// Two blocks in front, facing back at you — where you would stand to spar.
		Vec3 ahead = source.position().add(source.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(2.0));
		PracticeDummy dummy = PracticeDummy.spawn(source.level(), ahead,
				net.minecraft.util.Mth.wrapDegrees(source.getYRot() + 180.0F),
				"Dummy" + (PracticeDummy.all(ctx.getSource().getServer()).size() + 1));
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Spawned " + dummy.getName().getString() + " — a real player as far as the mod is concerned"), true);
		return 1;
	}

	private static int dummyRemove(CommandContext<CommandSourceStack> ctx) {
		int removed = 0;
		for (PracticeDummy dummy : PracticeDummy.all(ctx.getSource().getServer())) {
			dummy.connection.disconnect(Component.literal("removed"));
			removed++;
		}
		int count = removed;
		ctx.getSource().sendSuccess(() -> Component.literal("Removed " + count + " dummy(s)"), true);
		return count;
	}

	private static int dummyHeal(CommandContext<CommandSourceStack> ctx) {
		List<PracticeDummy> dummies = PracticeDummy.all(ctx.getSource().getServer());
		dummies.forEach(PracticeDummy::restore);
		ctx.getSource().sendSuccess(() -> Component.literal("Restored " + dummies.size() + " dummy(s)"), false);
		return dummies.size();
	}

	/** Dresses every dummy in whatever the caller is holding and wearing. */
	private static int dummyEquip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer source = ctx.getSource().getPlayerOrException();
		List<PracticeDummy> dummies = PracticeDummy.all(ctx.getSource().getServer());
		for (PracticeDummy dummy : dummies) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				dummy.setItemSlot(slot, source.getItemBySlot(slot).copy());
			}
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Copied your gear onto " + dummies.size() + " dummy(s)"), false);
		return dummies.size();
	}

	private static int dummyFightBack(CommandContext<CommandSourceStack> ctx) {
		boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
		List<PracticeDummy> dummies = PracticeDummy.all(ctx.getSource().getServer());
		dummies.forEach(dummy -> dummy.setFightBack(enabled));
		ctx.getSource().sendSuccess(() -> Component.literal(
				(enabled ? "Dummies will swing back" : "Dummies will stand still")
						+ " (" + dummies.size() + ")"), true);
		return dummies.size();
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
			statuses.add(String.format(Locale.ROOT, "CONCUSSION (stable %d/%ds)",
					data.concussionStableTicks() / 20,
					dev.captience.realisticdamage.config.Balance.CONCUSSION_STABLE_TICKS / 20));
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

	private static int destroyPart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BodyPart part = parsePart(ctx);
		if (part == null) {
			return 0;
		}
		dev.captience.realisticdamage.damage.DestructionLogic.destroy(player, RDAttachments.get(player), part);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Destroyed " + part.id() + " of " + player.getName().getString()), true);
		return 1;
	}

	private static int preset(CommandContext<CommandSourceStack> ctx, boolean arcade) {
		dev.captience.realisticdamage.config.ServerConfig config =
				dev.captience.realisticdamage.config.ServerConfig.get();
		if (arcade) {
			config.applyArcadePreset();
		} else {
			config.applyGroundedPreset();
		}
		dev.captience.realisticdamage.config.ServerConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal("Realistic Damage preset: "
				+ (arcade ? "Arcade (limb destruction ON)" : "Grounded (limb destruction OFF)")), true);
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
