package dev.captience.realisticdamage.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerLevel;

/**
 * Custom damage types (data-driven, see {@code data/realisticdamage/damage_type/}).
 * Bleed-out damage is tagged so it can never re-enter the trauma pipeline (spec §51).
 */
public final class RDDamageTypes {
	public static final ResourceKey<DamageType> BLEED_OUT = ResourceKey.create(
			Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("realisticdamage", "bleed_out"));

	/** Fatal loss of the head or torso (Arcade preset limb destruction). */
	public static final ResourceKey<DamageType> DESTROYED = ResourceKey.create(
			Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("realisticdamage", "destroyed"));

	public static DamageSource bleedOut(ServerLevel level) {
		return level.damageSources().source(BLEED_OUT);
	}

	public static DamageSource destroyed(ServerLevel level) {
		return level.damageSources().source(DESTROYED);
	}

	private RDDamageTypes() {}
}
