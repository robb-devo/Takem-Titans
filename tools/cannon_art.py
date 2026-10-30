"""
Takem Titans - the Garrison's cannons: a bronze field gun, a heavy iron gun and the three-barrelled battery.

Painted face by face on CannonRenderer's boxes (128x128 layout, painted at 256x256): an oak carriage with iron straps
and bolts, spoked wheels with iron tyres, and barrels shaded round with reinforcing rings and a dark bore.

Run from the repo root:  python tools/cannon_art.py
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gear_art import rgb, smooth, vnoise, paint_box, save  # noqa: E402

K = 2
OAK, DOAK = rgb('8a5a32'), rgb('5a3820')
IRON, DIRON = rgb('4a4e54'), rgb('26282c')
BOLT = rgb('8e949b')


def wood(f, seed, along='z'):
    g = f.x if along == 'x' else f.y
    grain = np.sin(g * 1.7 + vnoise(f.x * 2, f.y * 2, 1.0, seed) * 4.0) * 0.5 + 0.5
    n = vnoise(f.x * 6, f.y * 6, 1.0, seed + 1)
    f.c = OAK[None, None, :] * (0.82 + 0.18 * grain[..., None] + 0.08 * (n[..., None] - 0.5))


def round_shade(f, across, k=0.4):
    """Light a face as part of a cylinder: `across` runs 0..1 over the face, bright a third of the way in."""
    t = np.clip(across, 0, 1)
    shade = 0.62 + 0.55 * np.sin(t * math.pi) ** 0.7 + 0.25 * np.exp(-((t - 0.32) / 0.08) ** 2)
    f.c = f.c * (1 + (shade[..., None] - 1) * k / 0.4)


def carriage(f):
    # the trail: planks running front to back, two iron straps across and bolts along them
    along = 'x' if f.name in ('front', 'back') else 'z'
    wood(f, 3, along='y' if f.name in ('left', 'right') else along)
    if f.name in ('top', 'bottom'):
        for yy in (f.h * 0.25, f.h * 0.7):
            f.rect(0, yy - 0.5, f.w, yy + 0.5, IRON)
            for xx in np.linspace(0.8, f.w - 0.8, 4):
                f.put(np.hypot(f.x - xx, f.y - yy) < 0.28, BOLT)
        f.rect(0, 0, f.w, 0.4, DOAK)
    elif f.name in ('left', 'right'):
        for xx in (f.w * 0.25, f.w * 0.7):
            f.rect(xx - 0.5, 0, xx + 0.5, f.h, IRON)
    f.shade(top=0.06, bottom=-0.12, edge=0.16)


def cheek(f):
    wood(f, 7, along='z')
    if f.name in ('left', 'right'):
        for xx, yy in ((1.5, 1.2), (f.w - 1.5, 1.2), (1.5, f.h - 1.2), (f.w - 1.5, f.h - 1.2)):
            f.put(np.hypot(f.x - xx, f.y - yy) < 0.32, BOLT)
        # the trunnion cap where the barrel rests
        f.put(np.hypot(f.x - f.w * 0.45, f.y - 1.0) < 1.1, IRON)
        f.put(np.hypot(f.x - f.w * 0.45, f.y - 1.0) < 0.45, BOLT)
    f.shade(top=0.08, bottom=-0.12, edge=0.16)


def wheel(f):
    if f.name in ('left', 'right'):
        cx, cy = f.w / 2, f.h / 2
        r = np.hypot(f.x - cx, f.y - cy)
        a = np.arctan2(f.y - cy, f.x - cx)
        f.c = np.zeros_like(f.c)
        f.a = (r <= 4.5).astype(float)
        wood_col = OAK[None, None, :] * (0.85 + 0.15 * vnoise(f.x * 4, f.y * 4, 1.0, 9)[..., None])
        spokes = np.abs(((a / (2 * math.pi) * 8) % 1.0) - 0.5) > 0.36
        f.c = np.where(((r > 3.0) & (r <= 3.9))[..., None], wood_col * 0.9, f.c)
        f.c = np.where(((r > 3.9) & (r <= 4.5))[..., None], IRON * (0.9 + 0.2 * (np.cos(a + 2.2) * 0.5 + 0.5))[..., None], f.c)
        f.c = np.where(((r <= 3.0) & spokes)[..., None], wood_col, f.c)
        f.a = np.where((r <= 3.0) & ~spokes & (r > 1.2), 0.0, f.a)
        f.c = np.where((r <= 1.2)[..., None], IRON * 1.1, f.c)
        f.c = np.where((r <= 0.5)[..., None], BOLT, f.c)
        for k in range(8):
            ang = k * math.pi / 4 + math.pi / 8
            f.put(np.hypot(f.x - (cx + 4.2 * math.cos(ang)), f.y - (cy + 4.2 * math.sin(ang))) < 0.22, BOLT)
    else:
        # the tread: an iron tyre with rivets
        f.c = IRON[None, None, :] * (0.85 + 0.2 * vnoise(f.x * 6, f.y * 6, 1.0, 5)[..., None])
        f.put((np.mod(f.y if f.h > f.w else f.x, 2.25) < 0.35), BOLT * 0.9)
        f.shade(top=0.05, bottom=-0.1, edge=0.25)


def barrel(metal, ring_col, rings=(0.2, 0.5, 0.82), bore_r=0.32, band=None):
    """A barrel box pointing to -z: faces along it get round shading and rings; the front end is the muzzle."""
    def paint(f):
        n = vnoise(f.x * 5, f.y * 0.6, 1.0, 13)
        f.c = metal[None, None, :] * (0.9 + 0.12 * n[..., None])
        if f.name in ('front', 'back'):
            cx, cy = f.w / 2, f.h / 2
            r = np.hypot(f.x - cx, f.y - cy) / (min(f.w, f.h) / 2)
            f.c = f.c * (1.0 - 0.25 * np.clip(r, 0, 1))[..., None]
            if f.name == 'front':
                f.put(r < bore_r * 2.2, ring_col * 0.8)
                f.put(r < bore_r * 1.6, rgb('0c0c0c'))
            else:
                f.put(r < 0.45, ring_col)
            return
        # along the barrel: x (side faces) or y (top / bottom) runs its length
        along = f.x if f.name in ('left', 'right') else f.y
        across = f.y / f.h if f.name in ('left', 'right') else f.x / f.w
        L = f.w if f.name in ('left', 'right') else f.h
        # the box's front (muzzle) is at the end nearest z = min; on side faces that is x near 0 for 'right', far for 'left'
        t = along / L
        if f.name == 'left':
            t = 1 - t
        if f.name == 'bottom':
            t = 1 - t
        for rr in rings:
            m = np.abs(t - rr) < 0.035
            f.put(m, ring_col)
            f.put(np.abs(t - rr - 0.045) < 0.01, ring_col * 0.6)
        if band is not None:
            f.put(np.abs(across - 0.5) < 0.08, band)
        round_shade(f, across)
        # the bronze darkens towards the muzzle, the bore's smoke
        f.c = f.c * (1 - 0.15 * np.clip(1 - t / 0.15, 0, 1))[..., None]

    return paint


def collar(metal, rivets=True):
    def paint(f):
        f.c = metal[None, None, :] * (0.9 + 0.15 * vnoise(f.x * 6, f.y * 6, 1.0, 17)[..., None])
        if f.name in ('front', 'back'):
            cx, cy = f.w / 2, f.h / 2
            r = np.hypot(f.x - cx, f.y - cy) / (min(f.w, f.h) / 2)
            f.put(r < 0.62, rgb('0c0c0c'))
            f.put((r >= 0.62) & (r < 0.75), metal * 0.7)
        else:
            across = f.y / f.h if f.name in ('left', 'right') else f.x / f.w
            round_shade(f, across)
            if rivets:
                for xx in np.linspace(0.6, f.w - 0.6, max(2, int(f.w / 1.6))):
                    f.put(np.hypot(f.x - xx, f.y - f.h / 2) < 0.22, BOLT)

    return paint


def breech(f):
    f.c = DIRON[None, None, :] * (0.9 + 0.15 * vnoise(f.x * 5, f.y * 5, 1.0, 19)[..., None])
    f.rect(0, 0, f.w, 0.6, rgb('c9a24a'))
    f.rect(0, f.h - 0.6, f.w, f.h, rgb('c9a24a'))
    for xx in np.linspace(1.0, f.w - 1.0, max(2, int(f.w / 2.5))):
        f.put(np.hypot(f.x - xx, f.y - f.h / 2) < 0.35, BOLT)
    f.shade(top=0.08, bottom=-0.1, edge=0.18)


def common(img):
    paint_box(img, K, 'carriage', 0, 0, 10, 4, 16, carriage)
    paint_box(img, K, 'trail', 44, 20, 3, 4, 8, carriage)
    paint_box(img, K, 'cheek', 22, 20, 2, 6, 9, cheek)
    paint_box(img, K, 'wheel', 0, 20, 2, 9, 9, wheel)


def main():
    bronze, dbronze = rgb('b8863a'), rgb('7a5420')
    # tier 1: a bronze field gun with a muzzle swell
    img = np.zeros((128 * K, 128 * K, 4))
    common(img)
    paint_box(img, K, 'barrel', 0, 40, 5, 5, 22, barrel(bronze, dbronze))
    paint_box(img, K, 'muzzle', 0, 80, 6, 6, 3, collar(bronze * 1.05, rivets=False))
    save(img, 'entity/cannon/cannon_1.png')
    # tier 2: the heavy iron gun, banded, with a reinforcing breech ring
    img = np.zeros((128 * K, 128 * K, 4))
    common(img)
    paint_box(img, K, 'barrel', 0, 40, 7, 7, 28, barrel(rgb('3c3f44'), rgb('5d6168'), rings=(0.12, 0.3, 0.55, 0.78, 0.92)))
    paint_box(img, K, 'ring', 0, 80, 8, 8, 3, collar(rgb('4f5359')))
    save(img, 'entity/cannon/cannon_2.png')
    # tier 3: the battery, three blackened barrels in a brass-bound breech block
    img = np.zeros((128 * K, 128 * K, 4))
    common(img)
    # two of the three barrels are 4x4x24 boxes: paint for those, the middle one samples nearly the same
    paint_box(img, K, 'barrel', 0, 40, 4, 4, 24, barrel(rgb('2a2c30'), rgb('a8873e'), rings=(0.08, 0.92), bore_r=0.3))
    paint_box(img, K, 'breech', 0, 80, 16, 7, 6, breech)
    save(img, 'entity/cannon/cannon_3.png')


if __name__ == '__main__':
    main()
