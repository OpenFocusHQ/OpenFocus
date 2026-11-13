#!/usr/bin/env python3
"""
Generates OpenFocus's *placeholder* pixel-art sprite sheets from the text grids below.

  python3 art/tools/build_placeholders.py

Writes PNG sheets to app/src/main/res/drawable-nodpi/ and the shared palette to art/palette.hex / .gpl.
No third-party dependencies (pure-Python PNG writer). Output is CC0.

Real art should replace these PNGs directly (same file names, same grid layout) and be credited in
art/CREDITS.md. Once a sheet is hand-drawn, delete its generator entry so it isn't overwritten.

Grid conventions
  * Each row string is CENTERED in the frame, so symmetric shapes only need their middle part.
  * '.' is transparent. Every other character is a palette key (see PALETTE).
  * Sheets are fixed grids: one row per animation, frames left-to-right.
"""
import os
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")

# --------------------------------------------------------------------------------------------
# Palette: the single source of truth. Names are documented in art/palette.gpl.
# --------------------------------------------------------------------------------------------
PALETTE = {
    "o": ("2B2440", "outline"),
    "w": ("FFFFFF", "white"),
    "s": ("FFD9B8", "skin light"),
    "S": ("E8B48F", "skin shadow"),
    "p": ("F7A1A1", "blush"),
    "h": ("6B4226", "hair"),
    "H": ("4E2E19", "hair shadow"),
    "b": ("4A3A5C", "boots"),
    "g": ("FFC857", "gold"),
    "G": ("E0A526", "gold mid"),
    "y": ("FFE08A", "gold light"),
    "r": ("F0506E", "ruby"),
    "B": ("4DA3F0", "sky blue"),
    "R": ("E85D75", "rose"),
    "q": ("B8405A", "rose dark"),
    "V": ("5B4BC4", "violet deep"),
    "v": ("7C6CF2", "violet"),
    # Backdrops
    "L": ("E4DFFF", "lavender light"),
    "l": ("CFC7FF", "lavender"),
    "A": ("FFC9A3", "peach"),
    "K": ("FF9A6B", "sunset orange"),
    "k": ("7A4E7A", "dusk ground"),
    "f": ("BDEBC8", "mint sky"),
    "F": ("3E8E5E", "leaf green"),
    "T": ("235C3B", "pine"),
    "n": ("A9DDF7", "day sky"),
    "N": ("2F6FB5", "sea"),
    "m": ("7FC8F8", "wave"),
    "u": ("F2D8A7", "sand"),
    "z": ("1E1B4B", "night"),
    "Z": ("2E2A6B", "night light"),
}

# Skin ramps (light, shadow, blush). Template chars: s, S, p.
SKIN_TONES = {
    "light": ("FFD9B8", "E8B48F", "F7A1A1"),
    "medium": ("D99A6C", "B47A50", "E0806E"),
    "deep": ("8D5A3B", "6E4329", "A85A4E"),
}

# Cloak colour ramps (light, mid, dark), keyed by ShopItem id. Template chars: c, C, D.
CLOAKS = {
    "color_violet": ("A397FF", "7C6CF2", "5545C0"),
    "color_teal": ("5FD6C6", "2BB3A3", "1C7F74"),
    "color_ember": ("FF9A7E", "F06A4D", "B8442D"),
    "color_gold": ("FFD66B", "E0A526", "A8741A"),
}

# --------------------------------------------------------------------------------------------
# Hero template, 32x32. Rows 0-5 and 31 are empty (headroom for hats).
# --------------------------------------------------------------------------------------------
HERO = {
    6: "oooooooooo",
    7: "ohhhhhhhhhho",
    8: "ohhhhhhhhhhhho",
    9: "ohHhhhhhhhhHho",
    10: "ohssssssssssho",
    11: "osssssssssssso",
    12: "ossoossssoosso",
    13: "ossoossssoosso",
    14: "osppssssssppso",
    15: "osssssoossssso",
    16: "oSssssssssssSo",
    17: "ooSSSSSSSSoo",
    18: "osso",
    19: "ooCCCCCCCCCCoo",
    20: "ocCCCCggCCCCDo",
    21: "ocCCCCCCCCCCCCDo",
    22: "ocCCCCCCCCCCCCDo",
    23: "oSocCCCCCCCCCCDoSo",
    24: "oocCCCCCCCCCCDoo",
    25: "ocCCCCCCCCCCCCDo",
    26: "ocCCCCCCCCCCCCCCDo",
    27: "oDDDDDDDDDDDDDDDDo",
    28: "obbbo..obbbo",
    29: "obbbo..obbbo",
    30: "ooooo..ooooo",
}
CLOAK_CHARS = set("cCDg")

# Per-frame head offset (y). MUST match HEAD_* lists in SpriteCatalog.kt.
IDLE_BOB = [0, 1, 0, 0]
IDLE_BLINK = [False, False, False, True]
CELEBRATE_JUMP = [0, 1]  # frame 1 jumps up one pixel -> head anchor -1

