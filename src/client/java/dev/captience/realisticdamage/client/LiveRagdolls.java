package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.net.KnockdownPayload;
import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Knocked down, not dead (active ragdolls, phase 4): a body thrown off its
 * feet by a blast or a heavy blow, dropped by a hard landing, or gone out
 * cold, falls as a ragdoll and gets back up.
 *
 * <p>Started by the server's {@link KnockdownPayload} (a blast, a blow: it
 * carries the throw) or by the knocked-down and unconscious flags every client
 * already has in the body's snapshot (a landing, a faint), whichever arrives
 * first; the packet then adds its throw to the body the flag started.
 *
 * <p>Two stages. <b>Thrown:</b> the ragdoll leads. Your own client carries your
 * real position along with it (movement is yours to report; the knockdown has
 * already taken your steering); everyone else's ragdoll for you, and every
 * mob's, is kept loosely near where the entity is. <b>Down:</b> once it has
 * come to rest the entity leads, and the ragdoll is pulled along by its torso
 * wherever the entity goes, so a rescuer dragging an unconscious body drags
 * the ragdoll across the ground. Then, the knockdown over, the body awake and
 * still: it gets up, the ragdoll's pose blending into vanilla's standing one
 * over a second. A body with no legs left stays down, as it always has.
 */
public final class LiveRagdolls {
	private LiveRagdolls() {}

	/** Ticks the get-up takes. */
	static final int GET_UP_TICKS = 22;
	/** Thrown for at most this long before the entity leads regardless, ticks. */
	private static final int MAX_THROWN_TICKS = 70;
	/** The torso's pull toward the entity once down: velocity per block apart, blocks a second. */
	private static final double TETHER = 5.0;
	/** ...and the most it moves the torso, blocks a second. */
	private static final double TETHER_SPEED = 4.0;
	/** Farther than this from the entity, a ragdoll is moved bodily (a teleport, a correction). */
	private static final double SNAP = 4.0;

	static final class Live {
		final RagdollCorpse ragdoll;
		int age;
		/** Ticks still to be down at least, from the knockdown packet; 0 when the flags say. */
		int down;
		boolean thrown = true;
		/** Ticks into the get-up, or -1 before it. */
		int gettingUp = -1;

		Live(RagdollCorpse ragdoll, int down) {
			this.ragdoll = ragdoll;
			this.down = down;
		}

		/** 1 lying, falling to 0 standing over the get-up. */
		float blend(float partial) {
			if (gettingUp < 0) {
				return 1.0F;
			}
			float t = Mth.clamp((gettingUp + partial) / GET_UP_TICKS, 0.0F, 1.0F);
			return 1.0F - t * t * (3.0F - 2.0F * t);
		}
	}

	private static final Int2ObjectOpenHashMap<Live> LIVE = new Int2ObjectOpenHashMap<>();

	static boolean enabled() {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		return config.knockdownRagdolls && config.ragdolls == ClientEffectsConfig.RagdollMode.FULL;
	}

