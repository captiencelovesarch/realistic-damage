package dev.captience.realisticdamage.client.gametest;

import java.util.List;
import java.util.function.Consumer;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.client.BloodBodyLayer;
import dev.captience.realisticdamage.client.BodyHud;
import dev.captience.realisticdamage.client.BodyInspector;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;

/**
 * The marks themselves, drawn for real (1.6).
 *
 * <p>Everything this feature adds is cosmetic, which makes it exactly the kind
 * of thing that rots quietly: a seam that stopped drawing, a hover line that
 * survived a toggle it should have obeyed, a model pass that never ran. None of
 * those changes a number anywhere, so nothing but a client that has actually
 * rendered a frame can catch them. The four questions asked here are whether
 * each surface draws when it should, whether it draws <em>only</em> when it
 * should, whether it stops when the player says stop, and whether a mark ever
 * manages to look like an injury to the code that decides whether the player is
 * hurt.
 *
 * <p>The second of those is new in beta4 and is the one the first pass missed.
 * Every assertion here used to be of the form "the seam appeared", which a mark
 * drawn on the wrong body state passes just as happily as a mark drawn on the
 * right one — and that is exactly how a currently broken limb came to wear the
 * seam of its own mending for a release.
 *
 * <p>Registered last under {@code fabric-client-gametest} and it calls
 * {@link ClientEffectsConfig#load()} at both ends: the config is a singleton
 * shared by every class in the session, this class deliberately turns pieces of
 * it off, and a leaked flag would silently blank another test's screenshots.
 */
