# 字序 / GlyphOrder

[Download APK](https://github.com/RiversLee9312/GlyphOrder/releases/latest) · [中文](README.md)

An Android font switcher built with Kotlin, Jetpack Compose and Material 3 Expressive. Android 14 (API 34) minimum; validated on specific OnePlus 11 LineageOS 23.2 / Android 16 and LOS 24.0-20261023 / Android 17 builds. Root installation supports Magisk and KernelSU; KernelSU requires an active mounting metamodule (meta-overlayfs 1.3.1 was tested).

ColorOS font configuration at `/system_ext/etc/fonts_base.xml` and its OEM customization paths are supported and were tested on a OnePlus 13 running Android 17. The in-app reboot action uses `ksud soft-reboot` under KernelSU to preserve the running kernel and temporary root.

Includes five offline families: MiSans with its Global members grouped inside the family, Rubik, Noto Sans, Noto Serif and Sarasa Fixed SC Regular/Bold. Create named combinations with a primary and ordered fallbacks. Fresh installations have no predefined combinations or assignments; personal configurations remain in device preferences. Each selected family owns its declared weight and language members; unrelated font designs are not silently appended. Both library and picker show families only. This does not imply complete Unicode coverage or a matching design for every script. See [sources, coverage and licenses](design/FONTS.md). The interface supports 24 locales and native multilingual preview.

## Build

Use JDK 21, Android SDK 36, NDK 28.2.13676358 and CMake 3.22.1. Set `sdk.dir` in your untracked `local.properties`, then run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The debug APK uses the machine's Android debug key; production distribution should use a privately managed release key.

For signed releases, create an untracked root `keystore.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew :app:assembleRelease`. Output: `app/build/outputs/apk/release/app-release.apk`. Without that file the release APK is unsigned. Keep a private offline backup of the signing key and configuration for future updates.

The GitHub release uses a dedicated signing key and cannot update a debug installation in place. Before switching, back up application data, disable the font module and reboot, then uninstall the debug build and install the release APK.

## Use

1. Import multiple `.ttf`/`.otf` files or a `.zip` family through the Android file picker. Embedded typographic family names group weights and styles. The complete batch is staged and validated before commit; a malformed or conflicting member rolls it back. Family details support local naming, adding/removing faces, preview and variable axes. References survive removal of an original representative face. Files stay on the device; the app has no Internet or broad storage permission.
2. Use the three bottom destinations: **Library**, **Combinations** and **Apply**. Import/edit families in Library, then create a named combination with a primary followed by ordered fallbacks. Its detail page includes live text/weight preview and drag, menu and accessibility actions for reordering. The original system fallback stays at the end.
   Family details, combination details and Apply also open a full preview page with editable text, language samples, weight and size controls. Only this page defaults to 20sp; library samples follow the app language.
3. Allow the app's Root request in the active Magisk/KernelSU manager. The app reads the device's actual XML, named families and SELinux labels, and uses that manager's official module installer. Firmware baselines include Android API and OS incremental as well as fingerprint; a vendor fingerprint retained across an OTA cannot authorize an old baseline.
4. On **Apply**, select one saved combination with a scope (all families, categories or detected names), or assign saved combinations independently by style. System default keeps that category's original configuration. Edit combinations on Combinations. **Apply fonts** detects, generates and installs the selection without expanding a partial scope. Reboot explicitly to activate; KernelSU uses soft reboot. Separate generation/export remain available. Apps with bundled fonts may retain their own typography.
5. To restore the system fonts, disable the app's module and reboot.

Android's per-app language setting is available through system App info → Language. Locale files and their consistency are checked with `python tools/check-i18n.py`.

## Font behavior

- Combination order is explicit: first the primary, then each fallback in order, then the original system chain. Preview and generation resolve the same saved combination. No font pair or style assignment is hardcoded as an application default.
- Weight/style and language faces belong to their family and are edited there rather than becoming separate picker entries. Coverage comes from the actual selected fonts and the system; configuration cannot supply missing glyphs.
- Rubik’s upstream variable face starts at Light (300). Generated XML explicitly seeds the regular instance at 400, and legacy configurations put regular before light instances. Runtime requested weights and aliases remain available.
- Modern Android 15+ XML uses runtime `wght` and `ital` only when the font advertises those axes. Other axes are stored as fixed values. The legacy XML uses explicit, range-clamped instances. Static fonts retain their actual weight/style; the platform may synthesize unavailable styles.
- The preview uses Android's font APIs, ICU grapheme boundaries, continuous shaping runs and native bidirectional layout. Application-specific shaping, line breaking and typography can differ from the preview.
- TTC/OTC collections and web-font containers are intentionally unsupported. Import TTF/OTF faces directly or in ZIP archives. Each face is bounded at 128 MiB and an import batch at 384 MiB / 256 fonts; a generated module's selected fonts are bounded at 384 MiB.
- Font-internal names are preserved. Apps that select bundled fonts or explicit internal family names (including some browser configurations) can continue to use their own font selection.
- Shared script fallbacks use one family with multiple language tags where ordering, variant, axes and target scopes agree. Tagged automatic fonts are not appended again as universal fallbacks. Emoji families remain intact; reused system slots retain their original default faces for unselected targets.
- Generation and installation both enforce Minikin's 254-family limit for **every native fallback chain**, including named primaries, the first system family, product overrides and scoped fallbacks. Language tags and compact/elegant variants do not create separate budgets. ZIP contents are checked again before invoking Magisk, including ZIPs generated by older versions. An oversized preset is rejected with its chain/count/limit; fonts are never silently dropped to fit.
- Automatic optimization reads the actual system fonts (including TTC faces) and compares Unicode cmap coverage. It reuses system coverage for redundant supplements and reuses an existing script slot only when the primary, replacement and remaining current fallback families preserve every original glyph. Each replacement updates that proof; later replacements cannot rely on already replaced faces. Other target chains retain the original fonts. Unknown coverage never authorizes slot replacement. Redundant original slots can also be retired per selected chain after a current-coverage proof; unselected system/product names retain explicit copies of their original faces. Existing ordered injected families are shared before allocating more slots. Only referenced fonts are packaged; font files and shaping tables are not modified. Splitting a preset across multiple modules does not bypass the shared family limit.
- The frozen pre-expansion OnePlus 11 / LineageOS 23.2 fixture fits the original four built-ins and a minimal ASCII import with default automatic fallback in at most 243 native families. Tests check the complete selected cmap coverage in every selected chain. Without verified system coverage, an oversized full configuration is blocked. Coverage metadata describes characters, not a guarantee of identical shaping or complete Unicode support on every ROM.

## Systemless implementation

Since 0.1.1, generated modules include a Zygisk preloader that populates Android's process-wide font-data cache before app specialization removes module mounts. It covers all GlyphOrder files in both `/system/fonts` and `/product/fonts`, including fallback faces, without changing the font plan or XML. The framework retains the cache after the preloader library unloads. Tested with Magisk 30.7 and Zygisk Next 1.5.2 in unmount-only mode. A policy that prevents Zygisk modules from running also prevents preloading. After upgrading the APK, reapply the font configuration and reboot to update an existing module.

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

2026-11-07: On a OnePlus 13 (PJZ110, ColorOS / Android 17, KernelSU 3.3.1, meta-overlayfs 1.3.1), combination modules were installed and activated using KernelSU soft reboot without changing the kernel boot ID. All 91 unit tests and eight device regression tests pass, covering family imports, all five built-in family configurations, combinations and the public settings launch. The non-root probe completes 2,576 shaping/rendering runs over 46 families and records actual per-character font paths. Incidental Han glyphs from the former automatic pool were confirmed and eliminated from the user's combination. Generic fallback coverage is now preserved for legacy consumers; this fixes common Chinese missing-glyph boxes reproduced in the built-in HeyTap browser while native UI and newer browsers worked. ColorOS can still substitute serif/mono requests according to its system font mode; Apply links to the setting rather than changing it automatically. See [third-party research and device evidence](design/COLOROS.md).

Build the independent, dependency-free rendering probe with `:font-probe:assembleDebug`. Apply the target app's mount-isolation and package-hiding rules to `dev.glyphorder.fontprobe`, then launch it. It exercises the device's named font families and aliases at weights 100/400/700/900, upright and italic, using Latin, CJK, RTL, Indic and supplementary-plane text. Native shaping and Canvas/StaticLayout rendering must finish before `files/result.json` reports success. The report records the custom fonts actually used and whether their paths remain visible after isolation. It requests no root access and does not modify configuration. Repeat cold starts and inspect crash logs alongside the report.

For instrumentation against a signed release installation, build `:app:assembleReleaseAndroidTest -Pglyphorder.testBuildType=release` and install its test APK without uninstalling the application.

`FontConfigTest` checks modern and legacy syntax, supported/clamped axes, script ordering, product family metadata, emoji preservation, safe slot reuse and XML entity rejection. `FontSelectionTest` covers default expansion for every built-in and arbitrary imports, disabling/resetting and legacy catalog tags. `DeviceIntegrationTest` generates global modules for all five built-ins and three real variable/static/OpenType imports, verifies that native shaping actually uses the bundled fallback for an imported font, and checks preference migration/persistence without changing the user's saved settings.

`ModuleSafetyTest` checks the 254/255 boundary, named and product chains, target scopes, both XML dialects, and installation-time archive validation. Checked-in OnePlus 11 / LineageOS 23.2 XML, system cmap coverage and the frozen 167-face legacy manifest replay the bootloop without mounting a module or rebooting a device. The unoptimized archive must be rejected; all four built-ins and an ASCII-only import must pass with the same default pool and preserve every selected codepoint in every selected chain. `CmapCoverageTest` covers BMP/supplementary maps, TTC offsets, missing glyphs and malformed bounds.

`FontLibraryIntegrationTest` checks five-item built-in grouping, ZIP import/rollback, stable family identities after face removal and generation safety for every built-in family. `SettingsNavigationIntegrationTest` launches public display settings from the app’s unprivileged context.

`CombinationIntegrationTest` checks local combination migration/persistence, native preview glyph provenance and chosen-font archive contents without installing or changing live configurations.

`StyleCompositionTest` checks native Rubik regular shaping from both generated XML dialects, matching Serif/MiSans script designs, all five current built-ins, a four-font composition, referenced font packaging and saved-plan migration. These tests generate archives without installing modules or rebooting.

Run the read-only generation/device tests with:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The installation test is explicitly opt-in. It stages a same-font test module through the active root manager, validates its SELinux label and leaves it disabled; it does not reboot:

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
