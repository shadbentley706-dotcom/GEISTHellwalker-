"""Sound building blocks (from GEIST): oscillators, filters, struck metal and glass, cloth, rooms, and an OGG writer."""
import json, os, subprocess
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, fftconvolve, lfilter
SR = 44100
rng = np.random.default_rng(2323)
OUT = "out_sounds"  # set by the generator
# =============================================================== building blocks
def t_(d): return np.arange(int(round(SR * d))) / SR
def noise(n): return rng.uniform(-1, 1, n)
def bp(x, lo, hi, o=2): b, a = butter(o, [max(lo, 20) / (SR / 2), min(hi, SR / 2 - 200) / (SR / 2)], btype="band"); return lfilter(b, a, x)
def hp(x, f, o=2): b, a = butter(o, f / (SR / 2), btype="high"); return lfilter(b, a, x)
def lp(x, f, o=2): b, a = butter(o, min(f, SR / 2 - 200) / (SR / 2), btype="low"); return lfilter(b, a, x)
def m2f(m): return 440.0 * 2.0 ** ((m - 69) / 12.0)


def gate(t, start, attack, decay):
    s = t - start
    return np.where(s < 0, 0.0, np.minimum(1.0, s / max(attack, 1e-6)) * np.exp(-np.clip(s, 0, None) / decay))


def window(t, start, end, fade=0.01):
    return np.clip((t - start) / fade, 0, 1) * np.clip((end - t) / fade, 0, 1)


def bump(t, start, dur, peak=0.5):
    """A smooth rise and fall over [start, start + dur], cresting at `peak` (0..1) of the way through."""
    s = np.clip((t - start) / dur, 0, 1)
    up = np.clip(s / peak, 0, 1)
    down = np.clip((1 - s) / (1 - peak), 0, 1)
    inside = (t >= start) & (t <= start + dur)
    return np.where(s < peak, np.sin(np.pi / 2 * up) ** 2, np.sin(np.pi / 2 * down) ** 2) * inside


def glide(t, f0, f1, start, dur):
    """Exponential frequency glide f0 -> f1 over [start, start + dur] (flat either side)."""
    s = np.clip((t - start) / dur, 0, 1)
    return f0 * (f1 / f0) ** s


def osc(fr, phase=0.0):
    return np.sin(2 * np.pi * np.cumsum(fr) / SR + phase)


def tone(t, f, start, dur, attack=0.004):
    return np.sin(2 * np.pi * f * np.clip(t - start, 0, None)) * gate(t, start, attack, dur)


def bell(t, f, start, decay=0.4, amp=1.0):
    parts = ((1.0, 1.0), (2.76, 0.5), (5.4, 0.25), (8.93, 0.12))
    s = np.clip(t - start, 0, None)
    return amp * sum(a * np.sin(2 * np.pi * f * k * s) * np.exp(-s / (decay / k ** 0.5)) for k, a in parts) * (t >= start)


# a cast bell: hum, prime, minor tierce, quint, nominal and the upper partials (ratio, level, decay scale)
TOLL = ((0.5, 0.55, 1.7), (1.0, 1.0, 1.0), (1.19, 0.7, 0.8), (1.5, 0.4, 0.7), (2.0, 0.8, 0.6), (2.52, 0.3, 0.45), (3.0, 0.25, 0.35),
        (4.07, 0.15, 0.25))


def toll(t, f, start, decay=3.0, amp=1.0):
    s = np.clip(t - start, 0, None)
    y = sum(a * np.sin(2 * np.pi * f * k * s + k) * np.exp(-s / (decay * d)) for k, a, d in TOLL)
    return amp * y * (t >= start) * np.minimum(1.0, s / 0.002)


def boom(t, start, f0, f1, decay, rate=14.0):
    """A pitched thump: f0 falling to f1 at `rate`, decaying."""
    f = f1 + (f0 - f1) * np.exp(-np.clip(t - start, 0, None) * rate)
    return osc(f) * gate(t, start, 0.0015, decay)


def thump(t, f0, f1, decay, start=0.0):
    return boom(t, start, f0, f1, decay, 18.0)


def click(t, start, f=3000, decay=0.004, amp=1.0):
    return amp * bp(noise(len(t)), f * 0.6, min(f * 1.6, 20000)) * gate(t, start, 0.0003, decay)


