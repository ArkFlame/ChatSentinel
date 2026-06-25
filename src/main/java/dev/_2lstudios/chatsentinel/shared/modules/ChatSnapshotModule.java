package dev._2lstudios.chatsentinel.shared.modules;

public final class ChatSnapshotModule {
    public static final int DEFAULT_CLEAR_LINES = 128;

    private int clearLines = DEFAULT_CLEAR_LINES;

    public synchronized void loadData(final int clearLines) {
        this.clearLines = Math.max(1, Math.min(300, clearLines));
    }

    public String buildClearPayload(final String footerMessage) {
        final StringBuilder builder = new StringBuilder();
        appendBlankLines(builder);
        builder.append(safe(footerMessage));
        return builder.toString();
    }

    public int getClearLines() {
        return clearLines;
    }

    private void appendBlankLines(final StringBuilder builder) {
        for (int i = 0; i < clearLines; i++) {
            builder.append(" \n");
        }
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }
}
