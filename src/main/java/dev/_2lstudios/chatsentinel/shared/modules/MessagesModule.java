package dev._2lstudios.chatsentinel.shared.modules;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev._2lstudios.chatsentinel.shared.utils.PlaceholderUtil;

/**
 * Resolves player-facing messages from {@code messages.yml}.
 *
 * <p>Messages are grouped by domain: {@code general}, {@code help}, {@code spy}, {@code chat},
 * {@code cancel}, {@code server_mute} and {@code correction}. Cancellation text for every
 * moderation module lives under {@code cancel}, keyed by module &mdash; {@code cancel.flood},
 * {@code cancel.syntax}, {@code cancel.cooldown} and so on &mdash; with {@code cancel.fallback}
 * used only when a module cancels without its own text.</p>
 *
 * <p>Lookup order for a path:</p>
 * <ol>
 *   <li>requested locale, the configured default locale, then {@code en};</li>
 *   <li>the canonical dotted path;</li>
 *   <li>flat keys written by older versions (for example {@code flood_warn_message});</li>
 *   <li>the built-in English default from {@link MessageDefaults}.</li>
 * </ol>
 *
 * <p>Because of the last step a message can never be missing. An explicitly empty value is
 * still honoured, which is how an owner disables an individual message.</p>
 */
public class MessagesModule {
    private static final String BUILTIN_LOCALE = "en";

    /** Canonical path -> legacy flat keys, in priority order. */
    private static final Map<String, String[]> LEGACY_ALIASES;

    /** Module id or display name -> suffix used under {@code cancel}. */
    private static final Map<String, String> MODULE_SUFFIXES;

    static {
        final Map<String, String[]> aliases = new HashMap<String, String[]>();
        aliases.put(MessageDefaults.GENERAL_NO_PERMISSION, new String[] { "no_permission" });
        aliases.put(MessageDefaults.GENERAL_UNKNOWN_COMMAND, new String[] { "unknown_command" });
        aliases.put(MessageDefaults.GENERAL_RELOAD, new String[] { "reload" });
        aliases.put(MessageDefaults.GENERAL_NOTIFY_ENABLED, new String[] { "notify-enabled", "notify_enabled" });
        aliases.put(MessageDefaults.GENERAL_NOTIFY_DISABLED, new String[] { "notify-disabled", "notify_disabled" });
        aliases.put(MessageDefaults.HELP_LINES, new String[] { "help" });
        aliases.put(MessageDefaults.CHAT_CLEARED, new String[] { "cleared" });
        aliases.put(MessageDefaults.CHAT_CLEAR_BYPASS, new String[] { "clear_bypass_notice" });
        aliases.put(MessageDefaults.CHAT_CLEAR_SUMMARY, new String[] { "clear_sender_summary" });
        aliases.put(MessageDefaults.CHAT_SERVER_MUTED, new String[] { "server_muted" });
        aliases.put(MessageDefaults.CANCEL_FALLBACK, new String[] { "cancellation_fallback" });
        aliases.put(MessageDefaults.CANCEL_BLOCKED, new String[] { "blocked_message" });
        aliases.put(MessageDefaults.SERVER_MUTE_ENABLED, new String[] { "server_mute_enabled" });
        aliases.put(MessageDefaults.SERVER_MUTE_DISABLED, new String[] { "server_mute_disabled" });
        aliases.put(MessageDefaults.CORRECTION_ENABLED, new String[] { "correction_enabled" });
        aliases.put(MessageDefaults.CORRECTION_DISABLED, new String[] { "correction_disabled" });
        aliases.put(MessageDefaults.CORRECTION_USAGE, new String[] { "correction_usage" });
        aliases.put(MessageDefaults.CORRECTION_CONSOLE_ONLY, new String[] { "correction_console_only" });
        aliases.put(MessageDefaults.CORRECTION_NOTICE, new String[] { "correction_warn_message" });
        aliases.put(MessageDefaults.CORRECTION_WARN, new String[] { "correction_warn_message" });
        LEGACY_ALIASES = Collections.unmodifiableMap(aliases);

        final Map<String, String> suffixes = new LinkedHashMap<String, String>();
        suffixes.put("blacklist", "blacklist");
        suffixes.put("default", "blacklist");
        suffixes.put("advertisement", "advertisement");
        suffixes.put("advertising", "advertisement");
        suffixes.put("offense", "offense");
        suffixes.put("security", "security");
        suffixes.put("user", "user");
        suffixes.put("custom", "user");
        suffixes.put("client", "client");
        suffixes.put("capitalization", "capitalization");
        suffixes.put("caps", "capitalization");
        suffixes.put("flood", "flood");
        suffixes.put("syntax", "syntax");
        suffixes.put("cooldown", "cooldown");
        suffixes.put("similarity", "similarity");
        suffixes.put("allowedcharacters", "allowed_characters");
        suffixes.put("allowed-characters", "allowed_characters");
        suffixes.put("allowed_characters", "allowed_characters");
        suffixes.put("nomovechat", "no_move_chat");
        suffixes.put("no-move-chat", "no_move_chat");
        suffixes.put("correction", "correction");
        suffixes.put("servermute", "server_mute");
        suffixes.put("server-mute", "server_mute");
        MODULE_SUFFIXES = Collections.unmodifiableMap(suffixes);
    }

