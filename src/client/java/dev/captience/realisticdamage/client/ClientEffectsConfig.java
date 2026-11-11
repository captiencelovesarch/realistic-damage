package dev.captience.realisticdamage.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side presentation and accessibility settings (spec §44-45). All
 * sensory effects can be individually disabled or attenuated without touching
 * gameplay: the server never reads any of this.
 */
public final class ClientEffectsConfig {
	public boolean showBodyHud = true;
	/** 0 = only show the body HUD when something is wrong; 1 = always visible. */
	public boolean bodyHudAlwaysVisible = false;
	/** Injury icons under the body diagram (bleeding, broken, destroyed, ...). */
	public boolean showStatusIcons = true;
	/**
	 * Exact condition figures on the inventory body panel. Off by default: the
	 * no-numbers rule is what keeps the HUD readable, and it is only on the
	 * dedicated detail screen that numbers earn their place. Blood and
	 * consciousness stay hidden even here — they are named in bands, never shown
	 * as figures, because knowing them exactly would remove the fear they exist
	 * to create.
	 */
	public boolean showNumericStats = false;
	/**
	 * Marks on the body panel and the HUD for regions that have been broken or
	 * lost before (1.6). Information, not atmosphere — kept out of the presets
	 * and out of {@link #disableAllEffects()} like the injury icons, because it
	 * is a fact about your body rather than something happening to your senses.
	 *
	 * <p>Absent from a 1.5 config file, which Gson answers with the initialiser
	 * above, so there is nothing for {@link #migrate()} to do either.
	 *
	 * <p>The matching seam on the player model is paint on a body, so it rides
	 * Blood on Bodies as well as this one: the panic button silences that pass
	 * without ever having to learn that scars exist.
	 */
	public boolean showScars = true;
	/**
	 * How an injured limb is carried on the player model (1.6): a broken arm
	 * held against the ribs, a bad leg turned out and taking a shorter step.
	 *
	 * <p>Information, not atmosphere, and that is why it is here rather than in
	 * the Blood section and why {@link #disableAllEffects()} leaves it alone.
	 * The panic button exists for things happening to your <em>senses</em> — a
	 * swimming screen, a ringing ear, blood on the lens — and a limb held wrong
	 * is a fact about a body, the same kind of statement as the injury icons and
	 * the scar marks, which are excluded for the same reason. There is a second
	 * reason peculiar to this one: the mob AI prefers targets that are visibly
	 * limping, so this pose is the only warning a bystander gets that the player
	 * beside them is about to be singled out. A preset that silenced it would be
	 * hiding information the game is acting on.
	 *
	 * <p>On by default: it is the model's only statement about a break, and a
	 * fracture that shows nothing is the bug this setting exists to close.
	 *
	 * <p>Its own switch rather than riding {@link #showScars}, per the rule that
	 * every effect keeps its own control, and because the two say opposite
	 * things — a scar is history, a posture is now.
	 *
	 * <p>A plain boolean. Absent from a 1.5 config file Gson leaves the
	 * initialiser standing, which is already the right answer, so
	 * {@link #migrate()} has nothing to say about it.
	 */
	public boolean showInjuryPose = true;
	/**
	 * The white line at the bottom of the screen — "Your right leg breaks!"
	 * (1.6). Every other visible layer of this mod already had a control and
	 * this one did not, which made the output that uses <em>words</em> the only
	 * output a player could not refuse.
	 *
	 * <p>On by default: it is what everybody has today, and text switched off by
	 * default would hide the mod from a new player entirely.
	 *
	 * <p>A plain boolean, not a boxed one. Absent from a 1.5 config file Gson
	 * leaves the initialiser standing, which is already the right answer, so
	 * {@link #migrate()} has nothing to say about it — the same argument
	 * {@link #showScars} makes just above.
	 *
	 * <p>Kept out of the presets and out of {@link #disableAllEffects()} on the
	 * {@link #showStatusIcons} and {@link #showScars} precedent — information,
	 * not atmosphere — and for one reason of its own: cancelling the overlay
	 * cancels the narrator with it, so for a screen-reader player this line is
	 * the injury cue, and no preset should take it away unasked.
	 *
	 * <p>The rescue acknowledgements are outside this switch by construction
	 * rather than by a second control; see {@code InjuryMessages}.
	 */
	public boolean showInjuryMessages = true;

