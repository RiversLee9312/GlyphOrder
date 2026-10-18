package dev.glyphorder.app.ui

import android.icu.util.ULocale

data class PreviewSample(val key: String, val labelTag: String?, val language: String, val text: String) {
    fun label(locale: ULocale): String {
        val tag = labelTag ?: return ""
        val value = ULocale.forLanguageTag(tag)
        return if (tag.startsWith("und-")) value.getDisplayScript(locale).ifBlank { tag }
        else value.getDisplayName(locale).ifBlank { tag }
    }
}

/** Proof text for script shaping, bidi, combining marks, supplementary planes and emoji clusters. */
object PreviewSamples {
    val all = listOf(
        PreviewSample("mixed", null, "und", "Aa Bb Cc 0123456789\n清风徐来，水波不兴。\nالعربية · עברית · فارسی\nहिन्दी · বাংলা · ไทย\nかな カナ 한글\nA العربية 123 עברית Z\ne\u0301 · a\u0308 · 👨‍👩‍👧‍👦 · 🇨🇳"),
        PreviewSample("latin", "und-Latn", "en", "The quick brown fox jumps over the lazy dog.\nÀá Ää Åå Ææ Œœ ß İı Łł\n0123456789 · & @ #"),
        PreviewSample("arabic", "ar", "ar", "نص عربي لاختبار الخط وتشكيل الحروف.\nالسَّلَامُ عَلَيْكُمْ\nلا · لأ · لإ · لآ\nالعربية 123 Android 456 العربية"),
        PreviewSample("hebrew", "he", "he", "דג סקרן שט בים מאוכזב ולפתע מצא חברה.\nשָׁלוֹם עוֹלָם\nעברית 123 Android 456 עברית"),
        PreviewSample("persian", "fa", "fa", "متنی برای پیش‌نمایش قلم فارسی.\nمی‌توانیم · خانه‌ها · یک‌بار\nفارسی ۱۲۳ Android ۴۵۶"),
        PreviewSample("urdu", "ur", "ur", "اردو حروف اور جوڑوں کا پیش منظر۔\nیہ ایک خوب صورت دن ہے۔\nاردو ۱۲۳ Android ۴۵۶"),
        PreviewSample("devanagari", "hi", "hi", "यह देवनागरी लिपि का पूर्वावलोकन है।\nक्ष · त्र · ज्ञ · श्र · कि · की\nहिन्दी १२३ Android 456"),
        PreviewSample("bengali", "bn", "bn", "এটি বাংলা হরফের পূর্বরূপ।\nক্ষ · জ্ঞ · ত্র · কি · কী\nবাংলা ১২৩ Android 456"),
        PreviewSample("tamil", "ta", "ta", "இது தமிழ் எழுத்துருவின் முன்னோட்டம்.\nதமிழ் மொழி · கி கீ கு கூ\n௧௨௩௪௫௬௭௮௯"),
        PreviewSample("telugu", "te", "te", "ఇది తెలుగు అక్షరాల ముందస్తు వీక్షణ.\nతెలుగు భాష · క్ష · శ్రీ\n౧౨౩౪౫౬౭౮౯"),
        PreviewSample("gujarati", "gu", "gu", "આ ગુજરાતી અક્ષરોનું પૂર્વાવલોકન છે.\nગુજરાતી ભાષા · ક્ષ · જ્ઞ\n૧૨૩૪૫૬૭૮૯"),
        PreviewSample("gurmukhi", "pa-Guru", "pa-Guru", "ਇਹ ਪੰਜਾਬੀ ਅੱਖਰਾਂ ਦੀ ਝਲਕ ਹੈ।\nਪੰਜਾਬੀ ਭਾਸ਼ਾ · ਕਿ ਕੀ ਕੁ ਕੂ\n੧੨੩੪੫੬੭੮੯"),
        PreviewSample("thai", "th", "th", "ตัวอย่างแบบอักษรภาษาไทย\nน้ำ · ผู้ใช้ · เก้าอี้ · ภาษาไทย\n๑๒๓๔๕๖๗๘๙"),
        PreviewSample("lao", "lo", "lo", "ຕົວຢ່າງຮູບແບບຕົວອັກສອນລາວ\nພາສາລາວ · ສະບາຍດີ\n໑໒໓໔໕໖໗໘໙"),
        PreviewSample("myanmar", "my", "my", "မြန်မာစာလုံးပုံစံ နမူနာ\nမြန်မာဘာသာ · မင်္ဂလာပါ\n၁၂၃၄၅၆၇၈၉"),
        PreviewSample("khmer", "km", "km", "នេះជាគំរូពុម្ពអក្សរខ្មែរ។\nភាសាខ្មែរ · សួស្តី\n១២៣៤៥៦៧៨៩"),
        PreviewSample("ethiopic", "am", "am", "ይህ የአማርኛ ፊደል ቅድመ እይታ ነው።\nአማርኛ · ሰላም\n፩፪፫፬፭፮፯፰፱"),
        PreviewSample("georgian", "ka", "ka", "ეს არის ქართული შრიფტის ნიმუში.\nქართული ენა · თბილისი\nᲐᲑᲒᲓ · აბგდ · 0123456789"),
        PreviewSample("armenian", "hy", "hy", "Սա հայերեն տառատեսակի նմուշ է։\nՀայերեն · Երևան\nԱԲԳԴ · աբգդ · 0123456789"),
        PreviewSample("greek", "el", "el", "Δείγμα ελληνικής γραμματοσειράς.\nΕλληνικά · Αθήνα · ά έ ή ί ό ύ ώ\nΑΒΓΔ · αβγδ · 0123456789"),
        PreviewSample("cyrillic", "und-Cyrl", "ru", "Съешь ещё этих мягких французских булок.\nКириллица · АБВГ · абвг\nЁё Йй Її Єє · 0123456789"),
        PreviewSample("hans", "zh-Hans", "zh-Hans", "清风徐来，水波不兴。\n字体、字重与标点：你好，世界！\n汉字 骨 直 令 · 0123456789"),
        PreviewSample("hant", "zh-Hant", "zh-Hant", "清風徐來，水波不興。\n字體、字重與標點：你好，世界！\n漢字 骨 直 令 · 0123456789"),
        PreviewSample("japanese", "ja", "ja", "いろはにほへと ちりぬるを\nひらがな カタカナ 日本語\n骨 直 令 · 0123456789"),
        PreviewSample("korean", "ko", "ko", "다람쥐 헌 쳇바퀴에 타고파\n한글 글꼴 미리보기 · 한글\n骨 直 令 · 0123456789"),
        PreviewSample("tibetan", "bo", "bo", "བོད་ཡིག་གི་དཔེ་མཚོན།\nབཀྲ་ཤིས་བདེ་ལེགས།\n༡༢༣༤༥༦༧༨༩"),
        PreviewSample("sinhala", "si", "si", "මෙය සිංහල අකුරුවල පෙරදසුනකි.\nසිංහල භාෂාව · ශ්‍රී ලංකාව\n0123456789"),
        PreviewSample("adlam", "und-Adlm", "ff-Adlm", "𞤀𞤣𞤤𞤢𞤥 · 𞤊𞤵𞤤𞤢\n𞥐𞥑𞥒𞥓𞥔𞥕𞥖𞥗𞥘𞥙\n𞤀𞤣𞤤𞤢𞤥 123 Android"),
        PreviewSample("gothic", "und-Goth", "und-Goth", "𐌰𐌱𐌲𐌳𐌴𐌵𐌶𐌷𐌸𐌹\n𐌺𐌻𐌼𐌽𐌾𐌿𐍀𐍁𐍂𐍃\n𐍄𐍅𐍆𐍇𐍈𐍉 · 0123456789"),
        PreviewSample("math", "und-Zmth", "und-Zmth", "∑ ∫ √ ∞ ≠ ≈ ≤ ≥ ∂ ∇\n𝔄 𝕬 𝒜 𝐀 𝘼 𝙰 𝟘 𝟙 𝟚\n∀x ∈ ℝ, x² ≥ 0"),
        PreviewSample("symbols", "und-Zsym", "und-Zsym", "← ↑ → ↓ ↔ ↗ ⤴ ⇧ ⌘ ⌥\n♠ ♣ ♥ ♦ ♩ ♪ ♫ ☀ ☁ ☂\n€ £ ¥ ₹ ₩ ₿ · ① ② ③ · ⠿"),
        PreviewSample("emoji", "und-Zsye", "und-Zsye", "🙂 🌏 🎨 ✨ 👨‍👩‍👧‍👦\n👍 👍🏽 👩‍💻 🧑🏿‍🚀 ❤️‍🔥\n🇨🇳 🇯🇵 🇺🇳 · 1️⃣ 2️⃣ 3️⃣")
    )
}
