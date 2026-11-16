package dev.captience.realisticdamage.damage;

import java.util.ArrayList;
import java.util.List;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.BloodKind;
import dev.captience.realisticdamage.systems.BloodLayer;
import dev.captience.realisticdamage.systems.BloodiedMath;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Blood on the thing that drew it (2.0).
 *
 * <p>A weapon that opens a wound comes away with some of it, and keeps it.
 * The blood is a short list of marks written onto the item itself, each with
 * the shape of how it got there, the colour of whose it is and the time it
 * was last wet; so it is still there in a chest, on the floor, in an item
 * frame or in somebody else's hand, and a sword that has been through three
 * kinds of thing shows three kinds of blood.
 *
 * <p>How a mark is made:
 * <ul>
 * <li>a slash leaves a streak along the blade, a thrust buries the tip, a
 * blunt blow leaves a splat, anything else a plain soak;</li>
 * <li>how much is by the wound the blow opened, or less if it only landed in
 * one that was already open;</li>
 * <li>a wound that bleeds hard throws specks back as it opens;</li>
 * <li>the blow that kills, and the blow that takes a limb or a head off,
 * soak it;</li>
 * <li>and a wielder bleeding from their own weapon arm has it run onto the
 * grip, in their own colour.</li>
 * </ul>
 * Marks dry over half a minute and then stay until water takes them off:
 * holding the item in rain or while standing in water rinses it in a few
 * seconds, wet blood twice as fast as dried.
 *
 * <p>Stored in the item's vanilla {@code custom_data} rather than in a
 * component of the mod's own. An item carrying a component the game does not
 * know fails to load at all, so a world opened without the mod would have
 * deleted every sword that had ever been in a fight; {@code custom_data} is
 * the place vanilla sets aside for exactly this and it survives.
 *
 * <p>Only things that do not stack take blood. A bloodied stick would refuse
 * to stack with the clean ones, and sixty-three of them would have turned red
 * for one blow.
 */
public final class BloodiedItem {
	private BloodiedItem() {}

	/** The key inside {@code custom_data}. */
	public static final String KEY = "realisticdamage:blood";
	private static final String LAYERS = "layers";
	private static final String KIND = "kind";
	private static final String TINT = "tint";
	private static final String AMOUNT = "amount";
	private static final String SEED = "seed";
	private static final String WET_AT = "wet_at";

	/**
	 * What each {@code custom_data} value says about blood, remembered by the
	 * value itself: component values are never changed in place, so the answer
	 * for one object is good for as long as the object lives. The renderer asks
	 * for every item it draws, every frame, and should not copy a tag to find
	 * out that a pickaxe is clean.
	 */
	private static final Cache<CustomData, List<BloodLayer>> READS = CacheBuilder.newBuilder().weakKeys().build();

	/** Whether this is a thing blood stays on: one item, not a stack. */
	public static boolean takesBlood(ItemStack stack) {
		return !stack.isEmpty() && stack.getMaxStackSize() == 1;
	}