	public double vignetteIntensity = 1.0;
	public double sensitivityReductionIntensity = 1.0;
	/**
	 * How much the view keeps gliding after your hand stops (see MouseInertia).
	 * Separate from the sensitivity reduction above because they are different
	 * complaints: one is slow aim, the other is sloppy aim, and wanting one
	 * without the other is reasonable.
	 *
	 * <p>Boxed so that "absent from the file" is distinguishable from "set to
	 * zero". Before 1.1 this effect was governed by the sensitivity slider, so a
	 * config written by 1.0 has no key here — and defaulting it to 1.0 would
	 * switch the melty camera back on for someone who had deliberately turned it
	 * off. {@link #migrate()} seeds it from the old control instead.
	 */
	public Double lookInertiaIntensity;

	/** Never null after load; use this rather than the field. */
	public double lookInertia() {
		return lookInertiaIntensity == null ? sensitivityReductionIntensity : lookInertiaIntensity;
	}
	public double afterimageIntensity = 1.0;
	public double exposureIntensity = 1.0;
	public double ringingVolume = 1.0;
	/** true = synthesised tinnitus tone, false = the resonant bell ring. */
	public boolean tinnitusTone = true;
	/** True low-pass muffling of world audio (falls back to ducking if EFX is absent). */
	public double mufflingIntensity = 1.0;
	/** Flash, camera kick and thud when a heavy blow lands — and the slam of a landing. */
	public double impactIntensity = 1.0;
	/**
	 * How far your feet wander off your input as consciousness fades (1.4).
	 *
	 * <p>Its own control rather than part of the look sliders, and boxed for the
	 * same reason {@link #lookInertiaIntensity} is: this is the only effect in the
	 * mod that changes where the player physically ends up, so someone who is fine
	 * with a swimming screen may still want their footing left alone — and a
	 * config written before 1.4 must not have it switched on underneath them at
	 * full strength. {@link #migrate()} seeds it from the inertia control, which
	 * is the closest existing statement of "I am happy for my controls to lie to
	 * me".
	 */
	public Double stumbleIntensityValue;

	/** Never null after load; use this rather than the field. */
	public double stumbleIntensity() {
		return stumbleIntensityValue == null ? lookInertia() : stumbleIntensityValue;
	}
	/**
	 * The master control for everything blood (1.5): drops, the marks they leave,
	 * blood on your own body and blood on the camera.
	 *
	 * <p>Boxed and migrated like the controls above, but for a subtler reason.
	 * Blood is not a new effect layered onto something that already worked — it is
	 * the fix for bleeding having been effectively invisible, so a 1.4 config
	 * ought to receive it. What it must not do is switch itself on for someone who
	 * had deliberately turned the sensory layer off, and "turned everything off"
	 * is exactly the state {@link #disableAllEffects()} leaves behind.
	 * {@link #migrate()} therefore seeds this from the existing controls rather
	 * than defaulting it, so a silent config comes back on and a silenced one
	 * stays silent.
	 */
	public Double bloodIntensityValue;

	/** Never null after load; use this rather than the field. */
	public double bloodIntensity() {
		return bloodIntensityValue == null ? 1.0 : bloodIntensityValue;
	}

	/** Marks left on the world: the trail behind you and the pool under you. */
	public double bloodStainIntensity = 1.0;
	/** Droplets that land on the camera, run down it and dry. */
	public double bloodLensIntensity = 1.0;
	/** Blood on the player model, per injured region — yours and everyone else's. */
	public double bloodBodyIntensity = 1.0;
	/**
	 * Brighter, wetter, and more of it. The same split as
	 * {@link #visceralInjurySounds}, and for the same reason: two players on one
	 * server are allowed to disagree about how visceral their game is, because
	 * none of it is a gameplay signal the other can be denied.
	 */
	public boolean visceralBlood = false;

	/**
	 * The dying fade (1.5): the heart running down, the colour finishing its
	 * drain, the world going quiet under the death screen. Boxed and migrated
	 * like the other post-1.0 controls, seeded from the vignette because that is
	 * the closest existing statement of how much screen-covering drama the player
	 * wants — and it is zero for anyone who pressed the panic button.
	 */
	public Double deathIntensityValue;

