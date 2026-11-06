package com.patchhub.app;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, non-overlapping exact-byte replacements. */
final class HexPatch {
    static byte[] parse(String text) {
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty() || compact.length() % 2 != 0 || compact.length() > 256 || !compact.matches("[0-9a-fA-F]+"))
            throw new IllegalArgumentException("Enter 1–128 bytes as hexadecimal pairs, for example 0A FF 20.");
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    static Result replace(byte[] data, byte[] needle, byte[] replacement) {
        if (needle.length == 0 || needle.length != replacement.length) throw new IllegalArgumentException("Find and replacement must have the same nonzero length.");
        int[] prefix = new int[needle.length];
        for (int i = 1, j = 0; i < needle.length; i++) {
            while (j > 0 && needle[i] != needle[j]) j = prefix[j - 1];
            if (needle[i] == needle[j]) j++;
            prefix[i] = j;
        }
        int count = 0; List<Integer> offsets = new ArrayList<>();
        for (int i = 0, j = 0; i < data.length; i++) {
            while (j > 0 && data[i] != needle[j]) j = prefix[j - 1];
            if (data[i] == needle[j]) j++;
            if (j == needle.length) {
                int start = i - needle.length + 1;
                if (offsets.size() < 5) offsets.add(start);
                System.arraycopy(replacement, 0, data, start, replacement.length);
                count++; j = 0; // only non-overlapping matches
            }
        }
        return new Result(count, offsets);
    }
    static final class Result {
        final int count;
        final List<Integer> offsets;
        Result(int count, List<Integer> offsets) { this.count = count; this.offsets = offsets; }
    }
}
