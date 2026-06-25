package dev._2lstudios.chatsentinel.shared;

import dev._2lstudios.chatsentinel.shared.chat.ChatNotificationManager;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.commands.ChatSentinelCommandService;
import dev._2lstudios.chatsentinel.shared.config.MutableModuleConfigStore;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileStatus;
import dev._2lstudios.chatsentinel.shared.filter.UserFilterWriter;
import dev._2lstudios.chatsentinel.shared.filter.UserRegexAddService;
import dev._2lstudios.chatsentinel.shared.modules.GeneralModule;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.platform.ChatPlatform;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.platform.CommandActor;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyService;
import dev._2lstudios.chatsentinel.shared.text.WarningDeliverySettings;
import org.junit.Test;

import java.io.IOException;
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

public class ChatSentinelCommandServiceTest {
    @Test
    public void clear_sendsBlankPayloadToNonBypassAndNoticeOnlyToBypass() {
        TestModuleManager modules = modules();
        FakeUser normal = new FakeUser(UUID.randomUUID(), "Normal", false);
        FakeUser bypass = new FakeUser(UUID.randomUUID(), "Bypass", true);
        FakeActor actor = new FakeActor("Admin");
        ChatSentinelCommandService service = service(modules, new FakePlatform(normal, bypass), new FakeConfigStore(), new FakeWriter());

        service.execute(actor, new String[] { "clear", "Spamming" });

        assertTrue(normal.messages.get(0).startsWith(" \n"));
        assertTrue(normal.messages.get(0).contains("Reason: §fSpamming"));
        assertTrue(bypass.messages.get(0).contains("bypass Spamming"));
        assertTrue(!bypass.messages.get(0).startsWith(" \n"));
    }

    @Test
    public void mutechat_writesServerMutePath_whenToggle() {
        TestModuleManager modules = modules();
        FakeConfigStore store = new FakeConfigStore();
        ChatSentinelCommandService service = service(modules, new FakePlatform(), store, new FakeWriter());

        service.execute(new FakeActor("Admin"), new String[] { "mutechat", "toggle" });

        assertEquals("server-mute.muted=true", store.lastWrite);
    }

    @Test
    public void servermuteAlias_writesServerMutePath_whenNoArgsToggle() {
        TestModuleManager modules = modules();
        FakeConfigStore store = new FakeConfigStore();
        ChatSentinelCommandService service = service(modules, new FakePlatform(), store, new FakeWriter());

        service.execute(new FakeActor("Admin"), "servermute", new String[0]);

        assertEquals("server-mute.muted=true", store.lastWrite);
    }

    @Test
    public void servermuteAlias_suggestsModes() {
        TestModuleManager modules = modules();
        ChatSentinelCommandService service = service(modules, new FakePlatform(), new FakeConfigStore(), new FakeWriter());

        List<String> suggestions = service.suggest(new FakeActor("Admin"), "servermute", new String[] { "o" });

        assertEquals(java.util.Arrays.asList("on", "off"), suggestions);
    }

    @Test
    public void servermuteAlias_updatesInMemoryMutedState() {
        TestModuleManager modules = modules();
        ChatSentinelCommandService service = service(modules, new FakePlatform(), new FakeConfigStore(), new FakeWriter());

        service.execute(new FakeActor("Admin"), "servermute", new String[] { "on" });

        assertTrue(modules.getServerMuteModule().isMuted());
    }

    @Test
    public void regexAdd_callsWriter_whenCommon() {
        TestModuleManager modules = modules();
        FakeWriter writer = new FakeWriter();
        ChatSentinelCommandService service = service(modules, new FakePlatform(), new FakeConfigStore(), writer);

        service.execute(new FakeActor("Admin"), new String[] { "regex", "add", "user", "common", "hello" });

        assertEquals("user", writer.moduleId);
    }

    @Test
    public void help_doesNotMentionDeleteOrRecentchats() {
        TestModuleManager modules = modules();
        FakeActor actor = new FakeActor("Admin");
        ChatSentinelCommandService service = service(modules, new FakePlatform(), new FakeConfigStore(), new FakeWriter());

        service.execute(actor, new String[] { "help" });

        for (String msg : actor.messages) {
            final String lower = msg.toLowerCase(java.util.Locale.ROOT);
            assertFalse("help output must not mention 'delete': " + msg, lower.contains("delete"));
            assertFalse("help output must not mention 'recentchats': " + msg, lower.contains("recentchats"));
        }
    }