	/** Never null after load; use this rather than the field. */
	public double deathIntensity() {
		return deathIntensityValue == null ? 1.0 : deathIntensityValue;
	}

	/**
	 * Which death the fade plays (1.5). False is the cinematic one: five seconds,
	 * a red pulse, and a screen that dims but stays legible. True is the
	 * realistic one: vision tunnels out to true black just past the middle of a
	 * longer fade, the heart keeps beating in the dark after it — hearing is the
	 * last sense to go — and the end is genuinely black and silent. Selected by
	 * the Arcade and Grounded presets respectively, overridable on its own,
	 * exactly like the muted/visceral sound split.
	 */
	public boolean realisticDeath = false;

	/**
	 * The camera stays with the body after death (1.7): knockback and gravity
	 * keep carrying it, the view collapses to the floor, the blackout waits for
	 * the body to come to rest and the death screen fades in. See
	 * {@link DeathCamera}. On by default; a plain boolean, so a file from before
	 * it existed picks up the default.
	 */
	public boolean deathCamera = true;

	/**
	 * How other people's bodies fall (active ragdolls): {@code FULL} is the
	 * physics body (six jointed parts that land, slide and drape the way the
	 * world lets them), {@code SIMPLE} the 1.7 plank with swinging limbs, which
	 * also stands in for full bodies past {@link #ragdollLimit} or far away, and
	 * {@code OFF} vanilla's tip onto the side. Absent from an older file, Gson
	 * leaves the initialiser.
	 */
	public RagdollMode ragdolls = RagdollMode.FULL;

	/** At most this many full bodies at once; the rest fall the simple way. */
	public int ragdollLimit = 16;

	/**
	 * How long a ragdoll stays after death, seconds (active ragdolls phase 4).
	 * The server removes a body after five; past that this client keeps it,
	 * still lying where it fell, then lets it sink into the ground. 5 is
	 * vanilla's timing.
	 */
	public int bodiesStay = 30;

	/**
	 * Death View (active ragdolls phase 4): true rides the camera on your own
	 * ragdoll's head (see {@link RagdollView}); false is the classic death
	 * camera, a view that collapses, rolls and thuds on its own springs. Never
	 * the ragdoll in VR.
	 */
	public boolean ownRagdoll = true;

	/**
	 * Losing your head (2.0): true, the view goes with the head as it flies and
	 * rolls, and you watch your body go down; false cuts straight to black.
	 * Needs Death View on Ragdoll, and is never the head in VR.
	 */
	public boolean decapitationView = true;

	/**
	 * Knocked down without dying (active ragdolls phase 4): the body falls as a
	 * ragdoll and gets back up, instead of dropping into the prone pose. Your
	 * own body only with Death View on Ragdoll, and never in VR.
	 */
	public boolean knockdownRagdolls = true;

	/**
	 * Hit and still standing (active ragdolls phase 5): the struck part jerks
	 * with the blow and the body rocks, on top of its animation.
	 */
	public boolean hitFlinches = true;

	/**
	 * Death Stumble (stumbling deaths, IK phases 1 and 2): a body killed by a
	 * blow, an arrow or a bleed-out, with a mind and two legs left, stays on its
	 * feet for a moment, stepping after its balance as it weakens, and then goes
	 * down. {@code GROUNDED} is the physical one (the hit jolts what it struck
	 * and hardly moves the body, so only a body that was going somewhere has much
	 * to catch); {@code ARCADE} the cinematic one (shoved back along the shot and
	 * turned toward the shooter, a planned few clean steps, then a finisher, and
	 * a headshot hangs a beat before it drops); {@code OFF} is the stagger from
	 * before. Full ragdolls only. The Arcade and Grounded presets set it. Each
	 * player picks their own: the server never decides this.
	 */
	public DeathStumble deathStumble = DeathStumble.GROUNDED;

	/** Never null (a file naming a style this version does not have reads as null); use this rather than the field. */
	public DeathStumble deathStumble() {
		return deathStumble == null ? DeathStumble.GROUNDED : deathStumble;
	}

	public enum DeathStumble {
		OFF,
		GROUNDED,
		ARCADE
	}

