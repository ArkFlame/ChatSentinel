package dev._2lstudios.chatsentinel.shared.modules;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev._2lstudios.chatsentinel.shared.utils.PlaceholderUtil;

public class MessagesModule {
	private Map<String, Map<String, String>> locales;
	private String defaultLang = "en";

	public void loadData(String defaultLang, Map<String, Map<String, String>> messages) {
		this.locales = messages;
		this.defaultLang = defaultLang;
	}

	private String getString(String lang, String path) {
		final LookupResult result = lookupString(lang, path);
		if (result.present) {
			return result.value;
		}
		return "<CHATSENTINEL STRING NOT FOUND: " + path + ">";
	}

    private boolean hasString(String lang, String path) {
        return lookupString(lang, path).present;
    }

    private LookupResult lookupString(String lang, String path) {
        LookupResult result = lookupLocale(lang, path);
        if (result.present) {
            return result;
        }
        result = lookupLocale(defaultLang, path);
        if (result.present) {
            return result;
        }
        return lookupLocale("en", path);
    }

    private LookupResult lookupLocale(String lang, String path) {
        if (locales == null || lang == null || path == null) {
            return LookupResult.missing();
        }
        final Map<String, String> selected = locales.get(lang);
        if (selected != null && selected.containsKey(path)) {
            return LookupResult.present(selected.get(path));
        }
        return LookupResult.missing();
    }

	public String getCleared(String[][] placeholders, String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "cleared"), placeholders);
	}

	public String getReload(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "reload"));
	}

	public String getHelp(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "help"));
	}

	public String getUnknownCommand(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "unknown_command"));
	}

	public String getNoPermission(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "no_permission"));
	}

	public String getWarnMessage(String[][] placeholders, String lang, String module) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, warnMessagePath(lang, module)), placeholders);
	}

	public boolean hasWarnMessage(String lang, String module) {
		return hasString(lang, warnMessagePath(lang, module));
	}

	private String warnMessagePath(String lang, String module) {
		final String moduleLowerCase = module == null
				? ""
				: module.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		if ("capitalization".equals(moduleLowerCase)) {
			if (hasString(lang, "capitalization_warn_message")) {
				return "capitalization_warn_message";
			}
			if (hasString(lang, "caps_warn_message")) {
				return "caps_warn_message";
			}
			return "capitalization_warn_message";
		}
		if ("advertisement".equals(moduleLowerCase) || "advertising".equals(moduleLowerCase)) {
			return "advertising_warn_message";
		}
		if ("user".equals(moduleLowerCase) || "custom".equals(moduleLowerCase)) {
			return "custom_warn_message";
		}
		if ("default".equals(moduleLowerCase) || "blacklist".equals(moduleLowerCase)) {
			return "blacklist_warn_message";
		}
		if ("client".equals(moduleLowerCase)
				&& !hasString(lang, "client_warn_message")
				&& hasString(lang, "blacklist_warn_message")) {
			return "blacklist_warn_message";
		}
		return moduleLowerCase + "_warn_message";
	}

    public String getBlockedMessage(final String[][] placeholders, final String lang) {
        return PlaceholderUtil.replacePlaceholders(getString(lang, "blocked_message"), placeholders);
    }

	public String getFiltered(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "filtered"));
	}

    public String getNotifyEnabled(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "notify-enabled"));
    }

    public String getNotifyDisabled(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "notify-disabled"));
    }

	public String getServerMuted(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "server_muted"));
	}

	public String getNoMoveChatWarnMessage(String lang) {
		return getNoMoveChatWarnMessage(new String[][] { { "%distance%" }, { "" } }, lang);
	}

    public String getNoMoveChatWarnMessage(String[][] placeholders, String lang) {
        return PlaceholderUtil.replacePlaceholders(getString(lang, "no_move_chat_warn_message"), placeholders);
    }

	public String getServerMuteEnabled(String lang) {
		return getServerMuteEnabled(new String[][] { { "%reason%", "%player%" }, { "", "" } }, lang);
	}

    public String getServerMuteEnabled(String[][] placeholders, String lang) {
        return PlaceholderUtil.replacePlaceholders(getString(lang, "server_mute_enabled"), placeholders);
    }

	public String getServerMuteDisabled(String lang) {
		return getServerMuteDisabled(new String[][] { { "%reason%", "%player%" }, { "", "" } }, lang);
	}

    public String getServerMuteDisabled(String[][] placeholders, String lang) {
        return PlaceholderUtil.replacePlaceholders(getString(lang, "server_mute_disabled"), placeholders);
    }

    public String getClearBypassNotice(String[][] placeholders, String lang) {
        return PlaceholderUtil.replacePlaceholders(getString(lang, "clear_bypass_notice"), placeholders);
    }

    public String getClearSenderSummary(String[][] placeholders, String lang) {
        return PlaceholderUtil.replacePlaceholders(getString(lang, "clear_sender_summary"), placeholders);
    }

    public String getCorrectionWarnMessage(String[][] placeholders, String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "correction_warn_message"), placeholders);
	}

	public String getCorrectionEnabled(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "correction_enabled"));
	}

	public String getCorrectionDisabled(String lang) {
		return getCorrectionDisabled(new String[][] { { "%corrections%", "%original_message%", "%corrected_message%" }, { "", "", "" } }, lang);
	}

	public String getCorrectionDisabled(String[][] placeholders, String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "correction_disabled"), placeholders);
	}

	public String getCorrectionUsage(String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "correction_usage"));
	}

	public String getCorrectionConsoleOnly(String lang) {
		return getCorrectionConsoleOnly(new String[][] { { "%reason%", "%player%" }, { "", "" } }, lang);
	}

	public String getCorrectionConsoleOnly(String[][] placeholders, String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "correction_console_only"), placeholders);
	}

	public String getCooldownWarnMessage(final String[][] placeholders, final String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "cooldown_warn_message"), placeholders);
	}

	public String getSimilarityWarnMessage(final String[][] placeholders, final String lang) {
		return PlaceholderUtil.replacePlaceholders(getString(lang, "similarity_warn_message"), placeholders);
	}

	private static final class LookupResult {
		private final boolean present;
		private final String value;

		private LookupResult(boolean present, String value) {
			this.present = present;
			this.value = value == null ? "" : value;
		}

		private static LookupResult present(String value) {
			return new LookupResult(true, value);
		}

		private static LookupResult missing() {
			return new LookupResult(false, "");
		}
	}
}