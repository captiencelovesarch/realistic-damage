package dev.captience.realisticdamage.client;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * What a mob's severed limb is drawn with (1.7): the texture its renderer uses
 * and which limb build it has. The server decides which types throw a limb at
 * all through the {@code throws_limbs} tag; this is the client's half of the
 * same list, and a type missing here falls back to the plain zombie.
 */
public final class MobLooks {
	private MobLooks() {}

	public record Look(Identifier texture, LimbMeshes.Kind kind) {}

	private static final Look ZOMBIE = new Look(Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png"), LimbMeshes.Kind.WIDE);
	private static final Map<Identifier, Look> LOOKS = Map.ofEntries(
			Map.entry(Identifier.withDefaultNamespace("zombie"), ZOMBIE),
			Map.entry(Identifier.withDefaultNamespace("husk"), new Look(Identifier.withDefaultNamespace("textures/entity/zombie/husk.png"), LimbMeshes.Kind.WIDE)),
			Map.entry(Identifier.withDefaultNamespace("drowned"), new Look(Identifier.withDefaultNamespace("textures/entity/zombie/drowned.png"), LimbMeshes.Kind.WIDE)),
			Map.entry(Identifier.withDefaultNamespace("skeleton"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png"), LimbMeshes.Kind.THIN)),
			Map.entry(Identifier.withDefaultNamespace("stray"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/stray.png"), LimbMeshes.Kind.THIN)),
			Map.entry(Identifier.withDefaultNamespace("wither_skeleton"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png"), LimbMeshes.Kind.THIN)),
			Map.entry(Identifier.withDefaultNamespace("bogged"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/bogged.png"), LimbMeshes.Kind.THIN)),
			Map.entry(Identifier.withDefaultNamespace("parched"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/parched.png"), LimbMeshes.Kind.THIN)),
			Map.entry(Identifier.withDefaultNamespace("piglin"), new Look(Identifier.withDefaultNamespace("textures/entity/piglin/piglin.png"), LimbMeshes.Kind.WIDE)),
			Map.entry(Identifier.withDefaultNamespace("piglin_brute"), new Look(Identifier.withDefaultNamespace("textures/entity/piglin/piglin_brute.png"), LimbMeshes.Kind.WIDE)),
			Map.entry(Identifier.withDefaultNamespace("zombified_piglin"), new Look(Identifier.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png"), LimbMeshes.Kind.WIDE)),
			Map.entry(Identifier.withDefaultNamespace("zombie_villager"), new Look(Identifier.withDefaultNamespace("textures/entity/zombie_villager/zombie_villager.png"), LimbMeshes.Kind.ZOMBIE_VILLAGER)),
			Map.entry(Identifier.withDefaultNamespace("pillager"), new Look(Identifier.withDefaultNamespace("textures/entity/illager/pillager.png"), LimbMeshes.Kind.ILLAGER)),
			Map.entry(Identifier.withDefaultNamespace("vindicator"), new Look(Identifier.withDefaultNamespace("textures/entity/illager/vindicator.png"), LimbMeshes.Kind.ILLAGER)),
			Map.entry(Identifier.withDefaultNamespace("evoker"), new Look(Identifier.withDefaultNamespace("textures/entity/illager/evoker.png"), LimbMeshes.Kind.ILLAGER)),
			Map.entry(Identifier.withDefaultNamespace("illusioner"), new Look(Identifier.withDefaultNamespace("textures/entity/illager/illusioner.png"), LimbMeshes.Kind.ILLAGER)));

	public static Look lookFor(@Nullable EntityType<?> type) {
		if (type == null) {
			return ZOMBIE;
		}
		return LOOKS.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(type), ZOMBIE);
	}
}
