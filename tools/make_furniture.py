"""Generates the Dog Bed and Food Bowl blocks' models and textures.

Run from the repo root:  python3 tools/make_furniture.py
Writes:
  packs/resource_pack/models/blocks/jf_furniture.geo.json   (dog bed, empty bowl, full bowl)
  packs/resource_pack/textures/blocks/jf_furniture.png

Both models are built from solid-colour cubes that sample one pixel of a small swatch texture,
like Jerry's outfits. Block model coordinates run from -8 to 8 across and 0 to 16 up.
"""
import json
from pathlib import Path

from make_faces import write_rgba_png

ROOT = Path(__file__).resolve().parent.parent

# Swatch colours, one 2x2 square each along the top row of the texture.
COLOURS = {
    "r": (192, 57, 43, 255),    # bowl red
    "R": (146, 40, 31, 255),    # bowl inside
    "k": (141, 90, 43, 255),    # kibble
    "K": (176, 122, 62, 255),   # kibble highlight
    "b": (59, 111, 182, 255),   # bed rim
    "B": (44, 84, 140, 255),    # bed rim shade
    "c": (241, 227, 198, 255),  # cushion
    "C": (222, 204, 168, 255),  # cushion shade
    "w": (255, 255, 255, 255),  # paw print / bone
}
NAMES = list(COLOURS)


def solid(origin, size, colour):
    x = NAMES.index(colour) * 2
    face = {"uv": [x, 0], "uv_size": [1, 1]}
    return {"origin": origin, "size": size, "uv": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}


def bowl(full):
    cubes = [
        solid([-4, 0, -4], [8, 1, 8], "R"),          # bottom
        solid([-5, 0, -5], [10, 3, 1], "r"),         # walls
        solid([-5, 0, 4], [10, 3, 1], "r"),
        solid([-5, 0, -4], [1, 3, 8], "r"),
        solid([4, 0, -4], [1, 3, 8], "r"),
        solid([-1.5, 1.2, -5.05], [3, 1, 0.1], "w"),  # a little white bone on the front
    ]
    if full:
        cubes += [
            solid([-4, 1, -4], [8, 1.5, 8], "k"),
            solid([-3, 2.5, -2], [1, 0.5, 1], "K"), solid([1, 2.5, -3], [1, 0.5, 1], "K"),
            solid([-1, 2.5, 1], [1, 0.5, 1], "K"), solid([2, 2.5, 2], [1, 0.5, 1], "K"),
            solid([-2.5, 2.5, 2.5], [1, 0.5, 1], "K"),
        ]
    return cubes


BED = [
    solid([-7, 0, -7], [14, 1.5, 14], "C"),          # base
    solid([-6, 1.5, -6], [12, 1, 12], "c"),          # cushion
    solid([-7.5, 0, -7.5], [15, 4, 2], "b"),         # rim (lower at the front so he can step in)
    solid([-7.5, 0, 5.5], [15, 4.5, 2], "b"),
    solid([-7.5, 0, -5.5], [2, 4, 11], "B"),
    solid([5.5, 0, -5.5], [2, 4, 11], "B"),
    solid([-1, 2.5, -1], [2, 0.1, 2], "C"),          # a paw print on the cushion
    solid([-2, 2.5, -2.5], [0.8, 0.1, 0.8], "C"), solid([-0.4, 2.5, -2.9], [0.8, 0.1, 0.8], "C"),
    solid([1.2, 2.5, -2.5], [0.8, 0.1, 0.8], "C"),
]


def geometry(identifier, cubes):
    return {
        "description": {"identifier": identifier, "texture_width": 32, "texture_height": 4,
                        "visible_bounds_width": 2, "visible_bounds_height": 1, "visible_bounds_offset": [0, 0.5, 0]},
        "bones": [{"name": "block", "pivot": [0, 0, 0], "cubes": cubes}],
    }


def main():
    geo = {"format_version": "1.12.0", "minecraft:geometry": [
        geometry("geometry.jf_dog_bed", BED),
        geometry("geometry.jf_food_bowl", bowl(False)),
        geometry("geometry.jf_food_bowl_full", bowl(True)),
    ]}
    path = ROOT / "packs/resource_pack/models/blocks/jf_furniture.geo.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(geo, indent=2) + "\n")
    swatches = [COLOURS[ch] for ch in NAMES for _ in (0, 1)]
    row = swatches + [(0, 0, 0, 0)] * (32 - len(swatches))
    write_rgba_png(ROOT / "packs/resource_pack/textures/blocks/jf_furniture.png", [row, row, [(0, 0, 0, 0)] * 32, [(0, 0, 0, 0)] * 32])
    print("Dog bed and food bowl models written.")


if __name__ == "__main__":
    main()
