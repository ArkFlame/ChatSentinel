package dev._2lstudios.chatsentinel.velocity.utils;

import dev._2lstudios.chatsentinel.shared.config.ConfigDefaults;
import dev._2lstudios.chatsentinel.velocity.ChatSentinel;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigUtil {
	private final ChatSentinel plugin;

	public ConfigUtil(ChatSentinel plugin) {
		this.plugin = plugin;
	}

	public CommentedConfigurationNode get(String file) {
		try {
			Path dataDirectory =  plugin.getDataDirectory();
			final ConfigurationLoader<CommentedConfigurationNode> loader = YamlConfigurationLoader.builder().path(dataDirectory.resolve(file)).build();
			return loader.load();
		} catch (ConfigurateException e) {
            plugin.getLogger().error("An error occurred while trying to load {} config file.", file, e);
			return null;
		}
	}

	public void create(String file) {
		try {
			Path dataDirectory =  plugin.getDataDirectory();

			if (Files.notExists(dataDirectory)) {
				Files.createDirectory(dataDirectory);
			}

			Path configFile = dataDirectory.resolve(file);

			if (Files.notExists(configFile)) {
				InputStream inputStream = plugin.getClass().getClassLoader()
						.getResourceAsStream(file);

				if (inputStream != null) {
					Files.copy(inputStream, configFile);
					plugin.getLogger().info("File {} has been created!", configFile);
				} else {
					Files.createFile(configFile);
				}
				return;
			}

			// Only messages.yml is merged: it is the one file where new keys appear
			// between releases. Rewriting other configs would reformat hand-edited YAML.
			if ("messages.yml".equalsIgnoreCase(file)) {
				applyMissingDefaults(file, configFile);
			}
		} catch (IOException e) {
			plugin.getLogger().info("Unable to create configuration file!");
		}
	}

	private void applyMissingDefaults(final String file, final Path configFile) {
		final java.net.URL bundled = plugin.getClass().getClassLoader().getResource(file);
		if (bundled == null) {
			return;
		}
		try {
			final ConfigurationLoader<CommentedConfigurationNode> bundledLoader = YamlConfigurationLoader.builder()
					.url(bundled)
					.build();
			final ConfigurationLoader<CommentedConfigurationNode> existingLoader = YamlConfigurationLoader.builder()
					.path(configFile)
					.build();
			final CommentedConfigurationNode bundledNode = bundledLoader.load();
			final CommentedConfigurationNode existingNode = existingLoader.load();
			final Map<String, Object> defaults = ConfigDefaults.flatten(toMap(bundledNode));
			final List<String> present = new ArrayList<String>(ConfigDefaults.flatten(toMap(existingNode)).keySet());
			final List<String> missing = ConfigDefaults.missingPaths(present, defaults);
			if (missing.isEmpty()) {
				return;
			}
			for (String path : missing) {
				existingNode.node((Object[]) path.split("\\.")).set(defaults.get(path));
			}
			existingLoader.save(existingNode);
			final String summary = ConfigDefaults.describeAdded(configFile.getFileName().toString(), missing);
			if (summary != null) {
				plugin.getLogger().info(summary);
			}
		} catch (ConfigurateException exception) {
			plugin.getLogger().warn("Unable to add new keys to {}: {}", configFile.getFileName(),
					exception.getMessage());
		}
	}

	private Map<String, Object> toMap(final ConfigurationNode node) {
		final Map<String, Object> result = new LinkedHashMap<String, Object>();
		if (node == null || node.virtual()) {
			return result;
		}
		for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
			final ConfigurationNode child = entry.getValue();
			if (child.isMap()) {
				result.put(String.valueOf(entry.getKey()), toMap(child));
			} else {
				result.put(String.valueOf(entry.getKey()), child.raw());
			}
		}
		return result;
	}
}
