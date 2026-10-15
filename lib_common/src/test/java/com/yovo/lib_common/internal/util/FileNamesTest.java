package com.yovo.lib_common.internal.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FileNamesTest {

    @Test
    public void sanitize_stripsPathAndReservedCharacters() {
        assertEquals("a_b_c.txt", FileNames.sanitize("a/b\\c.txt", "fallback"));
        assertEquals("fallback", FileNames.sanitize("", "fallback"));
        assertEquals("fallback", FileNames.sanitize("..", "fallback"));
        assertEquals("a_b.txt", FileNames.sanitize("a:b.txt", "fallback"));
    }
}
