package dev.captience.realisticdamage.client;

import java.util.HashMap;
import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What a concussion does to the senses (1.7 rework). The server owns the
 * injury — grade, flare, when a vertigo spell starts and stops, when the
 * stomach goes — and sends it in the snapshot; this draws it.
 *
 * <ul>
 * <li><b>Vertigo</b>: the horizon rolls one way and keeps rolling, the world
 * swims, and your feet drift off your input in the same direction, so you are
 * steering against the room. Spells alternate direction.</li>
 * <li><b>Light</b>: bright places glare and you squint — the edges of the view
 * close in from above and below — and looking toward the sun is the worst of
 * it. Darkness is relief, and on the server it is also where the flare falls
 * fastest.</li>
 * <li><b>Noise</b>: a loud sound near you (an explosion, a hit, thunder, an
 * anvil) makes you wince: a flinch, a squint, a white spike and a moment of
 * seeing double.</li>
 * <li><b>The flare</b>: a dark throb at the edges on every heartbeat, the
 * headache made visible, growing with how hard you are pushing.</li>
 * <li><b>Retching</b>: the head goes down and what was in the stomach comes
 * out — drawn for everyone who is retching, not only you.</li>
 * </ul>
 *
 * <p>Everything here rides the Concussion Symptoms control; the foot drift
 * also answers to Stumble and the view roll to Head Sway, because those are
 * the controls a player has already used to say how much of that they want.
 */
public final class ConcussionEffects {
	private ConcussionEffects() {}

	private static final net.minecraft.resources.Identifier VIGNETTE =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/injury_vignette.png");

	/** Vertigo envelope, 0 to 1, and which way the room is going (+1 right, -1 left). */
	private static double vertigo;
	private static double direction = 1.0;
	private static double time;
	/** The wince at a loud noise: 1 on the instant, decaying fast. */
	private static double wince;
	private static long lastWinceTick = -1_000_000L;
	/** Brightness pain, smoothed; and the squint it causes. */
	private static double glare;
	private static double squint;
	/** Head bowed to retch. */
	private static double retch;
	/** When each retching body started, so its vomit comes out in the first second, not all spell long. */
	private static final Map<Integer, Long> retchStarted = new HashMap<>();

	/** Readouts for the client gametest. */
	private static long winces;
	private static long bileDrops;

	public static long winces() {
		return winces;
	}

	public static long bileDrops() {
		return bileDrops;
	}

	/** Brightness pain and the squint it causes, 0 to 1; for the client gametest. */
	public static double glare() {
		return glare;
	}

	public static double squint() {
		return squint;
	}

	public static double vertigoEnvelope() {
		return vertigo;
	}

	private static double intensity() {
		return ClientEffectsConfig.get().concussionEffects();
	}

	private static double gradeWeight() {
		return switch (ClientState.concussionGrade()) {
			case 3 -> 1.0;
			case 2 -> 0.8;
			case 1 -> 0.55;
			default -> 0.0;
		};
	}

