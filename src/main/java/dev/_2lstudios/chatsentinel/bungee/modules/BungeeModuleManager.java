package dev._2lstudios.chatsentinel.bungee.modules;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev._2lstudios.chatsentinel.bungee.ChatSentinel;
import dev._2lstudios.chatsentinel.bungee.filter.BungeeFilterFileLoader;
import dev._2lstudios.chatsentinel.bungee.utils.ConfigUtil;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileReport;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompileStatus;
import dev._2lstudios.chatsentinel.shared.filter.FilterCompiler;
import dev._2lstudios.chatsentinel.shared.filter.DefaultFilterInstaller;
import dev._2lstudios.chatsentinel.shared.filter.FilterExpressionFile;
import dev._2lstudios.chatsentinel.shared.filter.FilterKind;
import dev._2lstudios.chatsentinel.shared.filter.FilterModuleSettings;
import dev._2lstudios.chatsentinel.shared.filter.FilterModuleSettingsRegistry;
import dev._2lstudios.chatsentinel.shared.modules.AllowedCharactersModule;
import dev._2lstudios.chatsentinel.shared.modules.ChatSnapshotModule;
import dev._2lstudios.chatsentinel.shared.modules.ModuleManager;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyModuleId;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyModuleSettings;
import dev._2lstudios.chatsentinel.shared.socialspy.SocialSpyTrimSettings;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;

public class BungeeModuleManager extends ModuleManager {
	private ConfigUtil configUtil;

	public BungeeModuleManager(ConfigUtil configUtil) {
		this.configUtil = configUtil;
		reloadData();
	}

