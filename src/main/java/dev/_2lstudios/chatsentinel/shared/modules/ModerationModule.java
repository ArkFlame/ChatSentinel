package dev._2lstudios.chatsentinel.shared.modules;

import java.util.Locale;

import dev._2lstudios.chatsentinel.shared.chat.ChatEventResult;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.utils.PlaceholderUtil;

public abstract class ModerationModule {
	private boolean enabled = true;
	private int maxWarns = 0;
	private String warnNotification = "";
	private boolean webhookEnabled = true;
	private String[] commands = new String[0];
	private String customName;

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isWebhookEnabled() {
		return webhookEnabled;
	}

	public int getMaxWarns() {
		return maxWarns;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setWebhookEnabled(boolean webhookEnabled) {
		this.webhookEnabled = webhookEnabled;
	}

	public void setMaxWarns(int maxWarns) {
		this.maxWarns = maxWarns;
	}

	public String getWarnNotification(String[][] placeholders) {
		if (this.warnNotification == null || this.warnNotification.isEmpty()) {
			return null;
		}
		return PlaceholderUtil.replacePlaceholders(this.warnNotification, placeholders);
	}

	public void setWarnNotification(String warnNotification) {
		this.warnNotification = warnNotification == null ? "" : warnNotification;
	}

	public boolean hasExceededWarns(ChatPlayer chatPlayer) {
		return maxWarns > 0 && chatPlayer.getWarns(this) >= maxWarns;
	}

	public abstract String getName();

	public String getIdentityKey() {
		return getName().toLowerCase(Locale.ROOT);
	}

	public String getCustomName() {
		return customName;
	}

	public void setCustomName(String customName) {
		this.customName = customName;
	}

	public abstract ChatEventResult processEvent(ChatPlayer chatPlayer, MessagesModule messagesModule, String playerName, String originalMessage, String lang);

	public String getBypassPermission() {
		return "chatsentinel.bypass." + getName();
	}

	public String[] getCommands(String[][] placeholders) {
		if (this.commands.length == 0) {
			return new String[0];
		}
		String[] resolved = this.commands.clone();
		for (int i = 0; i < resolved.length; i++) {
			resolved[i] = PlaceholderUtil.replacePlaceholders(resolved[i], placeholders);
		}
		return resolved;
	}

	public void setCommands(String[] commands) {
		this.commands = commands == null ? new String[0] : commands;
	}

	/** Shared display-name fallback so callers never see a null module name. */
	protected static String defaultCustomName(String customName, String fallback) {
		return customName == null || customName.trim().isEmpty() ? fallback : customName;
	}
}
