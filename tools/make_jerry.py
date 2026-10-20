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
    # The "I'm listening!" speaker icon. Hidden unless the jf:speaking property is on. It hangs over his
    # head but isn't part of it, so it stays upright when he tilts his head to listen. The two sound
    # waves are their own bones so they can blink on one after the other.
    ("speaker", "root", [0, 22, -7], None, [cube([-1, 17, -7], [6, 10, 0], [0, 0])]),
    ("wave1", "speaker", [-1, 22, -7], None, [cube([-3, 20, -7], [1, 4, 0], [12, 0])]),
    ("wave2", "speaker", [-1, 22, -7], None, [cube([-5, 17, -7], [2, 10, 0], [14, 0])]),
    # A bone held across the mouth, sticking out both sides. Shown when jf:bone is on.
    ("mouth_bone", "head", [0, 9, -11], None, [
        cube([-3.5, 8.5, -11.5], [7, 1, 1], [0, 11]),
        cube([-4.5, 8, -12], [1, 2, 2], [20, 0]),
        cube([3.5, 8, -12], [1, 2, 2], [20, 4]),
    ]),
    # The tennis ball he brings back when playing fetch. Shown when jf:ball is on.
    ("mouth_ball", "head", [0, 9, -12.5], None, [cube([-1, 7.5, -13.5], [2, 2, 2], [20, 8])]),
]

# Outfits (part of geometry.jf_jerry_extras). Each piece is made of small solid-colour cubes that
# all sample one swatch pixel of jf_jerry_extras.png. Coordinates are model space, like the wolf.
SWATCHES = ["r", "w", "P", "c", "Y", "f", "F", "l", "L", "k", "e", "N", "u"]  # 2x2 squares from (0, 16)


def solid(origin, size, colour):
    x = SWATCHES.index(colour) * 2
    face = {"uv": [x, 16], "uv_size": [1, 1]}
    return {"origin": origin, "size": size, "uv": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}


# The red "!" that pops up over him when he hears his name. Shown when jf:alert is on.
ALERT_BONES = [
    ("alert", "root", [0, 17.5, -7], None, [
        solid([-1, 17.5, -8], [2, 2, 2], "r"),
        solid([-1, 20.5, -8], [2, 6, 2], "r"),
    ]),
]

NECK = [-1, 10, -3]  # the mane's pivot: "neck" undoes its 90 degree tilt, so the pieces below are upright
OUTFIT_BONES = [
    ("upperBody", "root", NECK, [90, 0, 0], []),
    ("neck", "upperBody", NECK, [-90, 0, 0], []),
    # A red bandana: a band around the neck and a little triangle on the chest, with white dots.
    ("bandana", "neck", NECK, None, [
        solid([-4.5, 6.75, -5], [9, 7.5, 1.5], "r"),
        solid([-3, 6.5, -6.6], [6, 1.5, 1], "r"),
        solid([-2, 5.5, -6.6], [4, 1, 1], "r"),
        solid([-1, 4.75, -6.6], [2, 0.75, 1], "r"),
        solid([-2, 6.75, -6.8], [1, 1, 0.2], "w"),
        solid([1, 6.75, -6.8], [1, 1, 0.2], "w"),
        solid([-0.5, 5.5, -6.8], [1, 0.75, 0.2], "w"),
    ]),
    # A blue bow tie under his chin.
    ("bow_tie", "neck", NECK, None, [
        solid([-2.5, 5.5, -6.6], [2, 2, 1], "u"),
        solid([0.5, 5.5, -6.6], [2, 2, 1], "u"),
        solid([-0.5, 6, -6.8], [1, 1, 1.2], "N"),
    ]),
    # A striped party hat between his ears, with a pom-pom.
    ("party_hat", "head", [0, 13.5, -7], None, [
        solid([-1.5, 13.5, -8.5], [3, 2, 3], "P"),
        solid([-1, 15.5, -8], [2, 1.5, 2], "Y"),
        solid([-0.5, 17, -7.5], [1, 1.5, 1], "c"),
        solid([-0.75, 18.5, -7.75], [1.5, 1, 1.5], "w"),
    ]),
    # A flower crown: little flowers and leaves around the top of his head.
    ("flower_crown", "head", [0, 13.5, -7], None, [
        solid([-3.25, 13.5, -9.25], [6.5, 0.5, 0.5], "l"),
        solid([-3.25, 13.5, -9.25], [0.5, 0.5, 4.5], "l"),
        solid([2.75, 13.5, -9.25], [0.5, 0.5, 4.5], "l"),
        solid([-2.75, 13.5, -9.5], [1, 1, 1], "f"),
        solid([-0.5, 13.5, -9.5], [1, 1, 1], "Y"),
        solid([1.75, 13.5, -9.5], [1, 1, 1], "F"),
        solid([-3.5, 13.5, -8], [1, 1, 1], "w"),
        solid([2.5, 13.5, -8], [1, 1, 1], "c"),
        solid([-3.5, 13.5, -5.75], [1, 1, 1], "F"),
        solid([2.5, 13.5, -5.75], [1, 1, 1], "f"),
        solid([-1.5, 13.5, -5.25], [3, 0.5, 0.5], "L"),
    ]),
    # Cool shades.
    ("shades", "head", [0, 11, -9], None, [
        solid([-3.2, 10.5, -9.6], [2.7, 1.6, 0.5], "k"),
        solid([0.5, 10.5, -9.6], [2.7, 1.6, 0.5], "k"),
        solid([-0.5, 11.4, -9.6], [1, 0.5, 0.5], "e"),
        solid([-3.3, 11.4, -9.2], [0.3, 0.4, 3.2], "e"),
        solid([3.0, 11.4, -9.2], [0.3, 0.4, 3.2], "e"),
        solid([-2.8, 11.5, -9.65], [0.6, 0.4, 0.1], "w"),
        solid([0.9, 11.5, -9.65], [0.6, 0.4, 0.1], "w"),
    ]),
]

