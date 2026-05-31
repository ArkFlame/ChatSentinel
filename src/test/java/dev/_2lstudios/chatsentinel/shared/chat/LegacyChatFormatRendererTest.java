package dev._2lstudios.chatsentinel.shared.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LegacyChatFormatRendererTest {
    @Test
    public void render_preservesThirdPartyFormat_whenFormatValid() {
        final LegacyChatFormatRenderer renderer = new LegacyChatFormatRenderer();

        final String rendered = renderer.render("[VIP] %s: %s", "Steve", "Steve", "Hello");

        assertEquals("[VIP] Steve: Hello", rendered);
    }

    @Test
    public void render_fallsBackToVanillaLine_whenFormatInvalid() {
        final LegacyChatFormatRenderer renderer = new LegacyChatFormatRenderer();

        final String rendered = renderer.render("%q", "Steve", "Steve", "Hello");

        assertEquals("<Steve> Hello", rendered);
    }

    @Test
    public void render_usesPlayerName_whenDisplayNameMissing() {
        final LegacyChatFormatRenderer renderer = new LegacyChatFormatRenderer();

        final String rendered = renderer.render("%s > %s", "", "Steve", "Hello");

        assertEquals("Steve > Hello", rendered);
    }
}