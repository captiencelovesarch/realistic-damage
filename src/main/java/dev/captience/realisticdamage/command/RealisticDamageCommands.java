package dev.captience.realisticdamage.command;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.dummy.PracticeDummy;
import dev.captience.realisticdamage.systems.CeilingMath;
import dev.captience.realisticdamage.systems.PreyMath;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
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
				.then(literal("ceiling")
						.then(argument("player", EntityArgument.player())
								.then(argument("part", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												Stream.concat(Stream.of("all"),
														Arrays.stream(BodyPart.values()).map(BodyPart::id)), builder))
										.then(argument("band", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
														Stream.of("max", "hurt", "injured", "critical"), builder))
												.executes(RealisticDamageCommands::ceiling)))))
				.then(literal("scar")
						.then(argument("player", EntityArgument.player())
								.then(partArg()
										.then(argument("level", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
														Arrays.stream(ScarLevel.values())
																.map(l -> l.name().toLowerCase(Locale.ROOT)),
														builder))
												.executes(RealisticDamageCommands::scar)))))
				.then(literal("blast")
						.then(argument("player", EntityArgument.player())
								.then(partArg()
										.executes(ctx -> blast(ctx, 3.0F))
										.then(argument("radius", FloatArgumentType.floatArg(0.5F, 16.0F))
												.executes(ctx -> blast(ctx, FloatArgumentType.getFloat(ctx, "radius")))))))
				.then(literal("preset")
						.then(literal("arcade").executes(ctx -> preset(ctx, true)))
						.then(literal("grounded").executes(ctx -> preset(ctx, false))))
				.then(configTree())
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
			// The one sanctioned exact-number channel in the mod (spec §54): the
			// panel and the HUD only ever say the band, so this is where a balancer
			// finds out what the band is actually worth in condition.
			if (partData.ceilingBand() > 0) {
				line.append(String.format(Locale.ROOT, " ceiling %s (%.2f)",
						CeilingMath.pinnedState(partData.ceilingBand()), partData.ceiling()));
			}
			// A word, not a figure — but this is the one place the history is
			// legible at all, since every player-facing surface only ever marks it.
			if (partData.scar() != ScarLevel.NONE) {
				line.append(" scar=").append(partData.scar().name().toLowerCase(Locale.ROOT));
			}
			out.add(line.toString());
		}
		out.add(String.format(Locale.ROOT, "  blood: %.1f/100   consciousness: %.1f (cap %.0f)",
				data.blood(), data.consciousness(), data.consciousnessCap()));
		// The body's own prey score, without any grudge — a grudge belongs to a
		// mob and not to a player, so no single number here could state it. Same
		// sanctioned exact-number channel as the two lines above.
		ServerConfig.PreyOnWounded prey = ServerConfig.get().preyOnWounded;
		out.add(String.format(Locale.ROOT, "  prey: %d (%s)",
				PreyMath.score(data, prey), prey.name().toLowerCase(Locale.ROOT)));
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

	/**
	 * A real blast, through the real path — {@code ServerExplosion}, the mixin,
	 * the damage hooks — nine tenths of a block outward from the limb's region
	 * centre so that limb is the one the blast is nearest. No block damage and no
	 * fire: it is for finding out what a creeper would do to this body without
	 * redecorating the base. Default radius 3, which is a creeper; 4 is TNT and 6
	 * a charged creeper or an end crystal.
	 *
	 * <p>Limbs only, deliberately, and unlike every other subcommand here this one
	 * does <em>not</em> bypass its feature's toggles — it detonates something and
	 * lets the rules decide, which is the only way it can prove the live path
	 * works. Aiming it at the head or chest would point it straight at the core
	 * finish-off path, which can kill through a totem that has just fired.
	 */
	private static int blast(CommandContext<CommandSourceStack> ctx, float radius) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BodyPart part = parsePart(ctx);
		if (part == null) {
			return 0;
		}
		if (!part.isLimb()) {
			ctx.getSource().sendFailure(Component.literal("Only arms and legs come off in a blast"));
			return 0;
		}
		Vec3 region = BodyHitResolver.regionCenter(player, part);
		Vec3 forward = Vec3.directionFromRotation(0.0F, player.yBodyRot);
		Vec3 right = forward.yRot((float) (-Math.PI / 2.0));
		Vec3 outward = part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG ? right.scale(-1.0) : right;
		Vec3 at = region.add(outward.scale(0.9));
		player.level().explode(null, at.x, at.y, at.z, radius, Level.ExplosionInteraction.NONE);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Blast r=%.1f beside %s of %s", radius, part.id(), player.getName().getString())), true);
		return 1;
	}

	/** Sets or clears a long-term ceiling. Bypasses the toggle, the way /rdmg destroy bypasses limb destruction. */
	private static int ceiling(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String partId = StringArgumentType.getString(ctx, "part").toLowerCase(Locale.ROOT);
		String word = StringArgumentType.getString(ctx, "band").toLowerCase(Locale.ROOT);
		int band = switch (word) {
			case "max" -> 0;
			case "hurt" -> 1;
			case "injured" -> 2;
			case "critical" -> 3;
			default -> -1;
		};
		if (band < 0) {
			ctx.getSource().sendFailure(Component.literal(
					"Unknown ceiling '" + word + "' (use: max, hurt, injured, critical)"));
			return 0;
		}
		List<BodyPart> parts;
		if ("all".equals(partId)) {
			parts = List.of(BodyPart.values());
		} else {
			// Reuses the existing helper, which reads the "part" argument and sends
			// the same "Unknown body part" failure every other subcommand sends.
			BodyPart part = parsePart(ctx);
			if (part == null) {
				return 0;
			}
			parts = List.of(part);
		}
		RealisticDamageData data = RDAttachments.get(player);
		for (BodyPart part : parts) {
			if (band == 0) {
				data.part(part).restoreCeiling();
			} else {
				data.part(part).lowerCeilingTo(band);   // floored per part: 'critical' on the head lands at Hurt
			}
		}
		RDAttachments.pushSnapshot(player);
		// Reports what was actually set, so a floored request (head 'critical' ->
		// Hurt) is visible rather than silently different from what was typed.
		StringJoiner out = new StringJoiner(", ");
		for (BodyPart part : parts) {
			int set = data.part(part).ceilingBand();
			out.add(part.id() + " " + (set == 0 ? "max"
					: CeilingMath.pinnedState(set).name().toLowerCase(Locale.ROOT)));
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				player.getName().getString() + " ceilings: " + out), true);
		return parts.size();
	}

	/**
	 * Writes a region's history directly, including erasing it. Cosmetic on both
	 * sides: a scar changes no outcome, so unlike every other verb here this one
	 * can only ever be worth a screenshot.
	 */
	private static int scar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BodyPart part = parsePart(ctx);
		if (part == null) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "level");
		ScarLevel level;
		try {
			level = ScarLevel.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal(
					"Unknown scar level '" + name + "' (none, broken, destroyed)"));
			return 0;
		}
		// setScar, not markScar: the debug verb is the one path allowed to lower one.
		RDAttachments.get(player).part(part).setScar(level);
		RDAttachments.pushSnapshot(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Scar on " + part.id() + " of "
				+ player.getName().getString() + " set to " + name.toLowerCase(Locale.ROOT)), true);
		return 1;
	}

	// --- /rdmg config (1.6) ---

	/**
	 * Every server setting, in a stable order. Reflection is the point: a toggle
	 * added in a later release appears in the command without anybody remembering
	 * to wire it up, which is the same argument the mod already makes for
	 * generating the internal damage tag, and the same shape vanilla uses for
	 * /gamerule.
	 *
	 * <p>The sort is not decoration. {@code Class.getFields()} is documented to
	 * hand back fields in no particular order; HotSpot happens to give declaration
	 * order today and nothing promises it tomorrow. Sorting by name makes both the
	 * printed listing and the order of the literal nodes stable by construction,
	 * and makes a seventeen-row listing scannable — which declaration order, being
	 * invisible to an operator, never was.
	 */
	private static final List<Field> SETTING_FIELDS = Arrays.stream(ServerConfig.class.getFields())
			.filter(field -> !Modifier.isStatic(field.getModifiers()))
			.sorted(Comparator.comparing(Field::getName))
			.toList();

	/** The same list the command is built from, so a test can pin the order rather than assume it. */
	public static List<Field> settingFields() {
		return SETTING_FIELDS;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> configTree() {
		LiteralArgumentBuilder<CommandSourceStack> node = literal("config")
				.executes(RealisticDamageCommands::configList);
		for (Field field : SETTING_FIELDS) {
			LiteralArgumentBuilder<CommandSourceStack> setting = literal(field.getName())
					.executes(ctx -> configQuery(ctx, field));
			Class<?> type = field.getType();
			if (type == boolean.class) {
				setting.then(argument("value", BoolArgumentType.bool())
						.executes(ctx -> configSet(ctx, field, BoolArgumentType.getBool(ctx, "value"))));
			} else if (type == double.class) {
				// Bounded above as well as below: every double here is a multiplier
				// around 1.0, and a typo'd 500 was previously accepted and written
				// straight to disk with nothing to tell an operator until the next
				// creeper. Brigadier refuses an out-of-range value at parse time.
				setting.then(argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
						.executes(ctx -> configSet(ctx, field, DoubleArgumentType.getDouble(ctx, "value"))));
			} else if (type == int.class) {
				setting.then(argument("value", IntegerArgumentType.integer(0, 1_000_000))
						.executes(ctx -> configSet(ctx, field, IntegerArgumentType.getInteger(ctx, "value"))));
			} else if (type.isEnum()) {
				setting.then(argument("value", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
								enumWords(type), builder))
						.executes(ctx -> configSetEnum(ctx, field,
								StringArgumentType.getString(ctx, "value"))));
			}
			// A field of any other type still gets its query literal — never
			// invisible — and no value node. ServerConfigFieldsTest fails the build
			// if one ever appears, so this branch is a safety net and not a plan.
			node.then(setting);
		}
		return node;
	}

	private static Stream<String> enumWords(Class<?> type) {
		return Arrays.stream(type.getEnumConstants())
				.map(constant -> ((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
	}

	/** Doubles print to two places and enums in lower case, matching what the setter accepts. */
	private static String show(Field field) {
		try {
			Object value = field.get(ServerConfig.get());
			if (value instanceof Double number) {
				return String.format(Locale.ROOT, "%.2f", number);
			}
			if (value instanceof Enum<?> constant) {
				return constant.name().toLowerCase(Locale.ROOT);
			}
			return String.valueOf(value);
		} catch (IllegalAccessException e) {
			return "?";
		}
	}

	/**
	 * Returns the field count rather than 1, so Brigadier can never read a listing
	 * as a failure — a zero result is how a command says it did nothing.
	 */
	private static int configList(CommandContext<CommandSourceStack> ctx) {
		StringJoiner out = new StringJoiner("\n");
		out.add("Realistic Damage server settings (server-wide, not per world)");
		for (Field field : SETTING_FIELDS) {
			out.add(String.format(Locale.ROOT, "  %-30s %s", field.getName(), show(field)));
		}
		ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
		return SETTING_FIELDS.size();
	}

	private static int configQuery(CommandContext<CommandSourceStack> ctx, Field field) {
		String line = field.getName() + " = " + show(field);
		ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	/** Every set saves immediately and broadcasts to ops, like /rdmg preset: this changes the server. */
	private static int configSet(CommandContext<CommandSourceStack> ctx, Field field, Object value) {
		try {
			field.set(ServerConfig.get(), value);
		} catch (IllegalAccessException e) {
			ctx.getSource().sendFailure(Component.literal("Could not set " + field.getName()));
			return 0;
		}
		ServerConfig.save();
		String line = field.getName() + " = " + show(field);
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return 1;
	}

	/** A bad word lists the legal ones and leaves the field exactly as it was. */
	private static int configSetEnum(CommandContext<CommandSourceStack> ctx, Field field, String word) {
		for (Object constant : field.getType().getEnumConstants()) {
			Enum<?> value = (Enum<?>) constant;
			if (value.name().equalsIgnoreCase(word)) {
				return configSet(ctx, field, value);
			}
		}
		ctx.getSource().sendFailure(Component.literal("Unknown value '" + word + "' for " + field.getName()
				+ " (use: " + String.join(", ", enumWords(field.getType()).toList()) + ")"));
		return 0;
	}

	private static int preset(CommandContext<CommandSourceStack> ctx, boolean arcade) {
		ServerConfig config = ServerConfig.get();
		if (arcade) {
			config.applyArcadePreset();
		} else {
			config.applyGroundedPreset();
		}
		ServerConfig.save();
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
