package dev.captience.realisticdamage.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.damage.BloodiedItem;
import dev.captience.realisticdamage.systems.BloodKind;
import dev.captience.realisticdamage.systems.BloodLayer;
import dev.captience.realisticdamage.systems.BloodiedMath;
import dev.captience.realisticdamage.systems.BloodiedPaint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Blood drawn on an item (2.0), wherever the item is drawn: in a hand, on the
 * floor, in a frame, in an inventory slot.
 *
 * <p>The marks on the item ({@link BloodiedItem}) are painted into a small
 * picture the size of the item's own sprite, one blood pixel per item pixel
 * and only where the sprite has a pixel, and that picture is drawn over the
 * item on the item's own geometry: every quad the item just drew is drawn
 * again with the blood picture in place of the sprite. Nothing is painted per
 * item by hand, so it works for any item from any mod or pack.
 *
 * <p>A picture is made once for each sprite and each look (which marks, how
 * much of each to the sixteenth, how dry to the eighth) and kept; a drying
 * sword is eight pictures over half a minute, not one per frame.
 */
public final class BloodiedItems {
	private BloodiedItems() {}

	/** One mark as it is drawn: its amount to the sixteenth and its wetness to the eighth. */
	public record Mark(BloodKind kind, String tint, int amountStep, int seed, int wetStep) {}

	/**
	 * Everything that decides what an item's blood looks like. Compared by
	 * value: the inventory keeps its pictures of items by "what does this
	 * item look like", and this is the blood's part of that answer.
	 */
	public record Look(List<Mark> marks, boolean visceral) {}

	/** The biggest sprite side this paints: a 256-pixel pack sprite is someone else's problem. */
	private static final int LARGEST_SPRITE = 128;
	/** Pictures kept; the least recently drawn goes first. */
	private static final int KEPT = 128;
	/**
	 * Blood on an item against the screen palette. Full strength: an item in
	 * the hand is already darkened by the world's light, and at the 0.8 this
	 * started at a zombie's dark blood on a sword read as a black sword.
	 */
	private static final float ON_ITEM = 1.0F;
	private static final float DRIED_LIFT = 0.06F;

	/** The look being handed from an item's render state to the draws it submits; see the mixins. */
	public static @Nullable Look submitting;

	private record Key(SpriteContents sprite, Look look) {}

	private record Picture(Identifier id, RenderType type) {}

	private static final Map<Key, Picture> pictures = new LinkedHashMap<>(64, 0.75F, true);
	private static final Map<SpriteContents, boolean[][]> solids = new IdentityHashMap<>();
	/** The last look worked out for each list of marks, and the time step it was worked out in. */
	private static final Cache<List<BloodLayer>, Object[]> looks = CacheBuilder.newBuilder().weakKeys().build();
	private static final Map<BakedQuad, BakedQuad> remapped = new IdentityHashMap<>();
	private static long made;
	private static long drawn;

	/** Pictures painted so far; read by the client gametest. */
	public static long picturesMade() {
		return made;
	}

	/** Bloodied quads drawn so far; read by the client gametest. */
	public static long quadsDrawn() {
		return drawn;
	}

	/** What this item's blood looks like right now, or null when it is clean or blood is switched off. */
	public static @Nullable Look lookOf(ItemStack stack) {
		List<BloodLayer> layers = BloodiedItem.read(stack);
		if (layers.isEmpty() || ClientEffectsConfig.get().bloodIntensity() <= 0.0) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		long now = client.level == null ? 0L : client.level.getGameTime();
		boolean visceral = ClientEffectsConfig.get().visceralBlood;
		// Worked out again only when the game clock has moved far enough for a
		// shade to have changed; in between, the same object is handed back.
		Long step = now / 5L;
		Object[] known = looks.getIfPresent(layers);
		if (known != null && known[0].equals(step) && ((Look) known[1]).visceral() == visceral) {
			return (Look) known[1];
		}
		List<Mark> marks = new ArrayList<>(layers.size());
		for (BloodLayer layer : layers) {
			marks.add(new Mark(layer.kind(), layer.tint(), BloodiedMath.amountStep(layer.amount()), layer.seed(),
					client.level == null ? 0 : BloodiedMath.wetStep(now, layer.wetAt())));
		}
		Look look = new Look(List.copyOf(marks), visceral);
		looks.put(layers, new Object[] {step, look});
		return look;
	}

	/** Whether any mark on this item is still wet, for the word in its tooltip. */
	public static boolean wet(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		return client.level == null || BloodiedMath.anyWet(BloodiedItem.read(stack), client.level.getGameTime());
	}

	/** How strongly blood is drawn, as the alpha of the overlay. */
	public static int tint() {
		int alpha = (int) Math.round(Math.clamp(ClientEffectsConfig.get().bloodIntensity(), 0.0, 1.0) * 255.0);
		return (alpha << 24) | 0xFFFFFF;
	}

