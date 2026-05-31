package dev._2lstudios.chatsentinel.bukkit.chat;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev._2lstudios.chatsentinel.shared.modules.ChatSnapshotModule;
import dev._2lstudios.chatsentinel.shared.text.LegacyText;

public final class BukkitOutboundChatDecorationTracker {

    private static final long PENDING_TTL_MILLIS = 3000L;
    private static final int MAX_PENDING = 64;

    private final Deque<PendingMessage> pending = new ArrayDeque<PendingMessage>();

    public synchronized void trackCandidate(final UUID senderUuid, final String senderName, final String message,
            final long nowMillis) {
        purgeExpired(nowMillis);
        if (senderUuid == null || message == null || message.trim().isEmpty()) {
            return;
        }
        final String normalizedMessage = normalize(message);
        if (normalizedMessage.isEmpty()) {
            return;
        }
        final PendingMessage item = new PendingMessage(senderUuid, senderName, message, normalizedMessage, nowMillis);
        while (pending.size() >= MAX_PENDING) {
            pending.removeFirst();
        }
        pending.addLast(item);
    }

    public synchronized void markFinalLine(final UUID senderUuid, final String message, final String renderedLine,
            final Collection<UUID> recipientIds, final ChatSnapshotModule snapshotModule, final long nowMillis) {
        if (senderUuid == null || message == null) {
            return;
        }
        final String normalizedMessage = normalize(message);
        for (final PendingMessage item : pending) {
            if (item.getSenderUuid().equals(senderUuid) && item.getNormalizedMessage().equals(normalizedMessage)) {
                if (renderedLine != null && !renderedLine.isEmpty()) {
                    item.setRenderedLine(renderedLine);
                    if (recipientIds != null && !recipientIds.isEmpty()) {
                        for (final UUID recipientId : recipientIds) {
                            item.addRecipientId(recipientId);
                        }
                    }
                    if (snapshotModule != null && snapshotModule.isEnabled()) {
                        final Optional<ChatSnapshotModule.Entry> recorded = snapshotModule.record(senderUuid,
                                item.getSenderName(), item.getMessage(), renderedLine, recipientIds);
                        if (recorded.isPresent()) {
                            item.setEntryId(recorded.get().getId());
                        }
                    }
                }
                return;
            }
        }
    }

    public synchronized Optional<DecorationMatch> match(final UUID viewerUuid, final String legacyLine,
            final ChatSnapshotModule snapshotModule, final long nowMillis) {
        purgeExpired(nowMillis);
        if (legacyLine == null || legacyLine.isEmpty()) {
            return Optional.empty();
        }
        final String strippedLine = stripColorCodes(legacyLine);
        final String normalizedLine = normalize(strippedLine);
        if (normalizedLine.isEmpty()) {
            return Optional.empty();
        }
        PendingMessage newest = null;
        int newestScore = 0;
        for (final PendingMessage item : pending) {
            final int score = item.matchScore(normalizedLine);
            if (score <= 0) {
                continue;
            }
            if (newest == null || score > newestScore
                    || (score == newestScore && item.getCreatedAt() > newest.getCreatedAt())) {
                newest = item;
                newestScore = score;
            }
        }
        if (newest == null) {
            return Optional.empty();
        }
        newest.addRecipientId(viewerUuid);
        if (snapshotModule != null && snapshotModule.isEnabled()) {
            if (newest.getEntryId() == null) {
                final Optional<ChatSnapshotModule.Entry> entry = snapshotModule.record(newest.getSenderUuid(),
                        newest.getSenderName(), newest.getMessage(), legacyLine,
                        Collections.singleton(viewerUuid));
                if (entry.isPresent()) {
                    newest.setEntryId(entry.get().getId());
                }
            } else {
                snapshotModule.addRecipient(newest.getEntryId(), viewerUuid);
            }
        }
        return Optional.of(new DecorationMatch(newest.getEntryId(), legacyLine));
    }

