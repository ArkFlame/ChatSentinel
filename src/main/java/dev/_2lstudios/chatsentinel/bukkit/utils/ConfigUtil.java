package dev._2lstudios.chatsentinel.bukkit.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import dev._2lstudios.chatsentinel.shared.config.ConfigDefaults;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ConfigUtil {
	private Plugin plugin;

	public ConfigUtil(Plugin plugin) {
		this.plugin = plugin;
	}

	public YamlConfiguration get(String filePath) {
		File dataFolder = plugin.getDataFolder();
		File file = new File(filePath.replace("%datafolder%", dataFolder.toPath().toString()));

		if (file.exists())
			return YamlConfiguration.loadConfiguration(file);
		else
			return new YamlConfiguration();
	}

	public void create(String file) {
		try {
			File dataFolder = plugin.getDataFolder();

			file = file.replace("%datafolder%", dataFolder.toPath().toString());

			File configFile = new File(file);

			if (!configFile.exists()) {
				String[] files = file.split("/");
				InputStream inputStream = plugin.getClass().getClassLoader()
						.getResourceAsStream(files[files.length - 1]);
				File parentFile = configFile.getParentFile();

				if (parentFile != null)
					parentFile.mkdirs();

				if (inputStream != null) {
					Files.copy(inputStream, configFile.toPath());
					plugin.getLogger().log(Level.INFO, ("[%pluginname%] File " + configFile + " has been created!")
							.replace("%pluginname%", plugin.getDescription().getName()));
				} else
					configFile.createNewFile();
				return;
			}

			// Only messages.yml is merged: it is the one file where new keys appear
			// between releases. Rewriting other configs would reformat hand-edited YAML.
			if (isMessagesFile(file)) {
				applyMissingDefaults(file,
						plugin.getClass().getClassLoader().getResourceAsStream(fileName(file)));
			}

		} catch (IOException e) {
			plugin.getLogger().log(Level.INFO, ("[%pluginname%] Unable to create configuration file!")
					.replace("%pluginname%", plugin.getDescription().getName()));
		}
	}

	private void applyMissingDefaults(final String filePath, final InputStream bundledStream) {
		if (bundledStream == null) {
			return;
		}
		final File target = new File(filePath);
		try {
			final YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
					new InputStreamReader(bundledStream, StandardCharsets.UTF_8));
			final List<String> bundledPaths = new ArrayList<String>();
			collectPaths(bundled, "", bundledPaths);
			final Map<String, Object> defaults = new LinkedHashMap<String, Object>();
			for (String path : bundledPaths) {
				defaults.put(path, bundled.get(path));
			}
			final YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
			final List<String> present = new ArrayList<String>();
			collectPaths(existing, "", present);
			final List<String> missing = ConfigDefaults.missingPaths(present, defaults);
			if (missing.isEmpty()) {
				return;
			}
			for (String path : missing) {
				existing.set(path, defaults.get(path));
			}
			existing.save(target);
			final String summary = ConfigDefaults.describeAdded(target.getName(), missing);
			if (summary != null) {
				plugin.getLogger().info(summary);
			}
		} catch (IOException exception) {
			plugin.getLogger().log(Level.WARNING, "Unable to add new keys to " + target.getName() + ": "
					+ exception.getMessage());
		} finally {
			try {
				bundledStream.close();
			} catch (IOException ignored) {
			}
		}
	}

	private void collectPaths(final ConfigurationSection section, final String prefix, final List<String> pathOut) {
		for (String key : section.getKeys(false)) {
			final String path = prefix.isEmpty() ? key : prefix + "." + key;
			final ConfigurationSection child = section.getConfigurationSection(key);
			if (child != null) {
				collectPaths(child, path, pathOut);
			} else {
				pathOut.add(path);
			}
		}
	}

	private boolean isMessagesFile(final String filePath) {
		return fileName(filePath).equalsIgnoreCase("messages.yml");
	}

	private String fileName(final String filePath) {
		final String[] parts = filePath.split("[/\\\\]");
		return parts[parts.length - 1];
	}
}
