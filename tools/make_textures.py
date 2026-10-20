"""Generates the pixel-art PNGs for the packs (item icons + pack icons).

Run from the repo root:  python3 tools/make_textures.py
Uses only the standard library, so it works anywhere Python 3 does.
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

PALETTE = {
    ".": (0, 0, 0, 0),
    "k": (43, 43, 43, 255),      # outline
    "s": (200, 204, 212, 255),   # silver
    "S": (138, 143, 153, 255),   # dark silver
    "w": (255, 255, 255, 255),   # white
    "r": (192, 57, 43, 255),     # red
    "y": (242, 197, 138, 255),   # biscuit highlight
    "t": (217, 160, 102, 255),   # biscuit
    "T": (168, 107, 50, 255),    # biscuit shadow
    "b": (139, 90, 43, 255),     # wood
    "B": (92, 58, 26, 255),      # dark wood
    "v": (212, 225, 87, 255),    # tennis ball
    "V": (158, 157, 36, 255),    # tennis ball shadow
    "g": (217, 217, 217, 255),   # fur
    "G": (166, 166, 166, 255),   # dark fur
    "n": (26, 26, 26, 255),      # nose
    "p": (255, 143, 163, 255),   # tongue
    "Y": (255, 211, 64, 255),    # gold tag
}

WHISTLE = [
    "................",
    "................",
    "................",
    "..rrr...........",
    ".r...r..........",
    ".r....r.........",
    "..r....kkkkkkkk.",
    "...r..kswwssssSk",
    "....rksssssssSSk",
    ".....ksssssssSSk",
    ".....kSsssssSSk.",
    "......kSSSSSkk..",
    ".......kkkkk....",
    "................",
    "................",
    "................",
]

BRUSH = [
    "................",
    ".........kkkkk..",
    "........kbbbbbk.",
    ".......kbwbwbwbk",
    ".......kbbwbwbbk",
    ".......kbwbwbwbk",
    ".......kbbwbwbbk",
    "........kbbbbbk.",
    ".......kBkkkkk..",
    "......kBBk......",
    ".....kBBk.......",
    "....kBBk........",
    "...kBBk.........",
    "..kBBk..........",
    "..kkk...........",
    "................",
]

BALL = [
    "................",
    "................",
    "......kkkk......",
    "....kkvvvvkk....",
    "...kvwvvvvvvk...",
    "...kvvwvvvvVk...",
    "..kvvvvwvvvvVk..",
    "..kvvvvvwwvvVk..",
    "..kvvvvvvvwvVk..",
    "..kvvvvvvvvwVk..",
    "...kvvvvvvvwk...",
    "...kVvvvvvVVk...",
    "....kkVVVVkk....",
    "......kkkk......",
    "................",
    "................",
]

TREAT = [
    "................",
    "................",
    "................",
    ".kkk........kkk.",
    "kyttk......kyttk",
    "kytttkkkkkkktttk",
    ".kttyyyyyyyyttk.",
    ".ktttttttttttTk.",
    "kttTkkkkkkkkTttk",
    "kttTk......kTTtk",
    ".kkk........kkk.",
    "................",
    "................",
    "................",
    "................",
    "................",
]

TALK = [
    "................",
    "................",
    "..kkkkkkkkkkkk..",
    ".kwwwwwwwwwwwwk.",
    ".kwwwwwwwwwwwwk.",
    ".kwnnwwnnwwnnwk.",
    ".kwnnwwnnwwnnwk.",
    ".kwwwwwwwwwwwwk.",
    ".kwwwwwwwwwwwwk.",
    "..kkkkwwkkkkkk..",
    "......kwk.......",
    ".....kwk........",
    ".....kk.........",
    "................",
    "................",
    "................",
]

DOG_FACE = [
    "................",
    "..kk........kk..",
    ".kGgk......kgGk.",
    ".kGggkkkkkkggGk.",
    ".kggggggggggggk.",
    "kggggggggggggggk",
    "kggkkggggggkkggk",
    "kggkwggggggkwggk",
    "kggggggggggggggk",
    "kgggggwwwwgggggk",
    ".kgggwwnnwwgggk.",
    ".kggwwwwwwwwggk.",
    "..kgwwwppwwwgk..",
    "..krrrrrrrrrrk..",
    "...kkkkYYkkkk...",
    "................",
]

# The "Z" that floats up when he snores (particles/jf_zzz.json).
ZZZ = [
    "kkkkkkkk",
    "kwwwwwwk",
    "kkkkwwkk",
    "..kwwk..",
    ".kwwk...",
    "kwwkkkkk",
    "kwwwwwwk",
    "kkkkkkkk",
]


def write_png(path, grid, scale=1, background=None):
    for row in grid:
        assert len(row) == len(grid[0]), f"ragged row in {path.name}: {row!r}"
    height, width = len(grid) * scale, len(grid[0]) * scale
    raw = bytearray()
    for row in grid:
        line = bytearray([0])  # PNG filter type 0
        for ch in row:
            rgba = PALETTE[ch]
            if rgba[3] == 0 and background:
                rgba = background
            line += bytes(rgba) * scale
        raw += bytes(line) * scale

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def main():
    items = ROOT / "packs/resource_pack/textures/items"
    write_png(items / "dog_whistle.png", WHISTLE)
    write_png(items / "pet_brush.png", BRUSH)
    write_png(items / "tennis_ball.png", BALL)
    write_png(items / "talk.png", TALK)
    write_png(items / "dog_treat.png", TREAT)
    write_png(ROOT / "packs/resource_pack/textures/particle/jf_zzz.png", ZZZ)
    for pack in ("behavior_pack", "resource_pack"):
        write_png(ROOT / "packs" / pack / "pack_icon.png", DOG_FACE, scale=8, background=(255, 211, 110, 255))
    print("Textures written.")


if __name__ == "__main__":
    main()
