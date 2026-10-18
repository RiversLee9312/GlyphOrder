# 字序 / GlyphOrder

An Android font switcher built with Kotlin, Jetpack Compose and Material 3 Expressive. Android 14 (API 34) minimum; developed against LineageOS 23.2 / Android 16 on a OnePlus 11.

Includes five offline choices: MiSans with the official Global language packs, Rubik, Noto Sans, Noto Serif with its script-specific Serif/CJK families, and Sarasa Fixed SC Regular/Bold. Every built-in and imported primary also has the shared Noto missing-glyph pool enabled by default. Matching family supplements precede the shared pool, preserving Serif and MiSans designs where available. This does not imply complete Unicode coverage or a matching design for every script. See [sources, coverage and licenses](design/FONTS.md). The interface supports 24 locales and native multilingual preview.

## Build

Use JDK 21 and Android SDK 36. Set `sdk.dir` in your untracked `local.properties`, then run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The debug APK uses the machine's Android debug key; production distribution should use a privately managed release key.

## Use

1. Import one or more single-face `.ttf` or `.otf` files through the Android file picker. Files stay on the device; the app has no Internet or broad storage permission.
2. Choose a primary font. Automatic fallback is enabled by default for both built-in and imported fonts. The fallback page lets you turn it off, add script-specific or universal choices, or restore the default fallback configuration. Restoring clears manual/universal choices and enables automatic fallback while preserving the primary, axes and application scope. Preview custom text, sizes, weights and variable axes.
3. Allow the app's Root request in Magisk. The app reads the device's actual XML, named families and SELinux labels.
4. On **Apply**, choose **Single font** with a scope (all families, style categories, or individual detected names), or **Mix by style** with independent Sans, Serif, Mono, Google Sans/LOS clock, Casual, Cursive and other-family choices. A category set to System default remains unchanged. **Apply configuration** detects, generates and installs the current selection in one operation; it never forces the scope to global. Reboot explicitly to activate. Preview has a category selector in mixed mode. Separate generation/export remain available. Apps with bundled fonts may keep their own typography.
5. To restore the system fonts, disable the app's module and reboot.

Android's per-app language setting is available through system App info → Language. Locale files and their consistency are checked with `python tools/check-i18n.py`.

## Font behavior

- The primary font supplies its existing glyphs first. Explicit script selections precede automatic Noto supplements and the system's corresponding fallback families. Scripts without manual selections use automatic fallback when enabled, then device defaults. Changing or importing the primary never requires a special preset identifier.
- The script catalog uses this Android release's ICU ISO 15924 script inventory, with separate Simplified Chinese, Traditional Chinese, Japanese and Korean choices. Universal fallbacks fill remaining missing glyphs in their selected order, including supplementary-plane characters and private-use areas.
- A setting for every script does not imply complete Unicode glyph coverage: actual coverage comes from imported and system fonts. Fonts do not acquire missing characters through configuration.
- Rubik’s upstream variable face starts at Light (300). Generated XML explicitly seeds the regular instance at 400, and legacy configurations put regular before light instances. Runtime requested weights and aliases remain available.
- Modern Android 15+ XML uses runtime `wght` and `ital` only when the font advertises those axes. Other axes are stored as fixed values. The legacy XML uses explicit, range-clamped instances. Static fonts retain their actual weight/style; the platform may synthesize unavailable styles.
- The preview uses Android's font APIs, ICU grapheme boundaries, continuous shaping runs and native bidirectional layout. Application-specific shaping, line breaking and typography can differ from the preview.
- TTC/OTC collections and web-font containers are intentionally unsupported. Import a single TTF/OTF face instead. File size is bounded at 128 MiB; a generated module's selected fonts are bounded at 384 MiB.
- Font-internal names are preserved. Apps that select bundled fonts or explicit internal family names (including some browser configurations) can continue to use their own font selection.
- Shared script fallbacks use one family with multiple language tags where ordering, variant, axes and target scopes agree. Tagged automatic fonts are not appended again as universal fallbacks. Emoji families remain intact; reused system slots retain their original default faces for unselected targets.
- Generation and installation both enforce Minikin's 254-family limit for **every native fallback chain**, including named primaries, the first system family, product overrides and scoped fallbacks. Language tags and compact/elegant variants do not create separate budgets. ZIP contents are checked again before invoking Magisk, including ZIPs generated by older versions. An oversized preset is rejected with its chain/count/limit; fonts are never silently dropped to fit.
- Automatic optimization reads the actual system fonts (including TTC faces) and compares Unicode cmap coverage. It reuses system coverage for redundant supplements and reuses an existing script slot only when the primary, replacement and remaining current fallback families preserve every original glyph. Each replacement updates that proof; later replacements cannot rely on already replaced faces. Other target chains retain the original fonts. Unknown coverage never authorizes slot replacement. Redundant original slots can also be retired per selected chain after a current-coverage proof; unselected system/product names retain explicit copies of their original faces. Existing ordered injected families are shared before allocating more slots. Only referenced fonts are packaged; font files and shaping tables are not modified. Splitting a preset across multiple modules does not bypass the shared family limit.
- The frozen pre-expansion OnePlus 11 / LineageOS 23.2 fixture fits the original four built-ins and a minimal ASCII import with default automatic fallback in at most 243 native families. Tests check the complete selected cmap coverage in every selected chain. Without verified system coverage, an oversized full configuration is blocked. Coverage metadata describes characters, not a guarantee of identical shaping or complete Unicode support on every ROM.

