package dev._2lstudios.chatsentinel.shared.modules;

public final class TextModerationSettings {
    private final boolean signsEnabled;
    private final boolean anvilsEnabled;
    private final boolean booksEnabled;
    private final boolean packetEventsEnabled;

    public TextModerationSettings(boolean signsEnabled, boolean anvilsEnabled, boolean booksEnabled,
            boolean packetEventsEnabled) {
        this.signsEnabled = signsEnabled;
        this.anvilsEnabled = anvilsEnabled;
        this.booksEnabled = booksEnabled;
        this.packetEventsEnabled = packetEventsEnabled;
    }

    public boolean isSignsEnabled() { return signsEnabled; }
    public boolean isAnvilsEnabled() { return anvilsEnabled; }
    public boolean isBooksEnabled() { return booksEnabled; }
    public boolean isPacketEventsEnabled() { return packetEventsEnabled; }
}
