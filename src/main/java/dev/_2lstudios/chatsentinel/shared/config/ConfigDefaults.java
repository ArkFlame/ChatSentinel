package dev._2lstudios.chatsentinel.shared.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helpers for adding newly bundled configuration keys to an existing file.
 *
 * <p>Merging is strictly additive: an owner's value is never overwritten, and only paths that
 * are absent from their file are written. This is what makes new messages such as
 * {@code cancel.fallback} appear after an update without resetting the file.</p>
 */
public final class ConfigDefaults {
    private ConfigDefaults() {
    }

    /** Flattens a nested map into dotted paths. Leaf values are kept as-is. */
    public static Map<String, Object> flatten(final Map<?, ?> root) {
        final Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (root == null) {
            return result;
        }
        flattenInto(result, "", root);
        return result;
    }

    private static void flattenInto(final Map<String, Object> result, final String prefix, final Map<?, ?> source) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            final String key = String.valueOf(entry.getKey());
            final String path = prefix.isEmpty() ? key : prefix + "." + key;
            final Object value = entry.getValue();
            if (value instanceof Map) {
                flattenInto(result, path, (Map<?, ?>) value);
            } else {
                result.put(path, value);
            }
        }
    }

    /**
     * Returns the dotted paths present in {@code defaults} but absent from {@code existing}.
     *
     * <p>A missing path is skipped when one of its ancestors already holds a value in the
     * owner's file. Older versions used flat keys such as {@code help}, while newer ones nest
     * {@code help.header} and {@code help.lines}; writing the nested keys would replace the
     * owner's text with a default. Legacy aliases keep the flat key readable instead.</p>
     *
     * @param existingPaths paths the owner's file already defines
     * @param defaults      flattened bundled defaults
     */
    public static List<String> missingPaths(final Iterable<String> existingPaths,
            final Map<String, Object> defaults) {
        final Set<String> present = new LinkedHashSet<String>();
        if (existingPaths != null) {
            for (String path : existingPaths) {
                present.add(path);
            }
        }
        final List<String> missing = new ArrayList<String>();
        for (String path : defaults.keySet()) {
            if (!present.contains(path) && !hasPresentAncestor(path, present)) {
                missing.add(path);
            }
        }
        return missing;
    }

    /** True when an ancestor of {@code path} already carries a value. */
    private static boolean hasPresentAncestor(final String path, final Set<String> present) {
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            if (present.contains(path.substring(0, dot))) {
                return true;
            }
            dot = path.lastIndexOf('.', dot - 1);
        }
        return false;
    }

    /** Human readable summary used by the startup log. */
    public static String describeAdded(final String fileName, final List<String> added) {
        if (added == null || added.isEmpty()) {
            return null;
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("Added ").append(added.size()).append(" new key(s) to ").append(fileName).append(": ");
        final int shown = Math.min(added.size(), 5);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(added.get(i));
        }
        if (added.size() > shown) {
            builder.append(", and ").append(added.size() - shown).append(" more");
        }
        return builder.toString();
    }
}
