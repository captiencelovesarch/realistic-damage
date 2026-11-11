package dev.captience.realisticdamage.entity;

import dev.captience.realisticdamage.RDComponents;
import dev.captience.realisticdamage.RDEntities;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.systems.HeadMath;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A head, off its body (2.0). A {@link SeveredLimb} in every way that matters
 * (thrown along the blow, bleeding from the cut for most of a minute, burnt
 * away by fire, never saved), with two differences.
 *
 * <p>It rolls. A head is a box, so while it moves it turns over end on end, and
 * when it stops it settles onto a face rather than lying along its length.
 *
 * <p>It can be taken. A head that has a vanilla item (a player's, a zombie's, a
 * skeleton's or wither skeleton's, a piglin's, a creeper's) is picked up by
 * walking over it, like a dropped item, and lies as long as one. What you get
 * is that head marked {@link RDComponents#SEVERED}: you can carry it and place
 * it, never wear it. A head with no item (a villager's, an illager's) is only a
 * thing on the floor, and goes in a minute like a limb.
 *
 * <p>A wither skeleton's skull is the exception, because it is the Wither's
 * key. It cannot be taken while it lies there: it hisses and flashes purple
 * for a moment and then either burns away, with a thin echo of the Wither's
 * death cry, or drops with a pop as a skull you can pick up. It drops exactly
 * as often as vanilla drops one ({@link HeadMath#skullSurvives}), and a
 * beheaded wither skeleton drops no second skull from its loot.
 */
public class SeveredHead extends SeveredLimb {
	/** Whether this one can be picked up, so the client fades it on the right clock. */
	private static final EntityDataAccessor<Boolean> TROPHY =
			SynchedEntityData.defineId(SeveredHead.class, EntityDataSerializers.BOOLEAN);

	/** A player's face, for the head they leave. Server side only. */
	private @Nullable ResolvableProfile profile;
	/** For a wither skeleton's skull: whether it comes through as one you can take. Server side only. */
	private boolean skullSurvives;

	public SeveredHead(EntityType<? extends SeveredHead> type, Level level) {
		super(type, level);
	}

	/**
	 * Throws the head the body just lost, along {@code blowDirection} (zero for
	 * none: it tips off forward). Null when severed parts are switched off.
	 */
	public static @Nullable SeveredHead throwFrom(LivingEntity body, Vec3 blowDirection, boolean blast,
			net.minecraft.world.entity.@Nullable Entity by) {
		if (!ServerConfig.get().spawnSeveredLimbs) {
			return null;
		}
		ServerLevel level = (ServerLevel) body.level();
		SeveredHead head = new SeveredHead(RDEntities.SEVERED_HEAD, level);
		Vec3 at = BodyHitResolver.regionCenter(body, BodyPart.HEAD);
		head.setPos(at.x, at.y - 0.25, at.z);
		head.ownedBy(body, BodyPart.HEAD);
		head.setYRot(body.getYHeadRot());
		if (body instanceof ServerPlayer player) {
			head.profile = ResolvableProfile.createResolved(player.getGameProfile());
		}
		head.entityData.set(TROPHY, itemFor(body instanceof ServerPlayer ? null : body.getType()) != null
				&& body.getType() != EntityTypes.WITHER_SKELETON);
		if (body.getType() == EntityTypes.WITHER_SKELETON) {
			int looting = 0;
			if (by instanceof LivingEntity killer) {
				var lootingHolder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
						.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING);
				looting = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
						lootingHolder, killer.getMainHandItem());
			}
			head.skullSurvives = HeadMath.skullSurvives(body.getUUID().getLeastSignificantBits()
					^ body.getUUID().getMostSignificantBits(), looting, by instanceof Player);
		}
		Vec3 facing = Vec3.directionFromRotation(0.0F, body.getYHeadRot());
		head.setDeltaMovement(throwVelocity(blowDirection, blast, facing,
				body.getId() * 31L + body.tickCount));
		level.addFreshEntity(head);
		return head;
	}

	/** The vanilla head item for a body of this type (null: a player's), or null when there is none. */
	public static @Nullable Item itemFor(@Nullable EntityType<?> type) {
		if (type == null) {
			return Items.PLAYER_HEAD;
		}
		if (type == EntityTypes.ZOMBIE) {
			return Items.ZOMBIE_HEAD;
		}
		if (type == EntityTypes.SKELETON) {
			return Items.SKELETON_SKULL;
		}
		if (type == EntityTypes.WITHER_SKELETON) {
			return Items.WITHER_SKELETON_SKULL;
		}
		if (type == EntityTypes.PIGLIN || type == EntityTypes.PIGLIN_BRUTE) {
			return Items.PIGLIN_HEAD;
		}
		if (type == EntityTypes.CREEPER) {
			return Items.CREEPER_HEAD;
		}
		return null;
	}

	/** What picking this up gives: the head, severed. Null when there is nothing to take. */
	public @Nullable ItemStack stack() {
		Item item = itemFor(ownerType());
		if (item == null) {
			return null;
		}
		ItemStack stack = new ItemStack(item);
		if (item == Items.PLAYER_HEAD && profile != null) {
			stack.set(DataComponents.PROFILE, profile);
		}
		return RDComponents.sever(stack);
	}

	public boolean trophy() {
		return entityData.get(TROPHY);
	}

	/** A wither skeleton's skull, still hissing; the renderer flashes it. */
	public boolean hissing() {
		return ownerType() == EntityTypes.WITHER_SKELETON && age() < Balance.WITHER_SKULL_HISS_TICKS;
	}

	/** For the gametests: settle this wither skull's fate. */
	public void setSkullSurvives(boolean survives) {
		this.skullSurvives = survives;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && hissing() && tickCount % 2 == 0) {
			level().addParticle(net.minecraft.core.particles.ParticleTypes.WITCH,
					getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.25 + random.nextDouble() * 0.3,
					getZ() + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected void serverTick() {
		super.serverTick();
		if (isRemoved() || ownerType() != EntityTypes.WITHER_SKELETON) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		int age = age();
		if (age == 1 || age == Balance.WITHER_SKULL_HISS_TICKS / 2) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.HOSTILE,
					0.6F, 0.6F + age * 0.01F);
		}
		if (age < Balance.WITHER_SKULL_HISS_TICKS) {
			return;
		}
		if (skullSurvives) {
			// The pop: a skull, there to be picked up like any drop.
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level,
					getX(), getY() + 0.2, getZ(), RDComponents.sever(new ItemStack(Items.WITHER_SKELETON_SKULL)),
					0.0, 0.18, 0.0);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.7F, 0.8F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, getX(), getY() + 0.3, getZ(),
					12, 0.2, 0.2, 0.2, 0.05);
			discard();
			return;
		}
		// Or it burns away, in soul fire, and something of the Wither cries out as it goes.
		if (age % 3 == 0) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.25, getZ(),
					2, 0.15, 0.15, 0.15, 0.01);
		}
		if (burnStep()) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.35F, 1.9F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(),
					10, 0.15, 0.2, 0.15, 0.02);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TROPHY, false);
	}

	@Override
	protected int lifetime() {
		return trophy() ? Balance.HEAD_LIFETIME_TICKS : Balance.LIMB_LIFETIME_TICKS;
	}

	/**
	 * End over end while it moves; once it is down it comes to rest upright or
	 * on its crown, never face-down or face-up. A box rests on a face either
	 * way, and these two leave its owner, whose view rides it, looking out at
	 * the world rather than into the grass or at the sky.
	 */
	@Override
	protected void present(Vec3 velocity) {
		lying = 0.0F;
		boolean resting = onGround() && velocity.horizontalDistanceSqr() < 1.0e-4;
		if (resting) {
			float face = Math.round(tumble / 180.0F) * 180.0F;
			tumble += (face - tumble) * 0.35F;
		} else {
			tumble += (float) (velocity.length() * 40.0);
		}
	}

	@Override
	public void playerTouch(Player player) {
		if (level().isClientSide() || isRemoved() || !trophy() || player.isSpectator()
				|| age() < Balance.HEAD_PICKUP_DELAY_TICKS) {
			return;
		}
		ItemStack stack = stack();
		if (stack == null || !player.getInventory().add(stack)) {
			return;
		}
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
				((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
		discard();
	}
}
