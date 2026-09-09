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
	private static final Map<Identifier, Look> LOOKS = Map.of(
			Identifier.withDefaultNamespace("zombie"), ZOMBIE,
			Identifier.withDefaultNamespace("husk"), new Look(Identifier.withDefaultNamespace("textures/entity/zombie/husk.png"), LimbMeshes.Kind.WIDE),
			Identifier.withDefaultNamespace("drowned"), new Look(Identifier.withDefaultNamespace("textures/entity/zombie/drowned.png"), LimbMeshes.Kind.WIDE),
			Identifier.withDefaultNamespace("skeleton"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png"), LimbMeshes.Kind.THIN),
			Identifier.withDefaultNamespace("stray"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/stray.png"), LimbMeshes.Kind.THIN),
			Identifier.withDefaultNamespace("wither_skeleton"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png"), LimbMeshes.Kind.THIN),
			Identifier.withDefaultNamespace("bogged"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/bogged.png"), LimbMeshes.Kind.THIN),
			Identifier.withDefaultNamespace("parched"), new Look(Identifier.withDefaultNamespace("textures/entity/skeleton/parched.png"), LimbMeshes.Kind.THIN));

	public static Look lookFor(@Nullable EntityType<?> type) {
		if (type == null) {
			return ZOMBIE;
		}
		return LOOKS.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(type), ZOMBIE);
	}
}
