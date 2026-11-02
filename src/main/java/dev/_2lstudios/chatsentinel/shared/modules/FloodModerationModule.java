package dev._2lstudios.chatsentinel.shared.modules;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import dev._2lstudios.chatsentinel.shared.chat.ChatEventResult;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;

public class FloodModerationModule extends ModerationModule {
	private static final String DEFAULT_PATTERN = "(([\\p{L}\\p{N}_])\\2{3,})|([^\\p{L}\\p{N}_ñ]{20,})|(^.{220,}$)";

	private boolean replace;
	private Pattern pattern = Pattern.compile(DEFAULT_PATTERN);

	public void loadData(boolean enabled, String customName, boolean replace, int maxWarns, String pattern,
			String warnNotification, boolean webhookEnabled, String[] commands) {
		setEnabled(enabled);
		setMaxWarns(maxWarns);
		setWarnNotification(warnNotification);
		setWebhookEnabled(webhookEnabled);
		setCommands(commands);
		setCustomName(customName);
		this.replace = replace;
		this.pattern = compilePattern(pattern);
	}

	public boolean isReplace() {
		return this.replace;
	}

	public String replace(String string) {
		return pattern.matcher(string).replaceAll("");
	}

	@Override
	public ChatEventResult processEvent(ChatPlayer chatPlayer, MessagesModule messagesModule, String playerName,
			String message, String lang) {
		if (!isEnabled() || message == null || !pattern.matcher(message).find()) {
			return null;
		}

		if (isReplace()) {
			final String replaced = replace(message);
			if (!replaced.isEmpty() && !replaced.equals(message)) {
				return ChatEventResult.rewrite(replaced);
			}
		}

		return ChatEventResult.block(message).withPlayerMessage(messagesModule.getWarnMessage(null, lang, getName()));
	}

	@Override
	public String getName() {
		return "Flood";
	}

	@Override
	public String getCustomName() {
		return defaultCustomName(super.getCustomName(), getName());
	}

	private Pattern compilePattern(String configured) {
		if (configured == null || configured.trim().isEmpty()) {
			return Pattern.compile(DEFAULT_PATTERN);
		}
		try {
			return Pattern.compile(configured);
		} catch (PatternSyntaxException exception) {
			return Pattern.compile(DEFAULT_PATTERN);
		}
	}
}
