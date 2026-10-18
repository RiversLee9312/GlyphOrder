"""Package the GPT Image foreground and Google's Material Symbols for Android."""
from pathlib import Path
import xml.etree.ElementTree as ET
import urllib.request
from PIL import Image

root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
symbols = ['add', 'text_fields', 'tune', 'layers', 'check', 'chevron_right',
           'arrow_back', 'close', 'delete', 'download', 'restart_alt', 'refresh',
           'arrow_upward', 'arrow_downward', 'search', 'font_download', 'check_circle', 'info', 'language', 'translate']
for name in symbols:
    url = f'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{name}/materialsymbolsrounded/{name}_24px.svg'
    svg = ET.fromstring(urllib.request.urlopen(url).read())
    paths = ''.join(f'<path android:fillColor="#000000" android:pathData="{p.attrib["d"]}"/>'
                    for p in svg.iter() if p.tag.endswith('path'))
    mirror = ' android:autoMirrored="true"' if name in ('arrow_back', 'chevron_right') else ''
    vector = f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="960" android:viewportHeight="960"{mirror}><group android:translateY="960">{paths}</group></vector>'
    (res / f'drawable/ic_{name}.xml').write_text(vector, encoding='utf-8')

# Mechanical launcher packaging only: no redraw of the AI-generated artwork.
source = Image.open(root / 'design/icon-source.png').convert('RGBA')
frame = Image.new('RGBA', (432, 432))
frame.alpha_composite(source.resize((392, 392), Image.Resampling.LANCZOS), (20, 20))
frame.save(res / 'drawable-nodpi/ic_launcher_foreground.png')
mono = Image.new('RGBA', frame.size, (255, 255, 255, 0))
mono.putalpha(frame.getchannel('A'))
mono.save(res / 'drawable-nodpi/ic_launcher_monochrome.png')
(res / 'mipmap-anydpi-v26/ic_launcher.xml').write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
<background android:drawable="@color/icon_background"/>
<foreground android:drawable="@drawable/ic_launcher_foreground"/>
<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>''', encoding='utf-8')
print('Packaged GPT Image icon and', len(symbols), 'Material Symbols')
