package dev._2lstudios.chatsentinel.bungee.listeners;

import dev._2lstudios.chatsentinel.bungee.ChatSentinel;
import dev._2lstudios.chatsentinel.bungee.platform.BungeeChatUser;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayerManager;
import dev._2lstudios.chatsentinel.shared.chat.ProcessedChatEvent;
import dev._2lstudios.chatsentinel.shared.modules.WhitelistModule;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class ChatListener implements Listener {
	private WhitelistModule whitelistModule;
	private ChatPlayerManager chatPlayerManager;
	private final ChatSentinel plugin;

	public ChatListener(ChatSentinel plugin, WhitelistModule whitelistModule, ChatPlayerManager chatPlayerManager) {
		this.plugin = plugin;
		this.whitelistModule = whitelistModule;
		this.chatPlayerManager = chatPlayerManager;
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onChatEvent(ChatEvent event) {
		if (event.isCancelled()) {
			return;
		}

		// Sender
		Connection sender = event.getSender();
		
		if (!(sender instanceof ProxiedPlayer)) {
			return;
		}

		// Get player
		ProxiedPlayer player = (ProxiedPlayer) sender;

		// Check if the player's current server is on the whitelist
		if (player.getServer() != null) {
			String playerCurrentServer = player.getServer().getInfo().getName();
			if (whitelistModule.getWhitelistedServers().contains(playerCurrentServer)) {
				return;
			}
		}

		// Get event variables
		String message = event.getMessage();

		// Get chat player
		BungeeChatUser chatUser = new BungeeChatUser(player, plugin.getMessageSink());
		ChatPlayer chatPlayer = chatPlayerManager.getPlayer(chatUser);

		// Process the event
		ProcessedChatEvent finalResult = plugin.getChatEventProcessor().process(chatUser, message, true);

		// Apply modifiers to event
		if (finalResult.isHide()) {
			event.setCancelled(true);
			chatUser.sendMessage(finalResult.getMessage());
		} else if (finalResult.isCancelled()) {
			event.setCancelled(true);
		} else {
			event.setMessage(finalResult.getMessage());
		}

		// Set last message
		if (!event.isCancelled()) {
			if (message.startsWith("/")) {
				chatPlayer.addLastCommand(System.currentTimeMillis());
			} else {
				chatPlayer.addLastMessage(finalResult.getMessage(), System.currentTimeMillis());
			}
		}
	}
}
