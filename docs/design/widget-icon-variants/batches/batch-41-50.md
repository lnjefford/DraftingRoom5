# DR5-054 · widget stills 41–50

Generated on 2026-09-14 with one distinct built-in `image_gen.imagegen` call per catalog scene. `batch-41-50-provenance.json` preserves each exact three-paragraph prompt, generated output reference, selected source, and the targeted moonwalk repair prompt. The original 45 and selected repair are both preserved in `generated-repairs/`; the final selection is in `generated-source/`.

`prepare_batch_41_50.py` fits each generated scene to a centered 1120 px true-black disc on a 1254 px transparent RGBA master. Its disc alpha mask guarantees transparent corners and keeps the shared outer geometry while preserving each scene's distinct internal gold-frame treatment. It writes 256 px WebPs and 2 × 5 contact sheets with actual 56 and 48 px icons on light and dark launcher-like fields, plus enlarged inspection views. The full 50-image sheet uses actual 56 px icons on both fields. Masters remain outside APK resources.

| ID | Single action | Silhouette | Crop/alpha | Contrast | Finish | Distinctness | 56/48 px review |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 41 punching bag | 2 | 2 | 2 | 2 | 2 | 2 | One broad punch connects with the suspended bag; broken right ring and gloves differ from the dumbbell/flex training looks. |
| 42 yoga | 2 | 2 | 2 | 2 | 2 | 2 | One-legged calm pose and low gold mat read without gym gear; quiet halo differs from the active training frames. |
| 43 prism | 2 | 2 | 2 | 2 | 2 | 2 | One central prism splits a single stroke into three broad rays; the pale-blue ray is a scene accent, not a dark backing. |
| 44 sandstorm | 2 | 2 | 2 | 2 | 2 | 2 | One thick sand gust wraps the partly buried 5; no funnel or separate desert landscape. |
| 45 moonwalk | 2 | 2 | 2 | 2 | 2 | 2 | Repaired generation enlarges the backward sliding leg and mirrored 5 below the moon, resolving the original's weak step. |
| 46 genie lamp | 2 | 2 | 2 | 2 | 2 | 2 | Broad ivory smoke curl becomes the 5 above one gold lamp; unlike the flame, this reads as a released vapor character. |
| 47 roller coaster | 2 | 2 | 2 | 2 | 2 | 2 | Single cart with a 5 rides the deep gold track dip; no extra cars or fine track supports. |
| 48 compass | 2 | 2 | 2 | 2 | 2 | 2 | One diagonal needle pivots through the 5 with two directional arcs, unlike the orbit or pendulum. |
| 49 kite | 2 | 2 | 2 | 2 | 2 | 2 | Sparse geometric 5-kite and one gold string remain clear against black; distinct from paper plane and balloon. |
| 50 curtain call | 2 | 2 | 2 | 2 | 2 | 2 | Two broad curtains part around a bowing 5, a distinct theatrical close without words or a spotlight shadow. |

Scores follow the catalog's 0–2 rubric. The 56 px and 48 px sheets show no unintended lettering, clipping, navy dark fields, duplicate composition, or identical inner gold frames. Every final-batch outer backing is a black circle with transparent corners. The deliberate reflected 5 in 45 and sparse scene in 49 were judged by action clarity and collection identity rather than requiring an intact inner ring.

Final-batch derivatives total **100,240 bytes**; the largest is 13,906 bytes. All ten masters are 1254 × 1254 RGBA and all ten runtime derivatives are 256 × 256 WebP RGBA with zero alpha at all four corners and distinct SHA-256 hashes. The full set contains **50 unique derivatives totaling 693,818 bytes**, below the 2 MB target; the largest individual file is 23,842 bytes. `batch-41-50-measurements.json` records final-batch source/master bounds, dimensions, bytes, alpha corners, and hashes; `all-50-measurements.json` records the same derivative checks for every ID. The full contact sheet was visually reviewed for duplicate concepts and inconsistent scale or identity; none were found. Runtime launcher behavior remains for DR5-055/056.
