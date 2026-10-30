"""
Takem Titans - every item icon, 32x32.

Each icon is drawn from shapes at 8x, shaded with light from the top left (flat, cylinder or sphere shading), then
reduced to 32px: a selective outline (each edge darkened in the colour it borders), a rim of light on the lit edges
and a little shadow on the far ones. One look for the whole mod; same colours and emblems as the gear you wear.

Run from the repo root:  python tools/icon_art.py            (all)
                         python tools/icon_art.py flare boots  (names containing these words)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(REPO, 'src/main/resources/assets/takemtitans/textures/item')
SS = 8
N = 32 * SS
LIGHT = np.array([-0.6, -0.8])  # light comes from the top left


def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255.0


def mix(a, b, t):
    return a * (1 - t) + b * t


class Icon:
    def __init__(self):
        self.c = np.zeros((N, N, 3))
        self.a = np.zeros((N, N))
        ys, xs = np.mgrid[0:N, 0:N]
        self.x = (xs + 0.5) / SS
        self.y = (ys + 0.5) / SS

    # --- shapes (all coordinates in icon pixels) -----------------------------------------------------------------
    def poly(self, pts):
        im = Image.new('L', (N, N), 0)
        ImageDraw.Draw(im).polygon([(px * SS, py * SS) for px, py in pts], fill=255)
        return np.array(im) > 127

    def ellipse(self, cx, cy, rx, ry, ang=0.0):
        ca, sa = math.cos(ang), math.sin(ang)
        dx, dy = self.x - cx, self.y - cy
        u = (dx * ca + dy * sa) / rx
        v = (-dx * sa + dy * ca) / ry
        return u * u + v * v <= 1.0

    def circle(self, cx, cy, r):
        return self.ellipse(cx, cy, r, r)

    def capsule(self, ax, ay, bx, by, r0, r1=None):
        r1 = r0 if r1 is None else r1
        dx, dy = bx - ax, by - ay
        L2 = dx * dx + dy * dy + 1e-9
        t = np.clip(((self.x - ax) * dx + (self.y - ay) * dy) / L2, 0, 1)
        px, py = ax + t * dx, ay + t * dy
        return np.hypot(self.x - px, self.y - py) <= r0 + (r1 - r0) * t

    def rect(self, x0, y0, x1, y1):
        return (self.x >= x0) & (self.x < x1) & (self.y >= y0) & (self.y < y1)

    def obox(self, cx, cy, length, width, ang, u0=None, u1=None):
        """A box along direction `ang` through (cx, cy): u along it (from u0 to u1), v across it."""
        u, v = self.frame(cx, cy, ang)
        u0 = -length / 2 if u0 is None else u0
        u1 = length / 2 if u1 is None else u1
        return (u >= u0) & (u <= u1) & (np.abs(v) <= width / 2)

    def frame(self, cx, cy, ang):
        ca, sa = math.cos(ang), math.sin(ang)
        dx, dy = self.x - cx, self.y - cy
        return dx * ca + dy * sa, -dx * sa + dy * ca

    # --- paint -----------------------------------------------------------------------------------------------------
    def fill(self, mask, col, shade='lin', amount=0.32, axis=None, center=None, radius=None, sep=None):
        """Paints `col` into `mask`. shade: 'flat', 'lin' (lit top-left), 'cyl' (round across `axis`), 'sph'."""
        if not mask.any():
            return mask
        col = np.asarray(col, dtype=float)
        if sep is not None:
            # a dark line round the part, where it overlaps what is under it
            ring = ndimage.binary_dilation(mask, iterations=int(sep * SS)) & ~mask & (self.a > 0)
            self.c[ring] = self.c[ring] * 0.45
        ys, xs = np.nonzero(mask)
        x0, x1, y0, y1 = xs.min() / SS, (xs.max() + 1) / SS, ys.min() / SS, (ys.max() + 1) / SS
        if shade == 'flat':
            k = np.ones(self.x.shape)
        elif shade == 'lin':
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            span = max(x1 - x0, y1 - y0, 1e-3) / 2
            t = ((self.x - cx) * LIGHT[0] + (self.y - cy) * LIGHT[1]) / span
            k = 1 + amount * 0.5 * np.clip(t, -1, 1)
        elif shade == 'cyl':
            ang = axis if axis is not None else 0.0
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            u, v = self.frame(cx, cy, ang)
            half = max(np.abs(v[mask]).max(), 1e-3)
            t = np.clip(v / half, -1, 1)
            # bright band a little towards the light, dark at the far rim
            side = -1 if (math.sin(ang) * LIGHT[0] - math.cos(ang) * LIGHT[1]) > 0 else 1
            k = 0.72 + 0.5 * np.cos((t - 0.35 * side) * math.pi / 2.2) ** 2
            k = k + 0.25 * np.exp(-((t + 0.45 * side) / 0.18) ** 2)
        elif shade == 'sph':
            cx, cy = center if center is not None else ((x0 + x1) / 2, (y0 + y1) / 2)
            r = radius if radius is not None else max(x1 - x0, y1 - y0) / 2
            hx, hy = cx + LIGHT[0] * r * 0.4, cy + LIGHT[1] * r * 0.4
            d = np.hypot(self.x - hx, self.y - hy) / (r * 1.4)
            k = 1.25 - 0.6 * np.clip(d, 0, 1)
            k = k + 0.3 * np.exp(-(d / 0.18) ** 2)
        else:
            raise ValueError(shade)
        self.c[mask] = np.clip(col[None, :] * k[mask][:, None], 0, 1)
        self.a[mask] = 1.0
        return mask

    def line(self, ax, ay, bx, by, w, col, shade='flat'):
        return self.fill(self.capsule(ax, ay, bx, by, w / 2), col, shade)

    def glint(self, cx, cy, s, col=rgb('ffffff')):
        """A four-pointed sparkle."""
        m = (np.abs(self.x - cx) < s * 0.18) & (np.abs(self.y - cy) < s) | (np.abs(self.y - cy) < s * 0.18) & (np.abs(self.x - cx) < s)
        self.fill(m, col, 'flat')

    # --- finishing -------------------------------------------------------------------------------------------------
    def done(self, outline_dark=0.42):
        a = self.a.reshape(32, SS, 32, SS).mean(axis=(1, 3))
        c = (self.c * self.a[..., None]).reshape(32, SS, 32, SS, 3).sum(axis=(1, 3)) / (a[..., None] * SS * SS + 1e-9)
        solid = a > 0.42
        out = np.zeros((32, 32, 4))
        out[..., :3] = c
        out[..., 3] = solid
        pad = np.pad(solid, 1)
        up, left = pad[:-2, 1:-1], pad[1:-1, :-2]
        down, right = pad[2:, 1:-1], pad[1:-1, 2:]
        # light catches the top-left edges, the bottom-right ones fall into shadow
        lit = solid & (~up | ~left)
        out[lit, :3] = np.clip(out[lit, :3] * 1.12 + 0.03, 0, 1)
        dim = solid & (~down | ~right) & ~lit
        out[dim, :3] = out[dim, :3] * 0.84
        # selective outline: each outline pixel takes the darkened colour of the part it borders
        ring = ~solid & (up | left | down | right)
        cp = np.pad(out[..., :3], ((1, 1), (1, 1), (0, 0)))
        sp = np.pad(solid, 1).astype(float)
        acc = np.zeros((32, 32, 3))
        cnt = np.zeros((32, 32))
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            s_ = sp[1 + dy:33 + dy, 1 + dx:33 + dx]
            acc += cp[1 + dy:33 + dy, 1 + dx:33 + dx] * s_[..., None]
            cnt += s_
        avg = acc / np.maximum(cnt, 1)[..., None]
        out[ring, :3] = avg[ring] * outline_dark
        out[ring, 3] = 1.0
        return out


def save(img, name):
    a = np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(a, 'RGBA').save(os.path.join(TEX, name + '.png'), optimize=True)
    print('  ', name)


# ---------------------------------------------------------------------------------------------------------------------
# Shared pieces
# ---------------------------------------------------------------------------------------------------------------------
WHITE, BLUE, INK = rgb('f2efe6'), rgb('2f6fd8'), rgb('1a1a1a')
BRASS, IRON, STEEL = rgb('c9a24a'), rgb('3c3f44'), rgb('b8bec6')
LEATHER, DLEATHER = rgb('7a4e2e'), rgb('3e2617')
WOOD, DWOOD = rgb('8a5a32'), rgb('5a3820')
GREEN, DGREEN = rgb('2f5a3a'), rgb('1f3d27')


def emblem(ic, cx, cy, s):
    """The Wings of Freedom on a shield, s pixels wide."""
    u = (ic.x - cx) / s
    v = (ic.y - cy) / s
    shield = (np.abs(u) < 0.5) & (v > -0.5) & (v < 0.55 - np.abs(u) * 0.95) & (v < 0.5 - 1.75 * u * u * (v > 0.05))
    ic.fill(shield, INK, 'flat')
    inner = (np.abs(u) < 0.4) & (v > -0.38) & (v < 0.45 - np.abs(u) * 0.95) & (v < 0.4 - 1.75 * u * u * (v > 0.05))
    ic.fill(inner & (u < 0), WHITE, 'flat')
    ic.fill(inner & (u >= 0), BLUE, 'flat')
    feather = inner & (np.abs(np.mod(v * 3.2 - np.abs(u) * 1.6 + 10, 1.0) - 0.5) < 0.12)
    ic.fill(feather & (u < 0), rgb('a9a59c'), 'flat')
    ic.fill(feather & (u >= 0), rgb('1d4ea8'), 'flat')


def crystal_fill(ic, mask, seed, base=rgb('cfeaff')):
    rng = np.random.default_rng(seed)
    ys, xs = np.nonzero(mask)
    pts = np.stack([rng.uniform(xs.min(), xs.max() + 1, 9) / SS, rng.uniform(ys.min(), ys.max() + 1, 9) / SS], 1)
    d = np.stack([np.hypot(ic.x - px, ic.y - py) for px, py in pts])
    o = np.sort(d, axis=0)
    cell = np.argmin(d, axis=0)
    tone = rng.random(len(pts))[cell]
    col = base[None, None, :] * (0.8 + 0.3 * tone[..., None])
    ridge = (o[1] - o[0]) < 0.35
    col[ridge] = np.clip(col[ridge] * 1.18, 0, 1)
    ic.c[mask] = col[mask]
    ic.a[mask] = 1.0


# ---------------------------------------------------------------------------------------------------------------------
# Blades and rigs
# ---------------------------------------------------------------------------------------------------------------------
BLADE_TIERS = {
    'odm': dict(steel='c9cdd2', edge='f4f7fa', grip='2b2522', metal='5d6168', accent='8c6a3c'),
    'veteran': dict(steel='c4d6e8', edge='f2f9ff', grip='3a1f1c', metal='4f5560', accent='b23a30'),
    'serrated': dict(steel='d9a3a0', edge='ffe6e0', grip='1d1a1a', metal='3b3d42', accent='d33a2c', teeth=True),
    'ackerman': dict(steel='4a4d55', edge='ff5a4a', grip='161414', metal='2a2b30', accent='b0201a'),
    'titanbreaker': dict(steel='e2bb5c', edge='fff1c2', grip='1e2a3c', metal='3c4250', accent='49c6ff', glow='7fdcff'),
    'founder': dict(steel='b79cf0', edge='f4ecff', grip='23173a', metal='3a2a5a', accent='e7c45b', glow='c7a8ff'),
}


def blade(tier, broken):
    t = {k: (rgb(v) if isinstance(v, str) else v) for k, v in BLADE_TIERS[tier].items()}
    ic = Icon()
    ang = -math.pi / 4
    cx, cy = 16.0, 16.0
    u, v = ic.frame(cx, cy, ang)
    w = 2.6
    end = 15.6 if not broken else 3.4
    body = (u > -4.0) & (u < end) & (np.abs(v) < w)
    if broken:
        body &= u < end - 0.9 * np.abs(np.sin(v * 3.3)) - 0.3
    else:
        body &= u < end - 3.4 * (v + w) / (2 * w)
    if t.get('teeth'):
        body &= ~((v > 0.6) & (np.mod(u, 1.7) < 0.8) & (v > w - 1.4 * np.mod(u, 1.7) / 0.8))
    across = (v + w) / (2 * w)
    col = mix(t['steel'][None, None, :], t['edge'][None, None, :], np.clip((across - 0.55) / 0.45, 0, 1)[..., None])
    col = col * (1.0 + 0.22 * np.exp(-((across - 0.28) / 0.13) ** 2))[..., None]
    col = col * (1 - 0.12 * ((np.mod(u + 4.0, 3.0) < 0.3) & (across < 0.8)))[..., None]
    if 'glow' in t:
        rune = (np.abs(across - 0.36) < 0.09) & (np.mod(u + 4.0, 3.0) > 0.6)
        col = np.where(rune[..., None], t['glow'][None, None, :], col)
    ic.c[body] = np.clip(col[body], 0, 1)
    ic.a[body] = 1.0
    ic.fill(ic.obox(cx, cy, 0, 6.6, ang, -7.6, -4.0), t['metal'], 'cyl', axis=ang, sep=0.25)
    ic.fill(ic.obox(cx, cy, 0, 1.8, ang, -6.6, -5.0), t['accent'], 'flat')
    grip = ic.obox(cx, cy, 0, 3.4, ang, -13.6, -7.6)
    band = np.mod(u * 1.7 + v, 2.0) < 1.0
    ic.fill(grip, t['grip'], 'cyl', axis=ang)
    ic.c[grip & band] *= 1.35
    ic.fill(ic.obox(cx, cy, 0, 1.5, ang, -10.6, -8.8) & (v > 1.2) & (v < 3.0) | (ic.obox(cx, cy, 0, 4, ang, -10.6, -8.8) & (v > 1.3) & (v < 3.0)), t['accent'], 'flat')
    ic.fill(ic.obox(cx, cy, 0, 4.2, ang, -15.6, -13.6), t['metal'] * 1.25, 'sph')
    return ic.done()


RIGS = {
    'odm_gear': dict(body='5b5f66', accent='3aa0d8', tank='c8ccd2'),
    'advanced_odm_gear': dict(body='3c3f45', accent='e0782e', tank='8e949b'),
    'phantom_odm_gear': dict(body='283442', accent='6fe3ff', tank='3e5366', glow='c8f4ff'),
    'godspeed_odm_gear': dict(body='e6dfca', accent='f2c84b', tank='f4efe0', glow='fff0b0'),
    'event_horizon_odm_gear': dict(body='1b1426', accent='b070ff', tank='2a1f3d', glow='e0c4ff'),
}


def rig(name):
    p = {k: rgb(v) for k, v in RIGS[name].items()}
    ic = Icon()
    for cx in (7.6, 24.4):
        tank = ic.capsule(cx, 10.5, cx, 26.5, 4.3)
        ic.fill(tank, p['tank'], 'cyl', axis=math.pi / 2)
        for yy in (14.0, 23.0):
            ic.fill(tank & (np.abs(ic.y - yy) < 0.75), p['accent'], 'cyl', axis=math.pi / 2)
        ic.fill(ic.rect(cx - 1.2, 4.6, cx + 1.2, 7.6), p['body'] * 1.3, 'cyl', axis=math.pi / 2)
    box = ic.rect(10.0, 6.5, 22.0, 20.0)
    ic.fill(box, p['body'], 'lin', amount=0.5, sep=0.4)
    drum = ic.capsule(11.8, 10.0, 20.2, 10.0, 2.1)
    ic.fill(drum, rgb('3a3a3c'), 'cyl', axis=0.0)
    ic.c[drum & (np.mod(ic.x * 1.4 + (ic.y - 10) * 0.4, 1.0) < 0.45)] *= 1.6
    ic.fill(ic.rect(10.0, 14.4, 22.0, 15.8), p['accent'], 'flat')
    if 'glow' in p:
        ic.fill(ic.rect(10.0, 14.8, 22.0, 15.4), p['glow'], 'flat')
    ic.fill(ic.rect(14.4, 20.0, 17.6, 23.2), p['body'] * 0.75, 'flat')
    ic.fill(ic.ellipse(16.0, 23.4, 2.4, 0.9), p['body'] * 1.1, 'flat')
    for s in (-1, 1):
        ic.line(16 + s * 3.0, 6.6, 16 + s * 9.5, 1.8, 0.8, rgb('2a2a2a'))
        ic.fill(ic.circle(16 + s * 9.7, 2.2, 1.6), mix(p['body'], rgb('ffffff'), 0.45), 'sph')
    return ic.done()


# ---------------------------------------------------------------------------------------------------------------------
# Weapons and tools
# ---------------------------------------------------------------------------------------------------------------------
def spear(head='thunder'):
    ic = Icon()
    ang = -math.pi / 4
    u, v = ic.frame(16, 16, ang)
    ic.fill(ic.obox(16, 16, 0, 2.0, ang, -14.5, 5.5), rgb('5a4a3a'), 'cyl', axis=ang)
    for k in range(3):
        ic.fill(ic.obox(16, 16, 0, 2.2, ang, -11.2 + k * 2.4, -10.4 + k * 2.4), rgb('a8a8a0'), 'flat')
    fins = (u > -15.2) & (u < -11.0) & (np.abs(v) < 1.0 + (-11.0 - u) * 0.85)
    ic.fill(fins, rgb('8a2a22') if head == 'thunder' else rgb('2a2a2a'), 'lin')
    ic.fill(ic.obox(16, 16, 0, 2.2, ang, -14.5, 5.5), rgb('5a4a3a'), 'cyl', axis=ang)
    if head == 'thunder':
        body = (u > 4.5) & (u < 11.5) & (np.abs(v) < 2.7 - np.clip(u - 9.2, 0, 3) * 0.9)
        ic.fill(body, rgb('6b7076'), 'cyl', axis=ang)
        ic.fill(body & (np.abs(u - 6.6) < 0.8), rgb('d8b440'), 'flat')
        ic.fill((u >= 11.0) & (u < 15.4) & (np.abs(v) < (15.4 - u) * 0.62), rgb('dfe4ea'), 'cyl', axis=ang)
    else:
        # the cluster head: a canister of bomblets held in a cage
        body = (u > 4.0) & (u < 12.5) & (np.abs(v) < 3.0)
        ic.fill(body, rgb('3a3d42'), 'cyl', axis=ang)
        for uu in (5.8, 8.2, 10.6):
            for vv in (-1.4, 1.4):
                bx = 16 + uu * math.cos(ang) - vv * math.sin(ang)
                by = 16 + uu * math.sin(ang) + vv * math.cos(ang)
                ic.fill(ic.circle(bx, by, 1.15), rgb('c23a2a'), 'sph')
        ic.fill((u >= 12.5) & (u < 15.0) & (np.abs(v) < (15.0 - u) * 1.1), rgb('c23a2a'), 'lin')
    return ic.done()


def thunder_battery():
    ic = Icon()
    ang = -math.pi / 4
    # four short thunder spears side by side in a clamp, their charged tips glowing
    perp = (math.cos(ang + math.pi / 2), math.sin(ang + math.pi / 2))
    for off in (4.5, 1.5, -1.5, -4.5):
        cx, cy = 15.0 + off * perp[0], 17.0 + off * perp[1]
        u1 = 10.5 - abs(off) * 0.5
        ic.fill(ic.obox(cx, cy, 0, 1.4, ang, -12.0, u1 - 2.5), rgb('5a4a3a'), 'cyl', axis=ang)
        ic.fill(ic.obox(cx, cy, 0, 2.6, ang, u1 - 3.5, u1), rgb('6b7076'), 'cyl', axis=ang, sep=0.2)
        ic.fill(ic.obox(cx, cy, 0, 2.7, ang, u1 - 2.6, u1 - 1.9), rgb('d8b440'), 'flat')
        tx, ty = cx + (u1 + 1.2) * math.cos(ang), cy + (u1 + 1.2) * math.sin(ang)
        ic.fill(ic.circle(tx, ty, 1.35), rgb('9fe9ff'), 'sph')
    for c in (-6.0, 2.0):
        ic.fill(ic.obox(15.0, 17.0, 0, 13.5, ang, c - 1.2, c + 1.2), rgb('3c3f44'), 'lin', sep=0.25)
        bx, by = 15.0 + c * math.cos(ang), 17.0 + c * math.sin(ang)
        ic.fill(ic.circle(bx, by, 0.9), BRASS, 'sph')
    return ic.done()


def flare_gun(colour=None):
    ic = Icon()
    barrel = ic.capsule(8.0, 11.5, 28.0, 11.5, 3.1)
    ic.fill(barrel & (ic.x < 27.8), rgb('b98a3a'), 'cyl', axis=0.0, sep=0.3)
    ic.fill(ic.ellipse(28.0, 11.5, 1.2, 3.1), rgb('2a1d10'), 'flat')
    ic.fill(ic.rect(5.5, 9.0, 11.0, 15.4), rgb('6b7076'), 'lin')
    grip = ic.poly([(6.5, 14.6), (11.6, 14.6), (9.6, 27.6), (3.6, 27.6)])
    ic.fill(grip, rgb('6a4026'), 'lin', amount=0.5, sep=0.3)
    ic.c[grip & (np.mod(ic.y * 1.3 + ic.x * 0.4, 1.6) < 0.25)] *= 0.8
    ic.fill(ic.poly([(11.0, 15.0), (14.6, 15.0), (14.0, 19.0), (11.0, 17.8)]), rgb('3c3f44'), 'flat')
    ic.fill(ic.poly([(5.2, 8.6), (7.2, 5.8), (8.4, 6.4), (7.6, 9.2)]), rgb('3c3f44'), 'flat')
    if colour is not None:
        band = barrel & (ic.x > 14.0) & (ic.x < 19.0)
        ic.fill(band, colour, 'cyl', axis=0.0)
        ic.fill(ic.circle(30.4, 7.6, 1.6), mix(colour, rgb('ffffff'), 0.25), 'sph')
        ic.fill(ic.circle(29.0, 4.6, 1.2), mix(colour, rgb('ffffff'), 0.35), 'sph')
    return ic.done()


FLARE_COLOURS = {'red': 'd8302a', 'green': '3fb24a', 'yellow': 'f0c63c', 'purple': '9a4ad8', 'black': '2a2a2e'}


def flare_shell(colour):
    ic = Icon()
    body = ic.rect(10.5, 4.5, 21.5, 22.0)
    ic.fill(body, colour, 'cyl', axis=math.pi / 2, sep=0.3)
    ic.fill(ic.ellipse(16.0, 4.5, 5.5, 1.6), mix(colour, rgb('000000'), 0.35), 'flat')
    ic.fill(ic.circle(16.0, 4.5, 1.2), mix(colour, rgb('ffffff'), 0.2), 'flat')
    if colour[0] < 0.25 and colour[1] < 0.25:
        ic.fill(body & (np.abs(ic.y - 13.0) < 1.5), rgb('e8e2d2'), 'cyl', axis=math.pi / 2)
    base = ic.rect(10.0, 21.0, 22.0, 28.0)
    ic.fill(base, BRASS, 'cyl', axis=math.pi / 2, sep=0.3)
    ic.fill(ic.rect(9.4, 26.6, 22.6, 28.4), BRASS * 0.85, 'cyl', axis=math.pi / 2)
    ic.fill(ic.circle(16.0, 24.6, 1.1), rgb('5a4a20'), 'flat')
    return ic.done()


def cannon(big=False):
    ic = Icon()
    s = 1.0 if big else 0.85
    ang = -0.32
    ax, ay = 9.0, 16.5
    L = 21.0 * s
    bx, by = ax + L * math.cos(ang), ay + L * math.sin(ang)
    barrel = ic.capsule(ax, ay, bx, by, 4.2 * s, 3.0 * s)
    ic.fill(barrel, rgb('34373c'), 'cyl', axis=ang, sep=0.3)
    for t in (0.15, 0.55, 0.92):
        px, py = ax + (bx - ax) * t, ay + (by - ay) * t
        ic.fill(ic.obox(px, py, 1.4, (8.8 - 2.4 * t) * s, ang), rgb('4a4e54'), 'cyl', axis=ang)
    ic.fill(ic.circle(bx, by, 2.2 * s), rgb('111111'), 'flat')
    ic.fill(ic.circle(ax - 3.4 * s, ay + 1.2 * s, 2.0 * s), rgb('34373c'), 'sph')
    carriage = ic.poly([(4.0, 21.0), (20.0, 18.5), (22.0, 23.5), (5.5, 26.5)])
    ic.fill(carriage, WOOD, 'lin', sep=0.3)
    wx, wy, wr = 14.5, 24.2, 6.6 * s if big else 5.6
    rim = ic.circle(wx, wy, wr) & ~ic.circle(wx, wy, wr - 1.4)
    ic.fill(rim, DWOOD, 'lin')
    for k in range(6):
        a = k * math.pi / 3
        ic.line(wx, wy, wx + (wr - 1) * math.cos(a), wy + (wr - 1) * math.sin(a), 0.9, DWOOD)
    ic.fill(ic.circle(wx, wy, 1.4), IRON, 'sph')
    if big:
        ic.fill(ic.rect(2.0, 26.0, 9.0, 28.6), WOOD, 'lin')
    return ic.done()


def cannon_shell():
    ic = Icon()
    body = ic.capsule(16.0, 9.0, 16.0, 22.0, 5.4)
    ic.fill(body & (ic.y < 21.5), STEEL * 0.8, 'sph', center=(16.0, 13.0), radius=6.5, sep=0.3)
    ic.fill(ic.poly([(10.6, 11.0), (16.0, 2.5), (21.4, 11.0)]) | ic.ellipse(16.0, 11.0, 5.4, 2.0), STEEL * 0.8, 'cyl', axis=math.pi / 2)
    ic.fill(ic.rect(10.6, 14.4, 21.4, 16.2), rgb('3a7fd0'), 'cyl', axis=math.pi / 2)
    ic.fill(ic.rect(10.4, 21.0, 21.6, 28.6), BRASS, 'cyl', axis=math.pi / 2, sep=0.3)
    ic.fill(ic.rect(9.8, 27.0, 22.2, 28.8), BRASS * 0.85, 'cyl', axis=math.pi / 2)
    return ic.done()


def demolition_charge():
    ic = Icon()
    for k, x in enumerate((9.0, 14.0, 19.0)):
        ic.fill(ic.capsule(x + 2.2, 8.0, x + 2.2, 27.0, 2.6), rgb('8a4a2a') if k != 1 else rgb('9a5634'), 'cyl', axis=math.pi / 2, sep=0.25)
    for yy in (12.0, 22.0):
        ic.fill(ic.rect(7.4, yy, 24.6, yy + 1.6), rgb('c2a878'), 'cyl', axis=math.pi / 2)
    box = ic.rect(12.5, 14.4, 20.5, 19.8)
    ic.fill(box, rgb('3c3f44'), 'lin', sep=0.3)
    ic.fill(ic.circle(16.5, 17.1, 1.3), rgb('ff3a2a'), 'sph')
    ic.line(20.5, 16.0, 26.0, 10.0, 0.8, rgb('d33a2c'))
    ic.line(26.0, 10.0, 28.0, 4.0, 0.8, rgb('1a1a1a'))
    return ic.done()


def thermobaric():
    ic = Icon()
    body = ic.capsule(16.0, 9.0, 16.0, 26.0, 6.5)
    ic.fill(body, rgb('c0392b'), 'cyl', axis=math.pi / 2, sep=0.3)
    band = body & (np.abs(ic.y - 18.0) < 2.4)
    ic.fill(band, rgb('f0c63c'), 'cyl', axis=math.pi / 2)
    ic.c[band & (np.mod(ic.x - ic.y, 2.4) < 1.2)] = rgb('1a1a1a')
    ic.fill(ic.rect(13.6, 1.8, 18.4, 5.6), rgb('6b7076'), 'cyl', axis=math.pi / 2)
    ic.fill(ic.rect(11.0, 1.2, 21.0, 2.6), rgb('8a8f96'), 'cyl', axis=math.pi / 2)
    ic.fill(ic.circle(20.6, 11.6, 2.2), rgb('ece6d2'), 'flat', sep=0.25)
    ic.line(20.6, 11.6, 21.8, 10.2, 0.5, rgb('b0201a'))
    return ic.done()


def smoke_bomb():
    ic = Icon()
    for cx, cy, r in ((22.0, 8.0, 4.0), (26.0, 5.0, 3.0), (18.5, 5.0, 3.0)):
        ic.fill(ic.circle(cx, cy, r), rgb('b8bcc0'), 'sph')
    ball = ic.circle(14.0, 19.5, 9.0)
    ic.fill(ball, rgb('4a4e54'), 'sph', center=(14.0, 19.5), radius=9.0, sep=0.3)
    ic.fill(ic.rect(11.8, 8.4, 16.2, 11.4), rgb('8a8f96'), 'cyl', axis=math.pi / 2)
    ic.line(15.0, 8.6, 18.8, 6.2, 0.8, rgb('6a4026'))
    ic.fill(ic.circle(19.2, 5.9, 0.9), rgb('ffb43a'), 'sph')
    return ic.done()


def net_launcher():
    ic = Icon()
    ang = -math.pi / 4
    u, v = ic.frame(16, 16, ang)
    ic.fill(ic.obox(16, 16, 0, 5.8, ang, -4.0, 9.0), rgb('3c3f44'), 'cyl', axis=ang, sep=0.3)
    ic.fill(ic.obox(16, 16, 0, 6.6, ang, 7.5, 9.0), rgb('6b7076'), 'cyl', axis=ang)
    net = (u > 9.0) & (u < 15.0) & (np.abs(v) < 4.8 - (u - 9.0) * 0.25)
    ic.fill(net, rgb('d9c7a0'), 'lin')
    ic.c[net & ((np.mod(u + v, 1.6) < 0.4) | (np.mod(u - v, 1.6) < 0.4))] = rgb('8a7650')
    grip = (u > -11.5) & (u < -4.0) & (np.abs(v) < 1.6)
    ic.fill(grip, rgb('6a4026'), 'cyl', axis=ang)
    ic.fill((u > -6.5) & (u < -3.5) & (v > 1.4) & (v < 5.5), rgb('3c3f44'), 'flat')
    return ic.done()


def blade_pack():
    ic = Icon()
    for k in range(4):
        x = 9.0 + k * 4.4
        ic.fill(ic.poly([(x, 16.0), (x, 4.5 + k % 2), (x + 2.6, 2.5 + k % 2), (x + 2.6, 16.0)]), rgb('d4d9de'), 'lin', amount=0.5, sep=0.25)
        ic.fill(ic.rect(x + 2.0, 3.0 + k % 2, x + 2.6, 16.0), rgb('f4f7fa'), 'flat')
    box = ic.rect(5.0, 14.0, 27.0, 28.0)
    ic.fill(box, rgb('4b5056'), 'lin', amount=0.4, sep=0.35)
    ic.fill(ic.rect(5.0, 14.0, 27.0, 16.2), rgb('6b7076'), 'lin')
    ic.fill(ic.rect(12.0, 19.5, 20.0, 23.5), LEATHER, 'lin')
    for x in (7.0, 25.0):
        ic.fill(ic.circle(x, 25.8, 0.8), BRASS, 'sph')
    return ic.done()


def gas_canister():
    ic = Icon()
    body = ic.capsule(16.0, 9.0, 16.0, 26.5, 6.2)
    ic.fill(body, rgb('c8ccd2'), 'cyl', axis=math.pi / 2, sep=0.3)
    ic.fill(body & (np.abs(ic.y - 18.0) < 2.2), rgb('3aa0d8'), 'cyl', axis=math.pi / 2)
    ic.fill(ic.rect(14.0, 2.4, 18.0, 6.8), rgb('5d6168'), 'cyl', axis=math.pi / 2)
    ic.fill(ic.rect(12.0, 1.6, 20.0, 3.0), rgb('8a8f96'), 'cyl', axis=math.pi / 2)
    return ic.done()


# ---------------------------------------------------------------------------------------------------------------------
# Field kit
# ---------------------------------------------------------------------------------------------------------------------
def med_kit():
    ic = Icon()
    ic.fill(ic.capsule(12.0, 7.5, 20.0, 7.5, 1.4) & ~ic.capsule(12.0, 7.5, 20.0, 7.5, 0.5), rgb('6b7076'), 'flat')
    box = ic.rect(4.0, 9.0, 28.0, 27.0)
    ic.fill(box, rgb('ece8de'), 'lin', amount=0.35, sep=0.35)
    ic.fill(ic.rect(4.0, 9.0, 28.0, 11.0), rgb('d2cdc0'), 'flat')
    ic.fill(ic.rect(13.5, 12.5, 18.5, 25.0) | ic.rect(9.5, 16.2, 22.5, 21.2), rgb('d0302a'), 'lin')
    for x in (6.5, 25.5):
        ic.fill(ic.rect(x - 1.0, 10.0, x + 1.0, 12.6), rgb('8a8f96'), 'flat')
    return ic.done()


def whetstone():
    ic = Icon()
    ic.fill(ic.poly([(3.0, 20.0), (27.0, 16.0), (29.0, 21.0), (5.0, 25.5)]), rgb('5a3820'), 'lin', sep=0.3)
    stone = ic.poly([(4.5, 15.5), (26.5, 11.5), (28.0, 16.5), (6.0, 20.5)])
    ic.fill(stone, rgb('8f9aa3'), 'lin', amount=0.5, sep=0.3)
    ic.c[stone & (np.mod(ic.x * 0.7 - ic.y * 1.2, 2.0) < 0.3)] *= 0.88
    ic.line(7.0, 15.5, 25.5, 12.2, 0.6, rgb('c3ccd4'))
    for x, y in ((22.0, 8.0), (25.0, 6.0), (19.5, 6.5)):
        ic.glint(x, y, 1.3, rgb('fff2b0'))
    return ic.done()


def syringe(liquid, glow=False):
    ic = Icon()
    ang = -math.pi / 4
    u, v = ic.frame(16, 16, ang)
    ic.fill((u > -14.0) & (u < -11.0) & (np.abs(v) < 4.0), rgb('8a8f96'), 'lin')
    ic.fill((u > -11.0) & (u < -6.0) & (np.abs(v) < 0.9), rgb('8a8f96'), 'flat')
    glass = (u > -6.0) & (u < 7.0) & (np.abs(v) < 3.4)
    ic.fill(glass, rgb('dfe8ee'), 'cyl', axis=ang, sep=0.3)
    fluid = (u > -3.0) & (u < 6.6) & (np.abs(v) < 2.6)
    ic.fill(fluid, liquid, 'cyl', axis=ang)
    if glow:
        ic.fill(fluid & (np.abs(v + 0.6) < 0.7), mix(liquid, rgb('ffffff'), 0.5), 'flat')
    for k in range(4):
        ic.fill(glass & (np.abs(u - (-4.5 + k * 3.0)) < 0.25) & (v > 1.2), rgb('5a6066'), 'flat')
    ic.fill((u > 7.0) & (u < 9.0) & (np.abs(v) < 2.0), rgb('8a8f96'), 'cyl', axis=ang)
    ic.fill((u > 9.0) & (u < 15.0) & (np.abs(v) < 0.45), rgb('d8dde2'), 'flat')
    return ic.done()


def horn(curved_big=False):
    ic = Icon()
    if curved_big:
        # the war horn of the Rumbling: a great curved horn of ivory, banded in iron
        outer = ic.circle(17.0, 25.0, 15.5) & ~ic.circle(19.0, 27.5, 11.0) & (ic.y < 25.5) & (ic.x > 2.0)
        ic.fill(outer, rgb('e6dcc4'), 'lin', amount=0.45, sep=0.3)
        r = np.hypot(ic.x - 17.0, ic.y - 25.0)
        ic.c[outer & (np.mod(np.arctan2(ic.y - 25.0, ic.x - 17.0) * 6.0, 1.0) < 0.12)] *= 0.8
        for a in (-2.4, -1.6, -0.8):
            px, py = 17.0 + 13.3 * math.cos(a), 25.0 + 13.3 * math.sin(a)
            ic.fill(ic.obox(px, py, 1.6, 6.4, a), rgb('3c3f44'), 'cyl', axis=a + math.pi / 2)
        ic.fill(ic.circle(28.6, 22.6, 2.6), rgb('6a5a40'), 'sph')
        ic.fill(ic.circle(3.0, 23.0, 1.4), rgb('3c3f44'), 'sph')
    else:
        # a brass signal bugle: one long loop of tube, the bell flaring out to the right, a red cord
        loop = ic.ellipse(14.0, 15.5, 10.5, 5.6) & ~ic.ellipse(14.0, 15.5, 8.4, 3.6)
        ic.fill(loop, BRASS, 'lin', amount=0.5, sep=0.25)
        ic.fill(ic.capsule(14.0, 9.9, 24.0, 9.9, 1.1), BRASS * 1.05, 'cyl', axis=0.0)
        bell = ic.poly([(23.0, 8.8), (30.5, 3.5), (30.5, 16.5), (23.0, 11.0)])
        ic.fill(bell, BRASS * 1.1, 'lin', amount=0.5, sep=0.25)
        ic.fill(ic.ellipse(30.3, 10.0, 1.2, 6.4), rgb('6a5420'), 'flat')
        ic.fill(ic.capsule(3.5, 21.1, 9.0, 21.1, 1.0), BRASS * 0.9, 'cyl', axis=0.0)
        ic.fill(ic.circle(2.6, 21.1, 1.4), BRASS * 0.8, 'sph')
        ic.line(9.0, 21.5, 12.0, 28.5, 0.9, rgb('c0392b'))
        ic.line(19.0, 21.0, 16.5, 28.5, 0.9, rgb('c0392b'))
        ic.fill(ic.circle(14.2, 29.0, 1.6), rgb('c0392b'), 'sph')
    return ic.done()


def whistle():
    ic = Icon()
    loop = ic.circle(11.0, 11.0, 8.0) & ~ic.circle(11.0, 11.0, 6.9)
    ic.fill(loop & ~((ic.x > 13) & (ic.y > 13)), LEATHER, 'flat')
    body = ic.capsule(14.5, 19.5, 25.0, 25.5, 3.2)
    ic.fill(body, STEEL, 'cyl', axis=math.atan2(6.0, 10.5), sep=0.3)
    ic.fill(ic.circle(14.2, 19.4, 2.0), rgb('3c3f44'), 'flat')
    ic.fill(ic.rect(19.0, 19.2, 21.6, 20.6), rgb('2a2a2a'), 'flat')
    ic.fill(ic.circle(26.0, 26.2, 1.6), STEEL * 1.1, 'sph')
    return ic.done()


def manual():
    ic = Icon()
    pages = ic.poly([(7.0, 6.5), (26.5, 4.5), (27.5, 26.5), (8.0, 28.5)])
    ic.fill(pages, rgb('efe6cf'), 'flat', sep=0.3)
    cover = ic.poly([(5.0, 5.0), (24.5, 3.0), (25.5, 25.0), (6.0, 27.0)])
    ic.fill(cover, rgb('6a3e22'), 'lin', amount=0.45, sep=0.3)
    ic.fill(ic.poly([(5.0, 5.0), (7.4, 4.8), (8.4, 26.8), (6.0, 27.0)]), rgb('4a2a16'), 'flat')
    emblem(ic, 16.0, 14.5, 10.0)
    ic.fill(ic.poly([(19.0, 25.6), (21.0, 25.4), (21.4, 30.5), (20.2, 29.4), (19.4, 30.7)]), rgb('c0392b'), 'flat')
    return ic.done()


def merit_badge():
    ic = Icon()
    ribbon = ic.poly([(9.0, 1.5), (23.0, 1.5), (20.5, 13.0), (11.5, 13.0)])
    ic.fill(ribbon, rgb('2f6fd8'), 'lin', sep=0.25)
    ic.fill(ribbon & (np.abs(ic.x - 16.0) < 2.0), rgb('f2efe6'), 'lin')
    medal = ic.circle(16.0, 20.5, 8.6)
    ic.fill(medal, rgb('e2b24a'), 'sph', center=(16.0, 20.5), radius=8.6, sep=0.3)
    ic.fill(ic.circle(16.0, 20.5, 6.6) & ~ic.circle(16.0, 20.5, 5.9), rgb('a87a28'), 'flat')
    emblem(ic, 16.0, 20.6, 7.6)
    return ic.done()


def ingot():
    ic = Icon()
    top = ic.poly([(8.0, 10.0), (24.0, 10.0), (28.0, 16.0), (4.0, 16.0)])
    front = ic.poly([(4.0, 16.0), (28.0, 16.0), (27.0, 23.0), (5.0, 23.0)])
    ic.fill(front, rgb('6f8aa8'), 'lin', sep=0.3)
    ic.fill(top, rgb('a9c4de'), 'lin', amount=0.5)
    ic.line(10.0, 12.0, 21.0, 12.0, 0.6, rgb('e6f2ff'))
    ic.fill(ic.rect(13.5, 17.5, 18.5, 21.5), rgb('4e6683'), 'flat')
    ic.glint(25.0, 9.0, 2.0)
    return ic.done()


def shard(kind):
    ic = Icon()
    if kind == 'ice':
        cols = [rgb('bfe8ff'), rgb('8fd2ff'), rgb('d8f2ff')]
        spikes = [((16.0, 2.0), (12.0, 28.0), (20.0, 28.0)), ((8.0, 10.0), (6.0, 28.0), (13.0, 27.0)), ((25.0, 9.0), (19.5, 27.5), (26.5, 28.0))]
        for (tip, a, b), c in zip(spikes, cols):
            m = ic.poly([tip, a, b])
            ic.fill(m, c, 'lin', amount=0.6, sep=0.25)
            ic.fill(ic.poly([tip, a, ((a[0] + b[0]) / 2, a[1])]) & m, c * 1.12, 'flat')
        ic.glint(9.0, 6.0, 2.0)
    elif kind == 'heart':
        m = ic.poly([(16.0, 2.5), (26.5, 11.0), (23.5, 25.0), (15.0, 29.5), (6.5, 22.0), (6.0, 10.0)])
        ic.fill(m, rgb('b0182c'), 'lin', amount=0.5, sep=0.3)
        for pts in (((16.0, 2.5), (6.0, 10.0), (14.0, 15.0)), ((16.0, 2.5), (26.5, 11.0), (17.0, 15.0)), ((6.5, 22.0), (15.0, 29.5), (14.0, 15.0))):
            ic.fill(ic.poly(pts) & m, rgb('e0405a'), 'lin', amount=0.4)
        veins = m & (np.abs(np.sin(ic.x * 1.4 + ic.y * 0.7) * 3 - (ic.y - 16) * 0.3) < 0.35)
        ic.fill(veins, rgb('ff8aa0'), 'flat')
    else:
        m = ic.poly([(17.0, 1.5), (23.0, 10.0), (19.0, 30.0), (13.0, 30.0), (9.0, 11.0)])
        ic.fill(m, rgb('aef4ff'), 'lin', amount=0.6, sep=0.3)
        ic.fill(ic.poly([(17.0, 1.5), (16.0, 30.0), (13.0, 30.0), (9.0, 11.0)]) & m, rgb('e6fcff'), 'lin', amount=0.4)
        ic.glint(25.0, 6.0, 2.4, rgb('ffffff'))
        ic.glint(6.5, 22.0, 1.6, rgb('c8f6ff'))
        ic.glint(26.5, 21.0, 1.4, rgb('c8f6ff'))
    return ic.done()


def serum():
    return syringe(rgb('ff8a2a'), glow=True)


def boulder():
    ic = Icon()
    m = ic.poly([(6.0, 12.0), (12.0, 5.0), (22.0, 4.5), (28.5, 11.0), (29.0, 21.0), (23.0, 28.0), (10.0, 28.5), (3.5, 21.5)])
    ic.fill(m, rgb('8a8580'), 'sph', center=(16.0, 16.5), radius=13.0, sep=0.3)
    for a, b in (((12.0, 9.0), (16.0, 17.0)), ((16.0, 17.0), (12.0, 24.0)), ((16.0, 17.0), (24.0, 20.0))):
        ic.line(a[0], a[1], b[0], b[1], 0.6, rgb('4a4642'))
    ic.fill(m & (ic.y < 10.5) & (ic.x > 13) & (ic.x < 24), rgb('5f7a3a'), 'flat')
    return ic.done()


# ---------------------------------------------------------------------------------------------------------------------
# Gear mods
# ---------------------------------------------------------------------------------------------------------------------
def mod_barbs():
    ic = Icon()
    ic.line(16.0, 30.0, 16.0, 12.0, 2.0, rgb('6b7076'), 'cyl')
    head = ic.poly([(16.0, 1.5), (20.0, 12.0), (16.0, 14.0), (12.0, 12.0)])
    ic.fill(head, STEEL, 'lin', amount=0.5, sep=0.3)
    for s in (-1, 1):
        ic.fill(ic.poly([(16 + s * 2.6, 11.0), (16 + s * 8.5, 16.5), (16 + s * 7.5, 13.0), (16 + s * 3.4, 8.8)]), rgb('d33a2c'), 'lin', sep=0.25)
        ic.fill(ic.poly([(16 + s * 2.0, 18.0), (16 + s * 6.5, 22.0), (16 + s * 5.5, 19.0), (16 + s * 2.0, 15.6)]), rgb('d33a2c'), 'lin', sep=0.25)
    return ic.done()


def mod_frame():
    ic = Icon()
    pts = [(16 + 12.5 * math.cos(math.pi / 6 + k * math.pi / 3), 16 + 12.5 * math.sin(math.pi / 6 + k * math.pi / 3)) for k in range(6)]
    for k in range(6):
        a, b = pts[k], pts[(k + 1) % 6]
        ic.line(a[0], a[1], b[0], b[1], 2.2, rgb('9aa3ad'), 'lin')
        ic.line(a[0], a[1], 16.0, 16.0, 1.4, rgb('6b7076'), 'flat')
    for p in pts:
        ic.fill(ic.circle(p[0], p[1], 1.6), BRASS, 'sph')
    ic.fill(ic.circle(16.0, 16.0, 3.4), rgb('3aa0d8'), 'sph', sep=0.3)
    return ic.done()


def mod_long_hooks():
    ic = Icon()
    ang = -math.pi / 4
    u, v = ic.frame(16, 16, ang)
    ic.fill(ic.obox(16, 16, 0, 1.6, ang, -15.0, 8.0), rgb('6b7076'), 'cyl', axis=ang)
    head = (u > 8.0) & (u < 15.5) & (np.abs(v) < (15.5 - u) * 0.5)
    ic.fill(head, STEEL, 'cyl', axis=ang, sep=0.25)
    for s in (-1, 1):
        ic.fill((u > 6.5) & (u < 10.0) & (s * v > 0.5) & (s * v < 0.5 + (10.0 - u) * 0.9), STEEL * 0.85, 'lin')
    coil = (u > -13.0) & (u < -4.0) & (np.abs(v) < 2.6)
    ic.fill(coil, rgb('2e2e30'), 'cyl', axis=ang)
    ic.c[coil & (np.mod(u, 1.0) < 0.45)] *= 1.7
    return ic.done()


def mod_recycler():
    ic = Icon()
    body = ic.capsule(16.0, 8.0, 16.0, 24.0, 7.0)
    ic.fill(body, rgb('8e949b'), 'cyl', axis=math.pi / 2, sep=0.3)
    r = np.hypot(ic.x - 16.0, ic.y - 16.0)
    ring = (r > 3.6) & (r < 5.6)
    a = np.arctan2(ic.y - 16.0, ic.x - 16.0)
    ic.fill(ring & (np.mod(a, math.pi) > 0.5), rgb('3fb24a'), 'flat')
    for k in (0, 1):
        ang = k * math.pi + 0.5
        cx, cy = 16 + 4.6 * math.cos(ang), 16 + 4.6 * math.sin(ang)
        tang = ang - math.pi / 2
        tip = (cx + 2.4 * math.cos(tang), cy + 2.4 * math.sin(tang))
        ic.fill(ic.poly([tip, (cx + 2.0 * math.cos(ang), cy + 2.0 * math.sin(ang)), (cx - 2.0 * math.cos(ang), cy - 2.0 * math.sin(ang))]), rgb('3fb24a'), 'flat')
    ic.fill(ic.rect(13.8, 1.5, 18.2, 4.0), rgb('5d6168'), 'cyl', axis=math.pi / 2)
    return ic.done()


def mod_twin_chamber():
    ic = Icon()
    for cx in (10.5, 21.5):
        tank = ic.capsule(cx, 8.5, cx, 26.0, 4.6)
        ic.fill(tank, rgb('c8ccd2'), 'cyl', axis=math.pi / 2, sep=0.3)
        ic.fill(tank & (np.abs(ic.y - 16.0) < 1.0), rgb('3aa0d8'), 'flat')
    ic.fill(ic.rect(12.0, 13.0, 20.0, 18.0), rgb('5d6168'), 'cyl', axis=math.pi / 2, sep=0.25)
    ic.fill(ic.circle(16.0, 15.5, 1.6), rgb('d33a2c'), 'sph')
    for cx in (10.5, 21.5):
        ic.fill(ic.rect(cx - 1.5, 2.0, cx + 1.5, 4.6), rgb('6b7076'), 'cyl', axis=math.pi / 2)
    return ic.done()


def mod_winch():
    ic = Icon()
    ic.fill(ic.rect(4.0, 24.0, 28.0, 28.0), rgb('5d6168'), 'lin', sep=0.3)
    for x in (6.0, 26.0):
        ic.fill(ic.rect(x - 1.6, 9.0, x + 1.6, 26.0), rgb('6b7076'), 'cyl', axis=0.0)
    drum = ic.capsule(7.0, 16.5, 25.0, 16.5, 6.0)
    ic.fill(drum, rgb('2e2e30'), 'cyl', axis=0.0, sep=0.3)
    ic.c[drum & (np.mod(ic.x * 0.9 + (ic.y - 16) * 0.3, 1.0) < 0.45)] = rgb('6a6a6e')
    ic.fill(ic.circle(6.0, 16.5, 2.0), BRASS, 'sph')
    ic.line(26.0, 16.5, 30.0, 10.0, 1.2, rgb('8a8f96'))
    ic.fill(ic.circle(30.0, 9.6, 1.4), rgb('d33a2c'), 'sph')
    return ic.done()


# ---------------------------------------------------------------------------------------------------------------------
# What you wear
# ---------------------------------------------------------------------------------------------------------------------
def hood(base, kind):
    ic = Icon()
    if kind == 'hood':
        m = ic.poly([(16.0, 2.0), (25.0, 7.0), (28.5, 17.0), (27.0, 29.0), (5.0, 29.0), (3.5, 17.0), (7.0, 7.0)])
        ic.fill(m, base, 'lin', amount=0.45, sep=0.3)
        face = ic.ellipse(16.0, 18.5, 7.2, 8.6) & (ic.y > 10.0)
        ic.fill(face, rgb('1e140c'), 'flat')
        ic.fill(ic.ellipse(16.0, 18.5, 8.4, 9.8) & ~ic.ellipse(16.0, 18.5, 7.2, 8.6) & m & (ic.y > 9.0), base * 0.72, 'flat')
        for x in (9.5, 22.5):
            ic.line(x, 22.0, x - (x - 16) * 0.2, 30.0, 0.8, rgb('d9c7a0'))
        ic.c[m & ~face & (np.mod(ic.x * 0.8 - ic.y * 0.5, 3.0) < 0.4) & (ic.y < 12)] *= 0.85
    elif kind == 'steel':
        dome = ic.ellipse(16.0, 18.0, 12.5, 12.5) & (ic.y < 19.5)
        ic.fill(dome, rgb('a7adb3'), 'sph', center=(16.0, 17.0), radius=12.5, sep=0.3)
        rim = ic.rect(3.0, 18.0, 29.0, 21.0)
        ic.fill(rim, rgb('7b8188'), 'cyl', axis=0.0, sep=0.25)
        for x in np.arange(5.0, 28.0, 3.5):
            ic.fill(ic.circle(x, 19.5, 0.7), rgb('e6e9ec'), 'flat')
        ic.fill(ic.rect(14.6, 21.0, 17.4, 28.0), rgb('9aa0a6'), 'cyl', axis=math.pi / 2)
        ic.fill(dome & (np.abs(ic.x - 16.0) < 1.6), rgb('2e4a33'), 'cyl', axis=math.pi / 2)
    else:
        m = ic.poly([(16.0, 1.5), (27.0, 8.0), (28.5, 22.0), (23.0, 29.5), (9.0, 29.5), (3.5, 22.0), (5.0, 8.0)])
        crystal_fill(ic, m, 7)
        ic.fill(ic.rect(8.0, 13.0, 24.0, 16.0), rgb('1b2a3a'), 'flat')
        ic.fill(ic.rect(8.0, 12.2, 24.0, 13.0), rgb('8fd6ff'), 'flat')
        ic.fill(ic.rect(12.0, 16.0, 20.0, 24.0), rgb('1b2a3a'), 'flat')
    return ic.done()


def jacket(base, kind):
    ic = Icon()
    torso = ic.poly([(10.0, 4.0), (22.0, 4.0), (25.0, 7.0), (24.0, 27.0 if kind != 'coat' else 30.5), (8.0, 27.0 if kind != 'coat' else 30.5), (7.0, 7.0)])
    for s in (-1, 1):
        x0 = 16 + s * 9.0
        sleeve = ic.poly([(x0, 5.0), (16 + s * 15.0, 18.0), (16 + s * 12.0, 23.0), (16 + s * 7.0, 12.0)])
        if kind == 'crystal':
            crystal_fill(ic, sleeve, 11 + s)
        else:
            ic.fill(sleeve, base * 0.92, 'lin', sep=0.3)
            cuff = sleeve & (np.hypot(ic.x - (16 + s * 13.5), ic.y - 20.5) < 3.0)
            ic.fill(cuff, base * 0.6 if kind != 'coat' else LEATHER, 'flat')
    if kind == 'crystal':
        crystal_fill(ic, torso, 5)
        ic.line(16.0, 6.0, 16.0, 26.0, 0.6, rgb('8fd6ff'))
        return ic.done()
    ic.fill(torso, base, 'lin', amount=0.4, sep=0.3)
    ic.fill(ic.poly([(13.5, 4.0), (18.5, 4.0), (17.4, 27.0 if kind != 'coat' else 30.5), (14.6, 27.0 if kind != 'coat' else 30.5)]), SHIRT := rgb('e9e5da'), 'flat')
    for s in (-1, 1):
        ic.fill(ic.poly([(16 + s * 2.6, 4.0), (16 + s * 5.5, 4.0), (16 + s * 1.6, 12.0)]), base * 0.62, 'flat')
    if kind == 'coat':
        for yy in (14.0, 18.0, 22.0):
            for s in (-1, 1):
                ic.fill(ic.circle(16 + s * 3.4, yy, 0.8), BRASS, 'sph')
        ic.fill(ic.rect(7.6, 24.0, 24.4, 25.8), LEATHER, 'flat')
        emblem(ic, 20.5, 10.5, 5.6)
    else:
        for xx in (11.5, 20.5):
            ic.fill(ic.rect(xx - 0.8, 4.0, xx + 0.8, 27.0), rgb('2e1d12'), 'flat')
        ic.fill(ic.rect(7.6, 14.0, 24.4, 15.4), rgb('2e1d12'), 'flat')
        ic.fill(ic.rect(7.6, 23.6, 24.4, 25.6), rgb('2e1d12'), 'flat')
        ic.fill(ic.rect(14.6, 23.4, 17.4, 25.8), BRASS, 'flat')
        emblem(ic, 20.6, 9.8, 5.0)
        if kind == 'recon':
            ic.fill(ic.rect(8.5, 17.0, 12.0, 21.0), base * 0.72, 'flat', sep=0.2)
    return ic.done()


def boots(base, kind):
    ic = Icon()
    for k, dx in enumerate((6.5, 0.0)):
        dx -= 3.0
        shaft = ic.rect(7.0 + dx, 3.5 + (1 - k) * 1.5, 15.0 + dx, 24.0)
        foot = ic.poly([(7.0 + dx, 22.0), (15.0 + dx, 22.0), (22.5 + dx, 25.5), (22.5 + dx, 28.5), (7.0 + dx, 28.5)])
        m = shaft | foot
        if kind == 'crystal':
            crystal_fill(ic, m, 21 + k)
            continue
        ic.fill(m, base * (0.72 if k == 0 else 1.0), 'lin', amount=0.4, sep=0.3)
        ic.fill(ic.rect(6.6 + dx, 3.5 + (1 - k) * 1.5, 15.4 + dx, 6.2 + (1 - k) * 1.5), base * 1.3, 'flat')
        ic.fill(ic.rect(7.0 + dx, 27.4, 22.5 + dx, 28.8), rgb('1a1310'), 'flat')
        if kind == 'steel':
            ic.fill(ic.poly([(16.0 + dx, 23.0), (22.5 + dx, 25.5), (22.5 + dx, 27.4), (16.0 + dx, 27.4)]), rgb('9aa0a6'), 'lin')
        for yy in (11.0, 16.0):
            ic.fill(ic.rect(7.0 + dx, yy, 15.0 + dx, yy + 1.4), rgb('2e1d12'), 'flat')
            ic.fill(ic.rect(12.4 + dx, yy - 0.2, 14.2 + dx, yy + 1.6), BRASS, 'flat')
        ic.fill(ic.rect(8.5 + dx, 6.5, 9.6 + dx, 22.0), mix(base, rgb('ffffff'), 0.25), 'flat')
    return ic.done()


def cloak():
    ic = Icon()
    m = ic.poly([(11.0, 3.0), (21.0, 3.0), (27.0, 8.0), (29.5, 29.0), (2.5, 29.0), (5.0, 8.0)])
    ic.fill(m, GREEN, 'lin', amount=0.4, sep=0.3)
    ic.c[m & (np.abs(np.sin(ic.x * 0.9)) > 0.92) & (ic.y > 12)] *= 0.82
    hoodm = ic.ellipse(16.0, 5.5, 7.5, 4.2)
    ic.fill(hoodm, GREEN * 1.1, 'lin', sep=0.25)
    ic.fill(ic.ellipse(16.0, 5.0, 4.6, 2.0), DGREEN * 0.8, 'flat')
    emblem(ic, 16.0, 17.0, 12.0)
    ic.fill(ic.rect(2.5, 27.6, 29.5, 29.0) & m, DGREEN, 'flat')
    return ic.done()


def barding():
    ic = Icon()
    blanket = ic.poly([(4.0, 8.0), (28.0, 8.0), (29.0, 27.0), (3.0, 27.0)])
    ic.fill(blanket, GREEN, 'lin', amount=0.4, sep=0.3)
    ic.fill(ic.rect(3.0, 24.5, 29.0, 27.0) & blanket, rgb('c9a24a'), 'flat')
    emblem(ic, 16.0, 17.5, 9.0)
    saddle = ic.poly([(9.0, 4.0), (23.0, 4.0), (25.0, 9.5), (7.0, 9.5)])
    ic.fill(saddle, LEATHER, 'lin', amount=0.5, sep=0.3)
    ic.fill(ic.rect(14.5, 1.5, 17.5, 4.5), DLEATHER, 'flat')
    return ic.done()


# ---------------------------------------------------------------------------------------------------------------------
ICONS = {}
for _t in BLADE_TIERS:
    ICONS['%s_blades' % _t] = (lambda t=_t: blade(t, False))
    ICONS['%s_blades_broken' % _t] = (lambda t=_t: blade(t, True))
for _r in RIGS:
    ICONS[_r] = (lambda r=_r: rig(r))
ICONS.update({
    'thunder_spear': lambda: spear('thunder'),
    'cluster_spear': lambda: spear('cluster'),
    'thunder_battery': thunder_battery,
    'gas_canister': gas_canister,
    'flare_gun': lambda: flare_gun(None),
    'field_cannon': lambda: cannon(False),
    'siege_cannon': lambda: cannon(True),
    'cannon_shell': cannon_shell,
    'demolition_charge': demolition_charge,
    'thermobaric_canister': thermobaric,
    'smoke_bomb': smoke_bomb,
    'net_launcher': net_launcher,
    'blade_pack': blade_pack,
    'med_kit': med_kit,
    'whetstone': whetstone,
    'adrenaline': lambda: syringe(rgb('c6e83a')),
    'colossal_serum': serum,
    'squad_horn': lambda: horn(False),
    'rumbling_horn': lambda: horn(True),
    'horse_whistle': whistle,
    'field_manual': manual,
    'merit_badge': merit_badge,
    'ultrahard_steel_ingot': ingot,
    'ice_burst_shard': lambda: shard('ice'),
    'titan_heart_shard': lambda: shard('heart'),
    'paths_shard': lambda: shard('paths'),
    'beast_boulder': boulder,
    'mod_barbs': mod_barbs,
    'mod_frame': mod_frame,
    'mod_long_hooks': mod_long_hooks,
    'mod_recycler': mod_recycler,
    'mod_twin_chamber': mod_twin_chamber,
    'mod_winch': mod_winch,
    'recon_hood': lambda: hood(rgb('3a5f40'), 'hood'),
    'recon_jacket': lambda: jacket(rgb('7a4e2e'), 'recon'),
    'recon_boots': lambda: boots(rgb('4a2c1a'), 'leather'),
    'vanguard_helmet': lambda: hood(None, 'steel'),
    'vanguard_coat': lambda: jacket(rgb('2e4a33'), 'coat'),
    'vanguard_boots': lambda: boots(rgb('262220'), 'steel'),
    'hardened_helm': lambda: hood(None, 'crystal'),
    'hardened_plate': lambda: jacket(None, 'crystal'),
    'hardened_boots': lambda: boots(None, 'crystal'),
    'scout_cloak': cloak,
    'scout_barding': barding,
})
for _c, _h in FLARE_COLOURS.items():
    ICONS['flare_%s' % _c] = (lambda h=_h: flare_shell(rgb(h)))
    ICONS['flare_gun_%s' % _c] = (lambda h=_h: flare_gun(rgb(h)))


def main():
    words = sys.argv[1:]
    for name, make in ICONS.items():
        if words and not any(w in name for w in words):
            continue
        save(make(), name)


if __name__ == '__main__':
    main()
