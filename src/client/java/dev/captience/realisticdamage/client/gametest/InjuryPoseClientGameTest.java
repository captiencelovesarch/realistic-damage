package dev.captience.realisticdamage.client.gametest;

import java.util.function.Consumer;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.InjuryPose;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.LimpMath;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The limp, on a body, in a running client (1.6).
 *
 * <p>Nothing this feature does changes a number, which is exactly what makes it
 * rot quietly: a mixin that stopped applying, a guard that swallowed every
 * player, a toggle that was never wired to the thing it names. None of that
 * fails a unit test, and none of it fails a server gametest either, because the
 * server never learns that a pose exists. Only a client that has actually drawn
 * a frame can say whether a broken limb is carried differently from a whole
 * one.
 *
 * <p>Four questions, and the second one is the one the release keeps
 * re-learning: does the pose appear when it should, does it appear
 * <em>only</em> when it should, does it stop when the player says stop, and
 * does it stay when the panic button is pressed — because this is information
 * about a body, not something happening to your senses, and the mob AI is about
 * to start acting on it.
 *
 * <p>Every "nothing was posed" assertion is bracketed by a positive one on the
 * same body with the same camera and the same window, so a zero can only mean
 * "suppressed" and never "the render loop had stopped".
 *
 * <p>Registered last under {@code fabric-client-gametest}, and it reloads
 * {@link ClientEffectsConfig} at both ends: the config is a singleton shared by
 * every class in the session, this one deliberately switches pieces of it off,
 * and a leaked flag would silently blank another test's screenshots.
 */