	/** Registered once with the sound manager; hears every sound the game starts. */
	public static final SoundEventListener NOISE = new SoundEventListener() {
		@Override
		public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
			hear(sound, range);
		}
	};

	private static void hear(SoundInstance sound, float range) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !ClientState.concussed() || !ClientState.active()) {
			return;
		}
		SoundSource source = sound.getSource();
		// Music, the menu, voice chat and the mod's own cues (heartbeat, ring,
		// breathing all play as AMBIENT) are not "a loud noise nearby".
		if (source == SoundSource.MUSIC || source == SoundSource.RECORDS || source == SoundSource.AMBIENT
				|| source == SoundSource.UI || source == SoundSource.VOICE) {
			return;
		}
		// Your own body's sounds — your grunt when hit, your own swing, the
		// mod's fracture and scream cues — are not a noise that startles you;
		// and a blow that has just landed has its own, bigger reaction, which a
		// wince on top of would only blur.
		net.minecraft.resources.Identifier id = sound.getIdentifier();
		if (id.getNamespace().equals(dev.captience.realisticdamage.RealisticDamage.MOD_ID)
				|| id.getPath().startsWith("entity.player.")) {
			return;
		}
		if (ImpactEffect.shock() > 0.01 || LandingEffect.shock() > 0.01) {
			return;
		}
		double volume;
		try {
			volume = sound.getVolume();
		} catch (RuntimeException unresolved) {
			return;
		}
		double heard = volume;
		if (!sound.isRelative() && sound.getAttenuation() != SoundInstance.Attenuation.NONE
				&& Float.isFinite(range) && range > 0.0F) {
			Vec3 at = client.gameRenderer.mainCamera().position();
			double distance = at.distanceTo(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
			heard = volume * Math.max(0.0, 1.0 - distance / range);
		}
		if (heard < 0.65) {
			return;
		}
		long now = client.level.getGameTime();
		if (now - lastWinceTick < 25) {
			return;   // one wince per noise, not one per sound in a clatter
		}
		lastWinceTick = now;
		double strength = Math.min(1.0, (heard - 0.45) / 0.55) * gradeWeight();
		wince = Math.max(wince, strength);
		winces++;
		CameraMotion.flinch(strength * intensity());
	}

	/** Once per client tick: light, and the vomit of anybody retching nearby. */
	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			retchStarted.clear();
			return;
		}
		tickRetchers(level);
		double target = 0.0;
		if (ClientState.concussed() && ClientState.active()) {
			target = brightnessPain(level, player) * gradeWeight() * (0.6 + 0.4 * ClientState.flare());
		}
		glare += (target - glare) * (target > glare ? 0.12 : 0.05);
	}

	/** How much the light right now hurts, 0 to 1, before grade and flare. */
	private static double brightnessPain(ClientLevel level, LocalPlayer player) {
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		int brightness = level.getMaxLocalRawBrightness(eye);
		double light = Math.clamp((brightness - 10) / 5.0, 0.0, 1.0) * 0.55;
		double sun = 0.0;
		if (level.dimensionType().hasSkyLight() && level.canSeeSky(eye)) {
			double angle = Math.toRadians(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, eye));
			Vec3 toSun = new Vec3(-Math.sin(angle), Math.cos(angle), 0.0);
			if (toSun.y > 0.0) {
				double facing = player.getViewVector(1.0F).dot(toSun);
				sun = Math.clamp((facing - 0.82) / 0.18, 0.0, 1.0) * (1.0 - level.getRainLevel(1.0F));
			}
		}
		return Math.max(light, sun);
	}

	/** Bile for every body that has just started retching, ours included. */
	private static void tickRetchers(ClientLevel level) {
		long now = level.getGameTime();
		retchStarted.keySet().removeIf(id -> {
			var entity = level.getEntity(id);
			BodySnapshot snapshot = entity == null ? null : entity.getAttached(RDAttachments.SNAPSHOT);
			return snapshot == null || !snapshot.retching();
		});
		for (Player body : level.players()) {
			BodySnapshot snapshot = body.getAttached(RDAttachments.SNAPSHOT);
			if (snapshot == null || !snapshot.retching()) {
				continue;
			}
			long started = retchStarted.computeIfAbsent(body.getId(), id -> now);
			long age = now - started;
			// Two heaves: a big one, a pause, a smaller one.
			boolean heave = (age >= 6 && age < 16) || (age >= 26 && age < 32);
			if (heave) {
				int drops = age < 16 ? 3 : 2;
				for (int i = 0; i < drops; i++) {
					BloodEmitter.spawnBile(body, now * 7L + i + body.getId() * 131L);
					bileDrops++;
				}
			}
		}
	}

	/** Advances the envelopes; once per rendered frame, game-time delta. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0) {
			return;
		}
		time += deltaTicks;
		boolean spinning = ClientState.vertigo() && ClientState.active();
		if (spinning) {
			direction = ClientState.vertigoLeft() ? -1.0 : 1.0;
		}
		vertigo = spinning ? Math.min(1.0, vertigo + deltaTicks * 0.06) : Math.max(0.0, vertigo - deltaTicks * 0.035);
		wince = Math.max(0.0, wince - deltaTicks * 0.07);
		double squintTarget = Math.max(glare * 0.85, wince);
		squint += (squintTarget - squint) * Math.min(1.0, deltaTicks * (squintTarget > squint ? 0.5 : 0.12));
		boolean bowed = ClientState.retching() && ClientState.active();
		retch = bowed ? Math.min(1.0, retch + deltaTicks * 0.12) : Math.max(0.0, retch - deltaTicks * 0.05);
	}

	/** View roll in degrees: the horizon going round in a spell. Rides Head Sway too. */
	public static double viewRoll() {
		if (vertigo <= 0.0) {
			return 0.0;
		}
		double sway = ClientEffectsConfig.get().cameraSway();
		double swing = 9.0 + 4.0 * Math.sin(time / 17.0) + 2.0 * Math.sin(time / 7.3);
		return direction * vertigo * swing * intensity() * Math.max(0.35, sway);
	}

	/** View pitch in degrees: the lurch of a spell, and the head going down to retch. */
	public static double viewPitch() {
		double lurch = vertigo * 2.5 * Math.sin(time / 11.0);
		double bow = retch * 28.0 + retch * 3.0 * Math.sin(time * 0.9);
		return (lurch + bow) * intensity();
	}

	/** Extra world warp for the distortion mixin. */
	public static double warp() {
		double flaring = Math.max(0.0, ClientState.flare() - 0.45) / 0.55;
		return Math.max(vertigo * 0.75, flaring * 0.45) * intensity();
	}

	/** Seeing double: a spell, a wince. */
	public static double doubled() {
		return Math.max(vertigo * 0.45, wince * 0.6) * intensity();
	}

	/** How far off true the feet are in a spell, in degrees; signed. Rides Stumble too. */
	public static double driftDegrees() {
		return direction * vertigo * 32.0 * intensity() * ClientEffectsConfig.get().stumbleIntensity();
	}

	/** A sideways stagger even with no key held, 0 to ~0.3 of a step. Rides Stumble too. */
	public static double stagger() {
		if (Minecraft.getInstance().gui.screen() != null) {
			return 0.0;   // nobody staggers off while looking at their inventory
		}
		return direction * vertigo * 0.28 * (0.6 + 0.4 * Math.sin(time / 9.0))
				* intensity() * ClientEffectsConfig.get().stumbleIntensity();
	}

	/** A short spike of ringing on a wince, for the audio layer. */
	public static double ring() {
		return wince * 0.6 * intensity();
	}

	/** Light: a warm veil and the squint; the flare: a throb on every beat; the wince: a white spike. */
	public static void render(GuiGraphicsExtractor graphics, int width, int height) {
		double strength = intensity();
		if (strength <= 0.0 || !ClientState.concussed()) {
			return;
		}
		if (glare > 0.01) {
			graphics.fill(0, 0, width, height,
					ARGB.color((int) (Math.min(1.0, glare) * 95.0 * strength), 255, 248, 225));
		}
		double flare = ClientState.flare();
		if (flare > 0.25) {
			double throb = (flare - 0.25) / 0.75 * (0.35 + 0.65 * ClientState.heartbeatEnvelope());
			float s = (float) Math.min(1.0, throb * 0.75 * strength);
			if (s > 0.004F) {
				graphics.blit(net.minecraft.client.renderer.RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F,
						width, height, width, height, ARGB.colorFromFloat(1.0F, s, s * 0.85F, s * 0.85F));
			}
		}
		if (wince > 0.01) {
			graphics.fill(0, 0, width, height, ARGB.color((int) (wince * wince * 80.0 * strength), 255, 255, 255));
		}
		// The squint: eyelids closing in from above and below, soft-edged.
		double lids = squint * strength;
		if (lids > 0.01) {
			int reach = (int) (height * 0.28 * Math.min(1.0, lids));
			int feather = Math.max(1, reach / 2);
			int solid = Math.max(0, reach - feather);
			int alpha = (int) (Math.min(1.0, lids * 1.4) * 235.0);
			int dark = ARGB.color(alpha, 8, 6, 6);
			int clear = ARGB.color(0, 8, 6, 6);
			graphics.fill(0, 0, width, solid, dark);
			graphics.fillGradient(0, solid, width, solid + feather, dark, clear);
			graphics.fill(0, height - solid, width, height, dark);
			graphics.fillGradient(0, height - solid - feather, width, height - solid, clear, dark);
		}
	}

	public static void reset() {
		vertigo = 0.0;
		wince = 0.0;
		glare = 0.0;
		squint = 0.0;
		retch = 0.0;
		retchStarted.clear();
		lastWinceTick = -1_000_000L;
	}
}
