"""
GEIST: Hellblazer - the casebook's sounds, synthesized (numpy/scipy + ffmpeg). Run from the project root:
    python3 tools/gen_sounds.py
Writes the .ogg files into assets/geist_hellblazer/sounds/ and sounds.json beside them.

An old set with dark red glass and a crack through it: it comes on with a thump and a hum, the glass is struck,
pages turn, a stamp comes down, a church bell for a closed case, and for a promotion a low choir and something burning.
"""
import json, os, sys
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sndlib
from sndlib import *  # noqa: F401,F403

M = "geist_hellblazer"
DIR = os.path.join("src/main/resources/assets", M, "sounds")
os.makedirs(DIR, exist_ok=True)
sndlib.OUT = DIR
rng = np.random.default_rng(666)
sndlib.rng = rng


def hum(t, start, end, f=50.0, amp=1.0):
    """Mains hum with its buzz: the set's transformer."""
    y = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * 2 * f * t) + 0.25 * np.tanh(4 * np.sin(2 * np.pi * 3 * f * t))
    return amp * lp(y, 900) * window(t, start, end, 0.04)


def glass_crack(t, start, amp=1.0, size=1.0):
    """A pane struck: the hit, the split running out (crackle), then loose bits pinging."""
    y = 1.6 * click(t, start, 5200, 0.003)
    y += 0.9 * bp(noise(len(t)), 1500, 9000) * gate(t, start, 0.0003, 0.02 * size)
    y += 1.4 * crackle(t, start + 0.004, start + 0.12 * size, 900, 2500, 12000, 0.0009, shape=lambda tm: np.exp(-(tm - start) / (0.06 * size)))
    y += 0.5 * crackle(t, start + 0.03, start + 0.4 * size, 160, 3000, 11000, 0.0012, shape=lambda tm: np.exp(-(tm - start) / (0.15 * size)))
    y += 0.35 * shards(t, start + 0.02, int(14 * size), 2600, 6800, spread=0.3 * size, decay=(0.015, 0.08))
    return amp * y


def choir(t, root, start, dur, notes=(0, 7, 12, 15), vowel=((700, 1220), (390, 2300)), amp=1.0):
    """A low choir: detuned saws through two formants that drift from one vowel to the next."""
    y = np.zeros(len(t))
    for i, n in enumerate(notes):
        f = root * 2 ** (n / 12.0)
        for d in (-0.006, 0.0, 0.007):
            vib = 1 + 0.004 * np.sin(2 * np.pi * (5.1 + i * 0.3) * t + i)
            y += saw(np.full(len(t), f * (1 + d)) * vib, rng.random()) / (1 + i * 0.3)
    k = np.clip((t - start) / dur, 0, 1)
    out = np.zeros(len(t))
    for (a1, a2), w in ((vowel[0], 1 - k), (vowel[1], k)):
        out += (bp(y, a1 * 0.85, a1 * 1.15) + 0.6 * bp(y, a2 * 0.9, a2 * 1.1)) * w
    return amp * out * bump(t, start, dur, 0.45)


def burn(t, start, dur, amp=1.0):
    """Something catching: a soft roar and the crackle of it."""
    y = 0.5 * lp(noise(len(t)), 700) * bump(t, start, dur, 0.4)
    y += crackle(t, start, start + dur, 70, 1500, 9000, 0.0015, shape=lambda tm: np.sin(np.pi * np.clip((tm - start) / dur, 0, 1)))
    return amp * y


# ---------------------------------------------------------------- the set coming on: thump, hum, the glass struck
def case_open():
    t = t_(1.7)
    y = 1.4 * thump(t, 110, 42, 0.18)
    y += 0.25 * hum(t, 0.02, 1.6) * np.clip(t / 0.25, 0, 1) * np.exp(-np.clip(t - 0.6, 0, None) / 0.6)
    y += 0.05 * np.sin(2 * np.pi * 15600 * t) * window(t, 0.03, 1.2, 0.2)          # the tube whining up
    y += 0.3 * whoosh(t, 0.0, 0.3, 300, 3000, 0.7)                                       # the picture opening
    y += glass_crack(t, 0.25, 0.9, 1.0)
    y += 0.45 * toll(t, m2f(38), 0.27, 1.2, 1.0)                                         # far off, a low bell
    y += 0.12 * choir(t, m2f(38), 0.3, 1.3, (0, 7, 12), amp=1.0)
    save("case_open", verb(y, 1.3, 0.35, dark=3500), drive=1.4)


def case_close():
    t = t_(0.7)
    fr = glide(t, 900, 60, 0.0, 0.35)
    y = 0.35 * osc(fr) * gate(t, 0.0, 0.002, 0.16)
    y += 0.9 * thump(t, 90, 40, 0.12, 0.02)
    y += 0.2 * hum(t, 0.0, 0.18)
    y += 0.25 * bp(noise(len(t)), 2000, 9000) * gate(t, 0.0, 0.001, 0.05)
    y += 0.15 * crackle(t, 0.05, 0.45, 60, 2000, 8000)
    save("case_close", verb(y, 0.7, 0.25, dark=3000))


def case_click():
    t = t_(0.12)
    y = click(t, 0.0, 2600, 0.004) + 0.6 * click(t, 0.008, 5200, 0.002)
    y += 0.4 * thump(t, 300, 140, 0.015)
    put(y, ring(3900, 0.03, 0.1, partials=GLASS), 0.0, 0.15)
    save("case_click", verb(y, 0.3, 0.15), peak=0.8)


