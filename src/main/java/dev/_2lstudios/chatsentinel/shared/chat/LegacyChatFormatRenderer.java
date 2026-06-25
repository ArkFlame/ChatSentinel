package dev._2lstudios.chatsentinel.shared.chat;

import java.util.Locale;

public final class LegacyChatFormatRenderer {
    private static final String DEFAULT_FORMAT = "<%s> %s";

    public String render(final String format, final String displayName, final String playerName, final String message) {
        final String safeMessage = safe(message);
        final String safePlayerName = safe(playerName);
        final String safeDisplayName = displayName == null || displayName.isEmpty() ? safePlayerName : displayName;
        final String safeFormat = format == null || format.isEmpty() ? DEFAULT_FORMAT : format;
        try {
            return appendMessageIfMissing(String.format(safeFormat, safeDisplayName, safeMessage), safeMessage);
        } catch (final RuntimeException ignored) {
            return String.format(DEFAULT_FORMAT, safePlayerName, safeMessage);
        }
    }

    private static String appendMessageIfMissing(final String formatted, final String message) {
        final String safeFormatted = safe(formatted);
        final String safeMessage = safe(message);
        if (safeMessage.isEmpty() || containsMessage(safeFormatted, safeMessage)) {
            return safeFormatted;
        }
        if (safeFormatted.isEmpty()) {
            return safeMessage;
        }
        final char last = safeFormatted.charAt(safeFormatted.length() - 1);
        if (Character.isWhitespace(last)) {
            return safeFormatted + safeMessage;
        }
        return safeFormatted + " " + safeMessage;
    }

    private static boolean containsMessage(final String formatted, final String message) {
        final String normalizedFormatted = normalizeForContainment(formatted);
        final String normalizedMessage = normalizeForContainment(message);
        return !normalizedMessage.isEmpty() && normalizedFormatted.contains(normalizedMessage);
    }

    private static String normalizeForContainment(final String value) {
        final String stripped = stripColorCodes(safe(value));
        return stripped.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String stripColorCodes(final String value) {
        String result = value.replaceAll("§x(§[0-9A-Fa-f]){6}", "");
        result = result.replaceAll("§[0-9A-Fa-fK-Ok-oRr]", "");
        result = result.replaceAll("(?i)&[0-9A-FK-OR]", "");
        return result;
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }
}
