package com.zerog.neoessentials.integrations;

import com.zerog.neoessentials.integrations.impl.DCIntegrationAdapter;
import com.zerog.neoessentials.integrations.impl.Mc2DiscordAdapter;
import com.zerog.neoessentials.integrations.impl.SDLinkAdapter;
import com.zerog.neoessentials.integrations.impl.WebhookAdapter;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Central integration manager for external chat mods.
 * Provides hooks for mods like DiscordIntegration, ChatMods, etc.
 * Works within the NeoForge modded environment.
 */
public class ChatIntegrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatIntegrationManager.class);
    private static final List<ChatIntegrationAdapter> adapters = new ArrayList<>();

    // ── Discord event log (rolling buffer, max 200 entries) ───────────────────
    private static final int MAX_EVENTS = 200;
    private static final LinkedList<Map<String, Object>> eventLog = new LinkedList<>();

    /**
     * Record a Discord relay event into the rolling log.
     * @param type     Event type (e.g. "chat", "join", "quit", "mute", "afk", "pm")
     * @param actor    Primary player name
     * @param target   Secondary player name, or null
     * @param channel  Destination Discord channel name / id
     * @param message  The raw message that was relayed
     */
    private static synchronized void recordEvent(String type, String actor, String target, String channel, String message) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type",      type);
        entry.put("actor",     actor);
        entry.put("target",    target);
        entry.put("channel",   channel);
        entry.put("message",   message);
        entry.put("timestamp", Instant.now().toString());
        eventLog.addLast(entry);
        if (eventLog.size() > MAX_EVENTS) {
            eventLog.removeFirst();
        }
    }

    /**
     * Return an immutable snapshot of the recent Discord relay event log.
     */
    public static synchronized List<Map<String, Object>> getRecentEvents() {
        return Collections.unmodifiableList(new ArrayList<>(eventLog));
    }

    /**
     * Clear the event log (e.g. on server restart).
     */
    public static synchronized void clearEventLog() {
        eventLog.clear();
    }

    /**
     * Initialize all built-in chat integration adapters.
     * Called once on server start. Each adapter checks if its target mod is loaded
     * before doing anything, so it is safe to call unconditionally.
     */
    public static void initialize() {
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Initializing chat integration adapters...");
        clearAdapters();

        List<ChatIntegrationAdapter> candidates = List.of(
            new SDLinkAdapter(),
            new Mc2DiscordAdapter(),
            new DCIntegrationAdapter(),
            // Unlike the three above, this one needs no companion mod at all (plain HTTPS
            // webhook POST) — it's always a candidate, and simply registers as disabled if no
            // webhookUrl is configured anywhere. Safe to run alongside any/all of the above:
            // it only acts on its own webhookUrl fields, which the bot-based adapters ignore.
            new WebhookAdapter()
        );

        int loaded = 0;
        for (ChatIntegrationAdapter adapter : candidates) {
            try {
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "Probing chat integration adapter candidate '{}'...", adapter.getName());
                if (adapter.initialize()) {
                    registerAdapter(adapter);
                    loaded++;
                }
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to initialize chat integration adapter '" +
                    adapter.getName() + "'", e);
            }
        }

        if (loaded == 0) {
            NeoLog.info(LOGGER, LogCategory.DISCORD, "No external chat integration mods found. Running in standalone mode.");
        } else {
            NeoLog.info(LOGGER, LogCategory.DISCORD, "Initialized {} chat integration adapter(s).", loaded);
        }
    }

    /**
     * Shutdown all registered adapters and clear the list.
     * Called on server stop.
     */
    public static void shutdown() {
        for (ChatIntegrationAdapter adapter : adapters) {
            try {
                adapter.shutdown();
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Error shutting down chat integration adapter '" +
                    adapter.getName() + "'", e);
            }
        }
        clearAdapters();
        clearEventLog();
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Chat integration adapters shut down.");
    }
    /**
     * Register a chat integration adapter for a NeoForge mod
     * @param adapter The adapter to register
     */
    public static void registerAdapter(ChatIntegrationAdapter adapter) {
        if (adapter != null && !adapters.contains(adapter)) {
            adapters.add(adapter);
            NeoLog.info(LOGGER, LogCategory.DISCORD, "Registered chat mod integration adapter: {}", adapter.getName());
        }
    }

    /**
     * Unregister a chat integration adapter
     * @param adapter The adapter to unregister
     */
    public static void unregisterAdapter(ChatIntegrationAdapter adapter) {
        if (adapters.remove(adapter)) {
            NeoLog.info(LOGGER, LogCategory.DISCORD, "Unregistered chat mod integration adapter: {}", adapter.getName());
        }
    }
    
    /**
     * Broadcast a player chat message event to all registered adapters
     * @param player The player sending the message
     * @param channel The channel name (e.g., "local", "global", "staff")
     * @param message The raw message content
     * @param formattedMessage The fully formatted message
     * @param discordChannelId Optional Discord channel ID (null = use default)
     */
    public static void broadcastPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
        String targetCh = (discordChannelId != null && !discordChannelId.isEmpty()) ? discordChannelId : channel;
        recordEvent("chat", player.getName().getString(), null, targetCh, message);
        NeoLog.debug(LOGGER, LogCategory.DISCORD, "Broadcasting player chat from '{}' in channel '{}' (discordChannelId={}) to {} adapter(s)",
            player.getName().getString(), channel, discordChannelId, adapters.size());
        for (ChatIntegrationAdapter adapter : adapters) {
            try {
                adapter.onPlayerChat(player, channel, message, formattedMessage, discordChannelId);
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Error in chat integration adapter " +
                    adapter.getName() + " while handling player chat", e);
            }
        }
    }

    /**
     * Resolves the configured Discord channel override for a non-chat event type, from
     * {@code discordEventChannels.<eventKey>} in config.json — see that section's own comment
     * for why this exists (only chat messages had per-channel Discord routing before this).
     * Returns {@code null} (meaning "let the adapter/bridge mod route to its own default") when
     * the section is missing, the event key isn't present, {@code enabled} is false, or
     * {@code channelId} is blank — every one of those is treated as "not configured", not an
     * error, since this feature is entirely opt-in.
     * @param eventKey one of "join"/"leave"/"mute"/"afk"/"advancement"/"privateMessage"
     */
    private static String resolveEventChannelId(String eventKey) {
        try {
            com.google.gson.JsonObject config = com.zerog.neoessentials.config.ConfigManager.getInstance()
                .getConfig(com.zerog.neoessentials.config.ConfigManager.MAIN_CONFIG);
            if (!config.has("discordEventChannels")) return null;
            com.google.gson.JsonObject section = config.getAsJsonObject("discordEventChannels");
            if (!section.has(eventKey)) return null;
            com.google.gson.JsonObject event = section.getAsJsonObject(eventKey);
            if (!event.has("enabled") || !event.get("enabled").getAsBoolean()) return null;
            if (!event.has("channelId")) return null;
            String channelId = event.get("channelId").getAsString();
            return (channelId != null && !channelId.isBlank()) ? channelId : null;
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Could not read discordEventChannels.{}: {}", eventKey, e.getMessage());
            return null;
        }
    }

    /**
     * Broadcast a private message event to all registered adapters
     * @param sender The sender
     * @param recipient The recipient
     * @param message The message content
     */
    public static void broadcastPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message) {
        recordEvent("pm", sender.getName().getString(), recipient.getName().getString(), "private-messages", message);
        String discordChannelId = resolveEventChannelId("privateMessage");
        for (ChatIntegrationAdapter adapter : adapters) {
            try {
                adapter.onPrivateMessage(sender, recipient, message, discordChannelId);
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Error in chat integration adapter " +
                    adapter.getName() + " while handling a private message", e);
            }
        }
    }

    /**
     * Broadcast a player mute event to all registered adapters
     * @param player The muted player
     * @param reason The mute reason
     * @param isMuted Whether the player is being muted or unmuted
     */
    public static void broadcastMuteEvent(ServerPlayer player, String reason, boolean isMuted) {
        recordEvent("mute", player.getName().getString(), null, "moderation",
                    (isMuted ? "muted" : "unmuted") + (reason != null && !reason.isEmpty() ? ": " + reason : ""));
        String discordChannelId = resolveEventChannelId("mute");
        for (ChatIntegrationAdapter adapter : adapters) {
            try {
                adapter.onPlayerMute(player, reason, isMuted, discordChannelId);
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Error in chat integration adapter " +
                    adapter.getName() + " while handling a mute event", e);
            }
        }
    }

    /**
     * Broadcast an AFK status change to all registered adapters
     * @param player The player
     * @param isAfk Whether the player is AFK
     * @param reason The AFK reason (if any)
     */
    public static void broadcastAfkEvent(ServerPlayer player, boolean isAfk, String reason) {
        recordEvent("afk", player.getName().getString(), null, "chat",
                    (isAfk ? "went AFK" : "returned") + (isAfk && reason != null && !reason.isEmpty() ? ": " + reason : ""));
        String discordChannelId = resolveEventChannelId("afk");
        for (ChatIntegrationAdapter adapter : adapters) {
            try {
                adapter.onAfkStatusChange(player, isAfk, reason, discordChannelId);
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Error in chat integration adapter " +
                    adapter.getName() + " while handling an AFK status change", e);
            }
        }
    }

    /**
     * Broadcast a player join event to all registered adapters
     * @param player The joining player
     */
    public static void broadcastPlayerJoin(ServerPlayer player) {
        recordEvent("join", player.getName().getString(), null, "chat", "joined the server");
        String discordChannelId = resolveEventChannelId("join");
        for (ChatIntegrationAdapter adapter : adapters) {
            try {
                adapter.onPlayerJoin(player, discordChannelId);
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Error in chat integration adapter " +
                    adapter.getName() + " while handling a player join event", e);
            }
        }
    }

    /**
     * Broadcast a player quit event to all registered adapters
     * @param player The quitting player
     */
    public static void broadcastPlayerQuit(ServerPlayer player) {
        recordEvent("quit", player.getName().getString(), null, "chat", "left the server");
        String discordChannelId = resolveEventChannelId("leave");
        for (ChatIntegrationAdapter adapter : adapters) {
            try {
                adapter.onPlayerQuit(player, discordChannelId);
            } catch (Exception e) {
                NeoLog.error(LOGGER, LogCategory.DISCORD, "Error in chat integration adapter " +
                    adapter.getName() + " while handling a player quit event", e);
            }
        }
    }
    
    /**
     * Get adapter status list for the dashboard.
     * @return List of maps, each with "name" and "enabled".
     */
    public static List<Map<String, Object>> getAdapterStatus() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatIntegrationAdapter adapter : adapters) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name",    adapter.getName());
            info.put("enabled", adapter.isEnabled());
            info.put("ready",   adapter.isReady());
            info.put("nativeRelayWarnings", adapter.getNativeRelayWarnings());
            result.add(info);
        }
        return result;
    }

    /**
     * Resolve the Discord user ID linked to a Minecraft player, checking whichever
     * registered adapter is currently ready. Used by dashboard Discord-identity
     * lookups and permission sync — adapter-agnostic, works with SDLink, Mc2Discord, or DCIntegration.
     */
    public static Optional<String> findLinkedDiscordId(UUID minecraftUuid) {
        for (ChatIntegrationAdapter adapter : adapters) {
            if (!adapter.isReady()) continue;
            Optional<String> id = adapter.getLinkedDiscordId(minecraftUuid);
            if (id.isPresent()) return id;
        }
        return Optional.empty();
    }

    /**
     * Resolve the Discord role IDs held by a linked player, checking whichever
     * registered adapter is currently ready.
     */
    public static List<String> findDiscordRoleIds(UUID minecraftUuid) {
        for (ChatIntegrationAdapter adapter : adapters) {
            if (!adapter.isReady()) continue;
            List<String> roles = adapter.getDiscordRoleIds(minecraftUuid);
            if (!roles.isEmpty()) return roles;
        }
        return List.of();
    }

    /**
     * Reverse of {@link #findLinkedDiscordId(UUID)} — resolve the Minecraft account linked to
     * a Discord user ID, checking whichever registered adapter is currently ready. Used by
     * the dashboard's Discord OAuth2 login flow to map a visitor's real Discord ID (obtained
     * by the Laravel app, not this mod) back to a linked player.
     */
    public static Optional<UUID> findLinkedMinecraftUuid(String discordId) {
        for (ChatIntegrationAdapter adapter : adapters) {
            if (!adapter.isReady()) continue;
            Optional<UUID> uuid = adapter.getLinkedMinecraftUuid(discordId);
            if (uuid.isPresent()) return uuid;
        }
        return Optional.empty();
    }

    /**
     * Sends a raw message to a specific Discord channel by ID, trying each ready adapter in
     * turn until one succeeds. Used by the dashboard's "send test message" feature.
     */
    public static boolean sendToChannel(String channelId, String message) {
        for (ChatIntegrationAdapter adapter : adapters) {
            if (!adapter.isReady()) continue;
            if (adapter.sendToChannel(channelId, message)) {
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "Sent message to Discord channel '{}' via adapter '{}'",
                    channelId, adapter.getName());
                return true;
            }
        }
        NeoLog.debug(LOGGER, LogCategory.DISCORD, "No ready adapter could send a message to Discord channel '{}'", channelId);
        return false;
    }

    /**
     * Get all registered adapters
     * @return List of registered adapters
     */
    public static List<ChatIntegrationAdapter> getAdapters() {
        return new ArrayList<>(adapters);
    }
    
    /**
     * Check if any integration adapters are registered
     * @return true if adapters are registered
     */
    public static boolean hasIntegrations() {
        return !adapters.isEmpty();
    }
    
    /**
     * Clear all registered adapters (used for shutdown)
     */
    public static void clearAdapters() {
        adapters.clear();
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Cleared all chat mod integration adapters");
    }
}