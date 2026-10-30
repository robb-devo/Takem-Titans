"""
Takem Titans - sound and music generator.

Every effect is built from physical layers (sub-bass weight, body, crunch, debris, air, room) instead of single
noise bursts, and comes in several takes so repeats don't stand out. Positional effects are mono (Minecraft only
places mono sounds in the world); music is stereo.

Run from the repo root:  python tools/sound_art.py            (everything)
                         python tools/sound_art.py step roar   (only names containing these words)
"""
import os
import sys

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, 'src/main/resources/assets/takemtitans/sounds')


# ---------------------------------------------------------------------------------------------------------------------
# DSP
# ---------------------------------------------------------------------------------------------------------------------
def n_(d):
    return int(round(d * SR))


def tt(d):
    return np.arange(n_(d)) / SR


def white(d, rng):
    return rng.standard_normal(n_(d))


def brown(d, rng):
    x = np.cumsum(rng.standard_normal(n_(d)))
    x = signal.sosfilt(signal.butter(1, 20, 'high', fs=SR, output='sos'), x)
    return x / (np.abs(x).max() + 1e-9)


def pink(d, rng):
    w = rng.standard_normal(n_(d))
    b = [0.049922035, -0.095993537, 0.050612699, -0.004408786]
    a = [1, -2.494956002, 2.017265875, -0.522189400]
    return signal.lfilter(b, a, w) * 4.0


def lp(x, f, order=2):
    f = min(f, SR * 0.45)
    return signal.sosfilt(signal.butter(order, f, 'low', fs=SR, output='sos'), x)


def hp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, 'high', fs=SR, output='sos'), x)


def bp(x, lo, hi, order=2):
    hi = min(hi, SR * 0.45)
    return signal.sosfilt(signal.butter(order, [lo, hi], 'band', fs=SR, output='sos'), x)


def peak(x, f, q, gain_db):
    """RBJ peaking EQ."""
    A = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f / SR
    al = np.sin(w0) / (2 * q)
    b = [1 + al * A, -2 * np.cos(w0), 1 - al * A]
    a = [1 + al / A, -2 * np.cos(w0), 1 - al / A]
    return signal.lfilter(b, a, x)


def resonator(x, f, bw):
    """Two-pole band resonator (for formants), unity-ish peak gain."""
    r = np.exp(-np.pi * bw / SR)
    w = 2 * np.pi * f / SR
    a = [1, -2 * r * np.cos(w), r * r]
    b = [(1 - r * r) * 0.5]
    return signal.lfilter(b, a, x)


def sweep(x, freqs, kind='low', q=0.707, block=128):
    """A filter whose cutoff follows freqs (one value per sample), processed in short blocks."""
    out = np.zeros_like(x)
    zi = np.zeros(2)
    for i in range(0, len(x), block):
        f = float(np.clip(freqs[min(i, len(freqs) - 1)], 20, SR * 0.45))
        w0 = 2 * np.pi * f / SR
        al = np.sin(w0) / (2 * q)
        c = np.cos(w0)
        if kind == 'low':
            b = [(1 - c) / 2, 1 - c, (1 - c) / 2]
        elif kind == 'high':
            b = [(1 + c) / 2, -(1 + c), (1 + c) / 2]
        else:  # band, constant peak gain
            b = [al, 0, -al]
        a = [1 + al, -2 * c, 1 - al]
        b = np.array(b) / a[0]
        a = np.array(a) / a[0]
        seg, zi = signal.lfilter(b, a, x[i:i + block], zi=zi)
        out[i:i + block] = seg
    return out


def formants(src, f1, f2, f3, gains=(1.0, 0.55, 0.28), bws=(110, 150, 220), block=256):
    """Vowel filter: three resonators whose centres follow f1/f2/f3 (arrays or numbers)."""
    out = np.zeros_like(src)
    for f, g, bw in zip((f1, f2, f3), gains, bws):
        fa = np.broadcast_to(np.asarray(f, dtype=float), src.shape) if np.ndim(f) else np.full(src.shape, float(f))
        y = np.zeros_like(src)
        zi = np.zeros(2)
        for i in range(0, len(src), block):
            fc = float(fa[i])
            r = np.exp(-np.pi * bw / SR)
            w = 2 * np.pi * fc / SR
            a = [1, -2 * r * np.cos(w), r * r]
            b = [(1 - r * r)]
            seg, zi = signal.lfilter(b, a, src[i:i + block], zi=zi)
            y[i:i + block] = seg
        out += g * y
    return out


def osc(freq, kind='sine', phase0=0.0):
    f = np.asarray(freq, dtype=float)
    ph = phase0 + np.cumsum(2 * np.pi * f / SR)
    if kind == 'sine':
        return np.sin(ph)
    if kind == 'saw':
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    if kind == 'square':
        return np.sign(np.sin(ph))
    if kind == 'tri':
        return 2 * np.abs(2 * ((ph / (2 * np.pi)) % 1.0) - 1) - 1
    raise ValueError(kind)


def glide(d, f0, f1, curve='exp'):
    t = np.linspace(0, 1, n_(d))
    if curve == 'exp':
        return f0 * (f1 / f0) ** t
    return f0 + (f1 - f0) * t


def env_exp(d, tau, attack=0.002):
    t = tt(d)
    e = np.exp(-t / tau)
    if attack > 0:
        e *= np.clip(t / attack, 0, 1)
    return e


def env_ad(d, attack, release, hold=0.0, curve=2.0):
    t = tt(d)
    e = np.ones_like(t)
    e = np.where(t < attack, (t / max(attack, 1e-6)) ** 0.7, e)
    tr = t - attack - hold
    e = np.where(tr > 0, np.clip(1 - tr / max(release, 1e-6), 0, 1) ** curve, e)
    return e


def place(dst, src, at, gain=1.0):
    i = n_(at)
    if i >= len(dst):
        return dst
    m = min(len(src), len(dst) - i)
    dst[i:i + m] += src[:m] * gain
    return dst


def pad(x, d):
    out = np.zeros(n_(d))
    m = min(len(x), len(out))
    out[:m] = x[:m]
    return out


def sat(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)


def smooth_noise(d, rate, rng):
    """Slow random wobble in -1..1 at about `rate` changes per second."""
    k = max(2, int(d * rate) + 2)
    pts = rng.uniform(-1, 1, k)
    xs = np.linspace(0, d, k)
    return np.interp(tt(d), xs, pts)


def reverb(x, decay=1.2, wet=0.25, damp=5000, pre=0.012, stereo=False, seed=7):
    rng = np.random.default_rng(seed)
    L = n_(decay * 1.6)
    t = np.arange(L) / SR
    chans = []
    for c in range(2 if stereo else 1):
        ir = rng.standard_normal(L) * np.exp(-t * 6.9 / decay)
        ir = lp(ir, damp, 1)
        ir[:n_(pre)] = 0
        ir /= np.sqrt((ir ** 2).sum()) + 1e-9
        src = x[c] if (stereo and x.ndim == 2) else x
        wet_sig = signal.fftconvolve(src, ir)[:len(src)]
        chans.append(src * (1 - wet) + wet_sig * wet * 2.2)
    return np.stack(chans) if stereo else chans[0]


def debris(d, rng, count, start, spread, lo=600, hi=4500, size=0.6, decay=0.35):
    """Little impacts scattered in time: grit, chips and pebbles landing."""
    out = np.zeros(n_(d))
    times = start + rng.exponential(spread, count)
    for tm in times:
        if tm >= d - 0.05:
            continue
        L = rng.uniform(0.008, 0.04)
        f = rng.uniform(lo, hi)
        burst = rng.standard_normal(n_(L)) * env_exp(L, L * 0.3, 0.0005)
        burst = bp(burst, f * 0.7, f * 1.4, 1)
        amp = size * np.exp(-(tm - start) / max(decay, 1e-3)) * rng.uniform(0.3, 1.0)
        place(out, burst, tm, amp)
    return out


def bell(d, f, ratios, decays, amps, rng=None):
    t = tt(d)
    out = np.zeros_like(t)
    for r, dc, a in zip(ratios, decays, amps):
        out += a * np.sin(2 * np.pi * f * r * t + (rng.uniform(0, 6.28) if rng is not None else 0)) * np.exp(-t / dc)
    return out


