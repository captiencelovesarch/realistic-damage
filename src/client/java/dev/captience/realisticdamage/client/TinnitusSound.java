package dev.captience.realisticdamage.client;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.lwjgl.BufferUtils;

/**
 * A synthesised tinnitus tone: a real continuous sine rather than a looped
 * sample, which is what actual ear-ringing sounds like. Generated procedurally
 * so nothing has to ship as an audio file.
 *
 * <p>Selectable against the resonant-bell ringing in the accessibility screen.
 */
public class TinnitusSound extends AbstractTickableSoundInstance {
	/**
	 * Around 2.6 kHz. The textbook tinnitus pitch is nearer 4 kHz, but that sits
	 * on top of Minecraft's own high end and reads as piercing rather than as
	 * ringing in your ears.
	 */
	private static final double CARRIER_HZ = 2600.0;
	private static final double BEAT_HZ = 5.5;
	private static final float SAMPLE_RATE = 44100.0F;

	private float targetVolume;
	private boolean fadingOut;

	public TinnitusSound() {
		super(dev.captience.realisticdamage.RealisticDamage.TINNITUS, SoundSource.AMBIENT,
				net.minecraft.util.RandomSource.create());
		this.looping = true;
		this.relative = true;
		this.attenuation = Attenuation.NONE;
		this.delay = 0;
		this.volume = 0.0F;
		this.pitch = 1.0F;
	}

	public void setTargetVolume(float target) {
		this.targetVolume = target;
	}

	public void fadeOut() {
		this.fadingOut = true;
		this.targetVolume = 0.0F;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		this.volume = Mth.lerp(0.08F, this.volume, this.targetVolume);
		if (this.fadingOut && this.volume < 0.01F) {
			stop();
		}
	}

	@Override
	public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, Identifier id,
			boolean repeatInstantly) {
		return CompletableFuture.completedFuture(new SineStream());
	}

	/** Mono 8-bit sine with a slow amplitude beat, so it shimmers rather than sitting dead flat. */
	private static final class SineStream implements AudioStream {
		// Unsigned 8-bit: silence is 128, not 0. Writing signed samples into an
		// unsigned format (as the usual example does) folds the negative half of
		// the wave over and adds harmonics, which is why the pitch sounded wrong.
		private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 8, 1, false, false);
		private static final double CARRIER_STEP = 2.0 * Math.PI * CARRIER_HZ / SAMPLE_RATE;
		private static final double BEAT_STEP = 2.0 * Math.PI * BEAT_HZ / SAMPLE_RATE;

		private double carrier;
		private double beat;

		@Override
		public AudioFormat getFormat() {
			return FORMAT;
		}

		@Override
		public ByteBuffer read(int capacity) {
			ByteBuffer buffer = BufferUtils.createByteBuffer(capacity);
			for (int i = 0; i < capacity; i++) {
				double envelope = 0.75 + 0.25 * Math.sin(beat);
				int sample = (int) Math.round(128.0 + Math.sin(carrier) * envelope * 110.0);
				buffer.put(i, (byte) Mth.clamp(sample, 0, 255));
				carrier = (carrier + CARRIER_STEP) % (2.0 * Math.PI);
				beat = (beat + BEAT_STEP) % (2.0 * Math.PI);
			}
			return buffer;
		}

		@Override
		public void close() {
		}
	}
}
