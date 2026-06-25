package dev._2lstudios.chatsentinel.velocity.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import dev._2lstudios.chatsentinel.shared.modules.WhitelistModule;
import dev._2lstudios.chatsentinel.velocity.ChatSentinel;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ProcessedChatEvent;
import dev._2lstudios.chatsentinel.velocity.platform.VelocityChatUser;

public class ChatListener {
	private final ChatSentinel plugin;
	private final WhitelistModule whitelistModule;

	public ChatListener(ChatSentinel plugin, WhitelistModule whitelistModule) {
		this.plugin = plugin;
        this.whitelistModule = whitelistModule;
    }

	@Subscribe(order = PostOrder.LAST)
	public void onChatEvent(PlayerChatEvent event) {
		if (!event.getResult().isAllowed()) {
			return;
		}

		// Sender
		Player player = event.getPlayer();
		
		if (player == null) {
			return;
		}

		// Check if the player's current server is on the whitelist
		if (player.getCurrentServer().isPresent()) {
			String playerCurrentServer = player.getCurrentServer().get().getServerInfo().getName();
			if (whitelistModule.getWhitelistedServers().contains(playerCurrentServer)) {
				return;
			}
		}

		// Get event variables
		String message = event.getMessage();

		// Get chat player
		VelocityChatUser chatUser = new VelocityChatUser(player, plugin.getMessageSink());
		ChatPlayer chatPlayer = plugin.getChatPlayerManager().getPlayer(chatUser);

		// Process the event
		if (plugin.getModuleManager().isSignedChatWarnOnly()) {
			plugin.getChatEventProcessor().process(chatUser, message, false);
			trackAllowedMessage(event, chatPlayer, player, message);
			return;
		}

		ProcessedChatEvent finalResult = plugin.getChatEventProcessor().process(chatUser, message, true);

		// Apply modifiers to event
		if (finalResult.isCancelled()) {
			// Velocity 1.19.1+ can kick signed-chat clients when denied; warn-only avoids mutation.
			event.setResult(PlayerChatEvent.ChatResult.denied());
		} else {
			event.setResult(PlayerChatEvent.ChatResult.message(finalResult.getMessage()));
		}

		// Set last message
		trackAllowedMessage(event, chatPlayer, player, finalResult.getMessage());
	}

	private void trackAllowedMessage(PlayerChatEvent event, ChatPlayer chatPlayer, Player player, String message) {
		if (!event.getResult().isAllowed()) {
			return;
		}

		if (message.startsWith("/")) {
			chatPlayer.addLastCommand(System.currentTimeMillis());
		} else {
			chatPlayer.addLastMessage(message, System.currentTimeMillis());
		}
	}
}
