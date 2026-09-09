package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

/** What the severed limb renderer needs from the entity for one frame (1.6.2). */
public class SeveredLimbRenderState extends EntityRenderState {
	public BodyPart part = BodyPart.LEFT_LEG;
	public Identifier skin = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
	public boolean slim;
	public float yaw;
	/** Spin in flight, degrees about the limb's own side axis. */
	public float tumble;
	/** 0 airborne to 1 lying flat on the ground. */
	public float lying;
	/** 1 fresh to 0 gone; alpha over the last two seconds. */
	public float alpha = 1.0F;
	/** 0 not burning to 1 burnt away. */
	public float burn;
	/** How wet the blood on it still is, 0 to 1. */
	public float blood;
}
