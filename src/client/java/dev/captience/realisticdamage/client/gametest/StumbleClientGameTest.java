package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.DeathCamera;
import dev.captience.realisticdamage.client.RagdollView;
import dev.captience.realisticdamage.client.RemoteCorpses;
import dev.captience.realisticdamage.damage.Corpses;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.Behaviour;
import dev.captience.realisticdamage.ragdoll.Stumble;
import dev.captience.realisticdamage.systems.MobAnatomy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Stumbling deaths, IK phase 1 (Grounded), in a real client, as real deaths
 * reach it: an arrow that flies and lands in a chest, a body shot in the back
 * as it sprints, a leg shot, a headshot, a husk killed by a blow from the
 * player, a bleed-out, a blast, a blow with the setting off, and your own
 * death in first person at a sprint.
 *
 * <p>What has to hold: blows, arrows and bleed-outs die on their feet, for
 * between 0.3 and 2 seconds, and end lying down; a headshot and a blast drop
 * the old way, and so does everything with Death Stumble off (the stagger the
 * stumble replaced comes back); a sprinting body takes at least two strides, carries at least
 * 1.4 blocks and lands face first (the running-death rule, on the ragdoll
 * path; one of up to three bodies has to, see {@code shotSprinting}); and while your own body is on its feet the view is carried by the
 * chest, so it turns a few degrees a tick and does not whip with the head.
 * The screenshots, from the side every other tick through the stumble, are
 * for a person to judge the look.
 */