def finish(x, peak_db=-1.5, hp_f=28):
    x = hp(x, hp_f, 2)
    m = np.abs(x).max() + 1e-9
    return x / m * 10 ** (peak_db / 20)


def fade(x, a=0.002, r=0.05):
    n = len(x) if x.ndim == 1 else x.shape[-1]
    e = np.ones(n)
    na, nr = n_(a), n_(r)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nr:
        e[-nr:] *= np.linspace(1, 0, nr)
    return x * e


def loopable(x, xfade=0.25):
    """Crossfade the tail into the head so the sound loops without a click."""
    nx = n_(xfade)
    head, tail = x[..., :nx], x[..., -nx:]
    w = np.linspace(0, 1, nx)
    blended = tail * (1 - w) + head * w
    y = x[..., nx:].copy()
    y[..., -nx:] = blended
    return y


# How loud each family plays (RMS of its audible part), measured from the 2.0 sounds the game was mixed around, so a
# new take lands at the level its old one had. Music sets the levels the MusicDirector's volumes were tuned for.
LOUDNESS = {
    'blade/break': 0.05, 'blade/draw': 0.133, 'blade/hit': 0.123, 'blade/impact': 0.337, 'blade/nape': 0.248,
    'blade/reload': 0.05, 'blade/slash': 0.165, 'block/alarm_bell': 0.134, 'block/cannon_fire': 0.3,
    'cannon/fire': 0.3, 'cannon/heavy': 0.32, 'cannon/battery': 0.34, 'cannon/reload': 0.1, 'odm/swing': 0.12, 'event/boulder_impact': 0.362, 'event/boulder_throw': 0.302, 'event/boulder_whoosh': 0.118,
    'event/building_collapse': 0.25, 'event/glass_shatter': 0.063, 'explosive/big_blast': 0.36,
    'explosive/blast_tail': 0.105, 'item/thunder_spear_blast': 0.364, 'item/thunder_spear_launch': 0.097,
    'odm/afterburner': 0.143, 'odm/equip': 0.071, 'odm/gas': 0.155, 'odm/gas_burst': 0.11, 'odm/gas_empty': 0.064,
    'odm/hook_fire': 0.058, 'odm/hook_hit': 0.133, 'odm/hook_hit_flesh': 0.187, 'odm/hook_retract': 0.085,
    'odm/meteor_slam': 0.302, 'odm/reel': 0.217, 'odm/refill': 0.115, 'odm/sonic_boom': 0.174, 'odm/wind': 0.168,
    'titan/death': 0.197, 'titan/fall': 0.352, 'titan/grab': 0.136, 'titan/groan': 0.216, 'titan/hurt': 0.271,
    'titan/roar': 0.234, 'titan/screech': 0.289, 'titan/step': 0.324,
    'story/boss_music': 0.365, 'music/battle': 0.33, 'music/danger': 0.26, 'music/explore': 0.2,
}


def active_rms(x):
    """RMS over the frames within 30 dB of the loudest one: how loud a sound is while it plays, not counting its tail."""
    m = x.mean(0) if x.ndim == 2 else x
    f = 2048
    n = len(m) // f
    if n == 0:
        return float(np.sqrt((m ** 2).mean()) + 1e-12)
    fr = np.sqrt((m[:n * f].reshape(n, f) ** 2).mean(1))
    a = fr[fr > fr.max() * 10 ** (-30 / 20)]
    return float(np.sqrt((a ** 2).mean()) + 1e-12)


def match_loudness(name, x):
    """Brings a take to its family's level; a soft limiter keeps the peaks under full scale, as the old mix did."""
    target = LOUDNESS.get(name.rstrip('0123456789'))
    if target is None:
        return x
    y = x
    for _ in range(3):
        # never more than about +4 dB a pass: a quiet take is lifted, a punchy one is not squashed flat
        g = min(target / active_rms(y), 1.6)
        y = 0.97 * np.tanh(y * g / 0.97)
    return y