	public enum RagdollMode {
		OFF,
		SIMPLE,
		FULL
	}

	/**
	 * The blast taking your hearing (1.7): the world cut to a dull thud under a
	 * ring for seconds after a nearby explosion, plus the dust and the shake.
	 * Boxed and migrated: seeded from Muffling, the closest existing statement
	 * of "let the mod take my hearing", so a silenced config stays silent.
	 */
	public Double blastDeafnessValue;

	/** Never null after load; use this rather than the field. */
	public double blastDeafness() {
		return blastDeafnessValue == null ? 1.0 : blastDeafnessValue;
	}

	/**
	 * What a concussion does to your senses (1.7): the room spinning, glare in
	 * bright light, the wince at a loud noise, the throb of a flare. The
	 * gameplay (no sprint when flaring, the retch, the slower legs) is the
	 * server's and is not this control's to take away; the drift of your feet
	 * in a spell still answers to Stumble as well. Boxed and migrated from the
	 * vignette.
	 */
	public Double concussionEffectsValue;

	/** Never null after load; use this rather than the field. */
	public double concussionEffects() {
		return concussionEffectsValue == null ? 1.0 : concussionEffectsValue;
	}

	/**
	 * The last two hearts (1.7): the tunnel and the narrowed hearing of the
	 * adrenaline surge, the shaking hands and grey edges of the crash after it.
	 * Boxed and migrated from the vignette.
	 */
	public Double lowHealthEffectsValue;

	/** Never null after load; use this rather than the field. */
	public double lowHealthEffects() {
		return lowHealthEffectsValue == null ? 1.0 : lowHealthEffectsValue;
	}

	/**
	 * Your own breathing (1.7): the gasp when you surface from too long under,
	 * the heaving chest after the adrenaline wears off. Boxed and migrated from
	 * the heartbeat volume, its nearest neighbour.
	 */
	public Double breathingVolumeValue;

	/** Never null after load; use this rather than the field. */
	public double breathingVolume() {
		return breathingVolumeValue == null ? 1.0 : breathingVolumeValue;
	}

	/**
	 * The controllers' share of the body, in VR (1.7 alpha.10): the heartbeat in
	 * both palms, and the thud of a blow, a landing or a blast — which in flat
	 * play is a kick to the view, and in VR may not be. Boxed and migrated from
	 * Impact, its nearest neighbour, so a silenced config stays silent.
	 */
	public Double vrHapticsValue;

	/** Never null after load; use this rather than the field. */
	public double vrHaptics() {
		return vrHapticsValue == null ? 1.0 : vrHapticsValue;
	}

	/** Fracture, destruction and collapse audio. */
	public double injurySoundVolume = 1.0;

	/**
	 * Players yell when a bone breaks and scream when a limb goes (1.7), heard
	 * at the Injury Volume. Yours and everyone else's: the switch is about what
	 * this client plays, like every other sound setting here. On by default.
	 */
	public boolean screams = true;
	/** Real colour drain through vanilla's post-processing. */
	public double desaturationIntensity = 1.0;
	/** Real world-space warping, riding vanilla's nausea distortion. */
	public double distortionIntensity = 1.0;
	public double heartbeatVolume = 1.0;
	/** Grounded preset: the screen goes completely black while unconscious. */
	public boolean fullBlackUnconscious = false;
	/**
	 * The camera sways and sags as consciousness fades — on the <em>aim</em>,
	 * which is what makes it the one camera effect that actually costs control.
	 * Off everywhere but Grounded until 1.6.2; now on at a low level by default,
	 * the author's call, because a mod about losing consciousness should let
	 * the head go a little for everyone. {@link #cameraDriftSeeded} carries an
	 * existing file across: a plain zero in a 1.6.1 file is the old default,
	 * not a choice, and is raised once — unless the whole sensory layer was
	 * silenced, in which case it stays silent.
	 */
	public double cameraDriftIntensity = 0.35;
	public boolean cameraDriftSeeded = false;
	/**
	 * The picture trailing the aim on fast turns (1.6.2); see
	 * {@link CameraMotion}. Its own control rather than part of Head Sway: this
	 * is the most motion-sickness-prone thing the camera does, and a player who
	 * wants the horizon to sway may still want the world to stop where their
	 * hand stopped. Boxed and migrated like {@link #cameraSwayIntensityValue}.
	 */
	public Double cameraLagIntensityValue;

