# Production artwork asset ledger

## Rice-bag exercise artwork — 2026-09-13

- Tool: OpenAI built-in image generation; managed image model identifier not exposed. Orchestrating model: `gpt-5.6-sol`.
- Source: one original generated, unbranded bag-only cutout. The approved `docs/design/phase-6-polish/rice-bag-artwork-option-a-approved.png` supplied direction, while the existing grip-hold and finger-extension exercise masters supplied catalog lighting context. No reference pixels were copied. No person, text, logo, or product identity was requested.
- Master: `docs/artwork/masters/exercise_rice_bag.png`, 1254 × 1254 RGBA with true transparent alpha. The built-in 1214 × 1295 render was trimmed, proportionally fitted within a 1110px box, and centered on a transparent square canvas without changing its subject.
- Runtime: `exercise_rice_bag_list.webp` (320 × 320) and `exercise_rice_bag_header.webp` (960 × 480), independently composed by `prepare_exercise_artwork.py`. No routine artwork or seeded exercise uses this ID.
- Rights/provenance: generated specifically for DraftingRoom5 under the applicable OpenAI output terms; generic equipment rather than a manufacturer design.

### Final prompt

> Use case: transparent product cutout for an Android exercise-art catalog. Create one original, photorealistic, generic portable rice-training grip bag as the ONLY subject: compact upright cylindrical dark-charcoal/black neoprene fabric pouch, rounded stable body and base, open rolled cuff seen slightly from above with only a restrained glimpse of pale uncooked rice inside, one short flat black webbing loop on the right side. Three-quarter front view, object centered with generous transparent padding so it can be cropped into a square list tile and a wide guided-session header. Genuine fully transparent background and clean alpha edges, no ground, no cast shadow, no glow outside the object. Subtle warm amber light on the left rim and cool cyan-blue light on the right rim, matching a premium dark fitness-equipment catalog. Fabric texture legible at phone size. No hand or person, no grocery sack, no drawstring, no text, no logo, no branding or manufacturer-specific features. The approved direction is a bag-only equipment cutout; do not include a UI, label, caption, phone, or comparison board.

### Inventory addition

| Stable ID | Subject request | Master | Paired variants | Status |
| --- | --- | --- | --- | --- |
| `rice_bag` | Compact charcoal neoprene rice-training pouch with open cuff. | `exercise_rice_bag.png` | list, header | Approved exercise-only asset |

## Hangboard exercise and routine artwork — 2026-09-13

- Tool: OpenAI built-in image generation; managed image model identifier not exposed. Orchestrating model: `gpt-5.6-sol`.
- Source: original generated equipment cutouts. The approved `docs/design/phase-6-polish/hangboard-artwork-option-a-approved.png` established the equipment-only direction; existing `exercise_dead_hang.png`, `exercise_grip_hold.png`, and `routine_grip_trainer.png` established catalog style. No pixels from those references, any purchased board, or third-party imagery were copied into the masters. The routine render used the generated exercise render as an identity reference. No logo, text, person, or watermark was requested.
- Masters: `docs/artwork/masters/exercise_hangboard.png` and `docs/artwork/masters/routine_hangboard.png`, each 1254 × 1254 transparent RGBA, centered from separate built-in renders with preserved alpha. The built-in returned wide 1993 × 789 cutouts; the project masters place their complete silhouettes with transparent margin on square canvases.
- Runtime: `exercise_hangboard_{list,header}.webp` and `routine_hangboard_{card,header,picker}.webp` in `app/src/main/res/drawable-nodpi/`, produced independently for each crop by the existing preparation scripts. Both contact sheets were refreshed for small-size visual review. Stable catalog ID: `hangboard` in each catalog; the seeded Forearm routine retains its `grip_trainer` routine art while adding one Hangboard Holds exercise.
- Rights/provenance: generated specifically for DraftingRoom5 under the applicable OpenAI output terms; original generic composite-equipment subject, no branded product source or manufacturer-specific design.

### Final exercise prompt

