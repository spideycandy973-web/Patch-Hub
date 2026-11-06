package com.patchhub.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public class FileInspectorTest {
    @Test public void showsDigestHexAndStrings() throws Exception {
        String report = FileInspector.describe("hello".getBytes(StandardCharsets.US_ASCII));
        assertTrue(report.contains("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"));
        assertTrue(report.contains("68 65 6C 6C 6F"));
        assertTrue(report.contains("00000000  hello"));
    }
    @Test public void streamingLargeFile() throws Exception {
        final int length = 17 * 1024 * 1024;
        java.io.InputStream stream = new java.io.InputStream() {
            int remaining = length;
            @Override public int read() { if (remaining == 0) return -1; remaining--; return 0; }
            @Override public int read(byte[] buffer, int offset, int len) { if (remaining == 0) return -1; int n = Math.min(len, remaining); java.util.Arrays.fill(buffer, offset, offset + n, (byte)0); remaining -= n; return n; }
        };
        String report = FileInspector.describe(stream);
        assertTrue(report.contains("Size: " + length + " bytes"));
        assertTrue(report.contains("first 4096 bytes"));
    }
    @Test public void stringsCrossBufferBoundaries() throws Exception {
        byte[] data = new byte[32775];
        byte[] word = "abcdef".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(word, 0, data, 32766, word.length);
        assertTrue(FileInspector.describe(data).contains("00007FFE  abcdef"));
    }
    @Test public void emptyInputHasNoStrings() throws Exception {
        assertTrue(FileInspector.describe(new byte[0]).contains("(none found)"));
    }
}
