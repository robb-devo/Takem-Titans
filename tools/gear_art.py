"""
Takem Titans - the gear you wear: uniforms, boots, helmets, the ODM harness, the cloak and the ODM rigs.

Every texture is painted face by face on the boxes of the model that wears it (vanilla's armor boxes, CloakLayer's
cloak, OdmGearLayer's rig), "as seen from outside", at two or four times the 64x32 layout the models expect.

Run from the repo root:  python tools/gear_art.py
"""
import math
import os

import numpy as np
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(REPO, 'src/main/resources/assets/takemtitans/textures')


def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255.0


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def vnoise(x, y, scale, seed):
    """Smooth 2D value noise in 0..1 at coordinates x, y (arrays)."""
    xs, ys = x / scale, y / scale
    x0, y0 = np.floor(xs).astype(np.int64), np.floor(ys).astype(np.int64)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def h(ix, iy):
        v = (ix * 374761393 + iy * 668265263 + seed * 1442695041) & 0xFFFFFFFF
        v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0

    a = h(x0, y0) * (1 - fx) + h(x0 + 1, y0) * fx
    b = h(x0, y0 + 1) * (1 - fx) + h(x0 + 1, y0 + 1) * fx
    return a * (1 - fy) + b * fy


class Face:
    """One face of a box in the texture: pixel arrays plus (x, y) in model units across it, as seen from outside."""

    def __init__(self, img, part, name, rect, w, h, k):
        self.img, self.part, self.name = img, part, name
        u0, v0, u1, v1 = rect
        self.k = k
        self.px0, self.py0 = int(math.floor(min(u0, u1) * k + 0.5)), int(math.floor(min(v0, v1) * k + 0.5))
        self.px1, self.py1 = int(math.floor(max(u0, u1) * k + 0.5)), int(math.floor(max(v0, v1) * k + 0.5))
        self.W, self.H = self.px1 - self.px0, self.py1 - self.py0
        self.w, self.h = w, h
        ys, xs = np.mgrid[0:max(self.H, 1), 0:max(self.W, 1)]
        self.x = (xs + 0.5) / max(self.W, 1) * w
        self.y = (ys + 0.5) / max(self.H, 1) * h
        self.c = np.zeros((max(self.H, 1), max(self.W, 1), 3))
        self.a = np.ones((max(self.H, 1), max(self.W, 1)))

    def put(self, mask, col):
        m = mask.astype(float) if mask.dtype == bool else mask
        col = np.asarray(col, dtype=float)
        if col.ndim == 1:
            col = col[None, None, :]
        self.c = self.c * (1 - m[..., None]) + col * m[..., None]

    def rect(self, x0, y0, x1, y1, col):
        self.put((self.x >= x0) & (self.x < x1) & (self.y >= y0) & (self.y < y1), col)

    def band(self, ax, ay, bx, by, width, col):
        """A strap from (ax, ay) to (bx, by), `width` wide."""
        dx, dy = bx - ax, by - ay
        L = math.hypot(dx, dy) + 1e-9
        t = ((self.x - ax) * dx + (self.y - ay) * dy) / (L * L)
        d = np.abs((self.x - ax) * dy - (self.y - ay) * dx) / L
        m = (t >= 0) & (t <= 1) & (d <= width / 2)
        self.put(m, col)
        return m

    def shade(self, top=0.06, bottom=-0.1, edge=0.12):
        k = 1 + top - (top - bottom) * (self.y / self.h)
        e = np.minimum(np.minimum(self.x, self.w - self.x), np.minimum(self.y, self.h - self.y))
        k = k * (1 - edge * (1 - smooth(0.0, 0.6, e)))
        self.c = self.c * k[..., None]

    def clear(self, mask):
        self.a = np.where(mask, 0.0, self.a)

    def commit(self):
        if self.W <= 0 or self.H <= 0:
            return
        sl = self.img[self.py0:self.py1, self.px0:self.px1]
        sl[..., :3] = np.clip(self.c, 0, 1)
        sl[..., 3] = self.a


