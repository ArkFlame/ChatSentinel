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

    @Test
    public void render_appendsMessage_whenFormatOnlyContainsPlayerPlaceholder() {
        final LegacyChatFormatRenderer renderer = new LegacyChatFormatRenderer();

        final String rendered = renderer.render("&cOWNER %s:", "LinsaFTW", "LinsaFTW", "asdas");

        assertEquals("&cOWNER LinsaFTW: asdas", rendered);
    }

    @Test
    public void render_appendsMessage_whenFormatHasNoFormatterPlaceholders() {
        final LegacyChatFormatRenderer renderer = new LegacyChatFormatRenderer();

        final String rendered = renderer.render("&cOWNER LinsaFTW:", "LinsaFTW", "LinsaFTW", "asdas");

        assertEquals("&cOWNER LinsaFTW: asdas", rendered);
    }

    @Test
    public void render_preservesPreRenderedFormat_whenMessageAlreadyPresent() {
        final LegacyChatFormatRenderer renderer = new LegacyChatFormatRenderer();

        final String rendered = renderer.render("&cOWNER LinsaFTW: asdas", "LinsaFTW", "LinsaFTW", "asdas");

        assertEquals("&cOWNER LinsaFTW: asdas", rendered);
    }

    @Test
    public void render_preservesChatControlEscapedPercentPreRenderedFormat_whenMessageAlreadyPresent() {
        final LegacyChatFormatRenderer renderer = new LegacyChatFormatRenderer();

        final String rendered = renderer.render("&7[100%%] LinsaFTW: asdas", "LinsaFTW", "LinsaFTW", "asdas");

        assertEquals("&7[100%] LinsaFTW: asdas", rendered);
    }
}