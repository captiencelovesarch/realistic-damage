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
 *
 * <p>Growing has a ceiling, and what happens at the ceiling is the difference
 * between a pool and a splat. A full mark does not swallow the next drop — the
 * field lays that one beside it, and the pool gets wider rather than staying
 * the size of the first quad forever. Past
 * {@link Balance#STAIN_POOL_SPRITE_RADIUS} the mark also stops drawing itself
 * with a splat sprite, because a magnified splat is a magnified splat.
 */
public class BloodStain extends SingleQuadParticle {
	/**
	 * Rotation from the quad's own +Z normal onto the surface normal, plus a roll
	 * about that normal so repeated splats on one floor do not all line up.
	 */
	private final Quaternionf orientation;
	/**
	 * The round, dense sprite a mark swaps to once it is plainly no longer a single
	 * splat. Held from construction rather than looked up on demand because the
	 * {@link net.minecraft.client.particle.SpriteSet} lives in {@link BloodStains}
	 * and a particle should not have to reach back out to its manager to draw
	 * itself.
	 */
	private final TextureAtlasSprite poolSprite;
	private final Direction face;
	private double radius;

	BloodStain(ClientLevel level, double x, double y, double z, Direction face, double radius, long seed,
			TextureAtlasSprite sprite, TextureAtlasSprite poolSprite) {
		super(level, x, y, z, sprite);
		this.face = face;
		this.poolSprite = poolSprite;
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

	/**
	 * Whether this mark has taken all the blood one quad can hold. A drop arriving
	 * at a saturated mark used to be thrown away; it now lays a mark beside it
	 * instead, which is how a pool gets wider than the quad it started as (see
	 * {@link BloodStains#land}).
	 */
	boolean saturated() {
		return radius >= Balance.STAIN_POOL_SIZE_MAX - 1.0e-9;
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

	/** Feeds another drop into this mark, growing it toward the pool maximum. */
	void grow(double incomingRadius) {
		radius = Math.min(Balance.STAIN_POOL_SIZE_MAX, radius + incomingRadius * Balance.STAIN_MERGE_GROWTH);
		// Past a certain size the splat sprite stops being the truth about what
		// this is. A splat is an off-centre mass with flung satellite droplets
		// around it, and scaling that up gives a magnified splatter rather than a
		// pool — which is precisely what the playtest report called it. The swap
		// is one-way: blood accumulating never turns back into a thrown drop.
		if (radius >= Balance.STAIN_POOL_SPRITE_RADIUS) {
			setSprite(poolSprite);
		}
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
