package dev._2lstudios.chatsentinel.bungee;

import dev._2lstudios.chatsentinel.bungee.commands.ChatSentinelCommand;
import dev._2lstudios.chatsentinel.bungee.commands.AutoCorrectCommand;
import dev._2lstudios.chatsentinel.bungee.commands.ServerMuteCommand;
import dev._2lstudios.chatsentinel.bungee.commands.SocialSpyCommand;
import dev._2lstudios.chatsentinel.bungee.config.BungeeMutableModuleConfigStore;
import dev._2lstudios.chatsentinel.bungee.filter.BungeeUserFilterWriter;
import dev._2lstudios.chatsentinel.bungee.listeners.ChatListener;
import dev._2lstudios.chatsentinel.bungee.listeners.PlayerDisconnectListener;
import dev._2lstudios.chatsentinel.bungee.listeners.PostLoginListener;
import dev._2lstudios.chatsentinel.bungee.listeners.SocialSpyCommandListener;
import dev._2lstudios.chatsentinel.bungee.modules.BungeeModuleManager;
import dev._2lstudios.chatsentinel.bungee.platform.BungeeChatPlatform;
import dev._2lstudios.chatsentinel.bungee.text.BungeeMessageSink;
import dev._2lstudios.chatsentinel.bungee.utils.ConfigUtil;
import dev._2lstudios.chatsentinel.shared.alerts.AlertBus;
import dev._2lstudios.chatsentinel.shared.alerts.AlertPayload;
import dev._2lstudios.chatsentinel.shared.alerts.LocalAlertBus;
import dev._2lstudios.chatsentinel.shared.alerts.RedisAlertBus;
import dev._2lstudios.chatsentinel.shared.chat.ChatEventProcessor;
import dev._2lstudios.chatsentinel.shared.chat.ChatNotificationManager;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.commands.ChatSentinelCommandService;
import dev._2lstudios.chatsentinel.shared.filter.UserRegexAddService;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileError;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileReport;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileStatus;
import dev._2lstudios.chatsentinel.shared.modules.GeneralModule;
import dev._2lstudios.chatsentinel.shared.packet.PacketEventsHook;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyService;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.config.Configuration;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ChatSentinel extends Plugin {
    private static ChatSentinel instance;

    private BungeeModuleManager moduleManager;
    private ChatPlayerManager chatPlayerManager;
    private ChatNotificationManager chatNotificationManager;
    private BungeeMessageSink messageSink;
    private BungeeChatPlatform chatPlatform;
    private ChatEventProcessor chatEventProcessor;
    private PacketEventsHook packetEventsHook;
    private ChatSentinelCommandService commandService;
    private SocialSpyService socialSpyService;
    private AlertBus alertBus = new LocalAlertBus();
    private String redisInstanceId;

    public static ChatSentinel getInstance() {
        return instance;
    }

    public static void setInstance(final ChatSentinel instance) {
        ChatSentinel.instance = instance;
    }

    @Override
    public void onEnable() {
        setInstance(this);
        final ConfigUtil configUtil = new ConfigUtil(this);
        configUtil.create("%datafolder%/config.yml");
        configUtil.create("%datafolder%/messages.yml");
        configUtil.create("%datafolder%/whitelist.yml");
        configUtil.create("%datafolder%/blacklist.yml");

        final ProxyServer server = getProxy();
        moduleManager = new BungeeModuleManager(configUtil);
        messageSink = new BungeeMessageSink(getLogger());
        logCompileReport(moduleManager.getLastCompileReport());
        logNoMoveChatProxyWarning();
        final GeneralModule generalModule = moduleManager.getGeneralModule();
        chatPlayerManager = new ChatPlayerManager();
        chatNotificationManager = new ChatNotificationManager();
        alertBus = createAlertBus(configUtil.get("%datafolder%/config.yml"));
        chatPlatform = new BungeeChatPlatform(this, getProxy(), messageSink);
        socialSpyService = new SocialSpyService(moduleManager, chatPlayerManager, chatPlatform);
        chatEventProcessor = new ChatEventProcessor(moduleManager, chatPlayerManager, chatNotificationManager, chatPlatform, alertBus);
        if (moduleManager.getTextModerationSettings().isPacketEventsEnabled()) {
            if (getProxy().getPluginManager().getPlugin("packetevents") != null) {
                try {
                    packetEventsHook = new PacketEventsHook(moduleManager, chatEventProcessor, chatPlatform);
                    getLogger().info("PacketEvents hook enabled: signs, anvil renames and books will be moderated on the proxy.");
                } catch (Throwable throwable) {
                    getLogger().warning("Unable to register PacketEvents hook: " + throwable.getMessage());
                }
            } else {
                getLogger().info("PacketEvents is not installed; sign/anvil/book moderation on this proxy is disabled.");
            }
        }
        commandService = new ChatSentinelCommandService(moduleManager, chatPlayerManager, chatNotificationManager, chatPlatform,
                new UserRegexAddService(new BungeeUserFilterWriter(getDataFolder())), new BungeeMutableModuleConfigStore(this, configUtil),
                socialSpyService);

        final PluginManager pluginManager = server.getPluginManager();
        pluginManager.registerListener(this, new ChatListener(this, moduleManager.getWhitelistModule(), chatPlayerManager));
        pluginManager.registerListener(this, new SocialSpyCommandListener(this, socialSpyService));
        pluginManager.registerListener(this, new PlayerDisconnectListener(generalModule, chatPlayerManager, chatNotificationManager));
        pluginManager.registerListener(this, new PostLoginListener(generalModule, chatPlayerManager, chatNotificationManager));
        pluginManager.registerCommand(this, new ChatSentinelCommand(this));
        pluginManager.registerCommand(this, new AutoCorrectCommand(this));
        pluginManager.registerCommand(this, new ServerMuteCommand(this));
        pluginManager.registerCommand(this, new SocialSpyCommand(this));

        getProxy().getScheduler().schedule(this, new Runnable() {
            @Override
            public void run() {
                if (generalModule.needsNicknameCompile()) {
                    generalModule.compileNicknamesPattern();
                }
            }
        }, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDisable() {
        if (packetEventsHook != null) {
            packetEventsHook.unregister();
        }
        alertBus.close();
    }

    public BungeeModuleManager getModuleManager() {
        return moduleManager;
    }

    public ChatEventProcessor getChatEventProcessor() {
        return chatEventProcessor;
    }

    public BungeeMessageSink getMessageSink() {
        return messageSink;
    }

    public ChatSentinelCommandService getCommandService() {
        return commandService;
    }

    public SocialSpyService getSocialSpyService() {
        return socialSpyService;
    }

    private void logCompileReport(final FilterCompileReport report) {
        if (report == null) {
            return;
        }
        getLogger().info(FilterCompileStatus.formatSummary(report));
        for (FilterCompileError error : report.getErrors()) {
            getLogger().warning(FilterCompileStatus.formatError(error));
        }
    }

    private void logNoMoveChatProxyWarning() {
        if (moduleManager.getNoMoveChatModule().isEnabled()) {
            getLogger().warning("no-move-chat.enabled is ignored on BungeeCord because proxies cannot see player movement.");
        }
    }

    private AlertBus createAlertBus(final Configuration config) {
        if (config == null || !config.getBoolean("redis.enabled", false)) {
            return new LocalAlertBus();
        }
        redisInstanceId = config.getString("redis.instance-id", "auto");
        if (redisInstanceId == null || redisInstanceId.trim().isEmpty() || "auto".equalsIgnoreCase(redisInstanceId)) {
            redisInstanceId = java.util.UUID.randomUUID().toString();
        }
        try {
            return new RedisAlertBus(config.getString("redis.uri", "redis://localhost:6379/0"),
                    config.getString("redis.channel", "chatsentinel:alerts"), redisInstanceId,
                    config.getBoolean("redis.publish-alerts", true), config.getBoolean("redis.receive-alerts", true),
                    this::dispatchRemoteAlert, getLogger());
        } catch (RuntimeException exception) {
            getLogger().warning("Redis alert bus disabled: " + exception.getMessage());
            return new LocalAlertBus();
        }
    }

    private void dispatchRemoteAlert(final AlertPayload payload) {
        getProxy().getScheduler().runAsync(this, new Runnable() {
            @Override
            public void run() {
                final String notificationMessage = payload.getNotificationMessage();
                final String spyMessage = payload.getSpyMessage();
                if (notificationMessage != null && !notificationMessage.isEmpty()) {
                    for (ChatPlayer chatPlayer : chatNotificationManager.getAllPlayers()) {
                        if (moduleManager.getSpyModule().isEnabled() && chatPlayer.isSpy()) {
                            continue;
                        }
                        final Optional<ChatUser> user = chatPlatform.findUser(chatPlayer.getUniqueId());
                        if (user.isPresent()) {
                            user.get().sendMessage(notificationMessage);
                        }
                    }
                    chatPlatform.sendConsoleMessage(notificationMessage);
                }

                if (moduleManager.getSpyModule().isEnabled() && spyMessage != null && !spyMessage.isEmpty()) {
                    for (ChatPlayer chatPlayer : chatPlayerManager.getAllPlayers()) {
                        if (!chatPlayer.isSpy()) {
                            continue;
                        }
                        final Optional<ChatUser> user = chatPlatform.findUser(chatPlayer.getUniqueId());
                        if (user.isPresent() && user.get().hasPermission(moduleManager.getSpyModule().getPermission())) {
                            user.get().sendMessage(spyMessage);
                        }
                    }
                }
            }
        });
    }
}
