"""GEIST: Hellblazer - the specialist's clothes, as armour: the TRENCH COAT (chest: a tan gabardine trench worn open, with
the white shirt unbuttoned at the throat and the red tie pulled loose as part of the same piece) and the BLACK TROUSERS
(legs).

Each piece is its own ModelPart rig (see modelgen.py) drawn close to the body like a tailored suit, at 8 texels per model
pixel:
  coat      torso (coat, shirt and tie), popped collar, sleeves (wide and slim-armed cuts in the one model) with cuff
            straps, a loose belt end, and the coat's skirts hanging open from the waist to below the knee (they ride on
            the legs, so they swing as you walk; the black trousers show down the front between them)
  trousers  a waistband with a black belt, pressed black legs with a turn-up

Out come:
  src/main/java/com/grapefruit/hellblazer/client/outfit/OutfitModels.java   the layer definitions
  src/main/resources/assets/geist_hellblazer/textures/models/armor/{coat,trousers}.png
  a preview (pass a folder as the first argument)

Run: python3 tools/gen_outfit.py [preview_dir]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from modelgen import Rig, hn, mix, clamp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ARMOR = os.path.join(ROOT, "src/main/resources/assets/geist_hellblazer/textures/models/armor")
JAVA = os.path.join(ROOT, "src/main/java/com/grapefruit/hellblazer/client/outfit/OutfitModels.java")
PX = 8

# ------------------------------------------------------------------ palette
TAN = (176, 141, 96)          # the gabardine
TAN_HI = (204, 170, 123)
TAN_LO = (118, 90, 58)
TAN_SEAM = (96, 72, 46)
LINING = (92, 70, 48)
BUTTON = (58, 40, 27)
BRASS = (170, 134, 68)
SHIRT = (236, 234, 228)
SHIRT_LO = (186, 182, 174)
TIE = (168, 22, 30)
TIE_HI = (205, 52, 56)
TIE_LO = (104, 10, 18)
WOOL = (30, 30, 34)
WOOL_HI = (54, 54, 60)
WOOL_LO = (14, 14, 17)
LEATHER = (20, 18, 18)
LEATHER_HI = (92, 88, 90)
SOLE = (40, 28, 22)
CLEAR = (0, 0, 0, 0)


def lit(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def twill(c, base, amt=0.07, seed=3):
    """Gabardine: a fine diagonal rib, a little grain, a little wear."""
    px, py = c["px"], c["py"]
    rib = 1.0 + (amt if ((px + py) // 2) % 3 == 0 else -amt * 0.35)
    n = 1.0 + (hn(px, py, seed + len(c["name"])) - 0.5) * amt * 1.6
    blot = 1.0 - 0.06 * max(0.0, hn(px // 9, py // 9, seed + 41) - 0.72) / 0.28
    return lit(base, rib * n * blot)


def wool(c, base=WOOL, amt=0.08, seed=5):
    px, py = c["px"], c["py"]
    n = 1.0 + (hn(px, py, seed) - 0.5) * amt * 1.8
    w = 1.0 + (amt * 0.5 if (px + 2 * py) % 4 == 0 else 0.0)
    return lit(base, n * w)


def uv(c):
    return (c["px"] + 0.5) / c["W"], (c["py"] + 0.5) / c["H"]


def vshade(col, v, top=1.06, bottom=0.82):
    return lit(col, top + (bottom - top) * v)


def ring(c, cx, cy, r):
    """Distance in texels from (cx, cy) given in UV fractions."""
    dx = (c["px"] + 0.5) - cx * c["W"]
    dy = (c["py"] + 0.5) - cy * c["H"]
    return math.hypot(dx, dy) - r


def button(c, col, cx, cy, r=2.4):
    d = ring(c, cx, cy, r)
    if d < -0.9:
        k = 1.0 + 0.25 * ((c["py"] + 0.5) < cy * c["H"] - 0.4)
        return lit(BUTTON, k)
    if d < 0.3:
        return lit(col, 0.62)
    return None


def stitch(c, line_px, along="v", every=4):
    """True on a dashed stitch line one texel wide at line_px (texels across)."""
    p = c["px"] if along == "v" else c["py"]
    q = c["py"] if along == "v" else c["px"]
    return p == line_px and (q // 2) % 2 == 0


# ================================================================== the coat
OPEN = 0.24        # half the width of the open front (UV), where the shirt shows
BELT = (0.705, 0.775)


def shirt_and_tie(c, u, v):
    """The open-necked shirt and the loosened tie in the open front of the coat, tucked into a black belt."""
    du = u - 0.5
    # the trousers' black belt and waistband at the bottom of the open front
    if v >= 0.86:
        if v < 0.935:
            if abs(du) < 0.05 and v > 0.87:
                inner = abs(du) < 0.03 and 0.885 < v < 0.92
                return LEATHER if inner else lit((170, 172, 178), 1.0 if v < 0.9 else 0.8)
            k = 1.7 if v < 0.872 else 1.0
            return lit(LEATHER, k)
        return lit(WOOL, 0.9 + 0.1 * hn(c["px"], c["py"], 5))
    # the shirt unbuttoned at the throat: a deep V of skin (transparent - the wearer's own skin shows through)
    if v < 0.2 and abs(du) < 0.12 * (1.0 - v / 0.2) + 0.012:
        return CLEAR
    # the collar points, spread wide over the lapels' inner edge
    if v < 0.2 and 0.07 < abs(du) < 0.27 and v < 0.2 - (abs(du) - 0.07) * 0.6:
        edge = v > 0.19 - (abs(du) - 0.07) * 0.6
        return lit(SHIRT_LO if edge else SHIRT, 0.95 + 0.05 * hn(c["px"], c["py"], 9))
    # the tie, pulled loose: knotted well down the open shirt and a little askew
    tc = 0.508 + 0.012 * v
    knot_top, knot_bot = 0.2, 0.26
    if knot_top <= v < knot_bot and abs(u - tc) < 0.078 - (v - knot_top) * 0.25:
        k = 1.12 if (u - tc) < 0 else 0.92
        if v > knot_bot - 0.012:
            k *= 0.7
        return lit(TIE, k)
    if knot_bot <= v < 0.8 and abs(u - tc) < 0.052 + (v - knot_bot) * 0.055:
        x = (u - tc) / (0.052 + (v - knot_bot) * 0.055)
        k = 1.0 + 0.16 * (1.0 - abs(x + 0.35))
        if abs(x) > 0.86:
            k *= 0.72
        # a faint diagonal weave
        if (c["px"] - c["py"]) % 6 == 0:
            k *= 0.9
        return lit(TIE, k)
    # the shirt: white, creased, shadowed by the coat at its edges
    edge = 1.0 - max(0.0, (abs(du) - (OPEN - 0.07)) / 0.07) * 0.25
    crease = 0.96 if hn(c["px"] // 3, c["py"] // 7, 4) > 0.86 else 1.0
    placket = 0.88 if abs(du) < 0.012 else 1.0
    # the undone buttonholes down the placket (the buttons below the tie are done up)
    if abs(du) < 0.03 and 0.2 <= v < 0.32 and int(v * 100) % 6 == 0:
        return lit(SHIRT_LO, 0.9)
    return lit(SHIRT, edge * crease * placket * (1.0 - 0.06 * v))


def lapel_w(v):
    """Width (UV) of the lapel beside the opening at height v."""
    if v > 0.46:
        return 0.0
    w = 0.16 * (1.0 - v / 0.46) + 0.02
    # the notch where collar meets lapel
    if 0.13 < v < 0.17:
        w *= 0.55
    return w


def coat_front(c):
    u, v = uv(c)
    du = abs(u - 0.5)
    if du < OPEN:
        return shirt_and_tie(c, u, v)
    e = du - OPEN                                   # distance (UV) from the open edge
    col = twill(c, TAN)
    col = vshade(col, v, 1.05, 0.9)
    # the shadowed edge of the opening, and the lining showing as the coat falls open
    if e < 0.018:
        return lit(LINING, 0.85)
    lw = lapel_w(v)
    if e < lw:
        lk = 0.92 + 0.12 * (e / max(lw, 1e-3))
        if e > lw - 0.014:
            return lit(TAN_LO, 0.95)
        return lit(twill(c, TAN_HI, 0.05), lk)
    if lw > 0.0 and e < lw + 0.03:
        col = lit(col, 0.82 + 0.18 * (e - lw) / 0.03)  # the lapel's shadow on the coat
    # storm flap on the wearer's right chest (the viewer's left)
    if u < 0.5 and v < 0.33 and 0.31 < u < 0.5 - OPEN - lw:
        pass
    if u < 0.5 and 0.30 < v < 0.335 and u > 0.06:
        return lit(TAN_SEAM, 0.95)                  # storm flap hem
    if u < 0.5 and v < 0.30 and abs(u - 0.06) < 0.012:
        return lit(TAN_SEAM, 0.95)
    # (the coat hangs open: no belt across the front)
    if False and BELT[0] <= v < BELT[1]:
        k = 0.82
        if abs(v - BELT[0]) < 0.008 or abs(v - BELT[1] + 0.008) < 0.008:
            k = 0.68
        if 0.06 < u < 0.15 and u < 0.5:
            # buckle
            bu = (u - 0.06) / 0.09
            if bu < 0.16 or bu > 0.84 or v < BELT[0] + 0.012 or v > BELT[1] - 0.016:
                return lit(BRASS, 0.92 + 0.2 * (v < (BELT[0] + BELT[1]) / 2))
            return lit(TAN_LO, 0.9)
        return lit(twill(c, TAN, 0.04), k)

    # double-breasted buttons: three a side, near the opening
    for bv in (0.47, 0.6):
        for side in (1, -1):
            bx = 0.5 + side * (OPEN + 0.075)
            b = button(c, col, bx, bv, 2.3)
            if b is not None:
                return b
            bx2 = 0.5 + side * (OPEN + 0.24)
            b = button(c, col, bx2, bv, 2.0)
            if b is not None:
                return b
    # princess seam
    if abs(du - 0.37) < 0.006 and v > 0.34:
        return lit(col, 0.86)
    return col


def coat_back(c):
    u, v = uv(c)
    col = vshade(twill(c, TAN), v, 1.04, 0.88)
    # the yoke (the cape over the shoulders) and its shadow
    if v < 0.32:
        if v > 0.3:
            return lit(TAN_SEAM, 0.9)
        return lit(col, 1.04)
    if 0.32 <= v < 0.36:
        col = lit(col, 0.86 + (v - 0.32) * 3.0)
    if BELT[0] <= v < BELT[1]:
        k = 0.8 if not (abs(v - BELT[0]) < 0.008 or abs(v - BELT[1] + 0.008) < 0.008) else 0.66
        if abs(u - 0.5) < 0.08:
            # the back strap
            if abs(u - 0.5) > 0.065:
                return lit(TAN_SEAM, 0.9)
            return lit(twill(c, TAN_LO, 0.04), 1.0)
        return lit(twill(c, TAN, 0.04), k)
    if abs(u - 0.5) < 0.006:
        return lit(col, 0.8)                        # centre back seam
    return col


def coat_side(c):
    u, v = uv(c)
    col = vshade(twill(c, TAN), v, 1.03, 0.86)
    if False and BELT[0] <= v < BELT[1]:
        k = 0.8 if not (abs(v - BELT[0]) < 0.01 or abs(v - BELT[1] + 0.01) < 0.01) else 0.66
        return lit(twill(c, TAN, 0.04), k)
    if abs(u - 0.5) < 0.012:
        return lit(col, 0.84)                       # side seam
    return col


def coat_top(c):
    return lit(twill(c, TAN), 1.08)


def coat_torso(c):
    f = c["face"]
    if f == "+z":
        return coat_front(c)
    if f == "-z":
        return coat_back(c)
    if f in ("+x", "-x"):
        return coat_side(c)
    if f == "+y":
        return coat_top(c)
    return lit(LINING, 0.8)


def collar(c):
    f = c["face"]
    u, v = uv(c)
    col = twill(c, TAN_HI, 0.05)
    inner = (c["name"] == "collar_back" and f == "+z") or (c["name"] == "collar_side_l" and f == "-x") or (c["name"] == "collar_side_r" and f == "+x")
    if inner:
        return lit(LINING, 0.9)
    if f == "+y":
        return lit(TAN_SEAM, 1.0)
    if v < 0.12:
        return lit(col, 0.85)
    if f in ("-z", "+x", "-x", "+z") and abs(v - 0.2) < 0.04:
        return lit(col, 0.9)                        # stitch line
    return lit(col, 1.0 - 0.12 * v)


def sleeve(c):
    f = c["face"]
    u, v = uv(c)
    right = c["name"].endswith("_r")
    outer = (f == "-x" and right) or (f == "+x" and not right)
    if f == "-y":
        return CLEAR                                # the hand shows through the cuff
    # the last 1.4 px: the hand
    if v > 1.0 - 1.4 / 12.0 and f != "+y":
        return CLEAR
    col = vshade(twill(c, TAN), v, 1.06, 0.84)
    if f == "+y":
        # the epaulette across the shoulder, with its button
        if 0.3 < u < 0.7:
            if abs(u - 0.5) < 0.12 and abs(v - 0.3) < 0.12:
                return lit(BUTTON, 1.1)
            return lit(twill(c, TAN_HI, 0.04), 0.94 if (abs(u - 0.31) < 0.03 or abs(u - 0.69) < 0.03) else 1.0)
        return lit(col, 1.06)
    # the cuff strap and its buckle
    cs0, cs1 = 0.74, 0.8
    if cs0 <= v < cs1:
        if outer and 0.35 < u < 0.7:
            return lit(BRASS, 0.9 + 0.2 * (v < (cs0 + cs1) / 2))
        return lit(twill(c, TAN_LO, 0.04), 0.95 if not (abs(v - cs0) < 0.008 or abs(v - cs1 + 0.008) < 0.008) else 0.8)
    if v > cs1:
        # the turned cuff
        return lit(col, 0.94)
    # the sleeve seam on the inside of the arm
    inner = (f == "+x" and right) or (f == "-x" and not right)
    if inner and abs(u - 0.5) < 0.03:
        return lit(col, 0.84)
    # shoulder seam
    if v < 0.06:
        return lit(col, 0.9)
    return col


def tail(c):
    """The coat's skirts, hanging open: they cover each leg's outer side and back, and fall away at the front so the
    black trousers show between them (the opening widens a little toward the hem, as an open coat hangs)."""
    f = c["face"]
    u, v = uv(c)
    right = c["name"].endswith("_r")
    if f in ("+y", "-y"):
        return CLEAR
    inner = (f == "+x" and right) or (f == "-x" and not right)
    if inner:
        return CLEAR                                # between the legs: open
    col = vshade(twill(c, TAN, seed=7), v, 0.93, 0.8)
    if f == "+z":
        out = u if right else 1.0 - u               # 0 at the outer side of the leg, 1 at the inner
        edge = 0.48 - 0.16 * v                      # where the coat falls open
        if out > edge:
            return CLEAR
        if out > edge - 0.05:
            return lit(LINING, 0.8)                 # the lining at the open edge
        if out > edge - 0.08:
            return lit(TAN_LO, 0.95)
        # the slash pocket
        if 0.16 < v < 0.34 and 0.06 < out < edge - 0.12 and abs((v - 0.16) - (edge - 0.12 - out) * 0.5) < 0.022:
            return lit(TAN_SEAM, 0.9)
    if f == "-z":
        edge = (1.0 - u) if right else u            # 0 at the inner edge (the vent)
        if edge < 0.05:
            return lit(LINING, 0.75)
    if v > 0.94:
        return lit(col, 0.78)                       # the hem
    if f in ("+x", "-x") and abs(u - 0.5) < 0.03:
        return lit(col, 0.86)                       # side seam
    return col


def belt_end(c):
    u, v = uv(c)
    if v > 0.86:
        return lit(BRASS, 1.0)
    k = 0.8 if (abs(u - 0.12) < 0.08 or abs(u - 0.88) < 0.08) else 0.9
    return lit(twill(c, TAN_LO, 0.04), k)


def build_coat():
    r = Rig(64, 64, PX, seed=31)
    r.group("head")
    r.group("hat")
    r.group("body")
    r.group("right_arm", None, pivot=(-5.0, -2.0, 0.0))
    r.group("left_arm", None, pivot=(5.0, -2.0, 0.0))
    # both cuts of sleeve, one shown at a time (the model takes its arm positions from the wearer's own)
    r.group("right_sleeve", "right_arm")
    r.group("left_sleeve", "left_arm")
    r.group("right_sleeve_slim", "right_arm")
    r.group("left_sleeve_slim", "left_arm")
    r.group("right_leg", None, pivot=(-1.9, -12.0, 0.0))
    r.group("left_leg", None, pivot=(1.9, -12.0, 0.0))
    # the skirts flare out a touch from the hip, like heavy cloth
    r.group("right_skirt", "right_leg", pivot=(0.0, 0.0, 0.0), rot=(0.0, 0.0, -0.05))
    r.group("left_skirt", "left_leg", pivot=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.05))
    r.mat.update(torso=coat_torso, collar=collar, sleeve=sleeve, tail=tail, belt=belt_end)
    r.part("body", "torso", (-4, -12, -2, 8, 12, 4), "torso", grow=0.62)
    # the popped collar, standing up round the back of the neck
    r.part("body", "collar_back", (-4.6, -0.6, -2.95, 9.2, 2.6, 1.0), "collar", grow=0.0)
    r.part("body", "collar_side_l", (3.6, -0.6, -2.95, 1.0, 2.2, 3.4), "collar", grow=0.0)
    r.part("body", "collar_side_r", (-4.6, -0.6, -2.95, 1.0, 2.2, 3.4), "collar", grow=0.0)
    # the belt end hanging loose on the wearer's right
    r.part("body", "belt_end", (-4.9, -12.8, 2.0, 1.0, 4.2, 0.5), "belt")
    r.part("right_sleeve", "sleeve_r", (-3, -10, -2, 4, 12, 4), "sleeve", grow=0.5)
    r.part("left_sleeve", "sleeve_l", (-1, -10, -2, 4, 12, 4), "sleeve", grow=0.5)
    r.part("right_sleeve_slim", "slimsleeve_r", (-2, -10, -2, 3, 12, 4), "sleeve", grow=0.5)
    r.part("left_sleeve_slim", "slimsleeve_l", (-1, -10, -2, 3, 12, 4), "sleeve", grow=0.5)
    # the skirts hang to below the knee, open at the front so the black trousers show between them
    r.part("right_skirt", "skirt_r", (-2, -8.0, -2, 4, 8.0, 4), "tail", grow=0.78)
    r.part("left_skirt", "skirt_l", (-2, -8.0, -2, 4, 8.0, 4), "tail", grow=0.78)
    return r


# ================================================================== trousers and shoes
def trouser_leg(c):
    f = c["face"]
    u, v = uv(c)
    if f == "+y":
        return CLEAR
    col = wool(c)
    col = vshade(col, v, 1.0, 0.9)
    if f == "+z" and abs(u - 0.5) < 0.04:
        return lit(col, 1.35)                       # the pressed crease
    if f == "-z" and abs(u - 0.5) < 0.03:
        return lit(col, 1.18)
    right = c["name"].endswith("_r")
    outer = (f == "-x" and right) or (f == "+x" and not right)
    if outer and abs(u - 0.5) < 0.03:
        return lit(col, 0.8)                        # side seam
    if f == "+z" and v < 0.16:
        # slant pocket at the top
        edge = (1.0 - u) if right else u
        if abs((0.16 - v) - (edge - 0.65) * 0.5) < 0.012 and edge > 0.65:
            return lit(WOOL_LO, 1.0)
    if f == "-y":
        return lit(WOOL_LO, 1.0)
    # the turn-up at the hem
    if v > 0.93:
        return lit(col, 0.78)
    return col


def waist(c):
    f = c["face"]
    u, v = uv(c)
    # only the bottom third of the body box is trousers (the coat and shirt cover the rest)
    if v < 0.68 and f != "-y":
        return CLEAR
    if f == "+y":
        return CLEAR
    col = wool(c)
    # the belt
    if 0.72 <= v < 0.8:
        if f == "+z" and abs(u - 0.5) < 0.07:
            bu = abs(u - 0.5) / 0.07
            if bu > 0.7 or abs(v - 0.76) > 0.03:
                return lit((170, 172, 178), 1.0 if v < 0.76 else 0.8)
            return LEATHER
        return lit(LEATHER, 1.6 if abs(v - 0.725) < 0.006 else 1.0)
    if f == "+z" and abs(u - 0.5) < 0.012:
        return lit(col, 0.7)                        # the fly
    return col


def shoe(c):
    f = c["face"]
    u, v = uv(c)
    if f == "+y":
        return CLEAR
    if f == "-y":
        return SOLE
    # sole band
    if v > 0.86:
        return SOLE
    col = lit(LEATHER, 1.0 + 0.4 * (hn(c["px"], c["py"], 2) > 0.95))
    # a polished highlight
    if f == "+z" and 0.25 < u < 0.42 and 0.3 < v < 0.5:
        return lit(LEATHER_HI, 0.8)
    if c["name"].startswith("shoe_") and f == "+z" and v < 0.55:
        # laces up the front of the instep
        if abs(u - 0.5) < 0.22 and int((v * c["H"]) // 3) % 2 == 0:
            return lit((60, 58, 58), 1.0)
    return col


def toe(c):
    f = c["face"]
    u, v = uv(c)
    if f == "-y":
        return SOLE
    if f == "+y":
        if 0.2 < u < 0.45 and v > 0.4:
            return lit(LEATHER_HI, 0.7)
        return lit(LEATHER, 1.15)
    if v > 0.7:
        return SOLE
    return lit(LEATHER, 1.1)


def build_trousers():
    r = Rig(64, 32, PX, seed=33)
    r.group("head")
    r.group("hat")
    r.group("body")
    r.group("right_arm", None, pivot=(-5.0, -2.0, 0.0))
    r.group("left_arm", None, pivot=(5.0, -2.0, 0.0))
    r.group("right_leg", None, pivot=(-1.9, -12.0, 0.0))
    r.group("left_leg", None, pivot=(1.9, -12.0, 0.0))
    r.mat.update(waist=waist, leg=trouser_leg)
    r.part("body", "waist", (-4, -12, -2, 8, 12, 4), "waist", grow=0.34)
    r.part("right_leg", "leg_r", (-2, -12, -2, 4, 12, 4), "leg", grow=0.32)
    r.part("left_leg", "leg_l", (-2, -12, -2, 4, 12, 4), "leg", grow=0.32)
    return r


def build_shoes():
    r = Rig(32, 32, PX, seed=35)
    r.group("head")
    r.group("hat")
    r.group("body")
    r.group("right_arm", None, pivot=(-5.0, -2.0, 0.0))
    r.group("left_arm", None, pivot=(5.0, -2.0, 0.0))
    r.group("right_leg", None, pivot=(-1.9, -12.0, 0.0))
    r.group("left_leg", None, pivot=(1.9, -12.0, 0.0))
    r.mat.update(shoe=shoe, toe=toe)
    r.part("right_leg", "shoe_r", (-2, -12, -2, 4, 3, 4), "shoe", grow=0.46)
    r.part("left_leg", "shoe_l", (-2, -12, -2, 4, 3, 4), "shoe", grow=0.46)
    r.part("right_leg", "toe_r", (-1.9, -12, 2.2, 3.8, 1.5, 1.3), "toe", grow=0.2)
    r.part("left_leg", "toe_l", (-1.9, -12, 2.2, 3.8, 1.5, 1.3), "toe", grow=0.2)
    return r


# ================================================================== a mannequin for the previews
def skin(c):
    f = c["face"]
    u, v = uv(c)
    base = (198, 160, 130)
    if c["name"] == "m_head" and f == "+z":
        if 0.55 < v < 0.68 and (abs(u - 0.3) < 0.08 or abs(u - 0.7) < 0.08):
            return (40, 40, 48)
        if v < 0.3:
            return (120, 92, 52)
    if c["name"] == "m_head" and f != "+z" and v < 0.45:
        return (120, 92, 52)
    if c["name"].startswith("m_leg") or c["name"] == "m_body":
        return (70, 90, 140)
    return base


def mannequin(r):
    r.mat["skin"] = skin
    r.part("head", "m_head", (-4, 0, -4, 8, 8, 8), "skin")
    r.part("body", "m_body", (-4, -12, -2, 8, 12, 4), "skin")
    r.part("right_arm", "m_arm_r", (-3, -10, -2, 4, 12, 4), "skin")
    r.part("left_arm", "m_arm_l", (-1, -10, -2, 4, 12, 4), "skin")
    r.part("right_leg", "m_leg_r", (-2, -12, -2, 4, 12, 4), "skin")
    r.part("left_leg", "m_leg_l", (-2, -12, -2, 4, 12, 4), "skin")


def preview_all(out):
    """The whole outfit on a mannequin (one rig with everything, only for the picture)."""
    r = Rig(128, 128, PX, seed=31)
    for g, parent, piv in (("head", None, (0, 0, 0)), ("body", None, (0, 0, 0)), ("right_arm", None, (-5, -2, 0)), ("left_arm", None, (5, -2, 0)),
                           ("right_leg", None, (-1.9, -12, 0)), ("left_leg", None, (1.9, -12, 0))):
        r.group(g, parent, pivot=piv)
    r.group("right_skirt", "right_leg")
    r.group("left_skirt", "left_leg")
    mannequin(r)
    coat = build_coat()
    tr = build_trousers()
    for src in (coat, tr):
        r.mat.update(src.mat)
        for (g, n, b, m) in src.parts:
            if g.endswith("_slim"):
                continue
            g = {"right_sleeve": "right_arm", "left_sleeve": "left_arm"}.get(g, g)
            r.part(g, n, b, m, src.grow.get(n, 0.0))
    r.pack()
    r.paint()
    walk = {"right_leg": (0.45, 0, 0), "left_leg": (-0.45, 0, 0), "right_arm": (-0.4, 0, 0), "left_arm": (0.4, 0, 0)}
    r.preview(os.path.join(out, "hb_outfit.png"), [(200, 8, {}, 0), (160, 8, {}, 0), (20, 8, {}, 0), (200, 8, walk, 0), (90, 6, {}, 0)],
              size=(300, 420), zoom=10.5, center=(0, -10, 0))


def main():
    os.makedirs(ARMOR, exist_ok=True)
    coat, trousers = build_coat(), build_trousers()
    for rig in (coat, trousers):
        rig.pack()
        rig.paint()
    coat.base.save(os.path.join(ARMOR, "coat.png"))
    trousers.base.save(os.path.join(ARMOR, "trousers.png"))
    java = f"""package com.grapefruit.hellblazer.client.outfit;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The specialist's clothes as armour meshes (generated by tools/gen_outfit.py - edit that, not this). Each is a full
 * humanoid (head, hat, body, arms, legs, as HumanoidModel wants) with only the pieces that piece of clothing covers:
 * the coat's skirts hang from the legs so they swing with each step.
 */
public final class OutfitModels {{
    private OutfitModels() {{}}

{coat.layer_method("coat", "The trench coat, open over the shirt and tie (both cuts of sleeve: CoatModel shows one).")}
{trousers.layer_method("trousers", "The black trousers and belt.")}}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    open(JAVA, "w").write(java)
    print("coat uv rows", coat.rows_used, "trousers", trousers.rows_used)
    if len(sys.argv) > 1:
        preview_all(sys.argv[1])


if __name__ == "__main__":
    main()
