package dev.captience.realisticdamage.client;

import java.util.List;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Part visibility for the models with no hand-written limb code (1.7 slice 3):
 * a cow's lost leg, a spider's lost pair, a bat's lost wing — and the isolation
 * that lets {@link RegionModel} draw one region.
 *
 * <p>Hooked around one model draw: after the draw has posed the model for its
 * submit and before it renders, the visibility of every part is saved, lost
 * limbs are hidden, and a region draw hides everything that is not its region;
 * after the render, everything is put back. Because it is undone inside the
 * same draw, nothing leaks to the next entity that shares the model — the
 * failure the humanoid path learned about in alpha.3.
 *
 * <p>Models that already hide their own limbs ({@link HiddenLimbs}: humanoid,
 * illager, villager, witch) are left to that code. Render thread only.
 */
public final class GenericLimbs {
	private GenericLimbs() {}

	private static List<ModelPart> saved;
	private static boolean[] visible = new boolean[64];
	private static boolean[] skipDraw = new boolean[64];

	/** Region draws and hidden-limb draws this session; read by the client gametest. */
	private static long regionDraws;
	private static long hiddenDraws;

	public static long regionDraws() {
		return regionDraws;
	}

	public static long hiddenDraws() {
		return hiddenDraws;
	}

	/** After the draw posed {@code model} for {@code state}, before it renders. */
	public static void afterPose(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || living.entityType == null) {
			return;
		}
		boolean region = model instanceof RegionModel<?>;
		int destroyed = ((LimbStateHolder) living).realisticdamage$destroyedMask();
		boolean ownLimbs = model instanceof HiddenLimbs;
		if (!region && (destroyed == 0 || ownLimbs)) {
			return;
		}
		BodyPlan plan = MobAnatomy.plan(living.entityType);
		ModelPart root = model.root();
		save(model.allParts());
		if (destroyed != 0 && !ownLimbs) {
			for (BodyPart part : BodyPart.values()) {
				if (part.isLimb() && LimbStateExtractor.gone(destroyed, part) && plan.has(part)) {
					ModelRegions.Region lost = ModelRegions.region(root, plan, part);
					if (lost.parts() != ModelRegions.region(root, plan, BodyPart.TORSO).parts()) {
						for (ModelPart limb : lost.parts()) {
							limb.visible = false;
						}
					}
				}
			}
			if (!region) {
				hiddenDraws++;
			}
		}
		if (region) {
			isolate(model.allParts(), ModelRegions.region(root, plan, ((RegionModel<?>) model).region()));
			regionDraws++;
		}
	}

	/** After the render: everything back the way the pose left it. */
	public static void afterRender() {
		if (saved == null) {
			return;
		}
		for (int i = 0; i < saved.size(); i++) {
			ModelPart part = saved.get(i);
			part.visible = visible[i];
			part.skipDraw = skipDraw[i];
		}
		saved = null;
	}

	private static void save(List<ModelPart> parts) {
		if (visible.length < parts.size()) {
			visible = new boolean[parts.size() * 2];
			skipDraw = new boolean[parts.size() * 2];
		}
		for (int i = 0; i < parts.size(); i++) {
			visible[i] = parts.get(i).visible;
			skipDraw[i] = parts.get(i).skipDraw;
		}
		saved = parts;
	}

	/** Only the region is drawn; its ancestors carry the pose without drawing their own cubes. */
	private static void isolate(List<ModelPart> parts, ModelRegions.Region region) {
		for (ModelPart part : parts) {
			if (region.drawn().contains(part)) {
				part.skipDraw = false;
			} else if (region.ancestors().contains(part)) {
				part.skipDraw = true;
			} else if (part.visible) {
				// The root itself is an ancestor of everything and has no cubes of its own to hide.
				part.visible = false;
			}
		}
	}
}