def write(name, x, quality=0.5):
    """Encode with ffmpeg's libvorbis: libsndfile's own Vorbis writer overflows its stack on long buffers."""
    import subprocess
    import tempfile
    x = match_loudness(name, x)
    path = os.path.join(OUT, name + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = x.T if x.ndim == 2 else x
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        sf.write(wav, np.clip(data, -1, 1).astype(np.float32), SR, subtype='FLOAT')
        q = '%.1f' % (quality * 10)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', q, path], check=True)
    print('  %-34s %5.2fs' % (name, (x.shape[-1]) / SR))


# ---------------------------------------------------------------------------------------------------------------------
# Titans
# ---------------------------------------------------------------------------------------------------------------------
def titan_step(seed, heavy=1.0, d=1.8):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    f0 = rng.uniform(46, 56) / heavy
    sub = osc(glide(0.9, f0, f0 * 0.62)) * env_exp(0.9, 0.32 * heavy, 0.004)
    place(out, sub, 0.0, 1.0)
    body = lp(brown(0.6, rng), 260, 2) * env_exp(0.6, 0.11, 0.002)
    place(out, body, 0.0, 0.9)
    crunch = bp(white(0.25, rng), 700, 3200, 2) * env_exp(0.25, 0.045, 0.001)
    place(out, crunch, 0.004, 0.45)
    place(out, debris(d, rng, int(40 * heavy), 0.03, 0.18, 500, 3800, 0.18, 0.4), 0, 1.0)
    rumble = lp(brown(d, rng), 160, 2) * env_exp(d, 0.55 * heavy, 0.02)
    place(out, rumble, 0.0, 0.35)
    out = sat(out * 1.4, 1.6)
    out = reverb(out, 0.9, 0.14, 2500, seed=seed)
    return finish(fade(out, 0.001, 0.2))


def voice(d, f0, f1, f2, f3, rng, fry=0.35, jitter=0.04, sub=0.4, breath=0.12, grit=2.5):
    """A monstrous voice: a buzzing source (plus an octave below and vocal fry) through moving formants."""
    n = n_(d)
    jit = 1 + jitter * smooth_noise(d, 28, rng)
    f = np.asarray(f0) * jit
    src = osc(f, 'saw') + sub * osc(f * 0.5, 'square') * 0.6
    if fry:
        src *= 1 - fry * (0.5 + 0.5 * osc(np.full(n, rng.uniform(26, 38)), 'sine'))
    src += breath * white(d, rng)
    v = formants(src, f1, f2, f3)
    v = sat(v / (np.abs(v).max() + 1e-9) * 1.2, grit)
    return v


def contour(d, pts):
    """Piecewise-linear contour: pts = [(time fraction, value), ...]."""
    xs = [p[0] * d for p in pts]
    ys = [p[1] for p in pts]
    return np.interp(tt(d), xs, ys)


def titan_roar(seed, d=4.2, pitch=1.0):
    rng = np.random.default_rng(seed)
    f0 = contour(d, [(0, 70), (0.15, 96), (0.45, 112), (0.8, 84), (1, 62)]) * pitch * rng.uniform(0.92, 1.08)
    f1 = contour(d, [(0, 500), (0.2, 760), (0.7, 700), (1, 420)])
    f2 = contour(d, [(0, 900), (0.2, 1150), (0.7, 1050), (1, 780)])
    f3 = contour(d, [(0, 2300), (1, 2500)])
    v = voice(d, f0, f1, f2, f3, rng, fry=0.4, sub=0.55, breath=0.14, grit=2.2)
    # an uncanny human scream riding on top
    scream_f = contour(d, [(0, 300), (0.2, 430), (0.6, 470), (1, 260)]) * pitch * (1 + 0.025 * osc(np.full(n_(d), 5.5)))
    scream = formants(osc(scream_f, 'saw'), 800, 1300, 2700, gains=(1, 0.7, 0.4)) * 0.35
    out = v + scream / (np.abs(scream).max() + 1e-9) * 0.25
    out *= env_ad(d, 0.22, d * 0.45, hold=d * 0.35, curve=1.6)
    # chest-deep rumble under it
    out += lp(brown(d, rng), 120) * env_ad(d, 0.3, d * 0.5, d * 0.3) * 0.5
    out = lp(out, 7500, 2)
    out = reverb(out, 2.4, 0.3, 3500, seed=seed)
    return finish(fade(out, 0.01, 0.4))


def titan_groan(seed, d=4.0):
    rng = np.random.default_rng(seed)
    f0 = contour(d, [(0, 60), (0.3, 72), (0.7, 64), (1, 52)]) * rng.uniform(0.9, 1.1)
    f1 = contour(d, [(0, 360), (0.5, 450), (1, 330)])
    f2 = contour(d, [(0, 780), (0.5, 920), (1, 700)])
    v = voice(d, f0, f1, f2, 2350, rng, fry=0.6, jitter=0.06, sub=0.6, breath=0.3, grit=2.0)
    v *= env_ad(d, 0.5, d * 0.5, d * 0.25, 1.4)
    v = lp(v, 4500)
    v = reverb(v, 1.8, 0.28, 3000, seed=seed)
    return finish(fade(v, 0.02, 0.4))


def titan_hurt(seed, d=1.9):
    rng = np.random.default_rng(seed)
    f0 = contour(d, [(0, 150), (0.12, 235), (0.5, 180), (1, 110)]) * rng.uniform(0.9, 1.1)
    v = voice(d, f0, contour(d, [(0, 650), (1, 520)]), contour(d, [(0, 1750), (1, 1300)]), 2600, rng, fry=0.25, sub=0.3, breath=0.2, grit=3.5)
    v *= env_ad(d, 0.03, d * 0.7, 0.1, 1.8)
    v += lp(brown(d, rng), 200) * env_exp(d, 0.2) * 0.3
    v = reverb(v, 1.5, 0.25, 4000, seed=seed)
    return finish(fade(v, 0.002, 0.3))


def titan_death(seed, d=6.0):
    rng = np.random.default_rng(seed)
    f0 = contour(d, [(0, 135), (0.1, 150), (0.55, 70), (1, 42)])
    v = voice(d, f0, contour(d, [(0, 700), (0.6, 420), (1, 330)]), contour(d, [(0, 1150), (1, 700)]), 2400, rng, fry=0.55, sub=0.6, breath=0.3)
    v *= env_ad(d, 0.08, d * 0.55, d * 0.15, 1.3)
    out = v
    # the body hits the ground, then the steam
    place(out, titan_fall_raw(seed + 3, 3.0) * 0.8, 2.6)
    steam = hp(white(d, rng), 2500) * contour(d, [(0, 0), (0.35, 0), (0.6, 0.35), (0.85, 0.25), (1, 0)])
    steam *= 1 + 0.4 * smooth_noise(d, 9, rng)
    out = out + steam
    out = reverb(out, 2.0, 0.25, 4000, seed=seed)
    return finish(fade(out, 0.005, 0.5))


def titan_screech(seed, d=2.6):
    rng = np.random.default_rng(seed)
    f0 = contour(d, [(0, 380), (0.1, 640), (0.3, 520), (0.5, 700), (0.8, 560), (1, 300)]) * rng.uniform(0.9, 1.1)
    v = voice(d, f0, contour(d, [(0, 320), (1, 380)]), contour(d, [(0, 2300), (0.5, 2700), (1, 2100)]), 3100, rng, fry=0.15, jitter=0.08, sub=0.2, breath=0.25, grit=5.0)
    v *= 0.6 + 0.4 * osc(np.full(n_(d), 175.0))           # ring modulation: nothing living sounds like this
    v *= env_ad(d, 0.04, d * 0.4, d * 0.45, 1.5)
    v = reverb(v, 1.6, 0.28, 6000, seed=seed)
    return finish(fade(v, 0.003, 0.3))


def titan_fall_raw(seed, d=3.5):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, osc(glide(1.6, 42, 24)) * env_exp(1.6, 0.6, 0.006), 0, 1.0)
    place(out, lp(brown(1.2, rng), 300) * env_exp(1.2, 0.25, 0.004), 0, 1.0)
    place(out, bp(white(0.4, rng), 500, 2600) * env_exp(0.4, 0.07), 0.005, 0.6)
    place(out, debris(d, rng, 160, 0.02, 0.45, 300, 4200, 0.3, 0.9), 0, 1.0)
    place(out, lp(brown(d, rng), 140) * env_exp(d, 1.0, 0.05), 0, 0.5)
    place(out, hp(white(d, rng), 1800) * contour(d, [(0, 0), (0.15, 0.12), (1, 0)]), 0, 1.0)
    return sat(out, 1.5)


def titan_fall(seed, d=3.5):
    return finish(fade(reverb(titan_fall_raw(seed, d), 1.6, 0.22, 2500, seed=seed), 0.001, 0.4))


def titan_grab(seed, d=0.9):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    wet = lp(white(0.5, rng), 900) * (0.5 + 0.5 * osc(np.full(n_(0.5), 38.0))) * env_ad(0.5, 0.03, 0.4)
    place(out, wet, 0.0, 0.8)
    creak = osc(glide(0.6, 95, 70), 'saw') * env_ad(0.6, 0.05, 0.5)
    place(out, bp(creak, 200, 1600), 0.1, 0.4)
    place(out, lp(brown(0.4, rng), 200) * env_exp(0.4, 0.1), 0, 0.6)
    return finish(fade(reverb(out, 0.8, 0.15, 3000, seed=seed), 0.001, 0.15))


# ---------------------------------------------------------------------------------------------------------------------
# Blades
# ---------------------------------------------------------------------------------------------------------------------
def blade_slash(seed, d=0.42):
    rng = np.random.default_rng(seed)
    n = white(d, rng)
    f = contour(d, [(0, 1200), (0.25, rng.uniform(4800, 6200)), (1, 2200)])
    swish = sweep(n, f, 'band', q=1.8) * env_ad(d, 0.05, 0.3, 0.02, 1.5)
    out = swish * 1.2
    # the edge sings: a few thin metallic partials ringing as it passes
    base = rng.uniform(2700, 3300)
    for r, a in ((1.0, 0.12), (1.47, 0.08), (2.09, 0.06), (2.71, 0.04)):
        out += osc(np.full(n_(d), base * r)) * env_exp(d, rng.uniform(0.08, 0.18), 0.03) * a
    out = hp(out, 400)
    return finish(fade(out, 0.002, 0.08), -2.5)


def blade_hit(seed, d=0.45):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, bp(white(0.12, rng), 250, 1600) * env_exp(0.12, 0.03), 0, 1.0)
    squelch = lp(white(0.3, rng), 700) * (0.5 + 0.5 * osc(np.full(n_(0.3), 45.0))) * env_exp(0.3, 0.08)
    place(out, squelch, 0.01, 0.7)
    place(out, osc(glide(0.15, 110, 60)) * env_exp(0.15, 0.05), 0, 0.6)
    place(out, blade_slash(seed + 1, 0.3) * 0.25, 0, 1)
    return finish(fade(reverb(out, 0.5, 0.1, 4000, seed=seed), 0.001, 0.08), -2)


def blade_nape(seed, d=1.6):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    # the chop: a deep thud and a sub punch
    place(out, lp(brown(0.4, rng), 350) * env_exp(0.4, 0.09, 0.001), 0, 1.0)
    place(out, osc(glide(0.35, 85, 45)) * env_exp(0.35, 0.12, 0.002), 0, 0.9)
    # bone giving way: a cluster of sharp cracks
    for k in range(rng.integers(4, 7)):
        cr = bp(white(0.02, rng), 1500, 5000) * env_exp(0.02, 0.004, 0.0003)
        place(out, cr, 0.01 + k * rng.uniform(0.008, 0.02), rng.uniform(0.5, 0.9))
    # wet tearing
    tear = bp(white(0.5, rng), 300, 2500) * (0.6 + 0.4 * smooth_noise(0.5, 60, rng)) * env_ad(0.5, 0.02, 0.4)
    place(out, tear, 0.02, 0.6)
    # and the steam bursting out of the cut
    st_d = 1.4
    steam = hp(white(st_d, rng), 1800) * contour(st_d, [(0, 0), (0.08, 0.6), (0.4, 0.35), (1, 0)])
    place(out, steam, 0.05, 0.7)
    place(out, blade_slash(seed + 5, 0.35) * 0.4, 0, 1)
    out = sat(out, 1.4)
    out = reverb(out, 1.2, 0.18, 4500, seed=seed)
    return finish(fade(out, 0.001, 0.2))