> Use case: product-mockup. Asset type: transparent 1254x1254 Android exercise-artwork master for list thumbnail and editorial header crops. Create an original generic training hangboard as an isolated equipment-only tactile 3D product illustration, realistic but slightly softened to match a premium dark fitness-equipment artwork catalog. One compact horizontal symmetrical dark-charcoal composite hangboard, gently beveled outer corners, balanced rows of distinct recessed finger pockets/edges with clear depth, plausible generic geometry without copying a real manufacturer's shape. Nearly front-on subtle three-quarter depth, centered with generous true transparent margin on all sides, complete silhouette, large and legible at phone thumbnail size. Soft controlled studio light; restrained warm gold/amber rim on viewer-left and cool electric-blue/mint rim on viewer-right; deep navy shadow within recesses; crisp clean alpha edges. No people, hands, branding, logo, writing, numerals, labels, wood grain, mounting wall, floor, platform, environment, background, border, watermark, UI, or baked rectangular shadow. The background must be genuinely transparent, not a checkerboard or solid color.

### Final routine prompt

> Use case: product-mockup. Asset type: matching transparent Android routine-artwork master for cards, editor headers, and picker. Generate a new clean studio view of exactly the same original generic dark-charcoal composite hangboard shown in the reference image. Preserve its symmetrical horizontal finger-pocket structure and equipment-only identity, warm-left / cool-electric-blue-right rim lighting, dark tactile material, and true transparent cutout. Compose it slightly from above in shallow three-quarter perspective with substantial transparent margin so it reads at small card and picker sizes. No hands, person, manufacturer shape, logo, text, numerals, wood grain, environment, wall, platform, background, or watermark. The result must have genuine alpha and crisp complete silhouette.

## Dashboard hero — reviewed 2026-09-11

- Runtime asset: `app/src/main/res/drawable-nodpi/dashboard_athlete_hero.png`, used only in the Dashboard training hero.
- Source status: project-owned artwork introduced for the DR5-008 dashboard implementation. No external stock asset, design-reference crop, trademark, text, logo, or watermark is recorded; the original generation prompt and tool metadata were not retained.
- Release handling: the app provides a complete text-only fallback if the image cannot decode. The asset was inspected in the compact, tall, and 2x-font native Dashboard renders for legibility, safe text overlap, and contained cropping.
- Superseded asset: the unused `session_dumbbell.png` legacy image was removed at the core-shell release milestone; session cards now use stable routine-catalog artwork.

## Routine artwork family — 2026-09-10

- Tool: OpenAI built-in image generation.
- Generation model: managed built-in image model; the tool did not expose a model identifier.
- Orchestrating model: `gpt-5.6-sol`.
- Source and license status: generated specifically for DraftingRoom5 without input imagery. No mockup pixels or third-party source files were used. Outputs contain no intentional trademarks, brand marks, people, text, or watermarks and are retained as project production assets under the applicable OpenAI output terms.
- Masters: `docs/artwork/masters/routine_<id>.png`, 1024 × 1024 transparent PNG.
- Runtime crops: `app/src/main/res/drawable-nodpi/routine_<id>_{card,header,picker}.webp`.
- Processing: `prepare_routine_artwork.py` trims transparent margins, preserves each complete silhouette, places it inside fixed transparent canvases, and exports WebP at quality 86. Card is 720 × 480 with a right-third bias; header is 960 × 480 with left-side text space; picker is a centered 320 × 320 crop.
- Review: `RoutineArtworkContactSheet.png` was inspected at phone-like thumbnail size on the app navy surface. All silhouettes remain distinct, no subject is clipped, the narrow machines remain recognizable, and card/header negative space is safe. Status: approved for implementation.

### Shared prompt

> Use case: product-mockup. Asset type: Android fitness routine artwork master for cards, headers, and pickers. Style/medium: polished tactile 3D product illustration, realistic but slightly softened, coordinated catalog style. Scene/backdrop: genuinely transparent background, isolated cutout only. Composition/framing: landscape-friendly square master; equipment centered slightly right, generous transparent margin, complete silhouette, no cropping. Lighting/mood: soft controlled studio lighting from upper left, subtle navy and mint reflected light, restrained warm-gold highlight. Color palette: gunmetal, deep navy, muted blue, subtle mint and warm gold accents. Materials/textures: realistic equipment materials. Constraints: one coherent object grouping only; unbranded generic equipment; no people, floor, platform, background, text, logo, watermark, labels, letters, numbers, or UI; crisp alpha edges; no baked shadow beyond a very soft tight contact shadow; readable at phone thumbnail size.

### Asset inventory

