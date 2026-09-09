package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.damage.ClassifiedDamage.Routing;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Maps vanilla damage sources onto {@link DamageCategory} + routing (spec
 * §33-35). Tag- and component-driven where possible so modded weapons that use
 * vanilla conventions classify sensibly; hardcoded entity identities only for
 * vanilla mobs with distinctive attacks.
 */
public final class DamageClassifier {
	private DamageClassifier() {}

	/** Returns null when the event should produce no Realistic Damage consequences. */
	@Nullable
	public static ClassifiedDamage classify(LivingEntity victim, DamageSource source) {
		// Recursion safety (spec §51) and vanilla-behavior passthroughs.
		if (source.is(RDDamageTypes.INTERNAL)
				|| source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypes.OUTSIDE_BORDER)
				|| source.is(DamageTypes.GENERIC_KILL)
				|| source.is(DamageTypes.STARVE)) {
			return null;
		}

		// Interoperability (1.3), most specific answer first: a mod that knows
		// what its own weapon did, then whatever a datapack has declared, then
		// the built-in table. Both layers can override vanilla, which is the
		// point — a server should be able to decide a trident punctures.
		// The public API is typed against players (1.3) and stays so; a mob
		// victim skips the external hooks and goes straight to the tags.
		if (victim instanceof ServerPlayer player
				&& dev.captience.realisticdamage.api.RealisticDamageApi.hasClassifiers()) {
			var external = dev.captience.realisticdamage.api.RealisticDamageApi
					.classifyExternally(player, source);
			if (external != null) {
				return external.toClassified();
			}
		}
		ClassifiedDamage tagged = DamageTypeClassification.classify(source);
		if (tagged != null) {
			return tagged;
		}

