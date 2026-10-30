"""
Takem Titans - blade models and HD item icons.

* The held blades: a 3D model per tier (pommel, leather grip, trigger, blade housing with its clamp, and a long
  scored blade with a bright cutting edge and the oblique box-cutter tip) plus a broken one, all on one 128px
  texture per tier.
* (The 32px icons are drawn by tools/icon_art.py.)

Run from the repo root:  python tools/item_art.py
"""
import json
import math
import os

import numpy as np
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, 'src/main/resources/assets/takemtitans')
TEX = os.path.join(ASSETS, 'textures/item')
MODELS = os.path.join(ASSETS, 'models/item')


def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255.0


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def noise2(w, h, scale, seed):
    rng = np.random.default_rng(seed)
    gw, gh = int(w / scale) + 2, int(h / scale) + 2
    g = rng.random((gh, gw))
    ys, xs = np.mgrid[0:h, 0:w] / scale
    x0, y0 = np.floor(xs).astype(int), np.floor(ys).astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


# ---------------------------------------------------------------------------------------------------------------------
# Tiers
# ---------------------------------------------------------------------------------------------------------------------
class Tier:
    def __init__(self, item, steel, steel_dark, edge, grip, metal, accent, glow=None, teeth=False, broken_item=None):
        self.item = item
        self.steel, self.steel_dark, self.edge = rgb(steel), rgb(steel_dark), rgb(edge)
        self.grip, self.metal, self.accent = rgb(grip), rgb(metal), rgb(accent)
        self.glow = rgb(glow) if glow else None
        self.teeth = teeth


TIERS = [
    Tier('odm', 'c9cdd2', '7c838c', 'f4f7fa', '2b2522', '5d6168', '8c6a3c'),
    Tier('veteran', 'c4d6e8', '6f8297', 'f2f9ff', '3a1f1c', '4f5560', 'b23a30'),
    Tier('serrated', 'd9a3a0', '8a4844', 'ffe6e0', '1d1a1a', '3b3d42', 'd33a2c', teeth=True),
    Tier('ackerman', '4a4d55', '1d1f24', 'ff5a4a', '161414', '2a2b30', 'b0201a'),
    Tier('titanbreaker', 'e2bb5c', '8f6a22', 'fff1c2', '1e2a3c', '3c4250', '49c6ff', glow='7fdcff'),
    Tier('founder', 'b79cf0', '5d3fa8', 'f4ecff', '23173a', '3a2a5a', 'e7c45b', glow='c7a8ff'),
]


# ---------------------------------------------------------------------------------------------------------------------
# The held blade: model + 128px texture
# ---------------------------------------------------------------------------------------------------------------------
T = 128                       # texture size
U = T / 16.0                  # pixels per UV unit

# UV regions (u0, v0, u1, v1) in 0..16 units
UV = {
    'blade': (0, 0, 3, 16), 'blade_side': (3.25, 0, 3.5, 16), 'blade_end': (3.25, 0, 3.5, 0.25),
    'grip': (5, 0, 6.3, 5.5), 'grip_end': (5, 6, 6.3, 7.3),
    'pommel': (7, 0, 8.6, 1.2), 'pommel_end': (7, 2, 8.6, 3.6),
    'trigger': (9, 0, 9.8, 1.4), 'trigger_end': (9, 2, 9.8, 2.5),
    'housing': (10, 0, 13.4, 2.7), 'housing_side': (10, 3, 12.2, 5.7), 'housing_end': (10, 6, 13.4, 8.2),
    'clamp': (5, 8, 8.8, 8.6), 'clamp_side': (5, 9, 7.4, 9.6), 'clamp_end': (5, 10, 8.8, 12.4),
}

BLADE_X0, BLADE_X1 = 6.8, 9.9
BLADE_Y0 = 8.2
BLADE_Y1 = 31.8
BROKEN_Y1 = 15.6


