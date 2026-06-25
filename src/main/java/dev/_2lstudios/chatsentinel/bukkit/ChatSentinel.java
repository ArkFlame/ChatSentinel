package dev._2lstudios.chatsentinel.bukkit;

import dev._2lstudios.chatsentinel.bukkit.commands.ChatSentinelCommand;
import dev._2lstudios.chatsentinel.bukkit.config.BukkitMutableModuleConfigStore;
import dev._2lstudios.chatsentinel.bukkit.filter.BukkitUserFilterWriter;
import dev._2lstudios.chatsentinel.bukkit.listeners.AsyncPlayerChatListener;
import dev._2lstudios.chatsentinel.bukkit.listeners.PlayerJoinListener;
import dev._2lstudios.chatsentinel.bukkit.listeners.PlayerMoveListener;
import dev._2lstudios.chatsentinel.bukkit.listeners.PlayerQuitListener;
import dev._2lstudios.chatsentinel.bukkit.listeners.PlayerTeleportListener;
import dev._2lstudios.chatsentinel.bukkit.listeners.ServerCommandListener;
import dev._2lstudios.chatsentinel.bukkit.listeners.SocialSpyBookListener;
import dev._2lstudios.chatsentinel.bukkit.listeners.SocialSpyAnvilListener;
import dev._2lstudios.chatsentinel.bukkit.gui.SocialSpyMenu;
import dev._2lstudios.chatsentinel.bukkit.listeners.SocialSpySignListener;
import dev._2lstudios.chatsentinel.bukkit.modules.BukkitModuleManager;
import dev._2lstudios.chatsentinel.bukkit.platform.BukkitChatPlatform;
import dev._2lstudios.chatsentinel.bukkit.text.BukkitMessageSink;
import dev._2lstudios.chatsentinel.bukkit.utils.ConfigUtil;
import dev._2lstudios.chatsentinel.bukkit.utils.FoliaAPI;
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
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyService;
import org.bukkit.Server;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class ChatSentinel extends JavaPlugin {
    private static ChatSentinel instance;

    private BukkitModuleManager moduleManager;
    private ChatPlayerManager chatPlayerManager;
    private ChatNotificationManager chatNotificationManager;
    private BukkitMessageSink messageSink;
    private BukkitChatPlatform chatPlatform;
    private ChatEventProcessor chatEventProcessor;
    private ChatSentinelCommandService commandService;
    private SocialSpyService socialSpyService;
    private SocialSpyMenu socialSpyMenu;
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
        FoliaAPI.init(this);

        final ConfigUtil configUtil = new ConfigUtil(this);
        final Server server = getServer();
        moduleManager = new BukkitModuleManager(configUtil);
        messageSink = new BukkitMessageSink(getLogger());
        logCompileReport(moduleManager.getLastCompileReport());
        final GeneralModule generalModule = moduleManager.getGeneralModule();
        chatPlayerManager = new ChatPlayerManager();
        chatNotificationManager = new ChatNotificationManager();
        alertBus = createAlertBus(configUtil.get("%datafolder%/config.yml"));
        chatPlatform = new BukkitChatPlatform(this, server, messageSink);
        socialSpyService = new SocialSpyService(moduleManager, chatPlayerManager, chatPlatform);
        chatEventProcessor = new ChatEventProcessor(moduleManager, chatPlayerManager, chatNotificationManager, chatPlatform, alertBus);
        commandService = new ChatSentinelCommandService(moduleManager, chatPlayerManager, chatNotificationManager, chatPlatform,
                new UserRegexAddService(new BukkitUserFilterWriter(getDataFolder())), new BukkitMutableModuleConfigStore(this, configUtil),
                socialSpyService);
        socialSpyMenu = new SocialSpyMenu(this, socialSpyService);
        chatPlatform.refreshOnlinePlayers(chatPlayerManager, chatNotificationManager, generalModule);

        final PluginManager pluginManager = server.getPluginManager();
        pluginManager.registerEvents(new AsyncPlayerChatListener(this, chatPlayerManager), this);
        pluginManager.registerEvents(new PlayerJoinListener(generalModule, chatPlayerManager, chatNotificationManager), this);
        pluginManager.registerEvents(new PlayerMoveListener(chatPlayerManager, moduleManager.getNoMoveChatModule()), this);
        pluginManager.registerEvents(new PlayerTeleportListener(chatPlayerManager), this);
        pluginManager.registerEvents(new PlayerQuitListener(moduleManager.getGeneralModule(), chatPlayerManager, chatNotificationManager), this);
        pluginManager.registerEvents(new SocialSpySignListener(socialSpyService), this);
        pluginManager.registerEvents(new SocialSpyBookListener(socialSpyService), this);
        pluginManager.registerEvents(new SocialSpyAnvilListener(socialSpyService), this);
        pluginManager.registerEvents(new ServerCommandListener(chatPlayerManager, chatNotificationManager, socialSpyService), this);

        final ChatSentinelCommand command = new ChatSentinelCommand(this);
        getCommand("chatsentinel").setExecutor(command);
        getCommand("chatsentinel").setTabCompleter(command);

        FoliaAPI.runTaskTimerAsync(this, new java.util.function.Consumer<Object>() {
            @Override
            public void accept(final Object ignored) {
                if (generalModule.needsNicknameCompile()) {
                    generalModule.compileNicknamesPattern();
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        alertBus.close();
        FoliaAPI.cancelAllTasks(this);
        FoliaAPI.reset();
        setInstance(null);
    }

    public BukkitModuleManager getModuleManager() {
        return moduleManager;
    }

    public ChatPlayerManager getChatPlayerManager() {
        return chatPlayerManager;
    }

    public BukkitMessageSink getMessageSink() {
        return messageSink;
    }

    public BukkitChatPlatform getChatPlatform() {
        return chatPlatform;
    }

    public ChatEventProcessor getChatEventProcessor() {
        return chatEventProcessor;
    }

    public ChatSentinelCommandService getCommandService() {
        return commandService;
    }

    public SocialSpyService getSocialSpyService() {
        return socialSpyService;
    }

    public SocialSpyMenu getSocialSpyMenu() {
        return socialSpyMenu;
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

    private AlertBus createAlertBus(final Configuration config) {
        if (!config.getBoolean("redis.enabled", false)) {
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
        FoliaAPI.runTask(this, new Runnable() {
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
