"""Compare three hatched gold borders against the selected B1 launcher concept."""

from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "docs/reviews/DR5-033"))
from render_launcher_mask_review import five_outline, mask_for  # noqa: E402


BLACK = "#000000"
IVORY = "#F4F0E7"
GOLD = "#FFC66D"
CANVAS = "#26384E"
FACTOR = 8


def icon(option: str, output_size: int, mask_name: str = "Circle", monochrome: bool = False) -> Image.Image:
    size = 108 * FACTOR
    image = Image.new("RGB", (size, size), BLACK)
    draw = ImageDraw.Draw(image)
    border_color = "#FFFFFF" if monochrome else GOLD
    five_color = "#FFFFFF" if monochrome else IVORY

    def ring_box(radius: float) -> tuple[int, int, int, int]:
        return tuple(round(value * FACTOR) for value in (54 - radius, 54 - radius, 54 + radius, 54 + radius))

    def polar(radius: float, degrees: float, tangent_offset: float = 0) -> tuple[int, int]:
        angle = math.radians(degrees)
        x = 54 + radius * math.cos(angle) - tangent_offset * math.sin(angle)
        y = 54 + radius * math.sin(angle) + tangent_offset * math.cos(angle)
        return round(x * FACTOR), round(y * FACTOR)

    def etch(degrees: float, inner: float, outer: float) -> None:
        draw.line((polar(inner, degrees, -0.6), polar(outer, degrees, 0.6)), fill=BLACK, width=round(0.8 * FACTOR))

    if option == "B1":
        draw.ellipse(ring_box(32), outline=border_color, width=2 * FACTOR)
    elif option == "H1":
        draw.ellipse(ring_box(33), outline=border_color, width=round(3.5 * FACTOR))
        for degrees in range(0, 360, 25):
            etch(degrees, 29.8, 32.2)
    elif option == "H2":
        draw.ellipse(ring_box(32), outline=border_color, width=2 * FACTOR)
        for start, end in ((25, 75), (205, 255)):
            draw.arc(ring_box(28.5), start, end, fill=border_color, width=round(1.2 * FACTOR))
        for degrees in (35, 47, 59, 71, 215, 227, 239, 251):
            draw.line((polar(28.5, degrees, -0.5), polar(31.5, degrees, 0.5)), fill=border_color, width=round(0.9 * FACTOR))
    elif option == "H3":
        draw.ellipse(ring_box(33), outline=border_color, width=round(1.2 * FACTOR))
        draw.ellipse(ring_box(29.5), outline=border_color, width=round(1.2 * FACTOR))
        for degrees in range(0, 360, 30):
            draw.line((polar(29.5, degrees, -0.8), polar(32.5, degrees, 0.8)), fill=border_color, width=round(0.9 * FACTOR))
    else:
        raise ValueError(option)

    def point(x: float, y: float) -> tuple[int, int]:
        return (
            round((54 + (x - 54) * 0.79 - 1) * FACTOR),
            round((54 + (y - 54) * 0.79 - 2) * FACTOR),
        )

    draw.polygon([point(x, y) for x, y in five_outline()], fill=five_color)
    for left in (44, 53, 62):
        draw.polygon([point(left, 29), point(left + 4, 29), point(left + 4, 35), point(left, 35)], fill=BLACK if monochrome else GOLD)

    mask = mask_for(mask_name, size)
    image.putalpha(mask)
    return image.crop((18 * FACTOR, 18 * FACTOR, 90 * FACTOR, 90 * FACTOR)).resize((output_size, output_size), Image.Resampling.LANCZOS)


def main() -> None:
    sheet = Image.new("RGB", (1440, 440), CANVAS)
    draw = ImageDraw.Draw(sheet)
    title = ImageFont.truetype("arial.ttf", 25)
    body = ImageFont.truetype("arial.ttf", 17)
    names = {
        "B1": ("Original B1", "Full gold ring"),
        "H1": ("Etched ring", "Fine hatching all around"),
        "H2": ("Hatched accents", "Texture on two sections"),
        "H3": ("Double ring", "Fine diagonal bridges"),
    }
    for index, option in enumerate(names):
        left = 40 + 350 * index
        name, description = names[option]
        draw.text((left, 26), f"{option}  {name}", fill=IVORY, font=title)
        draw.text((left, 65), description, fill="#CBD5E1", font=body)
        large = icon(option, 208)
        sheet.paste(large, (left + 60, 107), large)
        draw.text((left, 341), "Home-screen size", fill="#CBD5E1", font=body)
        small = icon(option, 48)
        sheet.paste(small, (left + 188, 330), small)
    draw.text((40, 407), "B1 is the reference; H1–H3 vary only the border. All retain the black background and measured 5.", fill="#CBD5E1", font=body)
    sheet.save(Path(__file__).with_name("hatched-ring-options.png"), optimize=True)


if __name__ == "__main__":
    main()
