package com.ggec.earbuds.ui.ai.markdown;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MdLinksTest {

    @Test
    public void webAndMailLinksAreSafe() {
        assertTrue(MdLinks.isSafe("https://example.com/a?b=c#d"));
        assertTrue(MdLinks.isSafe("HTTP://EXAMPLE.COM"));
        assertTrue(MdLinks.isSafe("  https://example.com  "));
        assertTrue(MdLinks.isSafe("mailto:someone@example.com"));
    }

    @Test
    public void everythingElseIsRefused() {
        assertFalse(MdLinks.isSafe(null));
        assertFalse(MdLinks.isSafe(""));
        assertFalse(MdLinks.isSafe("javascript:alert(1)"));
        assertFalse(MdLinks.isSafe("intent://scan/#Intent;scheme=zxing;end"));
        assertFalse(MdLinks.isSafe("file:///sdcard/secret.txt"));
        assertFalse(MdLinks.isSafe("content://com.android.contacts/contacts"));
        assertFalse(MdLinks.isSafe("tel:10086"));
        assertFalse(MdLinks.isSafe("example.com"));
        assertFalse(MdLinks.isSafe("https://"));
        assertFalse(MdLinks.isSafe("mailto:"));
    }

    @Test
    public void addressesWithEmbeddedWhitespaceOrControlCharactersAreRefused() {
        assertFalse(MdLinks.isSafe("https://exa mple.com"));
        assertFalse(MdLinks.isSafe("https://example.com/\u0000"));
        assertFalse(MdLinks.isSafe("https://example.com/\njavascript:alert(1)"));
    }
}
