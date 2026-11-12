package dev._2lstudios.chatsentinel.shared.modules;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import dev._2lstudios.chatsentinel.shared.chat.ChatEventResult;
import dev._2lstudios.chatsentinel.shared.chat.ChatPlayer;
import dev._2lstudios.chatsentinel.shared.filter.CompiledFilterFile;
import dev._2lstudios.chatsentinel.shared.filter.CompiledFilterRegistry;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompiler;
import dev._2lstudios.chatsentinel.shared.filter.FilterExpressionFile;
import dev._2lstudios.chatsentinel.shared.filter.FilterKind;
import dev._2lstudios.chatsentinel.shared.filter.FilterMatch;
import dev._2lstudios.chatsentinel.shared.filter.FilterModuleSettings;
import dev._2lstudios.chatsentinel.shared.filter.FilterModuleSettingsRegistry;
import dev._2lstudios.chatsentinel.shared.filter.FilterSource;
import dev._2lstudios.chatsentinel.shared.moderation.ModerationActionSettings;
import dev._2lstudios.chatsentinel.shared.moderation.ModerationIdentity;
import dev._2lstudios.chatsentinel.shared.moderation.ModerationViolation;

public class BlacklistModerationModule extends ModerationModule {
	private static final FilterSource LEGACY_BLACKLIST_SOURCE = new FilterSource(FilterKind.BLACKLIST, "default", "blacklist.yml", "Blacklist");
	private static final CompiledFilterRegistry EMPTY_BLACKLIST_REGISTRY = new CompiledFilterRegistry(FilterKind.BLACKLIST, Collections.emptyList());
	private static final CompiledFilterRegistry EMPTY_WHITELIST_REGISTRY = new CompiledFilterRegistry(FilterKind.WHITELIST, Collections.emptyList());

	private ModuleManager moduleManager;

	private boolean fakeMessage;
    	private boolean blockRawMessage;
	private CompiledFilterRegistry blacklistRegistry = EMPTY_BLACKLIST_REGISTRY;
	private CompiledFilterRegistry whitelistRegistry = EMPTY_WHITELIST_REGISTRY;
	private FilterModuleSettingsRegistry settingsRegistry = new FilterModuleSettingsRegistry(Collections.<String, FilterModuleSettings>emptyMap(), FilterModuleSettings.disabled("default"));

	private boolean censorshipEnabled;
	private String censorshipReplacement;