    private static ChatSentinelCommandService service(TestModuleManager modules, ChatPlatform platform,
            MutableModuleConfigStore store, UserFilterWriter writer) {
        final ChatPlayerManager chatPlayerManager = new ChatPlayerManager();
        return new ChatSentinelCommandService(modules, chatPlayerManager, new ChatNotificationManager(), platform,
                new UserRegexAddService(writer), store, new SocialSpyService(modules, chatPlayerManager, platform));
    }

    private static TestModuleManager modules() {
        TestModuleManager manager = new TestModuleManager();
        Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("cleared", "&dGame chat has been cleared by &f%player%&d. Reason: &f%reason%");
        en.put("clear_bypass_notice", "bypass %reason%");
        en.put("clear_sender_summary", "%cleared% %bypassed%");
        en.put("no_permission", "no permission");
        en.put("reload", "reload");
        en.put("help", "help");
        en.put("unknown_command", "unknown");
        en.put("notify-enabled", "notify on");
        en.put("notify-disabled", "notify off");
        en.put("server_mute_enabled", "muted");
        en.put("server_mute_disabled", "unmuted");
        locales.put("en", en);
        manager.getMessagesModule().loadData("en", locales);
        manager.getServerMuteModule().loadData(true, false, "mute.bypass");
        return manager;
    }

    private static final class TestModuleManager extends ModuleManager {
        @Override
        public void reloadData(FilterCompileStatus status) {
        }
    }

    private static final class FakeConfigStore implements MutableModuleConfigStore {
        private String lastWrite;

        @Override
        public void setBoolean(String path, boolean value) throws IOException {
            lastWrite = path + "=" + value;
        }
    }

    private static final class FakeWriter implements UserFilterWriter {
        private String moduleId;

        @Override
        public void appendExpression(String moduleId, String expression) throws IOException {
            this.moduleId = moduleId;
        }
    }

    private static final class FakePlatform implements ChatPlatform {
        private final List<ChatUser> users;

        private FakePlatform(ChatUser... users) {
            this.users = new ArrayList<ChatUser>();
            Collections.addAll(this.users, users);
        }

        @Override
        public Collection<ChatUser> getOnlineUsers() { return users; }

        @Override
        public Optional<ChatUser> findUser(UUID uniqueId) { return Optional.empty(); }

        @Override
        public void sendConsoleMessage(String legacyMessage) { }

        @Override
        public void dispatchConsoleCommand(String command) { }

        @Override
        public void runAsync(Runnable runnable) { runnable.run(); }

        @Override
        public String getPlatformName() { return "Test"; }

        @Override
        public void refreshOnlinePlayers(ChatPlayerManager chatPlayerManager,
                ChatNotificationManager chatNotificationManager,
                GeneralModule generalModule) { }
    }

    private static final class FakeUser implements ChatUser {
        private final UUID uuid;
        private final String name;
        private final boolean bypass;
        private final List<String> messages = new ArrayList<String>();

        private FakeUser(UUID uuid, String name, boolean bypass) {
            this.uuid = uuid;
            this.name = name;
            this.bypass = bypass;
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
        public boolean hasPermission(String permission) { return bypass && "chatsentinel.clear.bypass".equals(permission); }

        @Override
        public void sendMessage(String legacyMessage) { messages.add(legacyMessage); }

        @Override
        public void sendWarning(String legacyMessage, WarningDeliverySettings settings) { messages.add(legacyMessage); }
    }

    private static final class FakeActor implements CommandActor {
        private final String name;
        private final List<String> messages = new ArrayList<String>();

        private FakeActor(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getLocale() { return "en"; }

        @Override
        public boolean isPlayer() { return false; }

        @Override
        public boolean hasPermission(String permission) { return true; }

        @Override
        public void sendMessage(String legacyMessage) { messages.add(legacyMessage); }

        @Override
        public ChatUser asUserOrNull() { return null; }
    }
}
