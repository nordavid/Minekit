"""Draws the Minekit icon (32x32 pixel art, scaled with hard edges). Usage: python tools/make_icon.py OUT.png SCALE"""
import struct
import sys
import zlib
from pathlib import Path

N = 32


def hex_color(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


CLEAR = (0, 0, 0, 0)
TILE = hex_color(0x1B1F26)
TILE_TOP = hex_color(0x232933)
TILE_BORDER = hex_color(0x343A44)
TILE_EDGE = hex_color(0x2A2F38)
TEAL = hex_color(0x4CC9A6)
TEAL_LIGHT = hex_color(0x8BEBD0)
TEAL_DARK = hex_color(0x2E8C73)
SHADOW = hex_color(0x101317)
SOFT_SHADOW = hex_color(0x15181E)
AMBER = hex_color(0xE8B75A)
AMBER_LIGHT = hex_color(0xF6D48E)


def inside_tile(x, y, radius=4):
    """Rounded square covering the whole canvas with a margin of one pixel."""
    lo, hi = 1, N - 2
    if not (lo <= x <= hi and lo <= y <= hi):
        return False
    cx = min(max(x, lo + radius), hi - radius)
    cy = min(max(y, lo + radius), hi - radius)
    return (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius + 1


def m_pixels():
    pixels = set()
    top, bottom = 8, 23
    for y in range(top, bottom + 1):
        for x in range(6, 10):
            pixels.add((x, y))
        for x in range(22, 26):
            pixels.add((x, y))
        d = y - top
        if d <= 9:
            for x in range(6 + d, 6 + d + 4):
                if x <= 15:
                    pixels.add((x, y))
            for x in range(25 - d - 3, 25 - d + 1):
                if x >= 16:
                    pixels.add((x, y))
    return pixels


def draw():
    canvas = [[CLEAR for _ in range(N)] for _ in range(N)]
    for y in range(N):
        for x in range(N):
            if inside_tile(x, y):
                edge = not all(inside_tile(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if edge:
                    canvas[y][x] = TILE_BORDER if y <= 2 else TILE_EDGE
                elif y <= 4:
                    canvas[y][x] = TILE_TOP
                else:
                    canvas[y][x] = TILE
    m = m_pixels()
    # Drop shadow down and to the right.
    for (x, y) in m:
        for (dx, dy), color in (((2, 2), SOFT_SHADOW), ((1, 1), SHADOW)):
            px, py = x + dx, y + dy
            if (px, py) not in m and inside_tile(px, py) and canvas[py][px] != SHADOW:
                canvas[py][px] = color
    for (x, y) in m:
        if (x, y - 1) not in m:
            color = TEAL_LIGHT
        elif (x, y + 1) not in m or (x + 1, y) not in m:
            color = TEAL_DARK
        else:
            color = TEAL
        canvas[y][x] = color
    # A small amber spark: the "kit" that helps.
    for (x, y), color in {(26, 4): AMBER, (25, 5): AMBER, (26, 5): AMBER_LIGHT, (27, 5): AMBER, (26, 6): AMBER}.items():
        canvas[y][x] = color
    return canvas


def write_png(path, canvas, scale):
    size = N * scale
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        row = canvas[y // scale]
        for x in range(size):
            raw.extend(row[x // scale])

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    Path(path).write_bytes(png)


if __name__ == "__main__":
    canvas = draw()
    for target, scale in zip(sys.argv[1::2], map(int, sys.argv[2::2])):
        write_png(target, canvas, scale)
        print(target, N * scale)
