package dev._2lstudios.chatsentinel.shared.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaceholderUtilTest {
    @Test
    public void replacePlaceholders_usesEmptyString_whenReplacementIsNull() {
        assertEquals("\u00a7aServer ", PlaceholderUtil.replacePlaceholders("&aServer %server%", new String[][] {
                { "%server%" },
                { null }
        }));
    }

    @Test
    public void replacePlaceholders_skipsNullSearchKey() {
        assertEquals("hello", PlaceholderUtil.replacePlaceholders("hello", new String[][] {
                { null },
                { "ignored" }
        }));
    }

    @Test
    public void replacePlaceholders_ignoresMalformedPairs() {
        assertEquals("hello %missing%", PlaceholderUtil.replacePlaceholders("hello %missing%", new String[][] {
                { "%missing%" }
        }));
    }
}