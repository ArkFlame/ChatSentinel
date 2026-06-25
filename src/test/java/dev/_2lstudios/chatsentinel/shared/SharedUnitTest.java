package dev._2lstudios.chatsentinel.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.Test;

import dev._2lstudios.chatsentinel.shared.chat.ChatEventResult;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompiler;
import dev._2lstudios.chatsentinel.shared.filter.FilterExpressionFile;
import dev._2lstudios.chatsentinel.shared.filter.FilterKind;
import dev._2lstudios.chatsentinel.shared.filter.FilterMatch;
import dev._2lstudios.chatsentinel.shared.filter.FilterSource;
import dev._2lstudios.chatsentinel.shared.filter.CommonRegexGenerator;
import dev._2lstudios.chatsentinel.shared.modules.CapitalizationModule;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.text.WarningDeliverySettings;
import dev._2lstudios.chatsentinel.shared.utils.PatternUtil;

public class SharedUnitTest {
    @Test
    public void compileSafe_neverMatches_whenEmpty() {
        Pattern pattern = PatternUtil.compileSafe(Collections.<String>emptyList());

        assertFalse(pattern.matcher("anything").find());
    }

    @Test
    public void toCommonRegex_matchesLeetAndSpacedText_whenPlainTextProvided() {
        Pattern pattern = Pattern.compile(new CommonRegexGenerator().toCommonRegex("hello"),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        assertTrue(pattern.matcher("h3llo").find());
        assertTrue(pattern.matcher("h e l l o").find());
    }

    @Test
    public void compile_returnsSourcePathForMatchingFile_whenTwoFilesProvided() {
        FilterSource firstSource = new FilterSource(FilterKind.BLACKLIST, "first", "a.yml", "First");
        FilterSource secondSource = new FilterSource(FilterKind.BLACKLIST, "second", "b.yml", "Second");
        FilterCompiler.FilterCompilation compilation = new FilterCompiler().compile(FilterKind.BLACKLIST, Arrays.asList(
                new FilterExpressionFile(secondSource, Collections.singletonList("banana")),
                new FilterExpressionFile(firstSource, Collections.singletonList("apple"))));

        FilterMatch match = compilation.getRegistry().findFirst("green banana").get();

        assertEquals("b.yml", match.getSource().getRelativePath());
    }

    @Test
    public void processEvent_doesNotBlock_whenBlacklistMatchInsideWhitelistedPhrase() {
        ModuleManager moduleManager = new TestModuleManager();
        moduleManager.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList());
        moduleManager.getWhitelistModule().loadData(true, new String[] { "your ass" });
        moduleManager.getBlacklistModule().loadData(true, "Blacklist", false, false, "", 1, "", false,
                new String[0], new String[] { "ass" }, true);

        ChatEventResult result = moduleManager.getBlacklistModule().processEvent(
                new ChatPlayer(UUID.randomUUID()), moduleManager.getMessagesModule(), "player", "your ass", "en");

        assertNull(result);
    }

    @Test
    public void processEvent_blocks_whenBlacklistMatchNotWhitelistedPhrase() {
        ModuleManager moduleManager = new TestModuleManager();
        moduleManager.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList());
        moduleManager.getWhitelistModule().loadData(true, new String[] { "your ass" });
        moduleManager.getBlacklistModule().loadData(true, "Blacklist", false, false, "", 1, "", false,
                new String[0], new String[] { "ass" }, true);

        ChatEventResult result = moduleManager.getBlacklistModule().processEvent(
                new ChatPlayer(UUID.randomUUID()), moduleManager.getMessagesModule(), "player", "ass", "en");

