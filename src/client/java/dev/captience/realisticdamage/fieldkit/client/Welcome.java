package dev.captience.realisticdamage.fieldkit.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Whether the first-launch question has been asked (2.0).
 *
 * <p>Asked the first time the title screen appears with 2.0 — a fresh install
 * and an upgrade alike, because the file it remembers in is new in 2.0 — and
 * never again. Remembered in its own small file rather than in the effects
 * config: it is not a setting, there is nothing to change, and a player who
 * resets their effects to defaults has not asked to be asked again.
 *
 * <p>Marked as asked when the screen is left, by Done or by Escape. A game
 * closed with the screen still up asks again next time, which is the honest
 * answer: the question was never answered.
 *
 * <p>Client game tests share one run directory and expect the title screen,
 * so under {@code -Dfabric.client.gametest} the question waits until a test
 * arms it ({@link #armForTest}).
 */
public final class Welcome {
	private Welcome() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The file's whole content. */
	private static final class State {
		boolean welcomeShown = false;
	}

	private static State state;
	private static boolean armedForTest;
	private static int shownCount;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("realisticdamage-fieldkit.json");
	}

	private static State state() {
		if (state == null) {
			state = new State();
			Path path = path();
			if (Files.exists(path)) {
				try {
					State read = GSON.fromJson(Files.readString(path), State.class);
					if (read != null) {
						state = read;
					}
				} catch (IOException | JsonSyntaxException e) {
					RealisticDamage.LOGGER.error("Could not read {}, asking again", path, e);
				}
			}
		}
		return state;
	}

	private static boolean underTest() {
		return System.getProperty("fabric.client.gametest") != null;
	}

	/** Should the title screen be covered by the question now? */
	public static boolean due() {
		if (underTest() && !armedForTest) {
			return false;
		}
		return !state().welcomeShown;
	}

	/** Called every client tick: swaps a title screen for the question, once. */
	public static void tick(Minecraft client) {
		if (client.gui.screen() instanceof TitleScreen title && due()) {
			shownCount++;
			client.gui.setScreen(new WelcomeScreen(title));
		}
	}

	/** The question was answered (or dismissed): never ask again. */
	public static void answered() {
		state().welcomeShown = true;
		armedForTest = false;
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(state()));
		} catch (IOException e) {
			RealisticDamage.LOGGER.error("Could not write {}", path(), e);
		}
	}

	/** How many times the question has been put up in this session. */
	public static int shownCount() {
		return shownCount;
	}

	/** A test's fresh install: forget the answer and let the question through. */
	public static void armForTest() {
		state().welcomeShown = false;
		armedForTest = true;
	}
}