	@Override
	public void reloadData(FilterCompileStatus status) {
		configUtil.create("%datafolder%/config.yml");
		configUtil.create("%datafolder%/messages.yml");
		configUtil.create("%datafolder%/blacklist.yml");
		configUtil.create("%datafolder%/whitelist.yml");

		Configuration configYml = configUtil.get("%datafolder%/config.yml");
		Configuration messagesYml = configUtil.get("%datafolder%/messages.yml");
		Configuration whitelistYml = configUtil.get("%datafolder%/whitelist.yml");
		Map<String, Map<String, String>> locales = new HashMap<>();
		BungeeFilterFileLoader filterFileLoader = new BungeeFilterFileLoader();
		FilterCompiler filterCompiler = new FilterCompiler();
		File dataFolder = ChatSentinel.getInstance().getDataFolder();
		DefaultFilterInstaller.installIfNeeded(dataFolder, BungeeModuleManager.class.getClassLoader(), new java.util.function.Consumer<String>() {
			@Override
			public void accept(final String message) {
				ChatSentinel.getInstance().getLogger().info(message);
			}
		});
		List<FilterExpressionFile> blacklistFiles = filterFileLoader.load(FilterKind.BLACKLIST, dataFolder, configUtil);
		List<FilterExpressionFile> whitelistFiles = filterFileLoader.load(FilterKind.WHITELIST, dataFolder, configUtil);
		if (status != null) {
			status.start(blacklistFiles.size() + whitelistFiles.size());
		}
		FilterCompiler.FilterCompilation blacklistCompilation = filterCompiler.compile(FilterKind.BLACKLIST, blacklistFiles, status);
		FilterCompiler.FilterCompilation whitelistCompilation = filterCompiler.compile(FilterKind.WHITELIST, whitelistFiles, status);
		FilterCompileReport compileReport = FilterCompileReport.combine(blacklistCompilation.getReport(), whitelistCompilation.getReport());
		setLastCompileReport(compileReport);
		if (status != null) {
			status.done(compileReport);
		}
		loadFilterCompileNotification(
				configYml.getBoolean("filter-compile.notify-admins", true),
				configYml.getString("filter-compile.notify-permission", "chatsentinel.admin"));
		loadWarningDelivery(configYml.getBoolean("warnings.delivery.message", true),
				configYml.getBoolean("warnings.delivery.action-bar", true));
		getSpyModule().loadData(configYml.getBoolean("spy.enabled", true),
				configYml.getString("spy.permission", "chatsentinel.spy"),
				configYml.getString("spy.format", "&8[&bCS Spy&8] &e%player% &7failed &6%module% &8file=&f%source_file% &8match=&c%matched_text% &8msg=&f%message%"));
		loadSocialSpy(configYml);

		for (String lang : messagesYml.getSection("langs").getKeys()) {
			Configuration langSection = messagesYml.getSection("langs." + lang);
			Map<String, String> messages = new HashMap<>();

			for (String key : langSection.getKeys()) {
				String value = langSection.getString(key);

				messages.put(key, value);
			}

			locales.put(lang, messages);
		}

		String capitalizationPath = configYml.contains("capitalization") ? "capitalization" : "caps";
		getCapitalizationModule().loadData(configYml.getBoolean(capitalizationPath + ".enabled", true),
				configYml.getString(capitalizationPath + ".custom-module-name", "Capitalization"),
				configYml.getBoolean(configYml.contains("capitalization") ? "capitalization.correct" : "caps.replace", true),
				configYml.getBoolean(capitalizationPath + ".capitalize-first-letter", true),
				configYml.getInt(configYml.contains("capitalization") ? "capitalization.max-uppercase" : "caps.max", 8),
				configYml.getInt(capitalizationPath + ".warn.max", -1),
				configYml.getString(capitalizationPath + ".warn.notification", ""),
				configYml.getBoolean(capitalizationPath + ".warn.webhook-notification", true),
				readPunishments(configYml, capitalizationPath),
				configYml.getBoolean(capitalizationPath + ".whitelist-player-names", true),
				configYml.getStringList(capitalizationPath + ".whitelist").toArray(new String[0]),
				() -> ChatSentinel.getInstance().getProxy().getPlayers().stream()
						.map(ProxiedPlayer::getName)
						.collect(Collectors.toList()),
				configYml.getString(capitalizationPath + ".bypass-permission", ""));
		getCooldownModule().loadData(configYml.getBoolean("cooldown.enabled"),
				configYml.getInt("cooldown.time.repeat-global"), configYml.getInt("cooldown.time.repeat"),
				configYml.getInt("cooldown.time.normal"), configYml.getInt("cooldown.time.command"));
		getSimilarityModule().loadData(configYml.getBoolean("similarity.enabled", true),
				configYml.getString("similarity.custom-module-name", "Similarity"),
				configYml.getDouble("similarity.threshold-percentage", 75.0D),
				configYml.getInt("similarity.compare-last-messages", 3),
				configYml.getInt("similarity.min-normalized-length", 4),
				configYml.getBoolean("similarity.normalize.strip-special-characters", true),
				configYml.getBoolean("similarity.normalize.strip-accents", true),
				configYml.getBoolean("similarity.normalize.collapse-repeated-characters", true));
		getFloodModule().loadData(configYml.getBoolean("flood.enabled"), configYml.getString("flood.custom-module-name"), configYml.getBoolean("flood.replace"),
				configYml.getInt("flood.warn.max"), configYml.getString("flood.pattern"),
				configYml.getString("flood.warn.notification"),
				configYml.getBoolean("flood.warn.webhook-notification"),
				readPunishments(configYml, "flood"));
		getMessagesModule().loadData(messagesYml.getString("default"), locales);
		getServerMuteModule().loadData(configYml.getBoolean("server-mute.enabled", true),
				configYml.getBoolean("server-mute.muted", false),
				configYml.getString("server-mute.bypass-permission", "chatsentinel.mute.bypass"));
		getNoMoveChatModule().loadData(configYml.getBoolean("no-move-chat.enabled", true),
				configYml.getString("no-move-chat.bypass-permission", ""),
				configYml.getDouble("no-move-chat.min-distance-blocks", 5.0D),
				configYml.getBoolean("no-move-chat.allow-teleport", true));
		getChatSnapshotModule().loadData(configYml.getInt("chat-snapshot.clear-lines", ChatSnapshotModule.DEFAULT_CLEAR_LINES));
		getGeneralModule().loadData(configYml.getBoolean("general.sanitize", true),
				configYml.getBoolean("general.sanitize-names", true),
				configYml.getBoolean("general.filter-other", false),
				configYml.getStringList("general.commands"),
				configYml.getString("general.global-bypass-permission", "chatsentinel.bypass"),
				configYml.getStringList("general.global-bypass-excluded-modules"));
		getAllowedCharactersModule().loadData(
				configYml.contains("allowed-characters")
						? configYml.getBoolean("allowed-characters.enabled", false)
						: configYml.getBoolean("general.filter-other", false),
				configYml.getString("allowed-characters.mode", AllowedCharactersModule.DEFAULT_MODE),
				configYml.getString("allowed-characters.allowed-regex", AllowedCharactersModule.DEFAULT_ALLOWED_REGEX),
				configYml.getString("allowed-characters.replacement", AllowedCharactersModule.DEFAULT_REPLACEMENT));
		getCorrectionModule().loadData(
				configYml.getBoolean("correction.enabled", true),
				configYml.getString("correction.custom-module-name", "Correction"),
				configYml.getBoolean("correction.notify-player", true),
				configYml.getBoolean("correction.apply-to-normal-commands", false),
				configYml.getBoolean("correction.preserve-capitalization", true),
				configYml.getBoolean("correction.ignore-player-names", true),
				configYml.getInt("correction.max-corrections-per-message", 8),
				configYml.getString("correction.bypass-permission", ""),
				readStringMap(configYml.getSection("correction.replacements")),
				configYml.getStringList("correction.ignored-words"),
				() -> ChatSentinel.getInstance().getProxy().getPlayers().stream()
						.map(ProxiedPlayer::getName)
						.collect(Collectors.toList()));
		getWhitelistModule().loadData(configYml.getBoolean("whitelist.enabled"),
				whitelistYml.getStringList("servers"),
				whitelistYml.getStringList("expressions").toArray(new String[0]));
		getBlacklistModule().loadData(
				blacklistCompilation.getRegistry(),
				whitelistCompilation.getRegistry(),
				buildBlacklistSettingsRegistry(configYml));
		getSyntaxModule().loadData(configYml.getBoolean("syntax.enabled"), configYml.getString("syntax.custom-module-name"), configYml.getInt("syntax.warn.max"),
				configYml.getString("syntax.warn.notification"),
				configYml.getBoolean("syntax.warn.webhook-notification"),
				configYml.getStringList("syntax.whitelist").toArray(new String[0]),
				readPunishments(configYml, "syntax"));
		getDiscordWebhookModule().loadData(configYml.getBoolean("discord-webhook.enabled"), configYml.getString("discord-webhook.webhook-url"),
				configYml.getString("discord-webhook.sender.username"),
				configYml.getString("discord-webhook.sender.avatar-url"),
				configYml.getString("discord-webhook.author.name"),
				configYml.getString("discord-webhook.author.url"),
				configYml.getString("discord-webhook.author.icon-url"),
				configYml.getString("discord-webhook.title"),
				configYml.getString("discord-webhook.color"),
				configYml.getString("discord-webhook.description"),
				configYml.getString("discord-webhook.field-names.message"),
				configYml.getString("discord-webhook.field-names.server"),
				configYml.getString("discord-webhook.field-names.source-file"),
				configYml.getString("discord-webhook.field-names.source-module"),
				configYml.getString("discord-webhook.field-names.matched-text"),
				configYml.getString("discord-webhook.footer.text"),
				configYml.getString("discord-webhook.footer.icon-url"),
				configYml.getString("discord-webhook.thumbnail-url"));
	}