public class ScarClientGameTest implements FabricClientGameTest {
	/** Long enough for the render loop to have drawn many frames, short enough to be cheap. */
	private static final int WINDOW = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		// Defensive at the start for the same reason it is unconditional at the
		// end: a predecessor may have left the config panic-buttoned, and this
		// whole class is about things being drawn.
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
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);

			// 0. A clean body draws no marks. Asserted before anything is scarred,
			// so a later "> 0" cannot be passing on something that was always on.
			BodySnapshot clean = snapshot(context);
			for (BodyPart part : BodyPart.values()) {
				if (clean.scar(part) != ScarLevel.NONE) {
					throw new AssertionError("a fresh body arrived already scarred on " + part);
				}
			}
			if (scarSubmitsOver(context, WINDOW) != 0) {
				throw new AssertionError("an unscarred body drew scars");
			}

			// 1. Break an arm, and look at it before it mends. The mark is
			// already set — setBroken raises ScarLevel.BROKEN inside the setter,
			// which is right, because /rdmg break and the dismemberment path both
			// go round BreakLogic — so this is the one moment where the mask says
			// "scarred" and the truth says "still broken", and the model must
			// keep quiet. Beta3 is where that showed: a fresh fracture drew the
			// pale seam of its own healing and so looked like nothing at all.
			//
			// This is the assertion the class was missing. Every scar check below
			// asks whether a seam APPEARS; none of them asked whether it appears
			// ONLY when it is true, and that is the shape of the bug.
			mutate(singleplayer, data -> {
				data.part(BodyPart.LEFT_ARM).setCondition(2.0);
				data.part(BodyPart.LEFT_ARM).setBroken(true);
			});
			context.waitTicks(WINDOW);
			BodySnapshot fractured = snapshot(context);
			if (!fractured.broken(BodyPart.LEFT_ARM)
					|| fractured.scar(BodyPart.LEFT_ARM) != ScarLevel.BROKEN) {
				throw new AssertionError("a broken arm should be broken and already marked, got "
						+ fractured.broken(BodyPart.LEFT_ARM) + "/"
						+ fractured.scar(BodyPart.LEFT_ARM));
			}
			if (scarSubmitsOver(context, WINDOW, ScarLevel.BROKEN) != 0) {
				throw new AssertionError("an unhealed break wore the seam of its own mending");
			}
			// The before half of rd_scar_00_model: the same arm, the same camera,
			// nothing on it. What a break looks like on the model is the injury
			// pose's problem, and the pose is deferred to be built with the mob AI
			// that has to read the same limp.
			context.takeScreenshot("rd_scar_00_broken");

			// 2. Now heal it all the way back. The mark is the only thing left
			// over: nothing else about the region still says it ever happened,
			// which is the whole feature in one assertion.
			mutate(singleplayer, data -> data.part(BodyPart.LEFT_ARM).setCondition(13.0));
			context.waitTicks(WINDOW);
			BodySnapshot healed = snapshot(context);
			if (healed.scar(BodyPart.LEFT_ARM) != ScarLevel.BROKEN
					|| healed.broken(BodyPart.LEFT_ARM)
					|| healed.state(BodyPart.LEFT_ARM) != BodyState.HEALTHY) {
				throw new AssertionError("a mended arm should be healthy and marked, got "
						+ healed.scar(BodyPart.LEFT_ARM) + "/" + healed.broken(BodyPart.LEFT_ARM)
						+ "/" + healed.state(BodyPart.LEFT_ARM));
			}

			// 3. And it is on the model — the same limb, the same window and the
			// same camera as the zero above, which is what makes that zero mean
			// "suppressed while broken" rather than "nothing was drawing".
			context.waitTicks(10);
			if (scarSubmitsOver(context, WINDOW, ScarLevel.BROKEN) <= 0) {
				throw new AssertionError("a mended arm drew no scar pass on the model");
			}
			context.takeScreenshot("rd_scar_00_model");

			// 4. The panel: a seam on that region and no other, and one sentence
			// for it. Both halves — the region that must be marked and the one
			// that must not — because a whole-body gate would pass the first.
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(5);
			hover(context, BodyPart.LEFT_ARM);
			if (context.computeOnClient(client -> BodyInspector.hoveredPart()) != BodyPart.LEFT_ARM) {
				throw new AssertionError("the hover never resolved to the scarred arm");
			}
			if (!context.computeOnClient(client -> BodyInspector.scarMarkDrawn(BodyPart.LEFT_ARM))) {
				throw new AssertionError("the panel drew no seam on a scarred arm");
			}
			int marks = context.computeOnClient(client -> BodyInspector.scarMarks());
			if (marks != 1) {
				throw new AssertionError("one region is scarred and the panel marked " + marks);
			}
			if (!detailKeys(context).contains("realisticdamage.detail.scarred.broken")) {
				throw new AssertionError("the hover box never said the arm was scarred: "
						+ detailKeys(context));
			}
			context.takeScreenshot("rd_scar_01_panel");
			hover(context, BodyPart.RIGHT_ARM);
			if (context.computeOnClient(client -> BodyInspector.scarMarkDrawn(BodyPart.RIGHT_ARM))) {
				throw new AssertionError("an unmarked arm got a seam");
			}
			if (hasScarKey(detailKeys(context))) {
				throw new AssertionError("the hover box described a scar on an unmarked arm: "
						+ detailKeys(context));
			}
			context.setScreen(() -> null);
			context.waitTicks(3);

			// 5. The fade. A body whose only mark is a scar is a healed body, and
			// a healed body's HUD goes away — otherwise one bad break in April
			// pins the diagram on screen for the rest of the character's life.
			context.waitTicks(80);
			double visibility = context.computeOnClient(client -> BodyHud.visibility());
			if (visibility >= 0.05) {
				throw new AssertionError("a scar kept the HUD open, at visibility " + visibility);
			}

			// 6. The panic button. It silences the paint on the body, because that
			// is paint on a body — and it leaves the panel alone, because the
			// panel is information, like the injury icons.
			context.runOnClient(client -> ClientEffectsConfig.get().disableAllEffects());
			context.waitTicks(10);
			if (scarSubmitsOver(context, WINDOW) != 0) {
				throw new AssertionError("the panic button left the scar pass running");
			}
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(5);
			hover(context, BodyPart.LEFT_ARM);
			if (!context.computeOnClient(client -> BodyInspector.scarMarkDrawn(BodyPart.LEFT_ARM))
					|| !detailKeys(context).contains("realisticdamage.detail.scarred.broken")) {
				throw new AssertionError("the panic button took the panel's marks with it");
			}
			context.takeScreenshot("rd_scar_02_panic");
			context.setScreen(() -> null);
			context.runOnClient(client -> ClientEffectsConfig.load());
			context.waitTicks(10);

			// 7. The display toggle, which is the other half of stage 6: this one
			// is the switch that means "I do not want to see scars", so it takes
			// the mark and the sentence together. A toggle that hid the seam and
			// kept the line describing it would be worse than either half.
			context.runOnClient(client -> ClientEffectsConfig.get().showScars = false);
			context.waitTicks(10);
			if (scarSubmitsOver(context, WINDOW) != 0) {
				throw new AssertionError("showScars = false left the model pass running");
			}
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(5);
			hover(context, BodyPart.LEFT_ARM);
			if (context.computeOnClient(client -> BodyInspector.scarMarkDrawn(BodyPart.LEFT_ARM))) {
				throw new AssertionError("showScars = false still drew the seam");
			}
			if (hasScarKey(detailKeys(context))) {
				throw new AssertionError("showScars = false hid the seam and kept the sentence: "
						+ detailKeys(context));
			}
			context.takeScreenshot("rd_scar_03_hidden");
			context.setScreen(() -> null);
			context.waitTicks(3);
			// And back on again. This is what makes the two zero deltas above mean
			// something: the same camera, the same window and the same body give a
			// positive count the moment the flag returns, so a stalled render loop
			// cannot have faked either pass.
			context.runOnClient(client -> ClientEffectsConfig.get().showScars = true);
			context.waitTicks(10);
			if (scarSubmitsOver(context, WINDOW) <= 0) {
				throw new AssertionError("switching scars back on drew nothing");
			}

			// 8. A destroyed region: marked, but not seamed. The cross already
			// says everything a mark could there — and the sentence is still
			// printed, because the words are the half that can say "once lost".
			mutate(singleplayer, data -> data.part(BodyPart.RIGHT_ARM).setDestroyed(true));
			context.waitTicks(WINDOW);
			if (snapshot(context).scar(BodyPart.RIGHT_ARM) != ScarLevel.DESTROYED) {
				throw new AssertionError("destroying an arm left it unmarked");
			}
			// Nor on the model, and for the older of the two reasons: the limb is
			// hidden, so there is no surface. Worth pinning next to the broken
			// case, because it is what makes the heavy mark in the stage below
			// specifically the mark of a limb that came BACK.
			if (scarSubmitsOver(context, WINDOW, ScarLevel.DESTROYED) != 0) {
				throw new AssertionError("a limb that is gone drew a seam on nothing");
			}
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(5);
			hover(context, BodyPart.RIGHT_ARM);
			if (context.computeOnClient(client -> BodyInspector.scarMarkDrawn(BodyPart.RIGHT_ARM))) {
				throw new AssertionError("a destroyed region got a seam under its own cross");
			}
			if (!detailKeys(context).contains("realisticdamage.detail.scarred.destroyed")) {
				throw new AssertionError("a lost arm's hover box never said so: " + detailKeys(context));
			}
			context.setScreen(() -> null);
			context.waitTicks(3);

			// 9. Death. The only stage that runs the real respawn path, and the
			// only proof that the carry-over listener fires in a live client
			// session rather than only under a mock player. The right arm comes
			// back whole and still marked: the seam is now on an arm that exists.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.hurtServer(player.level(),
							player.level().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
				}
			});
			context.waitTicks(10);
			context.clickScreenButton("deathScreen.respawn");
			context.waitFor(client -> client.player != null
					&& client.player.getAttached(RDAttachments.SNAPSHOT) != null, 100);
			context.waitTicks(WINDOW);
			BodySnapshot reborn = snapshot(context);
			if (reborn.scar(BodyPart.LEFT_ARM) != ScarLevel.BROKEN
					|| reborn.scar(BodyPart.RIGHT_ARM) != ScarLevel.DESTROYED
					|| reborn.destroyed(BodyPart.RIGHT_ARM)
					|| reborn.state(BodyPart.RIGHT_ARM) != BodyState.HEALTHY) {
				throw new AssertionError("the new body did not carry the marks and only the marks");
			}
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			// Both rungs at once, on one body, in one window: the left arm's
			// mended break and the right arm's regrown limb, drawn at two
			// different weights. That is the model doing what the panel does with
			// one seam against two, and it is the whole of the "does a mended
			// break look different from a limb that grew back" question — no
			// third ScarLevel, because the data already had both rungs and only
			// the presentation was missing. (A bone that is broken right now is
			// not a third kind of history; it is a current condition, and its cue
			// is the deferred pose.)
			long paleBefore = scarSubmits(context, ScarLevel.BROKEN);
			long heavyBefore = scarSubmits(context, ScarLevel.DESTROYED);
			context.waitTicks(WINDOW);
			long pale = scarSubmits(context, ScarLevel.BROKEN) - paleBefore;
			long heavy = scarSubmits(context, ScarLevel.DESTROYED) - heavyBefore;
			if (pale <= 0 || heavy <= 0) {
				throw new AssertionError("the respawned body drew " + pale + " mended-break marks and "
						+ heavy + " regrown-limb marks; both should be positive");
			}
			if (BloodBodyLayer.scarWeight(ScarLevel.DESTROYED)
					<= BloodBodyLayer.scarWeight(ScarLevel.BROKEN)) {
				throw new AssertionError("a limb that was lost should be marked more heavily than "
						+ "one that was only broken");
			}
			// The only picture of both rungs on one body. The counters prove which
			// was drawn; a mark this faint is easier to believe in a screenshot
			// than to measure in one, so the two live side by side here.
			context.takeScreenshot("rd_scar_04_two_rungs");
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(5);
			hover(context, BodyPart.RIGHT_ARM);
			if (!detailKeys(context).contains("realisticdamage.detail.scarred.destroyed")) {
				throw new AssertionError("the once-lost arm lost its sentence in the respawn: "
						+ detailKeys(context));
			}
			context.takeScreenshot("rd_scar_04_respawned");
			context.setScreen(() -> null);
			context.waitTicks(3);

			// 10. And the debug heal forgets everything, marks included — the one
			// place in the mod where history is allowed to go away.
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(WINDOW);
			BodySnapshot wiped = snapshot(context);
			for (BodyPart part : BodyPart.values()) {
				if (wiped.scar(part) != ScarLevel.NONE) {
					throw new AssertionError("/rdmg heal left a mark on " + part);
				}
			}
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(5);
		}
	}

	/** True if the box printed any of the scar lines at all. */
	private static boolean hasScarKey(List<String> keys) {
		return keys.stream().anyMatch(key -> key.startsWith("realisticdamage.detail.scarred."));
	}

	private List<String> detailKeys(ClientGameTestContext context) {
		return context.computeOnClient(client -> BodyInspector.lastDetailKeys());
	}

	/**
	 * Scar submits drawn over the next {@code ticks} ticks.
	 *
	 * <p>A delta rather than an absolute, because the counter is monotonic
	 * across the whole session and every earlier stage has already added to it.
	 * Sampled in this direction on purpose: a render loop that has stalled
	 * produces zero, which fails a "drew something" assertion instead of passing
	 * one.
	 */
	private long scarSubmitsOver(ClientGameTestContext context, int ticks) {
		long before = context.computeOnClient(client -> BloodBodyLayer.scarSubmits());
		context.waitTicks(ticks);
		long after = context.computeOnClient(client -> BloodBodyLayer.scarSubmits());
		return after - before;
	}

	/**
	 * The same window, narrowed to one rung. Needed because the two marks differ
	 * only in tint and opacity — nothing a gametest can read off the screen — so
	 * "the mended break drew and the unhealed one did not" is only sayable if the
	 * counter knows which rung it counted.
	 */
	private long scarSubmitsOver(ClientGameTestContext context, int ticks, ScarLevel level) {
		long before = scarSubmits(context, level);
		context.waitTicks(ticks);
		return scarSubmits(context, level) - before;
	}

	private long scarSubmits(ClientGameTestContext context, ScarLevel level) {
		return context.computeOnClient(client -> BloodBodyLayer.scarSubmits(level));
	}

	/** Puts the real cursor over the centre of a body region and lets it render. */
	private void hover(ClientGameTestContext context, BodyPart part) {
		int[] centre = context.computeOnClient(client -> BodyInspector.regionCenter(part));
		if (centre == null) {
			throw new AssertionError("the inspector never drew " + part);
		}
		setCursor(context, centre[0], centre[1]);
		context.waitTicks(2);
	}

	/**
	 * Cursor positions are in physical window pixels, but the panel lays itself
	 * out in GUI-scaled ones, so the two have to be converted between or every
	 * hover lands in the wrong place on any scale but 1.
	 */
	private void setCursor(ClientGameTestContext context, int guiX, int guiY) {
		double[] raw = context.computeOnClient(client -> {
			var window = client.getWindow();
			double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
			double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
			return new double[] {(guiX + 0.5) * scaleX, (guiY + 0.5) * scaleY};
		});
		context.getInput().setCursorPos(raw[0], raw[1]);
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
