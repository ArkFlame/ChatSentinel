package dev._2lstudios.chatsentinel.shared.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ChatSnapshotModule {
    public static final int DEFAULT_HISTORY_SIZE = 50;
    public static final int DEFAULT_CLEAR_LINES = 128;
    public static final String DEFAULT_PROXY_REPLAY_FORMAT = "&7[%player%] &f%message%";
    public static final boolean DEFAULT_LIVE_DELETE_CLICK_ENABLED = true;
    public static final String DEFAULT_LIVE_DELETE_PERMISSION = "chatsentinel.delete";
    public static final String DEFAULT_LIVE_DELETE_PREFIX = "&8[&cX&8] ";
    public static final String DEFAULT_LIVE_DELETE_HOVER = "&eClick to delete this message.";
    public static final String DEFAULT_LIVE_DELETE_COMMAND = "/deletechat %id%";

    private boolean enabled = true;
    private int historySize = DEFAULT_HISTORY_SIZE;
    private int clearLines = DEFAULT_CLEAR_LINES;
    private String proxyReplayFormat = DEFAULT_PROXY_REPLAY_FORMAT;
    private boolean liveDeleteClickEnabled = DEFAULT_LIVE_DELETE_CLICK_ENABLED;
    private String liveDeletePermission = DEFAULT_LIVE_DELETE_PERMISSION;
    private String liveDeletePrefix = DEFAULT_LIVE_DELETE_PREFIX;
    private String liveDeleteHover = DEFAULT_LIVE_DELETE_HOVER;
    private String liveDeleteCommand = DEFAULT_LIVE_DELETE_COMMAND;
    private final Deque<Entry> entries = new ArrayDeque<Entry>(DEFAULT_HISTORY_SIZE);

    public synchronized void loadData(boolean enabled, int historySize, int clearLines, String proxyReplayFormat) {
        loadData(enabled, historySize, clearLines, proxyReplayFormat, DEFAULT_LIVE_DELETE_CLICK_ENABLED,
                DEFAULT_LIVE_DELETE_PERMISSION, DEFAULT_LIVE_DELETE_PREFIX, DEFAULT_LIVE_DELETE_HOVER,
                DEFAULT_LIVE_DELETE_COMMAND);
    }

    public synchronized void loadData(boolean enabled, int historySize, int clearLines, String proxyReplayFormat,
            boolean liveDeleteClickEnabled, String liveDeletePermission, String liveDeletePrefix, String liveDeleteHover,
            String liveDeleteCommand) {
        this.enabled = enabled;
        this.historySize = Math.max(1, Math.min(200, historySize));
        this.clearLines = Math.max(1, Math.min(300, clearLines));
        this.proxyReplayFormat = proxyReplayFormat == null || proxyReplayFormat.trim().isEmpty()
                ? DEFAULT_PROXY_REPLAY_FORMAT
                : proxyReplayFormat;
        this.liveDeleteClickEnabled = liveDeleteClickEnabled;
        this.liveDeletePermission = textOrDefault(liveDeletePermission, DEFAULT_LIVE_DELETE_PERMISSION);
        this.liveDeletePrefix = textOrDefault(liveDeletePrefix, DEFAULT_LIVE_DELETE_PREFIX);
        this.liveDeleteHover = textOrDefault(liveDeleteHover, DEFAULT_LIVE_DELETE_HOVER);
        this.liveDeleteCommand = textOrDefault(liveDeleteCommand, DEFAULT_LIVE_DELETE_COMMAND);
        trimHistory();
    }

    public synchronized Optional<Entry> record(UUID senderUuid, String senderName, String message, String renderedLine,
            Collection<UUID> recipientIds) {
        if (!enabled) {
            return Optional.empty();
        }
        Entry entry = new Entry(uniqueId(), System.currentTimeMillis(), senderUuid, safe(senderName), safe(message),
                safe(renderedLine), copyRecipients(recipientIds));
        entries.addLast(entry);
        trimHistory();
        return Optional.of(entry);
    }

    public synchronized Optional<Entry> markDeletedEntry(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        for (Entry entry : entries) {
            if (entry.getId().equalsIgnoreCase(id)) {
                entry.markDeleted();
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean addRecipient(final String id, final UUID recipientId) {
        if (id == null || id.trim().isEmpty() || recipientId == null) {
            return false;
        }
        for (final Entry entry : entries) {
            if (entry.getId().equalsIgnoreCase(id)) {
                entry.addRecipient(recipientId);
                return true;
            }
        }
        return false;
    }

    public synchronized List<Entry> getRecentEntries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(entries));
    }

    public synchronized List<Entry> getVisibleEntriesFor(UUID viewer) {
        List<Entry> visible = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (!entry.isDeleted() && entry.isVisibleTo(viewer)) {
                visible.add(entry);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    public String renderProxyLine(String player, String message) {
        return proxyReplayFormat.replace("%player%", safe(player)).replace("%message%", safe(message));
    }

    public String buildClearPayload(String footerMessage) {
        StringBuilder builder = new StringBuilder();
        appendBlankLines(builder);
        builder.append(safe(footerMessage));
        return builder.toString();
    }

    public synchronized String buildReplayPayload(UUID viewer) {
        StringBuilder builder = new StringBuilder();
        appendBlankLines(builder);
        for (Entry entry : entries) {
            if (!entry.isDeleted() && entry.isVisibleTo(viewer)) {
                builder.append(entry.getRenderedLine()).append('\n');
            }
        }
        return builder.toString();
    }

    public int getHistorySize() {
        return historySize;
    }

    public int getClearLines() {
        return clearLines;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isLiveDeleteClickEnabled() {
        return liveDeleteClickEnabled;
    }

    public String getLiveDeletePermission() {
        return liveDeletePermission;
    }

    public String getLiveDeletePrefix() {
        return liveDeletePrefix;
    }

    public String getLiveDeleteHover() {
        return liveDeleteHover;
    }

    public String buildLiveDeleteCommand(final String id) {
        return liveDeleteCommand.replace("%id%", safe(id));
    }

    private void appendBlankLines(StringBuilder builder) {
        for (int i = 0; i < clearLines; i++) {
            builder.append(" \n");
        }
    }

    private String uniqueId() {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8);
        } while (containsId(id));
        return id;
    }

    private boolean containsId(String id) {
        for (Entry entry : entries) {
            if (entry.getId().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private Set<UUID> copyRecipients(Collection<UUID> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<UUID>(recipientIds);
    }

    private void trimHistory() {
        while (entries.size() > historySize) {
            entries.removeFirst();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String textOrDefault(final String value, final String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static final class Entry {
        private final String id;
        private final long timestamp;
        private final UUID senderUuid;
        private final String senderName;
        private final String message;
        private final String renderedLine;
        private final Set<UUID> recipientIds;
        private boolean deleted;

        private Entry(String id, long timestamp, UUID senderUuid, String senderName, String message,
                String renderedLine, Set<UUID> recipientIds) {
            this.id = id;
            this.timestamp = timestamp;
            this.senderUuid = senderUuid;
            this.senderName = senderName;
            this.message = message;
            this.renderedLine = renderedLine;
            this.recipientIds = new HashSet<UUID>(recipientIds);
        }

        public String getId() { return id; }

        public long getTimestamp() { return timestamp; }

        public UUID getSenderUuid() { return senderUuid; }

        public String getSenderName() { return senderName; }

        public String getMessage() { return message; }

        public String getRenderedLine() { return renderedLine; }

        public Set<UUID> getRecipientIds() { return Collections.unmodifiableSet(new HashSet<UUID>(recipientIds)); }

        public boolean isDeleted() { return deleted; }

        public boolean isVisibleTo(UUID viewer) {
            return recipientIds.isEmpty() || (viewer != null && recipientIds.contains(viewer));
        }

        public String getDisplayLine() {
            return deleted ? "[deleted]" : renderedLine;
        }

        private void markDeleted() {
            this.deleted = true;
        }

        private void addRecipient(final UUID recipientId) {
            if (recipientId != null) {
                recipientIds.add(recipientId);
            }
        }
    }
}
