package dev._2lstudios.chatsentinel.shared.chat;

public final class LegacyChatFormatRenderer {
    private static final String DEFAULT_FORMAT = "<%s> %s";

    public String render(final String format, final String displayName, final String playerName, final String message) {
        final String safeMessage = safe(message);
        final String safePlayerName = safe(playerName);
        final String safeDisplayName = displayName == null || displayName.isEmpty() ? safePlayerName : displayName;
        final String safeFormat = format == null || format.isEmpty() ? DEFAULT_FORMAT : format;
        try {
            return String.format(safeFormat, safeDisplayName, safeMessage);
        } catch (final RuntimeException ignored) {
            return String.format(DEFAULT_FORMAT, safePlayerName, safeMessage);
        }
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }
}