		// Heat.
		if (source.is(DamageTypes.LAVA)) {
			return ClassifiedDamage.of(DamageCategory.LAVA, Routing.SPREAD);
		}
		if (source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.SULFUR_CUBE_HOT)) {
			return ClassifiedDamage.of(DamageCategory.FIRE, Routing.LEGS);
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return ClassifiedDamage.of(DamageCategory.FIRE, Routing.SPREAD);
		}

		// Falls and things falling on you.
		if (source.is(DamageTypes.STALAGMITE)) {
			// Fall trauma that also punctures (spec §30).
			return new ClassifiedDamage(DamageCategory.FALL, Routing.FALL_LEGS, 1.0, DamageCategory.HEAVY_PUNCTURE);
		}
		if (source.is(DamageTypes.ENDER_PEARL)) {
			return ClassifiedDamage.of(DamageCategory.GENERIC, Routing.LEGS);
		}
		if (source.is(DamageTypes.FLY_INTO_WALL)) {
			return ClassifiedDamage.of(DamageCategory.BLUNT, Routing.HEAD_TORSO);
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return ClassifiedDamage.of(DamageCategory.FALL, Routing.FALL_LEGS);
		}
		if (source.is(DamageTypes.FALLING_ANVIL)) {
			return ClassifiedDamage.of(DamageCategory.HEAVY_BLUNT, Routing.HEAD_HEAVY);
		}
		if (source.is(DamageTypes.FALLING_STALACTITE)) {
			return ClassifiedDamage.of(DamageCategory.HEAVY_PUNCTURE, Routing.HEAD_HEAVY);
		}
		if (source.is(DamageTypes.FALLING_BLOCK)) {
			return ClassifiedDamage.of(DamageCategory.BLUNT, Routing.HEAD_HEAVY);
		}

		// Consciousness-led sources (spec §35: sonic boom is not a limb hit).
		if (source.is(DamageTypes.SONIC_BOOM)) {
			return ClassifiedDamage.of(DamageCategory.MAGIC, Routing.NONE);
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return ClassifiedDamage.of(DamageCategory.EXPLOSION, Routing.EXPLOSION_DIRECTIONAL);
		}

		// Asphyxia and environment.
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return ClassifiedDamage.of(DamageCategory.DROWNING, Routing.NONE);
		}
		if (source.is(DamageTypes.IN_WALL)) {
			return ClassifiedDamage.of(DamageCategory.SUFFOCATION, Routing.TORSO);
		}
		if (source.is(DamageTypes.CRAMMING)) {
			return ClassifiedDamage.of(DamageCategory.SUFFOCATION, Routing.TORSO);
		}
		if (source.is(DamageTypes.CACTUS)) {
			return ClassifiedDamage.of(DamageCategory.PUNCTURE, Routing.LEGS_TORSO, 0.6);
		}
		if (source.is(DamageTypes.SWEET_BERRY_BUSH)) {
			return ClassifiedDamage.of(DamageCategory.GENERIC, Routing.LEGS);
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			return ClassifiedDamage.of(DamageCategory.SYSTEMIC, Routing.SPREAD);
		}

		// Systemic / magic (spec §9). Poison has no damage type: it arrives as
		// minecraft:magic while the Poison effect is active.
		if (source.is(DamageTypes.WITHER)) {
			return ClassifiedDamage.of(DamageCategory.SYSTEMIC, Routing.SPREAD, 2.0);
		}
		if (source.is(DamageTypes.MAGIC) && victim.hasEffect(MobEffects.POISON)) {
			return ClassifiedDamage.of(DamageCategory.SYSTEMIC, Routing.SPREAD);
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
				|| source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypeTags.IS_LIGHTNING)
				|| source.is(DamageTypes.THORNS)) {
			return ClassifiedDamage.of(DamageCategory.MAGIC, Routing.SPREAD);
		}

		// Projectiles: actual hit location (spec §6).
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			if (source.is(DamageTypes.ARROW)) {
				return ClassifiedDamage.of(DamageCategory.PUNCTURE, Routing.LOCATIONAL);
			}
			if (source.is(DamageTypes.TRIDENT)) {
				return ClassifiedDamage.of(DamageCategory.HEAVY_PUNCTURE, Routing.LOCATIONAL);
			}
			if (source.is(DamageTypes.WIND_CHARGE)) {
				return ClassifiedDamage.of(DamageCategory.BLUNT, Routing.LOCATIONAL, 0.5);
			}
			return ClassifiedDamage.of(DamageCategory.GENERIC, Routing.LOCATIONAL);
		}

		// Melee (incl. the 26.x spear stab and mace smash).
		if (source.is(DamageTypes.SPEAR)) {
			return ClassifiedDamage.of(DamageCategory.HEAVY_PUNCTURE, Routing.LOCATIONAL);
		}
		if (source.is(DamageTypes.MACE_SMASH)) {
			return ClassifiedDamage.of(DamageCategory.HEAVY_BLUNT, Routing.LOCATIONAL);
		}
		if (source.is(DamageTypes.PLAYER_ATTACK)) {
			return ClassifiedDamage.of(weaponCategory(source.getWeaponItem()), Routing.LOCATIONAL);
		}
		if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
				|| source.is(DamageTypes.STING)) {
			ItemStack weapon = source.getWeaponItem();
			if (weapon != null && !weapon.isEmpty()) {
				// Armed mobs (piglins etc.) classify by their weapon (spec §35).
				return ClassifiedDamage.of(weaponCategory(weapon), Routing.LOCATIONAL);
			}
			return ClassifiedDamage.of(mobCategory(source.getEntity()), Routing.LOCATIONAL);
		}

		// Unknown direct attack -> locate it; unknown ambient -> gentle spread.
		if (source.getDirectEntity() != null) {
			return ClassifiedDamage.of(DamageCategory.GENERIC, Routing.LOCATIONAL);
		}
		return ClassifiedDamage.of(DamageCategory.GENERIC, Routing.SPREAD, 0.5);
	}

	/** Vanilla weapon identities (spec §34). */
	private static DamageCategory weaponCategory(@Nullable ItemStack weapon) {
		if (weapon == null || weapon.isEmpty()) {
			return DamageCategory.BLUNT; // fists: blunt but weak — trauma comes from the tiny damage
		}
		if (weapon.is(Items.MACE)) {
			return DamageCategory.HEAVY_BLUNT;
		}
		if (weapon.is(Items.TRIDENT) || weapon.has(DataComponents.PIERCING_WEAPON)) {
			return DamageCategory.HEAVY_PUNCTURE;
		}
		if (weapon.is(ItemTags.AXES)) {
			return DamageCategory.HEAVY_SLASH;
		}
		if (weapon.is(ItemTags.SWORDS)) {
			return DamageCategory.SLASH;
		}
		return DamageCategory.GENERIC;
	}

	/** Distinctive vanilla mob attacks (spec §35). */
	private static DamageCategory mobCategory(@Nullable Entity attacker) {
		if (attacker == null) {
			return DamageCategory.GENERIC;
		}
		EntityType<?> type = attacker.getType();
		if (type == EntityTypes.IRON_GOLEM || type == EntityTypes.RAVAGER || type == EntityTypes.WARDEN
				|| type == EntityTypes.HOGLIN || type == EntityTypes.ZOGLIN) {
			return DamageCategory.HEAVY_BLUNT;
		}
		if (type == EntityTypes.ENDERMAN || type == EntityTypes.GOAT || type == EntityTypes.POLAR_BEAR) {
			return DamageCategory.BLUNT;
		}
		if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER || type == EntityTypes.WOLF) {
			return DamageCategory.SLASH;
		}
		if (attacker instanceof LivingEntity) {
			return DamageCategory.GENERIC;
		}
		return DamageCategory.GENERIC;
	}
}
