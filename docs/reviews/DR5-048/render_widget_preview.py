"""Prepare compact prototype resources and an actual-size 1-cell visual review."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
SAMPLES = ROOT / "docs/design/widget-icon-variants/prototype-rounded"
RUNTIME = ROOT / "app/src/main/res/drawable-nodpi"
REVIEW = Path(__file__).resolve().parent
NAMES = ("dumbbell", "flex", "fire", "melt")

REVIEW.mkdir(parents=True, exist_ok=True)
preview = Image.new("RGB", (392, 130), "#e9edf2")
draw = ImageDraw.Draw(preview)
for index, name in enumerate(NAMES):
    with Image.open(SAMPLES / f"five-{name}.png") as source:
        square = source.convert("RGBA").resize((256, 256), Image.Resampling.LANCZOS)
    square.save(RUNTIME / f"widget_five_{name}.webp", "WEBP", quality=88, method=6)
    tile = square.resize((56, 56), Image.Resampling.LANCZOS)
    mask = Image.new("L", (56, 56), 0)
    ImageDraw.Draw(mask).ellipse((2, 2, 54, 54), fill=255)
    x = 20 + index * 94
    preview.paste(tile, (x, 20), mask)
    draw.text((x, 87), name, fill="#102138")

preview.save(REVIEW / "widget-prototype-preview.png")
