package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerLevel;
import dev.captience.realisticdamage.body.BodyPart;

/**
 * Custom damage types (data-driven, see {@code data/realisticdamage/damage_type/}).
 * Everything the mod inflicts on a player carries the {@link #INTERNAL} tag so it
 * can never re-enter the trauma pipeline (spec §51).
 */
public final class RDDamageTypes {
	public static final ResourceKey<DamageType> BLEED_OUT = ResourceKey.create(
			Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("realisticdamage", "bleed_out"));

	/** Fatal loss of the head (Arcade preset limb destruction). */
	public static final ResourceKey<DamageType> SKULL_CRUSHED = ResourceKey.create(
			Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("realisticdamage", "skull_crushed"));

	/** Fatal loss of the torso (Arcade preset limb destruction). */
	public static final ResourceKey<DamageType> CHEST_CRUSHED = ResourceKey.create(
			Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("realisticdamage", "chest_crushed"));

	/**
	 * Every damage type the mod inflicts itself. The recursion guards test this
	 * tag rather than listing keys, so a damage type added later cannot silently
	 * feed itself back into the pipeline by being forgotten in two places.
	 */
	public static final TagKey<DamageType> INTERNAL = TagKey.create(
			Registries.DAMAGE_TYPE, RealisticDamage.id("internal"));

	public static DamageSource bleedOut(ServerLevel level) {
		return level.damageSources().source(BLEED_OUT);
	}

	/** The fatal source for destroying a core region — they die differently. */
	public static DamageSource coreDestroyed(ServerLevel level, BodyPart part) {
		return level.damageSources().source(part == BodyPart.HEAD ? SKULL_CRUSHED : CHEST_CRUSHED);
	}

	private RDDamageTypes() {}
}