def box(frm, to, front, side, end, mirror_back=True):
    f = {
        'north': {'uv': list(front), 'texture': '#t'},
        'south': {'uv': [front[2], front[1], front[0], front[3]] if mirror_back else list(front), 'texture': '#t'},
        'east': {'uv': list(side), 'texture': '#t'},
        'west': {'uv': list(side), 'texture': '#t'},
        'up': {'uv': list(end), 'texture': '#t'},
        'down': {'uv': list(end), 'texture': '#t'},
    }
    return {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': f}


def blade_model(tex, broken):
    y1 = BROKEN_Y1 if broken else BLADE_Y1
    # a broken blade shows only the lower part of the painted blade
    frac = (y1 - BLADE_Y0) / (BLADE_Y1 - BLADE_Y0)
    bu = UV['blade']
    blade_uv = (bu[0], 16 - 16 * frac, bu[2], 16)
    side = UV['blade_side']
    els = [
        box((7.2, -1.2, 7.2), (8.8, 0, 8.8), UV['pommel'], UV['pommel'], UV['pommel_end']),
        box((7.35, 0, 7.35), (8.65, 5.5, 8.65), UV['grip'], UV['grip'], UV['grip_end']),
        box((8.65, 2.6, 7.75), (9.45, 4.0, 8.25), UV['trigger'], UV['trigger'], UV['trigger_end']),
        box((6.4, 5.5, 6.9), (9.8, 8.2, 9.1), UV['housing'], UV['housing_side'], UV['housing_end']),
        box((6.2, 6.6, 6.8), (10.0, 7.2, 9.2), UV['clamp'], UV['clamp_side'], UV['clamp_end']),
        box((BLADE_X0, BLADE_Y0, 7.88), (BLADE_X1, y1, 8.12), blade_uv, (side[0], blade_uv[1], side[2], 16), UV['blade_end']),
    ]
    return {
        'textures': {'t': tex, 'particle': tex},
        'elements': els,
        'display': {
            'thirdperson_righthand': {'rotation': [0, -90, 0], 'translation': [0, 3.7, 0.4], 'scale': [0.7, 0.7, 0.7]},
            'firstperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
            'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0, 0, 0]},
        },
    }


def region(img, key):
    u0, v0, u1, v1 = UV[key]
    x0, y0, x1, y1 = int(round(u0 * U)), int(round(v0 * U)), int(round(u1 * U)), int(round(v1 * U))
    return img[y0:y1, x0:x1], x1 - x0, y1 - y0


def paint_blade_texture(tier, seed):
    img = np.zeros((T, T, 4))
    rng = np.random.default_rng(seed)

    # --- the blade face: x across (0 spine .. 1 edge), y along (0 tip .. 1 housing) -----------------------------------
    r, w, h = region(img, 'blade')
    ys, xs = np.mgrid[0:h, 0:w]
    u = (xs + 0.5) / w
    v = (ys + 0.5) / h
    length = BLADE_Y1 - BLADE_Y0
    along = (1 - v) * length                         # model units from the housing
    # bevel: flat body, then a ground edge over the last third of the width
    bevel = smooth(0.62, 0.98, u)
    base = tier.steel * (1 - bevel[..., None]) + tier.edge * bevel[..., None]
    # a long soft reflection running down the blade, and brushed-steel streaks along it
    refl = np.exp(-((u - 0.32) / 0.12) ** 2) * (0.75 + 0.25 * np.sin(along * 0.35))
    streak = noise2(w, h, 1.4, seed)[..., None] * 0.0 + noise2(w * 1, h, 1.0, seed + 1)[..., None]
    brushed = 0.92 + 0.12 * noise2(w, h, 0.9, seed + 2)[..., None] * (np.ones((h, w, 1)))
    col = base * brushed + refl[..., None] * 0.22
    col = col * (1 - 0.18 * (1 - smooth(0.0, 0.12, u))[..., None])            # the spine is darker
    col = col * (0.95 + 0.05 * streak)
    # scored break lines every few units: the blade is made to snap off segment by segment
    seg = 2.95
    m = np.mod(along - 0.6, seg)
    # faint: from a few blocks away the blade has to read as one clean piece of steel, not a saw
    groove = (m < 0.12).astype(float) * (u > 0.08) * (u < 0.8)
    col = col * (1 - 0.14 * groove[..., None])
    # scratches
    for _ in range(6):
        sy, sx = rng.uniform(0, h), rng.uniform(0, w)
        ang = rng.uniform(-0.25, 0.25)
        L = rng.uniform(4, 16)
        d = np.abs((ys - sy) * math.cos(ang) - (xs - sx) * math.sin(ang) * 6)
        a = (d < 0.6) & (np.abs(ys - sy) < L)
        col = np.where(a[..., None], col * 1.12, col)
    if tier.glow is not None:
        rune = (np.abs(u - 0.38) < 0.05) & (np.mod(along, seg) > 0.6) & (np.mod(along, seg) < 2.4)
        col = np.where(rune[..., None], tier.glow, col)
    alpha = np.ones((h, w))
    # the oblique tip: long point on the edge side, slanting back to the spine
    tip_len = 2.6
    tip_cut = along > (length - tip_len + tip_len * (1 - u) * 0.0) - 0.0
    slant = along > length - tip_len * u
    alpha = np.where(slant, 0.0, alpha)
    # a bright line along the cut of the tip
    near_cut = (along > length - tip_len * u - 0.5) & ~slant
    col = np.where(near_cut[..., None], tier.edge, col)
    if tier.teeth:
        teeth = (u > 0.86) & (np.mod(along, 0.9) < 0.42)
        alpha = np.where(teeth, 0.0, alpha)
        col = np.where(((u > 0.8) & (u <= 0.86))[..., None], tier.edge * 0.9, col)
    col = np.clip(col, 0, 1)
    r[..., :3] = col
    r[..., 3] = alpha

    # thin side and end faces of the blade: the edge itself
    for key in ('blade_side', 'blade_end'):
        r, w, h = region(img, key)
        r[..., :3] = tier.edge * 0.85
        r[..., 3] = 1.0

    # --- grip: wrapped leather with diagonal bands ----------------------------------------------------------------
    for key in ('grip', 'grip_end'):
        r, w, h = region(img, key)
        ys, xs = np.mgrid[0:h, 0:w]
        band = np.mod(xs * 0.6 + ys * 0.9, 6.0)
        col = tier.grip * (0.8 + 0.35 * (band < 3.2)[..., None]) * (0.9 + 0.2 * noise2(w, h, 2, seed + 7)[..., None])
        col = col * (1 - 0.35 * (band >= 5.4)[..., None])
        r[..., :3] = np.clip(col, 0, 1)
        r[..., 3] = 1.0

    # --- metal parts ---------------------------------------------------------------------------------------------
    for key, tint in (('pommel', 1.0), ('pommel_end', 0.9), ('trigger', 1.1), ('trigger_end', 1.0), ('housing', 1.0),
                      ('housing_side', 0.85), ('housing_end', 0.95), ('clamp', 1.25), ('clamp_side', 1.1), ('clamp_end', 1.0)):
        r, w, h = region(img, key)
        ys, xs = np.mgrid[0:h, 0:w]
        yy = (ys + 0.5) / h
        xx = (xs + 0.5) / w
        col = tier.metal * tint * (0.85 + 0.3 * (1 - yy))[..., None] * (0.92 + 0.12 * noise2(w, h, 2, seed + 9)[..., None])
        rim = (xx < 0.07) | (xx > 0.93) | (yy < 0.08) | (yy > 0.92)
        col = np.where(rim[..., None], col * 0.6, col)
        if key == 'housing':
            # rivets and an accent stripe: the tier's colour
            for cx in (0.14, 0.86):
                d = np.sqrt(((xx - cx) * w) ** 2 + ((yy - 0.5) * h) ** 2)
                col = np.where((d < 1.6)[..., None], tier.metal * 1.6, col)
            stripe = np.abs(yy - 0.5) < 0.12
            col = np.where((stripe & (xx > 0.25) & (xx < 0.75))[..., None], tier.accent, col)
        if key == 'trigger':
            col = tier.accent * (0.8 + 0.3 * (1 - yy))[..., None]
        r[..., :3] = np.clip(col, 0, 1)
        r[..., 3] = 1.0
    return img


# ---------------------------------------------------------------------------------------------------------------------
# 32px icons: painted big on a canvas with simple shapes, then reduced
# ---------------------------------------------------------------------------------------------------------------------
SS = 8
N = 32 * SS


class Canvas:
    def __init__(self):
        self.c = np.zeros((N, N, 3))
        self.a = np.zeros((N, N))
        ys, xs = np.mgrid[0:N, 0:N]
        self.x = (xs + 0.5) / SS          # icon pixels, 0..32
        self.y = (ys + 0.5) / SS

    def put(self, mask, col):
        mask = mask.astype(float) if mask.dtype == bool else mask
        if np.ndim(col) == 1:
            col = np.broadcast_to(col, self.c.shape)
        self.c = self.c * (1 - mask[..., None]) + col * mask[..., None]
        self.a = np.maximum(self.a, mask)

    def frame(self, cx, cy, ang):
        """Coordinates along (u) and across (v) a direction through (cx, cy)."""
        ca, sa = math.cos(ang), math.sin(ang)
        dx, dy = self.x - cx, self.y - cy
        return dx * ca + dy * sa, -dx * sa + dy * ca

    def done(self, outline=rgb('120f10')):
        # reduce: average colour weighted by coverage, then a crisp alpha
        a = self.a.reshape(32, SS, 32, SS).mean(axis=(1, 3))
        c = (self.c * self.a[..., None]).reshape(32, SS, 32, SS, 3).sum(axis=(1, 3)) / (a[..., None] * SS * SS + 1e-9)
        solid = a > 0.45
        out = np.zeros((32, 32, 4))
        out[..., :3] = c
        out[..., 3] = solid
        # one-pixel dark outline round the shape
        pad = np.pad(solid, 1)
        ring = (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]) & ~solid
        out[ring, :3] = outline
        out[ring, 3] = 1.0
        return out


