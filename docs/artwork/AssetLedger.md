# Production artwork asset ledger

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
