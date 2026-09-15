"""Audit current black-backed DR5-058 masters and WebP derivatives."""

from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parent
RESOURCES = ROOT.parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"


def main() -> None:
    paths = sorted((ROOT / "edited-source-black").glob("widget_*.png"))
    if len(paths) != 20:
        raise ValueError(f"Expected 20 selected edits, found {len(paths)}")
    for path in paths:
        master = np.asarray(Image.open(ROOT / "masters" / path.name).convert("RGBA"))
        widget = np.asarray(Image.open(RESOURCES / f"{path.stem}.webp").convert("RGBA"))
        visible = master[:, :, 3] >= 240
        black = (master[:, :, :3].max(axis=2) <= 3) & visible
        fraction = float(black.sum() / visible.sum())
        if fraction < 0.05:
            raise ValueError(f"{path.stem}: too little black backing ({fraction:.3f})")
        for array in (master, widget):
            if any(array[y, x, 3] for x, y in ((0, 0), (array.shape[1] - 1, 0), (0, array.shape[0] - 1), (array.shape[1] - 1, array.shape[0] - 1))):
                raise ValueError(f"{path.stem}: nontransparent corner")
        print(f"{path.stem}: {fraction:.1%} true-black visible master pixels; alpha corners clear")


if __name__ == "__main__":
    main()