METAL = ((1.0, 1.0), (2.32, 0.6), (3.86, 0.45), (5.11, 0.3), (6.97, 0.2), (8.6, 0.12))
GUNMETAL = ((1.0, 1.0), (1.73, 0.7), (2.91, 0.5), (4.12, 0.3), (5.6, 0.2))
GLASS = ((1.0, 1.0), (2.76, 0.45), (5.4, 0.2))


def ring(f, decay, dur=None, partials=METAL):
    """A struck piece of metal (or glass), as its own short array."""
    t = t_(dur or decay * 6)
    y = np.zeros(len(t))
    for k, a in partials:
        y += a * np.sin(2 * np.pi * f * k * t + rng.uniform(0, 6.28)) * np.exp(-t / (decay / k ** 0.6))
    return y * np.minimum(1.0, t / 0.0004)


def put(dst, src, at, gain=1.0):
    """Mix `src` into `dst` starting `at` seconds in."""
    i = int(round(at * SR))
    if i >= len(dst) or i + len(src) <= 0:
        return dst
    j0 = max(0, -i)
    i0 = max(0, i)
    n = min(len(src) - j0, len(dst) - i0)
    dst[i0:i0 + n] += src[j0:j0 + n] * gain
    return dst


def saw(fr, phase=0.0):
    """Band-limited (polyBLEP) sawtooth."""
    dt = np.clip(fr / SR, 1e-6, 0.45)
    ph = (phase + np.cumsum(dt)) % 1.0
    y = 2.0 * ph - 1.0
    m = ph < dt
    s = ph[m] / dt[m]
    y[m] -= s + s - s * s - 1.0
    m = ph > 1.0 - dt
    s = (ph[m] - 1.0) / dt[m]
    y[m] -= s * s + s + s + 1.0
    return y


def square(fr, phase=0.0):
    return 0.5 * (saw(fr, phase) - saw(fr, phase + 0.5))


def crush(x, bits=5, hold=6):
    q = 2 ** bits
    y = np.round(x * q) / q
    return np.repeat(y[::hold], hold)[:len(x)]


def bandsweep(t, f0, f1, start, dur, width=0.6, bands=14):
    """Noise whose pass band glides from f0 to f1: fixed bands cross-faded as the centre passes them."""
    src = noise(len(t))
    fc = glide(t, f0, f1, start, dur)
    out = np.zeros(len(t))
    for c in np.geomspace(min(f0, f1) / 1.25, max(f0, f1) * 1.25, bands):
        w = np.exp(-(np.log(fc / c) / 0.28) ** 2)
        out += bp(src, c * (1 - width / 2), c * (1 + width / 2)) * w
    return out


def whoosh(t, start, dur, f0, f1, peak=0.5, width=0.6):
    return bandsweep(t, f0, f1, start, dur, width) * bump(t, start, dur, peak)


def friction(t, start, dur, lo, hi, grain=250, fade=0.01):
    """Something dragged: band-limited noise with a rough, grainy level."""
    tex = np.abs(lp(noise(len(t)), grain))
    tex /= np.max(tex) + 1e-9
    return bp(noise(len(t)), lo, hi) * (0.25 + tex) * window(t, start, start + dur, fade)


def cloth(t, start, dur, lo=400, hi=3500, grain=40):
    tex = np.abs(lp(noise(len(t)), grain))
    tex /= np.max(tex) + 1e-9
    return bp(noise(len(t)), lo, hi) * tex ** 1.5 * window(t, start, start + dur, 0.02)


def snapcloth(t, start, lo=1500, hi=6000, decay=0.012):
    return bp(noise(len(t)), lo, hi) * gate(t, start, 0.0005, decay)


def velcro(t, start, dur):
    g = (rng.random(len(t)) < 0.03) * rng.uniform(0.3, 1.0, len(t))
    return 3.0 * bp(g, 2000, 9000) * window(t, start, start + dur, 0.005)


def crackle(t, start, end, rate, lo, hi, decay=0.0015, shape=None):
    """Sparse discharges / crinkles: `rate` per second between start and end; `shape(time)` scales them."""
    y = np.zeros(len(t))
    count = max(1, int((end - start) * rate))
    span = int(SR * decay * 7)
    k = np.arange(span)
    grain = np.exp(-k / (SR * decay))
    for tm in rng.uniform(start, end, count):
        i = int(tm * SR)
        if i >= len(t):
            continue
        a = rng.uniform(0.25, 1.0) * (shape(tm) if shape else 1.0)
        g = rng.uniform(-1, 1, span) * grain * a
        n = min(span, len(t) - i)
        y[i:i + n] += g[:n]
    return bp(y, lo, hi)


