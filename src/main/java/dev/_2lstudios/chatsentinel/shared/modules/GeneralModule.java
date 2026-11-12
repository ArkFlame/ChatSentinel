package dev._2lstudios.chatsentinel.shared.modules;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import dev._2lstudios.chatsentinel.shared.utils.PatternUtil;

public class GeneralModule {
	private Pattern nonAlphaNumericPattern = Pattern.compile("[^a-zA-Z0-9]");
	private volatile Pattern nicknamesPattern = PatternUtil.compileSafe(Collections.emptyList());
	private final Set<String> nicknames = Collections.synchronizedSet(new HashSet<String>());
	private Collection<String> commands;
	private boolean sanitize;
	private boolean sanitizeNames;
	private boolean filterOther;
	private String globalBypassPermission = "chatsentinel.bypass";
	private Set<String> globalBypassExcludedModules = new HashSet<>();

	public void loadData(boolean sanitize, boolean sanitizeNames, boolean filterOther,
			Collection<String> commands) {
		loadData(sanitize, sanitizeNames, filterOther, commands, "chatsentinel.bypass",
				Arrays.asList("capitalization", "correction"));
	}

	public void loadData(boolean sanitize, boolean sanitizeNames, boolean filterOther,
			Collection<String> commands, String globalBypassPermission,
			Collection<String> globalBypassExcludedModules) {
		this.sanitize = sanitize;
		this.sanitizeNames = sanitizeNames;
		this.filterOther = filterOther;
		this.commands = commands == null ? Collections.<String>emptyList() : commands;
		this.globalBypassPermission = globalBypassPermission == null || globalBypassPermission.trim().isEmpty()
				? "chatsentinel.bypass"
				: globalBypassPermission.trim();

		final Set<String> excluded = new HashSet<>();
		if (globalBypassExcludedModules != null) {
			for (String moduleId : globalBypassExcludedModules) {
				if (moduleId != null && !moduleId.trim().isEmpty()) {
					excluded.add(normalizeModuleId(moduleId));
				}
			}
		}
		this.globalBypassExcludedModules = excluded;
	}

	public boolean isSanitizeEnabled() {
		return sanitize;
	}

	/*
	 * Removes non latin words Credit:
	 * https://stackoverflow.com/users/636009/david-conrad
	 */
	public String sanitize(String message) {
		char[] out = new char[message.length()];

		message = Normalizer.normalize(message, Normalizer.Form.NFD);

		for (int j = 0, i = 0, n = message.length(); i < n; ++i) {
			char c = message.charAt(i);

			if (c <= '\u007F') {
				out[j++] = c;
			}
		}

		return new String(out).replace("(punto)", ".").replace("(dot)", ".").trim();
	}

	public boolean isSanitizeNames() {
		return sanitizeNames;
	}

	public String removeNonAlphanumeric(String text) {
		return nonAlphaNumericPattern.matcher(text).replaceAll("");
	}

	private volatile boolean needsNicknameCompile = false;

	public synchronized boolean needsNicknameCompile() {
		return needsNicknameCompile;
	}

	public synchronized void compileNicknamesPattern() {
		needsNicknameCompile = false;

		final Collection<String> nicknameSnapshot = new HashSet<String>(nicknames);
		if (nicknameSnapshot.isEmpty()) {
			nicknamesPattern = PatternUtil.compileSafe(Collections.emptyList());
			return;
		}

		Collection<String> quotedNicknames = new HashSet<String>();

		for (String nickname : nicknameSnapshot) {
			quotedNicknames.add(Pattern.quote(nickname));
		}

		nicknamesPattern = PatternUtil.compileSafe(quotedNicknames);
	}

	public Pattern getNicknamesPattern() {
		return nicknamesPattern;
	}

	public synchronized void addNickname(String nickname) {
		// Remove alphanumeric to avoid errors
		nicknames.add(removeNonAlphanumeric(nickname));

		// Compile the pattern with the nicknames
		needsNicknameCompile = true;
	}

	public synchronized void removeNickname(String nickname) {
		// Remove alphanumeric to avoid errors
		nicknames.remove(removeNonAlphanumeric(nickname));
		
		// Compile the pattern with the nicknames
		needsNicknameCompile = true;
	}

	public String sanitizeNames(String message) {
		return nicknamesPattern.matcher(message).replaceAll("");
	}

	public boolean isCommand(String message) {
		if (message == null || commands == null) {
			return false;
		}
		final String root = commandRoot(message);
		if (root.isEmpty()) {
			return false;
		}
		for (String command : commands) {
			final String configuredRoot = commandRoot(command);
			if (!configuredRoot.isEmpty() && configuredRoot.equals(root)) {
				return true;
			}
		}
		return false;
	}

	private static String commandRoot(final String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (!normalized.startsWith("/")) {
			return "";
		}
		normalized = normalized.substring(1);
		final int space = normalized.indexOf(' ');
		if (space >= 0) {
			normalized = normalized.substring(0, space);
		}
		final int colon = normalized.indexOf(':');
		if (colon >= 0 && colon + 1 < normalized.length()) {
			normalized = normalized.substring(colon + 1);
		}
		return normalized;
	}

	public boolean isFilterOther() {
		return filterOther;
	}

	public String getGlobalBypassPermission() {
		return globalBypassPermission;
	}

	public boolean isGlobalBypassExcluded(final String moduleId) {
		return globalBypassExcludedModules.contains(normalizeModuleId(moduleId));
	}

	public String normalizeModuleId(final String moduleId) {
		return moduleId == null ? "" : moduleId.trim().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}
