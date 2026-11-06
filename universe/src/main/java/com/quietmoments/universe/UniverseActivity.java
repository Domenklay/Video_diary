package com.quietmoments.universe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Embedded, offline universe screen. Does not access host diary database. */
public final class UniverseActivity extends Activity {
    public static final String RESULT_DATE = "com.quietmoments.universe.DATE";
    public static final String RESULT_ACTION = "com.quietmoments.universe.ACTION";
    private static final int EXPORT_JSON = 7011;
    private static final int IMPORT_JSON = 7012;
    private WebView web;
    private ValueCallback<Uri[]> upload;
    private String pendingJson;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(21,19,34));
        getWindow().setNavigationBarColor(Color.rgb(21,19,34));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(21,19,34));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true); // packaged file:///android_asset/universe/ only
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new StorageBridge(), "AndroidExport");
        web.addJavascriptInterface(new UniverseBackupBridge(), "UniverseBackup");
        web.addJavascriptInterface(new DiaryBridge(), "DiaryHost");
        web.addJavascriptInterface(new AppNavigationBridge(), "AppNav");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // no external navigation from offline content
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String theme = getIntent().getStringExtra("QM_THEME");
                if (theme == null) {
                    theme = getSharedPreferences("diary_theme", MODE_PRIVATE).getString("chosen", "moon");
                }
                if (theme == null || !theme.matches("moon|rose|forest|sea|dawn|night")) {
                    theme = "moon";
                }
                view.evaluateJavascript(
                    "document.documentElement.setAttribute('data-app-theme','" + theme + "')", null);
                boolean motion = getIntent().getBooleanExtra("QM_MOTION",
                    getSharedPreferences("diary_theme", MODE_PRIVATE)
                        .getBoolean("motion_enabled", true));
                view.evaluateJavascript(
                    "document.documentElement.setAttribute('data-motion','" +
                        (motion ? "on" : "off") + "')", null);
                boolean dark = "night".equals(theme);
                getWindow().setStatusBarColor(
                    dark ? Color.rgb(16,29,42) :
                    ("rose".equals(theme) ? Color.rgb(255,244,246) :
                     "forest".equals(theme) ? Color.rgb(241,248,242) :
                     "sea".equals(theme) ? Color.rgb(240,249,252) :
                     "dawn".equals(theme) ? Color.rgb(255,248,235) : Color.rgb(250,244,252)));
                getWindow().setNavigationBarColor(dark ? Color.rgb(22,38,51) : Color.rgb(255,251,255));
                getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (upload != null) upload.onReceiveValue(null);
                upload = cb;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("application/json");
                try { startActivityForResult(pick, IMPORT_JSON); return true; }
                catch (Exception ex) { upload.onReceiveValue(null); upload = null; return false; }
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/universe/index.html");
    }

    /** Mirror of the local WebView state, included in local album ZIP exports. */
    private final class UniverseBackupBridge {
        @JavascriptInterface public void saveSnapshot(String json) {
            if (json == null || json.length() > 2_000_000) return;
            try {
                JSONObject state = new JSONObject(json);
                File root = new File(getFilesDir(), "universe_backup");
                if (!root.exists() && !root.mkdirs()) return;
                File temp = new File(root, "snapshot.pending");
                try (FileOutputStream stream = new FileOutputStream(temp)) {
                    stream.write(state.toString().getBytes(StandardCharsets.UTF_8));
                    stream.getFD().sync();
                }
                File dest = new File(root, "snapshot.json");
                if (!temp.renameTo(dest)) {
                    Files.copy(temp.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    temp.delete();
                }
            } catch (Exception ignored) { }
        }
        @JavascriptInterface public String consumeRestore() {
            try {
                File root = new File(getFilesDir(), "universe_backup");
                File pending = new File(root, "restore.json");
                if (!pending.isFile() || pending.length() > 2_000_000) return "";
                String json = new String(Files.readAllBytes(pending.toPath()), StandardCharsets.UTF_8);
                new JSONObject(json); // validate before consuming
                if (!pending.delete()) return "";
                return json;
            } catch (Exception ignored) { return ""; }
        }
    }

    /** Bottom navigation shared with the native diary. Never changes persisted diary data. */
    private final class AppNavigationBridge {
        @JavascriptInterface public void openTab(String tab) {
            if (tab == null) return;
            runOnUiThread(() -> {
                String action;
                switch (tab) {
                    case "home": action = "NAVIGATE_HOME"; break;
                    case "diary": action = "NAVIGATE_DIARY"; break;
                    case "settings": action = "NAVIGATE_SETTINGS"; break;
                    default: return;
                }
                Intent result = new Intent();
                result.putExtra(RESULT_ACTION, action);
                if ("diary".equals(tab)) {
                    result.putExtra(RESULT_DATE, java.time.LocalDate.now().toString());
                }
                setResult(RESULT_OK, result);
                finish();
            });
        }
    }

    private final class DiaryBridge {
        @JavascriptInterface public void openDiary(String date, String section) {
            runOnUiThread(() -> {
                // Host receives a request, decides how to open diary. No direct DB mutation.
                Intent response = new Intent();
                response.putExtra(RESULT_DATE, date);
                response.putExtra(RESULT_ACTION, "NEW_DIARY_ENTRY");
                setResult(RESULT_OK, response);
                finish();
            });
        }
    }

    private final class StorageBridge {
        @JavascriptInterface public void saveJson(String json) {
            runOnUiThread(() -> {
                if (json == null || json.length() > 5_000_000) {
                    Toast.makeText(UniverseActivity.this, "Слишком большая резервная копия", Toast.LENGTH_LONG).show();
                    return;
                }
                pendingJson = json;
                Intent export = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                export.addCategory(Intent.CATEGORY_OPENABLE);
                export.setType("application/json");
                export.putExtra(Intent.EXTRA_TITLE, "my-universe-backup.json");
                startActivityForResult(export, EXPORT_JSON);
            });
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == IMPORT_JSON) {
            if (upload != null) {
                Uri[] selected = result == RESULT_OK && data != null && data.getData() != null
                    ? new Uri[]{data.getData()} : null;
                upload.onReceiveValue(selected);
                upload = null;
            }
        } else if (code == EXPORT_JSON) {
            if (result == RESULT_OK && data != null && data.getData() != null && pendingJson != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) out.write(pendingJson.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    Toast.makeText(this, "Не удалось сохранить файл", Toast.LENGTH_LONG).show();
                }
            }
            pendingJson = null;
        }
    }

    @Override public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(typeof route !== 'undefined' && route !== 'home') ? 'subpage' : 'home'", result -> {
            if ("\"subpage\"".equals(result)) web.evaluateJavascript("go('home')", null);
            else finish();
        });
    }
    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
