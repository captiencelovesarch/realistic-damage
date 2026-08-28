package dev.captience.realisticdamage.damage;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.jspecify.annotations.Nullable;

/**
 * Classification by damage-type tag (1.3) — the data-driven half of
 * interoperability.
 *
 * <p>A server or another mod can make any damage type behave as a category by
 * adding it to one of these tags in a datapack, with no code and no compile-time
 * dependency on either side. That matters more than a bespoke JSON format would:
 * tags already merge across datapacks, already reload with {@code /reload}, and
 * a pack author already knows how to write one.
 *
 * <p>Consulted <em>before</em> the built-in table, so a pack can also override
 * how vanilla damage is read — a server that wants tridents to count as heavy
 * puncture just says so.
 */
public final class DamageTypeClassification {
	private DamageTypeClassification() {}

	private static TagKey<DamageType> tag(String path) {
		return TagKey.create(Registries.DAMAGE_TYPE, RealisticDamage.id(path));
	}

	/**
	 * Ordered most specific first: a damage type in both {@code ballistic} and
	 * {@code heavy_ballistic} reads as the heavier of the two rather than
	 * depending on map order.
	 */
	private static final Map<TagKey<DamageType>, DamageCategory> TAGS = new LinkedHashMap<>();

	static {
		TAGS.put(tag("heavy_ballistic"), DamageCategory.HEAVY_BALLISTIC);
		TAGS.put(tag("ballistic"), DamageCategory.BALLISTIC);
		TAGS.put(tag("heavy_slash"), DamageCategory.HEAVY_SLASH);
		TAGS.put(tag("heavy_puncture"), DamageCategory.HEAVY_PUNCTURE);
		TAGS.put(tag("heavy_blunt"), DamageCategory.HEAVY_BLUNT);
		TAGS.put(tag("slash"), DamageCategory.SLASH);
		TAGS.put(tag("puncture"), DamageCategory.PUNCTURE);
		TAGS.put(tag("blunt"), DamageCategory.BLUNT);
	}

	/**
	 * The category a datapack has assigned to this source, or null if none has.
	 * Routing is always locational: something a pack bothered to classify as a
	 * weapon category was aimed at a place on the body.
	 */
	@Nullable
	public static ClassifiedDamage classify(DamageSource source) {
		for (Map.Entry<TagKey<DamageType>, DamageCategory> entry : TAGS.entrySet()) {
			if (source.is(entry.getKey())) {
				return ClassifiedDamage.of(entry.getValue(), ClassifiedDamage.Routing.LOCATIONAL);
			}
		}
		return null;
	}
}
