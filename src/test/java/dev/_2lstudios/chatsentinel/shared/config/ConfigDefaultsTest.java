package dev._2lstudios.chatsentinel.shared.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ConfigDefaultsTest {
    @Test
    public void flatten_producesDottedPathsForNestedMaps() {
        final Map<String, Object> cancel = new LinkedHashMap<String, Object>();
        cancel.put("fallback", "the fallback");
        cancel.put("flood", "the flood text");
        final Map<String, Object> lang = new LinkedHashMap<String, Object>();
        lang.put("cancel", cancel);
        final Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("en", lang);

        final Map<String, Object> flat = ConfigDefaults.flatten(root);

        assertEquals("the fallback", flat.get("en.cancel.fallback"));
        assertEquals("the flood text", flat.get("en.cancel.flood"));
    }

    @Test
    public void missingPaths_reportsNewKeysOnly() {
        final Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        defaults.put("en.cancel.flood", "flood");
        defaults.put("en.cancel.fallback", "fallback");

        final List<String> missing = ConfigDefaults.missingPaths(
                Arrays.asList("en.cancel.flood"), defaults);

        assertEquals(Collections.singletonList("en.cancel.fallback"), missing);
    }

    @Test
    public void missingPaths_isEmpty_whenConfigIsCurrent() {
        final Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        defaults.put("en.cancel.flood", "flood");

        final List<String> missing = ConfigDefaults.missingPaths(
                Arrays.asList("en.cancel.flood", "en.extra"), defaults);

        assertTrue(missing.isEmpty());
    }

    @Test
    public void missingPaths_skipsNestedDefaults_whenLegacyScalarParentExists() {
        final Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        defaults.put("help.header", "header");
        defaults.put("help.lines", "lines");

        // A legacy file stores "help" as one scalar; adding help.header would destroy it.
        final List<String> missing = ConfigDefaults.missingPaths(
                Arrays.asList("help"), defaults);

        assertTrue("nested defaults must not overwrite a legacy scalar key", missing.isEmpty());
    }

    @Test
    public void missingPaths_stillAddsNewTopLevelKeys_whenSiblingsExist() {
        final Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        defaults.put("cancel.flood", "flood");
        defaults.put("cancel.fallback", "fallback");

        final List<String> missing = ConfigDefaults.missingPaths(
                Arrays.asList("cancel.flood"), defaults);

        assertEquals(Collections.singletonList("cancel.fallback"), missing);
    }

    @Test
    public void describeAdded_summarisesLongLists() {
        final List<String> added = Arrays.asList("a", "b", "c", "d", "e", "f", "g");

        final String summary = ConfigDefaults.describeAdded("messages.yml", added);

        assertTrue(summary.contains("7 new key(s)"));
        assertTrue(summary.contains("and 2 more"));
    }

    @Test
    public void describeAdded_returnsNull_whenNothingAdded() {
        assertEquals(null, ConfigDefaults.describeAdded("messages.yml", Collections.<String>emptyList()));
    }
}
