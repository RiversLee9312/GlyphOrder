"""Extend the offline catalog with upstream Noto Serif and official MiSans Global.

Run after prepare-sarasa.py. Binaries remain unmodified. ZIP CRCs and immutable
Noto Git blobs are verified; exact source URLs and SHA256s are written to design/.
"""
from pathlib import Path
import io
import json
import re
import shutil
import hashlib
import struct
import urllib.request
import zipfile
import time
from concurrent.futures import ThreadPoolExecutor
from fontTools.ttLib import TTFont
from fontTools import unicodedata as ud
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('base_fonts', ROOT / 'tools/prepare-sarasa.py')
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
ASSETS = base.ASSETS
CACHE = ROOT / 'artifacts/global-families'
CACHE.mkdir(exist_ok=True)
catalog = json.loads((ASSETS / 'bundled-fonts.json').read_text(encoding='utf-8-sig'))
catalog = [x for x in catalog if not x.get('globalFamilyAsset')]
sources = []


def record(path, **metadata):
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    existing = next((x for x in catalog if x['sha256'] == digest), None)
    if existing:
        return existing
    asset = 'fonts/global/' + path.name
    (ASSETS / 'fonts/global').mkdir(exist_ok=True)
    shutil.copyfile(path, ASSETS / asset)
    chars = base.cmap(path)
    ranges = []
    for cp in sorted(chars):
        if ranges and ranges[-1][1] == cp - 1:
            ranges[-1][1] = cp
        else:
            ranges.append([cp, cp])
    scripts = {ud.script(chr(cp)) for cp in chars} - {'Latn', 'Zyyy', 'Zinh', 'Zzzz'}
    tags = ['und-' + s for s in sorted(scripts)]
    if 'CJK' in path.name:
        tags += ['zh-Hans', 'zh-Hant', 'ja', 'ko']
    if not tags:
        tags = ['und-Latn']
    item = dict(name=path.stem, asset=asset, sha256=digest, coverage=ranges,
                tags=tags, hidden=True, globalFamilyAsset=True, **metadata)
    catalog.append(item)
    return item


def fetch_noto(item):
    path = CACHE / Path(item['path']).name
    url = f'https://raw.githubusercontent.com/notofonts/notofonts.github.io/{base.COMMIT}/{item["path"]}'
    base.download(url, path, item['sha'])
    return path, url, item['sha']


class RemoteZip(io.RawIOBase):
    """Seekable HTTP Range reader, downloading only selected upstream ZIP members."""
    def __init__(self, url):
        self.url, self.pos = url, 0
        with urllib.request.urlopen(urllib.request.Request(url, method='HEAD'), timeout=45) as r:
            self.size = int(r.headers['Content-Length'])

    def seekable(self):
        return True

    def seek(self, offset, whence=0):
        self.pos = offset if whence == 0 else self.pos + offset if whence == 1 else self.size + offset
        return self.pos

    def tell(self):
        return self.pos

    def read(self, size=-1):
        end = self.size if size < 0 else min(self.size, self.pos + size)
        if end <= self.pos:
            return b''
        chunks = []
        while self.pos < end:
            chunk_end = min(end, self.pos + 256 * 1024)
            for attempt in range(5):
                try:
                    req = urllib.request.Request(self.url, headers={'Range': f'bytes={self.pos}-{chunk_end-1}'})
                    with urllib.request.urlopen(req, timeout=45) as r:
                        assert r.status == 206, 'Server must support exact HTTP ranges'
                        data = r.read()
                    assert len(data) == chunk_end - self.pos
                    chunks.append(data)
                    self.pos = chunk_end
                    break
                except Exception:
                    if attempt == 4:
                        raise
                    time.sleep(attempt + 1)
        return b''.join(chunks)


