package dev._2lstudios.chatsentinel.shared.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev._2lstudios.chatsentinel.shared.chat.ChatEventProcessor;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.platform.ChatPlatform;

public final class PacketEventsHook {
    private final PacketListenerCommon registration;

    public PacketEventsHook(ModuleManager moduleManager, ChatEventProcessor processor, ChatPlatform platform) {
        this.registration = PacketEvents.getAPI().getEventManager().registerListener(
                new PacketTextModerationListener(moduleManager, processor, platform),
                PacketListenerPriority.NORMAL);
    }

    public void unregister() {
        if (registration != null && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(registration);
        }
    }
}
