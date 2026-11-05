package com.patchhub.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.*;

public class MainActivity extends Activity {
    private static final int APK = 1, REPLACEMENT = 2, OUTPUT = 3;
    private Uri apk, replacement;
    private TextView apkLabel, replacementLabel, status;
    private EditText path;
    private Button patch;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Patch Hub"); title.setTextSize(30); root.addView(title);
        TextView intro = new TextView(this);
        intro.setText("Replace one existing asset in an APK you own or have permission to modify. This creates an unsigned APK; it must be aligned and signed before it can be installed.");
        intro.setTextSize(16); root.addView(intro);
        Button chooseApk = new Button(this); chooseApk.setText("1. Choose source APK"); root.addView(chooseApk);
        apkLabel = new TextView(this); apkLabel.setText("No APK selected"); root.addView(apkLabel);
        Button chooseReplacement = new Button(this); chooseReplacement.setText("2. Choose replacement file"); root.addView(chooseReplacement);
        replacementLabel = new TextView(this); replacementLabel.setText("No replacement selected"); root.addView(replacementLabel);
        path = new EditText(this); path.setSingleLine(true); path.setHint("assets/example.json"); root.addView(path);
        patch = new Button(this); patch.setText("3. Save unsigned APK"); root.addView(patch);
        status = new TextView(this); status.setText("Enter the exact, case-sensitive asset path inside the APK."); status.setGravity(Gravity.START); root.addView(status);
        chooseApk.setOnClickListener(v -> pick(APK, "application/vnd.android.package-archive"));
        chooseReplacement.setOnClickListener(v -> pick(REPLACEMENT, "*/*"));
        patch.setOnClickListener(v -> {
            String entry = path.getText().toString().trim();
            if (apk == null || replacement == null) { status.setText("Choose both an APK and a replacement file."); return; }
            if (!validPath(entry)) { status.setText("Enter a valid path under assets/, for example assets/example.json."); return; }
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE, "patched-unsigned.apk");
            startActivityForResult(intent, OUTPUT);
        });
    }
    private boolean validPath(String s) {
        if (!s.startsWith("assets/") || s.endsWith("/") || s.contains("\\")) return false;
        for (String part : s.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return true;
    }
    private void pick(int request, String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
        startActivityForResult(intent, request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == APK) { apk = uri; apkLabel.setText(name(uri)); }
        else if (request == REPLACEMENT) { replacement = uri; replacementLabel.setText(name(uri)); }
        else if (request == OUTPUT) {
            if (uri.equals(apk) || uri.equals(replacement)) { status.setText("Choose a different output file to avoid overwriting an input."); return; }
            String entry = path.getText().toString().trim(); Uri source = apk, input = replacement;
            patch.setEnabled(false); status.setText("Creating APK…");
            new Thread(() -> {
                String message;
                try { createPatch(source, input, uri, entry); message = "Saved unsigned APK. Align and sign it before installation. Existing installations may require the original signing key."; }
                catch (Exception e) { try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {} message = "Patch failed: " + e.getMessage() + ". If an empty or partial output remains, delete it."; }
                final String resultMessage = message;
                runOnUiThread(() -> { status.setText(resultMessage); patch.setEnabled(true); });
            }).start();
        }
    }
    private String name(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }
    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[32768]; int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
    private boolean signature(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (!n.startsWith("META-INF/")) return false;
        String file = n.substring(9);
        return !file.contains("/") && (file.equals("MANIFEST.MF") || file.endsWith(".SF") || file.endsWith(".RSA") || file.endsWith(".DSA") || file.endsWith(".EC") || file.startsWith("SIG-"));
    }
    private void createPatch(Uri source, Uri replacementUri, Uri dest, String target) throws IOException {
        boolean found = false; Set<String> names = new HashSet<>();
        try (InputStream raw = getContentResolver().openInputStream(source);
             OutputStream output = getContentResolver().openOutputStream(dest, "w")) {
            if (raw == null || output == null) throw new IOException("Could not open selected files");
            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw)); ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(output))) {
                ZipEntry item;
                while ((item = zin.getNextEntry()) != null) {
                    String name = item.getName();
                    if (!names.add(name)) throw new IOException("APK contains duplicate entries");
                    if (signature(name)) { zin.closeEntry(); continue; }
                    ZipEntry next = new ZipEntry(name);
                    // Preserve STORED entries (notably native libraries), including their required metadata.
                    if (item.getMethod() == ZipEntry.STORED && !name.equals(target)) {
                        next.setMethod(ZipEntry.STORED); next.setSize(item.getSize()); next.setCrc(item.getCrc());
                    }
                    zout.putNextEntry(next);
                    if (name.equals(target)) {
                        found = true;
                        try (InputStream in = getContentResolver().openInputStream(replacementUri)) {
                            if (in == null) throw new IOException("Could not open replacement file");
                            copy(in, zout);
                        }
                    } else copy(zin, zout);
                    zout.closeEntry(); zin.closeEntry();
                }
                if (!found) throw new IOException("Asset not found: " + target);
            }
        }
    }
}