public class InjuryPoseClientGameTest implements FabricClientGameTest {
	/** Long enough for the render loop to have drawn many frames, short enough to be cheap. */
	private static final int WINDOW = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> ClientEffectsConfig.load());
		try {
			run(context);
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	private void run(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			// Third person: the pose is on the player model, and the model is not
			// on screen in first person. That is a scope decision, not an
			// oversight — AvatarRenderer.renderHand resets the arm's pose and
			// submits it with no layers at all, so nothing here could reach it.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);

			// 0. A whole body is not posed. Asserted before anything is broken,
			// so the positive counts below cannot be measuring something that
			// was always running.
			BodySnapshot clean = snapshot(context);
			for (BodyPart part : BodyPart.values()) {
				if (part.isLimb() && rung(clean, part) != LimpMath.RUNG_SOUND) {
					throw new AssertionError("a fresh body arrived limping on " + part);
				}
			}
			if (posesOver(context, WINDOW) != 0) {
				throw new AssertionError("an uninjured body was posed");
			}
			context.takeScreenshot("rd_pose_00_whole");

			// 1. Break a leg. The model's first statement about a fracture in the
			// mod's history: before this the bone snapped, the sound carried, the
			// HUD drew a white line, and the body itself said nothing at all.
			mutate(singleplayer, data -> {
				data.part(BodyPart.LEFT_LEG).setCondition(3.0);
				data.part(BodyPart.LEFT_LEG).setBroken(true);
			});
			context.waitTicks(WINDOW);
			BodySnapshot broken = snapshot(context);
			if (rung(broken, BodyPart.LEFT_LEG) != LimpMath.RUNG_FRACTURED) {
				throw new AssertionError("a broken leg should sit on the fracture rung, got "
						+ rung(broken, BodyPart.LEFT_LEG));
			}
			if (posesOver(context, WINDOW, LimpMath.RUNG_FRACTURED) <= 0) {
				throw new AssertionError("a broken leg was not posed");
			}
			// And only that leg: the sound one has to stay sound, or the pose is
			// saying "this player is hurt" where it should be saying which limb.
			if (posesOver(context, WINDOW, LimpMath.RUNG_FAVOURED) != 0
					|| posesOver(context, WINDOW, LimpMath.RUNG_RUINED) != 0) {
				throw new AssertionError("a break posed limbs on rungs nothing was on");
			}
			context.takeScreenshot("rd_pose_01_broken_leg");
			// And from the front, because a turned-out foot is a silhouette and a
			// silhouette is what the author has to judge. The camera is the only
			// thing that moves between the two.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("rd_pose_01b_broken_leg_front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);

			// 2. Mend it, and the body stands up straight again. This is the pose
			// saying something a scar never can: it is a current condition, and
			// it goes away when the condition does.
			mutate(singleplayer, data -> {
				data.part(BodyPart.LEFT_LEG).setBroken(false);
				data.part(BodyPart.LEFT_LEG).setCondition(BodyPart.LEFT_LEG.maxCondition());
			});
			context.waitTicks(WINDOW);
			if (rung(snapshot(context), BodyPart.LEFT_LEG) != LimpMath.RUNG_SOUND) {
				throw new AssertionError("a mended leg is still on a limp rung");
			}
			if (posesOver(context, WINDOW) != 0) {
				throw new AssertionError("a mended leg kept its limp");
			}
			context.takeScreenshot("rd_pose_02_healed");

			// 3. A leg worn down without breaking — fire, lava, a slow bleed —
			// which is the case the band rung exists for, since every ordinary
			// combat path breaks a limb once it reaches Critical anyway. It is
			// posed, and it is posed on its own rung, which is how the model
			// tells a favoured leg from a fractured one at a glance.
			mutate(singleplayer, data -> data.part(BodyPart.RIGHT_LEG).setCondition(2.0));
			context.waitTicks(WINDOW);
			BodySnapshot worn = snapshot(context);
			if (rung(worn, BodyPart.RIGHT_LEG) != LimpMath.RUNG_FAVOURED) {
				throw new AssertionError("a Critical leg should sit on the favoured rung, got "
						+ rung(worn, BodyPart.RIGHT_LEG));
			}
			if (posesOver(context, WINDOW, LimpMath.RUNG_FAVOURED) <= 0) {
				throw new AssertionError("a Critical leg was not posed");
			}
			if (posesOver(context, WINDOW, LimpMath.RUNG_FRACTURED) != 0) {
				throw new AssertionError("an unbroken leg was posed as a fracture");
			}
			// The angles differ by a factor of three, which is the whole of
			// "distinguishable at a glance" — and it is an angle, so nothing but
			// the constants themselves can be asserted about it here. Against the
			// constants rather than against numbers copied into the test, so the
			// claim keeps tracking if either is ever retuned.
			if (LimpMath.poseWeight(LimpMath.RUNG_FRACTURED)
					<= LimpMath.poseWeight(LimpMath.RUNG_FAVOURED)) {
				throw new AssertionError("a break should be carried harder than a favoured leg");
			}
			context.takeScreenshot("rd_pose_03_favoured_leg");

			// 4. The toggle. Its own switch, because a posture is not a scar.
			//
			// Re-broken first, and every stage from here on re-applies its own
			// injury rather than inheriting the last one's: condition regenerates
			// while the test runs, so a leg parked at Critical for four stages
			// quietly climbs out of the band and the next zero would mean "it got
			// better", which is the one thing a suppression test must not be able
			// to mean.
			breakLeftLeg(singleplayer);
			context.waitTicks(10);
			context.runOnClient(client -> ClientEffectsConfig.get().showInjuryPose = false);
			context.waitTicks(10);
			if (posesOver(context, WINDOW) != 0) {
				throw new AssertionError("showInjuryPose = false left the pose running");
			}
			context.takeScreenshot("rd_pose_04_toggled_off");
			// Back on, same body, same camera, same window. This is what makes
			// the zero above mean "silenced".
			context.runOnClient(client -> ClientEffectsConfig.get().showInjuryPose = true);
			context.waitTicks(10);
			if (posesOver(context, WINDOW) <= 0) {
				throw new AssertionError("switching the pose back on posed nothing");
			}

			// 5. The panic button, which deliberately does NOT take it. Every
			// sensory effect in the mod goes quiet here; this one is information
			// about a body, like the injury icons and the panel's scar marks, and
			// the mob AI is about to prefer targets that are visibly limping —
			// so silencing it would hide the one thing the game is acting on.
			breakLeftLeg(singleplayer);
			context.waitTicks(10);
			context.runOnClient(client -> ClientEffectsConfig.get().disableAllEffects());
			context.waitTicks(10);
			if (posesOver(context, WINDOW) <= 0) {
				throw new AssertionError("the panic button silenced the injury pose");
			}
			context.runOnClient(client -> ClientEffectsConfig.load());
			context.waitTicks(10);

			// 6. A limb that is gone draws nothing — there is no surface to
			// angle, and the empty space says it louder than any pose could.
			// Cleared to a whole body first, so this zero is about the arm and
			// not about a leg that quietly healed.
			mutate(singleplayer, data -> {
				data.reset();
				data.part(BodyPart.LEFT_ARM).setDestroyed(true);
			});
			context.waitTicks(WINDOW);
			BodySnapshot lost = snapshot(context);
			if (rung(lost, BodyPart.LEFT_ARM) != LimpMath.RUNG_LOST) {
				throw new AssertionError("a destroyed arm should sit on the lost rung, got "
						+ rung(lost, BodyPart.LEFT_ARM));
			}
			if (posesOver(context, WINDOW) != 0) {
				throw new AssertionError("a limb that is gone was posed anyway");
			}
			context.takeScreenshot("rd_pose_05_lost_arm");

			// 7. Held items are a separate render layer, so making the model part
			// invisible is not enough: without an explicit guard the tool and shield
			// hover at the ends of arms that are no longer there. Start with two
			// visible items, remove one physical arm, then the other. Inspecting the
			// freshly extracted render state makes this a regression assertion rather
			// than asking a screenshot to judge whether a few pixels look detached.
			mutate(singleplayer, RealisticDamageData::reset);
			equipHands(singleplayer, true);
			context.waitTicks(WINDOW);
			assertHeldItem(context, HumanoidArm.RIGHT, true, "a sound right arm lost its held item");
			assertHeldItem(context, HumanoidArm.LEFT, true, "a sound left arm lost its held item");
			mutate(singleplayer, data -> data.part(BodyPart.RIGHT_ARM).setDestroyed(true));
			context.waitTicks(WINDOW);
			assertHeldItem(context, HumanoidArm.RIGHT, false, "an item floated beside the destroyed right arm");
			assertHeldItem(context, HumanoidArm.LEFT, true, "destroying the right arm hid the left-hand item");
			mutate(singleplayer, data -> data.part(BodyPart.LEFT_ARM).setDestroyed(true));
			context.waitTicks(WINDOW);
			assertHeldItem(context, HumanoidArm.RIGHT, false, "the right-hand item returned with both arms gone");
			assertHeldItem(context, HumanoidArm.LEFT, false, "an item floated beside the destroyed left arm");
			assertInventoryItemsRemain(context);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("rd_pose_06_no_floating_items");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			equipHands(singleplayer, false);
			context.waitTicks(5);

			// 8. An arm, which is carried rather than swung: pulled in against
			// the ribs and held forward. The mirror of the leg case, and the one
			// the author has to judge by eye, because "does it read as a body"
			// is not a thing a counter can answer.
			mutate(singleplayer, data -> {
				data.reset();
				data.part(BodyPart.RIGHT_ARM).setCondition(2.0);
				data.part(BodyPart.RIGHT_ARM).setBroken(true);
			});
			context.waitTicks(WINDOW);
			if (posesOver(context, WINDOW, LimpMath.RUNG_FRACTURED) <= 0) {
				throw new AssertionError("a broken arm was not carried");
			}
			context.takeScreenshot("rd_pose_07_broken_arm");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("rd_pose_07b_broken_arm_front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);

			// 9. In armour, which is the whole reason this hooks HumanoidModel and
			// not PlayerModel. The armour layer renders its own HumanoidModel from
			// the same render state, so a pose applied to the player's model alone
			// would rotate the limb and leave the plate behind — at the cradle's
			// 17° a twelve-unit arm displaces about 3.5 units, far outside
			// armour's 1.0 inflation, and the limb would come through the metal.
			// One injection point catches both, and the proof is arithmetic: the
			// same body under the same camera in the same window poses strictly
			// more limbs once it is dressed, because there is more than one model
			// to pose. (The armour layer submits an inner and an outer set, so the
			// real ratio is about threefold; the assertion only asks for more.)
			mutate(singleplayer, RealisticDamageData::reset);
			breakLeftLeg(singleplayer);
			context.waitTicks(WINDOW);
			long bare = posesOver(context, WINDOW);
			if (bare <= 0) {
				throw new AssertionError("an undressed broken leg was not posed");
			}
			dress(singleplayer, true);
			context.waitTicks(WINDOW);
			long dressed = posesOver(context, WINDOW);
			if (dressed <= bare) {
				throw new AssertionError("the armour did not follow the limb it covers: "
						+ bare + " poses bare against " + dressed + " dressed");
			}
			context.takeScreenshot("rd_pose_08_armoured");
			dress(singleplayer, false);
			context.waitTicks(5);

			// 10. And the limp in motion, which is where the shortened step lives:
			// standing still, the pose is only a turned-out foot. Last, after
			// every assertion, because walking is the one thing in this class
			// that can move the player somewhere the next stage did not expect.
			mutate(singleplayer, RealisticDamageData::reset);
			breakLeftLeg(singleplayer);
			context.waitTicks(10);
			context.getInput().holdKey(options -> options.keyUp);
			try {
				context.waitTicks(12);
				context.takeScreenshot("rd_pose_09_walking_a");
				context.waitTicks(4);
				context.takeScreenshot("rd_pose_10_walking_b");
			} finally {
				context.getInput().releaseKey(options -> options.keyUp);
			}
			context.waitTicks(5);

			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(5);
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(5);
		}
	}

	/** Puts a deliberately obvious item in each hand, or clears both again. */
	private void equipHands(TestSingleplayerContext singleplayer, boolean on) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.setItemInHand(InteractionHand.MAIN_HAND,
						on ? new ItemStack(Items.DIAMOND_AXE) : ItemStack.EMPTY);
				player.setItemInHand(InteractionHand.OFF_HAND,
						on ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
			}
		});
	}

	/** Verifies the actual per-frame item state consumed by the third-person layer. */
	private void assertHeldItem(ClientGameTestContext context, HumanoidArm arm, boolean expected, String why) {
		boolean rendered = context.computeOnClient(client -> {
			if (client.player == null) {
				throw new AssertionError("the client has no player to render");
			}
			AvatarRenderState state = (AvatarRenderState) client.getEntityRenderDispatcher()
					.extractEntity(client.player, 0.0F);
			return !(arm == HumanoidArm.RIGHT ? state.rightHandItemState : state.leftHandItemState).isEmpty();
		});
		if (rendered != expected) {
			throw new AssertionError(why);
		}
	}

	/** The render fix must hide stacks, never delete them from the actual hands. */
	private void assertInventoryItemsRemain(ClientGameTestContext context) {
		boolean intact = context.computeOnClient(client -> client.player != null
				&& client.player.getMainHandItem().is(Items.DIAMOND_AXE)
				&& client.player.getOffhandItem().is(Items.SHIELD));
		if (!intact) {
			throw new AssertionError("hiding orphaned held items changed the player's inventory");
		}
	}

	/** Iron over the broken leg, and the arms, or bare skin again. */
	private void dress(TestSingleplayerContext singleplayer, boolean on) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.setItemSlot(EquipmentSlot.LEGS,
						on ? new ItemStack(Items.IRON_LEGGINGS) : ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.CHEST,
						on ? new ItemStack(Items.IRON_CHESTPLATE) : ItemStack.EMPTY);
			}
		});
	}

	/**
	 * Breaks the left leg, low enough that the break is not the only thing wrong
	 * with it. Its own method because every stage that measures a suppression has
	 * to re-apply the injury first — see stage 4.
	 */
	private void breakLeftLeg(TestSingleplayerContext singleplayer) {
		mutate(singleplayer, data -> {
			data.part(BodyPart.LEFT_LEG).setCondition(3.0);
			data.part(BodyPart.LEFT_LEG).setBroken(true);
		});
	}

	/** The rung the shared definition puts a region on, read off the synced body. */
	private static int rung(BodySnapshot snapshot, BodyPart part) {
		return LimpMath.rung(snapshot.destroyed(part), snapshot.broken(part), snapshot.fraction(part));
	}

	/**
	 * Limbs posed over the next {@code ticks} ticks.
	 *
	 * <p>A delta rather than an absolute, because the counter is monotonic over
	 * the whole session. Sampled in this direction on purpose: a stalled render
	 * loop produces zero, which fails a "something was posed" assertion instead
	 * of passing one.
	 */
	private long posesOver(ClientGameTestContext context, int ticks) {
		long before = context.computeOnClient(client -> InjuryPose.poseApplications());
		context.waitTicks(ticks);
		return context.computeOnClient(client -> InjuryPose.poseApplications()) - before;
	}

	/**
	 * The same window narrowed to one rung. Needed because the rungs differ only
	 * in the size of an angle, which leaves no trace a gametest can read off the
	 * screen — so "a break was carried and a merely worn leg was not" is only
	 * sayable if the counter knows which rung it counted.
	 */
	private long posesOver(ClientGameTestContext context, int ticks, int rung) {
		long before = context.computeOnClient(client -> InjuryPose.poseApplications(rung));
		context.waitTicks(ticks);
		return context.computeOnClient(client -> InjuryPose.poseApplications(rung)) - before;
	}

	/** Mutates server-side state, exactly as gameplay would. */
	private void mutate(TestSingleplayerContext singleplayer, Consumer<RealisticDamageData> action) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				action.accept(RDAttachments.get(player));
				RDAttachments.pushSnapshot(player);
			}
		});
	}

	/** The snapshot the client is holding right now. */
	private BodySnapshot snapshot(ClientGameTestContext context) {
		BodySnapshot snapshot = context.computeOnClient(client ->
				client.player == null ? null : client.player.getAttached(RDAttachments.SNAPSHOT));
		if (snapshot == null) {
			throw new AssertionError("the client is holding no body snapshot");
		}
		return snapshot;
	}
}
