"""Independent Phase 7 pixel/resource audit. Run with Python + Pillow from any cwd."""
import hashlib
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ART = ROOT / "docs/design/widget-icon-variants"
RES = ROOT / "app/src/main/res/drawable-nodpi"
source = (ROOT / "app/src/main/java/dev/draftingroom5/LivingIconWidgetProvider.kt").read_text()
names = re.findall(r"R\.drawable\.(widget_\d\d_\w+)", source)
expected = {a["id"]: a for a in json.loads((ART / "batches/all-50-measurements.json").read_text())["assets"]}
assert len(names) == len(set(names)) == 50
assert [int(n[7:9]) for n in names] == list(range(1, 51))
assert set(names) == set(expected) == {p.stem for p in RES.glob("widget_*.webp")}
records, hashes = [], set()
sheet = Image.new("RGB", (800, 700), "#e1e1e1")
draw = ImageDraw.Draw(sheet)
for i, name in enumerate(names):
    path = RES / (name + ".webp")
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    assert digest == expected[name]["sha256"]
    hashes.add(digest)
    im = Image.open(path).convert("RGBA")
    assert im.size == (256, 256) and len(data) < 40_000
    alpha = im.getchannel("A")
    assert all(alpha.getpixel(p) == 0 for p in [(0, 0), (255, 0), (0, 255), (255, 255)])
    bounds = alpha.getbbox()
    assert min(bounds[:2]) >= 10 and max(bounds[2:]) <= 246
    master = Image.open(ART / "masters" / (name + ".png"))
    assert master.size == (1254, 1254) and master.mode == "RGBA"
    assert (ART / "generated-source" / (name + ".png")).exists()
    pixels = list(im.getdata())
    black = sum(p[:3] == (0, 0, 0) and p[3] == 255 for p in pixels)
    opaque = sum(p[3] == 255 for p in pixels)
    # Filled-disc occupancy, independent of scene color; free shapes must retain their own gaps.
    inner = [(x, y) for y in range(256) for x in range(256) if (x-127.5)**2 + (y-127.5)**2 < 105**2]
    inner_opaque = sum(alpha.getpixel(p) >= 250 for p in inner) / len(inner)
    if i >= 40:
        assert black > 8_000 and inner_opaque > .99, (name, black, inner_opaque)
    x, y = (i % 5) * 160, (i // 5) * 70
    small = im.resize((48, 48), Image.Resampling.LANCZOS)
    for offset, color in [(0, "#ffffff"), (74, "#1c222c")]:
        draw.rectangle((x+offset, y, x+offset+69, y+55), fill=color)
        sheet.paste(small, (x+offset+11, y+4), small)
    draw.text((x+7, y+57), f"{i+1:02}", fill="black")
    records.append(dict(id=name, bytes=len(data), sha256=digest, alpha_bounds=bounds,
                        opaque_pixels=opaque, true_black_pixels=black, inner_disc_occupancy=round(inner_opaque, 5)))
assert len(hashes) == 50
assert sum(r["bytes"] for r in records) == 693818
assert sum(r["inner_disc_occupancy"] < .85 for r in records[20:30]) >= 6
assert sum(r["inner_disc_occupancy"] < .85 for r in records[30:40]) >= 6
sheet.save(HERE / "independent-48px-sheet.png")
(HERE / "asset-audit.json").write_text(json.dumps({"count": 50, "total_bytes": 693818, "assets": records}, indent=2) + "\n")
print("PASS: 50 catalog/resource/hash/master/source matches; genuine alpha; safe bounds; black discs 41–50; free silhouettes 21–40; 693818 bytes")
