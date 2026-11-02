package dev._2lstudios.chatsentinel.shared.modules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Built-in English defaults for every message the plugin can send.
 *
 * <p>They are used only when a path is absent from {@code messages.yml}, so a missing or
 * outdated configuration can never produce a diagnostic placeholder such as
 * {@code <CHATSENTINEL STRING NOT FOUND>}. An explicitly empty value is still honoured,
 * which is how server owners disable an individual message.</p>
 */
public final class MessageDefaults {
    public static final String GENERAL_NO_PERMISSION = "general.no_permission";
    public static final String GENERAL_UNKNOWN_COMMAND = "general.unknown_command";
    public static final String GENERAL_RELOAD = "general.reload";
    public static final String GENERAL_CONSOLE_ONLY = "general.console_only";
    public static final String GENERAL_NOTIFY_ENABLED = "general.notify_enabled";
    public static final String GENERAL_NOTIFY_DISABLED = "general.notify_disabled";
    public static final String GENERAL_USAGE = "general.usage";
    public static final String GENERAL_UNKNOWN_MODULE = "general.unknown_module";
    public static final String GENERAL_SAVE_FAILED = "general.save_failed";
    public static final String GENERAL_MODULE_STATE = "general.module_state";
    public static final String GENERAL_MODULE_LIST = "general.module_list";
    public static final String GENERAL_STATUS_HEADER = "general.status_header";
    public static final String GENERAL_STATUS_PLATFORM = "general.status_platform";
    public static final String GENERAL_STATUS_FILTERS = "general.status_filters";
    public static final String GENERAL_FILTERS_NOT_COMPILED = "general.filters_not_compiled";
    public static final String GENERAL_FILTERS_SUMMARY = "general.filters_summary";
    public static final String GENERAL_SELFTEST_COMMAND = "general.selftest_command";
    public static final String GENERAL_SELFTEST_PLAYER = "general.selftest_player";
    public static final String GENERAL_SELFTEST_WARNING = "general.selftest_warning";
    public static final String GENERAL_REGEX_USAGE = "general.regex_usage";
    public static final String GENERAL_REGEX_ADDED = "general.regex_added";
    public static final String GENERAL_REGEX_FAILED = "general.regex_failed";
    public static final String GENERAL_NO_REASON = "general.no_reason";
    public static final String GENERAL_SOCIALSPY_CONSOLE_ONLY = "general.socialspy_console_only";
    public static final String GENERAL_SOCIALSPY_USAGE = "general.socialspy_usage";

    public static final String HELP_HEADER = "help.header";
    public static final String HELP_LINES = "help.lines";

    public static final String SPY_ENABLED = "spy.enabled";
    public static final String SPY_DISABLED = "spy.disabled";

    public static final String CHAT_CLEARED = "chat.clear";
    public static final String CHAT_CLEAR_BYPASS = "chat.clear_bypass";
    public static final String CHAT_CLEAR_SUMMARY = "chat.clear_summary";
    public static final String CHAT_SERVER_MUTED = "chat.server_muted";

    public static final String CANCEL_FALLBACK = "cancel.fallback";
    public static final String CANCEL_BLOCKED = "cancel.blocked";

    public static final String SERVER_MUTE_ENABLED = "server_mute.enabled";
    public static final String SERVER_MUTE_DISABLED = "server_mute.disabled";

    public static final String CORRECTION_ENABLED = "correction.enabled";
    public static final String CORRECTION_DISABLED = "correction.disabled";
    public static final String CORRECTION_USAGE = "correction.usage";
    public static final String CORRECTION_CONSOLE_ONLY = "correction.console_only";
    public static final String CORRECTION_NOTICE = "correction.notice";
    public static final String CORRECTION_WARN = "correction.notice";

    private static final Map<String, String> DEFAULTS;

