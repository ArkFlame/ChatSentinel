package dev._2lstudios.chatsentinel.bukkit.listeners;

import dev._2lstudios.chatsentinel.bukkit.ChatSentinel;
import dev._2lstudios.chatsentinel.bukkit.chat.BukkitOutboundChatDecorationTracker;
import dev._2lstudios.chatsentinel.bukkit.platform.BukkitChatUser;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.chat.LegacyChatFormatRenderer;
import dev._2lstudios.chatsentinel.shared.chat.ProcessedChatEvent;
import dev._2lstudios.chatsentinel.shared.modules.ChatSnapshotModule;
import dev._2lstudios.chatsentinel.bukkit.utils.FoliaAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class AsyncPlayerChatListener implements Listener {
    private final ChatSentinel plugin;
    private final ChatPlayerManager chatPlayerManager;
    private final LegacyChatFormatRenderer chatFormatRenderer;
    private final BukkitOutboundChatDecorationTracker decorationTracker;

    public AsyncPlayerChatListener(final ChatSentinel plugin, final ChatPlayerManager chatPlayerManager,
            final BukkitOutboundChatDecorationTracker decorationTracker) {
        this.plugin = plugin;
        this.chatPlayerManager = chatPlayerManager;
        this.decorationTracker = decorationTracker;
        this.chatFormatRenderer = new LegacyChatFormatRenderer();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAsyncPlayerChatCandidate(final AsyncPlayerChatEvent event) {
        if (event == null || decorationTracker == null) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        final String message = event.getMessage();
        if (isIgnoredCommand(message) || message.startsWith("/")) {
            return;
        }
        decorationTracker.trackCandidate(player.getUniqueId(), player.getName(), message, System.currentTimeMillis());
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAsyncPlayerChatFinalFormat(final AsyncPlayerChatEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }

        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        final String finalMessage = event.getMessage();
        if (isIgnoredCommand(finalMessage) || finalMessage.startsWith("/")) {
            return;
        }

        final ChatSnapshotModule snapshotModule = plugin.getModuleManager().getChatSnapshotModule();
        if (snapshotModule == null || !snapshotModule.isEnabled()) {
            return;
        }

        final Collection<Player> recipients = new ArrayList<Player>(event.getRecipients());
        if (recipients.isEmpty()) {
            return;
        }

        final String renderedLine = renderLine(event, player, finalMessage);
        decorationTracker.markFinalLine(player.getUniqueId(), finalMessage, renderedLine,
                recipientIds(recipients), snapshotModule, System.currentTimeMillis());

        if (!snapshotModule.isLiveDeleteClickEnabled()) {
            return;
        }

        final Optional<ChatSnapshotModule.Entry> entry = snapshotModule.record(player.getUniqueId(), player.getName(),
                finalMessage, renderedLine, recipientIds(recipients));
        if (!entry.isPresent()) {
            return;
        }

        event.setCancelled(true);
        sendLiveDeleteBroadcast(recipients, renderedLine, entry.get());
    }

    private void sendLiveDeleteBroadcast(final Collection<Player> recipients, final String renderedLine,
            final ChatSnapshotModule.Entry entry) {
        final ChatSnapshotModule snapshotModule = plugin.getModuleManager().getChatSnapshotModule();
        for (final Player recipient : recipients) {
            if (recipient == null) {
                continue;
            }
            FoliaAPI.runTaskForEntity(plugin, recipient, new Runnable() {
                @Override
                public void run() {
                    if (!recipient.isOnline()) {
                        return;
                    }
                    if (recipient.hasPermission(snapshotModule.getLiveDeletePermission())) {
                        plugin.getMessageSink().sendClickablePrefixMessage(recipient, snapshotModule.getLiveDeletePrefix(),
                                snapshotModule.getLiveDeleteHover(), snapshotModule.buildLiveDeleteCommand(entry.getId()),
                                renderedLine);
                        return;
                    }
                    plugin.getMessageSink().sendMessage(recipient, renderedLine);
                }
            }, null, 0L);
        }
    }

    private boolean isIgnoredCommand(final String message) {
        return message != null && message.startsWith("/")
                && !plugin.getModuleManager().getGeneralModule().isCommand(message);
    }

    private String renderLine(final AsyncPlayerChatEvent event, final Player player, final String message) {
        return chatFormatRenderer.render(event.getFormat(), player.getDisplayName(), player.getName(), message);
    }

    private Collection<UUID> recipientIds(final Collection<Player> recipients) {
        final Collection<UUID> ids = new ArrayList<UUID>();
        for (final Player recipient : recipients) {
            if (recipient != null) {
                ids.add(recipient.getUniqueId());
            }
        }
        return ids;
    }
}
