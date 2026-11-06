package com.patchhub.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class PatchLinkTest {
    @Test public void githubBlobToRaw() throws Exception { assertEquals("https://raw.githubusercontent.com/alice/patches/main/patch.json", PatchLink.normalize("https://github.com/alice/patches/blob/main/patch.json")); }
    @Test public void directHttps() throws Exception { assertEquals("https://example.com/x.json?version=1", PatchLink.normalize("https://example.com/x.json?version=1")); }
    @Test(expected = IllegalArgumentException.class) public void rejectHttp() throws Exception { PatchLink.normalize("http://example.com/x.json"); }
    @Test(expected = IllegalArgumentException.class) public void rejectCredentials() throws Exception { PatchLink.normalize("https://user:pass@example.com/x.json"); }
    @Test(expected = IllegalArgumentException.class) public void rejectRepoPage() throws Exception { PatchLink.normalize("https://github.com/alice/repo"); }
}
