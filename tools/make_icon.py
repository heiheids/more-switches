"""Generate the 更多开关功能 (More Switches) mod icon.

Design: a bordered panel holding two switch tracks — one grey with the knob on the
left (off), one green with the knob on the right (on). That is literally what the
mod's screen shows, so the icon needs no explanation.

Drawn as 16x16 pixel art and upscaled with NEAREST so it stays crisp in the Mods
list. Needs Pillow; the interpreter bundled with the harness already has it. Writes
only inside the project this script lives in.
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw

# <project>/tools/make_icon.py -> <project>/src/main/resources/assets/moreswitches/textures
PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(PROJECT, "src", "main", "resources", "assets", "moreswitches", "textures")
os.makedirs(OUT, exist_ok=True)

SIZE = 16
SCALED = 128

PANEL = (44, 48, 56, 255)
PANEL_LIGHT = (74, 80, 92, 255)
BORDER = (16, 18, 22, 255)

TRACK_OFF = (86, 86, 86, 255)
TRACK_OFF_LIGHT = (120, 120, 120, 255)
TRACK_ON = (60, 133, 39, 255)
TRACK_ON_LIGHT = (111, 201, 90, 255)
KNOB = (206, 206, 206, 255)
KNOB_LIGHT = (255, 255, 255, 255)
KNOB_DARK = (108, 108, 108, 255)

image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)


def pixel(x: int, y: int, color: tuple[int, int, int, int]) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        image.putpixel((x, y), color)


def rect(x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            pixel(x, y, color)


def track(x0: int, y0: int, x1: int, y1: int, on: bool) -> None:
    """One 12x4 switch track: dark border, bevelled body, 4x2 knob at the correct end.

    Inner area runs x0+1 .. x1-1 (10 px). The knob is 4 px wide, so it sits at
    x0+1 .. x0+4 when off and x1-4 .. x1-1 when on, leaving a visible gap at the far end.
    """
    body = TRACK_ON if on else TRACK_OFF
    light = TRACK_ON_LIGHT if on else TRACK_OFF_LIGHT
    rect(x0, y0, x1, y1, BORDER)
    rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, body)
    rect(x0 + 1, y0 + 1, x1 - 1, y0 + 1, light)

    knob_x0 = (x1 - 4) if on else (x0 + 1)
    rect(knob_x0, y0 + 1, knob_x0 + 3, y1 - 1, KNOB)
    rect(knob_x0, y0 + 1, knob_x0 + 3, y0 + 1, KNOB_LIGHT)
    rect(knob_x0, y1 - 1, knob_x0 + 3, y1 - 1, KNOB_DARK)


# --- panel: a dark rounded-ish card so the icon reads on any background
rect(0, 0, SIZE - 1, SIZE - 1, BORDER)
rect(1, 1, SIZE - 2, SIZE - 2, PANEL)
rect(1, 1, SIZE - 2, 2, PANEL_LIGHT)

# --- the two switches, one off and one on: the mod's whole idea in one picture
track(2, 4, 13, 7, on=False)
track(2, 9, 13, 12, on=True)

image.resize((SCALED, SCALED), Image.NEAREST).save(os.path.join(OUT, "icon.png"))
print(os.path.join(OUT, "icon.png"))