def lighten(c, k):
    return np.clip(c + (1 - c) * k, 0, 1)


def icon_blade(tier, broken):
    cv = Canvas()
    ang = -math.pi / 4                      # up and to the right
    cx, cy = 16, 16
    u, v = cv.frame(cx, cy, ang)
    # u runs from bottom-left (-) to top-right (+)
    blade_from, blade_to = -4.5, 14.5 if not broken else 3.0
    w = 1.7
    on = (u > blade_from) & (u < blade_to) & (v > -w) & (v < w)
    tip = u > blade_to - 3.2 * (v + w) / (2 * w)
    if not broken:
        on = on & ~tip
    else:
        jag = u > blade_to - 0.8 * np.abs(np.sin(v * 3.1)) - 0.4
        on = on & ~jag
    across = (v + w) / (2 * w)                          # 0 spine (upper-left) .. 1 edge
    col = tier.steel * (1 - smooth(0.55, 1.0, across)[..., None]) + tier.edge * smooth(0.55, 1.0, across)[..., None]
    col = col * (1.0 + 0.25 * np.exp(-((across - 0.3) / 0.15) ** 2))[..., None]
    seg = (np.mod(u - blade_from, 3.0) < 0.35) & (across < 0.85)
    col = np.where(seg[..., None], col * 0.6, col)
    if tier.glow is not None:
        col = np.where((np.abs(across - 0.38) < 0.08)[..., None] & ~seg[..., None], tier.glow, col)
    if tier.teeth:
        teeth = (across > 0.62) & (np.mod(u, 1.6) < 0.75) & (across > 1.0 - 0.38 * np.mod(u, 1.6) / 0.75)
        on = on & ~teeth
    cv.put(on, np.clip(col, 0, 1))
    # housing
    hs = (u > -7.5) & (u < -4.5) & (np.abs(v) < 2.9)
    cv.put(hs, tier.metal * (0.9 + 0.3 * (v < 0)[..., None]))
    cv.put(hs & (np.abs(v) < 0.8) & (u > -6.6) & (u < -5.4), tier.accent)
    # grip
    gp = (u > -13.5) & (u < -7.5) & (np.abs(v) < 1.5)
    band = np.mod(u * 2.0 + v, 2.2) < 1.1
    cv.put(gp, tier.grip * (0.85 + 0.35 * band[..., None]))
    # trigger
    tg = (u > -10.5) & (u < -8.8) & (v > 1.5) & (v < 3.0)
    cv.put(tg, tier.accent)
    # pommel
    pm = (u > -15.2) & (u < -13.5) & (np.abs(v) < 1.9)
    cv.put(pm, tier.metal * 1.2)
    return cv.done()