	private FilterModuleSettingsRegistry buildBlacklistSettingsRegistry(Configuration configYml) {
		FilterModuleSettings fallback = buildBlacklistSettings(configYml, "blacklist", "default", configYml.getString("blacklist.custom-module-name"));
		Map<String, FilterModuleSettings> settingsByModuleId = new HashMap<String, FilterModuleSettings>();
		settingsByModuleId.put(fallback.getModuleId(), fallback);

		if (configYml.contains("blacklist.modules")) {
			Configuration modulesSection = configYml.getSection("blacklist.modules");
			for (String moduleId : modulesSection.getKeys()) {
				settingsByModuleId.put(moduleId, buildBlacklistSettings(configYml, "blacklist.modules." + moduleId, moduleId, moduleId));
			}
		}

		return new FilterModuleSettingsRegistry(settingsByModuleId, fallback);
	}

	private FilterModuleSettings buildBlacklistSettings(Configuration configYml, String path, String moduleId, String defaultCustomName) {
		return FilterModuleSettings.defaultBlacklist(moduleId,
				configYml.getBoolean(path + ".enabled", configYml.getBoolean("blacklist.enabled")),
				configYml.getString(path + ".custom-module-name", defaultCustomName),
				configYml.getBoolean(path + ".fake_message", configYml.getBoolean("blacklist.fake_message")),
				configYml.getBoolean(path + ".censorship.enabled", configYml.getBoolean("blacklist.censorship.enabled", false)),
				configYml.getString(path + ".censorship.replacement", configYml.getString("blacklist.censorship.replacement", "***")),
				configYml.getInt(path + ".warn.max", configYml.getInt("blacklist.warn.max")),
				configYml.getString(path + ".warn.notification", configYml.getString("blacklist.warn.notification")),
				configYml.getBoolean(path + ".warn.webhook-notification", configYml.getBoolean("blacklist.warn.webhook-notification")),
				readPunishments(configYml, path, "blacklist"),
				configYml.getBoolean(path + ".block_raw_message", configYml.getBoolean("blacklist.block_raw_message")));
	}

	private String[] getStringArray(Configuration configYml, String path, String fallbackPath) {
		return configYml.getStringList(configYml.contains(path) ? path : fallbackPath).toArray(new String[0]);
	}

	private String[] readPunishments(Configuration configYml, String basePath) {
		final List<String> values = new ArrayList<String>();
		appendConfigValue(values, configYml.get(basePath + ".punishments"));
		appendConfigValue(values, configYml.get(basePath + ".punishment.commands"));
		appendConfigValue(values, configYml.get(basePath + ".punishment.command"));
		appendConfigValue(values, configYml.get(basePath + ".punishment"));
		return values.toArray(new String[0]);
	}

