"""Generates Mochi's model, face geometry and face expressions.

Run from the repo root:  python3 tools/make_mochi.py
Writes:
  packs/resource_pack/models/entity/mochi.geo.json   (geometry.jf_mochi + geometry.jf_mochi_face)
  packs/resource_pack/textures/entity/mochi/<coat>_<face>.png

Mochi looks exactly like a regular tamed cat: geometry.jf_mochi copies the vanilla cat model
(Mojang/bedrock-samples, models/entity/cat.geo.json) so she can use the game's own cat textures.
The vanilla model uses bind_pose_rotation for the body and tail (it tilts their own cubes but
not their children). Here each of those cubes sits in a "_shape" child bone with that rotation
instead, which does the same thing in the newer geometry format. Everything hangs off a "root"
bone so she can roll over.

Her expressions cover the top two rows of her face (brows and eyes) at 8x resolution. Every
vanilla coat has different face colours, so each coat gets its own set, painted over that
coat's own fur (the colours below are sampled from the vanilla *_tame textures).
"""
import json
from pathlib import Path

import make_faces
from make_faces import Canvas, INK, WHITE

ROOT = Path(__file__).resolve().parent.parent


def cube(origin, size, uv):
    return {"origin": origin, "size": size, "uv": uv}


# name, parent, pivot, rotation, cubes. Coordinates and UVs match the vanilla cat.
CAT_BONES = [
    ("root", None, [0, 0, 0], None, []),
    ("body", "root", [0, 7, 1], None, []),
    ("body_shape", "body", [0, 7, 1], [90, 0, 0], [cube([-2, -1, -2], [4, 16, 6], [20, 0])]),
    ("head", "body", [0, 9, -9], None, [
        cube([-2.5, 7, -12], [5, 4, 5], [0, 0]),
        cube([-1.5, 7.01562, -13], [3, 2, 2], [0, 24]),  # nose
        cube([-2, 11, -9], [1, 1, 2], [0, 10]),          # ears
        cube([1, 11, -9], [1, 1, 2], [6, 10]),
    ]),
    ("tail1", "body", [0, 9, 8], None, []),
    ("tail1_shape", "tail1", [0, 9, 8], [90, 0, 0], [cube([-0.5, 1, 8], [1, 8, 1], [0, 15])]),
    ("tail2", "tail1", [0, 9, 16], None, []),
    ("tail2_shape", "tail2", [0, 9, 16], [90, 0, 0], [cube([-0.5, 1, 16], [1, 8, 1], [4, 15])]),
    ("backLegL", "body", [1.1, 6, 7], None, [cube([0.1, 0, 6], [2, 6, 2], [8, 13])]),
    ("backLegR", "body", [-1.1, 6, 7], None, [cube([-2.1, 0, 6], [2, 6, 2], [8, 13])]),
    ("frontLegL", "body", [1.2, 10, -4], None, [cube([0.2, 0.2, -5], [2, 10, 2], [40, 0])]),
    ("frontLegR", "body", [-1.2, 10, -4], None, [cube([-2.2, 0.2, -5], [2, 10, 2], [40, 0])]),
]

# The expression overlay: a flat plane just in front of the top two rows of her face.
FACE_BONES = [
    ("root", None, [0, 0, 0], None, []),
    ("body", "root", [0, 7, 1], None, []),
    ("head", "body", [0, 9, -9], None, [
        {"origin": [-2.5, 9, -12.05], "size": [5, 2, 0], "uv": {"north": {"uv": [0, 0], "uv_size": [5, 2]}}},
    ]),
]