def shards(t, start, count, lo, hi, spread=0.25, decay=(0.02, 0.12), amp=1.0, partials=GLASS):
    """Glassy pings scattered after `start` (the first ones densest)."""
    y = np.zeros(len(t))
    for _ in range(count):
        at = start + rng.exponential(spread / 3)
        put(y, ring(rng.uniform(lo, hi), rng.uniform(*decay), partials=partials), at, rng.uniform(0.2, 1.0) * amp)
    return y


def squeak(t, start, dur, f0, f1, jitter=0.04):
    """A felt tip dragged hard: stick-slip squeak with a wandering pitch."""
    n = len(t)
    wander = lp(noise(n), 40)
    wander /= np.max(np.abs(wander)) + 1e-9
    fr = glide(t, f0, f1, start, dur) * (1 + jitter * wander)
    s = np.tanh(3 * osc(fr))
    tex = np.abs(lp(noise(n), 90))
    tex /= np.max(tex) + 1e-9
    return bp(s, 700, 9000) * (0.4 + 0.6 * tex) * window(t, start, start + dur, 0.012)


def flange(x, d0, d1, mix=0.8):
    """Comb-filter sweep: the signal against itself delayed d0 -> d1 seconds."""
    idx = np.arange(len(x), dtype=np.float64)
    d = np.linspace(d0 * SR, d1 * SR, len(x))
    return x + mix * np.interp(idx - d, idx, x, left=0.0)


def nwave(t, start, dur=0.08):
    s = t - start
    return lp(np.where((s >= 0) & (s <= dur), 1 - 2 * s / dur, 0.0), 2500)


def make_ir(decay, pre=0.012, dark=5000, seed=1, early=True):
    """A synthetic room: bright noise dying fast, dark noise dying slow, a few early reflections."""
    r = np.random.default_rng(seed)
    n = int(SR * (pre + decay * 1.6))
    tt = np.arange(n) / SR
    h = 0.5 * r.standard_normal(n) * np.exp(-tt * 6.9 / (decay * 0.4)) + lp(r.standard_normal(n), dark) * np.exp(-tt * 6.9 / decay)
    h[:int(pre * SR)] = 0.0
    h /= np.sqrt(np.sum(h ** 2))
    if early:
        for d, a in ((0.0071, 0.30), (0.0113, 0.22), (0.0187, 0.18), (0.0293, 0.12), (0.0419, 0.08)):
            i = int((pre * 0.5 + d) * SR)
            if i < n:
                h[i] += a * (1 if r.random() < 0.5 else -1)
    return h / np.sqrt(np.sum(h ** 2))


def verb(x, decay=1.0, mix=0.25, pre=0.012, dark=5000, seed=7):
    return x + mix * fftconvolve(x, make_ir(decay, pre, dark, seed))[:len(x)]


def thud(dur, f, lo, hi, decay=0.02):
    """A dull object meeting the ground."""
    t = t_(dur)
    return thump(t, f * 1.4, f, decay * 1.5) + 0.7 * bp(noise(len(t)), lo, hi) * gate(t, 0.0, 0.0005, decay)


WRITTEN = {}


def save(name, x, peak=0.9, drive=1.0, event=None):
    """Normalize (on the 99.95th percentile, so one spike does not decide the level), round off the very top, write OGG."""
    x = hp(np.asarray(x, dtype=np.float64), 22)
    ref = np.percentile(np.abs(x), 99.95) + 1e-9
    x = np.tanh(x / ref * drive) / np.tanh(drive)
    x[:64] *= np.linspace(0, 1, 64)
    x[-400:] *= np.linspace(1, 0, 400)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    tmp = os.path.join(OUT, name + ".tmp.wav")
    wavfile.write(tmp, SR, (x * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-ac", "1", "-c:a", "libvorbis", "-q:a", "6",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(tmp)
    WRITTEN.setdefault(event or name, []).append(name)
    print("wrote", name, f"{len(x) / SR:.2f}s")



