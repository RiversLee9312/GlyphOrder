package dev.glyphorder.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/** Test APK provider intentionally has no dependency on the target APK runtime. */
public class TestFontProvider extends ContentProvider {
    private File fixture(Uri uri) {
        switch (uri.getLastPathSegment()) {
            case "Roboto.ttf": return new File("/system/fonts/Roboto-Regular.ttf");
            case "SourceSansPro.ttf": return new File("/system/fonts/SourceSansPro-Regular.ttf");
            case "Ahom.otf": return new File("/system/fonts/NotoSansAhom-Regular.otf");
            case "Invalid.ttf":
                File invalid = new File(getContext().getCacheDir(), "Invalid.ttf");
                try (FileOutputStream stream = new FileOutputStream(invalid)) { stream.write(new byte[32]); }
                catch (Exception e) { throw new RuntimeException(e); }
                return invalid;
            default: throw new IllegalArgumentException("Unknown fixture");
        }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "font/ttf"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) row[i] = OpenableColumns.DISPLAY_NAME.equals(cols[i]) ? uri.getLastPathSegment() : fixture(uri).length();
        cursor.addRow(row); return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new IllegalArgumentException("Read only");
        return ParcelFileDescriptor.open(fixture(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
