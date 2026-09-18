package com.rsteel.postomatsiz.saftest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 2001;
    private static final String PREFS = "postomat_siz_native";
    private static final String PREF_TREE_URI = "tree_uri";
    private WebView webView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        webView.addJavascriptInterface(new NativeStoreBridge(), "NativeStore");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return url == null || !url.startsWith("https://postomat.local/");
            }
        });
        setContentView(webView);
        try {
            webView.loadDataWithBaseURL("https://postomat.local/", loadClassicUiHtml(), "text/html", "UTF-8", null);
        } catch (Exception e) {
            webView.loadDataWithBaseURL("https://postomat.local/",
                    "<html><body style='font-family:sans-serif;padding:24px'><h2>Ошибка загрузки интерфейса</h2><p>" + escapeHtml(e.toString()) + "</p></body></html>",
                    "text/html", "UTF-8", null);
        }
    }

    private String loadClassicUiHtml() throws Exception {
        StringBuilder b64 = new StringBuilder(52000);
        for (int i = 0; i < 9; i++) {
            String name = String.format(Locale.US, "classic/part%02d.txt", i);
            try (InputStream in = getAssets().open(name); ByteArrayOutputStream part = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) part.write(buf, 0, n);
                b64.append(part.toString("UTF-8").replace("\n", "").replace("\r", "").trim());
            }
        }
        byte[] gz = Base64.decode(b64.toString(), Base64.DEFAULT);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gz));
             ByteArrayOutputStream html = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = gzip.read(buf)) > 0) html.write(buf, 0, n);
            String page = html.toString("UTF-8");
            page = page.replace("title='Активные сотрудники';goTab='employees';", "title='Сотрудники компании';goTab='employees';");
            page = page.replace("title='Активные ячейки';goTab='cells';", "title='Ячейки постамата';goTab='cells';");
            page = page.replace("title='Активные виды СИЗ';goTab='ppe';", "title='Перечень СИЗ';goTab='ppe';");
            page = page.replace("title='Позиции, требующие внимания';goTab='inventory';", "title='Требует пополнения';goTab='inventory';");
            page = page.replace("title='Позиции, требующие пополнения';goTab='inventory';", "title='Требует пополнения';goTab='inventory';");
            page = page.replace("title='Сотрудники без ячейки';goTab='employees';", "title='Ячейка не присвоена следующим сотрудникам';goTab='employees';");
            page = page.replace("title='Сотрудники без назначеных СИЗ';goTab='employees';", "title='Сотрудники без назначенных СИЗ';goTab='employees';");
            page = page.replace("title='Свободные активные ячейки';goTab='cells';", "title='Свободные ячейки';goTab='cells';");
            page = page.replace("title='Некорректные связи';goTab='assignments';", "title='Ошибки';goTab='assignments';");
            return page;
        }
    }

    private String escapeHtml(String x) {
        return x == null ? "" : x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript(
                "(function(){try{if(typeof appBack==='function'&&typeof canAppBack==='function'&&canAppBack()){appBack();return 'handled';}}catch(e){}return 'none';})()",
                v -> { if (v == null || !v.contains("handled")) MainActivity.super.onBackPressed(); });
    }

    private Uri treeUri() {
        try { String x = prefs.getString(PREF_TREE_URI, ""); return x == null || x.isEmpty() ? null : Uri.parse(x); }
        catch (Exception e) { return null; }
    }

    private boolean hasTree(Uri u) {
        if (u == null) return false;
        for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions())
            if (u.equals(p.getUri()) && p.isReadPermission() && p.isWritePermission()) return true;
        return false;
    }

    private Uri rootDoc(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            return c != null && c.moveToFirst() ? c.getString(0) : "";
        } catch (Exception e) { return ""; }
    }

    private Uri findChild(Uri parent, String name) {
        try {
            String id = DocumentsContract.getDocumentId(parent);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id);
            try (Cursor c = getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (c != null) while (c.moveToNext()) if (name.equals(c.getString(1)))
                    return DocumentsContract.buildDocumentUriUsingTree(parent, c.getString(0));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Uri ensureDir(Uri parent, String name) throws Exception {
        Uri u = findChild(parent, name);
        if (u != null) return u;
        u = DocumentsContract.createDocument(getContentResolver(), parent,
                DocumentsContract.Document.MIME_TYPE_DIR, name);
        if (u == null) throw new IllegalStateException("Не удалось создать папку " + name);
        return u;
    }

    private Uri postomatRoot() throws Exception {
        Uri tree = treeUri();
        if (!hasTree(tree)) throw new IllegalStateException("Папка хранения не выбрана");
        Uri root = rootDoc(tree);
        return "Postomat_SIZ".equalsIgnoreCase(displayName(root)) ? root : ensureDir(root, "Postomat_SIZ");
    }

    private String relative(String path) {
        String p = path == null ? "" : path.replace('\\', '/');
        int k = p.indexOf("Postomat_SIZ/");
        if (k >= 0) p = p.substring(k + 13);
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private String mime(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".csv")) return "text/csv";
        return "application/octet-stream";
    }

    private Uri resolve(String path, boolean create) throws Exception {
        String rel = relative(path);
        String[] raw = rel.split("/");
        List<String> parts = new ArrayList<>();
        for (String p : raw) if (!p.trim().isEmpty()) parts.add(p.trim());
        if (parts.isEmpty()) throw new IllegalArgumentException("Пустой путь");
        Uri dir = postomatRoot();
        for (int i = 0; i < parts.size() - 1; i++) dir = ensureDir(dir, parts.get(i));
        String file = parts.get(parts.size() - 1);
        Uri u = findChild(dir, file);
        if (u != null || !create) return u;
        u = DocumentsContract.createDocument(getContentResolver(), dir, mime(file), file);
        if (u == null) throw new IllegalStateException("Не удалось создать файл " + file);
        return u;
    }

    public class NativeStoreBridge {
        @JavascriptInterface public boolean hasRootFolder() { return hasTree(treeUri()); }

        @JavascriptInterface public String getRootFolderLabel() {
            Uri t = treeUri();
            if (!hasTree(t)) return "Папка не выбрана";
            String n = displayName(rootDoc(t));
            return "Postomat_SIZ".equalsIgnoreCase(n) ? "Выбрано: Postomat_SIZ" :
                    "Выбрано: " + (n.isEmpty() ? "папка" : n) + " → Postomat_SIZ";
        }

        @JavascriptInterface public void chooseRootFolder() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                startActivityForResult(i, REQ_TREE);
            });
        }

        @JavascriptInterface public String saveBase64File(String path, String base64) {
            try {
                Uri target = resolve(path, true);
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                try (OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                    if (out == null) return "";
                    out.write(bytes); out.flush();
                }
                return "Postomat_SIZ/" + relative(path);
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface public boolean deleteFile(String path) {
            try { Uri u = resolve(path, false); return u != null && DocumentsContract.deleteDocument(getContentResolver(), u); }
            catch (Exception e) { return false; }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
            prefs.edit().putString(PREF_TREE_URI, uri.toString()).apply();
            webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onNativeFolderSelected==='function'){onNativeFolderSelected('ok');}", null));
        } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.removeJavascriptInterface("NativeStore"); webView.destroy(); }
        super.onDestroy();
    }
}
