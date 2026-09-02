# Themes

Seven user-selectable themes. Pick one from the swatch row on Home, or in
Settings → Colour Theme. Every screen follows the active theme.

## Cartoon set (original)

| Name | Character | Radius | Border | Texture |
|---|---|---|---|---|
| **Komiks** (default) | Violet / lime, comic-book | 12dp | 2.5dp | halftone dots |
| **Borówka** | Blue / coral | 18dp | 2.5dp | — |
| **Mięta** | Mint / red | 18dp | 2.5dp | — |
| **Zachód** | Sunset orange / purple | 18dp | 2.5dp | — |

Display face: Baloo 2. Buttons are pills.

## Retro console set

Added alongside the cartoon set — nothing about the four above changed.

| Name | Character | Radius | Border | Display face |
|---|---|---|---|---|
| **Szesnastka** (16-bit) | Muted violet console, bevelled controls | 6dp | 2.5dp | Pixelify Sans |
| **Automat** (arcade) | Dark neon cabinet, magenta/cyan | 2dp | 2dp | Jersey 15 |
| **Kieszonka** (handheld) | Teal pocket console, square corners | 0dp | 3dp | Press Start 2P |

Console themes use rectangular buttons rather than pills, and set headings,
kickers, buttons and nav labels in their display face.

### Theme-specific effects

Each is a Canvas drawable — no images, nothing that animates.

- **Szesnastka** — `BevelDrawable` puts an inset highlight on the top-left and
  a shade on the bottom-right of every button, chip and list row. A
  `BlockStripe` of repeating 24dp colour blocks runs under the wordmark.
- **Automat** — `GridHorizonDrawable` draws a perspective grid behind the
  masthead. `ScanlineDrawable` overlays 1dp CRT lines every 3dp across the
  whole screen; it is non-interactive, so taps pass through, and it can be
  turned off in Settings → Colour Theme → *Scanlines*. `ShadowLayout` swaps its
  hard offset shadow for a blurred neon glow. It is the only dark theme, and
  the status bar adapts automatically via the luminance check in
  `isDarkTheme()`.
- **Kieszonka** — no extra drawables; the look comes from 0dp corners and a
  3dp border.

## Adding a theme

Add one entry to `buildThemes()`. The 18 colour + 3 shape tokens are
positional (the argument order is documented in a comment there), then chain:

```java
.display(font, scale, maxSp, wordmarkSp)   // font: baloo|pixelify|jersey|pressstart
.effects(bevel, scanlines, gridHorizon, mastheadStripe, glow)
```

`display()` exists because display sizes are authored for Baloo 2; pixel faces
run much wider, so each theme scales the shared type scale rather than every
call site being rewritten per theme. `scale` multiplies every display size,
`maxSp` caps it (Press Start 2P is capped at 20sp), and `wordmarkSp` sets the
masthead independently. Calling `effects()` also switches the theme to
rectangular buttons.

## Fonts

All bundled, all SIL Open Font License; licences in `docs/licenses/`.

| Face | Used by | Weights |
|---|---|---|
| Nunito | body and UI, every theme | 400/700/800/900 |
| Baloo 2 | cartoon display | 600/700/800 |
| Pixelify Sans | Szesnastka display | 600/700 (instanced from the variable font) |
| Jersey 15 | Automat display | 400 |
| Press Start 2P | Kieszonka display | 400 |

### Substitution: Silkscreen → Jersey 15

The design spec asked for **Silkscreen** as the Automat display face. Silkscreen
ships no Polish diacritics — it is missing 17 of the 18 characters
`ąćęłńóśźżĄĆĘŁŃÓŚŹŻ` (only `ó` is present). In an app whose entire content is
Polish, headlines and flashcard words would have fallen back to the system font
mid-word.

**Jersey 15** was substituted: a blocky pixel face in the same spirit, with
complete Polish coverage. Press Start 2P and Pixelify Sans were both checked
and cover Polish in full, so they were used as specified.

If the designer wants a different substitute, VT323 (terminal/CRT), Micro 5 and
Jersey 10 were also verified as covering Polish completely.
