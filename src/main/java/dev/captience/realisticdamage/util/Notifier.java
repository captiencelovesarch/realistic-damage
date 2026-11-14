package dev.captience.realisticdamage.util;

import net.minecraft.world.entity.LivingEntity;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.net.InjurySoundPayload;
import dev.captience.realisticdamage.net.SoundMaterial;
import dev.captience.realisticdamage.systems.CeilingMath;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Action-bar feedback on meaningful transitions only — never per tick
 * (spec §43). All text is translatable; en_us ships in resources.
 */
public final class Notifier {
	private Notifier() {}

	public static void limbBreaks(LivingEntity player, BodyPart part) {
		message(player, "realisticdamage.msg.break." + part.id());
		injurySound(player, InjurySound.FRACTURE);
		// Not for a skull: a blow that cracks one leaves you dazed or out, not
		// yelling about it.
		if (part != BodyPart.HEAD) {
			scream(player, InjurySound.SCREAM_SHARP);
		}
	}

	/**
	 * A part is gone. Two wordings, one moment: a limb that opened a stump says
	 * so, because the bleeding is the news the player has to act on, and the old
	 * line would leave them looking for a wound the HUD is already showing. The
	 * head and torso lines are untouched — nothing bleeds on a corpse — and the
	 * stump wording is never used when {@code enableBleeding} is off, or the
	 * sentence would describe a wound that was never opened.
	 *
	 * <p>Still one sound. {@code DESTRUCTION} is the loudest cue in the mod and
	 * it already owns this frame; a second cue in the same tick only muddies it.
	 */
	public static void destroyed(LivingEntity player, BodyPart part, boolean stump) {
		message(player, (stump ? "realisticdamage.msg.stump." : "realisticdamage.msg.destroyed.") + part.id());
		injurySound(player, InjurySound.DESTRUCTION);
		// A destroyed head or torso is a death, and the dead do not scream.
		if (part.isLimb()) {
			scream(player, InjurySound.SCREAM_SEVERE);
		}
	}

	/**
	 * A blast took the limb (1.6). A different sentence from
	 * {@link #destroyed}, because "is destroyed" names the state and the player
	 * needs the cause: this is a rule with real counterplay — distance, cover,
	 * Blast Protection — and counterplay you cannot see is decoration.
	 *
	 * <p>Same cue, deliberately. {@code DESTRUCTION} is already the loudest sound
	 * in the mod, the victim's client is inside the BLAST whiteout with its ears
	 * ringing, and a bespoke amputation sound would play into deafness.
	 */
	public static void dismembered(LivingEntity player, BodyPart part) {
		message(player, "realisticdamage.msg.dismembered." + part.id());
		injurySound(player, InjurySound.DESTRUCTION);
		scream(player, InjurySound.SCREAM_SEVERE);
	}

	/** Ticks one scream holds the voice; about as long as the longest line plays. */
	private static final long SCREAM_HOLD_TICKS = 40;

	/** When each player last screamed, and how badly: {game time, ordinal}. Server thread only. */
	private static final java.util.Map<LivingEntity, long[]> lastScreams = new java.util.WeakHashMap<>();

	private static @Nullable InjurySound lastScream;

	/** The last scream {@link #scream} let through; for the gametests, like {@link #lastMessageKey}. */
	public static @Nullable InjurySound lastScream() {
		return lastScream;
	}

	/** Forgets every scream, voices included; for the gametests. */
	public static void clearLastScream() {
		lastScream = null;
		lastScreams.clear();
	}

	/** Forgets only which scream was last let through; the voices stay busy. */
	public static void clearLastScreamHook() {
		lastScream = null;
	}

	/**
	 * A player screams (1.7). The fracture cue is the bone; this is the person,
	 * and it was what the playtest said a broken limb was missing.
	 *
	 * <p>Players only — a zombie with a player's voice would be a joke — and only
	 * a player who can: the unconscious and the dead make no sound. One voice,
	 * one scream at a time: a blast that breaks three bones is one scream, not
	 * three on top of each other, and a lost limb outranks a broken one, so it
	 * may cut in on a yell that is still going but not the other way round.
	 */
	static void scream(LivingEntity victim, InjurySound sound) {
		if (!(victim instanceof ServerPlayer player) || !player.isAlive()
				|| dev.captience.realisticdamage.RDAttachments.get(player).unconscious()) {
			return;
		}
		long now = player.level().getGameTime();
		long[] last = lastScreams.get(player);
		if (last != null && now - last[0] < SCREAM_HOLD_TICKS && sound.ordinal() <= last[1]) {
			return;
		}
		lastScreams.put(player, new long[] {now, sound.ordinal()});
		lastScream = sound;
		injurySound(player, sound);
	}

