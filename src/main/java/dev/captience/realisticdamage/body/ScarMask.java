package dev.captience.realisticdamage.body;

/**
 * Two bits per region at {@code part.ordinal() * BITS_PER_PART}, holding a
 * {@link ScarLevel} ordinal. Twelve bits used; 0 means no scars anywhere.
 *
 * <p>Its own class rather than three copies of the same shift — in
 * {@code BodySnapshot.of}, in the accessor and in the render layer — because a
 * hand-edited shift is exactly the class of bug the Minecraft-free-math rule
 * exists to make unit-testable.
 */
public final class ScarMask {
	private ScarMask() {}

	public static final int BITS_PER_PART = 2;

	/** Every region's history in one int, in {@code BodyPart} ordinal order. */
	public static int pack(RealisticDamageData data) {
		int mask = 0;
		for (BodyPart part : BodyPart.values()) {
			mask |= data.part(part).scar().ordinal() << (part.ordinal() * BITS_PER_PART);
		}
		return mask;
	}

	/** One region's history back out. Unsigned shift, so a sign bit cannot walk in. */
	public static ScarLevel level(int mask, BodyPart part) {
		return ScarLevel.byOrdinal((mask >>> (part.ordinal() * BITS_PER_PART)) & 0b11);
	}

	/** The mask with one region replaced — for tests and for the debug command. */
	public static int with(int mask, BodyPart part, ScarLevel level) {
		int shift = part.ordinal() * BITS_PER_PART;
		return (mask & ~(0b11 << shift)) | (level.ordinal() << shift);
	}
}
