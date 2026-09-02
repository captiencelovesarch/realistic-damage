package dev.captience.realisticdamage;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The blood drop (1.5).
 *
 * <p>Deliberately a <em>simple</em> particle type, carrying no payload of its
 * own. Everything the renderer needs to know about a drop — how big it is, how
 * hard it left the wound, whether it is a seep or an arterial throw — is read
 * back from the velocity it was spawned with, and velocity is already on the
 * wire for every particle vanilla sends. A custom {@code ParticleOptions} would
 * have bought the same information at the cost of a codec, a stream codec and a
 * network format to keep compatible.
 *
 * <p>Registered on both sides: continuous bleeding is emitted entirely on the
 * client from the synced body snapshot, but the one-shot spray when a wound
 * opens is thrown by the server, and the registry has to match either way.
 */
public final class RDParticles {
	private RDParticles() {}

	public static final SimpleParticleType BLOOD = FabricParticleTypes.simple();

	public static void init() {
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, RealisticDamage.id("blood"), BLOOD);
	}
}
