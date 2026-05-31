package dev._2lstudios.chatsentinel.shared.utils;

public class PlaceholderUtil {
    public static String replacePlaceholders(String string, String[] ...placeholders) {
        string = string == null ? "" : string.replace('\u0026', '\u00a7');

        if (placeholders == null || placeholders.length < 2 || placeholders[0] == null || placeholders[1] == null) {
            return string;
        }

        final String[] ids = placeholders[0];
        final String[] values = placeholders[1];
        final int length = Math.min(ids.length, values.length);

        for (int i = 0; i < length; i++) {
            final String id = ids[i];
            if (id == null || id.isEmpty()) {
                continue;
            }

            final String value = values[i] == null ? "" : values[i];
            string = string.replace(id, value);
        }

        return string;
    }
}