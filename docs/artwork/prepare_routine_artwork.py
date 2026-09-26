"""Build full-bleed Android WebP crops and a visual-review contact sheet."""

from pathlib import Path
from time import sleep

from PIL import Image, ImageDraw, ImageOps


ROOT = Path(__file__).resolve().parents[2]
MASTERS = Path(__file__).resolve().parent / "masters"
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

CROPS = {
    "card": (720, 480),
    "header": (960, 480),
    "picker": (320, 320),
}

# Header crops intentionally favor faces and defining equipment. The card keeps
# the complete 3:2 master, while the square picker preserves the vertical frame.
HEADER_FOCUS_Y = {
    "routine_dumbbell": 0.0,
    "routine_hangboard": 0.0,
    "routine_jump_rope": 0.0,
    "routine_leg_day": 0.2,
}


def crop(master: Image.Image, size: tuple[int, int], focus_y: float = 0.5) -> Image.Image:
    return ImageOps.fit(
        master.convert("RGB"),
        size,
        method=Image.Resampling.LANCZOS,
        centering=(0.5, focus_y),
    )


def save_webp(image: Image.Image, destination: Path) -> None:
    for attempt in range(5):
        try:
            image.save(destination, "WEBP", quality=86, method=6)
            return
        except OSError:
            if attempt == 4:
                raise
            sleep(0.5)


def build() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    review_rows: list[tuple[str, list[Image.Image]]] = []
    for source in sorted(MASTERS.glob("routine_*.png")):
        master = Image.open(source)
        variants = []
        for variant, size in CROPS.items():
            focus_y = HEADER_FOCUS_Y.get(source.stem, 0.32) if variant == "header" else 0.5
            image = crop(master, size, focus_y)
            destination = OUTPUT / f"{source.stem}_{variant}.webp"
            save_webp(image, destination)
            variants.append(image)
        review_rows.append((source.stem.removeprefix("routine_"), variants))

    sheet = Image.new("RGB", (1080, 220 * len(review_rows)), "#071423")
    draw = ImageDraw.Draw(sheet)
    for row, (name, variants) in enumerate(review_rows):
        y = row * 220
        draw.text((12, y + 10), name.replace("_", " ").title(), fill="#f3efe5")
        for column, image in enumerate(variants):
            preview = image.copy()
            preview.thumbnail((320, 170), Image.Resampling.LANCZOS)
            card = Image.new("RGB", (336, 184), "#13243d")
            card.paste(preview, ((336 - preview.width) // 2, (184 - preview.height) // 2))
            sheet.paste(card, (column * 352, y + 32))
    sheet.save(Path(__file__).resolve().parent / "RoutineArtworkContactSheet.png", optimize=True)


def validate_outputs() -> None:
    sources = sorted(MASTERS.glob("routine_*.png"))
    if len(sources) != 12:
        raise ValueError(f"expected 12 routine masters, found {len(sources)}")
    for source in sources:
        with Image.open(source) as master:
            if master.width < 1200 or master.height < 800 or master.width <= master.height:
                raise ValueError(f"{source.name} must be a high-resolution landscape master")
        for variant, expected_size in CROPS.items():
            destination = OUTPUT / f"{source.stem}_{variant}.webp"
            with Image.open(destination) as image:
                if image.size != expected_size:
                    raise ValueError(f"{destination.name} has unexpected dimensions {image.size}")
                if image.getbbox() != (0, 0, image.width, image.height):
                    raise ValueError(f"{destination.name} is not full bleed")
    print(f"Validated {len(sources)} masters and {len(sources) * len(CROPS)} routine WebP assets")


if __name__ == "__main__":
    build()
    validate_outputs()
