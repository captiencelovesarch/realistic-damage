package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.joml.Quaternionf;

/**
 * A mark left on a surface where a drop landed (1.5). Floors, walls and
 * ceilings alike — this is the trail you leave behind you and the pool that
 * grows under you when you stop moving.
 *
 * <p>Implemented as a particle rather than as a bespoke render pass, which buys
 * batching, frustum culling, block lighting and lifetime management from code
 * that already exists and is already correct. What it does not buy is
 * orientation: every other particle in the game turns to face the camera, and a
 * stain has to stay flat against the thing it hit. {@link #extract} therefore
 * ignores the camera entirely and supplies a fixed rotation instead — see
 * {@link #orientation}.
 *
 * <p>It is also the only particle here that grows. Standing still over an open
 * artery would otherwise spend the whole stain budget on one square of floor
 * and erase the trail that led to it, so a drop landing near an existing mark
 * feeds that mark instead of adding another (see {@link BloodStains}).
 */
public class BloodStain extends SingleQuadParticle {
	/**
	 * Rotation from the quad's own +Z normal onto the surface normal, plus a roll
	 * about that normal so repeated splats on one floor do not all line up.
	 */
	private final Quaternionf orientation;
	private final Direction face;
	private double radius;

	BloodStain(ClientLevel level, double x, double y, double z, Direction face, double radius, long seed,
			TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.face = face;
		this.radius = radius;
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
		this.lifetime = (int) (Balance.STAIN_LIFETIME_SECONDS * 20.0);

		Vec3i normal = face.getUnitVec3i();
		this.orientation = new Quaternionf().rotationTo(
				0.0F, 0.0F, 1.0F, normal.getX(), normal.getY(), normal.getZ());
		this.orientation.rotateZ((float) (BloodFlow.jitter(seed, 11) * Math.PI * 2.0));
		applyLook();
	}

	Direction face() {
		return face;
	}

	double radius() {
		return radius;
	}

	// Particle keeps its position in protected fields, reachable from a subclass
	// but not from the manager alongside it. Reading the bounding box instead
	// would give the wrong answer: Particle sizes its box independently of the
	// quad, and setLocationFromBoundingbox puts y at the box's *bottom*.
	double px() {
		return x;
	}

	double py() {
		return y;
	}

	double pz() {
		return z;
	}

	/** Feeds another drop into this mark, growing it toward the maximum. */
	void grow(double incomingRadius) {
		radius = Math.min(Balance.STAIN_SIZE_MAX, radius + incomingRadius * Balance.STAIN_MERGE_GROWTH);
		// A mark that is still being fed is a mark that is still wet. Winding the
		// clock back is what makes a pool under a standing player read as active
		// bleeding rather than as something that happened and stopped.
		age = Math.max(0, age - (int) (Balance.STAIN_LIFETIME_SECONDS * 20.0 * Balance.STAIN_WET_FRACTION));
		applyLook();
	}

	private void applyLook() {
		double life = (double) age / lifetime;
		float[] colour = BloodPalette.colour(BloodFlow.stainWetness(life));
		setColor(colour[0], colour[1], colour[2]);
		setAlpha((float) (BloodFlow.stainAlpha(life)
				* ClientEffectsConfig.get().bloodIntensity()
				* ClientEffectsConfig.get().bloodStainIntensity));
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime) {
			remove();
			return;
		}
		applyLook();
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return (float) radius;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	/**
	 * Draw flat against the surface rather than facing the camera. The base
	 * implementation asks {@code getFacingCameraMode()} for a rotation; ours is
	 * fixed at construction, so the camera is not consulted at all.
	 */
	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTicks) {
		extractRotatedQuad(state, camera, orientation, partialTicks);
	}

	@Override
	public Particle scale(float scale) {
		radius *= scale;
		return this;
	}
}
