package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
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
	/** The colour this blood left the body as (1.7). */
	private final BloodTint tint;

	/**
	 * Set the tick the drop finds itself in water, after which it is a cloud
	 * rather than a drop: it slows almost to a stop, spreads, thins and is gone,
	 * and it never marks anything.
	 *
	 * <p>The bug this closes: a drop had gravity, drag and block collision and
	 * nothing else, so water was air to it. It fell straight through, stopped on
	 * the pool floor and laid a stain there — blood that sank like gravel and sat
	 * on the bottom of a river. Blood is barely denser than water and disperses
	 * into it within a second or two; the mark it leaves is a fading tint in the
	 * water itself, not a puddle under it.
	 */
	private boolean dissolving;
	private int dissolveTicks;
	private int dissolveAge;
	private float dissolveStartSize;
	private float dissolveStartAlpha;
	/** Last tick's size, so the growth interpolates instead of stepping at 20 Hz. */
	private float oQuadSize;

	/** Drops that met water and dispersed this session. Read by the client gametest. */
	private static long dissolved;
	private static @Nullable SpriteSet cloudSprites;

	/** How many drops have dispersed into water since the client started. */
	public static long dissolvedCount() {
		return dissolved;
	}

	BloodDrop(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
			int sourceEntityId, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, BloodTint tint) {
		super(level, x, y, z, xd, yd, zd, sprite);
		this.sourceEntityId = sourceEntityId;
		this.tint = tint;
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
		this.oQuadSize = this.quadSize;
		float[] colour = BloodPalette.particle(1.0, tint);
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
		oQuadSize = quadSize;

		// Fluids first, before gravity has its say. Lava burns the drop off
		// outright — no mark, nothing to see — and water turns it into a cloud.
		BlockPos at = BlockPos.containing(x, y, z);
		FluidState fluid = level.getFluidState(at);
		if (fluid.is(FluidTags.LAVA)) {
			remove();
			return;
		}
		if (!dissolving && fluid.is(FluidTags.WATER) && y < at.getY() + fluid.getHeight(level, at)) {
			beginDissolve();
		}
		if (dissolving) {
			tickDissolve();
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
	 * The drop meets water. Whatever it was doing, it now hangs, spreads and
	 * fades; how long that takes scales with how much blood the drop was.
	 */
	private void beginDissolve() {
		dissolving = true;
		dissolved++;
		// Water takes almost all of the speed at once — the drop was moving
		// through air a moment ago and a cloud has no momentum to speak of.
		xd *= Balance.DROP_WATER_ENTRY_KEEP;
		zd *= Balance.DROP_WATER_ENTRY_KEEP;
		yd = Math.min(0.0, yd) * Balance.DROP_WATER_ENTRY_KEEP;
		dissolveTicks = (int) Math.round(Mth.lerp(scale,
				Balance.DROP_DISSOLVE_TICKS_MIN, Balance.DROP_DISSOLVE_TICKS_MAX));
		dissolveAge = 0;
		lifetime = age + dissolveTicks + 1;
		dissolveStartSize = quadSize;
		dissolveStartAlpha = alpha;
		// A cloud is round and soft where a drop is a bead: swap to the pool
		// shape the stain field uses once a mark has spread. Deterministic
		// variant, keyed off where the drop went in — there is no random source
		// in here and there must not be.
		if (cloudSprites != null) {
			int variant = Math.floorMod((int) Math.floor(x * 7.0 + z * 13.0), 4);
			setSprite(cloudSprites.get(BloodEmitter.POOL_SPRITE_OFFSET + variant, BloodEmitter.SPRITE_COUNT - 1));
		}
		// Blood in water reads darker than blood on a surface: the water takes
		// the highlights off it.
		float[] colour = BloodPalette.particle(1.0, tint);
		setColor(colour[0] * 0.80F, colour[1] * 0.80F, colour[2] * 0.80F);
	}

	/**
	 * One tick of dispersal: the cloud grows fast at first and then barely at
	 * all, thins the whole way, sinks a hair, and is removed when it is gone.
	 * Nothing here settles, splashes or stains — water is where blood stops
	 * being a thing that lands.
	 */
	private void tickDissolve() {
		dissolveAge++;
		double t = Math.min(1.0, dissolveAge / (double) dissolveTicks);
		if (t >= 1.0) {
			remove();
			return;
		}
		double spread = 1.0 + (Balance.DROP_DISSOLVE_GROWTH - 1.0) * Math.sqrt(t);
		quadSize = (float) Math.min(Balance.DROP_DISSOLVE_SIZE_MAX, dissolveStartSize * spread);
		double thin = Math.pow(1.0 - t, 1.5);
		setAlpha((float) (dissolveStartAlpha * thin));
		// Blood is a shade denser than water: it sinks, slowly, while it spreads.
		yd -= Balance.DROP_WATER_SINK;
		xd *= Balance.DROP_WATER_DRAG;
		yd *= Balance.DROP_WATER_DRAG;
		zd *= Balance.DROP_WATER_DRAG;
		move(xd, yd, zd);
	}

	@Override
	public float getQuadSize(float partialTick) {
		return Mth.lerp(partialTick, oQuadSize, quadSize);
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
		BloodStains.land(sx, sy, sz, face, scale, sourceEntityId, tint);
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
			cloudSprites = sprites;
		}

		@Override
		public @Nullable Particle createParticle(SimpleParticleType options, ClientLevel level,
				double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
			if (ClientEffectsConfig.get().bloodIntensity() <= 0.0) {
				return null;
			}
			int occupant = occupantAt(level, x, y, z);
			return new BloodDrop(level, x, y, z, xd, yd, zd, occupant,
					sprites.get(random.nextInt(BloodEmitter.DROP_SPRITE_COUNT),
							BloodEmitter.SPRITE_COUNT - 1),
					BloodTint.of(occupant < 0 ? null : level.getEntity(occupant)));
		}

		/**
		 * Whose body this drop was thrown out of, or -1 if nobody's.
		 *
		 * <p>The spray is a {@link SimpleParticleType}, so the packet carries a
		 * position and a velocity and nothing else — but the position is
		 * {@code BodyHitResolver.regionCenter}, and every one of the six regions is
		 * built from the victim's live bounding box and sits strictly inside it. The
		 * victim is therefore exactly the player whose box contains the spawn point,
		 * and the client can work that out without anything extra crossing the wire.
		 *
		 * <p>This used to pass -1, which matches no entity. Every server-thrown drop
		 * consequently splashed against the person it came out of on its first or
		 * second tick — the precise case {@link BloodDrop#sourceEntityId} exists to
		 * prevent, and named in its javadoc — because almost nothing the mod throws
		 * clears a 0.6-wide hitbox before that check runs. Escaping needs more than
		 * 0.30 plus half the drop's own 0.02 box on a single axis in a single tick:
		 * the seep (0.030 b/tick) and the arterial spurt (0.275) cannot do it at
		 * all, and the impact spray only can when its 0.35 happens to fall almost
		 * exactly along an axis at nearly full jitter. One drop in twenty did.
		 * Measured in {@code BloodClientGameTest} by putting the -1 back: a critical
		 * blow's twenty drops left <em>one</em> mark on the world, against fourteen
		 * with the id right. The other nineteen deposited 19 &times; 0.16 &times;
		 * ~0.8 = 2.4 of body stain into a channel that clamps at 1.0, so the burst
		 * never read as missing blood — it read as "the blood went on me", which is
		 * very likely how it survived from 1.5 to a playtest report about walls.
		 *
		 * <p>Nearest first, so two clients watching one casualty agree on the answer
		 * even in the situation that cannot currently arise (two boxes over one
		 * region centre). There is no random source in here and there must not be.
		 *
		 * <p>-1 is still the honest answer when the spawn point is inside nobody: a
		 * future bleeder that is not a player, or a client whose copy of the victim
		 * has drifted more than 0.3 blocks sideways from the server's. That drop
		 * then behaves exactly as every drop did before this fix, which is the right
		 * way for a guess to fail.
		 */
		private static int occupantAt(ClientLevel level, double x, double y, double z) {
			int found = -1;
			double best = Double.MAX_VALUE;
			// Players and, since 1.7, any living thing with a body: the spray of a
			// zombie's wound is the zombie's, in the zombie's colour.
			for (net.minecraft.world.entity.LivingEntity living : level.getEntitiesOfClass(
					net.minecraft.world.entity.LivingEntity.class, new AABB(x, y, z, x, y, z).inflate(2.0))) {
				if (living.isSpectator() || living.getAttached(dev.captience.realisticdamage.RDAttachments.SNAPSHOT) == null
						|| !living.getBoundingBox().contains(x, y, z)) {
					continue;
				}
				double distance = living.position().distanceToSqr(x, y, z);
				if (distance < best) {
					best = distance;
					found = living.getId();
				}
			}
			return found;
		}
	}
}