	private String[] readPunishments(Configuration configYml, String basePath, String fallbackBasePath) {
		final String[] values = readPunishments(configYml, basePath);
		return values.length == 0 ? readPunishments(configYml, fallbackBasePath) : values;
	}

	private void appendConfigValue(List<String> values, Object value) {
		if (value instanceof Iterable<?>) {
			for (Object element : (Iterable<?>) value) {
				appendString(values, element == null ? null : String.valueOf(element));
			}
			return;
		}
		if (value instanceof String) {
			appendString(values, (String) value);
		}
	}

	private void appendString(List<String> values, String value) {
		if (value != null && !value.trim().isEmpty()) {
			values.add(value);
		}
	}

	private Map<String, String> readStringMap(final Configuration section) {
		final Map<String, String> result = new HashMap<String, String>();
		if (section == null) {
			return result;
		}
		for (String key : section.getKeys()) {
			final String value = section.getString(key);
			if (value != null) {
				result.put(key, value);
			}
		}
		return result;
	}

	private void loadSocialSpy(final Configuration configYml) {
		getSocialSpyModule().loadData(
				configYml.getBoolean("social-spy.enabled", true),
				configYml.getString("social-spy.root-permission", "chatsentinel.socialspy"),
				configYml.getBoolean("social-spy.include-self", false),
				configYml.getBoolean("social-spy.show-server", true),
				configYml.getString("social-spy.unavailable-platform-message", "&cThis SocialSpy module is not available on this platform."),
				configYml.getString("social-spy.invalid-module-message", "&cUnknown SocialSpy module. Available: &f%modules%"),
				configYml.getString("social-spy.enabled-message", "&aSocialSpy enabled for &f%module%&a."),
				configYml.getString("social-spy.disabled-message", "&cSocialSpy disabled for &f%module%&c."),
				configYml.getString("social-spy.enabled-all-message", "&aSocialSpy enabled for all permitted modules."),
				configYml.getString("social-spy.disabled-all-message", "&cSocialSpy disabled for all permitted modules."),
				configYml.getString("social-spy.status-header", "&eSocialSpy status:"),
				configYml.getString("social-spy.status-line", "&7- &f%module%&7: %state%"),
				configYml.getString("social-spy.state-enabled", "&aenabled"),
				configYml.getString("social-spy.state-disabled", "&cdisabled"),
				configYml.getString("social-spy.no-permission", "&cYou do not have permission to use this SocialSpy module."),
				new SocialSpyTrimSettings(configYml.getInt("social-spy.trim.command-content-chars", 160),
						configYml.getInt("social-spy.trim.message-content-chars", 160),
						configYml.getInt("social-spy.trim.sign-line-chars", 80),
						configYml.getInt("social-spy.trim.book-title-chars", 40),
						configYml.getInt("social-spy.trim.book-content-chars", 50),
						configYml.getInt("social-spy.trim.anvil-name-chars", 80),
						configYml.getString("social-spy.trim.append-ellipsis", "...")),
				readSocialSpyModuleSettings(configYml),
				configYml.getStringList("social-spy.message-command-patterns"),
				configYml.getStringList("social-spy.ignored-command-roots"));
	}

	private List<SocialSpyModuleSettings> readSocialSpyModuleSettings(final Configuration configYml) {
		final java.util.ArrayList<SocialSpyModuleSettings> result = new java.util.ArrayList<SocialSpyModuleSettings>();
		addSocialSpyModuleSettings(result, configYml, SocialSpyModuleId.MESSAGES, true);
		addSocialSpyModuleSettings(result, configYml, SocialSpyModuleId.SIGNS, true);
		addSocialSpyModuleSettings(result, configYml, SocialSpyModuleId.BOOKS, true);
		addSocialSpyModuleSettings(result, configYml, SocialSpyModuleId.ANVILS, true);
		addSocialSpyModuleSettings(result, configYml, SocialSpyModuleId.COMMANDS, false);
		return result;
	}

	private void addSocialSpyModuleSettings(final List<SocialSpyModuleSettings> result, final Configuration configYml,
			final String moduleId, final boolean defaultEnabled) {
		final String path = "social-spy.modules." + moduleId;
		result.add(new SocialSpyModuleSettings(moduleId, configYml.getBoolean(path + ".enabled", true),
				configYml.getBoolean(path + ".default-enabled", defaultEnabled),
				configYml.getString(path + ".permission", "chatsentinel.socialspy." + moduleId),
				configYml.getString(path + ".format", getSocialSpyModule().getSettings(moduleId).getFormat())));
	}
}
