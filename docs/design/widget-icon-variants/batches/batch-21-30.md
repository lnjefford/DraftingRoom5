# DR5-052 · widget stills 21–30

Generated 2026-09-14 with one built-in `image_gen.imagegen` call per distinct catalog asset. `batch-21-30-provenance.json` records every exact assembled prompt, output reference, targeted edit, regeneration, and selected source. The first outputs and rejected repair attempts for 25 and 26 remain in `generated-repairs/`; `generated-source/` contains the ten selected final sources.

`prepare_batch_21_30.py` fits each source's actual alpha bounds inside the centered 1120 px square, without an ellipse mask or painted disc. It writes 1254 px RGBA masters, 256 px alpha WebPs, `batch-21-30-measurements.json`, and labeled 2 × 5 sheets at actual 56 and 48 px on light and dark swatches. The sheets include an enlarged view for inspection. The prior two batch sheets were reviewed alongside the new sheet.

| ID | Single action | Silhouette | Crop/alpha | Contrast | Finish | Distinctness | 56/48 px review |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 21 climbing | 2 | 2 | 2 | 2 | 2 | 2 | Rope and hooked 5 remain separate and readable. |
| 22 balance beam | 2 | 2 | 2 | 2 | 2 | 2 | Extended upper stroke balances above a long straight beam. |
| 23 tornado | 2 | 2 | 2 | 2 | 2 | 2 | Broad funnel dominates; separated 5 fragments are secondary by design. Pale cool shading is confined to the storm, with no navy backing. |
| 24 geode | 2 | 2 | 2 | 2 | 2 | 2 | Irregular near-black rock splits around the large ivory crystal 5. |
| 25 portal | 2 | 2 | 2 | 2 | 2 | 2 | Regenerated after the initial and edited versions made a 6-like closed loop. The selected left slit, gold fold, and displaced curved fragment read as one crossing action; the 5 is deliberately incomplete. |
| 26 shadow puppet | 2 | 2 | 2 | 2 | 2 | 2 | Regenerated after the first stage minimized the hands and the edit painted a checkerboard. The selected angular black stage has large ivory hands and one cast black 5; corners are truly transparent. |
| 27 pendulum | 2 | 2 | 2 | 2 | 2 | 2 | Long gold suspension and one broad swing arc survive at 48 px. |
| 28 pinwheel | 2 | 2 | 2 | 2 | 2 | 2 | Four separated folded blades make a clear pinwheel silhouette. |
| 29 balloon | 2 | 2 | 2 | 2 | 2 | 2 | Balloon-shaped 5 and small lower string read without a circular backing. |
| 30 wind-up toy | 2 | 2 | 2 | 2 | 2 | 2 | Oversized side key, two feet, and low track distinguish the toy from earlier workout scenes. |

Scores use the catalog's 0–2 acceptance rubric. Nine of ten have unmistakably noncircular outer silhouettes at 56 px: 21–23 and 25–30. The geode is also irregular, but its compact rock outline is conservatively excluded from that count. Neither the masters nor the runtime WebPs have a final circular mask. The gold former frame becomes a rope anchor, straight beam, funnel arc, crystal facets, portal fold, stage arch, swing arc, wind path, string loop, or toy track rather than a repeated badge border.

All ten selected sources, masters, and WebPs have genuine alpha. Master alpha bounds lie within the 67–1187 px safe square; all 40 derivative corner alpha samples are zero. Every derivative is 256 × 256 WebP RGBA and under 40 KB; the ten together use **159,606 bytes** and have ten unique SHA-256 hashes. Dark scene backings are black/near-black, with no navy disc. The 30 prepared derivatives now total 447,820 bytes. `DR5-055` must remove the prototype widget provider's circular runtime mask before these scene silhouettes are displayed on a launcher.
