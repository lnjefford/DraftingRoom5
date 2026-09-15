# DR5-058 — Black backing correction for images 01–20

The user corrected the collection’s dark outer silhouette from navy to black. Each existing master received a separate built-in image-generation **edit** with the exact constrained prompt and run filename recorded in [DR5-058-black-edit-provenance.json](DR5-058-black-edit-provenance.json). Original `generated-source/` images and their original prompts in the two batch records remain unchanged as historical provenance. Selected edit outputs are in `edited-source-black/`; `prepare_black_correction.py` carries forward the approved master alpha silhouettes and rebuilds the current 1254 px masters, 256 px WebPs, measurements, and contact sheets. The pre-correction sheets and measurements remain in `pre-black/`.

## Visual review

The refreshed [01–10 sheet](batch-01-10-contact-sheet.png) and [11–20 sheet](batch-11-20-contact-sheet.png) place each icon at actual 56 px on light and dark backgrounds; [01–10 at 48 px](batch-01-10-contact-sheet-48px.png) and [11–20 at 48 px](batch-11-20-contact-sheet-48px.png) repeat the smaller-size check. I compared these with the preserved `pre-black/` sheets. Each scene, 5, gold frame/prop, and accent remains recognizable without a new crop or stray text. `widget_07_wave` retains teal water while the outer field and empty center are black; the stage shadow in 09 retains its intentional lighting. All twenty pass the catalog’s six columns (recognition, silhouette, crop/alpha, contrast, finish, distinctness) at **2 / 2 / 2 / 2 / 2 / 2**.

| ID | True-black visible master pixels | Visual QA |
| --- | ---: | --- |
| widget_01_dumbbell | 56.5% | Pass at 56 and 48 px |
| widget_02_flex | 53.6% | Pass at 56 and 48 px |
| widget_03_fire | 52.9% | Pass at 56 and 48 px |
| widget_04_melt | 67.2% | Pass at 56 and 48 px |
| widget_05_orbit | 78.4% | Pass at 56 and 48 px |
| widget_06_jump_rope | 78.1% | Pass at 56 and 48 px |
| widget_07_wave | 22.3% | Pass at 56 and 48 px |
| widget_08_skateboard | 61.9% | Pass at 56 and 48 px |
| widget_09_spotlight | 27.9% | Pass at 56 and 48 px |
| widget_10_paper_plane | 55.7% | Pass at 56 and 48 px |
| widget_11_kettlebell | 61.3% | Pass at 56 and 48 px |
| widget_12_chalk_clap | 34.6% | Pass at 56 and 48 px |
| widget_13_lightning | 57.5% | Pass at 56 and 48 px |
| widget_14_frost | 43.4% | Pass at 56 and 48 px |
| widget_15_alien_abduction | 40.8% | Pass at 56 and 48 px |
| widget_16_lying_down | 66.2% | Pass at 56 and 48 px |
| widget_17_pole_vault | 87.0% | Pass at 56 and 48 px |
| widget_18_slingshot | 58.2% | Pass at 56 and 48 px |
| widget_19_umbrella | 56.9% | Pass at 56 and 48 px |
| widget_20_butterfly | 70.8% | Pass at 56 and 48 px |

## File checks

`audit_black_edits.py` confirmed true-black pixels in every current master (22.3–87.0% of fully visible pixels) and transparent corners in both masters and derivatives. The two refreshed measurements JSON files record exact dimensions, WebP RGBA format, transparent alpha corners, bytes, and SHA-256 values. All twenty derivatives are under 40 KB, total **288,214 bytes**, and retain distinct image hashes. The image-generation edits painted a checkerboard outside some discs; the rebuild uses each approved pre-edit master’s genuine alpha silhouette, removing that artifact without touching the scene. A new launcher capture was not available in this art-only correction; the 56/48 px sheets and prior DR5-048 host footprint are the current evidence.