    public boolean isAlreadyDecorated(final String legacyLine, final String liveDeletePrefix) {
        if (legacyLine == null || liveDeletePrefix == null) {
            return false;
        }
        final String normalizedLine = normalize(legacyLine);
        final String normalizedPrefix = normalize(liveDeletePrefix);
        if (normalizedLine.isEmpty() || normalizedPrefix.isEmpty()) {
            return false;
        }
        final String strippedPrefix = normalizedPrefix.replaceAll("\\s+$", "");
        return normalizedLine.startsWith(strippedPrefix);
    }

    public synchronized int pendingCount(final long nowMillis) {
        purgeExpired(nowMillis);
        return pending.size();
    }

    private synchronized void purgeExpired(final long nowMillis) {
        final long cutoff = nowMillis - PENDING_TTL_MILLIS;
        while (!pending.isEmpty() && pending.peekFirst().getCreatedAt() < cutoff) {
            pending.removeFirst();
        }
    }

    private static String normalize(final String input) {
        if (input == null) {
            return "";
        }
        String converted = LegacyText.toSection(input);
        converted = stripColorCodes(converted);
        converted = converted.trim().replaceAll("\\s+", " ");
        converted = converted.toLowerCase(Locale.ROOT);
        return converted;
    }

    private static String stripColorCodes(final String input) {
        if (input == null) {
            return "";
        }
        String result = input;
        result = result.replaceAll("§x§[0-9A-Fa-f]{12}", "");
        result = result.replaceAll("§[0-9A-Fa-fK-Ok-oRr]", "");
        return result;
    }

    public static final class DecorationMatch {

        private final String entryId;
        private final String renderedLine;

        public DecorationMatch(final String entryId, final String renderedLine) {
            this.entryId = entryId;
            this.renderedLine = renderedLine;
        }

        public String getEntryId() {
            return entryId;
        }

        public String getRenderedLine() {
            return renderedLine;
        }
    }

    private static final class PendingMessage {

        private final UUID senderUuid;
        private final String senderName;
        private final String message;
        private final String normalizedMessage;
        private final long createdAt;
        private String renderedLine;
        private String normalizedRenderedLine;
        private String entryId;
        private final Set<UUID> recipientIds = new HashSet<UUID>();

        private PendingMessage(final UUID senderUuid, final String senderName, final String message,
                final String normalizedMessage, final long createdAt) {
            this.senderUuid = senderUuid;
            this.senderName = senderName;
            this.message = message;
            this.normalizedMessage = normalizedMessage;
            this.createdAt = createdAt;
            this.entryId = null;
        }

        public UUID getSenderUuid() {
            return senderUuid;
        }

        public String getSenderName() {
            return senderName;
        }

        public String getMessage() {
            return message;
        }

        public String getNormalizedMessage() {
            return normalizedMessage;
        }

        public String getRenderedLine() {
            return renderedLine;
        }

        public void setRenderedLine(final String renderedLine) {
            this.renderedLine = renderedLine;
            if (renderedLine != null) {
                this.normalizedRenderedLine = normalize(renderedLine);
            }
        }

        public String getNormalizedRenderedLine() {
            return normalizedRenderedLine;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public String getEntryId() {
            return entryId;
        }

        public void setEntryId(final String entryId) {
            this.entryId = entryId;
        }

        public void addRecipientId(final UUID recipientId) {
            if (recipientId != null) {
                recipientIds.add(recipientId);
            }
        }

        public int matchScore(final String normalizedLine) {
            if (normalizedLine == null || normalizedLine.isEmpty()) {
                return 0;
            }
            int score = 0;
            if (normalizedRenderedLine != null && !normalizedRenderedLine.isEmpty()
                    && normalizedLine.contains(normalizedRenderedLine)) {
                score = Math.max(score, 3);
            }
            if (normalizedMessage != null && !normalizedMessage.isEmpty()
                    && normalizedLine.contains(normalizedMessage)) {
                score = Math.max(score, 2);
            }
            final String normalizedSender = normalize(senderName);
            if (score > 0 && normalizedSender != null && !normalizedSender.isEmpty()
                    && normalizedLine.contains(normalizedSender)) {
                score += 1;
            }
            return score;
        }
    }
}