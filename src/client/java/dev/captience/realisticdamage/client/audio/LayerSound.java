package dev.captience.realisticdamage.client.audio;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jspecify.annotations.Nullable;

/**
 * One layer of a recipe: one sound file, played as it is.
 *
 * <p>A vanilla sound instance names a sound event, and the game then picks any
 * one of that event's files. This one carries its own single-file "event", so
 * the file a recipe names is the file that plays, with no entry in
 * {@code sounds.json} and nothing in the registry. It reads the file from the
 * player's own resources, so a vanilla sound is used where it lies and never
 * copied.
 */
final class LayerSound extends AbstractSoundInstance {
	private final WeighedSoundEvents event;

	/**
	 * @param name     what the log and the debug screen call it
	 * @param file     under {@code sounds/}, without {@code .ogg}
	 * @param subtitle translation key, or null: only a recipe's first layer has one
	 * @param range    blocks over which it fades to nothing, for one in the world
	 */
	LayerSound(Identifier name, Identifier file, @Nullable String subtitle, SoundSource source,
			float volume, float pitch, int range) {
		super(name, source, SoundInstance.createUnseededRandom());
		this.sound = new Sound(file, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE,
				false, false, range);
		this.event = new WeighedSoundEvents(name, subtitle);
		this.event.addSound(this.sound);
		this.volume = volume;
		this.pitch = pitch;
		// Inside your own head until placed in the world.
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	/** Somebody else's: at a place, fading with distance. */
	LayerSound at(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.relative = false;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		return this;
	}

	Identifier file() {
		return this.sound.getLocation();
	}

	@Override
	public WeighedSoundEvents resolve(SoundManager soundManager) {
		return this.event;
	}
}
