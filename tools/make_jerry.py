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
geometry.jf_jerry_extras holds the speaker icon and the bone Jerry can carry in his mouth, and
geometry.jf_jerry_face holds his expressions (textures drawn by make_faces.py). They share bone
names with the main model, so they follow the same animations (head tilt, roll over).
"""
import json
from pathlib import Path

import make_faces
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
    # A bone held across the mouth, sticking out both sides. Shown when jf:bone is on.
    ("mouth_bone", "head", [0, 9, -11], None, [
        cube([-3.5, 8.5, -11.5], [7, 1, 1], [0, 11]),
        cube([-4.5, 8, -12], [1, 2, 2], [20, 0]),
        cube([3.5, 8, -12], [1, 2, 2], [20, 4]),
    ]),
    # The tennis ball he brings back when playing fetch. Shown when jf:ball is on.
    ("mouth_ball", "head", [0, 9, -12.5], None, [cube([-1, 7.5, -13.5], [2, 2, 2], [20, 8])]),
]

# The expression overlay: a flat plane just in front of the top 6x4 pixels of the face
# (brows, eyes, top of the muzzle). The snout sits in front of its lower middle, like vanilla.
FACE_BONES = [
    ("root", None, [0, 7, 0], None, []),
    ("head", "root", [-1, 10.5, -7], None, []),
    ("face", "head", [0, 11.5, -9], None, [
        {"origin": [-3, 9.5, -9.05], "size": [6, 4, 0], "uv": {"north": {"uv": [0, 0], "uv_size": [6, 4]}}},
    ]),
]

PALETTE.update({"O": (236, 230, 214, 255), "Q": (196, 188, 170, 255),  # bone colours
                "v": (212, 225, 87, 255), "V": (158, 157, 36, 255)})      # tennis ball

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
    grid = [["."] * 32 for _ in range(16)]
    # Speaker: box UV for a 10x10x0 plane puts the front at (0, 0) and the back (mirrored) at (10, 0).
    for y, row in enumerate(SPEAKER_ICON):
        for x, ch in enumerate(row + row[::-1]):
            grid[y][x] = ch
    # Bone: shaft (7x1x1 at 0,11 -> 16x2) and two knobs (1x2x2 at 20,0 and 20,4 -> 6x4 each).
    for x0, y0, w, h in ((0, 11, 16, 2), (20, 0, 6, 4), (20, 4, 6, 4)):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                grid[y][x] = "Q" if y == y0 + h - 1 else "O"
    # Tennis ball: 2x2x2 at (20, 8) -> 8x4, yellow-green with a white seam row.
    for y in range(8, 12):
        for x in range(20, 28):
            grid[y][x] = "w" if y == 10 else ("V" if y == 11 else "v")
    return ["".join(row) for row in grid]


def main():
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            geometry("geometry.jf_jerry", WOLF_BONES, 64, 32),
            geometry("geometry.jf_jerry_extras", EXTRAS_BONES, 32, 16),
            geometry("geometry.jf_jerry_face", FACE_BONES, 6, 4),  # textures are 8x (48x32)
        ],
    }
    path = ROOT / "packs/resource_pack/models/entity/jerry.geo.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(geo, indent=2) + "\n")
    assert all(ch in PALETTE for row in extras_texture() for ch in row)
    write_png(ROOT / "packs/resource_pack/textures/entity/jf_jerry_extras.png", extras_texture())
    make_faces.main()
    print("Jerry's models, speaker/bone texture and faces written.")


if __name__ == "__main__":
    main()
