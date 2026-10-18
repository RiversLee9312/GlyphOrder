"""Fetch pinned, unmodified upstream fonts and build the shared offline Noto fallback manifest.

Requires fonttools. Downloads are build-time only; the Android app has no network permission.
Reuse the bundled, unmodified Sarasa Fixed SC Regular/Bold originals, or extract
the official 1.0.42 archive into artifacts/sarasa/ to override those inputs.
The full source tree and immutable Git blob IDs are recorded.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen, Request
import hashlib
import json
import shutil
import time
from collections import Counter
from fontTools.ttLib import TTFont
from fontTools import unicodedata as ud

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
COMMIT = '5c3913063bb189df4edc94b766584dfb4c04a187'
tree_path = ROOT / 'artifacts/noto-tree.json'
if not tree_path.exists():
    tree_path.parent.mkdir(exist_ok=True)
    tree_path.write_bytes(urlopen(Request(f'https://api.github.com/repos/notofonts/notofonts.github.io/git/trees/{COMMIT}?recursive=1',
                             headers={'User-Agent': 'GlyphOrder-build'}), timeout=90).read())
TREE = json.loads(tree_path.read_text(encoding='utf-8-sig'))
CACHE = ROOT / 'artifacts/noto'
CACHE.mkdir(exist_ok=True)

def download(url, path, blob=None):
    if path.exists():
        data = path.read_bytes()
        if not blob or hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == blob:
            return data
    for attempt in range(4):
        try:
            data = urlopen(Request(url, headers={'User-Agent': 'GlyphOrder-build'}), timeout=90).read()
            if blob:
                assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == blob
            path.write_bytes(data)
            return data
        except Exception:
            if attempt == 3:
                raise
            time.sleep(attempt + 1)

def cmap(path):
    with TTFont(path, lazy=True) as font:
        return {cp for cp, glyph in font.getBestCmap().items() if glyph != '.notdef'}

regular = [x for x in TREE['tree'] if x['path'].startswith('fonts/') and '/full/ttf/' in x['path']
           and x['path'].endswith('-Regular.ttf') and not any(name in x['path'] for name in ('Test/', 'Rotated/', 'Vertical/'))]
def fetch(x):
    name = x['path'].split('/')[-1]
    path = CACHE / name
    download(f'https://raw.githubusercontent.com/notofonts/notofonts.github.io/{COMMIT}/{x["path"]}', path, x['sha'])
    return x, path

if __name__ == '__main__':
    with ThreadPoolExecutor(max_workers=8) as pool:
        files = list(pool.map(fetch, regular))
    print(f'Fetched {len(files)} verified Noto fonts', flush=True)
    license_entry = next(x for x in TREE['tree'] if x['path'] == 'fonts/LICENSE')
    download(f'https://raw.githubusercontent.com/notofonts/notofonts.github.io/{COMMIT}/fonts/LICENSE',
             ASSETS / 'licenses/Noto-OFL.txt', license_entry['sha'])
    manifest = json.loads((ASSETS / 'bundled-fonts.json').read_text(encoding='utf-8-sig'))[:3]
    out = ASSETS / 'fonts/sarasa'
    out.mkdir(exist_ok=True)
    source = []
    sarasa_cmap = set()
    def entry(path, asset, name, license, **kwargs):
        destination = ASSETS / asset
        if path.resolve() != destination.resolve():
            shutil.copyfile(path, destination)
        ranges = []
        for cp in sorted(cmap(path)):
            if ranges and ranges[-1][1] == cp - 1:
                ranges[-1][1] = cp
            else:
                ranges.append([cp, cp])
        record = dict(name=name, asset=asset, sha256=hashlib.sha256(path.read_bytes()).hexdigest(), license=license, coverage=ranges, **kwargs)
        manifest.append(record)
        return record
    for style in ('Regular', 'Bold'):
        filename = f'SarasaFixedSC-{style}.ttf'
        path = next((ROOT / 'artifacts/sarasa').rglob(filename), out / filename)
        if not path.is_file():
            raise FileNotFoundError(f'Extract {filename} from the official Sarasa 1.0.42 archive into artifacts/sarasa/')
        assert hashlib.sha256(path.read_bytes()).hexdigest() == {
            'Regular': 'eca2c4618d27c83059e80d34000c41a48e14e578904a733f318732cb6924c2f6',
            'Bold': '349a5527d091776b714b63003628b327c04c2fa1e7e8985781ce0c75e232f7c7'}[style]
        sarasa_cmap |= cmap(path)
        entry(path, f'fonts/sarasa/{path.name}', 'Sarasa Fixed', 'licenses/Sarasa-OFL.txt',
              family='sarasa-fixed-sc', hidden=style != 'Regular', preset='sarasa' if style == 'Regular' else '')
    # Prefer Sans designs. Alternate UI/looped/unjoined designs only enter if they
    # contribute new characters. Other Noto designs cover scripts with no Sans face.
    files.sort(key=lambda item: (not item[1].name.startswith('NotoSans'),
                                any(x in item[1].name for x in ('UI-', 'Looped-', 'Unjoined-', 'SyriacEastern', 'SyriacWestern')),
                                item[1].name))
    covered = set(sarasa_cmap)
    selected = []
    for x, path in files:
        chars = cmap(path)
        additions = chars - covered
        if not additions:
            continue
        selected.append((x, path, chars))
        covered |= chars
    # CJK is separately distributed. Always validate the immutable Git blob.
    cjk = CACHE / 'NotoSansCJKsc-Regular.otf'
    cjk_commit = 'f8d157532fbfaeda587e826d4cd5b21a49186f7c'
    cjk_path = 'Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf'
    cjk_blob = 'dc15562470b4f842321894787a0d066879ccff8b'
    download(f'https://raw.githubusercontent.com/notofonts/noto-cjk/{cjk_commit}/{cjk_path}', cjk, cjk_blob)
    chars = cmap(cjk)
    selected.append((dict(path=cjk_path, sha=cjk_blob), cjk, chars))
    covered |= chars
    script_counts = Counter()
    for x, path, chars in selected:
        counts = Counter(ud.script(chr(cp)) for cp in chars)
        # Full builds include Latin helper glyphs. Route them to their actual script,
        # rather than letting every Noto family compete for Latin.
        scripts = sorted(s for s in counts if s not in ('Zyyy', 'Zinh', 'Zzzz') and
                         (s != 'Latn' or path.name == 'NotoSans-Regular.ttf'))
        tags = ['und-' + s for s in scripts]
        if path.name.startswith('NotoSansCJK'):
            tags += ['zh-Hans', 'zh-Hant', 'ja', 'ko']
        if 'Math' in path.name:
            tags += ['und-Zmth']
        # General symbols are also appended as unqualified fallback after emoji.
        if not scripts or any(t in path.name for t in ('Symbols', 'Music', 'Numerals')):
            tags += ['und-Zsym']
        record = entry(path, f'fonts/sarasa/{path.name}', path.stem.replace('-Regular', ''),
                       'licenses/NotoCJK-OFL.txt' if path.suffix == '.otf' else 'licenses/Noto-OFL.txt',
                       hidden=True, fallbackGroup='noto', tags=sorted(set(tags)))
        source.append(dict(asset=record['asset'], source=x['path'], gitBlob=x['sha'],
                           sha256=record['sha256'], codepoints=len(chars)))
        script_counts.update(scripts)
    (ASSETS / 'bundled-fonts.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    keep = {Path(record['asset']).name for record in manifest}
    for stale in out.iterdir():
        if stale.is_file() and stale.name not in keep:
            stale.unlink()
    report = dict(notoCommit=COMMIT, notoCjkCommit=cjk_commit, sarasaVersion='1.0.42',
                  sarasaArchiveSha256='e9246d9549e30d978fb046c9ea4506a9cb7c72d3e640f146e645b99f75098f79', sarasaCodepoints=len(sarasa_cmap),
                  combinedCodepoints=len(covered), notoFaces=len(selected), scripts=sorted(script_counts), sources=source)
    (ROOT / 'design/sarasa-coverage.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'Bundled {len(selected)} Noto faces; Sarasa {len(sarasa_cmap)}, combined {len(covered)} encoded codepoints; {len(script_counts)} scripts', flush=True)