def case_hover():
    t = t_(0.06)
    y = click(t, 0.0, 6200, 0.0018) + 0.3 * tone(t, 2400, 0.0, 0.01)
    save("case_hover", y, peak=0.55)


def case_tab():
    t = t_(0.4)
    y = 1.1 * friction(t, 0.0, 0.2, 900, 6000, 120, 0.03) * np.exp(-t / 0.12)      # a page across a page
    y += 0.5 * snapcloth(t, 0.16, 1500, 7000, 0.02)
    y += 0.6 * thump(t, 180, 80, 0.05, 0.17)
    save("case_tab", verb(y, 0.4, 0.2), peak=0.8)


def case_type():
    for i in range(3):
        t = t_(0.07)
        f = 2200 + 500 * i
        y = 0.8 * click(t, 0.0, f, 0.003) + 0.5 * friction(t, 0.004, 0.04, 2500, 9000, 400, 0.004) * np.exp(-t / 0.02)
        y += 0.25 * thump(t, 260 + 40 * i, 120, 0.01)
        save(f"case_type_{i + 1}", y, peak=0.6, event="case_type")


def case_stamp():
    t = t_(0.8)
    y = 1.6 * thump(t, 140, 55, 0.09)                                                  # the stamp meeting the desk
    y += 0.9 * bp(noise(len(t)), 120, 1800) * gate(t, 0.0, 0.0005, 0.03)
    put(y, ring(420, 0.06, 0.4, partials=((1.0, 1.0), (2.3, 0.4), (3.9, 0.2))), 0.0, 0.5)   # the wood
    y += 0.3 * click(t, 0.0, 3000, 0.004)
    save("case_stamp", verb(y, 0.9, 0.35, dark=3000), drive=1.5)


def case_deny():
    t = t_(0.38)
    fr = np.where(t < 0.14, 170.0, 130.0)
    y = np.tanh(3 * square(fr)) * (window(t, 0.0, 0.12, 0.004) + window(t, 0.15, 0.33, 0.004))
    y = lp(y, 2400) + 0.3 * crackle(t, 0.0, 0.33, 120, 1500, 6000)
    save("case_deny", verb(y, 0.4, 0.15), peak=0.7)


def case_closed():
    t = t_(2.6)
    y = 1.0 * case_stamp_hit(t, 0.0)
    y += 0.9 * toll(t, m2f(50), 0.12, 2.6)                                             # the bell
    y += 0.5 * toll(t, m2f(57), 0.12, 2.0, 0.6)
    y += 0.22 * choir(t, m2f(38), 0.18, 2.2, (0, 7, 12, 16), ((390, 2300), (600, 1000)))   # resolving, major
    save("case_closed", verb(y, 2.0, 0.35, dark=3500), drive=1.3)


def case_stamp_hit(t, at):
    return 1.2 * thump(t, 140, 55, 0.08, at) + 0.6 * bp(noise(len(t)), 120, 1800) * gate(t, at, 0.0005, 0.03)


def case_failed():
    t = t_(2.6)
    y = glass_crack(t, 0.0, 0.8, 1.4)
    fr1 = glide(t, m2f(45), m2f(38), 0.05, 2.0)
    fr2 = glide(t, m2f(46), m2f(39), 0.05, 2.0)                                        # a semitone off: wrong
    y += 0.25 * lp(saw(fr1) + saw(fr2), 1200) * bump(t, 0.05, 2.4, 0.2)
    y += 0.5 * toll(t, m2f(37), 0.05, 1.8, 0.8)
    y += 0.35 * whoosh(t, 1.0, 0.9, 2500, 300, 0.3)                                      # a candle going out
    save("case_failed", verb(y, 1.8, 0.35, dark=3000), drive=1.3)


def rank_up():
    t = t_(3.6)
    y = 0.35 * choir(t, m2f(36), 0.0, 3.4, (0, 7, 12, 15, 19), ((700, 1220), (300, 870)))
    y += 0.25 * choir(t, m2f(48), 0.6, 2.8, (0, 7, 12), ((390, 2300), (700, 1220)))
    y += 0.6 * burn(t, 0.1, 1.6)                                                        # the sigil catching
    y += 1.2 * thump(t, 80, 34, 0.5, 0.42)
    y += glass_crack(t, 0.45, 0.8, 1.2)
    y += 1.0 * toll(t, m2f(36), 0.45, 3.0)
    y += 0.5 * toll(t, m2f(43), 0.9, 2.4, 0.7)
    save("rank_up", verb(y, 2.4, 0.4, dark=3200), drive=1.4)


SUBS = {"case_open", "case_close", "case_click", "case_hover", "case_tab", "case_type", "case_stamp", "case_deny", "case_closed", "case_failed",
        "rank_up"}

if __name__ == "__main__":
    for fn in (case_open, case_close, case_click, case_hover, case_tab, case_type, case_stamp, case_deny, case_closed, case_failed, rank_up):
        fn()
    sounds = {}
    for event, names in sorted(sndlib.WRITTEN.items()):
        sounds[event] = {"category": "master", "subtitle": f"subtitles.{M}.{event}", "sounds": [f"{M}:{n}" for n in names]}
    assert set(sounds) == SUBS, set(sounds) ^ SUBS
    with open(os.path.join("src/main/resources/assets", M, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    print(len(sounds), "events")