def box_faces(u, v, w, h, d):
    """Box UV rectangles (u0, v0, u1, v1) and face sizes (across, down) in model units, Minecraft's layout."""
    return {
        'top': ((u + d, v, u + d + w, v + d), w, d),
        'bottom': ((u + d + w, v, u + d + 2 * w, v + d), w, d),
        'right': ((u, v + d, u + d, v + d + h), d, h),
        'front': ((u + d, v + d, u + d + w, v + d + h), w, h),
        'left': ((u + d + w, v + d, u + 2 * d + w, v + d + h), d, h),
        'back': ((u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), w, h),
    }


def paint_box(img, k, part, u, v, w, h, d, painter, skip=()):
    for name, (rect, fw, fh) in box_faces(u, v, w, h, d).items():
        if name in skip:
            continue
        f = Face(img, part, name, rect, fw, fh, k)
        if f.W <= 0 or f.H <= 0:
            continue
        painter(f)
        f.commit()


def save(img, rel):
    a = np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8)
    path = os.path.join(TEX, rel)
    Image.fromarray(a, 'RGBA').save(path, optimize=True)
    print('  %-48s %dx%d' % (rel, img.shape[1], img.shape[0]))


# ---------------------------------------------------------------------------------------------------------------------
# Materials
# ---------------------------------------------------------------------------------------------------------------------
def cloth(f, base, seed, folds=0.0, grain=0.06):
    n = vnoise(f.x * 4, f.y * 4, 1.0, seed)
    col = base[None, None, :] * (1 - grain + 2 * grain * n[..., None])
    if folds:
        fold = np.sin(f.x * 2.3 + vnoise(f.x, f.y * 0.4, 1.5, seed + 3) * 3.0)
        col = col * (1 + folds * fold[..., None] * smooth(0.0, f.h, f.y)[..., None])
    f.c = col


def leather(f, base, seed):
    n = vnoise(f.x * 6, f.y * 6, 1.0, seed)
    n2 = vnoise(f.x * 1.5, f.y * 1.5, 1.0, seed + 1)
    f.c = base[None, None, :] * (0.88 + 0.16 * n[..., None]) * (0.94 + 0.12 * n2[..., None])


def metal(f, base, seed, brushed=True):
    n = vnoise(f.x * 8, f.y * 0.8 if brushed else f.y * 8, 1.0, seed)
    f.c = base[None, None, :] * (0.9 + 0.14 * n[..., None])
    # a soft sheen across the upper part
    f.c = f.c * (1 + 0.12 * np.exp(-((f.y / f.h - 0.3) / 0.2) ** 2))[..., None]


def crystal(f, seed):
    """Hardened titan skin: pale blue facets, bright ridges, a few deep cracks."""
    pts = np.random.default_rng(seed).random((14, 2)) * [f.w, f.h]
    d = np.stack([np.hypot(f.x - px, f.y - py) for px, py in pts])
    o = np.sort(d, axis=0)
    cell = np.argmin(d, axis=0)
    tone = np.random.default_rng(seed + 1).random(14)[cell]
    base = rgb('cfeaff') * (0.82 + 0.25 * tone[..., None])
    ridge = 1 - smooth(0.0, 0.18, o[1] - o[0])
    col = base * (1 + 0.22 * ridge[..., None])
    deep = (1 - smooth(0.0, 0.06, o[1] - o[0])) * (tone > 0.75)
    col = col * (1 - 0.35 * deep[..., None])
    f.c = np.clip(col, 0, 1)


