package dev._2lstudios.chatsentinel.shared;

import dev._2lstudios.chatsentinel.shared.alerts.LocalAlertBus;
import dev._2lstudios.chatsentinel.shared.chat.ChatEventProcessor;
import dev._2lstudios.chatsentinel.shared.chat.ChatEventResult;
import dev._2lstudios.chatsentinel.shared.chat.ChatNotificationManager;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.chat.ProcessedChatEvent;
import dev._2lstudios.chatsentinel.shared.commands.CommandResult;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileStatus;
import dev._2lstudios.chatsentinel.shared.modules.AllowedCharactersModule;
import dev._2lstudios.chatsentinel.shared.modules.CooldownModerationModule;
import dev._2lstudios.chatsentinel.shared.modules.FloodModerationModule;
import dev._2lstudios.chatsentinel.shared.modules.GeneralModule;
import dev._2lstudios.chatsentinel.shared.modules.MessagesModule;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.modules.SimilarityModerationModule;
import dev._2lstudios.chatsentinel.shared.platform.ChatPlatform;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.text.WarningDeliverySettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatEventProcessorTest {
    @Test
    public void process_blocksServerMute_whenMuted() {
        TestModuleManager modules = modules();
        modules.getServerMuteModule().loadData(true, true, "mute.bypass");
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatEventProcessor processor = processor(modules, new FakePlatform("BungeeCord", Collections.singletonList(user)), new ChatPlayerManager());

        ProcessedChatEvent result = processor.process(user, "hello", true);

        assertTrue(result.isCancelled());
    }

    @Test
    public void process_allowsServerMute_whenBypassPermissionPresent() {
        TestModuleManager modules = modules();
        modules.getServerMuteModule().loadData(true, true, "mute.bypass");
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve") {
            @Override
            public boolean hasPermission(String permission) {
                return "mute.bypass".equals(permission);
            }
        };
        ChatEventProcessor processor = processor(modules, new FakePlatform("BungeeCord", Collections.singletonList(user)), new ChatPlayerManager());

        ProcessedChatEvent result = processor.process(user, "hello", true);

        assertFalse(result.isCancelled());
    }

    @Test
    public void process_skipsNoMove_whenPlatformNotBukkit() {
        TestModuleManager modules = modules();
        modules.getNoMoveChatModule().loadData(true, "move.bypass");
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatEventProcessor processor = processor(modules, new FakePlatform("Velocity", Collections.singletonList(user)), new ChatPlayerManager());

        ProcessedChatEvent result = processor.process(user, "hello", true);

        assertFalse(result.isCancelled());
    }

    @Test
    public void process_blocksNoMove_whenBukkitAndNotMoved() {
        TestModuleManager modules = modules();
        modules.getNoMoveChatModule().loadData(true, "move.bypass");
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), new ChatPlayerManager());

        ProcessedChatEvent result = processor.process(user, "hello", true);

        assertTrue(result.isCancelled());
    }

    @Test
    public void process_allowsNoMove_whenMovementGatePassed() {
        TestModuleManager modules = modules();
        modules.getNoMoveChatModule().loadData(true, "move.bypass", 5.0D, true);
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatPlayerManager players = new ChatPlayerManager();
        players.getPlayer(user).markMovementGatePassed();
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), players);

        ProcessedChatEvent result = processor.process(user, "hello", true);

        assertFalse(result.isCancelled());
    }

    @Test
    public void process_blocksNoMove_whenGlobalBypassButNoMoveExcluded() {
        TestModuleManager modules = modules();
        modules.getNoMoveChatModule().loadData(true, "", 5.0D, true);
        modules.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList(), "chatsentinel.bypass",
                Collections.singletonList("no-move-chat"));
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve") {
            @Override
            public boolean hasPermission(String permission) {
                return "chatsentinel.bypass".equals(permission);
            }
        };
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), new ChatPlayerManager());

        ProcessedChatEvent result = processor.process(user, "hello", true);

        assertTrue(result.isCancelled());
    }

    @Test
    public void process_correctsAndCapitalizes_whenBothModulesActive() {
        TestModuleManager modules = modules();
        modules.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList(), "",
                Collections.<String>emptyList());
        modules.getCorrectionModule().loadData(
                true, "Correction", true, false, true, true, 8, "",
                createCorrectionReplacements(),
                Collections.<String>emptyList(),
                () -> Collections.<String>emptyList());
        modules.getCapitalizationModule().loadData(true, "Capitalization", true, true, 8, -1, "", false,
                new String[0], false, new String[0], () -> Collections.<String>emptyList(), "");
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatPlayerManager players = new ChatPlayerManager();
        players.getPlayer(user).markMovementGatePassed();
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), players);

        ProcessedChatEvent result = processor.process(user, "wath are you doign FRIEND OF MINE?", true);

        assertEquals("What are you doing friend of mine?", result.getMessage());
        assertFalse(result.isCancelled());
    }

    @Test
    public void process_sendsDirectBlockedMessage_whenBlacklistCancels() {
        TestModuleManager modules = modules();
        Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("blocked_message", "Blocked word: %word%.");
        en.put("blacklist_warn_message", "warning");
        en.put("filtered", "filtered");
        locales.put("en", en);
        modules.getMessagesModule().loadData("en", locales);
        modules.getBlacklistModule().loadData(true, "Blacklist", false, false, "", 3, "notify", false,
                new String[0], new String[] { "pvt4" }, true);
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), new ChatPlayerManager());

        processor.process(user, "Pvt4", true);

        assertTrue(user.getMessages().contains("Blocked word: Pvt4."));
    }

    @Test
    public void process_usesFirstMatchedWord_whenBlacklistMessageContainsMultipleBlockedWords() {
        TestModuleManager modules = modules();
        Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("blocked_message", "Blocked word: %word%.");
        en.put("blacklist_warn_message", "warning");
        en.put("filtered", "filtered");
        locales.put("en", en);
        modules.getMessagesModule().loadData("en", locales);
        modules.getBlacklistModule().loadData(true, "Blacklist", false, false, "", 3, "notify", false,
                new String[0], new String[] { "pvt4", "badword" }, true);
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), new ChatPlayerManager());

        processor.process(user, "badword then Pvt4", true);

        assertTrue(user.getMessages().contains("Blocked word: badword."));
    }

    @Test
    public void process_sendsCooldownWarnMessage_notBlockedMessage_whenCooldownCancels() {
        TestModuleManager modules = modules();
        Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("blocked_message", "You cannot write that. If you continue, you will be automatically muted.");
        en.put("cooldown_warn_message", "You can write your next message in %cooldown% seconds");
        en.put("filtered", "filtered");
        locales.put("en", en);
        modules.getMessagesModule().loadData("en", locales);
        modules.getCooldownModule().loadData(true, 0, 0, 5000, 0);
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatPlayerManager players = new ChatPlayerManager();
        ChatPlayer chatPlayer = players.getPlayer(user);
        chatPlayer.addLastMessage("hello", System.currentTimeMillis() - 1000L);
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), players);

        ProcessedChatEvent result = processor.process(user, "hello", true);

        assertTrue(result.isCancelled());
        assertTrue(user.getMessages().stream().anyMatch(m -> m.contains("You can write your next message in")));
        assertTrue(user.getMessages().stream().noneMatch(m -> m.contains("You cannot write that")));
    }

    @Test
    public void process_doesNotRunFlood_whenCooldownReturnsHiddenTerminalResult() {
        TestModuleManager modules = modules();
        TrackingFloodModule floodModule = new TrackingFloodModule();
        modules.setCooldownOverride(new HiddenCooldownModule());
        modules.setFloodOverride(floodModule);
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), new ChatPlayerManager());

        processor.process(user, "hello", true);

        assertFalse(floodModule.wasCalled());
    }

    @Test
    public void process_sendsOnlyCooldownWarning_whenSimilarityWouldAlsoCancel() {
        TestModuleManager modules = modules();
        Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("blocked_message", "blocked");
        en.put("cooldown_warn_message", "cooldown");
        en.put("similarity_warn_message", "similarity");
        en.put("filtered", "filtered");
        locales.put("en", en);
        modules.getMessagesModule().loadData("en", locales);
        modules.getCooldownModule().loadData(true, 0, 0, 5000, 0);
        modules.getSimilarityModule().loadData(true, "Similarity", 75.0D, 3, 4, true, true, true);
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatPlayerManager players = new ChatPlayerManager();
        ChatPlayer chatPlayer = players.getPlayer(user);
        chatPlayer.addLastMessage("spam", System.currentTimeMillis() - 1L);
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), players);

        processor.process(user, "spaaam", true);

        assertEquals(Collections.singletonList("cooldown"), user.getMessages());
    }

    @Test
    public void process_allowsReplacementToContinueIntoSimilarity() {
        TestModuleManager modules = modules();
        Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("blocked_message", "blocked");
        en.put("similarity_warn_message", "similarity");
        en.put("filtered", "filtered");
        locales.put("en", en);
        modules.getMessagesModule().loadData("en", locales);
        modules.getAllowedCharactersModule().loadData(true,
                AllowedCharactersModule.DEFAULT_MODE,
                "[A-Za-z ]+", "");
        modules.getSimilarityModule().loadData(true, "Similarity", 75.0D, 3, 4, true, true, true);
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatPlayerManager players = new ChatPlayerManager();
        ChatPlayer chatPlayer = players.getPlayer(user);
        chatPlayer.addLastMessage("spam", System.currentTimeMillis() - 10000L);
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), players);

        ProcessedChatEvent result = processor.process(user, "sp am", true);

        assertTrue(result.isCancelled());
    }

    @Test
    public void process_skipsCapitalization_whenGlobalBypassPresent() {
        TestModuleManager modules = modules();
        modules.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList(), "chatsentinel.bypass",
                Collections.<String>emptyList());
        modules.getCapitalizationModule().loadData(true, "Capitalization", true, true, 8, -1, "", false,
                new String[0], false, new String[0], () -> Collections.<String>emptyList(), "chatsentinel.bypass.capitalization");
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve") {
            @Override
            public boolean hasPermission(String permission) {
                return "chatsentinel.bypass".equals(permission);
            }
        };
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), new ChatPlayerManager());

        ProcessedChatEvent result = processor.process(user, "hello everyone", true);

        assertEquals("hello everyone", result.getMessage());
    }

    @Test
    public void process_capitalizesFirstLetter_whenNoBypassPresent() {
        TestModuleManager modules = modules();
        modules.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList(), "chatsentinel.bypass",
                Collections.<String>emptyList());
        modules.getCapitalizationModule().loadData(true, "Capitalization", true, true, 8, -1, "", false,
                new String[0], false, new String[0], () -> Collections.<String>emptyList(), "chatsentinel.bypass.capitalization");
        FakeUser user = new FakeUser(UUID.randomUUID(), "Steve");
        ChatEventProcessor processor = processor(modules, new FakePlatform("Bukkit", Collections.singletonList(user)), new ChatPlayerManager());

        ProcessedChatEvent result = processor.process(user, "hello everyone", true);

        assertEquals("Hello everyone", result.getMessage());
    }

    @Test
    public void process_doesNotThrowSpyFormatting_whenPlaceholderReplacementIsNull() {
        TestModuleManager modules = modules();
        modules.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList(), "",
                Collections.<String>emptyList());
        modules.getSpyModule().loadData(true, "spy.permission", "&e%server_name%:%message%");
        modules.getBlacklistModule().loadData(true, "Blacklist", false, false, "", 0, "", false,
                new String[0], new String[] { "bad" }, true);
        final FakeUser sender = new FakeUser(UUID.randomUUID(), "Steve") {
            @Override
            public String getServerName() {
                return null;
            }
        };
        final FakeUser watcher = new FakeUser(UUID.randomUUID(), "Admin") {
            @Override
            public boolean hasPermission(final String permission) {
                return "spy.permission".equals(permission);
            }
        };
        final ChatPlayerManager players = new ChatPlayerManager();
        players.getPlayer(watcher).setSpy(true);
        final ChatEventProcessor processor = processor(modules,
                new FakePlatform("Bukkit", java.util.Arrays.<ChatUser>asList(sender, watcher)), players);

        final ProcessedChatEvent result = processor.process(sender, "bad", true);

        assertTrue(result.isCancelled());
    }

    private static java.util.Map<String, String> createCorrectionReplacements() {
        java.util.Map<String, String> replacements = new java.util.HashMap<String, String>();
        replacements.put("wath", "what");
        replacements.put("doign", "doing");
        return replacements;
    }

    private static ChatEventProcessor processor(TestModuleManager modules, ChatPlatform platform, ChatPlayerManager players) {
        return new ChatEventProcessor(modules, players, new ChatNotificationManager(), platform, new LocalAlertBus());
    }

    private static TestModuleManager modules() {
        TestModuleManager manager = new TestModuleManager();
        Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("blocked_message", "blocked");
        en.put("server_muted", "muted");
        en.put("no_move_chat_warn_message", "move first");
        en.put("filtered", "filtered");
        locales.put("en", en);
        manager.getMessagesModule().loadData("en", locales);
        manager.getGeneralModule().loadData(false, false, false, Collections.<String>emptyList());
        manager.getSyntaxModule().loadData(false, "Syntax", 0, "", false, new String[0], new String[0]);
        manager.getAllowedCharactersModule().loadData(false,
                dev._2lstudios.chatsentinel.shared.modules.AllowedCharactersModule.DEFAULT_MODE,
                dev._2lstudios.chatsentinel.shared.modules.AllowedCharactersModule.DEFAULT_ALLOWED_REGEX,
                dev._2lstudios.chatsentinel.shared.modules.AllowedCharactersModule.DEFAULT_REPLACEMENT);
        manager.getCapitalizationModule().loadData(false, "Capitalization", true, 0, 0, "", false, new String[0], false,
                new String[0], new java.util.function.Supplier<Collection<String>>() {
                    @Override
                    public Collection<String> get() {
                        return Collections.emptyList();
                    }
                });
        manager.getCooldownModule().loadData(false, 0, 0, 0, 0);
        manager.getFloodModule().loadData(false, "Flood", false, 0, "", "", false, new String[0]);
        manager.getBlacklistModule().loadData(false, "Blacklist", false, false, "", 0, "", false, new String[0], new String[0], true);
        manager.getNoMoveChatModule().loadData(false, "move.bypass", 5.0D, true);
        return manager;
    }

    private static final class TestModuleManager extends ModuleManager {
        private CooldownModerationModule cooldownOverride;
        private FloodModerationModule floodOverride;

        @Override
        public CooldownModerationModule getCooldownModule() {
            return cooldownOverride == null ? super.getCooldownModule() : cooldownOverride;
        }

        @Override
        public FloodModerationModule getFloodModule() {
            return floodOverride == null ? super.getFloodModule() : floodOverride;
        }

        private void setCooldownOverride(final CooldownModerationModule cooldownOverride) {
            this.cooldownOverride = cooldownOverride;
        }

        private void setFloodOverride(final FloodModerationModule floodOverride) {
            this.floodOverride = floodOverride;
        }

        @Override
        public void reloadData(FilterCompileStatus status) {
        }
    }

    private static final class HiddenCooldownModule extends CooldownModerationModule {
        @Override
        public ChatEventResult processEvent(final ChatPlayer chatPlayer, final MessagesModule messagesModule,
                final String playerName, final String originalMessage, final String lang) {
            return new ChatEventResult(originalMessage, false, true);
        }
    }

    private static final class TrackingFloodModule extends FloodModerationModule {
        private boolean called;

        @Override
        public ChatEventResult processEvent(final ChatPlayer chatPlayer, final MessagesModule messagesModule,
                final String playerName, final String message, final String lang) {
            this.called = true;
            return null;
        }

        private boolean wasCalled() {
            return called;
        }
    }

    private static final class FakePlatform implements ChatPlatform {
        private final String name;
        private final List<ChatUser> users;

        private FakePlatform(String name, Collection<ChatUser> users) {
            this.name = name;
            this.users = new ArrayList<ChatUser>(users);
        }

        @Override
        public Collection<ChatUser> getOnlineUsers() {
            return users;
        }

        @Override
        public Optional<ChatUser> findUser(UUID uniqueId) {
            for (ChatUser user : users) {
                if (user.getUniqueId().equals(uniqueId)) {
                    return Optional.of(user);
                }
            }
            return Optional.empty();
        }

        @Override
        public void sendConsoleMessage(String legacyMessage) {
        }

        @Override
        public void dispatchConsoleCommand(String command) {
        }

        @Override
        public void runAsync(Runnable runnable) {
            runnable.run();
        }

        @Override
        public String getPlatformName() {
            return name;
        }

        @Override
        public void refreshOnlinePlayers(ChatPlayerManager chatPlayerManager,
                ChatNotificationManager chatNotificationManager,
                GeneralModule generalModule) {
        }
    }

    private static class FakeUser implements ChatUser {
        private final UUID uuid;
        private final String name;
        private final List<String> messages = new ArrayList<String>();

        private FakeUser(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        @Override
        public UUID getUniqueId() { return uuid; }

        @Override
        public String getName() { return name; }

        @Override
        public String getLocale() { return "en"; }

        @Override
        public String getServerName() { return "server"; }

        @Override
        public boolean hasPermission(String permission) { return false; }

        @Override
        public void sendMessage(String legacyMessage) {
            messages.add(legacyMessage);
        }

        @Override
        public void sendWarning(String legacyMessage, WarningDeliverySettings settings) {
            messages.add(legacyMessage);
        }

        private List<String> getMessages() {
            return messages;
        }
    }
}
