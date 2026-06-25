package dev._2lstudios.chatsentinel.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
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
import dev._2lstudios.chatsentinel.velocity.commands.ChatSentinelCommand;
import dev._2lstudios.chatsentinel.velocity.config.VelocityMutableModuleConfigStore;
import dev._2lstudios.chatsentinel.velocity.filter.VelocityUserFilterWriter;
import dev._2lstudios.chatsentinel.velocity.listeners.ChatListener;
import dev._2lstudios.chatsentinel.velocity.listeners.PlayerDisconnectListener;
import dev._2lstudios.chatsentinel.velocity.listeners.PostLoginListener;
import dev._2lstudios.chatsentinel.velocity.listeners.SocialSpyCommandListener;
import dev._2lstudios.chatsentinel.velocity.modules.VelocityModuleManager;
import dev._2lstudios.chatsentinel.velocity.platform.VelocityChatPlatform;
import dev._2lstudios.chatsentinel.velocity.text.VelocityMessageSink;
import dev._2lstudios.chatsentinel.velocity.utils.ConfigUtil;
import dev._2lstudios.chatsentinel.velocity.utils.Constants;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyService;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(id = Constants.ID, name = Constants.NAME, version = Constants.VERSION, description = Constants.DESCRIPTION,
        url = Constants.URL, authors = Constants.AUTHOR)
public class ChatSentinel {
    private final ProxyServer server;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityModuleManager moduleManager;
    private GeneralModule generalModule;
    private ChatPlayerManager chatPlayerManager;
    private ChatNotificationManager chatNotificationManager;
    private VelocityMessageSink messageSink;
    private VelocityChatPlatform chatPlatform;
    private ChatEventProcessor chatEventProcessor;
    private ChatSentinelCommandService commandService;
    private SocialSpyService socialSpyService;
    private AlertBus alertBus = new LocalAlertBus();
    private String redisInstanceId;

    @Inject
    public ChatSentinel(final ProxyServer server, final ComponentLogger logger, @DataDirectory final Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(final ProxyInitializeEvent event) {
        final ConfigUtil configUtil = new ConfigUtil(this);
        moduleManager = new VelocityModuleManager(configUtil);
        messageSink = new VelocityMessageSink();
        alertBus = createAlertBus(configUtil.get("config.yml"));
        logCompileReport(moduleManager.getLastCompileReport());
        logNoMoveChatProxyWarning();
        logSignedChatPolicyWarning();
        generalModule = moduleManager.getGeneralModule();
        chatPlayerManager = new ChatPlayerManager();
        chatNotificationManager = new ChatNotificationManager();
        chatPlatform = new VelocityChatPlatform(this, server, messageSink);
        socialSpyService = new SocialSpyService(moduleManager, chatPlayerManager, chatPlatform);
        chatEventProcessor = new ChatEventProcessor(moduleManager, chatPlayerManager, chatNotificationManager, chatPlatform, alertBus);
        commandService = new ChatSentinelCommandService(moduleManager, chatPlayerManager, chatNotificationManager, chatPlatform,
                new UserRegexAddService(new VelocityUserFilterWriter(dataDirectory)), new VelocityMutableModuleConfigStore(this),
                socialSpyService);

        final EventManager eventManager = server.getEventManager();
        eventManager.register(this, new ChatListener(this, moduleManager.getWhitelistModule()));
        eventManager.register(this, new SocialSpyCommandListener(this, socialSpyService));
        eventManager.register(this, new PlayerDisconnectListener(this, generalModule, chatPlayerManager, chatNotificationManager));
        eventManager.register(this, new PostLoginListener(this, generalModule, chatPlayerManager, chatNotificationManager));

        final CommandManager commandManager = server.getCommandManager();
        final CommandMeta commandMeta = commandManager.metaBuilder("chatsentinel")
                .aliases("autocorrect", "correction", "servermute", "muteall", "muteserver", "socialspy", "sspy")
                .plugin(this)
                .build();
        final SimpleCommand chatSentinelCommand = new ChatSentinelCommand(this);
        commandManager.register(commandMeta, chatSentinelCommand);

        server.getScheduler().buildTask(this, new Runnable() {
            @Override
            public void run() {
                if (generalModule.needsNicknameCompile()) {
                    generalModule.compileNicknamesPattern();
                }
            }
        }).delay(1L, TimeUnit.SECONDS).repeat(1L, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        alertBus.close();
    }

    public ProxyServer getServer() {
        return server;
    }

    public ComponentLogger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public VelocityModuleManager getModuleManager() {
        return moduleManager;
    }

    public ChatPlayerManager getChatPlayerManager() {
        return chatPlayerManager;
    }

    public VelocityMessageSink getMessageSink() {
        return messageSink;
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

    private void logCompileReport(final FilterCompileReport report) {
        if (report == null) {
            return;
        }
        logger.info(FilterCompileStatus.formatSummary(report));
        for (FilterCompileError error : report.getErrors()) {
            logger.warn(FilterCompileStatus.formatError(error));
        }
    }

    private void logNoMoveChatProxyWarning() {
        if (moduleManager.getNoMoveChatModule().isEnabled()) {
            logger.warn("no-move-chat.enabled is ignored on Velocity because proxies cannot see player movement.");
        }
    }

    private void logSignedChatPolicyWarning() {
        if (moduleManager.isSignedChatWarningOnStartup() && "legacy-deny".equals(moduleManager.getSignedChatPolicy())) {
            logger.warn("ChatSentinel may kick 1.19.1+ signed-chat clients when blocking/modifying chat on Velocity. Use backend Bukkit mode or a signed-chat compatibility plugin if needed.");
        }
    }

    private AlertBus createAlertBus(final CommentedConfigurationNode config) {
        if (config == null || !config.node("redis", "enabled").getBoolean(false)) {
            return new LocalAlertBus();
        }
        redisInstanceId = config.node("redis", "instance-id").getString("auto");
        if (redisInstanceId == null || redisInstanceId.trim().isEmpty() || "auto".equalsIgnoreCase(redisInstanceId)) {
            redisInstanceId = java.util.UUID.randomUUID().toString();
        }
        try {
            return new RedisAlertBus(config.node("redis", "uri").getString("redis://localhost:6379/0"),
                    config.node("redis", "channel").getString("chatsentinel:alerts"), redisInstanceId,
                    config.node("redis", "publish-alerts").getBoolean(true), config.node("redis", "receive-alerts").getBoolean(true),
                    this::dispatchRemoteAlert, Logger.getLogger("ChatSentinel"));
        } catch (RuntimeException exception) {
            logger.warn("Redis alert bus disabled: {}", exception.getMessage());
            return new LocalAlertBus();
        }
    }

    private void dispatchRemoteAlert(final AlertPayload payload) {
        server.getScheduler().buildTask(this, new Runnable() {
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
        }).schedule();
    }
}
