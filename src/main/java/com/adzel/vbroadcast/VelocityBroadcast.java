package com.adzel.vbroadcast;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ALL")
@Plugin(id = "velocitybroadcast", name = "VelocityBroadcast", version = "1.0.5",
        description = "Broadcast messages across all servers", authors = {"Adzel"})
public class VelocityBroadcast {

    protected final ProxyServer server;
    protected final Logger logger;
    private String prefix;
    private boolean updateCheckEnabled;
    private boolean debugMessagesEnabled; // Field for debug messages
    private final Path dataDirectory;

    private static final String DEFAULT_PREFIX = "&9&l[&3&lServer&9&l]&r ";
    private static final String PLUGIN_VERSION = "1.0.5";
    private static final String VERSION_CHECK_URL = "https://api.spigotmc.org/legacy/update.php?resource=119858"; // Replace with your API URL

    private OkHttpClient client;

    @Inject
    public VelocityBroadcast(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        initializeDataDirectory();
        loadConfig();
        registerCommands();
    }

    public String getPrefix() {
        return prefix + " "; // Always return the prefix with a trailing space
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix + " "; // Store the prefix with a trailing space
        savePrefixToConfig(); // Save the new prefix to config
    }

    public boolean isUpdateCheckEnabled() {
        return updateCheckEnabled;
    }

