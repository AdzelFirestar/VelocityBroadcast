package com.adzel.vbroadcast;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files; // Added import for file operations
import java.nio.file.Path; // Ensure this is included
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Plugin(id = "velocitybroadcast", name = "VelocityBroadcast", version = "0.9 Pre-Release",
        description = "Broadcast messages across all servers", authors = {"adzel"})
public class VelocityBroadcast {

    private final ProxyServer server;
    private final Logger logger;
    private String prefix;
    private final Path dataDirectory; // Changed to Path

    // Define DEFAULT_PREFIX
    private static final String DEFAULT_PREFIX = "&9&l[&3&lServer&9&l]&r"; // Change this to your desired default prefix

    @Inject
    public VelocityBroadcast(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        // Ensure the data directory exists
        if (Files.notExists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory); // Creates the directory if it doesn't exist
            } catch (IOException e) {
                logger.error("Could not create data directory: " + dataDirectory.toAbsolutePath(), e);
            }
        }

        // Load prefix from config or set default
        this.prefix = loadPrefix();

        // Register commands
        server.getCommandManager().register(server.getCommandManager().metaBuilder("vbroadcast").aliases("vb").build(), new BroadcastCommand());
        server.getCommandManager().register(server.getCommandManager().metaBuilder("vbroadcastprefix").aliases("vbprefix", "vb p").build(), new PrefixCommand());
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("VelocityBroadcast initialized with prefix: " + prefix);
    }

    // Load prefix from YAML configuration file
    private String loadPrefix() {
        File configFile = new File(dataDirectory.toFile(), "config.yml"); // Convert Path to File
        if (!configFile.exists()) {
            // If the file doesn't exist, create it with the default prefix
            savePrefix(DEFAULT_PREFIX);
            return DEFAULT_PREFIX;
        }

        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));
            return (String) config.getOrDefault("prefix", DEFAULT_PREFIX);
        } catch (IOException e) {
            logger.error("Failed to load configuration: " + e.getMessage());
            return DEFAULT_PREFIX;
        }
    }

    // Save prefix to YAML configuration file
    private void savePrefix(String newPrefix) {
        Map<String, Object> config = new HashMap<>();
        config.put("prefix", newPrefix);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(new File(dataDirectory.toFile(), "config.yml"))) { // Convert Path to File
            yaml.dump(config, writer);
        } catch (IOException e) {
            logger.error("Failed to save configuration: " + e.getMessage());
        }
    }

    // Command for broadcasting
    public class BroadcastCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!source.hasPermission("vb.broadcast")) {
                source.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
                return;
            }

            if (args.length == 0) {
                source.sendMessage(Component.text("Usage: /vb [message]").color(NamedTextColor.YELLOW));
                return;
            }

            // Join the message arguments and convert legacy color codes
            String message = String.join(" ", args);
            Component broadcastMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + " " + message);

            // Send the formatted message to all players
            server.getAllPlayers().forEach(player -> player.sendMessage(broadcastMessage));

            logger.info("Broadcast message: {}", message);
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return List.of(); // No tab-completion needed for this command
        }
    }

    // Command for changing the broadcast prefix
    public class PrefixCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!source.hasPermission("vbroadcast.admin")) {
                source.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
                return;
            }

            if (args.length == 0) {
                source.sendMessage(Component.text("Usage: /vb prefix {newPrefix}").color(NamedTextColor.YELLOW));
                return;
            }

            // Join the prefix arguments and update the prefix
            String newPrefix = String.join(" ", args);
            prefix = newPrefix;

            // Save the new prefix to the config
            savePrefix(newPrefix);

            // Notify the user
            source.sendMessage(Component.text("Broadcast prefix changed to: " + prefix).color(NamedTextColor.GREEN));
            logger.info("Broadcast prefix changed to: " + prefix);
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return List.of(); // No tab-completion needed for this command
        }
    }
}