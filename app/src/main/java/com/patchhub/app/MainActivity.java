package com.patchhub.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;
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
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private static final int APK = 1, REPLACEMENT = 2, OUTPUT = 3, EXTRACT = 4;
    private static final int MAX_TEXT = 256 * 1024;
    private static final int WHITE = Color.rgb(241, 246, 255), MUTED = Color.rgb(165, 182, 209), BLUE = Color.rgb(131, 183, 255);
    private Uri apk, replacement;
    private final ArrayList<String> entries = new ArrayList<>();
    private final LinkedHashMap<String, Uri> queue = new LinkedHashMap<>();
    private TextView apkLabel, replacementLabel, status, queueLabel, entryCount;
    private EditText path;
    private Button scanButton, saveButton, chooseApkButton, chooseReplacementButton, addButton;
    private boolean busy;
    private TextView shizukuStatus;
    private final Shizuku.OnBinderReceivedListener binderReceived = () -> runOnUiThread(this::refreshShizuku);
    private final Shizuku.OnBinderDeadListener binderDead = () -> runOnUiThread(this::refreshShizuku);
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> runOnUiThread(() -> {
        refreshShizuku();
        if (requestCode == 100 && grantResult != PackageManager.PERMISSION_GRANTED) shizukuStatus.setText("Permission denied. You can change this in Shizuku settings.");
    });
    private String extractTarget;

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

        LinearLayout bridge = card(root); bridge.addView(text("SHIZUKU   /   OPTIONAL ACCESS", 12, BLUE, true));
        shizukuStatus = text("Checking connection…", 14, MUTED, false); space(shizukuStatus, 10, 4); bridge.addView(shizukuStatus);
        Button connect = button("Connect Shizuku", false); bridge.addView(connect);
        TextView bridgeHelp = text("Install and start Shizuku first. On Android 11+ use wireless debugging; older versions normally need a computer. Shizuku provides shell access, not universal patching or access to private app data.", 13, MUTED, false); space(bridgeHelp, 10, 0); bridge.addView(bridgeHelp);
        connect.setOnClickListener(v -> requestShizuku());
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);
        refreshShizuku();

        LinearLayout source = card(root); source.addView(text("01   SOURCE APK", 12, BLUE, true));
        apkLabel = text("No APK selected", 18, WHITE, true); space(apkLabel, 12, 3); source.addView(apkLabel);
        entryCount = text("Select an APK to inspect its contents.", 13, MUTED, false); source.addView(entryCount);
        chooseApkButton = button("Choose APK", false); source.addView(chooseApkButton);
        scanButton = button("Browse & search files", false); source.addView(scanButton);

        LinearLayout edit = card(root); edit.addView(text("02   PATCH QUEUE", 12, BLUE, true));
        TextView explanation = text("Choose an existing entry, then select its replacement. Add multiple changes before exporting.", 14, MUTED, false); space(explanation, 10, 6); edit.addView(explanation);
        path = new EditText(this); path.setSingleLine(true); path.setTextColor(WHITE); path.setHintTextColor(MUTED); path.setTextSize(14); path.setHint("Entry path, e.g. assets/config.json"); path.setPadding(dp(14), dp(12), dp(14), dp(12)); path.setBackground(shape(0x55344060, 14, 0x557a97bc)); edit.addView(path);
        Button hexEdit = button("Binary patch · find & replace hex", false); edit.addView(hexEdit);
        Button textEdit = button("Edit selected text file", false); edit.addView(textEdit);
        Button extract = button("Extract selected original file", false); edit.addView(extract);
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
        hexEdit.setOnClickListener(v -> showHexEditor());
        textEdit.setOnClickListener(v -> editTextEntry());
        extract.setOnClickListener(v -> {
            if (busy) return;
            String target = selectedTarget(); if (target == null) return;
            extractTarget = target;
            String fileName = target.substring(target.lastIndexOf('/') + 1);
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, fileName), EXTRACT);
        });
        clear.setOnClickListener(v -> { if (!busy) { queue.clear(); updateQueue(); } });
        saveButton.setOnClickListener(v -> {
            if (busy) return;
            if (apk == null || queue.isEmpty()) { status.setText("Choose an APK and add at least one replacement."); return; }
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE, "patched-unsigned.apk"), OUTPUT);
        });
    }
    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        super.onDestroy();
    }
    private void refreshShizuku() {
        try {
            if (!Shizuku.pingBinder()) { shizukuStatus.setText("Not connected · Start Shizuku, then return here."); return; }
            if (Shizuku.isPreV11()) { shizukuStatus.setText("Shizuku is too old. Install version 11 or newer."); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { shizukuStatus.setText("Available · Permission not granted."); return; }
            int uid = Shizuku.getUid();
            String identity = uid == 0 ? "root" : uid == 2000 ? "ADB shell" : "UID " + uid;
            shizukuStatus.setText("Connected · " + identity + " · Shizuku API " + Shizuku.getVersion() + "\nConnection diagnostics only; APK edits still use the document picker.");
        } catch (RuntimeException e) { shizukuStatus.setText("Shizuku unavailable: " + e.getMessage()); }
    }
    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) { refreshShizuku(); return; }
            if (Shizuku.isPreV11()) { refreshShizuku(); return; }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { refreshShizuku(); return; }
            if (Shizuku.shouldShowRequestPermissionRationale()) { shizukuStatus.setText("Permission was denied permanently. Enable Patch Hub in Shizuku settings."); return; }
            Shizuku.requestPermission(100);
        } catch (RuntimeException e) { shizukuStatus.setText("Unable to request Shizuku access: " + e.getMessage()); }
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
        else if (request == EXTRACT) {
            if (extractTarget == null || apk == null) { status.setText("Select an APK file again."); return; }
            if (uri.equals(apk) || queue.containsValue(uri)) { status.setText("Choose a different output document from every input."); return; }
            Uri source = apk; String target = extractTarget; setBusy(true); status.setText("Extracting original file…");
            new Thread(() -> { String msg; try (InputStream raw = getContentResolver().openInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (raw == null || out == null) throw new IOException("Could not open selected files");
                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) { ZipEntry item; boolean found = false; while ((item = zip.getNextEntry()) != null) { if (item.getName().equals(target)) { copy(zip, out); found = true; break; } zip.closeEntry(); } if (!found) throw new IOException("Entry not found"); }
                msg = "Original file extracted.";
            } catch (Exception e) { msg = "Extraction failed: " + e.getMessage() + ". Output may be incomplete."; } final String resultMsg = msg; runOnUiThread(() -> { status.setText(resultMsg); setBusy(false); }); }).start();
        }
        else if (request == OUTPUT) {
            if (uri.equals(apk) || queue.containsValue(uri)) { status.setText("Choose a different output document from every input."); return; }
            Uri source = apk; LinkedHashMap<String, Uri> patches = new LinkedHashMap<>(queue); setBusy(true); status.setText("Building patched APK…");
            new Thread(() -> { String msg; try { createPatch(source, patches, uri); msg = "Exported unsigned APK. Align and sign before installation; updates require the original signing key."; } catch (Exception e) { msg = "Export failed: " + e.getMessage() + ". The output may be incomplete; delete it if needed."; } final String resultMsg = msg; runOnUiThread(() -> { status.setText(resultMsg); setBusy(false); }); }).start();
        }
    }
    private void showHexEditor() {
        if (busy) return;
        final String target = selectedTarget(); if (target == null) return;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), 0, dp(20), 0);
        EditText find = new EditText(this); find.setSingleLine(true); find.setHint("Find hex bytes, e.g. 01 FF 20"); form.addView(find);
        EditText replace = new EditText(this); replace.setSingleLine(true); replace.setHint("Replace with hex bytes (same length)"); form.addView(replace);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Binary patch").setMessage(target + "\nReplaces all non-overlapping matches in this one entry. Limit: 16 MiB. Raw edits can break code, checksums and app integrity.").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Find matches", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            byte[] needle, replacement;
            try { needle = HexPatch.parse(find.getText().toString()); replacement = HexPatch.parse(replace.getText().toString()); if (needle.length != replacement.length) throw new IllegalArgumentException("Find and replacement must be the same length."); }
            catch (IllegalArgumentException e) { find.setError(e.getMessage()); return; }
            Uri source = apk, staged = queue.get(target); dialog.dismiss(); setBusy(true); status.setText("Searching selected entry…");
            new Thread(() -> {
                byte[] data = null; HexPatch.Result result = null; String problem = null;
                try { data = loadEntry(source, staged, target, 16 * 1024 * 1024); result = HexPatch.replace(data, needle, replacement); }
                catch (Exception e) { problem = e.getMessage(); }
                final byte[] patched = data; final HexPatch.Result matches = result; final String error = problem;
                runOnUiThread(() -> {
                    setBusy(false);
                    if (error != null) { status.setText("Binary patch failed: " + error); return; }
                    if (matches.count == 0) { status.setText("No matching bytes found; nothing changed."); return; }
                    StringBuilder offsets = new StringBuilder(); for (int offset : matches.offsets) { if (offsets.length() > 0) offsets.append(", "); offsets.append(String.format(Locale.ROOT, "0x%X", offset)); }
                    new AlertDialog.Builder(this).setTitle("Confirm binary patch").setMessage(matches.count + " non-overlapping match(es). First offsets: " + offsets + "\n\nStage these changes? The original APK stays unchanged; modified APKs may not work.").setNegativeButton("Cancel", null).setPositiveButton("Stage changes", (confirmation, w) -> {
                        try { File file = File.createTempFile("patch-", ".bin", getCacheDir()); try (FileOutputStream out = new FileOutputStream(file)) { out.write(patched); } queue.put(target, Uri.fromFile(file)); updateQueue(); status.setText("Binary patch staged for " + target + ". Export to create the APK."); }
                        catch (IOException e) { status.setText("Could not stage patch: " + e.getMessage()); }
                    }).show();
                });
            }).start();
        })); dialog.show();
    }
    private byte[] loadEntry(Uri source, Uri staged, String target, int limit) throws IOException {
        if (staged != null) { try (InputStream in = getContentResolver().openInputStream(staged)) { if (in == null) throw new IOException("Cannot open queued replacement"); return readBounded(in, limit); } }
        try (InputStream raw = getContentResolver().openInputStream(source)) {
            if (raw == null) throw new IOException("Cannot open APK");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) { ZipEntry e; while ((e = zip.getNextEntry()) != null) { if (e.getName().equals(target)) return readBounded(zip, limit); zip.closeEntry(); } }
        }
        throw new IOException("File not found");
    }
    private String selectedTarget() {
        String target = path.getText().toString().trim();
        if (apk == null || !validPath(target) || !entries.contains(target)) { status.setText("Choose an existing file in the APK explorer first."); return null; }
        return target;
    }
    private byte[] readBounded(InputStream in) throws IOException { return readBounded(in, MAX_TEXT); }
    private byte[] readBounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) != -1) { if (out.size() + n > limit) throw new IOException("Entry exceeds size limit (" + (limit / 1024) + " KiB)"); out.write(buf, 0, n); }
        return out.toByteArray();
    }
    private void editTextEntry() {
        if (busy) return;
        String target = selectedTarget(); if (target == null) return;
        Uri source = apk, staged = queue.get(target); setBusy(true); status.setText("Opening text editor…");
        new Thread(() -> {
            String value = null, problem = null;
            try {
                byte[] bytes = null;
                if (staged != null) {
                    try (InputStream in = getContentResolver().openInputStream(staged)) { if (in == null) throw new IOException("Cannot open queued replacement"); bytes = readBounded(in); }
                } else {
                    try (InputStream raw = getContentResolver().openInputStream(source)) {
                        if (raw == null) throw new IOException("Cannot open APK");
                        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) { ZipEntry e; while ((e = zip.getNextEntry()) != null) { if (e.getName().equals(target)) { bytes = readBounded(zip); break; } zip.closeEntry(); } }
                    }
                }
                if (bytes == null) throw new IOException("File not found");
                for (byte b : bytes) if (b == 0) throw new IOException("Binary files cannot be edited as text");
                value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (Exception e) { problem = e.getMessage(); }
            final String loaded = value, error = problem;
            runOnUiThread(() -> {
                setBusy(false);
                if (error != null) { status.setText("Cannot edit: " + error); return; }
                EditText editor = new EditText(this); editor.setText(loaded); editor.setTextSize(14); editor.setTypeface(Typeface.MONOSPACE); editor.setGravity(Gravity.TOP | Gravity.START); editor.setSingleLine(false); editor.setMinLines(10); editor.setMaxLines(18); editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Edit UTF-8 text").setMessage(target + "\nSaved edits are staged in the queue, not applied to the original.").setView(editor).setNegativeButton("Cancel", null).setPositiveButton("Stage edit", null).create();
                dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    byte[] data = editor.getText().toString().getBytes(StandardCharsets.UTF_8);
                    if (data.length > MAX_TEXT) { editor.setError("Text editor limit is 256 KiB"); return; }
                    try { File file = File.createTempFile("patch-", ".txt", getCacheDir()); try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); } queue.put(target, Uri.fromFile(file)); updateQueue(); status.setText("Text edit staged. Export to create the patched APK."); dialog.dismiss(); }
                    catch (IOException e) { status.setText("Could not stage edit: " + e.getMessage()); }
                })); dialog.show();
            });
        }).start();
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
