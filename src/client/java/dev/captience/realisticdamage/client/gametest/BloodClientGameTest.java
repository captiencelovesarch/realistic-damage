package dev.captience.realisticdamage.client.gametest;

import java.util.function.Consumer;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.client.BloodBody;
import dev.captience.realisticdamage.client.BloodDrop;
import dev.captience.realisticdamage.client.BloodEmitter;
import dev.captience.realisticdamage.client.SeveredLimbRenderer;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.client.BloodLens;
import dev.captience.realisticdamage.client.BloodStains;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.FirstPersonBlood;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.systems.BleedingSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The blood system, running for real (1.5).
 *
 * <p>Almost all of this rework lives on the client — the emitter, the physics
 * drop, the stain field, the body staining and the lens — so almost none of it
 * is reachable from a server gametest and the arithmetic alone
 * ({@code BloodFlowTest}) proves only that the numbers are right. This boots a
 * world, opens real wounds through the server-authoritative state, and asserts
 * that blood actually appears: particles in the level, stains accumulating on
 * the body, and nothing thrown while drawing any of it.
 *
 * <p>Screenshots are taken as well, but they are the weaker half. A screenshot
 * of a blood effect that silently did nothing looks exactly like a screenshot of
 * one that worked, which is why every stage below asserts on state too.
 */
