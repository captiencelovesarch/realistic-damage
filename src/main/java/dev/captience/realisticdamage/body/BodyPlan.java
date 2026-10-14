package dev.captience.realisticdamage.body;

import dev.captience.realisticdamage.config.Balance;

/**
 * How the six regions sit on a body that is not a person's (1.7 slice 3).
 *
 * <p>{@link BodyPart} stays six slots; a plan says which of them a body has,
 * what each one <em>is</em> on it — a cow's arm slots are its forelegs, a bat's
 * are its wings — and where they sit. Hit location, blood placement and blast
 * routing all read the same geometry, so where a cow is hit is where it bleeds.
 *
 * <p>Geometry is body-local: {@code forward} and {@code lateral} are in
 * half-widths of the bounding box (positive lateral is the body's right),
 * {@code height} a fraction of the box from its feet. Vanilla boxes are square,
 * so a long body's head and rump are the front and back faces of the box.
 * {@link #HUMANOID} is the exception: it keeps the band layout every humanoid
 * has used since 1.0, and {@code BodyHitResolver} runs it unchanged.
 *
 * <p>Which plan a mob uses is data, by entity type tag; see {@code MobAnatomy}.
 */
public enum BodyPlan {
	/** Head, torso, two arms with hands, two legs. Players and everything shaped like them. */
	HUMANOID("humanoid", R.ALL, Limbs.HANDS, Limbs.WALK),
	/** Vex and allay: a humanoid top with no legs to speak of. */
	SPRITE("sprite", R.HEAD | R.TORSO | R.ARMS, Limbs.HANDS, Limbs.NONE),
	/** The snow golem: head, body, two stick arms, and it slides. */
	SNOWMAN("snowman", R.HEAD | R.TORSO | R.ARMS, Limbs.HANDS, Limbs.NONE),
	/** Four legs under a horizontal body; the arm slots are the forelegs. */
	QUADRUPED("quadruped", R.ALL, Limbs.WALK, Limbs.WALK),
	/** Four short legs under an upright body: the creeper. */
	UPRIGHT("upright", R.ALL, Limbs.WALK, Limbs.WALK),
	/** Eight legs in four pairs: the arm slots are the front two pairs on each side. */
	ARTHROPOD("arthropod", R.ALL, Limbs.WALK, Limbs.WALK),
	/** Wings and legs: chicken, parrot. */
	AVIAN("avian", R.ALL, Limbs.WINGS, Limbs.WALK),
	/** Wings on the back, legs underneath that it hardly uses: the bee. */
	INSECT("insect", R.ALL, Limbs.WINGS, Limbs.NONE),
	/** Wings and nothing to stand on: bat, phantom. */
	FLYER("flyer", R.HEAD | R.TORSO | R.ARMS, Limbs.WINGS, Limbs.NONE),
	/** Head on a neck, a body, two wings, hind legs: the ender dragon. */
	DRAGON("dragon", R.ALL, Limbs.WINGS, Limbs.NONE),
	/** A body that is mostly head, on two legs: the strider. */
	STRIDER("strider", R.TORSO | R.LEGS, Limbs.NONE, Limbs.WALK),
	/** Head and body along the ground or the current: fish, guardians, silverfish. */
	LONG("long", R.HEAD | R.TORSO, Limbs.NONE, Limbs.NONE),
	/** Head above a body, nothing to lose: ghasts, blazes, squid, the wither. */
	FLOATER("floater", R.HEAD | R.TORSO, Limbs.NONE, Limbs.NONE),
	/** One region, no head to hit: the cube mobs. */
	BLOB("blob", R.TORSO, Limbs.NONE, Limbs.NONE);

	/** What a pair of limb slots does on this body. */
	public enum Limbs {
		/** Not there, or there and doing nothing the game reads. */
		NONE,
		/** Hands: swing, draw, grip, throw. */
		HANDS,
		/** Weight-bearing: movement reads them. */
		WALK,
		/** Flight reads them; a destroyed one grounds the body. */
		WINGS
	}

	/** Region bits by {@code BodyPart.ordinal()}; nested so the constants above may name them. */
	private static final class R {
		static final int HEAD = 1 << 0;
		static final int TORSO = 1 << 1;
		static final int ARMS = (1 << 2) | (1 << 3);
		static final int LEGS = (1 << 4) | (1 << 5);
		static final int ALL = HEAD | TORSO | ARMS | LEGS;
	}

	private final String id;
	private final int regions;
	private final Limbs arms;
	private final Limbs legs;

	BodyPlan(String id, int regions, Limbs arms, Limbs legs) {
		this.id = id;
		this.regions = regions;
		this.arms = arms;
		this.legs = legs;
	}

