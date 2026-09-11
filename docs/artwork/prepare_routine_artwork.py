"""Build bounded Android WebP crops and a visual-review contact sheet."""

from pathlib import Path
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
MASTERS = Path(__file__).resolve().parent / "masters"
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

CROPS = {
    "card": ((720, 480), (400, 400), (500, 240)),
    "header": ((960, 480), (420, 420), (710, 240)),
    "picker": ((320, 320), (248, 248), (160, 160)),
}


def crop(master: Image.Image, canvas_size: tuple[int, int], art_size: tuple[int, int], center: tuple[int, int]) -> Image.Image:
    alpha_bounds = master.getchannel("A").getbbox()
    if alpha_bounds is None:
        raise ValueError("master has no visible pixels")
    art = master.crop(alpha_bounds)
    art.thumbnail(art_size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    x = center[0] - art.width // 2
    y = center[1] - art.height // 2
    canvas.alpha_composite(art, (x, y))
    return canvas


def build() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    review_rows: list[tuple[str, list[Image.Image]]] = []
    for source in sorted(MASTERS.glob("routine_*.png")):
        master = Image.open(source).convert("RGBA")
        variants = []
        for variant, (canvas_size, art_size, center) in CROPS.items():
            image = crop(master, canvas_size, art_size, center)
            destination = OUTPUT / f"{source.stem}_{variant}.webp"
            image.save(destination, "WEBP", quality=86, method=4, exact=True)
            variants.append(image)
        review_rows.append((source.stem.removeprefix("routine_"), variants))

    sheet = Image.new("RGB", (1080, 220 * len(review_rows)), "#f3efe5")
    draw = ImageDraw.Draw(sheet)
    for row, (name, variants) in enumerate(review_rows):
        y = row * 220
        draw.text((12, y + 10), name.replace("_", " ").title(), fill="#0b1930")
        for column, image in enumerate(variants):
            preview = image.copy()
            preview.thumbnail((320, 170), Image.Resampling.LANCZOS)
            card = Image.new("RGBA", (336, 184), "#13243d")
            card.alpha_composite(preview, ((336 - preview.width) // 2, (184 - preview.height) // 2))
            sheet.paste(card.convert("RGB"), (column * 352, y + 32))
    sheet.save(Path(__file__).resolve().parent / "RoutineArtworkContactSheet.png", optimize=True)


if __name__ == "__main__":
    build()
