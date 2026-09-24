package com.ardal.operations;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

public class MainActivity extends Activity {
    private static final int REQUEST_SCAN = 2201;
    private static final int REQUEST_PICK_FILE = 2202;
    private static final String ARDAL_URL = "https://script.google.com/macros/s/AKfycby8HTm1GSMguFmYysNfYScbkLh4EQ2OPlKzQZNM8efAHiEMqkl5sX9oyDN-ri0ZcD8-/exec";

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;
    private GmsDocumentScanner scanner;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
                if (host.endsWith("google.com") || host.endsWith("googleusercontent.com") || host.endsWith("gstatic.com")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = filePathCallback;
                launchDocumentScanner();
                return true;
            }
        });

        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(10)
                .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        scanner = GmsDocumentScanning.getClient(options);

        if (savedInstanceState == null) {
            webView.loadUrl(ARDAL_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void launchDocumentScanner() {
        Toast.makeText(this, "Відкриваю сканер документа…", Toast.LENGTH_SHORT).show();
        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    try {
                        startIntentSenderForResult(intentSender, REQUEST_SCAN, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        launchFallbackPicker("Не вдалося запустити сканер. Обери PDF або фото.");
                    }
                })
                .addOnFailureListener(e -> launchFallbackPicker(
                        "Сканер Google недоступний на цьому пристрої. Обери PDF або фото."));
    }

    private void launchFallbackPicker(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/jpeg", "image/png"});
            startActivityForResult(intent, REQUEST_PICK_FILE);
        } catch (ActivityNotFoundException e) {
            finishFileRequest(null);
            new AlertDialog.Builder(this)
                    .setTitle("Не вдалося відкрити файл")
                    .setMessage("На пристрої немає доступного сканера або вибору файлів.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SCAN) {
            if (resultCode == RESULT_OK && data != null) {
                GmsDocumentScanningResult result = GmsDocumentScanningResult.fromActivityResultIntent(data);
                if (result != null && result.getPdf() != null && result.getPdf().getUri() != null) {
                    finishFileRequest(new Uri[]{result.getPdf().getUri()});
                    return;
                }
                if (result != null && result.getPages() != null && !result.getPages().isEmpty()) {
                    finishFileRequest(new Uri[]{result.getPages().get(0).getImageUri()});
                    return;
                }
            }
            finishFileRequest(null);
            return;
        }

        if (requestCode == REQUEST_PICK_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                finishFileRequest(new Uri[]{uri});
            } else {
                finishFileRequest(null);
            }
        }
    }

    private void finishFileRequest(Uri[] uris) {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(uris);
            pendingFileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        finishFileRequest(null);
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
