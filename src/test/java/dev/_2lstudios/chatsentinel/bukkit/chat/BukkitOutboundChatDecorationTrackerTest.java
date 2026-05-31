package dev._2lstudios.chatsentinel.bukkit.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import dev._2lstudios.chatsentinel.shared.modules.ChatSnapshotModule;

public final class BukkitOutboundChatDecorationTrackerTest {

    private ChatSnapshotModule snapshotModule;
    private BukkitOutboundChatDecorationTracker tracker;

    @Before
    public void setUp() {
        snapshotModule = new ChatSnapshotModule();
        snapshotModule.loadData(true, 50, 2, ChatSnapshotModule.DEFAULT_PROXY_REPLAY_FORMAT);
        tracker = new BukkitOutboundChatDecorationTracker();
    }

    @Test
    public void matchesPendingMessageAndCreatesSingleEntry() {
        final UUID senderUuid = UUID.randomUUID();
        final String senderName = "Steve";
        final String message = "Hello world";
        final String renderedLine = "&7[VIP] Steve: &fHello world";

        tracker.trackCandidate(senderUuid, senderName, message, 1000L);
        tracker.markFinalLine(senderUuid, message, renderedLine, Collections.singleton(senderUuid), snapshotModule, 1000L);

        final Optional<BukkitOutboundChatDecorationTracker.DecorationMatch> result = tracker.match(
                senderUuid, renderedLine, snapshotModule, 2000L);

        assertTrue(result.isPresent());
        final BukkitOutboundChatDecorationTracker.DecorationMatch match = result.get();
        assertNotNull(match.getEntryId());

        final java.util.List<ChatSnapshotModule.Entry> entries = snapshotModule.getRecentEntries();
        assertEquals(1, entries.size());
        assertEquals(message, entries.get(0).getMessage());
        assertEquals(renderedLine, entries.get(0).getRenderedLine());
    }

    @Test
    public void addsRecipientsToExistingEntry() {
        final UUID senderUuid = UUID.randomUUID();
        final String senderName = "Steve";
        final String message = "Hello world";
        final String renderedLine = "&7[VIP] Steve: &fHello world";
        final UUID viewerA = UUID.randomUUID();
        final UUID viewerB = UUID.randomUUID();

        tracker.trackCandidate(senderUuid, senderName, message, 1000L);
        tracker.markFinalLine(senderUuid, message, renderedLine, Collections.singleton(viewerA), snapshotModule, 1000L);

        tracker.match(viewerA, renderedLine, snapshotModule, 2000L);
        tracker.match(viewerB, renderedLine, snapshotModule, 3000L);

        final java.util.List<ChatSnapshotModule.Entry> entries = snapshotModule.getRecentEntries();
        assertEquals(1, entries.size());
        final ChatSnapshotModule.Entry entry = entries.get(0);
        assertTrue(entry.getRecipientIds().contains(viewerA));
        assertTrue(entry.getRecipientIds().contains(viewerB));
    }

    @Test
    public void doesNotMatchExpiredPendingMessage() {
        final UUID senderUuid = UUID.randomUUID();
        final String senderName = "Steve";
        final String message = "Hello world";
        final String renderedLine = "&7[VIP] Steve: &fHello world";

        tracker.trackCandidate(senderUuid, senderName, message, 1000L);
        tracker.markFinalLine(senderUuid, message, renderedLine, Collections.singleton(senderUuid), snapshotModule, 1000L);

        final Optional<BukkitOutboundChatDecorationTracker.DecorationMatch> result = tracker.match(
                senderUuid, renderedLine, snapshotModule, 5000L);

        assertFalse(result.isPresent());
    }

    @Test
    public void doesNotMatchAlreadyDecoratedLine() {
        final String legacyLine = "&8[&cX&8] &7[VIP] Steve: Hello";
        final String liveDeletePrefix = "&8[&cX&8] ";

        final boolean result = tracker.isAlreadyDecorated(legacyLine, liveDeletePrefix);

        assertTrue(result);
    }

    @Test
    public void matchesPacketLineWithoutRenderedLine() {
        final UUID senderUuid = UUID.randomUUID();
        final UUID viewerUuid = UUID.randomUUID();
        final String senderName = "Steve";
        final String message = "Hello world";
        final String packetLine = "&cOWNER &fSteve&7: &fHello world";

        tracker.trackCandidate(senderUuid, senderName, message, 1000L);

        final Optional<BukkitOutboundChatDecorationTracker.DecorationMatch> result = tracker.match(
                viewerUuid, packetLine, snapshotModule, 1500L);

        assertTrue(result.isPresent());
        assertNotNull(result.get().getEntryId());
        final java.util.List<ChatSnapshotModule.Entry> entries = snapshotModule.getRecentEntries();
        assertEquals(1, entries.size());
        assertEquals(message, entries.get(0).getMessage());
        assertEquals(packetLine, entries.get(0).getRenderedLine());
        assertTrue(entries.get(0).getRecipientIds().contains(viewerUuid));
    }

    @Test
    public void prefersNewestCandidateWhenRawAndCorrectedMessagesExist() {
        final UUID senderUuid = UUID.randomUUID();
        final UUID viewerUuid = UUID.randomUUID();
        final String senderName = "Steve";

        tracker.trackCandidate(senderUuid, senderName, "helo world", 1000L);
        tracker.trackCandidate(senderUuid, senderName, "hello world", 1100L);

        final Optional<BukkitOutboundChatDecorationTracker.DecorationMatch> result = tracker.match(
                viewerUuid, "&7[VIP] Steve: &fhello world", snapshotModule, 1500L);

        assertTrue(result.isPresent());
        final java.util.List<ChatSnapshotModule.Entry> entries = snapshotModule.getRecentEntries();
        assertEquals(1, entries.size());
        assertEquals("hello world", entries.get(0).getMessage());
    }

    @Test
    public void doesNotMatchUnrelatedPacketLine() {
        final UUID senderUuid = UUID.randomUUID();
        final UUID viewerUuid = UUID.randomUUID();

        tracker.trackCandidate(senderUuid, "Steve", "Hello world", 1000L);

        final Optional<BukkitOutboundChatDecorationTracker.DecorationMatch> result = tracker.match(
                viewerUuid, "&7[VIP] Alex: &fDifferent message", snapshotModule, 1500L);

        assertFalse(result.isPresent());
        assertEquals(0, snapshotModule.getRecentEntries().size());
    }
}
