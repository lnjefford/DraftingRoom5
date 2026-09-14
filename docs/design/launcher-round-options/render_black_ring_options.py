"""Compare black-background variants of the round measured-five launcher badge."""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "docs/reviews/DR5-033"))
from render_launcher_mask_review import five_outline  # noqa: E402


BLACK = "#000000"
IVORY = "#F4F0E7"
GOLD = "#FFC66D"
CANVAS = "#26384E"
FACTOR = 8


def icon(option: str, output_size: int) -> Image.Image:
    size = 108 * FACTOR
    image = Image.new("RGB", (size, size), BLACK)
    draw = ImageDraw.Draw(image)

    def ring_box(radius: float) -> tuple[int, int, int, int]:
        return tuple(round(value * FACTOR) for value in (54 - radius, 54 - radius, 54 + radius, 54 + radius))

    if option == "B1":
        draw.ellipse(ring_box(32), outline=GOLD, width=2 * FACTOR)
    elif option == "B2":
        for start, end in ((12, 78), (102, 168), (192, 258), (282, 348)):
            draw.arc(ring_box(32), start, end, fill=GOLD, width=2 * FACTOR)
    elif option == "B3":
        draw.ellipse(ring_box(31.5), outline=GOLD, width=round(1.5 * FACTOR))
        for start, end in (((54, 21), (54, 27)), ((81, 54), (87, 54)), ((54, 81), (54, 87)), ((21, 54), (27, 54))):
            draw.line(tuple((round(x * FACTOR), round(y * FACTOR)) for x, y in (start, end)), fill=GOLD, width=2 * FACTOR)
    else:
        raise ValueError(option)

    def point(x: float, y: float) -> tuple[int, int]:
        return (
            round((54 + (x - 54) * 0.79 - 1) * FACTOR),
            round((54 + (y - 54) * 0.79 - 2) * FACTOR),
        )

    draw.polygon([point(x, y) for x, y in five_outline()], fill=IVORY)
    for left in (44, 53, 62):
        draw.polygon([point(left, 29), point(left + 4, 29), point(left + 4, 35), point(left, 35)], fill=GOLD)

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
        "B1": ("Full ring", "Clean continuous border"),
        "B2": ("Broken ring", "Four measured gold arcs"),
        "B3": ("Compass ticks", "Fine ring with four markers"),
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
    draw.text((40, 407), "All variants use black backgrounds, ivory 5s, and gold measuring notches.", fill="#CBD5E1", font=body)
    sheet.save(Path(__file__).with_name("black-ring-options.png"), optimize=True)


if __name__ == "__main__":
    main()