# Raised arm for the celebrate pose, left side as (x, y, char); the right side is mirrored.
RAISED_ARM = [
    (6, 13, "o"), (7, 13, "o"), (8, 13, "o"),
    (6, 14, "o"), (7, 14, "S"), (8, 14, "o"),
    (6, 15, "o"), (7, 15, "S"), (8, 15, "o"),
    (6, 16, "o"), (7, 16, "c"), (8, 16, "o"),
    (6, 17, "o"), (7, 17, "C"), (8, 17, "o"),
    (7, 18, "o"), (8, 18, "C"), (9, 18, "o"),
]

# --------------------------------------------------------------------------------------------
# Hats, 32x32 single-frame overlays aligned to the hero's frame-0 head.
# --------------------------------------------------------------------------------------------
HATS = {
    "hat_party": {
        1: "yy", 2: "oyyo", 3: "oBBo", 4: "owwwwo", 5: "oBBBBBBo", 6: "oBBBBBBBBo", 7: "owwwwwwwwwwo",
    },
    "hat_beanie": {
        2: "owwo", 3: "oooooooo", 4: "oRRRRRRRRo", 5: "oRRRRRRRRRRo", 6: "oRRRRRRRRRRRRo",
        7: "oqqqqqqqqqqqqo", 8: "oooooooooooooo",
    },
    "hat_wizard": {
        0: "oo", 1: "oVVo", 2: "oVVo", 3: "oVyVVo", 4: "oVVVVo", 5: "oVVVVVVo", 6: "oVVVVVVVVo",
        7: "oooooooooooooooooooo", 8: "ovvvvvvvvvvvvvvvvvvo", 9: "oooooooooooooooooooo",
    },
    "hat_crown": {
        2: "o....oo....o", 3: "oGo.oGGo.oGo", 4: "oGGooGGooGGo", 5: "oGGGGGGGGGGo",
        6: "oGrGGyyGGrGo", 7: "oGGGGGGGGGGo", 8: "oooooooooooo",
    },
}


def grid(rows, w, h):
    g = [["."] * w for _ in range(h)]
    for y, content in rows.items():
        assert len(content) % 2 == 0, f"row {y} has odd width: {content!r}"
        x0 = (w - len(content)) // 2
        for i, ch in enumerate(content):
            g[y][x0 + i] = ch
    return g


def hero_frame(bob, blink, closed=False):
    g = grid(HERO, 32, 32)
    if blink or closed:
        for y in (12, 13):
            g[y] = [("s" if ch == "o" and 11 <= x <= 20 else ch) for x, ch in enumerate(g[y])]
        g[13] = [("o" if ch == "s" and x in (11, 12, 19, 20) else ch) for x, ch in enumerate(g[13])]
    if bob:
        # Head sinks one pixel into the neck; feet and cloak hem stay planted.
        for y in range(18, 6, -1):
            g[y] = list(g[y - 1])
        g[6] = ["."] * 32
    return g


def celebrate_frame(jump):
    g = hero_frame(0, False)
    g[23] = list(grid({23: "ocCCCCCCCCCCCCDo"}, 32, 32)[23])  # lower hands removed
    for x, y, ch in RAISED_ARM:
        g[y][x] = ch
        g[y][31 - x] = ch
    for x in (14, 17):  # wide open smile
        g[15][x] = "o"
    g[16][15] = g[16][16] = "q"
    if jump:
        g = g[1:] + [["."] * 32]
    return g


