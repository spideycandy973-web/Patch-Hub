package com.patchhub.app;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Small bounded forensic summary; not a disassembler or decompiler. */
final class FileInspector {
    private static final int HEX_LIMIT = 4096, STRING_COUNT = 100, STRING_LENGTH = 160;
    static String describe(byte[] data) throws NoSuchAlgorithmException {
        StringBuilder out = new StringBuilder();
        out.append("Size: ").append(data.length).append(" bytes\nSHA-256: ");
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        out.append("\n\nHEX + ASCII (first ").append(Math.min(data.length, HEX_LIMIT)).append(" bytes)\n");
        for (int offset = 0; offset < Math.min(data.length, HEX_LIMIT); offset += 16) {
            out.append(String.format(Locale.ROOT, "%08X  ", offset));
            for (int i = 0; i < 16; i++) { if (offset + i < data.length) out.append(String.format(Locale.ROOT, "%02X ", data[offset + i] & 255)); else out.append("   "); }
            out.append(" ");
            for (int i = 0; i < 16 && offset + i < data.length; i++) { int b = data[offset + i] & 255; out.append(b >= 32 && b <= 126 ? (char)b : '.'); }
            out.append('\n');
        }
        out.append("\nPRINTABLE ASCII STRINGS (min 4; first 100)\n");
        int count = 0, i = 0;
        while (i < data.length && count < STRING_COUNT) {
            int start = i;
            while (i < data.length && (data[i] & 255) >= 32 && (data[i] & 255) <= 126) i++;
            int len = i - start;
            if (len >= 4) {
                out.append(String.format(Locale.ROOT, "%08X  ", start));
                for (int j = 0; j < Math.min(len, STRING_LENGTH); j++) out.append((char)(data[start + j] & 255));
                if (len > STRING_LENGTH) out.append("…");
                out.append('\n'); count++;
            }
            if (i == start) i++;
        }
        if (count == 0) out.append("(none found)\n");
        return out.toString();
    }
}
