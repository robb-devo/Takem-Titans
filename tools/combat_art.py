"""
Takem Titans - art for the combat additions: the Shifter Serum and the anti-personnel ODM pistols.

Writes the 32x32 inventory icons, the pistols' 3D model (its elements and texture sheet) and the item model files.
Run from the repo root:  python tools/combat_art.py
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
N = 32          # icon size
SS = 4          # supersamples per pixel edge


def rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255.0


class Icon:
    """A supersampled canvas painted with masks, resolved to crisp pixel art with a dark outline."""

    def __init__(self):
        k = N * SS
        ys, xs = np.mgrid[0:k, 0:k]
        self.X = (xs + 0.5) / SS
        self.Y = (ys + 0.5) / SS
        self.col = np.zeros((k, k, 3))
        self.a = np.zeros((k, k))

    def put(self, mask, color):
        c = np.asarray(color, dtype=float)
        self.col[mask] = c if c.ndim == 1 else c[mask]
        self.a[mask] = 1.0

    def resolve(self, edge):
        k = SS
        cover = self.a.reshape(N, k, N, k)
        c = (self.col * self.a[..., None]).reshape(N, k, N, k, 3).sum(axis=(1, 3))
        c = c / np.maximum(cover.sum(axis=(1, 3))[..., None], 1e-6)
        out = np.zeros((N, N, 4))
        out[..., :3] = np.clip(c, 0, 1)
        solid = cover.mean(axis=(1, 3)) > 0.4
        out[..., 3] = solid
        grown = solid.copy()
        grown[1:, :] |= solid[:-1, :]
        grown[:-1, :] |= solid[1:, :]
        grown[:, 1:] |= solid[:, :-1]
        grown[:, :-1] |= solid[:, 1:]
        ring = grown & ~solid
        out[ring, :3] = edge
        out[ring, 3] = 1.0
        return out


def save(img, name):
    path = os.path.join(TEX, name + '.png')
    Image.fromarray(np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8), 'RGBA').save(path, optimize=True)
    print('  textures/item/' + name + '.png')


def serum():
    """A glass syringe of shifter serum, glowing green-gold, a spark jumping off the needle."""
    ic = Icon()
    X, Y = ic.X, ic.Y
    d = np.array([1.0, -1.0]) / math.sqrt(2.0)        # along the syringe: needle bottom left, plunger top right
    n = np.array([1.0, 1.0]) / math.sqrt(2.0)         # across it
    u = (X - 16.0) * d[0] + (Y - 16.0) * d[1]
    v = (X - 16.0) * n[0] + (Y - 16.0) * n[1]
    lit = np.clip(0.5 - v / 7.0, 0.0, 1.0)            # light from the upper left
    shade = (0.7 + 0.5 * lit)[..., None]
    steel, steel_dk = rgb('b8c2cc'), rgb('5e6872')
    ic.put((u > -15.2) & (u < -8.4) & (np.abs(v) < 0.55), steel * shade)
    ic.put((u >= -8.4) & (u < -6.6) & (np.abs(v) < 1.7), steel_dk * shade)
    body = (u >= -6.6) & (u < 6.4) & (np.abs(v) < 3.5)
    rim = body & (np.abs(v) > 2.6)
    inner = body & ~rim
    t = np.clip((v + 2.6) / 5.2, 0.0, 1.0)
    ic.put(rim, rgb('e8f6fb') * (0.75 + 0.3 * lit)[..., None])
    ic.put(inner, rgb('cfe6ee') * (0.8 + 0.25 * (1 - t))[..., None])
    fill = inner & (u < 3.4)
    ic.put(fill, rgb('8dff3a') * (0.62 + 0.45 * (1 - t))[..., None])
    ic.put(fill & (t > 0.14) & (t < 0.32), rgb('f0ffc4'))                    # the highlight down the glass
    ic.put(inner & (np.abs(u - 3.4) < 0.45), rgb('d8ff9a'))                  # the meniscus
    for gu in (-4.5, -2.5, -0.5, 1.5):
        ic.put(rim & (np.abs(u - gu) < 0.4) & (v > 0), rgb('6d8b96'))        # graduations on the shadow side
    for bx, by, br in ((14.0, 18.5, 0.7), (11.6, 19.6, 0.5)):
        ic.put(np.hypot(X - bx, Y - by) < br, rgb('f6ffe0'))                # bubbles
    ic.put((u >= 6.4) & (u < 7.6) & (np.abs(v) < 5.0), steel_dk * shade)    # finger flange
    ic.put((u >= 7.6) & (u < 12.2) & (np.abs(v) < 0.95), steel * shade)     # plunger rod
    ic.put((u >= 12.2) & (u < 13.6) & (np.abs(v) < 3.1), steel_dk * shade)  # thumb rest
    cx, cy = 4.0, 27.6
    star = (np.abs(X - cx) + 3.2 * np.abs(Y - cy) < 2.9) | (3.2 * np.abs(X - cx) + np.abs(Y - cy) < 2.9)
    ic.put(star, rgb('ffe66a'))
    ic.put(np.hypot(X - cx, Y - cy) < 0.8, rgb('fffbe0'))
    save(ic.resolve(rgb('1c2a1a')), 'shifter_serum')


def pistols():
    """The pair, crossed: double barrels, brass bands, leather grips, the wire spool of the ODM handle at the back."""
    ic = Icon()
    steel, steel_dk, gun, dark = rgb('9aa4ae'), rgb('5f6872'), rgb('4b5361'), rgb('22262c')
    brass, leather = rgb('d2ab52'), rgb('6a4226')
    a = math.radians(-38.0)
    for far in (True, False):      # the far gun first, the near one over it
        X = (N - ic.X) if far else ic.X
        Y = ic.Y
        ox, oy = 10.0, 19.0
        s = (X - ox) * math.cos(a) + (Y - oy) * math.sin(a)
        t = -(X - ox) * math.sin(a) + (Y - oy) * math.cos(a)
        lit = np.clip(0.5 - t / 6.0, 0.0, 1.0)[..., None]
        sh = (0.72 + 0.45 * lit) * (0.8 if far else 1.0)
        ic.put(np.hypot(s + 3.0, t + 0.6) < 2.1, steel_dk * sh)                           # the wire spool
        ic.put((np.abs(s + 3.0) < 0.5) & (np.abs(t + 0.6) < 2.5), brass * sh)             # its axle
        slant = s + 1.6 + (t - 0.8) * 0.42
        wrap = np.where(((np.floor(t * 1.4) % 2) == 0)[..., None], 1.0, 0.8)
        ic.put((t > 0.8) & (t < 7.2) & (np.abs(slant) < 1.35), leather * (0.8 + 0.3 * lit) * wrap * (0.8 if far else 1.0))
        ic.put((t >= 7.2) & (t < 8.2) & (np.abs(slant) < 1.5), brass * sh)                # grip cap
        ic.put((s > -2.6) & (s < 1.0) & (t > -3.0) & (t < 1.2), gun * sh)                 # receiver
        ic.put((s > -2.2) & (s < -0.8) & (t > -4.2) & (t <= -3.0), dark * sh)             # hammer
        ring = np.hypot(s - 1.6, t - 1.4)
        ic.put((ring < 1.9) & (ring > 1.2) & (t > 1.0), dark * sh)                         # trigger guard
        ic.put((s >= 1.0) & (s < 13.0) & (t > -2.8) & (t < -0.9), steel * sh)             # top barrel
        ic.put((s >= 1.5) & (s < 13.0) & (t >= -0.9) & (t < 0.9), steel * 0.85 * sh)      # bottom barrel
        ic.put((s >= 1.0) & (s < 13.0) & (np.abs(t + 0.9) < 0.22), dark * sh)            # the seam between them
        ic.put((s >= 12.2) & (s < 13.4) & (t > -3.1) & (t < 1.2), steel_dk * sh)         # muzzle band
        ic.put((s >= 6.4) & (s < 7.3) & (t > -3.1) & (t < 1.2), brass * sh)              # brass band
    save(ic.resolve(rgb('15181c')), 'odm_pistols')


def pistol_texture():
    """The 3D gun's texture sheet: one patch per material (the UVs in odm_pistols_3d.json point at these)."""
    rng = np.random.default_rng(31)
    img = np.zeros((32, 32, 4))
    img[..., 3] = 1.0

    def noise(h, w, amt):
        return 1.0 + amt * (rng.random((h, w, 1)) - 0.5)

    steel, gun, dark = rgb('9aa4ae'), rgb('4b5361'), rgb('22262c')
    brass, leather = rgb('d2ab52'), rgb('6a4226')
    for y, k in enumerate((0.7, 1.08, 0.92, 0.6)):                 # steel: rows shaded like a round barrel
        img[y, 0:16, :3] = steel * k
    img[0:4, 0:16, :3] *= noise(4, 16, 0.08)
    img[4:8, 0:16, :3] = gun * noise(4, 16, 0.12)                   # gunmetal, a few rivets
    for x, y in ((1, 5), (14, 5), (1, 6), (14, 6), (7, 5)):
        img[y, x, :3] = steel * 1.1
    for y in range(8, 16):                                          # leather grip: a diagonal wrap
        for x in range(0, 8):
            img[y, x, :3] = leather * (0.72 if (x + y) % 4 == 0 else 1.0) * (0.92 + 0.12 * rng.random())
    for y in range(8, 12):
        img[y, 8:12, :3] = brass * (1.12 - 0.08 * (y - 8))
        img[y, 12:16, :3] = dark * (1.1 - 0.05 * (y - 8))
    img[0:4, 16:20, :3] = steel * 0.8                               # the muzzle: a ring round a black bore
    img[1:3, 17:19, :3] = rgb('050505')
    for y in range(4, 12):                                          # the spool: tight windings of steel wire
        img[y, 16:24, :3] = steel * (0.95 if y % 2 == 0 else 0.7) * noise(1, 8, 0.1)[0]
    img[0:8, 24:32, :3] = steel * 1.2                               # edge shine
    img[..., :3] = np.clip(img[..., :3], 0, 1)
    save(img, 'odm_pistol_3d')


