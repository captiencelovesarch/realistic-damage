package dev.captience.realisticdamage.client.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.captience.realisticdamage.client.AccessibilityScreen;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The settings page after a preset (2.0 fix). Pressing Arcade, Grounded or the
 * panic button rebuilds the page, and every rebuild used to stack another list
 * on the last: the new one drew on top while the wheel and the clicks went to
 * the stale one underneath. So the page would not scroll and its buttons
 * seemed not to change, which a screenshot of the page cannot show — the top
 * list looked right. Driven here the way a player does it, with a real click,
 * the wheel and another click, so it is the list on screen that has to answer.
 */
public class SettingsScreenClientGameTest implements FabricClientGameTest {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("realisticdamage-client.json");

	@Override
	public void runTest(ClientGameTestContext context) {
		// The screen saves when it closes, and the preset would outlive this
		// test in the file every later test loads. Put it back afterwards.
		byte[] saved = context.computeOnClient(client -> {
			ClientEffectsConfig.save();
			try {
				return Files.readAllBytes(FILE);
			} catch (java.io.IOException e) {
				throw new AssertionError("could not read " + FILE, e);
			}
		});
		try {
			presetThenScrollAndClick(context);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				try {
					Files.write(FILE, saved);
				} catch (java.io.IOException e) {
					throw new AssertionError("could not restore " + FILE, e);
				}
				ClientEffectsConfig.load();
			});
		}
	}

	private void presetThenScrollAndClick(ClientGameTestContext context) {
		// Start from Grounded so that pressing Arcade visibly changes something.
		context.runOnClient(client -> {
			ClientEffectsConfig.get().applyGroundedPreset();
			ClientEffectsConfig.get().screams = true;
		});
		context.setScreen(() -> new AccessibilityScreen(null));
		context.waitTicks(5);

		String arcade = Component.translatable("realisticdamage.config.preset").append(": ")
				.append(Component.translatable("realisticdamage.config.preset.arcade")).getString();
		click(context, client -> widget(client, w -> w.getMessage().getString().equals(arcade), arcade));
		context.waitTicks(3);
		double vignette = context.computeOnClient(client -> ClientEffectsConfig.get().vignetteIntensity);
		if (vignette != 0.85) {
			throw new AssertionError("pressing " + arcade + " did not apply it, vignette at " + vignette);
		}

		// Pain Screams sits near the foot of the page, well out of view at the
		// top, so reaching it takes the wheel on the list the player can see.
		String screams = Component.translatable("realisticdamage.config.screams").getString() + ":";
		Function<Minecraft, AbstractWidget> target = client -> widget(client,
				w -> w instanceof CycleButton<?> && w.getMessage().getString().startsWith(screams), screams);
		reveal(context, target);
		double scrolled = context.computeOnClient(client -> list(client).scrollAmount());
		if (scrolled <= 0) {
			throw new AssertionError("the list on screen never scrolled after a preset");
		}
		context.takeScreenshot("rd_05b_settings_after_preset");

		click(context, target);
		context.waitTicks(3);
		boolean[] after = context.computeOnClient(client -> new boolean[] {
				ClientEffectsConfig.get().screams,
				(Boolean) ((CycleButton<?>) target.apply(client)).getValue()});
		if (after[0]) {
			throw new AssertionError("clicking Pain Screams after a preset did not switch it off");
		}
		if (after[1]) {
			throw new AssertionError("Pain Screams switched off but the button on screen still says on");
		}

		// And the reason it used to fail: one list, however many rebuilds.
		long lists = context.computeOnClient(client ->
				client.gui.screen().children().stream().filter(OptionsList.class::isInstance).count());
		if (lists != 1) {
			throw new AssertionError("the settings page holds " + lists + " lists after a preset");
		}
	}

	/** The list drawn on top: the last one added, which is the one the player sees. */
	private static OptionsList list(Minecraft client) {
		if (!(client.gui.screen() instanceof AccessibilityScreen screen)) {
			throw new AssertionError("the settings page is not open");
		}
		List<OptionsList> lists = screen.children().stream()
				.filter(OptionsList.class::isInstance).map(OptionsList.class::cast).toList();
		if (lists.isEmpty()) {
			throw new AssertionError("the settings page has no list");
		}
		return lists.getLast();
	}

	private static AbstractWidget widget(Minecraft client, Predicate<AbstractWidget> match, String what) {
		AbstractWidget found = find(list(client), match);
		if (found == null) {
			throw new AssertionError("no " + what + " on the settings page");
		}
		return found;
	}

	private static AbstractWidget find(GuiEventListener at, Predicate<AbstractWidget> match) {
		if (at instanceof AbstractWidget widget && !(at instanceof OptionsList) && match.test(widget)) {
			return widget;
		}
		if (at instanceof ContainerEventHandler container) {
			for (GuiEventListener child : container.children()) {
				AbstractWidget found = find(child, match);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * Rolls the wheel down over the list until the widget is wholly inside it.
	 * Not by computing a scroll amount: a list only places the rows it draws,
	 * and a row that has never been on screen still reports where it was built.
	 */
	private static void reveal(ClientGameTestContext context, Function<Minecraft, AbstractWidget> target) {
		for (int turn = 0; turn < 150; turn++) {
			int[] where = context.computeOnClient(client -> {
				OptionsList list = list(client);
				AbstractWidget widget = target.apply(client);
				return new int[] {widget.getY(), widget.getBottom(), list.getY(), list.getBottom(),
						list.getX() + list.getWidth() / 2, (list.getY() + list.getBottom()) / 2};
			});
			if (where[0] >= where[2] && where[1] <= where[3]) {
				return;
			}
			double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			context.getInput().setCursorPos(where[4] * scale, where[5] * scale);
			context.getInput().scroll(-1);
			context.waitTicks(2);
		}
		double scrolled = context.computeOnClient(client -> list(client).scrollAmount());
		throw new AssertionError("could not scroll Pain Screams into view; the list on screen is at "
				+ scrolled + " after 150 turns of the wheel");
	}

	/** A real left click at the middle of a widget, in window pixels. */
	private static void click(ClientGameTestContext context, Function<Minecraft, AbstractWidget> widget) {
		double[] at = context.computeOnClient(client -> {
			AbstractWidget target = widget.apply(client);
			double scale = client.getWindow().getGuiScale();
			return new double[] {(target.getX() + target.getWidth() / 2.0) * scale,
					(target.getY() + target.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}
}