	public BlacklistModerationModule(ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	public void loadData(boolean enabled, String customName, boolean fakeMessage, boolean censorshipEnabled, String censorshipReplacement, int maxWarns,
        String warnNotification, boolean webhookEnabled, String[] commands, String[] patterns, boolean blockRawMessage) {
		FilterCompiler compiler = new FilterCompiler();
		FilterExpressionFile expressionFile = new FilterExpressionFile(LEGACY_BLACKLIST_SOURCE,
				Arrays.asList(patterns == null ? new String[0] : patterns));
		CompiledFilterRegistry registry = compiler.compile(FilterKind.BLACKLIST, Collections.singletonList(expressionFile)).getRegistry();
		FilterModuleSettings settings = FilterModuleSettings.defaultBlacklist("default", enabled, customName, fakeMessage,
				censorshipEnabled, censorshipReplacement, maxWarns, warnNotification, webhookEnabled, commands, blockRawMessage);
		Map<String, FilterModuleSettings> settingsByModuleId = new HashMap<String, FilterModuleSettings>();
		settingsByModuleId.put(settings.getModuleId(), settings);
		// Internal compatibility adapter until platform managers load source-aware files
		loadData(registry, EMPTY_WHITELIST_REGISTRY, new FilterModuleSettingsRegistry(settingsByModuleId, settings));
	}

	public void loadData(CompiledFilterRegistry blacklistRegistry, CompiledFilterRegistry whitelistRegistry,
			FilterModuleSettingsRegistry settingsRegistry) {
		this.blacklistRegistry = blacklistRegistry == null ? EMPTY_BLACKLIST_REGISTRY : blacklistRegistry;
		this.whitelistRegistry = whitelistRegistry == null ? EMPTY_WHITELIST_REGISTRY : whitelistRegistry;
		this.settingsRegistry = settingsRegistry == null
				? new FilterModuleSettingsRegistry(Collections.<String, FilterModuleSettings>emptyMap(), FilterModuleSettings.disabled("default"))
				: settingsRegistry;
		FilterModuleSettings settings = this.settingsRegistry.resolve("default");
		applyDefaultSettings(settings);
	}

	public boolean isFakeMessage() {
		return this.fakeMessage;
	}

	public boolean isCensorshipEnabled() {
		return censorshipEnabled;
	}

	public String getCensorshipReplacement() {
		return censorshipReplacement;
	}

	public boolean isBlockRawMessage() {
		return this.blockRawMessage;
	}

	public CompiledFilterRegistry getBlacklistRegistry() {
		return blacklistRegistry;
	}

    public FilterModuleSettingsRegistry getSettingsRegistry() {
        return settingsRegistry;
    }

	@Override
	public ChatEventResult processEvent(ChatPlayer chatPlayer, MessagesModule messagesModule, String playerName,
			String message, String lang) {
		return processEvent(chatPlayer, messagesModule, playerName, message, lang, true);
	}

	@Override
	public ChatEventResult processTextEvent(ChatPlayer chatPlayer, MessagesModule messagesModule, String playerName,
			String text, String lang) {
		return processEvent(chatPlayer, messagesModule, playerName, text, lang, false);
	}

	private ChatEventResult processEvent(ChatPlayer chatPlayer, MessagesModule messagesModule, String playerName,
			String message, String lang, boolean commandAware) {
		if (!isEnabled()) {
			return null;
		}

		GeneralModule generalModule = moduleManager.getGeneralModule();
		WhitelistModule whitelistModule = moduleManager.getWhitelistModule();

		String sanitizedMessage = message;

		// Filter the arguments of the commands
		if (commandAware && sanitizedMessage.startsWith("/") && message.contains(" ")) {
			sanitizedMessage = sanitizedMessage.substring(message.indexOf(" "));
		}

		// Remove weird stuff
		if (generalModule.isSanitizeEnabled()) {
			sanitizedMessage = generalModule.sanitize(message);
		}

		// Remove names
		if (generalModule.isSanitizeNames()) {
			sanitizedMessage = generalModule.sanitizeNames(message);
		}

		sanitizedMessage = removeWhitelistMatches(sanitizedMessage, whitelistModule);

		Optional<FilterMatch> optionalMatch = blacklistRegistry.findFirst(sanitizedMessage);
		if (!optionalMatch.isPresent()) {
			return null;
		}

		FilterMatch match = optionalMatch.get();
		if (match.getMatchedText().isEmpty()) {
			return null;
		}
		FilterModuleSettings settings = settingsRegistry.resolve(match.getSource().getModuleId());
		if (!settings.isEnabled()) {
			return null;
		}

		String replacedMessage = findPattern(match).matcher(message).replaceAll(settings.getCensorshipReplacement());
		ModerationViolation violation = new ModerationViolation(
				ModerationIdentity.blacklist(match.getSource(), settings),
				new ModerationActionSettings(settings.getMaxWarns(), settings.getWarnNotification(), settings.isWebhookEnabled(), settings.getCommands()),
				match);

		if (settings.isFakeMessage()) {
			return ChatEventResult.selfOnly(message).withViolation(violation);
		} else if (settings.isCensorshipEnabled()) {
			return ChatEventResult.rewrite(replacedMessage).withViolation(violation);
		} else if (settings.isBlockRawMessage()) {
			return ChatEventResult.block(message).withViolation(violation);
		} else {
			return ChatEventResult.pass(message).withViolation(violation);
		}
	}

	@Override
	public String getName() {
		return "Blacklist";
	}

	private String removeWhitelistMatches(String sanitizedMessage, WhitelistModule whitelistModule) {
		String result = sanitizedMessage;
		Optional<FilterMatch> whitelistMatch = whitelistRegistry.findFirst(result);
		while (whitelistMatch.isPresent()) {
			FilterMatch match = whitelistMatch.get();
			if (match.getStart() == match.getEnd()) {
				break;
			}
			result = result.substring(0, match.getStart()) + result.substring(match.getEnd());
			whitelistMatch = whitelistRegistry.findFirst(result);
		}
		if (whitelistRegistry.fileCount() == 0 && whitelistModule.isEnabled()) {
			return whitelistModule.getPattern().matcher(result).replaceAll("");
		}
		return result;
	}

	private void applyDefaultSettings(FilterModuleSettings settings) {
		setEnabled(settings.isEnabled());
		setMaxWarns(settings.getMaxWarns());
		setWarnNotification(settings.getWarnNotification());
		setWebhookEnabled(settings.isWebhookEnabled());
		setCommands(settings.getCommands());
		setCustomName(settings.getCustomName());
		this.fakeMessage = settings.isFakeMessage();
		this.censorshipEnabled = settings.isCensorshipEnabled();
		this.censorshipReplacement = settings.getCensorshipReplacement();
		this.blockRawMessage = settings.isBlockRawMessage();
	}

	private Pattern findPattern(FilterMatch match) {
		for (CompiledFilterFile file : blacklistRegistry.files()) {
			if (file.getSource().equals(match.getSource())) {
				return file.getPattern();
			}
		}
		return blacklistRegistry.files().get(0).getPattern();
	}
}
