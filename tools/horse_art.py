"""
Takem Titans - the Survey Corps horse's barding, painted on the vanilla horse armor layout.

The vanilla leather horse armor marks which texels the armor model shows; inside that mask: a green saddle cloth over
the back and down both flanks with a gold border and the Wings of Freedom, a leather breast collar with a brass ring,
a girth strap, and white wraps on the lower legs. Painted at 128x128 (twice the 64x64 layout).

Run from the repo root:  python tools/horse_art.py <path to vanilla horse_armor_leather.png>
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gear_art import rgb, vnoise  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, 'src/main/resources/assets/takemtitans/textures/entity/horse/armor/horse_armor_scout_barding.png')
K = 2
GREEN, DGREEN = rgb('2f5a3a'), rgb('1f3d27')
GOLD, LEATHER, BRASS = rgb('c9a24a'), rgb('5a3820'), rgb('d8b45a')
WRAP = rgb('e6e0d0')


def wings(u, v):
    """Shield mask, inner mask and colours of the emblem at (u, v) in -0.5..0.5 shield space."""
    shield = (np.abs(u) < 0.5) & (v > -0.5) & (v < 0.55 - np.abs(u) * 0.95) & (v < 0.5 - 1.75 * u * u * (v > 0.05))
    inner = (np.abs(u) < 0.42) & (v > -0.42) & (v < 0.47 - np.abs(u) * 0.95) & (v < 0.43 - 1.75 * u * u * (v > 0.05))
    col = np.zeros(u.shape + (3,))
    col[...] = rgb('1a1a1a')
    col[inner] = rgb('d8d4c8')
    for side, c, dark in ((-1, rgb('f4f2ec'), rgb('8f8b82')), (1, rgb('2f6fd8'), rgb('183f8a'))):
        root = (-side * 0.02, 0.36)
        for ang, L, w in ((78, 0.8, 0.13), (60, 0.82, 0.13), (42, 0.74, 0.12), (24, 0.6, 0.11), (8, 0.46, 0.1)):
            a = math.radians(ang)
            dx, dy = side * math.cos(a), -math.sin(a)
            px, py = u - root[0], v - root[1]
            t = px * dx + py * dy
            d = px * (-dy) + py * dx
            taper = w * np.clip(1 - (t / L) ** 2, 0, 1) ** 0.5
            f = inner & (t > 0) & (t < L) & (np.abs(d) < taper)
            col[f] = c
            col[f & (np.abs(d) > taper - 0.03)] = dark
    return shield, col


def main(template):
    mask = np.array(Image.open(template).convert('RGBA').resize((64 * K, 64 * K), Image.NEAREST))[..., 3] > 0
    H = W = 64 * K
    ys, xs = np.mgrid[0:H, 0:W]
    U = (xs + 0.5) / K          # texel coordinates in the 64x64 layout
    V = (ys + 0.5) / K
    img = np.zeros((H, W, 4))
    n = vnoise(U * 4, V * 4, 1.0, 7)

    def put(m, col, a=1.0):
        m = m & mask
        img[m, :3] = col if np.ndim(col) == 1 else col[m]
        img[m, 3] = a

    cloth = GREEN[None, None, :] * (0.88 + 0.16 * n[..., None])
    # the body box: texOffs(0, 32), 10 x 10 x 22
    top = (U >= 22) & (U < 32) & (V >= 32) & (V < 54)
    sides = ((U >= 0) & (U < 22) | (U >= 32) & (U < 54)) & (V >= 54) & (V < 64)
    front = (U >= 22) & (U < 32) & (V >= 54) & (V < 64)
    belly = (U >= 32) & (U < 42) & (V >= 32) & (V < 54)
    # saddle cloth: the whole back, and down each flank to a hem with a gold border
    put(top, cloth)
    put(top & ((U < 22.8) | (U > 31.2)), GOLD)
    su = np.where(U < 22, U, U - 32)            # 0..22 along each flank
    cloth_side = sides & (su > 3.5) & (su < 18.5) & (V < 62.0)
    put(cloth_side, cloth)
    border = cloth_side & ((su < 4.3) | (su > 17.7) | (V > 61.2))
    put(border, GOLD)
    put(sides & (su > 3.5) & (su < 18.5) & (V >= 62.0) & (V < 62.5) & (np.mod(su, 1.0) < 0.5), GOLD)
    for cu in (11.0,):
        u = (su - cu) / 7.2
        v = (V - 58.2) / 7.2
        sh, col = wings(u, v)
        put(cloth_side & sh, col)
    # the breast collar across the chest, with a brass ring in the middle
    put(front & (np.abs(V - 57.5) < 0.9), LEATHER * (0.9 + 0.2 * n[..., None]))
    put(front & (np.hypot(U - 27.0, V - 57.5) < 1.2), BRASS)
    put(front & (np.hypot(U - 27.0, V - 57.5) < 0.6), LEATHER)
    # the girth under the belly
    put(belly & (np.abs(V - 41.0) < 1.1), LEATHER * (0.9 + 0.2 * n[..., None]))
    put(belly & (np.abs(V - 41.0) < 1.1) & (np.abs(U - 37.0) < 0.6), BRASS)
    # leg wraps: the lower part of every leg (legs: texOffs(48, 21), 4 x 11 x 4)
    legs = (U >= 48) & (U < 64) & (V >= 25) & (V < 36)
    lower = legs & (V > 29.5)
    wrap = WRAP[None, None, :] * (0.9 + 0.12 * vnoise(U * 6, V * 6, 1.0, 9)[..., None])
    put(lower, wrap)
    put(lower & (np.mod(V * 1.3 + U * 0.5, 1.6) < 0.25), WRAP * 0.78)
    put(legs & (V > 29.0) & (V <= 29.5), LEATHER)
    a = np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(a, 'RGBA').save(OUT, optimize=True)
    print('  horse_armor_scout_barding.png', W, 'x', H)


if __name__ == '__main__':
    main(sys.argv[1])
