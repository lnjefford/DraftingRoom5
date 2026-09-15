"""Prepare free-silhouette widget art and measured contact sheets for DR5-052."""

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
    "widget_21_climbing", "widget_22_balance_beam", "widget_23_tornado",
    "widget_24_geode", "widget_25_portal", "widget_26_shadow_puppet",
    "widget_27_pendulum", "widget_28_pinwheel", "widget_29_balloon",
    "widget_30_windup_toy",
]


def normalize(source: Image.Image) -> Image.Image:
    image = source.convert("RGBA")
    if image.size != (1254, 1254):
        raise ValueError(f"Expected 1254 px source, got {image.size}")
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("Empty source alpha")
    artwork = image.crop(bbox)
    scale = min(1120 / artwork.width, 1120 / artwork.height, 1.0)
    if scale != 1.0:
        artwork = artwork.resize(
            (round(artwork.width * scale), round(artwork.height * scale)),
            Image.Resampling.LANCZOS,
        )
    canvas = Image.new("RGBA", (1254, 1254), (0, 0, 0, 0))
    x = (1254 - artwork.width) // 2
    y = (1254 - artwork.height) // 2
    canvas.paste(artwork, (x, y))
    return canvas


def sheet(icons: list[Image.Image], size: int) -> None:
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
    output.save(BATCHES / f"batch-21-30-contact-sheet{suffix}.png")


def main() -> None:
    MASTERS.mkdir(parents=True, exist_ok=True)
    BATCHES.mkdir(parents=True, exist_ok=True)
    RESOURCES.mkdir(parents=True, exist_ok=True)
    measurements = []
    icons = []
    for name in IDS:
        source = Image.open(SOURCE / f"{name}.png")
        master = normalize(source)
        bbox = master.getchannel("A").getbbox()
        if bbox is None or min(bbox[0], bbox[1]) < 67 or max(bbox[2], bbox[3]) > 1187:
            raise ValueError(f"{name}: master exceeds safe bounds: {bbox}")
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
    sheet(icons, 56)
    sheet(icons, 48)
    if len({item["sha256"] for item in measurements}) != len(IDS):
        raise ValueError("Duplicate WebP derivatives")
    if sum(item["bytes"] for item in measurements) > 400_000:
        raise ValueError("Batch exceeds 400 KB budget")
    (BATCHES / "batch-21-30-measurements.json").write_text(
        json.dumps(measurements, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Prepared {len(icons)} icons; {sum(m['bytes'] for m in measurements)} bytes total")


if __name__ == "__main__":
    main()
