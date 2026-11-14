package dev.captience.realisticdamage.client.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.audio.SoundRecipe;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.audio.SoundDirector;
import dev.captience.realisticdamage.client.audio.SoundRecipes;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.Decapitation;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.net.SoundMaterial;
import dev.captience.realisticdamage.net.SoundSet;
import dev.captience.realisticdamage.systems.MobAnatomy;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Injury sounds from recipes (2.0 audio), in a real client with a real sound
 * engine.
 *
 * <p>Every recipe in both sets is played through the game's own engine, at an
 * Injury Volume too low to hear, and has to come out as the files the recipe
 * names: that is the claim the Arcade set rests on (one exact vanilla file per
 * layer, read from the player's own game). Then where a sound is placed, what
 * a skeleton leaves out, what strength does, whose setting decides the set,
 * and the budget for small sounds.
 *
 * <p>What this cannot say is whether any of it sounds good. That is a person's
 * job; see AUDIO_TESTING.md.
 */
public class SoundClientGameTest implements FabricClientGameTest {
	/** Loud enough that the engine really plays it, far too quiet to hear. */
	private static final double WHISPER = 0.002;

	private static final List<String> subtitles = Collections.synchronizedList(new ArrayList<>());
	private static final SoundEventListener SUBTITLES = (sound, event, range) -> {
		if (sound.getIdentifier().getPath().startsWith("recipe/") && event.getSubtitle() != null) {
			subtitles.add(event.getSubtitle().getString());
		}
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		migration(failures);
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().injurySoundVolume = WHISPER;
			ClientEffectsConfig.get().screams = false;
			ClientEffectsConfig.get().soundSetValue = SoundSet.ARCADE;
			client.getSoundManager().addListener(SUBTITLES);
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
			});
			context.waitTicks(5);

			everyRecipe(context, singleplayer, failures);
			placement(context, singleplayer, failures);
			material(context, singleplayer, failures);
			strength(context, singleplayer, failures);
			whoseSetting(context, singleplayer, failures);
			aHeadComesOff(context, singleplayer, failures);
			budget(context, failures);
			command(context, singleplayer, failures);

			if (!failures.isEmpty()) {
				throw new AssertionError(String.join("; ", failures));
			}
		} finally {
			context.runOnClient(client -> {
				client.getSoundManager().removeListener(SUBTITLES);
				SoundDirector.clearSmalls();
				SoundDirector.clearLast();
				ClientEffectsConfig.load();
			});
		}
	}

	/** A 1.x settings file: whoever chose the visceral sounds gets Grounded, and the old key goes. */
	private static void migration(List<String> failures) {
		Gson gson = new Gson();
		ClientEffectsConfig visceral = gson.fromJson("{\"visceralInjurySounds\":true}", ClientEffectsConfig.class);
		visceral.migrate();
		if (visceral.soundSet() != SoundSet.GROUNDED) {
			failures.add("a 1.x file with the visceral sounds on migrated to " + visceral.soundSet());
		}
		if (gson.toJson(visceral).contains("visceralInjurySounds")) {
			failures.add("the 1.x sound switch is still written after migrating");
		}
		ClientEffectsConfig muted = gson.fromJson("{\"visceralInjurySounds\":false}", ClientEffectsConfig.class);
		muted.migrate();
		ClientEffectsConfig fresh = gson.fromJson("{}", ClientEffectsConfig.class);
		fresh.migrate();
		if (muted.soundSet() != SoundSet.ARCADE || fresh.soundSet() != SoundSet.ARCADE) {
			failures.add("the muted sounds, or no choice at all, should migrate to Arcade");
		}
		ClientEffectsConfig chosen = gson.fromJson("{\"soundSetValue\":\"GROUNDED\",\"visceralInjurySounds\":false}",
				ClientEffectsConfig.class);
		chosen.migrate();
		if (chosen.soundSet() != SoundSet.GROUNDED) {
			failures.add("a set that was already chosen was migrated over");
		}
		ClientEffectsConfig preset = new ClientEffectsConfig();
		preset.applyGroundedPreset();
		if (preset.soundSet() != SoundSet.GROUNDED) {
			failures.add("the Grounded preset left the sound set at " + preset.soundSet());
		}
		preset.applyArcadePreset();
		if (preset.soundSet() != SoundSet.ARCADE) {
			failures.add("the Arcade preset left the sound set at " + preset.soundSet());
		}
	}

	private static List<InjurySound> bodySounds() {
		List<InjurySound> out = new ArrayList<>();
		for (InjurySound sound : InjurySound.values()) {
			if (!sound.isScream()) {
				out.add(sound);
			}
		}
		return out;
	}

	/** What the director did with one test play, read back on the client. */
	private record Heard(SoundDirector.Played played, List<Boolean> going, List<String> subtitles) {}

	private static Heard play(ClientGameTestContext context, TestSingleplayerContext singleplayer, InjurySound event,
			SoundSet set, float strength, boolean far, SoundMaterial material) {
		context.runOnClient(client -> {
			SoundDirector.clearLast();
			SoundDirector.clearSmalls();
			subtitles.clear();
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			if (!Notifier.soundTest(player, event, set, strength, far, material)) {
				throw new AssertionError("the test client cannot hear the mod's sounds");
			}
		});
		context.waitTicks(3);
		return context.computeOnClient(client -> {
			SoundDirector.Played played = SoundDirector.last();
			List<Boolean> going = new ArrayList<>();
			if (played != null) {
				for (SoundInstance layer : played.layers()) {
					going.add(client.getSoundManager().isActive(layer) || SoundDirector.isPending(layer));
				}
			}
			return new Heard(played, going, List.copyOf(subtitles));
		});
	}

	/** Channels of a sound file, by opening it as the game would; 0 if it cannot be read. */
	private static int channels(Minecraft client, Identifier file) {
		try (InputStream in = client.getResourceManager().open(Sound.SOUND_LISTER.idToFile(file))) {
			return new JOrbisAudioStream(in).getFormat().getChannels();
		} catch (Exception e) {
			return 0;
		}
	}

	private static void everyRecipe(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			List<String> failures) {
		Set<String> loaded = context.computeOnClient(client -> SoundRecipes.loaded());
		int played = 0;
		for (SoundSet set : SoundSet.values()) {
			for (InjurySound event : bodySounds()) {
				String what = set.id() + "/" + event.id();
				if (!loaded.contains(what)) {
					failures.add("recipe " + what + " did not load");
					continue;
				}
				for (boolean far : new boolean[] {false, true}) {
					String where = what + (far ? " (far)" : " (own body)");
					Heard heard = play(context, singleplayer, event, set, 1.0F, far, SoundMaterial.FLESH);
					if (heard.played() == null) {
						failures.add(where + " never reached the director");
						continue;
					}
					if (heard.played().event() != event || heard.played().set() != set) {
						failures.add(where + " played as " + heard.played().set().id() + "/" + heard.played().event().id());
					}
					if (heard.played().files().isEmpty()) {
						failures.add(where + " played no layers");
					}
					// Blood drops have layers seven and twelve ticks out; three
					// ticks in, every layer is either sounding or still waiting.
					// A short layer of a quarter second is still sounding too.
					if (heard.going().contains(false)) {
						failures.add(where + ": the sound engine did not take layer "
								+ heard.played().files().get(heard.going().indexOf(false)));
					}
					for (Identifier file : heard.played().files()) {
						int channels = context.computeOnClient(client -> channels(client, file));
						if (channels == 0) {
							failures.add(where + ": " + file + " cannot be opened as a sound");
						} else if (far && channels != 1) {
							// The game cannot place a stereo sound: it would play inside
							// the listener's head from across the cave.
							failures.add(where + ": " + file + " has " + channels + " channels, and a placed sound must be mono");
						}
					}
					if (heard.subtitles().size() != 1) {
						failures.add(where + " showed " + heard.subtitles().size() + " subtitles " + heard.subtitles()
								+ ", should be one");
					}
					played++;
				}
			}
		}
		if (played != 2 * 2 * 7) {
			failures.add("expected to play 28 recipes (2 sets, 7 events, near and far), played " + played);
		}

		// The Arcade bone break, file for file: the game's own sounds, one exact
		// file per layer, not whichever the sound event would have picked.
		Heard fracture = play(context, singleplayer, InjurySound.FRACTURE, SoundSet.ARCADE, 1.0F, false,
				SoundMaterial.FLESH);
		List<Identifier> expected = List.of(Identifier.parse("minecraft:mob/turtle/egg/egg_crack4"),
				Identifier.parse("minecraft:block/bone_block/break2"), Identifier.parse("minecraft:mob/goat/horn_break1"));
		if (fracture.played() == null || !fracture.played().files().equals(expected)) {
			failures.add("the Arcade bone break played " + (fracture.played() == null ? "nothing" : fracture.played().files()));
		}
	}

	private static void placement(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			List<String> failures) {
		Heard own = play(context, singleplayer, InjurySound.FRACTURE, SoundSet.GROUNDED, 1.0F, false, SoundMaterial.FLESH);
		Heard far = play(context, singleplayer, InjurySound.FRACTURE, SoundSet.GROUNDED, 1.0F, true, SoundMaterial.FLESH);
		if (own.played() == null || far.played() == null) {
			failures.add("placement: nothing played");
			return;
		}
		SoundInstance mine = own.played().layers().get(0), theirs = far.played().layers().get(0);
		if (!mine.isRelative() || mine.getAttenuation() != SoundInstance.Attenuation.NONE
				|| mine.getSource() != SoundSource.AMBIENT) {
			failures.add("your own fracture should play inside your head, in AMBIENT");
		}
		if (theirs.isRelative() || theirs.getAttenuation() != SoundInstance.Attenuation.LINEAR
				|| theirs.getSource() != SoundSource.PLAYERS) {
			failures.add("somebody else's fracture should play in the world, in PLAYERS");
		}
		Vec3 eye = context.computeOnClient(client -> client.player.getEyePosition());
		double distance = new Vec3(theirs.getX(), theirs.getY(), theirs.getZ()).distanceTo(eye);
		if (distance > 6.5) {
			failures.add("the far test sound was placed " + distance + " blocks away, should be within 6");
		}
		if (!own.played().files().get(0).getPath().equals("grounded/fracture")
				|| !far.played().files().get(0).getPath().equals("grounded/fracture_far")) {
			failures.add("Grounded fracture played " + own.played().files() + " near and " + far.played().files() + " far");
		}
		float ratio = theirs.getVolume() / mine.getVolume();
		if (Math.abs(ratio - Balance.INJURY_SOUND_OTHERS_VOLUME) > 1.0e-4F) {
			failures.add("somebody else's fracture is " + ratio + " of your own, should be " + Balance.INJURY_SOUND_OTHERS_VOLUME);
		}
	}

	private static void material(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			List<String> failures) {
		Heard dry = play(context, singleplayer, InjurySound.DESTRUCTION, SoundSet.ARCADE, 1.0F, false, SoundMaterial.DRY);
		Heard wet = play(context, singleplayer, InjurySound.DESTRUCTION, SoundSet.ARCADE, 1.0F, false, SoundMaterial.FLESH);
		if (dry.played() == null || wet.played() == null || dry.played().files().size() >= wet.played().files().size()) {
			failures.add("a dry limb should have fewer layers than a wet one");
			return;
		}
		for (Identifier file : dry.played().files()) {
			if (file.getPath().contains("slime") || file.getPath().contains("mud")) {
				failures.add("a dry limb played the wet layer " + file);
			}
		}

		// And the server works the material out itself: a real skeleton is dry,
		// a real zombie is not.
		for (boolean skeleton : new boolean[] {true, false}) {
			context.runOnClient(client -> SoundDirector.clearLast());
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				LivingEntity body = standing(level, skeleton ? EntityTypes.SKELETON : EntityTypes.ZOMBIE,
						player.getX() + 3.0, player.getY(), player.getZ());
				Notifier.injurySound(body, InjurySound.DESTRUCTION);
				body.discard();
			});
			context.waitTicks(3);
			SoundDirector.Played played = context.computeOnClient(client -> SoundDirector.last());
			int layers = played == null ? -1 : played.files().size();
			if (layers != (skeleton ? dry : wet).played().files().size()) {
				failures.add("a " + (skeleton ? "skeleton" : "zombie") + " losing a limb played " + layers + " layers");
			}
		}
	}

	private static void strength(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			List<String> failures) {
		Heard full = play(context, singleplayer, InjurySound.FRACTURE, SoundSet.ARCADE, 1.0F, false, SoundMaterial.FLESH);
		Heard faint = play(context, singleplayer, InjurySound.FRACTURE, SoundSet.ARCADE, 0.0F, false, SoundMaterial.FLESH);
		if (full.played() == null || faint.played() == null) {
			failures.add("strength: nothing played");
			return;
		}
		float ratio = faint.played().layers().get(0).getVolume() / full.played().layers().get(0).getVolume();
		if (Math.abs(ratio - SoundRecipe.DEFAULT_QUIET) > 1.0e-4F) {
			failures.add("a fracture at strength 0 is " + ratio + " of one at full strength, should be "
					+ SoundRecipe.DEFAULT_QUIET);
		}
		// At full strength the top layer is the Injury Volume and nothing else:
		// the recipe as it was tuned.
		float top = full.played().layers().get(0).getVolume();
		if (Math.abs(top - (float) WHISPER) > 1.0e-6F) {
			failures.add("the lead layer of a full-strength fracture is at " + top + ", should be the Injury Volume " + WHISPER);
		}
		float pitch = full.played().layers().get(0).getPitch();
		if (Math.abs(pitch - 0.85F) > 1.0e-6F) {
			failures.add("the lead layer of the Arcade fracture is pitched " + pitch + ", should be 0.85");
		}
	}

	/** A real break, no set named: the listener's own setting decides, and changing it changes the next one. */
	private static void whoseSetting(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			List<String> failures) {
		for (SoundSet set : SoundSet.values()) {
			context.runOnClient(client -> {
				ClientEffectsConfig.get().soundSetValue = set;
				SoundDirector.clearLast();
			});
			singleplayer.getServer().runOnServer(server ->
					Notifier.limbBreaks(server.getPlayerList().getPlayers().getFirst(), BodyPart.LEFT_ARM));
			context.waitTicks(3);
			SoundDirector.Played played = context.computeOnClient(client -> SoundDirector.last());
			if (played == null || played.event() != InjurySound.FRACTURE || played.set() != set) {
				failures.add("with the sound set on " + set.id() + ", a broken arm played "
						+ (played == null ? "nothing" : played.set().id() + "/" + played.event().id()));
			}
		}
		context.runOnClient(client -> ClientEffectsConfig.get().soundSetValue = SoundSet.ARCADE);
	}

	/** Decapitation used to be silent. A zombie's head comes off three blocks away: heard, from there. */
	private static void aHeadComesOff(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			List<String> failures) {
		context.runOnClient(client -> SoundDirector.clearLast());
		double[] where = new double[3];
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			LivingEntity zombie = standing(player.level(), EntityTypes.ZOMBIE,
					player.getX() + 3.0, player.getY(), player.getZ());
			where[0] = zombie.getX();
			where[1] = zombie.getY();
			where[2] = zombie.getZ();
			var data = RDAttachments.get(zombie);
			if (!Decapitation.can(zombie, data)) {
				throw new AssertionError("the test zombie cannot lose its head");
			}
			Decapitation.decapitate(zombie, data, new Vec3(1.0, 0.0, 0.0), null);
		});
		context.waitTicks(3);
		SoundDirector.Played played = context.computeOnClient(client -> SoundDirector.last());
		if (played == null || played.event() != InjurySound.DECAPITATION) {
			failures.add("a zombie's head came off and the client played "
					+ (played == null ? "nothing" : played.event().id()));
		} else {
			SoundInstance lead = played.layers().get(0);
			double off = new Vec3(lead.getX(), lead.getY(), lead.getZ()).distanceTo(new Vec3(where[0], where[1], where[2]));
			if (lead.isRelative() || off > 0.5) {
				failures.add("the decapitation was not heard from the zombie (" + off + " blocks off)");
			}
		}
		singleplayer.getServer().runOnServer(server -> server.getCommands().performPrefixedCommand(
				server.createCommandSourceStack(), "kill @e[type=!player]"));
		context.waitTicks(5);
	}

	/**
	 * Blood drops are small sounds, and a bad bleed makes hundreds. Twenty at
	 * once from the far end of the budget: only the budget's worth start. Then
	 * one right beside the player, which matters more than any of them: it
	 * plays, and the least of the others is stopped for it, unplayed layers
	 * and all.
	 */
	private static void budget(ClientGameTestContext context, List<String> failures) {
		List<SoundDirector.Played> first = new ArrayList<>();
		int[] counts = context.computeOnClient(client -> {
			SoundDirector.clearSmalls();
			Vec3 away = client.player.position().add(14.0, 0.0, 0.0);
			int started = 0;
			for (int i = 0; i < 20; i++) {
				SoundDirector.clearLast();
				SoundDirector.play(InjurySound.BLOOD_SPLAT, SoundSet.ARCADE, false, away, 1.0F, SoundMaterial.FLESH,
						(float) WHISPER);
				if (SoundDirector.last() != null) {
					first.add(SoundDirector.last());
					started++;
				}
			}
			return new int[] {started, SoundDirector.smallsInFlight()};
		});
		if (counts[0] != SoundDirector.SMALL_BUDGET || counts[1] != SoundDirector.SMALL_BUDGET) {
			failures.add("twenty blood drops at once: " + counts[0] + " started and " + counts[1]
					+ " are counted, the budget is " + SoundDirector.SMALL_BUDGET);
			return;
		}
		boolean[] result = context.computeOnClient(client -> {
			SoundDirector.clearLast();
			SoundDirector.play(InjurySound.BLOOD_SPLAT, SoundSet.ARCADE, false, client.player.position().add(1.0, 0.0, 0.0),
					1.0F, SoundMaterial.FLESH, (float) WHISPER);
			boolean played = SoundDirector.last() != null;
			// The one stopped for it has drops seven and twelve ticks out that
			// must never sound now.
			boolean cancelled = false;
			for (SoundDirector.Played earlier : first) {
				boolean anyWaiting = false;
				for (SoundInstance layer : earlier.layers()) {
					anyWaiting |= SoundDirector.isPending(layer);
				}
				cancelled |= !anyWaiting;
			}
			return new boolean[] {played, cancelled, SoundDirector.smallsInFlight() == SoundDirector.SMALL_BUDGET};
		});
		if (!result[0]) {
			failures.add("a blood drop beside the player was refused for eight far ones");
		}
		if (!result[1]) {
			failures.add("the drop stopped to make room kept its unplayed layers");
		}
		if (!result[2]) {
			failures.add("making room left the budget miscounted");
		}
		// And the budget empties by itself.
		context.waitTicks(30);
		boolean[] later = context.computeOnClient(client -> {
			SoundDirector.clearLast();
			SoundDirector.play(InjurySound.BLOOD_SPLAT, SoundSet.ARCADE, false, client.player.position().add(14.0, 0.0, 0.0),
					1.0F, SoundMaterial.FLESH, (float) WHISPER);
			return new boolean[] {SoundDirector.last() != null, SoundDirector.smallsInFlight() == 1};
		});
		if (!later[0] || !later[1]) {
			failures.add("a second and a half later the budget for small sounds had not emptied");
		}
	}

	/** {@code /rdmg sound}, as a player types it, with every argument and with one. */
	private static void command(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			List<String> failures) {
		String[][] cases = {
				{"rdmg sound fracture", "arcade", "fracture", "self"},
				{"rdmg sound collapse grounded", "grounded", "collapse", "self"},
				{"rdmg sound decapitation grounded 0.5 far dry", "grounded", "decapitation", "far"},
				{"rdmg sound wound_open mine 1 self flesh", "arcade", "wound_open", "self"},
		};
		for (String[] c : cases) {
			context.runOnClient(client -> SoundDirector.clearLast());
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				server.getCommands().performPrefixedCommand(
						player.createCommandSourceStack().withPermission(
								net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER), c[0]);
			});
			context.waitTicks(3);
			SoundDirector.Played played = context.computeOnClient(client -> SoundDirector.last());
			if (played == null || !played.set().id().equals(c[1]) || !played.event().id().equals(c[2])
					|| played.layers().get(0).isRelative() != c[3].equals("self")) {
				failures.add("/" + c[0] + " played " + (played == null ? "nothing"
						: played.set().id() + "/" + played.event().id()
								+ (played.layers().get(0).isRelative() ? " self" : " far")));
			}
		}
		// The dry decapitation above is the Grounded file with no wet in it.
		SoundDirector.Played dry = context.computeOnClient(client -> SoundDirector.last());
		context.runOnClient(client -> SoundDirector.clearLast());
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(
					net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER),
					"rdmg sound decapitation grounded 0.5 far dry");
		});
		context.waitTicks(3);
		dry = context.computeOnClient(client -> SoundDirector.last());
		if (dry == null || !dry.files().get(0).getPath().equals("grounded/decapitation_dry_far")) {
			failures.add("a dry far Grounded decapitation played " + (dry == null ? "nothing" : dry.files()));
		}
	}

	/** A body standing still near the player. */
	private static LivingEntity standing(ServerLevel level, net.minecraft.world.entity.EntityType<? extends LivingEntity> type,
			double x, double y, double z) {
		LivingEntity body = type.create(level, EntitySpawnReason.COMMAND);
		body.snapTo(x, y, z, 180.0F, 0.0F);
		((Mob) body).setNoAi(true);
		((Mob) body).setPersistenceRequired();
		body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
		level.addFreshEntity(body);
		RDAttachments.get(body).reset();
		RDAttachments.pushSnapshot(body);
		MobAnatomy.track(body);
		return body;
	}
}