if __name__ == '__main__':
    # Prefer genuine variable faces, otherwise retain Regular/Bold as a family.
    tree = base.TREE['tree']
    serif_regular = [x for x in tree if x['path'].startswith('fonts/NotoSerif') and '/full/ttf/' in x['path']
                     and x['path'].endswith('-Regular.ttf')]
    selected = []
    for regular in serif_regular:
        prefix = regular['path'].split('/full/')[0]
        variables = [x for x in tree if x['path'].startswith(prefix + '/full/variable/') and '-Italic' not in x['path']]
        if variables:
            selected += variables
        else:
            selected.append(regular)
            bold = next((x for x in tree if x['path'] == regular['path'].replace('-Regular.ttf', '-Bold.ttf')), None)
            if bold:
                selected.append(bold)
    selected += [x for x in tree if x['path'] == 'fonts/NotoSans/full/variable/NotoSans[wdth,wght].ttf']
    with ThreadPoolExecutor(max_workers=8) as pool:
        downloaded = list(pool.map(fetch_noto, selected))
    for path, url, blob in downloaded:
        family = re.split(r'[-\[]', path.name)[0]
        item = record(path, family='global-' + family, fallbackGroup='noto-serif', license='licenses/Noto-OFL.txt')
        if family == 'NotoSans':
            item.update(name='Noto Sans', hidden=False, preset='noto-sans', fallbackGroup=None)
        sources.append(dict(asset=item['asset'], url=url, gitBlob=blob, sha256=item['sha256']))
    # CJK's pan-language OTF includes locale substitutions, and genuine 400/700 faces.
    commit = 'f8d157532fbfaeda587e826d4cd5b21a49186f7c'
    cjk_tree_path = CACHE / 'noto-cjk-tree.json'
    if not cjk_tree_path.exists():
        base.download(f'https://api.github.com/repos/notofonts/noto-cjk/git/trees/{commit}?recursive=1', cjk_tree_path)
    cjk_tree = json.loads(cjk_tree_path.read_text())['tree']
    for weight in ('Regular', 'Bold'):
        source = f'Serif/OTF/SimplifiedChinese/NotoSerifCJKsc-{weight}.otf'
        blob = next(x['sha'] for x in cjk_tree if x['path'] == source)
        path = CACHE / Path(source).name
        url = f'https://raw.githubusercontent.com/notofonts/noto-cjk/{commit}/{source}'
        base.download(url, path, blob)
        item = record(path, family='global-NotoSerifCJKsc', fallbackGroup='noto-serif', license='licenses/NotoCJK-OFL.txt')
        sources.append(dict(asset=item['asset'], url=url, gitBlob=blob, sha256=item['sha256']))
    print(f'Verified {len(downloaded) + 2} Noto family assets', flush=True)

    packs = ['MiSans_Latin', 'MiSans_Arabic', 'MiSans_Devanagari', 'MiSans_Gujarati',
             'MiSans_Gurmukhi', 'MiSans_Khmer', 'MiSans_Lao', 'MiSans_Myanmar',
             'MiSans_Thai', 'MiSans_Tibetan', 'MiSans_TC', 'MiSans_L3']
    members = {}
    for pack in packs:
        url = 'https://hyperos.mi.com/font-download/' + pack + '.zip'
        with zipfile.ZipFile(RemoteZip(url)) as archive:
            entries = [x for x in archive.infolist() if not x.is_dir() and not x.filename.startswith('__MACOSX/')
                       and x.filename.lower().endswith('.ttf')]
            members[pack] = [x.filename for x in entries]
            chosen = [x for x in entries if re.search(r'(VF|Variable|\[.*\])\.ttf$', x.filename, re.I)]
            normalize = lambda name: re.sub(r'[^a-z0-9]', '', name.lower())
            vf_names = {normalize(re.split(r'(VF|Variable|\[)', Path(x.filename).stem)[0]) for x in chosen}
            chosen += [x for x in entries if re.search(r'[-_ ](Regular|Bold)\.ttf$', x.filename, re.I)
                       and normalize(re.split(r'[-_ ](?:Regular|Bold)', Path(x.filename).stem)[0]) not in vf_names]
            chosen += [x for x in entries if pack == 'MiSans_L3']
            assert chosen, 'No usable faces in ' + pack
            for entry in {x.filename: x for x in chosen}.values():
                path = CACHE / Path(entry.filename).name
                if not path.exists():
                    path.write_bytes(archive.read(entry))
                else:
                    import zlib
                    assert zlib.crc32(path.read_bytes()) & 0xffffffff == entry.CRC
                family = re.split(r'[-_ ](?:Regular|Bold)|VF|Variable|\[', path.stem)[0].strip()
                item = record(path, family='global-' + family, fallbackGroup='misans-global',
                              license='licenses/MiSans-license.txt', originalLicense='licenses/MiSans-license-original.pdf')
                sources.append(dict(asset=item['asset'], url=url, member=entry.filename, crc32=f'{entry.CRC:08x}', sha256=item['sha256']))
            print(f'Verified {pack}: {len(chosen)} faces', flush=True)
    (CACHE / 'misans-members.json').write_text(json.dumps(members, ensure_ascii=False, indent=2), encoding='utf-8')
    for item in catalog:
        if item['name'] == 'MiSans' and not item.get('hidden'):
            item['preset'] = 'misans-global'
        if item['name'] == 'Noto Serif' and not item.get('hidden'):
            item['preset'] = 'noto-serif'
    (ASSETS / 'bundled-fonts.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    from collections import Counter
    coverage = {}
    for primary in [x for x in catalog if not x.get('hidden')]:
        group = primary.get('preset')
        selected = [x for x in catalog if x is primary or
                    (x.get('family') and x.get('family') == primary.get('family')) or
                    x.get('fallbackGroup') == 'noto' or
                    (group in ('noto-serif', 'misans-global') and x.get('fallbackGroup') == group)]
        chars = set().union(*(base.cmap(ASSETS / x['asset']) for x in selected))
        coverage[primary['name']] = dict(codepoints=len(chars), scripts=len({ud.script(chr(cp)) for cp in chars} - {'Zyyy', 'Zinh', 'Zzzz'}), faces=len(selected))
    report = dict(notoCommit=base.COMMIT, notoCjkCommit=commit, coverage=coverage, sources=sources)
    (ROOT / 'design/global-families.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Catalog: {len(catalog)} faces, {sum(not x.get("hidden") for x in catalog)} visible families', flush=True)