	/** The tag path suffix: {@code realisticdamage:plan/<id>}. */
	public String id() {
		return id;
	}

	public boolean has(BodyPart part) {
		return (regions & (1 << part.ordinal())) != 0;
	}

	/** What this region does on this body; {@code NONE} for the core and for absent limbs. */
	public Limbs role(BodyPart part) {
		if (!has(part)) {
			return Limbs.NONE;
		}
		if (part.isArm()) {
			return arms;
		}
		return part.isLeg() ? legs : Limbs.NONE;
	}

	public boolean hands(BodyPart part) {
		return role(part) == Limbs.HANDS;
	}

	public boolean walks(BodyPart part) {
		return role(part) == Limbs.WALK;
	}

	public boolean wing(BodyPart part) {
		return role(part) == Limbs.WINGS;
	}

	public boolean hasHands() {
		return arms == Limbs.HANDS;
	}

	public boolean hasWings() {
		return arms == Limbs.WINGS;
	}

	/** How many regions carry the body's weight: 0, 2 or 4. */
	public int walkingLimbs() {
		return (arms == Limbs.WALK ? 2 : 0) + (legs == Limbs.WALK ? 2 : 0);
	}

	/** Where trauma meant for a region this body lacks goes instead: the torso. */
	public BodyPart redirect(BodyPart part) {
		return has(part) ? part : BodyPart.TORSO;
	}

	/**
	 * The region a body-local point falls in. Never returns a region the plan
	 * lacks. Not used for {@link #HUMANOID}, whose bands live in the resolver.
	 *
	 * @param forward along the body's facing, in half-widths (front face = 1)
	 * @param lateral toward the body's right, in half-widths
	 * @param height  fraction of the box height from the feet
	 */
	public BodyPart classify(double forward, double lateral, double height) {
		BodyPart side = lateral >= 0.0 ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		BodyPart sideLeg = lateral >= 0.0 ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
		double across = Math.abs(lateral);
		BodyPart part = switch (this) {
			case HUMANOID, SPRITE -> {
				if (height >= Balance.HEAD_BAND_FRACTION) {
					yield BodyPart.HEAD;
				}
				if (height < Balance.LEG_BAND_FRACTION) {
					yield sideLeg;
				}
				yield across > Balance.ARM_LATERAL_FRACTION ? side : BodyPart.TORSO;
			}
			case SNOWMAN -> {
				if (height >= 0.72) {
					yield BodyPart.HEAD;
				}
				yield across > 0.5 && height >= 0.42 ? side : BodyPart.TORSO;
			}
			case QUADRUPED -> {
				if (height < 0.45) {
					yield forward >= 0.0 ? side : sideLeg;
				}
				yield forward > 0.6 ? BodyPart.HEAD : BodyPart.TORSO;
			}
			case UPRIGHT -> {
				if (height < 0.24) {
					yield forward >= 0.0 ? side : sideLeg;
				}
				yield height >= 0.69 ? BodyPart.HEAD : BodyPart.TORSO;
			}
			case ARTHROPOD -> {
				if (across > 0.6 || height < 0.25) {
					yield forward >= -0.1 ? side : sideLeg;
				}
				yield forward > 0.5 ? BodyPart.HEAD : BodyPart.TORSO;
			}
			case AVIAN -> {
				if (height < 0.35) {
					yield sideLeg;
				}
				if (height >= 0.65) {
					yield BodyPart.HEAD;
				}
				yield across > 0.55 ? side : BodyPart.TORSO;
			}
			case INSECT -> {
				if (height >= 0.75 && across > 0.15) {
					yield side;
				}
				if (height < 0.25) {
					yield sideLeg;
				}
				yield forward > 0.55 ? BodyPart.HEAD : BodyPart.TORSO;
			}
			case FLYER -> {
				if (across > 0.45) {
					yield side;
				}
				yield height > 0.7 || forward > 0.6 ? BodyPart.HEAD : BodyPart.TORSO;
			}
			case DRAGON -> {
				if (across > 0.5 && height > 0.4) {
					yield side;
				}
				if (height < 0.3) {
					yield sideLeg;
				}
				yield forward > 0.6 ? BodyPart.HEAD : BodyPart.TORSO;
			}
			case STRIDER -> height < 0.5 ? sideLeg : BodyPart.TORSO;
			case LONG -> forward > 0.4 ? BodyPart.HEAD : BodyPart.TORSO;
			case FLOATER -> height >= 0.6 ? BodyPart.HEAD : BodyPart.TORSO;
			case BLOB -> BodyPart.TORSO;
		};
		return redirect(part);
	}

