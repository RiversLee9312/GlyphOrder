# ColorOS font compatibility

Research and device checks: 2026-11-07. These findings apply to the tested OnePlus 13 / Android 17 build; community reports for other releases are identified separately. The investigation prioritizes project authors' code, issues and firsthand technical accounts. No third-party font module was installed for comparison.

## Findings reproduced on the device

### OEM configuration selection

The device's `oplus-framework.jar` implements `SystemFontExtImpl.StaticExtImpl.getSystemFontConfig()` using `/system_ext/etc/fonts_base.xml`, `/system_ext/etc/fonts_customization.xml` and `/system_ext/fonts`. The presence of AOSP `font_fallback.xml` does not mean it is the configuration used by this framework. `fonts_ule.xml` also exists, but was not the active entry point in this inspected implementation.

This agrees with the version-specific paths recorded by [lxgw's module template](https://github.com/lxgw/simple-cjk-font-magisk-module-template#兼容性调整-仅供参考), [OneplusYingHeiFont for ColorOS 15](https://github.com/ellermister/OneplusYingHeiFont-For-ColorOS#realmeuicoloros-特别说明), and [FontMM's device XML scanning](https://github.com/YuleBest/FontMM/blob/main/src/customize.sh). GlyphOrder reads existing configurations and preserves their separate target scopes and partition paths. It does not replace every XML with one generic template.

### OEM family substitution

The inspected `OplusFontUtils` contains `shouldReplaceToOSans`, `getReplaceTypefaceName`, `replaceTypefaceWithVariation` and `updateOSansConfig`. Initially, the device's packed `font_variation_settings` value selected mode 3. Native shaping confirmed that requesting serif or monospace could still use the OEM-selected sans family. Correct XML alone cannot undo a later framework substitution.

Independent implementations recognize this problem: [MFGA recommends the Roboto system mode](https://github.com/Numbersf/MakeFontsGreatAgain/blob/main/README-en.md), while [ColorOS Mod's replacement hooks](https://github.com/rikumi/coloros-mod/blob/master/app/src/main/java/com/rikumi/colorosmod/hooks/SystemFontReplacementHooks.java) intercept those same OEM methods. GlyphOrder instead shows a relevant Apply-page notice and opens public display settings, where the device exposes a Font entry. It does not write the system setting or install a framework hook on the user's behalf. The setting must be chosen explicitly if independent styles are wanted.

### Incidental glyphs and language ranking

The bundled, unmodified `NotoSerifKhitanSmallScript-Regular.ttf` includes nine Han characters as well as its intended Khitan characters: 丹、仿、体、契、字、宋、小、歌、谷. The former automatic metadata routed every script found in the cmap, including Han. The native probe reproduced exactly this failure: ordinary Chinese used a system CJK font, while the overlapping characters used the Khitan font, creating mixed serif shapes and exaggerated line metrics.

Removing the incidental Han tag is useful metadata cleanup, but is **not sufficient isolation**. Android language tags influence ranking; they do not prohibit a font from supplying a glyph of another script. [FontMM issue #10](https://github.com/YuleBest/FontMM/issues/10) documents the analogous problem of a Western slot containing CJK glyphs. [font-settings](https://github.com/yuzlyn/font-settings#字体要求) uses cmap filtering by role. GlyphOrder preserves the upstream font binaries and instead generates only the user's explicit combination entries, before the retained system fallback. It no longer silently adds an unrelated automatic supplement pool to combinations. A selected library family includes its own declared language members, with relevant script routes and user-family ordering preserved; Noto Sans excludes Serif specialists. Khitan, Nushu and Phags-pa routing metadata names the intended script rather than incidental Han/Mongolian coverage.

After updating the module and using KernelSU soft reboot, the previously affected Chinese characters no longer used the Khitan font. A separately chosen personal combination was then checked against actual native glyph paths: Latin came from its chosen primary and all tested Chinese came from its chosen fallback. That combination exists only in the device's preferences, not in the shipped catalog or initialization code. A fresh installation starts with no combinations or assignments.

### Permission-protected settings pages

The device crash log proves that `oplus.intent.action.settings.FONT_SETTINGS` can resolve to an exported activity but still throw `SecurityException`, requiring `com.oplus.permission.safe.SETTINGS`. Exported status alone is not sufficient. GlyphOrder no longer calls that private action. It tries [public display settings](https://developer.android.com/reference/android/provider/Settings#ACTION_DISPLAY_SETTINGS), falls back to the system Settings home and handles permission/missing-activity/OEM launch failures with localized feedback. Its label is Display settings rather than claiming an unsupported direct jump. Returning from this entry refreshes the OS font state without interfering with document-picker imports.

The public display page on this phone contains Font, and its font page offers Roboto alongside OPPO Sans, OPPO Serif and One Sans. The subsequently observed packed value `500` selects mode 0. Native shaping then distinguishes Rubik sans, the saved mono family, and the original system Serif. This is a device-specific verification, not a reason to apply an Oplus setting on LineageOS.

`FontPlatform` checks LineageOS before Oplus properties. LineageOS and AOSP exclude the OEM configuration paths and never interpret `font_variation_settings` as a ColorOS mode. The configuration adapter does not infer the ROM from the OnePlus hardware brand.

## Other practical pitfalls

- **Locale and variant affect matching.** The ColorOS 15 YingHei author's [firsthand notes](https://github.com/ellermister/OneplusYingHeiFont-For-ColorOS#realmeuicoloros-特别说明) describe mismatched simplified glyph sizes under a Traditional Chinese locale. On the tested device, an unmodified Chinese fallback could come from `SysSans-Hant-Regular.ttf`, while another character came from the CJK TTC. GlyphOrder inserts the explicit chain in the relevant existing language/compact/elegant routes and preserves that order; preview remains an approximation for apps with their own locale or typography.
- **Lazy loading and root unmounting are separate from configuration.** [FontMM's preload implementation notes](https://github.com/YuleBest/FontMM/blob/main/DEVELOPMENT.md) describe why Android 12+ apps can fail after a root manager hides module paths. A bundled `zygisk/` consumer is not a Zygisk provider. GlyphOrder's preloader remains available, including `/system_ext/fonts`; the current OnePlus test establishes ordinary process visibility, not compatibility with every unmount policy. No Zygisk provider was added to this phone without a demonstrated need.
- **WebView and native consumers need separate evidence.** [ColorOS Mod's namespace backend](https://github.com/rikumi/coloros-mod/blob/master/app/src/main/assets/system-font-mounts.sh) explicitly handles selected browser/WebView processes and isolated namespace inheritance. Its [native bridge](https://github.com/rikumi/coloros-mod/blob/master/app/src/main/java/com/rikumi/colorosmod/hooks/NativeFontHooks.java) is a different intervention from an XML overlay. GlyphOrder does not claim that native UI results prove browser or direct-file clock/number rendering. Apps with bundled fonts can retain those fonts.
- **Metrics are part of the font.** [FontMM's release history](https://github.com/YuleBest/FontMM/releases) records removing metric rewrites after boot failures. GlyphOrder does not modify font metrics, names or shaping tables. A fallback can legitimately change line height, and a language tag cannot repair incompatible metrics.
- **Updates and modules can overlap.** FontMM scans device-specific XML and checks KernelSU mounting prerequisites. GlyphOrder records fingerprint, SDK and incremental build with its original baseline, detects conflicting overlays, validates every generated archive and keeps the native 254-family ceiling. A successful installation is not a rendering test.

## Reports that are clues, not established root causes

[This LINUX DO thread](https://linux.do/t/topic/1639118) contains firsthand reports of unexpected serif rendering after ColorOS 16 updates, with conflicting reports of a later fix. It establishes a symptom on particular builds, not the cause of this device's bug. [Avalonia issue #21940](https://github.com/AvaloniaUI/Avalonia/issues/21940) includes an app/version-specific reproducer for Chinese tofu on a OnePlus Ace 5; it is not proof of a general Android fallback defect.

[coloros-font-switcher](https://github.com/DerekHemcis/coloros-font-switcher) attributes loading failures to PostScript-name mismatches and discusses anti-bootloop behavior. Those claims depend on its file-replacement approach. The current GlyphOrder test loads hash-named, unmodified fonts successfully, so a mandatory font-name rewrite is not established for this app. Its anti-bootloop account is not treated as a universal KernelSU rule.

XDA and Coolapk were searched for original technical posts. No sufficiently detailed, directly retrievable original post from those sites was used as evidence. The template author's [original technical article](https://sspai.com/post/58049) and maintained GitHub implementation are preferable to unattributed reposts or older ROM instructions copied without checking the active framework.

## Verification limits

The current work passes 89 local unit tests and eight device regression tests on the device. The latter use isolated stores to check migration/persistence, native glyph provenance in preview, generated archive contents and unchanged live configuration. The final personal module contains 16 unmodified faces: its explicit primary/fallback families and the selected mono family. After KernelSU soft reboot, a separate non-root probe completes 2,576 shaping/rendering runs over 46 named families and confirms Rubik Latin, MiSans for the previously affected Han characters, JetBrains Mono Latin and the unchanged system Serif. The probe also reports individual glyph paths; a smoke-test PASS alone does not mean all Unicode glyphs exist, all requested styles survived OEM substitution, or all applications use the same fallback.

All device restarts in this investigation used `/data/adb/ksud soft-reboot`. The kernel boot ID remained unchanged while `system_server` changed, preserving the exploit-based KernelSU session.