def icon_odm(seed, body, accent, tank, glow=None):
    cv = Canvas()
    x, y = cv.x, cv.y
    # two gas tanks hanging at the hips, seen from the front: rounded cylinders
    for cx in (8.0, 24.0):
        dx = (x - cx) / 4.2
        tankm = (np.abs(dx) < 1.0) & (y > 9.5) & (y < 27.5)
        cap = ((x - cx) ** 2 / 4.2 ** 2 + (y - 9.5) ** 2 / 2.2 ** 2) < 1
        bottom = ((x - cx) ** 2 / 4.2 ** 2 + (y - 27.5) ** 2 / 1.6 ** 2) < 1
        m = tankm | cap | bottom
        shade = 0.7 + 0.5 * np.cos(np.clip(dx, -1, 1) * 1.4 - 0.5)
        col = tank * shade[..., None]
        band = (np.abs(y - 13.5) < 0.7) | (np.abs(y - 23.5) < 0.7)
        col = np.where(band[..., None], accent * shade[..., None], col)
        cv.put(m, np.clip(col, 0, 1))
        # valve on top
        cv.put((np.abs(x - cx) < 1.1) & (y > 5.5) & (y < 8.5), body * 1.3)
    # the central mechanism: a box with the cable reels and a gauge
    boxm = (np.abs(x - 16) < 6.2) & (y > 7.0) & (y < 20.0)
    yy = (y - 7.0) / 13.0
    cv.put(boxm, body * (1.15 - 0.35 * yy)[..., None])
    # the cable drum across the top of the box, wound with wire
    drum = (np.abs(x - 16) < 5.0) & (np.abs(y - 10.6) < 2.2)
    wind = np.mod(x * 1.0 + (y - 10.6) * 0.35, 1.2) < 0.55
    shade = 1.0 - 0.35 * np.abs(y - 10.6) / 2.2
    cv.put(drum, rgb('3a3a3c') * (0.8 + 0.5 * wind[..., None]) * shade[..., None])
    for ex in (10.6, 21.4):
        cv.put((np.abs(x - ex) < 0.7) & (np.abs(y - 10.6) < 2.7), lighten(body, 0.3))
    # the tier's stripe across the box and the gas outlet below it
    cv.put((np.abs(x - 16) < 6.2) & (np.abs(y - 15.2) < 0.8), accent)
    cv.put((np.abs(x - 16) < 1.6) & (y > 19.8) & (y < 23.0), body * 0.7)
    cv.put((np.abs(x - 16) < 2.4) & (y > 22.4) & (y < 23.6), lighten(body, 0.25))
    # the two hooks flaring out from the top of the box, and their cables
    for side in (-1, 1):
        hx = 16 + side * 8.5
        cable = (np.abs((x - 16) * side - (7.5 - y) * 1.6 - 1.5) < 0.45) & (y > 1.5) & (y < 7.2) & ((x - 16) * side > 0)
        cv.put(cable, rgb('2a2a2a'))
        hook = ((x - hx) ** 2 + (y - 2.5) ** 2) < 1.3 ** 2
        cv.put(hook, lighten(body, 0.5))
    if glow is not None:
        cv.put((np.abs(x - 16) < 6.2) & (np.abs(y - 15.2) < 0.35), glow)
    return cv.done()


