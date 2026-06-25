package dev._2lstudios.chatsentinel.bukkit.listeners;

import dev._2lstudios.chatsentinel.bukkit.ChatSentinel;
import dev._2lstudios.chatsentinel.bukkit.platform.BukkitChatUser;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.chat.LegacyChatFormatRenderer;
import dev._2lstudios.chatsentinel.shared.chat.ProcessedChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class AsyncPlayerChatListener implements Listener {
    private final ChatSentinel plugin;
    private final ChatPlayerManager chatPlayerManager;
    private final LegacyChatFormatRenderer chatFormatRenderer;

    public AsyncPlayerChatListener(final ChatSentinel plugin, final ChatPlayerManager chatPlayerManager) {
        this.plugin = plugin;
        this.chatPlayerManager = chatPlayerManager;
        this.chatFormatRenderer = new LegacyChatFormatRenderer();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncPlayerChatModeration(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String originalMessage = event.getMessage();

        if (isIgnoredCommand(originalMessage)) {
            return;
        }

        final BukkitChatUser chatUser = new BukkitChatUser(plugin, player, plugin.getMessageSink());
        final ProcessedChatEvent finalResult = plugin.getChatEventProcessor().process(chatUser, originalMessage, true);

        if (finalResult.isHide()) {
            event.setCancelled(true);
            chatUser.sendMessage(renderLine(event, player, finalResult.getMessage()));
            return;
        }

        if (finalResult.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        event.setMessage(finalResult.getMessage());
        final String finalMessage = finalResult.getMessage();
        final ChatPlayer chatPlayer = chatPlayerManager.getPlayer(chatUser);
        chatPlayer.addLastMessage(finalMessage, System.currentTimeMillis());
    }

    private boolean isIgnoredCommand(final String message) {
        return message != null && message.startsWith("/")
                && !plugin.getModuleManager().getGeneralModule().isCommand(message);
    }

    private String renderLine(final AsyncPlayerChatEvent event, final Player player, final String message) {
        return chatFormatRenderer.render(event.getFormat(), player.getDisplayName(), player.getName(), message);
    }
}
