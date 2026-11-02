"""Build Google Play artwork from the shipped watch-face preview.

Run with Pillow installed: python play/face/generate_assets.py
The Wear screenshot in this folder is captured separately from an emulator.
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SOURCE = ROOT / "face/src/main/res/drawable/preview.png"
MINT = (105, 230, 202)
ACCENTS = {
    "mint": MINT,
    "sky": (141, 205, 245),
    "gold": (245, 205, 119),
}


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    path = "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
        return ImageFont.truetype(name, size)


def recolor(source: Image.Image, accent: tuple[int, int, int]) -> Image.Image:
    """Change only the mint hand, seconds hand, and STAND label."""
    image = source.convert("RGB")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b = pixels[x, y]
            # The mint parts retain their characteristic G > B > R ordering
            # even at antialiased edges. Limit the search to their locations.
            if ((x > 185 and y > 90) or (x > 245 and 235 < y < 265)) and g - r > 48 and b - r > 32 and g - b > 8:
                strength = min(1.0, (g - r) / (MINT[1] - MINT[0]))
                background = tuple(round((channel - strength * base) / (1 - strength)) if strength < 0.98 else 0
                                   for channel, base in zip((r, g, b), MINT))
                pixels[x, y] = tuple(max(0, min(255, round(bg * (1 - strength) + target * strength)))
                                     for bg, target in zip(background, accent))
    return image


preview = Image.open(SOURCE).convert("RGB")
for name, accent in ACCENTS.items():
    recolor(preview, accent).save(HERE / f"wear-preview-{name}.png", optimize=True)

# Google Play icon: centered circular face, edge-to-edge, transparent corners.
icon_face = preview.crop((9, 9, 441, 441)).resize((512, 512), Image.Resampling.LANCZOS)
mask = Image.new("L", (512, 512), 0)
ImageDraw.Draw(mask).ellipse((0, 0, 511, 511), fill=255)
icon = icon_face.convert("RGBA")
icon.putalpha(mask)
icon.save(HERE / "app-icon-512.png", optimize=True)

# Feature graphic: a close view of the actual dial, with modest listing copy.
feature = Image.new("RGB", (1024, 500), "#0B1E25")
draw = ImageDraw.Draw(feature)
for y in range(500):
    t = y / 499
    color = (round(11 + 3 * t), round(30 - 10 * t), round(37 - 10 * t))
    draw.line((0, y, 1024, y), fill=color)
draw.ellipse((535, -290, 1135, 310), outline="#204E54", width=2)
draw.ellipse((620, -155, 1020, 245), outline="#2F6165", width=2)
draw.text((64, 65), "DRAFTINGROOM5", font=font(25, True), fill="#69E6CA")
draw.text((64, 128), "A little joy", font=font(64, True), fill="#F6F6F6")
draw.text((64, 204), "every time you look.", font=font(45), fill="#E4EFF1")
draw.text((66, 354), "Analog time  ·  Weather  ·  Stand hours", font=font(22), fill="#B9D5D8")
face = preview.resize((410, 410), Image.Resampling.LANCZOS)
feature.paste(face, (635, 45))
feature.save(HERE / "feature-graphic-1024x500.png", optimize=True)
