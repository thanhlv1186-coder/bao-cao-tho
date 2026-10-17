package vn.dmx.baocaotho;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.OutputStream;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WebView mở giao diện qua https://appassets.androidplatform.net/assets/...
 *  CẬP NHẬT GIAO DIỆN TỪ XA: mỗi lần mở/quay lại app, tải web/index.html mới nhất từ GitHub,
 *  lưu vào bộ nhớ app và nạp lại -> thêm mục/sửa giao diện KHÔNG cần cài lại APK.
 *  Không có mạng / tải lỗi -> dùng bản đã lưu, hoặc bản đóng gói trong APK. */
public class MainActivity extends Activity {
  private static final String HOST = "appassets.androidplatform.net";
  private WebView wv;
  private File uiFile;
  private long lastCheck = 0;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    uiFile = new File(getFilesDir(), "index.html");
    wv = new WebView(this);
    wv.setBackgroundColor(0xFFEEF3FA);
    WebSettings s = wv.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);
    wv.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
        Uri u = req.getUrl();
        if (!HOST.equals(u.getHost())) return null;
        String p = u.getPath();
        if (p == null || !p.startsWith("/assets/")) return null;
        p = p.substring(8);
        try {
          InputStream in;
          if (p.equals("index.html") && uiFile.exists() && uiFile.length() > 1000) in = new FileInputStream(uiFile);
          else in = getAssets().open(p);
          String mime = p.endsWith(".html") ? "text/html" : p.endsWith(".js") ? "application/javascript"
              : p.endsWith(".css") ? "text/css" : p.endsWith(".png") ? "image/png" : p.endsWith(".json") ? "application/json" : "application/octet-stream";
          WebResourceResponse r = new WebResourceResponse(mime, "utf-8", in);
          java.util.Map<String, String> h = new java.util.HashMap<>();
          h.put("Cache-Control", "no-store");
          r.setResponseHeaders(h);
          return r;
        } catch (Exception e) { return null; }
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
        Uri u = req.getUrl();
        if (HOST.equals(u.getHost())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
        return true;
      }
    });
    wv.addJavascriptInterface(new Bridge(), "AndroidApp");
    setContentView(wv);
    wv.loadUrl("https://" + HOST + "/assets/index.html");
  }

  @Override protected void onResume() {
    super.onResume();
    long now = System.currentTimeMillis();
    if (now - lastCheck > 60_000) { lastCheck = now; checkUi(); }
  }

  private String repo() {
    try (InputStream in = getAssets().open("config.js")) {
      Matcher m = Pattern.compile("repo:'([^']+)'").matcher(new String(readAll(in), StandardCharsets.UTF_8));
      return m.find() ? m.group(1) : null;
    } catch (Exception e) { return null; }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    byte[] buf = new byte[16384]; int n;
    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
    return bo.toByteArray();
  }

  /** tải giao diện mới; nếu khác bản đang dùng -> lưu + nạp lại */
  private void checkUi() {
    final String repo = repo();
    if (repo == null) return;
    new Thread(() -> {
      try {
        URL url = new URL("https://raw.githubusercontent.com/" + repo + "/main/web/index.html?t=" + System.currentTimeMillis());
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setUseCaches(false);
        if (c.getResponseCode() != 200) return;
        byte[] nb = readAll(c.getInputStream());
        String txt = new String(nb, StandardCharsets.UTF_8);
        if (nb.length < 1000 || !txt.contains("APP_CFG")) return;          // không phải trang app hợp lệ
        byte[] cur;
        if (uiFile.exists()) { try (InputStream in = new FileInputStream(uiFile)) { cur = readAll(in); } }
        else { try (InputStream in = getAssets().open("index.html")) { cur = readAll(in); } }
        if (Arrays.equals(cur, nb)) return;
        File tmp = new File(getFilesDir(), "index.tmp");
        try (FileOutputStream fo = new FileOutputStream(tmp)) { fo.write(nb); }
        if (!tmp.renameTo(uiFile)) return;
        runOnUiThread(() -> wv.reload());
      } catch (Exception ignored) {}
    }).start();
  }

  // ================== CHIA SẺ / LƯU ẢNH (gọi từ JS: window.AndroidApp) ==================
  private byte[] pendingBytes; private String pendingName;

  private static byte[] decode(String b64) {
    int i = b64.indexOf(','); if (i >= 0 && b64.startsWith("data:")) b64 = b64.substring(i + 1);
    return Base64.decode(b64, Base64.DEFAULT);
  }
  private static String safeName(String n) {
    n = (n == null ? "BaoCao.png" : n).replaceAll("[^A-Za-z0-9_.-]", "_");
    return n.endsWith(".png") ? n : n + ".png";
  }
  private void toastJs(String m) {
    runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
  }
  private File writeCache(byte[] b, String name) throws Exception {
    File d = new File(getCacheDir(), "share"); d.mkdirs();
    File[] old = d.listFiles(); if (old != null) for (File o : old) if (System.currentTimeMillis() - o.lastModified() > 3600_000) o.delete();
    File f = new File(d, name);
    try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(b); }
    return f;
  }
  private void doShare(byte[] b, String name, String text, String pkg) {
    try {
      File f = writeCache(b, name);
      Uri u = ShareProvider.uriFor(f);
      Intent it = new Intent(Intent.ACTION_SEND);
      it.setType("image/png");
      it.putExtra(Intent.EXTRA_STREAM, u);
      if (text != null && !text.isEmpty()) it.putExtra(Intent.EXTRA_TEXT, text);
      it.setClipData(ClipData.newRawUri(name, u));
      it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      if (pkg != null && !pkg.isEmpty()) {
        it.setPackage(pkg);
        try { startActivity(it); return; }
        catch (Exception e) { toastJs("Máy chưa cài ứng dụng này, chọn ứng dụng khác"); it.setPackage(null); }
      }
      Intent ch = Intent.createChooser(it, "Chia sẻ báo cáo");
      ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(ch);
    } catch (Exception e) { toastJs("Không chia sẻ được: " + e.getMessage()); }
  }
  private boolean doSave(byte[] b, String name) {
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BaoCaoTho");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri u = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (u == null) return false;
        try (OutputStream os = cr.openOutputStream(u)) { os.write(b); }
        v.clear(); v.put(MediaStore.Images.Media.IS_PENDING, 0); cr.update(u, v, null, null);
      } else {
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BaoCaoTho");
        d.mkdirs(); File f = new File(d, name);
        try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(b); }
        MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, new String[]{"image/png"}, null);
      }
      toastJs("Đã lưu ảnh vào Thư viện ảnh (Pictures/BaoCaoTho)");
      return true;
    } catch (Exception e) { toastJs("Không lưu được ảnh: " + e.getMessage()); return false; }
  }

  @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
    if (code == 77 && pendingBytes != null) {
      if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) doSave(pendingBytes, pendingName);
      else toastJs("Cần cấp quyền bộ nhớ để lưu ảnh");
      pendingBytes = null;
    }
  }

  private class Bridge {
    @JavascriptInterface public int version() { return 2; }
    /** pkg: "" = bảng chọn ứng dụng; "jp.naver.line.android" = LINE; "com.zing.zalo" = Zalo */
    @JavascriptInterface public void shareImage(String b64, String name, String text, String pkg) {
      final byte[] b = decode(b64); final String n = safeName(name);
      runOnUiThread(() -> doShare(b, n, text, pkg));
    }
    @JavascriptInterface public void saveImage(String b64, String name) {
      final byte[] b = decode(b64); final String n = safeName(name);
      runOnUiThread(() -> {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
          pendingBytes = b; pendingName = n;
          requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 77);
        } else doSave(b, n);
      });
    }
  }

  @Override public void onBackPressed() { if (wv.canGoBack()) wv.goBack(); else super.onBackPressed(); }
}
