package dev._2lstudios.chatsentinel.shared.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.junit.Test;

public final class ChatSnapshotModuleTest {
    @Test
    public void buildReplayPayloadDoesNotPrefixFirstVisibleLineWithSpace() {
        final ChatSnapshotModule module = new ChatSnapshotModule();
        module.loadData(true, 50, 2, ChatSnapshotModule.DEFAULT_PROXY_REPLAY_FORMAT);
        module.record(UUID.randomUUID(), "LinsaFTW", "hello", "<LinsaFTW> hello", Collections.<UUID>emptyList());

        final String payload = module.buildReplayPayload(UUID.randomUUID());

        assertTrue(payload.contains("\n<LinsaFTW> hello\n"));
    }

    @Test
    public void addRecipient_makesExistingEntryVisibleToRecipient() {
        final ChatSnapshotModule module = new ChatSnapshotModule();
        module.loadData(true, 50, 2, ChatSnapshotModule.DEFAULT_PROXY_REPLAY_FORMAT);
        final UUID senderUuid = UUID.randomUUID();
        final UUID viewerA = UUID.randomUUID();
        final UUID viewerB = UUID.randomUUID();

        module.record(senderUuid, "Steve", "hello", "<Steve> hello", Collections.singleton(viewerA));

        final boolean added = module.addRecipient(
                module.getRecentEntries().get(0).getId(), viewerB);

        assertTrue(added);
        final java.util.List<ChatSnapshotModule.Entry> visibleEntries = module.getVisibleEntriesFor(viewerB);
        assertEquals(1, visibleEntries.size());
        assertEquals("hello", visibleEntries.get(0).getMessage());
    }
}
