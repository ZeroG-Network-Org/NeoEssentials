package com.zerog.neoessentials.chat;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatManager handles all chat-related configuration, toggles, and logic for NeoEssentials.
 * Responsibilities:
 *   - Loads and provides access to chat config options (join/quit messages, AFK, muting, etc.)
 *   - Provides enable/disable toggles for each chat command
 *   - Exposes permission and mute/ignore logic for chat features
 *   - Can be extended for advanced formatting, localization, and plugin integration
 * Suggestions for future improvements:
 *   - Localize all user-facing messages (see en_us.json)
 *   - Add advanced formatting (hover/click events, color codes)
 *   - Integrate with external chat plugins (e.g., DiscordSRV)
 *   - Add runtime config reload support
 */
public class ChatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatManager.class);
    
    // Set of muted commands (from config) - thread-safe
    private final Set<String> mutedCommands;
    // Set of permissions required for chat features (from config) - thread-safe
    private final Set<String> playerChatPermissions;

    /**
     * Returns the set of permissions required to chat (from config).
     */
    public Set<String> getPlayerChatPermissions() {
        return playerChatPermissions;
    }
    // Config toggles and options
    private final String customJoinMessage;
    private final String customQuitMessage;
    // Chat format: can be a string (default) or a map for per-group/world - thread-safe
    private final String defaultChatFormat;
    private final java.util.Map<String, String> chatFormatMap;

    // Chat command toggles (from config)
    private final JsonObject commandsConfig;

    /**
     * Constructs a ChatManager from chat and commands config sections.
     * @param chatConfig The chat section of config.json
     * @param commandsConfig The commands section of config.json
     */
    public ChatManager(JsonObject chatConfig, JsonObject commandsConfig) {
        this.mutedCommands = toSet(chatConfig, "muteCommands");
        this.playerChatPermissions = toSet(chatConfig, "playerChatPermissions");
        this.customJoinMessage = chatConfig.has("customJoinMessage") ? chatConfig.get("customJoinMessage").getAsString() : "none";
        this.customQuitMessage = chatConfig.has("customQuitMessage") ? chatConfig.get("customQuitMessage").getAsString() : "none";
        // Support chat-format as string or object
        if (chatConfig.has("chat-format")) {
            if (chatConfig.get("chat-format").isJsonObject()) {
                JsonObject obj = chatConfig.getAsJsonObject("chat-format");
                java.util.Map<String, String> map = new ConcurrentHashMap<>();
                String def = null;
                for (String key : obj.keySet()) {
                    if (key.equalsIgnoreCase("default")) {
                        def = obj.get(key).getAsString();
                    } else {
                        // Lowercased to match getChatFormat()'s lookup keys, which lowercase the
                        // resolved group/world name before checking the map — without this, a
                        // config key written with any uppercase (e.g. "group:SeasonedExplorer",
                        // matching a rank/group's actual name casing) could never match, since
                        // Map.containsKey() is exact-case. Case is irrelevant to the *value*, just
                        // normalized on the way in so both sides of the lookup agree.
                        map.put(key.toLowerCase(), obj.get(key).getAsString());
                    }
                }
                this.defaultChatFormat = def != null ? def : "{neoessentials_displayname}: {MESSAGE}";
                this.chatFormatMap = map;
                NeoLog.info(LOGGER, LogCategory.CHAT, "Loaded chat-format (object): default=[{}], map size={}", this.defaultChatFormat, map.size());
            } else {
                this.defaultChatFormat = chatConfig.get("chat-format").getAsString();
                this.chatFormatMap = java.util.Collections.emptyMap();
                NeoLog.info(LOGGER, LogCategory.CHAT, "Loaded chat-format (string): [{}]", this.defaultChatFormat);
            }
        } else {
            this.defaultChatFormat = "{neoessentials_displayname}: {MESSAGE}";
            this.chatFormatMap = java.util.Collections.emptyMap();
            NeoLog.info(LOGGER, LogCategory.CHAT, "No chat-format in config, using default: [{}]", this.defaultChatFormat);
        }
        this.commandsConfig = commandsConfig;
    }

    /**
     * Converts a config array to a thread-safe Set<String>.
     */
    private Set<String> toSet(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return Collections.emptySet();
        Set<String> set = ConcurrentHashMap.newKeySet();
        obj.getAsJsonArray(key).forEach(e -> set.add(e.getAsString()));
        return set;
    }

    /**
     * Checks if a command is muted for chat.
     */
    public boolean isCommandMuted(String command) {
        return mutedCommands.contains(command);
    }

    /**
     * Checks if a player has a specific chat permission.
     */
    @SuppressWarnings("unused") // May be used by external systems
    public boolean hasChatPermission(String permission) {
        return playerChatPermissions.contains(permission);
    }

    // Accessors for chat config options
    public String getCustomJoinMessage() { return customJoinMessage; }
    public String getCustomQuitMessage() { return customQuitMessage; }

    /**
     * Returns the chat format for a given group and/or world.
     * Priority: templates (if enabled) > group+world > group > world > default
     * @param group Player's primary group (null if unknown)
     * @param world Player's world (null if unknown)
     */
    public String getChatFormat(String group, String world) {
        // Phase 3: Check if format templates are enabled
        try {
            com.google.gson.JsonObject chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("formatTemplates")) {
                com.google.gson.JsonObject templates = chatConfig.getAsJsonObject("formatTemplates");
                if (templates.has("enabled") && templates.get("enabled").getAsBoolean()) {
                    String activeTemplate = templates.has("activeTemplate") ?
                        templates.get("activeTemplate").getAsString() : "default";

                    if (templates.has("templates")) {
                        com.google.gson.JsonObject templateMap = templates.getAsJsonObject("templates");
                        if (templateMap.has(activeTemplate)) {
                            NeoLog.debug(LOGGER, LogCategory.CHAT, "Using format template '{}' for group={}, world={}", activeTemplate, group, world);
                            return templateMap.get(activeTemplate).getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CHAT, "Error checking format templates, falling back to normal format selection", e);
        }

        // Normal format selection
        // Try group+world
        if (group != null && world != null) {
            String key = "group:" + group.toLowerCase() + ":world:" + world.toLowerCase();
            if (chatFormatMap.containsKey(key)) {
                NeoLog.debug(LOGGER, LogCategory.CHAT, "Resolved chat format via group+world key '{}'", key);
                return chatFormatMap.get(key);
            }
        }
        // Try group
        if (group != null) {
            String key = "group:" + group.toLowerCase();
            if (chatFormatMap.containsKey(key)) {
                NeoLog.debug(LOGGER, LogCategory.CHAT, "Resolved chat format via group key '{}'", key);
                return chatFormatMap.get(key);
            }
        }
        // Try world
        if (world != null) {
            String key = "world:" + world.toLowerCase();
            if (chatFormatMap.containsKey(key)) {
                NeoLog.debug(LOGGER, LogCategory.CHAT, "Resolved chat format via world key '{}'", key);
                return chatFormatMap.get(key);
            }
        }
        // Fallback
        NeoLog.debug(LOGGER, LogCategory.CHAT, "No specific chat format match for group={}, world={}; using default format", group, world);
        return defaultChatFormat;
    }

    /**
     * Returns the default chat format (for config migration/compatibility).
     */
    @SuppressWarnings("unused") // May be used by external systems
    public String getDefaultChatFormat() {
        return defaultChatFormat;
    }

    // Chat command enable/disable checks
    @SuppressWarnings("BooleanMethodIsAlwaysInverted") // Used correctly with ! operator
    public boolean isAfkEnabled() { return isCommandEnabled("afk"); }
    public boolean isIgnoreEnabled() { return isCommandEnabled("ignore"); }
    public boolean isMsgEnabled() { return isCommandEnabled("msg"); }
    public boolean isMsgToggleEnabled() { return isCommandEnabled("msgtoggle"); }
    public boolean isMuteEnabled() { return isCommandEnabled("mute"); }
    public boolean isMuteListEnabled() { return isCommandEnabled("mutelist"); }
    public boolean isReplyEnabled() { return isCommandEnabled("reply"); }
    public boolean isSocialSpyEnabled() { return isCommandEnabled("socialspy"); }
    public boolean isUnignoreEnabled() { return isCommandEnabled("unignore"); }
    public boolean isUnmuteEnabled() { return isCommandEnabled("unmute"); }

    /**
     * Checks if a chat command is enabled in config.
     */
    private boolean isCommandEnabled(String command) {
        return commandsConfig != null && commandsConfig.has(command) && commandsConfig.get(command).getAsBoolean();
    }

    // Add more chat logic and improvements here as needed
}
