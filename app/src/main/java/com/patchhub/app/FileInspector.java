package com.patchhub.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Streaming forensic summary; not a disassembler or decompiler. */
final class FileInspector {
    private static final int HEX_LIMIT = 4096, STRING_COUNT = 100, STRING_LENGTH = 160;
    static String describe(byte[] data) throws IOException, NoSuchAlgorithmException { return describe(new ByteArrayInputStream(data)); }
    static String describe(InputStream input) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream preview = new ByteArrayOutputStream(HEX_LIMIT);
        StringBuilder strings = new StringBuilder(), current = new StringBuilder(STRING_LENGTH);
        byte[] buffer = new byte[32768]; long size = 0, stringStart = 0, stringLen = 0; int count = 0, n;
        while ((n = input.read(buffer)) != -1) {
            if (n == 0) continue;
            digest.update(buffer, 0, n);
            int remaining = HEX_LIMIT - preview.size();
            if (remaining > 0) preview.write(buffer, 0, Math.min(remaining, n));
            for (int i = 0; i < n && count < STRING_COUNT; i++) {
                int b = buffer[i] & 255;
                if (b >= 32 && b <= 126) {
                    if (stringLen == 0) stringStart = size + i;
                    stringLen++;
                    if (current.length() < STRING_LENGTH) current.append((char)b);
                } else {
                    if (stringLen >= 4) { appendString(strings, stringStart, current, stringLen); count++; }
                    current.setLength(0); stringLen = 0;
                }
            }
            size += n;
        }
        if (count < STRING_COUNT && stringLen >= 4) { appendString(strings, stringStart, current, stringLen); count++; }
        byte[] first = preview.toByteArray();
        StringBuilder out = new StringBuilder();
        out.append("Size: ").append(size).append(" bytes\nSHA-256: ");
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        out.append("\n\nHEX + ASCII (first ").append(first.length).append(" bytes)\n");
        for (int offset = 0; offset < first.length; offset += 16) {
            out.append(String.format(Locale.ROOT, "%08X  ", offset));
            for (int i = 0; i < 16; i++) { if (offset + i < first.length) out.append(String.format(Locale.ROOT, "%02X ", first[offset + i] & 255)); else out.append("   "); }
            out.append(" ");
            for (int i = 0; i < 16 && offset + i < first.length; i++) { int b = first[offset + i] & 255; out.append(b >= 32 && b <= 126 ? (char)b : '.'); }
            out.append('\n');
        }
        out.append("\nPRINTABLE ASCII STRINGS (min 4; first 100)\n");
        if (count == 0) out.append("(none found)\n"); else out.append(strings);
        return out.toString();
    }
    private static void appendString(StringBuilder out, long offset, StringBuilder text, long length) {
        out.append(String.format(Locale.ROOT, "%08X  ", offset)).append(text);
        if (length > STRING_LENGTH) out.append("…");
        out.append('\n');
    }
}
