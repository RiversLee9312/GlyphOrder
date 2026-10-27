package dev.glyphorder.fontprobe;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.text.TextRunShaper;
import android.graphics.text.PositionedGlyphs;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.TextView;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;
import java.io.FileInputStream;
import org.xmlpull.v1.XmlPullParser;

/** No root requests, native hooks, network, or third-party UI dependencies. */
public final class ProbeActivity extends Activity {
    private static final String TAG = "GlyphOrderProbe";
    private static final String[] SAMPLES = {
        "中文 字序 简体 繁體 漢字，。你好世界",
        "English Ag fi ffi 0123456789 e\u0301 ÀßΩ Ж",
        "日本語 あいうえお カタカナ 한국어 한글",
        "السَّلَامُ عَلَيْكُمْ שלום فارسی",
        "क्षत्रिय श्री ज्ञ বাংলা ไทย ລາວ မြန်မာ ខ្មែរ",
        "ᚠ ᜀ ᠮ Ꭳ ሀ ꆈ 𑜒 𞤀 ⴰ ⠿ ⌘ ∑ ∫ 𝄞",
        "🙂 👨‍👩‍👧‍👦 🇨🇳 1️⃣ ♥️"
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        File result = new File(getFilesDir(), "result.json");
        result.delete();
        try {
            JSONObject report = new JSONObject();
            report.put("pid", android.os.Process.myPid());
            report.put("suVisible", new File("/product/bin/su").exists());
            report.put("magiskVisible", new File("/debug_ramdisk/magisk").exists());
            JSONArray hidden = new JSONArray();
            for (String name : new String[]{"com.topjohnwu.magisk", "com.tsng.hidemyapplist", "dev.openlocation.app", "com.pf.a11yspy"}) {
                try { getPackageManager().getPackageInfo(name, 0); }
                catch (android.content.pm.PackageManager.NameNotFoundException expected) { hidden.put(name); }
            }
            report.put("hiddenPackages", hidden);
            Set<String> used = new TreeSet<>();
            int runs = 0;
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(36);
            Bitmap bitmap = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Set<String> families = new TreeSet<>(Arrays.asList("sans-serif", "sans-serif-smallcaps", "sans-serif-condensed", "roboto", "roboto-flex", "source-sans-pro", "serif", "monospace", "serif-monospace", "google-sans"));
            for (String path : new String[]{"/system/etc/fonts.xml", "/system/etc/font_fallback.xml", "/product/etc/fonts_customization.xml"}) {
                if (!new File(path).isFile()) continue;
                try (FileInputStream input = new FileInputStream(path)) {
                    XmlPullParser xml = android.util.Xml.newPullParser();
                    xml.setInput(input, "UTF-8");
                    for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
                        if (event != XmlPullParser.START_TAG) continue;
                        String name = xml.getAttributeValue(null, "name");
                        if (name != null && (xml.getName().equals("family") || xml.getName().equals("family-list") || xml.getName().equals("alias"))) families.add(name);
                    }
                }
            }
            // Reading font metadata/buffers here would itself warm the cache. Shape first.
            for (String family : families) {
                for (int weight : new int[]{100, 400, 700, 900}) {
                    for (boolean italic : new boolean[]{false, true}) {
                        paint.setTypeface(Typeface.create(Typeface.create(family, 0), weight, italic));
                        for (String text : SAMPLES) {
                            PositionedGlyphs glyphs = TextRunShaper.shapeTextRun(text, 0, text.length(), 0, text.length(), 0, 0, false, paint);
                            for (int i = 0; i < glyphs.glyphCount(); i++) {
                                File file = glyphs.getFont(i).getFile();
                                if (file != null) used.add(file.getAbsolutePath());
                            }
                            canvas.drawColor(0xffffffff);
                            StaticLayout.Builder.obtain(text, 0, text.length(), paint, 1000).build().draw(canvas);
                            runs++;
                        }
                    }
                }
            }
            bitmap.recycle();
            JSONArray custom = new JSONArray();
            for (String path : used) {
                if (path.contains("GlyphOrder-")) custom.put(new JSONObject().put("path", path).put("visible", new File(path).exists()));
            }
            report.put("runs", runs);
            report.put("families", families.size());
            report.put("customFontsUsed", custom);
            report.put("status", "PASS");
            try (FileOutputStream output = new FileOutputStream(result)) {
                output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Log.i(TAG, report.toString());
            TextView view = new TextView(this);
            view.setText("PASS · " + runs + " text runs\n" + String.join("\n", SAMPLES));
            view.setTextSize(20);
            setContentView(view);
        } catch (Exception error) {
            Log.e(TAG, "FAIL", error);
            throw new RuntimeException(error);
        }
    }
}