def blade_impact(seed, d=2.2):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, lp(brown(0.6, rng), 400) * env_exp(0.6, 0.12), 0, 1.0)
    place(out, osc(glide(0.8, 70, 38)) * env_exp(0.8, 0.3, 0.003), 0, 0.9)
    place(out, bp(white(0.2, rng), 600, 3000) * env_exp(0.2, 0.04), 0, 0.5)
    place(out, lp(brown(d, rng), 150) * env_exp(d, 0.6, 0.02), 0, 0.35)
    place(out, blade_slash(seed + 2, 0.4) * 0.35, 0, 1)
    return finish(fade(reverb(sat(out, 1.4), 1.5, 0.2, 3500, seed=seed), 0.001, 0.3))


def blade_reload(seed, d=0.85):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    # spent blades out, fresh ones in: slide, click, clack
    slide = sweep(white(0.3, rng), glide(0.3, 3000, 6000), 'band', 2.0) * env_ad(0.3, 0.05, 0.2)
    place(out, slide, 0.0, 0.5)
    for at, f in ((0.32, 3200), (0.55, 2600)):
        click = bp(white(0.03, rng), f * 0.7, f * 1.6) * env_exp(0.03, 0.006, 0.0003)
        ring = osc(np.full(n_(0.2), f * 1.37)) * env_exp(0.2, 0.05) * 0.2
        place(out, click, at, 1.0)
        place(out, ring, at, 1.0)
    return finish(fade(reverb(out, 0.4, 0.1, 6000, seed=seed), 0.001, 0.05), -3)


def blade_draw(seed, d=0.9):
    rng = np.random.default_rng(seed)
    shing = sweep(white(d, rng), glide(d, 2500, 7500), 'band', 3.0) * env_ad(d, 0.15, 0.6, 0.05, 1.4)
    out = shing
    for r, a in ((1.0, 0.1), (1.51, 0.06), (2.23, 0.05)):
        out += osc(glide(d, 3100 * r, 3400 * r)) * env_ad(d, 0.2, 0.6, 0.05, 1.2) * a
    return finish(fade(reverb(out, 0.6, 0.15, 7000, seed=seed), 0.003, 0.1), -3)


def blade_break(seed, d=1.0):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, bp(white(0.05, rng), 2000, 9000) * env_exp(0.05, 0.01), 0, 1.0)
    place(out, bell(0.8, 2400, [1, 2.76, 5.4, 8.9], [0.25, 0.15, 0.08, 0.05], [0.4, 0.25, 0.15, 0.1], rng), 0.002, 1.0)
    for k in range(12):
        place(out, bell(0.15, rng.uniform(3000, 8000), [1, 2.3], [0.04, 0.02], [0.2, 0.1], rng), 0.05 + rng.exponential(0.15), 0.5)
    return finish(fade(reverb(out, 0.6, 0.15, 8000, seed=seed), 0.001, 0.1), -3)


# ---------------------------------------------------------------------------------------------------------------------
# ODM gear
# ---------------------------------------------------------------------------------------------------------------------
def hook_fire(seed, d=0.62):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    # the gas charge: a hard pneumatic thump
    place(out, bp(white(0.05, rng), 150, 1500) * env_exp(0.05, 0.012, 0.0005), 0, 1.0)
    place(out, osc(glide(0.08, 160, 90)) * env_exp(0.08, 0.02), 0, 0.6)
    # the cable paying out, a zip that climbs
    zip_ = sweep(white(0.5, rng), glide(0.5, 2200, 6500), 'band', 2.5) * env_ad(0.5, 0.01, 0.48, 0, 1.3)
    place(out, zip_, 0.01, 0.7)
    whine = osc(glide(0.45, 900, 1400), 'saw')
    place(out, bp(whine, 800, 4000) * env_ad(0.45, 0.01, 0.43, 0, 2) * 0.12, 0.01, 1)
    return finish(fade(out, 0.0005, 0.05), -2)


def hook_hit(seed, d=0.38):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, bp(white(0.012, rng), 2500, 9000) * env_exp(0.012, 0.002, 0.0002), 0, 1.0)
    place(out, bp(white(0.15, rng), 700, 3000) * env_exp(0.15, 0.025), 0.001, 0.8)
    place(out, debris(d, rng, 12, 0.01, 0.06, 1000, 5000, 0.25, 0.1), 0, 1)
    place(out, bell(0.3, rng.uniform(1700, 2100), [1, 2.4, 3.9], [0.08, 0.05, 0.03], [0.25, 0.12, 0.06], rng), 0, 1)
    return finish(fade(reverb(out, 0.5, 0.12, 5000, seed=seed), 0.0005, 0.06), -2)


def hook_hit_flesh(seed, d=0.45):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, lp(white(0.08, rng), 1200) * env_exp(0.08, 0.02), 0, 1.0)
    sq = lp(white(0.35, rng), 600) * (0.5 + 0.5 * osc(np.full(n_(0.35), 52.0))) * env_exp(0.35, 0.09)
    place(out, sq, 0.005, 0.8)
    place(out, osc(glide(0.12, 120, 70)) * env_exp(0.12, 0.04), 0, 0.5)
    return finish(fade(out, 0.0005, 0.06), -2)


def hook_retract(seed, d=0.62):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    whirr = osc(glide(0.55, 520, 160), 'saw')
    place(out, bp(whirr, 300, 3500) * env_ad(0.55, 0.02, 0.5) * 0.4, 0, 1)
    place(out, sweep(white(0.55, rng), glide(0.55, 6000, 2000), 'band', 2.0) * env_ad(0.55, 0.02, 0.5) * 0.6, 0, 1)
    place(out, bp(white(0.03, rng), 1500, 5000) * env_exp(0.03, 0.006), 0.55, 0.8)
    return finish(fade(out, 0.001, 0.05), -3)


def reel_loop(seed, d=1.2):
    rng = np.random.default_rng(seed)
    t = tt(d)
    # winch motor (frequencies chosen to repeat within the loop)
    f = 150.0
    motor = sum(a * np.sin(2 * np.pi * f * k * t) for k, a in ((1, 0.5), (2, 0.35), (3, 0.25), (5, 0.12), (7, 0.08)))
    motor *= 1 + 0.08 * np.sin(2 * np.pi * 5 * t)
    # ratchet pawl clicking at 25 per second
    clicks = np.zeros(n_(d))
    for k in range(int(d * 25)):
        c = bp(white(0.006, rng), 2500, 7000) * env_exp(0.006, 0.0015, 0.0001)
        place(clicks, c, k / 25.0, rng.uniform(0.5, 0.8))
    hiss = hp(white(d, rng), 3500) * 0.15
    out = motor * 0.25 + clicks * 0.8 + hiss
    return finish(loopable(np.concatenate([out, out[:n_(0.2)]]), 0.2), -4)


def gas_loop(seed, d=2.2):
    rng = np.random.default_rng(seed)
    jet = hp(white(d, rng), 900)
    jet = peak(jet, 4200, 0.8, 6)
    jet = lp(jet, 11000)
    turb = 1 + 0.25 * smooth_noise(d, 14, rng) + 0.1 * smooth_noise(d, 55, rng)
    out = jet * turb + lp(brown(d, rng), 400) * 0.25
    return finish(loopable(out, 0.25), -4)


def gas_burst(seed, d=0.7):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    psst = hp(white(0.6, rng), 700) * env_ad(0.6, 0.006, 0.45, 0.02, 2.0)
    psst = sweep(psst, glide(0.6, 9000, 3000), 'low')
    place(out, psst, 0, 1.0)
    place(out, lp(brown(0.12, rng), 250) * env_exp(0.12, 0.03), 0, 0.7)
    return finish(fade(out, 0.0005, 0.08), -2)


