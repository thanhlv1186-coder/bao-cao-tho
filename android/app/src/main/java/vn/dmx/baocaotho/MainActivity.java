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
import java.io.InputStream;

/** WebView mở giao diện đóng gói trong app (assets) qua https://appassets.androidplatform.net,
 *  dữ liệu tải trực tuyến từ GitHub mỗi lần mở / làm mới. */
public class MainActivity extends Activity {
  private static final String HOST = "appassets.androidplatform.net";
  private WebView wv;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
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
          InputStream in = getAssets().open(p);
          String mime = p.endsWith(".html") ? "text/html" : p.endsWith(".js") ? "application/javascript"
              : p.endsWith(".css") ? "text/css" : p.endsWith(".png") ? "image/png" : p.endsWith(".json") ? "application/json" : "application/octet-stream";
          return new WebResourceResponse(mime, "utf-8", in);
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
    if (b != null) wv.restoreState(b); else wv.loadUrl("https://" + HOST + "/assets/index.html");
  }
  @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); wv.saveState(o); }
  @Override public void onBackPressed() { if (wv.canGoBack()) wv.goBack(); else super.onBackPressed(); }
}
