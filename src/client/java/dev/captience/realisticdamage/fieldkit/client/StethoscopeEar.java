package dev.captience.realisticdamage.fieldkit.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.RulesView;
import dev.captience.realisticdamage.fieldkit.medicine.Medicine;
import dev.captience.realisticdamage.fieldkit.medicine.StethoscopeItem;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.Cardio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What the stethoscope hears (2.0, phase 4 round 2). While the local player
 * holds it on a body, that body's heart beats in their ears: the core's own
 * heart for themselves, and for anyone else a heart run here from what every
 * client already sees of them (blood, consciousness, health), the same way
 * the core times a stranger's spurting artery. Sound only.
 *
 * <p>A beat is the core's heartbeat thump, pitched as the core pitches its
 * own: an ectopic beat is a weak early tap, the one after the pause a hard
 * low thump. Unlike the core's, a calm heart is heard too: that's what the
 * stethoscope is for. A dead one is silence.
 */
public final class StethoscopeEar {
	private StethoscopeEar() {}

	private static int heartOf = -1;
	private static Cardio heart;
	/** Beats played since the game started: for the client game test. */
	public static int beatsHeard;

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || !player.isUsingItem()
				|| !player.getUseItem().is(Medicine.STETHOSCOPE) || !RulesView.on(Half.MEDICINE)) {
			heartOf = -1;
			heart = null;
			return;
		}
		Entity target = client.level.getEntity(StethoscopeItem.listeningTo);
		if (!(target instanceof LivingEntity body) || !body.isAlive() || body.isRemoved()
				|| body.distanceTo(player) > Balance.TREATMENT_REACH + 1.0) {
			return;   // nothing to hear, or too far to hear it
		}
		boolean fired;
		double amplitude;
		double distress;
		if (body == player) {
			fired = ClientState.beatJustFired();
			amplitude = ClientState.beatAmplitude();
			distress = ClientState.distress();
		} else {
			BodySnapshot snapshot = body.getAttached(RDAttachments.SNAPSHOT);
			if (snapshot == null) {
				return;
			}
			if (heart == null || heartOf != body.getId()) {
				heart = new Cardio();
				heartOf = body.getId();
			}
			double effective = Vitals.effectiveConsciousness(snapshot.consciousness(), snapshot.concussionGrade(),
					snapshot.concussionFlare());
			double maxHealth = body.getMaxHealth();
			double health = maxHealth <= 0.0 ? 1.0 : body.getHealth() / maxHealth;
			double fromHealth = Vitals.distressFromHealth(health);
			double fromBlood = Vitals.distressFromBlood(snapshot.blood());
			double fromMind = Vitals.distressFromMind(effective);
			heart.tick(fromHealth, fromBlood, fromMind, ClientState.breath());
			fired = heart.beatJustFired();
			amplitude = heart.beatAmplitude();
			distress = Math.max(fromHealth, Math.max(fromBlood, fromMind));
		}
		if (!fired) {
			return;
		}
		float pitch = (float) Math.clamp((0.85 + 0.35 * distress) / Math.max(0.35, Math.pow(amplitude, 0.45)), 0.5, 2.0);
		float volume = (float) Math.clamp(Balance.STETHOSCOPE_VOLUME * amplitude, 0.05, 1.0);
		client.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(SoundEvents.WARDEN_HEARTBEAT, pitch, volume));
		beatsHeard++;
	}
}
