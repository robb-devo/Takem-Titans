"""
Takem Titans - town building blocks: lime plaster, half-timber braces and clay roof tiles.

Writes the 16px textures, blockstates, block/item models, loot tables, tags and lang names.
Run from the repo root:  python tools/block_art.py
"""
import json
import os

import numpy as np
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(REPO, 'src/main/resources/assets/takemtitans')
D = os.path.join(REPO, 'src/main/resources/data')
NS = 'takemtitans'


def rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255.0


def rng(seed):
    return np.random.default_rng(seed)


def save_png(img, name):
    a = np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(a, 'RGB').save(os.path.join(A, 'textures/block', name + '.png'))


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


# ---------------------------------------------------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------------------------------------------------
def plaster(base, seed):
    r = rng(seed)
    n = r.random((16, 16))
    # soft lime render: low-contrast mottling, a few darker grains and a faint drip of weathering
    blur = (n + np.roll(n, 1, 0) + np.roll(n, 1, 1) + np.roll(n, -1, 0) + np.roll(n, -1, 1)) / 5
    img = base[None, None, :] * (0.94 + 0.09 * blur[..., None])
    grains = r.random((16, 16)) < 0.05
    img[grains] *= 0.9
    return np.clip(img, 0, 1)


def wood_beam(t, along, seed):
    """Timber colour for a beam: dark oak, with grain running along the beam."""
    r = rng(seed)
    base = rgb('4a3020')
    grain = 0.85 + 0.25 * np.sin(along * 1.7 + r.random() * 6) * 0.5 + 0.1 * r.random(along.shape)
    return base[None, None, :] * grain[..., None]


def brace(base, seed, mirrored):
    img = plaster(base, seed)
    ys, xs = np.mgrid[0:16, 0:16]
    # "/" runs from the bottom-left corner to the top-right one; "\" the other way
    d = (xs + ys - 15) if not mirrored else (xs - ys)
    along = (xs - ys) if not mirrored else (xs + ys)
    beam = np.abs(d) <= 2
    edge = np.abs(d) == 3
    wood = wood_beam(None, along * 0.5, seed + 1)
    img = np.where(beam[..., None], wood, img)
    img = np.where(edge[..., None], img * 0.78, img)            # the plaster is shadowed along the beam
    return np.clip(img, 0, 1)


def tiles(main, dark, seed, slate=False):
    """Overlapping rows of clay tiles (or slates), each a little different, darker under the row above."""
    r = rng(seed)
    img = np.zeros((16, 16, 3))
    for row in range(4):
        y0 = row * 4
        off = 2 if row % 2 else 0
        for k in range(-1, 5):
            x0 = k * 4 + off
            tone = 0.88 + 0.22 * r.random()
            hue = main * tone + (r.random() - 0.5) * 0.06
            for dy in range(4):
                for dx in range(4):
                    x = (x0 + dx) % 16
                    y = y0 + dy
                    c = hue.copy()
                    # light on the upper part, shadow at the lower lip and between tiles
                    c = c * (1.06 - 0.06 * dy)
                    if dy == 3:
                        c = dark * (0.9 + 0.1 * r.random())
                    if dx == 3 and not slate:
                        c = c * 0.78
                    if slate and dx == 3:
                        c = c * 0.85
                    if not slate and dy == 2 and dx in (1, 2):
                        c = c * 0.93           # the curve of a pantile
                    img[y, x] = c
    noise = 0.96 + 0.08 * r.random((16, 16))
    return np.clip(img * noise[..., None], 0, 1)


PLASTERS = {
    'plaster': ('White Plaster', 'Weißer Putz', rgb('e9e4d8')),
    'plaster_cream': ('Cream Plaster', 'Cremefarbener Putz', rgb('e6d6b4')),
    'plaster_ochre': ('Ochre Plaster', 'Ockerputz', rgb('d4ad6a')),
}
BRACED = ['plaster', 'plaster_cream']
TILES = {
    'roof_tiles': ('Clay Roof Tiles', 'Tondachziegel', rgb('b2502e'), rgb('6e2a18'), False),
    'roof_tiles_dark': ('Dark Roof Tiles', 'Dunkle Dachziegel', rgb('7a4a32'), rgb('3e2416'), False),
    'roof_tiles_slate': ('Slate Roof Tiles', 'Schieferdach', rgb('4e5866'), rgb('262c34'), True),
}


