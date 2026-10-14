package dev.captience.realisticdamage.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.client.mixin.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Where a body region is on a model the mod has no baked shells for (1.7 slice
 * 3): found by part name, because vanilla names its parts consistently —
 * {@code left_front_leg} on every four-legged model, {@code left_wing} on every
 * winged one, {@code head} and {@code body} almost everywhere.
 *
 * <p>A region is a set of parts: a spider's front-left slot is two legs, a
 * salmon's torso is its front and back halves, a bee's legs are three cross
 * pieces it does not split by side. Limbs and heads are drawn with everything
 * under them (wing tips, feet, ears, a wolf's real head); a torso only as its
 * own cubes, because a torso is often the parent of everything else. A region
 * a model has no part for falls back to the torso's.
 *
 * <p>Resolved once per model root and cached weakly, so a reloaded resource
 * pack's new models resolve afresh and the old ones can go.
 */
public final class ModelRegions {
	private ModelRegions() {}

	/** One region's parts: what to draw, and the ancestors that only carry the pose. */
	public record Region(List<ModelPart> parts, Set<ModelPart> drawn, Set<ModelPart> ancestors) {
		public boolean isEmpty() {
			return parts.isEmpty();
		}
	}

	private static final Map<ModelPart, Map<BodyPlan, Region[]>> CACHE = new WeakHashMap<>();

	public static Region region(ModelPart root, BodyPlan plan, BodyPart part) {
		Map<BodyPlan, Region[]> byPlan = CACHE.computeIfAbsent(root, r -> new java.util.EnumMap<>(BodyPlan.class));
		Region[] regions = byPlan.computeIfAbsent(plan, p -> resolve(root, p));
		return regions[part.ordinal()];
	}

	private static Region[] resolve(ModelPart root, BodyPlan plan) {
		Map<ModelPart, ModelPart> parents = new IdentityHashMap<>();
		List<Map.Entry<String, ModelPart>> named = new ArrayList<>();
		walk(root, parents, named);
		Region[] regions = new Region[BodyPart.values().length];
		for (BodyPart part : BodyPart.values()) {
			regions[part.ordinal()] = build(find(named, names(plan, part)), parents, part != BodyPart.TORSO);
		}
		Region torso = regions[BodyPart.TORSO.ordinal()];
		if (torso.isEmpty()) {
			// A guardian is all head; whatever it has is its trunk too.
			torso = regions[BodyPart.HEAD.ordinal()];
			regions[BodyPart.TORSO.ordinal()] = torso;
		}
		for (BodyPart part : BodyPart.values()) {
			if (regions[part.ordinal()].isEmpty() && plan.has(part)) {
				regions[part.ordinal()] = torso;
			}
		}
		return regions;
	}

	private static void walk(ModelPart part, Map<ModelPart, ModelPart> parents, List<Map.Entry<String, ModelPart>> named) {
		for (Map.Entry<String, ModelPart> child : ((ModelPartAccessor) (Object) part).realisticdamage$children().entrySet()) {
			parents.put(child.getValue(), part);
			named.add(child);
			walk(child.getValue(), parents, named);
		}
	}

	/** Every part whose name matches; a trailing {@code *} matches by prefix. */
	private static List<ModelPart> find(List<Map.Entry<String, ModelPart>> named, String[] names) {
		List<ModelPart> found = new ArrayList<>();
		for (String name : names) {
			boolean prefix = name.endsWith("*");
			String stem = prefix ? name.substring(0, name.length() - 1) : name;
			for (Map.Entry<String, ModelPart> entry : named) {
				boolean match = prefix ? entry.getKey().startsWith(stem) : entry.getKey().equals(stem);
				if (match && !found.contains(entry.getValue())) {
					found.add(entry.getValue());
				}
			}
		}
		// A part already under another found part is drawn with it; listing it twice draws it twice.
		return found;
	}

	private static Region build(List<ModelPart> parts, Map<ModelPart, ModelPart> parents, boolean withChildren) {
		Set<ModelPart> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<ModelPart> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ModelPart part : parts) {
			drawn.add(part);
			if (withChildren) {
				addDescendants(part, drawn);
			}
			for (ModelPart up = parents.get(part); up != null; up = parents.get(up)) {
				ancestors.add(up);
			}
		}
		ancestors.removeAll(drawn);
		return new Region(List.copyOf(parts), drawn, ancestors);
	}

	private static void addDescendants(ModelPart part, Set<ModelPart> into) {
		for (ModelPart child : ((ModelPartAccessor) (Object) part).realisticdamage$children().values()) {
			into.add(child);
			addDescendants(child, into);
		}
	}

	/**
	 * The part names a region goes by, per plan. Lists are generous on purpose:
	 * a name a model lacks costs nothing, and one more name is one more mob
	 * whose leg disappears when it should.
	 */
	static String[] names(BodyPlan plan, BodyPart part) {
		boolean left = part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
		String side = left ? "left" : "right";
		return switch (part) {
			case HEAD -> new String[] {"head", "center_head", "left_head", "right_head"};
			case TORSO -> new String[] {"body", "upper_body", "lower_body", "body0", "body1", "body_front", "body_back",
					"cube", "ribcage", "shoulders", "base", "lid", "segment*", "part*", "shell", "inner_body", "rods"};
			case LEFT_ARM, RIGHT_ARM -> switch (plan.role(part)) {
				case HANDS -> new String[] {side + "_arm"};
				case WINGS -> new String[] {side + "_wing", side + "_wing_base"};
				case WALK -> plan == BodyPlan.ARTHROPOD
						? new String[] {side + "_front_leg", side + "_middle_front_leg"}
						: new String[] {side + "_front_leg", side + "_arm"};
				case NONE -> new String[0];
			};
			case LEFT_LEG, RIGHT_LEG -> {
				if (!plan.has(part)) {
					yield new String[0];
				}
				if (plan == BodyPlan.ARTHROPOD) {
					yield new String[] {side + "_middle_hind_leg", side + "_hind_leg"};
				}
				if (plan == BodyPlan.INSECT) {
					yield new String[] {"front_legs", "middle_legs", "back_legs"};
				}
				if (plan.walkingLimbs() == 4 || plan == BodyPlan.DRAGON) {
					yield new String[] {side + "_hind_leg", side + "_mid_leg", side + "_haunch", side + "_leg"};
				}
				yield new String[] {side + "_leg"};
			}
		};
	}
}
