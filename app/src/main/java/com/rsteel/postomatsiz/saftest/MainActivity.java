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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new NativeStoreBridge(), "NativeStore");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return url == null || !url.startsWith("https://postomat.local/");
            }
        });

        setContentView(webView);
        try {
            String html = loadClassicUiHtml();
            webView.loadDataWithBaseURL("https://postomat.local/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            webView.loadDataWithBaseURL(
                    "https://postomat.local/",
                    "<html><body style='font-family:sans-serif;padding:24px'><h2>Ошибка загрузки интерфейса</h2><p>" + escapeHtml(e.toString()) + "</p></body></html>",
                    "text/html",
                    "UTF-8",
                    null);
        }
    }

    private String loadClassicUiHtml() throws Exception {
        String b64;
        try (InputStream in = getAssets().open("classic_ui.b64")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            b64 = out.toString("UTF-8").replace("\n", "").replace("\r", "").trim();
        }
        byte[] gz = Base64.decode(b64, Base64.DEFAULT);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gz));
             ByteArrayOutputStream html = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = gzip.read(buf)) > 0) html.write(buf, 0, n);
            return html.toString("UTF-8");
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript(
                    "(function(){try{if(typeof appBack==='function'&&typeof canAppBack==='function'&&canAppBack()){appBack();return 'handled';}}catch(e){}return 'none';})()",
                    value -> {
                        if (value == null || !value.contains("handled")) MainActivity.super.onBackPressed();
                    });
        } else {
            super.onBackPressed();
        }
    }

    private Uri getTreeUri() {
        String raw = prefs.getString(PREF_TREE_URI, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            return Uri.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasPersistedTreePermission(Uri treeUri) {
        if (treeUri == null) return false;
        for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            if (treeUri.equals(p.getUri()) && p.isReadPermission() && p.isWritePermission()) return true;
        }
        return false;
    }

    private Uri getRootDocumentUri(Uri treeUri) {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
    }

    private String displayName(Uri documentUri) {
        String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor c = getContentResolver().query(documentUri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return "";
    }

    private Uri findChild(Uri parentDocumentUri, String name) {
        String parentId = DocumentsContract.getDocumentId(parentDocumentUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parentDocumentUri, parentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor c = getContentResolver().query(children, projection, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    if (name.equals(c.getString(1))) {
                        return DocumentsContract.buildDocumentUriUsingTree(parentDocumentUri, c.getString(0));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Uri ensureDirectory(Uri parentDocumentUri, String name) throws Exception {
        Uri existing = findChild(parentDocumentUri, name);
        if (existing != null) return existing;
        Uri created = DocumentsContract.createDocument(
                getContentResolver(),
                parentDocumentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name);
        if (created == null) throw new IllegalStateException("Не удалось создать папку " + name);
        return created;
    }

    private Uri resolvePostomatRoot() throws Exception {
        Uri treeUri = getTreeUri();
        if (!hasPersistedTreePermission(treeUri)) throw new IllegalStateException("Папка хранения не выбрана");
        Uri root = getRootDocumentUri(treeUri);
        if ("Postomat_SIZ".equalsIgnoreCase(displayName(root))) return root;
        return ensureDirectory(root, "Postomat_SIZ");
    }

    private String normalizeRelativePath(String incoming) {
        String p = incoming == null ? "" : incoming.replace('\\', '/');
        int marker = p.indexOf("Postomat_SIZ/");
        if (marker >= 0) p = p.substring(marker + "Postomat_SIZ/".length());
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private String mimeFor(String fileName) {
        String n = fileName.toLowerCase();
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private Uri ensureFile(Uri parent, String fileName, String mime) throws Exception {
        Uri existing = findChild(parent, fileName);
        if (existing != null) return existing;
        Uri created = DocumentsContract.createDocument(getContentResolver(), parent, mime, fileName);
        if (created == null) throw new IllegalStateException("Не удалось создать файл " + fileName);
        return created;
    }

    private Uri resolveDocument(String incomingPath, boolean createFile) throws Exception {
        String rel = normalizeRelativePath(incomingPath);
        if (rel.isEmpty()) throw new IllegalArgumentException("Пустой путь");
        String[] rawParts = rel.split("/");
        List<String> parts = new ArrayList<>();
        for (String part : rawParts) if (part != null && !part.trim().isEmpty()) parts.add(part.trim());
        if (parts.isEmpty()) throw new IllegalArgumentException("Пустой путь");

        Uri current = resolvePostomatRoot();
        for (int i = 0; i < parts.size() - 1; i++) current = ensureDirectory(current, parts.get(i));
        String fileName = parts.get(parts.size() - 1);
        if (!createFile) return findChild(current, fileName);
        return ensureFile(current, fileName, mimeFor(fileName));
    }

    public class NativeStoreBridge {
        @JavascriptInterface
        public boolean hasRootFolder() {
            return hasPersistedTreePermission(getTreeUri());
        }

        @JavascriptInterface
        public String getRootFolderLabel() {
            Uri tree = getTreeUri();
            if (!hasPersistedTreePermission(tree)) return "Папка не выбрана";
            try {
                Uri root = getRootDocumentUri(tree);
                String name = displayName(root);
                if ("Postomat_SIZ".equalsIgnoreCase(name)) return "Выбрано: " + name;
                return "Выбрано: " + (name.isEmpty() ? "папка" : name) + " → Postomat_SIZ";
            } catch (Exception e) {
                return "Папка выбрана";
            }
        }

        @JavascriptInterface
        public void chooseRootFolder() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                startActivityForResult(intent, REQ_TREE);
            });
        }

        @JavascriptInterface
        public String saveBase64File(String path, String base64) {
            try {
                Uri target = resolveDocument(path, true);
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                try (OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                    if (out == null) throw new IllegalStateException("Нет доступа к файлу");
                    out.write(data);
                    out.flush();
                }
                return "Postomat_SIZ/" + normalizeRelativePath(path);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean deleteFile(String path) {
            try {
                Uri target = resolveDocument(path, false);
                return target != null && DocumentsContract.deleteDocument(getContentResolver(), target);
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
            prefs.edit().putString(PREF_TREE_URI, uri.toString()).apply();
            if (webView != null) {
                webView.post(() -> webView.evaluateJavascript(
                        "if(typeof onNativeFolderSelected==='function'){onNativeFolderSelected('ok');}", null));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("NativeStore");
            webView.destroy();
        }
        super.onDestroy();
    }
}
