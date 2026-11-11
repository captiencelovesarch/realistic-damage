package dev.captience.realisticdamage.fieldkit.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.captience.realisticdamage.command.RealisticDamageCommands;
import dev.captience.realisticdamage.fieldkit.RulesView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * "World rules" (2.0): the server's settings, in a screen. Until now they were
 * {@code /rdmg config} and nothing else, which broke the rule that nobody has to
 * type or edit anything to change one.
 *
 * <p>Every {@code ServerConfig} field appears, found the same way the command
 * finds them, so a setting added later turns up here without anybody wiring it:
 * one the groups below do not name lands under "Other". In singleplayer (and on
 * the title screen) it edits this install's settings directly; on a server an
 * operator's change goes through {@code /rdmg config} and everyone else reads
 * (see {@link LocalRules}).
 */
public class WorldRulesScreen extends OptionsSubScreen {
	/** Settings by section. Anything not named here goes under Other, never missing. */
	private static final Map<String, List<String>> SECTIONS = new LinkedHashMap<>();

	static {
		SECTIONS.put("field_kit", List.of("enableWeapons", "enableMedicine"));
		SECTIONS.put("injuries", List.of("enableHitLocation", "enableBleeding", "enableBrokenLimbs",
				"enableConsciousness", "enableBurns", "enableMicrosleeps", "disabledArmsLoseGrip",
				"enableAdrenaline", "enableLongTermInjuries", "enableWhiplash"));
		SECTIONS.put("destruction", List.of("enableLimbDestruction", "enableExplosiveDismemberment",
				"enableDecapitation", "enableBrokenNecks", "spawnSeveredLimbs"));
		SECTIONS.put("falls", List.of("softenFallLethality", "enableKnockdown", "knockdownFromBlows"));
		SECTIONS.put("mobs", List.of("enableMobAnatomy", "unconsciousAggro", "preyOnWounded"));
		SECTIONS.put("scaling", List.of("bodyDamageMultiplier", "bleedingRateMultiplier",
				"consciousnessLossMultiplier", "healingMultiplier", "fallTraumaMultiplier", "pvpTraumaMultiplier"));
	}

	/** The highest a slider goes. The command takes up to 100; past 3 a value shows as it is until moved. */
	private static final double SLIDER_MAX = 3.0;

	/** Unsent changes for a remote server, by field, so a dragged slider is one command and not fifty. */
	private final Map<Field, String> pending = new LinkedHashMap<>();
	private int pendingAge;
	private int seenVersion;
	private boolean seenCanEdit;
	private final List<AbstractWidget> controls = new ArrayList<>();
	private final Map<String, AbstractWidget> byName = new LinkedHashMap<>();