	/** The marks on this item, oldest first; empty when it is clean. Never changed in place. */
	public static List<BloodLayer> read(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return List.of();
		}
		List<BloodLayer> known = READS.getIfPresent(data);
		if (known == null) {
			known = parse(data);
			READS.put(data, known);
		}
		return known;
	}

	private static List<BloodLayer> parse(CustomData data) {
		ListTag list = data.copyTag().getCompoundOrEmpty(KEY).getListOrEmpty(LAYERS);
		if (list.isEmpty()) {
			return List.of();
		}
		List<BloodLayer> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size() && out.size() < BloodiedMath.LAYERS_MAX; i++) {
			CompoundTag tag = list.getCompoundOrEmpty(i);
			float amount = Math.clamp(tag.getFloatOr(AMOUNT, 0.0F), 0.0F, BloodiedMath.MAX);
			if (amount > 0.0F) {
				out.add(new BloodLayer(BloodKind.byId(tag.getStringOr(KIND, "soak")), tag.getStringOr(TINT, "red"),
						amount, tag.getIntOr(SEED, 0), tag.getLongOr(WET_AT, 0L)));
			}
		}
		return List.copyOf(out);
	}

	/** Writes the marks, or takes the key away when there are none left. */
	public static void write(ItemStack stack, List<BloodLayer> layers) {
		if (layers.isEmpty()) {
			if (!read(stack).isEmpty()) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY));
			}
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			ListTag list = new ListTag();
			for (BloodLayer layer : layers) {
				CompoundTag entry = new CompoundTag();
				entry.putString(KIND, layer.kind().id());
				entry.putString(TINT, layer.tint());
				entry.putFloat(AMOUNT, layer.amount());
				entry.putInt(SEED, layer.seed());
				entry.putLong(WET_AT, layer.wetAt());
				list.add(entry);
			}
			CompoundTag blood = new CompoundTag();
			blood.put(LAYERS, list);
			tag.put(KEY, blood);
		});
	}

	/** Lands blood on the item: joining a wet mark of its own kind and colour, or starting one. */
	public static void add(ItemStack stack, BloodKind kind, String tint, float amount, int seed, long gameTime) {
		if (!takesBlood(stack) || amount <= 0.0F) {
			return;
		}
		List<BloodLayer> before = read(stack);
		List<BloodLayer> after = BloodiedMath.added(before, kind, tint, amount, seed, gameTime);
		if (after != before) {
			write(stack, after);
		}
	}

	/** One second in water. Returns whether anything came off. */
	public static boolean wash(ItemStack stack, long gameTime) {
		List<BloodLayer> before = read(stack);
		if (before.isEmpty()) {
			return false;
		}
		write(stack, BloodiedMath.washed(before, gameTime));
		return true;
	}

	public static void clear(ItemStack stack) {
		write(stack, List.of());
	}

	/**
	 * Whether two items are the same thing apart from the blood on them. The
	 * hand and the pickaxe use it: a sword that just got bloody is the sword
	 * you were holding, and should neither dip out of view as if you had
	 * swapped it nor start a block's cracks over.
	 */
	public static boolean sameButForBlood(ItemStack a, ItemStack b) {
		if (a.isEmpty() || b.isEmpty() || !a.is(b.getItem()) || a.getCount() != b.getCount()) {
			return false;
		}
		if (read(a).isEmpty() && read(b).isEmpty()) {
			return false;
		}
		ItemStack cleanA = a.copy();
		ItemStack cleanB = b.copy();
		clear(cleanA);
		clear(cleanB);
		return ItemStack.matchesIgnoringComponents(cleanA, cleanB,
				net.minecraft.core.component.DataComponentType::ignoreSwapAnimation);
	}

	// --- where the blood comes from ---

	/**
	 * Every region's wound as it stands, to be read back after a blow: 0 for
	 * none, else the severity's place plus one. Taken before the pipeline runs
	 * so the blow can be told apart from what was already there.
	 */
	public static int[] woundsNow(RealisticDamageData data) {
		int[] out = new int[BodyPart.values().length];
		for (BleedWound wound : data.wounds()) {
			out[wound.part().ordinal()] = Math.max(out[wound.part().ordinal()], wound.severity().ordinal() + 1);
		}
		return out;
	}

	/** Which regions are gone, as bits by region; taken before a blow for the same reason. */
	public static int destroyedNow(RealisticDamageData data) {
		return CeilingLogic.mask(data, BodyPartData::destroyed);
	}

	/**
	 * A blow landed and the pipeline has dealt with it. If it was dealt by hand
	 * with something held, the thing held takes blood for what the blow did.
	 *
	 * @param woundsBefore    {@link #woundsNow} from before the blow
	 * @param destroyedBefore {@link #destroyedNow} from before the blow
	 */
	public static void afterBlow(LivingEntity victim, RealisticDamageData data, DamageSource source,
			List<TraumaPipeline.AppliedTrauma> traumas, int[] woundsBefore, int destroyedBefore) {
		ItemStack weapon = weapon(victim, source);
		if (weapon == null) {
			return;
		}
		int[] woundsAfter = woundsNow(data);
		float amount = 0.0F;
		float specks = 0.0F;
		int where = 0;
		for (TraumaPipeline.AppliedTrauma trauma : traumas) {
			if (!trauma.primary()) {
				continue;
			}
			int part = trauma.part().ordinal();
			if (woundsAfter[part] == 0) {
				continue;
			}
			WoundSeverity severity = WoundSeverity.values()[woundsAfter[part] - 1];
			boolean opened = woundsAfter[part] > woundsBefore[part];
			float left = BloodiedMath.fromWound(severity) * (opened ? 1.0F : BloodiedMath.INTO_OPEN_WOUND);
			if (left > amount) {
				amount = left;
				where = part;
			}
			if (opened) {
				specks = Math.max(specks, BloodiedMath.spatter(severity));
			}
		}
		boolean severed = (destroyedNow(data) & ~destroyedBefore) != 0;
		if (amount <= 0.0F && !severed) {
			return;
		}
		String tint = tintOf(victim);
		long now = victim.level().getGameTime();
		int seed = seed(now, victim.getId(), where);
		add(weapon, kindOf(victim, source), tint, amount, seed, now);
		add(weapon, BloodKind.SPECKS, tint, specks, seed * 31 + 7, now);
		if (severed) {
			add(weapon, BloodKind.SOAK, tint, BloodiedMath.SEVERING_BLOW, seed * 31 + 13, now);
		}
	}

	/**
	 * The blow that killed. The pipeline never sees it (the game does not
	 * report a hit that kills as a hit), so it is judged here: an edge or a
	 * point always bloodies the weapon, and a blunt one does if the body was
	 * already bleeding.
	 */
	public static void afterKill(LivingEntity victim, DamageSource source) {
		ItemStack weapon = weapon(victim, source);
		if (weapon == null || !MobAnatomy.participates(victim)) {
			return;
		}
		ClassifiedDamage classified = DamageClassifier.classify(victim, source);
		boolean cuts = classified != null && DamageProfiles.of(
				classified.woundAs() != null ? classified.woundAs() : classified.category()).canBleed();
		if (!cuts && !RDAttachments.get(victim).bleeding()) {
			return;
		}
		String tint = tintOf(victim);
		long now = victim.level().getGameTime();
		int seed = seed(now, victim.getId(), 97);
		add(weapon, kindOf(victim, source), tint, BloodiedMath.KILLING_BLOW, seed, now);
		add(weapon, BloodKind.SPECKS, tint, BloodiedMath.spatter(WoundSeverity.SEVERE), seed * 31 + 7, now);
	}

	/**
	 * The held thing that dealt this, or null: only a blow struck by hand
	 * counts. An arrow's blood is on the arrow, thorns draw blood with the
	 * armour and not with whatever its wearer was holding, and a body with no
	 * blood in it leaves none.
	 */
	private static @Nullable ItemStack weapon(LivingEntity victim, DamageSource source) {
		if (!ServerConfig.get().enableBloodiedItems || !ServerConfig.get().enableBleeding
				|| !MobAnatomy.bleeds(victim)) {
			return null;
		}
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker
				|| attacker == victim || source.is(DamageTypes.THORNS)
				|| source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return null;
		}
		ItemStack held = attacker.getMainHandItem();
		return takesBlood(held) ? held : null;
	}

	/** The shape a blow of this sort leaves. */
	private static BloodKind kindOf(LivingEntity victim, DamageSource source) {
		ClassifiedDamage classified = DamageClassifier.classify(victim, source);
		if (classified == null) {
			return BloodKind.SOAK;
		}
		return switch (classified.category()) {
			case SLASH, HEAVY_SLASH -> BloodKind.STREAK;
			case PUNCTURE, HEAVY_PUNCTURE -> BloodKind.TIP;
			case BLUNT, HEAVY_BLUNT -> BloodKind.SPLAT;
			default -> BloodKind.SOAK;
		};
	}

	/**
	 * Where a mark sits, from when and what it was struck out of. Not chance:
	 * the same blow in the same tick on the same body is the same mark, and
	 * nothing about an injury ever depends on it.
	 */
	private static int seed(long gameTime, int victimId, int salt) {
		long h = gameTime * 0x9E3779B97F4A7C15L ^ (victimId * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) h;
	}

	/**
	 * Whose blood this is, as the name of a client palette entry. The order is
	 * the client's own ({@code BloodTint.ofType}), so an item and the stain
	 * beside it agree on the colour.
	 */
	public static String tintOf(LivingEntity victim) {
		if (victim instanceof Player) {
			return "red";
		}
		if (victim.is(MobAnatomy.BLOOD_PURPLE)) {
			return "purple";
		}
		if (victim.is(MobAnatomy.BLOOD_DARK)) {
			return "dark";
		}
		if (victim.is(MobAnatomy.BLOOD_GREEN)) {
			return "green";
		}
		if (victim.is(MobAnatomy.BLOOD_MAGMA)) {
			return "magma";
		}
		if (victim.is(MobAnatomy.BLOOD_BLUE)) {
			return "blue";
		}
		if (victim.is(MobAnatomy.BLOOD_GLOW)) {
			return "glow";
		}
		if (victim.is(MobAnatomy.BLOOD_INSECT)) {
			return "insect";
		}
		if (victim.is(MobAnatomy.BLOOD_SCULK)) {
			return "sculk";
		}
		if (victim.is(MobAnatomy.BLOOD_SULFUR)) {
			return "sulfur";
		}
		return "red";
	}

	// --- once a second, for whoever is holding it ---

	/**
	 * Once a second per player: water takes blood off what they hold (both
	 * hands, in rain or while standing in water), and a weapon arm that is
	 * bleeding more than a scratch runs onto the grip of what it holds.
	 */
	public static void tickHolder(ServerPlayer player) {
		if (player.tickCount % 20 != 0) {
			return;
		}
		long now = player.level().getGameTime();
		if (player.isInWaterOrRain()) {
			wash(player.getMainHandItem(), now);
			wash(player.getOffhandItem(), now);
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!takesBlood(held) || !ServerConfig.get().enableBloodiedItems || !ServerConfig.get().enableBleeding
				|| player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		if (data.underPressure()) {
			return;
		}
		BodyPart arm = player.getMainArm() == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		WoundSeverity worst = null;
		for (BleedWound wound : data.wounds()) {
			if (wound.part() == arm && (worst == null || wound.severity().ordinal() > worst.ordinal())) {
				worst = wound.severity();
			}
		}
		float more = BloodiedMath.gripPerSecond(worst);
		if (more <= 0.0F) {
			return;
		}
		List<BloodLayer> before = read(held);
		List<BloodLayer> after = BloodiedMath.grip(before, "red", more, seed(now, player.getId(), 41), now);
		// Written only when the picture would change: a full grip that is being
		// kept wet does not need telling every second.
		if (after.size() != before.size() || looksDifferent(before, after, now)) {
			write(held, after);
		}
	}

	private static boolean looksDifferent(List<BloodLayer> before, List<BloodLayer> after, long now) {
		for (int i = 0; i < after.size(); i++) {
			BloodLayer was = before.get(i);
			BloodLayer is = after.get(i);
			if (BloodiedMath.amountStep(was.amount()) != BloodiedMath.amountStep(is.amount())
					|| BloodiedMath.wetStep(now, was.wetAt()) != BloodiedMath.wetStep(now, is.wetAt())) {
				return true;
			}
		}
		return false;
	}
}
