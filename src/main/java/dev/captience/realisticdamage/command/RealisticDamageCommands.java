package dev.captience.realisticdamage.command;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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
import dev.captience.realisticdamage.systems.CeilingMath;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.systems.PreyMath;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
		RagdollTestCommand.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(tree("realisticdamage"));
			dispatcher.register(tree("rdmg"));
		});
	}

	/**
	 * One grammar for every verb that touches a body (2.0): the verb, then who,
	 * then which part, then how much. {@code /rdmg break captience head},
	 * {@code /rdmg set captience blood 40}, {@code /rdmg bleed @e[type=zombie] torso severe}.
	 * "Who" is any selector: players always, and every mob the mod keeps a body
	 * for. The glued-together verbs this replaced (breakneck, setpart,
	 * damagepart, setblood, setconsciousness) are gone rather than kept as
	 * aliases, because a second spelling in the tab list is the clutter the
	 * rewrite was for.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
		return literal(name)
				.requires(PermissionPredicates.require(RealisticDamage.id("command/debug"), PermissionLevel.GAMEMASTERS))
				.then(literal("heal")
						.executes(ctx -> heal(ctx, self(ctx)))
						.then(targets().executes(ctx -> heal(ctx, bodies(ctx)))))
				.then(literal("info")
						.executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
						.then(argument("target", EntityArgument.entity())
								.executes(ctx -> info(ctx, body(ctx)))))
				.then(literal("set")
						.then(targets()
								.then(wordArg("what", Stream.concat(Arrays.stream(BodyPart.values()).map(BodyPart::id),
										Stream.of("blood", "consciousness")).toList())
										.then(argument("value", DoubleArgumentType.doubleArg(0.0))
												.executes(RealisticDamageCommands::set)))))
				.then(literal("damage")
						.then(targets()
								.then(partArg()
										.then(argument("amount", DoubleArgumentType.doubleArg(0.0))
												.executes(RealisticDamageCommands::damagePart)))))
				// Arms and legs fracture; the head's break is the neck (2.0).
				.then(literal("break")
						.then(targets()
								.then(wordArg("part", Arrays.stream(BodyPart.values())
										.filter(part -> part != BodyPart.TORSO).map(BodyPart::id).toList())
										.executes(RealisticDamageCommands::breakPart))))
				.then(literal("destroy")
						.then(targets()
								.then(partArg().executes(RealisticDamageCommands::destroyPart))))
				// 2.0: takes the head off a player or any mob with a body, for testing.
				.then(literal("behead")
						.executes(ctx -> behead(ctx, self(ctx)))
						.then(targets().executes(ctx -> behead(ctx, EntityArgument.getEntities(ctx, "targets")))))
				.then(literal("bleed")
						.then(targets()
								.then(partArg()
										.then(wordArg("severity", Arrays.stream(WoundSeverity.values())
												.map(s -> s.name().toLowerCase(Locale.ROOT)).toList())
												.executes(RealisticDamageCommands::bleed)))))
				.then(literal("whiplash")
						.executes(ctx -> whiplash(ctx, self(ctx), 1.0))
						.then(targets()
								.executes(ctx -> whiplash(ctx, EntityArgument.getEntities(ctx, "targets"), 1.0))
								.then(argument("severity", DoubleArgumentType.doubleArg(0.0, 1.0))
										.executes(ctx -> whiplash(ctx, EntityArgument.getEntities(ctx, "targets"),
												DoubleArgumentType.getDouble(ctx, "severity"))))))
				.then(literal("concussion")
						.then(targets()
								.executes(RealisticDamageCommands::concussion)
								.then(wordArg("grade", List.of("mild", "moderate", "severe", "none"))
										.executes(RealisticDamageCommands::concussion))))
				.then(literal("burn")
						.then(targets().executes(RealisticDamageCommands::burn)))
				.then(literal("blast")
						.then(targets()
								.then(wordArg("part", Arrays.stream(BodyPart.values())
										.filter(BodyPart::isLimb).map(BodyPart::id).toList())
										.executes(ctx -> blast(ctx, 3.0F))
										.then(argument("radius", FloatArgumentType.floatArg(0.5F, 16.0F))
												.executes(ctx -> blast(ctx, FloatArgumentType.getFloat(ctx, "radius")))))))
				// History belongs to players: a mob has one life and no record of it.
				.then(literal("ceiling")
						.then(argument("targets", EntityArgument.players())
								.then(wordArg("part", Stream.concat(Stream.of("all"),
										Arrays.stream(BodyPart.values()).map(BodyPart::id)).toList())
										.then(wordArg("band", List.of("max", "hurt", "injured", "critical"))
												.executes(RealisticDamageCommands::ceiling)))))
				.then(literal("scar")
						.then(argument("targets", EntityArgument.players())
								.then(partArg()
										.then(wordArg("level", Arrays.stream(ScarLevel.values())
												.map(l -> l.name().toLowerCase(Locale.ROOT)).toList())
												.executes(RealisticDamageCommands::scar)))))
				.then(literal("preset")
						.then(literal("arcade").executes(ctx -> preset(ctx, true)))
						.then(literal("grounded").executes(ctx -> preset(ctx, false))))
				.then(configTree())
				.then(soundTree())
				.then(RagdollTestCommand.tree());

	}

	private static RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector> targets() {
		return argument("targets", EntityArgument.entities());
	}

	/** Whoever ran the command, for the verbs that default to yourself. */
	private static List<LivingEntity> self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return List.of(ctx.getSource().getPlayerOrException());
	}

	/** Whether the mod keeps a body for this: every player, and every mob with anatomy. */
	private static boolean hasBody(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive()
				&& (living instanceof ServerPlayer || MobAnatomy.isMobWithBody(living));
	}

	/**
	 * The bodies among the targets. A selector that matched only things without
	 * one (an item, a boat, a mob outside the anatomy tag) is a failure with
	 * its own sentence, so "nothing happened" is never the whole answer.
	 */
	private static List<LivingEntity> bodies(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<LivingEntity> out = new ArrayList<>();
		for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
			if (hasBody(entity)) {
				out.add((LivingEntity) entity);
			}
		}
		if (out.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal(
					"None of those has a body: it takes a player, or a mob Realistic Damage gives anatomy to"));
		}
		return out;
	}

	private static LivingEntity body(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Entity entity = EntityArgument.getEntity(ctx, "target");
		if (!hasBody(entity)) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal(
					"That has no body: it takes a player, or a mob Realistic Damage gives anatomy to")).create();
		}
		return (LivingEntity) entity;
	}

	/** Publishes a changed body, and starts a mob's body ticking if it was not. */
	private static void changed(LivingEntity body) {
		MobAnatomy.track(body);
		RDAttachments.pushSnapshot(body);
	}

	/** "captience", or "3 bodies" when the selector caught several. */
	private static String who(List<LivingEntity> bodies) {
		return bodies.size() == 1 ? bodies.get(0).getName().getString() : bodies.size() + " bodies";
	}

	/** "left leg" for {@code left_leg}: what a sentence calls the part. */
	private static String said(BodyPart part) {
		return part.id().replace('_', ' ');
	}

	/**
	 * {@code sound <event> [mine|arcade|grounded] [strength] [self|far] [flesh|dry]}
	 * (2.0 audio): plays one injury sound to whoever ran it. Every argument
	 * after the event is optional and they are read in order.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> soundTree() {
		return literal("sound")
				.then(wordArg("event", Arrays.stream(dev.captience.realisticdamage.net.InjurySound.values())
						.filter(sound -> !sound.isScream())
						.map(dev.captience.realisticdamage.net.InjurySound::id).toList())
						.executes(RealisticDamageCommands::sound)
						.then(wordArg("set", List.of("mine", "arcade", "grounded"))
								.executes(RealisticDamageCommands::sound)
								.then(argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
										.executes(RealisticDamageCommands::sound)
										.then(wordArg("from", List.of("self", "far"))
												.executes(RealisticDamageCommands::sound)
												.then(wordArg("material", List.of("flesh", "dry"))
														.executes(RealisticDamageCommands::sound))))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> wordArg(String name, List<String> words) {
		return argument(name, StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(words, builder));
	}

	/** An optional word argument: the default if the command stopped before it. */
	private static String word(CommandContext<CommandSourceStack> ctx, String name, String fallback) {
		try {
			return StringArgumentType.getString(ctx, name).toLowerCase(Locale.ROOT);
		} catch (IllegalArgumentException absent) {
			return fallback;
		}
	}

	private static int sound(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = word(ctx, "event", "");
		var event = dev.captience.realisticdamage.net.InjurySound.byId(id);
		if (event == null || event.isScream()) {
			ctx.getSource().sendFailure(Component.literal("Unknown sound '" + id + "' (use: "
					+ String.join(", ", Arrays.stream(dev.captience.realisticdamage.net.InjurySound.values())
							.filter(sound -> !sound.isScream())
							.map(dev.captience.realisticdamage.net.InjurySound::id).toList()) + ")"));
			return 0;
		}
		String setWord = word(ctx, "set", "mine");
		var set = dev.captience.realisticdamage.net.SoundSet.byId(setWord);
		if (set == null && !setWord.equals("mine")) {
			ctx.getSource().sendFailure(Component.literal("Unknown sound set '" + setWord + "' (use: mine, arcade, grounded)"));
			return 0;
		}
		float strength;
		try {
			strength = FloatArgumentType.getFloat(ctx, "strength");
		} catch (IllegalArgumentException absent) {
			strength = 1.0F;
		}
		String from = word(ctx, "from", "self");
		if (!from.equals("self") && !from.equals("far")) {
			ctx.getSource().sendFailure(Component.literal("Unknown place '" + from + "' (use: self, far)"));
			return 0;
		}
		String materialWord = word(ctx, "material", "flesh");
		var material = dev.captience.realisticdamage.net.SoundMaterial.byId(materialWord);
		if (material == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown material '" + materialWord + "' (use: flesh, dry)"));
			return 0;
		}
		if (!dev.captience.realisticdamage.util.Notifier.soundTest(player, event, set, strength,
				from.equals("far"), material)) {
			ctx.getSource().sendFailure(Component.literal("Your game does not have Realistic Damage's sounds"));
			return 0;
		}
		final float heard = strength;
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Played %s (%s set, strength %.2f, %s, %s)", event.id(),
				set == null ? "your" : set.id(), heard,
				from.equals("far") ? "six blocks away" : "your own body", material.id())), false);
		return 1;
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> partArg() {
		return wordArg("part", Arrays.stream(BodyPart.values()).map(BodyPart::id).toList());
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

	private static int heal(CommandContext<CommandSourceStack> ctx, List<LivingEntity> bodies) {
		for (LivingEntity body : bodies) {
			RDAttachments.get(body).reset();
			changed(body);
		}
		if (bodies.isEmpty()) {
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Fully restored " + who(bodies)), true);
		return bodies.size();
	}

	private static int info(CommandContext<CommandSourceStack> ctx, LivingEntity player) {
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
			statuses.add(String.format(Locale.ROOT, "CONCUSSION (grade %d, severity %.1f, flare %.0f%s%s)",
					data.concussionGrade(), data.concussionSeverity(), data.concussionFlare(),
					data.vertigo() ? ", VERTIGO" : "", data.retching() ? ", RETCHING" : ""));
		}
		if (data.hypoxia() > 0.0) {
			statuses.add(String.format(Locale.ROOT, "HYPOXIA (%.1fs debt)", data.hypoxia()));
		}
		if (data.adrenaline().phase != dev.captience.realisticdamage.systems.AdrenalineMath.Phase.NONE) {
			statuses.add(String.format(Locale.ROOT, "ADRENALINE %s (%dt%s)", data.adrenaline().phase,
					data.adrenaline().ticks, data.adrenaline().winded ? ", WINDED" : ""));
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

	/** Takes the head off each target that can lose one, as an edge on a ruined head would. */
	private static int behead(CommandContext<CommandSourceStack> ctx,
			java.util.Collection<? extends Entity> targets) {
		int done = 0;
		for (Entity target : targets) {
			if (!(target instanceof LivingEntity body) || !body.isAlive()
					|| !MobAnatomy.participates(body)) {
				continue;
			}
			var data = RDAttachments.get(body);
			if (!dev.captience.realisticdamage.damage.Decapitation.can(body, data)) {
				continue;
			}
			data.part(BodyPart.HEAD).setCondition(0.0);
			Entity by = ctx.getSource().getEntity();
			Vec3 blow = by == null ? Vec3.ZERO
					: body.position().subtract(by.position()).multiply(1.0, 0.0, 1.0);
			dev.captience.realisticdamage.damage.Decapitation.decapitate(body, data,
					blow.lengthSqr() < 1.0e-6 ? blow : blow.normalize(), by == body ? null : by);
			done++;
		}
		final int count = done;
		if (count == 0) {
			ctx.getSource().sendFailure(Component.literal("No head to take: the target needs a body, a head, "
					+ "and decapitation switched on (it sits under limb destruction)"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Took " + count + (count == 1 ? " head" : " heads")), true);
		return count;
	}

	/** Snaps the neck of each target that has one, as a head-first landing would. */
	private static int breakNeck(CommandContext<CommandSourceStack> ctx, List<LivingEntity> bodies) {
		int done = 0;
		for (LivingEntity body : bodies) {
			if (dev.captience.realisticdamage.damage.BrokenNeck.can(body)) {
				Entity by = ctx.getSource().getEntity();
				dev.captience.realisticdamage.damage.BrokenNeck.snap(body, RDAttachments.get(body), by == body ? null : by);
				done++;
			}
		}
		final int count = done;
		if (count == 0) {
			ctx.getSource().sendFailure(Component.literal("No neck to break: the target needs a head, "
					+ "and can't be a boss or in creative"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Broke " + count + (count == 1 ? " neck" : " necks")), true);
		return count;
	}

	/** Gives each target whiplash of this severity (0 mild to 1 all but broken). */
	private static int whiplash(CommandContext<CommandSourceStack> ctx,
			java.util.Collection<? extends Entity> targets, double severity) {
		int done = 0;
		for (Entity target : targets) {
			if (target instanceof LivingEntity body && body.isAlive()
					&& dev.captience.realisticdamage.damage.BrokenNeck.can(body)) {
				var data = RDAttachments.get(body);
				// Forced: the command replaces whatever is there, milder or not.
				data.clearWhiplash();
				dev.captience.realisticdamage.damage.BrokenNeck.wrench(body, data, severity);
				if (data.whiplashed()) {
					done++;
				}
			}
		}
		final int count = done;
		if (count == 0) {
			ctx.getSource().sendFailure(Component.literal("No neck to wrench: the target needs a body and a head, "
					+ "and whiplash switched on"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Wrenched " + count + (count == 1 ? " neck" : " necks")), true);
		return count;
	}

	/**
	 * {@code set <targets> <what> <value>}: a region's condition, or one of the
	 * two hidden meters. One verb where there were three (setpart, setblood,
	 * setconsciousness), because the thing being set is an argument and not
	 * part of the verb.
	 */
	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<LivingEntity> bodies = bodies(ctx);
		if (bodies.isEmpty()) {
			return 0;
		}
		String what = StringArgumentType.getString(ctx, "what").toLowerCase(Locale.ROOT);
		double value = DoubleArgumentType.getDouble(ctx, "value");
		BodyPart part = BodyPart.byId(what);
		boolean blood = what.equals("blood");
		if (part == null && !blood && !what.equals("consciousness")) {
			ctx.getSource().sendFailure(Component.literal("Unknown thing to set '" + what + "' (use: "
					+ String.join(", ", Arrays.stream(BodyPart.values()).map(BodyPart::id).toList())
					+ ", blood, consciousness)"));
			return 0;
		}
		if (part == null && value > 100.0) {
			ctx.getSource().sendFailure(Component.literal(
					(blood ? "Blood" : "Consciousness") + " runs from 0 to 100"));
			return 0;
		}
		for (LivingEntity body : bodies) {
			RealisticDamageData data = RDAttachments.get(body);
			if (part != null) {
				data.part(part).setCondition(value);
			} else if (blood) {
				data.setBlood(value);
			} else {
				data.setConsciousness(value);
			}
			changed(body);
		}
		// Read back off the first body, so a clamped request shows what was set.
		RealisticDamageData first = RDAttachments.get(bodies.get(0));
		String line;
		if (part != null) {
			line = String.format(Locale.ROOT, "%s of %s set to %.2f", said(part), who(bodies),
					first.part(part).condition());
		} else if (blood) {
			line = String.format(Locale.ROOT, "Blood of %s set to %.1f", who(bodies), first.blood());
		} else {
			line = String.format(Locale.ROOT, "Consciousness of %s set to %.1f (cap %.0f)", who(bodies),
					first.consciousness(), first.consciousnessCap());
		}
		ctx.getSource().sendSuccess(() -> Component.literal(line.substring(0, 1).toUpperCase(Locale.ROOT)
				+ line.substring(1)), true);
		return bodies.size();
	}

	private static int damagePart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<LivingEntity> bodies = bodies(ctx);
		BodyPart part = parsePart(ctx);
		if (part == null || bodies.isEmpty()) {
			return 0;
		}
		double amount = DoubleArgumentType.getDouble(ctx, "amount");
		double dealt = 0.0;
		for (LivingEntity body : bodies) {
			dealt = RDAttachments.get(body).part(part).damage(amount);
			changed(body);
		}
		final double last = dealt;
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Dealt %.2f condition damage to the %s of %s (now %s)", last, said(part), who(bodies),
				RDAttachments.get(bodies.get(bodies.size() - 1)).part(part).state())), true);
		return bodies.size();
	}

	/**
	 * {@code break <targets> <part>}: an arm or a leg fractures, and the head's
	 * break is the neck (2.0; this took over from the separate breakneck verb).
	 * The torso has nothing in it the mod breaks.
	 */
	private static int breakPart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<LivingEntity> bodies = bodies(ctx);
		BodyPart part = parsePart(ctx);
		if (part == null || bodies.isEmpty()) {
			return 0;
		}
		if (part == BodyPart.HEAD) {
			return breakNeck(ctx, bodies);
		}
		if (!part.isLimb()) {
			ctx.getSource().sendFailure(Component.literal(
					"Arms and legs break, and the head's break is the neck; the torso has nothing to break"));
			return 0;
		}
		for (LivingEntity body : bodies) {
			RDAttachments.get(body).part(part).setBroken(true);
			changed(body);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Broke the " + said(part) + " of " + who(bodies)), true);
		return bodies.size();
	}

	private static int bleed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<LivingEntity> bodies = bodies(ctx);
		BodyPart part = parsePart(ctx);
		if (part == null || bodies.isEmpty()) {
			return 0;
		}
		String severityName = StringArgumentType.getString(ctx, "severity");
		WoundSeverity severity;
		try {
			severity = WoundSeverity.valueOf(severityName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("Unknown severity '" + severityName + "' (use: "
					+ String.join(", ", Arrays.stream(WoundSeverity.values())
							.map(s -> s.name().toLowerCase(Locale.ROOT)).toList()) + ")"));
			return 0;
		}
		for (LivingEntity body : bodies) {
			RDAttachments.get(body).addWound(part, severity);
			changed(body);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Opened a %s wound on the %s of %s (effective %.2f blood/s)", severity.name().toLowerCase(Locale.ROOT),
				said(part), who(bodies), RDAttachments.get(bodies.get(0)).effectiveBleedRate())), true);
		return bodies.size();
	}

	private static int concussion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<LivingEntity> bodies = bodies(ctx);
		if (bodies.isEmpty()) {
			return 0;
		}
		String grade = word(ctx, "grade", "moderate");
		double severity = switch (grade) {
			case "mild" -> 20.0;
			case "moderate" -> dev.captience.realisticdamage.config.Balance.CONCUSSION_DEFAULT_SEVERITY;
			case "severe" -> 85.0;
			case "none" -> 0.0;
			default -> -1.0;
		};
		if (severity < 0.0) {
			ctx.getSource().sendFailure(Component.literal("Grade must be mild, moderate, severe or none"));
			return 0;
		}
		for (LivingEntity body : bodies) {
			RDAttachments.get(body).setConcussionSeverity(severity);
			changed(body);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(severity > 0.0
				? "Concussed " + who(bodies) + " (" + grade + ")"
				: "Cleared the concussion of " + who(bodies)), true);
		return bodies.size();
	}

	private static int destroyPart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<LivingEntity> bodies = bodies(ctx);
		BodyPart part = parsePart(ctx);
		if (part == null || bodies.isEmpty()) {
			return 0;
		}
		for (LivingEntity body : bodies) {
			dev.captience.realisticdamage.damage.DestructionLogic.destroy(body, RDAttachments.get(body), part);
			changed(body);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Destroyed the " + said(part) + " of " + who(bodies)), true);
		return bodies.size();
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
		List<LivingEntity> bodies = bodies(ctx);
		BodyPart part = parsePart(ctx);
		if (part == null || bodies.isEmpty()) {
			return 0;
		}
		if (!part.isLimb()) {
			ctx.getSource().sendFailure(Component.literal("Only arms and legs come off in a blast"));
			return 0;
		}
		for (LivingEntity body : bodies) {
			Vec3 region = BodyHitResolver.regionCenter(body, part);
			Vec3 forward = Vec3.directionFromRotation(0.0F, body.yBodyRot);
			Vec3 right = forward.yRot((float) (-Math.PI / 2.0));
			Vec3 outward = part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG ? right.scale(-1.0) : right;
			Vec3 at = region.add(outward.scale(0.9));
			body.level().explode(null, at.x, at.y, at.z, radius, Level.ExplosionInteraction.NONE);
			changed(body);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"Blast r=%.1f beside the %s of %s", radius, said(part), who(bodies))), true);
		return bodies.size();
	}

	/** Sets or clears a long-term ceiling. Bypasses the toggle, the way /rdmg destroy bypasses limb destruction. */
	private static int ceiling(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
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
		for (ServerPlayer player : players) {
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
		}
		return parts.size() * players.size();
	}

	/**
	 * Writes a region's history directly, including erasing it. Cosmetic on both
	 * sides: a scar changes no outcome, so unlike every other verb here this one
	 * can only ever be worth a screenshot.
	 */
	private static int scar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
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
		for (ServerPlayer player : players) {
			// setScar, not markScar: the debug verb is the one path allowed to lower one.
			RDAttachments.get(player).part(part).setScar(level);
			RDAttachments.pushSnapshot(player);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Scar on the " + said(part) + " of "
				+ (players.size() == 1 ? players.iterator().next().getName().getString() : players.size() + " players")
				+ " set to " + name.toLowerCase(Locale.ROOT)), true);
		return players.size();
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
		List<LivingEntity> bodies = bodies(ctx);
		if (bodies.isEmpty()) {
			return 0;
		}
		for (LivingEntity body : bodies) {
			RDAttachments.get(body).setBurned(true);
			changed(body);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Burned " + who(bodies)), true);
		return bodies.size();
	}
}
