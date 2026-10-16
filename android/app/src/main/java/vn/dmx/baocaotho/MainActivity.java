package vn.dmx.baocaotho;

import android.app.Activity;
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
    wv.setBackgroundColor(0xFF020817);
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

  @Override public void onBackPressed() { if (wv.canGoBack()) wv.goBack(); else super.onBackPressed(); }
}