public class StumbleClientGameTest implements FabricClientGameTest {
	/** Blocks between one scene and the next, along +X. */
	private static final double APART = 10.0;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig config = ClientEffectsConfig.get();
			config.ragdolls = ClientEffectsConfig.RagdollMode.FULL;
			config.deathStumble = ClientEffectsConfig.DeathStumble.GROUNDED;
			config.deathCamera = true;
			config.ownRagdoll = true;
			config.bodiesStay = 30;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
			});
			double px = context.computeOnClient(client -> client.player.getX());
			double py = context.computeOnClient(client -> client.player.getY());
			double pz = context.computeOnClient(client -> client.player.getZ());

			arrowInTheChest(context, singleplayer, px, py, pz);
			shotSprinting(context, singleplayer, px + APART, py, pz);
			shotInTheLeg(context, singleplayer, px + 2 * APART, py, pz);
			drops(context, singleplayer, px + 3 * APART, py, pz, "projectile head 0.3", "4_head", "a headshot");
			husk(context, singleplayer, px + 4 * APART, py, pz);
			bleedOut(context, singleplayer, px + 5 * APART, py, pz);
			drops(context, singleplayer, px + 6 * APART, py, pz, "explosion torso 1.0", "7_blast", "a blast");
			off(context, singleplayer, px + 8 * APART, py, pz);
			ownDeath(context, singleplayer, px + 7 * APART, py, pz);
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(options -> options.keySprint);
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	// --- The scenes -----------------------------------------------------------------

	/** A real arrow, flown four blocks into the chest of a body standing still. */
	private static void arrowInTheChest(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		stage(context, singleplayer, x, y, z);
		int id = singleplayer.getServer().computeOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			Mannequin mannequin = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
			mannequin.snapTo(x, y, z + 3.0, 180.0F, 0.0F);
			mannequin.setYHeadRot(180.0F);
			mannequin.setYBodyRot(180.0F);
			level.addFreshEntity(mannequin);
			mannequin.setHealth(1.0F);
			return mannequin.getId();
		});
		watch(context, x, y, z + 3.0);
		context.waitTicks(20);
		context.takeScreenshot("rd_stumble_1_arrow_00_before");
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			arrow.setPos(x, y + 1.25, z - 1.0);
			arrow.shoot(0.0, 0.0, 1.0, 3.0F, 0.0F);
			level.addFreshEntity(arrow);
		});
		Stumble stumble = awaitStumble(context, id, 40);
		if (stumble == null) {
			throw new AssertionError("killed by an arrow in the chest, standing, and did not stumble (ragdoll lean "
					+ context.computeOnClient(client -> RemoteCorpses.leanOf(id)) + ")");
		}
		Vector3d from = context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
		int letGo = film(context, stumble, "rd_stumble_1_arrow", 16);
		Vector3d to = down(context, id, "an arrow in the chest");
		context.takeScreenshot("rd_stumble_1_arrow_99_down");
		double went = Math.hypot(to.x - from.x, to.z - from.z);
		report("arrow in the chest", stumble, letGo, went, context.computeOnClient(client -> RemoteCorpses.faceOf(id)));
		onItsFeet(stumble, letGo, "an arrow in the chest");
		if (stumble.stepper.steps() > 2) {
			throw new AssertionError("shot standing still and took " + stumble.stepper.steps() + " steps");
		}
		if (went > 2.5) {
			throw new AssertionError("shot standing still and ended " + went + " blocks away");
		}
	}

	/**
	 * Shot in the back at a sprint: the running-death rule, on the ragdoll path.
	 *
	 * <p>Where in its stride a body died decides how its first step goes, and
	 * the game takes that from the body itself. About one in twenty-five died
	 * with its feet together: it puts a foot down short, is over it before the
	 * next arrives, and goes down on its face after a single step. So up to
	 * three bodies are shot, and one of them has to run on. Everything else
	 * holds for every one of them.
	 */
	private static void shotSprinting(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		List<String> misses = new ArrayList<>();
		for (int body = 0; body < 3; body++) {
			// The second and third fall past the last scene, out of everyone's way.
			double at = body == 0 ? x : x + (7 + body) * APART;
			String miss = shotSprinting(context, singleplayer, at, y, z, body == 0 ? "" : "_" + (char) ('a' + body));
			if (miss == null) {
				return;
			}
			System.out.println("RD stumble test: shot at a sprint, body " + (body + 1) + " " + miss);
			misses.add(miss);
		}
		throw new AssertionError("three bodies shot at a sprint, and none ran on: " + misses);
	}

	/** One body shot at a sprint. Null if it ran on as the rule has it, or how it did not. */
	private static String shotSprinting(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z, String take) {
		stage(context, singleplayer, x, y, z);
		int id = place(context, singleplayer, x, y, z, "projectile torso 0.3 sprinting away");
		watch(context, x, y, z + 5.0);
		Stumble stumble = awaitStumble(context, id, 40);
		if (stumble == null) {
			throw new AssertionError("shot in the back at a sprint and did not stumble");
		}
		Vector3d from = context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
		int letGo = film(context, stumble, "rd_stumble_2_sprint" + take, 16);
		Vector3d to = down(context, id, "shot at a sprint");
		context.takeScreenshot("rd_stumble_2_sprint" + take + "_99_down");
		double went = to.z - from.z;
		double face = context.computeOnClient(client -> RemoteCorpses.faceOf(id));
		report("shot at a sprint", stumble, letGo, went, face);
		onItsFeet(stumble, letGo, "shot at a sprint");
		if (stumble.stepper.strides() < 2) {
			return "took " + stumble.stepper.strides() + " strides, not two";
		}
		if (went < 1.4) {
			return "carried " + went + " blocks, not 1.4";
		}
		if (face >= 0.0) {
			return "did not land face first: facing " + face;
		}
		return null;
	}

	/** Shot in the right leg as it walks: that leg cannot carry it. */
	private static void shotInTheLeg(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		stage(context, singleplayer, x, y, z);
		int id = place(context, singleplayer, x, y, z, "projectile right_leg 0.3 walking away");
		watch(context, x, y, z + 4.0);
		Stumble stumble = awaitStumble(context, id, 40);
		if (stumble == null) {
			throw new AssertionError("shot in the leg and did not stumble");
		}
		Vector3d from = context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
		int letGo = film(context, stumble, "rd_stumble_3_leg", 8);
		Vector3d to = down(context, id, "shot in the leg");
		context.takeScreenshot("rd_stumble_3_leg_99_down");
		report("shot in the leg", stumble, letGo, Math.hypot(to.x - from.x, to.z - from.z),
				context.computeOnClient(client -> RemoteCorpses.faceOf(id)));
		onItsFeet(stumble, letGo, "shot in the leg");
		if (stumble.weakLeg != 0) {
			throw new AssertionError("shot in the right leg, and the leg that cannot carry it is " + stumble.weakLeg);
		}
		if (letGo > 26) {
			throw new AssertionError("on a leg that cannot carry it for " + letGo + " ticks");
		}
	}

	/** A death that gets no time on its feet: it falls the way it always did. */
	private static void drops(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z, String death, String shot, String what) {
		stage(context, singleplayer, x, y, z);
		int id = place(context, singleplayer, x, y, z, death);
		watch(context, x, y, z + 3.0);
		int waited = 0;
		while (context.computeOnClient(client -> RemoteCorpses.leanOf(id)) < 0.0) {
			context.waitTick();
			if (++waited > 40) {
				throw new AssertionError(what + ": the mannequin never became a ragdoll");
			}
		}
		if (context.computeOnClient(client -> RemoteCorpses.stumbleOf(id)) != null) {
			throw new AssertionError(what + " got time on its feet");
		}
		context.waitTicks(6);
		context.takeScreenshot("rd_stumble_" + shot + "_00");
		down(context, id, what);
		context.takeScreenshot("rd_stumble_" + shot + "_99_down");
		System.out.println("RD stumble test: " + what + " dropped, no stumble");
	}

	/** A husk at its last health, struck in the chest by the player. */
	private static void husk(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		stage(context, singleplayer, x, y, z);
		int id = singleplayer.getServer().computeOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			LivingEntity husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
			husk.snapTo(x, y, z + 2.5, 180.0F, 0.0F);
			husk.setYHeadRot(180.0F);
			husk.setYBodyRot(180.0F);
			((Mob) husk).setNoAi(true);
			level.addFreshEntity(husk);
			RDAttachments.get(husk).reset();
			RDAttachments.pushSnapshot(husk);
			MobAnatomy.track(husk);
			return husk.getId();
		});
		watch(context, x, y, z + 3.0);
		context.waitTicks(20);
		context.takeScreenshot("rd_stumble_5_husk_00_before");
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			// In front of it, looking at its chest: the look decides where a blow lands.
			player.snapTo(x, y, z, 0.0F, 12.0F);
			player.setYHeadRot(0.0F);
			LivingEntity husk = (LivingEntity) level.getEntity(id);
			husk.setHealth(2.0F);
			husk.invulnerableTime = 0;
			husk.hurtServer(level, level.damageSources().playerAttack(player), 7.0F);
		});
		Stumble stumble = awaitStumble(context, id, 40);
		if (stumble == null) {
			throw new AssertionError("a husk killed by a blow to the chest did not stumble (ragdoll lean "
					+ context.computeOnClient(client -> RemoteCorpses.leanOf(id)) + ")");
		}
		Vector3d from = context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
		int letGo = film(context, stumble, "rd_stumble_5_husk", 10);
		Vector3d to = down(context, id, "a husk struck in the chest");
		context.takeScreenshot("rd_stumble_5_husk_99_down");
		report("husk struck in the chest", stumble, letGo, Math.hypot(to.x - from.x, to.z - from.z),
				context.computeOnClient(client -> RemoteCorpses.faceOf(id)));
		onItsFeet(stumble, letGo, "a husk struck in the chest");
	}

	/** Bled out standing: nothing hit it, so all it has to catch is its own sway. */
	private static void bleedOut(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		stage(context, singleplayer, x, y, z);
		int id = place(context, singleplayer, x, y, z, "fade");
		watch(context, x, y, z + 3.0);
		Stumble stumble = awaitStumble(context, id, 40);
		if (stumble == null) {
			throw new AssertionError("bled out on its feet and did not stumble");
		}
		Vector3d from = context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
		int letGo = film(context, stumble, "rd_stumble_6_bleed", 8);
		Vector3d to = down(context, id, "a bleed-out");
		context.takeScreenshot("rd_stumble_6_bleed_99_down");
		report("bleed-out", stumble, letGo, Math.hypot(to.x - from.x, to.z - from.z),
				context.computeOnClient(client -> RemoteCorpses.faceOf(id)));
		onItsFeet(stumble, letGo, "a bleed-out");
	}

	/**
	 * Death Stumble: Off. A blow to the gut gets no time on its feet, and the
	 * stagger that the stumble replaced is back among its reactions: the death
	 * alpha.21 gave it.
	 */
	private static void off(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		context.runOnClient(client -> ClientEffectsConfig.get().deathStumble = ClientEffectsConfig.DeathStumble.OFF);
		try {
			stage(context, singleplayer, x, y, z);
			int id = place(context, singleplayer, x, y, z, "blow torso 0.2");
			watch(context, x, y, z + 3.0);
			int waited = 0;
			while (context.computeOnClient(client -> RemoteCorpses.leanOf(id)) < 0.0) {
				context.waitTick();
				if (++waited > 40) {
					throw new AssertionError("a blow, setting off: the mannequin never became a ragdoll");
				}
			}
			if (context.computeOnClient(client -> RemoteCorpses.stumbleOf(id)) != null) {
				throw new AssertionError("a blow got time on its feet with Death Stumble off");
			}
			var reactions = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id);
				return e == null ? null : RemoteCorpses.ragdollReactions(e);
			});
			if (reactions == null || !reactions.contains(Behaviour.Reaction.STAGGER)) {
				throw new AssertionError("a blow with Death Stumble off died with " + reactions
						+ ": the old stagger is not back");
			}
			context.waitTicks(6);
			context.takeScreenshot("rd_stumble_9_off_00");
			down(context, id, "a blow, setting off");
			context.takeScreenshot("rd_stumble_9_off_99_down");
			System.out.println("RD stumble test: a blow with the setting off staggers and drops, " + reactions);
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.get().deathStumble = ClientEffectsConfig.DeathStumble.GROUNDED);
		}
	}

	/**
	 * Your own death, in first person, shot in the chest at a sprint. While
	 * the body is on its feet the view has to be the chest's: a few degrees a
	 * tick, where the head (hanging loose) whips at the camera's limit.
	 * Measured in this scene: carried by the chest it turns about 5 degrees a
	 * tick at most (8.6 is its limit) and 2.3 on average, rolls 2 and bobs
	 * 0.06 blocks. Carried by the head it turned at the camera's limit (12.9)
	 * and rolled 13.8. It differs by where in your stride you died: the chest
	 * of a body that pitches straight over turns faster.
	 */
	private static void ownDeath(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		stage(context, singleplayer, x, y, z);
		context.runOnClient(client -> client.player.setXRot(5.0F));
		context.getInput().holdKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keySprint);
		context.waitTicks(25);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			Corpses.script(player, new Corpses.Script(DeathKind.PROJECTILE, BodyPart.TORSO, new Vec3(0.0, 0.0, -1.0), 0.3, 0.5));
			player.invulnerableTime = 0;
			player.hurtServer(player.level(), player.level().damageSources().generic(), 40.0F);
		});
		int waited = 0;
		while (!context.computeOnClient(client -> ClientState.dying())) {
			context.waitTick();
			if (++waited > 20) {
				throw new AssertionError("the shot did not kill");
			}
		}
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().releaseKey(options -> options.keySprint);
		context.waitTicks(2);
		if (!context.computeOnClient(client -> RagdollView.active())) {
			throw new AssertionError("dead, and the camera is not riding a ragdoll");
		}
		Stumble stumble = context.computeOnClient(client -> RemoteCorpses.stumbleOf(client.player.getId()));
		if (stumble == null) {
			throw new AssertionError("shot in the chest at a sprint, and your own body did not stumble");
		}
		if (!context.computeOnClient(client -> RagdollView.steady())) {
			throw new AssertionError("on its feet, and the view is not carried by the chest");
		}
		int id = context.computeOnClient(client -> client.player.getId());
		// Each tick: the eye, the way the view looks, its roll, and how far over the body is.
		double[] before = null;
		double worstTurn = 0.0;
		double turned = 0.0;
		double worstBob = 0.0;
		double worstRoll = 0.0;
		double worstOffEye = 0.0;
		int upright = 0;
		int letGo = -1;
		for (int tick = 0; tick < 70; tick++) {
			// A screenshot draws a frame of its own, a whole tick long to the view,
			// which chases the chest twice that tick: not one to judge it by.
			boolean shot = tick == 3 || tick == 7 || tick == 13 || tick == 19;
			if (shot) {
				context.takeScreenshot(String.format("rd_stumble_8_own_%02d_first_person", tick));
			}
			context.waitTick();
			double[] now = context.computeOnClient(client -> {
				Vec3 eye = RagdollView.eye(1.0F);
				double yaw = Math.toRadians(RagdollView.yaw());
				double pitch = Math.toRadians(RagdollView.pitch());
				Vec3 camera = client.gameRenderer.mainCamera().position();
				return new double[] {eye.x, eye.y, eye.z,
						-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch),
						RagdollView.roll(), RemoteCorpses.leanOf(id), eye.distanceTo(camera)};
			});
			boolean held = stumble.holding();
			if (!held && letGo < 0) {
				letGo = tick + 1;
			}
			worstOffEye = Math.max(worstOffEye, now[8]);
			if (before != null && held && now[7] < 40.0 && !shot) {
				double dot = before[3] * now[3] + before[4] * now[4] + before[5] * now[5];
				double turn = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
				worstTurn = Math.max(worstTurn, turn);
				turned += turn;
				worstBob = Math.max(worstBob, Math.abs(now[1] - before[1]));
				worstRoll = Math.max(worstRoll, Math.abs(now[6] - before[6]));
				upright++;
			}
			before = now;
			if (letGo > 0 && tick == letGo + 8 && context.computeOnClient(client -> RagdollView.steady())) {
				throw new AssertionError("down for 8 ticks, and the view is still carried by the chest");
			}
		}
		context.takeScreenshot("rd_stumble_8_own_99_landed");
		double mean = upright == 0 ? 0.0 : turned / upright;
		System.out.printf(Locale.ROOT,
				"RD stumble test: own death, %d steps, off its feet after %d ticks (%s); on its feet for %d ticks the view"
						+ " turned %.1f deg a tick at most, %.1f on average, rolled %.1f, bobbed %.3f; camera off the eye %.2f%n",
				stumble.stepper.steps(), letGo, stumble.why(), upright, worstTurn, mean, worstRoll, worstBob, worstOffEye);
		if (letGo < 0) {
			throw new AssertionError("your own body was held on its feet and never let go");
		}
		if (upright < 3) {
			throw new AssertionError("your own body was upright for " + upright + " ticks: nothing to judge the view by");
		}
		if (worstTurn > 9.5) {
			throw new AssertionError("on its feet, the view turned " + worstTurn + " degrees in a tick");
		}
		if (mean > 6.0) {
			throw new AssertionError("on its feet, the view turned " + mean + " degrees a tick on average");
		}
		if (worstRoll > 8.0) {
			throw new AssertionError("on its feet, the view rolled " + worstRoll + " degrees in a tick");
		}
		if (worstBob > 0.3) {
			throw new AssertionError("on its feet, the view bobbed " + worstBob + " blocks in a tick");
		}
		if (worstOffEye > 1.5) {
			throw new AssertionError("the camera was not at the view's eye: off by " + worstOffEye);
		}
		if (context.computeOnClient(client -> RemoteCorpses.leanOf(id)) < 60.0) {
			throw new AssertionError("your own body is still up after three and a half seconds");
		}
		// Hygiene: the screen arrives and the next life is clean.
		int settle = 0;
		while (!context.computeOnClient(client -> DeathCamera.settled())) {
			context.waitTick();
			if (++settle > 300) {
				throw new AssertionError("your own body never came to rest");
			}
		}
		int screen = 0;
		while (context.computeOnClient(client -> DeathCamera.screenAlpha(1.0F)) < 0.999F
				|| !context.computeOnClient(client -> DeathCamera.buttonsReady())) {
			context.waitTick();
			if (++screen > 400) {
				throw new AssertionError("the death screen never finished arriving");
			}
		}
		context.waitTicks(10);
		context.clickScreenButton("deathScreen.respawn");
		context.waitTicks(20);
		if (context.computeOnClient(client -> ClientState.dying())) {
			throw new AssertionError("respawned and still dying");
		}
		if (context.computeOnClient(client -> RagdollView.active())) {
			throw new AssertionError("the ragdoll view leaked into the next life");
		}
	}

	// --- Helpers ----------------------------------------------------------------------

	/** Puts the player at a scene's mark, looking level along +Z. */
	private static void stage(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z) {
		singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().teleportTo(x, y, z));
		context.waitTicks(4);
		context.runOnClient(client -> {
			client.player.setYRot(0.0F);
			client.player.setXRot(0.0F);
		});
		context.waitTicks(4);
	}

	/**
	 * Runs {@code /rdmg ragdoll <death>} as the player at the scene's mark,
	 * which places a mannequin three blocks along +Z and kills it a second
	 * later. Returns the mannequin's id.
	 */
	private static int place(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double x, double y, double z, String death) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.snapTo(x, y, z, 0.0F, 0.0F);
			// As the player, with the server's permission (a test world's player is not an operator).
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
					"execute as " + player.getScoreboardName() + " at @s rotated as @s run rdmg ragdoll " + death);
		});
		context.waitTicks(3);
		return singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			List<Mannequin> found = player.level().getEntitiesOfClass(Mannequin.class,
					new AABB(x - 2, y - 1, z + 1, x + 2, y + 3, z + 5), LivingEntity::isAlive);
			if (found.isEmpty()) {
				throw new AssertionError("'rdmg ragdoll " + death + "' placed no mannequin");
			}
			return found.getFirst().getId();
		});
	}

	/** Watch from the side: off to -X, level with the body, looking +X at this spot. */
	private static void watch(ClientGameTestContext context, double x, double y, double z) {
		context.runOnClient(client -> {
			client.player.setPos(x - 4.6, y, z);
			client.player.setYRot(-90.0F);
			client.player.setXRot(8.0F);
		});
		context.waitTicks(2);
	}

	/** Waits for this entity's death to be a stumble; null if it never is. */
	private static Stumble awaitStumble(ClientGameTestContext context, int id, int ticks) {
		for (int i = 0; i < ticks; i++) {
			Stumble stumble = context.computeOnClient(client -> RemoteCorpses.stumbleOf(id));
			if (stumble != null) {
				return stumble;
			}
			context.waitTick();
		}
		return context.computeOnClient(client -> RemoteCorpses.stumbleOf(id));
	}

	/**
	 * A frame every other tick through the stumble, then waits for the body to
	 * be let go of. Returns the tick it was, or -1 if it never was.
	 */
	private static int film(ClientGameTestContext context, Stumble stumble, String name, int frames) {
		int letGo = -1;
		for (int frame = 0; frame < frames; frame++) {
			context.takeScreenshot(String.format("%s_%02d", name, frame + 1));
			for (int i = 0; i < 2; i++) {
				context.waitTick();
				if (letGo < 0 && !stumble.holding()) {
					letGo = frame * 2 + i + 1;
				}
			}
		}
		for (int tick = frames * 2; tick < 60 && letGo < 0; tick++) {
			context.waitTick();
			if (!stumble.holding()) {
				letGo = tick + 1;
			}
		}
		return letGo;
	}

	/** Two seconds on, the body has to be lying down. Returns where its torso is. */
	private static Vector3d down(ClientGameTestContext context, int id, String what) {
		context.waitTicks(40);
		double lean = context.computeOnClient(client -> RemoteCorpses.leanOf(id));
		if (lean < 60.0) {
			throw new AssertionError(what + ": still up two seconds on, lean " + lean);
		}
		return context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
	}

	/** Its time on its feet: between 0.3 and 2 seconds, and over when it was due. */
	private static void onItsFeet(Stumble stumble, int letGo, String what) {
		if (stumble.onFeet < 0.3 - 1.0e-6 || stumble.onFeet > 2.0 + 1.0e-6) {
			throw new AssertionError(what + ": " + stumble.onFeet + " seconds on its feet");
		}
		if (letGo < 0) {
			throw new AssertionError(what + ": held on its feet and never let go");
		}
		if (letGo > Math.ceil(stumble.onFeet * 20.0) + 4) {
			throw new AssertionError(what + ": due " + stumble.onFeet + " seconds on its feet, and still up after "
					+ letGo + " ticks");
		}
	}

	private static void report(String what, Stumble stumble, int letGo, double went, double face) {
		System.out.printf(Locale.ROOT,
				"RD stumble test: %s: due %.2f s on its feet (vigor %.2f, weak leg %d), %d steps (%d strides), off them after %d ticks"
						+ " (%s), went %.2f blocks, facing %.2f%n",
				what, stumble.onFeet, stumble.vigor, stumble.weakLeg, stumble.stepper.steps(), stumble.stepper.strides(), letGo,
				stumble.why(),
				went, face);
	}
}
