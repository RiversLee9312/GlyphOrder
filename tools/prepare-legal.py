"""Refresh offline runtime notices after :app:exportRuntimeDependencies.

Reads resolved POMs and original notices from the Gradle cache. Unknown licenses
fail closed so dependency updates cannot silently inherit the project's license.
The report's local file paths are never copied into the application or repository.
"""
from pathlib import Path
import hashlib
import io
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
OVERRIDES = {
    # These published POMs omit licenses; verified against upstream LICENSE.
    'com.github.topjohnwu.libsu:core:6.0.0': 'https://github.com/topjohnwu/libsu/blob/8c3e80ffe466b89ff875471e542d77e6a1b480c5/LICENSE',
    'com.google.guava:listenablefuture:1.0': 'https://github.com/google/guava/blob/v26.0/guava/COPYING',
}


def embedded_notices(data, prefix=''):
    results = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            if name.endswith('/'):
                continue
            if name == 'classes.jar':
                results.extend(embedded_notices(archive.read(name), prefix + name + '/'))
            elif re.search(r'(^|/)(LICENSE|NOTICE|COPYING)([._-].*)?$', name, re.I):
                text = archive.read(name).decode('utf-8-sig')
                results.append((prefix + name, text))
    return results


def main():
    records = json.loads((ROOT / 'app/build/reports/runtime-dependencies.json').read_text())
    entries = []
    notices_dir = ASSETS / 'legal/dependencies'
    notices_dir.mkdir(parents=True, exist_ok=True)
    third_party = [
        '# Third-party notices', '',
        'Project code and documentation are Apache-2.0; third-party components keep their original licenses.',
        'Generated from resolved debug/release runtime artifacts (including transitive dependencies). Debug-only tooling may be absent from release builds.', '',
        '## Runtime libraries', '',
    ]
    for item in records:
        group, name, version = (item[key] for key in ('group', 'name', 'version'))
        module = f'{group}:{name}'
        coordinate = f'{module}:{version}'
        pom = next((CACHE / group / name / version).rglob('*.pom'))
        root = ET.parse(pom).getroot()
        licenses = root.findall('m:licenses/m:license', NS)
        if coordinate not in OVERRIDES:
            assert licenses and all('apache' in license.findtext('m:name', '', NS).lower()
                                    and '2.0' in license.findtext('m:name', '', NS) for license in licenses), coordinate
        source = root.findtext('m:url', '', NS) or root.findtext('m:scm/m:url', '', NS)
        if not source:
            source = OVERRIDES.get(coordinate, 'https://developer.android.com/jetpack/androidx')
        entry = dict(name=root.findtext('m:name', name, NS), coordinate=coordinate,
                     license='Apache-2.0', licenseAsset='legal/Apache-2.0.txt', source=source)
        notices = embedded_notices(Path(item['file']).read_bytes())
        if notices:
            text = '\n\n'.join(f'--- {name} ---\n{text}' for name, text in notices)
            filename = hashlib.sha256(text.encode()).hexdigest()[:20] + '.txt'
            (notices_dir / filename).write_text(text, encoding='utf-8')
            entry['noticeAsset'] = f'legal/dependencies/{filename}'
        entries.append(entry)
        third_party.append(f'- `{coordinate}` — Apache-2.0. [Upstream]({source}).')
    keep = {Path(entry['noticeAsset']).name for entry in entries if 'noticeAsset' in entry}
    for path in notices_dir.glob('*.txt'):
        if path.name not in keep:
            path.unlink()
    (ASSETS / 'legal/libraries.json').write_text(json.dumps(entries, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    third_party += ['', 'Full Apache-2.0 text: [LICENSE](LICENSE). Original embedded notices are retained in `app/src/main/assets/legal/dependencies/` and shown in the app.', '',
                    '## Fonts and artwork', '',
                    '- MiSans / MiSans Global: Xiaomi MiSans Font Intellectual Property License Agreement, **not an open-source font license**. Included unchanged as application assets. Do not modify the fonts or redistribute them as a standalone font package. The application expressly credits MiSans. [Original agreement](app/src/main/assets/licenses/MiSans-license-original.pdf), [readable text](app/src/main/assets/licenses/MiSans-license.txt), [official source](https://hyperos.mi.com/font/en/download/).',
                    '- Noto Sans, Noto Serif and script-specific Noto companions: SIL Open Font License 1.1. [Noto](app/src/main/assets/licenses/Noto-OFL.txt), [Serif](app/src/main/assets/licenses/NotoSerif-OFL.txt), [CJK](app/src/main/assets/licenses/NotoCJK-OFL.txt).',
                    '- Rubik: SIL Open Font License 1.1. [License and copyright](app/src/main/assets/licenses/Rubik-OFL.txt).',
                    '- Sarasa Fixed: SIL Open Font License 1.1, including its upstream copyright attributions. [License](app/src/main/assets/licenses/Sarasa-OFL.txt).',
                    '- Google Material Symbols Rounded: Apache-2.0. Original SVG geometry mechanically converted to Android vectors. [License](design/LICENSE-material-symbols.txt), [provenance](design/ASSETS.md).',
                    '- Launcher artwork: generated for this project; provenance in [design/ASSETS.md](design/ASSETS.md).', '',
                    'The project license does not relicense fonts, upstream libraries, trademarks or other third-party works. Complete font source URLs, versions and hashes are recorded in [design/FONTS.md](design/FONTS.md) and its linked inventories.', '',
                    '## Development tools', '',
                    'Gradle wrapper: Apache-2.0, Copyright Gradle, Inc.; its full distribution license and additional upstream terms are retained in [gradle/wrapper/LICENSE](gradle/wrapper/LICENSE). JDK, Android SDK, Android Gradle Plugin, Kotlin compiler, JUnit, AndroidX Test, Python, Pillow and fontTools are build/test tools and are not part of the application runtime; they retain their upstream licenses.', '']
    (ROOT / 'THIRD_PARTY_NOTICES.md').write_text('\n'.join(third_party), encoding='utf-8')
    print(f'Prepared {len(entries)} runtime notices, {len(keep)} original notice texts.')


if __name__ == '__main__':
    main()
