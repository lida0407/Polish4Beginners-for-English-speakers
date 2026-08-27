# Theme system — cartoon set

Four user-selectable themes, chosen from the swatches in the Home masthead.
The choice applies to **every screen**, not just Home.

| Theme | Palette | Radius | Texture |
|---|---|---|---|
| **Komiks** (default) | violet + lime on lavender | 12dp | halftone dots |
| **Borówka** | blueberry blue, lemon, coral | 18dp | none |
| **Mięta** | mint field, strawberry, mango | 18dp | none |
| **Zachód** | peach sunset, tangerine, violet, aqua | 18dp | none |

## How it reaches every screen

Themes are code objects in `buildThemes()`. All screens draw through shared
helpers, so a theme change propagates automatically:

- `rounded(fill, stroke, th.radius, th.border)` — every card, row, chip and
  panel. There are no literal corner radii left at call sites.
- `flatButton` / `filledButton` — pill radius (`max(theme radius, height/2)`),
  thick ink outline, display typeface.
- `shadowWrap` — hard offset shadow, no blur, drawn in `shadow` (= `ink`),
  corner radius follows the theme.
- `HalftoneDrawable` — the Komiks dot field, applied to the screen root.

## Tokens

The original 17 colour roles, plus four new ones:

| Token | Purpose |
|---|---|
| `accent3` | third accent — the third stat cell, More-screen chips |
| `radius` | card corner radius (dp) |
| `border` | card / button outline width (dp) |
| `halftone` | whether to draw the dot texture |

`isDarkTheme()` is derived from background luminance rather than a theme name,
so status-bar contrast stays correct if a dark cartoon theme is added later.

## Type

- **Display — Baloo 2** (SemiBold / Bold / ExtraBold): titles, card words,
  numbers, buttons. Polish text remains the visual hero.
- **UI — Nunito** (Regular / Bold / ExtraBold / Black): everything else.

Both are SIL OFL, bundled as static instances generated from the Google Fonts
variable originals (minSdk 23 cannot vary weight at runtime). Full Polish
diacritic coverage was verified before bundling. Licences are in
`docs/licenses/`.

## Navigation

Bottom nav is **5 tabs**: `Dom · Karty · Słuchaj · Czytaj · Więcej`.

- **Czytaj** groups News and Conversations behind a top toggle.
- **Więcej** lists Grammar, Alphabet, Translate and Settings.
- Back from any of those returns to its parent tab.

## Migration

Users with an old theme saved (`Klasyczny`, `Las`, `Bałtyk`, `Wrzos`,
`Atrament`) fall back to the default automatically — `buildThemes()` is checked
with `containsKey` before use, so no one is stranded on a missing theme.
