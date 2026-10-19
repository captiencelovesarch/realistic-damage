package dev.captience.realisticdamage.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.ArmorModifier;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Anatomy for every other mob (1.7 slice 3): the whole vanilla roster has a
 * body and a plan, every one of them survives being hit every way the game
 * hits things, and each modifier does what its tag says.
 */
public class AllMobsGameTest {
	private ServerPlayer attacker(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	private static void tick(Mob mob, RealisticDamageData data, int ticks) {
		for (int i = 0; i < ticks && mob.isAlive(); i++) {
			mob.tickCount++;
			MobAnatomy.tickBody(mob, data);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, BlockPos at) {
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		return mob;
	}

	private static double modifier(LivingEntity mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			String id) {
		var instance = mob.getAttribute(attribute);
		if (instance == null) {
			return 0.0;
		}
		AttributeModifier modifier = instance.getModifier(RealisticDamage.id(id));
		return modifier == null ? 0.0 : modifier.amount();
	}

	private static double missing(RealisticDamageData data) {
		double total = 0.0;
		for (BodyPartData part : data.allParts()) {
			total += part.missing();
		}
		return total;
	}

	/** Every vanilla mob is in the tag. A mob added to the game and forgotten here fails this. */
	@GameTest(environment = "realisticdamage:isolated")
	public void everyVanillaMobHasABody(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> missing = new ArrayList<>();
		int mobs = 0;
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Entity entity;
			try {
				entity = type.create(level, EntitySpawnReason.COMMAND);
			} catch (RuntimeException e) {
				continue;
			}
			if (!(entity instanceof Mob) || entity instanceof Player) {
				continue;
			}
			mobs++;
			if (!type.builtInRegistryHolder().is(MobAnatomy.HAS_ANATOMY)) {
				missing.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
			}
		}
		helper.assertTrue(missing.isEmpty(), "mobs without a body: " + missing);
		helper.assertTrue(mobs >= 85, "the roster walk found the mobs at all: " + mobs);
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void bodiesGetTheRightPlans(GameTestHelper helper) {
		Map<EntityType<?>, BodyPlan> expected = Map.ofEntries(
				Map.entry(EntityTypes.ZOMBIE, BodyPlan.HUMANOID),
				Map.entry(EntityTypes.IRON_GOLEM, BodyPlan.HUMANOID),
				Map.entry(EntityTypes.ENDERMAN, BodyPlan.HUMANOID),
				Map.entry(EntityTypes.VEX, BodyPlan.SPRITE),
				Map.entry(EntityTypes.SNOW_GOLEM, BodyPlan.SNOWMAN),
				Map.entry(EntityTypes.COW, BodyPlan.QUADRUPED),
				Map.entry(EntityTypes.HORSE, BodyPlan.QUADRUPED),
				Map.entry(EntityTypes.CREEPER, BodyPlan.UPRIGHT),
				Map.entry(EntityTypes.SPIDER, BodyPlan.ARTHROPOD),
				Map.entry(EntityTypes.CHICKEN, BodyPlan.AVIAN),
				Map.entry(EntityTypes.BEE, BodyPlan.INSECT),
				Map.entry(EntityTypes.BAT, BodyPlan.FLYER),
				Map.entry(EntityTypes.ENDER_DRAGON, BodyPlan.DRAGON),
				Map.entry(EntityTypes.STRIDER, BodyPlan.STRIDER),
				Map.entry(EntityTypes.COD, BodyPlan.LONG),
				Map.entry(EntityTypes.GHAST, BodyPlan.FLOATER),
				Map.entry(EntityTypes.WITHER, BodyPlan.FLOATER),
				Map.entry(EntityTypes.SLIME, BodyPlan.BLOB));
		for (var entry : expected.entrySet()) {
			helper.assertTrue(MobAnatomy.plan(entry.getKey()) == entry.getValue(),
					entry.getKey() + " should be " + entry.getValue() + " but is " + MobAnatomy.plan(entry.getKey()));
		}
		helper.succeed();
	}

	/** What a body bleeds, and whether it bleeds at all, is in the tags the client reads. */
	@GameTest(environment = "realisticdamage:isolated")
	public void bloodIsTheRightColour(GameTestHelper helper) {
		helper.assertTrue(!MobAnatomy.bleeds(EntityTypes.SKELETON), "skeletons have nothing to bleed");
		helper.assertTrue(!MobAnatomy.bleeds(EntityTypes.IRON_GOLEM), "nor do golems");
		helper.assertTrue(!MobAnatomy.bleeds(EntityTypes.SKELETON_HORSE), "nor a skeleton horse");
		helper.assertTrue(!MobAnatomy.bleeds(EntityTypes.WITHER), "nor the wither");
		helper.assertTrue(MobAnatomy.bleeds(EntityTypes.COW), "a cow does");
		helper.assertTrue(EntityTypes.ENDER_DRAGON.builtInRegistryHolder().is(MobAnatomy.BLOOD_PURPLE), "the dragon bleeds purple");
		helper.assertTrue(EntityTypes.ENDERMAN.builtInRegistryHolder().is(MobAnatomy.BLOOD_PURPLE), "endermen bleed purple");
		helper.assertTrue(EntityTypes.ZOMBIE.builtInRegistryHolder().is(MobAnatomy.BLOOD_DARK), "zombies bleed dark");
		helper.assertTrue(EntityTypes.SLIME.builtInRegistryHolder().is(MobAnatomy.BLOOD_GREEN), "slimes bleed green");
		helper.assertTrue(EntityTypes.SPIDER.builtInRegistryHolder().is(MobAnatomy.BLOOD_BLUE), "spiders bleed blue");
		helper.assertTrue(EntityTypes.WARDEN.builtInRegistryHolder().is(MobAnatomy.BLOOD_SCULK), "the warden bleeds sculk");
		helper.succeed();
	}

	/**
	 * The smoke test that justifies "all": every tagged type spawned, hit by a
	 * player, a blast, a fall and fire, and ticked. No exception anywhere, every
	 * mob the blow actually hurt has a body, and nothing ever lands on a region
	 * its plan does not have.
	 */
	@GameTest(environment = "realisticdamage:isolated", maxTicks = 400)
	public void everyMobSurvivesBeingHitEveryWay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer attacker = attacker(helper);
		List<String> failures = new ArrayList<>();
		List<String> unhurt = new ArrayList<>();
		int bodies = 0;
		for (var holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(MobAnatomy.HAS_ANATOMY)) {
			EntityType<?> type = holder.value();
			String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
			Entity spawned = null;
			try {
				spawned = helper.spawn(type, new BlockPos(1, 2, 1));
				if (spawned instanceof net.minecraft.world.entity.decoration.Mannequin) {
					continue; // tagged, not a mob; MannequinGameTest covers it
				}
				if (!(spawned instanceof Mob mob)) {
					failures.add(name + ": not a mob");
					continue;
				}
				mob.setNoAi(true);
				attacker.setPos(mob.getX(), mob.getY(), mob.getZ() - 3.0);
				// lookAt sends a packet, and a mock player has no connection to send it on.
				Vec3 toward = mob.getBoundingBox().getCenter().subtract(attacker.getEyePosition());
				float yaw = (float) (Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90.0);
				float pitch = (float) -Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance()));
				attacker.setYRot(yaw);
				attacker.setYHeadRot(yaw);
				attacker.setXRot(pitch);
				float before = mob.getHealth();
				// A guardian's spikes hurt whoever touches it, and hurting a mock
				// player with no connection throws; it takes the arrow instead.
				if (!(mob instanceof net.minecraft.world.entity.monster.Guardian)) {
					mob.hurtServer(level, level.damageSources().playerAttack(attacker), 4.0F);
				}
				mob.invulnerableTime = 0;
				var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, attacker,
						new ItemStack(Items.ARROW), null);
				Vec3 face = mob.getBoundingBox().getCenter().add(0.0, 0.0, -mob.getBbWidth() / 2.0);
				arrow.setPos(face);
				arrow.setDeltaMovement(0.0, 0.0, 2.0);
				mob.hurtServer(level, level.damageSources().arrow(arrow, attacker), 3.0F);
				arrow.discard();
				boolean hurt = mob.getHealth() < before;
				mob.invulnerableTime = 0;
				mob.hurtServer(level, level.damageSources().explosion(null, null), 4.0F);
				mob.invulnerableTime = 0;
				mob.hurtServer(level, level.damageSources().fall(), 3.0F);
				mob.invulnerableTime = 0;
				mob.hurtServer(level, level.damageSources().onFire(), 1.0F);
				RealisticDamageData data = mob.getAttached(RDAttachments.DATA);
				if (data == null) {
					if (hurt) {
						failures.add(name + ": hurt but no body");
					} else {
						unhurt.add(name);
					}
					continue;
				}
				bodies++;
				BodyPlan plan = MobAnatomy.plan(mob);
				for (BodyPart part : BodyPart.values()) {
					BodyPartData region = data.part(part);
					if (!Double.isFinite(region.fraction())) {
						failures.add(name + ": " + part + " is " + region.fraction());
					}
					if (!plan.has(part) && region.missing() > 1.0e-9) {
						failures.add(name + ": " + plan + " took trauma on absent " + part);
					}
					Vec3 center = BodyHitResolver.regionCenter(mob, part);
					if (!Double.isFinite(center.x) || !Double.isFinite(center.y)) {
						failures.add(name + ": no centre for " + part);
					}
				}
				if (!MobAnatomy.bleeds(mob) && data.bleeding()) {
					failures.add(name + ": bloodless but bleeding");
				}
				if (!MobAnatomy.feelsConsciousness(mob) && (data.unconscious() || data.concussion())) {
					failures.add(name + ": mindless or boss but its mind was touched");
				}
				tick(mob, data, 40);
			} catch (Throwable t) {
				failures.add(name + ": " + t);
			} finally {
				if (spawned != null) {
					spawned.discard();
				}
			}
		}
		helper.assertTrue(failures.isEmpty(), "failures: " + failures);
		helper.assertTrue(bodies >= 80, bodies + " bodies; untouched by every blow: " + unhurt);
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aCowIsHitWhereItWasHit(GameTestHelper helper) {
		Mob cow = still(helper, EntityTypes.COW, new BlockPos(1, 1, 1));
		try {
			cow.setYRot(0.0F);
			cow.setYBodyRot(0.0F);
			AABB box = cow.getBoundingBox();
			Vec3 c = box.getCenter();
			double half = box.getXsize() / 2.0;
			// Yaw 0 faces +z; the body's right is -x.
			helper.assertTrue(BodyHitResolver.classifyPoint(cow, new Vec3(c.x, box.minY + box.getYsize() * 0.75, box.maxZ)) == BodyPart.HEAD,
					"the face is the head");
			helper.assertTrue(BodyHitResolver.classifyPoint(cow, new Vec3(c.x + half, box.minY + 0.1, c.z + half * 0.6)) == BodyPart.LEFT_ARM,
					"low at the front on the left is the left foreleg");
			helper.assertTrue(BodyHitResolver.classifyPoint(cow, new Vec3(c.x - half, box.minY + 0.1, c.z - half * 0.6)) == BodyPart.RIGHT_LEG,
					"low at the back on the right is the right hind leg");
			helper.assertTrue(BodyHitResolver.classifyPoint(cow, new Vec3(c.x + half, box.minY + box.getYsize() * 0.7, c.z)) == BodyPart.TORSO,
					"the flank is the torso");
		} finally {
			cow.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void fourLegsShareTheLoad(GameTestHelper helper) {
		Mob cow = still(helper, EntityTypes.COW, new BlockPos(1, 1, 1));
		Mob zombie = still(helper, EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
		try {
			RealisticDamageData cowBody = RDAttachments.get(cow);
			RealisticDamageData zombieBody = RDAttachments.get(zombie);
			cowBody.part(BodyPart.LEFT_LEG).setDestroyed(true);
			zombieBody.part(BodyPart.LEFT_LEG).setDestroyed(true);
			ImpairmentSystem.tick(cow, cowBody);
			ImpairmentSystem.tick(zombie, zombieBody);
			double cowSlow = -modifier(cow, Attributes.MOVEMENT_SPEED, "leg_speed");
			double zombieSlow = -modifier(zombie, Attributes.MOVEMENT_SPEED, "leg_speed");
			helper.assertTrue(cowSlow > 0.1 && cowSlow < zombieSlow,
					"a three-legged cow limps (" + cowSlow + ") less than a one-legged zombie (" + zombieSlow + ")");
			cowBody.part(BodyPart.RIGHT_LEG).setDestroyed(true);
			cowBody.part(BodyPart.LEFT_ARM).setDestroyed(true);
			ImpairmentSystem.tick(cow, cowBody);
			double crawl = -modifier(cow, Attributes.MOVEMENT_SPEED, "leg_speed");
			helper.assertTrue(crawl >= Balance.CRAWL_MOVEMENT_PENALTY - 1.0e-9, "three legs gone of four is a crawl: " + crawl);
			helper.assertTrue(MobAnatomy.canStrike(cow), "a cow's forelegs are not hands");
		} finally {
			cow.discard();
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aBatWithOneWingFalls(GameTestHelper helper) {
		Mob bat = helper.spawn(EntityTypes.BAT, new BlockPos(1, 4, 1));
		try {
			RealisticDamageData data = RDAttachments.get(bat);
			data.part(BodyPart.LEFT_ARM).setDestroyed(true);
			bat.setDeltaMovement(0.0, 0.3, 0.0);
			tick(bat, data, 1);
			helper.assertTrue(MobAnatomy.grounded(bat), "the bat is grounded");
			helper.assertTrue(bat.isNoAi(), "and flies by nothing any more");
			helper.assertTrue(!bat.isNoGravity(), "and gravity has it");
			helper.assertTrue(bat.getDeltaMovement().y <= -Balance.GROUNDED_FALL_SPEED + 1.0e-9,
					"and it is going down: " + bat.getDeltaMovement().y);
		} finally {
			bat.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aBeeWithABrokenWingFliesSlowerAndWithoutOneNotAtAll(GameTestHelper helper) {
		Mob bee = still(helper, EntityTypes.BEE, new BlockPos(1, 2, 1));
		try {
			RealisticDamageData data = RDAttachments.get(bee);
			data.part(BodyPart.RIGHT_ARM).damage(BodyPart.RIGHT_ARM.maxCondition() * 0.8);
			data.part(BodyPart.RIGHT_ARM).setBroken(true);
			ImpairmentSystem.tick(bee, data);
			double broken = -modifier(bee, Attributes.FLYING_SPEED, "wing_speed");
			helper.assertTrue(broken > 0.0 && broken < 1.0, "a broken wing slows flight: " + broken);
			data.part(BodyPart.RIGHT_ARM).setDestroyed(true);
			ImpairmentSystem.tick(bee, data);
			double gone = -modifier(bee, Attributes.FLYING_SPEED, "wing_speed");
			helper.assertTrue(gone >= 1.0 - 1.0e-9, "a lost wing ends it: " + gone);
			helper.assertTrue(MobAnatomy.grounded(bee), "and the bee is grounded");
		} finally {
			bee.discard();
		}
		helper.succeed();
	}

	/** Size, blood and mind: a golem is five people's worth of iron with no blood and no brain. */
	@GameTest(environment = "realisticdamage:isolated")
	public void anIronGolemIsBigBloodlessAndMindless(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = attacker(helper);
		Mob golem = still(helper, EntityTypes.IRON_GOLEM, new BlockPos(1, 1, 1));
		Mob zombie = still(helper, EntityTypes.ZOMBIE, new BlockPos(4, 1, 1));
		try {
			golem.hurtServer(level, level.damageSources().playerAttack(player), 8.0F);
			zombie.hurtServer(level, level.damageSources().playerAttack(player), 8.0F);
			RealisticDamageData golemBody = golem.getAttached(RDAttachments.DATA);
			RealisticDamageData zombieBody = zombie.getAttached(RDAttachments.DATA);
			helper.assertTrue(golemBody != null && zombieBody != null, "both have bodies");
			double golemLoss = missing(golemBody);
			double zombieLoss = missing(zombieBody);
			helper.assertTrue(golemLoss > 0.0 && golemLoss < zombieLoss * 0.4,
					"the same blow costs the golem a fraction (" + golemLoss + " vs " + zombieLoss + ")");
			helper.assertTrue(!golemBody.bleeding(), "and opens no wound in iron");
			golemBody.part(BodyPart.HEAD).damage(100.0);
			for (int i = 0; i < 5; i++) {
				golem.invulnerableTime = 0;
				golem.hurtServer(level, level.damageSources().playerAttack(player), 20.0F);
			}
			helper.assertTrue(!golemBody.concussion() && !golemBody.unconscious(),
					"and no blow to the head finds a mind to put out");
			helper.assertTrue(golemBody.consciousness() >= golemBody.consciousnessCap() - 1.0e-9,
					"consciousness never moved: " + golemBody.consciousness());
		} finally {
			golem.discard();
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void anIronGolemWithNoArmsCannotSwing(GameTestHelper helper) {
		Mob golem = still(helper, EntityTypes.IRON_GOLEM, new BlockPos(1, 1, 1));
		Mob target = still(helper, EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
		try {
			RealisticDamageData data = RDAttachments.get(golem);
			data.part(BodyPart.LEFT_ARM).setDestroyed(true);
			data.part(BodyPart.RIGHT_ARM).setDestroyed(true);
			float before = target.getHealth();
			boolean struck = golem.doHurtTarget(helper.getLevel(), target);
			helper.assertTrue(!struck && target.getHealth() == before, "an armless golem lands nothing");
		} finally {
			golem.discard();
			target.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aSnowGolemWithoutItsArmThrowsNothing(GameTestHelper helper) {
		SnowGolem golem = still(helper, EntityTypes.SNOW_GOLEM, new BlockPos(1, 1, 1));
		Mob target = still(helper, EntityTypes.ZOMBIE, new BlockPos(1, 1, 5));
		try {
			RealisticDamageData data = RDAttachments.get(golem);
			data.part(ImpairmentSystem.armFor(golem, true)).setDestroyed(true);
			AABB around = golem.getBoundingBox().inflate(8.0);
			int before = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball.class, around).size();
			golem.performRangedAttack(target, 1.0F);
			int after = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball.class, around).size();
			helper.assertTrue(after == before, "no snowball without the throwing arm");
		} finally {
			golem.discard();
			target.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aSlimeIsAllTorso(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob slime = still(helper, EntityTypes.SLIME, new BlockPos(1, 1, 1));
		try {
			slime.hurtServer(level, level.damageSources().playerAttack(attacker(helper)), 1.0F);
			slime.invulnerableTime = 0;
			slime.hurtServer(level, level.damageSources().fall(), 1.0F);
			RealisticDamageData data = slime.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null, "a slime has a body");
			for (BodyPart part : BodyPart.values()) {
				if (part != BodyPart.TORSO) {
					helper.assertTrue(data.part(part).missing() < 1.0e-9, "nothing but the torso is hurt: " + part);
				}
			}
			helper.assertTrue(data.part(BodyPart.TORSO).missing() > 0.0, "and the torso is");
		} finally {
			slime.discard();
		}
		helper.succeed();
	}

	/** A boss bleeds and breaks but cannot be put down or taken apart. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aBossCannotBeKnockedOutOrDismembered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = attacker(helper);
		// A blade, because a fist is blunt and blunt blows bruise rather than cut.
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		Mob warden = still(helper, EntityTypes.WARDEN, new BlockPos(1, 1, 1));
		var config = dev.captience.realisticdamage.config.ServerConfig.get();
		double multiplier = config.bodyDamageMultiplier;
		try {
			// Fifty times the trauma: past every destruction bar even after the
			// warden's size divides it, so only the boss rule stands in the way.
			config.bodyDamageMultiplier = 50.0;
			RealisticDamageData data = RDAttachments.get(warden);
			// The first blows take each region to nothing and open wounds; the
			// rest land on regions already gone and would finish them.
			for (int i = 0; i < 6; i++) {
				warden.invulnerableTime = 0;
				warden.hurtServer(level, level.damageSources().playerAttack(player), 15.0F);
				warden.invulnerableTime = 0;
				warden.hurtServer(level, level.damageSources().explosion(null, null), 10.0F);
			}
			for (BodyPart part : BodyPart.values()) {
				helper.assertTrue(!data.part(part).destroyed(), "the warden keeps its " + part);
			}
			helper.assertTrue(warden.isAlive(), "and its head and chest did not end it");
			helper.assertTrue(!data.unconscious() && !data.concussion() && !data.knockedDown(),
					"and it is neither out, concussed nor down");
			helper.assertTrue(data.bleeding(), "but it does bleed: missing " + missing(data) + ", worst wound " + data.worstWound()
					+ ", hp " + warden.getHealth() + ", blood " + data.blood());
		} finally {
			config.bodyDamageMultiplier = multiplier;
			warden.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void theDragonIsHitThroughItsParts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = attacker(helper);
		EnderDragon dragon = still(helper, EntityTypes.ENDER_DRAGON, new BlockPos(1, 4, 1));
		try {
			dragon.hurt(level, dragon.head, level.damageSources().playerAttack(player), 12.0F);
			RealisticDamageData data = dragon.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null, "the dragon has a body");
			helper.assertTrue(data.part(BodyPart.HEAD).missing() > 0.0, "a blow to the head part is a head wound");
			helper.assertTrue(data.part(BodyPart.TORSO).missing() < 1.0e-9, "and not a body wound");
			EnderDragonPart wing = null;
			for (EnderDragonPart part : dragon.getSubEntities()) {
				if (part.name.equals("wing")) {
					wing = part;
					break;
				}
			}
			helper.assertTrue(wing != null, "the dragon has wings");
			BodyPart region = dev.captience.realisticdamage.damage.DragonHits.regionOf(dragon, wing);
			helper.assertTrue(region.isArm(), "a wing part is a wing region: " + region);
		} finally {
			dragon.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void shellsAndBardingTakeTheBlow(GameTestHelper helper) {
		Mob turtle = still(helper, EntityTypes.TURTLE, new BlockPos(1, 1, 1));
		Mob cow = still(helper, EntityTypes.COW, new BlockPos(3, 1, 1));
		Mob horse = still(helper, EntityTypes.HORSE, new BlockPos(5, 1, 1));
		Mob llama = still(helper, EntityTypes.LLAMA, new BlockPos(7, 1, 1));
		try {
			helper.assertTrue(ArmorModifier.reduction(turtle, BodyPart.TORSO, DamageCategory.SLASH) >= Balance.SHELL_TORSO_REDUCTION - 1.0e-9,
					"a turtle's shell takes torso trauma");
			helper.assertTrue(ArmorModifier.reduction(turtle, BodyPart.HEAD, DamageCategory.SLASH) < 1.0e-9, "but not its head's");
			helper.assertTrue(ArmorModifier.reduction(cow, BodyPart.TORSO, DamageCategory.SLASH) < 1.0e-9, "a cow has no shell");
			horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
			helper.assertTrue(ArmorModifier.reduction(horse, BodyPart.TORSO, DamageCategory.SLASH) > 0.0, "horse armour covers the body");
			llama.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.CARPET.pick(net.minecraft.world.item.DyeColor.RED)));
			helper.assertTrue(ArmorModifier.reduction(llama, BodyPart.TORSO, DamageCategory.SLASH) < 1.0e-9, "a carpet is not armour");
		} finally {
			turtle.discard();
			cow.discard();
			horse.discard();
			llama.discard();
		}
		helper.succeed();
	}

	/** Food is the cure for them too: a vanilla heal mends the body by the same amount. */
	@GameTest(environment = "realisticdamage:isolated")
	public void aFedWolfMends(GameTestHelper helper) {
		Mob wolf = still(helper, EntityTypes.WOLF, new BlockPos(1, 1, 1));
		try {
			RealisticDamageData data = RDAttachments.get(wolf);
			data.part(BodyPart.LEFT_LEG).damage(8.0);
			double before = data.part(BodyPart.LEFT_LEG).missing();
			wolf.setHealth(2.0F);
			wolf.heal(6.0F);
			helper.assertTrue(data.part(BodyPart.LEFT_LEG).missing() < before,
					"the heal mended the leg: " + before + " -> " + data.part(BodyPart.LEFT_LEG).missing());
		} finally {
			wolf.discard();
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:isolated")
	public void aFishFallsOnItsBody(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob cod = still(helper, EntityTypes.COD, new BlockPos(1, 1, 1));
		try {
			DamageSource fall = level.damageSources().fall();
			cod.hurtServer(level, fall, 1.0F);
			RealisticDamageData data = cod.getAttached(RDAttachments.DATA);
			helper.assertTrue(data != null && data.part(BodyPart.TORSO).missing() > 0.0, "a fall on a fish lands on its body");
			helper.assertTrue(data.part(BodyPart.LEFT_LEG).missing() < 1.0e-9, "it has no legs to land on");
		} finally {
			cod.discard();
		}
		helper.succeed();
	}
}