def gas_empty(seed, d=0.85):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    at = 0.0
    for k in range(5):
        L = 0.08 * (1 - k * 0.12)
        puff = hp(white(L, rng), 1200) * env_ad(L, 0.004, L * 0.8) * (1 - k * 0.18)
        place(out, puff, at, 1.0)
        at += rng.uniform(0.09, 0.15)
    place(out, bp(white(0.02, rng), 2000, 6000) * env_exp(0.02, 0.004), at + 0.05, 0.8)
    return finish(fade(out, 0.0005, 0.05), -3)


def wind_loop(seed, d=5.0):
    rng = np.random.default_rng(seed)
    w = lp(pink(d, rng), 700, 2)
    gust = 0.65 + 0.35 * smooth_noise(d, 1.5, rng)
    whistle = sweep(white(d, rng), 900 + 300 * smooth_noise(d, 0.8, rng), 'band', 8.0) * 0.4
    out = w * gust + whistle * gust ** 2
    return finish(loopable(out, 0.6), -4)


def afterburner_loop(seed, d=2.2):
    rng = np.random.default_rng(seed)
    roar = lp(brown(d, rng), 1400) * 1.0 + hp(white(d, rng), 2500) * 0.2
    crackle = debris(d, rng, int(d * 70), 0, d, 800, 5000, 0.4, 999)
    out = roar * (1 + 0.15 * smooth_noise(d, 20, rng)) + crackle * 0.5
    return finish(loopable(sat(out, 1.5), 0.25), -3)


def sonic_boom(seed, d=4.6):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    for at in (0.0, rng.uniform(0.1, 0.14)):           # the double crack of the shock waves
        place(out, white(0.006, rng) * env_exp(0.006, 0.0015), at, 1.0)
        place(out, lp(white(0.2, rng), 1800) * env_exp(0.2, 0.04), at, 0.8)
    place(out, osc(glide(1.5, 50, 28)) * env_exp(1.5, 0.5, 0.003), 0, 1.0)
    place(out, lp(brown(d, rng), 180) * env_exp(d, 1.2, 0.02), 0, 0.6)
    return finish(fade(reverb(sat(out, 1.8), 2.8, 0.35, 2000, seed=seed), 0.0005, 0.6))


def meteor_slam(seed, d=7.0):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, explosion_raw(seed, 3.0, 1.3), 0, 1.0)
    place(out, titan_fall_raw(seed + 1, 3.5), 0.02, 0.8)
    place(out, debris(d, rng, 260, 0.1, 1.0, 300, 4500, 0.35, 1.8), 0, 1.0)
    place(out, lp(brown(d, rng), 120) * env_exp(d, 2.0, 0.05), 0, 0.6)
    return finish(fade(reverb(out, 3.0, 0.3, 2200, seed=seed), 0.0005, 0.8))


def refill(seed, d=1.4):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    hiss = sweep(white(1.1, rng), glide(1.1, 1500, 7000), 'band', 1.5) * env_ad(1.1, 0.05, 0.3, 0.7)
    place(out, hiss, 0.05, 0.9)
    place(out, bp(white(0.02, rng), 1500, 5000) * env_exp(0.02, 0.004), 0.0, 0.8)
    place(out, bp(white(0.03, rng), 1200, 4000) * env_exp(0.03, 0.006), 1.2, 1.0)
    place(out, bell(0.2, 1900, [1, 2.4], [0.06, 0.03], [0.2, 0.1], rng), 1.2, 1)
    return finish(fade(out, 0.001, 0.05), -3)


def equip(seed, d=0.7):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    for k in range(4):
        at = k * rng.uniform(0.08, 0.16)
        place(out, bp(white(0.02, rng), 1200, 6000) * env_exp(0.02, 0.005), at, rng.uniform(0.5, 1))
        place(out, lp(white(0.08, rng), 900) * env_exp(0.08, 0.02), at, 0.3)
    return finish(fade(out, 0.0005, 0.05), -4)


# ---------------------------------------------------------------------------------------------------------------------
# Explosions, destruction
# ---------------------------------------------------------------------------------------------------------------------
def explosion_raw(seed, d=3.0, size=1.0):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, white(0.01, rng) * env_exp(0.01, 0.002), 0, 1.0)                       # the crack
    place(out, lp(white(0.6, rng), 2500) * env_exp(0.6, 0.08 * size, 0.0005), 0, 0.9)  # the blast body
    place(out, osc(glide(1.4 * size, 55, 26)) * env_exp(1.4 * size, 0.45 * size, 0.002), 0, 1.0)
    place(out, lp(brown(d, rng), 220) * env_exp(d, 0.7 * size, 0.01), 0, 0.7)
    place(out, debris(d, rng, int(120 * size), 0.05, 0.4 * size, 400, 5000, 0.3, 0.8 * size), 0, 1)
    return sat(out * 1.2, 2.0)


def big_blast(seed, d=3.2):
    return finish(fade(reverb(explosion_raw(seed, d, 1.0), 2.2, 0.3, 3000, seed=seed), 0.0005, 0.5))


def blast_tail(seed, d=4.5):
    rng = np.random.default_rng(seed)
    out = lp(brown(d, rng), 200) * env_ad(d, 0.4, d * 0.8, 0, 1.4)
    out += debris(d, rng, 80, 0.2, 1.0, 300, 2500, 0.2, 2.0)
    return finish(fade(reverb(out, 3.0, 0.4, 1500, seed=seed), 0.05, 0.6), -3)


def thunder_spear_blast(seed, d=3.0):
    rng = np.random.default_rng(seed)
    out = explosion_raw(seed, d, 0.9)
    # crackling charge and shrapnel singing away
    place(out, debris(d, rng, 200, 0.0, 0.25, 3000, 9000, 0.25, 0.5), 0, 1)
    for k in range(6):
        place(out, osc(glide(0.6, rng.uniform(1500, 3000), rng.uniform(600, 1200))) * env_exp(0.6, 0.2) * 0.08, rng.uniform(0.02, 0.2), 1)
    return finish(fade(reverb(out, 2.0, 0.28, 3500, seed=seed), 0.0005, 0.4))


def thunder_spear_launch(seed, d=1.1):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, bp(white(0.05, rng), 200, 2000) * env_exp(0.05, 0.012), 0, 1.0)
    fizz = sweep(white(1.0, rng), glide(1.0, 1500, 6000), 'band', 1.2) * env_ad(1.0, 0.02, 0.9, 0, 1.2)
    fizz *= 1 + 0.5 * smooth_noise(1.0, 80, rng)
    place(out, fizz, 0.01, 0.8)
    return finish(fade(out, 0.0005, 0.1), -2)


def building_collapse(seed, d=7.0):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    # it goes: a crack, a groan of timber, then the whole weight comes down in a long rolling crash
    place(out, bp(white(0.05, rng), 800, 6000) * env_exp(0.05, 0.01), 0, 1.0)
    for k in range(3):
        cre = osc(glide(0.8, rng.uniform(140, 220), rng.uniform(80, 120)), 'saw')
        place(out, bp(cre, 150, 1500) * env_ad(0.8, 0.1, 0.6) * 0.3, rng.uniform(0.0, 0.6), 1)
    swell = lp(brown(d, rng), 260) * contour(d, [(0, 0.1), (0.2, 1.0), (0.45, 0.8), (1, 0)])
    place(out, swell, 0, 1.0)
    place(out, osc(glide(2.0, 48, 30)) * env_ad(2.0, 0.3, 1.5), 0.6, 0.7)
    place(out, debris(d, rng, 700, 0.4, 1.6, 250, 4000, 0.45, 2.6), 0, 1.0)
    place(out, debris(d, rng, 160, 0.5, 1.0, 2500, 8000, 0.2, 1.8), 0, 1.0)      # splinters and tiles
    dust = hp(white(d, rng), 2000) * contour(d, [(0, 0), (0.3, 0.1), (1, 0)])
    out += dust
    return finish(fade(reverb(sat(out, 1.6), 2.4, 0.28, 2500, seed=seed), 0.0005, 0.8))


