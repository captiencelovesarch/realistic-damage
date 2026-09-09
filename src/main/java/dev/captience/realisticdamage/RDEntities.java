package dev.captience.realisticdamage;

import dev.captience.realisticdamage.entity.SeveredLimb;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The one entity the mod adds (1.6.2): a lost limb, lying where it landed.
 *
 * <p>Not saved, not summonable, and small. Tracked to ten chunks because it
 * is a thing you see from across a clearing and walk up to, and updated at
 * vanilla's default interval because after the first second it is not moving.
 */
public final class RDEntities {
	private RDEntities() {}

	public static final ResourceKey<EntityType<?>> SEVERED_LIMB_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("severed_limb"));
	public static final EntityType<SeveredLimb> SEVERED_LIMB = EntityType.Builder
			.of(SeveredLimb::new, MobCategory.MISC)
			.sized(0.35F, 0.35F)
			.noSave()
			.noSummon()
			.clientTrackingRange(10)
			.build(SEVERED_LIMB_KEY);

	public static void init() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, SEVERED_LIMB_KEY, SEVERED_LIMB);
	}
}
