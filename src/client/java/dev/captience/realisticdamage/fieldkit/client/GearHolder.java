package dev.captience.realisticdamage.fieldkit.client;

/**
 * What a body is wearing from the field kit, on its render state (items.7):
 * which regions are bandaged (and soaked through), tourniqueted, splinted and
 * slung, as masks over {@code BodyPart.ordinal()}, and whether it wears the
 * tinted goggles or has the stethoscope in its ears. Copied on at extraction
 * ({@code GearExtractMixin}), read by {@link GearLayer} and the sling pose.
 */
public interface GearHolder {
	int realisticdamage$bandaged();

	int realisticdamage$soaked();

	int realisticdamage$tourniquets();

	int realisticdamage$splints();

	int realisticdamage$slings();

	boolean realisticdamage$goggles();

	boolean realisticdamage$listening();

	void realisticdamage$setGear(int bandaged, int soaked, int tourniquets, int splints, int slings,
			boolean goggles, boolean listening);

	static boolean has(int mask, dev.captience.realisticdamage.body.BodyPart part) {
		return (mask & (1 << part.ordinal())) != 0;
	}
}
