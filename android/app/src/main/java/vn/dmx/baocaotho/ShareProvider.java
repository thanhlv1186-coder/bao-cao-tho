package vn.dmx.baocaotho;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Cấp file ảnh báo cáo (cache/share/*.png) cho app khác (LINE, Zalo...) qua content:// — không cần thư viện androidx. */
public class ShareProvider extends ContentProvider {
  public static final String AUTH = "vn.dmx.baocaotho.share";
  public static Uri uriFor(File f) { return Uri.parse("content://" + AUTH + "/" + Uri.encode(f.getName())); }

  private File fileOf(Uri u) throws FileNotFoundException {
    String n = u.getLastPathSegment();
    if (n == null || n.contains("/") || n.contains("..")) throw new FileNotFoundException();
    File f = new File(new File(getContext().getCacheDir(), "share"), n);
    if (!f.exists()) throw new FileNotFoundException(n);
    return f;
  }
  @Override public boolean onCreate() { return true; }
  @Override public String getType(Uri u) { return "image/png"; }
  @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
    return ParcelFileDescriptor.open(fileOf(u), ParcelFileDescriptor.MODE_READ_ONLY);
  }
  @Override public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort) {
    MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
    try { File f = fileOf(u); c.addRow(new Object[]{f.getName(), f.length()}); } catch (Exception ignored) {}
    return c;
  }
  @Override public Uri insert(Uri u, ContentValues v) { return null; }
  @Override public int delete(Uri u, String s, String[] a) { return 0; }
  @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
