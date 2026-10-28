"""
Takem Titans - titan model and skin generator.

One description of the titan body drives everything:
  * the box model (written into TitanModel.java between the GENERATED markers),
  * the UV atlas those boxes use,
  * every titan skin, wound overlay and glow mask, painted in 3D body space so seams line up.

Run from the repo root:  python tools/titan_art.py
Options: --only pure_titan_0 (paint one skin), --density N (texels per model unit, default 4), --preview (atlas sheet)
"""
import math
import os
import re
import sys

import numpy as np
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(REPO, 'src/main/resources/assets/takemtitans/textures/entity/titan')
MODEL_JAVA = os.path.join(REPO, 'src/main/java/com/takem/titans/client/render/TitanModel.java')

# --------------------------------------------------------------------------------------------------------------------
# Skeleton: must match TitanSkeleton.PIVOT / PARENT (joints 0..14). Extra parts hang off a joint at a fixed offset.
# --------------------------------------------------------------------------------------------------------------------
JOINTS = {
    'root': (None, (0, 24, 0)),
    'hips': ('root', (0, -20, 0)),
    'torso': ('hips', (0, 0, 0)),
    'head': ('torso', (0, -26, 0)),
    'jaw': ('head', (0, -6, 3)),
    'right_arm': ('torso', (-13, -23, 0)),
    'right_forearm': ('right_arm', (0, 14, 0)),
    'right_hand': ('right_forearm', (0, 15, 0)),
    'left_arm': ('torso', (13, -23, 0)),
    'left_forearm': ('left_arm', (0, 14, 0)),
    'left_hand': ('left_forearm', (0, 15, 0)),
    'right_leg': ('hips', (-5, 0, 0)),
    'right_shin': ('right_leg', (0, 10, 0)),
    'left_leg': ('hips', (5, 0, 0)),
    'left_shin': ('left_leg', (0, 10, 0)),
    # static children (PartPose given here; pivot is the offset from the parent)
    'pelvis': ('hips', (0, 0, 0)),
    'belly': ('torso', (0, 0, 0)),
    'hair': ('head', (0, 0, 0)),
    'right_stump': ('right_arm', (0, 0, 0)),
    'left_stump': ('left_arm', (0, 0, 0)),
    'right_fingers': ('right_hand', (0, 5, 0)),
    'left_fingers': ('left_hand', (0, 5, 0)),
}

STYLES = ['armored', 'beast', 'jaw', 'cart', 'spiked', 'longhair', 'messy']


class Cube:
    def __init__(self, part, tag, x, y, z, w, h, d, inflate=0.0, style=None, mat=None):
        self.part, self.tag = part, tag
        self.x, self.y, self.z = x, y, z
        self.w, self.h, self.d = w, h, d
        self.inflate = inflate
        self.style = style      # only shown for this style (an extra)
        self.mat = mat          # material hint for the painter
        self.mirror_of = None   # left-side copy: shares the UV of this right-side cube
        self.u = self.v = 0

    def footprint(self):
        return 2 * (self.w + self.d), self.d + self.h


def C(part, tag, x, y, z, w, h, d, **kw):
    return Cube(part, tag, x, y, z, w, h, d, **kw)


# Right-side limbs are written once and mirrored onto the left (same UV, mirrored box), like the vanilla player model.
BODY = [
    # torso: a narrower waist under a ribcage, chest muscles, shoulder line, and the neck with the nape behind
    C('torso', 'abdomen', -8, -11, -5, 16, 11, 10),
    C('torso', 'chest', -10, -25, -6, 20, 14, 12),
    C('torso', 'pecs', -9, -24, -7, 18, 7, 1),
    C('torso', 'traps', -11.5, -27, -5, 23, 3, 10),
    C('torso', 'neck', -4.5, -32, -2, 9, 6, 9),
    C('belly', 'belly', -8, -12, -8, 16, 10, 3),
    C('pelvis', 'pelvis', -8.5, -2, -5, 17, 5, 10),
    # head
    C('head', 'skull', -9, -18, -9, 18, 12, 18),
    C('head', 'nose', -1.5, -11.8, -11, 3, 4, 2),
    C('head', 'ear', -10.5, -13, -1, 2, 5, 3),
    C('head', 'teeth_top', -6.5, -6, -9, 13, 1, 1),
    C('jaw', 'jaw', -8, 0, -12, 16, 6, 15),
    C('jaw', 'chin', -4, 3.5, -13, 8, 2, 1),
    C('jaw', 'teeth_bottom', -6.5, -1, -11.6, 13, 1, 1),
    C('hair', 'hair', -9, -18, -9, 18, 12, 18, inflate=0.6),
    # arms
    C('right_arm', 'delt', -3.5, -3, -3.5, 7, 6, 7),
    C('right_arm', 'upper_arm', -3, 0, -3, 6, 14, 6),
    C('right_arm', 'bicep', -2.5, 3, -4, 5, 7, 1),
    C('right_stump', 'stump', -3, 13, -3, 6, 2, 6, inflate=0.15),
    C('right_forearm', 'forearm', -3, 0, -3, 6, 15, 6),
    C('right_forearm', 'forearm_mass', -3, 1, -3, 6, 6, 6, inflate=0.4),
    C('right_hand', 'palm', -3.5, 0, -2, 7, 5, 4),
    C('right_hand', 'thumb', 2.0, 0.5, -3.5, 2, 5, 2),
    C('right_fingers', 'finger', -3.5, 0, -1.25, 1, 5, 2),
    C('right_fingers', 'finger', -1.7, 0, -1.25, 1, 6, 2),
    C('right_fingers', 'finger', 0.1, 0, -1.25, 1, 6, 2),
    C('right_fingers', 'finger', 1.9, 0, -1.25, 1, 5, 2),
    # legs
    C('right_leg', 'thigh', -4.5, -1, -4.5, 9, 11, 9),
    C('right_leg', 'quad', -3.5, 1, -5.5, 7, 7, 1),
    C('right_shin', 'shin', -3.5, 0, -3.5, 7, 10, 7),
    C('right_shin', 'knee', -2.5, -1.5, -4.5, 5, 3, 1),
    C('right_shin', 'calf', -3, 1, 3.5, 6, 5, 1),
    C('right_shin', 'foot', -3.5, 7.5, -7.5, 7, 2.5, 11) if False else C('right_shin', 'foot', -3.5, 7, -7.5, 7, 3, 11),
    C('right_shin', 'toe', 1.4, 8.4, -9, 2, 2, 2),
    C('right_shin', 'toe', -0.4, 8.8, -8.6, 1, 1, 1),
    C('right_shin', 'toe', -1.9, 8.9, -8.5, 1, 1, 1),
    C('right_shin', 'toe', -3.3, 9.0, -8.4, 1, 1, 1),
]

