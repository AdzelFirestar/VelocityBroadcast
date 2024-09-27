package com.adzel.vbroadcast;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.List;

@Plugin(id = "velocitybroadcast", name = "VelocityBroadcast", version = "1.0-SNAPSHOT",
        description = "Broadcast messages across all servers", authors = {"adzel"})
public class VelocityBroadcast {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public VelocityBroadcast(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        // Register commands
        server.getCommandManager().register(server.getCommandManager().metaBuilder("vbroadcast").aliases("vb").build(), new BroadcastCommand());
        server.getCommandManager().register(server.getCommandManager().metaBuilder("vbroadcasthelp").aliases("vbhelp").build(), new HelpCommand());
    }

    // Command for broadcasting
    public class BroadcastCommand implements SimpleCommand {

        // Executes the broadcast command
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!source.hasPermission("vb.broadcast")) {
                source.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
                return;
            }

            if (args.length == 0) {
                source.sendMessage(Component.text("Usage: /vbroadcast {message}").color(NamedTextColor.YELLOW));
                return;
            }

            // Join the message arguments and convert legacy color codes
            String message = String.join(" ", args);
            Component broadcastMessage = LegacyComponentSerializer.legacyAmpersand().deserialize("&b[Broadcast] &f" + message);

            // Send the formatted message to all players
            server.getAllPlayers().forEach(player -> player.sendMessage(broadcastMessage));

            logger.info("Broadcast message: {}", message);
        }

        // Provides tab-completion (suggestions) for this command
        public List<String> suggest(Invocation invocation) {
            return List.of(); // No tab-completion needed for this command
        }
    }

    // Command for help
    public class HelpCommand implements SimpleCommand {

        // Executes the help command
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            source.sendMessage(Component.text("VelocityBroadcast Plugin Help").color(NamedTextColor.GOLD));
            source.sendMessage(Component.text("/vbroadcast {message} - Sends a global message to all servers on the network").color(NamedTextColor.YELLOW));
            source.sendMessage(Component.text("Aliases: /vb").color(NamedTextColor.GRAY));
        }

        // Provides tab-completion (suggestions) for this command
        public List<String> suggest(Invocation invocation) {
            return List.of(); // No tab-completion needed for this command
        }
    }
}