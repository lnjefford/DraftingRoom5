"""Rebuild DR5-050/051 artwork from the selected DR5-058 image edits."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
EDITED = ROOT / "edited-source-black"
MASTERS = ROOT / "masters"
BATCHES = ROOT / "batches"
RESOURCES = ROOT.parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"
BATCH_IDS = [
    [
        "widget_01_dumbbell", "widget_02_flex", "widget_03_fire", "widget_04_melt",
        "widget_05_orbit", "widget_06_jump_rope", "widget_07_wave",
        "widget_08_skateboard", "widget_09_spotlight", "widget_10_paper_plane",
    ],
    [
        "widget_11_kettlebell", "widget_12_chalk_clap", "widget_13_lightning",
        "widget_14_frost", "widget_15_alien_abduction", "widget_16_lying_down",
        "widget_17_pole_vault", "widget_18_slingshot", "widget_19_umbrella",
        "widget_20_butterfly",
    ],
]


def render_sheet(ids: list[str], derivatives: list[Image.Image], label: str, tile_px: int) -> None:
    cell_w, cell_h = 330, 330
    sheet = Image.new("RGB", (cell_w * 5, cell_h * 2), "#e8edf5")
    draw = ImageDraw.Draw(sheet)
    for index, (name, icon) in enumerate(zip(ids, derivatives)):
        left, top = index % 5 * cell_w, index // 5 * cell_h
        draw.rectangle((left + 8, top + 8, left + 158, top + 94), fill="#f7f8f8")
        draw.rectangle((left + 168, top + 8, left + 318, top + 94), fill="#1d2636")
        tile = icon.resize((tile_px, tile_px), Image.Resampling.LANCZOS)
        sheet.paste(tile, (left + 83 - tile_px // 2, top + 50 - tile_px // 2), tile)
        sheet.paste(tile, (left + 243 - tile_px // 2, top + 50 - tile_px // 2), tile)
        enlarged = icon.resize((208, 208), Image.Resampling.LANCZOS)
        sheet.paste(enlarged, (left + 61, top + 99), enlarged)
        draw.text((left + 12, top + 310), name, fill="#101b2d")
    suffix = "" if tile_px == 56 else f"-{tile_px}px"
    sheet.save(BATCHES / f"{label}-contact-sheet{suffix}.png")


def main() -> None:
    for ids, label in zip(BATCH_IDS, ("batch-01-10", "batch-11-20")):
        measurements: list[dict[str, object]] = []
        derivatives: list[Image.Image] = []
        for name in ids:
            original = Image.open(MASTERS / f"{name}.png").convert("RGBA")
            edited = Image.open(EDITED / f"{name}.png").convert("RGBA")
            if original.size != (1254, 1254) or edited.size != original.size:
                raise ValueError(f"{name}: expected matching 1254 px images")

            # Image-generation edits can paint a checkerboard outside the art.
            # The previous approved master supplies the precise alpha silhouette.
            edited.putalpha(original.getchannel("A"))
            edited.save(MASTERS / f"{name}.png", optimize=True)
            widget = edited.resize((256, 256), Image.Resampling.LANCZOS)
            out = RESOURCES / f"{name}.webp"
            widget.save(out, format="WEBP", quality=90, method=6)
            confirmed = Image.open(out).convert("RGBA")
            corners = [confirmed.getpixel(point)[3] for point in ((0, 0), (255, 0), (0, 255), (255, 255))]
            if any(corners):
                raise ValueError(f"{name}: nontransparent corners {corners}")
            if out.stat().st_size > 40_000:
                raise ValueError(f"{name}: derivative is {out.stat().st_size} bytes")
            derivatives.append(confirmed)
            measurements.append({
                "id": name, "master_px": [1254, 1254], "derivative_px": [256, 256],
                "format": "WebP RGBA", "alpha_corners": corners,
                "bytes": out.stat().st_size,
                "sha256": hashlib.sha256(out.read_bytes()).hexdigest(),
            })
        render_sheet(ids, derivatives, label, 56)
        render_sheet(ids, derivatives, label, 48)
        (BATCHES / f"{label}-measurements.json").write_text(
            json.dumps(measurements, indent=2) + "\n", encoding="utf-8"
        )
        print(f"{label}: {len(ids)} images, {sum(int(item['bytes']) for item in measurements)} bytes")


if __name__ == "__main__":
    main()