EXTRAS = [
    # Armored: plates of hardened skin over chest, back, shoulders, forearms, thighs, shins and the crown of the head
    C('torso', 'plate_chest', -10.5, -25.5, -7.6, 21, 9, 2, style='armored', mat='plate'),
    C('torso', 'plate_abs', -7, -15.5, -6.6, 14, 10, 2, style='armored', mat='plate'),
    C('torso', 'plate_back', -10, -26, 5.5, 20, 13, 2, style='armored', mat='plate'),
    C('head', 'plate_crown', -9.6, -18.8, -9.6, 19, 5, 19, style='armored', mat='plate'),
    C('head', 'plate_cheek', -9.8, -13, -9.8, 4, 6, 6, style='armored', mat='plate'),
    C('right_arm', 'plate_pauldron', -4.5, -4.2, -4.5, 9, 6, 9, style='armored', mat='plate'),
    C('right_forearm', 'plate_bracer', -3.6, 2, -3.6, 7, 11, 7, style='armored', mat='plate'),
    C('right_leg', 'plate_thigh', -4.8, 0, -5.7, 10, 8, 2, style='armored', mat='plate'),
    C('right_shin', 'plate_greave', -4, -1, -4.8, 8, 8, 2, style='armored', mat='plate'),
    # Beast: a shaggy coat over shoulders and chest, fur down the arms
    C('torso', 'fur_coat', -11.5, -28, -7.5, 23, 17, 15, style='beast', mat='fur'),
    C('right_arm', 'fur_arm', -4.2, -3.5, -4.2, 8, 16, 8, style='beast', mat='fur'),
    C('right_forearm', 'fur_fore', -3.8, 0, -3.8, 7, 11, 7, style='beast', mat='fur'),
    C('head', 'fur_mane', -10, -19, -1, 20, 14, 11, style='beast', mat='fur'),
    # Jaw: a bone mask over the upper face, a beak-like guard on the jaw, claws
    C('head', 'bone_mask', -8.6, -17.6, -10.4, 17, 8, 2, style='jaw', mat='bone'),
    C('jaw', 'bone_guard', -8.5, 0.5, -13.4, 17, 5, 2, style='jaw', mat='bone'),
    C('right_fingers', 'claw', -3.5, 5, -1.0, 1, 3, 1, style='jaw', mat='bone'),
    C('right_fingers', 'claw', -1.7, 6, -1.0, 1, 3, 1, style='jaw', mat='bone'),
    C('right_fingers', 'claw', 0.1, 6, -1.0, 1, 3, 1, style='jaw', mat='bone'),
    C('right_fingers', 'claw', 1.9, 5, -1.0, 1, 3, 1, style='jaw', mat='bone'),
    # Cart: a harness and armoured pack on its back, a helmet
    C('torso', 'pack', -8, -25, 6, 16, 15, 6, style='cart', mat='gear'),
    C('head', 'helmet', -9.8, -19.2, -9.8, 20, 6, 20, style='cart', mat='gear'),
    # messy hair: tufts standing up off the crown
    C('hair', 'tuft', -7, -20.2, -5, 5, 2, 5, style='messy', mat='hair'),
    C('hair', 'tuft', 0.5, -20.6, -7, 6, 2, 5, style='messy', mat='hair'),
    C('hair', 'tuft', -4, -20.4, 1.5, 6, 2, 6, style='messy', mat='hair'),
    C('hair', 'tuft', 3.5, -20.0, 2, 5, 2, 5, style='messy', mat='hair'),
    # Female / long hair: hair hanging down the back of the head
    C('head', 'hair_back', -8.5, -17, 6, 17, 16, 4, style='longhair', mat='hair'),
    # Spiked (Ravager): a ridge of bone spikes down the spine
    C('torso', 'spike', -1, -25, 6, 2, 4, 4, style='spiked', mat='bone'),
    C('torso', 'spike', -1, -19, 6, 2, 4, 4, style='spiked', mat='bone'),
    C('torso', 'spike', -1, -13, 6, 2, 4, 3, style='spiked', mat='bone'),
    C('torso', 'spike', -1, -7, 5, 2, 3, 3, style='spiked', mat='bone'),
    C('right_arm', 'spike', -1, -5, -1, 2, 3, 2, style='spiked', mat='bone'),
]

MIRRORED_PARTS = {'right_arm': 'left_arm', 'right_forearm': 'left_forearm', 'right_hand': 'left_hand',
                  'right_leg': 'left_leg', 'right_shin': 'left_shin', 'right_stump': 'left_stump',
                  'right_fingers': 'left_fingers'}


def all_cubes():
    cubes = []
    for c in BODY + EXTRAS:
        cubes.append(c)
    # the right ear also gets a mirrored twin on the head, and right limb cubes get left twins
    out = list(cubes)
    for c in cubes:
        if c.part in MIRRORED_PARTS or (c.part == 'head' and c.tag in ('ear', 'plate_cheek')):
            m = Cube(MIRRORED_PARTS.get(c.part, c.part), c.tag, -(c.x + c.w), c.y, c.z, c.w, c.h, c.d, c.inflate, c.style, c.mat)
            m.mirror_of = c
            out.append(m)
    return out


CUBES = all_cubes()
OWNERS = [c for c in CUBES if c.mirror_of is None]


# --------------------------------------------------------------------------------------------------------------------
# UV packing (box UV, one texel per model unit in the layout; the PNGs carry DENSITY texels per unit)
# --------------------------------------------------------------------------------------------------------------------
ATLAS_W = 256


def pack():
    import math as _m
    items = sorted(OWNERS, key=lambda c: (-c.footprint()[1], -c.footprint()[0]))
    x = y = 0
    shelf = 0
    for c in items:
        fw, fh = (int(_m.ceil(v)) for v in c.footprint())
        if x + fw > ATLAS_W:
            x = 0
            y += shelf + 1
            shelf = 0
        c.u, c.v = x, y
        x += fw + 1
        shelf = max(shelf, fh)
    for c in CUBES:
        if c.mirror_of is not None:
            c.u, c.v = c.mirror_of.u, c.mirror_of.v
    h = y + shelf + 1
    # entity textures need not be powers of two; keep the atlas just tall enough
    return (h + 7) // 8 * 8


ATLAS_H = pack()


# --------------------------------------------------------------------------------------------------------------------
# Java
# --------------------------------------------------------------------------------------------------------------------
def fnum(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    if s in ('-0', ''):
        s = '0'
    return s + 'F'


def java_layer():
    lines = []
    order = ['root', 'hips', 'pelvis', 'torso', 'belly', 'head', 'jaw', 'hair',
             'right_arm', 'right_stump', 'right_forearm', 'right_hand', 'right_fingers',
             'left_arm', 'left_stump', 'left_forearm', 'left_hand', 'left_fingers',
             'right_leg', 'right_shin', 'left_leg', 'left_shin']
    var = {'root': 'root'}
    lines.append('        MeshDefinition mesh = new MeshDefinition();')
    lines.append('        PartDefinition top = mesh.getRoot();')
    skeleton_idx = {'root': 0, 'hips': 1, 'torso': 2, 'head': 3, 'jaw': 4, 'right_arm': 5, 'right_forearm': 6,
                    'right_hand': 7, 'left_arm': 8, 'left_forearm': 9, 'left_hand': 10, 'right_leg': 11,
                    'right_shin': 12, 'left_leg': 13, 'left_shin': 14}
    for name in order:
        parent, off = JOINTS[name]
        body = [c for c in CUBES if c.part == name and c.style is None]
        builder = 'CubeListBuilder.create()'
        for c in body:
            builder += box_call(c)
        if name in skeleton_idx:
            pose = 'at(%d)' % skeleton_idx[name]
        elif name.endswith('fingers'):
            pose = 'PartPose.offsetAndRotation(0.0F, 5.0F, 0.0F, 0.35F, 0.0F, 0.0F)'
        else:
            pose = 'PartPose.ZERO'
        pvar = 'top' if parent is None else var[parent]
        var[name] = name.replace('right_', 'r').replace('left_', 'l').replace('_', '')
        lines.append('        PartDefinition %s = %s.addOrReplaceChild("%s", %s, %s);' % (var[name], pvar, name, builder, pose))
    # style extras: one child part per style per parent, hidden unless that style is drawn
    for style in STYLES:
        for name in order:
            cubes = [c for c in CUBES if c.part == name and c.style == style]
            if not cubes:
                continue
            builder = 'CubeListBuilder.create()' + ''.join(box_call(c) for c in cubes)
            lines.append('        %s.addOrReplaceChild("x_%s", %s, PartPose.ZERO);' % (var[name], style, builder))
    lines.append('        return LayerDefinition.create(mesh, %d, %d);' % (ATLAS_W, ATLAS_H))
    return '\n'.join(lines)


def box_call(c):
    s = '.texOffs(%d, %d)' % (c.u, c.v)
    if c.mirror_of is not None:
        s += '.mirror()'
    if c.inflate:
        s += '.addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))' % (
            fnum(c.x), fnum(c.y), fnum(c.z), fnum(c.w), fnum(c.h), fnum(c.d), fnum(c.inflate))
    else:
        s += '.addBox(%s, %s, %s, %s, %s, %s)' % (fnum(c.x), fnum(c.y), fnum(c.z), fnum(c.w), fnum(c.h), fnum(c.d))
    if c.mirror_of is not None:
        s += '.mirror(false)'
    return s


def write_java():
    src = open(MODEL_JAVA, encoding='utf-8').read()
    start = src.index('// BEGIN GENERATED (tools/titan_art.py)')
    end = src.index('// END GENERATED\n', start)
    head = src[:start]
    body = '// BEGIN GENERATED (tools/titan_art.py)\n' + java_layer() + '\n        '
    styles = ', '.join('"%s"' % s for s in STYLES)
    out = head + body + src[end:]
    out = re.sub(r'public static final String\[\] STYLES = \{[^}]*\};',
                 'public static final String[] STYLES = {%s};' % styles, out)
    table = ',\n'.join('        Map.entry("%s", "%s")' % (l.name, ','.join(styles_of(l))) for l in LOOKS if styles_of(l))
    out = re.sub(r'(// BEGIN GENERATED STYLES\n).*?(\n\s*// END GENERATED STYLES)',
                 lambda m: m.group(1) + '    private static final Map<String, String> SKIN_STYLES = Map.ofEntries(\n' + table + '\n    );' + m.group(2),
                 out, flags=re.S)
    open(MODEL_JAVA, 'w', encoding='utf-8').write(out)


# --------------------------------------------------------------------------------------------------------------------
# Geometry for painting: rest-pose positions of every texel of every face
# --------------------------------------------------------------------------------------------------------------------
def joint_origin(part):
    x = y = z = 0.0
    p = part
    while p is not None:
        parent, off = JOINTS[p]
        x += off[0]
        y += off[1]
        z += off[2]
        p = parent
    return x, y, z


def faces(c):
    """(name, (u0, v0, u1, v1), V0, V1, V2, V3) with Minecraft's box-UV vertex order; vertices in rest body space."""
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    i = c.inflate
    ox, oy, oz = joint_origin(c.part)
    x0, y0, z0 = c.x - i + ox, c.y - i + oy, c.z - i + oz
    x1, y1, z1 = c.x + w + i + ox, c.y + h + i + oy, c.z + d + i + oz
    v1 = (x0, y0, z0); v2 = (x1, y0, z0); v3 = (x1, y1, z0); v4 = (x0, y1, z0)
    v5 = (x0, y0, z1); v6 = (x1, y0, z1); v7 = (x1, y1, z1); v8 = (x0, y1, z1)
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    return [
        ('top', (f5, f10, f6, f11), v6, v5, v1, v2),        # minY: the top of the box in the world
        ('bottom', (f6, f11, f7, f10), v3, v4, v8, v7),     # maxY
        ('right', (f4, f11, f5, f12), v1, v5, v8, v4),      # minX
        ('front', (f5, f11, f6, f12), v2, v1, v4, v3),      # minZ
        ('left', (f6, f11, f8, f12), v6, v2, v3, v7),       # maxX
        ('back', (f8, f11, f9, f12), v5, v6, v7, v8),       # maxZ
    ]


NORMALS = {'top': (0, -1, 0), 'bottom': (0, 1, 0), 'right': (-1, 0, 0), 'front': (0, 0, -1),
           'left': (1, 0, 0), 'back': (0, 0, 1)}


class FaceGrid:
    """Texel centres of one face: body-space points P (n,3), face coords s,t in 0..1, edge distance in units."""

    def __init__(self, c, face, D):
        name, (u0, v0, u1, v1), A0, A1, A2, A3 = face
        self.cube, self.name, self.D = c, name, D
        x0, x1 = sorted((u0, u1))
        y0, y1 = sorted((v0, v1))
        self.px0, self.px1 = int(round(x0 * D)), int(round(x1 * D))
        self.py0, self.py1 = int(round(y0 * D)), int(round(y1 * D))
        W, H = self.px1 - self.px0, self.py1 - self.py0
        self.W, self.H = W, H
        if W <= 0 or H <= 0:
            self.n = 0
            return
        xs = (np.arange(W) + 0.5) / D + x0
        ys = (np.arange(H) + 0.5) / D + y0
        U, V = np.meshgrid(xs, ys)
        s = (U - u0) / (u1 - u0)
        t = (V - v0) / (v1 - v0)
        A0, A1, A2, A3 = (np.array(a, dtype=float) for a in (A0, A1, A2, A3))
        s3, t3 = s[..., None], t[..., None]
        self.P = (1 - t3) * ((1 - s3) * A1 + s3 * A0) + t3 * ((1 - s3) * A2 + s3 * A3)
        self.s, self.t = s, t
        ew = np.linalg.norm(A0 - A1)
        eh = np.linalg.norm(A2 - A1)
        self.edge = np.minimum(np.minimum(s * ew, (1 - s) * ew), np.minimum(t * eh, (1 - t) * eh))
        self.ew, self.eh = ew, eh
        self.N = np.array(NORMALS[name], dtype=float)
        self.n = W * H


# --------------------------------------------------------------------------------------------------------------------
# Noise
# --------------------------------------------------------------------------------------------------------------------
def _hash3(ix, iy, iz, seed):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0x1000000)