        assertTrue(result.isCancelled());
    }

    @Test
    public void warns_areSeparated_whenIdentityKeyDiffers() {
        ChatPlayer chatPlayer = new ChatPlayer(UUID.randomUUID());

        chatPlayer.addWarn("blacklist:one");
        chatPlayer.addWarn("blacklist:two");
        chatPlayer.addWarn("blacklist:two");

        assertEquals(1, chatPlayer.getWarns("blacklist:one"));
        assertEquals(2, chatPlayer.getWarns("blacklist:two"));
    }

    @Test
    public void processEvent_ignoresPlayerNameCapitalization_whenWhitelistPlayerNamesEnabled() {
        CapitalizationModule module = new CapitalizationModule();
        module.loadData(true, "Capitalization", true, 0, 1, "", false, new String[0], true, new String[0],
                () -> Collections.singletonList("ABC"));

        ChatEventResult result = module.processEvent(new ChatPlayer(UUID.randomUUID()), null, "ABC", "ABC", "en");

        assertNull(result);
    }

    @Test
    public void processEvent_correctsExcessiveCapitalization_whenAboveThreshold() {
        CapitalizationModule module = new CapitalizationModule();
        module.loadData(true, "Capitalization", true, 2, 1, "", false, new String[0], false, new String[0],
                () -> Collections.<String>emptyList());

        ChatEventResult result = module.processEvent(new ChatPlayer(UUID.randomUUID()), null, "Steve", "hello EVERYONE", "en");

        assertEquals("hello everyone", result.getMessage());
        assertFalse(result.isCancelled());
    }

    @Test
    public void getPlayer_returnsSamePlayer_whenChatUserUuidMatches() {
        UUID uuid = UUID.randomUUID();
        dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager manager = new dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager();

        ChatPlayer first = manager.getPlayer(new TestChatUser(uuid));
        ChatPlayer second = manager.getPlayer(uuid);

        assertEquals(first, second);
    }

    @Test
    public void correct_replacesCommonTypos_whenConfigured() {
        dev._2lstudios.chatsentinel.shared.modules.CorrectionModule module = new dev._2lstudios.chatsentinel.shared.modules.CorrectionModule();
        java.util.Map<String, String> replacements = new java.util.HashMap<String, String>();
        replacements.put("wath", "what");
        replacements.put("doign", "doing");
        module.loadData(true, "Correction", true, false, true, true, 8, "",
                replacements,
                java.util.Collections.<String>emptyList(),
                new java.util.function.Supplier<java.util.Collection<String>>() {
                    @Override
                    public java.util.Collection<String> get() {
                        return java.util.Collections.emptyList();
                    }
                });

        dev._2lstudios.chatsentinel.shared.modules.CorrectionResult result = module.correct("wath are you doign?");

        assertEquals("what are you doing?", result.getMessage());
    }

    @Test
    public void processEvent_capitalizesFirstLetterSilently_whenOnlyFirstLetterNeedsCorrection() {
        CapitalizationModule module = new CapitalizationModule();
        module.loadData(true, "Capitalization", true, true, 8, -1, "", false, new String[0], false,
                new String[0], () -> java.util.Collections.<String>emptyList(), "");

        ChatEventResult result = module.processEvent(new ChatPlayer(UUID.randomUUID()), null, "Steve", "hello", "en");

        assertEquals("Hello", result.getMessage());
        assertFalse(result.isNotify());
    }

    @Test
    public void processEvent_correctsUppercaseAndCapitalizesFirstLetter_whenAboveThreshold() {
        CapitalizationModule module = new CapitalizationModule();
        module.loadData(true, "Capitalization", true, true, 8, -1, "", false, new String[0], false,
                new String[0], () -> java.util.Collections.<String>emptyList(), "");

        ChatEventResult result = module.processEvent(new ChatPlayer(UUID.randomUUID()), null, "Steve", "HELLO EVERYONE", "en");

        assertEquals("Hello everyone", result.getMessage());
        assertTrue(result.isNotify());
    }

    @Test
    public void compile_oldSpanishOffenseRegex_blocksPelotudo() {
        FilterSource source = new FilterSource(FilterKind.BLACKLIST, "offense", "blacklist/offense/spanish.yml", "offense");
        FilterCompiler.FilterCompilation compilation = new FilterCompiler().compile(FilterKind.BLACKLIST,
                java.util.Collections.singletonList(new FilterExpressionFile(source, java.util.Collections.singletonList("pelotudo"))));

        assertTrue(compilation.getRegistry().findFirst("sos un pelotudo").isPresent());
    }

    @Test
    public void compile_oldAdvertisementRegex_blocksIpv4() {
        FilterSource source = new FilterSource(FilterKind.BLACKLIST, "advertisement", "blacklist/advertisement/domains.yml", "advertisement");
        FilterCompiler.FilterCompilation compilation = new FilterCompiler().compile(FilterKind.BLACKLIST,
                java.util.Collections.singletonList(new FilterExpressionFile(source, java.util.Collections.singletonList("\\b(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\b"))));

        assertTrue(compilation.getRegistry().findFirst("join 135.181.237.31").isPresent());
    }

    @Test
    public void getWarnMessage_fallsBackToEnglishKey_whenLocaleKeyMissing() {
        dev._2lstudios.chatsentinel.shared.modules.MessagesModule module = new dev._2lstudios.chatsentinel.shared.modules.MessagesModule();
        java.util.Map<String, java.util.Map<String, String>> locales = new java.util.HashMap<String, java.util.Map<String, String>>();
        java.util.Map<String, String> en = new java.util.HashMap<String, String>();
        en.put("blacklist_warn_message", "blocked");
        locales.put("en", en);
        locales.put("es", new java.util.HashMap<String, String>());
        module.loadData("en", locales);

        assertEquals("blocked", module.getWarnMessage(new String[][] { { "%message%" }, { "x" } }, "es", "Blacklist"));
    }

    @Test
    public void getWarnMessage_returnsEmpty_whenCapitalizationMessageConfiguredEmpty() {
        dev._2lstudios.chatsentinel.shared.modules.MessagesModule module = new dev._2lstudios.chatsentinel.shared.modules.MessagesModule();
        java.util.Map<String, java.util.Map<String, String>> locales = new java.util.HashMap<String, java.util.Map<String, String>>();
        java.util.Map<String, String> en = new java.util.HashMap<String, String>();
        en.put("capitalization_warn_message", "");
        en.put("caps_warn_message", "legacy caps");
        locales.put("en", en);
        module.loadData("en", locales);

        assertEquals("", module.getWarnMessage(new String[][] { { "%word%" }, { "ABC" } }, "en", "Capitalization"));
    }

    @Test
    public void getWarnMessage_usesAdvertisingAliasForAdvertisementModule() {
        dev._2lstudios.chatsentinel.shared.modules.MessagesModule module = new dev._2lstudios.chatsentinel.shared.modules.MessagesModule();
        java.util.Map<String, java.util.Map<String, String>> locales = new java.util.HashMap<String, java.util.Map<String, String>>();
        java.util.Map<String, String> en = new java.util.HashMap<String, String>();
        en.put("advertising_warn_message", "Advertising is not allowed: %word%.");
        locales.put("en", en);
        module.loadData("en", locales);

        assertEquals("Advertising is not allowed: f3f5.ru.", module.getWarnMessage(new String[][] { { "%word%" }, { "f3f5.ru" } }, "en", "advertisement"));
    }

    @Test
    public void hasWarnMessage_returnsTrue_whenWarnMessageConfiguredEmpty() {
        dev._2lstudios.chatsentinel.shared.modules.MessagesModule module = new dev._2lstudios.chatsentinel.shared.modules.MessagesModule();
        java.util.Map<String, java.util.Map<String, String>> locales = new java.util.HashMap<String, java.util.Map<String, String>>();
        java.util.Map<String, String> en = new java.util.HashMap<String, String>();
        en.put("capitalization_warn_message", "");
        locales.put("en", en);
        module.loadData("en", locales);

        assertTrue(module.hasWarnMessage("en", "capitalization"));
    }

    private static final class TestModuleManager extends ModuleManager {
        @Override
        public void reloadData(dev._2lstudios.chatsentinel.shared.filter.FilterCompileStatus status) {
        }
    }

    private static final class TestChatUser implements ChatUser {
        private final UUID uuid;

        private TestChatUser(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public UUID getUniqueId() { return uuid; }

        @Override
        public String getName() { return "Steve"; }

        @Override
        public String getLocale() { return "en"; }

        @Override
        public String getServerName() { return "server"; }

        @Override
        public boolean hasPermission(String permission) { return false; }

        @Override
        public void sendMessage(String legacyMessage) { }

        @Override
        public void sendWarning(String legacyMessage, WarningDeliverySettings settings) { }
    }
}
