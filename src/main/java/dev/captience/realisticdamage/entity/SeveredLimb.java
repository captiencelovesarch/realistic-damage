package dev.captience.realisticdamage.entity;

import java.util.UUID;

import dev.captience.realisticdamage.RDEntities;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The limb itself (1.6.2, pulled forward from the 1.7 roadmap the way limb
 * destruction was pulled into 1.0).
 *
 * <p>1.6 left a mark where a limb came off and a bleeding stump on the body;
 * this is the missing piece: a physical object thrown from the loss, that
 * falls, bounces once, lies where it landed bleeding a diminishing trickle
 * for most of a minute, and is gone. Not an item — it cannot be picked up,
 * holds nothing, drops nothing. Not a mob — nothing targets it, nothing
 * collides with it, you walk through it. Not saved: a limb on the floor
 * across a relog would be a save-file decoration, and the blood rules forbid
 * blood that is decoration.
 *
 * <h2>What crosses the wire</h2>
 *
 * <p>Which region it is, whose skin to draw it in, how old it is (once every
 * second, so a client that arrives late fades it on time), and how far along
 * burning away it is. The skin's arm width is <em>not</em> sent: the server
 * does not know it, the client does, and the client resolves it from the
 * owner's skin — or from the default skin for that UUID when the owner has
 * gone. Everything else is vanilla's position sync.
 *
 * <h2>The author's four answers</h2>
 *
 * <ul>
 * <li>Fire and lava burn it away: it shrinks over {@link Balance#LIMB_BURN_TICKS}
 * and is discarded, with no mark.
 * <li>No collision: {@code canBeCollidedWith} is Entity's default false, and
 * it is neither pickable nor pushable.
 * <li>Mobs ignore it: it is not a {@code LivingEntity}, so no AI can see it,
 * and {@code PreyMath} reads bodies, never props.
 * <li>Blasts throw it, along the blast radial and hard; a blow tosses it along
 * the swing; a limb ground down, or taken by command, drops where it was.
 * </ul>
 *
 * <p>The throw is deterministic — its jitter comes from {@link BloodFlow}'s
 * hash, seeded from the victim, the region and the tick — so two clients, or
 * two identical events, see the same flight.
 */
public class SeveredLimb extends Entity {
	private static final EntityDataAccessor<Byte> PART = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.STRING);
	/** The owner's entity type id when it was a mob (1.7), or empty for a player. */
	private static final EntityDataAccessor<String> OWNER_TYPE = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> BURN = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.INT);
	/** A bone off a skeleton (1.7) has nothing to bleed. */
	private static final EntityDataAccessor<Boolean> BLOODLESS = SynchedEntityData.defineId(SeveredLimb.class, EntityDataSerializers.BOOLEAN);

	/** How often the server re-sends the age, in ticks. */
	private static final int AGE_SYNC_INTERVAL = 20;

	// Client-side presentation state: the tumble in flight and the settle on the ground.
	protected float tumble;
	protected float tumbleO;
	protected float lying;
	protected float lyingO;
	/** Client tick at which AGE last arrived, so age can be interpolated between syncs. */
	private int ageSyncTick;

	public SeveredLimb(EntityType<? extends SeveredLimb> type, Level level) {
		super(type, level);
	}

	/**
	 * Throws the limb the victim just lost. {@code blowDirection} is the
	 * direction the killing blow travelled, or {@code Vec3.ZERO} when there was
	 * none; {@code blast} selects the harder throw. Returns null when the rule is
	 * off or the part is not a limb.
	 */
	public static @Nullable SeveredLimb throwFrom(LivingEntity player, BodyPart part, Vec3 blowDirection, boolean blast) {
		if (!part.isLimb() || !ServerConfig.get().spawnSeveredLimbs) {
			return null;
		}
		// A mob's limb is thrown only when the client can draw it (1.7): the
		// throws_limbs tag names the types whose texture layout is known.
		if (!(player instanceof ServerPlayer) && !MobAnatomy.throwsLimbs(player)) {
			return null;
		}
		ServerLevel level = (ServerLevel) player.level();
		SeveredLimb limb = new SeveredLimb(RDEntities.SEVERED_LIMB, level);
		Vec3 at = BodyHitResolver.regionCenter(player, part);
		limb.setPos(at.x, at.y, at.z);
		limb.ownedBy(player, part);
		limb.setYRot(player.getYRot());
		// The side the limb was on: a left leg with nowhere to be thrown falls
		// off to the left, clear of the body, rather than dropping inside it.
		double yaw = Math.toRadians(player.getYRot());
		Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
		Vec3 side = part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG ? left : left.scale(-1.0);
		limb.setDeltaMovement(throwVelocity(blowDirection, blast, side,
				player.getId() * 31L + part.ordinal() * 7L + player.tickCount));
		level.addFreshEntity(limb);
		return limb;
	}

	/** Whose part this is and which: the synced fields every lost part carries. */
	protected void ownedBy(LivingEntity owner, BodyPart part) {
		entityData.set(PART, (byte) part.ordinal());
		entityData.set(OWNER, owner.getUUID().toString());
		entityData.set(OWNER_TYPE, owner instanceof ServerPlayer ? ""
				: net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType()).toString());
		entityData.set(BLOODLESS, !MobAnatomy.bleeds(owner));
	}

	/**
	 * Where the limb goes. Along the blow — a swing carries what it takes off
	 * and a blast throws what it tears off — with a lift so it arcs rather than
	 * skids, and a small deterministic scatter so two limbs from one creeper do
	 * not land in the same spot. No direction means no throw: the limb tips
	 * off to {@code side} — the side of the body it was on — and drops there.
	 * Straight down was the first build, and a limb lying inside the player's
	 * own hitbox read as the leg still being there.
	 */
	public static Vec3 throwVelocity(Vec3 blowDirection, boolean blast, Vec3 side, long seed) {
		Vec3 velocity;
		if (blowDirection.lengthSqr() > 1.0e-6) {
			double speed = blast ? Balance.LIMB_BLAST_SPEED : Balance.LIMB_THROW_SPEED;
			double lift = blast ? Balance.LIMB_BLAST_LIFT : Balance.LIMB_THROW_LIFT;
			velocity = blowDirection.normalize().scale(speed).add(0.0, lift, 0.0);
		} else {
			Vec3 tip = side.lengthSqr() > 1.0e-6 ? side.normalize() : Vec3.ZERO;
			velocity = tip.scale(Balance.LIMB_DROP_SIDE_SPEED).add(0.0, Balance.LIMB_DROP_LIFT, 0.0);
		}
		return velocity.add(BloodFlow.jitterSigned(seed, 61) * Balance.LIMB_THROW_SCATTER, 0.0,
				BloodFlow.jitterSigned(seed, 62) * Balance.LIMB_THROW_SCATTER);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PART, (byte) BodyPart.LEFT_LEG.ordinal());
		builder.define(OWNER, "");
		builder.define(OWNER_TYPE, "");
		builder.define(AGE, 0);
		builder.define(BURN, 0);
		builder.define(BLOODLESS, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (AGE.equals(accessor)) {
			ageSyncTick = tickCount;
		}
	}

	@Override
	public void tick() {
		super.tick();
		tumbleO = tumble;
		lyingO = lying;
		if (!level().isClientSide()) {
			serverTick();
			if (isRemoved()) {
				return;
			}
		}
		physics();
	}

	protected void serverTick() {
		if (tickCount % AGE_SYNC_INTERVAL == 0) {
			entityData.set(AGE, tickCount);
		}
		// Fire and lava burn it away. isInLava is checked as well as isOnFire
		// because Entity.baseTick only lights a lava-touching entity through a
		// hurt call this entity refuses.
		if ((isOnFire() || isInLava()) && burnStep()) {
			return;
		}
		if (tickCount >= lifetime()) {
			discard();
		}
	}

	/**
	 * Meat, not an item: it bounces once, low, and stops dead on the ground
	 * instead of sliding, and it sinks in water. Runs on both sides so the
	 * flight is smooth between position packets, as vanilla's items do.
	 */
	private void physics() {
		if (isInWater()) {
			setDeltaMovement(getDeltaMovement().multiply(Balance.LIMB_WATER_DRAG, Balance.LIMB_WATER_DRAG, Balance.LIMB_WATER_DRAG)
					.add(0.0, -Balance.LIMB_WATER_SINK, 0.0));
		} else {
			applyGravity();
		}
		move(MoverType.SELF, getDeltaMovement());
		Vec3 velocity = getDeltaMovement();
		double drag = onGround() ? Balance.LIMB_GROUND_DRAG : Balance.LIMB_AIR_DRAG;
		velocity = velocity.multiply(drag, Balance.LIMB_AIR_DRAG, drag);
		if (onGround() && velocity.y < 0.0) {
			velocity = new Vec3(velocity.x,
					Math.abs(velocity.y) > Balance.LIMB_BOUNCE_MIN_SPEED ? -velocity.y * Balance.LIMB_BOUNCE : 0.0,
					velocity.z);
		}
		setDeltaMovement(velocity);
		present(velocity);
	}

	/** One tick further along burning away; true, and discarded, once it has gone. */
	protected boolean burnStep() {
		int burn = entityData.get(BURN) + 1;
		entityData.set(BURN, burn);
		if (burn >= Balance.LIMB_BURN_TICKS) {
			discard();
			return true;
		}
		return false;
	}

	/** Ticks it lies there before it is gone. */
	protected int lifetime() {
		return Balance.LIMB_LIFETIME_TICKS;
	}

	/** Presentation: spin while it flies, settle flat once it stops. */
	protected void present(Vec3 velocity) {
		boolean resting = onGround() && velocity.horizontalDistanceSqr() < 1.0e-4;
		if (resting) {
			lying = Math.min(1.0F, lying + 0.2F);
		} else {
			tumble += (float) (velocity.length() * 40.0);
			lying = Math.max(0.0F, lying - 0.15F);
		}
	}

	@Override
	protected double getDefaultGravity() {
		return Balance.LIMB_GRAVITY;
	}

	/** Which region this is. */
	public BodyPart part() {
		int ordinal = entityData.get(PART);
		BodyPart[] parts = BodyPart.values();
		return parts[Math.clamp(ordinal, 0, parts.length - 1)];
	}

	/** The mob type this came off, or null for a player's limb (1.7). */
	public net.minecraft.world.entity.@Nullable EntityType<?> ownerType() {
		String id = entityData.get(OWNER_TYPE);
		if (id.isEmpty()) {
			return null;
		}
		return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
				.getOptional(net.minecraft.resources.Identifier.tryParse(id)).orElse(null);
	}

	/** Whose limb, or null when unknown. */
	public @Nullable UUID ownerId() {
		String owner = entityData.get(OWNER);
		if (owner.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(owner);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Ticks since the loss, on either side; the server's word carried forward between syncs. */
	public int age() {
		if (!level().isClientSide()) {
			return tickCount;
		}
		return entityData.get(AGE) + Math.max(0, tickCount - ageSyncTick);
	}

	/** True for a limb off a body that never bleeds; the renderer paints no blood on it. */
	public boolean bloodless() {
		return entityData.get(BLOODLESS);
	}

	/** How far along burning away, 0 (not) to 1 (gone). */
	public double burn() {
		return Math.min(1.0, entityData.get(BURN) / (double) Balance.LIMB_BURN_TICKS);
	}

	/**
	 * Blood per second still leaving the cut end: a critical wound's rate at
	 * first, running dry over {@link Balance#LIMB_BLEED_TICKS}. Read by the
	 * client emitter; there is no heart in a limb, so it never spurts.
	 */
	public double bleedRate() {
		if (entityData.get(BLOODLESS)) {
			return 0.0;
		}
		double left = 1.0 - age() / (double) Balance.LIMB_BLEED_TICKS;
		return left <= 0.0 ? 0.0 : WoundSeverity.CRITICAL.bloodPerSecond() * left;
	}

	/** How much of its minute is left, 1 fresh to 0 gone; the renderer fades on the last of it. */
	public double remaining() {
		return Math.clamp(1.0 - age() / (double) lifetime(), 0.0, 1.0);
	}

	public float tumble(float partialTick) {
		return tumbleO + (tumble - tumbleO) * partialTick;
	}

	public float lying(float partialTick) {
		return lyingO + (lying - lyingO) * partialTick;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.NONE;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// Never saved (noSave); nothing to restore.
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		// Never saved.
	}
}