# The thrown tennis ball (its own little entity, jf:thrown_ball). Same texture as the ball in
# his mouth, a bit bigger so you can spot it on the ground.
BALL_BONES = [
    ("ball", None, [0, 1.5, 0], None, [dict(cube([-1, 0.5, -1], [2, 2, 2], [20, 8]), inflate=0.5)]),
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
                "v": (212, 225, 87, 255), "V": (158, 157, 36, 255),       # tennis ball
                "P": (240, 98, 146, 255), "c": (79, 195, 247, 255),       # party pink, sky blue
                "f": (244, 143, 177, 255), "F": (186, 104, 200, 255),     # flower pink, lilac
                "l": (102, 187, 106, 255), "L": (56, 142, 60, 255),       # leaves
                "e": (66, 66, 66, 255), "N": (21, 40, 90, 255), "u": (48, 79, 254, 255)})  # frames, navy, blue

SPEAKER_ICON = [  # a hollow outline, like the one in the video
    "....kk....",
    "...k.k..k.",
    "kkk..k...k",
    "k....k.k.k",
    "k....k.k.k",
    "k....k.k.k",
    "k....k.k.k",
    "kkk..k...k",
    "...k.k..k.",
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
    grid = [["."] * 64 for _ in range(32)]
    # Speaker: box UV for a Wx10x0 plane puts the front at (u, 0) and the back (mirrored) at (u + W, 0).
    # The icon is cut into the speaker (columns 0-5), the small wave (column 7) and the big wave (8-9).
    for u, (x0, x1), (y0, y1) in ((0, (0, 6), (0, 10)), (12, (7, 8), (3, 7)), (14, (8, 10), (0, 10))):
        for y in range(y0, y1):
            part = SPEAKER_ICON[y][x0:x1]
            for x, ch in enumerate(part + part[::-1]):
                grid[y - y0][u + x] = ch
    # Bone: shaft (7x1x1 at 0,11 -> 16x2) and two knobs (1x2x2 at 20,0 and 20,4 -> 6x4 each).
    for x0, y0, w, h in ((0, 11, 16, 2), (20, 0, 6, 4), (20, 4, 6, 4)):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                grid[y][x] = "Q" if y == y0 + h - 1 else "O"
    # Tennis ball: 2x2x2 at (20, 8) -> 8x4, yellow-green with a white seam row.
    for y in range(8, 12):
        for x in range(20, 28):
            grid[y][x] = "w" if y == 10 else ("V" if y == 11 else "v")
    for i, ch in enumerate(SWATCHES):
        for y in (16, 17):
            grid[y][i * 2] = grid[y][i * 2 + 1] = ch
    return ["".join(row) for row in grid]


def main():
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            geometry("geometry.jf_jerry", WOLF_BONES, 64, 32),
            geometry("geometry.jf_jerry_extras", EXTRAS_BONES + ALERT_BONES + OUTFIT_BONES, 64, 32),
            geometry("geometry.jf_jerry_face", FACE_BONES, 6, 4),  # textures are 8x (48x32)
            geometry("geometry.jf_tennis_ball", BALL_BONES, 64, 32),
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
