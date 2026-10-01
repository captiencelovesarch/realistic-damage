package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.damage.DeathMath;
import dev.captience.realisticdamage.net.DeathCameraPayload;
import dev.captience.realisticdamage.systems.DeathMotion;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The camera stays with the body (1.7).
 *
 * <p>The playtest that asked for this: cornered on a bastion roof by a brute,
 * killed by an axe, knocked off the edge — and the camera never moved. Not the
 * mod: vanilla deletes the local player from the client world exactly twenty
 * ticks after death ({@code LocalPlayer.tickDeath}), and the camera stays
 * bolted to the deleted entity wherever it was on that tick. A second is not
 * long enough for knockback to carry a body off a roof, so what the player saw
 * was the angle they died at, held.
 *
 * <p>With this on, the body is kept in the world and keeps its physics until
 * the respawn (see {@code LocalPlayerDeathMixin}): the blow's knockback
 * carries it, gravity takes it over the edge, and the view goes with it. Its
 * position is never sent to the server — a corpse has no business walking a
 * dead player through a portal. On top of that:
 *
 * <ul>
 * <li><b>The collapse.</b> The eye sinks to the floor over the first
 * three-quarters of a second and the view rolls over and sags, so the last
 * thing you see is the world from where you ended up lying.</li>
 * <li><b>The thud.</b> A body that falls and lands jolts the view and makes a
 * sound, the same as a living one would.</li>
 * <li><b>The held blackout.</b> A violent death cuts to black within a second,
 * which would hide the fall this exists to show. While the body is still
 * moving the death look may darken the screen only so far; once it has come
 * to rest the blackout completes as it always did.</li>
 * <li><b>The death screen fades in</b> once the body has settled, and its
 * buttons only wake up once it is nearly visible — a respawn button you could
 * press before you could see it would be a trap.</li>
 * </ul>
 *
 * <p>Deterministic (no RNG, which side you fall to follows the knockback) and
 * behind one switch, Death Camera, on by default. Off gives vanilla back
 * exactly: the one-second freeze and the sudden screen.
 *
 * <p><b>1.7, second pass.</b> The playtest: it "moves a bunch and then just
 * FREEZES", and it should move "based on different kinds of deaths". Two
 * causes for the first. The server removed the dead player from its world a
 * second in, which took every mob off this client and stopped every update —
 * fixed on the server ({@code systems.DeathHold}, asked for here while the
 * camera watches). And the view itself stopped dead the moment the collapse
 * curve ran out. The motion is now a {@link DeathMotion} profile chosen by the
 * server's {@link DeathKind} and scaled by violence: springs carry the roll and
 * pitch so the body lands and bounces instead of stopping, a blast spins the
 * view while the body is airborne, fire and lava thrash, lightning convulses,
 * a drowning drifts, and after landing the head keeps settling for a few
 * seconds and a violent death twitches once or twice.
 */
public final class DeathCamera {
	private DeathCamera() {}

	/**
	 * The body's angular springs, per tick squared and per tick. Lightly
	 * under-damped: a roll that lands overshoots a degree or two and comes back,
	 * which is what a body does and what a curve that simply ends does not.
	 */
	private static final double ANGLE_STIFFNESS = 0.55;
	private static final double ANGLE_DAMPING = 1.15;
	/** How hard a landing kicks the roll and pitch, degrees a tick at full thud. */
	private static final double THUD_ROLL_KICK = 5.0;
	private static final double THUD_PITCH_KICK = 3.0;
	/** How hard an agonal twitch kicks, degrees a tick. */
	private static final double TWITCH_KICK = 2.6;

	/** Below this horizontal speed, blocks a tick, a body on the ground is still. */
	private static final double STILL_SPEED = 0.05;
	/** Ticks of stillness before the body counts as having come to rest. */
	private static final int STILL_TICKS = 6;
	/** No settling before this: the knockback packet arrives a tick or two after the death one. */
	private static final int SETTLE_EARLIEST = 8;
	/** A body that never stops (ice, a lava current, the void) counts as settled here anyway. */
	private static final int SETTLE_LATEST = 120;

