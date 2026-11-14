package org.recompile.freej2me.lite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only content URI for handing a downloaded APK to Android's package installer. */
public final class UpdateApkProvider extends ContentProvider {
    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        if (file == null || !file.isFile()) return null;
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only APK provider");
        File file = resolve(uri);
        if (file == null || !file.isFile()) throw new FileNotFoundException("APK not found");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File resolve(Uri uri) {
        if (getContext() == null || uri == null || uri.getPathSegments().size() != 2
                || !"apk".equals(uri.getPathSegments().get(0))) return null;
        String filename = uri.getPathSegments().get(1);
        if (!filename.matches("J2MEX-[A-Za-z0-9._-]+\\.apk")) return null;
        File directory = new File(getContext().getCacheDir(), "updates");
        try {
            File file = new File(directory, filename).getCanonicalFile();
            return file.getParentFile().equals(directory.getCanonicalFile()) ? file : null;
        } catch (Exception error) {
            return null;
        }
    }
}
