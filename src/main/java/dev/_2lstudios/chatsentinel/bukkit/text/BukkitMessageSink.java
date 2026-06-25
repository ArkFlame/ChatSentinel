package dev._2lstudios.chatsentinel.bukkit.text;

import java.util.logging.Logger;

import org.bukkit.entity.Player;

import dev._2lstudios.chatsentinel.shared.text.LegacyText;
import dev._2lstudios.chatsentinel.shared.text.MessageSink;
import dev._2lstudios.chatsentinel.shared.text.WarningDeliverySettings;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public final class BukkitMessageSink implements MessageSink<Player> {
	private final Logger logger;
	private boolean actionBarWarningLogged;

	public BukkitMessageSink(Logger logger) {
		this.logger = logger;
	}

	public void sendBlankLines(final Player player, final int lineCount) {
		if (player == null || lineCount <= 0) {
			return;
		}
		for (int i = 0; i < lineCount; i++) {
			player.sendMessage("");
		}
	}

	@Override
	public void sendMessage(Player player, String legacyMessage) {
		if (player == null || legacyMessage == null || legacyMessage.isEmpty()) {
			return;
		}
		for (final String line : LegacyText.toSectionLines(legacyMessage)) {
			player.sendMessage(line);
		}
	}

	@Override
	public void sendActionBar(Player player, String legacyMessage) {
		if (player == null || legacyMessage == null || legacyMessage.isEmpty()) {
			return;
		}
		sendActionBarSafely(player, LegacyText.toSection(legacyMessage).replace('\n', ' '));
	}

	@Override
	public void sendWarning(Player player, String legacyMessage, WarningDeliverySettings settings) {
		if (settings == null || legacyMessage == null || legacyMessage.isEmpty()) {
			return;
		}
		final String sectionMessage = LegacyText.toSection(legacyMessage);
		if (settings.isMessageEnabled()) {
			sendMessage(player, sectionMessage);
		}
		if (settings.isActionBarEnabled() && !sendActionBarSafely(player, sectionMessage.replace('\n', ' ')) && !settings.isMessageEnabled()) {
			logActionBarUnsupported();
		}
	}

	private boolean sendActionBarSafely(Player player, String legacyMessage) {
		try {
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacyMessage));
			return true;
		} catch (NoClassDefFoundError | NoSuchMethodError | UnsupportedOperationException exception) {
			return false;
		}
	}

	private void logActionBarUnsupported() {
		if (!actionBarWarningLogged && logger != null) {
			actionBarWarningLogged = true;
			logger.warning("Warning action-bar delivery is unavailable on this Bukkit server; warning chat fallback is disabled.");
		}
	}
}
