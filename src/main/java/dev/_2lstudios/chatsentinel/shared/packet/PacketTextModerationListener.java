package dev._2lstudios.chatsentinel.shared.packet;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import dev._2lstudios.chatsentinel.shared.chat.ChatEventProcessor;
import dev._2lstudios.chatsentinel.shared.chat.ChatModerationDecision;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.modules.TextModerationSettings;
import dev._2lstudios.chatsentinel.shared.platform.ChatPlatform;
import dev._2lstudios.chatsentinel.shared.platform.ChatUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketTextModerationListener implements PacketListener {
    private static final long ANVIL_PUNISH_INTERVAL_MS = 10_000L;
    private static final long ANVIL_FEEDBACK_INTERVAL_MS = 2_000L;

    private final ModuleManager moduleManager;
    private final ChatEventProcessor processor;
    private final ChatPlatform platform;
    private final ConcurrentHashMap<UUID, Long> anvilPunishTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> anvilFeedbackTimestamps = new ConcurrentHashMap<>();

    public PacketTextModerationListener(final ModuleManager moduleManager, final ChatEventProcessor processor,
            final ChatPlatform platform) {
        this.moduleManager = moduleManager;
        this.processor = processor;
        this.platform = platform;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.isCancelled() || event.getUser() == null || event.getUser().getUUID() == null) {
            return;
        }

        final UUID uuid = event.getUser().getUUID();
        final Optional<ChatUser> foundUser = platform.findUser(uuid);
        if (!foundUser.isPresent()) {
            return;
        }
        final ChatUser user = foundUser.get();
        final TextModerationSettings settings = moduleManager.getTextModerationSettings();
        final PacketTypeCommon packetType = event.getPacketType();

        if (PacketType.Play.Client.UPDATE_SIGN.equals(packetType)) {
            if (!settings.isSignsEnabled()) {
                return;
            }
            final String text = join(new WrapperPlayClientUpdateSign(event).getTextLines());
            if (text.isEmpty()) {
                return;
            }
            final ChatModerationDecision decision = processor.processText(user, text, true);
            if (decision.isTerminal()) {
                event.setCancelled(true);
                sendFeedback(user, decision);
            }
            return;
        }

        if (PacketType.Play.Client.NAME_ITEM.equals(packetType)) {
            if (!settings.isAnvilsEnabled()) {
                return;
            }
            final String text = new WrapperPlayClientNameItem(event).getItemName();
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            final ChatModerationDecision decision = processor.processText(user, text, false);
            if (!decision.isTerminal()) {
                return;
            }
            event.setCancelled(true);
            final long now = System.currentTimeMillis();
            if (throttle(anvilPunishTimestamps, uuid, now, ANVIL_PUNISH_INTERVAL_MS)) {
                processor.processText(user, text, true);
            }
            if (throttle(anvilFeedbackTimestamps, uuid, now, ANVIL_FEEDBACK_INTERVAL_MS)) {
                sendFeedback(user, decision);
            }
            return;
        }

        if (PacketType.Play.Client.EDIT_BOOK.equals(packetType)) {
            if (!settings.isBooksEnabled()) {
                return;
            }
            final WrapperPlayClientEditBook wrapper = new WrapperPlayClientEditBook(event);
            if (!Boolean.TRUE.equals(wrapper.getSigning())) {
                return;
            }
            final StringBuilder builder = new StringBuilder();
            final String title = wrapper.getTitle();
            if (title != null && !title.trim().isEmpty()) {
                builder.append(title);
            }
            final List<String> pages = wrapper.getPages();
            if (pages != null) {
                for (String page : pages) {
                    if (page == null) {
                        continue;
                    }
                    final String cleaned = page.replace('\n', ' ');
                    if (cleaned.trim().isEmpty()) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(cleaned);
                }
            }
            final String text = builder.toString().trim();
            if (text.isEmpty()) {
                return;
            }
            final ChatModerationDecision decision = processor.processText(user, text, true);
            if (decision.isTerminal()) {
                event.setCancelled(true);
                sendFeedback(user, decision);
            }
        }
    }

    private boolean throttle(final ConcurrentHashMap<UUID, Long> timestamps, final UUID uuid, final long now,
            final long interval) {
        final boolean[] allowed = { false };
        timestamps.compute(uuid, (key, previous) -> {
            if (previous == null || now - previous >= interval) {
                allowed[0] = true;
                return now;
            }
            return previous;
        });
        return allowed[0];
    }

    private String join(final String[] lines) {
        if (lines == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    private void sendFeedback(final ChatUser user, final ChatModerationDecision decision) {
        decision.getPlayerFeedback().ifPresent(user::sendMessage);
    }
}