def glass_shatter(seed, d=1.5):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, hp(white(0.08, rng), 2500) * env_exp(0.08, 0.015), 0, 1.0)
    for k in range(int(rng.integers(60, 90))):
        at = rng.exponential(0.12) if k < 50 else rng.uniform(0.1, d - 0.2)
        f = rng.uniform(2200, 9500)
        tink = bell(0.3, f, [1, 1.53, 2.31], [rng.uniform(0.02, 0.09), 0.03, 0.02], [1, 0.5, 0.3], rng)
        place(out, tink, at, rng.uniform(0.05, 0.25) * (1.0 if k < 50 else 0.5))
    return finish(fade(reverb(out, 0.8, 0.15, 9000, seed=seed), 0.0005, 0.1), -2)


def boulder_impact(seed, d=5.0):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, explosion_raw(seed, 2.5, 1.1) * 0.8, 0, 1)
    place(out, titan_fall_raw(seed + 4, 3.0), 0, 0.8)
    place(out, debris(d, rng, 300, 0.05, 0.9, 300, 4000, 0.35, 1.6), 0, 1)
    return finish(fade(reverb(out, 2.6, 0.28, 2500, seed=seed), 0.0005, 0.6))


def boulder_whoosh(seed, d=2.8):
    rng = np.random.default_rng(seed)
    f = contour(d, [(0, 300), (0.5, 1400), (1, 250)])
    out = sweep(pink(d, rng), f, 'low', 1.2) * contour(d, [(0, 0), (0.5, 1), (1, 0)]) ** 1.5
    out += lp(brown(d, rng), 150) * contour(d, [(0, 0), (0.5, 0.6), (1, 0)])
    return finish(fade(out, 0.05, 0.2), -2)


def boulder_throw(seed, d=1.6):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    place(out, titan_grab(seed, 0.8) * 0.6, 0, 1)
    place(out, boulder_whoosh(seed, 1.4) * 0.8, 0.2, 1)
    place(out, lp(brown(0.4, rng), 200) * env_exp(0.4, 0.1), 0, 0.7)
    return finish(fade(out, 0.0005, 0.2))


def cannon_shot(seed, size=1.0, d=None, crack=1.0):
    """
    A muzzle-loader going off: the supersonic crack of the muzzle blast (an N-wave a few milliseconds long), the thump
    of the charge in the chest, the roar rolling out over the land with echoes off hills and walls coming back later
    and duller, and the gun itself jumping back on its carriage.
    """
    rng = np.random.default_rng(seed)
    d = d or (3.2 + 1.2 * size)
    out = np.zeros(n_(d))
    T = 0.006 * size ** 0.4
    t = tt(T)
    nwave = (1 - 2 * t / T) * np.sin(np.pi * np.clip(t / T, 0, 1)) ** 0.15
    place(out, nwave, 0.0, 1.0 * crack)
    place(out, hp(white(0.03, rng), 900) * env_exp(0.03, 0.004, 0.0002), 0.0, 0.7 * crack)
    place(out, lp(white(0.5, rng), 700 / size ** 0.3) * env_exp(0.5, 0.07 * size, 0.0008), 0.0005, 1.2)
    f0 = 68 / size ** 0.35
    place(out, osc(glide(1.2 * size, f0, f0 * 0.5)) * env_exp(1.2 * size, 0.22 * size, 0.002), 0.0, 1.1)
    roar = lp(brown(d, rng), 320) * env_ad(d, 0.05, d * 0.85, 0.05, 1.8)
    place(out, roar, 0.01, 0.55)
    body = lp(white(0.4, rng), 500) * env_exp(0.4, 0.09 * size, 0.003)
    for delay, cut, g in ((0.32, 900, 0.32), (0.74, 650, 0.22), (1.35, 450, 0.15), (2.1, 320, 0.09)):
        dl = delay * rng.uniform(0.85, 1.15)
        place(out, lp(body, cut), dl, g)
        place(out, osc(glide(0.5, f0 * 0.8, f0 * 0.5)) * env_exp(0.5, 0.15, 0.01), dl, g * 0.6)
    # the carriage slams back and the barrel rings
    place(out, lp(brown(0.25, rng), 400) * env_exp(0.25, 0.05), 0.03, 0.35)
    place(out, bell(0.6, rng.uniform(380, 460) / size ** 0.3, [1, 2.32, 3.9], [0.18, 0.1, 0.05], [0.12, 0.06, 0.03], rng), 0.02, 1)
    # the shell tearing away
    place(out, sweep(white(0.5, rng), glide(0.5, 2600, 700), 'band', 2.0) * env_ad(0.5, 0.02, 0.45), 0.05, 0.12)
    out = sat(out * 1.5, 2.2)
    out = reverb(out, 2.6, 0.22, 2200, seed=seed)
    return finish(fade(out, 0.0, 0.4))


def cannon_battery(seed):
    """Three barrels off in a ragged volley, a tenth of a second apart, under one long roar."""
    rng = np.random.default_rng(seed)
    d = 4.6
    out = np.zeros(n_(d))
    for k, at in enumerate((0.0, 0.13 + rng.uniform(-0.02, 0.02), 0.27 + rng.uniform(-0.02, 0.02))):
        place(out, cannon_shot(seed * 7 + k, 0.9, d - at), at, (1.0, 0.85, 0.9)[k])
    return finish(out)


def cannon_reload(seed, d=1.6):
    """The rammer scraping down the barrel, the charge seated with a thunk, the breech clanking home."""
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    scrape = sweep(white(0.6, rng), glide(0.6, 1800, 900), 'band', 3.0) * env_ad(0.6, 0.05, 0.5) * (0.7 + 0.3 * smooth_noise(0.6, 40, rng))
    place(out, scrape, 0.0, 0.5)
    place(out, lp(brown(0.2, rng), 300) * env_exp(0.2, 0.04), 0.62, 0.8)
    for at in (0.95, 1.05):
        place(out, bell(0.5, rng.uniform(900, 1100), [1, 2.7, 4.1], [0.12, 0.06, 0.03], [0.4, 0.2, 0.1], rng), at, 0.8)
        place(out, bp(white(0.02, rng), 1500, 6000) * env_exp(0.02, 0.004), at, 0.6)
    return finish(fade(reverb(out, 0.6, 0.12, 5000, seed=seed), 0.002, 0.1), -3)


def swing_whoosh(seed, d=0.9):
    """Air rushing past at the bottom of a swing: a band of noise sweeping up and down, a low push under it."""
    rng = np.random.default_rng(seed)
    centre = contour(d, [(0, 500), (0.45, 1500), (1, 600)]) * rng.uniform(0.9, 1.1)
    w = sweep(pink(d, rng), centre, 'band', 1.4) * contour(d, [(0, 0), (0.4, 1), (1, 0)]) ** 1.6
    low = lp(brown(d, rng), 220) * contour(d, [(0, 0), (0.45, 0.6), (1, 0)])
    return finish(fade(w + low * 0.5, 0.01, 0.1), -3)


def alarm_bell(seed, d=4.5):
    rng = np.random.default_rng(seed)
    out = np.zeros(n_(d))
    ratios = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166]
    decays = [2.5, 1.8, 1.4, 1.2, 1.0, 0.7, 0.6, 0.5, 0.3]
    amps = [0.5, 1.0, 0.6, 0.45, 0.5, 0.25, 0.2, 0.18, 0.1]
    for k, at in enumerate((0.0, 1.4, 2.8)):
        strike = bell(d - at, 440.0 * (1 - 0.003 * k), ratios, decays, amps, rng)
        place(strike, bp(white(0.02, rng), 1500, 6000) * env_exp(0.02, 0.004), 0, 0.3)
        place(out, strike, at, 1.0)
    return finish(fade(reverb(out, 2.5, 0.3, 5000, seed=seed), 0.0005, 0.6), -2)


# ---------------------------------------------------------------------------------------------------------------------
# Music
# ---------------------------------------------------------------------------------------------------------------------
A4 = 440.0
NOTE = {'C': -9, 'C#': -8, 'Db': -8, 'D': -7, 'D#': -6, 'Eb': -6, 'E': -5, 'F': -4, 'F#': -3, 'Gb': -3, 'G': -2,
        'G#': -1, 'Ab': -1, 'A': 0, 'A#': 1, 'Bb': 1, 'B': 2}