    private void initializeDataDirectory() {
        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException e) {
            logger.error("Could not create data directory: " + dataDirectory.toAbsolutePath(), e);
        }
    }

    private void loadConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        } else {
            updateConfigVersionIfNeeded(configFile);
        }

        this.prefix = loadPrefix();
        this.updateCheckEnabled = loadUpdateCheckSetting();
        this.debugMessagesEnabled = loadDebugMessagesSetting(); // Load debug messages setting
    }

    private void registerCommands() {
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("vb").build(),
                new CombinedCommand(this) // Pass this instance to CombinedCommand
        );
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("VelocityBroadcast initialized with prefix: " + prefix);
        checkForUpdates(); // Call the update check when the plugin initializes
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (event.getPlayer() != null) {
            // Only send the update message when there's an available update
            sendUpdateMessageToPlayer(event);
        } else {
            logger.warn("Player object is null during PostLoginEvent.");
        }
    }

    private void sendUpdateMessageToPlayer(PostLoginEvent event) {
        String latestVersion = fetchLatestVersionFromAPI(); // Get the latest version

        // Only send the message if the plugin version is different from the latest version (indicating a mismatch)
        if (!PLUGIN_VERSION.equals(latestVersion)) {
            Component updateMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    String.format("&6[&eVelocityBroadcast&6] &eA new version is available:\n Version: %s (You are running: %s)", latestVersion, PLUGIN_VERSION));

            // Check if the player has a specific permission
            if (event.getPlayer().hasPermission("vb.broadcast")) {
                // Send the update message to the player
                event.getPlayer().sendMessage(updateMessage);
            }

            // Log the plain update message to the console
            logger.info(String.format("[VelocityBroadcast] Sent update message to player: %s", event.getPlayer().getUsername()));
        }
    }

    private String fetchLatestVersionFromAPI() {
        Request request = new Request.Builder()
                .url(VERSION_CHECK_URL)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string(); // Assuming the API returns just the version as plain text
            } else {
                logger.warn("Failed to fetch latest version from API. Using default version.");
                return "unknown"; // Handle the case when fetching fails
            }
        } catch (IOException e) {
            logger.error("Error while fetching the latest version: " + e.getMessage());
            return "unknown"; // Handle the case when fetching fails
        }
    }

    private void checkForUpdates() {
        if (isUpdateCheckEnabled()) {
            String latestVersion = fetchLatestVersionFromAPI();
            if (PLUGIN_VERSION.equals(latestVersion)) {
                logger.info(String.format("[VelocityBroadcast] Your version (%s) is up to date.", PLUGIN_VERSION));
            } else {
                logger.info(String.format("[VelocityBroadcast] A new version is available: %s (Current: %s)", latestVersion, PLUGIN_VERSION));
            }
        }
    }

    private String loadPrefix() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));
            return (String) config.getOrDefault("prefix", DEFAULT_PREFIX);
        } catch (IOException e) {
            logger.error("Failed to load prefix from configuration: " + e.getMessage());
            return DEFAULT_PREFIX;
        }
    }

    private void saveDefaultConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                writer.write("# DO NOT EDIT\n");
                writer.write("Plugin Version: '" + PLUGIN_VERSION + "' # Do not edit this value, as it will mess up version checking and break the plugin\n");
                writer.write("\n");
                writer.write("# ONLY EDIT BELOW THIS LINE\n");
                writer.write("debug-messages-enabled: false # This enables/disables debug messages (Default: false)\n"); // Default value for debug messages set to false
                writer.write("version-check-enabled: true # This toggles the yellow version message admins see on login. (Default: true)\n");
                writer.write("prefix: '" + DEFAULT_PREFIX + "' # The prefix of the broadcasts and server messages\n");
            } catch (IOException e) {
                logger.error("Failed to save default configuration: " + e.getMessage());
            }
        }
    }

    private void updateConfigVersionIfNeeded(File configFile) {
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));

            // Check if the version is not the latest
            if (!PLUGIN_VERSION.equals(config.get("Plugin Version"))) {
                // Prepare the new config
                Map<String, Object> newConfig = Map.of(
                        "Plugin Version", PLUGIN_VERSION,  // Update the version
                        "debug-messages-enabled", config.getOrDefault("debug-messages-enabled", false), // Retain previous value or set to default
                        "version-check-enabled", config.getOrDefault("version-check-enabled", true), // Retain previous value or set to default
                        "prefix", config.getOrDefault("prefix", DEFAULT_PREFIX) // Retain previous value or set to default
                );

                // Write new configuration back to the file
                try (Writer writer = new FileWriter(configFile)) {
                    writer.write("# DO NOT EDIT\n");
                    writer.write("Plugin Version: '" + newConfig.get("Plugin Version") + "' # Do not edit this value, as it will mess up version checking and break the plugin\n");
                    writer.write("\n");
                    writer.write("# ONLY EDIT BELOW THIS LINE\n");
                    writer.write("debug-messages-enabled: " + newConfig.get("debug-messages-enabled") + " # This enables/disables debug messages (Default: false)\n");
                    writer.write("version-check-enabled: " + newConfig.get("version-check-enabled") + " # This toggles the yellow version message admins see on login. (Default: true)\n");
                    writer.write("prefix: '" + newConfig.get("prefix") + "' # The prefix of the broadcasts and server messages\n");
                }
            }
        } catch (IOException e) {
            logger.error("Failed to update config version: " + e.getMessage());
        }
    }

    private boolean loadUpdateCheckSetting() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));
            return (boolean) config.getOrDefault("version-check-enabled", true);
        } catch (IOException e) {
            logger.error("Failed to load update check setting from configuration: " + e.getMessage());
            return true;
        }
    }

    private boolean loadDebugMessagesSetting() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));
            return (boolean) config.getOrDefault("debug-messages-enabled", false);
        } catch (IOException e) {
            logger.error("Failed to load debug messages setting from configuration: " + e.getMessage());
            return false;
        }
    }

    private void savePrefixToConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile, true))) {
            writer.write("prefix: '" + prefix + "' # The prefix of the broadcasts and server messages\n");
        } catch (IOException e) {
            logger.error("Failed to save prefix to configuration: " + e.getMessage());
        }
    }

    public class CombinedCommand implements SimpleCommand {
        private final VelocityBroadcast plugin;

        public CombinedCommand(VelocityBroadcast plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cInvalid command. Use /vb <command>."));
                return;
            }

            switch (args[0].toLowerCase()) {
                case "prefix":
                    if (args.length > 1) {
                        String newPrefix = String.join(" ", args).substring("prefix".length()).trim();
                        plugin.setPrefix(newPrefix);
                        source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aPrefix changed to: " + newPrefix));
                    } else {
                        source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUsage: /vb prefix <newPrefix>"));
                    }
                    break;
                case "reload":
                    plugin.loadConfig();
                    source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aConfiguration reloaded."));
                    break;
                case "checkupdates":
                    plugin.checkForUpdates();
                    source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aChecking for updates..."));
                    break;
                default:
                    // Check if the command is meant to broadcast a message
                    String message = String.join(" ", args);
                    broadcastMessage(message);
                    break;
            }
        }

        private void broadcastMessage(String message) {
            Component broadcastMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getPrefix() + message);
            plugin.server.getAllPlayers().forEach(player -> player.sendMessage(broadcastMessage));
        }
    }
}
