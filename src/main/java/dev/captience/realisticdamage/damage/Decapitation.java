package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.entity.SeveredHead;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.systems.HeadMath;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Heads come off (2.0).
 *
 * <p>The rule is the one that takes limbs, read for the head: a head that is
 * already Disabled and takes another serious blow is lost. What the blow was
 * decides how: an edge takes it off, anything else crushes it as before. A
 * killing blow counts too, because the pipeline that runs the finish-off rule
 * never sees a hit that kills (Fabric's AFTER_DAMAGE does not fire on one), so
 * an axe that both empties the health bar and lands on a ruined head would
 * otherwise only ever crush it. Dying to it is the proof it was serious enough,
 * the same argument {@link LethalBlast} makes. And a blast at its most violent
 * throws the head with the limbs.
 *
 * <p>Deterministic like every other injury: no roll anywhere. Bosses keep their
 * heads ({@link MobAnatomy#canLoseParts}), and only bodies the client can draw
 * headless lose one: players, and the {@link #DECAPITABLE} tag.
 *
 * <p>A zombie in {@link #WALKS_HEADLESS} does not die of it at once. It drops
 * its target and walks on blind for a few seconds; {@code MobAnatomy} runs that
 * and ends it.
 */
public final class Decapitation {
	private Decapitation() {}

	/** Mobs whose head can come off: the client knows how to draw them without one. */
	public static final TagKey<EntityType<?>> DECAPITABLE =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("decapitable"));
	/** The undead that walk on for a while without a head. */
	public static final TagKey<EntityType<?>> WALKS_HEADLESS =
			TagKey.create(Registries.ENTITY_TYPE, RealisticDamage.id("walks_headless"));

	/**
	 * A beheaded wither skeleton's skull came off with its head, and that head
	 * decides whether there is a skull to take (see {@link SeveredHead}): its
	 * loot gets no second one.
	 */
	public static void init() {
		net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY)
					instanceof LivingEntity body && body.getType() == net.minecraft.world.entity.EntityTypes.WITHER_SKELETON) {
				RealisticDamageData data = body.getAttached(RDAttachments.DATA);
				if (data != null && data.headSevered()) {
					drops.removeIf(stack -> stack.is(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL));
				}
			}
		});
	}

	/** Whether this kind of blow takes a head off rather than crushing it. */
	public static boolean edged(@Nullable DamageCategory category) {
		return HeadMath.edged(category);
	}

	/** See {@link HeadMath#takes}. */
	public static boolean takes(@Nullable DamageCategory category, BodyState head, boolean destroyed,
			boolean lethal, double worth) {
		return HeadMath.takes(category, head, destroyed, lethal, worth);
	}

	/** Whether this body can lose its head at all, right now. */
	public static boolean can(LivingEntity body, RealisticDamageData data) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableLimbDestruction || !config.enableDecapitation || !MobAnatomy.canLoseParts(body)) {
			return false;
		}
		if (data.part(BodyPart.HEAD).destroyed() || data.headSevered()) {
			return false;
		}
		return body instanceof Player || body.is(DECAPITABLE);
	}

	/**
	 * Takes the head of a living body, from the finish-off rule: it comes off,
	 * and then the body dies of it, unless it is a zombie that walks on.
	 */
	public static void decapitate(LivingEntity body, RealisticDamageData data, Vec3 blowDirection,
			@Nullable Entity by) {
		sever(body, data, blowDirection, false, by);
		if (body.is(WALKS_HEADLESS) && !(body instanceof Player)) {
			data.setHeadlessTicks(HeadMath.walkTicks(MobAnatomy.vitalityScale(body)));
			MobAnatomy.track(body);
			RDAttachments.pushSnapshot(body);
			return;
		}
		ServerLevel level = (ServerLevel) body.level();
		body.hurtServer(level, RDDamageTypes.decapitated(level, by), Float.MAX_VALUE);
	}

	/**
	 * The head comes off: the region is gone, the neck opens, the head is
	 * thrown along the blow, and every client watching is told before it hears
	 * anything else about this body. Works on a body that is already dead (a
	 * killing blow, a lethal blast) as well as on a living one.
	 */
	public static @Nullable SeveredHead sever(LivingEntity body, RealisticDamageData data, Vec3 blowDirection,
			boolean blast, @Nullable Entity by) {
		data.part(BodyPart.HEAD).setDestroyed(true);
		data.setHeadSevered(true);
		if (ServerConfig.get().enableBleeding && MobAnatomy.bleeds(body)) {
			// The neck: the worst wound there is, on the region that is gone, so
			// the body keeps pumping it out through its last beats.
			data.addWound(BodyPart.HEAD, WoundSeverity.CRITICAL);
			// spray() throws against the direction it is given: this is up, out of the neck.
			BleedingSystem.spray(body, BodyPart.HEAD, WoundSeverity.CRITICAL, new Vec3(0.0, -1.0, 0.0), true);
		}
		SeveredHead head = SeveredHead.throwFrom(body, blowDirection, blast, by);
		RDAttachments.pushSnapshot(body);
		return head;
	}
}
