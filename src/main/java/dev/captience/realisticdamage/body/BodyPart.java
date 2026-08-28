package dev.captience.realisticdamage.body;

import dev.captience.realisticdamage.config.Balance;

public enum BodyPart {
	HEAD("head", Balance.HEAD_MAX),
	TORSO("torso", Balance.TORSO_MAX),
	LEFT_ARM("left_arm", Balance.ARM_MAX),
	RIGHT_ARM("right_arm", Balance.ARM_MAX),
	LEFT_LEG("left_leg", Balance.LEG_MAX),
	RIGHT_LEG("right_leg", Balance.LEG_MAX);

	private final String id;
	private final double maxCondition;

	BodyPart(String id, double maxCondition) {
		this.id = id;
		this.maxCondition = maxCondition;
	}

	public String id() {
		return id;
	}

	public double maxCondition() {
		return maxCondition;
	}

	public boolean isArm() {
		return this == LEFT_ARM || this == RIGHT_ARM;
	}

	public boolean isLeg() {
		return this == LEFT_LEG || this == RIGHT_LEG;
	}

	public boolean isLimb() {
		return isArm() || isLeg();
	}

	public boolean isCore() {
		return this == HEAD || this == TORSO;
	}

	public static BodyPart byId(String id) {
		for (BodyPart part : values()) {
			if (part.id.equals(id)) {
				return part;
			}
		}
		return null;
	}
}
