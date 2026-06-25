package dev._2lstudios.chatsentinel.bukkit.platform;

import dev._2lstudios.chatsentinel.bukkit.ChatSentinel;
import dev._2lstudios.chatsentinel.bukkit.text.BukkitMessageSink;
import dev._2lstudios.chatsentinel.bukkit.utils.FoliaAPI;
import dev._2lstudios.chatsentinel.shared.chat.ChatNotificationManager;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.modules.GeneralModule;
import dev._2lstudios.chatsentinel.shared.platform.ChatPlatform;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;
import dev._2lstudios.chatsentinel.shared.text.LegacyText;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BukkitChatPlatform implements ChatPlatform {
    private final ChatSentinel plugin;
    private final Server server;
    private final BukkitMessageSink messageSink;
    private final Map<UUID, Player> onlinePlayers = new ConcurrentHashMap<UUID, Player>();
    private final Map<UUID, String> onlinePlayerNames = new ConcurrentHashMap<UUID, String>();

    public BukkitChatPlatform(final ChatSentinel plugin, final Server server, final BukkitMessageSink messageSink) {
        this.plugin = plugin;
        this.server = server;
        this.messageSink = messageSink;
    }

    public void trackPlayer(final Player player) {
        if (player == null) {
            return;
        }
        onlinePlayers.put(player.getUniqueId(), player);
        onlinePlayerNames.put(player.getUniqueId(), player.getName());
    }

    public void untrackPlayer(final UUID uniqueId) {
        if (uniqueId == null) {
            return;
        }
        onlinePlayers.remove(uniqueId);
        onlinePlayerNames.remove(uniqueId);
    }

    public Collection<String> getOnlinePlayerNamesSnapshot() {
        return new ArrayList<String>(onlinePlayerNames.values());
    }

    @Override
    public Collection<ChatUser> getOnlineUsers() {
        final List<ChatUser> users = new ArrayList<ChatUser>();
        for (Player player : onlinePlayers.values()) {
            users.add(new BukkitChatUser(plugin, player, messageSink));
        }
        return users;
    }

    @Override
    public Optional<ChatUser> findUser(final UUID uniqueId) {
        final Player player = onlinePlayers.get(uniqueId);
        return player == null ? Optional.<ChatUser>empty() : Optional.<ChatUser>of(new BukkitChatUser(plugin, player, messageSink));
    }

    @Override
    public void sendConsoleMessage(final String legacyMessage) {
        FoliaAPI.runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (final String line : LegacyText.toSectionLines(legacyMessage)) {
                    server.getConsoleSender().sendMessage(line);
                }
            }
        });
    }

    @Override
    public void dispatchConsoleCommand(final String command) {
        FoliaAPI.runTask(plugin, new Runnable() {
            @Override
            public void run() {
                server.dispatchCommand(server.getConsoleSender(), command);
            }
        });
    }

    @Override
    public void runAsync(final Runnable runnable) {
        FoliaAPI.runTaskAsync(plugin, runnable);
    }

    @Override
    public String getPlatformName() {
        return "Bukkit";
    }

    @Override
    public void refreshOnlinePlayers(final ChatPlayerManager chatPlayerManager,
            final ChatNotificationManager chatNotificationManager,
            final GeneralModule generalModule) {
        for (Player player : server.getOnlinePlayers()) {
            trackPlayer(player);
            final BukkitChatUser user = new BukkitChatUser(plugin, player, messageSink);
            final ChatPlayer chatPlayer = chatPlayerManager.getPlayer(user);
            chatPlayer.setLocale(null);
            final Location location = player.getLocation();
            chatPlayer.resetMovementGate(location.getWorld() == null ? "" : location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ());
            if (player.hasPermission("chatsentinel.notify")) {
                chatNotificationManager.addPlayer(chatPlayer);
            } else if (chatNotificationManager.containsPlayer(chatPlayer)) {
                chatNotificationManager.removePlayer(chatPlayer);
            }
            generalModule.addNickname(player.getName());
        }
        generalModule.compileNicknamesPattern();
    }
}
