# DR5-053 · widget stills 31–40

Generated on 2026-09-14 with one distinct built-in `image_gen.imagegen` call for each catalog scene. `batch-31-40-provenance.json` records the exact assembled prompts, generated output references, selected sources, and targeted repair attempts. Rejected versions for 33, 35, 37, and 38 are preserved in `generated-repairs/`; selected sources are in `generated-source/`. After multiple imagegen attempts could not produce a transparent cutout in 38, the catalog scene prompt was revised to a large sculptural 5 on the middle of three clearly falling dominoes. The final asset is a new generation using that revised exact three-paragraph prompt, not a relabel of the earlier ambiguous image.

Queue timing: DR5-052 completed at 14:32:21 CDT, so the normal 60-minute gate would have held DR5-053 until 15:32:21 CDT. The user explicitly requested immediate dispatch, and DR5-053 was claimed at 14:55:12 CDT. No other TODO task was running at claim time.

`prepare_batch_31_40.py` fits each source's actual alpha bounds into the centered 1120 px square, without an ellipse mask or painted disc. It writes 1254 px RGBA masters, 256 px alpha WebPs, measured bytes and hashes, and labeled 2 × 5 contact sheets with actual 56 and 48 px previews on light and dark backgrounds. The 01–30 sheets were compared alongside this batch.

| ID | Single action | Silhouette | Crop/alpha | Contrast | Finish | Distinctness | 56/48 px review |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 31 sprint start | 2 | 2 | 2 | 2 | 2 | 2 | Angled 5 pushes off two gold blocks, unlike the jumping and vaulting scenes. |
| 32 rowing | 2 | 2 | 2 | 2 | 2 | 2 | Single oar, crescent boat and low wake remain distinct from the wave. |
| 33 rain cloud | 2 | 2 | 2 | 2 | 2 | 2 | Fresh regeneration replaced blue cloud mass with black; three ivory bars and gold sun arc survive at 48 px. |
| 34 coral | 2 | 2 | 2 | 2 | 2 | 2 | Teal fronds and gold branching stem make a distinct irregular underwater silhouette. |
| 35 impossible stair | 2 | 2 | 2 | 2 | 2 | 2 | Fresh regeneration enlarged the upside-down 5 under broad angular steps. |
| 36 origami | 2 | 2 | 2 | 2 | 2 | 2 | One angular ivory paper fold forms the 5 without repeating the paper plane. |
| 37 spring | 2 | 2 | 2 | 2 | 2 | 2 | Fresh regeneration gave the coil two large turns under the 5 and removed the cluttered rim. |
| 38 domino | 2 | 2 | 2 | 2 | 2 | 2 | Three slabs in distinct falling stages read as one impact chain, with a large sculptural 5 on the middle tile and one gold frame fragment at the first. |
| 39 juggling | 2 | 2 | 2 | 2 | 2 | 2 | Three large gold balls and an expressive 5 pose read without facial detail. |
| 40 treasure | 2 | 2 | 2 | 2 | 2 | 2 | Gold chest reveals one large ivory 5, distinct from the geode's mineral reveal. |

Scores follow the catalog's 0–2 rubric. All ten outer alpha silhouettes are unmistakably noncircular at 56 px, with genuine scene-shaped transparency and no runtime circular normalization. Dark masses are black rather than navy; blue appears only in scene-specific water/teal accents. The first imagegen edits for 33 and 37 painted opaque checkerboard rectangles, so they were discarded and regenerated; the selected files have transparent corners and no visible matte.

Final measurements: ten 256 × 256 WebP RGBA derivatives use **145,758 bytes** total; each is below 40 KB and has a unique SHA-256. All masters are 1254 × 1254 RGBA, with alpha bounds inside the 67–1187 px safe square. All derivative corner alpha samples are zero. IDs 01–40 are visually distinct on the four batch sheets. The selected 38 is a new readable domino action with the revised catalog treatment; all six rubric columns now pass for all ten assets.
