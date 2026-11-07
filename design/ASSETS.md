# Assets

The launcher foreground was generated using the built-in GPT Image tool. Source: `icon-source.png`.
Prompt: Create one production Android adaptive launcher icon foreground for a minimalist font switching app named 字序. Transparent background. Flat vector-like graphic, no words, no mockup, no bounding square, no shadows or 3D or gradients. A single abstract typographic ligature resembling two softly rounded interlocking lowercase f stems, one dark petrol teal and one muted pale lime, with a small expressive rounded crossbar; optical balance and generous negative space. Google Material 3 Expressive shape language. Center the mark within the central 58% of a square image for adaptive cropping and a monochrome silhouette derivative.

Android layers use a 108 dp canvas. The artwork is padded further during packaging to keep the meaningful mark within the 66 dp safe zone. The monochrome layer uses the generated artwork's alpha silhouette; the launcher supplies its color.

UI icons: Google's Material Symbols Rounded, from https://github.com/google/material-design-icons (Apache License 2.0). `tools/prepare-assets.py` preserves Google's original SVG path geometry and converts coordinates to Android VectorDrawable. See `LICENSE-material-symbols.txt`.

The combination editor uses Google Fonts' [drag_indicator](https://fonts.google.com/icons?icon.query=drag_indicator) and [more_vert](https://fonts.google.com/icons?icon.query=more_vert), both available in Material Symbols Rounded. Their original 24px SVG paths are mechanically converted, without drawing or approximating geometry. Source SVG SHA-256 values verified on 2026-11-07:

- `drag_indicator`: `0996dc745ce9b9acdcd5b46d1641b1e1bd28b15af06541ddcbe829ffa83e8b89`
- `more_vert`: `f3959916d17e6ec783267c2753bdddfe4b3f4b12b835c875231dbbba792f070b`

For new UI icons, search Google Fonts first. If no suitable Material Symbol exists, use GPT Image with Material 3 Expressive guidance; do not hand-draw a substitute.