public class BloodClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// The config is a singleton shared by every test in this client session, and
		// this one asserts on what the effects do. Reloading from disk first means a
		// panic-buttoned config leaked by whichever class ran before us fails here as
		// nothing rendering, rather than as a puzzle — the restore below is the same
		// courtesy in the other direction, and neither can rely on run order.
		context.runOnClient(client -> ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			// Nothing wrong: no blood anywhere. Establishes that what follows is
			// caused by the wounds and not simply always on. Particles are compared
			// against this baseline rather than against zero, because the level is
			// entitled to its own ambient ones and none of them are our business.
			int clean = countParticles(context);
			assertNoStain(context, "an uninjured player");
			context.takeScreenshot("rd_blood_00_clean");

			// A critical wound: this is the arterial case, so it should be
			// emitting hard and throwing on the beat.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
			});
			context.waitTicks(40);
			int bleeding = countParticles(context);
			if (bleeding <= clean) {
				throw new AssertionError("a critical wound produced no particles: "
						+ clean + " before, " + bleeding + " after");
			}
			assertStain(context, BodyPart.TORSO, "a critical torso wound");
			context.takeScreenshot("rd_blood_01_critical");

			// Third person, so the body staining is actually on screen and the
			// render layer gets exercised rather than merely ticked.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(40);
			context.takeScreenshot("rd_blood_02_stained_body");

			// Staining has to be regional. A torso wound must not make the legs
			// bloody — that resolution is the entire reason the layer draws per
			// model part instead of tinting the whole model at once.
			assertNoStain(context, BodyPart.LEFT_LEG, "a wound on the torso only");

			// ...and wounding a leg must stain that leg.
			mutate(singleplayer, data -> data.addWound(BodyPart.LEFT_LEG, WoundSeverity.SEVERE));
			context.waitTicks(40);
			assertStain(context, BodyPart.LEFT_LEG, "a severe left leg wound");
			context.takeScreenshot("rd_blood_03_leg");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			// Staining has to keep accumulating while the wound is open. This is
			// the assertion that the whole client tick chain is actually running
			// rather than having painted one frame and stopped — the failure mode
			// a screenshot could not tell apart from success.
			double before = stainOf(context, BodyPart.TORSO);
			context.waitTicks(40);
			double after = stainOf(context, BodyPart.TORSO);
			if (after <= before) {
				throw new AssertionError("staining stopped growing over an open wound: "
						+ before + " then " + after);
			}
			// Blood on the camera. The layer that exists precisely because
			// everything else happens where a first-person player is not looking,
			// so if this one silently does nothing the rework has not fixed the
			// complaint it was built for.
			int droplets = context.computeOnClient(client -> BloodLens.count());
			if (droplets <= 0) {
				throw new AssertionError("bled for seconds and nothing reached the camera");
			}
			context.takeScreenshot("rd_blood_04_accumulating");

			// The arm you are looking down at (1.6.1). Third person showed the
			// blood and first person never did, because the hand is not drawn
			// through the player renderer's layers. Wound the right arm, hold the
			// empty hand up in first person, and the blood shell has to be
			// submitted onto it frame after frame.
			mutate(singleplayer, data -> data.addWound(BodyPart.RIGHT_ARM, WoundSeverity.CRITICAL));
			context.waitTicks(40);
			assertStain(context, BodyPart.RIGHT_ARM, "a critical right arm wound");
			long handSubmits = context.computeOnClient(client -> FirstPersonBlood.bloodSubmits());
			context.waitTicks(10);
			long handSubmitsLater = context.computeOnClient(client -> FirstPersonBlood.bloodSubmits());
			if (handSubmitsLater <= handSubmits) {
				throw new AssertionError("a bloody right arm was drawn clean in first person: "
						+ handSubmits + " then " + handSubmitsLater + " shells submitted");
			}
			context.takeScreenshot("rd_blood_14_first_person_arm");

			// Drops have to actually arrive somewhere. This is the headline of the
			// rework — the old particles hung in the air and never landed — and it
			// is invisible from the default view, because blood falls straight down
			// and lands under your own feet where the camera never looks. Point the
			// camera at the floor, and assert on the count rather than trusting the
			// screenshot to show it.
			context.runOnClient(client -> {
				if (client.player != null) {
					client.player.setXRot(85.0F);
				}
			});
			context.waitTicks(60);
			int marks = context.computeOnClient(client -> BloodStains.count());
			if (marks <= 0) {
				throw new AssertionError("bled for seconds over solid ground and left no mark on it");
			}
			context.takeScreenshot("rd_blood_07_ground");

			// Standing still over an open wound has to build a pool, and a pool is
			// a thing that keeps getting bigger while the wound is open. That is
			// the whole of the feature and nothing above asserts it: the count
			// check on the line above passes with two splats, with one, and with
			// seven hundred, which is exactly how 1.5 shipped a stain field that
			// threw away ninety-two per cent of every drop it was given — a full
			// minute of bleeding leaving the same two marks three seconds had.
			//
			// The count is the wrong measure — merging deliberately keeps it low —
			// so this measures ink actually laid down, which is what pooling means.
			//
			// Two deliberate choices. The wound is freshened and the field cleared
			// first, so the window below is this pool's own first eight seconds
			// rather than the tail of everything else this test has already done —
			// a pool that has been growing for twenty seconds is nearly flat over
			// any six of them, and the assertion would be measuring the test's
			// history instead of the field's behaviour. And the numbers are not
			// guesses: running this same scenario against 1.5's stain field gives
			// 3.00 -> 4.04 over these ticks, a ratio of 1.34, and it has plateaued
			// completely by twenty seconds. This build gives 3.09 -> 9.50, a ratio
			// of 3.08, and is still climbing at thirty. The threshold sits between
			// the two with room on both sides.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
			});
			context.runOnClient(client -> BloodStains.reset());
			context.waitTicks(40);
			double inkEarly = context.computeOnClient(client -> BloodStains.inkArea());
			if (inkEarly <= 0.0) {
				throw new AssertionError("two seconds of an open wound put no ink on the ground at all");
			}
			context.waitTicks(120);
			double inkLate = context.computeOnClient(client -> BloodStains.inkArea());
			if (inkLate < inkEarly * 1.5) {
				throw new AssertionError("six more seconds of bleeding grew the pool from " + inkEarly
						+ " to " + inkLate + " — it is splattering, not pooling");
			}
			// ...and the size the pool got to has to be one a single drop could not
			// have produced. This is the other half, and the ratio above cannot
			// prove it: a field that only ever laid fresh splats side by side would
			// also gain ink. Nothing but accretion gets a mark past the ceiling on
			// what one drop marks, so this is the assertion that the two ceilings
			// have not quietly become one number again.
			double biggest = context.computeOnClient(client -> BloodStains.largestMark());
			if (biggest <= Balance.STAIN_DROP_SIZE_MAX) {
				throw new AssertionError("the largest mark is " + biggest
						+ ", no bigger than one splat — a fed pool is not growing past a single drop");
			}

			// ...and the pool has to be still getting bigger long after every mark
			// in it is full, because that is the only thing accretion does. The two
			// assertions above do not ask this and cannot: over the first eight
			// seconds the marks are still filling, so a field that fed a full mark
			// and threw the drop away — 1.5's actual bug — still gains ink at very
			// nearly the same rate and still reaches a 0.46 mark. Both pass it.
			//
			// Twenty seconds in, filling is finished and the difference is the
			// whole difference. Measured, same wound and same scenario, by running
			// the accretion fall-through out of the field above and putting it
			// back: with it, ink reaches 14.21 at this tick and is still climbing
			// past 17 at forty-four seconds. Without it the field freezes solid at
			// 8.464 — ten marks at the 0.46 ceiling, 10 * 4 * 0.46^2 — and never
			// moves again however long the wound stays open. 1.5 as shipped froze
			// lower still, at 4.62. Eleven is the gap between the live number and
			// the frozen one, with about thirty per cent of room on either side.
			context.waitTicks(240);
			double inkSettled = context.computeOnClient(client -> BloodStains.inkArea());
			if (inkSettled < 11.0) {
				throw new AssertionError("twenty seconds of an open critical wound left " + inkSettled
						+ " square blocks of blood; a pool that only fills and never widens stops at 8.46"
						+ " — the drops are being thrown away again");
			}
			// ...and after twenty seconds of standing over an open artery, some of
			// those marks have to actually be DRAWN as pools. This is the positive
			// half of the roadmap's promise; the negative half — that walking
			// leaves none — is asserted a few lines below, and neither is worth
			// anything without the other. It costs no extra ticks: this is already
			// the longest stationary bleed in the suite.
			int poolsStanding = context.computeOnClient(client -> BloodStains.poolMarks());
			if (poolsStanding <= 0) {
				throw new AssertionError("twenty seconds of standing over a critical wound drew "
						+ poolsStanding + " marks as pools out of "
						+ context.computeOnClient(client -> BloodStains.count())
						+ " — a player who stands still is supposed to grow a pool");
			}
			context.takeScreenshot("rd_blood_08_pool");

			// The other half of the same promise, and the half nothing has ever
			// tested: standing still grows a pool, walking lays a trail. Same
			// wound, same length of time, same settling time for the drops still in
			// the air — the only difference is whether the player moved, and moving
			// has to spread the blood out into more marks rather than feeding one.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
			});
			int standing = marksLaidOver(context, 60, false);
			int walking = marksLaidOver(context, 60, true);
			int poolsWalkingSevere = context.computeOnClient(client -> BloodStains.poolMarks());
			if (walking <= standing) {
				throw new AssertionError("walking left " + walking + " marks and standing left " + standing
						+ " — a trail and a pool are supposed to look different");
			}
			// The count above says the blood was spread out. It cannot say what the
			// spread-out blood is drawn as, and that is where the whole distinction
			// actually lives: a trail is made of splats. A pool is twenty seconds of
			// standing over an open wound, not one heartbeat's spurt landing on its
			// own sibling. Before the feed gate, a player walking with an open
			// artery laid one pool-sprited mark per heartbeat — three blocks apart
			// at a sprint, all the same size — and every measure this test had
			// (count, ink, largest mark) read exactly the same as a trail of splats,
			// which is how it shipped.
			if (poolsWalkingSevere > 0) {
				throw new AssertionError("walking with a severe wound left " + poolsWalkingSevere
						+ " marks drawn as pools out of " + walking
						+ " — the trail is claiming to be a pool at every heartbeat");
			}
			// Again at critical, which is where the author saw it and where it fails
			// with the most margin: severe throws two drops a beat, critical three
			// or four, so critical put roughly three times as many false pools on
			// the ground as severe did.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
			});
			int walkingCritical = marksLaidOver(context, 60, true);
			int poolsWalkingCritical = context.computeOnClient(client -> BloodStains.poolMarks());
			if (poolsWalkingCritical > 0) {
				throw new AssertionError("walking with a critical wound left " + poolsWalkingCritical
						+ " marks drawn as pools out of " + walkingCritical
						+ " — one heartbeat's burst is still minting a pool");
			}

			// Healing closes the wounds; the blood must then dry off rather than
			// staying forever. Slowly — blood dries on, it does not rinse — so
			// this only has to fall, not vanish.
			double whileBleeding = stainOf(context, BodyPart.TORSO);
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(80);
			double whileHealed = stainOf(context, BodyPart.TORSO);
			if (whileHealed >= whileBleeding) {
				throw new AssertionError("blood never dried off a closed wound: "
						+ whileBleeding + " then " + whileHealed);
			}
			context.takeScreenshot("rd_blood_05_healed");

			// The burst a blow throws, which is the only blood in the mod with a
			// direction and the only blood that was ever going to reach a wall.
			//
			// Everything above this line is the client emitter: blood that seeps
			// out of a standing wound and falls at the player's feet. The burst is
			// the other path — thrown by the server, aimed back along the blow —
			// and it had been deleting itself since 1.5. Every drop of it is born
			// at BodyHitResolver.regionCenter, which is inside the victim's own
			// hitbox by construction, and the drop was built with a source entity
			// id of -1, which matches no player: the "do not splash the body you
			// came out of" guard therefore never recognised the body it came out
			// of, found the victim on tick one, painted them and deleted the drop.
			//
			// So: close the wounds and wipe both fields first, and the only blood
			// in the next second and a half is the one burst thrown below.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				BloodStains.reset();
				BloodBody.reset();
			});
			sprayCritical(singleplayer, false);
			context.waitTicks(30);

			// It has to get out of the body. Measured, same scenario, by putting the
			// -1 back and taking it out again: with the id wrong the twenty drops
			// leave 1 mark and 0.4624 square blocks of ink — which is 4 x 0.34^2,
			// i.e. exactly one drop at STAIN_DROP_SIZE_MAX, the single survivor.
			// With it right they leave 14 marks and 6.54. Six sits between the two
			// with a wide margin on both sides.
			int burstMarks = context.computeOnClient(client -> BloodStains.count());
			double burstInk = context.computeOnClient(client -> BloodStains.inkArea());
			if (burstMarks < 6) {
				throw new AssertionError("a critical blow threw "
						+ Balance.SPRAY_DROPS_PER_SEVERITY * (1 + WoundSeverity.CRITICAL.ordinal())
						+ " drops and left " + burstMarks + " marks on the world — they are being"
						+ " deleted inside the body they came out of");
			}
			// ...and the body it came out of has to be clean. This is the invariant
			// the source id actually encodes, and the half a count cannot see: a
			// count that went up would look like success even if every drop had
			// bounced straight off the victim. Blood on your own limbs is
			// BloodBody's business, fed by the open wound, not by your own spray
			// splashing back onto you.
			//
			// Measured under the same mutation: the torso reads 0.972 with the id
			// wrong and 0.0 with it right. 0.972 is BODY_STAIN_MAX clamped at 1.0
			// and then dried for the thirty ticks above at BODY_STAIN_DECAY / 20 —
			// 1.0 - 30 x 0.001 = 0.97. One blow saturating a body to its ceiling is
			// what the missing splatter actually looked like on screen: the blood
			// was not absent, it was all over the victim.
			assertNoStain(context, "a spray thrown clear of the body");
			context.takeScreenshot("rd_blood_09_burst");

			// Now the wall, which is the complaint in the author's own words. Same
			// burst, thrown at stone about a metre away, and the marks have to land
			// on the vertical face rather than at the thrower's feet.
			//
			// A flat world has no natural vertical surface anywhere near the player,
			// so a mark facing NORTH can only have come off the wall built below.
			// NORTH is the right face: spray() throws back along the blow, the blow
			// passed in is -Z, so the drops travel +Z and stop against the side of
			// the wall that points -Z.
			//
			// Measured across the same mutation: with the id right, 10 marks on the
			// wall out of 11 in the whole field. With it wrong, 1 out of 1 — the
			// single drop that got out went the whole way and hit the wall. So the
			// threshold cannot be "any at all": the broken build does put the
			// occasional drop on a wall, which is exactly why the author reported
			// this as blood that does not reach walls rather than as no blood.
			// Three is above the one and far below the ten, and it has to tolerate
			// the wall's near face landing anywhere from 0.5 to 1.5 blocks out
			// depending on where in his own block the player is standing (see
			// sprayCritical) against a drop that carries about 2.0.
			context.runOnClient(client -> BloodStains.reset());
			sprayCritical(singleplayer, true);
			context.waitTicks(30);
			int onWall = context.computeOnClient(client -> BloodStains.countOnFace(Direction.NORTH));
			if (onWall < 3) {
				throw new AssertionError("a critical blow thrown at a wall a metre away left " + onWall
						+ " marks on it, out of " + context.computeOnClient(client -> BloodStains.count())
						+ " marks in total — the blood is reaching the floor but not the wall");
			}
			// Look at the wall for the picture. The camera has been pointed at the
			// floor since the ground stage; yaw 0 is +Z, which is where the wall and
			// the blood are. Forty degrees down rather than level, because the eye is
			// 1.62 up and the drops cross the wall between the floor and about 1.2 —
			// atan(1.0 / 1.2) over a metre of gap puts that band under the crosshair.
			// After the assertion, so the view cannot be part of what is being
			// measured: this is the one screenshot in the file that shows the thing
			// the author said was missing.
			context.runOnClient(client -> {
				if (client.player != null) {
					client.player.setXRot(40.0F);
					client.player.setYRot(0.0F);
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("rd_blood_10_wall");

			// Contact is its own path. A seep falls at your feet and a burst follows
			// the blow; neither promises that a bleeding body held still against a
			// wall leaves anything on the surface it is touching. Build that exact
			// arrangement, use a non-arterial wound so no heartbeat spray can fake the
			// result, and assert the transfer counter as well as the visible face.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.TORSO, WoundSeverity.MODERATE);
			});
			context.runOnClient(client -> BloodStains.reset());
			prepareContactWall(singleplayer);
			context.waitTicks(60);
			long transfers = context.computeOnClient(client -> BloodStains.contactTransfers());
			if (transfers <= 0) {
				String contactState = context.computeOnClient(client -> {
					if (client.player == null) {
						return "no local player";
					}
					int collisions = 0;
					for (var ignored : client.player.level().getBlockCollisions(client.player,
							client.player.getBoundingBox().inflate(0.035, 0.0, 0.035))) {
						collisions++;
					}
					return "pos=" + client.player.position() + ", box=" + client.player.getBoundingBox()
							+ ", nearby collision shapes=" + collisions;
				});
				throw new AssertionError("a bleeding body held against stone transferred no blood; "
						+ contactState);
			}
			int contactMarks = context.computeOnClient(client -> BloodStains.countOnFace(Direction.NORTH));
			if (contactMarks <= 0) {
				throw new AssertionError(transfers + " contact transfers fired but left no mark on the wall");
			}
			context.takeScreenshot("rd_blood_11_contact");

			// A large wet wall mark should not remain a perfectly static decal. Feed
			// one through the same contact path until it is a pool-sized mark, then
			// watch the phase accumulator shed a real physics drop from its lower edge.
			// The counter is the non-vacuous half: a screenshot cannot prove a drop
			// existed between two frames.
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(10);
			context.runOnClient(client -> {
				BloodStains.reset();
				if (client.player == null) {
					throw new AssertionError("no local player for the wall-run stage");
				}
				for (int i = 0; i < Balance.STAIN_POOL_SPRITE_FEEDS + 2; i++) {
					if (!BloodStains.transfer(client.player, BodyPart.TORSO, 1.0, 17L)) {
						throw new AssertionError("the prepared contact wall stopped touching the player");
					}
				}
			});
			context.waitTicks(80);
			long runs = context.computeOnClient(client -> BloodStains.wallRuns());
			if (runs <= 0) {
				throw new AssertionError("a saturated wet wall mark never ran downward");
			}
			context.takeScreenshot("rd_blood_12_wall_run");

			// Losing the limb is a larger physical event than opening the critical
			// wound on it. Compare the two on the same client and over the same
			// settling window; both count and ink must clear the ordinary burst, or
			// the model can still make the limb disappear without the world recording
			// where it happened.
			context.runOnClient(client -> BloodStains.reset());
			severLeftLeg(singleplayer);
			context.waitTicks(30);
			int severanceMarks = context.computeOnClient(client -> BloodStains.count());
			double severanceInk = context.computeOnClient(client -> BloodStains.inkArea());
			if (severanceMarks <= burstMarks || severanceInk <= burstInk) {
				throw new AssertionError("severance left " + severanceMarks + " marks / " + severanceInk
						+ " ink; an ordinary critical opening left " + burstMarks + " / " + burstInk);
			}
			context.takeScreenshot("rd_blood_13_severance");

			// The limb itself (1.6.2). The loss above threw one along the blow;
			// it has to exist on this client, be drawn, and bleed where it lies.
			int limbs = context.computeOnClient(client -> client.level.getEntitiesOfClass(SeveredLimb.class,
					client.player.getBoundingBox().inflate(24.0)).size());
			if (limbs < 1) {
				throw new AssertionError("a lost leg left no limb in the world");
			}
			context.runOnClient(client -> {
				client.player.setXRot(30.0F);
				client.player.setYRot(0.0F);
			});
			long limbSubmits = context.computeOnClient(client -> SeveredLimbRenderer.submits());
			long limbBlood = context.computeOnClient(client -> BloodEmitter.limbDrops());
			context.waitTicks(20);
			if (context.computeOnClient(client -> SeveredLimbRenderer.submits()) <= limbSubmits) {
				throw new AssertionError("the severed limb was never drawn");
			}
			if (context.computeOnClient(client -> BloodEmitter.limbDrops()) <= limbBlood) {
				throw new AssertionError("the severed limb lay there without bleeding");
			}
			context.takeScreenshot("rd_blood_16_severed_limb");

			// Water (1.6.1). A drop treated water as air: it fell through, stopped
			// on the pool floor and marked it, blood sitting on the bottom like
			// gravel. Wade into a shallow pool with an open wound and the drops
			// must disperse — and the floor under the water must stay unmarked.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
			});
			wadeIntoPool(singleplayer);
			// Order matters: the limb is discarded inside wadeIntoPool, but its
			// last drops are still in the air for a few ticks and land wherever
			// they were going. Let them, then clear the field, so the count below
			// is the pool's alone. Two of those late drops failed this stage when
			// the reset came first.
			context.waitTicks(25);
			context.runOnClient(client -> BloodStains.reset());
			mutate(singleplayer, data -> data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE));
			long dissolvedBefore = context.computeOnClient(client -> BloodDrop.dissolvedCount());
			context.waitTicks(60);
			long dissolvedAfter = context.computeOnClient(client -> BloodDrop.dissolvedCount());
			if (dissolvedAfter <= dissolvedBefore) {
				throw new AssertionError("bled into a pool for three seconds and no drop dispersed");
			}
			int wetMarks = context.computeOnClient(client -> BloodStains.count());
			if (wetMarks != 0) {
				throw new AssertionError("blood marked the bottom of the pool: " + wetMarks + " marks");
			}
			context.runOnClient(client -> {
				if (client.player != null) {
					client.player.setXRot(72.0F);
				}
			});
			context.waitTicks(4);
			context.takeScreenshot("rd_blood_15_water");

			// The panic button has to cover blood too. This is the promise
			// disableAllEffects makes, and a new effect that forgot to join it
			// would break that promise silently.
			// Everything is cleared first so the assertions below are about the
			// switch suppressing new blood, not about old blood happening to have
			// aged out inside the window — which is what they would have measured
			// otherwise, and would have passed or failed on timing.
			context.runOnClient(client -> {
				ClientEffectsConfig.get().disableAllEffects();
				BloodBody.reset();
				BloodLens.reset();
				BloodStains.reset();
			});
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.HEAD, WoundSeverity.CRITICAL);
			});
			context.waitTicks(40);
			assertNoStain(context, "blood with every effect disabled");
			int lensWhenOff = context.computeOnClient(client -> BloodLens.count());
			if (lensWhenOff > 0) {
				throw new AssertionError("the panic button left " + lensWhenOff + " droplets on the camera");
			}
			int marksWhenOff = context.computeOnClient(client -> BloodStains.count());
			if (marksWhenOff > 0) {
				throw new AssertionError("the panic button left " + marksWhenOff + " marks on the world");
			}
			context.takeScreenshot("rd_blood_06_disabled");

			// The config is a singleton shared by every test in this client
			// session, and the line above just zeroed all of it. Reloading from
			// disk restores the defaults; without this, whichever test runs next
			// inherits a panic-buttoned config and renders nothing — which is
			// exactly how the death test shipped its first screenshots of a
			// completely vanilla death screen.
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	/**
	 * Particles alive in the level right now.
	 *
	 * <p>{@code countParticles} returns a formatted per-group breakdown rather
	 * than a bare total, so the counts are summed out of it. The first version of
	 * this helper parsed the whole string as one integer, caught the
	 * NumberFormatException and returned a sentinel that made every particle
	 * assertion below skip itself — which passed, and proved nothing. There is no
	 * escape hatch now: a format change should break the test loudly.
	 */
	private static int countParticles(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			int total = 0;
			for (String token : client.particleEngine.countParticles().split("\\D+")) {
				if (!token.isEmpty()) {
					total += Integer.parseInt(token);
				}
			}
			return total;
		});
	}

	/**
	 * Marks laid on the world over {@code ticks}, from an empty field, either
	 * standing still or walking forward.
	 *
	 * <p>The twenty ticks on the end are the drops still in the air: a drop takes
	 * about seven ticks to fall from the torso to the floor, and counting without
	 * waiting for them would systematically undercount whichever leg of the
	 * comparison ran last. Both legs get the same allowance for the same reason.
	 */
	private static int marksLaidOver(ClientGameTestContext context, int ticks, boolean walking) {
		context.runOnClient(client -> BloodStains.reset());
		if (walking) {
			context.getInput().holdKeyFor(options -> options.keyUp, ticks);
		} else {
			context.waitTicks(ticks);
		}
		context.waitTicks(20);
		return context.computeOnClient(client -> BloodStains.count());
	}

	/**
	 * Throws one critical torso burst on the server, optionally at a wall built
	 * for it to hit.
	 *
	 * <p>The blow is passed as {@code -Z} because {@code spray} throws back along
	 * it, so the drops travel {@code +Z} and the face they contact is
	 * {@link Direction#NORTH}.
	 *
	 * <p>The wall goes at {@code floor(z + 1.5)}, which puts its near face between
	 * 0.5 and 1.5 blocks out wherever in his block the player happens to be
	 * standing — never closer than 0.2 blocks to his own hitbox, and never further
	 * than the 2.0 blocks a thrown drop can carry. Five wide and three high
	 * because the cone is {@link Balance#SPRAY_SPREAD} degrees in both yaw and
	 * pitch: at a metre out that is about 0.8 blocks of lateral spread, and the
	 * drops cross the wall anywhere from the floor to a little above head height.
	 */
	private static void sprayCritical(TestSingleplayerContext singleplayer, boolean intoWall) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (intoWall) {
					ServerLevel level = player.level();
					int wallZ = Mth.floor(player.getZ() + 1.5);
					int originX = Mth.floor(player.getX());
					int originY = Mth.floor(player.getY());
					for (int dx = -2; dx <= 2; dx++) {
						for (int dy = 0; dy <= 2; dy++) {
							level.setBlockAndUpdate(new BlockPos(originX + dx, originY + dy, wallZ),
									Blocks.STONE.defaultBlockState());
						}
					}
				}
				BleedingSystem.spray(player, BodyPart.TORSO, WoundSeverity.CRITICAL,
						new Vec3(0.0, 0.0, -1.0));
			}
		});
	}

	/** Builds a north-facing stone wall and places the player one millimetre from it. */
	private static void prepareContactWall(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ServerLevel level = player.level();
				int wallZ = Mth.floor(player.getZ()) + 2;
				int originX = Mth.floor(player.getX());
				int originY = Mth.floor(player.getY());
				for (int dx = -2; dx <= 2; dx++) {
					for (int dy = 0; dy <= 2; dy++) {
						level.setBlockAndUpdate(new BlockPos(originX + dx, originY + dy, wallZ),
								Blocks.STONE.defaultBlockState());
					}
				}
				player.setDeltaMovement(Vec3.ZERO);
				player.teleportTo(player.getX(), player.getY(),
						wallZ - player.getBbWidth() / 2.0 - 0.001);
			}
		});
	}

	/**
	 * Moves the player twelve blocks along and stands them knee-deep in a
	 * five-by-five pool one block deep, with dry floor under it. Wide enough
	 * that a falling seep cannot drift past its edge, shallow enough that the
	 * player stands rather than swims.
	 */
	private static void wadeIntoPool(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ServerLevel level = player.level();
				// The severance stage left a limb bleeding onto dry floor within
				// emitter range; its marks are not the pool's. Two of them failed
				// this stage's zero-marks assertion the first time it ran after
				// the limb existed.
				level.getEntitiesOfClass(SeveredLimb.class,
						player.getBoundingBox().inflate(64.0)).forEach(SeveredLimb::discard);
				double px = player.getX() + 12.0;
				int originX = Mth.floor(px);
				int originY = Mth.floor(player.getY());
				int originZ = Mth.floor(player.getZ());
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						level.setBlockAndUpdate(new BlockPos(originX + dx, originY, originZ + dz),
								Blocks.WATER.defaultBlockState());
					}
				}
				player.setDeltaMovement(Vec3.ZERO);
				player.teleportTo(px, player.getY(), player.getZ());
			}
		});
	}

	/** Takes the limb through the production choke point, then closes the ongoing wound. */
	private static void severLeftLeg(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.teleportTo(player.getX(), player.getY(), player.getZ() - 3.0);
				RealisticDamageData data = RDAttachments.get(player);
				data.reset();
				DestructionLogic.destroy(player, data, BodyPart.LEFT_LEG,
						WoundSeverity.CRITICAL, new Vec3(0.0, 0.0, 1.0));
				data.clearWounds();
				RDAttachments.pushSnapshot(player);
			}
		});
	}

	private static void assertStain(ClientGameTestContext context, BodyPart part, String when) {
		double level = stainOf(context, part);
		if (level <= 0.0) {
			throw new AssertionError("the " + part + " should be bloody with " + when + ", but is clean");
		}
	}

	private static void assertNoStain(ClientGameTestContext context, BodyPart part, String when) {
		double level = stainOf(context, part);
		if (level > 0.0) {
			throw new AssertionError("the " + part + " is bloody with " + when + " (" + level + ")");
		}
	}

	private static void assertNoStain(ClientGameTestContext context, String when) {
		for (BodyPart part : BodyPart.values()) {
			assertNoStain(context, part, when);
		}
	}

	private static double stainOf(ClientGameTestContext context, BodyPart part) {
		return context.computeOnClient(client -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.player == null ? 0.0 : BloodBody.stain(mc.player.getId(), part);
		});
	}

	private static void mutate(TestSingleplayerContext singleplayer, Consumer<RealisticDamageData> change) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				RealisticDamageData data = RDAttachments.get(player);
				change.accept(data);
				RDAttachments.pushSnapshot(player);
			}
		});
	}
}
