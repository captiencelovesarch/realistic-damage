package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.client.mixin.AgeableMobRendererAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The model each living thing was last drawn with. A renderer holds more than
 * one (a baby zombie's own small model beside the adult's, a cow's for each
 * climate) and picks one as it draws, so "the renderer's model" is whichever
 * body it drew last. A ragdoll built from the wrong one is the wrong size and
 * shape, and is drawn stretched over the right one.
 */
public final class ModelMemory {
	private ModelMemory() {}

	/** More than this many remembered and the lot is forgotten (the dead and the gone pile up). */
	private static final int MOST = 4096;
	private static final Int2ObjectOpenHashMap<EntityModel<?>> LAST = new Int2ObjectOpenHashMap<>();

	/** As a body is drawn: the model its renderer chose for it. */
	public static void remember(int entityId, EntityModel<?> model) {
		if (entityId < 0) {
			return;
		}
		if (LAST.size() >= MOST && !LAST.containsKey(entityId)) {
			LAST.clear();
		}
		LAST.put(entityId, model);
	}

	/**
	 * The model this body is drawn with: the one it was last drawn with, or, for
	 * one this client has not drawn (out of sight), the adult or baby model by
	 * its age where the renderer keeps both, or the renderer's own.
	 */
	public static EntityModel<?> of(LivingEntityRenderer<?, ?, ?> renderer, LivingEntity entity) {
		EntityModel<?> drawn = LAST.get(entity.getId());
		if (drawn != null) {
			return drawn;
		}
		if (renderer instanceof AgeableMobRenderer<?, ?, ?> ageable) {
			AgeableMobRendererAccess access = (AgeableMobRendererAccess) ageable;
			return entity.isBaby() ? access.realisticdamage$babyModel() : access.realisticdamage$adultModel();
		}
		return renderer.getModel();
	}

	/** A new world: nothing drawn in the old one is here. */
	public static void clear() {
		LAST.clear();
	}
}
