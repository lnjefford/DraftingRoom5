# DR5-051 — Widget artwork batch 11–20

Ten distinct assets generated with one built-in image-generation call per concept. The complete, exact final prompts, original and selected run filenames, references, user-directed catalog deviations, and two repair prompts are recorded in [batch-11-20-provenance.json](batch-11-20-provenance.json). No source reference images were supplied; the 01–10 contact sheet was used for visual comparison after generation.

**DR5-058 color correction:** The original generation prompts remain in that JSON as historical provenance. The current masters, WebPs, measurements, and contact sheets use black outer backings after separate image-generation edits. See [black-edit provenance](DR5-058-black-edit-provenance.json) and [correction QA](DR5-058-black-correction.md). Pre-correction measurements and sheets remain in `pre-black/`.

Source PNGs are in `generated-source/`, normalized 1254 × 1254 RGBA masters in `masters/`, and runtime 256 × 256 WebP files in `app/src/main/res/drawable-nodpi/`. `prepare_batch_11_20.py` reproduces the master normalization, circular alpha mask, derivative encoding, size checks, measurements, and 2 × 5 contact sheets.

## Visual QA

Reviewed [56 px contact sheet](batch-11-20-contact-sheet.png) and [48 px contact sheet](batch-11-20-contact-sheet-48px.png) on light and dark backgrounds at their actual embedded pixel size, alongside enlarged inspection views. Compared with [batch 01–10](batch-01-10-contact-sheet.png); no scene repeats an earlier action or silhouette. Scores follow the catalog rubric: recognition, silhouette, crop/alpha, contrast, finish, distinctness; 2 means pass.

| ID | Recogn. | Silhouette | Crop/alpha | Contrast | Finish | Distinct | Reviewer note |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| widget_11_kettlebell | 2 | 2 | 2 | 2 | 2 | 2 | Kettlebell handle and compressed platform |
| widget_12_chalk_clap | 2 | 2 | 2 | 2 | 2 | 2 | Clapping palms, burst, opened ring arcs |
| widget_13_lightning | 2 | 2 | 2 | 2 | 2 | 2 | Single strike and split frame |
| widget_14_frost | 2 | 2 | 2 | 2 | 2 | 2 | Crystal merges with frozen crescent |
| widget_15_alien_abduction | 2 | 2 | 2 | 2 | 2 | 2 | Large 5 lifted through beam; frame stretched |
| widget_16_lying_down | 2 | 2 | 2 | 2 | 2 | 2 | Resting horizontal 5, folded pillow, moon |
| widget_17_pole_vault | 2 | 2 | 2 | 2 | 2 | 2 | 5 clears peeled-away pole and bar |
| widget_18_slingshot | 2 | 2 | 2 | 2 | 2 | 2 | Forked frame launches a 5 |
| widget_19_umbrella | 2 | 2 | 2 | 2 | 2 | 2 | Canopy formed from upper arc |
| widget_20_butterfly | 2 | 2 | 2 | 2 | 2 | 2 | 5 and ring dissolve into wings |

The initial `widget_15_alien_abduction` had a 5 too small to register at 56 px; the selected regeneration enlarges it while keeping the broken ring and beam. The initial `widget_16_lying_down` was clipped at the lower disc edge; the selected regeneration moves the full resting silhouette inside the safe zone. Both repairs were rechecked at 56 and 48 px. Other eight first outputs passed.

## File checks

[Measurements](batch-11-20-measurements.json) record dimensions, RGBA WebP format, four alpha-corner values, byte size, and SHA-256 for each derivative. All corners are transparent, each file is under 40 KB, all ten hashes are unique, and the current black-backed runtime images total **147,376 bytes**. No stray text or watermark is visible. A fresh 1 × 1 launcher capture was not available in this art-only task; DR5-048 established the host footprint, and later widget integration will recheck the complete set.
