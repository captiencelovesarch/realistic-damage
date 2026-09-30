package dev.captience.realisticdamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DeathMath;
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
 */
public final class DeathCamera {
	private DeathCamera() {}

	/** Ticks for the eye to drop from standing to the floor. */
	private static final double COLLAPSE_TICKS = 15.0;
	/** Eye height of a body lying on the ground, blocks. */
	private static final float LYING_EYE = 0.3F;
	/** How far the view rolls onto its side, degrees. Less than lying flat would be; that read as a bug. */
	private static final double COLLAPSE_ROLL = 32.0;
	/** ...and the head going limp, degrees of pitch down. */
	private static final double COLLAPSE_SAG = 10.0;

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
		}
		ticks++;

		// The void has no floor to land on: stop the body a little below the
		// world rather than let it fall for as long as the death screen is up.
		double floor = player.level().getMinY() - 64.0;
		if (player.getY() < floor) {
			player.setPos(player.getX(), floor, player.getZ());
			player.setDeltaMovement(Vec3.ZERO);
		}

		Vec3 velocity = player.getDeltaMovement();
		// Fall to the side the blow pushed you, once the knockback has arrived.
		if (ticks == 3) {
			Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
			rollSign = velocity.x * right.x + velocity.z * right.z >= 0.0 ? 1.0 : -1.0;
		}

		boolean onGround = player.onGround();
		if (onGround && !wasOnGround && lastFallSpeed > THUD_MIN_SPEED) {
			thud(lastFallSpeed);
		}
		wasOnGround = onGround;
		lastFallSpeed = Math.max(0.0, -velocity.y);

		double horizontal = velocity.horizontalDistance();
		boolean moving = (!onGround && !player.isInLiquid()) || horizontal > STILL_SPEED;
		stillTicks = ticks >= SETTLE_EARLIEST && !moving ? stillTicks + 1 : 0;
		if (!settled && (stillTicks >= STILL_TICKS || ticks >= SETTLE_LATEST)) {
			settled = true;
		}
		if (settled) {
			restTicks++;
		}

		screenTicksOld = screenTicks;
		if (settled && ticks >= screenDelay()) {
			screenTicks = Math.min(SCREEN_FADE_TICKS, screenTicks + 1);
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
		thudVel -= THUD_DROP * s;
		thuds++;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
				SoundEvents.PLAYER_BIG_FALL, (float) (0.8 - 0.1 * s), (float) (0.5 + 0.5 * s)));
	}

	/** Steps the thud spring; once per rendered frame, game-time delta. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0 || (thudPos == 0.0 && thudVel == 0.0)) {
			return;
		}
		double remaining = deltaTicks;
		while (remaining > 1.0e-6) {
			double dt = Math.min(0.25, remaining);
			remaining -= dt;
			thudVel += (-THUD_STIFFNESS * thudPos - THUD_DAMPING * thudVel) * dt;
			thudPos += thudVel * dt;
		}
		if (Math.abs(thudPos) < 1.0e-4 && Math.abs(thudVel) < 1.0e-4) {
			thudPos = 0.0;
			thudVel = 0.0;
		}
	}

	/** 0 at death to 1 once the body is on the floor, eased in: the knees go, then everything. */
	public static double collapse(float partialTick) {
		if (!active()) {
			return 0.0;
		}
		double t = Math.clamp((ticks + partialTick) / COLLAPSE_TICKS, 0.0, 1.0);
		return t * t;
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
		double t = Math.clamp((ticks + 1.0) / COLLAPSE_TICKS, 0.0, 1.0);
		return (float) Mth.lerp(t * t, standing, LYING_EYE);
	}

	/** The roll, sag and thud, onto a view pose stack, after vanilla's own death tilt. */
	public static void apply(PoseStack poseStack) {
		if (!active()) {
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double c = collapse(partial);
		// Smoothstep on top of the ease-in, so the roll lands rather than stops.
		double settle = c * c * (3.0 - 2.0 * c);
		poseStack.translate(0.0F, (float) thudPos, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) (rollSign * COLLAPSE_ROLL * settle)));
		poseStack.mulPose(Axis.XP.rotationDegrees((float) (COLLAPSE_SAG * settle)));
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

	public static void reset() {
		tracking = false;
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