# ---------------------------------------------------------------------------------------------------------------------
# JSON
# ---------------------------------------------------------------------------------------------------------------------
def cube(name, tex=None):
    write_json(os.path.join(A, 'models/block', name + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': '%s:block/%s' % (NS, tex or name)}})
    write_json(os.path.join(A, 'blockstates', name + '.json'), {'variants': {'': {'model': '%s:block/%s' % (NS, name)}}})
    item_model(name)
    loot(name)


def item_model(name):
    write_json(os.path.join(A, 'models/item', name + '.json'), {'parent': '%s:block/%s' % (NS, name)})


def loot(name, slab=False):
    entry = {'type': 'minecraft:item', 'name': '%s:%s' % (NS, name)}
    if slab:
        entry['functions'] = [{
            'function': 'minecraft:set_count', 'count': 2.0, 'add': False,
            'conditions': [{'condition': 'minecraft:block_state_property', 'block': '%s:%s' % (NS, name), 'properties': {'type': 'double'}}],
        }, {'function': 'minecraft:explosion_decay'}]
    write_json(os.path.join(D, NS, 'loot_table/blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
    })


def stairs(name, tex):
    t = '%s:block/%s' % (NS, tex)
    for suffix, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
        write_json(os.path.join(A, 'models/block', name + suffix + '.json'),
                   {'parent': 'minecraft:block/' + parent, 'textures': {'bottom': t, 'top': t, 'side': t}})
    variants = {}
    for facing, y0 in (('east', 0), ('south', 90), ('west', 180), ('north', 270)):
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                model = name + ('' if shape == 'straight' else ('_inner' if shape.startswith('inner') else '_outer'))
                y = y0
                if half == 'bottom' and shape in ('inner_left', 'outer_left'):
                    y = (y0 + 270) % 360
                if half == 'top' and shape in ('inner_right', 'outer_right'):
                    y = (y0 + 90) % 360
                v = {'model': '%s:block/%s' % (NS, model)}
                if half == 'top':
                    v['x'] = 180
                if y:
                    v['y'] = y
                if half == 'top' or y:
                    v['uvlock'] = True
                variants['facing=%s,half=%s,shape=%s' % (facing, half, shape)] = v
    write_json(os.path.join(A, 'blockstates', name + '.json'), {'variants': variants})
    item_model(name)
    loot(name)


def slab(name, tex, full):
    t = '%s:block/%s' % (NS, tex)
    write_json(os.path.join(A, 'models/block', name + '.json'), {'parent': 'minecraft:block/slab', 'textures': {'bottom': t, 'top': t, 'side': t}})
    write_json(os.path.join(A, 'models/block', name + '_top.json'), {'parent': 'minecraft:block/slab_top', 'textures': {'bottom': t, 'top': t, 'side': t}})
    write_json(os.path.join(A, 'blockstates', name + '.json'), {'variants': {
        'type=bottom': {'model': '%s:block/%s' % (NS, name)},
        'type=top': {'model': '%s:block/%s_top' % (NS, name)},
        'type=double': {'model': '%s:block/%s' % (NS, full)},
    }})
    item_model(name)
    loot(name, slab=True)


def merge_tag(path, values):
    tag = {'replace': False, 'values': []}
    if os.path.exists(path):
        tag = json.load(open(path, encoding='utf-8'))
    for v in values:
        if v not in tag['values']:
            tag['values'].append(v)
    write_json(path, tag)


def merge_lang(lang, names):
    p = os.path.join(A, 'lang', lang + '.json')
    d = json.load(open(p, encoding='utf-8'))
    d.update(names)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write('\n')


def main():
    os.makedirs(os.path.join(A, 'textures/block'), exist_ok=True)
    all_blocks, stairs_ids, slab_ids = [], [], []
    en, de = {}, {}
    for i, (name, (n_en, n_de, col)) in enumerate(PLASTERS.items()):
        save_png(plaster(col, 10 + i), name)
        cube(name)
        all_blocks.append(name)
        en['block.%s.%s' % (NS, name)] = n_en
        de['block.%s.%s' % (NS, name)] = n_de
        if name in BRACED:
            for mirrored in (False, True):
                b = name + '_brace' + ('_mirrored' if mirrored else '')
                save_png(brace(col, 20 + i, mirrored), b)
                cube(b)
                all_blocks.append(b)
                en['block.%s.%s' % (NS, b)] = n_en + (' Brace' if not mirrored else ' Brace (Mirrored)')
                de['block.%s.%s' % (NS, b)] = n_de + (' mit Strebe' if not mirrored else ' mit Strebe (gespiegelt)')
    for i, (name, (n_en, n_de, main_col, dark, is_slate)) in enumerate(TILES.items()):
        save_png(tiles(main_col, dark, 40 + i, is_slate), name)
        cube(name)
        stairs(name + '_stairs', name)
        slab(name + '_slab', name, name)
        all_blocks += [name, name + '_stairs', name + '_slab']
        stairs_ids.append(name + '_stairs')
        slab_ids.append(name + '_slab')
        for suffix, s_en, s_de in (('', '', ''), ('_stairs', ' Stairs', ' (Treppe)'), ('_slab', ' Slab', ' (Stufe)')):
            en['block.%s.%s%s' % (NS, name, suffix)] = n_en + s_en
            de['block.%s.%s%s' % (NS, name, suffix)] = n_de + s_de
    merge_tag(os.path.join(D, 'minecraft/tags/block/mineable/pickaxe.json'), ['%s:%s' % (NS, b) for b in all_blocks])
    merge_tag(os.path.join(D, 'minecraft/tags/block/stairs.json'), ['%s:%s' % (NS, b) for b in stairs_ids])
    merge_tag(os.path.join(D, 'minecraft/tags/block/slabs.json'), ['%s:%s' % (NS, b) for b in slab_ids])
    merge_tag(os.path.join(D, 'minecraft/tags/item/stairs.json'), ['%s:%s' % (NS, b) for b in stairs_ids])
    merge_tag(os.path.join(D, 'minecraft/tags/item/slabs.json'), ['%s:%s' % (NS, b) for b in slab_ids])
    merge_lang('en_us', en)
    merge_lang('de_de', de)
    print('blocks:', ' '.join(all_blocks))


if __name__ == '__main__':
    main()
