package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * A body this client simulates (active ragdolls): a humanoid ragdoll
 * ({@link RagdollCorpse}) or any other model's ({@link GenericCorpse}). What
 * {@link RemoteCorpses} needs from either: ticking, the frame to draw, being
 * kept after the server lets go of the entity, and what the tests and the
 * debug view read.
 */
abstract class SimulatedCorpse implements RemoteCorpses.View {
	final int id;
	/** The entity this body was, to notice it gone or replaced (a respawn reuses the id). */
	final java.lang.ref.WeakReference<LivingEntity> entity;
	/**
	 * How it was last drawn (phase 4: bodies that stay). Minecraft makes a
	 * fresh state every frame, so holding the last one is safe; once the
	 * entity is gone, this is what the body keeps looking like.
	 */
	@Nullable LivingEntityRenderState lastState;
	/** Ticks since the entity went away, or -1 while it is still there. */
	int orphanAge = -1;
	int age;

	SimulatedCorpse(LivingEntity entity) {
		this.id = entity.getId();
		this.entity = new java.lang.ref.WeakReference<>(entity);
	}

	/** Once per client tick. */
	abstract void tick(double timeScale);

	/** Whether the simulation gave out (and the body should be dropped). */
	abstract boolean expired();

	/** This frame of the body, for the renderer. */
	abstract RemoteCorpses.Frame frame(float partialTicks);

	/** Vanilla's red death tint, for the first moment only. */
	boolean red() {
		return age < 8;
	}

	// For the debug view and the tests.

	abstract Iterable<Body> bodies();

	abstract boolean asleep();

	abstract double gap();

	abstract Vector3d centreOf();
}
