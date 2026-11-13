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

# --------------------------------------------------------------------------------------------
# Goal "ascent" scene, 160x112. One sheet per goal colour; row 0 = climbing, row 1 = summit reached.
# --------------------------------------------------------------------------------------------
GOAL_COLORS = ["7C6CF2", "2BB3A3", "F06A4D", "E0A526", "4DA3F0", "E85D75", "3E8E5E", "9B59B6"]  # = PaletteColors (Theme.kt)
SCENE_W, SCENE_H = 160, 112
PEAK = (88, 10)
# Where the climber's feet go, base to summit. MUST match AscentTrail.POINTS in Kotlin.
TRAIL = [(30, 104), (118, 92), (52, 78), (112, 62), (66, 46), (98, 32), (88, 18)]


def mix(hexv, other, t):
    a = [int(hexv[i:i + 2], 16) for i in (0, 2, 4)]
    b = [int(other[i:i + 2], 16) for i in (0, 2, 4)]
    return "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(a, b))


def scene_colors(base):
    """Per-scene ramp: 1/2 sky, 3 back range, 4 lit face, 5 shaded face."""
    return {
        "1": mix(base, "FFFFFF", 0.86), "2": mix(base, "FFFFFF", 0.74),
        "3": mix(base, "FFFFFF", 0.45), "4": base, "5": mix(base, "000000", 0.32),
    }


def peak_bounds(y):
    xl = 6 + (111 - y) * 82 / 101
    xr = 154 - (111 - y) * 66 / 101
    return xl, xr


def line_points(a, b):
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0))
    return [(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)) for i in range(n + 1)]


