package dev._2lstudios.chatsentinel.bungee.utils;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

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

	public Configuration get(String file) {
		File dataFolder = plugin.getDataFolder();

		file = file.replace("%datafolder%", dataFolder.toPath().toString());

		try {
			return ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(file));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
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
				applyMissingDefaults(configFile, plugin.getClass().getClassLoader()
						.getResourceAsStream(fileName(file)));
			}
		} catch (IOException e) {
			plugin.getLogger().log(Level.INFO, ("[%pluginname%] Unable to create configuration file!")
					.replace("%pluginname%", plugin.getDescription().getName()));
		}
	}

	private void applyMissingDefaults(final File target, final InputStream bundledStream) {
		if (bundledStream == null) {
			return;
		}
		final ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
		try {
			final Configuration bundled = provider.load(new InputStreamReader(bundledStream, StandardCharsets.UTF_8));
			final Map<String, Object> defaults = ConfigDefaults.flatten(toMap(bundled));
			final Configuration existing = provider.load(target);
			final List<String> present = new ArrayList<String>(ConfigDefaults.flatten(toMap(existing)).keySet());
			final List<String> missing = ConfigDefaults.missingPaths(present, defaults);
			if (missing.isEmpty()) {
				return;
			}
			for (String path : missing) {
				existing.set(path, defaults.get(path));
			}
			provider.save(existing, target);
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

	private Map<String, Object> toMap(final Configuration configuration) {
		final Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (configuration == null) {
			return result;
		}
		for (String key : configuration.getKeys()) {
			// getSection throws when the value is a scalar, so inspect the raw value first.
			final Object raw = configuration.get(key);
			result.put(key, raw instanceof Configuration ? toMap((Configuration) raw) : raw);
		}
		return result;
	}

	private boolean isMessagesFile(final String filePath) {
		return fileName(filePath).equalsIgnoreCase("messages.yml");
	}

	private String fileName(final String filePath) {
		final String[] parts = filePath.split("[/\\\\]");
		return parts[parts.length - 1];
	}
}
