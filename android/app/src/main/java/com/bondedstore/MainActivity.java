package com.bondedstore;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A shell around the store. All of the app is the page in assets/www; this class
 * only gives it the three things a browser tab cannot do on a phone: serve
 * itself from a real origin, print, and hand a file to another app.
 */
public class MainActivity extends android.app.Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assets are served over https://appassets.androidplatform.net rather than
        // file://. That gives the page a secure origin, which is what localStorage
        // and the camera both require — from file:// the store would silently
        // forget its stock and the scanner would never start.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // the store's whole ledger lives here
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        web.setBackgroundColor(0xFF06121C);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true;                    // nothing in this app leaves the app
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // Only ever the camera, and only once Android itself has granted it.
                runOnUiThread(() -> {
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                            if (hasCamera()) request.grant(new String[]{r});
                            else { askCamera(); request.deny(); }
                            return;
                        }
                    }
                    request.deny();
                });
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);

        if (!hasCamera()) askCamera();
        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");

    }

    @Override
    public void onBackPressed() {
        // Let the page close its own sheet or scanner first; it calls
        // Android.exitApp() only when there is nothing left to dismiss.
        web.evaluateJavascript("window.dispatchEvent(new Event('bs-back'))", null);
    }

    private boolean hasCamera() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void askCamera() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 11);
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    /** The small surface the page is allowed to reach the phone through. */
    private class Bridge {

        /** Print, or "save as PDF" — the same system dialog offers both. */
        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm == null) return;
                pm.print("Bonded Store",
                        web.createPrintDocumentAdapter("Bonded Store"),
                        new PrintAttributes.Builder().build());
            });
        }

        /** Write a CSV or backup into app storage and offer it to other apps. */
        @JavascriptInterface
        public void saveFile(String name, String content, String mime) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getFilesDir(), "exports");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("cannot create exports folder");
                    File f = new File(dir, sanitize(name));
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType(mime == null ? "text/plain" : mime)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_SUBJECT, f.getName())
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Send " + f.getName()));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void exitApp() { runOnUiThread(MainActivity.this::finish); }
    }

    /** Keep a filename from the page inside the exports folder. */
    private static String sanitize(String name) {
        String n = (name == null ? "" : name).replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.isEmpty() || n.startsWith(".")) n = "export" + n;
        return n.length() > 100 ? n.substring(0, 100) : n;
    }
}