	/** How dark the death look may get while the body is still moving. */
	private static final double MOVING_BLACK_CAP = 0.6;
	/** Ticks to let the held blackout go once the body is at rest. */
	private static final double BLACK_RELEASE_TICKS = 16.0;

	/** Ticks the death screen takes to fade in. */
	private static final int SCREEN_FADE_TICKS = 30;
	/** The screen waits at least this share of the death sequence... */
	private static final double SCREEN_DELAY_SHARE = 0.5;
	/** ...clamped to this many ticks either way. */
	private static final int SCREEN_DELAY_MIN = 20;
	private static final int SCREEN_DELAY_MAX = 60;
	/** Buttons wake up at this much of the fade. */
	private static final double BUTTONS_AT = 0.85;

	/** Fall speed, blocks a tick, below which a landing makes no thud. */
	private static final double THUD_MIN_SPEED = 0.35;
	/** Fall speed of a full-strength thud. */
	private static final double THUD_FULL_SPEED = 1.6;
	private static final double THUD_DROP = 0.25;
	private static final double THUD_STIFFNESS = 1.4;
	private static final double THUD_DAMPING = 0.9;

	private static boolean tracking;
	private static int ticks;
	private static int stillTicks;
	private static boolean settled;
	private static int restTicks;
	private static int screenTicks;
	private static int screenTicksOld;
	private static double rollSign = 1.0;
	private static boolean wasOnGround = true;
	private static double lastFallSpeed;
	private static double thudPos;
	private static double thudVel;
	private static int thuds;

	private static DeathKind kind = DeathKind.BLOW;
	private static DeathMotion.Profile profile = DeathMotion.profile(DeathKind.BLOW, 0.0);
	private static double rollPos;
	private static double rollVel;
	private static double pitchPos;
	private static double pitchVel;
	/** Accumulated airborne spin, degrees, about the roll and pitch axes. */
	private static double tumbleRoll;
	private static double tumblePitch;
	private static boolean airborneSinceDeath;
	private static double maxTumble;
	private static boolean released;
	/** What {@link #apply} last put on the view, for the client gametest. */
	private static double lastRoll;
	private static double lastPitch;

	/** The switch. */
	public static boolean enabled() {
		return ClientEffectsConfig.get().deathCamera;
	}

	/** True while the local player is dead and this is in charge of their camera. */
	public static boolean active() {
		return enabled() && ClientState.dying();
	}