	/** Never null after load; use this rather than the field. */
	public double cameraLag() {
		return cameraLagIntensityValue == null ? 0.7 : cameraLagIntensityValue;
	}
	/**
	 * The horizon tilting, the head nodding into a microsleep, the slump of
	 * collapse (1.6.1) — all of it on the view only, never on the aim. See
	 * {@link CameraMotion}. Separate from Camera Drift above because they answer
	 * different complaints: drift moves the crosshair, this moves the picture,
	 * and a player who cannot stand having their aim touched can still have
	 * this. On by default at a restrained level; the Grounded preset takes it
	 * all the way.
	 *
	 * <p>Boxed and migrated like the other post-1.0 controls: seeded from the
	 * vignette so a panic-buttoned config stays silent across the upgrade.
	 */
	public Double cameraSwayIntensityValue;

	/** Never null after load; use this rather than the field. */
	public double cameraSway() {
		return cameraSwayIntensityValue == null ? 0.7 : cameraSwayIntensityValue;
	}
	/**
	 * Which set of injury sounds to play: the muted set that sits inside
	 * vanilla's palette, or the visceral set that does not. Presentation only,
	 * so each player picks their own — the server never decides this.
	 */
	public boolean visceralInjurySounds = false;

	/** Punchy and readable — effects present but never obscuring. */
	public void applyArcadePreset() {
		vignetteIntensity = 0.85;
		sensitivityReductionIntensity = 0.5;
		lookInertiaIntensity = 0.4;
		stumbleIntensityValue = 0.35;
		afterimageIntensity = 0.6;
		exposureIntensity = 0.6;
		ringingVolume = 0.8;
		heartbeatVolume = 1.0;
		fullBlackUnconscious = false;
		cameraDriftIntensity = 0.35;
		cameraSwayIntensityValue = 0.55;
		cameraLagIntensityValue = 0.5;
		desaturationIntensity = 0.7;
		distortionIntensity = 0.5;
		visceralInjurySounds = false;
		// Blood is information here, not atmosphere: kept legible but tidied up,
		// with the lens droplets pulled back hardest because they are the layer
		// that actually sits between you and the game.
		bloodIntensityValue = 0.85;
		bloodStainIntensity = 0.7;
		bloodLensIntensity = 0.45;
		bloodBodyIntensity = 0.8;
		visceralBlood = false;
		deathIntensityValue = 0.75;
		realisticDeath = false;
		blastDeafnessValue = 0.7;
		concussionEffectsValue = 0.7;
		lowHealthEffectsValue = 0.8;
		breathingVolumeValue = 0.8;
		deathStumble = DeathStumble.ARCADE;
	}

	/** Heavier immersion: full blackout, camera drift, everything at full strength. */
	public void applyGroundedPreset() {
		vignetteIntensity = 1.0;
		sensitivityReductionIntensity = 1.0;
		lookInertiaIntensity = 1.0;
		stumbleIntensityValue = 1.0;
		afterimageIntensity = 1.0;
		exposureIntensity = 1.0;
		ringingVolume = 1.0;
		heartbeatVolume = 1.0;
		fullBlackUnconscious = true;
		cameraDriftIntensity = 1.0;
		cameraSwayIntensityValue = 1.0;
		cameraLagIntensityValue = 1.0;
		desaturationIntensity = 1.0;
		distortionIntensity = 1.0;
		visceralInjurySounds = true;
		bloodIntensityValue = 1.0;
		bloodStainIntensity = 1.0;
		bloodLensIntensity = 1.0;
		bloodBodyIntensity = 1.0;
		visceralBlood = true;
		deathIntensityValue = 1.0;
		realisticDeath = true;
		blastDeafnessValue = 1.0;
		concussionEffectsValue = 1.0;
		lowHealthEffectsValue = 1.0;
		breathingVolumeValue = 1.0;
		deathStumble = DeathStumble.GROUNDED;
	}