    static {
        final Map<String, String> defaults = new HashMap<String, String>();
        defaults.put(GENERAL_NO_PERMISSION, "&cYou do not have permission to use this command!");
        defaults.put(GENERAL_UNKNOWN_COMMAND, "&cUnknown command. Use /chatsentinel help to see available commands.");
        defaults.put(GENERAL_RELOAD, "&a&lCS: &aConfiguration reloaded.");
        defaults.put(GENERAL_CONSOLE_ONLY, "&cOnly players can use this feature.");
        defaults.put(GENERAL_NOTIFY_ENABLED, "&aChatSentinel notifications are now enabled!");
        defaults.put(GENERAL_NOTIFY_DISABLED, "&cChatSentinel notifications are now disabled!");
        defaults.put(GENERAL_USAGE, "&cUsage: &f%usage%");
        defaults.put(GENERAL_UNKNOWN_MODULE, "&cUnknown module: &f%module%");
        defaults.put(GENERAL_SAVE_FAILED, "&cUnable to save the configuration. Check the console for details.");
        defaults.put(GENERAL_MODULE_STATE, "&aModule &f%module% &ais now &f%state%&a.");
        defaults.put(GENERAL_MODULE_LIST, "&aModules: &f%modules%");
        defaults.put(GENERAL_STATUS_HEADER, "&aChatSentinel status:");
        defaults.put(GENERAL_STATUS_PLATFORM, "&7Platform: &f%platform%");
        defaults.put(GENERAL_STATUS_FILTERS, "&7Filters: &f%filters%");
        defaults.put(GENERAL_FILTERS_NOT_COMPILED, "not compiled");
        defaults.put(GENERAL_FILTERS_SUMMARY, "%files% files, %expressions% expressions, %errors% errors");
        defaults.put(GENERAL_SELFTEST_COMMAND, "&a&lCS: &7Command message delivery works.");
        defaults.put(GENERAL_SELFTEST_PLAYER, "&b&lCS: &7Player message delivery works.");
        defaults.put(GENERAL_SELFTEST_WARNING, "&6&lCS: &eWarning delivery works.");
        defaults.put(GENERAL_REGEX_USAGE, "&cUsage: &f/chatsentinel regex add <module> common|raw <text>");
        defaults.put(GENERAL_REGEX_ADDED, "&aAdded regex to &f%module%&a: &f%expression%");
        defaults.put(GENERAL_REGEX_FAILED, "&cCould not add regex: &f%error%");
        defaults.put(GENERAL_NO_REASON, "No reason specified");
        defaults.put(GENERAL_SOCIALSPY_CONSOLE_ONLY, "&cOnly players can use SocialSpy.");
        defaults.put(GENERAL_SOCIALSPY_USAGE, "&cUsage: &f/socialspy [module|status]");

        defaults.put(HELP_HEADER, "&a&lChatSentinel &7commands:");
        defaults.put(HELP_LINES,
                "&e/chatsentinel help &7- &bShows this help message.\n"
                + "&e/chatsentinel reload &7- &bReloads config, messages and filters.\n"
                + "&e/chatsentinel status &7- &bShows module and filter status.\n"
                + "&e/chatsentinel module list|enable|disable|toggle <module> &7- &bManages modules live.\n"
                + "&e/chatsentinel clear [reason] &7- &bClears visible chat.\n"
                + "&e/chatsentinel notify &7- &bToggles moderation notifications.\n"
                + "&e/chatsentinel spy &7- &bToggles spy alerts.\n"
                + "&e/chatsentinel regex add <module> common|raw <text> &7- &bAdds a custom filter.\n"
                + "&e/servermute [on|off|toggle] [reason] &7- &bMutes or unmutes chat.\n"
                + "&e/socialspy [module|status] &7- &bToggles SocialSpy modules.\n"
                + "&e/autocorrect [on|off|toggle] &7- &bToggles personal correction.");

        defaults.put(SPY_ENABLED, "&aSpy alerts enabled.");
        defaults.put(SPY_DISABLED, "&cSpy alerts disabled.");

        defaults.put(CHAT_CLEARED, "&dGame chat has been cleared by &f%player%&d. Reason: &f%reason%");
        defaults.put(CHAT_CLEAR_BYPASS, "&7Chat was cleared by &f%player%&7, but you bypassed clear. Reason: &f%reason%");
        defaults.put(CHAT_CLEAR_SUMMARY, "&aChat clear sent to %cleared% players; skipped %bypassed% bypass players.");
        defaults.put(CHAT_SERVER_MUTED, "&c&lCS: &cChat is currently muted.");

        defaults.put(CANCEL_FALLBACK, "&c&lCS: &cYour message was blocked by &f%module%&c.");
        defaults.put(CANCEL_BLOCKED, "&c&lCS: &cYou cannot write that: &f%word%&c. If you continue, you will be automatically muted.");
        defaults.put("cancel.flood", "&c&lCS: &cPlease do not flood the chat.");
        defaults.put("cancel.syntax", "&c&lCS: &cSyntax is disabled on this server.");
        defaults.put("cancel.cooldown", "&c&lCS: &cYou can write your next message in &f%cooldown% &cseconds.");
        defaults.put("cancel.similarity", "&c&lCS: &cStop repeating similar messages. Similarity: &f%similarity%");
        defaults.put("cancel.capitalization", "&6&lCS: &ePlease avoid excessive capitalization.");
        defaults.put("cancel.blacklist", "&c&lCS: &cYou cannot write that: &f%word%&c.");
        defaults.put("cancel.allowed_characters", "&c&lCS: &cYour message contains characters that are not allowed.");
        defaults.put("cancel.advertisement", "&c&lCS: &cAdvertising is not allowed: &f%word%&c.");
        defaults.put("cancel.offense", "&c&lCS: &cYou cannot write that: &f%word%&c.");
        defaults.put("cancel.security", "&c&lCS: &cYour message matched a security protection rule.");
        defaults.put("cancel.custom", "&c&lCS: &cYou cannot write that: &f%word%&c.");
        defaults.put("cancel.user", "&c&lCS: &cYou cannot write that: &f%word%&c.");
        defaults.put("cancel.client", "&c&lCS: &cYou cannot write that: &f%word%&c.");
        defaults.put("cancel.filtered", "&c&lCS: &cYour message contains characters that are not allowed.");
        defaults.put("cancel.no_move_chat", "&c&lCS: &cPlease move at least &f%distance% &cblocks before typing in chat.");
        defaults.put("cancel.server_mute", "&c&lCS: &cChat is currently muted.");

        defaults.put(SERVER_MUTE_ENABLED, "&dChat has been muted by &f%player%&d. Reason: &f%reason%");
        defaults.put(SERVER_MUTE_DISABLED, "&dChat has been unmuted by &f%player%&d. Reason: &f%reason%");

        defaults.put(CORRECTION_ENABLED, "&aAuto-correction enabled.");
        defaults.put(CORRECTION_DISABLED, "&cAuto-correction disabled.");
        defaults.put(CORRECTION_USAGE, "&cUsage: &f/autocorrect [on|off|toggle]");
        defaults.put(CORRECTION_CONSOLE_ONLY, "&cOnly players can toggle auto-correction.");
        defaults.put(CORRECTION_NOTICE, "&7&lCS: &7One of your words was corrected. Use &e/autocorrect off &7to disable this.");

        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    private MessageDefaults() {
    }

    public static String get(final String path) {
        return path == null ? null : DEFAULTS.get(path);
    }

    public static Map<String, String> all() {
        return DEFAULTS;
    }
}
