# Themes

Three user-selectable themes. Pick one from the swatches on Home or in
Settings → Colour Theme. Every screen follows the active theme. A saved theme
that no longer exists falls back to the default.

| Name | Character | Corners | Border | Faces |
|---|---|---|---|---|
| **Komiks** (default) | Violet / lime cartoon, halftone dots | 12dp, pill buttons | 2.5dp | Baloo 2 headings, Nunito text |
| **Książka** (paper book) | Aged paper, ink-brown text, oxblood rubrics, ink-blue Polish, gilt accents | 2dp | 1.2dp | Literata throughout |
| **Atrament** (e-ink) | Black on white for e-paper screens such as Boox | 4dp | 2dp | Literata throughout |

## Książka

`.book()` sets every UI face in Literata (the reading face of Google Play
Books): `uiText()` maps the Nunito weights onto Literata Regular and Bold.
Buttons are rectangular and the hard shadow is a pale paper tone, so cards read
as stacked pages.

## Atrament (e-ink)

Built for monochrome e-paper:

- Black and white only. Greys are kept dark (#2e2e2e–#7a7a7a for text tiers)
  so they survive 16-level greyscale instead of washing out.
- No shadows (the shadow colour is the page colour), no halftone or other
  texture, nothing that would ghost on a slow refresh.
- `.mono()`: Polish cannot be told apart from English by colour, so the guide
  and course set Polish in bold instead.
- Quiz feedback does not rely on colour: the right answer is filled black with
  white text, a wrong pick is light grey, and the verdict is written out.

## Adding a theme

Add one entry to `buildThemes()`. The 18 colour + 3 shape tokens are
positional (the argument order is documented in a comment there), then chain
any of:

```java
.display(font, scale, maxSp, wordmarkSp)   // font: baloo | literata
.book()                                     // all text in the book serif
.mono()                                     // mark Polish by weight, not colour
.effects(bevel, scanlines, gridHorizon, mastheadStripe, glow)
```

`effects()` drives Canvas drawables left from the retired console themes
(bevelled controls, CRT scanlines, grid horizon, block stripe, neon glow). No
current theme uses them; they are off by default and cost nothing.

## Fonts

All bundled, all SIL Open Font License; licences in `docs/licenses/`.

| Face | Used by | Weights |
|---|---|---|
| Nunito | Komiks text | 400/700/800/900 |
| Baloo 2 | Komiks headings | 600/700/800 |
| Literata | Książka and Atrament, all text | 400/650/700, instanced and subset (full Polish) |

The pixel faces of the retired console themes (Pixelify Sans, Jersey 15,
Press Start 2P) were removed with them.