def emblem(f, cx, cy, size):
    """
    The Wings of Freedom: a white wing behind a blue one, both rising out of the foot of a shield, rasterised at four
    times the face's resolution and averaged down so even a small one keeps its shape.
    """
    S = 4
    H, W = f.H, f.W
    ys, xs = np.mgrid[0:H * S, 0:W * S]
    x = (xs + 0.5) / (W * S) * f.w
    y = (ys + 0.5) / (H * S) * f.h
    u = (x - cx) / size            # -0.5 .. 0.5 across the shield
    v = (y - cy) / size            # -0.5 .. 0.5 down
    shield = (np.abs(u) < 0.5) & (v > -0.5) & (v < 0.55 - np.abs(u) * 0.95) & (v < 0.5 - 1.75 * u * u * (v > 0.05))
    inner = (np.abs(u) < 0.43) & (v > -0.43) & (v < 0.47 - np.abs(u) * 0.95) & (v < 0.43 - 1.75 * u * u * (v > 0.05))
    col = np.zeros(x.shape + (3,))
    col[...] = rgb('d8d4c8')

    def wing(side, white):
        # four feathers fanning out from the root near the bottom middle, the longest on the outside
        m = np.zeros(x.shape, dtype=bool)
        line = np.zeros(x.shape, dtype=bool)
        root = (-side * 0.02, 0.36)
        for i, (ang, L, wdt) in enumerate(((78, 0.8, 0.13), (60, 0.82, 0.13), (42, 0.74, 0.12), (24, 0.6, 0.11), (8, 0.46, 0.1))):
            a = math.radians(ang)
            dx, dy = side * math.cos(a), -math.sin(a)
            px, py = u - root[0], v - root[1]
            t = px * dx + py * dy
            d = px * (-dy) + py * dx
            taper = wdt * np.clip(1 - (t / L) ** 2, 0, 1) ** 0.5
            feather = (t > 0) & (t < L) & (np.abs(d) < taper)
            m |= feather
            line |= feather & (np.abs(d) > taper - 0.025)
        return m, line

    wm, wl = wing(-1, True)
    bm, bl = wing(1, False)
    col[wm] = rgb('f4f2ec')
    col[wl] = rgb('8f8b82')
    col[bm] = rgb('2f6fd8')
    col[bl] = rgb('183f8a')
    col[~inner] = rgb('1a1a1a')
    m = shield.astype(float)
    m4 = m.reshape(H, S, W, S).mean(axis=(1, 3))
    c4 = (col * m[..., None]).reshape(H, S, W, S, 3).sum(axis=(1, 3)) / (m.reshape(H, S, W, S).sum(axis=(1, 3))[..., None] + 1e-9)
    on = m4 > 0.35
    f.c = np.where(on[..., None], c4, f.c)


def buckle(f, cx, cy, s, col=rgb('c9a24a')):
    ring = (np.abs(f.x - cx) < s) & (np.abs(f.y - cy) < s * 0.8)
    hole = (np.abs(f.x - cx) < s * 0.45) & (np.abs(f.y - cy) < s * 0.35)
    f.put(ring & ~hole, col)
    f.put(hole, col * 0.4)


# ---------------------------------------------------------------------------------------------------------------------
# Uniforms (vanilla armor boxes: head 0,0 8x8x8 | body 16,16 8x12x4 | arm 40,16 4x12x4 | leg 0,16 4x12x4)
# ---------------------------------------------------------------------------------------------------------------------
STRAP = rgb('2e1d12')
BRASS = rgb('c9a24a')
SHIRT = rgb('e9e5da')


def jacket(base, seed, coat=False, recon=False):
    def body(f):
        leather(f, base, seed)
        n = f.name
        if n == 'front':
            # open front over a white shirt, lapels, breast pockets, harness and belt
            f.rect(3.3, 1.4, 4.7, 12, SHIRT * (0.92 + 0.08 * (f.y / 12))[..., None])
            f.band(2.0, 0.0, 3.3, 2.6, 0.55, base * 0.62)
            f.band(6.0, 0.0, 4.7, 2.6, 0.55, base * 0.62)
            if coat:
                for yy in (4.0, 6.0, 8.0):
                    for xx in (2.6, 5.4):
                        f.put(np.hypot(f.x - xx, f.y - yy) < 0.32, BRASS)
            else:
                for x0 in (0.9, 5.2):
                    f.rect(x0, 3.0, x0 + 1.9, 5.0, base * 0.85)
                    f.rect(x0, 3.0, x0 + 1.9, 3.5, base * 0.6)
                emblem(f, 6.15, 4.2, 1.45)
            for xx in (1.9, 6.1):
                f.rect(xx - 0.35, 0, xx + 0.35, 12, STRAP)
            f.rect(0, 6.0, 8, 6.6, STRAP)
            buckle(f, 1.9, 6.3, 0.42)
            buckle(f, 6.1, 6.3, 0.42)
            if recon:
                f.rect(0.4, 8.2, 2.6, 9.8, base * 0.7)
                f.rect(5.4, 8.2, 7.6, 9.8, base * 0.7)
        elif n == 'back':
            # the harness runs over the shoulders and down to the belt, either side of the emblem
            for xx in (0.85, 7.15):
                f.rect(xx - 0.35, 0, xx + 0.35, 12, STRAP)
            f.rect(0, 8.3, 8, 8.8, STRAP)
            emblem(f, 4.0, 4.0, 5.0 if not coat else 5.4)
        elif n in ('right', 'left'):
            f.rect(1.6, 0, 2.4, 12, base * 0.8)
        if n in ('front', 'back', 'right', 'left'):
            f.rect(0, 10.4, f.w, 11.5, STRAP)
            if n == 'front':
                buckle(f, 4.0, 10.95, 0.55)
        f.shade()

    def arm(f):
        leather(f, base, seed + 7)
        if f.name in ('front', 'back', 'right', 'left'):
            f.rect(0, 10.2, f.w, 12, base * 0.62)
            f.rect(0, 10.2, f.w, 10.5, base * 0.45)
            f.rect(0, 0, f.w, 0.6, base * 0.75)
        if f.name == 'right':
            emblem(f, 2.0, 2.9, 2.6)
        f.shade()

    return body, arm