	/**
	 * The ceiling came down (1.6). No sound: the break or destruction that caused
	 * it already played its cue, and the wording folds that injury into this line
	 * so the one action-bar slot carries both pieces of news instead of one
	 * overwriting the other. Losing the part outright says nothing here — the
	 * destruction line owns that moment.
	 */
	public static void ceilingLowered(LivingEntity player, BodyPart part, CeilingMath.Event event) {
		switch (event) {
			case BROKEN -> message(player, "realisticdamage.msg.ceiling.broken." + part.id());
			case DISABLED -> message(player, "realisticdamage.msg.ceiling.gave." + part.id());
			case DESTROYED -> { }
		}
	}

	private static @Nullable String lastMessageKey;

	/**
	 * The last key handed to {@link #message}, recorded before the connection
	 * guard so a gametest — whose mock players and practice dummies receive no
	 * packets at all — can still prove which line the server chose. Last write
	 * wins, which is the point rather than a limitation: sendOverlayMessage means
	 * the last line written on a frame is the only one the player reads, so this
	 * hook answers exactly the question the player would ask. Server thread only.
	 */
	public static @Nullable String lastMessageKey() {
		return lastMessageKey;
	}

	public static void clearLastMessageKey() {
		lastMessageKey = null;
	}

	public static void message(LivingEntity victim, String key) {
		// Words are for players. A mob's body (1.7) goes through every other
		// consequence in silence; nothing reads the action bar on its behalf.
		if (!(victim instanceof ServerPlayer player)) {
			return;
		}
		lastMessageKey = key;   // before the guard, or the hook sees nothing in a gametest
		// Mock players (gametests) have no connection; skip the cosmetic packet.
		if (player.connection != null) {
			player.sendOverlayMessage(Component.translatable(key));
		}
	}

	/**
	 * Can this player actually receive our sound cue? Asking the channel rather
	 * than just null-checking the connection matters on a server: a client
	 * without the mod has no receiver registered, and a gametest TestPlayer's
	 * connection goes nowhere at all.
	 */
	private static boolean canHear(ServerPlayer player) {
		return player.connection != null
				&& ServerPlayNetworking.canSend(player, InjurySoundPayload.TYPE);
	}

	/**
	 * Your own body is the loudest thing in the room; someone else's is a cue
	 * from across it. Sent as a message rather than played as a world sound so
	 * that each client picks its own style variant, and so the injured player
	 * hears it non-positionally instead of as a noise coming from their feet.
	 */
	public static void injurySound(LivingEntity victim, InjurySound sound) {
		injurySound(victim, sound, 1.0F);
	}

	/**
	 * @param strength how bad, 0 to 1 (2.0). The client plays the same event
	 *                 lighter or heavier; full strength is the sound as designed.
	 */
	public static void injurySound(LivingEntity victim, InjurySound sound, float strength) {
		SoundMaterial material = material(victim);
		if (victim instanceof ServerPlayer player && canHear(player)) {
			ServerPlayNetworking.send(player, new InjurySoundPayload(
					sound, true, player.getX(), player.getY(), player.getZ(), strength, material));
		}
		// Everyone nearby hears it from where it happened — a zombie's leg going
		// (1.7) is a cue across the cave exactly as a teammate's is.
		for (ServerPlayer other : victim.level().getEntitiesOfClass(ServerPlayer.class,
				victim.getBoundingBox().inflate(Balance.INJURY_SOUND_RADIUS),
				candidate -> candidate != victim && canHear(candidate))) {
			ServerPlayNetworking.send(other, new InjurySoundPayload(
					sound, false, victim.getX(), victim.getY(), victim.getZ(), strength, material));
		}
	}

	/** A body with nothing in it to spill sounds like bone and nothing else. */
	public static SoundMaterial material(LivingEntity body) {
		return dev.captience.realisticdamage.systems.MobAnatomy.bleeds(body) ? SoundMaterial.FLESH : SoundMaterial.DRY;
	}

	/**
	 * Plays one event to one player on demand, for {@code /rdmg sound} (2.0), so
	 * a playtest of the audio does not need a real broken leg.
	 *
	 * @param set null for the player's own sound set
	 * @param far as if it happened to someone six blocks in front of them,
	 *            instead of to their own body
	 * @return false if this player's client cannot hear the mod at all
	 */
	public static boolean soundTest(ServerPlayer player, InjurySound sound,
			dev.captience.realisticdamage.net.@Nullable SoundSet set, float strength, boolean far,
			SoundMaterial material) {
		if (!canHear(player)) {
			return false;
		}
		net.minecraft.world.phys.Vec3 at = far
				? player.getEyePosition().add(player.getLookAngle().multiply(6.0, 0.0, 6.0))
				: player.position();
		ServerPlayNetworking.send(player, new InjurySoundPayload(sound, !far, at.x, at.y, at.z,
				strength, material, InjurySoundPayload.forcing(set)));
		return true;
	}
}
