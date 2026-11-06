package com.patchhub.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class HexPatchTest {
    @Test public void parseAndReplace() {
        byte[] input = HexPatch.parse("01 FF 20 01 ff 20");
        HexPatch.Result result = HexPatch.replace(input, HexPatch.parse("01 ff"), HexPatch.parse("AA 00"));
        assertEquals(2, result.count);
        assertEquals(Arrays.asList(0, 3), result.offsets);
        assertArrayEquals(HexPatch.parse("AA 00 20 AA 00 20"), input);
    }
    @Test public void nonOverlappingAndNoMatch() {
        byte[] input = HexPatch.parse("aa aa aa");
        HexPatch.Result result = HexPatch.replace(input, HexPatch.parse("aa aa"), HexPatch.parse("bb bb"));
        assertEquals(1, result.count);
        assertArrayEquals(HexPatch.parse("bb bb aa"), input);
        assertEquals(0, HexPatch.replace(input, HexPatch.parse("cc"), HexPatch.parse("dd")).count);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectOddHex() { HexPatch.parse("ABC"); }
    @Test(expected = IllegalArgumentException.class) public void rejectInvalidHex() { HexPatch.parse("zz"); }
    @Test(expected = IllegalArgumentException.class) public void rejectLengthMismatch() { HexPatch.replace(new byte[]{1}, new byte[]{1}, new byte[]{1, 2}); }
}
