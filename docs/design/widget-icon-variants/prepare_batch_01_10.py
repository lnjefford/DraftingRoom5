"""Normalize the ten DR5-050 masters and produce measured widget QA artifacts."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
SOURCES = ROOT / "generated-source"
MASTERS = ROOT / "masters"
BATCHES = ROOT / "batches"
RESOURCES = ROOT.parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"
IDS = [
    "widget_01_dumbbell", "widget_02_flex", "widget_03_fire", "widget_04_melt",
    "widget_05_orbit", "widget_06_jump_rope", "widget_07_wave",
    "widget_08_skateboard", "widget_09_spotlight", "widget_10_paper_plane",
]


def circle_mask(size: int, radius: float) -> Image.Image:
    # Work at master resolution; LANCZOS resizing antialiases the final edge.
    mask = Image.new("L", (size, size), 0)
    center = size / 2
    ImageDraw.Draw(mask).ellipse(
        (center - radius, center - radius, center + radius, center + radius),
        fill=255,
    )
    return mask


def render_sheet(derivatives: list[Image.Image], tile_px: int = 56) -> None:
    # 2 x 5 cells. The swatches are embedded at exactly tile_px physical pixels.
    cell_w, cell_h = 330, 330
    sheet = Image.new("RGB", (cell_w * 5, cell_h * 2), "#e8edf5")
    draw = ImageDraw.Draw(sheet)
    for index, (name, icon) in enumerate(zip(IDS, derivatives)):
        left = index % 5 * cell_w
        top = index // 5 * cell_h
        draw.rectangle((left + 8, top + 8, left + 158, top + 94), fill="#f7f8f8")
        draw.rectangle((left + 168, top + 8, left + 318, top + 94), fill="#1d2636")
        tile = icon.resize((tile_px, tile_px), Image.Resampling.LANCZOS)
        sheet.paste(tile, (left + 83 - tile_px // 2, top + 50 - tile_px // 2), tile)
        sheet.paste(tile, (left + 243 - tile_px // 2, top + 50 - tile_px // 2), tile)
        enlarged = icon.resize((208, 208), Image.Resampling.LANCZOS)
        sheet.paste(enlarged, (left + 61, top + 99), enlarged)
        draw.text((left + 12, top + 310), name, fill="#101b2d")
    suffix = "" if tile_px == 56 else f"-{tile_px}px"
    sheet.save(BATCHES / f"batch-01-10-contact-sheet{suffix}.png")


def main() -> None:
    MASTERS.mkdir(parents=True, exist_ok=True)
    BATCHES.mkdir(parents=True, exist_ok=True)
    RESOURCES.mkdir(parents=True, exist_ok=True)
    measurements = []
    derivatives = []
    for name in IDS:
        raw = Image.open(SOURCES / f"{name}.png").convert("RGBA")
        if raw.size != (1254, 1254):
            raise ValueError(f"{name}: expected 1254 px square, got {raw.size}")

        # The two referenced sample generations painted checkerboard outside
        # the disc. Cut at the source-disc edge before fitting the final circle.
        if name in {"widget_01_dumbbell", "widget_03_fire", "widget_04_melt"}:
            raw.putalpha(Image.composite(raw.getchannel("A"), Image.new("L", raw.size, 0), circle_mask(1254, 585)))

        fitted = raw.resize((1120, 1120), Image.Resampling.LANCZOS)
        master = Image.new("RGBA", (1254, 1254), (0, 0, 0, 0))
        master.paste(fitted, (67, 67), fitted)
        mask = circle_mask(1254, 560)
        master.putalpha(Image.composite(master.getchannel("A"), Image.new("L", master.size, 0), mask))
        master.save(MASTERS / f"{name}.png", optimize=True)

        widget = master.resize((256, 256), Image.Resampling.LANCZOS)
        out = RESOURCES / f"{name}.webp"
        widget.save(out, format="WEBP", quality=90, method=6)
        confirmed = Image.open(out).convert("RGBA")
        corners = [confirmed.getpixel((x, y))[3] for x, y in ((0, 0), (255, 0), (0, 255), (255, 255))]
        if any(corners):
            raise ValueError(f"{name}: alpha corners are {corners}")
        if out.stat().st_size > 40_000:
            raise ValueError(f"{name}: derivative is {out.stat().st_size} bytes")
        derivatives.append(confirmed)
        measurements.append({
            "id": name, "master_px": [1254, 1254], "derivative_px": [256, 256],
            "format": "WebP RGBA", "alpha_corners": corners,
            "bytes": out.stat().st_size,
            "sha256": hashlib.sha256(out.read_bytes()).hexdigest(),
        })
    render_sheet(derivatives)
    render_sheet(derivatives, tile_px=48)
    (BATCHES / "batch-01-10-measurements.json").write_text(
        json.dumps(measurements, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Prepared {len(measurements)} images; total derivative bytes: {sum(item['bytes'] for item in measurements)}")


if __name__ == "__main__":
    main()
