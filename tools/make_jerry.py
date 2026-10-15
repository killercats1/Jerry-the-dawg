"""Generates Jerry's models and the speaker-icon texture.

Run from the repo root:  python3 tools/make_jerry.py
Writes:
  packs/resource_pack/models/entity/jerry.geo.json   (geometry.jf_jerry + geometry.jf_jerry_extras)
  packs/resource_pack/textures/entity/jf_jerry_extras.png

Jerry looks exactly like a regular tamed wolf: geometry.jf_jerry copies the shape and UVs of the
vanilla wolf model (Mojang/bedrock-samples, models/entity/wolf.geo.json) so it can use the game's own
wolf texture. Differences from vanilla, needed for Jerry's tricks:
  - everything hangs off a "root" bone, so the whole dog can roll over
  - the resting pose that vanilla sets in animation.wolf.setup is baked into the bones
geometry.jf_jerry_extras holds the speaker icon. It shares bone names with the main model, so it
follows the same animations (head tilt, roll over).
"""
import json
from pathlib import Path

from make_textures import PALETTE, write_png

ROOT = Path(__file__).resolve().parent.parent

LEG_UV = [0, 18]


def cube(origin, size, uv):
    return {"origin": origin, "size": size, "uv": uv}


# name, parent, pivot, rotation, cubes. Coordinates and UVs match the vanilla wolf.
WOLF_BONES = [
    ("root", None, [0, 7, 0], None, []),
    ("head", "root", [-1, 10.5, -7], None, [
        cube([-3, 7.5, -9], [6, 6, 4], [0, 0]),
        cube([-3, 13.5, -7], [2, 2, 1], [16, 14]),   # ears
        cube([1, 13.5, -7], [2, 2, 1], [16, 14]),
        cube([-1.5, 7.51563, -12], [3, 3, 4], [0, 10]),  # snout
    ]),
    ("body", "root", [0, 10, 2], [90, 0, 0], [cube([-3, 3, -1], [6, 9, 6], [18, 14])]),
    ("upperBody", "root", [-1, 10, -3], [90, 0, 0], [cube([-4, 7, -6], [8, 6, 7], [21, 0])]),  # mane + collar
    ("leg0", "root", [-2.5, 8, 7], None, [cube([-2.5, 0, 6], [2, 8, 2], LEG_UV)]),   # back legs
    ("leg1", "root", [0.5, 8, 7], None, [cube([0.5, 0, 6], [2, 8, 2], LEG_UV)]),
    ("leg2", "root", [-2.5, 8, -4], None, [cube([-2.5, 0, -5], [2, 8, 2], LEG_UV)]),  # front legs
    ("leg3", "root", [0.5, 8, -4], None, [cube([0.5, 0, -5], [2, 8, 2], LEG_UV)]),
    ("tail", "root", [-1, 12, 8], [95, 0, 0], [cube([-1, 4, 7], [2, 8, 2], [9, 18])]),  # tamed, happy tail
]

EXTRAS_BONES = [
    ("root", None, [0, 7, 0], None, []),
    ("head", "root", [-1, 10.5, -7], None, []),
    # The "I heard you!" speaker icon. Hidden unless the jf:speaking property is on.
    ("speaker", "head", [0, 22, -7], None, [cube([-5, 17, -7], [10, 10, 0], [0, 0])]),
]

SPEAKER_ICON = [
    "....kk....",
    "...kwk..k.",
    "kkkwwk...k",
    "kwwwwk.k.k",
    "kwwwwk.k.k",
    "kwwwwk.k.k",
    "kwwwwk.k.k",
    "kkkwwk...k",
    "...kwk..k.",
    "....kk....",
]


def geometry(identifier, bones, tex_w, tex_h):
    out = []
    for name, parent, pivot, rotation, cubes in bones:
        bone = {"name": name, "pivot": pivot}
        if parent:
            bone["parent"] = parent
        if rotation:
            bone["rotation"] = rotation
        if cubes:
            bone["cubes"] = cubes
        out.append(bone)
    return {
        "description": {
            "identifier": identifier,
            "texture_width": tex_w,
            "texture_height": tex_h,
            "visible_bounds_width": 2,
            "visible_bounds_height": 2.5,
            "visible_bounds_offset": [0, 1, 0],
        },
        "bones": out,
    }


def extras_texture():
    # Box UV for a 10x10x0 plane: front face at (0, 0), back face (mirrored) at (10, 0).
    rows = []
    for y in range(16):
        icon = SPEAKER_ICON[y] if y < 10 else "." * 10
        back = icon[::-1]
        rows.append((icon + back).ljust(32, "."))
    return rows


def main():
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            geometry("geometry.jf_jerry", WOLF_BONES, 64, 32),
            geometry("geometry.jf_jerry_extras", EXTRAS_BONES, 32, 16),
        ],
    }
    path = ROOT / "packs/resource_pack/models/entity/jerry.geo.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(geo, indent=2) + "\n")
    assert all(ch in PALETTE for row in extras_texture() for ch in row)
    write_png(ROOT / "packs/resource_pack/textures/entity/jf_jerry_extras.png", extras_texture())
    print("Jerry's models and speaker texture written.")


if __name__ == "__main__":
    main()
