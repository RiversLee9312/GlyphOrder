# Assets

The launcher foreground was generated using the built-in GPT Image tool. Source: `icon-source.png`.
Prompt: Create one production Android adaptive launcher icon foreground for a minimalist font switching app named 字序. Transparent background. Flat vector-like graphic, no words, no mockup, no bounding square, no shadows or 3D or gradients. A single abstract typographic ligature resembling two softly rounded interlocking lowercase f stems, one dark petrol teal and one muted pale lime, with a small expressive rounded crossbar; optical balance and generous negative space. Google Material 3 Expressive shape language. Center the mark within the central 58% of a square image for adaptive cropping and a monochrome silhouette derivative.

Android layers use a 108 dp canvas. The artwork is padded further during packaging to keep the meaningful mark within the 66 dp safe zone. The monochrome layer uses the generated artwork's alpha silhouette; the launcher supplies its color.

UI icons: Google's Material Symbols Rounded, from https://github.com/google/material-design-icons (Apache License 2.0). `tools/prepare-assets.py` preserves Google's original SVG path geometry and converts coordinates to Android VectorDrawable. See `LICENSE-material-symbols.txt`.
