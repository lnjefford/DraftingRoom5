"""Render the production launcher geometry through representative adaptive masks."""

from __future__ import annotations

import math
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
VECTOR = ROOT / "app/src/main/res/drawable/measured_five_foreground.xml"
OUTPUT = ROOT / "docs/reviews/DR5-033/icon-mask-review.png"
ANDROID = "{http://schemas.android.com/apk/res/android}"
NAVY = "#071424"
IVORY = "#F4F0E7"
GOLD = "#FFC66D"
CANVAS = "#33465F"
SCALE = 6


def vector_scale() -> float:
    root = ET.parse(VECTOR).getroot()
    group = next(item for item in root.findall("group") if item.get(ANDROID + "name") == "launcher_safe_zone")
    scale_x = float(group.get(ANDROID + "scaleX"))
    scale_y = float(group.get(ANDROID + "scaleY"))
    if scale_x != scale_y:
        raise ValueError("launcher safe-zone transform must use a uniform scale")
    return scale_x


def transform(point: tuple[float, float], scale: float, inner: bool = False) -> tuple[float, float]:
    x, y = point
    if inner:
        x = (x - 54) * 0.85 + 54 - 2
        y = (y - 54) * 0.85 + 54 - 3
    return ((x - 54) * scale + 54, (y - 54) * scale + 54)


def cubic(start, control1, control2, end, steps=18):
    points = []
    for index in range(1, steps + 1):
        t = index / steps
        inverse = 1 - t
        points.append((
            inverse**3 * start[0] + 3 * inverse**2 * t * control1[0] + 3 * inverse * t**2 * control2[0] + t**3 * end[0],
            inverse**3 * start[1] + 3 * inverse**2 * t * control1[1] + 3 * inverse * t**2 * control2[1] + t**3 * end[1],
        ))
    return points


def five_outline() -> list[tuple[float, float]]:
    points = [(36, 29), (73, 29), (73, 40), (50, 40), (50, 50), (60, 50)]
    current = (60, 50)
    segments = [
        ((72, 50), (80, 57), (80, 67)),
        ((80, 79), (71, 86), (57, 86)),
        ((47, 86), (38, 82), (33, 75)),
    ]
    for control1, control2, end in segments:
        points.extend(cubic(current, control1, control2, end))
        current = end
    points.extend([(42, 68)])
    current = (42, 68)
    for control1, control2, end in [
        ((46, 73), (51, 75), (57, 75)),
        ((64, 75), (68, 72), (68, 67)),
        ((68, 62), (64, 59), (57, 59)),
    ]:
        points.extend(cubic(current, control1, control2, end))
        current = end
    points.extend([(36, 59)])
    return points


def draw_mark(size: int, monochrome: bool) -> Image.Image:
    source_scale = vector_scale()
    factor = size / 108
    image = Image.new("RGBA", (size, size), NAVY)
    draw = ImageDraw.Draw(image)

    def pixels(points, inner=False):
        return [(round(x * factor), round(y * factor)) for x, y in (transform(point, source_scale, inner) for point in points)]

    frame_color = IVORY if monochrome else GOLD
    for rectangle in [((21, 24), (87, 26)), ((21, 82), (87, 84)), ((24, 21), (26, 87)), ((82, 21), (84, 87))]:
        draw.polygon(pixels([rectangle[0], (rectangle[1][0], rectangle[0][1]), rectangle[1], (rectangle[0][0], rectangle[1][1])]), fill=frame_color)

    draw.polygon(pixels(five_outline(), inner=True), fill=IVORY)
    for left in (44, 53, 62):
        hole = [(left, 29), (left + 4, 29), (left + 4, 35), (left, 35)]
        draw.polygon(pixels(hole, inner=True), fill=NAVY)
    return image


def superellipse_mask(size: int, exponent: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    center = size / 2
    radius = size / 3
    points = []
    for index in range(720):
        angle = 2 * math.pi * index / 720
        cosine, sine = math.cos(angle), math.sin(angle)
        x = center + radius * math.copysign(abs(cosine) ** (2 / exponent), cosine)
        y = center + radius * math.copysign(abs(sine) ** (2 / exponent), sine)
        points.append((x, y))
    draw.polygon(points, fill=255)
    return mask


def mask_for(name: str, size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    edge = size // 6
    bounds = (edge, edge, size - edge, size - edge)
    if name == "Circle":
        draw.ellipse(bounds, fill=255)
    elif name == "Squircle":
        return superellipse_mask(size, 4)
    elif name == "Rounded square":
        draw.rounded_rectangle(bounds, radius=size * 0.14, fill=255)
    elif name == "Tight mask":
        draw.rounded_rectangle(bounds, radius=size * 0.055, fill=255)
    else:
        raise ValueError(name)
    return mask


def masked_icon(name: str, size: int, monochrome: bool) -> Image.Image:
    icon = draw_mark(size, monochrome)
    transparent = Image.new("RGBA", icon.size)
    return Image.composite(icon, transparent, mask_for(name, size))


def main() -> None:
    masks = ("Circle", "Squircle", "Rounded square", "Tight mask")
    width, height = 1120, 680
    sheet = Image.new("RGB", (width * SCALE, height * SCALE), CANVAS)
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("arial.ttf", 24 * SCALE)
    small_font = ImageFont.truetype("arial.ttf", 18 * SCALE)
    draw.text((40 * SCALE, 28 * SCALE), "DR5-033 · Android launcher mask review", fill=IVORY, font=font)
    draw.text((40 * SCALE, 64 * SCALE), "48.5-unit mark · 66-unit safe zone · production color and monochrome geometry", fill="#C8D1DC", font=small_font)

    for column, name in enumerate(masks):
        left = (40 + column * 270) * SCALE
        draw.text((left, 110 * SCALE), name, fill=IVORY, font=small_font)
        for row, monochrome in enumerate((False, True)):
            top = (150 + row * 250) * SCALE
            icon = masked_icon(name, 210 * SCALE, monochrome)
            sheet.paste(icon, (left, top), icon)
            label = "Monochrome" if monochrome else "Full color"
            draw.text((left, top + 216 * SCALE), label, fill="#C8D1DC", font=small_font)

    draw.text((40 * SCALE, 642 * SCALE), "Masks use Android's 72-unit viewport; the mark also fits the guaranteed 66-unit circular safe zone.", fill="#C8D1DC", font=small_font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.resize((width, height), Image.Resampling.LANCZOS).save(OUTPUT, optimize=True)


if __name__ == "__main__":
    main()