def boots(base, seed, toe=None, crystal_boot=False):
    def leg(f):
        top = 5.2
        if crystal_boot:
            crystal(f, seed + hash(f.name) % 97)
        else:
            leather(f, base, seed)
            # a shine running down the front of the shaft
            if f.name == 'front':
                f.c = f.c * (1 + 0.22 * np.exp(-((f.x - 1.3) / 0.45) ** 2) * (f.y > top))[..., None]
        if f.name in ('front', 'back', 'right', 'left'):
            f.rect(0, top, f.w, top + 0.9, base * 1.25 if not crystal_boot else rgb('e8f6ff'))
            f.rect(0, 11.4, f.w, 12, rgb('1a1310'))
            if not crystal_boot:
                for yy in (7.4, 9.4):
                    f.rect(0, yy, f.w, yy + 0.55, STRAP)
                    if f.name == 'right':
                        buckle(f, 2.0, yy + 0.27, 0.38)
            if toe is not None and f.name == 'front':
                f.rect(0, 10.0, f.w, 11.4, toe)
                f.rect(0, 10.0, f.w, 10.25, toe * 1.2)
            f.clear(f.y < top)
        elif f.name == 'top':
            f.clear(np.ones_like(f.x, dtype=bool))
        else:
            f.c = rgb('1a1310') * np.ones_like(f.c)
        f.shade(top=0.08, bottom=-0.06, edge=0.08)

    return leg


def hood(base, seed):
    def head(f):
        cloth(f, base, seed, folds=0.07)
        if f.name == 'front':
            # the face looks out of the hood: a rim of cloth round it
            inside = (f.x > 1.1) & (f.x < 6.9) & (f.y > 1.3)
            rim = (f.x > 0.7) & (f.x < 7.3) & (f.y > 0.9) & ~inside
            f.put(rim, base * 0.7)
            f.clear(inside)
        elif f.name == 'bottom':
            f.clear(np.ones_like(f.x, dtype=bool))
        else:
            # folds run back to the point of the hood
            fold = np.sin((f.x + f.y * 0.6) * 1.9 + vnoise(f.x, f.y, 2.0, seed) * 2)
            f.c = f.c * (1 + 0.08 * fold[..., None])
        f.shade(top=0.08, bottom=-0.08, edge=0.1)

    return head