	/**
	 * The centre of a region in the same body-local units as {@link #classify}:
	 * {@code {forward, lateral, height}}. Absent regions answer with the torso's.
	 */
	public double[] anchor(BodyPart part) {
		BodyPart at = redirect(part);
		double side = at == BodyPart.RIGHT_ARM || at == BodyPart.RIGHT_LEG ? 1.0 : -1.0;
		return switch (this) {
			case HUMANOID, SPRITE -> switch (at) {
				case HEAD -> v(0.0, 0.0, (Balance.HEAD_BAND_FRACTION + 1.0) / 2.0);
				case TORSO -> v(0.0, 0.0, (Balance.LEG_BAND_FRACTION + Balance.HEAD_BAND_FRACTION) / 2.0);
				case LEFT_ARM, RIGHT_ARM -> v(0.0, 0.8 * side, (Balance.LEG_BAND_FRACTION + Balance.HEAD_BAND_FRACTION) / 2.0);
				case LEFT_LEG, RIGHT_LEG -> v(0.0, 0.5 * side, Balance.LEG_BAND_FRACTION / 2.0);
			};
			case SNOWMAN -> switch (at) {
				case HEAD -> v(0.0, 0.0, 0.86);
				case LEFT_ARM, RIGHT_ARM -> v(0.0, 0.85 * side, 0.6);
				default -> v(0.0, 0.0, 0.4);
			};
			case QUADRUPED -> switch (at) {
				case HEAD -> v(0.85, 0.0, 0.75);
				case LEFT_ARM, RIGHT_ARM -> v(0.55, 0.5 * side, 0.22);
				case LEFT_LEG, RIGHT_LEG -> v(-0.55, 0.5 * side, 0.22);
				default -> v(0.0, 0.0, 0.7);
			};
			case UPRIGHT -> switch (at) {
				case HEAD -> v(0.0, 0.0, 0.85);
				case LEFT_ARM, RIGHT_ARM -> v(0.4, 0.5 * side, 0.11);
				case LEFT_LEG, RIGHT_LEG -> v(-0.4, 0.5 * side, 0.11);
				default -> v(0.0, 0.0, 0.46);
			};
			case ARTHROPOD -> switch (at) {
				case HEAD -> v(0.75, 0.0, 0.5);
				case LEFT_ARM, RIGHT_ARM -> v(0.3, 0.85 * side, 0.3);
				case LEFT_LEG, RIGHT_LEG -> v(-0.4, 0.85 * side, 0.3);
				default -> v(-0.2, 0.0, 0.55);
			};
			case AVIAN -> switch (at) {
				case HEAD -> v(0.3, 0.0, 0.8);
				case LEFT_ARM, RIGHT_ARM -> v(0.0, 0.75 * side, 0.55);
				case LEFT_LEG, RIGHT_LEG -> v(0.0, 0.35 * side, 0.15);
				default -> v(0.0, 0.0, 0.5);
			};
			case INSECT -> switch (at) {
				case HEAD -> v(0.7, 0.0, 0.5);
				case LEFT_ARM, RIGHT_ARM -> v(0.0, 0.5 * side, 0.85);
				case LEFT_LEG, RIGHT_LEG -> v(0.0, 0.3 * side, 0.12);
				default -> v(-0.1, 0.0, 0.5);
			};
			case FLYER -> switch (at) {
				case HEAD -> v(0.7, 0.0, 0.75);
				case LEFT_ARM, RIGHT_ARM -> v(0.0, 0.8 * side, 0.6);
				default -> v(0.0, 0.0, 0.5);
			};
			case DRAGON -> switch (at) {
				case HEAD -> v(0.9, 0.0, 0.6);
				case LEFT_ARM, RIGHT_ARM -> v(0.0, 0.8 * side, 0.7);
				case LEFT_LEG, RIGHT_LEG -> v(-0.2, 0.4 * side, 0.15);
				default -> v(0.0, 0.0, 0.5);
			};
			case STRIDER -> switch (at) {
				case LEFT_LEG, RIGHT_LEG -> v(0.0, 0.45 * side, 0.25);
				default -> v(0.0, 0.0, 0.75);
			};
			case LONG -> at == BodyPart.HEAD ? v(0.65, 0.0, 0.5) : v(-0.15, 0.0, 0.5);
			case FLOATER -> at == BodyPart.HEAD ? v(0.0, 0.0, 0.8) : v(0.0, 0.0, 0.35);
			case BLOB -> v(0.0, 0.0, 0.5);
		};
	}

	private static double[] v(double forward, double lateral, double height) {
		return new double[] {forward, lateral, height};
	}

	public static BodyPlan byId(String id) {
		for (BodyPlan plan : values()) {
			if (plan.id.equals(id)) {
				return plan;
			}
		}
		return null;
	}
}
