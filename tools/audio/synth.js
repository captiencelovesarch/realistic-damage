/*
 * The Grounded injury sounds, synthesised.
 *
 * One file, two homes: the tuner page loads it to play sounds as sliders
 * move, and `node synth.js wav <dir> [settings.json]` renders the same sounds
 * to files (build_grounded.py turns those into the mod's .ogg files). So what
 * is tuned by ear is exactly what gets shipped.
 *
 * Nothing here is a recording. What it learned from recordings (celery
 * snaps, a wet sponge, a squeezed grapefruit) is three numbers the first
 * version got wrong:
 *   - gore is BRIGHT: half the energy of a snap or a tear sits above 4 kHz;
 *   - gore is NOISE: crackle and grit, almost nothing with a pitch;
 *   - a snap is over in 15 ms.
 * Version 1 was dark, slow and built from sine "bubbles", which is why it
 * sounded like a cartoon. Bubbles are still here as a slider.
 *
 * The defaults and takes are the ones tuned by ear on the page.
 */
(function (root) {
  'use strict';
  const SR = 44100;

  // ---------------------------------------------------------------- basics

  function rng(seed) {
    let a = seed >>> 0;
    const f = function () {
      a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    f.range = (lo, hi) => lo + (hi - lo) * f();
    f.log = (lo, hi) => Math.exp(f.range(Math.log(lo), Math.log(hi)));
    f.exp = (mean) => -mean * Math.log(1 - f() + 1e-12);
    f.gauss = () => Math.sqrt(-2 * Math.log(f() + 1e-12)) * Math.cos(6.283185307 * f());
    return f;
  }

  function noise(r, n) {
    const x = new Float64Array(n);
    for (let i = 0; i < n; i++) x[i] = r.gauss();
    return x;
  }

  /** RBJ biquad, in place. type: 'lp' | 'hp' | 'bp'. */
  function biquad(x, type, fc, q) {
    const w = 2 * Math.PI * Math.max(20, Math.min(fc, SR * 0.45)) / SR;
    const c = Math.cos(w), al = Math.sin(w) / (2 * q);
    let b0, b1, b2;
    if (type === 'lp') { b0 = (1 - c) / 2; b1 = 1 - c; b2 = b0; }
    else if (type === 'hp') { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = b0; }
    else { b0 = al; b1 = 0; b2 = -al; }
    const a0 = 1 + al, a1 = -2 * c, a2 = 1 - al;
    let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
    for (let i = 0; i < x.length; i++) {
      const xi = x[i];
      const y = (b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
      x2 = x1; x1 = xi; y2 = y1; y1 = y;
      x[i] = y;
    }
    return x;
  }

  function peak(x) {
    let m = 0;
    for (let i = 0; i < x.length; i++) { const v = Math.abs(x[i]); if (v > m) m = v; }
    return m;
  }

  function norm(x) {
    const m = peak(x);
    if (m > 1e-9) for (let i = 0; i < x.length; i++) x[i] /= m;
    return x;
  }

  function add(out, piece, at, gain) {
    if (!gain) return;
    const s = Math.max(0, Math.round(at * SR));
    const n = Math.min(piece.length, out.length - s);
    for (let i = 0; i < n; i++) out[s + i] += gain * piece[i];
  }

  /** A stretch of coloured noise that grains are cut out of. */
  function bed(r, fc, q) {
    return norm(biquad(noise(r, Math.round(0.4 * SR)), 'bp', fc, q));
  }

  /** Slow unevenness, 0..1: nothing soft moves at a steady rate. */
  function lumps(r, n, hz) {
    const x = biquad(noise(r, n + 2000), 'lp', hz, 0.7).subarray(2000);
    let lo = Infinity, hi = -Infinity;
    for (let i = 0; i < n; i++) { if (x[i] < lo) lo = x[i]; if (x[i] > hi) hi = x[i]; }
    const out = new Float64Array(n);
    for (let i = 0; i < n; i++) out[i] = (x[i] - lo) / (hi - lo + 1e-12);
    return out;
  }

  /**
   * Scatter short grains of noise: the one texture everything wet, torn or
   * sprayed is made of. Each grain snaps on and dies away, like a surface
   * letting go.
   */
  function grains(out, r, o) {
    const n = out.length, dur = n / SR;
    let t = 0;
    for (;;) {
      const u = t / dur;
      t += r.exp(1 / Math.max(o.rate(Math.min(u, 0.999)), 0.5));
      if (t >= dur) break;
      const b = o.beds[Math.floor(r() * o.beds.length)];
      const m = Math.max(8, Math.round(r.range(o.len[0], o.len[1]) * SR));
      const off = Math.floor(r() * (b.length - m - 1));
      const s = Math.round(t * SR);
      const g = o.gain(r) * o.shape(t / dur);
      const rise = Math.max(2, Math.round(m * 0.12));
      for (let i = 0; i < m && s + i < n; i++) {
        const w = i < rise ? i / rise : Math.pow(1 - (i - rise) / (m - rise), 2);
        out[s + i] += g * w * b[off + i];
      }
    }
    return out;
  }

  // ---------------------------------------------------------------- layers

  /** One micro-fracture: a burst of noise through a stiff, short body. */
  function crack(r, bright, hard, len) {
    const n = Math.round(len * SR);
    const tau = 0.009 * Math.pow(0.0013 / 0.009, hard);
    const x = noise(r, n);
    for (let i = 0; i < n; i++) x[i] *= Math.exp(-i / SR / tau);
    const a = Float64Array.from(x), b = Float64Array.from(x);
    biquad(biquad(x, 'hp', bright * 0.3, 0.7), 'lp', bright * 2.2, 0.7);
    norm(x);
    norm(biquad(a, 'bp', r.log(bright * 0.4, bright * 0.9), 6));
    norm(biquad(b, 'bp', r.log(bright * 0.9, bright * 1.6), 8));
    for (let i = 0; i < n; i++) x[i] += 0.5 * a[i] + 0.35 * b[i];
    x[0] += 0.8; x[1] -= 0.5;
    return norm(x);
  }

  /** A bone letting go: maybe a creak, a double crack, then splinters. */
  function cracks(r, dur, p) {
    const out = new Float64Array(Math.round(dur * SR));
    const count = Math.round(p.crackCount);
    if (count < 1) return out;
    const lead = crackLead(p), spread = p.crackSpread / 1000;
    if (p.creak > 0.02) {
      let t = lead - 0.036, gap = 0.009;
      while (t < lead - 0.003) {
        const along = 1 - (lead - t) / 0.036;
        add(out, crack(r, p.crackBright * 0.45, 0.8, 0.02), t, p.creak * (0.08 + 0.25 * along) * r.range(0.6, 1));
        t += gap * r.range(0.7, 1.2);
        gap = Math.max(gap * 0.8, 0.002);
      }
    }
    add(out, crack(r, p.crackBright, p.crackHard, 0.06), lead, 1);
    if (count >= 2) add(out, crack(r, p.crackBright * 0.8, p.crackHard, 0.06), lead + r.range(0.004, 0.009), 0.7);
    for (let i = 2; i < count; i++) {
      const dt = 0.012 + r.exp(spread);
      add(out, crack(r, p.crackBright * r.range(0.9, 1.4), Math.min(1, p.crackHard + 0.2), 0.04), lead + dt,
        0.55 * Math.exp(-dt / (spread * 1.6)) * r.range(0.4, 1));
    }
    return norm(out);
  }

  function crackLead(p) { return p.creak > 0.02 ? 0.04 : 0.003; }

  /** Weight. Mostly low noise; the tone under it barely moves in pitch. */
  function thud(r, depth, len) {
    const n = Math.round((len * 5 + 0.05) * SR);
    const tone = new Float64Array(n);
    let ph = 0;
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      ph += 2 * Math.PI * depth * (1 - 0.15 * i / n) / SR;
      tone[i] = Math.sin(ph) * Math.exp(-t / (len * 0.5)) * Math.min(1, t / 0.002);
    }
    const low = biquad(noise(r, n), 'lp', depth * 3.5, 0.7);
    const slap = biquad(noise(r, n), 'bp', 700, 0.8);
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      low[i] *= Math.exp(-t / (len * 0.4));
      slap[i] *= Math.exp(-t / 0.012);
    }
    norm(low); norm(slap);
    for (let i = 0; i < n; i++) tone[i] = 0.4 * tone[i] + 0.8 * low[i] + 0.7 * slap[i];
    return norm(tone);
  }

  /** Wet: sticky grains over a soft bed. Bubbles only if asked for. */
  function wet(r, dur, p) {
    const n = Math.round(dur * SR);
    const out = new Float64Array(n);
    if (n < 64) return out;
    const beds = [bed(r, p.wetBright * 0.5, 0.9), bed(r, p.wetBright, 0.9), bed(r, p.wetBright * 2, 0.9)];
    const lump = lumps(r, n, 9);
    const shape = (u) => Math.min(1, u * dur / 0.012) * Math.pow(1 - u, 1.4);
    const base = 30 * Math.pow(15, p.wetDensity);
    grains(out, r, {
      beds,
      rate: (u) => base * (0.3 + 1.4 * lump[Math.floor(u * n)]),
      len: [0.002, 0.004 + 0.008 * (1 - 0.5 * p.wetDensity)],
      gain: (q) => 0.08 + Math.pow(q(), 3),
      shape,
    });
    norm(out);
    const soft = norm(biquad(noise(r, n), 'bp', p.wetBright * 0.6, 0.7));
    for (let i = 0; i < n; i++) out[i] += 0.22 * soft[i] * shape(i / n) * (0.3 + 0.7 * lump[i]);
    if (p.wetBubble > 0.01) {
      let t = 0;
      for (;;) {
        t += r.exp(1 / 60);
        if (t >= dur) break;
        const f0 = r.log(p.wetBright * 0.3, p.wetBright * 1.3);
        const damp = 0.13 * f0 + 0.0072 * Math.pow(f0, 1.5);
        const m = Math.round(Math.min(7 / damp, 0.09) * SR);
        const rise = r.range(0.02, 0.25) * damp, g = p.wetBubble * 0.6 * Math.pow(r(), 2) * shape(t / dur);
        const s = Math.round(t * SR);
        for (let i = 0; i < m && s + i < n; i++) {
          const tb = i / SR;
          out[s + i] += g * Math.sin(2 * Math.PI * f0 * (tb + 0.5 * rise * tb * tb)) * Math.exp(-damp * tb) * Math.min(1, i / 16);
        }
      }
    }
    return norm(out);
  }

  /** Tissue parting: a rush of tiny bright failures, in ragged surges. */
  function tear(r, dur, p) {
    const n = Math.round(dur * SR);
    const out = new Float64Array(n);
    if (n < 64) return out;
    const surge = lumps(r, n, 25);
    for (let i = 0; i < n; i++) surge[i] = Math.pow(surge[i], 1 + 3 * p.tearRough);
    const shape = (u) => Math.pow(Math.sin(Math.PI * u), 0.5);
    grains(out, r, {
      beds: [bed(r, 5000, 0.7), bed(r, 2800, 0.7)],
      rate: (u) => (120 + 260 * u) * (0.25 + 1.5 * surge[Math.floor(u * n)]),
      len: [0.0004, 0.0016],
      gain: (q) => 0.1 + 1.2 * Math.pow(q(), 4),
      shape,
    });
    norm(out);
    const under = norm(biquad(noise(r, n), 'bp', 1400, 0.7));
    for (let i = 0; i < n; i++) out[i] += 0.25 * under[i] * surge[i] * shape(i / n);
    return norm(out);
  }

  /** Blood under pressure: spatter in pulses, each weaker, then it lands. */
  function spray(r, p) {
    const pulses = Math.round(p.sprayPulses);
    const out = new Float64Array(Math.round((0.42 * Math.max(pulses - 1, 0) + p.sprayLen + 0.5) * SR));
    const air = biquad(bed(r, 3600, 0.9), 'lp', 8000, 0.7), drops = [bed(r, 2200, 0.9), bed(r, 4000, 0.9)];
    for (let k = 0; k < pulses; k++) {
      const at = 0.42 * k + (k ? r.range(-0.03, 0.03) : 0), level = Math.pow(0.72, k);
      const n = Math.round(p.sprayLen * SR);
      const mod = new Float64Array(n);
      grains(mod, r, { beds: [new Float64Array(SR).fill(1)], rate: () => 170, len: [0.002, 0.006], gain: (q) => q(), shape: () => 1 });
      norm(mod);
      const pulse = new Float64Array(n);
      for (let i = 0; i < n; i++) {
        const t = i / SR;
        pulse[i] = air[(i + k * 9111) % air.length] * (0.15 + 0.85 * mod[i]) * Math.min(1, t / 0.012) * Math.exp(-t / (p.sprayLen / 3));
      }
      add(out, norm(pulse), at, level);
      const land = new Float64Array(Math.round(0.35 * SR));
      grains(land, r, { beds: drops, rate: () => 45, len: [0.002, 0.007], gain: (q) => 0.1 + Math.pow(q(), 2), shape: (u) => 1 - u });
      add(out, norm(land), at + 0.09, 0.5 * level);
    }
    return norm(out);
  }

  /** An edge through air. */
  function swish(r) {
    const n = Math.round(0.1 * SR), x = noise(r, n);
    let y = 0;
    for (let i = 0; i < n; i++) {
      const u = i / n, fc = 900 * Math.pow(6, Math.sin(Math.PI * u * 0.8));
      y += (1 - Math.exp(-2 * Math.PI * fc / SR)) * (x[i] - y);
      x[i] = y * Math.pow(Math.sin(Math.PI * u), 1.5);
    }
    return norm(biquad(x, 'hp', 500, 0.7));
  }

  /**
   * Dead weight landing on something that gives. The old thump rang for four
   * or five cycles and had a 700 Hz knock on top, which is a floorboard. This
   * one pushes for about one cycle, sags in pitch, and has no knock at all.
   */
  function slump(r, depth, len, soft) {
    const n = Math.round((len * 5 + 0.1) * SR);
    const rise = 0.002 + 0.02 * soft, tau = 1.1 / depth, sag = 2 / depth;
    const push = new Float64Array(n);
    let ph = 0;
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      ph += 2 * Math.PI * depth * (1 - 0.35 * Math.min(1, t / sag)) / SR;
      push[i] = Math.sin(ph) * Math.exp(-t / tau);
    }
    const low = biquad(noise(r, n), 'lp', depth * 2.5, 0.5);
    const mass = biquad(biquad(noise(r, n), 'bp', depth * 5, 0.5), 'lp', 900 - 500 * soft, 0.5);
    for (let i = 0; i < n; i++) {
      const t = i / SR, u = Math.min(1, t / rise), on = u * u * (3 - 2 * u);
      low[i] *= on * Math.exp(-t / len);
      mass[i] *= on * Math.exp(-t / (len * 0.3));
    }
    norm(low); norm(mass);
    const meat = 0.9 - 0.35 * soft;
    for (let i = 0; i < n; i++) push[i] = 0.6 * push[i] + 0.9 * low[i] + meat * mass[i];
    return norm(push);
  }

  /** What the body lands in: dull grit, thick at the hit and thinning out. */
  function grit(r, dur) {
    const n = Math.round(dur * SR), out = new Float64Array(n);
    grains(out, r, {
      beds: [bed(r, 650, 0.8), bed(r, 1300, 0.8), bed(r, 2400, 0.8)],
      rate: (u) => 30 + 320 * Math.pow(1 - u, 3),
      len: [0.003, 0.012],
      gain: (q) => 0.1 + Math.pow(q(), 2),
      shape: (u) => Math.min(1, u * dur / 0.01) * Math.pow(1 - u, 1.5),
    });
    return norm(out);
  }

  /** The air going out of someone. Noise only: no voice, no pitch. */
  function breath(r, dur) {
    const n = Math.round(dur * SR);
    const a = norm(biquad(noise(r, n), 'bp', 950, 0.7)), b = norm(biquad(noise(r, n), 'bp', 2600, 1.2));
    const lump = lumps(r, n, 12);
    for (let i = 0; i < n; i++) {
      const u = i / n, on = Math.min(1, i / SR / 0.03);
      a[i] = (a[i] + 0.35 * b[i]) * on * on * Math.pow(1 - u, 2) * (0.7 + 0.3 * lump[i]);
    }
    return norm(a);
  }

  // ---------------------------------------------------------------- sliders

  const S = {
    crackGain: ['Cracks: volume', 0, 1.5, 0.05],
    crackCount: ['Cracks: how many', 0, 14, 1],
    crackSpread: ['Cracks: bunched ↔ spread out', 10, 250, 5],
    crackBright: ['Cracks: dull ↔ sharp', 1500, 8000, 100],
    crackHard: ['Cracks: thick ↔ dry tick', 0, 1, 0.05],
    creak: ['Creak before the snap', 0, 1, 0.05],
    thudGain: ['Thump: volume', 0, 1.5, 0.05],
    thudDepth: ['Thump: deep ↔ light', 50, 300, 5],
    thudLen: ['Thump: tight ↔ boomy', 0.01, 0.2, 0.01],
    wetGain: ['Wet: volume', 0, 1.5, 0.05],
    wetLen: ['Wet: short ↔ long', 0.05, 1.5, 0.05],
    wetBright: ['Wet: dark ↔ bright', 600, 7000, 100],
    wetDensity: ['Wet: sparse drips ↔ thick mush', 0, 1, 0.05],
    wetBubble: ['Wet: bubbles (the version 1 "bloop")', 0, 1, 0.05],
    tearGain: ['Tear: volume', 0, 1.5, 0.05],
    tearLen: ['Tear: short ↔ long', 0.05, 1, 0.05],
    tearRough: ['Tear: smooth ↔ ragged', 0, 1, 0.05],
    sprayGain: ['Spurts: volume', 0, 1.5, 0.05],
    sprayPulses: ['Spurts: how many', 0, 5, 1],
    sprayLen: ['Spurts: short ↔ long', 0.1, 0.6, 0.05],
    swishGain: ['Blade swish: volume', 0, 1, 0.05],
    slumpGain: ['Body hit: volume', 0, 1.5, 0.05],
    slumpDepth: ['Body hit: deep ↔ light', 40, 200, 5],
    slumpLen: ['Body hit: tight ↔ boomy', 0.03, 0.3, 0.01],
    slumpSoft: ['Body hit: hard floor ↔ soft ground', 0, 1, 0.05],
    loose: ['Body: one lump ↔ loose limbs', 0, 1, 0.25],
    kneeGain: ['Knees first: volume', 0, 1, 0.05],
    gritGain: ['Ground scuff: volume', 0, 1.5, 0.05],
    breathGain: ['Breath knocked out: volume', 0, 1.5, 0.05],
    gap: ['Knees to floor: quick ↔ slow', 0.1, 0.7, 0.02],
    width: ['Stereo: narrow ↔ wide', 0, 1, 0.05],
  };

  // ---------------------------------------------------------------- events
  // build(p, core, side): `core` seeds what is dead centre, `side` what is
  // different in each ear.

  const EVENTS = {
    bone_break: {
      title: 'Bone breaks',
      blurb: 'A fracture. The most-heard injury sound in the mod.',
      seed: 15,
      defaults: {
        crackGain: 1.15, crackCount: 2, crackSpread: 35, crackBright: 7700, crackHard: 0.2, creak: 0.15,
        thudGain: 1.2, thudDepth: 75, thudLen: 0.06,
        wetGain: 0.7, wetLen: 0.4, wetBright: 1900, wetDensity: 0.35, wetBubble: 0, width: 0.3,
      },
      build(p, core, side) {
        const out = new Float64Array(Math.round((0.4 + p.wetLen) * SR)), lead = crackLead(p);
        add(out, cracks(core(1), 0.42, p), 0, p.crackGain);
        add(out, thud(core(2), p.thudDepth, p.thudLen), lead - 0.002, p.thudGain);
        add(out, wet(side(3), p.wetLen, p), lead + 0.03, p.wetGain);
        return out;
      },
    },
    limb_lost: {
      title: 'Limb destroyed / severed',
      blurb: 'An arm or leg goes.',
      seed: 9,
      defaults: {
        crackGain: 0.1, crackCount: 1, crackSpread: 110, crackBright: 1500, crackHard: 0.05, creak: 0,
        thudGain: 0.55, thudDepth: 50, thudLen: 0.07,
        tearGain: 1.15, tearLen: 0.25, tearRough: 0.2,
        wetGain: 0.2, wetLen: 0.5, wetBright: 2400, wetDensity: 0.85, wetBubble: 0.05, width: 0.7,
      },
      build(p, core, side) {
        const out = new Float64Array(Math.round((0.75 + Math.max(p.wetLen, p.tearLen)) * SR)), lead = crackLead(p);
        add(out, cracks(core(1), 0.7, p), 0, p.crackGain);
        add(out, thud(core(2), p.thudDepth, p.thudLen), lead, p.thudGain);
        add(out, tear(side(3), p.tearLen, p), lead + 0.03, p.tearGain);
        add(out, wet(side(4), p.wetLen, p), lead + 0.05, p.wetGain);
        return out;
      },
    },
    decapitation: {
      title: 'Decapitation',
      blurb: 'An edged blow takes the head.',
      seed: 6,
      defaults: {
        swishGain: 0.35,
        crackGain: 0.8, crackCount: 3, crackSpread: 30, crackBright: 4000, crackHard: 0.6, creak: 0,
        thudGain: 0.4, thudDepth: 100, thudLen: 0.05,
        wetGain: 0.55, wetLen: 0.3, wetBright: 2600, wetDensity: 0.65, wetBubble: 0,
        sprayGain: 0.5, sprayPulses: 0, sprayLen: 0.3, width: 0.7,
      },
      build(p, core, side) {
        const cut = p.swishGain > 0.02 ? 0.07 : 0;
        const pulses = Math.round(p.sprayPulses);
        const tail = pulses > 0 ? 0.42 * (pulses - 1) + p.sprayLen + 0.5 : 0;
        const out = new Float64Array(Math.round((cut + 0.6 + Math.max(p.wetLen, tail)) * SR));
        add(out, swish(core(1)), 0, p.swishGain);
        add(out, cracks(core(2), 0.4, p), cut, p.crackGain);
        add(out, thud(core(3), p.thudDepth, p.thudLen), cut, p.thudGain);
        add(out, wet(side(4), p.wetLen, p), cut + 0.01, p.wetGain);
        if (pulses > 0) add(out, spray(side(5), p), cut + 0.25, p.sprayGain);
        return out;
      },
    },
    blood_splat: {
      title: 'Blood landing',
      blurb: 'Three drops hitting the floor, one after another.',
      seed: 15,
      defaults: {
        thudGain: 0, thudDepth: 50, thudLen: 0.01,
        wetGain: 0.75, wetLen: 0.2, wetBright: 2500, wetDensity: 0, wetBubble: 0.75, width: 0,
      },
      build(p, core, side) {
        const out = new Float64Array(Math.round((0.75 + p.wetLen) * SR));
        [[0.02, 1], [0.37, 0.75], [0.62, 0.9]].forEach(([at, level], k) => {
          add(out, thud(core(10 + k), p.thudDepth * (1 + 0.15 * k), p.thudLen), at, p.thudGain * level);
          add(out, wet(side(20 + k), p.wetLen, p), at + 0.002, p.wetGain * level);
        });
        return out;
      },
    },
    flesh_hit: {
      title: 'Wound opens',
      blurb: 'A cut or stab that starts a bleed.',
      seed: 8,
      defaults: {
        thudGain: 0.35, thudDepth: 115, thudLen: 0.03,
        tearGain: 0.4, tearLen: 0.1, tearRough: 0.4,
        wetGain: 0.8, wetLen: 0.2, wetBright: 3000, wetDensity: 0.65, wetBubble: 0,
        crackGain: 0.5, crackCount: 0, crackSpread: 30, crackBright: 4500, crackHard: 0.6, creak: 0, width: 0.5,
      },
      build(p, core, side) {
        const out = new Float64Array(Math.round((0.3 + Math.max(p.wetLen, p.tearLen)) * SR));
        add(out, thud(core(1), p.thudDepth, p.thudLen), 0.004, p.thudGain);
        add(out, tear(side(2), p.tearLen, p), 0.008, p.tearGain);
        add(out, wet(side(3), p.wetLen, p), 0.012, p.wetGain);
        add(out, cracks(core(4), 0.25, p), 0.004, p.crackGain);
        return out;
      },
    },
    collapse: {
      title: 'Collapse',
      blurb: 'Consciousness runs out and you go down. Dead weight on the ground: a soft heavy slump, no floorboards.',
      seed: 1,
      defaults: {
        slumpGain: 1, slumpDepth: 55, slumpLen: 0.04, slumpSoft: 0.7, loose: 0.5,
        kneeGain: 0.3, gap: 0.5, gritGain: 0.3, breathGain: 0.35, width: 0.4,
      },
      build(p, core, side) {
        const knees = p.kneeGain > 0.02, body = 0.06 + (knees ? p.gap : 0);
        const out = new Float64Array(Math.round((body + 0.8 + p.slumpLen * 2) * SR));
        if (knees) {
          add(out, slump(core(1), p.slumpDepth * 1.5, p.slumpLen * 0.4, p.slumpSoft), 0.06, p.slumpGain * p.kneeGain);
          add(out, grit(side(2), 0.15), 0.065, p.gritGain * p.kneeGain * 0.7);
        }
        // A body is not one object: hip, shoulder, arm and head arrive apart.
        const r = core(3);
        add(out, slump(r, p.slumpDepth, p.slumpLen, p.slumpSoft), body, p.slumpGain);
        for (let k = Math.round(p.loose * 4); k > 0; k--) {
          const at = r.range(0.03, 0.06 + 0.3 * p.loose);
          add(out, slump(r, p.slumpDepth * r.range(1.3, 2), p.slumpLen * r.range(0.3, 0.6), p.slumpSoft), body + at,
            p.slumpGain * r.range(0.25, 0.55));
        }
        add(out, grit(side(4), 0.2 + 0.3 * p.loose), body + 0.005, p.gritGain);
        add(out, breath(core(5), 0.45), body + 0.03, p.breathGain);
        return out;
      },
    },
  };

  // ---------------------------------------------------------------- render

  /** Returns { left, right } as Float32Array, peak 0.9. */
  function render(name, params, seed) {
    const ev = EVENTS[name], p = Object.assign({}, ev.defaults, params || {});
    seed = seed || ev.seed || 1;
    const one = (ear) => ev.build(p, (k) => rng(seed * 1000 + k), (k) => rng(seed * 1000 + k + 500 * (ear + 1)));
    const a = one(0), b = one(1), n = a.length, w = p.width === undefined ? 0.6 : p.width;
    const l = new Float64Array(n), r = new Float64Array(n);
    for (let i = 0; i < n; i++) {
      l[i] = (1 + w) / 2 * a[i] + (1 - w) / 2 * b[i];
      r[i] = (1 + w) / 2 * b[i] + (1 - w) / 2 * a[i];
    }
    biquad(l, 'hp', 35, 0.7); biquad(r, 'hp', 35, 0.7);
    const m = Math.max(peak(l), peak(r));
    const left = new Float32Array(n), right = new Float32Array(n);
    if (m < 1e-9) return { left, right };
    const drive = 1.2, k = 0.9 / Math.tanh(drive), fade = Math.round(0.01 * SR);
    for (let i = 0; i < n; i++) {
      const f = i > n - fade ? (n - i) / fade : 1;
      left[i] = k * Math.tanh(drive * l[i] / m) * f;
      right[i] = k * Math.tanh(drive * r[i] / m) * f;
    }
    return { left, right };
  }

  const api = { SR, EVENTS, SLIDERS: S, render };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.RDSynth = api;

  // ---------------------------------------------------------------- node

  if (typeof require !== 'undefined' && typeof module !== 'undefined' && require.main === module) {
    const fs = require('fs'), path = require('path');
    const [cmd, dir, file] = process.argv.slice(2);
    const settings = file ? JSON.parse(fs.readFileSync(file, 'utf8')) : {};
    if (cmd !== 'wav' || !dir) {
      console.log('usage: node synth.js wav <out dir> [settings.json]');
      process.exit(1);
    }
    fs.mkdirSync(dir, { recursive: true });
    for (const name of Object.keys(EVENTS)) {
      const s = settings[name] || {};
      const t0 = Date.now();
      const { left, right } = render(name, s.params, s.seed);
      const n = left.length, buf = Buffer.alloc(44 + n * 4);
      buf.write('RIFF', 0); buf.writeUInt32LE(36 + n * 4, 4); buf.write('WAVEfmt ', 8);
      buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
      buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
      buf.write('data', 36); buf.writeUInt32LE(n * 4, 40);
      let bad = 0;
      for (let i = 0; i < n; i++) {
        if (!Number.isFinite(left[i]) || !Number.isFinite(right[i])) bad++;
        buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, left[i])) * 32767), 44 + i * 4);
        buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, right[i])) * 32767), 46 + i * 4);
      }
      fs.writeFileSync(path.join(dir, `grounded_${name}.wav`), buf);
      console.log(`${name.padEnd(13)} ${(n / SR).toFixed(2)} s  ${Date.now() - t0} ms to render  bad samples ${bad}`);
    }
  }
})(typeof self !== 'undefined' ? self : this);
