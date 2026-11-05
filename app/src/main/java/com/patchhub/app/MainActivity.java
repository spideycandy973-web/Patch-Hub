package com.patchhub.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private static final int APK = 1, REPLACEMENT = 2, OUTPUT = 3;
    private static final int WHITE = Color.rgb(241, 246, 255), MUTED = Color.rgb(165, 182, 209), BLUE = Color.rgb(131, 183, 255);
    private Uri apk, replacement;
    private final ArrayList<String> entries = new ArrayList<>();
    private final LinkedHashMap<String, Uri> queue = new LinkedHashMap<>();
    private TextView apkLabel, replacementLabel, status, queueLabel, entryCount;
    private EditText path;
    private Button scanButton, saveButton, chooseApkButton, chooseReplacementButton, addButton;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10, 16, 35));
        getWindow().setNavigationBarColor(Color.rgb(10, 16, 35));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff111b38, 0xff1f3050, 0xff17172f, 0xff0b1326}));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(22), dp(22), dp(36)); scroll.addView(root); setContentView(scroll);
        TextView eyebrow = text("PATCH HUB   /   WORKSPACE", 11, BLUE, true); root.addView(eyebrow);
        TextView title = text("Your APK,\nyour canvas.", 34, WHITE, true); title.setLineSpacing(dp(1), 1f); root.addView(title);
        TextView subtitle = text("Inspect. Prepare. Patch with intention.", 14, MUTED, false); subtitle.setPadding(0, dp(8), 0, dp(20)); root.addView(subtitle);

        LinearLayout source = card(root); source.addView(text("01   SOURCE APK", 12, BLUE, true));
        apkLabel = text("No APK selected", 18, WHITE, true); space(apkLabel, 12, 3); source.addView(apkLabel);
        entryCount = text("Select an APK to inspect its contents.", 13, MUTED, false); source.addView(entryCount);
        chooseApkButton = button("Choose APK", false); source.addView(chooseApkButton);
        scanButton = button("Browse & search files", false); source.addView(scanButton);

        LinearLayout edit = card(root); edit.addView(text("02   PATCH QUEUE", 12, BLUE, true));
        TextView explanation = text("Choose an existing entry, then select its replacement. Add multiple changes before exporting.", 14, MUTED, false); space(explanation, 10, 6); edit.addView(explanation);
        path = new EditText(this); path.setSingleLine(true); path.setTextColor(WHITE); path.setHintTextColor(MUTED); path.setTextSize(14); path.setHint("Entry path, e.g. assets/config.json"); path.setPadding(dp(14), dp(12), dp(14), dp(12)); path.setBackground(shape(0x55344060, 14, 0x557a97bc)); edit.addView(path);
        chooseReplacementButton = button("Choose replacement file", false); edit.addView(chooseReplacementButton);
        replacementLabel = text("No replacement selected", 13, MUTED, false); edit.addView(replacementLabel);
        addButton = button("Add to queue", false); edit.addView(addButton);
        queueLabel = text("Your queue is empty.", 13, MUTED, false); space(queueLabel, 8, 4); edit.addView(queueLabel);
        Button clear = button("Clear queue", false); edit.addView(clear);

        LinearLayout export = card(root); export.addView(text("03   EXPORT", 12, BLUE, true));
        TextView notice = text("Output is unsigned. Align and sign it before installation. Modifying code or resources may break an app; server-side data cannot be changed here.", 13, MUTED, false); space(notice, 10, 4); export.addView(notice);
        saveButton = button("Export unsigned APK  ↗", true); export.addView(saveButton);
        status = text("Use only with APKs you are authorized to modify.", 13, MUTED, false); space(status, 10, 0); export.addView(status);

        chooseApkButton.setOnClickListener(v -> { if (!busy) pick(APK, "application/vnd.android.package-archive"); });
        chooseReplacementButton.setOnClickListener(v -> { if (!busy) pick(REPLACEMENT, "*/*"); });
        scanButton.setOnClickListener(v -> { if (apk == null) status.setText("Choose an APK first."); else if (!busy) browse(); });
        addButton.setOnClickListener(v -> addToQueue());
        clear.setOnClickListener(v -> { if (!busy) { queue.clear(); updateQueue(); } });
        saveButton.setOnClickListener(v -> {
            if (busy) return;
            if (apk == null || queue.isEmpty()) { status.setText("Choose an APK and add at least one replacement."); return; }
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE, "patched-unsigned.apk"), OUTPUT);
        });
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private void space(View v, int top, int bottom) { v.setPadding(0, dp(top), 0, dp(bottom)); }
    private LinearLayout card(LinearLayout parent) { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(18), dp(18), dp(18), dp(18)); c.setBackground(shape(0x66374661, 22, 0x6685a2c7)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(14)); parent.addView(c, lp); return c; }
    private Button button(String label, boolean primary) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(primary ? 0xff10213c : WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(shape(primary ? 0xffb3d4ff : 0x553f5878, 14, primary ? 0xffd7ebff : 0x66819cbf)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48)); lp.setMargins(0, dp(10), 0, 0); b.setLayoutParams(lp); return b; }
    private void pick(int request, String type) { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), request); }
    private boolean validPath(String s) { if (s.isEmpty() || s.startsWith("/") || s.endsWith("/") || s.contains("\\") || signature(s)) return false; for (String part : s.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) return false; return true; }
    private void addToQueue() {
        if (busy) return;
        String target = path.getText().toString().trim();
        if (apk == null || replacement == null) { status.setText("Choose an APK and a replacement first."); return; }
        if (!validPath(target) || !entries.contains(target)) { status.setText("Choose an existing non-signature file from the APK."); return; }
        queue.put(target, replacement); replacement = null; replacementLabel.setText("No replacement selected"); updateQueue(); status.setText("Replacement added. Choose another file or export.");
    }
    private void updateQueue() { if (queue.isEmpty()) { queueLabel.setText("Your queue is empty."); return; } StringBuilder b = new StringBuilder(queue.size() + " replacement(s) ready\n"); for (Map.Entry<String, Uri> e : queue.entrySet()) b.append("\n• ").append(e.getKey()).append("  ←  ").append(name(e.getValue())); queueLabel.setText(b.toString()); }
    private void setBusy(boolean value) { busy = value; chooseApkButton.setEnabled(!value); chooseReplacementButton.setEnabled(!value); scanButton.setEnabled(!value); addButton.setEnabled(!value); saveButton.setEnabled(!value); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == APK) { apk = uri; entries.clear(); queue.clear(); replacement = null; path.setText(""); replacementLabel.setText("No replacement selected"); updateQueue(); apkLabel.setText(name(uri)); scan(uri); }
        else if (request == REPLACEMENT) { replacement = uri; replacementLabel.setText(name(uri)); }
        else if (request == OUTPUT) {
            if (uri.equals(apk) || queue.containsValue(uri)) { status.setText("Choose a different output document from every input."); return; }
            Uri source = apk; LinkedHashMap<String, Uri> patches = new LinkedHashMap<>(queue); setBusy(true); status.setText("Building patched APK…");
            new Thread(() -> { String msg; try { createPatch(source, patches, uri); msg = "Exported unsigned APK. Align and sign before installation; updates require the original signing key."; } catch (Exception e) { msg = "Export failed: " + e.getMessage() + ". The output may be incomplete; delete it if needed."; } final String resultMsg = msg; runOnUiThread(() -> { status.setText(resultMsg); setBusy(false); }); }).start();
        }
    }
    private void scan(Uri source) {
        setBusy(true); entryCount.setText("Inspecting archive…"); status.setText("Reading APK entries…");
        new Thread(() -> { ArrayList<String> found = new ArrayList<>(); String error = null; try (InputStream raw = getContentResolver().openInputStream(source)) { if (raw == null) throw new IOException("Unable to open APK"); try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) { ZipEntry e; Set<String> seen = new HashSet<>(); while ((e = zip.getNextEntry()) != null) { if (!seen.add(e.getName())) throw new IOException("Duplicate entries in archive"); if (!e.isDirectory() && !signature(e.getName())) found.add(e.getName()); if (seen.size() > 50000) throw new IOException("Archive exceeds 50,000 entries"); zip.closeEntry(); } } if (found.isEmpty()) throw new IOException("No files found in APK"); Collections.sort(found); } catch (Exception ex) { error = ex.getMessage(); } final String problem = error; runOnUiThread(() -> { entries.clear(); if (problem == null) { entries.addAll(found); entryCount.setText(entries.size() + " files available to browse"); status.setText("Archive inspected. Browse files to get started."); } else { apk = null; entryCount.setText("Could not inspect this archive."); status.setText("Inspection failed: " + problem); } setBusy(false); }); }).start();
    }
    private void browse() {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(12), dp(20), 0);
        EditText search = new EditText(this); search.setSingleLine(true); search.setHint("Search file paths"); content.addView(search);
        TextView count = new TextView(this); content.addView(count);
        ListView list = new ListView(this); content.addView(list, new LinearLayout.LayoutParams(-1, dp(380)));
        ArrayList<String> matches = new ArrayList<>(); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, matches); list.setAdapter(adapter);
        Runnable filter = () -> { String query = search.getText().toString().toLowerCase(Locale.ROOT); matches.clear(); int total = 0; for (String item : entries) if (item.toLowerCase(Locale.ROOT).contains(query)) { total++; if (matches.size() < 300) matches.add(item); } count.setText(total + " matches" + (total > 300 ? " · showing first 300" : "")); adapter.notifyDataSetChanged(); };
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int c, int a) {} public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); } public void afterTextChanged(Editable e) {} });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("APK explorer").setView(content).setNegativeButton("Close", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> { path.setText(matches.get(position)); dialog.dismiss(); }); filter.run(); dialog.show();
    }
    private String name(Uri uri) { try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) return c.getString(0); } catch (Exception ignored) {} return uri.getLastPathSegment(); }
    private void copy(InputStream in, OutputStream out) throws IOException { byte[] buffer = new byte[32768]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); }
    private boolean signature(String name) { String n = name.toUpperCase(Locale.ROOT); if (!n.startsWith("META-INF/")) return false; String file = n.substring(9); return !file.contains("/") && (file.equals("MANIFEST.MF") || file.endsWith(".SF") || file.endsWith(".RSA") || file.endsWith(".DSA") || file.endsWith(".EC") || file.startsWith("SIG-")); }
    private void createPatch(Uri source, Map<String, Uri> patches, Uri dest) throws IOException {
        Set<String> seen = new HashSet<>(), found = new HashSet<>();
        try (InputStream raw = getContentResolver().openInputStream(source); OutputStream output = getContentResolver().openOutputStream(dest, "w")) {
            if (raw == null || output == null) throw new IOException("Could not open selected files");
            try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw)); ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(output))) {
                ZipEntry item; while ((item = zin.getNextEntry()) != null) { String name = item.getName(); if (!seen.add(name)) throw new IOException("APK contains duplicate entries"); if (signature(name)) { zin.closeEntry(); continue; }
                    ZipEntry next = new ZipEntry(name); if (item.getMethod() == ZipEntry.STORED && !patches.containsKey(name)) { next.setMethod(ZipEntry.STORED); next.setSize(item.getSize()); next.setCrc(item.getCrc()); }
                    zout.putNextEntry(next); if (patches.containsKey(name)) { found.add(name); try (InputStream in = getContentResolver().openInputStream(patches.get(name))) { if (in == null) throw new IOException("Could not open replacement: " + name); copy(in, zout); } } else copy(zin, zout); zout.closeEntry(); zin.closeEntry();
                } if (found.size() != patches.size()) throw new IOException("One or more target files no longer exist");
            }
        }
    }
}