def hz(name):
    """'D3' -> frequency."""
    p, o = name[:-1], int(name[-1])
    return A4 * 2 ** ((NOTE[p] + (o - 4) * 12) / 12)


def strings(f, d, rng, bright=0.5, attack=0.6, release=1.2, vib=0.004):
    """An ensemble of detuned saws, filtered soft: a string section holding one note."""
    t = tt(d)
    out = np.zeros(len(t))
    for k in range(5):
        det = 1 + rng.uniform(-0.006, 0.006)
        v = 1 + vib * np.sin(2 * np.pi * rng.uniform(4.6, 5.6) * t + rng.uniform(0, 6))
        out += osc(f * det * v, 'saw', rng.uniform(0, 6))
    out = lp(out / 5, 600 + 2600 * bright, 2)
    return out * env_ad(d, attack, release, max(0.0, d - attack - release), 1.5)


def choir(f, d, rng, vowel='ah', attack=0.8, release=1.5):
    t = tt(d)
    src = np.zeros(len(t))
    for k in range(6):
        det = 1 + rng.uniform(-0.008, 0.008)
        v = 1 + 0.006 * np.sin(2 * np.pi * rng.uniform(4.8, 5.8) * t + rng.uniform(0, 6))
        src += osc(f * det * v, 'saw', rng.uniform(0, 6))
    src += white(d, rng) * 0.6
    fm = {'ah': (730, 1090, 2440), 'oo': (330, 870, 2240), 'eh': (530, 1840, 2480)}[vowel]
    out = formants(src / 6, *fm, gains=(1.0, 0.5, 0.25), bws=(90, 120, 160))
    out = lp(out, 5000)
    return out * env_ad(d, attack, release, max(0.0, d - attack - release), 1.4)


def harp(f, d, rng, damp=0.996):
    """Karplus-Strong plucked string."""
    N = int(SR / f)
    a = np.zeros(N + 2)
    a[0] = 1
    a[N] = -0.5 * damp
    a[N + 1] = -0.5 * damp
    x = np.zeros(n_(d))
    burst = lp(rng.standard_normal(N), 4000, 1)
    x[:N] = burst
    y = signal.lfilter([1.0], a, x)
    return y * env_ad(d, 0.001, d * 0.9, 0, 1.0)


def drum(d, rng, f0=70, f1=42, tau=0.35, skin=0.4):
    out = osc(glide(d, f0, f1)) * env_exp(d, tau, 0.002)
    out += lp(white(d, rng), 900) * env_exp(d, 0.04) * skin
    return out


def brass(f, d, rng):
    """A low brass stab, bright at the attack and closing down - the 'braam'."""
    t = tt(d)
    src = sum(osc(f * (1 + rng.uniform(-0.004, 0.004)), 'saw', rng.uniform(0, 6)) for _ in range(4)) / 4
    src += osc(f * 0.5, 'saw') * 0.5
    cut = 300 + 3500 * np.exp(-t / 0.25)
    out = sweep(src, cut, 'low', 1.1)
    return sat(out * env_ad(d, 0.03, d * 0.7, d * 0.2, 1.3), 2.0)


def stereo(x, pan=0.0, width_delay=0.0):
    l = x * np.sqrt(0.5 * (1 - pan))
    r = x * np.sqrt(0.5 * (1 + pan))
    if width_delay:
        r = np.concatenate([np.zeros(n_(width_delay)), r])[:len(r)]
    return np.stack([l, r])


def mix_into(bus, part, at, gain=1.0, pan=0.0, width=0.0):
    s = stereo(part, pan, width) * gain
    i = n_(at)
    if i >= bus.shape[1]:
        return
    m = min(s.shape[1], bus.shape[1] - i)
    bus[:, i:i + m] += s[:, :m]


CHORDS = {
    'Dm': ['D3', 'A3', 'D4', 'F4'], 'Bb': ['Bb2', 'F3', 'Bb3', 'D4'], 'F': ['F2', 'C3', 'A3', 'C4'],
    'C': ['C3', 'G3', 'C4', 'E4'], 'Gm': ['G2', 'D3', 'G3', 'Bb3'], 'A': ['A2', 'E3', 'A3', 'C#4'],
    'Am': ['A2', 'E3', 'A3', 'C4'], 'Eb': ['Eb3', 'Bb3', 'Eb4', 'G4'],
}


def music_explore(seed=1):
    """Calm and a little sad: strings and a distant choir under a slow harp, the walls on the horizon."""
    rng = np.random.default_rng(seed)
    bpm = 66
    beat = 60 / bpm
    bar = beat * 4
    prog = ['Dm', 'Bb', 'F', 'C', 'Dm', 'Gm', 'Bb', 'A'] * 2
    d = bar * len(prog)
    bus = np.zeros((2, n_(d + 4)))
    melody = ['A4', 'F4', 'E4', 'D4', 'F4', 'G4', 'F4', 'E4', 'A4', 'D5', 'C5', 'A4', 'F4', 'G4', 'A4', 'E4']
    for i, ch in enumerate(prog):
        at = i * bar
        for j, note in enumerate(CHORDS[ch]):
            mix_into(bus, strings(hz(note), bar + 1.2, rng, bright=0.25, attack=1.0, release=1.6), at, 0.11, pan=(j - 1.5) * 0.35, width=0.012)
        mix_into(bus, choir(hz(CHORDS[ch][2]) , bar + 1.0, rng, 'oo', 1.2, 1.5), at, 0.06, pan=0.2)
        # harp: a rising broken chord on each bar
        for k in range(8):
            note = CHORDS[ch][(k % 4)]
            f = hz(note) * (2 if k >= 4 else 1)
            mix_into(bus, harp(f, 2.5, rng), at + k * beat / 2, 0.09 * (0.8 + 0.2 * rng.random()), pan=-0.3 + 0.08 * k)
        # a lone melody on a softer harp every other bar
        if i % 2 == 0:
            mix_into(bus, harp(hz(melody[i % len(melody)]) * 2, 3.5, rng, 0.998), at + beat * 2, 0.12, pan=0.25)
    bus = reverb(bus, 3.5, 0.42, 4500, stereo=True, seed=seed)
    return bus[:, :n_(d)]


