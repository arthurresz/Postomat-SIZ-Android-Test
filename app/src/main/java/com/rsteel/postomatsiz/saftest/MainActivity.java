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
            page = page.replace(">+ Сотрудник<", ">Добавить сотрудника<");
            page = page.replace(">+ Ячейка<", ">Добавить ячейку<");
            page = page.replace(">+ СИЗ<", ">Добавить СИЗ<");
            page = page.replace(">+ Назначение<", ">Добавить назначение<");
            page = page.replace(">+ Назначить СИЗ<", ">Добавить СИЗ<");
            page = page.replace("const APP_VERSION='3.0-standard-classic-ui';", "const APP_VERSION='3.10-standard-classic-ui-admin-label';");
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
workspace=function(role,active,body){
  if(role!=='WAREHOUSE')return __workspaceV37(role,active,body);
  const nav=[['replenish','Восполнение'],['revision','Ревизия'],['history','История']];
  return `<div class="workspace"><aside class="sidebar">
    <div class="profile"><b>${esc(session.user?.name||'')}</b><span>Склад</span></div>
    ${nav.map(n=>`<button class="navbtn ${n[0]===active?'active':''}" data-nav="${n[0]}">${n[1]}</button>`).join('')}
    <button class="navbtn bottom" data-nav="logout">← Выйти</button>
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
  const cards=groups.map(g=>{
    const c=cell(g.cellId),o=ownerOfCell(g.cellId);
    const critical=g.tasks.some(t=>t.a.stock===0);
    const qty=g.tasks.reduce((s,t)=>s+Math.max(0,t.a.target-t.a.stock),0);
    const items=g.tasks.map(t=>`<div class="whMiniLine"><span>${esc(ppe(t.ppeId)?.name||t.ppeId)}</span><b>+${Math.max(0,t.a.target-t.a.stock)}</b></div>`).join('');
    return `<div class="whCellCard">
      <div class="row"><div><div class="h2" style="font-size:18px">${esc(c?.name||('Ячейка №'+g.cellId))}</div><div class="sub">${esc(o?.name||'Сотрудник не назначен')}</div></div><span class="badge ${critical?'red':'orange'}">${critical?'КРИТИЧНО':'ПОПОЛНИТЬ'}</span></div>
      <div class="whCellMeta"><span>${g.tasks.length} поз.</span><span>Добавить всего: <b>${qty}</b></span></div>
      <div class="whMiniList">${items}</div>
      <button class="btn primary block" data-wh-cell="${g.cellId}">ПЕРЕЙТИ К ВОСПОЛНЕНИЮ</button>
    </div>`;
  }).join('');
  return `<style>
    .whStats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-bottom:14px}
    .whStat{background:#fff;border:1px solid var(--line);border-radius:14px;padding:14px}.whStat b{font-size:24px;display:block}.whStat span{font-size:12px;color:var(--muted)}
    .whCells{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}
    .whCellCard{background:#fff;border:1px solid var(--line);border-radius:16px;padding:14px}
    .whCellMeta{display:flex;justify-content:space-between;gap:10px;margin:12px 0;font-size:12px;color:var(--muted)}
    .whMiniList{border-top:1px solid var(--line);padding-top:9px;margin-bottom:12px}
    .whMiniLine{display:flex;justify-content:space-between;gap:12px;padding:5px 0;font-size:13px}
    .whMiniLine b{color:#0f766e}
    .whInfo{margin-bottom:14px}
    @media(max-width:620px){.whStats{grid-template-columns:1fr}.whCells{grid-template-columns:1fr}}
  </style>
  <div class="contentHead"><div><div class="h1">Восполнение</div><p>Очередь формируется автоматически, когда остаток СИЗ становится меньше или равен Min.</p></div><div class="right"><button class="btn outline" id="whRefresh">Обновить</button></div></div>
  <div class="note whInfo"><b>Отчёт склада:</b> формируется из этой очереди. Расписание — <b>среда и пятница</b>. Автоматическую отправку Email включим после подключения почтового канала.</div>
  <div class="whStats">
    <div class="whStat"><b>${groups.length}</b><span>Ячеек к восполнению</span></div>
    <div class="whStat"><b>${totalPositions}</b><span>Позиций СИЗ</span></div>
    <div class="whStat"><b>${totalQty}</b><span>Единиц добавить</span></div>
  </div>
  <div class="whCells">${cards||'<div class="empty">Сейчас восполнение не требуется</div>'}</div>`;
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

// ===== v3.8 role entry screen: admin + warehouse =====
showBetaHome=function(push=true){
  if(push)setUi({screen:'home'},true);else uiState={screen:'home',role:null,tab:null};
  session.role=null;session.user=null;session.sid='DEMO-SID';session.connected=true;session.mode='DEMO';
  render(`<div class="betaHome">
    <div class="betaHero">
      <div class="betaTag">● БЕТА • РЕАЛЬНЫЙ ПОСТОМАТ ОТКЛЮЧЁН</div>
      <h1>Постомат СИЗ</h1>
      <p>Выберите рабочий контур. В тестовой версии команды на реальный контроллер не отправляются.</p>
      <div style="height:20px"></div>
      <div style="display:grid;gap:10px">
        <button id="betaWarehouse" class="btn" style="background:#fff;color:#0f4f99;width:100%;height:56px;font-size:16px">ВХОД ДЛЯ СКЛАДА</button>
        <button id="betaAdmin" class="btn" style="background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.55);color:#fff;width:100%;height:52px;font-size:15px">АДМИНИСТРИРОВАНИЕ</button>
      </div>
    </div>
    <div class="betaSteps">
      <div class="betaStep"><b>Склад</b><span>Восполнение ячеек, ревизия и история выполненных операций.</span></div>
      <div class="betaStep"><b>Администратор</b><span>Сотрудники, ячейки, СИЗ, назначения, остатки, журналы, отчёты и настройки.</span></div>
      <div class="betaStep"><b>Оператор</b><span>Контур выдачи СИЗ подключим отдельным этапом после проверки склада.</span></div>
    </div>
  </div>`);
  byId('betaWarehouse').onclick=()=>showUserLogin('WAREHOUSE');
  byId('betaAdmin').onclick=()=>showUserLogin('ADMIN');
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