## Systemless implementation

All changes are staged in the `glyphorder_fonts` Magisk module. The app uses `magisk --install-module`; it never writes to the real `/system/fonts`, requests SELinux permissive mode, or replaces/deletes `NotoColorEmoji.ttf`. The original emoji families are checked for preservation.

Original XML and a ROM fingerprint are included under `baseline/` in the module. Named families, aliases and other fallback families are preserved. Android 15+ `font_fallback.xml` and legacy `fonts.xml` are updated together. Product customization fonts are placed under `system/product/fonts` because the product parser resolves relative font names against `/product/fonts`.

The installer carries forward each configuration file's observed SELinux label, including `system_font_fallback_file` when that is the device's label. Regeneration checks the original baseline and rejects conflicting configuration overlays. After an OTA, disable the existing module and reboot before regenerating it for the new build.

Generated ZIP files are kept locally. Successful installations retain `backup/last-installed.zip` and the preceding successful ZIP inside app-private storage.

## Recovery

From an authorized ADB shell:

```sh
su -c 'touch /data/adb/modules/glyphorder_fonts/disable'
su -c '[ ! -d /data/adb/modules_update/glyphorder_fonts ] || touch /data/adb/modules_update/glyphorder_fonts/disable'
```

Then reboot. If Android cannot start, use Magisk's documented safe mode or an already configured recovery/ADB method. A font module can affect boot before the app is available, so app-level recovery is not a guarantee against every font-parser failure. No automatic reboot is performed.

## Verification

`FontConfigTest` checks modern and legacy syntax, supported/clamped axes, script ordering, product family metadata, emoji preservation, safe slot reuse and XML entity rejection. `FontSelectionTest` covers default expansion for every built-in and arbitrary imports, disabling/resetting and legacy catalog tags. `DeviceIntegrationTest` generates global modules for all five built-ins and three real variable/static/OpenType imports, verifies that native shaping actually uses the bundled fallback for an imported font, and checks preference migration/persistence without changing the user's saved settings.

`ModuleSafetyTest` checks the 254/255 boundary, named and product chains, target scopes, both XML dialects, and installation-time archive validation. Checked-in OnePlus 11 / LineageOS 23.2 XML, system cmap coverage and the frozen 167-face legacy manifest replay the bootloop without mounting a module or rebooting a device. The unoptimized archive must be rejected; all four built-ins and an ASCII-only import must pass with the same default pool and preserve every selected codepoint in every selected chain. `CmapCoverageTest` covers BMP/supplementary maps, TTC offsets, missing glyphs and malformed bounds.

`StyleCompositionTest` checks native Rubik regular shaping from both generated XML dialects, matching Serif/MiSans script designs, all five current built-ins, a four-font composition, referenced font packaging and saved-plan migration. These tests generate archives without installing modules or rebooting.

Run the read-only generation/device tests with:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The installation test is explicitly opt-in. It stages a same-font test module through Magisk, validates its SELinux label and leaves it disabled; it does not reboot:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.installModule=true'
```

Test font stores, generated ZIPs, backups and preferences use isolated per-test cache contexts and are cleaned after each test. Localized contexts keep the same sandbox. Roboto, Source Sans Pro and the Ahom OTF are system test fixtures, not bundled Noto Sans variants; tests never import them into the user library. `FixtureIsolationTest` verifies the real library and saved plan remain unchanged.

The test provider is shipped only in the separate instrumentation APK, never in the application APK. Its fixed fixtures are intended for the target OnePlus 11.

For an already authorized personal test device, build `:app:assembleDebugAndroidTest`, install the two APKs with `adb install -r`, and call `adb shell am instrument -w dev.glyphorder.app.test/androidx.test.runner.AndroidJUnitRunner` directly. This avoids Gradle's connected-test uninstall cleanup changing the application's UID and resetting its Magisk authorization. `BootVerificationTest` additionally runs with `-e assertActive true` during deliberate reboot validation; it checks the live system font service without changing it.

## References

- [AOSP custom font fallback](https://source.android.com/docs/core/fonts/custom-font-fallback)
- [Magisk module guide](https://topjohnwu.github.io/Magisk/guides.html)
- [Magisk CLI tools](https://topjohnwu.github.io/Magisk/tools.html)
- [libsu](https://github.com/topjohnwu/libsu)
- [Material 3 for Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Adaptive and themed icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Generated icon and Material Symbols attribution](design/ASSETS.md)

## License and privacy

Original project code and documentation use [Apache-2.0](LICENSE); attribution is in [NOTICE](NOTICE). Third-party libraries, font binaries and artwork retain their original licenses. In particular, **MiSans / MiSans Global is proprietary freeware under Xiaomi’s agreement, not Apache-2.0 or OFL**. Its unmodified files are included as application assets; do not modify them or redistribute a standalone font package. See [third-party notices](THIRD_PARTY_NOTICES.md) and [privacy](PRIVACY.md).

The application’s top bar has an About entry with offline project, dependency and font license pages. To refresh notices after changing dependencies, run `:app:exportRuntimeDependencies`, then `python tools/prepare-legal.py`. A build-time check rejects a stale runtime inventory. Original embedded dependency notices are retained.

Contributions are welcome through GitHub issues and pull requests. Run the build, unit tests and `python tools/check-i18n.py`; include source hashes and license updates for new fonts or dependencies. Unless separately agreed, original contributions use the project’s Apache-2.0 license.
