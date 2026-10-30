"""
Takem Titans - the people of the walls: townsfolk, Garrison soldiers, builders, the story characters and your squad.

Player-layout skins (64x64 boxes, painted at 128x128): faces with eyes, brows and mouths, hair in several cuts, and the
clothes of each trade or regiment, wearing the emblems of the gear the player wears (tools/gear_art.py).

Run from the repo root:  python tools/npc_art.py
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gear_art as G  # noqa: E402
from gear_art import rgb, smooth, vnoise, paint_box, cloth, leather, emblem, buckle, save  # noqa: E402

K = 2
STRAP, BRASS, SHIRT = G.STRAP, G.BRASS, G.SHIRT


# ---------------------------------------------------------------------------------------------------------------------
# Emblems of the other regiments
# ---------------------------------------------------------------------------------------------------------------------
def shield_mask(f, cx, cy, size):
    u = (f.x - cx) / size
    v = (f.y - cy) / size
    outer = (np.abs(u) < 0.5) & (v > -0.5) & (v < 0.55 - np.abs(u) * 0.95) & (v < 0.5 - 1.75 * u * u * (v > 0.05))
    inner = (np.abs(u) < 0.4) & (v > -0.38) & (v < 0.45 - np.abs(u) * 0.95) & (v < 0.4 - 1.75 * u * u * (v > 0.05))
    return u, v, outer, inner


def roses(f, cx, cy, size):
    """The Garrison: two roses, red and white, on a shield."""
    u, v, outer, inner = shield_mask(f, cx, cy, size)
    f.put(outer, rgb('1a1a1a'))
    f.put(inner, rgb('d8d2c0'))
    for rx, col in ((-0.17, rgb('c0392b')), (0.17, rgb('f4f1ea'))):
        r = np.hypot(u - rx, v + 0.04)
        f.put(inner & (r < 0.17), col)
        f.put(inner & (r < 0.07), col * 0.6)
    f.put(inner & (np.abs(u) < 0.04) & (v > 0.1), rgb('2f5a3a'))


def swords(f, cx, cy, size):
    """The Training Corps: two swords crossed in front of a shield."""
    u, v, outer, inner = shield_mask(f, cx, cy, size)
    f.put(outer, rgb('1a1a1a'))
    f.put(inner, rgb('c9a24a'))
    for s in (-1, 1):
        d = np.abs((u * s) - (-v) * 0.9) / math.hypot(1, 0.9)
        f.put(inner & (d < 0.06), rgb('eef2f6'))
        f.put(inner & (d < 0.06) & (v > 0.25), rgb('5a3a22'))


# ---------------------------------------------------------------------------------------------------------------------
# People
# ---------------------------------------------------------------------------------------------------------------------
SKINS = {'light': rgb('ecc9ae'), 'fair': rgb('e3b694'), 'medium': rgb('cf9f7c'), 'tan': rgb('b98361'), 'dark': rgb('8e5c40')}
HAIR = {'brown': rgb('5a3a22'), 'dark': rgb('2a1d16'), 'black': rgb('141012'), 'blond': rgb('d9b86a'), 'red': rgb('9a3a1e'),
        'auburn': rgb('7a3420'), 'grey': rgb('9a958e'), 'white': rgb('dcd8d0'), 'ash': rgb('b8a98a')}
EYES = {'brown': rgb('5a3a22'), 'blue': rgb('3a6ea8'), 'green': rgb('3f7a46'), 'grey': rgb('6a7480'), 'hazel': rgb('7a6a3a')}


class Person:
    def __init__(self, name, skin='fair', hair='brown', cut='short', eyes='brown', beard=None, female=False, age=0, brow=0.0,
                 scar=False, outfit='civil', **o):
        self.name, self.skin, self.hair, self.cut = name, SKINS[skin], HAIR[hair], cut
        self.eyes, self.beard, self.female, self.age, self.brow, self.scar = EYES[eyes], beard, female, age, brow, scar
        self.outfit = outfit
        self.o = o
        self.seed = abs(hash(name)) % 997


def skin_shade(f, p):
    n = vnoise(f.x * 5, f.y * 5, 1.0, p.seed)
    f.c = p.skin[None, None, :] * (0.95 + 0.07 * n[..., None])


def head_painter(p):
    def paint(f):
        skin_shade(f, p)
        x, y, n = f.x, f.y, f.name
        hair = p.hair
        hn = vnoise(x * 6, y * 1.5, 1.0, p.seed + 3)
        hair_col = hair[None, None, :] * (0.82 + 0.3 * hn[..., None])
        bald = p.cut == 'bald'
        if n == 'top':
            if not bald:
                f.c = hair_col
            else:
                f.c = f.c * (1.06 + 0.04 * vnoise(x * 3, y * 3, 1, 9)[..., None])
        elif n == 'bottom':
            f.c = p.skin * 0.8 * np.ones_like(f.c)
        elif n == 'front':
            # hairline / fringe
            if not bald:
                line = {'short': 1.6, 'long': 1.8, 'bun': 1.4, 'slick': 1.1, 'fringe': 2.6, 'messy': 2.2, 'bob': 2.2}.get(p.cut, 1.6)
                rag = vnoise(x * 2.5, 0 * x, 1.0, p.seed + 5) * (0.7 if p.cut in ('messy', 'fringe') else 0.25)
                f.put(y < line + rag, hair_col)
                if p.cut in ('long', 'bob', 'bun') or p.female:
                    f.put(((x < 0.9) | (x > 7.1)) & (y < 6.0 if p.cut != 'bob' else y < 7.0), hair_col)
            # brows, eyes, nose, mouth
            for cx in (2.2, 5.8):
                tilt = p.brow * (1 if cx < 4 else -1)
                by = 3.35 + tilt * (x - cx) * 0.25
                f.put((np.abs(x - cx) < 1.0) & (np.abs(y - by) < 0.28), (hair if not bald else rgb('3a2a20')) * 0.85)
                white = (np.abs(x - cx) < 0.8) & (y > 3.9) & (y < 4.9)
                f.put(white, rgb('f2efe8'))
                iris = (np.abs(x - (cx + (0.25 if cx < 4 else -0.25))) < 0.42) & (y > 3.9) & (y < 4.9)
                f.put(iris, p.eyes)
                f.put(iris & (np.abs(x - (cx + (0.25 if cx < 4 else -0.25))) < 0.18) & (y > 4.2), rgb('141012'))
                f.put((np.abs(x - cx) < 0.85) & (np.abs(y - 3.85) < 0.12), p.skin * 0.55)
                if p.female:
                    f.put((np.abs(x - (cx + (-0.85 if cx < 4 else 0.85))) < 0.18) & (np.abs(y - 4.0) < 0.22), rgb('1a1210'))
                if p.age > 0:
                    f.put((np.abs(x - cx) < 0.7) & (np.abs(y - 5.25) < 0.1), p.skin * 0.8)
            f.put((np.abs(x - 4.0) < 0.5) & (y > 4.6) & (y < 5.9), p.skin * 0.86)
            f.put((np.abs(x - 4.25) < 0.25) & (y > 5.5) & (y < 5.9), p.skin * 0.74)
            lips = rgb('b06a5c') if p.female else p.skin * 0.7
            f.put((np.abs(x - 4.0) < 1.0) & (np.abs(y - 6.55) < 0.22), lips)
            f.put((np.abs(x - 4.0) < 0.9) & (np.abs(y - 6.4) < 0.08), p.skin * 0.5)
            if p.scar:
                f.put((np.abs((x - 5.8) - (y - 4.4) * 0.25) < 0.12) & (y > 2.8) & (y < 6.2), rgb('9a5a4a'))
            if p.beard == 'full':
                f.put(((y > 5.6) & ((x < 2.2) | (x > 5.8))) | (y > 6.8), hair_col)
                f.put((np.abs(x - 4.0) < 1.2) & (y > 5.9) & (y < 6.3), hair_col)
            elif p.beard == 'goatee':
                f.put((np.abs(x - 4.0) < 1.1) & (y > 6.75), hair_col)
                f.put((np.abs(x - 4.0) < 1.2) & (y > 5.95) & (y < 6.3), hair_col)
            elif p.beard == 'moustache':
                f.put((np.abs(x - 4.0) < 1.5) & (y > 5.9) & (y < 6.35), hair_col)
            elif p.beard == 'stubble':
                f.c = np.where(((y > 5.7) & (np.mod(x * 3 + y * 7, 1.0) < 0.5))[..., None], f.c * 0.82, f.c)
        else:
            # sides and back: hair down to a length set by the cut, an ear on each side
            if not bald:
                down = {'short': 3.2, 'slick': 3.0, 'fringe': 3.6, 'messy': 3.8, 'long': 8.0, 'bob': 6.5, 'bun': 4.6}.get(p.cut, 3.2)
                if n == 'back':
                    down = {'short': 5.2, 'slick': 5.0, 'fringe': 5.2, 'messy': 5.6, 'long': 8.0, 'bob': 6.8, 'bun': 6.2}.get(p.cut, 5.2)
                rag = vnoise(x * 2.5, 0 * x, 1.0, p.seed + 7) * 0.8
                f.put(y < down + rag, hair_col)
                if n == 'back' and p.cut == 'bun':
                    f.put(np.hypot(x - 4.0, y - 3.0) < 1.6, hair_col * 1.1)
            if n in ('left', 'right'):
                ear = (np.abs(x - 4.2) < 0.6) & (y > 3.8) & (y < 5.6)
                f.put(ear, p.skin * 0.9)
                if p.beard == 'full':
                    f.put((y > 5.0) & (x < 5.5 if n == 'right' else x > 2.5), hair * 0.9)
        f.shade(top=0.04, bottom=-0.06, edge=0.06)

    return paint


def hat_painter(p):
    """The overlay round the head: hats, scarves and long hair falling over the shoulders."""
    kind = p.o.get('hat')

    def paint(f):
        f.c[...] = 0
        f.a[...] = 0.0
        x, y, n = f.x, f.y, f.name
        if kind == 'straw':
            col = rgb('d8b860') * (0.85 + 0.25 * vnoise(x * 6, y * 6, 1, 3))[..., None]
            if n == 'top':
                f.c, f.a = col, np.ones_like(x)
            elif n != 'bottom':
                band = y < 2.2
                f.c = np.where((np.abs(y - 1.8) < 0.3)[..., None], rgb('8a3a22'), col)
                f.a = band.astype(float)
        elif kind == 'scarf':
            col = p.o.get('scarf_col', rgb('a83a2a'))
            col = col * (0.85 + 0.2 * vnoise(x * 4, y * 4, 1, 5))[..., None]
            if n == 'top':
                f.c, f.a = col, np.ones_like(x)
            elif n == 'front':
                f.c, f.a = col, (y < 1.6).astype(float)
            elif n in ('left', 'right', 'back'):
                f.c, f.a = col, (y < (6.0 if n == 'back' else 4.5)).astype(float)
        elif kind == 'band':
            col = p.o.get('band_col', rgb('b0302a'))
            if n not in ('top', 'bottom'):
                m = np.abs(y - 1.7) < 0.45
                f.c, f.a = np.where(m[..., None], col, 0), m.astype(float)
        elif kind == 'cap':
            col = rgb('3a3d42')
            if n == 'top':
                f.c, f.a = col, np.ones_like(x)
            elif n == 'front':
                f.c, f.a = col * 0.8, (y < 1.4).astype(float)
            elif n != 'bottom':
                f.c, f.a = col, (y < 1.2).astype(float)
        if p.cut == 'long' and n in ('back', 'left', 'right'):
            hn = vnoise(x * 6, y * 1.5, 1.0, p.seed + 3)
            hair = p.hair[None, None, :] * (0.82 + 0.3 * hn[..., None])
            m = y > 4.0
            f.c = np.where(m[..., None], hair, f.c)
            f.a = np.maximum(f.a, m.astype(float))

    return paint


def arm_painter(p, sleeve, cuff=None, rolled=False, emblem_fn=None, side='right', jacket=False):
    def paint(f):
        x, y, n = f.x, f.y, f.name
        cloth(f, sleeve, p.seed + 11, folds=0.04)
        if jacket:
            leather(f, sleeve, p.seed + 11)
        end = 6.0 if rolled else 10.6
        if rolled:
            f.put((y > 6.0) & (y < 6.8), sleeve * 0.85)
        if cuff is not None and not rolled:
            f.put((y > 9.6) & (y < end), cuff)
        hand = y > end if not rolled else y > 6.8
        skin = p.skin[None, None, :] * (0.95 + 0.06 * vnoise(x * 5, y * 5, 1, p.seed)[..., None])
        f.c = np.where(hand[..., None], skin, f.c)
        if n == 'bottom':
            f.c = p.skin * 0.85 * np.ones_like(f.c)
        if emblem_fn is not None and n == side:
            emblem_fn(f, 2.0, 2.9, 2.6)
        f.shade(top=0.05, bottom=-0.06, edge=0.08)

    return paint


def leg_painter(p, trousers, boots=rgb('3e2617'), boot_top=7.0, skirt=None, straps=False):
    def paint(f):
        x, y, n = f.x, f.y, f.name
        if skirt is not None:
            cloth(f, skirt, p.seed + 21, folds=0.1)
            f.put(y > 10.5, rgb('3a2a20'))
        else:
            cloth(f, trousers, p.seed + 21, folds=0.05)
            if straps and n in ('front', 'back', 'right', 'left'):
                for yy in (1.4, 4.2):
                    f.put((y > yy) & (y < yy + 0.7), STRAP)
                if n == 'right':
                    buckle(f, 2.0, 1.75, 0.4)
            b = (y > boot_top)
            leather(f, boots, p.seed + 23) if False else None
            bc = boots[None, None, :] * (0.88 + 0.16 * vnoise(x * 6, y * 6, 1, p.seed + 23)[..., None])
            f.c = np.where(b[..., None], bc, f.c)
            f.put((y > boot_top) & (y < boot_top + 0.6), boots * 1.3)
            f.put(y > 11.4, rgb('1a1310'))
        if n == 'bottom':
            f.c = rgb('1a1310') * np.ones_like(f.c)
        f.shade(top=0.04, bottom=-0.06, edge=0.08)

    return paint


def torso_painter(p, kind, **o):
    def paint(f):
        x, y, n = f.x, f.y, f.name
        if kind == 'shirt_vest':
            cloth(f, o['shirt'], p.seed + 31)
            if n in ('front', 'back'):
                vest = (x < 2.6) | (x > 5.4) if n == 'front' else np.ones_like(x, dtype=bool)
                vest = vest & (y < 8.4)
                f.put(vest, o['vest'] * (0.9 + 0.12 * vnoise(x * 5, y * 5, 1, p.seed)[..., None]))
                if n == 'front':
                    for yy in (2.0, 4.0, 6.0):
                        f.put(np.hypot(x - 2.3, y - yy) < 0.25, BRASS)
            elif n in ('left', 'right'):
                f.put(y < 8.4, o['vest'] * 0.9)
            f.put((y > 8.4) & (y < 9.4), rgb('2e1d12'))
            if n == 'front':
                buckle(f, 4.0, 8.9, 0.45)
        elif kind == 'dress':
            cloth(f, o['dress'], p.seed + 31, folds=0.06)
            if n == 'front':
                f.put((np.abs(x - 4.0) < 1.6) & (y < 2.0), p.skin)
                f.put((np.abs(x - 4.0) < 1.6) & (y > 1.6) & (y < 2.2), o['dress'] * 0.7)
                if o.get('apron') is not None:
                    f.put((x > 1.4) & (x < 6.6) & (y > 6.2), o['apron'])
                    f.put((y > 6.0) & (y < 6.6), o['apron'] * 0.85)
            if o.get('shawl') is not None:
                f.put(y < (3.6 if n != 'back' else 5.0), o['shawl'] * (0.9 + 0.15 * vnoise(x * 4, y * 4, 1, 3)[..., None]))
            f.put((y > 6.2) & (y < 6.8) & (n != 'front' or o.get('apron') is None), o['dress'] * 0.7)
        elif kind == 'work':
            cloth(f, o['shirt'], p.seed + 31)
            if n in ('front', 'back'):
                for xx in (2.0, 6.0):
                    f.put((np.abs(x - xx) < 0.4), o['braces'])
                f.put((y > 8.8) & (y < 10.0), rgb('3e2617'))
                if n == 'front':
                    for xx in (1.4, 3.0, 5.4):
                        f.put((np.abs(x - xx) < 0.4) & (y > 9.6) & (y < 11.6), rgb('6b7076'))
                    f.put((np.abs(x - 6.6) < 0.5) & (y > 9.4) & (y < 12), rgb('8a5a32'))
            elif n in ('left', 'right'):
                f.put((y > 8.8) & (y < 10.0), rgb('3e2617'))
        elif kind == 'apron':
            cloth(f, o['shirt'], p.seed + 31)
            if n == 'front':
                f.put((x > 1.0) & (x < 7.0) & (y > 2.5), o['apron'])
                f.put((np.abs(x - 1.3) < 0.25) & (y < 2.6) | (np.abs(x - 6.7) < 0.25) & (y < 2.6), o['apron'] * 0.8)
                f.put((x > 2.5) & (x < 5.5) & (y > 6.0) & (y < 8.4), o['apron'] * 0.85)
            f.put((y > 8.0) & (y < 8.8), o['apron'] * 0.75)
        elif kind == 'uniform':
            leather(f, o['jacket'], p.seed + 31)
            emb = o.get('emblem')
            if n == 'front':
                f.rect(3.3, 1.4, 4.7, 12, SHIRT)
                f.band(2.0, 0.0, 3.3, 2.6, 0.55, o['jacket'] * 0.62)
                f.band(6.0, 0.0, 4.7, 2.6, 0.55, o['jacket'] * 0.62)
                if o.get('bolo'):
                    f.put(np.hypot(x - 4.0, y - 1.9) < 0.45, rgb('2a8a5a'))
                    f.put((np.abs(x - 4.0) < 0.1) & (y > 2.2) & (y < 4.2), rgb('1a1a1a'))
                if emb is not None:
                    emb(f, 6.15, 4.2, 1.45)
                for xx in (1.9, 6.1):
                    f.rect(xx - 0.35, 0, xx + 0.35, 12, STRAP)
                f.rect(0, 6.0, 8, 6.6, STRAP)
                buckle(f, 1.9, 6.3, 0.42)
                buckle(f, 6.1, 6.3, 0.42)
            elif n == 'back':
                for xx in (0.85, 7.15):
                    f.rect(xx - 0.35, 0, xx + 0.35, 12, STRAP)
                f.rect(0, 8.3, 8, 8.8, STRAP)
                if emb is not None:
                    emb(f, 4.0, 4.0, 5.0)
            elif n in ('left', 'right'):
                f.rect(1.6, 0, 2.4, 12, o['jacket'] * 0.8)
            if n in ('front', 'back', 'right', 'left'):
                f.rect(0, 10.4, f.w, 11.5, STRAP)
                if n == 'front':
                    buckle(f, 4.0, 10.95, 0.55)
        f.shade()

    return paint


def build(p):
    img = np.zeros((64 * K, 64 * K, 4))
    o = p.o
    out = p.outfit
    emb = o.get('emblem')
    if out == 'uniform':
        body = torso_painter(p, 'uniform', jacket=o['jacket'], emblem=emb, bolo=o.get('bolo'))
        rarm = arm_painter(p, o['jacket'], cuff=o['jacket'] * 0.62, jacket=True)
        larm = arm_painter(p, o['jacket'], cuff=o['jacket'] * 0.62, jacket=True, emblem_fn=emb, side='left')
        legs = leg_painter(p, SHIRT, boots=rgb('3e2617'), boot_top=6.0, straps=True)
    elif out == 'dress':
        body = torso_painter(p, 'dress', dress=o['dress'], apron=o.get('apron'), shawl=o.get('shawl'))
        rarm = arm_painter(p, o.get('shawl', o['dress']) if o.get('shawl') is not None else o['dress'], cuff=None)
        larm = rarm
        legs = leg_painter(p, None, skirt=o['dress'])
    elif out == 'work':
        body = torso_painter(p, 'work', shirt=o['shirt'], braces=o.get('braces', rgb('3e2617')))
        rarm = arm_painter(p, o['shirt'], rolled=True)
        larm = rarm
        legs = leg_painter(p, o.get('trousers', rgb('5a4a3a')), boots=rgb('3e2617'))
    elif out == 'apron':
        body = torso_painter(p, 'apron', shirt=o['shirt'], apron=o['apron'])
        rarm = arm_painter(p, o['shirt'], rolled=True)
        larm = rarm
        legs = leg_painter(p, o.get('trousers', rgb('4a3a2e')))
    else:
        body = torso_painter(p, 'shirt_vest', shirt=o.get('shirt', rgb('e6e0d0')), vest=o.get('vest', rgb('6a4a2e')))
        rarm = arm_painter(p, o.get('shirt', rgb('e6e0d0')), cuff=o.get('shirt', rgb('e6e0d0')) * 0.9)
        larm = rarm
        legs = leg_painter(p, o.get('trousers', rgb('4a3a2e')))
    paint_box(img, K, 'head', 0, 0, 8, 8, 8, head_painter(p))
    paint_box(img, K, 'hat', 32, 0, 8, 8, 8, hat_painter(p))
    paint_box(img, K, 'body', 16, 16, 8, 12, 4, body)
    paint_box(img, K, 'rarm', 40, 16, 4, 12, 4, rarm)
    paint_box(img, K, 'larm', 32, 48, 4, 12, 4, larm)
    paint_box(img, K, 'rleg', 0, 16, 4, 12, 4, legs)
    paint_box(img, K, 'lleg', 16, 48, 4, 12, 4, legs)
    save(img, 'entity/npc/%s.png' % p.name)


TAN, SCOUT_TAN = rgb('8f6239'), rgb('7a4e2e')

PEOPLE = [
    Person('civilian_0', skin='fair', hair='brown', cut='short', eyes='brown', outfit='vest', hat='straw',
           shirt=rgb('e6dcc4'), vest=rgb('6a4a2e'), trousers=rgb('4a3a2e')),
    Person('civilian_1', skin='light', hair='red', cut='long', eyes='green', female=True, outfit='dress',
           dress=rgb('9a2e28'), apron=rgb('ece6d8')),
    Person('civilian_2', skin='medium', hair='black', cut='short', eyes='brown', beard='full', outfit='work',
           shirt=rgb('ece8de'), braces=rgb('3e2617'), trousers=rgb('3a4a6a')),
    Person('civilian_3', skin='light', hair='blond', cut='bun', eyes='blue', female=True, outfit='dress',
           dress=rgb('4f7a4a'), shawl=rgb('e8e2d2')),
    Person('civilian_4', skin='fair', hair='grey', cut='bun', eyes='grey', female=True, age=1, outfit='dress',
           dress=rgb('5a4a78'), shawl=rgb('7a6a8a'), hat='scarf', scarf_col=rgb('6a5a7a')),
    Person('civilian_5', skin='fair', hair='white', cut='short', eyes='grey', beard='full', age=1, outfit='vest',
           shirt=rgb('e8e4d8'), vest=rgb('6a6a6a'), trousers=rgb('4a4440')),
    Person('soldier_0', skin='fair', hair='brown', cut='short', eyes='hazel', outfit='uniform', jacket=TAN, emblem=roses),
    Person('soldier_1', skin='tan', hair='black', cut='messy', eyes='brown', outfit='uniform', jacket=TAN, emblem=roses),
    Person('soldier_2', skin='light', hair='blond', cut='fringe', eyes='blue', outfit='uniform', jacket=TAN, emblem=roses),
    Person('builder_0', skin='medium', hair='brown', cut='messy', eyes='brown', outfit='work', hat='band',
           band_col=rgb('b0302a'), shirt=rgb('c9b896'), braces=rgb('5a3a22'), trousers=rgb('4a3a2e')),
    Person('builder_1', skin='tan', hair='dark', cut='short', eyes='brown', beard='full', outfit='work', hat='band',
           band_col=rgb('d8a030'), shirt=rgb('6a7a5a'), braces=rgb('3e2617'), trousers=rgb('3a3430')),
    Person('builder_2', skin='fair', hair='grey', cut='short', eyes='grey', beard='stubble', age=1, outfit='work', hat='band',
           band_col=rgb('3a6ea8'), shirt=rgb('a89a7a'), braces=rgb('3e2617'), trousers=rgb('4a3a2e')),
    Person('commander', skin='fair', hair='blond', cut='slick', eyes='blue', brow=0.25, outfit='uniform', jacket=SCOUT_TAN,
           emblem=emblem, bolo=True),
    Person('quartermaster', skin='fair', hair='brown', cut='bald', eyes='brown', beard='moustache', age=1, outfit='apron',
           shirt=rgb('e6e0d0'), apron=rgb('6a4a2e'), trousers=rgb('3a3430')),
    Person('mira', skin='light', hair='auburn', cut='bob', eyes='green', female=True, outfit='uniform', jacket=SCOUT_TAN,
           emblem=emblem),
    Person('hale', skin='medium', hair='dark', cut='short', eyes='brown', beard='moustache', brow=0.2, scar=True,
           outfit='uniform', jacket=TAN, emblem=roses),
    Person('instructor', skin='fair', hair='brown', cut='bald', eyes='grey', beard='goatee', brow=0.45, age=1, scar=True,
           outfit='uniform', jacket=rgb('6a5a48'), emblem=swords),
    Person('jaeger_0', skin='fair', hair='brown', cut='messy', eyes='green', brow=0.15, outfit='uniform', jacket=SCOUT_TAN, emblem=emblem),
    Person('jaeger_1', skin='light', hair='black', cut='fringe', eyes='grey', outfit='uniform', jacket=SCOUT_TAN, emblem=emblem),
    Person('jaeger_2', skin='light', hair='blond', cut='bob', eyes='blue', female=True, outfit='uniform', jacket=SCOUT_TAN, emblem=emblem),
    Person('jaeger_3', skin='tan', hair='red', cut='long', eyes='hazel', female=True, outfit='uniform', jacket=SCOUT_TAN, emblem=emblem),
]


def main():
    words = sys.argv[1:]
    for p in PEOPLE:
        if words and not any(w in p.name for w in words):
            continue
        build(p)


if __name__ == '__main__':
    main()
