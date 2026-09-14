"""Render three round-first launcher concepts from the app's measured-five path."""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "docs/reviews/DR5-033"))
from render_launcher_mask_review import five_outline  # noqa: E402


NAVY = "#122032"
IVORY = "#F4F0E7"
GOLD = "#FFC66D"
CANVAS = "#26384E"
FACTOR = 8


def icon(option: str, output_size: int) -> Image.Image:
    size = 108 * FACTOR
    background = IVORY if option == "C" else NAVY
    image = Image.new("RGB", (size, size), background)
    draw = ImageDraw.Draw(image)
    if option == "B":
        edge = 54 - 32
        draw.ellipse((edge * FACTOR, edge * FACTOR, (108 - edge) * FACTOR, (108 - edge) * FACTOR), outline=GOLD, width=2 * FACTOR)

    scale = {"A": 0.97, "B": 0.79, "C": 0.95}[option]
    x_shift, y_shift = -1, -2

    def point(x: float, y: float) -> tuple[int, int]:
        return (
            round((54 + (x - 54) * scale + x_shift) * FACTOR),
            round((54 + (y - 54) * scale + y_shift) * FACTOR),
        )

    draw.polygon([point(x, y) for x, y in five_outline()], fill=NAVY if option == "C" else IVORY)
    for left in (44, 53, 62):
        draw.polygon([point(left, 29), point(left + 4, 29), point(left + 4, 35), point(left, 35)], fill=GOLD)

    # Android displays the central 72 units after applying the launcher mask.
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((18 * FACTOR, 18 * FACTOR, 90 * FACTOR, 90 * FACTOR), fill=255)
    image.putalpha(mask)
    return image.crop((18 * FACTOR, 18 * FACTOR, 90 * FACTOR, 90 * FACTOR)).resize((output_size, output_size), Image.Resampling.LANCZOS)


def main() -> None:
    sheet = Image.new("RGB", (1080, 440), CANVAS)
    draw = ImageDraw.Draw(sheet)
    title = ImageFont.truetype("arial.ttf", 25)
    body = ImageFont.truetype("arial.ttf", 17)
    names = {
        "A": ("Big 5", "Border-free; largest numeral"),
        "B": ("Measuring ring", "Round gold frame"),
        "C": ("Ivory seal", "Light badge; dark numeral"),
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
    draw.text((40, 407), "All three retain the measured 5 and its three gold notches; circle masks shown.", fill="#CBD5E1", font=body)
    sheet.save(Path(__file__).with_name("launcher-round-options.png"), optimize=True)


if __name__ == "__main__":
    main()
