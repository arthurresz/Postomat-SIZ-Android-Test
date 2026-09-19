package com.rsteel.postomatsiz.saftest;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_TREE = 2001;
    private static final String PREFS = "postomat_siz_native";
    private static final String PREF_TREE_URI = "tree_uri";
    private static final String PREF_SMTP_EMAIL = "smtp_sender_email";
    private static final String PREF_SMTP_IV = "smtp_password_iv";
    private static final String PREF_SMTP_SECRET = "smtp_password_secret";
    private static final String PREF_CONTROLLER_ADDRESS = "postomat_controller_address";
    private static final String PREF_CONTROLLER_IV = "postomat_controller_password_iv";
    private static final String PREF_CONTROLLER_SECRET = "postomat_controller_password_secret";
    private static final String SMTP_KEY_ALIAS = "postomat_siz_smtp_key";
    private static final String CONTROLLER_KEY_ALIAS = "postomat_controller_key";
    private static final String SMTP_HOST = "smtp.mail.ru";
    private static final int SMTP_PORT = 465;
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
            page = page.replace(">+ Сотрудник<", ">Добавить сотрудника<");
            page = page.replace(">+ Ячейка<", ">Добавить ячейку<");
            page = page.replace(">+ СИЗ<", ">Добавить СИЗ<");
            page = page.replace(">+ Назначение<", ">Добавить назначение<");
            page = page.replace(">+ Назначить СИЗ<", ">Добавить СИЗ<");
            page = page.replace("const APP_VERSION='3.0-standard-classic-ui';", "const APP_VERSION='3.48-RC2';");
            page = page.replace(" placeholder=\"warehouse@company.kz\"", "");
            int scriptEnd = page.lastIndexOf("</script>");
            if (scriptEnd >= 0) page = page.substring(0, scriptEnd) + uiPatchScript() + warehouseReportPatchScript() + smtpMailPatchScript() + readableReportPatchScript() + replenishmentDataFixPatchScript() + monthlyMovementPreviewPatchScript() + weeklyReportPreviewPatchScript() + simpleIssueReportsPatchScript() + issueLogFixPatchScript() + initialCatalogPatchScript() + warehouseReportsPatchScript() + operatorUserGridPatchScript() + operatorPinSelectionPatchScript() + operatorGuidedFlowPatchScript() + operatorConsumablesGridPatchScript() + operatorQtyStepperPatchScript() + operatorLayoutFixPatchScript() + productionRcPatchScript() + autoStartConnectPatchScript() + page.substring(scriptEnd);
            page = page.replace("Постомат СИЗ", "Постомат расходных материалов");
            page = page.replace("СИЗ", "Расходные материалы");
            return page;
        }
    }

    private String uiPatchScript() {
        return """

// ===== v3.3 reports/email/physical backup patch =====
window.__pendingStorageAction=null;

function sendReportByEmail(type,key,email){
  email=String(email||'').trim();
  if(!/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email)){toast('Введите корректный Email','error');return false}
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
  const months=reportMonths(),weeks=reportWeeks(),mSel=window.__reportMonth||monthKey(new Date().getFullYear(),new Date().getMonth()+1),wSel=window.__reportWeek||weekKeyFromStart(weekBoundsFromDate(new Date()).start);
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
   <div class="reportCard"><h3>Еженедельный отчёт</h3><p>Журнал выдач СИЗ складом в ячейки сотрудников за выбранную неделю.</p><div class="field" style="margin-top:12px"><label>Неделя</label><select id="reportWeek" class="select">${weekOptions}</select></div><div style="display:grid;gap:8px"><button id="previewWeekReport" class="btn outline block">ПРЕДПРОСМОТР</button><button id="makeWeekReport" class="btn primary block">СФОРМИРОВАТЬ В АРХИВ</button></div><div class="field" style="margin-top:14px"><label>Email</label><input id="weeklyReportEmail" class="input" data-vk="latin" value="" autocomplete="off"></div><button id="sendWeekReportEmail" class="btn primary block">ОТПРАВИТЬ ЕЖЕНЕДЕЛЬНЫЙ ОТЧЁТ НА EMAIL</button></div>
   <div class="reportCard"><h3>Ежемесячный отчёт</h3><p>Журнал выдач СИЗ складом в ячейки сотрудников за выбранный месяц.</p><div class="field" style="margin-top:12px"><label>Месяц</label><select id="reportMonth" class="select">${monthOptions}</select></div><div style="display:grid;gap:8px"><button id="previewMonthReport" class="btn outline block">ПРЕДПРОСМОТР</button><button id="makeReport" class="btn primary block">СФОРМИРОВАТЬ В АРХИВ</button></div><div class="field" style="margin-top:14px"><label>Email</label><input id="monthlyReportEmail" class="input" data-vk="latin" value="" autocomplete="off"></div><button id="sendMonthReportEmail" class="btn primary block">ОТПРАВИТЬ ЕЖЕМЕСЯЧНЫЙ ОТЧЁТ НА EMAIL</button></div>
   <div class="reportCard"><h3>Резервные копии</h3><p>Backup сохраняется физическим файлом в выбранную папку <b>Postomat_SIZ/Backup</b>.</p><div class="reportStatus ${lastBackup&&lastBackup.storage==='DEVICE_FILE'?'ok':'internal'}">Последний: ${last}${lastBackupPath}</div><button id="backupNow" class="btn green block" style="margin-top:12px">СОЗДАТЬ BACKUP В ПАМЯТИ ПЛАНШЕТА</button><div class="fieldRow" style="margin-top:12px"><div class="field"><label>Backup каждые, дней</label><input id="backupDays" class="input" data-vk="number" value="${db.settings.backupEveryDays||7}"></div><div class="field"><label>Хранить недель</label><input id="backupWeeks" class="input" data-vk="number" value="${db.settings.backupRetentionWeeks||12}"></div></div><div class="field"><label>Хранить месячные отчёты, месяцев</label><input id="reportMonthsKeep" class="input" data-vk="number" value="${db.settings.reportRetentionMonths||12}"></div><button id="saveArchiveSettings" class="btn outline block">СОХРАНИТЬ НАСТРОЙКИ</button></div>
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

// ===== v3.4 compact assignments =====
function compactAssignmentCard(a){
  const p=ppe(a.ppeId),st=assignmentStatus(a);
  return `<details class="asCompact">
    <summary>
      <div class="asSummaryMain"><b>${esc(p?.name||a.ppeId)}</b><span>Остаток ${a.stock} • Min ${a.min} • Max ${a.target}</span></div>
      <span class="pill ${st[1]}">${st[0]}</span>
    </summary>
    <div class="asCompactBody">
      <div class="assignmentMetrics">
        <div class="assignmentMetric"><span>Остаток</span><b>${a.stock}</b></div>
        <div class="assignmentMetric"><span>Min</span><b>${a.min}</b></div>
        <div class="assignmentMetric"><span>Max</span><b>${a.target}</b></div>
        <div class="assignmentMetric"><span>Разово к получению</span><b>${a.issueQty}</b></div>
        <div class="assignmentMetric"><span>Плановая эксплуатация</span><b>${a.periodDays?a.periodDays+' дн.':'Без срока'}</b></div>
      </div>
      <div class="assignmentActions">
        <button class="btn small outline" data-edit-as="${a.id}">Изменить</button>
        <button class="btn small outline" data-stock-as="${a.id}">Корректировка</button>
        <button class="btn small red" data-remove-as="${a.id}">Убрать связь</button>
      </div>
    </div>
  </details>`;
}

function compactEmployeeAssignments(e,forceOpen){
  const c=cell(e.cellId),aa=assignForEmployee(e.id);
  const label=aa.length===1?'1 СИЗ':aa.length+' СИЗ';
  return `<details class="empAsGroup" ${forceOpen?'open':''}>
    <summary>
      <div class="empAsSummary"><b>${esc(e.name)}</b><span>${c?esc(c.name):'Ячейка не присвоена'} • ${label}</span></div>
      <span class="empAsArrow">▾</span>
    </summary>
    <div class="empAsBody">
      <div class="empAsToolbar">
        <div class="meta">${c?'Назначения для '+esc(c.name):'Сначала назначьте сотруднику ячейку'}</div>
        <button class="btn small primary" data-add-as-emp="${e.id}">Добавить СИЗ</button>
      </div>
      ${aa.length?aa.map(compactAssignmentCard).join(''):'<div class="empty" style="padding:18px">СИЗ сотруднику ещё не назначены</div>'}
    </div>
  </details>`;
}

adminAssignments=function(){
  const employees=db.employees.filter(e=>e.active).sort((a,b)=>String(a.name).localeCompare(String(b.name),'ru'));
  const validSelected=window.__adminEmployeeFilter&&employees.some(e=>e.id===window.__adminEmployeeFilter);
  const selected=validSelected?window.__adminEmployeeFilter:'';
  if(!validSelected)window.__adminEmployeeFilter=null;
  const options=['<option value="">Все сотрудники</option>'].concat(employees.map(e=>`<option value="${e.id}" ${e.id===selected?'selected':''}>${esc(e.name)}</option>`)).join('');
  const shown=selected?employees.filter(e=>e.id===selected):employees;
  const totalAs=db.assignments.filter(a=>a.active).length;
  const content=shown.map(e=>compactEmployeeAssignments(e,Boolean(selected))).join('');
  return adminHeader('Назначения СИЗ','Компактный просмотр по сотрудникам. Один и тот же вид СИЗ можно назначать разным сотрудникам.',`<button id="addAs" class="btn primary">Добавить назначение</button>`)+
  `<style>
    .asFilterCard{background:#fff;border:1px solid var(--line);border-radius:15px;padding:14px;margin-bottom:14px}
    .asFilterMeta{display:flex;justify-content:space-between;gap:10px;align-items:center;margin-top:9px;font-size:12px;color:var(--muted)}
    .empAsGroup{background:#fff;border:1px solid var(--line);border-radius:15px;margin-bottom:10px;overflow:hidden}
    .empAsGroup>summary,.asCompact>summary{list-style:none;cursor:pointer;-webkit-tap-highlight-color:transparent}
    .empAsGroup>summary::-webkit-details-marker,.asCompact>summary::-webkit-details-marker{display:none}
    .empAsGroup>summary{display:flex;align-items:center;gap:10px;padding:15px 16px}
    .empAsSummary{flex:1;min-width:0}.empAsSummary b{display:block;font-size:16px}.empAsSummary span{display:block;color:var(--muted);font-size:12px;margin-top:4px}
    .empAsArrow{font-size:18px;color:var(--muted);transition:.15s}.empAsGroup[open] .empAsArrow{transform:rotate(180deg)}
    .empAsBody{border-top:1px solid var(--line);padding:11px;background:#f8fafc}
    .empAsToolbar{display:flex;gap:10px;align-items:center;justify-content:space-between;padding:2px 2px 10px}
    .asCompact{background:#fff;border:1px solid var(--line);border-radius:12px;margin-bottom:8px;overflow:hidden}
    .asCompact>summary{display:flex;align-items:center;gap:10px;padding:12px}
    .asSummaryMain{flex:1;min-width:0}.asSummaryMain b{display:block;font-size:14px}.asSummaryMain span{display:block;color:var(--muted);font-size:11px;margin-top:3px}
    .asCompactBody{border-top:1px solid var(--line);padding:10px}
    .asCompact .assignmentMetrics{margin-top:0}
    @media(max-width:520px){.empAsToolbar{align-items:stretch;flex-direction:column}.empAsToolbar .btn{width:100%}.asFilterMeta{align-items:flex-start;flex-direction:column}}
  </style>
  <div class="asFilterCard">
    <div class="field" style="margin:0"><label>Сотрудник</label><select id="assignmentEmployeeFilter" class="select">${options}</select></div>
    <div class="asFilterMeta"><span>${selected?'Показаны назначения выбранного сотрудника':'Сотрудники свернуты. Нажмите на сотрудника, чтобы раскрыть его СИЗ.'}</span><b>Всего активных назначений: ${totalAs}</b></div>
  </div>
  ${content||'<div class="empty">Нет активных сотрудников</div>'}`;
};

const __wireAdminV34=wireAdmin;
wireAdmin=function(tab){
  __wireAdminV34(tab);
  if(tab==='assignments'){
    const f=byId('assignmentEmployeeFilter');
    if(f)f.onchange=()=>{window.__adminEmployeeFilter=f.value||null;showAdmin('assignments')};
    document.querySelectorAll('[data-add-as-emp]').forEach(b=>b.onclick=()=>assignmentModal(b.dataset.addAsEmp));
  }
};

// ===== v3.5 native mobile keyboard =====
function enableNativeMobileKeyboard(root){
  const host=root||document;
  host.querySelectorAll('input[data-vk]').forEach(inp=>{
    if(inp.disabled)return;
    inp.readOnly=false;
    inp.removeAttribute('readonly');
    inp.onclick=null;

    const kind=String(inp.dataset.vk||'text');
    const id=String(inp.id||'');
    const isEmail=(id==='weeklyReportEmail'||id==='monthlyReportEmail'||id==='setEmail');

    if(kind==='number'){
      inp.setAttribute('inputmode','numeric');
      inp.setAttribute('pattern','[0-9]*');
      inp.setAttribute('autocorrect','off');
      inp.setAttribute('autocomplete','off');
    }else if(isEmail){
      inp.setAttribute('inputmode','email');
      inp.setAttribute('autocapitalize','none');
      inp.setAttribute('autocorrect','off');
      inp.setAttribute('autocomplete','off');
    }else{
      inp.setAttribute('inputmode','text');
      inp.removeAttribute('pattern');
      if(kind==='latin'){
        inp.setAttribute('autocapitalize','none');
        inp.setAttribute('autocorrect','off');
      }
    }

    const wrap=inp.parentElement;
    if(wrap&&wrap.classList&&wrap.classList.contains('vkField')){
      const parent=wrap.parentNode;
      if(parent){
        parent.insertBefore(inp,wrap);
        wrap.remove();
      }
    }
  });

  const kb=byId('keyboardRoot');
  if(kb){
    kb.classList.remove('show');
    kb.innerHTML='';
  }
}

attachVirtualInputs=enableNativeMobileKeyboard;
try{enableNativeMobileKeyboard(document)}catch(e){console.error('native keyboard init',e)}

// ===== v3.7 warehouse contour: replenishment / revision / history =====
function ensureWarehouseData(){
  if(!Array.isArray(db.revisionLog))db.revisionLog=[];
  if(!db.settings.warehouseReportDays)db.settings.warehouseReportDays=[3,5];
  if(typeof db.settings.warehouseReportEmail!=='string')db.settings.warehouseReportEmail='';
  if(!session.whHistoryType)session.whHistoryType='replenish';
  if(!session.revisionActual)session.revisionActual={};
}

const __workspaceV37=workspace;
function stripExitButton(html){
  return String(html||'').replace(/<button[^>]*data-nav=["']logout["'][^>]*>[\\s\\S]*?<\\/button>/gi,'');
}
workspace=function(role,active,body){
  if(role!=='WAREHOUSE')return stripExitButton(__workspaceV37(role,active,body));
  const nav=[['replenish','Восполнение'],['revision','Ревизия'],['history','История']];
  return `<div class="workspace"><aside class="sidebar">
    <div class="profile"><b>${esc(session.user?.name||'')}</b><span>Склад</span></div>
    ${nav.map(n=>`<button class="navbtn ${n[0]===active?'active':''}" data-nav="${n[0]}">${n[1]}</button>`).join('')}
  </aside><main class="content">${body}</main></div>`;
};

function whQueueGroups(){
  syncTasks();
  const tasks=openTasks();
  const map=new Map();
  tasks.forEach(t=>{
    const cid=Number(t.cellId);
    if(!map.has(cid))map.set(cid,[]);
    map.get(cid).push(t);
  });
  return [...map.entries()].sort((a,b)=>a[0]-b[0]).map(([cellId,tasks])=>({cellId,tasks}));
}

function whQueueBody(){
  const groups=whQueueGroups();
  const totalPositions=groups.reduce((s,g)=>s+g.tasks.length,0);
  const totalQty=groups.reduce((s,g)=>s+g.tasks.reduce((x,t)=>x+Math.max(0,t.a.target-t.a.stock),0),0);
  const rows=groups.map(g=>{
    const c=cell(g.cellId),o=ownerOfCell(g.cellId);
    const critical=g.tasks.some(t=>t.a.stock===0);
    const qty=g.tasks.reduce((s,t)=>s+Math.max(0,t.a.target-t.a.stock),0);
    return `<button class="whQueueRow" data-wh-cell="${g.cellId}">
      <div class="whQueueMain">
        <b>${esc(c?.name||('Ячейка №'+g.cellId))}</b>
        <span>${esc(o?.name||'Сотрудник не назначен')}</span>
      </div>
      <div class="whQueueMetric"><b>${g.tasks.length}</b><span>позиций</span></div>
      <div class="whQueueMetric"><b>+${qty}</b><span>единиц</span></div>
      <span class="badge ${critical?'red':'orange'}">${critical?'КРИТИЧНО':'ПОПОЛНИТЬ'}</span>
      <span class="whQueueArrow">›</span>
    </button>`;
  }).join('');
  return `<style>
    .whStats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-bottom:14px}
    .whStat{background:#fff;border:1px solid var(--line);border-radius:14px;padding:14px}.whStat b{font-size:24px;display:block}.whStat span{font-size:12px;color:var(--muted)}
    .whQueueList{display:grid;gap:8px}
    .whQueueRow{width:100%;display:grid;grid-template-columns:minmax(180px,1fr) 90px 90px auto 24px;gap:12px;align-items:center;text-align:left;background:#fff;border:1px solid var(--line);border-radius:14px;padding:13px 14px;color:inherit;font:inherit;cursor:pointer}
    .whQueueRow:active{transform:scale(.995)}
    .whQueueMain{min-width:0}.whQueueMain b{display:block;font-size:16px}.whQueueMain span{display:block;color:var(--muted);font-size:12px;margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .whQueueMetric{text-align:center}.whQueueMetric b{display:block;font-size:16px}.whQueueMetric span{display:block;color:var(--muted);font-size:10px;margin-top:2px}
    .whQueueArrow{font-size:26px;color:var(--muted);text-align:right}
    .whInfo{margin-bottom:14px}
    @media(max-width:700px){
      .whStats{grid-template-columns:repeat(3,1fr)}
      .whQueueRow{grid-template-columns:1fr auto auto;gap:8px}
      .whQueueMetric{min-width:58px}
      .whQueueRow .badge{grid-column:1/3;justify-self:start}
      .whQueueArrow{grid-column:3;grid-row:2}
    }
    @media(max-width:500px){.whStats{grid-template-columns:1fr}.whStat{padding:10px 12px}.whStat b{font-size:20px}}
  </style>
  <div class="contentHead"><div><div class="h1">Восполнение</div><p>Сначала выберите ячейку. После выбора откроется список СИЗ и количество для пополнения.</p></div><div class="right"><button class="btn outline" id="whRefresh">Обновить</button></div></div>
  <div class="note whInfo"><b>Отчёт склада:</b> формируется из этой очереди. Расписание — <b>среда и пятница</b>.</div>
  <div class="whStats">
    <div class="whStat"><b>${groups.length}</b><span>Ячеек к восполнению</span></div>
    <div class="whStat"><b>${totalPositions}</b><span>Позиций СИЗ</span></div>
    <div class="whStat"><b>${totalQty}</b><span>Единиц добавить</span></div>
  </div>
  <div class="whQueueList">${rows||'<div class="empty">Сейчас восполнение не требуется</div>'}</div>`;
}

function startWhCell(cellId){
  const all=whQueueGroups().map(g=>Number(g.cellId));
  const first=Number(cellId);
  session.routeCells=[first,...all.filter(x=>x!==first)];
  session.routeIndex=0;
  session.routeActual={};
  session.flow=null;
  showWarehouse('replenish');
}

function whReplenishWorkBody(){
  const cid=currentRouteCell();
  if(cid==null)return whQueueBody();
  const ts=routeTasks();
  if(!ts.length){
    session.routeCells=[];session.routeIndex=-1;session.routeActual={};
    return whQueueBody();
  }
  const c=cell(cid),o=ownerOfCell(cid);
  const items=ts.map(t=>{
    const rec=Math.max(0,t.a.target-t.a.stock);
    const val=session.routeActual[t.a.id]??rec;
    return `<div class="routeItem">
      <div><b>${esc(ppe(t.ppeId)?.name||t.ppeId)}</b><div class="sub">Сейчас <b>${t.a.stock}</b> • Min ${t.a.min} • Max ${t.a.target} • добавить ${rec}</div></div>
      <input class="qty routeQty" data-aid="${t.a.id}" type="number" inputmode="numeric" min="0" max="999" value="${val}">
    </div>`;
  }).join('');
  return `<div class="contentHead"><div><div class="h1">Восполнение</div><p>Ячейка ${session.routeIndex+1} из ${session.routeCells.length}</p></div><div class="right"><button id="backWhQueue" class="btn outline">К списку ячеек</button></div></div>
  <div class="routeGrid">
    <div class="card"><div class="h2">${esc(c?.name||('Ячейка №'+cid))}</div><div class="sub">${esc(o?.name||'Сотрудник не назначен')}</div><div class="sectionLabel">Необходимо пополнить</div><div>${items}</div></div>
    <div class="card sticky"><div class="sub">Состояние ячейки</div><div id="routeCellState" class="stateBig">ЗАКРЫТА</div>
      <div id="routeHint" class="note">Проверьте список СИЗ и количество. Затем откройте ячейку.</div>
      <button id="openRoute" class="btn primary block" style="margin-top:12px">ОТКРЫТЬ ЯЧЕЙКУ</button>
      <button id="confirmRoute" class="btn green block" style="margin-top:9px" disabled>ВОСПОЛНЕНО</button>
    </div>
  </div>`;
}

function wireWhReplenish(){
  if(byId('whRefresh'))byId('whRefresh').onclick=()=>showWarehouse('replenish');
  document.querySelectorAll('[data-wh-cell]').forEach(b=>b.onclick=()=>startWhCell(b.dataset.whCell));
  if(byId('backWhQueue'))byId('backWhQueue').onclick=()=>{session.routeCells=[];session.routeIndex=-1;session.routeActual={};session.flow=null;showWarehouse('replenish')};
  document.querySelectorAll('.routeQty').forEach(x=>x.oninput=()=>session.routeActual[x.dataset.aid]=Math.max(0,Number(x.value||0)));
  if(byId('openRoute'))byId('openRoute').onclick=()=>openRouteCell();
  if(byId('confirmRoute'))byId('confirmRoute').onclick=confirmWhReplenish;
  enableNativeMobileKeyboard(document);
}

function confirmWhReplenish(){
  if(!session.flow?.closed){toast('Сначала закройте ячейку','error');return}
  document.querySelectorAll('.routeQty').forEach(x=>session.routeActual[x.dataset.aid]=Math.max(0,Number(x.value||0)));
  const cid=currentRouteCell(),list=routeTasks(),recipient=ownerOfCell(cid)?.name||'—';
  const lines=list.map(t=>`${esc(ppe(t.ppeId)?.name||t.ppeId)} — <b>${session.routeActual[t.a.id]??0}</b>`).join('<br>');
  confirmModal('Подтвердить восполнение',lines,'ВОСПОЛНЕНО',()=>{
    const tx=uid('REP'),ts=nowIso();
    for(const t of list){
      const q=Math.max(0,Number(session.routeActual[t.a.id]??0));
      if(!q)continue;
      const a=t.a,before=a.stock;
      a.stock+=q;
      db.replenishLog.unshift({id:uid('R'),tx,ts,userId:session.user.id,userName:session.user.name,issuedBy:session.user.name,recipientName:recipient,cellId:a.cellId,ppeId:a.ppeId,ppeName:ppe(a.ppeId)?.name||a.ppeId,before,qty:q,after:a.stock});
    }
    saveDb();syncTasks();session.flow=null;session.routeActual={};
    const remain=whQueueGroups().map(g=>Number(g.cellId));
    if(remain.length){
      const next=remain.find(x=>x!==Number(cid)) ?? remain[0];
      session.routeCells=[next,...remain.filter(x=>x!==next)];
      session.routeIndex=0;
      toast('Восполнение сохранено. Следующая ячейка подготовлена.','ok');
      showWarehouse('replenish');
    }else{
      session.routeCells=[];session.routeIndex=-1;
      toast('Восполнение сохранено. Очередь закрыта.','ok');
      showWarehouse('replenish');
    }
  });
}

function whRevisionBody(){
  const cells=db.cells.filter(c=>c.active).sort((a,b)=>Number(a.id)-Number(b.id));
  if(session.revisionCellId!=null)return whRevisionWorkBody();
  const options=cells.map(c=>{const o=ownerOfCell(c.id);return `<option value="${c.id}">${esc(c.name)} — ${esc(o?.name||'без сотрудника')}</option>`}).join('');
  return `<div class="contentHead"><div><div class="h1">Ревизия</div><p>Ревизия выполняется складом в любое необходимое время.</p></div></div>
  <div class="card" style="max-width:700px">
    <div class="field"><label>Ячейка</label><select id="revisionCellSelect" class="select">${options}</select></div>
    <button id="startRevision" class="btn primary block" ${cells.length?'':'disabled'}>ПЕРЕЙТИ К РЕВИЗИИ</button>
  </div>`;
}

function whRevisionWorkBody(){
  const cid=Number(session.revisionCellId),c=cell(cid),o=ownerOfCell(cid),aa=assignForCell(cid);
  const rows=aa.map(a=>{
    const actual=session.revisionActual[a.id]??a.stock;
    return `<div class="routeItem"><div><b>${esc(ppe(a.ppeId)?.name||a.ppeId)}</b><div class="sub">По учёту: <b>${a.stock}</b></div></div><input class="qty revisionQty" data-aid="${a.id}" type="number" inputmode="numeric" min="0" max="9999" value="${actual}"></div>`;
  }).join('');
  return `<div class="contentHead"><div><div class="h1">Ревизия</div><p>${esc(c?.name||('Ячейка №'+cid))} • ${esc(o?.name||'Сотрудник не назначен')}</p></div><div class="right"><button id="cancelRevision" class="btn outline">К выбору ячейки</button></div></div>
  <div class="routeGrid">
    <div class="card"><div class="h2">Сверка остатков</div><div class="sub">Введите фактическое количество каждого СИЗ.</div><div style="margin-top:12px">${rows||'<div class="empty">В ячейке нет назначенных СИЗ</div>'}</div><div class="field" style="margin-top:14px"><label>Причина / комментарий при расхождении</label><input id="revisionComment" class="input" data-vk="text" value="" autocomplete="off"></div></div>
    <div class="card sticky"><div class="sub">Состояние ячейки</div><div id="revisionCellState" class="stateBig">ЗАКРЫТА</div><div id="revisionHint" class="note">Откройте ячейку и пересчитайте фактические остатки.</div><button id="openRevision" class="btn primary block" style="margin-top:12px">ОТКРЫТЬ ЯЧЕЙКУ</button><button id="finishRevision" class="btn green block" style="margin-top:9px" disabled>ЗАВЕРШИТЬ РЕВИЗИЮ</button></div>
  </div>`;
}

function wireWhRevision(){
  if(byId('startRevision'))byId('startRevision').onclick=()=>{session.revisionCellId=Number(byId('revisionCellSelect').value);session.revisionActual={};session.flow=null;showWarehouse('revision')};
  if(byId('cancelRevision'))byId('cancelRevision').onclick=()=>{session.revisionCellId=null;session.revisionActual={};session.flow=null;showWarehouse('revision')};
  document.querySelectorAll('.revisionQty').forEach(x=>x.oninput=()=>session.revisionActual[x.dataset.aid]=Math.max(0,Number(x.value||0)));
  if(byId('openRevision'))byId('openRevision').onclick=openRevisionCell;
  if(byId('finishRevision'))byId('finishRevision').onclick=finishRevision;
  enableNativeMobileKeyboard(document);
}

async function openRevisionCell(){
  const cid=Number(session.revisionCellId);
  confirmModal('Открыть ячейку для ревизии?',`Будет открыта <b>${esc(cell(cid)?.name||('Ячейка №'+cid))}</b>.`,'ОТКРЫТЬ',async()=>{
    try{
      if(byId('openRevision'))byId('openRevision').disabled=true;
      session.flow={kind:'REVISION',cellId:cid,sawOpen:false,closed:false};
      byId('revisionHint').textContent='Команда открытия отправлена. Ожидаем открытия.';
      await apiOpen(cid);
      pollRevisionFlow();
    }catch(e){session.flow=null;toast('Ошибка открытия: '+e.message,'error');if(byId('openRevision'))byId('openRevision').disabled=false}
  });
}

function pollRevisionFlow(){
  let tries=0;
  const timer=setInterval(async()=>{
    if(!session.flow||session.flow.kind!=='REVISION'){clearInterval(timer);return}
    tries++;
    try{
      const c=await apiCell(session.flow.cellId),st=Number(c.state),el=byId('revisionCellState');
      if(el){el.textContent=st===0?'ОТКРЫТА':'ЗАКРЫТА';el.className='stateBig '+(st===0?'open':'closed')}
      if(st===0){session.flow.sawOpen=true;if(byId('revisionHint'))byId('revisionHint').textContent='Ячейка открыта. Пересчитайте СИЗ и закройте дверцу.'}
      if(st===1&&session.flow.sawOpen){session.flow.closed=true;clearInterval(timer);if(byId('revisionHint'))byId('revisionHint').textContent='Ячейка закрыта. Введите фактические количества и завершите ревизию.';if(byId('finishRevision'))byId('finishRevision').disabled=false}
      if(tries>70){clearInterval(timer);toast('Не удалось зафиксировать цикл открытия/закрытия','error')}
    }catch(e){if(tries>5){clearInterval(timer);toast('Ошибка контроля двери: '+e.message,'error')}}
  },700);
}

function finishRevision(){
  if(!session.flow?.closed){toast('Сначала закройте ячейку','error');return}
  document.querySelectorAll('.revisionQty').forEach(x=>session.revisionActual[x.dataset.aid]=Math.max(0,Number(x.value||0)));
  const cid=Number(session.revisionCellId),aa=assignForCell(cid);
  const diffs=aa.filter(a=>Number(session.revisionActual[a.id]??a.stock)!==Number(a.stock));
  const comment=String(byId('revisionComment')?.value||'').trim();
  if(diffs.length&&!comment){toast('При расхождении укажите причину / комментарий','error');return}
  const lines=aa.map(a=>{const fact=Number(session.revisionActual[a.id]??a.stock);return `${esc(ppe(a.ppeId)?.name||a.ppeId)} — учёт <b>${a.stock}</b>, факт <b>${fact}</b>`}).join('<br>');
  confirmModal('Завершить ревизию',lines,'СОХРАНИТЬ',()=>{
    const ts=nowIso(),recipient=ownerOfCell(cid)?.name||'—',tx=uid('REV');
    aa.forEach(a=>{
      const expected=Number(a.stock),actual=Math.max(0,Number(session.revisionActual[a.id]??expected)),delta=actual-expected;
      const result=delta===0?'Совпадает':delta<0?'Недостача / перерасход':'Положительное отклонение';
      db.revisionLog.unshift({id:uid('RV'),tx,ts,userId:session.user.id,userName:session.user.name,recipientName:recipient,cellId:cid,ppeId:a.ppeId,ppeName:ppe(a.ppeId)?.name||a.ppeId,expected,actual,delta,result,comment:delta===0?'':comment});
      a.stock=actual;
    });
    saveDb();syncTasks();session.flow=null;session.revisionCellId=null;session.revisionActual={};
    toast('Ревизия сохранена','ok');showWarehouse('revision');
  });
}

function whHistoryBody(){
  const type=session.whHistoryType||'replenish';
  let content='';
  if(type==='replenish'){
    content=db.replenishLog.map(x=>`<div class="archiveItem"><div class="row"><b>${fmtDate(x.ts)}</b><span class="badge">ВОСПОЛНЕНО</span></div><div class="meta">${esc(cell(x.cellId)?.name||('Ячейка №'+x.cellId))} • Получатель: ${esc(x.recipientName||ownerOfCell(x.cellId)?.name||'—')}<br>${esc(x.ppeName)} • было ${x.before} • добавлено <b>${x.qty}</b> • стало ${x.after}<br>Восполнил: ${esc(x.issuedBy||x.userName||'Склад')}</div></div>`).join('');
  }else{
    content=(db.revisionLog||[]).map(x=>`<div class="archiveItem"><div class="row"><b>${fmtDate(x.ts)}</b><span class="badge ${x.delta===0?'':'orange'}">${esc(x.result)}</span></div><div class="meta">${esc(cell(x.cellId)?.name||('Ячейка №'+x.cellId))} • ${esc(x.recipientName||'—')}<br>${esc(x.ppeName)} • по учёту ${x.expected} • факт <b>${x.actual}</b> • отклонение ${x.delta>0?'+':''}${x.delta}<br>Проверил: ${esc(x.userName||'Склад')}${x.comment?'<br>Комментарий: '+esc(x.comment):''}</div></div>`).join('');
  }
  return `<div class="contentHead"><div><div class="h1">История</div><p>Фактически выполненные операции склада.</p></div></div>
  <div style="display:flex;gap:8px;margin-bottom:14px;flex-wrap:wrap"><button class="btn ${type==='replenish'?'primary':'outline'}" id="histReplenish">Восполнения</button><button class="btn ${type==='revision'?'primary':'outline'}" id="histRevision">Ревизии</button></div>
  ${content||'<div class="empty">Записей пока нет</div>'}`;
}

function showWarehouse(tab='replenish',push=true){
  ensureWarehouseData();
  if(tab==='tasks'||tab==='route')tab='replenish';
  if(tab==='done')tab='history';
  setUi({screen:'warehouse',role:'WAREHOUSE',tab},push);
  let body='';
  if(tab==='replenish')body=(session.routeCells&&session.routeCells.length&&session.routeIndex>=0)?whReplenishWorkBody():whQueueBody();
  if(tab==='revision')body=whRevisionBody();
  if(tab==='history')body=whHistoryBody();
  render(workspace('WAREHOUSE',tab,body));wireNav('WAREHOUSE');
  if(tab==='replenish')wireWhReplenish();
  if(tab==='revision')wireWhRevision();
  if(tab==='history'){
    if(byId('histReplenish'))byId('histReplenish').onclick=()=>{session.whHistoryType='replenish';showWarehouse('history')};
    if(byId('histRevision'))byId('histRevision').onclick=()=>{session.whHistoryType='revision';showWarehouse('history')};
  }
}

// ===== v3.9 warehouse report email setting =====
const __adminSettingsV39=adminSettings;
adminSettings=function(){
  ensureWarehouseData();
  const base=__adminSettingsV39();
  return base+`
    <div class="sectionLabel">Отчёт склада по восполнению</div>
    <div class="card" style="max-width:760px">
      <div class="h2" style="font-size:18px">Постоянный Email получателя</div>
      <div class="sub" style="margin-bottom:12px">Этот адрес будет использоваться для автоматической отправки отчёта по восполнению по средам и пятницам.</div>
      <div class="field">
        <label>Email склада / получателя отчёта</label>
        <input id="warehouseReportEmail" class="input" data-vk="latin" inputmode="email" autocomplete="off" autocapitalize="none" value="${esc(db.settings.warehouseReportEmail||'')}">
      </div>
      <button id="saveWarehouseReportEmail" class="btn primary">СОХРАНИТЬ EMAIL</button>
    </div>`;
};

const __wireAdminV39=wireAdmin;
wireAdmin=function(tab){
  __wireAdminV39(tab);
  if(tab==='settings'){
    const b=byId('saveWarehouseReportEmail');
    if(b)b.onclick=()=>{
      const email=String(byId('warehouseReportEmail')?.value||'').trim();
      if(email&&!/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(email)){
        toast('Введите корректный Email','error');return;
      }
      db.settings.warehouseReportEmail=email;
      saveDb();
      toast(email?'Email для отчёта склада сохранён':'Email для отчёта склада очищен','ok');
      showAdmin('settings');
    };
    enableNativeMobileKeyboard(document);
  }
};

// ===== v3.18 main role cards =====
showBetaHome=function(push=true){
  if(push)setUi({screen:'home'},true);else uiState={screen:'home',role:null,tab:null};
  session.role=null;session.user=null;session.sid='DEMO-SID';session.connected=true;session.mode='DEMO';

  const appSvg=`<svg viewBox="0 0 240 170" xmlns="http://www.w3.org/2000/svg" fill="none">
    <rect x="59" y="34" width="122" height="118" rx="19" fill="#fff"/>
    <rect x="76" y="52" width="30" height="24" rx="6" fill="#dce8f7"/>
    <rect x="112" y="52" width="30" height="24" rx="6" fill="#dce8f7"/>
    <rect x="148" y="52" width="16" height="24" rx="6" fill="#dce8f7"/>
    <rect x="76" y="83" width="30" height="24" rx="6" fill="#dce8f7"/>
    <rect x="112" y="83" width="30" height="24" rx="6" fill="#dce8f7"/>
    <rect x="148" y="83" width="16" height="24" rx="6" fill="#dce8f7"/>
    <rect x="76" y="115" width="88" height="21" rx="7" fill="#d7e6f8"/>
    <rect x="101" y="6" width="38" height="38" rx="12" fill="#ffd45c"/>
    <path d="M120 13l11 5v8c0 7-5 12-11 15-6-3-11-8-11-15v-8l11-5z" fill="#0f4f99"/>
    <path d="M115 25l4 4 7-8" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`;

  const opSvg=`<svg viewBox="0 0 120 100" xmlns="http://www.w3.org/2000/svg" fill="none">
    <circle cx="44" cy="26" r="14" fill="#ffd45c"/>
    <path d="M31 27c2-11 8-17 13-17 6 0 12 6 14 17" stroke="#8f5b00" stroke-width="6" stroke-linecap="round"/>
    <rect x="26" y="45" width="38" height="34" rx="12" fill="#fff" stroke="#8f5b00" stroke-width="4"/>
    <rect x="73" y="38" width="28" height="43" rx="8" fill="#9adf9a"/>
    <rect x="79" y="45" width="16" height="12" rx="4" fill="#fff"/>
    <path d="M68 62h10" stroke="#8f5b00" stroke-width="5" stroke-linecap="round"/>
  </svg>`;

  const whSvg=`<svg viewBox="0 0 120 100" xmlns="http://www.w3.org/2000/svg" fill="none">
    <rect x="14" y="16" width="92" height="11" rx="5.5" fill="#136f63" opacity=".2"/>
    <rect x="20" y="27" width="14" height="58" rx="5" fill="#136f63" opacity=".2"/>
    <rect x="86" y="27" width="14" height="58" rx="5" fill="#136f63" opacity=".2"/>
    <rect x="26" y="34" width="25" height="20" rx="6" fill="#ffc857"/>
    <rect x="56" y="34" width="25" height="20" rx="6" fill="#7cc8ff"/>
    <rect x="41" y="61" width="25" height="20" rx="6" fill="#9adf9a"/>
    <path d="M79 67h22M90 56v22" stroke="#136f63" stroke-width="8" stroke-linecap="round"/>
  </svg>`;

  const adminSvg=`<svg viewBox="0 0 120 100" xmlns="http://www.w3.org/2000/svg" fill="none">
    <rect x="16" y="15" width="78" height="51" rx="12" fill="#7cc8ff" opacity=".32"/>
    <rect x="24" y="23" width="62" height="35" rx="8" fill="#fff" stroke="#0f4f99" stroke-width="4"/>
    <path d="M42 76h28M56 58v18" stroke="#0f4f99" stroke-width="7" stroke-linecap="round"/>
    <circle cx="91" cy="72" r="14" fill="#ffd45c"/>
    <path d="M91 62v20M81 72h20M84 65l14 14M98 65L84 79" stroke="#0f4f99" stroke-width="4" stroke-linecap="round"/>
  </svg>`;

  render(`<div class="betaHome homeScreenV318">
    <style>
      .homeScreenV318{display:flex;flex-direction:column;gap:12px}
      .homeHeroV318{background:linear-gradient(135deg,#0f4f99 0%,#1976d2 100%);color:#fff;border-radius:22px;padding:15px 18px;box-shadow:0 14px 32px rgba(15,79,153,.2)}
      .homeHeroGridV318{display:grid;grid-template-columns:minmax(0,1fr) 180px;gap:14px;align-items:center}
      .homeHeroV318 h1{margin:5px 0 5px;font-size:29px;line-height:1.05}
      .homeHeroV318 p{margin:0;color:rgba(255,255,255,.9);font-size:13px;line-height:1.35}
      .homeHeroArtV318{width:180px;justify-self:end}
      .homeHeroNoteV318{margin-top:7px;font-size:11px;color:rgba(255,255,255,.83)}
      .homeCardsV318{display:grid;grid-template-columns:1fr;gap:10px}
      .homeRoleCardV318{appearance:none;width:100%;border:1px solid #dbe5f1;background:#fff;border-radius:20px;padding:11px 14px;cursor:pointer;text-align:left;display:grid;grid-template-columns:118px minmax(0,1fr) 126px;gap:16px;align-items:center;min-height:112px;box-shadow:0 8px 20px rgba(15,79,153,.07);color:inherit;font:inherit}
      .homeRoleCardV318:active{transform:scale(.997)}
      .homeRoleIconV318{height:88px;border-radius:16px;background:linear-gradient(180deg,#f6f9fe,#edf4fc);display:flex;align-items:center;justify-content:center}
      .homeRoleIconV318 svg{width:100px;height:82px}
      .homeRoleTitleV318{font-size:22px;font-weight:800;color:#172033;line-height:1.15}
      .homeRoleOpenV318{height:46px;border-radius:14px;color:#fff;font-weight:800;display:flex;align-items:center;justify-content:center;font-size:14px;letter-spacing:.3px}
      .homeOperatorV318 .homeRoleOpenV318{background:#8f5b00}
      .homeWarehouseV318 .homeRoleOpenV318{background:#136f63}
      .homeAdminV318 .homeRoleOpenV318{background:#0f4f99}
      @media(max-width:720px){
        .homeHeroGridV318{grid-template-columns:1fr 135px}.homeHeroArtV318{width:135px}
        .homeRoleCardV318{grid-template-columns:88px minmax(0,1fr);gap:12px}.homeRoleIconV318{height:76px}.homeRoleIconV318 svg{width:82px;height:70px}.homeRoleTitleV318{font-size:18px}.homeRoleOpenV318{grid-column:1/-1;height:40px}
      }
    </style>
    <div class="homeHeroV318">
      <div class="betaTag">● ДЕМО РЕЖИМ • ПОСТОМАТ</div>
      <div class="homeHeroGridV318">
        <div>
          <h1>ПОСТАМАТ СИЗ</h1>
          <p>Выберите необходимый раздел.</p>
          <div class="homeHeroNoteV318">Все изменения выполняются локально на планшете. Реальный постомат не задействуется.</div>
        </div>
        <div class="homeHeroArtV318">${appSvg}</div>
      </div>
    </div>
    <div class="homeCardsV318">
      <button id="betaOperator" class="homeRoleCardV318 homeOperatorV318">
        <div class="homeRoleIconV318">${opSvg}</div>
        <div class="homeRoleTitleV318">ПОЛУЧЕНИЕ СИЗ</div>
        <div class="homeRoleOpenV318">ОТКРЫТЬ</div>
      </button>
      <button id="betaWarehouse" class="homeRoleCardV318 homeWarehouseV318">
        <div class="homeRoleIconV318">${whSvg}</div>
        <div class="homeRoleTitleV318">ВХОД ДЛЯ СКЛАДА</div>
        <div class="homeRoleOpenV318">ОТКРЫТЬ</div>
      </button>
      <button id="betaAdmin" class="homeRoleCardV318 homeAdminV318">
        <div class="homeRoleIconV318">${adminSvg}</div>
        <div class="homeRoleTitleV318">АДМИНИСТРИРОВАНИЕ</div>
        <div class="homeRoleOpenV318">ОТКРЫТЬ</div>
      </button>
    </div>
  </div>`);

  const back=byId('globalBack');
  if(back)back.style.display='none';

  byId('betaOperator').onclick=()=>showUserLogin('OPERATOR');
  byId('betaWarehouse').onclick=()=>showUserLogin('WAREHOUSE');
  byId('betaAdmin').onclick=()=>showUserLogin('ADMIN');
};

// Original classic UI renders the home screen before this late patch is injected.
// Re-render it once after the override is installed so the new role buttons are actually visible.
setTimeout(()=>{
  try{
    if(byId('betaAdmin') || byId('betaWarehouse') || (window.uiState&&uiState.screen==='home')){
      showBetaHome(false);
    }
  }catch(e){console.error('refresh patched home',e)}
},0);

// ===== v3.13 deterministic Back: return to section root, not navigation history =====
window.canAppBack=function(){return true};
window.appBack=function(){
  try{
    if(document.querySelector('.modal.show,.modalOverlay.show,.dialog.show')){
      const close=document.querySelector('.modal.show [data-close],.modal.show .close,.modalOverlay.show [data-close],.dialog.show [data-close]');
      if(close){close.click();return}
    }

    const role=(window.uiState&&uiState.role)||session.role||null;
    const tab=(window.uiState&&uiState.tab)||null;

    if(role==='WAREHOUSE'){
      if(tab==='replenish'&&(session.routeCells?.length||session.routeIndex>=0)){
        session.routeCells=[];session.routeIndex=-1;session.routeActual={};session.flow=null;
        showWarehouse('replenish',false);
        return;
      }
      if(tab==='revision'&&session.revisionCellId!=null){
        session.revisionCellId=null;session.revisionActual={};session.flow=null;
        showWarehouse('revision',false);
        return;
      }
      if(tab!=='replenish'){
        session.routeCells=[];session.routeIndex=-1;session.routeActual={};session.revisionCellId=null;session.revisionActual={};session.flow=null;
        showWarehouse('replenish',false);
        return;
      }
      showBetaHome(false);
      return;
    }

    if(role==='ADMIN'){
      if(tab&&tab!=='overview'){showAdmin('overview',false);return}
      showBetaHome(false);
      return;
    }

    if(role==='OPERATOR'){
      showBetaHome(false);
      return;
    }

    if(window.uiState&&uiState.screen!=='home'){showBetaHome(false);return}
    return;
  }catch(e){
    console.error('appBack v3.13',e);
    try{showBetaHome(false)}catch(_){}
  }
};

// ===== v3.14 compact warehouse cards + dynamic Back/Exit =====
whQueueBody=function(){
  const groups=whQueueGroups();
  const totalPositions=groups.reduce((s,g)=>s+g.tasks.length,0);
  const totalQty=groups.reduce((s,g)=>s+g.tasks.reduce((x,t)=>x+Math.max(0,t.a.target-t.a.stock),0),0);
  const cards=groups.map(g=>{
    const c=cell(g.cellId),o=ownerOfCell(g.cellId);
    const critical=g.tasks.some(t=>t.a.stock===0);
    const qty=g.tasks.reduce((s,t)=>s+Math.max(0,t.a.target-t.a.stock),0);
    return `<button class="whCard" data-wh-cell="${g.cellId}">
      <div class="whCardHead">
        <div class="whCardPerson"><b>${esc(o?.name||'Сотрудник не назначен')}</b><span>${esc(c?.name||('Ячейка №'+g.cellId))}</span></div>
        <span class="badge ${critical?'red':'orange'}">${critical?'КРИТИЧНО':'ПОПОЛНИТЬ'}</span>
      </div>
      <div class="whCardStats">
        <div class="whCardMetric"><b>${g.tasks.length}</b><span>позиций</span></div>
        <div class="whCardMetric"><b>+${qty}</b><span>единиц</span></div>
        <span class="whCardArrow">›</span>
      </div>
    </button>`;
  }).join('');
  return `<style>
    .whStats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-bottom:14px}
    .whStat{background:#fff;border:1px solid var(--line);border-radius:14px;padding:14px}.whStat b{font-size:24px;display:block}.whStat span{font-size:12px;color:var(--muted)}
    .whCards{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
    .whCard{width:100%;min-height:118px;background:#fff;border:1px solid var(--line);border-radius:14px;padding:13px;text-align:left;color:inherit;font:inherit;cursor:pointer;display:block}
    .whCard:active{transform:scale(.995)}
    .whCardHead{display:flex;align-items:flex-start;gap:8px}.whCardPerson{min-width:0;flex:1}.whCardPerson b{display:block;font-size:16px;line-height:1.2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.whCardPerson span{display:block;color:var(--muted);font-size:11px;margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .whCardHead .badge{margin-left:0;flex:0 0 auto;font-size:10px;padding:5px 7px}
    .whCardStats{display:grid;grid-template-columns:1fr 1fr 20px;gap:8px;align-items:center;margin-top:13px}.whCardMetric{background:#f6f8fb;border-radius:9px;padding:8px 9px}.whCardMetric b{display:block;font-size:17px}.whCardMetric span{display:block;color:var(--muted);font-size:10px;margin-top:2px}.whCardArrow{font-size:25px;color:var(--muted);text-align:right}
    .whInfo{margin-bottom:14px}
    @media(max-width:520px){.whCards{grid-template-columns:1fr}.whStats{grid-template-columns:1fr}.whStat{padding:10px 12px}.whStat b{font-size:20px}}
  </style>
  <div class="contentHead"><div><div class="h1">Восполнение</div><p>Выберите пользователя / ячейку. После выбора откроется список СИЗ и количество для пополнения.</p></div><div class="right"><button class="btn outline" id="whRefresh">Обновить</button></div></div>
  <div class="note whInfo"><b>Отчёт склада:</b> формируется из этой очереди. Расписание — <b>среда и пятница</b>.</div>
  <div class="whStats">
    <div class="whStat"><b>${groups.length}</b><span>Ячеек к восполнению</span></div>
    <div class="whStat"><b>${totalPositions}</b><span>Позиций СИЗ</span></div>
    <div class="whStat"><b>${totalQty}</b><span>Единиц добавить</span></div>
  </div>
  <div class="whCards">${cards||'<div class="empty">Сейчас восполнение не требуется</div>'}</div>`;
};

function warehouseAtRoot(active){
  return active==='replenish'&&!(session.routeCells&&session.routeCells.length&&session.routeIndex>=0);
}

function currentSectionRoot(){
  const role=(window.uiState&&uiState.role)||session.role||null;
  const screen=(window.uiState&&uiState.screen)||null;
  const tab=(window.uiState&&uiState.tab)||null;

  if(role==='WAREHOUSE'||screen==='warehouse'){
    return (tab==='replenish'||tab==='tasks'||tab==null)&&warehouseAtRoot('replenish');
  }
  if(role==='ADMIN'||screen==='admin'){
    return tab==='overview'||tab==null;
  }
  if(role==='OPERATOR'||screen==='operator'){
    return true;
  }
  return false;
}

function currentSectionName(){
  const role=(window.uiState&&uiState.role)||session.role||null;
  if(role==='WAREHOUSE')return 'Склад';
  if(role==='ADMIN')return 'Администрирование';
  if(role==='OPERATOR')return 'Оператор';
  return 'раздел';
}

function confirmSectionExit(){
  const section=currentSectionName();
  confirmModal('Выйти из раздела?',`Завершить работу в разделе «${esc(section)}» и вернуться на главный экран?`,'ВЫЙТИ',()=>{
    session.routeCells=[];session.routeIndex=-1;session.routeActual={};
    session.revisionCellId=null;session.revisionActual={};
    session.flow=null;
    session.role=null;session.user=null;
    resetUiHome();
    showBetaHome(false);
  });
}

// Одна штатная кнопка globalBack во всем приложении:
// на корне любого рабочего раздела = ВЫХОД,
// на вложенных экранах = ← НАЗАД.
const __renderV316=render;
render=function(html){
  __renderV316(html);
  const b=byId('globalBack');
  if(!b)return;
  if(window.uiState&&uiState.screen==='home'){
    b.style.display='none';
    return;
  }
  b.style.display='';
  if(currentSectionRoot()){
    b.textContent='ВЫХОД';
    b.title='Выйти из раздела';
  }else{
    b.textContent='← НАЗАД';
    b.title='Назад';
  }
};

const __appBackV316=window.appBack;
window.appBack=function(){
  try{
    const mr=byId('modalRoot');
    if(mr&&mr.classList.contains('show')){
      __appBackV316();
      return;
    }

    const role=(window.uiState&&uiState.role)||session.role||null;
    const tab=(window.uiState&&uiState.tab)||null;

    if(currentSectionRoot()){
      confirmSectionExit();
      return;
    }

    // Вложенные экраны всегда возвращаются в корень своего раздела,
    // а не прокликивают историю переходов.
    if(role==='WAREHOUSE'){
      session.routeCells=[];session.routeIndex=-1;session.routeActual={};
      session.revisionCellId=null;session.revisionActual={};session.flow=null;
      showWarehouse('replenish',false);
      return;
    }
    if(role==='ADMIN'){
      showAdmin('overview',false);
      return;
    }
    if(role==='OPERATOR'){
      showOperator(false);
      return;
    }

    __appBackV316();
  }catch(e){
    console.error('appBack v3.16',e);
    try{__appBackV316()}catch(_){}
  }
};


// ===== v3.19 reliable root Exit button =====
function syncGlobalSectionButton(){
  const b=byId('globalBack');
  if(!b)return;

  const screen=(window.uiState&&uiState.screen)||null;
  const role=(window.uiState&&uiState.role)||session.role||null;
  const tab=(window.uiState&&uiState.tab)||null;

  if(screen==='home'){
    b.style.display='none';
    b.onclick=null;
    return;
  }

  b.style.display='';

  let atRoot=false;
  if(role==='WAREHOUSE'||screen==='warehouse'){
    atRoot=(tab==='replenish')&&warehouseAtRoot('replenish');
  }else if(role==='ADMIN'||screen==='admin'){
    atRoot=(tab==='overview');
  }else if(role==='OPERATOR'||screen==='operator'){
    atRoot=true;
  }

  if(atRoot){
    b.textContent='ВЫХОД';
    b.title='Выйти из раздела';
    b.onclick=()=>confirmSectionExit();
  }else{
    b.textContent='← НАЗАД';
    b.title='Назад';
    b.onclick=()=>window.appBack();
  }
}

function setGlobalButtonExit(){
  const b=byId('globalBack');
  if(!b)return;
  b.style.display='';
  b.textContent='ВЫХОД';
  b.title='Выйти из раздела';
  b.onclick=()=>confirmSectionExit();
}

function setGlobalButtonBack(){
  const b=byId('globalBack');
  if(!b)return;
  b.style.display='';
  b.textContent='← НАЗАД';
  b.title='Назад';
  b.onclick=()=>window.appBack();
}

function applyWarehouseNavMode(tab){
  const normalized=(tab==='tasks'||tab==='route')?'replenish':(tab==='done'?'history':tab);
  const atRoot=normalized==='replenish'&&!(session.routeCells&&session.routeCells.length&&session.routeIndex>=0);
  if(atRoot)setGlobalButtonExit();else setGlobalButtonBack();
}

const __showWarehouseV319=showWarehouse;
showWarehouse=function(tab='replenish',push=true){
  __showWarehouseV319(tab,push);
  applyWarehouseNavMode(tab);
  setTimeout(()=>applyWarehouseNavMode(tab),0);
};

const __showAdminV319=showAdmin;
showAdmin=function(tab='overview',push=true){
  __showAdminV319(tab,push);
  if(tab==='overview'||tab==null)setGlobalButtonExit();else setGlobalButtonBack();
  setTimeout(()=>{if(tab==='overview'||tab==null)setGlobalButtonExit();else setGlobalButtonBack()},0);
};

const __showOperatorV319=showOperator;
showOperator=function(push=true){
  __showOperatorV319(push);
  setGlobalButtonExit();
  setTimeout(()=>setGlobalButtonExit(),0);
};

const __showUserLoginV319=showUserLogin;
showUserLogin=function(role,push=true){
  __showUserLoginV319(role,push);

  // На экране выбора пользователя / ввода PIN оставляем только
  // внутреннюю кнопку "Назад". Верхнюю globalBack удаляем.
  const topBack=byId('globalBack');
  if(topBack)topBack.remove();

  const backRoles=byId('backRoles');
  if(backRoles){
    backRoles.textContent='← НАЗАД';
    backRoles.onclick=()=>showBetaHome(false);
  }
};

const __showBetaHomeV319=showBetaHome;
showBetaHome=function(push=true){
  __showBetaHomeV319(push);

  // На главном экране выбора раздела кнопки "Назад" быть не должно.
  const topBack=byId('globalBack');
  if(topBack)topBack.remove();
};


// ===== v3.22 deterministic navigation by role =====
function roleRootState(){
  const role=(window.uiState&&uiState.role)||session.role||null;
  const screen=(window.uiState&&uiState.screen)||null;
  const tab=(window.uiState&&uiState.tab)||null;

  if(screen==='home')return {role:null,root:false,home:true};
  if(screen==='login')return {role,root:false,login:true};

  if(role==='WAREHOUSE'||screen==='warehouse'){
    const normalized=(tab==='tasks'||tab==='route'||tab==null)?'replenish':(tab==='done'?'history':tab);
    const routeOpen=!!(session.routeCells&&session.routeCells.length&&session.routeIndex>=0);
    return {role:'WAREHOUSE',root:normalized==='replenish'&&!routeOpen};
  }
  if(role==='ADMIN'||screen==='admin'){
    return {role:'ADMIN',root:tab==='overview'||tab==null};
  }
  if(role==='OPERATOR'||screen==='operator'){
    return {role:'OPERATOR',root:true};
  }
  return {role,root:false};
}

function returnToRoleRoot(){
  const s=roleRootState();

  if(s.login){
    showBetaHome(false);
    return;
  }

  if(s.role==='WAREHOUSE'){
    session.routeCells=[];session.routeIndex=-1;session.routeActual={};
    session.revisionCellId=null;session.revisionActual={};
    session.flow=null;
    showWarehouse('replenish',false);
    return;
  }

  if(s.role==='ADMIN'){
    showAdmin('overview',false);
    return;
  }

  if(s.role==='OPERATOR'){
    showOperator(false);
    return;
  }

  showBetaHome(false);
}

function closeTopDialogIfAny(){
  const close=document.querySelector(
    '.modal.show [data-close],.modal.show .close,'+
    '.modalOverlay.show [data-close],.dialog.show [data-close]'
  );
  if(close){close.click();return true}
  return false;
}

// "Назад" никогда не решает сам, выходить или нет.
// Он только возвращает в корень текущей роли.
setGlobalButtonBack=function(){
  const b=byId('globalBack');
  if(!b)return;
  b.style.display='';
  b.textContent='← НАЗАД';
  b.title='Назад';
  b.onclick=()=>returnToRoleRoot();
};

// Финальная единая логика аппаратной/системной кнопки Back.
window.appBack=function(){
  try{
    if(closeTopDialogIfAny())return;

    const s=roleRootState();
    if(s.home)return;
    if(s.login){showBetaHome(false);return}

    if(s.root){
      confirmSectionExit();
      return;
    }

    returnToRoleRoot();
  }catch(e){
    console.error('appBack v3.22',e);
    try{showBetaHome(false)}catch(_){}
  }
};

""";
    }


    private String warehouseReportPatchScript() {
        return """

// ===== v3.24 manual warehouse replenishment report =====
const __whQueueBodyV324=whQueueBody;
whQueueBody=function(){
  const html=__whQueueBodyV324();
  const marker='<button class="btn outline" id="whRefresh">Обновить</button>';
  const controls='<div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn primary" id="whSendReplenishmentReport">ОТЧЁТ О ВОСПОЛНЕНИИ</button><button class="btn outline" id="whRefresh">Обновить</button></div>';
  return String(html).replace(marker,controls);
};

function warehouseReplenishmentReport(){
  ensureWarehouseData();
  const groups=whQueueGroups();
  const now=new Date();
  const pad=n=>String(n).padStart(2,'0');
  const stamp=pad(now.getDate())+'.'+pad(now.getMonth()+1)+'.'+now.getFullYear()+' '+pad(now.getHours())+':'+pad(now.getMinutes());

  let totalPositions=0,totalQty=0;
  const lines=[];
  lines.push('ОТЧЁТ О ВОСПОЛНЕНИИ СИЗ');
  lines.push('Сформирован: '+stamp);
  lines.push('');

  if(!groups.length){
    lines.push('На момент формирования отчёта восполнение не требуется.');
  }else{
    groups.forEach((g,idx)=>{
      const c=cell(g.cellId),o=ownerOfCell(g.cellId);
      const cellName=c?.name||('Ячейка №'+g.cellId);
      const owner=o?.name||'Сотрудник не назначен';
      lines.push((idx+1)+'. '+cellName+' — '+owner);
      g.tasks.forEach(t=>{
        const a=t.a;
        const need=Math.max(0,Number(a.target||0)-Number(a.stock||0));
        const ppeName=ppe(t.ppeId)?.name||String(t.ppeId||'СИЗ');
        totalPositions++;
        totalQty+=need;
        lines.push('   • '+ppeName+': сейчас '+a.stock+', целевой '+a.target+', добавить '+need);
      });
      lines.push('');
    });
    lines.push('ИТОГО');
    lines.push('Ячеек: '+groups.length);
    lines.push('Позиций СИЗ: '+totalPositions);
    lines.push('Единиц к пополнению: '+totalQty);
  }

  return {
    subject:'Постомат СИЗ — отчёт о восполнении — '+pad(now.getDate())+'.'+pad(now.getMonth()+1)+'.'+now.getFullYear(),
    body:lines.join(String.fromCharCode(10))
  };
}

function sendWarehouseReplenishmentReport(){
  ensureWarehouseData();
  const email=String(db.settings.warehouseReportEmail||'').trim();
  if(!email){
    toast('Email склада не настроен. Укажите его в Администрирование → Настройки.','error');
    return false;
  }
  if(email.indexOf('@')<1||email.lastIndexOf('.')<email.indexOf('@')+2){
    toast('В настройках указан некорректный Email склада','error');
    return false;
  }
  if(typeof NativeStore==='undefined'||typeof NativeStore.sendEmailText!=='function'){
    toast('Отправка Email недоступна в этой сборке','error');
    return false;
  }

  try{
    const report=warehouseReplenishmentReport();
    const result=String(NativeStore.sendEmailText(email,report.subject,report.body)||'');
    if(result==='OK'){
      toast('Отчёт подготовлен для отправки на '+email,'ok');
      return true;
    }
    if(result==='NO_APP'){
      toast('На планшете не найдено почтовое приложение','error');
      return false;
    }
    toast('Не удалось открыть отправку отчёта'+(result?': '+result:''),'error');
    return false;
  }catch(e){
    toast('Ошибка формирования отчёта: '+(e.message||e),'error');
    return false;
  }
}

const __wireWhReplenishV324=wireWhReplenish;
wireWhReplenish=function(){
  __wireWhReplenishV324();
  const b=byId('whSendReplenishmentReport');
  if(b)b.onclick=()=>sendWarehouseReplenishmentReport();
};

""";
    }


    private String smtpMailPatchScript() {
        return """

// ===== v3.25 Mail.ru SMTP background mail =====
function smtpConfigured(){
  try{return typeof NativeStore!=='undefined'&&typeof NativeStore.isSmtpConfigured==='function'&&NativeStore.isSmtpConfigured()}catch(e){return false}
}
function smtpSenderEmail(){
  try{return typeof NativeStore!=='undefined'&&typeof NativeStore.getSmtpSenderEmail==='function'?String(NativeStore.getSmtpSenderEmail()||''):''}catch(e){return ''}
}
function smtpEmailOk(v){
  v=String(v||'').trim();
  return v.indexOf('@')>0&&v.lastIndexOf('.')>v.indexOf('@')+1;
}
window.onNativeMailResult=function(contextId,ok,message){
  try{
    if(ok)toast(String(message||'Письмо отправлено'),'ok');
    else toast(String(message||'Не удалось отправить письмо'),'error');
  }catch(e){console.error('mail result',e)}
};

const __adminSettingsV325=adminSettings;
adminSettings=function(){
  const base=__adminSettingsV325();
  const configured=smtpConfigured();
  const sender=smtpSenderEmail();
  let h='';
  h+='<div class="sectionLabel">Почта для отправки отчётов</div>';
  h+='<div class="card" style="max-width:760px">';
  h+='<div class="h2" style="font-size:18px">Техническая почта Mail.ru</div>';
  h+='<div class="sub" style="margin-bottom:12px">Отчёты отправляются напрямую через smtp.mail.ru:465 (SSL) в фоне. Почтовый клиент на планшете не используется.</div>';
  h+='<div class="reportStatus '+(configured?'ok':'internal')+'" style="margin-bottom:12px">'+(configured?'SMTP настроен • '+esc(sender):'SMTP ещё не настроен')+'</div>';
  h+='<div class="field"><label>Email отправителя Mail.ru</label><input id="smtpSenderEmail" class="input" data-vk="latin" inputmode="email" autocomplete="off" autocapitalize="none" value="'+esc(sender)+'"></div>';
  h+='<div class="field"><label>Пароль для внешнего приложения Mail.ru</label><input id="smtpAppPassword" class="input" type="password" data-vk="latin" autocomplete="new-password" autocapitalize="none" value="" placeholder="'+(configured?'Сохранён. Для замены введите новый пароль':'Введите пароль приложения')+'"></div>';
  h+='<div style="display:flex;gap:8px;flex-wrap:wrap"><button id="saveSmtpSettings" class="btn primary">СОХРАНИТЬ ПОЧТУ</button>';
  if(configured)h+='<button id="clearSmtpSettings" class="btn outline">УДАЛИТЬ НАСТРОЙКИ ПОЧТЫ</button>';
  h+='</div>';
  h+='<div class="sectionLabel" style="margin-top:18px">Тестовая отправка</div>';
  h+='<div class="sub" style="margin-bottom:10px">Введите любой адрес. Приложение отправит на него тестовое письмо без открытия почтового клиента.</div>';
  h+='<div class="field"><label>Тестовый адрес получателя</label><input id="smtpTestRecipient" class="input" data-vk="latin" inputmode="email" autocomplete="off" autocapitalize="none" value=""></div>';
  h+='<button id="sendSmtpTest" class="btn green">ОТПРАВИТЬ ТЕСТОВОЕ ПИСЬМО</button>';
  h+='</div>';
  return base+h;
};

const __wireAdminV325=wireAdmin;
wireAdmin=function(tab){
  __wireAdminV325(tab);
  if(tab==='settings'){
    const save=byId('saveSmtpSettings');
    if(save)save.onclick=function(){
      const email=String(byId('smtpSenderEmail')?byId('smtpSenderEmail').value:'').trim();
      const pass=String(byId('smtpAppPassword')?byId('smtpAppPassword').value:'');
      const current=smtpSenderEmail();
      if(!smtpEmailOk(email)){toast('Введите корректный Email отправителя','error');return}
      if(!pass){
        if(smtpConfigured()&&email===current){toast('Настройки почты уже сохранены','ok');return}
        toast('Введите пароль для внешнего приложения Mail.ru','error');return;
      }
      try{
        const ok=NativeStore.saveSmtpCredentials(email,pass);
        if(!ok){toast('Не удалось сохранить настройки SMTP','error');return}
        if(byId('smtpAppPassword'))byId('smtpAppPassword').value='';
        toast('Настройки Mail.ru сохранены в защищённом хранилище планшета','ok');
        showAdmin('settings');
      }catch(e){toast('Ошибка сохранения почты: '+(e.message||e),'error')}
    };

    const clear=byId('clearSmtpSettings');
    if(clear)clear.onclick=function(){
      confirmModal('Удалить настройки почты?','Технический Email и сохранённый пароль приложения будут удалены с планшета.','УДАЛИТЬ',function(){
        try{NativeStore.clearSmtpCredentials();toast('Настройки почты удалены','ok');showAdmin('settings')}catch(e){toast('Не удалось удалить настройки','error')}
      });
    };

    const test=byId('sendSmtpTest');
    if(test)test.onclick=function(){
      const to=String(byId('smtpTestRecipient')?byId('smtpTestRecipient').value:'').trim();
      if(!smtpEmailOk(to)){toast('Введите корректный тестовый Email','error');return}
      if(!smtpConfigured()){toast('Сначала сохраните техническую почту Mail.ru','error');return}
      const stamp=new Date().toLocaleString('ru-RU');
      const body='Тестовое письмо из приложения «Постомат СИЗ».\\n\\nВремя проверки: '+stamp+'\\nЕсли это письмо получено, фоновая SMTP-отправка работает.';
      const result=String(NativeStore.smtpSendText(to,'Постомат СИЗ — тест отправки',body,'test')||'');
      if(result==='QUEUED')toast('Тестовое письмо отправляется в фоне…','ok');
      else if(result==='NOT_CONFIGURED')toast('SMTP не настроен','error');
      else toast('Не удалось запустить тестовую отправку: '+result,'error');
    };
    enableNativeMobileKeyboard(document);
  }
};

function smtpQueueAttachment(bytes,fileName,to,subject,body,contextId){
  if(!smtpConfigured()){toast('Сначала настройте техническую почту в Администрирование → Настройки','error');return false}
  if(!smtpEmailOk(to)){toast('Введите корректный Email получателя','error');return false}
  try{
    const result=String(NativeStore.smtpSendBase64Attachment(bytesToBase64(bytes),fileName,to,subject,body,contextId)||'');
    if(result==='QUEUED'){toast('Отчёт сформирован. Письмо отправляется в фоне…','ok');return true}
    if(result==='NOT_CONFIGURED'){toast('SMTP не настроен','error');return false}
    toast('Не удалось запустить отправку: '+result,'error');return false;
  }catch(e){toast('Ошибка отправки: '+(e.message||e),'error');return false}
}

sendReportByEmail=function(type,key,email){
  email=String(email||'').trim();
  if(!smtpEmailOk(email)){toast('Введите корректный Email','error');return false}
  try{
    const rec=saveReport(type,key,false);
    const bytes=buildOneSheetXlsx(type,key);
    const subject=(type==='weekly'?'Еженедельный':'Ежемесячный')+' отчёт СИЗ — '+(rec.label||key);
    const body='Отчёт сформирован приложением «Постомат СИЗ». XLSX-файл приложен к письму.';
    return smtpQueueAttachment(bytes,rec.fileName,email,subject,body,type+':'+key);
  }catch(e){toast('Ошибка формирования отчёта: '+(e.message||e),'error');return false}
};

function buildWarehouseReplenishmentXlsx(){
  const groups=whQueueGroups().slice().sort(function(a,b){
    return Number(a.cellId||0)-Number(b.cellId||0);
  });
  const now=new Date();
  const stamp=now.toLocaleString('ru-RU');
  let qty=0,positions=0;
  const data=[];

  groups.forEach(function(g){
    const c=cell(g.cellId),o=ownerOfCell(g.cellId);
    let cellLabel=c&&c.name?String(c.name):('№'+g.cellId);
    if(cellLabel.indexOf('Ячейка ')===0)cellLabel=cellLabel.slice(7);
    g.tasks.slice().sort(function(a,b){
      const an=ppe(a.ppeId)?ppe(a.ppeId).name:String(a.ppeId||'СИЗ');
      const bn=ppe(b.ppeId)?ppe(b.ppeId).name:String(b.ppeId||'СИЗ');
      return an.localeCompare(bn,'ru');
    }).forEach(function(t){
      const a=t.a;
      const need=Math.max(0,Number(a.target||0)-Number(a.stock||0));
      qty+=need;positions++;
      data.push([
        cellLabel,
        o&&o.name?o.name:'Сотрудник не назначен',
        ppe(t.ppeId)?ppe(t.ppeId).name:String(t.ppeId||'СИЗ'),
        Number(a.stock||0),
        Number(a.target||0),
        need
      ]);
    });
  });

  const rows=[];
  rows.push([{v:'ОТЧЁТ О ВОСПОЛНЕНИИ СИЗ',s:1},{},{},{},{},{}]);
  rows.push([{v:'Сформирован: '+stamp,s:2},{},{},{},{},{}]);
  rows.push([{v:'Ячеек к пополнению: '+groups.length+'   •   Позиций СИЗ: '+positions+'   •   Всего добавить: '+qty+' шт.',s:3},{},{},{},{},{}]);
  rows.push([{v:'Ячейка',s:4},{v:'Сотрудник',s:4},{v:'СИЗ',s:4},{v:'Сейчас',s:4},{v:'Max',s:4},{v:'Добавить',s:4}]);

  if(data.length){
    data.forEach(function(r){
      rows.push([
        {v:r[0],s:5},
        {v:r[1],s:5},
        {v:r[2],s:5},
        {v:r[3],s:6},
        {v:r[4],s:6},
        {v:r[5],s:7}
      ]);
    });
    rows.push([
      {v:'ИТОГО',s:8},
      {v:'',s:8},
      {v:'',s:8},
      {v:'',s:8},
      {v:'',s:8},
      {v:qty,s:9}
    ]);
  }else{
    rows.push([{v:'На момент формирования восполнение не требуется',s:10},{},{},{},{},{}]);
  }

  let rr='';
  rows.forEach(function(row,ri){
    let cc='';
    for(let ci=0;ci<7;ci++){
      const x=row[ci]||{v:null,s:0};
      cc+=cellXml(x.v,colName(ci+1)+(ri+1),x.s||0);
    }
    let ht='';
    if(ri===0)ht=' ht="30" customHeight="1"';
    else if(ri===3)ht=' ht="28" customHeight="1"';
    rr+='<row r="'+(ri+1)+'"'+ht+'>'+cc+'</row>';
  });

  const merges=data.length
    ?'<mergeCells count="3"><mergeCell ref="A1:G1"/><mergeCell ref="A2:G2"/><mergeCell ref="A3:G3"/></mergeCells>'
    :'<mergeCells count="4"><mergeCell ref="A1:G1"/><mergeCell ref="A2:G2"/><mergeCell ref="A3:G3"/><mergeCell ref="A5:G5"/></mergeCells>';

  const sheet='<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    +'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    +'<sheetViews><sheetView workbookViewId="0"><pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
    +'<cols>'
      +'<col min="1" max="1" width="14" customWidth="1"/>'
      +'<col min="2" max="2" width="28" customWidth="1"/>'
      +'<col min="3" max="3" width="34" customWidth="1"/>'
      +'<col min="4" max="6" width="14" customWidth="1"/>'
    +'</cols>'
    +'<sheetData>'+rr+'</sheetData>'+merges
    +'<pageMargins left="0.35" right="0.35" top="0.45" bottom="0.45" header="0.2" footer="0.2"/>'
    +'<pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0" paperSize="9"/>'
    +'</worksheet>';

  const ct='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>';
  const rels='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>';
  const wb='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Восполнение" sheetId="1" r:id="rId1"/></sheets></workbook>';
  const wbr='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>';

  const styles='<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    +'<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    +'<fonts count="5">'
      +'<font><sz val="10"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="16"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
      +'<font><i/><sz val="10"/><color rgb="FF44546A"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="11"/><color rgb="FF7F6000"/><name val="Calibri"/></font>'
    +'</fonts>'
    +'<fills count="7">'
      +'<fill><patternFill patternType="none"/></fill>'
      +'<fill><patternFill patternType="gray125"/></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FF17365D"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFD9EAF7"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FF5B9BD5"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFE2F0D9"/></patternFill></fill>'
    +'</fills>'
    +'<borders count="2"><border/><border>'
      +'<left style="thin"><color rgb="FFD9E1F2"/></left><right style="thin"><color rgb="FFD9E1F2"/></right>'
      +'<top style="thin"><color rgb="FFD9E1F2"/></top><bottom style="thin"><color rgb="FFD9E1F2"/></bottom>'
    +'</border></borders>'
    +'<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
    +'<cellXfs count="11">'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
      +'<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="4" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="4" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="2" fillId="6" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>'
    +'</cellXfs>'
    +'</styleSheet>';

  return zipStore([
    {name:'[Content_Types].xml',text:ct},
    {name:'_rels/.rels',text:rels},
    {name:'xl/workbook.xml',text:wb},
    {name:'xl/_rels/workbook.xml.rels',text:wbr},
    {name:'xl/styles.xml',text:styles},
    {name:'xl/worksheets/sheet1.xml',text:sheet}
  ]);
}

sendWarehouseReplenishmentReport=function(){
  ensureWarehouseData();
  const email=String(db.settings.warehouseReportEmail||'').trim();
  if(!email){toast('Email склада не настроен. Укажите его в Администрирование → Настройки.','error');return false}
  if(!smtpEmailOk(email)){toast('В настройках указан некорректный Email склада','error');return false}
  try{
    const bytes=buildWarehouseReplenishmentXlsx();
    const d=new Date(),pad=function(n){return String(n).padStart(2,'0')};
    const fileName='Otchet_vospolnenie_'+d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+'_'+pad(d.getHours())+pad(d.getMinutes())+'.xlsx';
    if(nativeStorageAvailable()){try{nativeSaveBytes('Reports/'+fileName,bytes)}catch(e){}}
    const groups=whQueueGroups();
    const totalPositions=groups.reduce(function(s,g){return s+g.tasks.length},0);
    const totalQty=groups.reduce(function(s,g){return s+g.tasks.reduce(function(x,t){return x+Math.max(0,t.a.target-t.a.stock)},0)},0);
    const subject='Постомат СИЗ — отчёт о восполнении — '+pad(d.getDate())+'.'+pad(d.getMonth()+1)+'.'+d.getFullYear();
    const body='Актуальный отчёт о восполнении СИЗ.\\nЯчеек: '+groups.length+'\\nПозиций: '+totalPositions+'\\nЕдиниц добавить: '+totalQty+'\\n\\nПодробный XLSX-файл приложен к письму.';
    return smtpQueueAttachment(bytes,fileName,email,subject,body,'warehouse');
  }catch(e){toast('Ошибка формирования отчёта: '+(e.message||e),'error');return false}
};

""";
    }


    private String readableReportPatchScript() {
        return """

// ===== v3.26 readable XLSX reports =====
function rrRow(rows,vals,style){
  const styles=Array.isArray(style)?style:null;
  rows.push(Array.from({length:9},function(_,i){
    return {v:i<vals.length?vals[i]:null,s:styles?(styles[i]||0):(style||0)};
  }));
}
function rrMergeRow(rows,merges,title,style){
  const r=rows.length+1;
  rrRow(rows,[title],style);
  merges.push('A'+r+':I'+r);
}
function rrKpi(rows,merges,items){
  for(let block=0;block<2;block++){
    const base=block*3;
    const labelRow=rows.length+1;
    const valueRow=labelRow+1;
    const labels=[],values=[];
    for(let i=0;i<3;i++){
      const item=items[base+i]||['',''];
      labels[i*3]=item[0];
      values[i*3]=item[1];
      const from=colName(i*3+1),to=colName(i*3+3);
      merges.push(from+labelRow+':'+to+labelRow);
      merges.push(from+valueRow+':'+to+valueRow);
    }
    rrRow(rows,labels,5);
    rrRow(rows,values,6);
  }
}
function rrCurrentAttention(){
  const today=new Date();
  const out=[];
  db.assignments.filter(function(a){return a.active&&Number(a.stock||0)<=Number(a.min||0)}).forEach(function(a){
    const e=employee(a.employeeId),pp=ppe(a.ppeId);
    const late=(db.tasks||[]).find(function(t){
      return t.status==='OPEN'&&t.assignmentId===a.id&&new Date(t.due)<today;
    });
    const need=Math.max(0,Number(a.target||0)-Number(a.stock||0));
    const status=late?'ПРОСРОЧЕНО':Number(a.stock||0)===0?'КРИТИЧНО':'ПОПОЛНИТЬ';
    const note=late?('Срок: '+reportFmtDate(late.due)):(Number(a.stock||0)===0?'Нет в наличии':'Остаток достиг Min');
    out.push({
      status:status,cell:'№'+a.cellId,employee:e?e.name:'—',ppe:pp?pp.name:a.ppeId,
      stock:Number(a.stock||0),min:Number(a.min||0),max:Number(a.target||0),need:need,note:note
    });
  });
  const order={ПРОСРОЧЕНО:0,КРИТИЧНО:1,ПОПОЛНИТЬ:2};
  out.sort(function(a,b){
    const s=(order[a.status]||0)-(order[b.status]||0);
    if(s!==0)return s;
    const ca=parseInt(String(a.cell).replace(/\\D/g,''),10)||0;
    const cb=parseInt(String(b.cell).replace(/\\D/g,''),10)||0;
    return ca-cb||String(a.ppe).localeCompare(String(b.ppe),'ru');
  });
  return out;
}

function rrMonthlyIssuedByEmployeePpe(d){
  const map={};
  d.issues.forEach(function(x){
    const employeeName=String(x.userName||x.userId||'—');
    const ppeName=String(x.ppeName||x.ppeId||'—');
    const key=employeeName+'\u0001'+ppeName;
    if(!map[key]){
      map[key]={
        employee:employeeName,
        cell:x.cellId?('№'+x.cellId):'—',
        ppe:ppeName,
        qty:0,
        count:0,
        dates:[]
      };
    }
    map[key].qty+=Number(x.qty||0);
    map[key].count++;
    if(x.ts)map[key].dates.push(x.ts);
    if(x.cellId)map[key].cell='№'+x.cellId;
  });
  return Object.values(map).map(function(x){
    const dates=x.dates.slice().sort(function(a,b){return new Date(a)-new Date(b)});
    x.first=dates.length?reportFmtDate(dates[0]):'—';
    x.last=dates.length?reportFmtDate(dates[dates.length-1]):'—';
    return x;
  }).sort(function(a,b){
    const e=String(a.employee).localeCompare(String(b.employee),'ru');
    return e!==0?e:String(a.ppe).localeCompare(String(b.ppe),'ru');
  });
}

styledSheetXml=function(rows,merges){
  merges=merges||[];
  let rr='';
  rows.forEach(function(row,ri){
    let cc='';
    for(let ci=0;ci<9;ci++){
      const cell=row[ci]||{v:null,s:0};
      cc+=cellXml(cell.v,colName(ci+1)+(ri+1),cell.s||0);
    }
    let ht='';
    if(ri===0)ht=' ht="30" customHeight="1"';
    else if(ri===1)ht=' ht="22" customHeight="1"';
    else if(row.some(function(x){return x&&x.s===5}))ht=' ht="28" customHeight="1"';
    else if(row.some(function(x){return x&&x.s===6}))ht=' ht="32" customHeight="1"';
    else if(row.some(function(x){return x&&x.s===4}))ht=' ht="30" customHeight="1"';
    rr+='<row r="'+(ri+1)+'"'+ht+'>'+cc+'</row>';
  });
  const mergeXml=merges.length?'<mergeCells count="'+merges.length+'">'+merges.map(function(x){return '<mergeCell ref="'+x+'"/>'}).join('')+'</mergeCells>':'';
  return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    +'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    +'<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>'
    +'<sheetViews><sheetView workbookViewId="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
    +'<cols>'
    +'<col min="1" max="1" width="18" customWidth="1"/>'
    +'<col min="2" max="2" width="13" customWidth="1"/>'
    +'<col min="3" max="3" width="24" customWidth="1"/>'
    +'<col min="4" max="4" width="28" customWidth="1"/>'
    +'<col min="5" max="8" width="13" customWidth="1"/>'
    +'<col min="9" max="9" width="24" customWidth="1"/>'
    +'</cols>'
    +'<sheetData>'+rr+'</sheetData>'+mergeXml
    +'<pageMargins left="0.3" right="0.3" top="0.45" bottom="0.45" header="0.2" footer="0.2"/>'
    +'<pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0" paperSize="9"/>'
    +'</worksheet>';
};

buildOneSheetXlsx=function(type,key){
  const d=calcReportData(type,key),rows=[],merges=[];
  const attention=rrCurrentAttention();
  const needQty=attention.reduce(function(s,x){return s+x.need},0);
  const people=new Set(d.issues.map(function(x){return x.userId||x.userName})).size;
  const critical=attention.filter(function(x){return x.status==='КРИТИЧНО'||x.status==='ПРОСРОЧЕНО'}).length;

  rrMergeRow(rows,merges,d.p.title,1);
  rrMergeRow(rows,merges,'Период: '+d.p.label+'   •   Сформирован: '+reportFmtDateTime(new Date()),2);
  rrMergeRow(rows,merges,'СВОДКА',3);

  const kpis=type==='weekly'
    ?[
      ['Выдано, шт.',d.totalIssued],
      ['Сотрудников',people],
      ['Пополнено, шт.',d.totalRepl],
      ['Позиций к пополнению',attention.length],
      ['Критично / просрочено',critical],
      ['Нужно добавить, шт.',needQty]
     ]
    :[
      ['Выдано, шт.',d.totalIssued],
      ['Сотрудников',people],
      ['Пополнено, шт.',d.totalRepl],
      ['Отклонений',d.deviations.length],
      ['Корректировок',d.edits.length],
      ['Нужно добавить, шт.',needQty]
     ];
  rrKpi(rows,merges,kpis);

  rrMergeRow(rows,merges,'1. ТРЕБУЕТ ПОПОЛНЕНИЯ',3);
  rrRow(rows,['Статус','Ячейка','Сотрудник','СИЗ','Остаток','Min','Max','Добавить','Комментарий'],4);
  if(attention.length){
    attention.forEach(function(x){
      const st=(x.status==='КРИТИЧНО'||x.status==='ПРОСРОЧЕНО')?9:8;
      rrRow(rows,[x.status,x.cell,x.employee,x.ppe,x.stock,x.min,x.max,x.need,x.note],st);
    });
    rrRow(rows,['ИТОГО','','','','','','',needQty,''],11);
  }else{
    const r=rows.length+1;
    rrRow(rows,['На дату формирования пополнение не требуется'],12);
    merges.push('A'+r+':I'+r);
  }

  rrMergeRow(rows,merges,'2. ДВИЖЕНИЕ СИЗ ЗА ПЕРИОД',3);
  rrRow(rows,['СИЗ','Выдано, шт.','Пополнено, шт.','Баланс движения','Кол-во выдач','Сотрудников','','',''],4);
  const movementNames=new Set([].concat(Object.keys(d.byPpe),Object.keys(d.replByPpe)));
  const movementRows=[];
  Array.from(movementNames).sort(function(a,b){return a.localeCompare(b,'ru')}).forEach(function(name){
    const stat=d.byPpe[name]||{q:0,count:0,people:new Set()};
    const issued=Number(stat.q||0);
    const repl=Number(d.replByPpe[name]||0);
    if(issued>0||repl>0){
      movementRows.push([name,issued,repl,repl-issued,Number(stat.count||0),stat.people.size,'','','']);
    }
  });
  if(movementRows.length){
    movementRows.forEach(function(x){rrRow(rows,x,7)});
    rrRow(rows,['ИТОГО',d.totalIssued,d.totalRepl,d.totalRepl-d.totalIssued,'','','','',''],11);
  }else{
    const r=rows.length+1;
    rrRow(rows,['За выбранный период выдач и пополнений не было'],10);
    merges.push('A'+r+':I'+r);
  }

  let sectionNo=3;

  if(type==='weekly'){
    rrMergeRow(rows,merges,sectionNo+'. ВЫДАНО СИЗ СОТРУДНИКАМ ЗА НЕДЕЛЮ',3);sectionNo++;
    rrRow(rows,['Сотрудник','Ячейка','СИЗ','Выдано, шт.','Кол-во выдач','Первая выдача','Последняя выдача','',''],4);
    const weeklyIssuedRows=rrMonthlyIssuedByEmployeePpe(d);
    if(weeklyIssuedRows.length){
      weeklyIssuedRows.forEach(function(x){
        rrRow(rows,[x.employee,x.cell,x.ppe,x.qty,x.count,x.first,x.last,'',''],7);
      });
      rrRow(rows,['ИТОГО','','',d.totalIssued,'','','','',''],11);
    }else{
      const r=rows.length+1;rrRow(rows,['В выбранной неделе выдач СИЗ не было'],10);merges.push('A'+r+':I'+r);
    }
  }

  if(type==='monthly'){
    rrMergeRow(rows,merges,sectionNo+'. ВЫДАНО СИЗ СОТРУДНИКАМ ЗА МЕСЯЦ',3);sectionNo++;
    rrRow(rows,['Сотрудник','Ячейка','СИЗ','Выдано, шт.','Кол-во выдач','Первая выдача','Последняя выдача','',''],4);
    const issuedRows=rrMonthlyIssuedByEmployeePpe(d);
    if(issuedRows.length){
      issuedRows.forEach(function(x){
        rrRow(rows,[x.employee,x.cell,x.ppe,x.qty,x.count,x.first,x.last,'',''],7);
      });
      rrRow(rows,['ИТОГО','','',d.totalIssued,'','','','',''],11);
    }else{
      const r=rows.length+1;rrRow(rows,['В выбранном месяце выдач СИЗ не было'],10);merges.push('A'+r+':I'+r);
    }

    rrMergeRow(rows,merges,sectionNo+'. ОТКЛОНЕНИЯ И КОНТРОЛЬ',3);sectionNo++;
    rrRow(rows,['Тип','Сотрудник','СИЗ','Факт','Норма','Отклонение','Комментарий','',''],4);
    if(d.deviations.length){
      d.deviations.forEach(function(x){
        const st=x[0]==='Просрочка склада'?9:x[0]==='Перерасход'?8:7;
        rrRow(rows,x,st);
      });
    }else{
      const r=rows.length+1;rrRow(rows,['Отклонений за период не зафиксировано'],12);merges.push('A'+r+':I'+r);
    }
  }

  rrMergeRow(rows,merges,sectionNo+'. ВЫДАЧИ ЗА ПЕРИОД',3);sectionNo++;
  rrRow(rows,['Дата','Время','Сотрудник','Ячейка','СИЗ','Было','Выдано','Осталось','Статус'],4);
  if(d.issues.length){
    d.issues.slice().sort(function(a,b){return new Date(a.ts)-new Date(b.ts)}).forEach(function(x){
      const dt=new Date(x.ts);
      rrRow(rows,[reportFmtDate(dt),pad2(dt.getHours())+':'+pad2(dt.getMinutes()),x.userName,'№'+x.cellId,x.ppeName,x.before,Number(x.qty||0),x.after,x.status||'Подтверждено'],7);
    });
  }else{
    const r=rows.length+1;rrRow(rows,['За выбранный период выдач не было'],10);merges.push('A'+r+':I'+r);
  }

  rrMergeRow(rows,merges,sectionNo+'. ПОПОЛНЕНИЯ ЗА ПЕРИОД',3);sectionNo++;
  rrRow(rows,['Дата','Время','Склад','Ячейка','Сотрудник','СИЗ','Было','Добавлено','Стало'],4);
  if(d.reps.length){
    d.reps.slice().sort(function(a,b){return new Date(a.ts)-new Date(b.ts)}).forEach(function(x){
      const dt=new Date(x.ts);
      rrRow(rows,[reportFmtDate(dt),pad2(dt.getHours())+':'+pad2(dt.getMinutes()),x.issuedBy||x.userName||'Склад','№'+x.cellId,x.recipientName||ownerOfCell(x.cellId)?.name||'—',x.ppeName,x.before,Number(x.qty||0),x.after],7);
    });
  }else{
    const r=rows.length+1;rrRow(rows,['За выбранный период пополнений не было'],10);merges.push('A'+r+':I'+r);
  }

  if(type==='monthly'){
    rrMergeRow(rows,merges,sectionNo+'. КОРРЕКТИРОВКИ ОСТАТКОВ',3);
    rrRow(rows,['Дата/время','Администратор','Изменение','Было','Стало','','','',''],4);
    if(d.edits.length){
      d.edits.slice().sort(function(a,b){return new Date(a.ts)-new Date(b.ts)}).forEach(function(x){
        rrRow(rows,[reportFmtDateTime(x.ts),x.admin,x.action,x.before,x.after,'','','',''],7);
      });
    }else{
      const r=rows.length+1;rrRow(rows,['Корректировок за период не было'],10);merges.push('A'+r+':I'+r);
    }
  }

  const sheet=styledSheetXml(rows,merges);
  const ct='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>';
  const rels='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>';
  const wb='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Отчёт" sheetId="1" r:id="rId1"/></sheets></workbook>';
  const wbr='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>';
  const styles='<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    +'<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    +'<fonts count="5">'
      +'<font><sz val="10"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="16"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
      +'<font><i/><sz val="10"/><color rgb="FF44546A"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="13"/><color rgb="FF17365D"/><name val="Calibri"/></font>'
    +'</fonts>'
    +'<fills count="9">'
      +'<fill><patternFill patternType="none"/></fill>'
      +'<fill><patternFill patternType="gray125"/></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FF17365D"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFD9EAF7"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FF5B9BD5"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFF7F9FC"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFF4CCCC"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFE2F0D9"/></patternFill></fill>'
    +'</fills>'
    +'<borders count="2"><border/><border>'
      +'<left style="thin"><color rgb="FFD9E1F2"/></left>'
      +'<right style="thin"><color rgb="FFD9E1F2"/></right>'
      +'<top style="thin"><color rgb="FFD9E1F2"/></top>'
      +'<bottom style="thin"><color rgb="FFD9E1F2"/></bottom>'
    +'</border></borders>'
    +'<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
    +'<cellXfs count="13">'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
      +'<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="3" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="4" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="6" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="7" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="2" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="4" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="8" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
    +'</cellXfs>'
    +'</styleSheet>';

  return zipStore([
    {name:'[Content_Types].xml',text:ct},
    {name:'_rels/.rels',text:rels},
    {name:'xl/workbook.xml',text:wb},
    {name:'xl/_rels/workbook.xml.rels',text:wbr},
    {name:'xl/styles.xml',text:styles},
    {name:'xl/worksheets/sheet1.xml',text:sheet}
  ]);
};

const __reportPreviewHtmlV327=reportPreviewHtml;
reportPreviewHtml=function(type,key){
  const base=__reportPreviewHtmlV327(type,key);
  if(type!=='monthly')return base;

  const d=calcReportData(type,key);
  const issuedRows=rrMonthlyIssuedByEmployeePpe(d);
  const body=issuedRows.length
    ?issuedRows.map(function(x){
      return '<tr><td>'+esc(x.employee)+'</td><td>'+esc(x.cell)+'</td><td>'+esc(x.ppe)+'</td><td>'+x.qty+'</td><td>'+x.count+'</td><td>'+esc(x.first)+'</td><td>'+esc(x.last)+'</td></tr>';
    }).join('')
    :'<tr><td colspan="7">В выбранном месяце выдач СИЗ не было</td></tr>';

  const section='<div class="reportSection">3. ВЫДАНО СИЗ СОТРУДНИКАМ ЗА МЕСЯЦ</div>'
    +'<div class="reportTableWrap"><table class="reportTable"><thead><tr>'
    +'<th>Сотрудник</th><th>Ячейка</th><th>СИЗ</th><th>Выдано, шт.</th><th>Кол-во выдач</th><th>Первая выдача</th><th>Последняя выдача</th>'
    +'</tr></thead><tbody>'+body
    +(issuedRows.length?'<tr><td><b>ИТОГО</b></td><td></td><td></td><td><b>'+d.totalIssued+'</b></td><td></td><td></td><td></td></tr>':'')
    +'</tbody></table></div>';

  const startMarker='<div class="reportSection">3. ВЫДАЧА ПО СОТРУДНИКАМ</div>';
  const endMarker='<div class="reportSection">4. ОТКЛОНЕНИЯ И КОНТРОЛЬ</div>';
  const start=base.indexOf(startMarker);
  const end=base.indexOf(endMarker);
  if(start>=0&&end>start)return base.slice(0,start)+section+base.slice(end);
  return base+section;
};

""";
    }


    private String replenishmentDataFixPatchScript() {
        return """

// ===== v3.29 replenishment data consistency =====
// Canonical warehouse queue = union of OPEN replenishment tasks and active assignments
// currently at/below Min. This preserves partially completed OPEN tasks until Max and
// also protects the queue/report if a task record is missing or stale.
function warehouseNeedTasksV329(){
  ensureWarehouseData();
  try{syncTasks()}catch(e){console.error('syncTasks v3.29',e)}

  const byAssignment=new Map();

  try{
    openTasks().forEach(function(t){
      if(!t||!t.a||!t.a.active)return;
      const stock=Number(t.a.stock||0),target=Number(t.a.target||0);
      if(stock>=target)return;
      byAssignment.set(String(t.a.id),t);
    });
  }catch(e){console.error('openTasks v3.29',e)}

  (db.assignments||[]).filter(function(a){
    return a&&a.active&&Number(a.stock||0)<=Number(a.min||0)&&Number(a.stock||0)<Number(a.target||0);
  }).forEach(function(a){
    const key=String(a.id);
    if(byAssignment.has(key))return;
    const existing=(db.tasks||[]).find(function(t){
      return t.assignmentId===a.id&&t.status==='OPEN';
    });
    byAssignment.set(key,existing?Object.assign({},existing,{a:a}):{
      id:'AUTO-'+a.id,
      assignmentId:a.id,
      cellId:a.cellId,
      ppeId:a.ppeId,
      created:nowIso(),
      due:hoursFromNow(Number(a.stock||0)===0?db.settings.criticalHours:db.settings.normalHours),
      status:'OPEN',
      a:a
    });
  });

  return Array.from(byAssignment.values());
}

whQueueGroups=function(){
  const tasks=warehouseNeedTasksV329();
  const map=new Map();
  tasks.forEach(function(t){
    const cid=Number(t.cellId!=null?t.cellId:t.a.cellId);
    if(!map.has(cid))map.set(cid,[]);
    map.get(cid).push(t);
  });
  return Array.from(map.entries())
    .sort(function(a,b){return a[0]-b[0]})
    .map(function(x){return {cellId:x[0],tasks:x[1]};});
};

routeTasks=function(){
  const cid=currentRouteCell();
  if(cid==null)return [];
  const g=whQueueGroups().find(function(x){return Number(x.cellId)===Number(cid)});
  return g?g.tasks:[];
};

// Monthly report should default to the month in which the user is working.
// Keep the selected month when the user has explicitly chosen one.
const __showAdminV329=showAdmin;
showAdmin=function(tab='overview',push=true){
  if(tab==='reports'&&!window.__reportMonth){
    window.__reportMonth=monthKey(new Date().getFullYear(),new Date().getMonth()+1);
  }
  if(tab==='reports'&&!window.__reportWeek){
    window.__reportWeek=weekKeyFromStart(weekBoundsFromDate(new Date()).start);
  }
  __showAdminV329(tab,push);
};

""";
    }


    private String monthlyMovementPreviewPatchScript() {
        return """

// ===== v3.30 monthly movement preview: period data only =====
const __reportPreviewHtmlV330=reportPreviewHtml;
reportPreviewHtml=function(type,key){
  const base=__reportPreviewHtmlV330(type,key);
  if(type!=='monthly')return base;

  const d=calcReportData(type,key);
  const names=new Set([].concat(Object.keys(d.byPpe),Object.keys(d.replByPpe)));
  const rows=[];
  Array.from(names).sort(function(a,b){return a.localeCompare(b,'ru')}).forEach(function(name){
    const stat=d.byPpe[name]||{q:0,count:0,people:new Set()};
    const issued=Number(stat.q||0);
    const repl=Number(d.replByPpe[name]||0);
    if(issued>0||repl>0){
      rows.push('<tr><td>'+esc(name)+'</td><td>'+issued+'</td><td>'+repl+'</td><td>'+(repl-issued)+'</td><td>'+Number(stat.count||0)+'</td><td>'+stat.people.size+'</td></tr>');
    }
  });

  const movement='<div class="reportSection">2. ДВИЖЕНИЕ СИЗ ЗА МЕСЯЦ</div>'
    +'<div class="reportTableWrap"><table class="reportTable"><thead><tr>'
    +'<th>СИЗ</th><th>Выдано, шт.</th><th>Пополнено, шт.</th><th>Баланс движения</th><th>Кол-во выдач</th><th>Сотрудников</th>'
    +'</tr></thead><tbody>'
    +(rows.length?rows.join('')+'<tr><td><b>ИТОГО</b></td><td><b>'+d.totalIssued+'</b></td><td><b>'+d.totalRepl+'</b></td><td><b>'+(d.totalRepl-d.totalIssued)+'</b></td><td></td><td></td></tr>':'<tr><td colspan="6">За выбранный месяц выдач и пополнений не было</td></tr>')
    +'</tbody></table></div>';

  const startMarker='<div class="reportSection">2. РАСХОД ПО ВИДАМ СИЗ</div>';
  const endMarker='<div class="reportSection">3. ВЫДАНО СИЗ СОТРУДНИКАМ ЗА МЕСЯЦ</div>';
  const start=base.indexOf(startMarker);
  const end=base.indexOf(endMarker);
  if(start>=0&&end>start)return base.slice(0,start)+movement+base.slice(end);
  return base;
};

""";
    }


    private String weeklyReportPreviewPatchScript() {
        return """

// ===== v3.31 weekly preview: current week + period-only movement =====
const __reportPreviewHtmlV331=reportPreviewHtml;
reportPreviewHtml=function(type,key){
  const base=__reportPreviewHtmlV331(type,key);
  if(type!=='weekly')return base;

  const d=calcReportData(type,key);
  const names=new Set([].concat(Object.keys(d.byPpe),Object.keys(d.replByPpe)));
  const movementRows=[];
  Array.from(names).sort(function(a,b){return a.localeCompare(b,'ru')}).forEach(function(name){
    const stat=d.byPpe[name]||{q:0,count:0,people:new Set()};
    const issued=Number(stat.q||0);
    const repl=Number(d.replByPpe[name]||0);
    if(issued>0||repl>0){
      movementRows.push('<tr><td>'+esc(name)+'</td><td>'+issued+'</td><td>'+repl+'</td><td>'+(repl-issued)+'</td><td>'+Number(stat.count||0)+'</td><td>'+stat.people.size+'</td></tr>');
    }
  });

  const issuedRows=rrMonthlyIssuedByEmployeePpe(d);
  const issuedBody=issuedRows.length
    ?issuedRows.map(function(x){
      return '<tr><td>'+esc(x.employee)+'</td><td>'+esc(x.cell)+'</td><td>'+esc(x.ppe)+'</td><td>'+x.qty+'</td><td>'+x.count+'</td><td>'+esc(x.first)+'</td><td>'+esc(x.last)+'</td></tr>';
    }).join('')
    :'<tr><td colspan="7">В выбранной неделе выдач СИЗ не было</td></tr>';

  const movement='<div class="reportSection">2. ДВИЖЕНИЕ СИЗ ЗА НЕДЕЛЮ</div>'
    +'<div class="reportTableWrap"><table class="reportTable"><thead><tr>'
    +'<th>СИЗ</th><th>Выдано, шт.</th><th>Пополнено, шт.</th><th>Баланс движения</th><th>Кол-во выдач</th><th>Сотрудников</th>'
    +'</tr></thead><tbody>'
    +(movementRows.length?movementRows.join('')+'<tr><td><b>ИТОГО</b></td><td><b>'+d.totalIssued+'</b></td><td><b>'+d.totalRepl+'</b></td><td><b>'+(d.totalRepl-d.totalIssued)+'</b></td><td></td><td></td></tr>':'<tr><td colspan="6">За выбранную неделю выдач и пополнений не было</td></tr>')
    +'</tbody></table></div>';

  const issued='<div class="reportSection">3. ВЫДАНО СИЗ СОТРУДНИКАМ ЗА НЕДЕЛЮ</div>'
    +'<div class="reportTableWrap"><table class="reportTable"><thead><tr>'
    +'<th>Сотрудник</th><th>Ячейка</th><th>СИЗ</th><th>Выдано, шт.</th><th>Кол-во выдач</th><th>Первая выдача</th><th>Последняя выдача</th>'
    +'</tr></thead><tbody>'+issuedBody
    +(issuedRows.length?'<tr><td><b>ИТОГО</b></td><td></td><td></td><td><b>'+d.totalIssued+'</b></td><td></td><td></td><td></td></tr>':'')
    +'</tbody></table></div>';

  const startMarker='<div class="reportSection">2. РАСХОД ПО ВИДАМ СИЗ</div>';
  const attentionMarker='<div class="reportSection">3. ТРЕБУЕТ ВНИМАНИЯ</div>';
  const start=base.indexOf(startMarker);
  const att=base.indexOf(attentionMarker);
  if(start>=0&&att>start){
    let rest=base.slice(att);
    rest=rest
      .replace('<div class="reportSection">3. ТРЕБУЕТ ВНИМАНИЯ</div>','<div class="reportSection">4. ТРЕБУЕТ ВНИМАНИЯ</div>')
      .replace('<div class="reportSection">4. ДЕТАЛИЗАЦИЯ ВЫДАЧ</div>','<div class="reportSection">5. ДЕТАЛИЗАЦИЯ ВЫДАЧ</div>')
      .replace('<div class="reportSection">5. ПОПОЛНЕНИЯ</div>','<div class="reportSection">6. ПОПОЛНЕНИЯ</div>');
    return base.slice(0,start)+movement+issued+rest;
  }
  return base;
};

""";
    }


    private String simpleIssueReportsPatchScript() {
        return """

// ===== v3.32 simple weekly/monthly issue reports =====
function simpleIssueReportTitle(type){
  return type==='weekly'?'ЕЖЕНЕДЕЛЬНЫЙ ОТЧЁТ ПО ВЫДАЧЕ СИЗ':'ЕЖЕМЕСЯЧНЫЙ ОТЧЁТ ПО ВЫДАЧЕ СИЗ';
}

function simpleIssueRows(type,key){
  const d=calcReportData(type,key);
  const p=periodData(type,key);
  const source=Array.isArray(db.replenishLog)?db.replenishLog:[];
  const rows=source.filter(function(x){
    const raw=x.ts||x.createdAt||x.dateTime||x.date;
    if(!raw)return false;
    const dt=new Date(raw);
    return !isNaN(dt.getTime())&&dt>=p.start&&dt<p.end;
  }).sort(function(a,b){
    return new Date(a.ts||a.createdAt||a.dateTime||a.date)-new Date(b.ts||b.createdAt||b.dateTime||b.date);
  }).map(function(x){
    const raw=x.ts||x.createdAt||x.dateTime||x.date;
    const dt=new Date(raw);
    const cid=x.cellId!=null?x.cellId:'—';
    const cellName=(cell(cid)&&cell(cid).name)?cell(cid).name:(cid==='—'?'—':'№'+cid);
    const qty=Number(x.qty!=null?x.qty:(x.issued!=null?x.issued:x.added)||0);
    const after=Number(x.after!=null?x.after:(x.stockAfter!=null?x.stockAfter:0));
    const owner=ownerOfCell&&cid!=='—'?ownerOfCell(cid):null;
    return {
      date:reportFmtDate(dt),
      time:pad2(dt.getHours())+':'+pad2(dt.getMinutes()),
      cell:cellName,
      ppe:x.ppeName||x.nomenclature||x.ppeId||'—',
      employee:x.recipientName||x.employeeName||(owner&&owner.name)||'—',
      qty:qty
    };
  });
  return {d:d,rows:rows};
}

buildOneSheetXlsx=function(type,key){
  const pack=simpleIssueRows(type,key);
  const d=pack.d,items=pack.rows;
  const totalQty=items.reduce(function(s,x){return s+x.qty},0);
  const rows=[];

  rows.push([{v:simpleIssueReportTitle(type),s:1},{},{},{},{},{}]);
  rows.push([{v:'Период: '+d.p.label+'   •   Сформирован: '+reportFmtDateTime(new Date()),s:2},{},{},{},{},{}]);
  rows.push([{v:'Операций выдачи: '+items.length+'   •   Выдано всего: '+totalQty+' шт.',s:3},{},{},{},{},{}]);
  rows.push([
    {v:'Дата',s:4},
    {v:'Время',s:4},
    {v:'Ячейка',s:4},
    {v:'Номенклатура',s:4},
    {v:'Сотрудник',s:4},
    {v:'Выдано',s:4}
  ]);

  if(items.length){
    items.forEach(function(x){
      rows.push([
        {v:x.date,s:5},
        {v:x.time,s:6},
        {v:x.cell,s:5},
        {v:x.ppe,s:5},
        {v:x.employee,s:5},
        {v:x.qty,s:7}
      ]);
    });
    rows.push([
      {v:'ИТОГО',s:8},
      {v:'',s:8},
      {v:'',s:8},
      {v:'',s:8},
      {v:'',s:8},
      {v:totalQty,s:9}
    ]);
  }else{
    rows.push([{v:'За выбранный период выдач СИЗ не было',s:10},{},{},{},{},{}]);
  }

  let rr='';
  rows.forEach(function(row,ri){
    let cc='';
    for(let ci=0;ci<6;ci++){
      const x=row[ci]||{v:null,s:0};
      cc+=cellXml(x.v,colName(ci+1)+(ri+1),x.s||0);
    }
    let ht='';
    if(ri===0)ht=' ht="30" customHeight="1"';
    else if(ri===3)ht=' ht="28" customHeight="1"';
    rr+='<row r="'+(ri+1)+'"'+ht+'>'+cc+'</row>';
  });

  const merges=items.length
    ?'<mergeCells count="3"><mergeCell ref="A1:F1"/><mergeCell ref="A2:F2"/><mergeCell ref="A3:F3"/></mergeCells>'
    :'<mergeCells count="4"><mergeCell ref="A1:F1"/><mergeCell ref="A2:F2"/><mergeCell ref="A3:F3"/><mergeCell ref="A5:F5"/></mergeCells>';

  const sheet='<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    +'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    +'<sheetViews><sheetView workbookViewId="0"><pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
    +'<cols>'
      +'<col min="1" max="1" width="14" customWidth="1"/>'
      +'<col min="2" max="2" width="10" customWidth="1"/>'
      +'<col min="3" max="3" width="16" customWidth="1"/>'
      +'<col min="4" max="4" width="34" customWidth="1"/>'
      +'<col min="5" max="5" width="28" customWidth="1"/>'
      +'<col min="6" max="6" width="12" customWidth="1"/>'
    +'</cols>'
    +'<sheetData>'+rr+'</sheetData>'+merges
    +'<pageMargins left="0.35" right="0.35" top="0.45" bottom="0.45" header="0.2" footer="0.2"/>'
    +'<pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0" paperSize="9"/>'
    +'</worksheet>';

  const ct='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>';
  const rels='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>';
  const wb='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Выдачи СИЗ" sheetId="1" r:id="rId1"/></sheets></workbook>';
  const wbr='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>';

  const styles='<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    +'<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    +'<fonts count="5">'
      +'<font><sz val="10"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="16"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
      +'<font><i/><sz val="10"/><color rgb="FF44546A"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
      +'<font><b/><sz val="11"/><color rgb="FF17365D"/><name val="Calibri"/></font>'
    +'</fonts>'
    +'<fills count="6">'
      +'<fill><patternFill patternType="none"/></fill>'
      +'<fill><patternFill patternType="gray125"/></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FF17365D"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFD9EAF7"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FF5B9BD5"/></patternFill></fill>'
      +'<fill><patternFill patternType="solid"><fgColor rgb="FFE2F0D9"/></patternFill></fill>'
    +'</fills>'
    +'<borders count="2"><border/><border>'
      +'<left style="thin"><color rgb="FFD9E1F2"/></left><right style="thin"><color rgb="FFD9E1F2"/></right>'
      +'<top style="thin"><color rgb="FFD9E1F2"/></top><bottom style="thin"><color rgb="FFD9E1F2"/></bottom>'
    +'</border></borders>'
    +'<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
    +'<cellXfs count="11">'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
      +'<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
      +'<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="4" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="4" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
      +'<xf numFmtId="0" fontId="2" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>'
    +'</cellXfs>'
    +'</styleSheet>';

  return zipStore([
    {name:'[Content_Types].xml',text:ct},
    {name:'_rels/.rels',text:rels},
    {name:'xl/workbook.xml',text:wb},
    {name:'xl/_rels/workbook.xml.rels',text:wbr},
    {name:'xl/styles.xml',text:styles},
    {name:'xl/worksheets/sheet1.xml',text:sheet}
  ]);
};

reportPreviewHtml=function(type,key){
  const pack=simpleIssueRows(type,key);
  const d=pack.d,items=pack.rows;
  const totalQty=items.reduce(function(s,x){return s+x.qty},0);
  const body=items.length
    ?items.map(function(x){
      return '<tr><td>'+esc(x.date)+'</td><td>'+esc(x.time)+'</td><td>'+esc(x.cell)+'</td><td>'+esc(x.ppe)+'</td><td>'+esc(x.employee)+'</td><td>'+x.qty+'</td></tr>';
    }).join('')
    :'<tr><td colspan="6">За выбранный период выдач СИЗ не было</td></tr>';

  return '<div class="reportSheet">'
    +'<div class="reportTitle">'+esc(simpleIssueReportTitle(type))+'</div>'
    +'<div class="reportSub">Период: '+esc(d.p.label)+' • Сформирован: '+esc(reportFmtDateTime(new Date()))+'</div>'
    +'<div class="reportSection">Операций выдачи: '+items.length+' • Выдано всего: '+totalQty+' шт.</div>'
    +'<div class="reportTableWrap"><table class="reportTable"><thead><tr>'
    +'<th>Дата</th><th>Время</th><th>Ячейка</th><th>Номенклатура</th><th>Сотрудник</th><th>Выдано</th>'
    +'</tr></thead><tbody>'+body
    +(items.length?'<tr><td><b>ИТОГО</b></td><td></td><td></td><td></td><td></td><td><b>'+totalQty+'</b></td></tr>':'')
    +'</tbody></table></div>'
    +'</div>';
};

""";
    }


    private String issueLogFixPatchScript() {
        return """

// ===== v3.34 guaranteed issue logging =====
confirmIssue=function(){
  if(!session.flow||!session.flow.closed){
    toast('Сначала закройте ячейку','error');
    return;
  }

  updateIssueSummary();
  const selected=Object.entries(issueSelection)
    .map(function(x){
      const a=db.assignments.find(function(z){return z.id===x[0]});
      return {a:a,q:Math.max(0,Number(x[1]||0))};
    })
    .filter(function(x){return x.a&&x.q>0});

  if(!selected.length){
    toast('Не выбраны СИЗ для выдачи','error');
    return;
  }

  for(const x of selected){
    if(x.q>Number(x.a.stock||0)){
      toast('Остаток изменился. Обновите экран и повторите выдачу.','error');
      return;
    }
  }

  const rows=selected.map(function(x){
    return esc(ppe(x.a.ppeId)?ppe(x.a.ppeId).name:x.a.ppeId)+' — <b>'+x.q+'</b>';
  }).join('<br>');

  confirmModal('Подтверждение выдачи',rows,'ПОДТВЕРДИТЬ',function(){
    const tx=uid('ISS');
    const ts=nowIso();
    let logged=0;

    selected.forEach(function(x){
      const a=x.a,q=x.q;
      const before=Number(a.stock||0);
      const after=Math.max(0,before-q);
      a.stock=after;

      db.issueLog.unshift({
        id:uid('L'),
        tx:tx,
        ts:ts,
        userId:session.user.id,
        userName:session.user.name,
        cellId:a.cellId,
        ppeId:a.ppeId,
        ppeName:ppe(a.ppeId)?ppe(a.ppeId).name:a.ppeId,
        before:before,
        qty:q,
        after:after,
        status:'Подтверждено'
      });
      logged++;
    });

    // Сначала фиксируем выдачу в БД, затем создаём/обновляем задания склада.
    saveDb();
    syncTasks();
    saveDb();

    session.flow=null;
    issueSelection={};

    if(logged>0){
      toast('Выдача зарегистрирована: '+logged+' поз.','ok');
    }else{
      toast('Ошибка: выдача не записана в журнал','error');
    }
    showOperator();
  });
};

""";
    }


    private String initialCatalogPatchScript() {
        return """

// ===== v3.37 initial structure: 20 cells + employee catalog =====
(function seedInitialCatalogV337(){
  try{
    if(typeof db==='undefined'||!db)return;
    db.settings=db.settings||{};
    if(db.settings.initialCatalogV337)return;

    const employeeNames=[
      'Власов Р.В.',
      'Дроздов Д.В.',
      'Жилко В.А.',
      'Иссоев Г.А.',
      'Комарницкий А.В.',
      'Лапик В.С.',
      'Львов П.Г.',
      'Михайлов А.В.',
      'Орехов Р.С.',
      'Погорельский А.Ю.',
      'Сироткин А.В.',
      'Служба механиков',
      'Филатов А.С.',
      'Фитисов М.А.',
      'Черников Ю.А.',
      'Шашин И.В.'
    ];

    db.cells=Array.isArray(db.cells)?db.cells:[];
    db.sim=db.sim||{};
    for(let id=1;id<=20;id++){
      let c=db.cells.find(function(x){return Number(x.id)===id});
      if(!c){
        c={id:id,name:'Ячейка №'+id,type:'INDIVIDUAL',active:true};
        db.cells.push(c);
      }else{
        if(!c.name)c.name='Ячейка №'+id;
        if(!c.type)c.type='INDIVIDUAL';
        c.active=true;
      }
      if(db.sim[id]==null)db.sim[id]='CLOSED';
    }
    db.cells.sort(function(a,b){return Number(a.id||0)-Number(b.id||0)});

    db.employees=Array.isArray(db.employees)?db.employees:[];

    // Старого тестового пользователя превращаем в пользователя из фактического списка,
    // чтобы не оставлять дубликат "Погорельский А.".
    let pog=db.employees.find(function(e){
      const n=String(e.name||'').trim();
      return e.id!=='WH'&&(n==='Погорельский А.'||n==='Погорельский А');
    });
    let pogTarget=db.employees.find(function(e){return String(e.name||'').trim()==='Погорельский А.Ю.'});
    if(pog&&!pogTarget){
      pog.name='Погорельский А.Ю.';
      pog.pin=String(pog.pin||'0000');
      pog.active=true;
      pog.cellId=null;
      pogTarget=pog;
    }

    const seededIds=[];
    employeeNames.forEach(function(name,idx){
      let e=db.employees.find(function(x){return String(x.name||'').trim()===name});
      if(!e){
        let id='SEED_EMP_'+String(idx+1).padStart(2,'0');
        while(db.employees.some(function(x){return x.id===id}))id=id+'_N';
        e={id:id,name:name,pin:'0000',cellId:null,active:true,role:'OPERATOR'};
        db.employees.push(e);
      }
      e.name=name;
      e.active=true;
      if(!String(e.pin||'').trim())e.pin='0000';
      e.cellId=null;
      if(!e.role)e.role='OPERATOR';
      seededIds.push(e.id);
    });

    // По условию первоначального каталога пользователи пока не связаны с ячейками.
    // Существующие тестовые назначения этих пользователей деактивируем один раз.
    db.assignments=Array.isArray(db.assignments)?db.assignments:[];
    db.assignments.forEach(function(a){
      if(seededIds.indexOf(a.employeeId)>=0)a.active=false;
    });

    // Убираем остаточный старый демо-профиль, если он не был преобразован выше.
    db.employees=db.employees.filter(function(e){
      const n=String(e.name||'').trim();
      if(e.id==='WH')return true;
      return n!=='Погорельский А.'&&n!=='Погорельский А';
    });

    // Фактические сотрудники идут по алфавиту; технические учётные записи остаются после них.
    const order=new Map(employeeNames.map(function(n,i){return [n,i]}));
    db.employees.sort(function(a,b){
      const ai=order.has(String(a.name||'').trim())?order.get(String(a.name||'').trim()):9999;
      const bi=order.has(String(b.name||'').trim())?order.get(String(b.name||'').trim()):9999;
      if(ai!==bi)return ai-bi;
      return String(a.name||'').localeCompare(String(b.name||''),'ru');
    });

    db.settings.initialCatalogV337=true;
    if(typeof saveDb==='function')saveDb();
    else if(typeof save==='function')save();
  }catch(e){
    console.error('initialCatalogV337',e);
  }
})();

""";
    }


    private String warehouseReportsPatchScript() {
        return """

// ===== v3.38 reports available in warehouse role =====
const __workspaceV338=workspace;
workspace=function(role,active,body){
  if(role!=='WAREHOUSE')return __workspaceV338(role,active,body);
  const nav=[
    ['replenish','Восполнение'],
    ['revision','Ревизия'],
    ['history','История'],
    ['reports','Отчёты']
  ];
  return '<div class="workspace"><aside class="sidebar">'
    +'<div class="profile"><b>'+esc(session.user&&session.user.name?session.user.name:'')+'</b><span>Склад</span></div>'
    +nav.map(function(n){return '<button class="navbtn '+(n[0]===active?'active':'')+'" data-nav="'+n[0]+'">'+n[1]+'</button>';}).join('')
    +'</aside><main class="content">'+body+'</main></div>';
};

function warehouseReportsBody(){
  ensureArchive();
  ensureWarehouseData();

  const months=reportMonths();
  const weeks=reportWeeks();
  const currentMonth=monthKey(new Date().getFullYear(),new Date().getMonth()+1);
  const currentWeek=weekKeyFromStart(weekBoundsFromDate(new Date()).start);
  const mSel=window.__whReportMonth||currentMonth;
  const wSel=window.__whReportWeek||currentWeek;
  const mail=String(db.settings.warehouseReportEmail||'');

  const monthOptions=months.map(function(k){
    return '<option value="'+esc(k)+'" '+(k===mSel?'selected':'')+'>'+esc(k)+'</option>';
  }).join('');
  const weekOptions=weeks.map(function(k){
    return '<option value="'+esc(k)+'" '+(k===wSel?'selected':'')+'>'+esc(weekLabel(k))+'</option>';
  }).join('');

  const reports=(db.archive&&Array.isArray(db.archive.reports)?db.archive.reports:[])
    .slice()
    .sort(function(a,b){return new Date(b.createdAt)-new Date(a.createdAt);})
    .slice(0,10)
    .map(function(r){
      const title=(r.type==='weekly'?'Еженедельный':'Ежемесячный')+' • '+esc(r.label||r.key||'');
      return '<button class="whReportArchiveItem" data-wh-report-type="'+esc(r.type||'weekly')+'" data-wh-report-key="'+esc(r.key||'')+'">'
        +'<div><b>'+title+'</b><span>'+esc(fmtDate(r.createdAt))+'</span></div>'
        +'<span class="whReportOpen">›</span>'
        +'</button>';
    }).join('');

  let h='';
  h+='<style>';
  h+='.whReportsHero{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:16px}';
  h+='.whReportsHero .h1{margin-bottom:4px}';
  h+='.whReportsGrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-bottom:18px}';
  h+='.whReportCard{background:#fff;border:1px solid var(--line);border-radius:16px;padding:16px;box-shadow:0 4px 14px rgba(20,45,80,.04)}';
  h+='.whReportCard h3{margin:0 0 5px;font-size:18px}.whReportCard p{margin:0 0 14px;color:var(--muted);font-size:12px;line-height:1.45}';
  h+='.whReportActions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}';
  h+='.whReportMail{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;align-items:end;margin-top:12px}.whReportMail .field{margin:0}';
  h+='.whReportInfo{background:#eef5ff;border:1px solid #cfe0f5;border-radius:14px;padding:12px 14px;margin-bottom:14px;font-size:12px;color:#35506f}';
  h+='.whPreviewBox{margin-top:16px}';
  h+='.whReportArchive{display:grid;gap:8px}';
  h+='.whReportArchiveItem{width:100%;display:flex;align-items:center;justify-content:space-between;gap:12px;text-align:left;background:#fff;border:1px solid var(--line);border-radius:13px;padding:12px 14px;color:inherit;font:inherit;cursor:pointer}';
  h+='.whReportArchiveItem b{display:block;font-size:13px}.whReportArchiveItem span{display:block;font-size:11px;color:var(--muted);margin-top:3px}.whReportOpen{font-size:24px!important;margin:0!important}';
  h+='@media(max-width:700px){.whReportsGrid{grid-template-columns:1fr}.whReportMail{grid-template-columns:1fr}.whReportActions{grid-template-columns:1fr}}';
  h+='</style>';

  h+='<div class="whReportsHero"><div><div class="h1">Отчёты</div><p>Выдача расходных материалов складом сотрудникам.</p></div></div>';
  h+='<div class="whReportInfo">Отчёты формируются по фактическим операциям склада. Быстрый <b>«Отчёт о восполнении»</b> остаётся в разделе «Восполнение».</div>';

  h+='<div class="whReportsGrid">';
  h+='<div class="whReportCard">';
  h+='<h3>Еженедельный отчёт</h3><p>Дата, время, ячейка, номенклатура, сотрудник и количество выданного за выбранную неделю.</p>';
  h+='<div class="field"><label>Неделя</label><select id="whReportWeek" class="select">'+weekOptions+'</select></div>';
  h+='<div class="whReportActions"><button id="whPreviewWeek" class="btn outline block">ПРЕДПРОСМОТР</button><button id="whMakeWeek" class="btn primary block">СФОРМИРОВАТЬ</button></div>';
  h+='<div class="whReportMail"><div class="field"><label>Email получателя</label><input id="whWeekEmail" class="input" data-vk="latin" inputmode="email" value="'+esc(mail)+'" autocomplete="off"></div><button id="whSendWeek" class="btn green">ОТПРАВИТЬ</button></div>';
  h+='</div>';

  h+='<div class="whReportCard">';
  h+='<h3>Ежемесячный отчёт</h3><p>Та же форма отчёта, но за выбранный календарный месяц.</p>';
  h+='<div class="field"><label>Месяц</label><select id="whReportMonth" class="select">'+monthOptions+'</select></div>';
  h+='<div class="whReportActions"><button id="whPreviewMonth" class="btn outline block">ПРЕДПРОСМОТР</button><button id="whMakeMonth" class="btn primary block">СФОРМИРОВАТЬ</button></div>';
  h+='<div class="whReportMail"><div class="field"><label>Email получателя</label><input id="whMonthEmail" class="input" data-vk="latin" inputmode="email" value="'+esc(mail)+'" autocomplete="off"></div><button id="whSendMonth" class="btn green">ОТПРАВИТЬ</button></div>';
  h+='</div>';
  h+='</div>';

  h+='<div id="whReportPreview" class="whPreviewBox"></div>';
  h+='<div class="sectionLabel">Последние сформированные отчёты</div>';
  h+='<div class="whReportArchive">'+(reports||'<div class="empty">Сформированных отчётов пока нет</div>')+'</div>';
  return h;
}

function whShowReportPreview(type,key){
  const box=byId('whReportPreview');
  if(!box)return;
  try{
    box.innerHTML=reportPreviewHtml(type,key);
    box.scrollIntoView({behavior:'smooth',block:'start'});
  }catch(e){
    console.error('warehouse report preview',e);
    toast('Не удалось открыть предпросмотр отчёта','error');
  }
}

function wireWarehouseReports(){
  const week=byId('whReportWeek'),month=byId('whReportMonth');
  if(week)week.onchange=function(){window.__whReportWeek=week.value;};
  if(month)month.onchange=function(){window.__whReportMonth=month.value;};

  if(byId('whPreviewWeek'))byId('whPreviewWeek').onclick=function(){
    const key=byId('whReportWeek').value;
    window.__whReportWeek=key;
    whShowReportPreview('weekly',key);
  };
  if(byId('whPreviewMonth'))byId('whPreviewMonth').onclick=function(){
    const key=byId('whReportMonth').value;
    window.__whReportMonth=key;
    whShowReportPreview('monthly',key);
  };

  if(byId('whMakeWeek'))byId('whMakeWeek').onclick=function(){
    const key=byId('whReportWeek').value;
    window.__whReportWeek=key;
    if(!nativeStorageAvailable()){
      toast('Сначала настройте папку хранения в Администрирование → Отчёты','error');
      return;
    }
    saveWeeklyReport(key,false);
    toast('Еженедельный отчёт сформирован','ok');
    showWarehouse('reports',false);
  };
  if(byId('whMakeMonth'))byId('whMakeMonth').onclick=function(){
    const key=byId('whReportMonth').value;
    window.__whReportMonth=key;
    if(!nativeStorageAvailable()){
      toast('Сначала настройте папку хранения в Администрирование → Отчёты','error');
      return;
    }
    saveMonthlyReport(key,false);
    toast('Ежемесячный отчёт сформирован','ok');
    showWarehouse('reports',false);
  };

  if(byId('whSendWeek'))byId('whSendWeek').onclick=function(){
    const key=byId('whReportWeek').value;
    const email=String(byId('whWeekEmail').value||'').trim();
    sendReportByEmail('weekly',key,email);
  };
  if(byId('whSendMonth'))byId('whSendMonth').onclick=function(){
    const key=byId('whReportMonth').value;
    const email=String(byId('whMonthEmail').value||'').trim();
    sendReportByEmail('monthly',key,email);
  };

  document.querySelectorAll('[data-wh-report-type]').forEach(function(b){
    b.onclick=function(){whShowReportPreview(b.dataset.whReportType,b.dataset.whReportKey);};
  });

  enableNativeMobileKeyboard(document);
}

const __showWarehouseV338=showWarehouse;
showWarehouse=function(tab='replenish',push=true){
  if(tab!=='reports')return __showWarehouseV338(tab,push);

  ensureWarehouseData();
  ensureArchive();
  setUi({screen:'warehouse',role:'WAREHOUSE',tab:'reports'},push);
  render(workspace('WAREHOUSE','reports',warehouseReportsBody()));
  wireNav('WAREHOUSE');
  wireWarehouseReports();
  try{applyWarehouseNavMode('reports');}catch(e){}
  setTimeout(function(){try{applyWarehouseNavMode('reports');}catch(e){}},0);
};

""";
    }


    private String operatorUserGridPatchScript() {
        return """

// ===== v3.42 20 employees visible at once: 5 x 4 =====
function applyOperatorUserGrid(){
  const grid=document.querySelector('.userGrid');
  if(!grid)return;

  const loginBox=grid.closest('.loginBox');
  if(loginBox)loginBox.classList.add('operatorLoginCompactV342');

  let style=byId('operatorUserGridStyleV342');
  if(!style){
    style=document.createElement('style');
    style.id='operatorUserGridStyleV342';
    style.textContent=
      '.operatorLoginCompactV342{width:min(1080px,100%)!important;max-width:1080px!important;margin:0 auto!important;}'
      +'.operatorLoginCompactV342 .contentHead{margin-bottom:5px!important;}'
      +'.operatorLoginCompactV342 .contentHead .h1{font-size:23px!important;margin-bottom:3px!important;}'
      +'.operatorLoginCompactV342 .contentHead p{font-size:11px!important;line-height:1.25!important;margin:0!important;}'
      +'.operatorLoginCompactV342 .right .btn{height:38px!important;padding:0 12px!important;}'
      +'.userGrid.operatorUserGridV342{display:grid!important;grid-template-columns:repeat(5,minmax(0,1fr))!important;gap:7px!important;margin:8px 0 8px!important;}'
      +'.userGrid.operatorUserGridV342 .userCard{display:flex!important;width:100%!important;height:72px!important;min-height:72px!important;max-height:72px!important;margin:0!important;padding:6px 7px!important;border:1px solid #d8e0e9!important;border-radius:11px!important;background:#fff!important;color:#1c2a3a!important;align-items:center!important;justify-content:center!important;text-align:center!important;box-shadow:0 2px 7px rgba(20,45,80,.035)!important;overflow:hidden!important;}'
      +'.userGrid.operatorUserGridV342 .userCard b{font-size:12px!important;line-height:1.18!important;font-weight:800!important;display:-webkit-box!important;-webkit-line-clamp:3!important;-webkit-box-orient:vertical!important;overflow:hidden!important;word-break:break-word!important;}'
      +'.userGrid.operatorUserGridV342 .userCard span{display:none!important;}'
      +'.userGrid.operatorUserGridV342 .userCard.selected{border:2px solid #1263b6!important;background:#eef6ff!important;box-shadow:0 0 0 1px rgba(18,99,182,.08)!important;}'
      +'.userGrid.operatorUserGridV342 .userCard:active{transform:scale(.98)!important;}'
      +'.operatorLoginCompactV342 .pinLoginPanel{display:grid!important;grid-template-columns:minmax(0,1fr) 255px!important;gap:8px!important;margin-top:6px!important;align-items:stretch!important;}'
      +'.operatorLoginCompactV342 .pinInfo{padding:10px 12px!important;}'
      +'.operatorLoginCompactV342 .pinSelected{font-size:16px!important;margin-bottom:3px!important;}'
      +'.operatorLoginCompactV342 .pinHint{font-size:10px!important;line-height:1.25!important;}'
      +'.operatorLoginCompactV342 .pinDisplay{height:42px!important;margin-top:7px!important;font-size:23px!important;letter-spacing:9px!important;border-radius:10px!important;}'
      +'.operatorLoginCompactV342 .pinCounter{font-size:10px!important;margin-top:3px!important;}'
      +'.operatorLoginCompactV342 .pinLoginButton{height:40px!important;margin-top:6px!important;font-size:12px!important;}'
      +'.operatorLoginCompactV342 .pinPad{padding:7px!important;gap:5px!important;}'
      +'.operatorLoginCompactV342 .pinKey{height:34px!important;border-radius:8px!important;font-size:16px!important;}'
      +'.operatorLoginCompactV342 .pinKey.action,.operatorLoginCompactV342 .pinKey.clear{font-size:10px!important;}'
      +'@media(max-width:700px){.userGrid.operatorUserGridV342{grid-template-columns:repeat(4,minmax(0,1fr))!important}.userGrid.operatorUserGridV342 .userCard{height:68px!important;min-height:68px!important;max-height:68px!important}.operatorLoginCompactV342 .pinLoginPanel{grid-template-columns:1fr 220px!important}}'
      +'@media(max-width:540px){.userGrid.operatorUserGridV342{grid-template-columns:repeat(3,minmax(0,1fr))!important}.operatorLoginCompactV342 .pinLoginPanel{grid-template-columns:1fr!important}}';
    document.head.appendChild(style);
  }

  const oldGrid=byId('operatorUserGridV339');
  if(oldGrid)oldGrid.remove();
  document.querySelectorAll('.operatorUserGridHintV339').forEach(function(x){x.remove();});

  grid.classList.remove('operatorUserGridV341');
  grid.classList.add('operatorUserGridV342');
  grid.style.display='grid';

  Array.from(grid.querySelectorAll('.userCard')).forEach(function(card){
    card.style.display='flex';
    if(card.parentElement&&card.parentElement!==grid)card.parentElement.style.display='';
  });
}

const __showUserLoginV342=showUserLogin;
showUserLogin=function(role,push=true){
  __showUserLoginV342(role,push);
  if(role==='OPERATOR')applyOperatorUserGrid();
};

""";
    }


    private String operatorPinSelectionPatchScript() {
        return """

// ===== v3.43 explicit employee selection + immediate PIN entry =====
function decorateOperatorPinSelectionV343(){
  const grid=document.querySelector('.userGrid');
  const panel=document.querySelector('.pinLoginPanel');
  const selectedName=byId('selectedName');
  const counter=byId('pinCounter');
  if(!grid||!panel||!selectedName)return;

  let style=byId('operatorPinSelectionStyleV343');
  if(!style){
    style=document.createElement('style');
    style.id='operatorPinSelectionStyleV343';
    style.textContent=
      '.pinLoginPanel.operatorPinAwaitV343{opacity:.62;transition:.16s;}'
      +'.pinLoginPanel.operatorPinReadyV343{opacity:1;transition:.16s;}'
      +'.operatorPinPromptV343{font-size:10px;color:#6b7788;margin-top:3px;line-height:1.25;}'
      +'.userGrid .userCard.operatorChosenV343{border:2px solid #1263b6!important;background:#eaf4ff!important;box-shadow:0 0 0 2px rgba(18,99,182,.10)!important;}'
      +'.userGrid .userCard.operatorChosenV343:after{content:"✓";position:absolute;right:7px;top:5px;width:20px;height:20px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#1263b6;color:#fff;font-size:12px;font-weight:900;}'
      +'.userGrid .userCard{position:relative!important;}';
    document.head.appendChild(style);
  }

  let prompt=byId('operatorPinPromptV343');
  if(!prompt){
    prompt=document.createElement('div');
    prompt.id='operatorPinPromptV343';
    prompt.className='operatorPinPromptV343';
    selectedName.insertAdjacentElement('afterend',prompt);
  }

  function sync(){
    const chosen=grid.querySelector('.userCard.selected');
    grid.querySelectorAll('.userCard').forEach(function(card){
      card.classList.toggle('operatorChosenV343',card===chosen);
    });

    if(chosen){
      panel.classList.remove('operatorPinAwaitV343');
      panel.classList.add('operatorPinReadyV343');
      const fio=String(chosen.querySelector('b')?chosen.querySelector('b').textContent:'').trim();
      selectedName.textContent='Введите PIN для: '+fio;
      prompt.textContent='Ошиблись? Просто выберите другого сотрудника — введённый PIN очистится.';
      if(counter&&String(counter.textContent||'').indexOf('Сначала')>=0)counter.textContent='0 цифр';
    }else{
      panel.classList.add('operatorPinAwaitV343');
      panel.classList.remove('operatorPinReadyV343');
      selectedName.textContent='Сначала выберите сотрудника';
      prompt.textContent='После выбора фамилии PIN-панель сразу станет активной.';
    }
  }

  grid.querySelectorAll('.userCard').forEach(function(card){
    card.addEventListener('click',function(){
      // Штатный onclick уже переключает выбранного пользователя и очищает PIN.
      setTimeout(sync,0);
    });
  });

  sync();
}

const __showUserLoginV343=showUserLogin;
showUserLogin=function(role,push=true){
  __showUserLoginV343(role,push);
  if(role==='OPERATOR')decorateOperatorPinSelectionV343();
};

""";
    }

    private String operatorGuidedFlowPatchScript() {
        return """

// ===== v3.40 guided operator receiving flow =====
function ensureOperatorFlowStyleV340(){
  if(byId('operatorFlowStyleV340'))return;
  const s=document.createElement('style');
  s.id='operatorFlowStyleV340';
  s.textContent=
    '.operatorStepsV340{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin:0 0 14px;}'
    +'.operatorStepV340{background:#fff;border:1px solid var(--line);border-radius:13px;padding:10px 11px;min-height:60px;display:flex;gap:9px;align-items:center;}'
    +'.operatorStepV340 .n{width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#e7edf4;color:#526176;font-weight:900;flex:0 0 auto;}'
    +'.operatorStepV340 b{display:block;font-size:12px}.operatorStepV340 span{display:block;font-size:10px;color:var(--muted);margin-top:2px;}'
    +'.operatorStepV340.active{border-color:#75a8da;background:#eef6ff}.operatorStepV340.active .n{background:var(--b);color:#fff;}'
    +'.operatorStepV340.done{border-color:#a8d7c2;background:#f0faf5}.operatorStepV340.done .n{background:var(--g);color:#fff;}'
    +'.operatorChosenTitleV340{font-size:14px;font-weight:900;margin:12px 0 7px;}'
    +'.operatorChosenV340 .summaryLine{background:#f4f8fc;border:1px solid #dce6f0;border-radius:10px;padding:10px 11px;margin-bottom:7px;display:flex;justify-content:space-between;gap:10px;align-items:center;color:var(--ink);}'
    +'.operatorChosenV340 .summaryLine b{font-size:18px;}'
    +'.operatorLockedV340{display:none!important;}'
    +'.operatorSelectedCardV340{border-color:#75a8da!important;box-shadow:0 0 0 2px rgba(18,99,182,.07);}'
    +'.operatorTakeBannerV340{background:#eef6ff;border:1px solid #bcd4ec;border-radius:13px;padding:12px 13px;margin-bottom:10px;font-size:12px;line-height:1.45;color:#264d73;}'
    +'@media(max-width:760px){.operatorStepsV340{grid-template-columns:repeat(2,minmax(0,1fr));}}';
  document.head.appendChild(s);
}

function operatorFlowStageV340(stage){
  const steps=document.querySelectorAll('.operatorStepV340');
  steps.forEach(function(el){
    const n=Number(el.dataset.step||0);
    el.classList.remove('active','done');
    if(n<stage)el.classList.add('done');
    else if(n===stage)el.classList.add('active');
  });
}

function selectedIssueCountV340(){
  return Object.keys(issueSelection||{}).length;
}

function operatorSelectedHtmlV340(){
  const lines=[];
  Object.entries(issueSelection||{}).forEach(function(entry){
    const a=db.assignments.find(function(x){return x.id===entry[0];});
    if(!a)return;
    const p=ppe(a.ppeId);
    lines.push('<div class="summaryLine"><span>'+esc(p?p.name:a.ppeId)+'</span><b>'+Number(entry[1]||0)+'</b></div>');
  });
  return lines.join('')||'<div class="sub">Ничего не выбрано</div>';
}

function syncOperatorOpenButtonV340(){
  const b=byId('openIssue');
  if(!b)return;
  const has=selectedIssueCountV340()>0;
  if(!session.flow)b.disabled=!has;
}

function enhanceOperatorIssueV340(){
  const open=byId('openIssue');
  const confirm=byId('confirmIssue');
  const summary=byId('issueSummary');
  if(!open||!confirm||!summary)return;

  ensureOperatorFlowStyleV340();

  const h1=document.querySelector('.contentHead .h1');
  if(h1)h1.textContent='Получение расходных материалов';

  const layout=document.querySelector('.issueLayout');
  if(layout&&!byId('operatorStepsV340')){
    const steps=document.createElement('div');
    steps.id='operatorStepsV340';
    steps.className='operatorStepsV340';
    steps.innerHTML=
      '<div class="operatorStepV340 active" data-step="1"><div class="n">1</div><div><b>Выберите</b><span>Материалы и количество</span></div></div>'
      +'<div class="operatorStepV340" data-step="2"><div class="n">2</div><div><b>Откройте</b><span>Нажмите кнопку открытия</span></div></div>'
      +'<div class="operatorStepV340" data-step="3"><div class="n">3</div><div><b>Заберите</b><span>И закройте ячейку</span></div></div>'
      +'<div class="operatorStepV340" data-step="4"><div class="n">4</div><div><b>Подтвердите</b><span>Получение материалов</span></div></div>';
    layout.parentNode.insertBefore(steps,layout);
  }

  const summaryParent=summary.parentElement;
  if(summaryParent){
    const title=Array.from(summaryParent.querySelectorAll('.h2')).find(function(x){
      return String(x.textContent||'').trim().toLowerCase()==='выбрано';
    });
    if(title)title.textContent='Выбрано к получению';
  }
  summary.classList.add('operatorChosenV340');

  open.textContent='ОТКРЫТЬ ЯЧЕЙКУ';
  confirm.textContent='ПОДТВЕРДИТЬ ПОЛУЧЕНИЕ';
  confirm.disabled=true;

  const hint=byId('flowHint');
  if(hint)hint.textContent='1. Выберите расходные материалы и количество. После этого станет доступно открытие ячейки.';

  document.querySelectorAll('.issueQty').forEach(function(inp){
    const prev=inp.oninput;
    inp.oninput=function(ev){
      if(typeof prev==='function')prev.call(inp,ev);
      else updateIssueSummary();
      syncOperatorOpenButtonV340();
      operatorFlowStageV340(selectedIssueCountV340()>0?2:1);
    };
  });

  updateIssueSummary();
  syncOperatorOpenButtonV340();
  operatorFlowStageV340(selectedIssueCountV340()>0?2:1);
}

const __updateIssueSummaryV340=updateIssueSummary;
updateIssueSummary=function(){
  __updateIssueSummaryV340();
  const summary=byId('issueSummary');
  if(summary){
    summary.innerHTML=operatorSelectedHtmlV340();
    summary.classList.add('operatorChosenV340');
  }
  syncOperatorOpenButtonV340();
};

function lockOperatorSelectionV340(){
  document.querySelectorAll('.ppeCard').forEach(function(card){
    const inp=card.querySelector('.issueQty');
    if(!inp)return;
    const q=Math.max(0,Number(inp.value||0));
    inp.disabled=true;
    if(q>0)card.classList.add('operatorSelectedCardV340');
    else card.classList.add('operatorLockedV340');
  });

  const cards=document.querySelector('.cards2');
  if(cards&&!byId('operatorTakeBannerV340')){
    const b=document.createElement('div');
    b.id='operatorTakeBannerV340';
    b.className='operatorTakeBannerV340';
    b.innerHTML='<b>Заберите только выбранные позиции</b><br>Ниже оставлен только список расходных материалов, которые вы выбрали до открытия ячейки.';
    cards.insertBefore(b,cards.firstChild);
  }
}

startIssue=async function(){
  updateIssueSummary();
  if(!selectedIssueCountV340()){
    toast('Сначала выберите расходные материалы и количество','error');
    return;
  }

  const rows=operatorSelectedHtmlV340();
  confirmModal(
    'Открыть ячейку?',
    '<div style="margin-bottom:8px">После открытия возьмите только выбранные позиции:</div>'+rows,
    'ОТКРЫТЬ',
    async function(){
      try{
        const open=byId('openIssue');
        if(open)open.disabled=true;
        lockOperatorSelectionV340();
        operatorFlowStageV340(2);

        const hint=byId('flowHint');
        if(hint)hint.textContent='Команда открытия отправлена. Ожидаем открытия ячейки.';

        session.flow={kind:'ISSUE',cellId:session.user.cellId,sawOpen:false,closed:false};
        await apiOpen(session.user.cellId);
        pollPhysicalFlow();
      }catch(e){
        session.flow=null;
        toast('Ошибка открытия: '+e.message,'error');
        showOperator();
      }
    }
  );
};

pollPhysicalFlow=function(){
  let tries=0;
  const timer=setInterval(async function(){
    if(!session.flow){clearInterval(timer);return;}
    tries++;
    try{
      const c=await apiCell(session.flow.cellId);
      const st=Number(c.state);
      setCellState(st);

      const routeState=byId('routeCellState');
      if(routeState){
        routeState.textContent=st===0?'ОТКРЫТА':'ЗАКРЫТА';
        routeState.className='stateBig '+(st===0?'open':'closed');
      }

      if(st===0){
        session.flow.sawOpen=true;
        if(session.flow.kind==='ISSUE'){
          operatorFlowStageV340(3);
          const h=byId('flowHint');
          if(h)h.textContent='Ячейка открыта. Возьмите выбранные расходные материалы из списка и закройте дверцу.';
        }else{
          const h=byId('routeHint');
          if(h)h.textContent='Ячейка открыта. Выполните операцию и закройте дверцу.';
        }
      }

      if(st===1&&session.flow.sawOpen){
        session.flow.closed=true;
        clearInterval(timer);

        if(session.flow.kind==='ISSUE'){
          operatorFlowStageV340(4);
          const h=byId('flowHint');
          if(h)h.textContent='Ячейка закрыта. Проверьте список и нажмите «Подтвердить получение».';
          const b=byId('confirmIssue');
          if(b)b.disabled=false;
        }

        if(session.flow.kind==='REPLENISH'){
          const h=byId('routeHint');
          if(h)h.textContent='Ячейка закрыта. Проверьте количество и подтвердите.';
          const b=byId('confirmRoute');
          if(b)b.disabled=false;
        }
      }

      if(tries>70){
        clearInterval(timer);
        toast('Не удалось зафиксировать цикл открытия/закрытия','error');
      }
    }catch(e){
      if(tries>5){
        clearInterval(timer);
        toast('Ошибка контроля двери: '+e.message,'error');
      }
    }
  },700);
};

confirmIssue=function(){
  if(!session.flow||!session.flow.closed){
    toast('Сначала закройте ячейку','error');
    return;
  }

  const selected=Object.entries(issueSelection)
    .map(function(x){
      const a=db.assignments.find(function(z){return z.id===x[0];});
      return {a:a,q:Math.max(0,Number(x[1]||0))};
    })
    .filter(function(x){return x.a&&x.q>0;});

  if(!selected.length){
    toast('Нет выбранных расходных материалов','error');
    return;
  }

  for(const x of selected){
    if(x.q>Number(x.a.stock||0)){
      toast('Остаток изменился. Обновите экран и повторите получение.','error');
      return;
    }
  }

  const rows=selected.map(function(x){
    const p=ppe(x.a.ppeId);
    return '<div class="summaryLine"><span>'+esc(p?p.name:x.a.ppeId)+'</span><b>'+x.q+'</b></div>';
  }).join('');

  confirmModal('Подтвердить получение?',rows,'ПОДТВЕРДИТЬ',function(){
    const tx=uid('ISS');
    const ts=nowIso();
    let logged=0;

    selected.forEach(function(x){
      const a=x.a,q=x.q;
      const before=Number(a.stock||0);
      const after=Math.max(0,before-q);
      a.stock=after;

      db.issueLog.unshift({
        id:uid('L'),
        tx:tx,
        ts:ts,
        userId:session.user.id,
        userName:session.user.name,
        cellId:a.cellId,
        ppeId:a.ppeId,
        ppeName:ppe(a.ppeId)?ppe(a.ppeId).name:a.ppeId,
        before:before,
        qty:q,
        after:after,
        status:'Подтверждено'
      });
      logged++;
    });

    saveDb();
    syncTasks();
    saveDb();

    session.flow=null;
    issueSelection={};
    toast(logged>0?'Получение зарегистрировано':'Ошибка: получение не записано',logged>0?'ok':'error');
    showOperator();
  });
};

const __showOperatorV340=showOperator;
showOperator=function(push=true){
  __showOperatorV340(push);
  enhanceOperatorIssueV340();
};

""";
    }


    private String operatorConsumablesGridPatchScript() {
        return """

// ===== v3.44 compact consumables grid: at least 8 visible on tablet =====
(function installOperatorConsumablesGridV344(){
  if(byId('operatorConsumablesGridStyleV344'))return;
  const s=document.createElement('style');
  s.id='operatorConsumablesGridStyleV344';
  s.textContent=
    '.issueLayout{grid-template-columns:minmax(0,1fr) 218px!important;gap:10px!important;align-items:start!important;}'
    +'.issueLayout>.cards2{display:grid!important;grid-template-columns:repeat(4,minmax(0,1fr))!important;gap:8px!important;}'
    +'.issueLayout .ppeCard{min-width:0!important;height:122px!important;min-height:122px!important;max-height:122px!important;border-radius:12px!important;padding:9px!important;overflow:hidden!important;}'
    +'.issueLayout .ppeTop{gap:5px!important;align-items:flex-start!important;}'
    +'.issueLayout .ppeTop>div:first-child{min-width:0!important;flex:1!important;}'
    +'.issueLayout .ppeName{font-size:12px!important;line-height:1.18!important;min-height:29px!important;max-height:29px!important;display:-webkit-box!important;-webkit-line-clamp:2!important;-webkit-box-orient:vertical!important;overflow:hidden!important;}'
    +'.issueLayout .badge{font-size:8px!important;padding:3px 5px!important;border-radius:999px!important;max-width:58px!important;overflow:hidden!important;text-overflow:ellipsis!important;}'
    +'.issueLayout .meta{font-size:9px!important;line-height:1.15!important;gap:5px!important;margin-top:4px!important;display:block!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;}'
    +'.issueLayout .meta span{margin-right:5px!important;}'
    +'.issueLayout .ppeCard>.sub{display:none!important;}'
    +'.issueLayout .qtyRow{margin-top:7px!important;gap:4px!important;flex-wrap:nowrap!important;}'
    +'.issueLayout .qtyRow>span:first-child{font-size:9px!important;white-space:nowrap!important;}'
    +'.issueLayout .qtyRow>.sub{display:inline!important;font-size:9px!important;white-space:nowrap!important;}'
    +'.issueLayout .qty{width:48px!important;height:30px!important;border-radius:7px!important;font-size:12px!important;padding:0 3px!important;}'
    +'.issueLayout>.card.sticky{padding:11px!important;border-radius:12px!important;top:6px!important;}'
    +'.issueLayout>.card.sticky>.sub:first-child{font-size:9px!important;}'
    +'.issueLayout .stateBig{font-size:18px!important;margin:3px 0 7px!important;}'
    +'.issueLayout>.card.sticky .h2{font-size:13px!important;margin:0!important;}'
    +'.issueLayout #issueSummary{min-height:42px!important;max-height:105px!important;overflow:auto!important;margin-top:4px!important;}'
    +'.issueLayout #issueSummary .summaryLine,.issueLayout .operatorChosenV340 .summaryLine{padding:4px 5px!important;margin-bottom:3px!important;font-size:9px!important;border-radius:7px!important;}'
    +'.issueLayout #issueSummary .summaryLine b,.issueLayout .operatorChosenV340 .summaryLine b{font-size:12px!important;}'
    +'.issueLayout #flowHint{font-size:9px!important;line-height:1.25!important;padding:7px 8px!important;margin-top:6px!important;}'
    +'.issueLayout #openIssue,.issueLayout #confirmIssue{height:36px!important;font-size:10px!important;margin-top:6px!important;}'
    +'.operatorStepsV340{gap:6px!important;margin:0 0 8px!important;}'
    +'.operatorStepV340{min-height:46px!important;padding:7px 8px!important;gap:6px!important;border-radius:10px!important;}'
    +'.operatorStepV340 .n{width:22px!important;height:22px!important;font-size:10px!important;}'
    +'.operatorStepV340 b{font-size:10px!important}.operatorStepV340 span{font-size:8px!important;}'
    +'@media(max-width:720px){.issueLayout{grid-template-columns:minmax(0,1fr) 190px!important}.issueLayout>.cards2{grid-template-columns:repeat(4,minmax(0,1fr))!important;gap:6px!important}.issueLayout .ppeCard{height:116px!important;min-height:116px!important;max-height:116px!important;padding:7px!important}.issueLayout .ppeName{font-size:11px!important}}'
    +'@media(max-width:560px){.issueLayout{grid-template-columns:1fr!important}.issueLayout>.cards2{grid-template-columns:repeat(2,minmax(0,1fr))!important}.issueLayout .ppeCard{height:112px!important;min-height:112px!important;max-height:112px!important}}';
  document.head.appendChild(s);
})();

""";
    }


    private String operatorQtyStepperPatchScript() {
        return """

// ===== v3.45 quantity stepper: minus / value / plus =====
function installQtyStepperStyleV345(){
  if(byId('qtyStepperStyleV345'))return;
  const s=document.createElement('style');
  s.id='qtyStepperStyleV345';
  s.textContent=
    '.qtyStepperV345{display:grid;grid-template-columns:34px 42px 34px;gap:4px;align-items:center;margin-left:auto;}'
    +'.qtyStepBtnV345{width:34px;height:32px;border:1px solid #c7d2df;border-radius:8px;background:#f4f7fb;color:#203247;font-size:20px;font-weight:900;line-height:1;display:flex;align-items:center;justify-content:center;padding:0;cursor:pointer;}'
    +'.qtyStepBtnV345:active:not(:disabled){transform:scale(.95);background:#e7f0fa;}'
    +'.qtyStepBtnV345.plus{background:#1263b6;border-color:#1263b6;color:#fff;}'
    +'.qtyStepBtnV345:disabled{opacity:.28;cursor:default;}'
    +'.qtyStepValueV345{height:32px;border:1px solid #cfd8e5;border-radius:8px;background:#fff;display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:900;color:#1c2a3a;}'
    +'.issueLayout .qtyRow{display:flex!important;align-items:center!important;gap:6px!important;}'
    +'.issueLayout .qtyRow>span:first-child{margin-right:auto!important;}'
    +'.issueLayout .qtyRow>.sub{margin-left:2px!important;}'
    +'@media(max-width:720px){.qtyStepperV345{grid-template-columns:31px 38px 31px;gap:3px}.qtyStepBtnV345{width:31px;height:30px}.qtyStepValueV345{height:30px;font-size:13px}}';
  document.head.appendChild(s);
}

function applyQtyStepperV345(){
  installQtyStepperStyleV345();

  document.querySelectorAll('.issueQty').forEach(function(inp){
    if(inp.dataset.stepperV345==='1')return;
    inp.dataset.stepperV345='1';

    const row=inp.closest('.qtyRow');
    if(!row)return;

    const max=Math.max(0,Number(inp.max||0));
    const disabled=inp.disabled||max<=0;

    // Скрываем ручной ввод: значение хранится в штатном input,
    // поэтому остальная логика приложения продолжает работать без изменений.
    inp.style.display='none';
    inp.tabIndex=-1;
    inp.setAttribute('inputmode','none');

    const box=document.createElement('div');
    box.className='qtyStepperV345';

    const minus=document.createElement('button');
    minus.type='button';
    minus.className='qtyStepBtnV345 minus';
    minus.textContent='−';

    const value=document.createElement('div');
    value.className='qtyStepValueV345';

    const plus=document.createElement('button');
    plus.type='button';
    plus.className='qtyStepBtnV345 plus';
    plus.textContent='+';

    function syncButtons(){
      const q=Math.max(0,Math.min(max,Number(inp.value||0)));
      inp.value=String(q);
      value.textContent=String(q);
      minus.disabled=disabled||q<=0;
      plus.disabled=disabled||q>=max;
    }

    function setQty(next){
      const q=Math.max(0,Math.min(max,Number(next||0)));
      if(Number(inp.value||0)===q){syncButtons();return;}
      inp.value=String(q);
      try{
        inp.dispatchEvent(new Event('input',{bubbles:true}));
      }catch(e){
        if(typeof updateIssueSummary==='function')updateIssueSummary();
      }
      syncButtons();
    }

    minus.onclick=function(){setQty(Number(inp.value||0)-1);};
    plus.onclick=function(){setQty(Number(inp.value||0)+1);};

    box.appendChild(minus);
    box.appendChild(value);
    box.appendChild(plus);
    row.insertBefore(box,inp.nextSibling);

    syncButtons();
  });
}

const __showOperatorV345=showOperator;
showOperator=function(push=true){
  __showOperatorV345(push);
  applyQtyStepperV345();
};

""";
    }


    private String operatorLayoutFixPatchScript() {
        return """

// ===== v3.46 stable operator layout =====
(function installOperatorLayoutFixV346(){
  const old=byId('operatorLayoutFixStyleV346');
  if(old)old.remove();

  const s=document.createElement('style');
  s.id='operatorLayoutFixStyleV346';
  s.textContent=
    '.operatorStepsV340{grid-template-columns:repeat(4,minmax(0,1fr))!important;gap:6px!important;margin:0 0 8px!important;}'
    +'.operatorStepV340{min-height:44px!important;padding:6px 8px!important;gap:6px!important;border-radius:10px!important;}'
    +'.operatorStepV340 .n{width:22px!important;height:22px!important;font-size:10px!important;}'
    +'.operatorStepV340 b{font-size:10px!important;line-height:1.1!important}.operatorStepV340 span{font-size:8px!important;line-height:1.1!important;}'

    +'.issueLayout{display:grid!important;grid-template-columns:minmax(0,1fr) 240px!important;gap:12px!important;align-items:start!important;}'
    +'.issueLayout>.cards2{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important;gap:10px!important;min-width:0!important;}'
    +'.issueLayout .ppeCard{height:148px!important;min-height:148px!important;max-height:148px!important;padding:11px!important;border-radius:13px!important;overflow:hidden!important;}'
    +'.issueLayout .ppeTop{gap:7px!important;}'
    +'.issueLayout .ppeName{font-size:13px!important;line-height:1.2!important;min-height:32px!important;max-height:32px!important;-webkit-line-clamp:2!important;}'
    +'.issueLayout .badge{font-size:8px!important;padding:4px 6px!important;max-width:66px!important;}'
    +'.issueLayout .meta{font-size:9px!important;line-height:1.2!important;margin-top:6px!important;white-space:normal!important;overflow:visible!important;}'
    +'.issueLayout .meta span{display:block!important;margin:2px 0!important;}'
    +'.issueLayout .ppeCard>.sub{display:none!important;}'
    +'.issueLayout .qtyRow{margin-top:9px!important;gap:6px!important;display:flex!important;align-items:center!important;flex-wrap:nowrap!important;}'
    +'.issueLayout .qtyRow>span:first-child{font-size:9px!important;white-space:nowrap!important;margin-right:auto!important;}'
    +'.issueLayout .qtyRow>.sub{font-size:9px!important;white-space:nowrap!important;margin-left:0!important;}'

    +'.qtyStepperV345{grid-template-columns:32px 38px 32px!important;gap:4px!important;margin-left:0!important;flex:0 0 auto!important;}'
    +'.qtyStepBtnV345{width:32px!important;height:32px!important;font-size:18px!important;border-radius:8px!important;}'
    +'.qtyStepValueV345{height:32px!important;font-size:13px!important;border-radius:8px!important;}'

    +'.issueLayout>.card.sticky{width:240px!important;min-width:240px!important;max-width:240px!important;padding:12px!important;border-radius:13px!important;top:6px!important;}'
    +'.issueLayout>.card.sticky>.sub:first-child{font-size:9px!important;}'
    +'.issueLayout .stateBig{font-size:19px!important;margin:3px 0 8px!important;}'
    +'.issueLayout>.card.sticky .h2{font-size:13px!important;margin:0!important;}'
    +'.issueLayout #issueSummary{min-height:50px!important;max-height:130px!important;overflow:auto!important;margin-top:5px!important;}'
    +'.issueLayout #issueSummary .summaryLine{padding:6px 7px!important;margin-bottom:4px!important;font-size:10px!important;}'
    +'.issueLayout #issueSummary .summaryLine b{font-size:13px!important;}'
    +'.issueLayout #flowHint{font-size:9px!important;line-height:1.25!important;padding:8px!important;margin-top:7px!important;}'
    +'.issueLayout #openIssue,.issueLayout #confirmIssue{height:38px!important;font-size:10px!important;margin-top:7px!important;}'

    +'.operatorTakeBannerV346{grid-column:1/-1!important;background:#eef6ff;border:1px solid #bcd4ec;border-radius:11px;padding:8px 11px;margin:0 0 9px;font-size:10px;line-height:1.25;color:#264d73;}'
    +'.operatorTakeBannerV346 b{font-size:11px;}'
    +'.operatorTakeBannerV340{display:none!important;}'

    +'@media(max-width:760px){'
      +'.operatorStepsV340{grid-template-columns:repeat(4,minmax(0,1fr))!important;}'
      +'.issueLayout{grid-template-columns:minmax(0,1fr) 220px!important;gap:9px!important;}'
      +'.issueLayout>.card.sticky{width:220px!important;min-width:220px!important;max-width:220px!important;}'
      +'.issueLayout>.cards2{grid-template-columns:repeat(3,minmax(0,1fr))!important;gap:7px!important;}'
      +'.issueLayout .ppeCard{height:142px!important;min-height:142px!important;max-height:142px!important;padding:9px!important;}'
      +'.issueLayout .ppeName{font-size:12px!important;}'
    +'}'

    +'@media(max-width:620px){'
      +'.operatorStepsV340{grid-template-columns:repeat(2,minmax(0,1fr))!important;}'
      +'.issueLayout{grid-template-columns:1fr!important;}'
      +'.issueLayout>.card.sticky{width:auto!important;min-width:0!important;max-width:none!important;position:static!important;}'
      +'.issueLayout>.cards2{grid-template-columns:repeat(2,minmax(0,1fr))!important;}'
    +'}';
  document.head.appendChild(s);
})();

lockOperatorSelectionV340=function(){
  document.querySelectorAll('.ppeCard').forEach(function(card){
    const inp=card.querySelector('.issueQty');
    if(!inp)return;
    const q=Math.max(0,Number(inp.value||0));
    inp.disabled=true;

    const stepper=card.querySelector('.qtyStepperV345');
    if(stepper){
      stepper.querySelectorAll('button').forEach(function(b){b.disabled=true;});
    }

    if(q>0){
      card.classList.add('operatorSelectedCardV340');
      card.classList.remove('operatorLockedV340');
    }else{
      card.classList.add('operatorLockedV340');
      card.classList.remove('operatorSelectedCardV340');
    }
  });

  const old=byId('operatorTakeBannerV340');
  if(old)old.remove();

  const existing=byId('operatorTakeBannerV346');
  if(existing)existing.remove();

  const layout=document.querySelector('.issueLayout');
  if(layout){
    const b=document.createElement('div');
    b.id='operatorTakeBannerV346';
    b.className='operatorTakeBannerV346';
    b.innerHTML='<b>Заберите только выбранные позиции</b> и закройте ячейку.';
    layout.parentNode.insertBefore(b,layout);
  }
};

const __showOperatorV346=showOperator;
showOperator=function(push=true){
  __showOperatorV346(push);

  const old=byId('operatorTakeBannerV346');
  if(old&&!session.flow)old.remove();
};

""";
    }


    private String productionRcPatchScript() {
        return """

// ===== v3.47 RC1 real postomat controller =====
function rcParseNativeJsonV347(raw,label){
  const text=String(raw==null?'':raw).trim();
  if(!text)throw new Error((label||'Контроллер')+': пустой ответ');
  let data;
  try{data=JSON.parse(text)}catch(e){throw new Error((label||'Контроллер')+': некорректный JSON');}
  if(data&&data.error)throw new Error(String(data.error));
  return data;
}

apiLogin=async function(address,password){
  if(typeof NativeStore==='undefined'||typeof NativeStore.postomatLogin!=='function'){
    throw new Error('Нативный модуль связи с постоматом недоступен');
  }
  const base=String(address||'').trim().replace(/\\/+$/,'');
  if(!base)throw new Error('Укажите адрес контроллера');
  const data=rcParseNativeJsonV347(
    NativeStore.postomatLogin(base,String(password||'')),
    'Авторизация'
  );
  if(!data.sid)throw new Error('Контроллер не вернул SID');
  session.sid=String(data.sid);
  session.apiRole=String(data.al==null?'':data.al);
  session.connected=true;
  session.mode='LIVE';
  db.settings.address=base;
  db.settings.mode='LIVE';
  saveDb();
  return data;
};

apiCells=async function(){
  if(!session.sid||session.sid==='DEMO-SID')throw new Error('Нет активной сессии постамата');
  const data=rcParseNativeJsonV347(
    NativeStore.postomatGetCells(String(db.settings.address||'http://10.10.10.1'),String(session.sid)),
    'Чтение ячеек'
  );
  const list=Array.isArray(data)?data:(Array.isArray(data.boxs)?data.boxs:(Array.isArray(data.data)?data.data:null));
  if(!list)throw new Error('Контроллер вернул неожиданный формат списка ячеек');
  session.connected=true;
  return list;
};

apiCell=async function(cid){
  const list=await apiCells();
  const found=list.find(function(x){return Number(x.bid)===Number(cid);});
  if(!found)throw new Error('Ячейка BID '+cid+' не найдена контроллером');
  return found;
};

apiOpen=async function(cid){
  if(!session.sid||session.sid==='DEMO-SID')throw new Error('Нет активной сессии постамата');
  const data=rcParseNativeJsonV347(
    NativeStore.postomatOpenCell(
      String(db.settings.address||'http://10.10.10.1'),
      String(session.sid),
      Number(cid)
    ),
    'Открытие ячейки'
  );
  if(data.read!=null&&String(data.read)!=='ok'){
    throw new Error('Контроллер не подтвердил открытие ячейки');
  }
  return data;
};

chrome=function(content){
  const online=!!(session.connected&&session.sid&&session.sid!=='DEMO-SID');
  const status=online
    ?'<span class="dot ok"></span><span>Постомат подключён</span>'
    :'<span class="dot err"></span><span>Нет подключения</span>';
  const back='<button id="globalBack" class="topBack '+(canAppBack()?'':'hidden')+'">← НАЗАД</button>';
  return '<div class="topbar">'+back
    +'<div class="brand"><div class="brandmark">P</div><div class="brandtext"><b>Постомат расходных материалов</b><span>Выдача • пополнение • контроль</span></div></div>'
    +'<div class="spacer"></div><div class="conn">'+status+'</div><div class="version">v'+APP_VERSION+'</div></div>'
    +'<div class="screen">'+content+'</div>';
};

function cleanRcUiV347(){
  document.querySelectorAll('.betaBar').forEach(function(x){x.style.display='none';});
  document.querySelectorAll('[data-nav="simulator"]').forEach(function(x){x.style.display='none';});
  const reset=byId('resetDemo');if(reset)reset.style.display='none';
  const autoClose=byId('setAutoClose');
  if(autoClose&&autoClose.closest('.field'))autoClose.closest('.field').style.display='none';
}

const __renderRcV347=render;
render=function(html){
  __renderRcV347(html);
  cleanRcUiV347();
};

showConnect=function(push=true){
  if(push)setUi({screen:'connect',role:null,tab:null},true);
  else uiState={screen:'connect',role:null,tab:null};

  session.role=null;
  session.user=null;
  session.connected=false;
  session.sid=null;
  session.apiRole='';

  const address=String(db.settings.address||'http://10.10.10.1');
  render('<div class="centerScreen"><div class="panel connectPanel" style="max-width:620px;margin:28px auto">'
    +'<div class="h1">Подключение к постамату</div>'
    +'<p class="sub">RC1 работает с реальным контроллером постамата. Планшет должен быть подключён к сети постамата.</p>'
    +'<div class="note" style="margin-top:12px">Адрес по умолчанию: <b>http://10.10.10.1</b>. Пароль используется только для текущего запуска приложения и не сохраняется.</div>'
    +'<div class="field" style="margin-top:16px"><label>Адрес контроллера</label><input id="rcControllerAddr" class="input" data-vk="latin" value="'+esc(address)+'"></div>'
    +'<div class="field"><label>Пароль штатного web-интерфейса</label><input id="rcControllerPassword" class="input" type="password" autocomplete="off"></div>'
    +'<button id="rcConnectBtn" class="btn primary block" style="margin-top:14px">ПОДКЛЮЧИТЬСЯ</button>'
    +'<div id="rcConnectMsg" class="sub" style="margin-top:12px"></div>'
    +'</div></div>');

  const back=byId('globalBack');if(back)back.style.display='none';

  const btn=byId('rcConnectBtn');
  if(btn)btn.onclick=async function(){
    const addr=String(byId('rcControllerAddr').value||'').trim();
    const pwd=String(byId('rcControllerPassword').value||'');
    btn.disabled=true;
    byId('rcConnectMsg').textContent='Проверяю связь с контроллером…';
    try{
      await apiLogin(addr,pwd);
      const list=await apiCells();
      if(!list.length)throw new Error('Контроллер не вернул ни одной ячейки');
      byId('rcControllerPassword').value='';
      toast('Связь с постаматом установлена','ok');
      showBetaHome(false);
    }catch(e){
      session.connected=false;session.sid=null;
      const msg=e&&e.message?e.message:String(e);
      byId('rcConnectMsg').textContent='Ошибка: '+msg;
      toast('Не удалось подключиться к постамату','error');
    }finally{
      btn.disabled=false;
    }
  };
};

const __homeRcV347=showBetaHome;
showBetaHome=function(push=true){
  if(!session.connected||!session.sid||session.sid==='DEMO-SID'){
    showConnect(push);
    return;
  }

  const sid=session.sid;
  const apiRole=session.apiRole;
  const address=String(db.settings.address||'http://10.10.10.1');

  __homeRcV347(push);

  session.sid=sid;
  session.apiRole=apiRole;
  session.connected=true;
  session.mode='LIVE';
  db.settings.mode='LIVE';

  const tag=document.querySelector('.betaTag');
  if(tag)tag.textContent='● RC1 • ПОСТАМАТ ПОДКЛЮЧЁН';
  const hero=document.querySelector('.homeHeroV318');
  if(hero){
    const h=hero.querySelector('h1');if(h)h.textContent='ПОСТАМАТ РАСХОДНЫХ МАТЕРИАЛОВ';
    const note=hero.querySelector('.homeHeroNoteV318');
    if(note)note.textContent='Контроллер: '+address+' • реальные ячейки активны';
  }
  const op=byId('betaOperator');
  if(op){
    const t=op.querySelector('.homeRoleTitleV318');
    if(t)t.textContent='ПОЛУЧЕНИЕ РАСХОДНЫХ МАТЕРИАЛОВ';
  }
  const back=byId('globalBack');if(back)back.style.display='none';
  cleanRcUiV347();
};

showRoles=function(){showBetaHome(false);};

const __showAdminRcV347=showAdmin;
showAdmin=function(tab='overview',push=true){
  if(tab==='simulator')tab='overview';
  __showAdminRcV347(tab,push);
  cleanRcUiV347();

  if(tab==='settings'){
    db.settings.mode='LIVE';
    const save=byId('saveSettings');
    if(save&&!save.dataset.rcLiveV347){
      save.dataset.rcLiveV347='1';
      const old=save.onclick;
      save.onclick=function(){
        if(typeof old==='function')old.call(save);
        db.settings.mode='LIVE';
        saveDb();
      };
    }
  }
};

setTimeout(function(){
  try{
    session.sid=null;
    session.connected=false;
    session.mode='LIVE';
    db.settings.mode='LIVE';
    saveDb();
    showConnect(false);
  }catch(e){
    console.error('RC1 startup',e);
  }
},0);

""";
    }


    private String autoStartConnectPatchScript() {
        return """

// ===== v3.48 RC2 secure stored controller credentials + automatic connect =====
function rc2NativeReadyV348(){
  return typeof NativeStore!=='undefined'
    &&typeof NativeStore.hasControllerCredentials==='function'
    &&typeof NativeStore.postomatAutoLogin==='function';
}

async function rc2AutoConnectV348(){
  if(!rc2NativeReadyV348()){
    showConnect(false);
    return;
  }

  if(!NativeStore.hasControllerCredentials()){
    showConnect(false);
    return;
  }

  const address=String(NativeStore.getControllerAddress()||'http://10.10.10.1');
  db.settings.address=address;
  saveDb();

  render('<div class="centerScreen"><div class="panel connectPanel" style="max-width:620px;margin:40px auto;text-align:center">'
    +'<div class="h1">Подключение к постамату</div>'
    +'<p class="sub">Автоматически подключаюсь к контроллеру…</p>'
    +'<div class="stateBig" style="margin-top:20px;color:#0f4f99">ПОДКЛЮЧЕНИЕ</div>'
    +'<div class="sub">'+esc(address)+'</div>'
    +'</div></div>');

  try{
    const data=rcParseNativeJsonV347(NativeStore.postomatAutoLogin(),'Авторизация');
    if(!data.sid)throw new Error('Контроллер не вернул SID');

    session.sid=String(data.sid);
    session.apiRole=String(data.al==null?'':data.al);
    session.connected=true;
    session.mode='LIVE';
    db.settings.mode='LIVE';

    const cells=await apiCells();
    if(!cells.length)throw new Error('Контроллер не вернул ни одной ячейки');

    saveDb();
    showBetaHome(false);
  }catch(e){
    session.sid=null;
    session.connected=false;
    render('<div class="centerScreen"><div class="panel connectPanel" style="max-width:620px;margin:40px auto">'
      +'<div class="h1">Нет связи с постаматом</div>'
      +'<p class="sub">'+esc(e&&e.message?e.message:String(e))+'</p>'
      +'<div class="note" style="margin-top:14px">Проверьте сеть постамата. Приложение повторит подключение автоматически.</div>'
      +'<button id="rc2Retry" class="btn primary block" style="margin-top:16px">ПОВТОРИТЬ СЕЙЧАС</button>'
      +'<button id="rc2ChangeController" class="btn outline block" style="margin-top:8px">ИЗМЕНИТЬ ПОДКЛЮЧЕНИЕ</button>'
      +'</div></div>');

    const retry=byId('rc2Retry');
    if(retry)retry.onclick=function(){rc2AutoConnectV348();};
    const change=byId('rc2ChangeController');
    if(change)change.onclick=function(){showConnect(false);};

    clearTimeout(window.__rc2ReconnectTimer);
    window.__rc2ReconnectTimer=setTimeout(function(){
      if(!session.connected)rc2AutoConnectV348();
    },5000);
  }
}

showConnect=function(push=true){
  if(push)setUi({screen:'connect',role:null,tab:null},true);
  else uiState={screen:'connect',role:null,tab:null};

  session.role=null;
  session.user=null;
  session.connected=false;
  session.sid=null;
  session.apiRole='';

  const stored=rc2NativeReadyV348()&&NativeStore.hasControllerCredentials();
  const address=stored
    ?String(NativeStore.getControllerAddress()||'http://10.10.10.1')
    :String(db.settings.address||'http://10.10.10.1');

  render('<div class="centerScreen"><div class="panel connectPanel" style="max-width:620px;margin:28px auto">'
    +'<div class="h1">'+(stored?'Настройка подключения':'Первичная настройка постамата')+'</div>'
    +'<p class="sub">'+(stored
      ?'Сохранённое подключение можно изменить. После сохранения приложение будет подключаться автоматически.'
      :'Введите пароль контроллера один раз. Он будет зашифрован Android Keystore и больше запрашиваться не будет.')+'</p>'
    +'<div class="field" style="margin-top:16px"><label>Адрес контроллера</label><input id="rcControllerAddr" class="input" data-vk="latin" value="'+esc(address)+'"></div>'
    +'<div class="field"><label>Пароль штатного web-интерфейса</label><input id="rcControllerPassword" class="input" type="password" autocomplete="off" placeholder="'+(stored?'Оставьте пустым, чтобы не менять':'Введите пароль')+'"></div>'
    +'<button id="rcConnectBtn" class="btn primary block" style="margin-top:14px">'+(stored?'СОХРАНИТЬ И ПОДКЛЮЧИТЬСЯ':'СОХРАНИТЬ И ПОДКЛЮЧИТЬСЯ')+'</button>'
    +(stored?'<button id="rcForgetBtn" class="btn outline block" style="margin-top:8px">СБРОСИТЬ СОХРАНЁННОЕ ПОДКЛЮЧЕНИЕ</button>':'')
    +'<div id="rcConnectMsg" class="sub" style="margin-top:12px"></div>'
    +'</div></div>');

  const back=byId('globalBack');if(back)back.style.display='none';

  const btn=byId('rcConnectBtn');
  if(btn)btn.onclick=async function(){
    const addr=String(byId('rcControllerAddr').value||'').trim();
    const pwd=String(byId('rcControllerPassword').value||'');
    btn.disabled=true;
    byId('rcConnectMsg').textContent='Проверяю и сохраняю подключение…';
    try{
      if(!stored||pwd){
        const test=await apiLogin(addr,pwd);
        if(!test.sid)throw new Error('Контроллер не вернул SID');
        if(!NativeStore.saveControllerCredentials(addr,pwd)){
          throw new Error('Не удалось сохранить пароль в защищённом хранилище Android');
        }
      }else{
        if(!NativeStore.updateControllerAddress(addr)){
          throw new Error('Не удалось обновить адрес контроллера');
        }
        db.settings.address=addr;
        saveDb();
      }
      await rc2AutoConnectV348();
    }catch(e){
      const msg=e&&e.message?e.message:String(e);
      byId('rcConnectMsg').textContent='Ошибка: '+msg;
      toast('Не удалось подключиться к постамату','error');
    }finally{
      btn.disabled=false;
    }
  };

  const forget=byId('rcForgetBtn');
  if(forget)forget.onclick=function(){
    NativeStore.clearControllerCredentials();
    session.sid=null;session.connected=false;
    showConnect(false);
  };
};

// override RC1 startup
setTimeout(function(){
  try{
    clearTimeout(window.__rc2ReconnectTimer);
    session.sid=null;
    session.connected=false;
    session.mode='LIVE';
    db.settings.mode='LIVE';
    saveDb();
    rc2AutoConnectV348();
  }catch(e){
    console.error('RC2 startup',e);
    showConnect(false);
  }
},60);

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


    private SecretKey smtpKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key key = ks.getKey(SMTP_KEY_ALIAS, null);
        if (key instanceof SecretKey) return (SecretKey) key;
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(
                SMTP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return gen.generateKey();
    }

    private boolean storeSmtpCredentials(String email, String password) {
        try {
            email = email == null ? "" : email.trim();
            if (email.isEmpty() || !email.contains("@") || password == null || password.isEmpty()) return false;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, smtpKey());
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(PREF_SMTP_EMAIL, email)
                    .putString(PREF_SMTP_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(PREF_SMTP_SECRET, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String loadSmtpPassword() throws Exception {
        String iv64 = prefs.getString(PREF_SMTP_IV, "");
        String sec64 = prefs.getString(PREF_SMTP_SECRET, "");
        if (iv64 == null || iv64.isEmpty() || sec64 == null || sec64.isEmpty()) return "";
        byte[] iv = Base64.decode(iv64, Base64.NO_WRAP);
        byte[] enc = Base64.decode(sec64, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, smtpKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
    }

    private boolean smtpReady() {
        String email = prefs.getString(PREF_SMTP_EMAIL, "");
        String iv = prefs.getString(PREF_SMTP_IV, "");
        String secret = prefs.getString(PREF_SMTP_SECRET, "");
        return email != null && !email.trim().isEmpty()
                && iv != null && !iv.isEmpty()
                && secret != null && !secret.isEmpty();
    }

    private void clearSmtp() {
        prefs.edit().remove(PREF_SMTP_EMAIL).remove(PREF_SMTP_IV).remove(PREF_SMTP_SECRET).apply();
    }

    private String smtpReadResponse(BufferedReader in) throws Exception {
        String line = in.readLine();
        if (line == null || line.length() < 3) throw new IllegalStateException("SMTP: нет ответа сервера");
        StringBuilder all = new StringBuilder(line);
        String code = line.substring(0, 3);
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) break;
            all.append("\n").append(line);
            if (line.startsWith(code + " ")) break;
        }
        return all.toString();
    }

    private int smtpCode(String response) {
        try { return Integer.parseInt(response.substring(0, 3)); }
        catch (Exception e) { return -1; }
    }

    private void smtpExpect(BufferedReader in, int... allowed) throws Exception {
        String response = smtpReadResponse(in);
        int code = smtpCode(response);
        for (int a : allowed) if (code == a) return;
        throw new IllegalStateException(response.replace('\n', ' '));
    }

    private void smtpCommand(BufferedWriter out, BufferedReader in, String command, int... allowed) throws Exception {
        out.write(command);
        out.write("\r\n");
        out.flush();
        smtpExpect(in, allowed);
    }

    private String mimeWord(String value) {
        String v = value == null ? "" : value;
        return "=?UTF-8?B?" + java.util.Base64.getEncoder()
                .encodeToString(v.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private String safeHeader(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ").trim();
    }

    private String mimeBase64(byte[] bytes) {
        return java.util.Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(bytes == null ? new byte[0] : bytes);
    }

    private void smtpSend(String recipient, String subject, String body,
                          byte[] attachment, String attachmentName) throws Exception {
        if (!smtpReady()) throw new IllegalStateException("SMTP не настроен");
        String sender = prefs.getString(PREF_SMTP_EMAIL, "").trim();
        String password = loadSmtpPassword();
        recipient = safeHeader(recipient);
        if (recipient.isEmpty() || !recipient.contains("@")) throw new IllegalArgumentException("Некорректный Email получателя");

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(SMTP_HOST, SMTP_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(45000);
            socket.startHandshake();
            smtpExpect(in, 220);
            smtpCommand(out, in, "EHLO postomat-siz.local", 250);
            smtpCommand(out, in, "AUTH LOGIN", 334);
            smtpCommand(out, in, java.util.Base64.getEncoder().encodeToString(sender.getBytes(StandardCharsets.UTF_8)), 334);
            smtpCommand(out, in, java.util.Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            smtpCommand(out, in, "MAIL FROM:<" + sender + ">", 250);
            smtpCommand(out, in, "RCPT TO:<" + recipient + ">", 250, 251);
            smtpCommand(out, in, "DATA", 354);

            StringBuilder msg = new StringBuilder();
            msg.append("From: <").append(sender).append(">\r\n");
            msg.append("To: <").append(recipient).append(">\r\n");
            msg.append("Subject: ").append(mimeWord(safeHeader(subject))).append("\r\n");
            msg.append("MIME-Version: 1.0\r\n");

            if (attachment != null && attachment.length > 0) {
                String boundary = "----PostomatSIZ" + System.currentTimeMillis();
                String encodedName = mimeWord(safeHeader(attachmentName == null ? "report.xlsx" : attachmentName));
                msg.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
                msg.append("--").append(boundary).append("\r\n");
                msg.append("Content-Type: text/plain; charset=UTF-8\r\n");
                msg.append("Content-Transfer-Encoding: base64\r\n\r\n");
                msg.append(mimeBase64((body == null ? "" : body).getBytes(StandardCharsets.UTF_8))).append("\r\n");
                msg.append("--").append(boundary).append("\r\n");
                msg.append("Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; name=\"").append(encodedName).append("\"\r\n");
                msg.append("Content-Disposition: attachment; filename=\"").append(encodedName).append("\"\r\n");
                msg.append("Content-Transfer-Encoding: base64\r\n\r\n");
                msg.append(mimeBase64(attachment)).append("\r\n");
                msg.append("--").append(boundary).append("--\r\n");
            } else {
                msg.append("Content-Type: text/plain; charset=UTF-8\r\n");
                msg.append("Content-Transfer-Encoding: base64\r\n\r\n");
                msg.append(mimeBase64((body == null ? "" : body).getBytes(StandardCharsets.UTF_8))).append("\r\n");
            }

            out.write(msg.toString());
            out.write(".\r\n");
            out.flush();
            smtpExpect(in, 250);
            try { smtpCommand(out, in, "QUIT", 221); } catch (Exception ignored) {}
        }
    }

    private void notifyMailResult(String contextId, boolean ok, String message) {
        if (webView == null) return;
        final String js = "if(window.onNativeMailResult)window.onNativeMailResult("
                + JSONObject.quote(contextId == null ? "" : contextId) + ","
                + (ok ? "true" : "false") + ","
                + JSONObject.quote(message == null ? "" : message) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private String smtpErrorMessage(Exception e) {
        String m = e == null ? "" : String.valueOf(e.getMessage());
        if (m.contains("535")) return "Mail.ru отклонил авторизацию. Проверьте Email и пароль для внешнего приложения.";
        if (m.contains("550") || m.contains("553")) return "Mail.ru отклонил адрес получателя или отправителя.";
        if (m.toLowerCase(Locale.ROOT).contains("timeout")) return "Не удалось связаться с Mail.ru: превышено время ожидания.";
        return "Ошибка SMTP: " + (m.isEmpty() ? e.getClass().getSimpleName() : m);
    }



    private SecretKey getOrCreateAesKey(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(alias)) {
            SecretKey key = (SecretKey) ks.getKey(alias, null);
            if (key != null) return key;
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build();
        gen.init(spec);
        return gen.generateKey();
    }

    private boolean storeControllerCredentials(String address, String password) {
        try {
            String root = normalizePostomatBase(address);
            if (password == null || password.isEmpty()) return false;
            SecretKey key = getOrCreateAesKey(CONTROLLER_KEY_ALIAS);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(PREF_CONTROLLER_ADDRESS, root)
                    .putString(PREF_CONTROLLER_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(PREF_CONTROLLER_SECRET, Base64.encodeToString(enc, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String controllerPassword() {
        try {
            String iv64 = prefs.getString(PREF_CONTROLLER_IV, "");
            String sec64 = prefs.getString(PREF_CONTROLLER_SECRET, "");
            if (iv64 == null || sec64 == null || iv64.isEmpty() || sec64.isEmpty()) return "";
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(CONTROLLER_KEY_ALIAS, null);
            if (key == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Base64.decode(iv64, Base64.DEFAULT)));
            byte[] dec = cipher.doFinal(Base64.decode(sec64, Base64.DEFAULT));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String controllerAddress() {
        String x = prefs.getString(PREF_CONTROLLER_ADDRESS, "http://10.10.10.1");
        return x == null || x.trim().isEmpty() ? "http://10.10.10.1" : x.trim();
    }

    private boolean controllerCredentialsReady() {
        return !controllerPassword().isEmpty();
    }

    private void clearControllerCredentials() {
        prefs.edit()
                .remove(PREF_CONTROLLER_ADDRESS)
                .remove(PREF_CONTROLLER_IV)
                .remove(PREF_CONTROLLER_SECRET)
                .apply();
    }

    private String normalizePostomatBase(String base) throws Exception {
        String x = base == null ? "" : base.trim();
        while (x.endsWith("/")) x = x.substring(0, x.length() - 1);
        if (!x.startsWith("http://") && !x.startsWith("https://")) {
            throw new Exception("Адрес контроллера должен начинаться с http:// или https://");
        }
        return x;
    }

    private String postomatHttp(String url, String method, String body, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setUseCaches(false);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType == null ? "application/x-www-form-urlencoded" : contentType);
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(data);
                out.flush();
            }
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = "";
        if (stream != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder b = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) b.append(line);
                text = b.toString();
            }
        }
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + (text.isEmpty() ? "" : ": " + text));
        }
        return text.isEmpty() ? "{}" : text;
    }

    private String postomatError(Exception e) {
        try {
            JSONObject o = new JSONObject();
            String m = e == null || e.getMessage() == null ? "Неизвестная ошибка связи" : e.getMessage();
            o.put("error", m);
            return o.toString();
        } catch (Exception ignored) {
            return "{\"error\":\"Ошибка связи с контроллером\"}";
        }
    }

    public class NativeStoreBridge {

        @JavascriptInterface public boolean saveControllerCredentials(String address, String password) {
            return storeControllerCredentials(address, password);
        }

        @JavascriptInterface public boolean hasControllerCredentials() {
            return controllerCredentialsReady();
        }

        @JavascriptInterface public boolean updateControllerAddress(String address) {
            String password = controllerPassword();
            if (password.isEmpty()) return false;
            return storeControllerCredentials(address, password);
        }

        @JavascriptInterface public String getControllerAddress() {
            return controllerAddress();
        }

        @JavascriptInterface public void clearControllerCredentials() {
            clearControllerCredentials();
        }

        @JavascriptInterface public String postomatAutoLogin() {
            try {
                String password = controllerPassword();
                if (password.isEmpty()) {
                    JSONObject o = new JSONObject();
                    o.put("error", "NOT_CONFIGURED");
                    return o.toString();
                }
                String root = controllerAddress();
                String body = "password=" + URLEncoder.encode(password, "UTF-8");
                return postomatHttp(root + "/api/login", "POST", body, "application/x-www-form-urlencoded");
            } catch (Exception e) {
                return postomatError(e);
            }
        }

        @JavascriptInterface public String postomatLogin(String base, String password) {
            try {
                String root = normalizePostomatBase(base);
                String body = "password=" + URLEncoder.encode(password == null ? "" : password, "UTF-8");
                return postomatHttp(root + "/api/login", "POST", body, "application/x-www-form-urlencoded");
            } catch (Exception e) {
                return postomatError(e);
            }
        }

        @JavascriptInterface public String postomatGetCells(String base, String sid) {
            try {
                String root = normalizePostomatBase(base);
                String q = URLEncoder.encode(sid == null ? "" : sid, "UTF-8");
                return postomatHttp(root + "/api/boxs?sid=" + q, "GET", null, null);
            } catch (Exception e) {
                return postomatError(e);
            }
        }

        @JavascriptInterface public String postomatOpenCell(String base, String sid, int bid) {
            try {
                String root = normalizePostomatBase(base);
                String q = URLEncoder.encode(sid == null ? "" : sid, "UTF-8");
                return postomatHttp(root + "/api/boxs?sid=" + q + "&bid=" + bid + "&cmd=open", "GET", null, null);
            } catch (Exception e) {
                return postomatError(e);
            }
        }

        @JavascriptInterface public boolean saveSmtpCredentials(String email, String password) {
            return storeSmtpCredentials(email, password);
        }

        @JavascriptInterface public boolean isSmtpConfigured() { return smtpReady(); }

        @JavascriptInterface public String getSmtpSenderEmail() {
            String x = prefs.getString(PREF_SMTP_EMAIL, "");
            return x == null ? "" : x;
        }

        @JavascriptInterface public void clearSmtpCredentials() { clearSmtp(); }

        @JavascriptInterface public String smtpSendText(String email, String subject, String body, String contextId) {
            if (!smtpReady()) return "NOT_CONFIGURED";
            if (email == null || !email.contains("@")) return "BAD_EMAIL";
            new Thread(() -> {
                try {
                    smtpSend(email, subject, body, null, null);
                    notifyMailResult(contextId, true, "Письмо отправлено на " + email);
                } catch (Exception e) {
                    notifyMailResult(contextId, false, smtpErrorMessage(e));
                }
            }, "PostomatMailText").start();
            return "QUEUED";
        }

        @JavascriptInterface public String smtpSendBase64Attachment(String base64, String fileName, String email,
                                                                     String subject, String body, String contextId) {
            if (!smtpReady()) return "NOT_CONFIGURED";
            if (email == null || !email.contains("@")) return "BAD_EMAIL";
            new Thread(() -> {
                try {
                    byte[] data = Base64.decode(base64 == null ? "" : base64, Base64.DEFAULT);
                    smtpSend(email, subject, body, data, fileName);
                    notifyMailResult(contextId, true, "Отчёт отправлен на " + email);
                } catch (Exception e) {
                    notifyMailResult(contextId, false, smtpErrorMessage(e));
                }
            }, "PostomatMailAttachment").start();
            return "QUEUED";
        }

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
                send.setClipData(ClipData.newUri(getContentResolver(), "Отчёт расходных материалов", target));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (send.resolveActivity(getPackageManager()) == null) return "NO_APP";
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "Отправить отчёт")));
                return "OK";
            } catch (Exception e) {
                return "ERROR: " + e.getClass().getSimpleName();
            }
        }

        @JavascriptInterface public String sendEmailText(String email, String subject, String body) {
            try {
                if (email == null || email.trim().isEmpty()) return "BAD_EMAIL";
                Intent send = new Intent(Intent.ACTION_SENDTO);
                send.setData(Uri.parse("mailto:" + email.trim()));
                send.putExtra(Intent.EXTRA_EMAIL, new String[]{email.trim()});
                send.putExtra(Intent.EXTRA_SUBJECT, subject == null ? "" : subject);
                send.putExtra(Intent.EXTRA_TEXT, body == null ? "" : body);
                if (send.resolveActivity(getPackageManager()) == null) return "NO_APP";
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "Отправить отчёт о восполнении")));
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
