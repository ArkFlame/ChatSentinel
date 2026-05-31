package dev._2lstudios.chatsentinel.bukkit.chat;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import dev._2lstudios.chatsentinel.bukkit.ChatSentinel;
import dev._2lstudios.chatsentinel.bukkit.utils.FoliaAPI;
import dev._2lstudios.chatsentinel.shared.modules.ChatSnapshotModule;
import dev._2lstudios.chatsentinel.shared.text.LegacyText;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.logging.Logger;

public final class BukkitLiveDeletePacketListener implements PacketListener {

    private final ChatSentinel plugin;
    private final BukkitOutboundChatDecorationTracker decorationTracker;
    private boolean loggedSystemChatParseError;
    private boolean loggedChatMessageParseError;

    public BukkitLiveDeletePacketListener(final ChatSentinel plugin,
            final BukkitOutboundChatDecorationTracker decorationTracker) {
        this.plugin = plugin;
        this.decorationTracker = decorationTracker;
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }

        final PacketTypeCommon packetType = event.getPacketType();
        if (!packetType.equals(PacketType.Play.Server.SYSTEM_CHAT_MESSAGE)
                && !packetType.equals(PacketType.Play.Server.CHAT_MESSAGE)) {
            return;
        }

        final Object player = event.getPlayer();
        if (!(player instanceof Player)) {
            return;
        }

        final Player viewer = (Player) player;

        if (plugin.getModuleManager() == null) {
            return;
        }

        final ChatSnapshotModule snapshotModule = plugin.getModuleManager().getChatSnapshotModule();
        if (snapshotModule == null || !snapshotModule.isEnabled() || !snapshotModule.isLiveDeleteClickEnabled()) {
            return;
        }

        final ExtractedChatLine extracted = extractChatLine(event, packetType);
        if (extracted == null) {
            return;
        }

        if (decorationTracker.isAlreadyDecorated(extracted.legacyLine, snapshotModule.getLiveDeletePrefix())) {
            return;
        }

        final Optional<BukkitOutboundChatDecorationTracker.DecorationMatch> match =
                decorationTracker.match(viewer.getUniqueId(), extracted.legacyLine, snapshotModule, System.currentTimeMillis());

        if (!match.isPresent()) {
            return;
        }

        if (!viewer.hasPermission(snapshotModule.getLiveDeletePermission())) {
            return;
        }

        event.setCancelled(true);

        final String entryId = match.get().getEntryId();
        if (entryId == null || entryId.trim().isEmpty()) {
            return;
        }
        FoliaAPI.runTaskForEntity(plugin, viewer, new Runnable() {
            @Override
            public void run() {
                if (!viewer.isOnline()) {
                    return;
                }
                plugin.getMessageSink().sendClickablePrefixMessage(viewer,
                        snapshotModule.getLiveDeletePrefix(),
                        snapshotModule.getLiveDeleteHover(),
                        snapshotModule.buildLiveDeleteCommand(entryId),
                        extracted.bodyComponents);
            }
        }, null, 0L);
    }

    private ExtractedChatLine extractChatLine(final PacketSendEvent event, final PacketTypeCommon packetType) {
        if (packetType.equals(PacketType.Play.Server.SYSTEM_CHAT_MESSAGE)) {
            return extractSystemChatLine(event);
        } else if (packetType.equals(PacketType.Play.Server.CHAT_MESSAGE)) {
            return extractChatMessageLine(event);
        }
        return null;
    }

    private ExtractedChatLine extractSystemChatLine(final PacketSendEvent event) {
        try {
            final WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
            if (packet.isOverlay()) {
                return null;
            }
            final String json = packet.getMessageJson();
            return fromJson(json);
        } catch (final Exception exception) {
            if (!loggedSystemChatParseError) {
                loggedSystemChatParseError = true;
                final Logger logger = plugin.getLogger();
                if (logger != null) {
                    logger.fine("Failed to parse SYSTEM_CHAT_MESSAGE packet: " + exception.getMessage());
                }
            }
            return null;
        }
    }

    private ExtractedChatLine extractChatMessageLine(final PacketSendEvent event) {
        try {
            final WrapperPlayServerChatMessage packet = new WrapperPlayServerChatMessage(event);
            if (packet.getMessage() == null) {
                return null;
            }
            final String json = packet.getMessage().getChatContentJson(event.getClientVersion());
            return fromJson(json);
        } catch (final Exception exception) {
            if (!loggedChatMessageParseError) {
                loggedChatMessageParseError = true;
                final Logger logger = plugin.getLogger();
                if (logger != null) {
                    logger.fine("Failed to parse CHAT_MESSAGE packet: " + exception.getMessage());
                }
            }
            return null;
        }
    }

    private ExtractedChatLine fromJson(final String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            final BaseComponent[] components = ComponentSerializer.parse(json);
            if (components == null || components.length == 0) {
                return null;
            }
            final StringBuilder legacyBuilder = new StringBuilder();
            for (final BaseComponent component : components) {
                if (component != null) {
                    legacyBuilder.append(component.toLegacyText());
                }
            }
            return new ExtractedChatLine(legacyBuilder.toString(), components);
        } catch (final Exception exception) {
            final String legacy = LegacyText.toSection(json);
            if (!legacy.isEmpty() && (legacy.indexOf('{') < 0 || legacy.indexOf('}') < 0)) {
                final BaseComponent[] components = TextComponent.fromLegacyText(legacy);
                return new ExtractedChatLine(legacy, components);
            }
            return null;
        }
    }

    private static final class ExtractedChatLine {
        final String legacyLine;
        final BaseComponent[] bodyComponents;

        ExtractedChatLine(final String legacyLine, final BaseComponent[] bodyComponents) {
            this.legacyLine = legacyLine;
            this.bodyComponents = bodyComponents;
        }
    }
}
