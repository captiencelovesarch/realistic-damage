package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A drop of blood with somewhere to be (1.5).
 *
 * <p>The thing the old bleeding effect was missing. Before this, bleeding was
 * redstone dust spawned with zero velocity at the centre of the wounded body
 * region: it hung in the air, it never fell, it never arrived anywhere, and in
 * first person it spawned behind the camera where it could not be seen at all.
 *
 * <p>This one has gravity, drag and block collision, and when it stops it
 * leaves something behind — a mark on whatever it hit, or blood on whoever it
 * hit. Its severity travels in its velocity rather than in a custom payload, so
 * a seep and an arterial throw look different on every client without anything
 * extra crossing the network.
 */
public class BloodDrop extends SingleQuadParticle {
	/** Movement below this in a tick counts as "did not get there" — i.e. blocked. */
	private static final double COLLISION_EPSILON = 1.0e-5;

	/**
	 * The entity this drop came out of, ignored for staining. A drop leaving your
	 * shoulder starts inside your own hitbox, so without this every drop would
	 * splash against its owner on the tick it was born and never fall at all.
	 * Blood on your own body is the business of {@link BloodBody}, which knows
	 * which region is bleeding — it does not need to be told by a particle.
	 */
	private final int sourceEntityId;
	private final double scale;

	BloodDrop(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
			int sourceEntityId, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
		super(level, x, y, z, xd, yd, zd, sprite);
		this.sourceEntityId = sourceEntityId;
		// Velocity is the severity channel: read it once, here, before drag and
		// gravity start changing it.
		this.scale = BloodFlow.dropScale(Math.sqrt(xd * xd + yd * yd + zd * zd));
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		setSize(0.02F, 0.02F);
		this.gravity = (float) Balance.DROP_GRAVITY;
		this.lifetime = Balance.DROP_MAX_AGE_TICKS;
		this.quadSize = (float) (0.028 + 0.062 * scale);
		float[] colour = BloodPalette.colour(1.0);
		setColor(colour[0], colour[1], colour[2]);
		setAlpha((float) Math.clamp(ClientEffectsConfig.get().bloodIntensity(), 0.0, 1.0));
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
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

		yd -= gravity;
		double wantX = xd;
		double wantY = yd;
		double wantZ = zd;
		double fromX = x;
		double fromY = y;
		double fromZ = z;
		move(xd, yd, zd);

		// move() reports collisions only by side effect — it zeroes xd and zd and
		// sets a private stoppedByCollision — so which face we hit has to be
		// recovered by comparing what we asked for against what we got.
		Direction face = contactFace(wantX, wantY, wantZ, x - fromX, y - fromY, z - fromZ);
		if (face != null) {
			settle(face);
			return;
		}
		if (splashOntoEntity()) {
			return;
		}

		xd *= Balance.DROP_AIR_DRAG;
		yd *= Balance.DROP_AIR_DRAG;
		zd *= Balance.DROP_AIR_DRAG;
	}

	/**
	 * Which surface stopped us, as the direction its face points, or null if
	 * nothing did. Vertical is tested first: a drop that lands in a corner has
	 * hit the floor, whatever else it also brushed on the way down.
	 */
	private static @Nullable Direction contactFace(double wantX, double wantY, double wantZ,
			double gotX, double gotY, double gotZ) {
		if (Math.abs(wantY - gotY) > COLLISION_EPSILON) {
			return wantY < 0.0 ? Direction.UP : Direction.DOWN;
		}
		if (Math.abs(wantX - gotX) > COLLISION_EPSILON) {
			return wantX < 0.0 ? Direction.EAST : Direction.WEST;
		}
		if (Math.abs(wantZ - gotZ) > COLLISION_EPSILON) {
			return wantZ < 0.0 ? Direction.SOUTH : Direction.NORTH;
		}
		return null;
	}

	/** Hands the landing point to the stain field and stops being a drop. */
	private void settle(Direction face) {
		AABB box = getBoundingBox();
		double sx = (box.minX + box.maxX) / 2.0;
		double sy = (box.minY + box.maxY) / 2.0;
		double sz = (box.minZ + box.maxZ) / 2.0;
		switch (face) {
			case UP -> sy = box.minY;
			case DOWN -> sy = box.maxY;
			case EAST -> sx = box.minX;
			case WEST -> sx = box.maxX;
			case SOUTH -> sz = box.minZ;
			case NORTH -> sz = box.maxZ;
		}
		BloodStains.land(sx, sy, sz, face, scale);
		remove();
	}

	/**
	 * Blood landing on somebody. Particles do not collide with entities in
	 * vanilla — {@code Particle.move} passes an empty entity list — so this is
	 * our own check, over the client level's player list, which is short.
	 */
	private boolean splashOntoEntity() {
		AABB box = getBoundingBox();
		for (Player player : level.players()) {
			if (player.getId() == sourceEntityId || player.isSpectator()) {
				continue;
			}
			if (player.getBoundingBox().intersects(box)) {
				BloodBody.splash(player, y, scale);
				remove();
				return true;
			}
		}
		return false;
	}

	/** Server-thrown drops (the spray when a wound opens) arrive through here. */
	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
			// The stain field draws from the same definition; this is the only
			// place a SpriteSet for it is handed out.
			BloodStains.bindSprites(sprites);
			BloodEmitter.bindSprites(sprites);
		}

		@Override
		public @Nullable Particle createParticle(SimpleParticleType options, ClientLevel level,
				double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
			if (ClientEffectsConfig.get().bloodIntensity() <= 0.0) {
				return null;
			}
			return new BloodDrop(level, x, y, z, xd, yd, zd, -1,
					sprites.get(random.nextInt(BloodEmitter.DROP_SPRITE_COUNT),
							BloodEmitter.SPRITE_COUNT - 1));
		}
	}
}