def steel_helmet(seed):
    steel = rgb('a7adb3')

    def head(f):
        metal(f, steel, seed, brushed=False)
        if f.name == 'top':
            f.rect(3.4, 0, 4.6, 8, rgb('2e4a33'))
            f.rect(3.4, 0, 3.6, 8, rgb('1c2e20'))
        elif f.name == 'bottom':
            f.clear(np.ones_like(f.x, dtype=bool))
        else:
            reach = 2.2 if f.name == 'front' else (5.0 if f.name == 'back' else 4.4)
            f.rect(0, reach - 0.6, f.w, reach, steel * 0.7)
            for xx in np.arange(0.8, f.w, 1.6):
                f.put(np.hypot(f.x - xx, f.y - (reach - 0.3)) < 0.2, rgb('e6e9ec'))
            gone = f.y > reach
            if f.name == 'front':
                nasal = (np.abs(f.x - 4.0) < 0.45) & (f.y < 5.0)
                f.put(nasal & gone, steel * 0.9)
                gone &= ~nasal
            f.clear(gone)
        f.shade(top=0.1, bottom=-0.08, edge=0.1)

    return head


def crystal_helmet(seed):
    def head(f):
        crystal(f, seed + len(f.name))
        if f.name == 'bottom':
            f.clear(np.ones_like(f.x, dtype=bool))
        elif f.name == 'front':
            eyes = (f.y > 2.6) & (f.y < 4.2) & (f.x > 1.5) & (f.x < 6.5)
            lower = (f.y >= 4.2) & (f.x > 2.2) & (f.x < 5.8)
            f.put((f.y > 2.3) & (f.y < 2.6) & (f.x > 1.2) & (f.x < 6.8), rgb('8fd6ff'))
            f.clear(eyes | lower)
        f.shade(top=0.06, bottom=-0.06, edge=0.1)

    return head


def crystal_body(seed):
    def part(f):
        crystal(f, seed + len(f.name) * 7)
        if f.name == 'back':
            f.band(4.0, 0.5, 4.0, 11.5, 0.25, rgb('8fd6ff'))
        f.shade(top=0.06, bottom=-0.08, edge=0.1)

    return part


def harness_legs(seed):
    pants = rgb('e6e2d6')

    def leg(f):
        cloth(f, pants, seed, folds=0.04)
        if f.name in ('front', 'back', 'right', 'left'):
            for yy in (1.4, 4.2):
                f.rect(0, yy, f.w, yy + 0.7, STRAP)
            if f.name == 'right':
                f.rect(1.6, 0, 2.4, 5.0, STRAP)
                buckle(f, 2.0, 1.75, 0.4)
                buckle(f, 2.0, 4.55, 0.4)
            if f.name == 'front':
                f.rect(0, 5.8, f.w, 6.1, pants * 0.85)
        f.shade(top=0.04, bottom=-0.06, edge=0.08)

    def waist(f):
        cloth(f, pants, seed + 3)
        if f.name in ('front', 'back', 'right', 'left'):
            f.rect(0, 9.4, f.w, 10.6, STRAP)
            if f.name == 'front':
                buckle(f, 4.0, 10.0, 0.6)
            f.clear(f.y < 9.4)
        else:
            f.clear(np.ones_like(f.x, dtype=bool))
        f.shade(top=0.0, bottom=-0.04, edge=0.06)

    return leg, waist


def armor_set(name, head=None, body=None, arm=None, legs=None, waist=None, k=2):
    img1 = np.zeros((32 * k, 64 * k, 4))
    if head:
        paint_box(img1, k, 'head', 0, 0, 8, 8, 8, head)
    if body:
        paint_box(img1, k, 'body', 16, 16, 8, 12, 4, body)
    if arm:
        paint_box(img1, k, 'arm', 40, 16, 4, 12, 4, arm)
    if legs:
        paint_box(img1, k, 'leg', 0, 16, 4, 12, 4, legs)
    save(img1, 'models/armor/%s_layer_1.png' % name)
    img2 = np.zeros((32 * k, 64 * k, 4))
    save(img2, 'models/armor/%s_layer_2.png' % name)