UV = {
    'steel': [0, 0, 8, 2], 'gun': [0, 2, 8, 4], 'grip': [0, 4, 4, 8], 'brass': [4, 4, 6, 6],
    'dark': [6, 4, 8, 6], 'muzzle': [8, 0, 10, 2], 'spool': [8, 2, 12, 6], 'shine': [12, 0, 16, 4],
}
GRIP_TILT = {'angle': -22.5, 'axis': 'x', 'origin': [8, 6.6, 12.4]}
# the gun points along -Z (away from the viewer in first person), the grip hangs down at the back
PARTS = [
    ('barrel_top', [7.0, 9.2, 0.5], [9.0, 11.2, 10.5], 'steel', {'north': 'muzzle', 'up': 'shine'}, None),
    ('barrel_bottom', [7.0, 7.2, 1.0], [9.0, 9.2, 10.5], 'steel', {'north': 'muzzle'}, None),
    ('band_front', [6.8, 7.0, 1.4], [9.2, 11.4, 2.2], 'dark', {}, None),
    ('band_brass', [6.8, 7.0, 6.0], [9.2, 11.4, 6.8], 'brass', {}, None),
    ('receiver', [6.6, 6.4, 10.5], [9.4, 11.6, 14.0], 'gun', {}, None),
    ('hammer', [7.4, 11.6, 12.6], [8.6, 12.8, 14.2], 'dark', {}, None),
    ('guard', [7.6, 4.6, 8.8], [8.4, 5.4, 11.2], 'dark', {}, None),
    ('guard_front', [7.6, 5.4, 8.8], [8.4, 6.4, 9.6], 'dark', {}, None),
    ('trigger', [7.75, 5.2, 10.4], [8.25, 6.4, 10.9], 'brass', {}, None),
    ('grip', [7.1, 0.8, 11.0], [8.9, 6.6, 13.8], 'grip', {}, GRIP_TILT),
    ('grip_cap', [6.9, 0.2, 10.9], [9.1, 1.0, 13.9], 'brass', {}, GRIP_TILT),
    ('spool', [6.2, 7.0, 14.0], [9.8, 10.6, 15.8], 'spool', {}, None),
    ('axle', [5.8, 8.4, 14.5], [10.2, 9.2, 15.3], 'brass', {}, None),
]
# first person is posed in code (PistolClient), so only third person, the head slot and the like get transforms here
DISPLAY = {
    'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 3.2, 1.0], 'scale': [0.62, 0.62, 0.62]},
    'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 3.2, 1.0], 'scale': [0.62, 0.62, 0.62]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7]},
}