def icon_thunder_spear():
    cv = Canvas()
    u, v = cv.frame(16, 16, -math.pi / 4)
    shaft = (u > -14) & (u < 5) & (np.abs(v) < 0.9)
    cv.put(shaft, rgb('5a4a3a') * (0.9 + 0.3 * (v < 0))[..., None])
    for k in range(3):
        cv.put(shaft & (np.abs(u + 10 - k * 2.2) < 0.4), rgb('a8a8a0'))
    head = (u > 5) & (u < 11.5) & (np.abs(v) < 2.3 - np.clip(u - 9.0, 0, 3) * 0.75)
    cv.put(head, rgb('6b7076') * (0.85 + 0.35 * (v < 0))[..., None])
    stripe = head & (np.abs(u - 7.2) < 0.7)
    cv.put(stripe, rgb('d8b440'))
    tip = (u >= 11.5) & (u < 15) & (np.abs(v) < (15 - u) * 0.6)
    cv.put(tip, rgb('cfd4da'))
    fins = (u > -15) & (u < -11.5) & (np.abs(v) < 0.9 + (-11.5 - u) * 0.75)
    cv.put(fins & ~shaft, rgb('8a2a22'))
    return cv.done()


def icon_gas_canister():
    cv = Canvas()
    x, y = cv.x, cv.y
    dx = (x - 16) / 6.0
    body = (np.abs(dx) < 1) & (y > 8) & (y < 28)
    top = ((x - 16) ** 2 / 36 + (y - 8) ** 2 / 9) < 1
    bot = ((x - 16) ** 2 / 36 + (y - 28) ** 2 / 4) < 1
    shade = 0.65 + 0.5 * np.cos(np.clip(dx, -1, 1) * 1.4 - 0.5)
    col = rgb('c8ccd2') * shade[..., None]
    band = np.abs(y - 18) < 2.2
    col = np.where(band[..., None], rgb('3aa0d8') * shade[..., None], col)
    cv.put(body | top | bot, np.clip(col, 0, 1))
    cv.put((np.abs(x - 16) < 1.8) & (y > 2.5) & (y < 6.5), rgb('5d6168'))
    cv.put((np.abs(x - 16) < 3.4) & (y > 2.0) & (y < 3.4), rgb('8a8f96'))
    return cv.done()


def save(img, name):
    a = np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(a, 'RGBA').save(os.path.join(TEX, name + '.png'), optimize=True)
    print('  ', name, img.shape[1], 'x', img.shape[0])


def main():
    # the 32px icons of blades, rigs and everything else come from tools/icon_art.py
    for k, tier in enumerate(TIERS):
        tex = 'takemtitans:item/%s_blade_model' % tier.item
        save(paint_blade_texture(tier, 50 + k), '%s_blade_model' % tier.item)
        for broken in (False, True):
            name = '%s_blades%s_3d' % (tier.item, '_broken' if broken else '')
            with open(os.path.join(MODELS, name + '.json'), 'w') as f:
                json.dump(blade_model(tex, broken), f, indent=2)


if __name__ == '__main__':
    main()