def music_danger(seed=2):
    """Titans near: a held low drone, a heartbeat drum, strings shivering on a semitone."""
    rng = np.random.default_rng(seed)
    bpm = 72
    beat = 60 / bpm
    bar = beat * 4
    bars = 16
    d = bar * bars
    bus = np.zeros((2, n_(d + 4)))
    for i in range(0, bars, 4):
        at = i * bar
        for note, pan in (('D2', -0.2), ('A2', 0.2), ('D3', 0.0)):
            mix_into(bus, strings(hz(note), bar * 4 + 1.5, rng, bright=0.15, attack=2.0, release=2.0), at, 0.14, pan)
        tense = 'Eb3' if (i // 4) % 2 == 0 else 'C#3'
        trem = strings(hz(tense), bar * 4, rng, bright=0.35, attack=1.5, release=1.5)
        trem *= 0.6 + 0.4 * np.sin(2 * np.pi * 11 * tt(bar * 4))
        mix_into(bus, trem, at + bar, 0.07, 0.4)
        mix_into(bus, choir(hz('D4'), bar * 4, rng, 'oo', 2.5, 2.5), at, 0.05, -0.3)
    for i in range(bars):
        for b in (0, 0.42):
            mix_into(bus, drum(1.2, rng, 62, 40, 0.3, 0.2), i * bar + b * beat, 0.35 if b == 0 else 0.22)
        if i % 4 == 3:
            # a low swell into the next phrase
            sw = lp(brown(bar, rng), 300) * contour(bar, [(0, 0), (0.95, 1), (1, 0)])
            mix_into(bus, sw, i * bar, 0.2)
    bus = reverb(bus, 3.0, 0.35, 3500, stereo=True, seed=seed)
    return bus[:, :n_(d)]


def war_drums(bus, d, bar, beat, rng, gain=1.0):
    pattern = [(0, 1.0), (0.75, 0.5), (1.5, 0.8), (2, 1.0), (2.5, 0.45), (3, 0.9), (3.5, 0.6), (3.75, 0.5)]
    bars = int(round(d / bar))
    for i in range(bars):
        for pos, v in pattern:
            mix_into(bus, drum(1.0, rng, 78, 44, 0.28, 0.5), i * bar + pos * beat, 0.42 * v * gain, pan=rng.uniform(-0.2, 0.2))
        if i % 2 == 1:
            for k in range(4):   # a roll of smaller drums
                mix_into(bus, drum(0.4, rng, 180, 120, 0.08, 0.8), i * bar + 3 * beat + k * beat / 4, 0.12 * gain, pan=0.4)


def music_battle(seed=3, boss=False):
    """Fighting: war drums, braams on every chord, strings driving sixteenths, the choir over everything."""
    rng = np.random.default_rng(seed)
    bpm = 132 if boss else 118
    beat = 60 / bpm
    bar = beat * 4
    prog = ['Dm', 'Bb', 'C', 'A', 'Dm', 'Bb', 'Gm', 'A'] if not boss else ['Dm', 'Eb', 'Dm', 'A', 'Gm', 'Eb', 'C', 'A']
    prog = prog * 2
    d = bar * len(prog)
    bus = np.zeros((2, n_(d + 4)))
    war_drums(bus, d, bar, beat, rng, 1.2 if boss else 1.0)
    for i, ch in enumerate(prog):
        at = i * bar
        notes = CHORDS[ch]
        mix_into(bus, brass(hz(notes[0]), bar * 0.9, rng), at, 0.22 if boss else 0.18)
        if i % 2 == 1 or boss:
            mix_into(bus, brass(hz(notes[1]), bar * 0.45, rng), at + beat * 2.5, 0.12)
        # ostinato: root, third, fifth in sixteenths, accents on the beat
        cyc = [notes[1], notes[2], notes[3], notes[2]]
        for k in range(16):
            f = hz(cyc[k % 4]) * (2 if k % 8 >= 4 else 1)
            s = strings(f, beat * 0.3, rng, bright=0.7, attack=0.01, release=0.2, vib=0.0)
            mix_into(bus, s, at + k * beat / 4, 0.05 * (1.4 if k % 4 == 0 else 1.0), pan=-0.35 if k % 2 else 0.35)
        mix_into(bus, choir(hz(notes[2]) * 2, bar + 0.8, rng, 'ah', 0.3, 0.9), at, 0.07 if not boss else 0.1, pan=-0.2)
        mix_into(bus, choir(hz(notes[3]) * 2, bar + 0.8, rng, 'ah', 0.3, 0.9), at, 0.05 if not boss else 0.08, pan=0.2)
        if i % 4 == 3:
            sw = hp(white(bar, rng), 3000) * contour(bar, [(0, 0), (0.98, 1), (1, 0)]) ** 2
            mix_into(bus, sw, at, 0.08)   # a cymbal swell into the next phrase
    bus = reverb(bus, 2.6, 0.3, 5000, stereo=True, seed=seed)
    return bus[:, :n_(d)]


def finish_music(bus, peak_db=-1.0):
    bus = np.stack([hp(c, 30) for c in bus])
    # gentle glue compression: soft-knee tanh on the sum
    m = np.abs(bus).max() + 1e-9
    bus = np.tanh(bus / m * 1.3) / np.tanh(1.3)
    return bus * 10 ** (peak_db / 20)


# ---------------------------------------------------------------------------------------------------------------------
JOBS = [
    ('titan/step%d', [lambda k: titan_step(100 + k)] * 4),
    ('titan/roar%d', [lambda k: titan_roar(200 + k, pitch=[1.0, 0.86, 1.12][k - 1])] * 3),
    ('titan/groan%d', [lambda k: titan_groan(300 + k)] * 3),
    ('titan/hurt%d', [lambda k: titan_hurt(400 + k)] * 2),
    ('titan/death%d', [lambda k: titan_death(500 + k)] * 2),
    ('titan/screech%d', [lambda k: titan_screech(600 + k)] * 2),
    ('titan/fall%d', [lambda k: titan_fall(700 + k)] * 1),
    ('titan/grab%d', [lambda k: titan_grab(800 + k)] * 1),
    ('blade/slash%d', [lambda k: blade_slash(900 + k)] * 4),
    ('blade/hit%d', [lambda k: blade_hit(1000 + k)] * 3),
    ('blade/nape%d', [lambda k: blade_nape(1100 + k)] * 2),
    ('blade/impact%d', [lambda k: blade_impact(1200 + k)] * 3),
    ('blade/reload%d', [lambda k: blade_reload(1300 + k)] * 2),
    ('blade/draw%d', [lambda k: blade_draw(1400 + k)] * 1),
    ('blade/break%d', [lambda k: blade_break(1500 + k)] * 1),
    ('odm/hook_fire%d', [lambda k: hook_fire(1600 + k)] * 3),
    ('odm/hook_hit%d', [lambda k: hook_hit(1700 + k)] * 3),
    ('odm/hook_hit_flesh%d', [lambda k: hook_hit_flesh(1800 + k)] * 2),
    ('odm/hook_retract%d', [lambda k: hook_retract(1900 + k)] * 2),
    ('odm/reel', [lambda k: reel_loop(2000)]),
    ('odm/gas', [lambda k: gas_loop(2100)]),
    ('odm/gas_burst%d', [lambda k: gas_burst(2200 + k)] * 3),
    ('odm/gas_empty%d', [lambda k: gas_empty(2300 + k)] * 2),
    ('odm/wind', [lambda k: wind_loop(2400)]),
    ('odm/afterburner', [lambda k: afterburner_loop(2500)]),
    ('odm/sonic_boom%d', [lambda k: sonic_boom(2600 + k)] * 2),
    ('odm/meteor_slam%d', [lambda k: meteor_slam(2700 + k)] * 2),
    ('odm/refill%d', [lambda k: refill(2800 + k)] * 1),
    ('odm/equip%d', [lambda k: equip(2900 + k)] * 2),
    ('explosive/big_blast%d', [lambda k: big_blast(3000 + k)] * 3),
    ('explosive/blast_tail%d', [lambda k: blast_tail(3100 + k)] * 1),
    ('item/thunder_spear_blast%d', [lambda k: thunder_spear_blast(3200 + k)] * 2),
    ('item/thunder_spear_launch%d', [lambda k: thunder_spear_launch(3300 + k)] * 2),
    ('event/building_collapse%d', [lambda k: building_collapse(3400 + k)] * 3),
    ('event/glass_shatter%d', [lambda k: glass_shatter(3500 + k)] * 3),
    ('event/boulder_impact%d', [lambda k: boulder_impact(3600 + k)] * 3),
    ('event/boulder_whoosh%d', [lambda k: boulder_whoosh(3700 + k)] * 2),
    ('event/boulder_throw%d', [lambda k: boulder_throw(3800 + k)] * 1),
    ('cannon/fire%d', [lambda k: cannon_shot(3900 + k, 1.0)] * 2),
    ('block/cannon_fire%d', [lambda k: cannon_shot(3950 + k, 1.0)] * 3),
    ('cannon/heavy%d', [lambda k: cannon_shot(4050 + k, 1.45)] * 2),
    ('cannon/battery%d', [lambda k: cannon_battery(4150 + k)] * 2),
    ('cannon/reload%d', [lambda k: cannon_reload(4250 + k)] * 1),
    ('odm/swing%d', [lambda k: swing_whoosh(4350 + k)] * 3),
    ('block/alarm_bell%d', [lambda k: alarm_bell(4000 + k)] * 1),
]

MUSIC = [
    ('music/explore', lambda: music_explore(1)),
    ('music/danger', lambda: music_danger(2)),
    ('music/battle', lambda: music_battle(3)),
    ('story/boss_music', lambda: music_battle(4, boss=True)),
]


def main():
    words = sys.argv[1:]
    for pattern, makers in JOBS:
        if words and not any(w in pattern for w in words):
            continue
        for k, mk in enumerate(makers, start=1):
            name = pattern % k if '%d' in pattern else pattern
            write(name, mk(k))
    for name, mk in MUSIC:
        if words and not any(w in name for w in words):
            continue
        write(name, finish_music(mk()), quality=0.4)


if __name__ == '__main__':
    main()
