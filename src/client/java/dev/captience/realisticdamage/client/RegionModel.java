package dev.captience.realisticdamage.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.client.model.Model;

/**
 * One body region of another model, as a model of its own (1.7 slice 3).
 *
 * <p>Models are shared by every entity their renderer draws, and the frame is
 * submitted first and drawn later, so a layer cannot simply hand over the live
 * leg of a cow: by the time it is drawn, that leg is posed for whichever cow
 * was set up last. A model submit does not have that problem, because the draw
 * calls {@code setupAnim} with the submit's own state right before it renders.
 * This wraps the real model so its submit is one of those: posing delegates to
 * the real model, and {@link GenericLimbs} (hooked on the draw) shows only this
 * region's parts for the one render and puts everything back after.
 */
public final class RegionModel<S> extends Model<S> {
	private final Model<S> inner;
	private final BodyPart region;

	private static final Map<Model<?>, Map<BodyPart, RegionModel<?>>> WRAPPERS = new WeakHashMap<>();

	private RegionModel(Model<S> inner, BodyPart region) {
		super(inner.root(), inner.renderType());
		this.inner = inner;
		this.region = region;
	}

	@SuppressWarnings("unchecked")
	public static <S> RegionModel<S> of(Model<S> model, BodyPart region) {
		return (RegionModel<S>) WRAPPERS.computeIfAbsent(model, m -> new EnumMap<>(BodyPart.class))
				.computeIfAbsent(region, r -> new RegionModel<>(model, r));
	}

	public BodyPart region() {
		return region;
	}

	@Override
	public void setupAnim(S state) {
		inner.setupAnim(state);
	}
}
