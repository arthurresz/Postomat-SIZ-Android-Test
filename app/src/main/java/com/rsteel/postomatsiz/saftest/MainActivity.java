package com.rsteel.postomatsiz.saftest;

import android.app.Activity;
import android.content.ClipData;
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
            page = page.replace("const APP_VERSION='3.0-standard-classic-ui';", "const APP_VERSION='3.3-standard-classic-ui';");
            page = page.replace(" placeholder=\"warehouse@company.kz\"", "");
            int scriptEnd = page.lastIndexOf("</script>");
            if (scriptEnd >= 0) page = page.substring(0, scriptEnd) + uiPatchScript() + page.substring(scriptEnd);
            return page;
        }
    }

    private String uiPatchScript() {
        return """

// ===== v3.3 reports/email/physical backup patch =====
window.__pendingStorageAction=null;

function sendReportByEmail(type,key,email){
  email=String(email||'').trim();
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)){toast('Введите корректный Email','error');return false}
  if(!nativeStorageAvailable()){
    window.__pendingStorageAction={kind:'email',type,key,email};
    chooseNativeStorage();
    return false;
  }
  try{
    const rec=saveReport(type,key,false);
    if(typeof NativeStore==='undefined'||typeof NativeStore.sendEmailAttachment!=='function'){
      toast('Отправка Email недоступна в этой сборке','error');return false;
    }
    const subject=(type==='weekly'?'Еженедельный':'Ежемесячный')+' отчёт СИЗ — '+(rec.label||key);
    const body='Отчёт сформирован в приложении «Постомат СИЗ». Файл приложен к письму.';
    const result=String(NativeStore.sendEmailAttachment(STORAGE_ROOT+'/Reports/'+rec.fileName,email,subject,body)||'');
    if(result==='OK'){toast('Открыто приложение для отправки Email. Проверьте письмо и нажмите «Отправить».','ok');return true}
    if(result==='NO_APP'){toast('На планшете не найдено приложение для отправки Email','error');return false}
    if(result==='FILE_NOT_FOUND'){toast('Не найден файл отчёта для отправки','error');return false}
    toast('Не удалось открыть отправку Email'+(result?': '+result:''),'error');return false;
  }catch(e){toast('Ошибка отправки Email: '+(e.message||e),'error');return false}
}

window.onNativeFolderSelected=function(){
  try{
    const pending=window.__pendingStorageAction;window.__pendingStorageAction=null;
    if(pending&&pending.kind==='weekly'){
      saveWeeklyReport(pending.key,false);
      toast('Недельный отчёт сохранён в Postomat_SIZ/Reports','ok');
    }else if(pending&&pending.kind==='monthly'){
      saveMonthlyReport(pending.key,false);
      toast('Месячный отчёт сохранён в Postomat_SIZ/Reports','ok');
    }else if(pending&&pending.kind==='backup'){
      const rec=createBackup(false);
      toast('Backup сохранён в памяти планшета: '+(rec.path||'Postomat_SIZ/Backup'),'ok');
    }else if(pending&&pending.kind==='email'){
      sendReportByEmail(pending.type,pending.key,pending.email);
    }else{
      ensureArchive();
      const hasPhysical=db.archive.backups.some(b=>b.storage==='DEVICE_FILE'&&b.path);
      if(!hasPhysical&&nativeStorageAvailable()){
        const rec=createBackup(false);
        toast('Папка хранения подключена. Первый backup создан: '+(rec.path||'Postomat_SIZ/Backup'),'ok');
      }else toast('Папка хранения подключена','ok');
    }
    showAdmin('reports');
  }catch(e){toast('Ошибка после выбора папки: '+(e.message||e),'error');showAdmin('reports')}
};

const __oldRunStorageMaintenance=runStorageMaintenance;
runStorageMaintenance=function(){
  __oldRunStorageMaintenance();
  try{
    ensureArchive();
    const hasPhysical=db.archive.backups.some(b=>b.storage==='DEVICE_FILE'&&b.path);
    if(nativeStorageAvailable()&&!hasPhysical){createBackup(true);saveDb()}
  }catch(e){console.error('physical backup maintenance',e)}
};

adminReports=function(){
  ensureArchive();
  const months=reportMonths(),weeks=reportWeeks(),mSel=window.__reportMonth||prevMonthKey(),wSel=window.__reportWeek||prevWeekKey();
  const monthOptions=months.map(k=>`<option value="${k}" ${k===mSel?'selected':''}>${k}</option>`).join('');
  const weekOptions=weeks.map(k=>`<option value="${k}" ${k===wSel?'selected':''}>${weekLabel(k)}</option>`).join('');
  const reports=db.archive.reports.slice().sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt)).map(r=>`<div class="archiveItem"><div class="row"><b>${r.type==='weekly'?'Недельный':'Месячный'} отчёт ${esc(r.label||r.key)}</b><span class="badge">${r.auto?'АВТО':'РУЧНОЙ'}</span></div><div class="meta">${fmtDate(r.createdAt)} • ${esc(r.fileName||'Отчёт.xlsx')}<br>${r.storage==='DEVICE_FILE'?'Файл: '+esc(r.path||r.fileName):'Хранение: внутренний архив'}</div><div class="archiveActions"><button class="btn small primary" data-view-report="${esc(r.id||'')}">Просмотреть</button></div></div>`).join('');
  const backups=db.archive.backups.slice(0,20).map(r=>`<div class="archiveItem"><div class="row"><b>Backup ${fmtDate(r.createdAt)}</b><span class="badge">${r.auto?'АВТО':'РУЧНОЙ'}</span></div><div class="meta">Размер: ${Math.max(1,Math.round(Number(r.size||0)/1024))} КБ<br>${r.storage==='DEVICE_FILE'&&r.path?'Файл в памяти планшета: '+esc(r.path):'Хранение: внутренний архив приложения'}</div></div>`).join('');
  const lastBackup=db.archive.backups[0]||null;
  const last=db.archive.lastBackupAt?fmtDate(db.archive.lastBackupAt):'ещё не создавался';
  const lastBackupPath=lastBackup&&lastBackup.storage==='DEVICE_FILE'&&lastBackup.path?`<div class="meta" style="margin-top:6px">Файл: ${esc(lastBackup.path)}</div>`:'';
  return adminHeader('Отчёты','Еженедельный и ежемесячный отчёт читаются сверху вниз как один лист.')+
  `<div class="betaBar">● ДЕМО-РЕЖИМ: реальные ячейки не открываются. Отчёты и backup сохраняются физически через штатную папку Android.</div>
  <div class="reportGrid">
   <div class="reportCard"><h3>Папка хранения</h3><p>Один раз выберите <b>Documents</b> или существующую папку <b>Postomat_SIZ</b>. Приложение будет использовать подпапки Reports и Backup.</p><div class="reportStatus ${nativeStorageAvailable()?'ok':'internal'}" style="margin-top:12px">${esc(nativeStorageLabel())}</div><button id="chooseStorageRoot" class="btn outline block" style="margin-top:12px">ВЫБРАТЬ ПАПКУ ХРАНЕНИЯ</button></div>
   <div class="reportCard"><h3>Еженедельный отчёт</h3><p>Ключевые показатели → расход по СИЗ → требует внимания → выдачи → пополнения.</p><div class="field" style="margin-top:12px"><label>Неделя</label><select id="reportWeek" class="select">${weekOptions}</select></div><div style="display:grid;gap:8px"><button id="previewWeekReport" class="btn outline block">ПРЕДПРОСМОТР</button><button id="makeWeekReport" class="btn primary block">СФОРМИРОВАТЬ В АРХИВ</button></div><div class="field" style="margin-top:14px"><label>Email</label><input id="weeklyReportEmail" class="input" data-vk="latin" value="" autocomplete="off"></div><button id="sendWeekReportEmail" class="btn primary block">ОТПРАВИТЬ ЕЖЕНЕДЕЛЬНЫЙ ОТЧЁТ НА EMAIL</button></div>
   <div class="reportCard"><h3>Ежемесячный отчёт</h3><p>Ключевые показатели → расход → сотрудники → отклонения → выдачи → пополнения → корректировки.</p><div class="field" style="margin-top:12px"><label>Месяц</label><select id="reportMonth" class="select">${monthOptions}</select></div><div style="display:grid;gap:8px"><button id="previewMonthReport" class="btn outline block">ПРЕДПРОСМОТР</button><button id="makeReport" class="btn primary block">СФОРМИРОВАТЬ В АРХИВ</button></div><div class="field" style="margin-top:14px"><label>Email</label><input id="monthlyReportEmail" class="input" data-vk="latin" value="" autocomplete="off"></div><button id="sendMonthReportEmail" class="btn primary block">ОТПРАВИТЬ ЕЖЕМЕСЯЧНЫЙ ОТЧЁТ НА EMAIL</button></div>
   <div class="reportCard"><h3>Резервные копии</h3><p>Backup сохраняется физическим файлом в выбранную папку <b>Postomat_SIZ/Backup</b>.</p><div class="reportStatus ${lastBackup&&lastBackup.storage==='DEVICE_FILE'?'ok':'internal'}">Последний: ${last}${lastBackupPath}</div><button id="backupNow" class="btn green block" style="margin-top:12px">СОЗДАТЬ BACKUP В ПАМЯТИ ПЛАНШЕТА</button><div class="fieldRow" style="margin-top:12px"><div class="field"><label>Backup каждые, дней</label><input id="backupDays" class="input" data-vk="number" value="${db.settings.backupEveryDays||7}"></div><div class="field"><label>Хранить недель, шт.</label><input id="backupWeeks" class="input" data-vk="number" value="${db.settings.backupRetentionWeeks||12}"></div></div><div class="field"><label>Хранить месячные отчёты, месяцев</label><input id="reportMonthsKeep" class="input" data-vk="number" value="${db.settings.reportRetentionMonths||12}"></div><button id="saveArchiveSettings" class="btn outline block">СОХРАНИТЬ НАСТРОЙКИ</button></div>
  </div>
  <div class="sectionLabel">Архив сформированных отчётов</div>${reports||'<div class="empty">Архив пока пуст. Выберите период и нажмите «Сформировать в архив».</div>'}
  <div class="sectionLabel">Резервные копии</div>${backups||'<div class="empty">Backup пока нет</div>'}`;
};

const __oldWireAdmin=wireAdmin;
wireAdmin=function(tab){
  __oldWireAdmin(tab);
  if(tab==='reports'){
    if(byId('makeWeekReport'))byId('makeWeekReport').onclick=()=>{const key=byId('reportWeek').value;if(!nativeStorageAvailable()){window.__pendingStorageAction={kind:'weekly',key};chooseNativeStorage();return}saveWeeklyReport(key,false);toast('Недельный отчёт сохранён в Postomat_SIZ/Reports','ok');showAdmin('reports')};
    if(byId('makeReport'))byId('makeReport').onclick=()=>{const key=byId('reportMonth').value;if(!nativeStorageAvailable()){window.__pendingStorageAction={kind:'monthly',key};chooseNativeStorage();return}saveMonthlyReport(key,false);toast('Месячный отчёт сохранён в Postomat_SIZ/Reports','ok');showAdmin('reports')};
    if(byId('sendWeekReportEmail'))byId('sendWeekReportEmail').onclick=()=>sendReportByEmail('weekly',byId('reportWeek').value,byId('weeklyReportEmail').value);
    if(byId('sendMonthReportEmail'))byId('sendMonthReportEmail').onclick=()=>sendReportByEmail('monthly',byId('reportMonth').value,byId('monthlyReportEmail').value);
    if(byId('backupNow'))byId('backupNow').onclick=()=>{if(!nativeStorageAvailable()){window.__pendingStorageAction={kind:'backup'};chooseNativeStorage();return}const rec=createBackup(false);toast('Backup сохранён в памяти планшета: '+(rec.path||'Postomat_SIZ/Backup'),'ok');showAdmin('reports')};
    attachVirtualInputs(document);
  }
};

""";
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

        @JavascriptInterface public String sendEmailAttachment(String path, String email, String subject, String body) {
            try {
                Uri target = resolve(path, false);
                if (target == null) return "FILE_NOT_FOUND";
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                send.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
                send.putExtra(Intent.EXTRA_SUBJECT, subject);
                send.putExtra(Intent.EXTRA_TEXT, body);
                send.putExtra(Intent.EXTRA_STREAM, target);
                send.setClipData(ClipData.newUri(getContentResolver(), "Отчёт СИЗ", target));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (send.resolveActivity(getPackageManager()) == null) return "NO_APP";
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "Отправить отчёт")));
                return "OK";
            } catch (Exception e) {
                return "ERROR: " + e.getClass().getSimpleName();
            }
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
