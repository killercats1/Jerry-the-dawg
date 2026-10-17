"""Draws Jerry's face expressions (the overlays that sit over the vanilla wolf's eyes).

Run from the repo root:  python3 tools/make_faces.py   (make_jerry.py also runs it)
Writes packs/resource_pack/textures/entity/jf_jerry_face_<name>.png

Each overlay covers the top 6x4 pixels of the wolf's face (brows, eyes, top of the muzzle) at
8x resolution (48x32), so the eyes can be smooth and detailed like a cartoon. The background
repaints the vanilla tame wolf's face colours so the normal eyes are hidden underneath.
Everything is left/right symmetric, so it looks right whichever way the game maps the face.
Pure standard library: shapes are drawn 4x supersampled and averaged down for smooth edges.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SS = 4         # supersampling

# Vanilla tame wolf face, per face-pixel (columns 0-5, rows 0-3). Rows 1-2, columns 1 and 4
# are where the eyes and brows were; they get plain fur so the expressions can draw there.
FUR = [
    ["c1bebe", "d3cfcf", "dddadb", "dddadb", "d3cfcf", "c1bebe"],
    ["c1bebe", "d3cfcf", "d3cfcf", "d3cfcf", "d3cfcf", "cac7c8"],
    ["ffffff", "d3cfcf", "a78f7e", "a78f7e", "d3cfcf", "ffffff"],
    ["a78f7e", "8c6f52", "9a8c88", "9a8c88", "8c6f52", "a78f7e"],
]

INK = (30, 27, 32)
WHITE = (255, 255, 255)


def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Canvas:
    # fur: rows of face-pixel colours (hex) painted as the background, 8x8 final pixels each.
    def __init__(self, fur=FUR):
        self.w, self.h = len(fur[0]) * 8, len(fur) * 8
        self.px = [[(0, 0, 0)] * (self.w * SS) for _ in range(self.h * SS)]
        for row, cols in enumerate(fur):
            for col, colour in enumerate(cols):
                self.rect(col * 8, row * 8, col * 8 + 8, row * 8 + 8, hex_rgb(colour))

    # All coordinates are in final (48x32) pixels; shapes are mirrored with mirror=True.
    def rect(self, x0, y0, x1, y1, colour):
        for y in range(int(y0 * SS), int(y1 * SS)):
            for x in range(int(x0 * SS), int(x1 * SS)):
                self.px[y][x] = colour

    def ellipse(self, cx, cy, rx, ry, colour, mirror=True):
        for x_c in ([cx, self.w - cx] if mirror else [cx]):
            for y in range(max(0, int((cy - ry) * SS)), min(self.h * SS, int((cy + ry) * SS) + 1)):
                for x in range(max(0, int((x_c - rx) * SS)), min(self.w * SS, int((x_c + rx) * SS) + 1)):
                    dx, dy = (x + 0.5) / SS - x_c, (y + 0.5) / SS - cy
                    if (dx / rx) ** 2 + (dy / ry) ** 2 <= 1:
                        self.px[y][x] = colour

    def line(self, points, width, colour, mirror=True):
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            steps = max(2, int(math.hypot(x1 - x0, y1 - y0) * SS * 2))
            for i in range(steps + 1):
                t = i / steps
                self.ellipse(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, width / 2, width / 2, colour, mirror)

    def heart(self, cx, cy, size, colour):
        r = size * 0.28
        self.ellipse(cx - r * 0.95, cy - r * 0.4, r, r, colour)
        self.ellipse(cx + r * 0.95, cy - r * 0.4, r, r, colour)
        for y in range(int((cy - r * 0.4) * SS), int((cy + size * 0.55) * SS)):
            fy = (y + 0.5) / SS
            half = (cy + size * 0.55 - fy) / (size * 0.95) * size * 0.98
            for x_c in (cx, self.w - cx):
                self.rect(x_c - half, fy, x_c + half, fy + 1 / SS, colour)

    def pixels(self):
        out = []
        for y in range(self.h):
            row = []
            for x in range(self.w):
                block = [self.px[y * SS + j][x * SS + i] for j in range(SS) for i in range(SS)]
                row.append(tuple(sum(c[k] for c in block) // len(block) for k in range(3)) + (255,))
            out.append(row)
        return out


# ---------------------------------------------------------------- the expressions
# Eye centres line up with the vanilla eyes (face columns 1 and 4 -> x 12 and 36).

def happy():
    c = Canvas()
    c.line([(6, 21), (12, 15), (18, 21)], 2.4, INK)            # ^ ^
    c.ellipse(4, 25.5, 3.6, 2.2, (247, 150, 168))              # blush
    return c


def puppy():
    c = Canvas()
    c.line([(5, 10), (16, 6.5)], 1.6, (110, 100, 100))         # worried, raised brows
    c.ellipse(12, 18, 6.6, 6.8, INK)                           # big shiny eyes
    c.ellipse(12, 18.6, 4.6, 4.8, (70, 46, 36))
    c.ellipse(12, 19, 2.8, 2.9, (12, 10, 14))
    c.ellipse(9.4, 15.2, 2.3, 2.3, WHITE)                      # sparkles
    c.ellipse(14.6, 21, 1.1, 1.1, WHITE)
    return c


def shocked():
    c = Canvas()
    c.line([(3, 7), (7, 4.5), (12, 5.5), (17, 3.5)], 1.4, INK)  # wobbly worried brows
    c.ellipse(12, 17, 8.8, 7.4, INK)                           # huge cartoon eyes
    c.ellipse(12, 17, 7.6, 6.2, WHITE)
    c.ellipse(13.6, 18.4, 2.7, 2.7, INK)                       # tiny pupils
    c.ellipse(12.8, 17.4, 0.8, 0.8, WHITE)
    for angle in (-115, -90, -65):                             # lashes
        a = math.radians(angle)
        x0, y0 = 12 + 8.6 * math.cos(a), 17 + 7.2 * math.sin(a)
        c.line([(x0, y0), (x0 + 2.6 * math.cos(a), y0 + 2.6 * math.sin(a))], 1.1, INK)
    return c


def love():
    c = Canvas()
    c.heart(12, 18, 13, (226, 52, 84))
    c.ellipse(9, 15, 1.5, 1.2, (255, 190, 200))               # shine
    return c


def sleepy():
    c = Canvas()
    c.line([(6, 18), (9, 20.6), (12, 21.3), (15, 20.6), (18, 18)], 1.9, INK)  # closed eyes
    c.line([(7, 21.2), (6, 23)], 1.0, INK)                    # little lashes
    c.line([(11, 22), (11, 23.8)], 1.0, INK)
    return c


# Order matters: index = value of the jf:face entity property (0 = normal face, no overlay).
FACES = {"happy": happy, "puppy": puppy, "shocked": shocked, "love": love, "sleepy": sleepy}


def write_rgba_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    for name, draw in FACES.items():
        write_rgba_png(ROOT / f"packs/resource_pack/textures/entity/jf_jerry_face_{name}.png", draw().pixels())
    print(f"Jerry's faces written: {', '.join(FACES)}")


if __name__ == "__main__":
    main()