	/** Once per client tick, after {@link ClientState#tick()}. */
	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || !player.isDeadOrDying() || !enabled()) {
			if (tracking) {
				reset();
			}
			return;
		}
		if (!tracking) {
			tracking = true;
			wasOnGround = player.onGround();
			// No knockback to fall away from (a drop, a drowning, lava): pick the
			// side from where the body is, so it is fixed for a given death and
			// not always the same one.
			rollSign = ((Mth.floor(player.getX()) + Mth.floor(player.getZ())) & 1) == 0 ? 1.0 : -1.0;
			choose();
			// Keep me in the world while I watch it (DeathHold, server side).
			watch(true);
			if (profile.impactAtDeath()) {
				// The landing that killed you happened on the tick you died; the body
				// is already on the floor, so there is no landing left to detect.
				impact(profile.thud(), false);
			}
		}
		ticks++;
		// The death payload is sent from the same server tick as the death and
		// normally lands first; if it was late, take it while the fall is young.
		if (ticks <= 4 && (kind != ClientState.deathKind() || profile != chosenFor(ClientState.deathKind()))) {
			choose();
		}

		// The void has no floor to land on: stop the body a little below the
		// world rather than let it fall for as long as the death screen is up.
		double floor = player.level().getMinY() - 64.0;
		if (player.getY() < floor) {
			player.setPos(player.getX(), floor, player.getZ());
			player.setDeltaMovement(Vec3.ZERO);
		}

		Vec3 velocity = player.getDeltaMovement();
		// Fall to the side the blow pushed you, once the knockback has arrived.
		if (ticks == 3 && velocity.horizontalDistanceSqr() > 1.0e-4) {
			Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
			rollSign = velocity.x * right.x + velocity.z * right.z >= 0.0 ? 1.0 : -1.0;
		}

		boolean onGround = player.onGround();
		if (onGround && !wasOnGround && lastFallSpeed > THUD_MIN_SPEED) {
			thud(lastFallSpeed);
		}
		wasOnGround = onGround;
		lastFallSpeed = Math.max(0.0, -velocity.y);

		// A spin only while the body is in the air; once it is down the springs
		// carry the view round to the nearest upright turn (DeathMotion.restingTumble).
		boolean airborne = !onGround && !player.isInLiquid();
		if (airborne && profile.tumble() > 0.0) {
			airborneSinceDeath = true;
			tumbleRoll += rollSign * profile.tumble();
			tumblePitch += 0.45 * profile.tumble();
			maxTumble = Math.max(maxTumble, Math.abs(tumbleRoll));
		}

		double horizontal = velocity.horizontalDistance();
		boolean moving = (!onGround && !player.isInLiquid()) || horizontal > STILL_SPEED;
		stillTicks = ticks >= SETTLE_EARLIEST && !moving ? stillTicks + 1 : 0;
		// Still, and down: a faint is still folding long after its body stopped
		// sliding, and the blackout must not close on a view that is still falling.
		boolean down = ticks >= profile.collapseTicks();
		if (!settled && ((stillTicks >= STILL_TICKS && down) || ticks >= SETTLE_LATEST)) {
			settled = true;
		}
		if (settled) {
			restTicks++;
			// The nerves fire once or twice after everything else has stopped.
			for (int i = 0; i < profile.twitches(); i++) {
				if (restTicks == DeathMotion.TWITCH_TICKS[i]) {
					double side = i % 2 == 0 ? rollSign : -rollSign;
					rollVel += side * TWITCH_KICK;
					pitchVel += 0.6 * TWITCH_KICK;
				}
			}
		}

		screenTicksOld = screenTicks;
		if (settled && ticks >= screenDelay()) {
			screenTicks = Math.min(SCREEN_FADE_TICKS, screenTicks + 1);
		}
		// The screen is up: nothing more to watch, so the server may tidy the
		// body away as vanilla would have (DeathHold).
		if (screenTicks >= SCREEN_FADE_TICKS && !released) {
			watch(false);
		}
	}

	/** Takes the kind and violence the server sent for this death. */
	private static void choose() {
		kind = ClientState.deathKind();
		profile = chosenFor(kind);
	}

	private static DeathMotion.Profile chosenProfile;
	private static DeathKind chosenKind;
	private static double chosenViolence = -1.0;

	/** The profile for a kind at the current violence, cached so it compares by identity. */
	private static DeathMotion.Profile chosenFor(DeathKind k) {
		double violence = ClientState.deathViolence();
		if (chosenProfile == null || chosenKind != k || chosenViolence != violence) {
			chosenProfile = DeathMotion.profile(k, violence);
			chosenKind = k;
			chosenViolence = violence;
		}
		return chosenProfile;
	}

	private static void watch(boolean watching) {
		released = !watching;
		if (ClientPlayNetworking.canSend(DeathCameraPayload.TYPE)) {
			ClientPlayNetworking.send(new DeathCameraPayload(watching));
		}
	}

	/** Ticks the death screen waits: half the death sequence, clamped. */
	private static int screenDelay() {
		double style = ClientEffectsConfig.get().realisticDeath
				? Balance.DEATH_FADE_SECONDS_REALISTIC : Balance.DEATH_FADE_SECONDS;
		double sequenceTicks = DeathMath.fadeSeconds(style, ClientState.deathViolence()) * 20.0;
		return Mth.clamp((int) Math.round(sequenceTicks * SCREEN_DELAY_SHARE), SCREEN_DELAY_MIN, SCREEN_DELAY_MAX);
	}

	private static void thud(double fallSpeed) {
		double s = Math.clamp((fallSpeed - THUD_MIN_SPEED) / (THUD_FULL_SPEED - THUD_MIN_SPEED), 0.15, 1.0);
		if (profile.thud() <= 0.0) {
			return;   // water and lava take the landing
		}
		impact(s * profile.thud(), true);
		thuds++;
	}

	/** A landing: the view drops and bounces, and the body rocks on the springs. */
	private static void impact(double strength, boolean sound) {
		double s = Math.min(1.6, strength);
		thudVel -= THUD_DROP * s;
		rollVel += rollSign * THUD_ROLL_KICK * s;
		pitchVel += THUD_PITCH_KICK * s;
		if (sound) {
			double v = Math.min(1.0, s);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
					SoundEvents.PLAYER_BIG_FALL, (float) (0.8 - 0.1 * v), (float) (0.5 + 0.5 * v)));
		}
	}

	/** Steps the thud and angle springs; once per rendered frame, game-time delta. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0 || !active()) {
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double t = ticks + partial;
		double s = DeathMotion.settle(DeathMotion.collapse(profile, t));
		double loll = settled ? DeathMotion.loll(profile, restTicks + partial) : 0.0;
		boolean spinning = airborneSinceDeath && !settled && !wasOnGround;
		// Down: let the spin come round to the nearest upright turn.
		double tumbleR = spinning ? tumbleRoll : DeathMotion.restingTumble(tumbleRoll);
		double tumbleP = spinning ? tumblePitch : DeathMotion.restingTumble(tumblePitch);
		if (!spinning && airborneSinceDeath) {
			tumbleRoll += (tumbleR - tumbleRoll) * Math.min(1.0, 0.18 * deltaTicks);
			tumblePitch += (tumbleP - tumblePitch) * Math.min(1.0, 0.18 * deltaTicks);
		}
		double rollTarget = rollSign * (profile.roll() * s + profile.loll() * loll);
		double pitchTarget = profile.pitch() * s + Math.signum(profile.pitch()) * 0.5 * profile.loll() * loll;

		double remaining = deltaTicks;
		while (remaining > 1.0e-6) {
			double dt = Math.min(0.25, remaining);
			remaining -= dt;
			thudVel += (-THUD_STIFFNESS * thudPos - THUD_DAMPING * thudVel) * dt;
			thudPos += thudVel * dt;
			rollVel += (ANGLE_STIFFNESS * (rollTarget - rollPos) - ANGLE_DAMPING * rollVel) * dt;
			rollPos += rollVel * dt;
			pitchVel += (ANGLE_STIFFNESS * (pitchTarget - pitchPos) - ANGLE_DAMPING * pitchVel) * dt;
			pitchPos += pitchVel * dt;
		}
		if (Math.abs(thudPos) < 1.0e-4 && Math.abs(thudVel) < 1.0e-4) {
			thudPos = 0.0;
			thudVel = 0.0;
		}
	}

	/** 0 at death to 1 once the body is on the floor, on this death's curve. */
	public static double collapse(float partialTick) {
		if (!active()) {
			return 0.0;
		}
		return DeathMotion.collapse(profile, ticks + partialTick);
	}

	/**
	 * The eye height the camera eases toward, given what the entity reports.
	 * Called from {@code Camera.tick}, whose own half-per-tick ease turns the
	 * steps into a smooth drop.
	 */
	public static float eyeHeight(Entity entity, float standing) {
		if (entity != Minecraft.getInstance().player || !active()) {
			return standing;
		}
		// One tick ahead so the camera's own lag does not stretch the fall.
		return (float) Mth.lerp(DeathMotion.collapse(profile, ticks + 1.0), standing, profile.eye());
	}

	/**
	 * The roll, pitch and thud, onto a view pose stack, after vanilla's own death
	 * tilt. The springs carry the body; the thrash, the convulsion and the head
	 * snap sit on top of them unfiltered, because a spring would smooth a
	 * convulsion into a sway.
	 */
	public static void apply(PoseStack poseStack) {
		if (!active()) {
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double t = ticks + partial;
		double roll = rollPos + tumbleRoll + rollSign * DeathMotion.writheRoll(profile, t);
		double pitch = pitchPos + tumblePitch + DeathMotion.writhePitch(profile, t) + DeathMotion.jerk(profile, t);
		lastRoll = roll;
		lastPitch = pitch;
		poseStack.translate(0.0F, (float) thudPos, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) roll));
		poseStack.mulPose(Axis.XP.rotationDegrees((float) pitch));
	}

	/** Whether the first-person hand should draw. A body on the floor is not holding anything up. */
	public static boolean hideHand() {
		return active() && ticks >= 2;
	}

	/** The most the death look may darken the screen right now, 0 to 1. */
	public static double blackCap() {
		if (!active()) {
			return 1.0;
		}
		if (!settled) {
			return MOVING_BLACK_CAP;
		}
		return MOVING_BLACK_CAP + (1.0 - MOVING_BLACK_CAP) * Math.min(1.0, restTicks / BLACK_RELEASE_TICKS);
	}

	/** Opacity of the death screen, 0 to 1. Always 1 with the switch off. */
	public static float screenAlpha(float partialTick) {
		if (!active()) {
			return 1.0F;
		}
		double t = Mth.lerp(partialTick, screenTicksOld, screenTicks) / SCREEN_FADE_TICKS;
		t = Math.clamp(t, 0.0, 1.0);
		return (float) (t * t * (3.0 - 2.0 * t));
	}

	/** Whether the death screen's buttons may wake up yet. */
	public static boolean buttonsReady() {
		return !active() || screenTicks >= SCREEN_FADE_TICKS * BUTTONS_AT;
	}

	/** Whether the body has come to rest; for the client gametest. */
	public static boolean settled() {
		return settled;
	}

	/** Landings that thudded since the death; for the client gametest. */
	public static int thuds() {
		return thuds;
	}

	/** The kind of death being played; for the client gametest. */
	public static DeathKind kind() {
		return kind;
	}

	/** The roll and pitch the last frame put on the view, degrees; for the client gametest. */
	public static double lastRoll() {
		return lastRoll;
	}

	public static double lastPitch() {
		return lastPitch;
	}

	/** The furthest an airborne spin has turned the view this death, degrees; for the client gametest. */
	public static double tumbled() {
		return maxTumble;
	}

	/** Whether the server has been told it may tidy the body away; for the client gametest. */
	public static boolean released() {
		return released;
	}

	public static void reset() {
		tracking = false;
		kind = DeathKind.BLOW;
		profile = DeathMotion.profile(DeathKind.BLOW, 0.0);
		rollPos = 0.0;
		rollVel = 0.0;
		pitchPos = 0.0;
		pitchVel = 0.0;
		tumbleRoll = 0.0;
		tumblePitch = 0.0;
		airborneSinceDeath = false;
		maxTumble = 0.0;
		released = false;
		lastRoll = 0.0;
		lastPitch = 0.0;
		ticks = 0;
		stillTicks = 0;
		settled = false;
		restTicks = 0;
		screenTicks = 0;
		screenTicksOld = 0;
		rollSign = 1.0;
		wasOnGround = true;
		lastFallSpeed = 0.0;
		thudPos = 0.0;
		thudVel = 0.0;
		thuds = 0;
	}
}
