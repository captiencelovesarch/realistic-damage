package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.entity.SeveredHead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Your own decapitation, seen from the head (2.0): the view goes with the head
 * as it flies and rolls, and the body you just lost is out there in front of
 * it, going down. {@link RagdollView} does the riding (the smoothing, the
 * capped turn rate, the death intensity) and asks this class where the head
 * is and which way its face points.
 *
 * <p>Off with the Accessibility setting, and never in VR: there the death cuts
 * to black, because nothing may turn a headset's view.
 */
public final class HeadView {
	private HeadView() {}

	private static int cachedId = -1;

	/** Whether this death took your head and the view should go with it. */
	public static boolean wanted() {
		return ClientState.headless() && ClientEffectsConfig.get().decapitationView
				&& !dev.captience.realisticdamage.client.compat.Vr.active();
	}

	/** Whether the view is riding your head this frame. */
	public static boolean riding() {
		return wanted() && RagdollView.active() && ownHead() != null;
	}

	/** Your head, if it is in the world. */
	public static @Nullable SeveredHead ownHead() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer me = client.player;
		ClientLevel level = client.level;
		if (me == null || level == null) {
			return null;
		}
		if (cachedId >= 0 && level.getEntity(cachedId) instanceof SeveredHead head && !head.isRemoved()
				&& me.getUUID().equals(head.ownerId())) {
			return head;
		}
		cachedId = -1;
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof SeveredHead head && me.getUUID().equals(head.ownerId())) {
				cachedId = head.getId();
				return head;
			}
		}
		return null;
	}

	public static void reset() {
		cachedId = -1;
	}

	/**
	 * Which way the face points, as a camera orientation (forward -Z, up +Y):
	 * the way the body was looking, turned end over end about the head's own
	 * side axis by how far it has rolled.
	 */
	static Quaterniond orientation(SeveredHead head, float partial, Quaterniond out) {
		double yaw = Math.toRadians(head.getYRot());
		Vector3d forward = new Vector3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vector3d up = new Vector3d(0.0, 1.0, 0.0);
		Vector3d side = new Vector3d(Math.cos(yaw), 0.0, Math.sin(yaw));
		double roll = Math.toRadians(head.tumble(partial));
		Quaterniond turn = new Quaterniond().fromAxisAngleRad(side, roll);
		turn.transform(forward);
		turn.transform(up);
		return RagdollView.basis(forward, up, out);
	}

	/** Where the eyes are: in the middle of the head, a little toward the face. */
	static Vec3 eye(SeveredHead head, float partial) {
		Quaterniond q = orientation(head, partial, new Quaterniond());
		Vector3d forward = q.transform(new Vector3d(0.0, 0.0, -1.0));
		Vec3 centre = head.getPosition(partial).add(0.0, head.getBbHeight() / 2.0, 0.0);
		return centre.add(forward.x * 0.12, forward.y * 0.12, forward.z * 0.12);
	}
}