# ---------------------------------------------------------------------------------------------------------------------
# The cloak (CloakLayer: cloak 0,0 12x19x1, hood 26,0 9x3x2.2)
# ---------------------------------------------------------------------------------------------------------------------
def cloak_texture(k=4):
    green, lining = rgb('2f5a3a'), rgb('203d28')
    img = np.zeros((32 * k, 64 * k, 4))

    def cloak(f):
        cloth(f, green, 61, folds=0.0)
        n = f.name
        if n == 'front':
            # the side that faces away from the wearer: long folds, a hem and the emblem between the shoulders
            fold = np.sin(f.x * 1.9 + 0.7 * np.sin(f.y * 0.35)) * smooth(2.0, 20.0, f.y)
            f.c = f.c * (1 + 0.13 * fold[..., None])
            f.c = f.c * (1 - 0.08 * np.abs(np.sin(f.x * 3.8))[..., None] * smooth(8, 20, f.y)[..., None])
            f.rect(0, 19.2, 16, 20, green * 0.55)
            f.rect(0, 0, 16, 0.7, green * 1.15)
            emblem(f, 8.0, 7.6, 7.2)
        elif n == 'back':
            f.c = lining[None, None, :] * (0.92 + 0.12 * vnoise(f.x * 3, f.y * 3, 1.0, 9)[..., None])
        else:
            f.c = green * 0.7 * np.ones_like(f.c)
        f.shade(top=0.08, bottom=-0.12, edge=0.05)

    def hood_part(f):
        cloth(f, green, 63)
        fold = np.sin(f.x * 3.1 + f.y * 1.7)
        f.c = f.c * (1 + 0.12 * fold[..., None])
        if f.name == 'bottom':
            f.c = lining * np.ones_like(f.c)
        f.shade(top=0.1, bottom=-0.12, edge=0.12)

    paint_box(img, k, 'cloak', 0, 0, 16, 20, 1, cloak)
    paint_box(img, k, 'hood', 36, 0, 10, 3.5, 2.5, hood_part)
    save(img, 'entity/scout_cloak.png')


# ---------------------------------------------------------------------------------------------------------------------
# ODM rigs (OdmGearLayer, 64x32 layout)
# ---------------------------------------------------------------------------------------------------------------------
RIG = [
    # name, u, v, w, h, d, material
    ('belt', 0, 0, 9, 1.6, 5, 'leather'),
    ('chest_strap', 0, 7, 8.6, 0.9, 4.6, 'leather'),
    ('strap', 28, 0, 1.1, 10.6, 0.5, 'leather'),
    ('motor', 0, 13, 5, 3.8, 2.4, 'motor'),
    ('nozzle', 15, 13, 2, 1.6, 1.4, 'nozzle'),
    ('reel', 22, 13, 1.2, 2.4, 2.4, 'reel'),
    ('tank', 0, 20, 2.3, 2.3, 7, 'tank'),
    ('tank_cap', 18, 20, 1.6, 1.6, 0.8, 'cap'),
    ('box', 30, 12, 2.4, 3.6, 8.4, 'box'),
    ('box_lid', 30, 24, 2.8, 0.6, 2.0, 'lid'),
    ('launcher', 48, 0, 1.8, 1.8, 3, 'launcher'),
    ('launcher_tip', 48, 5, 1.2, 1.2, 0.8, 'tip'),
    ('ring', 56, 24, 1.5, 0.5, 0.5, 'ring'),
]

TIERS = {
    'odm_gear': dict(metal='8e949b', dark='4b5056', leather='5a3a22', accent='3aa0d8', tank='c8ccd2', buckle='c9a24a'),
    'odm_gear_advanced': dict(metal='5d6269', dark='2e3136', leather='3a2416', accent='e0782e', tank='9aa0a8', buckle='b0b4b8'),
    'odm_gear_phantom': dict(metal='2c3a4a', dark='161e27', leather='1f2a36', accent='6fe3ff', tank='3e5366', buckle='9fe9ff', glow='6fe3ff'),
    'odm_gear_godspeed': dict(metal='e6dfca', dark='9c8e66', leather='efe8d8', accent='f2c84b', tank='f4efe0', buckle='f2c84b'),
    'odm_gear_event_horizon': dict(metal='1b1426', dark='0b0712', leather='140d1e', accent='b070ff', tank='2a1f3d', buckle='d6b8ff', glow='b070ff', stars=True),
}


