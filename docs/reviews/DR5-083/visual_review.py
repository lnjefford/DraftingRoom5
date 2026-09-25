"""Build review-only before/after crops; never changes screenshot references."""
from pathlib import Path
import json
import os
from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent / "visual"
OUT.mkdir(exist_ok=True)
RENDERED = Path(os.environ["LOCALAPPDATA"]) / "DraftingRoom5/repositories/cf5b26b6651a/build/app/outputs/screenshotTest-results/preview/debug/rendered"
REFERENCE = ROOT / "app/src/screenshotTestDebug/reference"
font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 16)
manifest_file = OUT / "manifest.json"
manifest = json.loads(manifest_file.read_text(encoding="utf-8")) if manifest_file.exists() else []
reviewed = {entry["path"] for entry in manifest}
panels = []
for candidate in sorted(RENDERED.rglob("*.png")):
    relative = candidate.relative_to(RENDERED)
    if relative.as_posix() in reviewed:
        continue
    reference = REFERENCE / relative
    after = Image.open(candidate).convert("RGB")
    before = Image.open(reference).convert("RGB") if reference.exists() else None
    if before and before.size == after.size:
        bbox = ImageChops.difference(before, after).getbbox()
        if bbox is None:
            continue
        bbox = (max(0, bbox[0]-15), max(0, bbox[1]-15), min(after.width, bbox[2]+15), min(after.height, bbox[3]+15))
    else:
        bbox = (0, 0, after.width, after.height)
    index = len(manifest)+1
    record = {"index": index, "path": relative.as_posix(), "status": "changed" if before else "new", "crop": bbox}
    manifest.append(record)
    crops = [im.crop(bbox) if im.size == after.size else im for im in (before, after) if im]
    scaled = []
    for crop in crops:
        ratio = min(1, 580 / crop.width, 1250 / crop.height)
        scaled.append(crop.resize((round(crop.width*ratio), round(crop.height*ratio))))
    panel = Image.new("RGB", (1200, max(i.height for i in scaled)+68), "#eeeeee")
    draw = ImageDraw.Draw(panel)
    draw.text((8, 5), f"{index:03d} {relative.name}", fill="black", font=font)
    draw.text((8, 29), "BEFORE" if before else "NEW CANDIDATE", fill="black", font=font)
    if before:
        draw.text((608, 29), "AFTER", fill="black", font=font)
    for i, crop in enumerate(scaled):
        panel.paste(crop, (8+i*600, 60))
    panels.append(panel)

page = []; height = 0; page_no = len(list(OUT.glob("differences-*.png")))
def write_page():
    global page_no
    page_no += 1
    canvas = Image.new("RGB", (1200, sum(p.height for p in page)), "white")
    y = 0
    for p in page:
        canvas.paste(p, (0, y)); y += p.height
    canvas.save(OUT / f"differences-{page_no:02d}.png")
for panel in panels:
    if page and height+panel.height > 3600:
        write_page(); page=[]; height=0
    page.append(panel); height += panel.height
if page:
    write_page()
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")
print(json.dumps({"changed_or_new": len(manifest), "pages": page_no}))
