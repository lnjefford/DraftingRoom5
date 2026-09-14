"""Render the chosen H2 launcher icon through representative Android masks."""

from pathlib import Path
import sys

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "docs/design/launcher-round-options"))
from render_hatched_ring_options import icon  # noqa: E402


def main() -> None:
    sheet = Image.new("RGB", (1120, 680), "#26384E")
    draw = ImageDraw.Draw(sheet)
    title = ImageFont.truetype("arial.ttf", 25)
    body = ImageFont.truetype("arial.ttf", 17)
    draw.text((40, 28), "H2 · round-first launcher mask review", fill="#F4F0E7", font=title)
    draw.text((40, 66), "Black field · ivory 5 · hatched gold accents · white themed silhouette", fill="#CBD5E1", font=body)
    for column, name in enumerate(("Circle", "Squircle", "Rounded square", "Tight mask")):
        left = 40 + column * 270
        draw.text((left, 112), name, fill="#F4F0E7", font=body)
        for row, monochrome in enumerate((False, True)):
            top = 150 + row * 250
            preview = icon("H2", 210, mask_name=name, monochrome=monochrome)
            sheet.paste(preview, (left, top), preview)
            draw.text((left, top + 216), "Themed" if monochrome else "Full color", fill="#CBD5E1", font=body)
    draw.text((40, 642), "Outer stroke reaches the 33-unit safe radius; circle and other masks preserve the complete mark.", fill="#CBD5E1", font=body)
    sheet.save(Path(__file__).with_name("mask-review.png"), optimize=True)


if __name__ == "__main__":
    main()