	/** The server's knockdown: start the ragdoll with the throw, or add the throw to one a flag started. */
	public static void start(KnockdownPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !enabled()) {
			return;
		}
		Entity entity = level.getEntity(payload.body().entityId());
		if (!(entity instanceof LivingEntity body) || body.isDeadOrDying() || !allowed(body)) {
			return;
		}
		Live live = LIVE.get(body.getId());
		if (live != null && live.ragdoll.entity.get() == body && live.gettingUp < 0) {
			if (live.age <= 4) {
				live.ragdoll.relaunch(body, payload.body());
			}
			live.down = Math.max(live.down, payload.ticks());
			return;
		}
		begin(body, payload.body(), payload.ticks(), level);
	}

	/** Whether this body may be ragdolled while alive here (your own: not in VR, and Death View on Ragdoll). */
	private static boolean allowed(LivingEntity body) {
		if (body == Minecraft.getInstance().player) {
			return ClientEffectsConfig.get().ownRagdoll && !dev.captience.realisticdamage.client.compat.Vr.active();
		}
		return true;
	}

	private static void begin(LivingEntity body, CorpsePayload payload, int ticks, ClientLevel level) {
		RagdollCorpse ragdoll = RagdollCorpse.create(body, payload, level, true);
		if (ragdoll != null) {
			LIVE.put(body.getId(), new Live(ragdoll, ticks));
		}
	}

	/** Once per client tick: new knockdowns from the flags, then every live ragdoll. */
	static void tick(ClientLevel level, double timeScale) {
		if (!enabled()) {
			LIVE.clear();
			return;
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity body) || body.isDeadOrDying() || LIVE.containsKey(body.getId())) {
				continue;
			}
			BodySnapshot snapshot = body.getAttached(RDAttachments.SNAPSHOT);
			if (snapshot == null || !(snapshot.knockedDown() || snapshot.unconscious()) || !allowed(body)
					|| Minecraft.getInstance().gameRenderer.mainCamera().position().distanceTo(body.position()) > 64.0) {
				continue;
			}
			// A landing, or a faint: no throw to speak of beyond what it was doing.
			DeathKind kind = snapshot.unconscious() ? DeathKind.FADE : DeathKind.FALL;
			begin(body, new CorpsePayload(body.getId(), kind, 0.3F, 0.0F, 0.0F, 0.0F), 0, level);
		}

		var it = LIVE.int2ObjectEntrySet().fastIterator();
		while (it.hasNext()) {
			Live live = it.next().getValue();
			Entity entity = level.getEntity(live.ragdoll.id);
			if (!(entity instanceof LivingEntity body) || entity != live.ragdoll.entity.get() || body.isRemoved()) {
				it.remove();
				continue;
			}
			if (body.isDeadOrDying()) {
				// Its death packet takes the ragdoll over (RemoteCorpses.start);
				// if none comes, let it go.
				if (++live.age > 10) {
					it.remove();
				}
				continue;
			}
			live.age++;
			if (live.down > 0) {
				live.down--;
			}
			BodySnapshot snapshot = body.getAttached(RDAttachments.SNAPSHOT);
			boolean flagged = snapshot != null && (snapshot.knockedDown() || snapshot.unconscious());
			boolean legless = snapshot != null && snapshot.destroyed(BodyPart.LEFT_LEG) && snapshot.destroyed(BodyPart.RIGHT_LEG);

			if (live.gettingUp >= 0) {
				if (flagged) {
					// Knocked down again on the way up: down it goes again.
					live.gettingUp = -1;
					live.thrown = true;
					live.ragdoll.body.wake();
				} else if (++live.gettingUp >= GET_UP_TICKS) {
					it.remove();
				}
				continue;
			}

			RagdollCorpse ragdoll = live.ragdoll;
			ragdoll.tick(timeScale);
			boolean rested = ragdoll.body.asleep() || (live.age > 15 && ragdoll.body.sim.maxMotion() < 0.4);
			if (live.thrown && (rested || live.age > MAX_THROWN_TICKS)) {
				live.thrown = false;
			}
			if (live.thrown) {
				if (body == Minecraft.getInstance().player) {
					carry((LocalPlayer) body, ragdoll);
				} else {
					tether(body, ragdoll, 0.4);
				}
			} else {
				tether(body, ragdoll, 1.0);
			}

			if (!flagged && live.down <= 0 && !live.thrown && live.age > 10) {
				if (legless) {
					// Nothing to stand on: the crawl it always had.
					it.remove();
				} else {
					live.gettingUp = 0;
				}
			}
		}
	}

	/** Your own body, thrown: your position goes where the ragdoll's torso goes. */
	private static void carry(LocalPlayer player, RagdollCorpse ragdoll) {
		Body torso = ragdoll.body.body(HumanoidRagdoll.TORSO);
		double dx = Mth.clamp(torso.x.x - player.getX(), -1.0, 1.0);
		double dz = Mth.clamp(torso.x.z - player.getZ(), -1.0, 1.0);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(dx, v.y, dz);
	}

	/**
	 * Keeps a ragdoll with its entity: the torso pulled toward where the entity
	 * is (by {@code strength} of the full pull), so the limbs trail after it.
	 */
	private static void tether(LivingEntity entity, RagdollCorpse ragdoll, double strength) {
		Body torso = ragdoll.body.body(HumanoidRagdoll.TORSO);
		double dx = entity.getX() - torso.x.x;
		double dz = entity.getZ() - torso.x.z;
		double apart = Math.hypot(dx, dz);
		if (apart > SNAP) {
			// A teleport or a correction: move the whole body.
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				Body b = ragdoll.body.body(i);
				b.x.add(dx, 0.0, dz);
				b.tickX.add(dx, 0.0, dz);
			}
			ragdoll.body.wake();
			return;
		}
		if (apart < 0.3) {
			return;
		}
		ragdoll.body.wake();
		double speed = Math.min(TETHER_SPEED, (apart - 0.3) * TETHER) * strength;
		torso.v.add(dx / apart * speed * 0.25, 0.0, dz / apart * speed * 0.25);
	}

	/** The live ragdoll for this entity, or null. */
	static @Nullable Live of(Entity entity) {
		if (LIVE.isEmpty()) {
			return null;
		}
		Live live = LIVE.get(entity.getId());
		return live != null && live.ragdoll.entity.get() == entity ? live : null;
	}

	/** A body that died while down: hands its ragdoll to the death, or null if it had none. */
	static @Nullable RagdollCorpse takeForDeath(LivingEntity entity, CorpsePayload payload) {
		Live live = of(entity);
		if (live == null) {
			return null;
		}
		LIVE.remove(entity.getId());
		live.ragdoll.becomeDeath(entity, payload);
		return live.ragdoll;
	}

	/** Your own live ragdoll, or null. */
	static @Nullable Live own() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : of(player);
	}

	static Iterable<RagdollCorpse> ragdolls() {
		java.util.List<RagdollCorpse> all = new java.util.ArrayList<>();
		for (Live live : LIVE.values()) {
			all.add(live.ragdoll);
		}
		return all;
	}

	static void clear() {
		LIVE.clear();
	}

	/** For the client gametest. */
	public static int count() {
		return LIVE.size();
	}

	/** For the client gametest: -1 no live ragdoll, 0 down, 1 getting up. */
	public static int stage(Entity entity) {
		Live live = of(entity);
		return live == null ? -1 : live.gettingUp >= 0 ? 1 : 0;
	}
}
