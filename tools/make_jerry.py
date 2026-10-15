"""Generates Jerry's 3D model (geometry) and matching texture from one cube list.

Run from the repo root:  python3 tools/make_jerry.py
Writes:
  packs/resource_pack/models/entity/jerry.geo.json
  packs/resource_pack/textures/entity/jf_jerry.png

Coordinates are Bedrock model pixels: y is up, the dog faces -Z (north).
Every cube uses box UV, so each cube owns a (2d + 2w) x (d + h) area of the texture.
"""
import json
import random
from pathlib import Path

from make_textures import write_png

ROOT = Path(__file__).resolve().parent.parent
TEX_W, TEX_H = 64, 64

COLORS = {
    ".": (0, 0, 0, 0),
    "W": (240, 240, 242, 255),  # white fur
    "G": (168, 170, 178, 255),  # grey fur
    "K": (28, 28, 30, 255),     # eyes / nose
    "P": (255, 160, 178, 255),  # pink (inner ears, cheeks)
    "R": (204, 52, 46, 255),    # collar
    "Y": (255, 206, 64, 255),   # collar tag
    "w": (255, 255, 255, 255),  # speaker fill
}
FUR = {"W", "G"}

SPEAKER_ICON = [
    "....KK....",
    "...KwK..K.",
    "KKKwwK...K",
    "KwwwwK.K.K",
    "KwwwwK.K.K",
    "KwwwwK.K.K",
    "KwwwwK.K.K",
    "KKKwwK...K",
    "...KwK..K.",
    "....KK....",
]


def cube(origin, size, uv, base="W", top=None, side_top_rows=0, front=None, back=None, bottom=None):
    return {
        "origin": origin, "size": size, "uv": uv,
        "paint": {"base": base, "top": top, "side_top_rows": side_top_rows, "front": front, "back": back, "bottom": bottom},
    }


# name, parent, pivot, rotation, cubes
BONES = [
    ("root", None, [0, 8, 0], None, []),
    ("body", "root", [0, 10, -4], None, [
        cube([-3, 7, -3], [6, 6, 10], [20, 0], top="G", side_top_rows=2),            # torso
        cube([-4, 6, -6], [8, 8, 5], [0, 16], top="G", side_top_rows=2),             # fluffy chest
        cube([-4.5, 11, -6.5], [9, 2, 6], [26, 16], base="R",                        # collar
             front=["RRRRYRRRR", "RRRRYRRRR"]),
    ]),
    ("head", "body", [0, 12, -7], None, [
        cube([-3, 10, -10], [6, 6, 4], [0, 0], top="G", side_top_rows=2, front=[
            "GGGGGG",
            "GWWWWG",
            "WKWWKW",
            "WKWWKW",
            "PWWWWP",
            "WWWWWW",
        ]),
        cube([-1.5, 10, -13], [3, 3, 3], [0, 10], front=["KKK", "WWW", "WWW"]),     # snout + nose
        cube([-3, 16, -8], [2, 2, 1], [12, 10], base="G", front=["GG", "PG"]),       # ears
        cube([1, 16, -8], [2, 2, 1], [12, 13], base="G", front=["GG", "GP"]),
    ]),
    # The "I heard you!" speaker icon. Hidden unless the jf:speaking property is on.
    ("speaker", "head", [0, 24, -8], None, [
        cube([-5, 19, -8], [10, 10, 0], [40, 29], base=".", front=SPEAKER_ICON,
             back=[row[::-1] for row in SPEAKER_ICON]),
    ]),
    ("leg0", "root", [-2, 8, -4], None, [cube([-3, 0, -5], [2, 8, 2], [0, 29], bottom="G")]),
    ("leg1", "root", [2, 8, -4], None, [cube([1, 0, -5], [2, 8, 2], [8, 29], bottom="G")]),
    ("leg2", "root", [-2, 8, 5], None, [cube([-3, 0, 4], [2, 8, 2], [16, 29], bottom="G")]),
    ("leg3", "root", [2, 8, 5], None, [cube([1, 0, 4], [2, 8, 2], [24, 29], bottom="G")]),
    ("tail", "body", [0, 12, 7], [50, 0, 0], [cube([-1, 4, 6], [2, 8, 2], [32, 29], side_top_rows=3, top="G")]),
]


def box_faces(u, v, w, h, d):
    """Texture rectangles (x, y, width, height) for each face in Bedrock box UV."""
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def paint_texture():
    grid = [["." for _ in range(TEX_W)] for _ in range(TEX_H)]
    shade = [[0 for _ in range(TEX_W)] for _ in range(TEX_H)]
    rng = random.Random(7)

    def fill(rect, color, rows=None):
        x0, y0, w, h = rect
        for y in range(h if rows is None else min(rows, h)):
            for x in range(w):
                grid[y0 + y][x0 + x] = color
                shade[y0 + y][x0 + x] = rng.randint(-7, 7)

    def stamp(rect, art):
        x0, y0, w, h = rect
        assert len(art) == h and all(len(r) == w for r in art), f"art doesn't fit {rect}"
        for y, row in enumerate(art):
            for x, ch in enumerate(row):
                grid[y0 + y][x0 + x] = ch

    for _, _, _, _, cubes in BONES:
        for c in cubes:
            w, h, d = (int(s) for s in c["size"])
            faces = box_faces(*c["uv"], w, h, d)
            p = c["paint"]
            for face, rect in faces.items():
                fill(rect, p["base"])
            if p["top"]:
                fill(faces["up"], p["top"])
                for side in ("east", "west", "north", "south"):
                    fill(faces[side], p["top"], rows=p["side_top_rows"])
            if p["bottom"]:
                fill(faces["down"], p["bottom"])
            if p["front"]:
                stamp(faces["north"], p["front"])
            if p["back"]:
                stamp(faces["south"], p["back"])

    # Convert to the palette-grid format write_png expects, with a little fur noise.
    palette = {}
    rows = []
    for y in range(TEX_H):
        row = ""
        for x in range(TEX_W):
            ch = grid[y][x]
            rgba = COLORS[ch]
            if ch in FUR:
                n = shade[y][x]
                rgba = tuple(max(0, min(255, c + n)) for c in rgba[:3]) + (255,)
            key = chr(0x100 + len(palette)) if rgba not in palette.values() else next(k for k, v in palette.items() if v == rgba)
            palette[key] = rgba
            row += key
        rows.append(row)
    return rows, palette


def geometry():
    bones = []
    for name, parent, pivot, rotation, cubes in BONES:
        bone = {"name": name, "pivot": pivot}
        if parent:
            bone["parent"] = parent
        if rotation:
            bone["rotation"] = rotation
        if cubes:
            bone["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in cubes]
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.jf_jerry",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 2,
                "visible_bounds_height": 2.5,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": bones,
        }],
    }


def main():
    rows, palette = paint_texture()
    import make_textures
    make_textures.PALETTE.update(palette)
    write_png(ROOT / "packs/resource_pack/textures/entity/jf_jerry.png", rows)
    geo_path = ROOT / "packs/resource_pack/models/entity/jerry.geo.json"
    geo_path.parent.mkdir(parents=True, exist_ok=True)
    geo_path.write_text(json.dumps(geometry(), indent=2) + "\n")
    print("Jerry's model and texture written.")


if __name__ == "__main__":
    main()