    private Map<String, Map<String, String>> locales = new HashMap<String, Map<String, String>>();
    private String defaultLang = BUILTIN_LOCALE;

    public void loadData(final String defaultLang, final Map<String, Map<String, String>> messages) {
        final Map<String, Map<String, String>> loaded = new HashMap<String, Map<String, String>>();
        if (messages != null) {
            for (Map.Entry<String, Map<String, String>> entry : messages.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                final Map<String, String> values = new HashMap<String, String>();
                for (Map.Entry<String, String> value : entry.getValue().entrySet()) {
                    if (value.getKey() != null) {
                        values.put(normalizeKey(value.getKey()), value.getValue());
                    }
                }
                loaded.put(normalizeKey(entry.getKey()), values);
            }
        }
        this.locales = loaded;
        this.defaultLang = defaultLang == null || defaultLang.trim().isEmpty()
                ? BUILTIN_LOCALE
                : normalizeKey(defaultLang);
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public String getCleared(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.CHAT_CLEARED, placeholders, lang);
    }

    public String getClearBypassNotice(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.CHAT_CLEAR_BYPASS, placeholders, lang);
    }

    public String getClearSenderSummary(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.CHAT_CLEAR_SUMMARY, placeholders, lang);
    }

    public String getReload(final String lang) {
        return resolve(MessageDefaults.GENERAL_RELOAD, null, lang);
    }

    public String getNoPermission(final String lang) {
        return resolve(MessageDefaults.GENERAL_NO_PERMISSION, null, lang);
    }

    public String getUnknownCommand(final String lang) {
        return resolve(MessageDefaults.GENERAL_UNKNOWN_COMMAND, null, lang);
    }

    public String getConsoleOnly(final String lang) {
        return resolve(MessageDefaults.GENERAL_CONSOLE_ONLY, null, lang);
    }

    public String getUsage(final String usage, final String lang) {
        return resolve(MessageDefaults.GENERAL_USAGE, pair("%usage%", usage), lang);
    }

    public String getUnknownModule(final String module, final String lang) {
        return resolve(MessageDefaults.GENERAL_UNKNOWN_MODULE, pair("%module%", module), lang);
    }

    public String getSaveFailed(final String lang) {
        return resolve(MessageDefaults.GENERAL_SAVE_FAILED, null, lang);
    }

    public String getModuleState(final String module, final boolean enabled, final String lang) {
        return resolve(MessageDefaults.GENERAL_MODULE_STATE,
                new String[][] { { "%module%", "%state%" }, { module, enabled ? "enabled" : "disabled" } }, lang);
    }

    public String getModuleList(final String modules, final String lang) {
        return resolve(MessageDefaults.GENERAL_MODULE_LIST, pair("%modules%", modules), lang);
    }

    public String getStatusHeader(final String lang) {
        return resolve(MessageDefaults.GENERAL_STATUS_HEADER, null, lang);
    }

    public String getStatusPlatform(final String platform, final String lang) {
        return resolve(MessageDefaults.GENERAL_STATUS_PLATFORM, pair("%platform%", platform), lang);
    }

    public String getStatusFilters(final String filters, final String lang) {
        return resolve(MessageDefaults.GENERAL_STATUS_FILTERS, pair("%filters%", filters), lang);
    }

    public String getFiltersNotCompiled(final String lang) {
        return resolve(MessageDefaults.GENERAL_FILTERS_NOT_COMPILED, null, lang);
    }

    public String getFiltersSummary(final String files, final String expressions, final String errors,
            final String lang) {
        return resolve(MessageDefaults.GENERAL_FILTERS_SUMMARY, new String[][] {
                { "%files%", "%expressions%", "%errors%" },
                { files, expressions, errors }
        }, lang);
    }

    public String getSelfTestCommand(final String lang) {
        return resolve(MessageDefaults.GENERAL_SELFTEST_COMMAND, null, lang);
    }

    public String getSelfTestPlayer(final String lang) {
        return resolve(MessageDefaults.GENERAL_SELFTEST_PLAYER, null, lang);
    }

    public String getSelfTestWarning(final String lang) {
        return resolve(MessageDefaults.GENERAL_SELFTEST_WARNING, null, lang);
    }

    public String getRegexUsage(final String lang) {
        return resolve(MessageDefaults.GENERAL_REGEX_USAGE, null, lang);
    }

    public String getRegexAdded(final String module, final String expression, final String lang) {
        return resolve(MessageDefaults.GENERAL_REGEX_ADDED,
                new String[][] { { "%module%", "%expression%" }, { module, expression } }, lang);
    }

    public String getRegexFailed(final String error, final String lang) {
        return resolve(MessageDefaults.GENERAL_REGEX_FAILED, pair("%error%", error), lang);
    }

    public String getNoReason(final String lang) {
        return resolve(MessageDefaults.GENERAL_NO_REASON, null, lang);
    }

    public String getSocialSpyConsoleOnly(final String lang) {
        return resolve(MessageDefaults.GENERAL_SOCIALSPY_CONSOLE_ONLY, null, lang);
    }

    public String getSocialSpyUsage(final String lang) {
        return resolve(MessageDefaults.GENERAL_SOCIALSPY_USAGE, null, lang);
    }

    public String getHelpHeader(final String lang) {
        return resolve(MessageDefaults.HELP_HEADER, null, lang);
    }

    public String getHelpLines(final String lang) {
        return resolve(MessageDefaults.HELP_LINES, null, lang);
    }

    public String getNotifyEnabled(final String lang) {
        return resolve(MessageDefaults.GENERAL_NOTIFY_ENABLED, null, lang);
    }

    public String getNotifyDisabled(final String lang) {
        return resolve(MessageDefaults.GENERAL_NOTIFY_DISABLED, null, lang);
    }

    public String getSpyEnabled(final String lang) {
        return resolve(MessageDefaults.SPY_ENABLED, null, lang);
    }

    public String getSpyDisabled(final String lang) {
        return resolve(MessageDefaults.SPY_DISABLED, null, lang);
    }

    public String getServerMuted(final String lang) {
        return resolve(MessageDefaults.CHAT_SERVER_MUTED, null, lang);
    }

    public String getCorrectionConsoleOnly(final String lang) {
        return resolve(MessageDefaults.CORRECTION_CONSOLE_ONLY, null, lang);
    }

    public String getServerMuteEnabled(final String lang) {
        return getServerMuteEnabled(empty("%player%", "%reason%"), lang);
    }

    public String getServerMuteEnabled(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.SERVER_MUTE_ENABLED, placeholders, lang);
    }

    public String getServerMuteDisabled(final String lang) {
        return getServerMuteDisabled(empty("%player%", "%reason%"), lang);
    }

    public String getServerMuteDisabled(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.SERVER_MUTE_DISABLED, placeholders, lang);
    }

    public String getCorrectionEnabled(final String lang) {
        return resolve(MessageDefaults.CORRECTION_ENABLED, null, lang);
    }

    public String getCorrectionDisabled(final String lang) {
        return resolve(MessageDefaults.CORRECTION_DISABLED, null, lang);
    }

    public String getCorrectionUsage(final String lang) {
        return resolve(MessageDefaults.CORRECTION_USAGE, null, lang);
    }

    public String getCorrectionWarnMessage(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.CORRECTION_WARN, placeholders, lang);
    }

    public String getNoMoveChatWarnMessage(final String lang) {
        return getWarnMessage(empty("%distance%"), lang, "no-move-chat");
    }

    public String getNoMoveChatWarnMessage(final String[][] placeholders, final String lang) {
        return getWarnMessage(placeholders, lang, "no-move-chat");
    }

    public String getCooldownWarnMessage(final String[][] placeholders, final String lang) {
        return getWarnMessage(placeholders, lang, "cooldown");
    }

    public String getSimilarityWarnMessage(final String[][] placeholders, final String lang) {
        return getWarnMessage(placeholders, lang, "similarity");
    }

    public String getFiltered(final String lang) {
        return getWarnMessage(null, lang, "filtered");
    }

    /** Shared text for a module that cancelled a message without supplying its own. */
    public String getCancellationFallback(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.CANCEL_FALLBACK, placeholders, lang);
    }

    /** Word-oriented cancellation text, kept for blacklist compatibility. */
    public String getBlockedMessage(final String[][] placeholders, final String lang) {
        return resolve(MessageDefaults.CANCEL_BLOCKED, placeholders, lang);
    }

    /**
     * Feedback for a cancelled message.
     *
     * <p>Priority: the module's own player message, then its configured
     * {@code cancel.<module>} entry, then {@code cancel.fallback}. This is what makes the
     * fallback reachable for modules that cancel without their own text.</p>
     */
    public String getRequiredCancellationMessage(final String preferredMessage, final String[][] placeholders,
            final String lang, final String module) {
        if (hasText(preferredMessage)) {
            return PlaceholderUtil.replacePlaceholders(preferredMessage, placeholders);
        }
        final String configured = getWarnMessage(placeholders, lang, module);
        if (hasText(configured)) {
            return configured;
        }
        return getCancellationFallback(placeholders, lang);
    }

    /** Cancellation text for a module, resolved from {@code cancel.<module>} or its legacy key. */
    public String getWarnMessage(final String[][] placeholders, final String lang, final String module) {
        return resolve(warnMessagePath(module), placeholders, lang, legacyWarnKeys(module));
    }

    /** True when the owner explicitly configured cancellation text for this module. */
    public boolean hasWarnMessage(final String lang, final String module) {
        return isConfigured(warnMessagePath(module), lang, legacyWarnKeys(module));
    }

    /** Canonical path for a module id or display name: {@code cancel.<module>}. */
    public String warnMessagePath(final String module) {
        return "cancel." + moduleSuffix(module);
    }

    private String moduleSuffix(final String module) {
        final String key = normalizeKey(module);
        final String mapped = MODULE_SUFFIXES.get(key);
        if (mapped != null) {
            return mapped;
        }
        return key.replace('-', '_').replace(' ', '_');
    }

    /** Flat keys older versions used for a module's cancellation text. */
    private List<String> legacyWarnKeys(final String module) {
        final String suffix = moduleSuffix(module);
        final Set<String> keys = new LinkedHashSet<String>();
        keys.add(suffix + "_warn_message");
        if ("allowed_characters".equals(suffix)) {
            keys.add("filtered");
        }
        if ("capitalization".equals(suffix)) {
            keys.add("caps_warn_message");
        }
        if ("blacklist".equals(suffix)) {
            keys.add("blocked_message");
        }
        if ("advertisement".equals(suffix)) {
            keys.add("advertising_warn_message");
        }
        if ("similarity".equals(suffix)) {
            keys.add("similarity_warn_message");
        }
        return Arrays.asList(keys.toArray(new String[0]));
    }

    private String resolve(final String path, final String[][] placeholders, final String lang) {
        return resolve(path, placeholders, lang, null);
    }

    private String resolve(final String path, final String[][] placeholders, final String lang,
            final List<String> extraAliases) {
        for (String locale : localeChain(lang)) {
            final String canonical = lookup(locale, path);
            if (canonical != null) {
                return PlaceholderUtil.replacePlaceholders(canonical, placeholders);
            }
        }
        for (String alias : aliasesFor(path, extraAliases)) {
            for (String locale : localeChain(lang)) {
                final String legacy = lookup(locale, alias);
                if (legacy != null) {
                    return PlaceholderUtil.replacePlaceholders(legacy, placeholders);
                }
            }
        }
        return PlaceholderUtil.replacePlaceholders(MessageDefaults.get(path), placeholders);
    }

    private boolean isConfigured(final String path, final String lang, final List<String> extraAliases) {
        for (String locale : localeChain(lang)) {
            if (lookup(locale, path) != null) {
                return true;
            }
        }
        for (String alias : aliasesFor(path, extraAliases)) {
            for (String locale : localeChain(lang)) {
                if (lookup(locale, alias) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> aliasesFor(final String path, final List<String> extraAliases) {
        final Set<String> aliases = new LinkedHashSet<String>();
        final String[] statics = LEGACY_ALIASES.get(path);
        if (statics != null) {
            aliases.addAll(Arrays.asList(statics));
        }
        if (extraAliases != null) {
            aliases.addAll(extraAliases);
        }
        return Arrays.asList(aliases.toArray(new String[0]));
    }

    private String lookup(final String locale, final String key) {
        final Map<String, String> values = locales.get(locale);
        return values == null ? null : values.get(normalizeKey(key));
    }

    private Iterable<String> localeChain(final String lang) {
        final String requested = lang == null || lang.trim().isEmpty() ? defaultLang : normalizeKey(lang);
        if (requested.equals(BUILTIN_LOCALE)) {
            return Collections.singletonList(BUILTIN_LOCALE);
        }
        if (requested.equals(defaultLang)) {
            return Arrays.asList(defaultLang, BUILTIN_LOCALE);
        }
        return Arrays.asList(requested, defaultLang, BUILTIN_LOCALE);
    }

    private static String[][] pair(final String key, final String value) {
        return new String[][] { { key }, { value } };
    }

    private static String[][] empty(final String... keys) {
        return new String[][] { keys, new String[keys.length] };
    }

    private static String normalizeKey(final String key) {
        return key == null ? null : key.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }
}
