"""Build paired Android WebP exercise compositions and a review sheet."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
MASTERS = Path(__file__).resolve().parent / "masters"
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

# List art is compact and centered. Header art is independently scaled and
# right-weighted so the left half remains available for exercise text.
COMPOSITIONS = {
    "list": ((320, 320), (276, 276), (160, 160)),
    "header": ((960, 480), (430, 430), (730, 240)),
}


def compose(
    master: Image.Image,
    canvas_size: tuple[int, int],
    art_size: tuple[int, int],
    center: tuple[int, int],
) -> Image.Image:
    alpha_bounds = master.getchannel("A").getbbox()
    if alpha_bounds is None:
        raise ValueError("master has no visible pixels")
    art = master.crop(alpha_bounds)
    art.thumbnail(art_size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    position = (center[0] - art.width // 2, center[1] - art.height // 2)
    canvas.alpha_composite(art, position)
    return canvas


def build() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    review_rows: list[tuple[str, Image.Image, Image.Image]] = []
    for source in sorted(MASTERS.glob("exercise_*.png")):
        master = Image.open(source).convert("RGBA")
        variants: dict[str, Image.Image] = {}
        for variant, (canvas_size, art_size, center) in COMPOSITIONS.items():
            image = compose(master, canvas_size, art_size, center)
            destination = OUTPUT / f"{source.stem}_{variant}.webp"
            image.save(destination, "WEBP", quality=86, method=4, exact=True)
            variants[variant] = image
        review_rows.append((source.stem.removeprefix("exercise_"), variants["list"], variants["header"]))

    sheet = Image.new("RGB", (1060, 190 * len(review_rows)), "#f3efe5")
    draw = ImageDraw.Draw(sheet)
    for row, (name, list_image, header_image) in enumerate(review_rows):
        y = row * 190
        draw.text((12, y + 8), name.replace("_", " ").title(), fill="#0b1930")
        list_preview = list_image.copy()
        list_preview.thumbnail((164, 164), Image.Resampling.LANCZOS)
        header_preview = header_image.copy()
        header_preview.thumbnail((720, 164), Image.Resampling.LANCZOS)
        list_card = Image.new("RGBA", (180, 164), "#13243d")
        list_card.alpha_composite(list_preview, ((180 - list_preview.width) // 2, 0))
        header_card = Image.new("RGBA", (720, 164), "#13243d")
        header_card.alpha_composite(header_preview, ((720 - header_preview.width) // 2, 0))
        sheet.paste(list_card.convert("RGB"), (160, y + 20))
        sheet.paste(header_card.convert("RGB"), (352, y + 20))
    sheet.save(Path(__file__).resolve().parent / "ExerciseArtworkContactSheet.png", optimize=True)


def validate_outputs() -> None:
    sources = sorted(MASTERS.glob("exercise_*.png"))
    if len(sources) != 8:
        raise ValueError(f"expected 8 exercise masters, found {len(sources)}")
    for source in sources:
        with Image.open(source) as master:
            if master.size != (1254, 1254) or "A" not in master.getbands():
                raise ValueError(f"{source.name} must be a 1254px RGBA master")
        for variant, (expected_size, _, _) in COMPOSITIONS.items():
            destination = OUTPUT / f"{source.stem}_{variant}.webp"
            with Image.open(destination).convert("RGBA") as image:
                if image.size != expected_size:
                    raise ValueError(f"{destination.name} has unexpected dimensions {image.size}")
                bounds = image.getchannel("A").getbbox()
                if bounds is None:
                    raise ValueError(f"{destination.name} is empty")
                if bounds[0] <= 0 or bounds[1] <= 0 or bounds[2] >= image.width or bounds[3] >= image.height:
                    raise ValueError(f"{destination.name} clips visible artwork at {bounds}")
                if variant == "header" and bounds[0] < image.width // 2:
                    raise ValueError(f"{destination.name} does not preserve left-side text space: {bounds}")
    print(f"Validated {len(sources)} masters and {len(sources) * len(COMPOSITIONS)} paired WebP assets")


if __name__ == "__main__":
    build()
    validate_outputs()
