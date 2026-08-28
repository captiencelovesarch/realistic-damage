package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.api.BodyView;
import dev.captience.realisticdamage.api.ExternalClassification;
import dev.captience.realisticdamage.api.RealisticDamageApi;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.damage.DamageClassifier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 1.3 interoperability, exercised through the same seams a firearm mod would
 * use. TACZ itself cannot be present — it is Forge-only, and the unofficial
 * Fabric port stops well short of this Minecraft version — so what is verified
 * here is that the hooks a gun mod needs actually redirect the pipeline.
 *
 * <p>The hooks are registered once and gated behind a flag, because the API
 * deliberately has no unregister: a mod that integrates does so for the life of
 * the process, and adding removal purely for tests would be a worse API.
 */
public class InteropGameTest {
	private static volatile boolean classifierActive;
	private static volatile Vec3 forcedImpact;

	static {
		RealisticDamageApi.registerClassifier((victim, source) ->
				classifierActive ? ExternalClassification.of(DamageCategory.HEAVY_BALLISTIC) : null);
		RealisticDamageApi.registerImpactPoints((victim, source) -> forcedImpact);
	}

	private ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(1.0, 2.0, 1.0));
		player.setPos(pos.x, pos.y, pos.z);
		RDAttachments.get(player).reset();
		return player;
	}

	/** A registered classifier must beat the built-in table. */
	@GameTest
	public void anExternalClassifierOverridesTheBuiltInTable(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		DamageSource source = helper.getLevel().damageSources().generic();
		try {
			classifierActive = false;
			var vanilla = DamageClassifier.classify(player, source);
			helper.assertTrue(vanilla != null && vanilla.category() != DamageCategory.HEAVY_BALLISTIC,
					"baseline must not already be ballistic, or this proves nothing");

			classifierActive = true;
			var overridden = DamageClassifier.classify(player, source);
			helper.assertTrue(overridden != null
							&& overridden.category() == DamageCategory.HEAVY_BALLISTIC,
					"a registered classifier must win over the built-in table");
		} finally {
			classifierActive = false;
		}
		helper.succeed();
	}

	/** Recursion safety outranks everything, including an external mod. */
	@GameTest
	public void externalClassifiersCannotReviveTheModsOwnDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			classifierActive = true;
			var bleedOut = DamageClassifier.classify(player,
					dev.captience.realisticdamage.damage.RDDamageTypes.bleedOut(helper.getLevel()));
			helper.assertTrue(bleedOut == null,
					"the mod's own damage must stay out of the pipeline whatever a mod claims");
		} finally {
			classifierActive = false;
		}
		helper.succeed();
	}

	/**
	 * The datapack route, which is the one a gun mod should use: a damage type
	 * in {@code realisticdamage:ballistic} is classified with no code on either
	 * side. TACZ's four bullet types are already listed there as optional
	 * entries, so a port arriving later needs nothing from anybody.
	 */
	@GameTest
	public void aTaggedDamageTypeClassifiesWithoutCode(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		var classified = DamageClassifier.classify(player,
				dev.captience.realisticdamage.damage.RDDamageTypes.testBallistic(helper.getLevel()));
		helper.assertTrue(classified != null, "a tagged damage type must classify");
		helper.assertTrue(classified.category() == DamageCategory.BALLISTIC,
				"and as what the tag says, got " + classified.category());
		helper.assertTrue(classified.routing()
						== dev.captience.realisticdamage.damage.ClassifiedDamage.Routing.LOCATIONAL,
				"a weapon a pack bothered to classify was aimed somewhere");
		helper.succeed();
	}

	/** An untagged source must fall through to the built-in table untouched. */
	@GameTest
	public void anUntaggedDamageTypeIsLeftToTheBuiltInTable(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		var classified = DamageClassifier.classify(player,
				helper.getLevel().damageSources().cactus());
		helper.assertTrue(classified != null
						&& classified.category() == DamageCategory.PUNCTURE,
				"cactus must still read as puncture");
		helper.succeed();
	}

	/**
	 * The whole point for a hitscan weapon: no projectile exists to reconstruct
	 * from, so without a supplied point every shot would resolve to the torso
	 * and headshots would silently stop working.
	 */
	@GameTest
	public void aSuppliedImpactPointDecidesTheRegion(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		DamageSource source = helper.getLevel().damageSources().generic();
		try {
			forcedImpact = player.getEyePosition();
			helper.assertTrue(BodyHitResolver.resolve(player, source) == BodyPart.HEAD,
					"a point at eye height must resolve to the head");

			forcedImpact = player.position().add(0.0, 0.2, 0.0);
			BodyPart low = BodyHitResolver.resolve(player, source);
			helper.assertTrue(low == BodyPart.LEFT_LEG || low == BodyPart.RIGHT_LEG,
					"a point at the feet must resolve to a leg, got " + low);
		} finally {
			forcedImpact = null;
		}
		helper.succeed();
	}

	/** A point well off the model still has to land somewhere sane. */
	@GameTest
	public void aWildImpactPointStillResolves(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			forcedImpact = player.position().add(40.0, 40.0, 40.0);
			helper.assertTrue(BodyHitResolver.resolve(player,
							helper.getLevel().damageSources().generic()) != null,
					"a bad point from another mod must not break the pipeline");
		} finally {
			forcedImpact = null;
		}
		helper.succeed();
	}

	@GameTest
	public void bodyViewReportsWhatTheBodyIsDoing(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.part(BodyPart.LEFT_ARM).setCondition(1.0);
		data.part(BodyPart.LEFT_ARM).setBroken(true);
		data.setBlood(20.0);

		BodyView view = RealisticDamageApi.bodyOf(player);
		helper.assertTrue(view != null, "a survival player must expose a body view");
		helper.assertTrue(view.broken(BodyPart.LEFT_ARM), "the broken arm must be visible");
		helper.assertTrue(view.state(BodyPart.LEFT_ARM) == BodyState.CRITICAL,
				"and its state, got " + view.state(BodyPart.LEFT_ARM));
		helper.assertTrue(view.bloodBand() >= 3, "severe blood loss must read as such");
		helper.assertTrue(view.condition(BodyPart.RIGHT_ARM) > 0.99,
				"an untouched region must read intact");
		helper.succeed();
	}

	/** Creative and spectator are outside the mod entirely, API included. */
	@GameTest
	public void bodyViewIsAbsentForPlayersTheModIgnores(GameTestHelper helper) {
		ServerPlayer creative = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
		helper.assertTrue(RealisticDamageApi.bodyOf(creative) == null,
				"creative players are not tracked, and the API must say so");
		helper.succeed();
	}
}
