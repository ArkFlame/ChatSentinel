package dev._2lstudios.chatsentinel.bukkit.modules;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev._2lstudios.chatsentinel.bukkit.ChatSentinel;
import dev._2lstudios.chatsentinel.bukkit.filter.BukkitFilterFileLoader;
import dev._2lstudios.chatsentinel.bukkit.utils.ConfigUtil;
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

public class BukkitModuleManager extends ModuleManager {
	private ConfigUtil configUtil;
	private FilterCompileReport lastBlacklistCompileReport;
	private FilterCompileReport lastWhitelistCompileReport;

	public BukkitModuleManager(ConfigUtil configUtil) {
		super();
		this.configUtil = configUtil;
		reloadData();
	}

	@Override
	public void reloadData(FilterCompileStatus status) {
		configUtil.create("%datafolder%/config.yml");
		configUtil.create("%datafolder%/messages.yml");

		Configuration configYml = configUtil.get("%datafolder%/config.yml");
		Configuration messagesYml = configUtil.get("%datafolder%/messages.yml");
		BukkitFilterFileLoader filterFileLoader = new BukkitFilterFileLoader();
		File dataFolder = ChatSentinel.getInstance().getDataFolder();
		DefaultFilterInstaller.installIfNeeded(dataFolder, BukkitModuleManager.class.getClassLoader(), new java.util.function.Consumer<String>() {
			@Override
			public void accept(final String message) {
				ChatSentinel.getInstance().getLogger().info(message);
			}
		});
		List<FilterExpressionFile> blacklistFiles = filterFileLoader.load(FilterKind.BLACKLIST, dataFolder, configUtil);
		List<FilterExpressionFile> whitelistFiles = filterFileLoader.load(FilterKind.WHITELIST, dataFolder, configUtil);
		Configuration whitelistYml = configUtil.get("%datafolder%/whitelist.yml");
		FilterCompiler compiler = new FilterCompiler();
		if (status != null) {
			status.start(blacklistFiles.size() + whitelistFiles.size());
		}
		FilterCompiler.FilterCompilation blacklistCompilation = compiler.compile(FilterKind.BLACKLIST, blacklistFiles, status);
		FilterCompiler.FilterCompilation whitelistCompilation = compiler.compile(FilterKind.WHITELIST, whitelistFiles, status);
		FilterCompiler.FilterCompilation activeWhitelistCompilation = configYml.getBoolean("whitelist.enabled")
				? whitelistCompilation
				: compiler.compile(FilterKind.WHITELIST, Collections.<FilterExpressionFile>emptyList());
		lastBlacklistCompileReport = blacklistCompilation.getReport();
		lastWhitelistCompileReport = whitelistCompilation.getReport();
		FilterCompileReport compileReport = FilterCompileReport.combine(lastBlacklistCompileReport, lastWhitelistCompileReport);
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
		FilterModuleSettingsRegistry blacklistSettingsRegistry = buildBlacklistSettingsRegistry(configYml);
		Map<String, Map<String, String>> locales = new HashMap<>();

		for (String lang : messagesYml.getConfigurationSection("langs").getKeys(false)) {
			ConfigurationSection langSection = messagesYml.getConfigurationSection("langs." + lang);
			Map<String, String> messages = new HashMap<>();

			for (String key : langSection.getKeys(false)) {
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
				() -> ChatSentinel.getInstance().getChatPlatform().getOnlinePlayerNamesSnapshot(),
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
				readStringMap(configYml.getConfigurationSection("correction.replacements")),
				configYml.getStringList("correction.ignored-words"),
				() -> ChatSentinel.getInstance().getChatPlatform().getOnlinePlayerNamesSnapshot());
		getWhitelistModule().loadData(configYml.getBoolean("whitelist.enabled"),
				whitelistYml.getStringList("expressions").toArray(new String[0]));
		getBlacklistModule().loadData(blacklistCompilation.getRegistry(), activeWhitelistCompilation.getRegistry(), blacklistSettingsRegistry);
		getSyntaxModule().loadData(configYml.getBoolean("syntax.enabled"), configYml.getString("syntax.custom-module-name"), configYml.getInt("syntax.warn.max"),
				configYml.getString("syntax.warn.notification"),
				configYml.getBoolean("syntax.warn.webhook-notification"),
				configYml.getStringList("syntax.whitelist").toArray(new String[0]),
				readPunishments(configYml, "syntax"));
		getDiscordWebhookModule().loadData(
				configYml.getBoolean("discord-webhook.enabled"),
				configYml.getString("discord-webhook.webhook-url"),
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
				configYml.getString("discord-webhook.thumbnail-url")
		);
	}

	public FilterCompileReport getLastBlacklistCompileReport() {
		return lastBlacklistCompileReport;
	}

	public FilterCompileReport getLastWhitelistCompileReport() {
		return lastWhitelistCompileReport;
	}

	public void setServerMuteMuted(boolean muted) throws IOException {
		YamlConfiguration configYml = configUtil.get("%datafolder%/config.yml");
		configYml.set("server-mute.muted", muted);
		configYml.save(new File(ChatSentinel.getInstance().getDataFolder(), "config.yml"));
		getServerMuteModule().setMuted(muted);
	}

	private FilterModuleSettingsRegistry buildBlacklistSettingsRegistry(Configuration configYml) {
		FilterModuleSettings fallback = FilterModuleSettings.defaultBlacklist("default",
				configYml.getBoolean("blacklist.enabled"),
				configYml.getString("blacklist.custom-module-name"),
				configYml.getBoolean("blacklist.fake_message"),
				configYml.getBoolean("blacklist.censorship.enabled", false),
				configYml.getString("blacklist.censorship.replacement", "***"),
				configYml.getInt("blacklist.warn.max"),
				configYml.getString("blacklist.warn.notification"),
				configYml.getBoolean("blacklist.warn.webhook-notification"),
				readPunishments(configYml, "blacklist"),
				configYml.getBoolean("blacklist.block_raw_message"));

		Map<String, FilterModuleSettings> settingsByModuleId = new HashMap<String, FilterModuleSettings>();
		settingsByModuleId.put(fallback.getModuleId(), fallback);
		ConfigurationSection modulesSection = configYml.getConfigurationSection("blacklist.modules");
		if (modulesSection != null) {
			for (String moduleId : modulesSection.getKeys(false)) {
				ConfigurationSection moduleSection = modulesSection.getConfigurationSection(moduleId);
				if (moduleSection != null) {
					settingsByModuleId.put(moduleId, buildModuleSettings(moduleId, moduleSection, fallback));
				}
			}
		}
		return new FilterModuleSettingsRegistry(settingsByModuleId, fallback);
	}

	private FilterModuleSettings buildModuleSettings(String moduleId, ConfigurationSection section, FilterModuleSettings fallback) {
		String customName = section.getString("custom-module-name", fallback.getCustomName());
		String warnNotification = section.getString("warn.notification", fallback.getWarnNotification());
		String censorshipReplacement = section.getString("censorship.replacement", fallback.getCensorshipReplacement());
		String[] commands = readModulePunishments(section, fallback.getCommands());
		return FilterModuleSettings.defaultBlacklist(moduleId,
				section.getBoolean("enabled", fallback.isEnabled()),
				customName,
				section.getBoolean("fake_message", fallback.isFakeMessage()),
				section.getBoolean("censorship.enabled", fallback.isCensorshipEnabled()),
				censorshipReplacement,
				section.getInt("warn.max", fallback.getMaxWarns()),
				warnNotification,
				section.getBoolean("warn.webhook-notification", fallback.isWebhookEnabled()),
				commands,
				section.getBoolean("block_raw_message", fallback.isBlockRawMessage()));
	}

	private Map<String, String> readStringMap(final ConfigurationSection section) {
		final Map<String, String> result = new HashMap<String, String>();
		if (section == null) {
			return result;
		}
		for (String key : section.getKeys(false)) {
			final String value = section.getString(key);
			if (value != null) {
				result.put(key, value);
			}
		}
		return result;
	}

	private String[] readPunishments(final ConfigurationSection section, final String basePath) {
		final List<String> values = new ArrayList<String>();
		appendStringList(values, section, basePath + ".punishments");
		appendStringList(values, section, basePath + ".punishment.commands");
		appendString(values, section.getString(basePath + ".punishment.command", null));
		appendStringOrList(values, section, basePath + ".punishment");
		return values.toArray(new String[0]);
	}

	private String[] readPunishments(final ConfigurationSection section, final String basePath, final String[] fallback) {
		final String[] values = readPunishments(section, basePath);
		return values.length == 0 ? fallback.clone() : values;
	}

	private String[] readModulePunishments(final ConfigurationSection section, final String[] fallback) {
		final List<String> values = new ArrayList<String>();
		appendStringList(values, section, "punishments");
		appendStringList(values, section, "punishment.commands");
		appendString(values, section.getString("punishment.command", null));
		appendStringOrList(values, section, "punishment");
		return values.isEmpty() ? fallback.clone() : values.toArray(new String[0]);
	}

	private void appendStringOrList(final List<String> values, final ConfigurationSection section, final String path) {
		if (section.isList(path)) {
			appendStringList(values, section, path);
		} else if (section.isString(path)) {
			appendString(values, section.getString(path, null));
		}
	}

	private void appendStringList(final List<String> values, final ConfigurationSection section, final String path) {
		if (section == null) {
			return;
		}
		for (final String value : section.getStringList(path)) {
			appendString(values, value);
		}
	}

	private void appendString(final List<String> values, final String value) {
		if (value != null && !value.trim().isEmpty()) {
			values.add(value);
		}
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
				new SocialSpyTrimSettings(
						configYml.getInt("social-spy.trim.command-content-chars", 160),
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
		result.add(new SocialSpyModuleSettings(moduleId,
				configYml.getBoolean(path + ".enabled", true),
				configYml.getBoolean(path + ".default-enabled", defaultEnabled),
				configYml.getString(path + ".permission", "chatsentinel.socialspy." + moduleId),
				configYml.getString(path + ".format", getSocialSpyModule().getSettings(moduleId).getFormat())));
	}

}