def rig_textures(k=4):
    for name, p in TIERS.items():
        P = {key: rgb(val) for key, val in p.items() if isinstance(val, str)}
        stars = p.get('stars', False)
        img = np.zeros((32 * k, 64 * k, 4))
        glow = np.zeros((32 * k, 64 * k, 4)) if 'glow' in p else None
        seed = abs(hash(name)) % 1000

        for part, u, v, w, h, d, mat in RIG:
            def painter(f, mat=mat, part=part):
                accent_mask = np.zeros(f.x.shape, dtype=bool)
                if mat == 'leather':
                    leather(f, P['leather'], seed)
                    if part == 'belt' and f.name == 'front':
                        buckle(f, f.w / 2, f.h / 2, 0.55, P['buckle'])
                    if part == 'strap':
                        f.rect(0, 0, f.w, 0.15, P['leather'] * 0.7)
                        f.rect(0, f.h - 0.15, f.w, f.h, P['leather'] * 0.7)
                        for yy in (3.0, 7.0):
                            buckle(f, f.w / 2, yy, 0.42, P['buckle'])
                elif mat in ('motor', 'box', 'launcher', 'lid', 'nozzle', 'tip', 'reel', 'cap'):
                    base = P['metal'] if mat in ('motor', 'box', 'lid') else P['dark']
                    if mat == 'cap':
                        base = P['buckle']
                    metal(f, base, seed + len(part))
                    if mat == 'box' and f.name in ('right', 'left'):
                        # the long side of the blade box: two panels, rivets, the tier's stripe
                        f.rect(0, f.h * 0.45, f.w, f.h * 0.58, P['accent'])
                        accent_mask = (f.y >= f.h * 0.45) & (f.y < f.h * 0.58)
                        f.rect(f.w * 0.5 - 0.06, 0, f.w * 0.5 + 0.06, f.h, P['dark'])
                        for xx in (0.35, f.w * 0.5 - 0.35, f.w * 0.5 + 0.35, f.w - 0.35):
                            for yy in (0.35, f.h - 0.35):
                                f.put(np.hypot(f.x - xx, f.y - yy) < 0.16, P['buckle'])
                    elif mat == 'box' and f.name == 'front':
                        # the spare blades, stacked edge-on in the mouth of the box
                        f.rect(0.2, 0.2, f.w - 0.2, f.h - 0.2, rgb('141414'))
                        for i in range(4):
                            xx = 0.45 + i * (f.w - 0.9) / 3
                            f.rect(xx - 0.12, 0.35, xx + 0.12, f.h - 0.35, rgb('d9dde2'))
                    elif mat == 'box' and f.name == 'top':
                        f.rect(0, f.h * 0.48, f.w, f.h * 0.52, P['dark'])
                    elif mat == 'motor':
                        if f.name == 'back':
                            # a pressure gauge and a vent grille on the housing
                            g = np.hypot(f.x - f.w * 0.3, f.y - f.h * 0.45)
                            f.put(g < 0.9, rgb('ece6d2'))
                            f.put((g < 0.9) & (np.abs((f.x - f.w * 0.3) + (f.y - f.h * 0.45)) < 0.12), rgb('b0201a'))
                            f.put((g >= 0.9) & (g < 1.1), P['dark'])
                            for i in range(4):
                                yy = f.h * 0.25 + i * f.h * 0.15
                                f.rect(f.w * 0.58, yy, f.w * 0.9, yy + 0.18, P['dark'])
                        f.rect(0, f.h - 0.5, f.w, f.h - 0.25, P['accent'])
                        accent_mask = (f.y >= f.h - 0.5) & (f.y < f.h - 0.25)
                    elif mat == 'reel':
                        wire = np.mod(f.y * 5.0, 1.0) < 0.45
                        if f.name in ('front', 'back', 'top', 'bottom'):
                            f.put(wire, rgb('2a2a2a'))
                        else:
                            r = np.hypot(f.x - f.w / 2, f.y - f.h / 2)
                            f.put(r < 0.45, P['accent'])
                            accent_mask = r < 0.45
                    elif mat == 'nozzle' and f.name == 'back':
                        r = np.hypot(f.x - f.w / 2, f.y - f.h / 2)
                        f.put(r < 0.55, rgb('0a0a0a'))
                    elif mat in ('launcher', 'tip'):
                        if mat == 'tip' and f.name == 'front':
                            r = np.hypot(f.x - f.w / 2, f.y - f.h / 2)
                            f.put(r < 0.35, rgb('0a0a0a'))
                            f.put((r >= 0.35) & (r < 0.55), P['accent'])
                            accent_mask = (r >= 0.35) & (r < 0.55)
                        elif mat == 'launcher' and f.name in ('right', 'left', 'top'):
                            f.rect(f.w * 0.15, 0, f.w * 0.3, f.h, P['accent'])
                            accent_mask = (f.x >= f.w * 0.15) & (f.x < f.w * 0.3)
                elif mat == 'tank':
                    # a gas cylinder: its faces shaded round, bands of the tier's colour, a bright streak
                    if f.name in ('front', 'back'):
                        r = np.hypot(f.x - f.w / 2, f.y - f.h / 2)
                        f.c = P['tank'][None, None, :] * (1.0 - 0.3 * (r / (f.w / 2)))[..., None]
                        f.put(r < 0.35, P['dark'])
                    else:
                        across = f.y / f.h if f.name in ('right', 'left') else f.x / f.w
                        along = f.x if f.name in ('right', 'left') else f.y
                        roundness = 0.7 + 0.45 * np.sin(np.clip(across, 0, 1) * math.pi) ** 0.6
                        f.c = P['tank'][None, None, :] * roundness[..., None]
                        f.c = f.c * (1 + 0.25 * np.exp(-((across - 0.3) / 0.08) ** 2))[..., None]
                        L = 7.0
                        bands = (np.abs(along - L * 0.18) < 0.3) | (np.abs(along - L * 0.82) < 0.3)
                        f.put(bands, P['accent'])
                        accent_mask = bands
                elif mat == 'ring':
                    f.c = P['accent'] * np.ones_like(f.c)
                    accent_mask = np.ones(f.x.shape, dtype=bool)
                if stars and mat in ('motor', 'box', 'tank', 'launcher'):
                    rnd = vnoise(f.x * 23, f.y * 23, 1.0, seed + 5)
                    star = rnd > 0.93
                    f.put(star, rgb('f2eaff'))
                    accent_mask = accent_mask | star
                f.shade(top=0.08, bottom=-0.1, edge=0.14)
                painter.accent = accent_mask

            paint_box(img, k, part, u, v, w, h, d, painter)
            if glow is not None:
                def glow_painter(f, mat=mat, part=part):
                    painter(f)
                    m = painter.accent
                    f.c = np.where(m[..., None], P['glow'][None, None, :], 0.0)
                    f.a = np.where(m, 1.0, 0.0)

                paint_box(glow, k, part, u, v, w, h, d, glow_painter)
        save(img, 'entity/%s.png' % name)
        if glow is not None:
            save(glow, 'entity/%s_glow.png' % name)


