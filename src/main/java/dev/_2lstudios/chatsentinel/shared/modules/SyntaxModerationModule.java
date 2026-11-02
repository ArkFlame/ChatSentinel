package dev._2lstudios.chatsentinel.shared.modules;

import dev._2lstudios.chatsentinel.shared.chat.ChatEventResult;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;

public class SyntaxModerationModule extends ModerationModule {
	private String[] whitelist = new String[0];

	public void loadData(boolean enabled, String customName, int maxWarns, String warnNotification,
			boolean webhookEnabled, String[] whitelist, String[] commands) {
		setEnabled(enabled);
		setMaxWarns(maxWarns);
		setWarnNotification(warnNotification);
		setWebhookEnabled(webhookEnabled);
		setCommands(commands);
		setCustomName(customName);
		this.whitelist = whitelist == null ? new String[0] : whitelist;
	}

	public boolean isWhitelisted(String message) {
		if (message == null) {
			return false;
		}
		for (String entry : whitelist) {
			if (entry != null && message.startsWith(entry)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public ChatEventResult processEvent(ChatPlayer chatPlayer, MessagesModule messagesModule, String playerName,
			String message, String lang) {
		if (isEnabled() && !isWhitelisted(message) && hasSyntax(message)) {
			return ChatEventResult.block(message).withPlayerMessage(messagesModule.getWarnMessage(null, lang, getName()));
		}

		return null;
	}

	@Override
	public String getName() {
		return "Syntax";
	}

	@Override
	public String getCustomName() {
		return defaultCustomName(super.getCustomName(), getName());
	}

	private boolean hasSyntax(String message) {
		if (message == null || !message.startsWith("/")) {
			return false;
		}
		final String command = message.contains(" ") ? message.substring(0, message.indexOf(' ')) : message;
		return command.indexOf(':', 1) > 0;
	}
}
