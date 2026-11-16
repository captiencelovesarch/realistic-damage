package dev.captience.realisticdamage.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.ScarLevel;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * Your body, in the place the inventory already shows you (2.0).
 *
 * <p>The box in the inventory that holds the little turning model of the
 * player now holds a paper doll made of the player's own skin, facing front
 * and standing still, with what is wrong drawn on it: blood where a region is
 * bleeding or stained, a grey cast and a crack on a broken limb, a stump and a
 * ghost where one is gone, and a frame round each region in the colour of its
 * state. Hovering a region names it and lists what is wrong with it, in an
 * ordinary tooltip. The icons down the left edge say what kinds of trouble
 * there are at all, and the small button in the corner changes the view:
 * the body, the x-ray (the same thing drawn on Minecraft's own skeleton), or
 * the ordinary model back.
 *
 * <p>It used to be a panel of its own beside the inventory. That strip is
 * where the recipe book opens, so the panel had to be measured round it and
 * stood aside whenever there was no room, which on the automatic GUI scale
 * was whenever the book was open. In the box it moves with the window, is the
 * same at every scale, and is where anybody would look for themselves.
 *
 * <p>Still no raw numbers by default. "Exact Numbers" opts into condition
 * figures here and nowhere else, and adds the two hidden meters to the
 * tooltip by band, never as figures: a player who could read their exact
 * blood level would never be surprised by it, and being surprised by it is
 * the point of hiding it (spec §42).
 */
public final class BodyInspector {
	private BodyInspector() {}

	/** What the box shows. */
	public enum View {
		/** The paper doll of your skin. */
		BODY,
		/** The same, on the skeleton. */
		XRAY,
		/** Vanilla's turning model, untouched. */
		MODEL;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static View byId(@Nullable String id) {
			for (View view : values()) {
				if (view.id().equals(id)) {
					return view;
				}
			}
			return BODY;
		}

		View next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/** One skin pixel is this many screen pixels: the 16 by 32 figure is 32 by 64 in a box 49 by 70. */
	private static final int SCALE = 2;
	/** The figure sits right of centre, leaving the left edge for the icons. */
	private static final int FIGURE_LEFT = 13;
	private static final int FIGURE_TOP = 3;
	private static final int BUTTON = 9;

	private static final Identifier SKELETON = Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
	private static final int XRAY_BACK = 0xFF080E16;
	private static final int GHOST = 0xFF343434;
	private static final int CUT_DARK = 0xFF7A0C12;
	private static final int CUT_LIGHT = 0xFFC1121F;

	/**
	 * A region of the figure, in skin pixels on the 16 by 32 doll: where it
	 * sits, how big it is, and where its front is on a player skin and on the
	 * skeleton texture.
	 */
	private enum Piece {
		HEAD(BodyPart.HEAD, 4, 0, 8, 8, 8, 8, 40, 8, PlayerModelPart.HAT, 8, 8, 8, 4),
		TORSO(BodyPart.TORSO, 4, 8, 8, 12, 20, 20, 20, 36, PlayerModelPart.JACKET, 20, 20, 8, 4),
		RIGHT_ARM(BodyPart.RIGHT_ARM, 0, 8, 4, 12, 44, 20, 44, 36, PlayerModelPart.RIGHT_SLEEVE, 42, 18, 2, 2),
		LEFT_ARM(BodyPart.LEFT_ARM, 12, 8, 4, 12, 36, 52, 52, 52, PlayerModelPart.LEFT_SLEEVE, 42, 18, 2, 12),
		RIGHT_LEG(BodyPart.RIGHT_LEG, 4, 20, 4, 12, 4, 20, 4, 36, PlayerModelPart.RIGHT_PANTS_LEG, 2, 18, 2, 5),
		LEFT_LEG(BodyPart.LEFT_LEG, 8, 20, 4, 12, 20, 52, 4, 52, PlayerModelPart.LEFT_PANTS_LEG, 2, 18, 2, 9);

		final BodyPart part;
		final int x;
		final int y;
		final int w;
		final int h;
		final int skinU;
		final int skinV;
		final int overU;
		final int overV;
		final PlayerModelPart layer;
		final int boneU;
		final int boneV;
		final int boneW;
		/** Where the bone's left edge sits on the doll, in skin pixels. */
		final int boneX;

		Piece(BodyPart part, int x, int y, int w, int h, int skinU, int skinV, int overU, int overV,
				PlayerModelPart layer, int boneU, int boneV, int boneW, int boneX) {
			this.part = part;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.skinU = skinU;
			this.skinV = skinV;
			this.overU = overU;
			this.overV = overV;
			this.layer = layer;
			this.boneU = boneU;
			this.boneV = boneV;
			this.boneW = boneW;
			this.boneX = boneX;
		}

		boolean limb() {
			return part.isLimb();
		}
	}

	/** A region as drawn this frame, in screen pixels. */
	private record Region(Piece piece, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	// What was drawn last frame, for the client gametests: a hover that
	// shipped untested once because it was believed to be untestable.
	private static final java.util.Map<BodyPart, int[]> lastCenters = new java.util.EnumMap<>(BodyPart.class);
	private static @Nullable BodyPart lastHovered;
	private static List<String> lastDetailKeys = List.of();
	private static final EnumSet<BodyPart> lastScarMarks = EnumSet.noneOf(BodyPart.class);
	private static int @Nullable [] lastPanelBounds;
	private static int @Nullable [] lastButton;
	private static @Nullable View lastView;

	/** The box's rectangle as of the last frame, {left, top, right, bottom}, or null when the body was not drawn. */
	public static int @Nullable [] panelBounds() {
		return lastPanelBounds;
	}

	/** The view button's centre as of the last frame, or null when it was not drawn. */
	public static int @Nullable [] viewButton() {
		return lastButton == null ? null
				: new int[] {(lastButton[0] + lastButton[2]) / 2, (lastButton[1] + lastButton[3]) / 2};
	}

	/** Which view the box drew last frame; null when the mod left the inventory alone. */
	public static @Nullable View viewDrawn() {
		return lastView;
	}

	/** Was the scar seam drawn on this region as of the last frame? */
	public static boolean scarMarkDrawn(BodyPart part) {
		return lastScarMarks.contains(part);
	}

	/** How many regions the seam was drawn on as of the last frame. */
	public static int scarMarks() {
		return lastScarMarks.size();
	}

	/** Screen position of a region as of the last frame, or null if not drawn. */
	public static int @Nullable [] regionCenter(BodyPart part) {
		return lastCenters.get(part);
	}

	/** Which region the cursor was over as of the last frame. */
	public static @Nullable BodyPart hoveredPart() {
		return lastHovered;
	}

	/**
	 * Every translation key the tooltip put on screen last frame, in order;
	 * empty when no region was hovered. Keys rather than text, because the
	 * assertion is about the branch taken and not about the English.
	 */
	public static List<String> lastDetailKeys() {
		return lastDetailKeys;
	}

	private static void forget() {
		lastCenters.clear();
		lastHovered = null;
		lastDetailKeys = List.of();
		lastScarMarks.clear();
		lastPanelBounds = null;
		lastButton = null;
		lastView = null;
	}

	public static View view() {
		return View.byId(ClientEffectsConfig.get().inventoryBodyView);
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				forget();
				ScreenMouseEvents.allowMouseClick(screen).register((opened, event) ->
						!clicked(event.x(), event.y(), event.button()));
				ScreenEvents.remove(screen).register(closed -> forget());
			}
		});
	}

	/** A click on the view button changes the view and is not passed on. */
	private static boolean clicked(double x, double y, int button) {
		int[] at = lastButton;
		if (button != 0 || at == null || x < at[0] || x >= at[2] || y < at[1] || y >= at[3]) {
			return false;
		}
		ClientEffectsConfig.get().inventoryBodyView = view().next().id();
		ClientEffectsConfig.save();
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		return true;
	}

	/**
	 * Whether the mod has anything to show in the box at all: the display is
	 * on, the game is one the mod takes part in, and the body has arrived.
	 */
	private static @Nullable BodySnapshot showing() {
		if (!ClientEffectsConfig.get().showBodyHud || !ClientState.active()) {
			return null;
		}
		return ClientState.snapshot();
	}

	/**
	 * Called in place of vanilla's player model in the inventory. Returns
	 * false when vanilla should draw its own after all (the mod is out of it,
	 * or the player chose the model); the button is still drawn over that.
	 */
	public static boolean draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float mouseX, float mouseY) {
		BodySnapshot snapshot = showing();
		LocalPlayer player = Minecraft.getInstance().player;
		if (snapshot == null || player == null) {
			forget();
			return false;
		}
		View view = view();
		lastView = view;
		lastPanelBounds = new int[] {x0, y0, x1, y1};
		lastScarMarks.clear();
		if (view == View.MODEL) {
			lastCenters.clear();
			lastHovered = null;
			lastDetailKeys = List.of();
			return false;
		}
		if (view == View.XRAY) {
			graphics.fill(x0, y0, x1, y1, XRAY_BACK);
		}
		int left = x0 + FIGURE_LEFT;
		int top = y0 + FIGURE_TOP;
		List<Region> regions = new ArrayList<>(6);
		for (Piece piece : Piece.values()) {
			regions.add(new Region(piece, left + piece.x * SCALE, top + piece.y * SCALE, piece.w * SCALE, piece.h * SCALE));
		}
		for (Region region : regions) {
			if (view == View.XRAY) {
				drawBone(graphics, snapshot, region);
			} else {
				drawSkin(graphics, snapshot, player, region);
			}
			lastCenters.put(region.piece().part, new int[] {region.x() + region.w() / 2, region.y() + region.h() / 2});
		}
		// Frames last and worst last, so where two regions share an edge the
		// one in more trouble is the one whose colour shows.
		regions.stream()
				.sorted(java.util.Comparator.comparingInt(region -> snapshot.state(region.piece().part).ordinal()))
				.forEach(region -> frame(graphics, snapshot, region));

		Region hovered = null;
		for (Region region : regions) {
			if (region.contains(mouseX, mouseY)) {
				hovered = region;
			}
		}
		drawIcons(graphics, snapshot, x0, y0, mouseX, mouseY);
		lastHovered = hovered == null ? null : hovered.piece().part;
		if (hovered == null) {
			lastDetailKeys = List.of();
		} else {
			outline(graphics, hovered.x() - 1, hovered.y() - 1, hovered.w() + 2, hovered.h() + 2, 0xFFFFFFFF);
			List<Component> lines = detail(snapshot, hovered.piece().part);
			List<String> keys = new ArrayList<>(lines.size());
			for (Component line : lines) {
				if (line.getContents() instanceof TranslatableContents translatable) {
					keys.add(translatable.getKey());
				}
			}
			lastDetailKeys = List.copyOf(keys);
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, lines, Optional.empty(), (int) mouseX, (int) mouseY);
		}
		return true;
	}

	/**
	 * The view button, in the bottom right corner of the box. Drawn after
	 * whichever view the box holds, vanilla's model included, since it is the
	 * way back from that one.
	 */
	public static void drawButton(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float mouseX, float mouseY) {
		if (showing() == null) {
			lastButton = null;
			return;
		}
		int bx = x1 - BUTTON - 1;
		int by = y1 - BUTTON - 1;
		lastButton = new int[] {bx, by, bx + BUTTON, by + BUTTON};
		boolean over = mouseX >= bx && mouseX < bx + BUTTON && mouseY >= by && mouseY < by + BUTTON;
		View view = view();
		graphics.fill(bx, by, bx + BUTTON, by + BUTTON, 0xFF000000);
		graphics.fill(bx + 1, by + 1, bx + BUTTON - 1, by + BUTTON - 1, over ? 0xFFA8B4D8 : 0xFF8B8B8B);
		graphics.fill(bx + 1, by + 1, bx + BUTTON - 1, by + 2, over ? 0xFFDCE2F6 : 0xFFFFFFFF);
		graphics.fill(bx + 1, by + 1, bx + 2, by + BUTTON - 1, over ? 0xFFDCE2F6 : 0xFFFFFFFF);
		graphics.fill(bx + 1, by + BUTTON - 2, bx + BUTTON - 1, by + BUTTON - 1, 0xFF555555);
		graphics.fill(bx + BUTTON - 2, by + 1, bx + BUTTON - 1, by + BUTTON - 1, 0xFF555555);
		// A little figure: flesh for the body, bone for the x-ray, the model's blue for vanilla's.
		int ink = switch (view) {
			case BODY -> 0xFF8A3A2A;
			case XRAY -> 0xFFF4F4EC;
			case MODEL -> 0xFF2C4A9A;
		};
		graphics.fill(bx + 4, by + 2, bx + 5, by + 3, ink);
		graphics.fill(bx + 3, by + 3, bx + 6, by + 5, ink);
		graphics.fill(bx + 4, by + 5, bx + 5, by + 6, ink);
		graphics.fill(bx + 3, by + 6, bx + 4, by + 7, ink);
		graphics.fill(bx + 5, by + 6, bx + 6, by + 7, ink);
		if (over) {
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, List.of(
					Component.translatable("realisticdamage.inspector.view." + view.id()),
					Component.translatable("realisticdamage.inspector.view.change").withStyle(ChatFormatting.GRAY)),
					Optional.empty(), (int) mouseX, (int) mouseY);
		}
	}

	// --- the skin doll ---

	private static void drawSkin(GuiGraphicsExtractor graphics, BodySnapshot snapshot, LocalPlayer player, Region region) {
		Piece piece = region.piece();
		BodyPart part = piece.part;
		Identifier skin = player.getSkin().body().texturePath();
		boolean slim = piece.limb() && (part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM)
				&& player.getSkin().model() == PlayerModelType.SLIM;
		// A slim arm is three wide and hangs against the body.
		int w = slim ? 3 : piece.w;
		int x = region.x() + (slim && part == BodyPart.RIGHT_ARM ? SCALE : 0);
		boolean gone = snapshot.destroyed(part);
		int rows = gone ? (piece.limb() ? 3 : 0) : piece.h;
		if (gone) {
			ghost(graphics, region);
		}
		if (rows > 0) {
			// Greyed while the bone is broken: the limb is there and not working.
			int tint = !gone && snapshot.broken(part) ? 0xFFA9A9A9 : 0xFFFFFFFF;
			graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, region.y(), piece.skinU, piece.skinV,
					w * SCALE, rows * SCALE, w, rows, 64, 64, tint);
			if (player.isModelPartShown(piece.layer)) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, region.y(), piece.overU, piece.overV,
						w * SCALE, rows * SCALE, w, rows, 64, 64, tint);
			}
		}
		if (gone) {
			if (rows > 0) {
				cut(graphics, x, region.y() + rows * SCALE, w * SCALE);
			}
			return;
		}
		blood(graphics, snapshot, player, region, x, w);
		if (snapshot.broken(part)) {
			crack(graphics, x, region.y(), w * SCALE, region.h());
		}
		BodyHud.drawCeilingCap(graphics, x, region.y(), w * SCALE, Math.max(2, region.h() / 3),
				snapshot.ceilingBand(part), 0xC0000000);
		scar(graphics, snapshot, region);
	}

	/**
	 * Blood on a region of the doll: a blot that grows from where the wound
	 * is, by how stained the region really is on your body right now (the
	 * same figure that reddens the model in the world), red while the wound
	 * is open and brown once it has only left its mark. The same pixels every
	 * frame; nothing here is chance.
	 */
	private static void blood(GuiGraphicsExtractor graphics, BodySnapshot snapshot, LocalPlayer player, Region region,
			int x, int w) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity();
		if (strength <= 0.0) {
			return;
		}
		Piece piece = region.piece();
		WoundSeverity wound = snapshot.wound(piece.part);
		double stain = BloodBody.stain(player.getId(), piece.part);
		double amount = Math.max(stain, wound == null ? 0.0 : 0.25 + 0.2 * wound.ordinal());
		if (amount < 0.04) {
			return;
		}
		float[] fresh = BloodPalette.colour(wound == null ? 0.15 : 1.0);
		double middleX = w / 2.0 - 0.5;
		double middleY = piece.h * 0.35;
		double reach = 0.7 + amount * 3.0;
		long seed = piece.ordinal() * 7919L + 13L;
		for (int py = 0; py < piece.h; py++) {
			for (int px = 0; px < w; px++) {
				double distance = Math.hypot(px - middleX, (py - middleY) * 0.8);
				double grain = dev.captience.realisticdamage.systems.BloodFlow.jitter(seed + py * 31L + px, 3);
				boolean blot = distance < reach - 0.5 + grain;
				// What runs down from an open wound.
				boolean run = wound != null && Math.abs(px - Math.round(middleX)) < 1 && py > middleY
						&& py < middleY + 2.0 + amount * 6.0 && grain > 0.15;
				if (!blot && !run) {
					continue;
				}
				float shade = grain > 0.5 ? 0.72F : 1.0F;
				int colour = ARGB.color((int) (Math.clamp(strength, 0.0, 1.0) * 235.0),
						(int) (fresh[0] * shade * 255.0F), (int) (fresh[1] * shade * 255.0F), (int) (fresh[2] * shade * 255.0F));
				graphics.fill(x + px * SCALE, region.y() + py * SCALE, x + (px + 1) * SCALE, region.y() + (py + 1) * SCALE, colour);
			}
		}
	}

	/** A break: a pale zigzag across the middle of the limb with a dark edge under it. */
	private static void crack(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		int middle = y + h / 2;
		for (int i = 0; i < w; i++) {
			int row = middle + ((i / 2) % 2 == 0 ? 0 : -1);
			graphics.fill(x + i, row, x + i + 1, row + 1, 0xFFF4F0E4);
			graphics.fill(x + i, row + 1, x + i + 1, row + 2, 0xFF2A2222);
		}
	}

	/** Where a lost region was: a faint hatch, so there is still something to point at. */
	private static void ghost(GuiGraphicsExtractor graphics, Region region) {
		for (int row = 0; row < region.h(); row += 2) {
			int shift = (row / 2) % 2;
			for (int col = shift * 2; col < region.w(); col += 4) {
				graphics.fill(region.x() + col, region.y() + row, region.x() + col + 1, region.y() + row + 1, GHOST);
			}
		}
	}

	/** The raw end of a stump. */
	private static void cut(GuiGraphicsExtractor graphics, int x, int y, int w) {
		for (int i = 0; i < w; i += SCALE) {
			graphics.fill(x + i, y, x + i + SCALE, y + SCALE, (i / SCALE) % 2 == 0 ? CUT_LIGHT : CUT_DARK);
		}
	}

	/**
	 * History: a pale seam rising from the bottom-left of a region that was
	 * once broken, doubled and darker for one that was once lost. Not drawn on
	 * a region that is gone right now, where the stump says all of it.
	 */
	private static void scar(GuiGraphicsExtractor graphics, BodySnapshot snapshot, Region region) {
		BodyPart part = region.piece().part;
		ScarLevel scar = snapshot.scar(part);
		if (!ClientEffectsConfig.get().showScars || scar == ScarLevel.NONE || snapshot.destroyed(part)) {
			return;
		}
		int n = Math.min(region.w(), region.h()) / 2;
		seam(graphics, region, 0, n, BodyHud.SCAR_LIGHT, BodyHud.SCAR_DARK, true);
		if (scar == ScarLevel.DESTROYED) {
			seam(graphics, region, 3, n - 1, BodyHud.SCAR_DARK, BodyHud.SCAR_OUTLINE, false);
		}
		lastScarMarks.add(part);
	}

	private static void seam(GuiGraphicsExtractor graphics, Region region, int inset, int n, int light, int shadow, boolean ticks) {
		int baseX = region.x() + 1 + inset;
		int baseY = region.y() + region.h() - 2;
		for (int i = 0; i < n; i++) {
			int px = baseX + i;
			int py = baseY - i;
			if (px + 1 >= region.x() + region.w()) {
				break;
			}
			graphics.fill(px, py, px + 1, py + 1, light);
			graphics.fill(px + 1, py, px + 2, py + 1, shadow);
			if (ticks && i % 3 == 1) {
				graphics.fill(px - 1, py + 1, px, py + 2, shadow);
				graphics.fill(px + 1, py - 1, px + 2, py, shadow);
			}
		}
	}

	// --- the x-ray ---

	/**
	 * A region on Minecraft's own skeleton: the front of the skeleton's
	 * texture, bone for bone where the player's limbs are. A broken bone is
	 * parted in the middle, a lost one stops short, and an open wound runs red
	 * beside it. The region itself is a faint wash of its state's colour, so
	 * the body's outline is still there round the bones.
	 */
	private static void drawBone(GuiGraphicsExtractor graphics, BodySnapshot snapshot, Region region) {
		Piece piece = region.piece();
		BodyPart part = piece.part;
		BodyState state = snapshot.state(part);
		boolean gone = snapshot.destroyed(part);
		int wash = state == BodyState.HEALTHY ? 0x203C5A6E : (BodyHud.stateColor(state) & 0x00FFFFFF) | 0x38000000;
		if (!gone) {
			graphics.fill(region.x(), region.y(), region.x() + region.w(), region.y() + region.h(), wash);
		} else {
			ghost(graphics, region);
		}
		int rows = gone ? (piece.limb() ? 3 : 0) : piece.h;
		int bx = region.x() - piece.x * SCALE + piece.boneX * SCALE;
		if (rows > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, SKELETON, bx, region.y(), piece.boneU, piece.boneV,
					piece.boneW * SCALE, rows * SCALE, piece.boneW, rows, 64, 32);
		}
		if (gone) {
			if (rows > 0) {
				cut(graphics, bx, region.y() + rows * SCALE, piece.boneW * SCALE);
			}
			return;
		}
		if (snapshot.broken(part)) {
			// The bone parted: a gap the colour of the plate, and the splinters.
			int middle = region.y() + region.h() / 2;
			graphics.fill(bx - 1, middle - 1, bx + piece.boneW * SCALE + 1, middle + 2, XRAY_BACK);
			graphics.fill(bx - 1, middle - 1, bx + piece.boneW * SCALE + 1, middle + 2, wash);
			graphics.fill(bx - 1, middle + 1, bx, middle + 2, 0xFFFF8A3C);
			graphics.fill(bx + piece.boneW * SCALE, middle - 1, bx + piece.boneW * SCALE + 1, middle, 0xFFFF8A3C);
		}
		WoundSeverity wound = snapshot.wound(part);
		if (wound != null && ClientEffectsConfig.get().bloodIntensity() > 0.0) {
			int from = region.y() + region.h() / 3;
			int length = 4 + wound.ordinal() * 3;
			int side = bx + piece.boneW * SCALE + (piece.limb() ? 1 : -4);
			for (int i = 0; i < length && from + i < region.y() + region.h(); i++) {
				graphics.fill(side, from + i, side + 1, from + i + 1, i % 3 == 2 ? CUT_DARK : CUT_LIGHT);
			}
			graphics.fill(side - 1, from, side + 2, from + 2, CUT_LIGHT);
		}
	}

	// --- shared ---

	/** A frame in the state's colour round a region that is not healthy; dashed round one that is gone. */
	private static void frame(GuiGraphicsExtractor graphics, BodySnapshot snapshot, Region region) {
		BodyState state = snapshot.state(region.piece().part);
		if (state == BodyState.HEALTHY) {
			return;
		}
		if (snapshot.destroyed(region.piece().part)) {
			for (int i = 0; i < region.w(); i += 3) {
				graphics.fill(region.x() + i, region.y() + region.h(), region.x() + i + 1, region.y() + region.h() + 1, 0xFF8B0000);
			}
			return;
		}
		outline(graphics, region.x() - 1, region.y() - 1, region.w() + 2, region.h() + 2, BodyHud.stateColor(state));
	}

	private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
		graphics.fill(x, y, x + w, y + 1, colour);
		graphics.fill(x, y + h - 1, x + w, y + h, colour);
		graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
	}

	/**
	 * What kinds of trouble there are, as icons down the left edge of the box:
	 * the one line that can be read without pointing at anything.
	 */
	private static void drawIcons(GuiGraphicsExtractor graphics, BodySnapshot snapshot, int x0, int y0, float mouseX, float mouseY) {
		if (!ClientEffectsConfig.get().showStatusIcons) {
			return;
		}
		List<StatusIcons.Icon> icons = new ArrayList<>(5);
		List<String> names = new ArrayList<>(5);
		boolean bleeding = false;
		boolean broken = false;
		boolean destroyed = false;
		for (BodyPart part : BodyPart.values()) {
			bleeding |= snapshot.wound(part) != null;
			broken |= snapshot.broken(part);
			destroyed |= snapshot.destroyed(part);
		}
		if (bleeding) {
			icons.add(StatusIcons.BLEED);
			names.add("realisticdamage.inspector.icon.bleeding");
		}
		if (broken) {
			icons.add(StatusIcons.BROKEN);
			names.add("realisticdamage.inspector.icon.broken");
		}
		if (destroyed) {
			icons.add(StatusIcons.DESTROYED);
			names.add("realisticdamage.inspector.icon.destroyed");
		}
		if (snapshot.concussion()) {
			icons.add(StatusIcons.CONCUSSION);
			names.add("realisticdamage.inspector.icon.concussion");
		}
		if (snapshot.burned()) {
			icons.add(StatusIcons.BURNED);
			names.add("realisticdamage.inspector.icon.burned");
		}
		for (int i = 0; i < icons.size(); i++) {
			int ix = x0 + 2;
			int iy = y0 + 2 + i * (StatusIcons.SIZE + 1);
			StatusIcons.draw(graphics, icons.get(i), ix, iy, 0xFF000000);
			if (mouseX >= ix && mouseX < ix + StatusIcons.SIZE && mouseY >= iy && mouseY < iy + StatusIcons.SIZE) {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.translatable(names.get(i)));
				lines.addAll(vitals(snapshot));
				graphics.setTooltipForNextFrame(Minecraft.getInstance().font, lines, Optional.empty(), (int) mouseX, (int) mouseY);
			}
		}
	}

	/**
	 * The two hidden meters, named by band and never numbered; only for a
	 * player who has opted into numbers at all.
	 */
	private static List<Component> vitals(BodySnapshot snapshot) {
		if (!ClientEffectsConfig.get().showNumericStats) {
			return List.of();
		}
		return List.of(
				Component.translatable("realisticdamage.inspector.vital",
						Component.translatable("realisticdamage.vitals.blood"),
						Component.translatable("realisticdamage.vitals.blood." + Vitals.bloodBand(snapshot.blood())))
						.withStyle(ChatFormatting.DARK_GRAY),
				Component.translatable("realisticdamage.inspector.vital",
						Component.translatable("realisticdamage.vitals.consciousness"),
						Component.translatable("realisticdamage.vitals.consciousness."
								+ Vitals.consciousnessBand(snapshot.consciousness(), snapshot.unconscious())))
						.withStyle(ChatFormatting.DARK_GRAY));
	}

	/** The tooltip for a region: its name, its state in the state's colour, then what is wrong, then what happened. */
	private static List<Component> detail(BodySnapshot snapshot, BodyPart part) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("realisticdamage.part." + part.id()));
		BodyState state = snapshot.state(part);
		lines.add(Component.translatable("realisticdamage.state." + state.name().toLowerCase(Locale.ROOT))
				.withStyle(style -> style.withColor(BodyHud.stateColor(state) & 0x00FFFFFF)));
		// Directly under the state word, because it qualifies it: "Injured", and
		// this is as far as it will ever come back. A band, never a figure.
		if (snapshot.ceilingBand(part) > 0) {
			lines.add(grey("realisticdamage.detail.ceiling."
					+ snapshot.ceilingState(part).name().toLowerCase(Locale.ROOT)));
		}
		if (ClientEffectsConfig.get().showNumericStats && !snapshot.destroyed(part)) {
			// Derived from a snapshot quantized to 1%, so one decimal is as much
			// precision as actually exists. /rdmg info remains the exact source.
			double max = part.maxCondition();
			lines.add(Component.translatable("realisticdamage.detail.condition",
					String.format(Locale.ROOT, "%.1f", snapshot.fraction(part) * max),
					String.format(Locale.ROOT, "%.0f", max),
					Math.round(snapshot.fraction(part) * 100.0f)).withStyle(ChatFormatting.GRAY));
		}
		if (snapshot.destroyed(part)) {
			lines.add(grey("realisticdamage.detail.destroyed"));
		}
		if (snapshot.broken(part)) {
			lines.add(grey("realisticdamage.detail.broken"));
		}
		WoundSeverity wound = snapshot.wound(part);
		if (wound != null) {
			lines.add(Component.translatable("realisticdamage.detail.bleeding."
					+ wound.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.RED));
		}
		if (part == BodyPart.HEAD && snapshot.concussion()) {
			lines.add(grey("realisticdamage.detail.concussion." + Math.clamp(snapshot.concussionGrade(), 1, 3)));
			// The one piece of advice the box gives: what the flare is telling you.
			lines.add(grey(snapshot.concussionFlare() >= 40
					? "realisticdamage.detail.concussion_flaring" : "realisticdamage.detail.concussion_rest"));
		}
		if (snapshot.burned()) {
			lines.add(grey("realisticdamage.detail.burned"));
		}
		// Last, because it is history and not condition; gated with the seam, as
		// a toggle that hides the mark and keeps the sentence is worse than either.
		ScarLevel scar = snapshot.scar(part);
		if (ClientEffectsConfig.get().showScars && scar != ScarLevel.NONE) {
			lines.add(grey("realisticdamage.detail.scarred." + scar.name().toLowerCase(Locale.ROOT)
					+ (scar == ScarLevel.DESTROYED && part.isCore() ? "." + part.id() : "")));
		}
		lines.addAll(vitals(snapshot));
		return lines;
	}

	private static Component grey(String key) {
		return Component.translatable(key).withStyle(ChatFormatting.GRAY);
	}
}
