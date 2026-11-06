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
    @Test public void emptyInputHasNoStrings() throws Exception {
        assertTrue(FileInspector.describe(new byte[0]).contains("(none found)"));
    }
}