	public WorldRulesScreen(@Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("realisticdamage.rules.title"));
	}

	/**
	 * Vanilla's options screens never rebuild, so their layout keeps whatever
	 * was added to it: rebuilding this one (as it does whenever the server
	 * reports a change) would stack a second list on the first, with the wheel
	 * and the clicks going to the stale one underneath. Start from empty.
	 */
	@Override
	protected void init() {
		layout.removeChildren();
		super.init();
	}

	/** The title and, under it, whose rules these are and whether you can change them. */
	@Override
	protected void addTitle() {
		String note = !LocalRules.remote() ? "local" : (RulesView.canEdit() ? "operator" : "read_only");
		layout.setHeaderHeight(44);
		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(5));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(title, font));
		header.addChild(new StringWidget(Component.translatable("realisticdamage.rules.note." + note)
				.withStyle(style -> style.withColor(0xA0A0A0)), font));
	}

	@Override
	protected void addOptions() {
		controls.clear();
		byName.clear();
		seenVersion = RulesView.version();
		seenCanEdit = LocalRules.canEdit();
		Set<String> placed = new LinkedHashSet<>();
		Map<String, Field> fields = new LinkedHashMap<>();
		for (Field field : RealisticDamageCommands.settingFields()) {
			fields.put(field.getName(), field);
		}
		for (Map.Entry<String, List<String>> section : SECTIONS.entrySet()) {
			List<AbstractWidget> row = new ArrayList<>();
			for (String name : section.getValue()) {
				Field field = fields.get(name);
				if (field != null) {
					row.add(control(field));
					placed.add(name);
				}
			}
			addSection(section.getKey(), row);
		}
		List<AbstractWidget> other = new ArrayList<>();
		for (Field field : fields.values()) {
			if (!placed.contains(field.getName())) {
				other.add(control(field));
			}
		}
		addSection("other", other);
	}

	private void addSection(String key, List<AbstractWidget> widgets) {
		if (widgets.isEmpty()) {
			return;
		}
		list.addHeader(Component.translatable("realisticdamage.rules.section." + key));
		list.addSmall(widgets);
	}

	private AbstractWidget control(Field field) {
		Class<?> type = field.getType();
		String word = LocalRules.read(field);
		Component label = label(field);
		AbstractWidget widget;
		if (type == boolean.class) {
			widget = CycleButton.onOffBuilder(Boolean.parseBoolean(word))
					.withTooltip(value -> tooltip(field))
					.create(0, 0, 150, 20, label, (button, value) -> change(field, Boolean.toString(value)));
		} else if (type.isEnum()) {
			List<Object> values = List.of(type.getEnumConstants());
			Object current = values.stream()
					.filter(value -> ((Enum<?>) value).name().equalsIgnoreCase(word))
					.findFirst().orElse(values.getFirst());
			widget = CycleButton.builder(value -> valueLabel(field, (Enum<?>) value), current)
					.withValues(values)
					.withTooltip(value -> tooltip(field))
					.create(0, 0, 150, 20, label, (button, value) -> change(field, LocalRules.word(value)));
		} else if (type == double.class) {
			widget = new MultiplierSlider(field, label, parseDouble(word));
			widget.setTooltip(tooltip(field));
		} else {
			// No int settings exist; one added later still shows, read-only,
			// until it gets a control of its own.
			widget = Button.builder(label.copy().append(": " + word), button -> {}).width(150).build();
			widget.active = false;
		}
		if (!LocalRules.canEdit()) {
			widget.active = false;
		}
		controls.add(widget);
		byName.put(field.getName(), widget);
		return widget;
	}

	private static double parseDouble(String word) {
		try {
			return Double.parseDouble(word);
		} catch (NumberFormatException e) {
			return 1.0;
		}
	}

	private static Component label(Field field) {
		return Component.translatableWithFallback("realisticdamage.rules." + field.getName(), field.getName());
	}

	private static Component valueLabel(Field field, Enum<?> value) {
		String word = value.name().toLowerCase(Locale.ROOT);
		return Component.translatableWithFallback("realisticdamage.rules." + field.getName() + "." + word, word);
	}

	private static @Nullable Tooltip tooltip(Field field) {
		String key = "realisticdamage.rules." + field.getName() + ".tooltip";
		return Tooltip.create(Component.translatableWithFallback(key, ""));
	}

	private void change(Field field, String word) {
		if (!LocalRules.canEdit()) {
			return;
		}
		if (LocalRules.remote()) {
			pending.put(field, word);
			pendingAge = 0;
		} else {
			LocalRules.write(field, word);
		}
	}

	private void flush() {
		for (Map.Entry<Field, String> entry : pending.entrySet()) {
			LocalRules.write(entry.getKey(), entry.getValue());
		}
		pending.clear();
	}

	@Override
	public void tick() {
		super.tick();
		if (!pending.isEmpty() && ++pendingAge >= 5) {
			flush();
		}
		// The server answered (or someone else changed something): show it.
		// Not while a change of ours is still on its way, or the page would
		// snap back to the old value for a moment. In place, so the list keeps
		// its scroll; only a change in who may edit rebuilds the page.
		if (pending.isEmpty() && RulesView.version() != seenVersion) {
			seenVersion = RulesView.version();
			if (LocalRules.canEdit() != seenCanEdit) {
				rebuildWidgets();
			} else {
				refreshValues();
			}
		}
	}

	/** Every control shows the current value again, without rebuilding anything. */
	@SuppressWarnings("unchecked")
	private void refreshValues() {
		for (Field field : RealisticDamageCommands.settingFields()) {
			AbstractWidget widget = byName.get(field.getName());
			String word = LocalRules.read(field);
			if (widget instanceof CycleButton<?> cycle) {
				try {
					((CycleButton<Object>) cycle).setValue(LocalRules.parse(field, word));
				} catch (IllegalArgumentException e) {
					// An unreadable word leaves the control as it was.
				}
			} else if (widget instanceof MultiplierSlider slider) {
				slider.show(parseDouble(word));
			}
		}
	}

	@Override
	public void removed() {
		flush();
	}

	@Override
	public void onClose() {
		flush();
		super.onClose();
	}

	/** Every control on the page, in order, for the game test. */
	public List<AbstractWidget> controls() {
		return List.copyOf(controls);
	}

	/** How far the list is scrolled, for the game test. */
	public double scrollAmount() {
		return list == null ? -1 : list.scrollAmount();
	}

	/** The list's visible band, top and bottom in GUI pixels, for the game test to scroll into. */
	public int[] listArea() {
		return new int[] {layout.getHeaderHeight(), height - layout.getFooterHeight()};
	}

	/** The control for one setting, for the game test. */
	public @Nullable AbstractWidget control(String name) {
		return byName.get(name);
	}

	/** A multiplier from 0 to 3 in steps of 0.05, sent when let go. */
	private final class MultiplierSlider extends AbstractSliderButton {
		private final Field field;
		private final Component name;
		private double shown;

		MultiplierSlider(Field field, Component name, double value) {
			super(0, 0, 150, 20, Component.empty(), Math.clamp(value / SLIDER_MAX, 0.0, 1.0));
			this.field = field;
			this.name = name;
			this.shown = value;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(name.copy().append(String.format(Locale.ROOT, ": ×%.2f", shown)));
		}

		/** The server's value, shown without sending it back. */
		void show(double value) {
			shown = value;
			this.value = Math.clamp(value / SLIDER_MAX, 0.0, 1.0);
			updateMessage();
		}

		@Override
		protected void applyValue() {
			shown = Math.round(value * SLIDER_MAX / 0.05) * 0.05;
			updateMessage();
			change(field, LocalRules.word(shown));
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			if (LocalRules.remote()) {
				flush();
			}
		}
	}
}