def main():
    brown, tan, dark_brown = rgb('7a4e2e'), rgb('8f6239'), rgb('4a2c1a')
    green = rgb('2e4a33')
    body, arm = jacket(brown, 11, recon=True)
    armor_set('recon', head=hood(rgb('3a5f40'), 21), body=body, arm=arm, legs=boots(dark_brown, 31))
    body, arm = jacket(tan, 13)
    armor_set('scout_uniform', body=body, arm=arm)
    body, arm = jacket(green, 15, coat=True)
    armor_set('vanguard', head=steel_helmet(41), body=body, arm=arm, legs=boots(rgb('262220'), 33, toe=rgb('9aa0a6')))
    cb = crystal_body(51)
    armor_set('hardened', head=crystal_helmet(43), body=cb, arm=cb, legs=boots(None, 35, crystal_boot=True))
    leg, waist = harness_legs(71)
    k = 2
    img1 = np.zeros((32 * k, 64 * k, 4))
    save(img1, 'models/armor/odm_harness_layer_1.png')
    img2 = np.zeros((32 * k, 64 * k, 4))
    paint_box(img2, k, 'body', 16, 16, 8, 12, 4, waist)
    paint_box(img2, k, 'leg', 0, 16, 4, 12, 4, leg)
    save(img2, 'models/armor/odm_harness_layer_2.png')
    cloak_texture()
    rig_textures()


if __name__ == '__main__':
    main()