# Face rows 0-1 of each vanilla tame coat, eyes painted over with the fur around them.
# Order matches the cat variants (and the jf:coat property): 0 white ... 10 jellie.
COATS = {
    "white": (["fdf9fb", "fdf9fb", "fdf9fb", "fdf9fb", "fdf9fb"], ["fdf9fb", "fdf9fb", "fdf9fb", "fdf9fb", "fdf9fb"]),
    "tuxedo": (["1c1827", "1c1827", "1c1827", "1c1827", "1c1827"], ["1c1827", "1c1827", "1c1827", "1c1827", "1c1827"]),
    "red": (["eaa939", "db7920", "db7920", "db7920", "eaa939"], ["eaa939", "db7920", "eaa939", "db7920", "eaa939"]),
    "siamese": (["55443b", "312720", "312720", "312720", "55443b"], ["55443b", "312720", "312720", "312720", "55443b"]),
    "british": (["a6a7a6", "bcbcbc", "bcbcbc", "bcbcbc", "a6a7a6"], ["a6a7a6", "bcbcbc", "bcbcbc", "bcbcbc", "a6a7a6"]),
    "calico": (["423b32", "4f4940", "db9c3e", "e5be84", "e5be84"], ["423b32", "4f4940", "ffffff", "e5be84", "e5be84"]),
    "persian": (["ffeacb", "ffeacb", "ffeacb", "ffeacb", "ffeacb"], ["ffeacb", "ffeacb", "ec8b7a", "ffeacb", "ffeacb"]),
    "ragdoll": (["897469", "dad6d2", "efefef", "dad6d2", "897469"], ["897469", "dad6d2", "ffffff", "dad6d2", "897469"]),
    "tabby": (["957256", "957256", "d1b7a1", "957256", "957256"], ["957256", "957256", "f3e9db", "957256", "957256"]),
    "black": (["161524", "161524", "161524", "161524", "161524"], ["161524", "161524", "161524", "161524", "161524"]),
    "jellie": (["515150", "4a4949", "ededed", "4a4949", "4a4949"], ["515150", "4a4949", "f9f5f8", "4a4949", "4a4949"]),
}


def ink_for(coat):
    # Dark coats get light outlines so the expressions still show up.
    return (236, 232, 240) if coat in ("tuxedo", "black", "siamese") else INK


# Eye centres line up with the vanilla eyes (face columns 0-1 and 3-4 -> x 8 and 32 of 40).
def happy(coat):
    c = Canvas(COATS[coat])
    c.line([(3, 13), (8, 8.5), (13, 13)], 2.2, ink_for(coat))   # ^ ^
    return c


def puppy(coat):
    c = Canvas(COATS[coat])
    c.ellipse(8, 9.5, 5.2, 5.6, INK)                           # big shiny eyes
    c.ellipse(8, 10, 3.8, 4.2, (96, 160, 64))
    c.ellipse(8, 10.4, 2.2, 3.0, (12, 10, 14))
    c.ellipse(6.2, 7.6, 1.7, 1.7, WHITE)
    c.ellipse(9.8, 12.2, 0.8, 0.8, WHITE)
    return c


def shocked(coat):
    c = Canvas(COATS[coat])
    c.ellipse(8, 9, 6.4, 6.4, INK)                             # huge round eyes
    c.ellipse(8, 9, 5.4, 5.4, WHITE)
    c.ellipse(8.8, 9.6, 1.8, 1.8, INK)                         # tiny pupils
    return c


def love(coat):
    c = Canvas(COATS[coat])
    c.heart(8, 9, 10, (226, 52, 84))
    c.ellipse(6, 6.6, 1.2, 1.0, (255, 190, 200))
    return c


def sleepy(coat):
    c = Canvas(COATS[coat])
    c.line([(3.5, 9.5), (6, 11.6), (8, 12), (10, 11.6), (12.5, 9.5)], 1.8, ink_for(coat))  # closed eyes
    return c


# Order matters: index = value of the jf:face entity property (0 = normal face, no overlay).
FACES = {"happy": happy, "puppy": puppy, "shocked": shocked, "love": love, "sleepy": sleepy}


def main():
    import make_jerry
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            make_jerry.geometry("geometry.jf_mochi", CAT_BONES, 64, 32),
            make_jerry.geometry("geometry.jf_mochi_face", FACE_BONES, 5, 2),  # textures are 8x (40x16)
        ],
    }
    (ROOT / "packs/resource_pack/models/entity/mochi.geo.json").write_text(json.dumps(geo, indent=2) + "\n")
    for coat in COATS:
        for name, draw in FACES.items():
            make_faces.write_rgba_png(ROOT / f"packs/resource_pack/textures/entity/mochi/{coat}_{name}.png", draw(coat).pixels())
    print(f"Mochi's model and faces written ({len(COATS)} coats x {len(FACES)} faces).")


if __name__ == "__main__":
    main()
