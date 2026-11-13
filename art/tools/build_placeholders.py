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

# Per-frame head offset (y) of the idle animation. MUST match SpriteCatalog.HEAD_BOB in Kotlin.
IDLE_BOB = [0, 1, 0, 0]
IDLE_BLINK = [False, False, False, True]

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

    idle = [hero_frame(b, bl) for b, bl in zip(IDLE_BOB, IDLE_BLINK)]
    rest = [hero_frame(1, False, closed=True)]
    body_rows = [[split(f, lambda ch: ch not in CLOAK_CHARS) for f in idle], [split(f, lambda ch: ch not in CLOAK_CHARS) for f in rest]]
    write_png(os.path.join(OUT, "px_hero_body.png"), sheet(body_rows, 32, 32), colors)

    for item, (light, mid, dark) in CLOAKS.items():
        cc = dict(colors, c=light, C=mid, D=dark)
        rows = [[split(f, lambda ch: ch in CLOAK_CHARS) for f in idle], [split(f, lambda ch: ch in CLOAK_CHARS) for f in rest]]
        write_png(os.path.join(OUT, f"px_cloak_{item.removeprefix('color_')}.png"), sheet(rows, 32, 32), cc)

    for item, rows in HATS.items():
        write_png(os.path.join(OUT, f"px_{item}.png"), sheet([[grid(rows, 32, 32)]], 32, 32), colors)

    write_png(os.path.join(OUT, "px_coin.png"), sheet([coin_frames()], 16, 16), colors)

    # Palette exports (base palette + cloak ramps).
    entries = [(v[0], v[1]) for v in PALETTE.values()]
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
