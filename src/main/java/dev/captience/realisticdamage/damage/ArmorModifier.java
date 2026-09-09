package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * Localized armor (spec §31): vanilla armor already mitigated the HP damage our
 * trauma base derives from, so this layer only answers "was the RIGHT piece
 * worn?" — presence + relevant enchantments, deterministic, capped so armor
 * never zeroes the mod out.
 */
public final class ArmorModifier {
	private ArmorModifier() {}

	/** Fraction (0..cap) by which the covering armor reduces body trauma for this part. */
	public static double reduction(LivingEntity player, BodyPart part, DamageCategory category) {
		double total = 0.0;
		total += pieceReduction(player, EquipmentSlot.HEAD, coverageHead(part), category);
		total += pieceReduction(player, EquipmentSlot.CHEST, coverageChest(part), category);
		total += pieceReduction(player, EquipmentSlot.LEGS, coverageLegs(part), category);
		total += pieceReduction(player, EquipmentSlot.FEET, coverageFeet(part, category), category);
		double cap = category == DamageCategory.FALL ? Balance.ARMOR_FALL_REDUCTION_CAP : Balance.ARMOR_REDUCTION_CAP;
		return Math.min(total, cap);
	}

	private static double pieceReduction(LivingEntity player, EquipmentSlot slot, double coverage, DamageCategory category) {
		if (coverage <= 0.0) {
			return 0.0;
		}
		ItemStack piece = player.getItemBySlot(slot);
		if (piece.isEmpty()) {
			return 0.0;
		}
		double reduction = Balance.LOCAL_ARMOR_MAX_REDUCTION;
		reduction += Math.min(Balance.PROTECTION_TRAUMA_CAP,
				Balance.PROTECTION_TRAUMA_PER_LEVEL * level(player, Enchantments.PROTECTION, piece));
		ResourceKey<Enchantment> specialized = specializedEnchantment(category);
		if (specialized != null) {
			reduction += Balance.ENCH_SPECIALIZED_TRAUMA_PER_LEVEL * level(player, specialized, piece);
		}
		if (category == DamageCategory.FALL && slot == EquipmentSlot.FEET) {
			// Feather Falling is very strong against leg trauma (spec §30-31).
			reduction += Balance.FEATHER_FALLING_LEG_TRAUMA_PER_LEVEL * level(player, Enchantments.FEATHER_FALLING, piece);
		}
		return coverage * reduction;
	}

	@Nullable
	private static ResourceKey<Enchantment> specializedEnchantment(DamageCategory category) {
		return switch (category) {
			case EXPLOSION -> Enchantments.BLAST_PROTECTION;
			case PUNCTURE, HEAVY_PUNCTURE -> Enchantments.PROJECTILE_PROTECTION;
			case FIRE, LAVA -> Enchantments.FIRE_PROTECTION;
			default -> null;
		};
	}

	/**
	 * Projectile Protection also reduces bleeding severity (spec §31): a strongly
	 * enchanted covering piece downgrades puncture wounds one step (a MINOR wound
	 * disappears entirely). Deterministic: total level >= 3 across covering slots.
	 */
	@Nullable
	public static WoundSeverity downgradeWound(LivingEntity player,
			BodyPart part, DamageCategory category, WoundSeverity severity) {
		if (category != DamageCategory.PUNCTURE && category != DamageCategory.HEAVY_PUNCTURE) {
			return severity;
		}
		int level = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
				continue;
			}
			double coverage = switch (slot) {
				case HEAD -> coverageHead(part);
				case CHEST -> coverageChest(part);
				case LEGS -> coverageLegs(part);
				case FEET -> coverageFeet(part, category);
				default -> 0.0;
			};
			if (coverage > 0.0) {
				ItemStack piece = player.getItemBySlot(slot);
				if (!piece.isEmpty()) {
					level += level(player, Enchantments.PROJECTILE_PROTECTION, piece);
				}
			}
		}
		return level >= 3 ? severity.weaker() : severity;
	}

	private static int level(LivingEntity player, ResourceKey<Enchantment> key, ItemStack piece) {
		Holder<Enchantment> holder = player.level().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		return EnchantmentHelper.getItemEnchantmentLevel(holder, piece);
	}

	// Coverage map (spec §31): helmet=head; chest=torso strongly + arms partially;
	// leggings=legs strongly + lower torso; boots=legs somewhat, more for falls.
	private static double coverageHead(BodyPart part) {
		return part == BodyPart.HEAD ? 1.0 : 0.0;
	}

	private static double coverageChest(BodyPart part) {
		if (part == BodyPart.TORSO) {
			return 1.0;
		}
		return part.isArm() ? 0.6 : 0.0;
	}

	private static double coverageLegs(BodyPart part) {
		if (part.isLeg()) {
			return 1.0;
		}
		return part == BodyPart.TORSO ? 0.3 : 0.0;
	}

	private static double coverageFeet(BodyPart part, DamageCategory category) {
		if (!part.isLeg()) {
			return 0.0;
		}
		return category == DamageCategory.FALL ? 1.0 : 0.5;
	}
}
