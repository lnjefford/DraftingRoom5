"""Prepare DR5-054 black-disc widget art, derivatives, and contact sheets."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "generated-source"
MASTERS = ROOT / "masters"
BATCHES = ROOT / "batches"
RESOURCES = ROOT.parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"
IDS = [
    "widget_41_punching_bag", "widget_42_yoga", "widget_43_prism",
    "widget_44_sandstorm", "widget_45_moonwalk", "widget_46_genie_lamp",
    "widget_47_roller_coaster", "widget_48_compass", "widget_49_kite",
    "widget_50_curtain_call",
]
SIZE = 1254
DISC_BOX = (67, 67, 1186, 1186)


def normalize(source: Image.Image) -> Image.Image:
    image = source.convert("RGBA")
    if image.size != (SIZE, SIZE):
        raise ValueError(f"Expected 1254 px source, got {image.size}")
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("Empty source alpha")
    artwork = image.crop(bbox)
    scale = min(1120 / artwork.width, 1120 / artwork.height)
    artwork = artwork.resize(
        (round(artwork.width * scale), round(artwork.height * scale)),
        Image.Resampling.LANCZOS,
    )
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    layer.alpha_composite(artwork, ((SIZE - artwork.width) // 2, (SIZE - artwork.height) // 2))
    # Keep the generated scene and its distinct frame, but guarantee the shared
    # true-black circular backing and genuine transparent corners.
    disc = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(disc).ellipse(DISC_BOX, fill=(0, 0, 0, 255))
    disc.alpha_composite(layer)
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).ellipse(DISC_BOX, fill=255)
    disc.putalpha(mask)
    return disc


def batch_sheet(icons: list[Image.Image], size: int) -> None:
    cell_w, cell_h = 330, 330
    output = Image.new("RGB", (cell_w * 5, cell_h * 2), "#e8edf5")
    draw = ImageDraw.Draw(output)
    for i, (name, icon) in enumerate(zip(IDS, icons)):
        left, top = i % 5 * cell_w, i // 5 * cell_h
        draw.rectangle((left + 8, top + 8, left + 158, top + 94), fill="#f7f8f8")
        draw.rectangle((left + 168, top + 8, left + 318, top + 94), fill="#1d2636")
        small = icon.resize((size, size), Image.Resampling.LANCZOS)
        output.paste(small, (left + 83 - size // 2, top + 50 - size // 2), small)
        output.paste(small, (left + 243 - size // 2, top + 50 - size // 2), small)
        large = icon.resize((208, 208), Image.Resampling.LANCZOS)
        output.paste(large, (left + 61, top + 99), large)
        draw.text((left + 12, top + 310), name, fill="#101b2d")
    suffix = "" if size == 56 else f"-{size}px"
    output.save(BATCHES / f"batch-41-50-contact-sheet{suffix}.png")


def full_sheet() -> None:
    names = sorted(p.stem for p in RESOURCES.glob("widget_[0-9][0-9]_*.webp"))
    if len(names) != 50:
        raise ValueError(f"Expected 50 runtime images, found {len(names)}")
    cell_w, cell_h = 154, 128
    output = Image.new("RGB", (cell_w * 10, cell_h * 5), "#e8edf5")
    draw = ImageDraw.Draw(output)
    records = []
    for i, name in enumerate(names):
        path = RESOURCES / f"{name}.webp"
        icon = Image.open(path).convert("RGBA")
        corners = [icon.getpixel(pt)[3] for pt in ((0, 0), (255, 0), (0, 255), (255, 255))]
        if icon.size != (256, 256) or any(corners):
            raise ValueError(f"{name}: invalid size or alpha corners")
        records.append({
            "id": name,
            "derivative_px": list(icon.size),
            "format": "WebP RGBA",
            "alpha_corners": corners,
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        })
        left, top = i % 10 * cell_w, i // 10 * cell_h
        draw.rectangle((left + 4, top + 4, left + 75, top + 83), fill="#f7f8f8")
        draw.rectangle((left + 79, top + 4, left + 150, top + 83), fill="#1d2636")
        small = icon.resize((56, 56), Image.Resampling.LANCZOS)
        output.paste(small, (left + 12, top + 15), small)
        output.paste(small, (left + 87, top + 15), small)
        draw.text((left + 6, top + 89), name.replace("widget_", ""), fill="#101b2d")
    output.save(BATCHES / "all-50-contact-sheet-56px.png")
    if len({record["sha256"] for record in records}) != 50:
        raise ValueError("Duplicate derivative in complete collection")
    summary = {
        "count": len(records),
        "total_bytes": sum(record["bytes"] for record in records),
        "largest_bytes": max(record["bytes"] for record in records),
        "assets": records,
    }
    if summary["total_bytes"] > 2_000_000:
        raise ValueError("Complete collection exceeds 2 MB budget")
    (BATCHES / "all-50-measurements.json").write_text(
        json.dumps(summary, indent=2) + "\n", encoding="utf-8"
    )


def main() -> None:
    MASTERS.mkdir(parents=True, exist_ok=True)
    BATCHES.mkdir(parents=True, exist_ok=True)
    RESOURCES.mkdir(parents=True, exist_ok=True)
    measurements = []
    icons = []
    for name in IDS:
        source = Image.open(SOURCE / f"{name}.png")
        master = normalize(source)
        master.save(MASTERS / f"{name}.png", optimize=True)
        icon = master.resize((256, 256), Image.Resampling.LANCZOS)
        path = RESOURCES / f"{name}.webp"
        icon.save(path, format="WEBP", quality=90, method=6)
        confirmed = Image.open(path).convert("RGBA")
        corners = [confirmed.getpixel(pt)[3] for pt in ((0, 0), (255, 0), (0, 255), (255, 255))]
        if any(corners):
            raise ValueError(f"{name}: opaque corners {corners}")
        if path.stat().st_size > 40_000:
            raise ValueError(f"{name}: derivative too large: {path.stat().st_size}")
        icons.append(confirmed)
        measurements.append({
            "id": name,
            "source_px": list(source.size),
            "source_alpha_bbox": source.convert("RGBA").getchannel("A").getbbox(),
            "master_px": list(master.size),
            "master_alpha_bbox": master.getchannel("A").getbbox(),
            "derivative_px": list(confirmed.size),
            "format": "WebP RGBA",
            "alpha_corners": corners,
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        })
    if len({item["sha256"] for item in measurements}) != len(IDS):
        raise ValueError("Duplicate derivatives in final batch")
    batch_sheet(icons, 56)
    batch_sheet(icons, 48)
    full_sheet()
    (BATCHES / "batch-41-50-measurements.json").write_text(
        json.dumps(measurements, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Prepared {len(icons)} icons; {sum(m['bytes'] for m in measurements)} bytes total")


if __name__ == "__main__":
    main()