def ascent_scene(summit):
    import math
    W, H = SCENE_W, SCENE_H
    g = [["." for _ in range(W)] for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if summit:
                c = "A" if y < 40 else "K"
                if y in (38, 39) and (x + y) % 2 == 0:
                    c = "K"
            else:
                c = "1" if y < 44 else "2"
                if y in (42, 43) and (x + y) % 2 == 0:
                    c = "2"
            g[y][x] = c
    if summit:
        for y in range(H):
            for x in range(W):
                if (x - 132) ** 2 + (y - 30) ** 2 <= 80:
                    g[y][x] = "y"
    else:
        for cx, cy, rx in ((28, 22, 12), (136, 30, 9)):
            for y in range(cy - 4, cy + 4):
                for x in range(cx - rx, cx + rx):
                    if ((x - cx) / rx) ** 2 + ((y - cy) / 4) ** 2 <= 1 and not (y == cy + 3 and (x + y) % 2):
                        g[y][x] = "w"
    # Back range
    for x in range(W):
        top = int(62 + 9 * math.sin(x / 13) + 5 * math.sin(x / 5.3 + 1))
        for y in range(top, H):
            g[y][x] = "o" if y == top else "3"
    # Main peak with outline, lit left face, shaded right face, snow cap
    inside = [[False] * W for _ in range(H)]
    for y in range(PEAK[1], H):
        xl, xr = peak_bounds(y)
        for x in range(W):
            inside[y][x] = xl <= x <= xr
    for y in range(PEAK[1], H):
        ridge = PEAK[0] + (y - PEAK[1]) * 0.12
        for x in range(W):
            if not inside[y][x]:
                continue
            edge = not (inside[y - 1][x] and inside[y][max(0, x - 1)] and inside[y][min(W - 1, x + 1)])
            snowline = 27 + 3 * math.sin(x / 3.1)
            if edge:
                c = "o"
            elif y < snowline:
                c = "w" if x <= ridge else "L"
            else:
                c = "4" if x <= ridge else "5"
            g[y][x] = c
    # Dashed trail
    step = 0
    for a, b in zip(TRAIL, TRAIL[1:]):
        for (x, y) in line_points(a, b)[:-1]:
            if step % 4 < 2:
                g[y][x] = "h" if g[y][x] in ("w", "L") else "u"
            step += 1
    for (x, y) in TRAIL:
        assert inside[y][x], f"trail point {(x, y)} is off the mountain"
    return g


# --------------------------------------------------------------------------------------------
# Achievement badges: 24x24 medal (bronze / silver / gold) + ribbon + 12x12 glyph.
# Order MUST match org.openfocus.app.core.Achievements.all (checked by a test).
# --------------------------------------------------------------------------------------------
TIERS = {  # rim, face, shadow
    "bronze": ("F0B57E", "C9844A", "8A5226"),
    "silver": ("F4F4FA", "C9CBDA", "8E91A6"),
    "gold": ("FFE08A", "FFC857", "C98E1C"),
}

DIGITS = {
    "0": ["ooo", "o.o", "o.o", "o.o", "ooo"], "1": [".o.", "oo.", ".o.", ".o.", "ooo"],
    "2": ["ooo", "..o", "ooo", "o..", "ooo"], "3": ["ooo", "..o", ".oo", "..o", "ooo"],
    "5": ["ooo", "o..", "ooo", "..o", "ooo"],
}
MINI = {  # 5x5 icons shown above a number
    "check": ["....o", "...oo", "o.oo.", "ooo..", ".o..."],
    "flame": ["..o..", ".oKo.", ".oKKo", "oKyKo", ".ooo."],
    "clock": [".ooo.", "o.o.o", "o.ooo", "o...o", ".ooo."],
    "arrow": ["..o..", ".ooo.", "o.o.o", "..o..", "..o.."],
    "peak": ["..o..", ".owo.", ".oTo.", "oTTTo", "ooooo"],
}


def numbered(icon, number):
    g = [["."] * 12 for _ in range(12)]
    for y, row in enumerate(MINI[icon]):
        for x, ch in enumerate(row):
            g[y][3 + x] = ch if ch != "." else g[y][3 + x]
    text = [DIGITS[d] for d in number]
    width = 4 * len(text) - 1
    x0 = (12 - width) // 2
    for i, d in enumerate(text):
        for y in range(5):
            for x in range(3):
                if d[y][x] != ".":
                    g[6 + y][x0 + 4 * i + x] = "o"
    return ["".join(r) for r in g]


def shape(fn):
    """12x12 glyph from a predicate fn(x, y) -> char or None."""
    return ["".join(fn(x, y) or "." for x in range(12)) for y in range(12)]


def star(fill, outline="o"):
    import math
    pts = []
    for i in range(10):
        r = 5.8 if i % 2 == 0 else 2.6
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((5.5 + r * math.cos(a), 6.0 + r * math.sin(a)))

    def inside(px, py):
        c = False
        for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):
            if (y1 > py) != (y2 > py) and px < (x2 - x1) * (py - y1) / (y2 - y1) + x1:
                c = not c
        return c

    def fn(x, y):
        if not inside(x + .5, y + .5):
            return None
        edge = not all(inside(x + .5 + dx, y + .5 + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        return outline if edge else fill
    return shape(fn)


def no_entry(x, y):
    d = ((x - 5.5) ** 2 + (y - 5.5) ** 2) ** 0.5
    if 4.0 <= d <= 5.6:
        return "r"
    if d < 4.0 and abs(x - y) <= 0:
        return "r"
    if d < 4.0 and abs(x - y) == 1:
        return "o"
    return None


FLAME = ["....o.......", "...oKo......", "...oKKo.....", "..oKKKo.o...", "..oKKKKoKo..", ".oKKKyKKKo..",
         ".oKKyyyKKo..", "oKKyyyyyKKo.", "oKKyyyyyKKo.", "oKKKyyyKKKo.", ".oKKKKKKKo..", "..ooooooo..."]
SHIELD = ["..oooooooo..", ".oBBBBBBBBo.", ".oBwBBBBBBo.", ".oBwBBBBBBo.", ".oBBBBBBBBo.", ".oBBBBBBBBo.",
          "..oBBBBBBo..", "..oBBBBBBo..", "...oBBBBo...", "....oBBo....", ".....oo.....", "............"]
GLYPHS = {
    "first_step": ["............", "..oo....oo..", ".oFFo..oFFo.", ".oFFFooFFFo.", "..oFFFFFFo..", "...ooFFoo...",
                   ".....oo.....", ".....oo.....", "..oooooooo..", "..oKKKKKKo..", "...oKKKKo...", "....oooo...."],
    "habit_50": numbered("check", "50"),
    "habit_500": numbered("check", "500"),
    "streak_7": FLAME,
    "streak_30": [r.replace("K", "r").replace("y", "K") for r in FLAME],
    "streak_100": numbered("flame", "100"),
    "focus_first": [".....oo.....", "....oooo....", "....oooo....", ".....oo.....", "...oooooo...", "..o.oooo.o..",
                    ".o..oooo..o.", "....oooo....", "..oooooooo..", ".oooooooooo.", "............", "............"],
    "focus_10h": numbered("clock", "10"),
    "focus_100h": numbered("clock", "100"),
    "milestone_1": ["...oooooo...", "...orrrrro..", "...orrrrrro.", "...orrrrro..", "...oooooo...", "...o........",
                    "...o........", "...o........", "...o........", "...o........", "..ooo.......", "............"],
    "goal_1": ["............", "............", ".....o......", "....owo.....", "...owwwo....", "..oTwTwTo...",
               ".oTTTTTTTo..", "oTTTTTTTTTo.", "ooooooooooo.", "............", "............", "............"],
    "goal_5": numbered("peak", "5"),
    "resist_10": SHIELD,
    "resist_100": [r if i not in (3, 4, 5) else r[:5] + ("yy" if i != 4 else "yy") + r[7:] for i, r in enumerate(SHIELD)],
    "shorts_25": shape(no_entry),
    "level_5": numbered("arrow", "5"),
    "level_10": star("w"),
    "level_25": star("r"),
    "treat_yo_self": ["...y..y..y..", "...o..o..o..", "...o..o..o..", ".oooooooooo.", ".owwwwwwwwo.", ".oRwRRwRRwo.",
                      ".oRRRRRRRRo.", ".oRRRRRRRRo.", ".oqqqqqqqqo.", "oooooooooooo", "............", "............"],
    "collector": [".....yy.....", ".....oo.....", "....oBBo....", "....owwo....", "...oBBBBo...", "...owwwwo...",
                  "..oBBBBBBo..", "..owwwwwwo..", ".oBBBBBBBBo.", ".oooooooooo.", "............", "............"],
}
BADGES = [  # (achievement id, tier) in Achievements.all order
    ("first_step", "bronze"), ("habit_50", "silver"), ("habit_500", "gold"), ("streak_7", "bronze"),
    ("streak_30", "silver"), ("streak_100", "gold"), ("focus_first", "bronze"), ("focus_10h", "silver"),
    ("focus_100h", "gold"), ("milestone_1", "bronze"), ("goal_1", "silver"), ("goal_5", "gold"),
    ("resist_10", "bronze"), ("resist_100", "gold"), ("shorts_25", "silver"), ("level_5", "bronze"),
    ("level_10", "silver"), ("level_25", "gold"), ("treat_yo_self", "bronze"), ("collector", "silver"),
]
BADGE_COLS = 5


def badge(glyph):
    n = 24
    g = [["."] * n for _ in range(n)]
    # Ribbon tails (drawn first, behind the medal)
    tails = set()
    for y in range(14, 24):
        k = (y - 14) // 3
        for x in range(6 - k, 12 - k):
            if not (y == 23 and x in (8 - k, 9 - k)):  # V-notch at the end of each tail
                tails.add((x, y))
        for x in range(12 + k, 18 + k):
            if not (y == 23 and x in (14 + k, 15 + k)):
                tails.add((x, y))
    for (x, y) in tails:
        edge = any((x + dx, y + dy) not in tails for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        g[y][x] = "o" if edge else ("v" if x < 12 else "V")
    cx, cy, r = 11.5, 9.5, 9.2
    disc = {(x, y) for y in range(n) for x in range(n) if (x - cx) ** 2 + (y - cy) ** 2 <= r * r}
    for (x, y) in disc:
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        edge = any((x + dx, y + dy) not in disc for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            g[y][x] = "o"
        elif d > r - 2.2:
            g[y][x] = "1" if (x - cx) + (y - cy) < 0 else "3"  # lit rim top-left, shaded bottom-right
        else:
            g[y][x] = "2"
    for y, row in enumerate(glyph):
        assert len(row) == 12, (glyph, row)
        for x, ch in enumerate(row):
            if ch != ".":
                g[4 + y][6 + x] = ch
    return g


def sparkle_frames():
    """48x48 level-up burst, 8 frames: sparkles fly outward and fade."""
    import math
    frames = []
    sizes = [1, 2, 2, 3, 3, 2, 1, 1]
    for k in range(8):
        g = [["."] * 48 for _ in range(48)]
        radius = 9 + 2.0 * k
        for i in range(8):
            a = i * math.pi / 4 + (math.pi / 8 if k % 2 else 0)
            cx, cy = round(23.5 + radius * math.cos(a)), round(23.5 + radius * math.sin(a))
            sz = sizes[k] if i % 2 == 0 else max(1, sizes[k] - 1)
            for d in range(-sz, sz + 1):
                for (x, y) in ((cx + d, cy), (cx, cy + d)):
                    if 0 <= x < 48 and 0 <= y < 48:
                        g[y][x] = "w" if d == 0 else ("y" if abs(d) < sz else "g")
        frames.append(g)
    return frames


TENT = {  # 8x8, feet at (4, 6). Row 0 unlit, row 1 lit (2-frame flicker).
    "unlit": [{1: "oo", 2: "oKKo", 3: "oKKKKo", 4: "oKKooKKo", 5: "oKKooKKo", 6: "oooooooo"}],
    "lit": [{1: "oo", 2: "oKKo", 3: "oKKKKo", 4: "oKKyyKKo", 5: "oKKyyKKo", 6: "oooooooo"},
            {1: "oo", 2: "oKKo", 3: "oKKKKo", 4: "oKKgyKKo", 5: "oKKyyKKo", 6: "oooooooo"}],
}

# 10x12 mini climber, feet at (5, 11). Explicit rows (not centred).
CLIMBER = [
    ["...oooo...", "..ohhhho..", "..osssso..", "..osooso..", "..osssso..", "...oooo...",
     "..orrrro..", ".orrrrrro.", ".osrrrrso.", "..orrrro..", "..ob..bo..", "..oo..oo.."],
    ["...oooo...", "..ohhhho..", "..osssso..", "..osooso..", "..osssso..", "...oooo...",
     "..orrrro..", ".orrrrrro.", ".osrrrrso.", "..orrrro..", ".ob....bo.", ".oo....oo."],
]

# 8x8 summit flag, pole at x=0 (placed so the pole stands on the peak). Row 0 red, row 1 gold.
FLAG = [
    ["orrrrr..", "orrrrrr.", "orrrrr..", "o.......", "o.......", "o.......", "o.......", "o......."],
    ["orrrr...", "orrrrrr.", "orrrrrrr", "o.......", "o.......", "o.......", "o.......", "o......."],
]


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
                hexv = ch[1:] if ch.startswith("#") else colors[ch]
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

    rows = [BADGES[i:i + BADGE_COLS] for i in range(0, len(BADGES), BADGE_COLS)]
    out = [["."] * (24 * BADGE_COLS) for _ in range(24 * len(rows))]
    for r, row in enumerate(rows):
        for c, (aid, tier) in enumerate(row):
            rim, face, shadow = TIERS[tier]
            tc = dict(colors, **{"1": rim, "2": face, "3": shadow})
            g = badge(GLYPHS[aid])
            for y in range(24):
                for x in range(24):
                    ch = g[y][x]
                    out[r * 24 + y][c * 24 + x] = "." if ch == "." else ("#" + tc[ch])
    write_png(os.path.join(OUT, "px_badges.png"), out, {})
    write_png(os.path.join(OUT, "px_sparkle.png"), sheet([sparkle_frames()], 48, 48), colors)

    climbing, summit = ascent_scene(False), ascent_scene(True)
    for i, base in enumerate(GOAL_COLORS):
        write_png(os.path.join(OUT, f"px_ascent_{i}.png"), sheet([[climbing], [summit]], SCENE_W, SCENE_H),
                  dict(colors, **scene_colors(base)))
    write_png(os.path.join(OUT, "px_tent.png"),
              sheet([[grid(f, 8, 8) for f in TENT["unlit"]], [grid(f, 8, 8) for f in TENT["lit"]]], 8, 8), colors)
    write_png(os.path.join(OUT, "px_climber.png"), sheet([[[list(r) for r in f] for f in CLIMBER]], 10, 12), colors)
    gold = [[r.replace("r", "g") for r in f] for f in FLAG]
    write_png(os.path.join(OUT, "px_flag.png"),
              sheet([[[list(r) for r in f] for f in FLAG], [[list(r) for r in f] for f in gold]], 8, 8), colors)

    # Palette exports (base palette + cloak ramps).
    entries = [(v[0], v[1]) for v in PALETTE.values()]
    for i, base in enumerate(GOAL_COLORS):
        for key, hexv in scene_colors(base).items():
            entries.append((hexv, f"goal {i} ramp {key}"))
    for tier, ramp in TIERS.items():
        for shade, hexv in zip(("rim", "face", "shadow"), ramp):
            entries.append((hexv, f"medal {tier} {shade}"))
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