def models():
    faces = ('north', 'south', 'east', 'west', 'up', 'down')
    elements = []
    for name, a, b, mat, over, rot in PARTS:
        e = {'name': name, 'from': a, 'to': b, 'faces': {f: {'uv': UV[over.get(f, mat)], 'texture': '#0'} for f in faces}}
        if rot:
            e['rotation'] = rot
        elements.append(e)
    base = {
        'credit': 'Takem Titans - tools/combat_art.py',
        'textures': {'0': 'takemtitans:item/odm_pistol_3d', 'particle': 'takemtitans:item/odm_pistol_3d'},
        'elements': elements,
        'display': DISPLAY,
    }
    flat = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'takemtitans:item/odm_pistols'}}
    item = {
        'loader': 'neoforge:separate_transforms', 'gui_light': 'front',
        'base': {'parent': 'takemtitans:item/odm_pistols_3d'},
        'perspectives': {'gui': flat, 'ground': flat, 'fixed': flat},
    }
    serum_model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'takemtitans:item/shifter_serum'}}
    for name, m in (('odm_pistols_3d', base), ('odm_pistols', item), ('shifter_serum', serum_model)):
        with open(os.path.join(MODELS, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(m, f, indent=2)
            f.write('\n')
        print('  models/item/' + name + '.json')


if __name__ == '__main__':
    serum()
    pistols()
    pistol_texture()
    models()