	/**
	 * The draw type whose texture is this sprite's blood picture for this
	 * look, painting it first if it is new. Null for a sprite too big to
	 * paint or one the marks do not touch. Render thread only.
	 */
	public static @Nullable RenderType typeFor(TextureAtlasSprite sprite, Look look) {
		SpriteContents contents = sprite.contents();
		int width = contents.width();
		int height = contents.height();
		if (width > LARGEST_SPRITE || height > LARGEST_SPRITE || width <= 0 || height <= 0) {
			return null;
		}
		Key key = new Key(contents, look);
		Picture picture = pictures.get(key);
		if (picture == null) {
			picture = paint(contents, look);
			pictures.put(key, picture);
			if (pictures.size() > KEPT) {
				Iterator<Map.Entry<Key, Picture>> oldest = pictures.entrySet().iterator();
				Picture gone = oldest.next().getValue();
				oldest.remove();
				Minecraft.getInstance().getTextureManager().release(gone.id());
			}
		}
		return picture.type();
	}

	private static Picture paint(SpriteContents contents, Look look) {
		boolean[][] solid = solids.computeIfAbsent(contents, sprite -> {
			boolean[][] out = new boolean[sprite.height()][sprite.width()];
			for (int y = 0; y < out.length; y++) {
				for (int x = 0; x < out[y].length; x++) {
					out[y][x] = !sprite.isTransparent(0, x, y);
				}
			}
			return out;
		});
		List<BloodiedPaint.Stroke> strokes = new ArrayList<>(look.marks().size());
		// Marks are placed by their own seed and the sprite's name, so the same
		// blow sits differently on a sword and on the axe beside it.
		long spriteSeed = contents.name().hashCode() * 0x9E3779B97F4A7C15L;
		for (Mark mark : look.marks()) {
			BloodTint tint = BloodTint.byName(mark.tint());
			float[] wet = look.visceral() ? tint.freshVisceral() : tint.fresh();
			float[] dry = tint.dried();
			strokes.add(new BloodiedPaint.Stroke(mark.kind(), mark.amountStep() * BloodiedMath.STEP,
					mark.seed() * 0xC2B2AE3D27D4EB4FL ^ spriteSeed,
					mark.wetStep() / (double) BloodiedMath.WET_STEPS,
					new float[] {wet[0] * ON_ITEM, wet[1] * ON_ITEM, wet[2] * ON_ITEM},
					// Lifted a shade: the dried palette is tuned for stains on a floor,
					// and on a blade the darkest of them (a zombie's) read as soot.
					new float[] {dry[0] * ON_ITEM + DRIED_LIFT, dry[1] * ON_ITEM + DRIED_LIFT * 0.6F,
							dry[2] * ON_ITEM + DRIED_LIFT * 0.5F}));
		}
		int[][] colours = BloodiedPaint.compose(solid, strokes);
		NativeImage image = new NativeImage(contents.width(), contents.height(), true);
		for (int y = 0; y < colours.length; y++) {
			for (int x = 0; x < colours[y].length; x++) {
				image.setPixel(x, y, colours[y][x]);
			}
		}
		Identifier id = RealisticDamage.id("dynamic/item_blood_" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Realistic Damage item blood", image));
		// The item pipeline, blended, on the item's own target: the same vertex
		// program that drew the item, so the overlay lands on exactly the same
		// depth and passes the ordinary depth test with nothing to tune.
		RenderType type = RenderType.create("realisticdamage_item_blood",
				RenderSetup.builder(RenderPipelines.ITEM_TRANSLUCENT)
						.withTexture("Sampler0", id)
						.useLightmap()
						.useOverlay()
						.createRenderSetup());
		return new Picture(id, type);
	}

	/**
	 * The same quad, reading the blood picture instead of the atlas: its
	 * corners keep their places and its texture coordinates are moved from
	 * the sprite's rectangle in the atlas to the whole of the picture.
	 */
	public static BakedQuad remap(BakedQuad quad, TextureAtlasSprite sprite) {
		BakedQuad known = remapped.get(quad);
		if (known != null) {
			return known;
		}
		if (remapped.size() > 8192) {
			remapped.clear();
		}
		float u0 = sprite.getU0();
		float v0 = sprite.getV0();
		float du = sprite.getU1() - u0;
		float dv = sprite.getV1() - v0;
		BakedQuad moved = new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
				local(quad.packedUV0(), u0, v0, du, dv), local(quad.packedUV1(), u0, v0, du, dv),
				local(quad.packedUV2(), u0, v0, du, dv), local(quad.packedUV3(), u0, v0, du, dv),
				quad.direction(), quad.materialInfo());
		remapped.put(quad, moved);
		return moved;
	}

	private static long local(long packed, float u0, float v0, float du, float dv) {
		return UVPair.pack((UVPair.unpackU(packed) - u0) / du, (UVPair.unpackV(packed) - v0) / dv);
	}

	public static void countDrawn(int quads) {
		drawn += quads;
	}

	/**
	 * Forgets every picture: the sprites they were painted for are gone after
	 * a resource reload, and the quads with them.
	 */
	public static void reset() {
		Minecraft client = Minecraft.getInstance();
		for (Picture picture : pictures.values()) {
			client.getTextureManager().release(picture.id());
		}
		pictures.clear();
		solids.clear();
		remapped.clear();
		looks.invalidateAll();
	}
}