def backdrop(kind, frame=0):
    """48x48 round backdrop with a pixel outline. Procedural so palettes stay consistent."""
    n = 48
    c = (n - 1) / 2
    inside = [[(x - c) ** 2 + (y - c) ** 2 <= 23.2 ** 2 for x in range(n)] for y in range(n)]
    g = [["."] * n for _ in range(n)]

    def fill(x, y):
        if kind == "default":
            return "L" if y < 30 else "l"
        if kind == "sunrise":
            if y >= 38:
                return "k"
            if (x - 36) ** 2 + (y - 16) ** 2 <= 30:
                return "y"
            return "A" if y < 22 else "K"
        if kind == "forest":
            if y >= 40:
                return "T"
            for tx, top in ((8, 20), (20, 14), (38, 18), (46, 24)):
                half = (y - top) // 2
                if top <= y <= 40 and abs(x - tx) <= half:
                    return "F" if x - tx == -half and y % 3 else "T"  # lit left edge
            return "f" if y < 30 else "F"
        if kind == "ocean":
            if y >= 41:
                return "u"
            if y >= 30:
                return "m" if (y in (32, 36) and (x + 3 * y) % 9 < 3) else "N"
            return "n"
        if kind == "night":
            if (x - 13) ** 2 + (y - 13) ** 2 <= 20 and not ((x - 15) ** 2 + (y - 11) ** 2 <= 18):
                return "y"
            stars = [(30, 8), (38, 14), (24, 18), (40, 26), (8, 28), (33, 33)]
            for i, (sx, sy) in enumerate(stars):
                if (x, y) == (sx, sy) and (i + frame) % 2 == 0:
                    return "w"
                if frame % 2 == i % 2 and abs(x - sx) + abs(y - sy) == 1 and i < 3:
                    return "Z"
            return "z" if y < 34 else "Z"
        raise ValueError(kind)

    for y in range(n):
        for x in range(n):
            if not inside[y][x]:
                continue
            edge = any(not (0 <= x + dx < n and 0 <= y + dy < n and inside[y + dy][x + dx])
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            g[y][x] = "o" if edge else fill(x, y)
    return g


BACKDROPS = {"default": 1, "sunrise": 1, "forest": 1, "ocean": 1, "night": 2}


def split(g, keep):
    return [[ch if keep(ch) else "." for ch in row] for row in g]


def sheet(frames_by_row, fw, fh):
    rows = len(frames_by_row)
    cols = max(len(r) for r in frames_by_row)
    out = [["."] * (fw * cols) for _ in range(fh * rows)]
    for r, frames in enumerate(frames_by_row):
        for c, f in enumerate(frames):
            for y in range(fh):
                for x in range(fw):
                    out[r * fh + y][c * fw + x] = f[y][x]
    return out


def write_png(path, pixels, colors):
    h, w = len(pixels), len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for ch in row:
            if ch == ".":
                raw += b"\x00\x00\x00\x00"
            else:
                hexv = colors[ch]
                raw += bytes.fromhex(hexv) + b"\xff"

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    print("wrote", os.path.relpath(path, ROOT), f"{w}x{h}")


def coin_frames():
    widths = [12, 10, 6, 2, 6, 10]
    frames = []
    for w in widths:
        rows = {}
        for y in range(2, 14):
            dy = abs(y - 7.5) / 6.0
            half = max(1, int(round((w / 2) * (1 - dy ** 2) ** 0.5)))
            if y in (2, 13):
                rows[y] = "o" * (2 * half)
            elif half == 1:
                rows[y] = "oo"
            else:
                fill = "g" * (2 * half - 2)
                if w >= 10 and 5 <= y <= 10:  # embossed centre
                    fill = "g" + "G" * (2 * half - 4) + "g"
                if y == 4 and w >= 6:
                    fill = "y" + fill[1:]
                rows[y] = "o" + fill + "o"
        frames.append(grid(rows, 16, 16))
    return frames


def main():
    os.makedirs(OUT, exist_ok=True)
    colors = {k: v[0] for k, v in PALETTE.items()}

    # Sheet rows: 0 idle (4 frames), 1 rest (1), 2 celebrate (2).
    poses = [
        [hero_frame(b, bl) for b, bl in zip(IDLE_BOB, IDLE_BLINK)],
        [hero_frame(1, False, closed=True)],
        [celebrate_frame(j) for j in CELEBRATE_JUMP],
    ]
    is_body = lambda ch: ch not in CLOAK_CHARS
    is_cloak = lambda ch: ch in CLOAK_CHARS
    for tone, (light, shadow, blush) in SKIN_TONES.items():
        tc = dict(colors, s=light, S=shadow, p=blush)
        write_png(os.path.join(OUT, f"px_hero_body_{tone}.png"),
                  sheet([[split(f, is_body) for f in row] for row in poses], 32, 32), tc)
    for item, (light, mid, dark) in CLOAKS.items():
        cc = dict(colors, c=light, C=mid, D=dark)
        write_png(os.path.join(OUT, f"px_cloak_{item.removeprefix('color_')}.png"),
                  sheet([[split(f, is_cloak) for f in row] for row in poses], 32, 32), cc)

    for kind, frames in BACKDROPS.items():
        write_png(os.path.join(OUT, f"px_bg_{kind}.png"), sheet([[backdrop(kind, f) for f in range(frames)]], 48, 48), colors)

    for item, rows in HATS.items():
        write_png(os.path.join(OUT, f"px_{item}.png"), sheet([[grid(rows, 32, 32)]], 32, 32), colors)

    write_png(os.path.join(OUT, "px_coin.png"), sheet([coin_frames()], 16, 16), colors)

    # Palette exports (base palette + cloak ramps).
    entries = [(v[0], v[1]) for v in PALETTE.values()]
    for tone, ramp in SKIN_TONES.items():
        for shade, hexv in zip(("light", "shadow", "blush"), ramp):
            entries.append((hexv, f"skin {tone} {shade}"))
    for item, ramp in CLOAKS.items():
        for shade, hexv in zip(("light", "mid", "dark"), ramp):
            entries.append((hexv, f"{item.removeprefix('color_')} cloak {shade}"))
    with open(os.path.join(ROOT, "art", "palette.hex"), "w") as f:
        f.write("\n".join(h.lower() for h, _ in entries) + "\n")
    with open(os.path.join(ROOT, "art", "palette.gpl"), "w") as f:
        f.write("GIMP Palette\nName: OpenFocus\nColumns: 8\n#\n")
        for h, name in entries:
            r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
            f.write(f"{r:3d} {g:3d} {b:3d}\t{name}\n")
    print(f"palette: {len(entries)} colours")


if __name__ == "__main__":
    main()
