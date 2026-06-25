package dev._2lstudios.chatsentinel.shared.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ChatSnapshotModuleTest {
    @Test
    public void buildClearPayload_appendsConfiguredBlankLinesBeforeFooter() {
        final ChatSnapshotModule module = new ChatSnapshotModule();
        module.loadData(2);

        final String payload = module.buildClearPayload("done");

        assertEquals(" \n \ndone", payload);
    }

    @Test
    public void loadData_clampsClearLinesToMinimumOne() {
        final ChatSnapshotModule module = new ChatSnapshotModule();
        module.loadData(0);

        final String payload = module.buildClearPayload("done");

        assertTrue(payload.startsWith(" \n"));
    }
}