def vnoise(P, freq, seed):
    p = P * freq
    i = np.floor(p).astype(np.int64)
    f = p - i
    f = f * f * (3 - 2 * f)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * _hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
    return out


def fbm(P, freq, seed, octaves=4):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total = total + amp * vnoise(P, freq * (2 ** o), seed + o * 101)
        norm += amp
        amp *= 0.5
    return total / norm


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t)[..., None] if np.ndim(t) else t
    return a * (1 - t) + b * t


def rgb(hexstr):
    hexstr = hexstr.lstrip('#')
    return np.array([int(hexstr[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255.0


# --------------------------------------------------------------------------------------------------------------------
# Looks: one per skin file
# --------------------------------------------------------------------------------------------------------------------
class Look:
    def __init__(self, name, **kw):
        self.name = name
        self.skin = rgb(kw.get('skin', 'd9a58a'))
        self.skin2 = rgb(kw.get('skin2', 'b97f6a'))      # shadow / blotch tone
        self.hair = rgb(kw.get('hair', '3a2a20'))
        self.hairstyle = kw.get('hairstyle', 'short')    # short, bowl, messy, long, bald, slick, bun
        self.face = kw.get('face', 'grin')               # grin, blank, gape, smile, snarl, void, sad
        self.eyes = kw.get('eyes', 'wide')               # wide, sleepy, cross, void, glow, small, mad
        self.muscle = kw.get('muscle', 0.6)              # how sharply the anatomy is drawn
        self.fat = kw.get('fat', 0.0)
        self.material = kw.get('material', 'skin')       # skin, muscle, rock, fur, lava, wood, char
        self.plate = rgb(kw.get('plate', 'c9c2b4'))
        self.fur = rgb(kw.get('fur', '6b4a2e'))
        self.glow = kw.get('glow', None)                 # glow mask colour for eyes / cracks
        self.seed = kw.get('seed', abs(hash(name)) % 9973)
        self.blotch = kw.get('blotch', 0.15)
        self.lips = rgb(kw.get('lips', 'a8584f'))
        self.iris = rgb(kw.get('iris', '4a3a2a'))
        self.exposed = kw.get('exposed', 0.0)            # patches of bare muscle
        self.scars = kw.get('scars', 0.0)


LOOKS = [
    Look('pure_titan_0', skin='dcae92', skin2='b9846c', hair='e2c27a', hairstyle='bowl', face='grin', eyes='wide', seed=11),
    Look('pure_titan_1', skin='e0b09a', skin2='b8806e', hair='8a3a22', hairstyle='messy', face='smile', eyes='sleepy', seed=23),
    Look('pure_titan_2', skin='c99778', skin2='9f6f58', hair='2a1d16', hairstyle='slick', face='blank', eyes='small', fat=0.6, seed=37),
    Look('pure_titan_3', skin='eabda4', skin2='c49480', hair='111111', hairstyle='short', face='gape', eyes='wide', seed=41),
    Look('pure_titan_4', skin='b9876a', skin2='8c5f4b', hair='5a4030', hairstyle='bald', face='grin', eyes='cross', seed=53, fat=0.3),
    Look('pure_titan_5', skin='e8c0a8', skin2='c4927e', hair='c9b18a', hairstyle='long', face='sad', eyes='wide', seed=67),
    Look('abnormal_titan_0', skin='d8c0ae', skin2='a88e7e', hair='7b6650', hairstyle='messy', face='snarl', eyes='mad', seed=71, muscle=0.9),
    Look('abnormal_titan_1', skin='c7b2a4', skin2='8f7b70', hair='222222', hairstyle='bald', face='void', eyes='void', seed=79, muscle=1.0, glow='ffd7a0'),
    Look('abnormal_titan_2', skin='e2c4b2', skin2='b4927f', hair='d8d0c0', hairstyle='long', face='grin', eyes='mad', seed=83, muscle=0.8),
    Look('crawler_titan_0', skin='d5b6a4', skin2='a5877a', hair='231a14', hairstyle='long', face='gape', eyes='wide', seed=89),
    Look('crawler_titan_1', skin='c4a593', skin2='936f60', hair='3e2c22', hairstyle='messy', face='void', eyes='void', seed=97, glow='ff9a6a'),
    Look('ghoul_titan_0', skin='e9e3dc', skin2='b8aea6', hair='9a9690', hairstyle='long', face='void', eyes='void', seed=101, muscle=1.0, glow='bfe6ff'),
    Look('colossal_titan_0', material='muscle', skin='a8322a', skin2='6e1712', hair='000000', hairstyle='bald', face='colossal', eyes='glow', glow='fff0b0', seed=103, muscle=1.0),
    Look('wall_titan_0', material='rock', skin='9d968c', skin2='6d665e', hairstyle='bald', face='colossal', eyes='glow', glow='ffcf8a', seed=107, exposed=0.35),
    Look('wall_titan_1', material='rock', skin='aaa294', skin2='746c62', hairstyle='bald', face='colossal', eyes='glow', glow='ffcf8a', seed=109, exposed=0.5),
    Look('wall_titan_2', material='rock', skin='8f8c86', skin2='5e5a55', hairstyle='bald', face='colossal', eyes='glow', glow='ffcf8a', seed=113, exposed=0.25),
    Look('armored_titan_0', skin='b0a397', skin2='7c6f66', plate='d8d2c4', hair='e0c27a', hairstyle='short', face='armored', eyes='small', seed=127, muscle=1.0, exposed=0.4),
    Look('armored_titan_1', skin='9f8f86', skin2='6a5c55', plate='c3bcb0', hair='c9a95a', hairstyle='short', face='armored', eyes='glow', glow='ffd36a', seed=131, muscle=1.0, exposed=0.5),
    Look('female_titan_0', skin='e2b8a4', skin2='b78c7a', hair='f0d48a', hairstyle='bun', face='blank', eyes='small', iris='3a5d78', seed=137, muscle=0.9, exposed=0.25),
    Look('female_titan_1', skin='e9cbbf', skin2='bb9a8e', hair='f4dc98', hairstyle='bun', face='blank', eyes='glow', glow='9fd8ff', seed=139, muscle=1.0, exposed=0.4),
    Look('war_hammer_titan_0', skin='ece6dc', skin2='b9b0a3', hair='f2efe6', hairstyle='long', face='blank', eyes='glow', glow='ffe9a8', seed=149, muscle=0.9, scars=0.5),
    Look('cart_titan_0', skin='a4968c', skin2='6d6058', hair='2a2420', hairstyle='messy', face='blank', eyes='small', seed=151, fat=0.2),
    Look('cart_titan_1', skin='8e8076', skin2='5d514a', hair='1a1614', hairstyle='messy', face='snarl', eyes='glow', glow='ffb070', seed=157),
    Look('beast_titan_0', material='skin', skin='8d6e58', skin2='5a4334', fur='6b4f35', hair='5a4029', hairstyle='messy', face='snarl', eyes='small', seed=163, muscle=0.8),
    Look('jaw_titan_0', material='char', skin='3a2f2c', skin2='1d1715', plate='e6dfcf', hair='0e0c0b', hairstyle='long', face='jaw', eyes='glow', glow='ffd25a', seed=167, muscle=1.0),
    Look('ravager_titan_0', material='lava', skin='7a1c14', skin2='2a0806', hairstyle='bald', face='colossal', eyes='glow', glow='ffb43a', seed=173, muscle=1.0),
    Look('training_dummy_0', material='wood', skin='b88a55', skin2='7a5631', hair='d9c27a', hairstyle='messy', face='dummy', eyes='small', seed=179, muscle=0.0),
]

EVAPORATE = Look('evaporate', material='muscle', skin='9c2b22', skin2='4f0f0b', plate='d9cbb4', fur='5a1a14', hair='3a0c08',
                 hairstyle='bald', face='colossal', eyes='void', seed=191, muscle=1.0)

STYLE_OF = {
    'armored_titan': ['armored'], 'beast_titan': ['beast', 'messy'], 'jaw_titan': ['jaw', 'longhair'], 'cart_titan': ['cart', 'messy'],
    'female_titan': ['longhair'], 'war_hammer_titan': ['longhair'], 'ravager_titan': ['spiked'],
}


def styles_of(look):
    base = look.name.rsplit('_', 1)[0]
    out = list(STYLE_OF.get(base, []))
    if look.hairstyle == 'messy' and 'messy' not in out:
        out.append('messy')
    if look.hairstyle == 'long' and 'longhair' not in out:
        out.append('longhair')
    return out


# --------------------------------------------------------------------------------------------------------------------
# Painting
# --------------------------------------------------------------------------------------------------------------------
def paint(look, D, styles=None):
    W, H = ATLAS_W * D, ATLAS_H * D
    img = np.zeros((H, W, 4), dtype=float)
    glow = np.zeros((H, W, 4), dtype=float) if look.glow else None
    styles = styles_of(look) if styles is None else styles
    for c in OWNERS:
        if c.style is not None and c.style not in styles:
            continue
        for face in faces(c):
            g = FaceGrid(c, face, D)
            if g.n == 0:
                continue
            col, alpha, gl = shade_face(look, g)
            sl = (slice(g.py0, g.py1), slice(g.px0, g.px1))
            # box UV stores the bottom face upside down; FaceGrid already follows Minecraft's own mapping
            img[sl][..., :3] = col
            img[sl][..., 3] = alpha
            if glow is not None and gl is not None:
                # premultiplied: the glow is drawn additively, so black means no light
                a = np.maximum(glow[sl][..., 3], np.clip(gl, 0, 1))
                glow[sl][..., 3] = a
                glow[sl][..., :3] = rgb(look.glow) * a[..., None]
    return img, glow


def shade_face(look, g):
    c, P, tag = g.cube, g.P, g.cube.tag
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    mat = c.mat or look.material
    alpha = np.ones(x.shape)
    gl = None
    seed = look.seed

    # --- base material ---------------------------------------------------------------------------------------
    n1 = fbm(P, 0.18, seed, 3)
    n2 = vnoise(P, 1.3, seed + 7)
    n3 = vnoise(P, 3.1, seed + 9)
    if mat == 'plate':
        col = plate_material(look, g, P, n1, n2, n3)
    elif mat == 'fur' or (mat == 'skin' and look.name.startswith('beast') and tag in ('chest', 'abdomen', 'pecs', 'traps', 'upper_arm', 'forearm', 'thigh', 'shin', 'delt')):
        col, alpha = fur_material(look, g, P, n1, n2)
    elif mat == 'bone':
        col = bone_material(look, g, P, n1, n2, n3)
    elif mat == 'gear':
        col = gear_material(look, g, P, n1, n2, n3)
    elif mat == 'hair' or tag == 'hair':
        col, alpha = hair_material(look, g, P)
    elif mat == 'muscle':
        col = muscle_material(look, g, P, n1, n2)
    elif mat == 'rock':
        col = rock_material(look, g, P, n1, n2, n3)
    elif mat == 'lava':
        col, gl = lava_material(look, g, P, n1, n2)
    elif mat == 'wood':
        col = wood_material(look, g, P, n1, n2)
    else:
        col = skin_material(look, g, P, n1, n2, n3)

    # --- anatomy (lines and volumes painted as light and shade) ---------------------------------------------
    if mat in ('skin', 'muscle', 'rock', 'lava', 'char') and tag not in ('hair',):
        sh = anatomy(look, g)
        col = col * sh[..., None]

    # --- roundness: boxes darken towards their edges so they read as limbs, not crates ----------------------
    if tag not in ('hair', 'teeth_top', 'teeth_bottom') and mat != 'fur':
        k = 0.22 if mat in ('skin', 'muscle', 'char', 'lava') else 0.15
        # thin faces (a shoulder ridge, a strip of neck) would go dark all over: shade them less
        k *= min(1.0, min(g.ew, g.eh) / 6.0)
        reach = min(2.2, 0.35 * min(g.ew, g.eh) + 0.4)
        round_ = 1.0 - k * (1.0 - smooth(0.0, reach, g.edge))
        col = col * round_[..., None]

    # --- face, mouth, teeth, nails ------------------------------------------------------------------------------
    if tag == 'skull' and g.name == 'front':
        col, gface = face_paint(look, g, col)
        if gface is not None:
            gl = gface if gl is None else np.maximum(gl, gface)
    elif tag == 'skull' and g.name == 'bottom':
        col = mouth_inside(col, g, 0.30)
    elif tag == 'jaw' and g.name == 'top':
        col = mouth_inside(col, g, 0.36)
    elif tag == 'jaw' and g.name == 'front':
        col = lower_lip(look, g, col)
    elif tag in ('teeth_top', 'teeth_bottom'):
        col = teeth(look, g)
    elif tag == 'finger' and g.name in ('front', 'bottom', 'back'):
        col = nails(look, g, col, tip_low=True)
    elif tag == 'toe' and g.name in ('front', 'top'):
        col = nails(look, g, col, tip_low=False)
    elif tag == 'stump':
        col = stump_paint(g)
    elif tag == 'ear':
        col = col * (1 - 0.25 * (1 - smooth(0.2, 0.9, np.abs(g.s - 0.5) * 2)))[..., None]
    if tag == 'bone_mask' and g.name == 'front':
        col = mask_eyes(look, g, col)
        if look.glow:
            gm = mask_eye_glow(g)
            gl = gm if gl is None else np.maximum(gl, gm)
    if tag == 'plate_crown' and g.name == 'front' and look.eyes == 'glow':
        pass

    if mat == 'fur' and tag.startswith('fur'):
        alpha = alpha * fur_edge(g)
    return np.clip(col, 0, 1), alpha, gl


def skin_material(look, g, P, n1, n2, n3):
    col = mix(look.skin, look.skin2, smooth(0.45, 0.85, n1) * look.blotch * 2.2)
    col = col * (0.94 + 0.08 * n2[..., None]) * (0.97 + 0.05 * n3[..., None])
    if look.material == 'char':
        col = mix(look.skin, look.skin2, smooth(0.3, 0.8, n1))
        col = col * (0.85 + 0.25 * n3[..., None])
    if look.exposed > 0:
        m = smooth(1 - look.exposed * 0.55, 1 - look.exposed * 0.45, fbm(P, 0.09, look.seed + 33, 3))
        col = mix(col, muscle_material(look, g, P, n1, n2, base='9e2a24', dark='5a120e'), m)
    if look.scars > 0:
        sc = np.abs(vnoise(P * np.array([1, 0.15, 1]), 0.6, look.seed + 5) - 0.5)
        col = col * (1 - look.scars * 0.35 * (1 - smooth(0.0, 0.03, sc)))[..., None]
    return col


def muscle_material(look, g, P, n1, n2, base=None, dark=None):
    b = rgb(base) if base else look.skin
    dk = rgb(dark) if dark else look.skin2
    # fibres run along the limb: stretch the noise along y
    fib = vnoise(P * np.array([2.2, 0.25, 2.2]), 1.0, look.seed + 21)
    fib2 = vnoise(P * np.array([4.5, 0.4, 4.5]), 1.0, look.seed + 22)
    col = mix(b, dk, smooth(0.35, 0.95, fib * 0.6 + fib2 * 0.4))
    col = col * (0.9 + 0.2 * n2[..., None])
    # white tendons and sinew at the joints
    tend = smooth(0.82, 0.95, vnoise(P * np.array([3.0, 0.2, 3.0]), 0.8, look.seed + 23))
    col = mix(col, rgb('e9d6c6'), tend * 0.55)
    return col


def rock_material(look, g, P, n1, n2, n3):
    col = mix(look.skin, look.skin2, smooth(0.3, 0.8, n1))
    col = col * (0.88 + 0.22 * n3[..., None])
    cr = cracks(P, 0.32, look.seed + 4)
    col = col * (1 - 0.55 * cr)[..., None]
    if look.exposed > 0:
        m = smooth(1 - look.exposed * 0.6, 1 - look.exposed * 0.5, fbm(P, 0.07, look.seed + 33, 3))
        flesh = muscle_material(look, g, P, n1, n2, base='9a2620', dark='4e0f0b')
        col = mix(col, flesh, m)
    return col


def plate_material(look, g, P, n1, n2, n3):
    col = look.plate * (0.86 + 0.18 * n1[..., None]) * (0.95 + 0.07 * n3[..., None])
    cr = cracks(P, 0.22, look.seed + 8)
    col = col * (1 - 0.45 * cr)[..., None]
    # plates are bevelled: a light rim and a darker seam
    rim = 1 - smooth(0.0, 0.7, g.edge)
    col = col * (1 + 0.18 * rim)[..., None]
    col = col * (1 - 0.35 * (1 - smooth(0.0, 0.25, g.edge)))[..., None]
    return col


def bone_material(look, g, P, n1, n2, n3):
    base = look.plate if look.name.startswith('jaw') else rgb('e6dccb')
    col = base * (0.88 + 0.15 * n1[..., None]) * (0.96 + 0.06 * n3[..., None])
    cr = cracks(P, 0.5, look.seed + 12)
    col = col * (1 - 0.3 * cr)[..., None]
    if g.cube.tag == 'claw':
        col = mix(col, rgb('2a2420'), smooth(0.2, 1.0, g.t if g.name != 'top' else np.zeros_like(g.t)) * 0.6)
    return col


def gear_material(look, g, P, n1, n2, n3):
    # riveted iron plates over oiled leather
    iron = rgb('5d6064') * (0.85 + 0.25 * n1[..., None])
    leather = rgb('5a3a22') * (0.8 + 0.3 * n2[..., None])
    band = (np.floor((P[..., 1] + 40) / 4.0) % 2 == 0)
    col = np.where(band[..., None], iron, leather)
    rivet = ((np.mod(P[..., 0] + 40, 3.0) < 0.6) & (np.mod(P[..., 1] + 40, 4.0) < 0.6))
    col = np.where(rivet[..., None], rgb('a7a9a8'), col)
    col = col * (1 - 0.3 * (1 - smooth(0.0, 0.5, g.edge)))[..., None]
    return col


def fur_material(look, g, P, n1, n2):
    # clumps of hair hanging down, each darker at the root and lighter at the tip, with dark partings between
    clump = vnoise(P * np.array([1.6, 0.0, 1.6]), 1.0, look.seed + 40)
    phase = np.mod(P[..., 1] * 0.55 + clump * 3.0, 1.0)
    strands = vnoise(P * np.array([6.0, 0.5, 6.0]), 1.0, look.seed + 41)
    strands2 = vnoise(P * np.array([12.0, 0.9, 12.0]), 1.0, look.seed + 42)
    col = look.fur * (0.55 + 0.55 * phase[..., None]) * (0.75 + 0.35 * strands[..., None] + 0.2 * (strands2[..., None] - 0.5))
    parting = smooth(0.0, 0.12, phase) * smooth(1.0, 0.9, phase)
    col = col * (0.55 + 0.45 * parting[..., None])
    col = mix(col, look.fur * 0.5, smooth(0.55, 0.9, n1) * 0.5)
    return col, np.ones(P.shape[:-1])


def fur_edge(g):
    # ragged hem along the lower edge of each fur piece, so the coat hangs in tufts
    if g.name in ('top', 'bottom'):
        return np.ones(g.s.shape)
    n = vnoise(np.stack([g.P[..., 0] * 3, np.zeros_like(g.s), g.P[..., 2] * 3], -1), 1.0, 77)
    cut = 0.82 + 0.16 * n
    return (g.t < cut).astype(float)


def hair_material(look, g, P):
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    hs = look.hairstyle
    if g.cube.tag == 'hair_back':
        mask = np.ones(x.shape)
        ragged = vnoise(np.stack([x * 1.6, np.zeros_like(x), z], -1), 1.0, look.seed + 3)
        mask = mask * (g.t < 0.86 + 0.14 * ragged)
    elif hs == 'bald':
        mask = np.zeros(x.shape)
    else:
        # height of the hairline in body space, all round the head (head spans y -40..-28)
        top = -40.6
        rag = vnoise(np.stack([x * 1.3, np.zeros_like(x), z * 1.3], -1), 1.0, look.seed + 3)
        front = z < -8
        back = z > 8
        if hs == 'bowl':
            line = np.where(front, -35.5, -33.0) + rag * 0.8
        elif hs == 'messy':
            line = np.where(front, -36.5, -32.0) + rag * 2.4
        elif hs == 'slick':
            line = np.where(front, -38.2, -34.5) + rag * 0.3
        elif hs in ('long', 'bun'):
            line = np.where(front, -37.0, -29.5) + rag * 0.7
        else:  # short
            line = np.where(front, -37.6, -33.5) + rag * 1.0
        # temples: the line steps down from the forehead to the sides
        side = (np.abs(x) > 8.5) & ~front
        line = np.where(side, np.minimum(line + 2.5, -31.0) if hs != 'slick' else line + 1.0, line)
        if hs in ('long', 'bun'):
            line = np.where(back, -28.4, line)
        mask = (y < line).astype(float)
        if g.name == 'top':
            mask = np.ones(x.shape)
        if hs == 'bun' and g.name == 'back':
            mask = np.maximum(mask, ((np.abs(x) < 3.5) & (y < -33)).astype(float))
    strands = vnoise(P * np.array([4.0, 0.35, 4.0]) if g.name != 'top' else P * np.array([4.0, 1.0, 0.4]), 1.0, look.seed + 61)
    fine = vnoise(P * np.array([10.0, 0.8, 10.0]), 1.0, look.seed + 62)
    col = look.hair * (0.6 + 0.45 * strands[..., None] + 0.25 * (fine[..., None] - 0.5))
    # a sheen across the crown
    sheen = smooth(-37, -40.5, y) * (g.name != 'bottom')
    col = col * (1 + 0.18 * sheen[..., None])
    return col, mask


def cracks(P, freq, seed):
    """Dark lines where two cells of a 3D cellular noise meet."""
    p = P * freq
    i = np.floor(p).astype(np.int64)
    best = np.full(P.shape[:-1], 9.0)
    second = np.full(P.shape[:-1], 9.0)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz
                fx = cx + _hash3(cx, cy, cz, seed)
                fy = cy + _hash3(cx, cy, cz, seed + 1)
                fz = cz + _hash3(cx, cy, cz, seed + 2)
                d = np.sqrt((p[..., 0] - fx) ** 2 + (p[..., 1] - fy) ** 2 + (p[..., 2] - fz) ** 2)
                second = np.where(d < best, best, np.minimum(second, d))
                best = np.minimum(best, d)
    return 1 - smooth(0.0, 0.09, second - best)


def lava_material(look, g, P, n1, n2):
    col = muscle_material(look, g, P, n1, n2)
    col = col * 0.75
    cr = cracks(P, 0.28, look.seed + 6)
    hot = cr * smooth(0.3, 0.6, n1)
    col = mix(col, rgb('ffb43a'), hot * 0.9)
    return col, hot * 0.95


def wood_material(look, g, P, n1, n2):
    grain = np.sin((P[..., 0] * 0.6 + P[..., 2] * 0.6 + n1 * 6.0) * 3.0) * 0.5 + 0.5
    col = mix(look.skin, look.skin2, grain * 0.5 + 0.25 * n2)
    # straw stuffing bursting from the seams
    straw = smooth(0.78, 0.9, vnoise(P * np.array([6.0, 0.5, 6.0]), 1.0, 5))
    col = mix(col, rgb('d9c27a'), straw * 0.7)
    rope = (np.mod(P[..., 1] + 60, 7.0) < 0.8)
    col = np.where(rope[..., None], rgb('8a6a3a'), col)
    return col


# --- anatomy -----------------------------------------------------------------------------------------------------
def line(d, width):
    """1 on a line (d=0), falling off over width."""
    return 1 - smooth(0.0, width, np.abs(d))


def anatomy(look, g):
    P, tag, face = g.P, g.cube.tag, g.name
    o = joint_origin(g.cube.part)
    x, y, z = P[..., 0] - o[0], P[..., 1] - o[1], P[..., 2] - o[2]   # part-local
    by = P[..., 1]                                                    # body height, for broad shading
    m = look.muscle
    sh = np.ones(x.shape)
    ax = np.abs(x)
    if tag in ('chest', 'pecs') and face == 'front':
        # pectorals: a soft lower edge, the sternum between them, and a highlight on their upper half
        pec_edge = y - (-17.5 - 1.6 * np.cos(np.clip(ax / 9.0, 0, 1) * 1.6))
        sh *= 1 - 0.30 * m * line(pec_edge, 0.9) * (ax > 0.6)
        sh *= 1 - 0.22 * m * line(x, 0.7) * (y > -24)
        sh *= 1 + 0.10 * m * smooth(-17.5, -22, y) * (ax < 8.5)
        nip = np.sqrt((ax - 5.2) ** 2 + (y + 19.0) ** 2)
        sh *= 1 - 0.35 * (1 - smooth(0.35, 0.75, nip))
        sh *= 1 - 0.18 * m * line(y - (-24.6 + 0.12 * ax), 0.5) * (ax > 1.2) * (ax < 9.5)   # collarbones
    if tag == 'chest' and face in ('left', 'right'):
        rib = np.sin((y + z * 0.35) * 1.9)
        sh *= 1 - 0.14 * m * smooth(0.6, 1.0, rib) * (y > -22) * (y < -12)
    if tag in ('abdomen', 'belly') and face == 'front':
        fat = look.fat if tag == 'abdomen' else 1.0
        k = m * (1 - 0.7 * fat)
        sh *= 1 - 0.25 * k * line(x, 0.6)
        for yy in (-9.0, -6.0, -3.2):
            sh *= 1 - 0.18 * k * line(y - yy - 0.15 * ax, 0.5) * (ax < 5.5)
        sh *= 1 - 0.18 * k * line(ax - 5.8, 0.8) * (y > -11)
        navel = np.sqrt(x ** 2 + (y + 1.6) ** 2)
        sh *= 1 - 0.55 * (1 - smooth(0.25, 0.7, navel))
        sh *= 1 - 0.20 * smooth(-3.0, 0.0, y)
    if tag in ('chest', 'traps', 'abdomen', 'neck') and face == 'back':
        sh *= 1 - 0.28 * m * line(x, 0.8)
        blade = np.sqrt(((ax - 5.0) / 3.5) ** 2 + ((y + 20.5) / 3.0) ** 2)
        sh *= 1 - 0.07 * m * line(blade - 1.0, 0.5) * (y > -23)
    if tag == 'neck':
        sh *= 1 - 0.25 * smooth(-28.5, -26.5, y)
        if face == 'back':
            napeo = np.sqrt((x / 3.4) ** 2 + ((y + 29.5) / 2.0) ** 2)
            sh *= 1 + 0.05 * (napeo < 1.0)
            sh *= 1 - 0.12 * line(napeo - 1.0, 0.3)
    if tag == 'traps' and face == 'top':
        sh *= 1 - 0.15 * line(ax - 4.5, 1.0)
    if tag == 'upper_arm':
        sh *= 1 - 0.18 * m * line(y - 2.6, 0.6)                     # under the deltoid
        if face == 'front':
            sh *= 1 + 0.10 * m * (1 - smooth(0.0, 1.0, np.abs(y - 7.0) / 4.0))
        sh *= 1 - 0.15 * smooth(12.0, 14.0, y)
    if tag == 'forearm':
        sh *= 1 - 0.22 * line(y - 0.8, 0.7)                         # elbow crease
        sh *= 1 - 0.20 * smooth(12.5, 14.5, y)                      # wrist
        if face in ('front', 'right', 'left'):
            vein = np.abs(vnoise(P * np.array([2.0, 0.3, 2.0]), 1.0, look.seed + 91) - 0.5)
            sh *= 1 - 0.10 * m * line(vein, 0.03)
    if tag == 'palm' and face == 'back':
        sh *= 1 - 0.15 * line(y - 4.2, 0.4)
    if tag == 'finger':
        sh *= 1 - 0.22 * line(np.mod(y + 0.2, 2.2) - 1.1, 0.25)      # knuckle creases
    if tag == 'thumb':
        sh *= 1 - 0.22 * line(y - 2.6, 0.25)
    if tag == 'thigh':
        sh *= 1 - 0.22 * smooth(1.5, -1.0, y)                       # groin shadow at the top
        if face == 'front':
            sh *= 1 - 0.15 * m * line(x + 0.1 * (y - 4), 0.6)
    if tag in ('shin', 'knee', 'calf'):
        if face == 'front':
            kn = np.sqrt((x / 2.2) ** 2 + ((y - 0.2) / 1.8) ** 2)
            sh *= 1 - 0.22 * line(kn - 1.0, 0.35)
        if face == 'back':
            sh *= 1 - 0.25 * line(y - 0.5, 0.6)
        sh *= 1 - 0.18 * smooth(6.5, 8.0, y)                        # ankle
    if tag == 'foot':
        sh *= 1 - 0.25 * smooth(8.5, 10.0, y) * (face != 'bottom')
    if tag == 'pelvis':
        if face == 'front':
            sh *= 1 - 0.30 * line(ax - 2.6 - (y - 2) * 0.9, 0.7)
    sh *= 0.9 + 0.12 * smooth(24, -30, by)
    if face == 'bottom':
        sh *= 0.8
    return sh


# --- face ----------------------------------------------------------------------------------------------------------
def face_paint(look, g, col):
    x, y = g.P[..., 0], g.P[..., 1]          # head front face: x -9..9, y -40..-28 (eyes at about -33)
    fy = y + 22.0                            # back into head space: -18 (crown) .. -6 (mouth line)
    gl = None
    if look.face == 'dummy':
        col = mix(col, rgb('2a1d12'), circle(x, fy, -4.0, -11.0, 1.0) + circle(x, fy, 4.0, -11.0, 1.0))
        col = mix(col, rgb('2a1d12'), line(fy + 7.2 + 0.08 * x * x * 0.1, 0.35) * (np.abs(x) < 5))
        return col, None
    ex = 4.2
    eye_y = -11.0
    eye_w, eye_h = (2.4, 1.25) if look.eyes not in ('small',) else (1.7, 0.95)
    if look.eyes == 'wide':
        eye_w, eye_h = 2.6, 1.5
    if look.face in ('colossal',):
        eye_w, eye_h = 2.2, 1.2
    # the brow ridge, a band of shadow across the forehead
    col = col * (1 - 0.16 * line(fy - (eye_y - 2.4), 0.8) * (np.abs(x) < 8.0))[..., None]
    # sockets: the eyes sit in shadow under the brow
    for sx in (-ex, ex):
        sock = np.sqrt(((x - sx) / (eye_w + 1.4)) ** 2 + ((fy - eye_y + 0.2) / (eye_h + 1.2)) ** 2)
        col = col * (1 - 0.32 * (1 - smooth(0.6, 1.15, sock)))[..., None]
    # cheekbones and nasolabial folds
    col = col * (1 + 0.07 * (1 - smooth(0.0, 2.0, np.abs(fy + 8.6))) * (np.abs(x) > 4))[..., None]
    fold = np.abs(np.abs(x) - (2.6 + (fy + 9.0) * 0.55))
    col = col * (1 - 0.18 * line(fold, 0.35) * (fy > -9.5) * (fy < -6.3))[..., None]
    # eyes
    for i, sx in enumerate((-ex, ex)):
        e = ((x - sx) / eye_w) ** 2 + ((fy - eye_y) / eye_h) ** 2
        inside = e < 1.0
        if look.eyes == 'void':
            col = np.where(inside[..., None], rgb('0b0807'), col)
            pupil = ((x - sx) ** 2 + (fy - eye_y) ** 2) < 0.22
            gl_e = (inside * 0.0 + pupil * 1.0)
            gl = gl_e if gl is None else np.maximum(gl, gl_e)
            continue
        white = rgb('efe9dc') if look.material not in ('muscle', 'rock', 'lava') else rgb('f4f1e8')
        col = np.where(inside[..., None], white, col)
        if look.eyes == 'glow':
            # boss titans: the whole eye burns
            irisr = 0.9
            ir = ((x - sx) ** 2 + (fy - eye_y) ** 2) < irisr ** 2
            col = np.where(ir[..., None] & inside[..., None], rgb(look.glow) * 0.9, col)
            gl_e = inside * 0.55 + ir * 0.45
            gl = gl_e if gl is None else np.maximum(gl, gl_e)
        else:
            px = sx + (0.55 if (look.eyes == 'cross' and sx < 0) else (-0.55 if look.eyes == 'cross' else 0.0))
            if look.eyes == 'mad':
                px = sx + (0.6 if i == 0 else -0.2)
            irisr = 0.75 if look.eyes != 'small' else 0.55
            d = np.sqrt((x - px) ** 2 + (fy - eye_y) ** 2)
            col = np.where(((d < irisr) & inside)[..., None], look.iris * (0.8 + 0.4 * (d / irisr))[..., None], col)
            col = np.where(((d < irisr * 0.5) & inside)[..., None], rgb('0a0806'), col)
            hl = np.sqrt((x - px + 0.25) ** 2 + (fy - eye_y + 0.25) ** 2) < 0.16
            col = np.where((hl & inside)[..., None], rgb('ffffff'), col)
        # lids: a dark rim, heavy upper lid for sleepy eyes
        rim = (e >= 0.82) & (e < 1.25)
        col = np.where(rim[..., None], col * 0.55, col)
        if look.eyes == 'sleepy':
            lid = inside & (fy < eye_y - eye_h * 0.1)
            col = np.where(lid[..., None], look.skin2 * 0.9, col)
        # bags under the eyes
        bag = np.sqrt(((x - sx) / (eye_w + 0.4)) ** 2 + ((fy - eye_y - 1.7) / 0.7) ** 2)
        col = col * (1 - 0.18 * line(bag - 1.0, 0.35) * (fy > eye_y + 1.0))[..., None]
    # brows (the brow cube carries the ridge; paint the hairs just above the sockets)
    if look.hairstyle != 'bald' and look.material == 'skin':
        tilt = {'sad': -0.25, 'snarl': 0.35, 'grin': 0.12}.get(look.face, 0.0)
        for sx in (-ex, ex):
            by = eye_y - 2.5 + tilt * (x - sx) * np.sign(sx)
            brow = (np.abs(fy - by) < 0.42) & (np.abs(x - sx) < 2.5)
            col = np.where(brow[..., None], mix(col, look.hair * 0.7, 0.85), col)
    # mouth along the bottom edge of the skull (the jaw meets it at fy = -6)
    col = upper_mouth(look, x, fy, col)
    # forehead wrinkles
    if look.face in ('grin', 'sad', 'gape', 'snarl'):
        for k in range(3):
            wy = -15.2 + k * 0.9 + 0.04 * x * x * 0.1
            col = col * (1 - 0.10 * line(fy - wy, 0.15) * (np.abs(x) < 6))[..., None]
    return col, gl


def circle(x, y, cx, cy, r):
    return (((x - cx) ** 2 + (y - cy) ** 2) < r * r).astype(float)


def upper_mouth(look, x, fy, col):
    f = look.face
    ax = np.abs(x)
    if f in ('grin', 'smile', 'colossal', 'jaw', 'snarl'):
        width = {'grin': 7.6, 'smile': 6.0, 'colossal': 8.6, 'jaw': 8.8, 'snarl': 6.5}[f]
        up = {'grin': 0.9, 'smile': 0.7, 'colossal': 0.0, 'jaw': 0.0, 'snarl': -0.2}[f]
        # the mouth line curves up towards its corners
        mline = -6.0 - up * (ax / width) ** 2 * 1.6
        teeth_h = 1.15 if f != 'colossal' else 1.6
        in_mouth = (fy > mline - teeth_h) & (ax < width)
        tooth = np.mod(x + 40.0, 1.15)
        tcol = rgb('efe7d2') * (1 - 0.35 * (tooth < 0.16))[..., None] * (1 - 0.25 * smooth(mline - teeth_h, mline, fy))[..., None]
        col = np.where(in_mouth[..., None], tcol, col)
        if f in ('colossal', 'jaw'):
            # no lips at all: bare gums above the teeth
            gum = (fy > mline - teeth_h - 1.1) & (fy <= mline - teeth_h) & (ax < width)
            col = np.where(gum[..., None], rgb('8e2a26'), col)
        else:
            lip = (fy > mline - teeth_h - 0.55) & (fy <= mline - teeth_h) & (ax < width + 0.3)
            col = np.where(lip[..., None], look.lips * 0.85, col)
            corner = np.sqrt((ax - width) ** 2 + (fy - mline + 0.3) ** 2) < 0.6
            col = np.where(corner[..., None], look.lips * 0.5, col)
    elif f == 'gape':
        hole = (((x / 4.0) ** 2 + ((fy + 6.0) / 1.6) ** 2) < 1.0)
        col = np.where(hole[..., None], rgb('2a0c0a'), col)
        lip = (((x / 4.6) ** 2 + ((fy + 6.0) / 2.1) ** 2) < 1.0) & ~hole
        col = np.where(lip[..., None], look.lips * 0.8, col)
    elif f == 'void':
        lip = (np.abs(fy + 6.3) < 0.35) & (ax < 6.5)
        col = np.where(lip[..., None], col * 0.45, col)
    elif f == 'armored':
        teeth_band = (fy > -7.4) & (ax < 8.0)
        tooth = np.mod(x + 40.0, 1.3)
        col = np.where(teeth_band[..., None], rgb('e2dccb') * (1 - 0.4 * (tooth < 0.18))[..., None], col)
    else:  # blank, sad
        wl = 4.8 if f == 'blank' else 4.2
        curve = 0.0 if f == 'blank' else -0.5
        mline = -6.5 + curve * (1 - (ax / wl) ** 2)
        lip = (np.abs(fy - mline) < 0.32) & (ax < wl)
        col = np.where(lip[..., None], look.lips * 0.55, col)
        upper = (fy > mline - 0.9) & (fy < mline - 0.3) & (ax < wl - 0.4)
        col = np.where(upper[..., None], mix(col, look.lips, 0.55), col)
    return col


def lower_lip(look, g, col):
    x, y = g.P[..., 0], g.P[..., 1]
    fy = y + 22.0 + 0.0           # jaw front: fy from -6 (top) to 0 (chin)
    ax = np.abs(x)
    f = look.face
    if f in ('grin', 'smile', 'colossal', 'jaw', 'snarl', 'armored'):
        width = {'grin': 7.6, 'smile': 6.0, 'colossal': 8.6, 'jaw': 8.8, 'snarl': 6.5, 'armored': 8.0}[f]
        teeth_h = 1.1 if f != 'colossal' else 1.6
        in_mouth = (fy < -6.0 + teeth_h) & (ax < width)
        tooth = np.mod(x + 40.5, 1.15)
        tcol = rgb('e9e0c8') * (1 - 0.35 * (tooth < 0.16))[..., None]
        col = np.where(in_mouth[..., None], tcol, col)
        if f in ('colossal', 'jaw'):
            gum = (fy >= -6.0 + teeth_h) & (fy < -6.0 + teeth_h + 1.1) & (ax < width)
            col = np.where(gum[..., None], rgb('8e2a26'), col)
        elif f != 'armored':
            lip = (fy >= -6.0 + teeth_h) & (fy < -6.0 + teeth_h + 0.6) & (ax < width + 0.3)
            col = np.where(lip[..., None], look.lips * 0.85, col)
    elif f in ('blank', 'sad', 'void', 'gape'):
        lip = (fy < -5.2) & (ax < 4.3)
        col = np.where(lip[..., None], mix(col, look.lips, 0.6), col)
    # the chin's shadow
    col = col * (1 - 0.15 * smooth(-3.0, 0.0, fy))[..., None]
    return col


def mouth_inside(col, g, dark):
    flesh = rgb('5e1a18') * (0.7 + 0.3 * g.t[..., None])
    tongue = (np.abs(g.P[..., 0]) < 4.5) & (g.s > 0.2)
    flesh = np.where(tongue[..., None], rgb('9a3a36'), flesh)
    return flesh * (1 - dark * 0.5)


def teeth(look, g):
    x = g.P[..., 0]
    tooth = np.mod(x + 40.0, 1.15)
    col = rgb('f0e8d4') * (1 - 0.35 * (tooth < 0.18))[..., None]
    if look.material in ('muscle', 'lava', 'rock'):
        col = col * 0.92
    return col


def nails(look, g, col, tip_low):
    t = g.t
    if g.name == 'bottom':
        return mix(col, rgb('e6cfc0'), 0.55)
    mask = t > 0.72 if tip_low else (t < 0.6)
    nail = mix(col, rgb('efdcd0'), 0.6) * 1.05
    return np.where(mask[..., None], nail, col)


def stump_paint(g):
    P = g.P
    meat = muscle_material(Look('stump', skin='9a2620', skin2='4e0f0b', seed=3), g, P, fbm(P, 0.2, 3, 2), vnoise(P, 1.3, 4))
    if g.name == 'bottom':
        d = np.sqrt((g.s - 0.5) ** 2 + (g.t - 0.5) ** 2)
        meat = np.where((d < 0.16)[..., None], rgb('f1e6d6'), meat)
        meat = np.where(((d >= 0.16) & (d < 0.22))[..., None], rgb('c9a890'), meat)
    return meat


def mask_eyes(look, g, col):
    x, y = g.P[..., 0], g.P[..., 1] + 22.0
    for sx in (-4.2, 4.2):
        slit = (((x - sx) / 2.6) ** 2 + ((y + 11.0) / 0.9) ** 2) < 1.0
        col = np.where(slit[..., None], rgb('0b0807'), col)
    return col


def mask_eye_glow(g):
    x, y = g.P[..., 0], g.P[..., 1] + 22.0
    out = np.zeros(x.shape)
    for sx in (-4.2, 4.2):
        out = np.maximum(out, ((((x - sx) / 1.4) ** 2 + ((y + 11.0) / 0.55) ** 2) < 1.0).astype(float))
    return out


# --------------------------------------------------------------------------------------------------------------------
# Wounds: decals in the same atlas, three levels of damage
# --------------------------------------------------------------------------------------------------------------------
def paint_wounds(level, D):
    W, H = ATLAS_W * D, ATLAS_H * D
    img = np.zeros((H, W, 4), dtype=float)
    rng = np.random.default_rng(1000 + level)
    for c in OWNERS:
        if c.style is not None or c.tag in ('hair', 'teeth_top', 'teeth_bottom'):
            continue
        for face in faces(c):
            g = FaceGrid(c, face, D)
            if g.n == 0:
                continue
            P = g.P
            out = np.zeros(P.shape[:-1] + (4,))
            area = g.ew * g.eh
            cuts = rng.poisson(area * 0.006 * level * level)
            for _ in range(cuts):
                cx, cy = rng.uniform(0, 1), rng.uniform(0, 1)
                ang = rng.uniform(0, math.pi)
                L = rng.uniform(2.0, 6.0) * (0.7 + 0.3 * level)
                wdt = rng.uniform(0.25, 0.6) * (0.8 + 0.3 * level)
                dx = (g.s - cx) * g.ew
                dy = (g.t - cy) * g.eh
                u = dx * math.cos(ang) + dy * math.sin(ang)
                v = -dx * math.sin(ang) + dy * math.cos(ang)
                taper = np.clip(1 - (u / (L * 0.5)) ** 2, 0, 1)
                core = (np.abs(v) < wdt * taper) & (np.abs(u) < L * 0.5)
                rim = (np.abs(v) < wdt * taper + 0.35 + 0.2 * level) & (np.abs(u) < L * 0.5 + 0.3)
                n = vnoise(P, 2.0, 11)
                flesh = rgb('6e0f0c') * (0.8 + 0.4 * n[..., None])
                out[..., :3] = np.where(core[..., None], flesh, np.where(rim[..., None] & (out[..., 3:] < 0.5), rgb('8f1611') * (0.85 + 0.3 * n[..., None]), out[..., :3]))
                out[..., 3] = np.maximum(out[..., 3], np.where(core, 1.0, np.where(rim, 0.85, 0.0)))
                # blood runs down from the lower lip of the cut
                if g.name not in ('top', 'bottom') and level >= 2:
                    run = (dy > 0) & (np.abs(dx - math.cos(ang) * rng.uniform(-L / 2, L / 2)) < 0.25) & (dy < rng.uniform(2, 6) * level / 2)
                    out[..., :3] = np.where(run[..., None] & (out[..., 3:] < 0.5), rgb('7a100c'), out[..., :3])
                    out[..., 3] = np.maximum(out[..., 3], run * 0.9)
            if level == 3:
                splat = smooth(0.72, 0.8, fbm(P, 0.25, 501, 3))
                out[..., :3] = np.where((splat > 0.5)[..., None] & (out[..., 3:] < 0.5), rgb('5a0b08'), out[..., :3])
                out[..., 3] = np.maximum(out[..., 3], splat * 0.75)
            sl = (slice(g.py0, g.py1), slice(g.px0, g.px1))
            img[sl] = out
    return img


# --------------------------------------------------------------------------------------------------------------------
def save(img, path):
    a = np.clip(img * 255.0 + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(a, 'RGBA').save(path, optimize=True)


def main():
    args = sys.argv[1:]
    D = 4
    only = None
    if '--density' in args:
        D = int(args[args.index('--density') + 1])
    if '--only' in args:
        only = args[args.index('--only') + 1]
    print('atlas %dx%d units, %d cubes (%d with own UV), density %d' % (ATLAS_W, ATLAS_H, len(CUBES), len(OWNERS), D))
    if only is None:
        write_java()
    for look in LOOKS:
        if only and look.name != only:
            continue
        d = D * 2 if look.name.startswith(('colossal', 'wall_titan')) else D
        img, glow = paint(look, d)
        save(img, os.path.join(TEX_DIR, look.name + '.png'))
        gpath = os.path.join(TEX_DIR, look.name + '_glow.png')
        if glow is not None:
            save(glow, gpath)
        elif os.path.exists(gpath):
            os.remove(gpath)
        print('  painted', look.name, img.shape[1], 'x', img.shape[0])
    if only is None or only == 'evaporate':
        # the raw body under every titan's skin, shown as it boils away (all extras painted: any titan may wear them)
        img, _ = paint(EVAPORATE, D, STYLES)
        save(img, os.path.join(TEX_DIR, 'evaporate.png'))
        print('  painted evaporate')
    if only is None or only.startswith('wounds'):
        for lvl in (1, 2, 3):
            save(paint_wounds(lvl, D), os.path.join(TEX_DIR, 'wounds_%d.png' % lvl))
            print('  painted wounds_%d' % lvl)


if __name__ == '__main__':
    main()
