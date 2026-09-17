package com.rsteel.postomatsiz.saftest;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int REQ_CREATE_XLSX = 1001;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(245, 248, 252));

        TextView title = new TextView(this);
        title.setText("Тест штатного сохранения");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(23, 32, 51));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("Сборка обычным Android toolchain. Разрешение на доступ ко всей памяти не используется. Нажмите кнопку и выберите папку в системном окне Huawei.");
        info.setTextSize(15);
        info.setTextColor(Color.rgb(90, 105, 125));
        info.setPadding(0, dp(18), 0, dp(18));
        root.addView(info, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button save = new Button(this);
        save.setText("СОХРАНИТЬ ТЕСТОВЫЙ XLSX");
        save.setAllCaps(false);
        save.setTextSize(16);
        save.setOnClickListener(v -> openSystemSaveDialog());
        root.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        status = new TextView(this);
        status.setText("Ожидание проверки");
        status.setTextSize(14);
        status.setTextColor(Color.rgb(65, 80, 100));
        status.setPadding(0, dp(18), 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void openSystemSaveDialog() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, "Postomat_SIZ_SAF_TEST.xlsx");
        try {
            startActivityForResult(intent, REQ_CREATE_XLSX);
            status.setText("Открыто системное окно сохранения…");
        } catch (ActivityNotFoundException ex) {
            status.setText("На устройстве не найден системный выбор файла: " + ex.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CREATE_XLSX) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            status.setText("Сохранение отменено.");
            return;
        }

        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IOException("ContentResolver вернул пустой OutputStream");
            out.write(buildMinimalXlsx());
            out.flush();
            status.setText("УСПЕХ: XLSX сохранён через системное окно.\n" + uri);
        } catch (Exception ex) {
            status.setText("ОШИБКА ЗАПИСИ: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private byte[] buildMinimalXlsx() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "</Types>");

            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>");

            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"Тест\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                    "</workbook>");

            put(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>");

            put(zip, "xl/worksheets/sheet1.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                    "<sheetData>" +
                    "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Постомат СИЗ</t></is></c></row>" +
                    "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t>Системное сохранение XLSX работает</t></is></c></row>" +
                    "</sheetData></worksheet>");
        }
        return bytes.toByteArray();
    }

    private void put(ZipOutputStream zip, String name, String text) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