| Stable ID | Subject request | Master | Variants | Status |
| --- | --- | --- | --- | --- |
| `generic` | Two small weight plates and a rolled resistance band; neutral general-training silhouette. | `routine_generic.png` | card, header, picker | Approved fallback |
| `dumbbell` | Generic adjustable dumbbell, three-quarter view. | `routine_dumbbell.png` | card, header, picker | Approved |
| `grip_trainer` | Spring hand-grip trainer with two dark handles. | `routine_grip_trainer.png` | card, header, picker | Approved |
| `running_shoe` | Unbranded navy running shoe with ivory midsole. | `routine_running_shoe.png` | card, header, picker | Approved |
| `kettlebell` | Generic cast-iron kettlebell. | `routine_kettlebell.png` | card, header, picker | Approved |
| `leg_day` | Compact half squat rack with barbell and plates. | `routine_leg_day.png` | card, header, picker | Approved |
| `full_body` | Kettlebell, dumbbell, and resistance-band grouping. | `routine_full_body.png` | card, header, picker | Approved |
| `push_day` | Flat bench with paired dumbbells. | `routine_push_day.png` | card, header, picker | Approved |
| `pull_day` | Compact lat-pulldown tower emphasizing bar, cable, pulley, and seat. | `routine_pull_day.png` | card, header, picker | Approved |
| `jump_rope` | Speed rope with two handles and a clean cable loop. | `routine_jump_rope.png` | card, header, picker | Approved |
| `stopwatch` | Generic analog sports stopwatch with blank face and no numerals. | `routine_stopwatch.png` | card, header, picker | Approved |

## Exercise artwork family — 2026-09-10

- Tool: OpenAI built-in image generation.
- Generation model: managed built-in image model; the tool did not expose a model identifier.
- Orchestrating model: `gpt-5.6-sol`.
- Source and license status: generated specifically for DraftingRoom5 without input imagery. No mockup pixels or third-party source files were used. Outputs contain no intentional trademarks, brand marks, text, or watermarks and are retained as project production assets under the applicable OpenAI output terms.
- Masters: `docs/artwork/masters/exercise_<id>.png`, 1254 × 1254 transparent PNG.
- Runtime pairs: `app/src/main/res/drawable-nodpi/exercise_<id>_{list,header}.webp`.
- Processing: `prepare_exercise_artwork.py` trims transparent margins and independently composes each source on a centered 320 × 320 list canvas and a 960 × 480 right-weighted header canvas. WebP exports use quality 86 and preserve alpha.
- Review: `ExerciseArtworkContactSheet.png` presents every pair on the app navy surface at phone-like size. List silhouettes and header text-safe regions must be inspected together before approval.

### Shared prompt

> Use case: product-mockup. Asset type: Android fitness exercise artwork master for paired list and wide header compositions. Style/medium: polished tactile 3D product illustration, realistic but slightly softened, coordinated premium fitness catalog style. Scene/backdrop: genuinely transparent background, isolated cutout only. Composition/framing: square master, complete silhouette centered slightly right, generous transparent margin, no cropping, readable at phone thumbnail size. Lighting/mood: soft controlled studio lighting from upper left with restrained electric-blue and mint rim light and a subtle warm-gold highlight. Color palette: dark gunmetal, deep navy, muted blue, subtle mint and warm gold. Constraints: generic and unbranded; no floor, platform, background, text, logo, watermark, labels, letters, numbers, or UI; crisp alpha edges.

### Asset inventory

| Stable ID | Subject request | Master | Paired variants | Status |
| --- | --- | --- | --- | --- |
| `generic` | Compact forearm-training kit: dumbbell, hand gripper, and resistance band. | `exercise_generic.png` | list, header | Approved fallback |
| `dead_hang` | Textured thick pull-up bar between short rack uprights. | `exercise_dead_hang.png` | list, header | Approved |
| `farmers_walk` | Paired unbranded hex dumbbells. | `exercise_farmers_walk.png` | list, header | Approved |
| `grip_hold` | Paired hanging pinch plates. | `exercise_grip_hold.png` | list, header | Approved |
| `wrist_curl` | Palm-up dumbbell wrist curl with forearm only. | `exercise_wrist_curl.png` | list, header | Approved |
| `reverse_wrist_curl` | Palm-down dumbbell wrist curl with forearm only. | `exercise_reverse_wrist_curl.png` | list, header | Approved |
| `finger_extension` | Two hands opening a mint finger-extension band. | `exercise_finger_extension.png` | list, header | Approved |
| `wrist_rotation` | Weighted exercise lever turning through pronation/supination. | `exercise_wrist_rotation.png` | list, header | Approved |