	/**
	 * The panic button: every sensory effect off, gameplay untouched. Lives here
	 * rather than in the screen so that adding an effect cannot quietly leave it
	 * out of the one control that promises to silence everything.
	 */
	public void disableAllEffects() {
		vignetteIntensity = 0.0;
		sensitivityReductionIntensity = 0.0;
		lookInertiaIntensity = 0.0;
		stumbleIntensityValue = 0.0;
		afterimageIntensity = 0.0;
		exposureIntensity = 0.0;
		ringingVolume = 0.0;
		heartbeatVolume = 0.0;
		cameraDriftIntensity = 0.0;
		cameraDriftSeeded = true;
		cameraSwayIntensityValue = 0.0;
		cameraLagIntensityValue = 0.0;
		desaturationIntensity = 0.0;
		distortionIntensity = 0.0;
		mufflingIntensity = 0.0;
		impactIntensity = 0.0;
		injurySoundVolume = 0.0;
		bloodIntensityValue = 0.0;
		deathIntensityValue = 0.0;
		deathCamera = false;
		blastDeafnessValue = 0.0;
		concussionEffectsValue = 0.0;
		lowHealthEffectsValue = 0.0;
		breathingVolumeValue = 0.0;
		vrHapticsValue = 0.0;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientEffectsConfig instance = new ClientEffectsConfig();

	public static ClientEffectsConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("realisticdamage-client.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				instance = GSON.fromJson(Files.readString(path), ClientEffectsConfig.class);
				if (instance == null) {
					instance = new ClientEffectsConfig();
				}
			} catch (IOException | JsonSyntaxException e) {
				RealisticDamage.LOGGER.error("Could not read {}, using defaults", path, e);
				instance = new ClientEffectsConfig();
			}
		}
		instance.migrate();
		save();
	}

	/**
	 * Carries settings forward when a control is split or renamed. An effect the
	 * player switched off must stay off across an update — that is the whole
	 * promise of the accessibility screen, and silently restoring one because a
	 * new JSON key defaulted to full strength would break it.
	 */
	public void migrate() {
		if (lookInertiaIntensity == null) {
			lookInertiaIntensity = sensitivityReductionIntensity;
		}
		if (stumbleIntensityValue == null) {
			stumbleIntensityValue = lookInertia();
		}
		if (bloodIntensityValue == null) {
			// The strongest surviving statement of "show me that I am hurt". A
			// config with the whole sensory layer at zero seeds blood at zero and
			// the panic button keeps its promise across the upgrade; anything else
			// gets the effect it was missing.
			bloodIntensityValue = Math.max(vignetteIntensity, impactIntensity);
		}
		if (deathIntensityValue == null) {
			deathIntensityValue = vignetteIntensity;
		}
		if (cameraSwayIntensityValue == null) {
			// Zero for a silenced config, a restrained default for anyone else.
			cameraSwayIntensityValue = vignetteIntensity <= 0.0 ? 0.0 : 0.7 * Math.min(1.0, vignetteIntensity);
		}
		if (cameraLagIntensityValue == null) {
			cameraLagIntensityValue = vignetteIntensity <= 0.0 ? 0.0 : 0.7 * Math.min(1.0, vignetteIntensity);
		}
		if (blastDeafnessValue == null) {
			blastDeafnessValue = Math.min(1.0, mufflingIntensity);
		}
		if (concussionEffectsValue == null) {
			concussionEffectsValue = Math.min(1.0, vignetteIntensity);
		}
		if (lowHealthEffectsValue == null) {
			lowHealthEffectsValue = Math.min(1.0, vignetteIntensity);
		}
		if (breathingVolumeValue == null) {
			breathingVolumeValue = Math.min(1.0, heartbeatVolume);
		}
		if (vrHapticsValue == null) {
			vrHapticsValue = Math.min(1.0, impactIntensity);
		}
		if (!cameraDriftSeeded) {
			// One-time: a file from before 1.6.2 holds the old zero default. It
			// is raised to the new default unless the layer was silenced, and
			// never touched again — a later zero is the player's own.
			if (cameraDriftIntensity <= 0.0 && vignetteIntensity > 0.0) {
				cameraDriftIntensity = 0.35;
			}
			cameraDriftSeeded = true;
		}
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(instance));
		} catch (IOException e) {
			RealisticDamage.LOGGER.error("Could not write {}", path(), e);
		}
	}
}
