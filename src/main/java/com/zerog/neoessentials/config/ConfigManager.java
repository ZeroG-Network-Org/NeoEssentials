package com.zerog.neoessentials.config;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.zerog.neoessentials.util.ResourceUtil;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings({"unused", "InvertedCondition"}) // Public API class with many getters/setters
public class ConfigManager {
    /**
     * Returns true if kick actions should be logged (logKickActions in config).
     * Defaults to true if not set.
     */
    public static boolean isLogKickActionsEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("logKickActions")) {
                return kickSettings.get("logKickActions").getAsBoolean();
            }
        }
        return true;
    }
    /**
     * Returns the kickMessage from moderation.kickSettings.kickMessage
     * Defaults to 'You have been kicked from the server. Reason: {reason} Kicked by: {kicker}' if not set.
     */
    public static String getKickMessage() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("kickMessage")) {
                String val = kickSettings.get("kickMessage").getAsString();
                if (val != null && !val.trim().isEmpty()) return val;
            }
        }
        return "You have been kicked from the server.\nReason: {reason}\nKicked by: {kicker}";
    }

    /**
     * Returns the kickAllMessage from moderation.kickSettings.kickAllMessage
     * Defaults to 'Server maintenance in progress. Please reconnect in a few minutes.' if not set.
     */
    public static String getKickAllMessage() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("kickAllMessage")) {
                String val = kickSettings.get("kickAllMessage").getAsString();
                if (val != null && !val.trim().isEmpty()) return val;
            }
        }
        return "Server maintenance in progress. Please reconnect in a few minutes.";
    }
    /**
     * Returns true if staff should be notified when a player is kicked (notifyStaffOnKick in config).
     * Defaults to true if not set.
     */
    public static boolean isNotifyStaffOnKickEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("notifyStaffOnKick")) {
                return kickSettings.get("notifyStaffOnKick").getAsBoolean();
            }
        }
        return true;
    }
    /**
     * Returns the defaultKickReason from moderation.kickSettings.defaultKickReason
     * Defaults to 'Kicked by an operator' if not set or invalid.
     */
    @SuppressWarnings("unused") // Public API method
    public static String getDefaultKickReason() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("defaultKickReason")) {
                String val = kickSettings.get("defaultKickReason").getAsString();
                if (val != null && !val.trim().isEmpty()) return val;
            }
        }
        return "Kicked by an operator";
    }
    /**
     * Returns the maxKickReason from moderation.kickSettings.maxKickReason
     * Defaults to 500 if not set or invalid.
     */
    @SuppressWarnings("unused") // Public API method
    public static int getMaxKickReasonLength() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("maxKickReason")) {
                try {
                    int val = kickSettings.get("maxKickReason").getAsInt();
                    if (val > 0) return val;
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for moderation.kickSettings.maxKickReason, using default 500", e);
                }
            }
        }
        return 500;
    }

    /**
     * Returns true if kick actions should be broadcast to all players (broadcastKicks in config).
     * Defaults to false if not set.
     */
    public static boolean isBroadcastKicksEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("broadcastKicks")) {
                return kickSettings.get("broadcastKicks").getAsBoolean();
            }
        }
        return false;
    }
    /**
     * Returns true if the kick system is enabled (enableKickSystem in config).
     * Defaults to true if not set.
     */
    @SuppressWarnings("unused") // Public API method
    public static boolean isKickSystemEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
            JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
            if (kickSettings.has("enableKickSystem")) {
                return kickSettings.get("enableKickSystem").getAsBoolean();
            }
        }
        return true;
    }
    /**
     * Returns the freezeMessage from moderation.freezeSettings.freezeMessage
     * Falls back to localization key if not set.
     */
    public static String getFreezeMessage() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("freezeMessage")) {
                String val = freezeSettings.get("freezeMessage").getAsString();
                if (val != null && !val.trim().isEmpty()) return val;
            }
        }
        // Fallback to localization key
        return "neoessentials.moderation.frozen_message";
    }

    /**
     * Returns the unfreezeMessage from moderation.freezeSettings.unfreezeMessage
     * Falls back to localization key if not set.
     */
    public static String getUnfreezeMessage() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("unfreezeMessage")) {
                String val = freezeSettings.get("unfreezeMessage").getAsString();
                if (val != null && !val.trim().isEmpty()) return val;
            }
        }
        // Fallback to localization key
        return "neoessentials.moderation.unfrozen_message";
    }

    /**
     * Returns the freezeReminder from moderation.freezeSettings.freezeReminder
     * Falls back to localization key if not set.
     */
    public static String getFreezeReminder() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("freezeReminder")) {
                String val = freezeSettings.get("freezeReminder").getAsString();
                if (val != null && !val.trim().isEmpty()) return val;
            }
        }
        // Fallback to localization key
        return "neoessentials.moderation.freeze_reminder";
    }
    /**
     * Returns the defaultFreezeReason from moderation.freezeSettings.defaultFreezeReason
     * Defaults to 'Frozen by an operator' if not set or invalid.
     */
    public static String getDefaultFreezeReason() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("defaultFreezeReason")) {
                String val = freezeSettings.get("defaultFreezeReason").getAsString();
                if (val != null && !val.trim().isEmpty()) return val;
            }
        }
        return "Frozen by an operator";
    }
    /**
     * Returns the maxFreezeReason from moderation.freezeSettings.maxFreezeReason
     * Defaults to 500 if not set or invalid.
     */
    public static int getMaxFreezeReasonLength() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("maxFreezeReason")) {
                try {
                    int val = freezeSettings.get("maxFreezeReason").getAsInt();
                    if (val > 0) return val;
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for moderation.freezeSettings.maxFreezeReason, using default 500", e);
                }
            }
        }
        return 500;
    }

    /**
     * Returns the freezeReminderInterval (in seconds) from moderation.freezeSettings.freezeReminderInterval
     * Defaults to 30 if not set or invalid.
     */
    @SuppressWarnings("unused") // Public API method
    public static int getFreezeReminderInterval() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("freezeReminderInterval")) {
                try {
                    int val = freezeSettings.get("freezeReminderInterval").getAsInt();
                    if (val >= 0) return val;
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for moderation.freezeSettings.freezeReminderInterval, using default 30", e);
                }
            }
        }
        return 30;
    }

    /**
     * Returns true if freeze/unfreeze actions should be logged (logFreezeActions in config).
     * Defaults to true if not set.
     */
    @SuppressWarnings("unused") // Public API method
    public static boolean isLogFreezeActionsEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("logFreezeActions")) {
                return freezeSettings.get("logFreezeActions").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if frozen players should remain frozen when they log back in (freezeOnLogin in config).
     * Defaults to true if not set.
     */
    @SuppressWarnings("unused") // Public API method
    public static boolean isFreezeOnLoginEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("freezeOnLogin")) {
                return freezeSettings.get("freezeOnLogin").getAsBoolean();
            }
        }
        return true;
    }
    /**
     * Returns the list of allowed commands for frozen players from moderation.freezeSettings.allowedCommands.
     * Returns an empty list if not set.
     */
    public static java.util.List<String> getFreezeAllowedCommands() {
        java.util.List<String> allowed = new java.util.ArrayList<>();
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("allowedCommands") && freezeSettings.get("allowedCommands").isJsonArray()) {
                for (var el : freezeSettings.getAsJsonArray("allowedCommands")) {
                    if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                        allowed.add(el.getAsString().toLowerCase());
                    }
                }
            }
        }
        return allowed;
    }


    /**
     * Returns true if freeze system is enabled in moderation.freezeSettings config section.
     * (moderation.freezeSettings.enableFreezeSystem)
     * Defaults to true if not set.
     */
    public static boolean isFreezeSystemEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("enableFreezeSystem")) {
                return freezeSettings.get("enableFreezeSystem").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if frozen players should be prevented from using commands (preventCommands in config).
     * Defaults to true if not set.
     */
    public static boolean isFreezePreventCommandsEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
            JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
            if (freezeSettings.has("preventCommands")) {
                return freezeSettings.get("preventCommands").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if vanished players should be prevented from interacting (preventInteraction in config).
     * Defaults to true if not set.
     */
    public static boolean isVanishPreventInteractionEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
            JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
            if (vanishSettings.has("preventInteraction")) {
                return vanishSettings.get("preventInteraction").getAsBoolean();
            }
        }
        return true;
    }
    /**
     * Returns true if vanish actions should be broadcast to staff (broadcastToStaffVanish in config).
     * Defaults to false if not set.
     */
    public static boolean isBroadcastToStaffVanishEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
            JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
            if (vanishSettings.has("broadcastToStaffVanish")) {
                return vanishSettings.get("broadcastToStaffVanish").getAsBoolean();
            }
        }
        return false;
    }

    /**
     * Returns true if vanish actions should be broadcast to all players (BroadcastToAllVanish in config).
     * Defaults to false if not set.
     */
    public static boolean isBroadcastToAllVanishEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
            JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
            if (vanishSettings.has("BroadcastToAllVanish")) {
                return vanishSettings.get("BroadcastToAllVanish").getAsBoolean();
            }
        }
        return false;
    }
    /**
     * Returns true if vanished players should be hidden from the tab list (hideFromTabList in config).
     * Defaults to true if not set.
     */
    public static boolean isHideFromTabListEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
            JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
            if (vanishSettings.has("hideFromTabList")) {
                return vanishSettings.get("hideFromTabList").getAsBoolean();
            }
        }
        return true;
    }
    /**
     * Returns true if vanish actions should be logged (moderation.vanishSettings.logVanishActions).
     * Defaults to true if not set.
     */
    public static boolean isLogVanishActionsEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
            JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
            if (vanishSettings.has("logVanishActions")) {
                return vanishSettings.get("logVanishActions").getAsBoolean();
            }
        }
        return true;
    }
    // Note: instance methods for vanish system/on-join exist and should be used via getInstance().
    /**
     * Returns true if staff should be vanished on join (vanishOnJoin in config).
     * Defaults to false if not set.
     */
    public boolean isVanishOnJoinEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
            JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
            if (vanishSettings.has("vanishOnJoin")) {
                return vanishSettings.get("vanishOnJoin").getAsBoolean();
            }
        }
        return false;
    }
    /**
     * Returns true if the vanish system is enabled in the config
     * (moderation.vanishSettings.enableVanishSystem).
     * Defaults to true if not set.
     */
    public boolean isVanishSystemEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
            JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
            if (vanishSettings.has("enableVanishSystem")) {
                return vanishSettings.get("enableVanishSystem").getAsBoolean();
            }
        }
        return true;
    }
        /**
         * Returns true if logJailActions is enabled in moderation.jailSettings config section.
         * (moderation.jailSettings.logJailActions)
         */
        public boolean isLogJailActionsEnabled() {
            JsonObject config = getConfig(MAIN_CONFIG);
            if (config.has("moderation")) {
                JsonObject moderation = config.getAsJsonObject("moderation");
                if (moderation.has("jailSettings")) {
                    JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                    if (jailSettings.has("logJailActions")) {
                        return jailSettings.get("logJailActions").getAsBoolean();
                    }
                }
            }
            return true;
        }
        /**
         * Returns true if preventJailEscape is enabled in moderation.jailSettings config section.
         * (moderation.jailSettings.preventJailEscape)
         */
        public boolean isPreventJailEscapeEnabled() {
            JsonObject config = getConfig(MAIN_CONFIG);
            if (config.has("moderation")) {
                JsonObject moderation = config.getAsJsonObject("moderation");
                if (moderation.has("jailSettings")) {
                    JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                    if (jailSettings.has("preventJailEscape")) {
                        return jailSettings.get("preventJailEscape").getAsBoolean();
                    }
                }
            }
            return false;
        }
        /**
         * Returns the jail message format from moderation.jailSettings.jailMessageFormat
         * Defaults to a standard message if not set.
         */
        public String getJailMessageFormat() {
            JsonObject config = getConfig(MAIN_CONFIG);
            if (config.has("moderation")) {
                JsonObject moderation = config.getAsJsonObject("moderation");
                if (moderation.has("jailSettings")) {
                    JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                    if (jailSettings.has("jailMessageFormat")) {
                        String val = jailSettings.get("jailMessageFormat").getAsString();
                        if (val != null && !val.trim().isEmpty()) return val;
                    }
                }
            }
            return "You cannot leave jail!";
        }
        /**
         * Returns the configured jail wand item id from moderation.jailSettings.wandItem.
         * Defaults to "minecraft:stick" if not set.
         */
        public static String getJailWandItem() {
            JsonObject config = getInstance().getConfig(MAIN_CONFIG);
            if (config.has("moderation")) {
                JsonObject moderation = config.getAsJsonObject("moderation");
                if (moderation.has("jailSettings")) {
                    JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                    if (jailSettings.has("wandItem")) {
                        String val = jailSettings.get("wandItem").getAsString();
                        if (val != null && !val.trim().isEmpty()) return val;
                    }
                }
            }
            return "minecraft:stick";
        }
        /**
         * Returns the default sphere-jail radius (blocks) used when a jail is set as a sphere
         * without an explicit radius, from moderation.jailSettings.defaultSphereRadius.
         * Defaults to 10.0 if not set. Also used to fall back existing pre-shape-system jails
         * (which only ever had a center point) to a sphere of this radius.
         */
        public static double getDefaultJailSphereRadius() {
            JsonObject config = getInstance().getConfig(MAIN_CONFIG);
            if (config.has("moderation")) {
                JsonObject moderation = config.getAsJsonObject("moderation");
                if (moderation.has("jailSettings")) {
                    JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                    if (jailSettings.has("defaultSphereRadius")) {
                        return jailSettings.get("defaultSphereRadius").getAsDouble();
                    }
                }
            }
            return 10.0;
        }
        /**
         * Returns the maxJailsBeforeTempBan from moderation.jailSettings.maxJailsBeforeTempBan
         * Defaults to 3 if not set.
         */
    public static int getMaxJailsBeforeTempBan() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("jailSettings")) {
                JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                if (jailSettings.has("maxJailsBeforeTempBan")) {
                    return jailSettings.get("maxJailsBeforeTempBan").getAsInt();
                }
            }
        }
        return 3;
    }
    /**
     * Returns true if jailTeleportOnLogin is enabled in moderation.jailSettings.jailTeleportOnLogin
     * Defaults to true if not set.
     */
    public boolean isJailTeleportOnLoginEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("jailSettings")) {
                JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                if (jailSettings.has("jailTeleportOnLogin")) {
                    return jailSettings.get("jailTeleportOnLogin").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns the staff notification permission node from moderation.generalSettings.staffNotificationPermission
     * Defaults to 'neoessentials.moderation.notify' if not set.
     */
    public String getStaffNotificationPermission() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("generalSettings")) {
                JsonObject general = moderation.getAsJsonObject("generalSettings");
                if (general.has("staffNotificationPermission")) {
                    String val = general.get("staffNotificationPermission").getAsString();
                    if (val != null && !val.trim().isEmpty()) return val;
                }
            }
        }
        return "neoessentials.moderation.notify";
    }

    /**
     * Returns true if broadcastBans is enabled in moderation.banSettings config section.
     * (moderation.banSettings.broadcastBans)
     */
    public boolean isBroadcastBansEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("broadcastBans")) {
                    return banSettings.get("broadcastBans").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Returns true if logBanActions is enabled in moderation.banSettings config section.
     * (moderation.banSettings.logBanActions)
     */
    public boolean isLogBanActionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("logBanActions")) {
                    return banSettings.get("logBanActions").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns the checkExpiredBansInterval (in seconds) from moderation.banSettings.checkExpiredBansInterval.
     * Defaults to 300 if not set or invalid. Values <= 0 disable the scheduler (returns 0). Minimum allowed is 5 seconds.
     */
    public int getCheckExpiredBansInterval() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("checkExpiredBansInterval")) {
                    try {
                        int val = banSettings.get("checkExpiredBansInterval").getAsInt();
                        if (val <= 0) return 0; // Disabled
                        return Math.max(val, 5); // Enforce minimum
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for moderation.banSettings.checkExpiredBansInterval, using default 300", e);
                    }
                }
            }
        }
        return 300;
    }
    /**
     * Returns the defaultBanReason from moderation.banSettings.defaultBanReason
     * Defaults to 'Banned by an operator' if not set or invalid.
     */
    public String getDefaultBanReason() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("defaultBanReason")) {
                    String val = banSettings.get("defaultBanReason").getAsString();
                    if (val != null && !val.trim().isEmpty()) return val;
                }
            }
        }
        return "Banned by an operator";
    }

    /**
     * Returns the maxBanReason from moderation.banSettings.maxBanReason
     * Defaults to 500 if not set or invalid.
     */
    public int getMaxBanReasonLength() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("maxBanReason")) {
                    try {
                        int val = banSettings.get("maxBanReason").getAsInt();
                        if (val > 0) return val;
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for moderation.banSettings.maxBanReason, using default 500", e);
                    }
                }
            }
        }
        return 500;
    }

    /**
     * Returns the max jail reason length from moderation.jailSettings.maxJailReason.
     * Defaults to 500 if not set or invalid.
     */
    public int getMaxJailReasonLength() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("jailSettings")) {
                JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                if (jailSettings.has("maxJailReason")) {
                    try {
                        int val = jailSettings.get("maxJailReason").getAsInt();
                        if (val > 0) return val;
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for moderation.jailSettings.maxJailReason, using default 500", e);
                    }
                }
            }
        }
        return 500;
    }

    /**
     * Returns true if IP bans are disabled in moderation.banSettings.enableIPBans.
     * Defaults to false if not set.
     */
    public boolean isIPBansDisabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("enableIPBans")) {
                    return !banSettings.get("enableIPBans").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Returns true if permanent bans are enabled in moderation.banSettings.enablePermanentBans
     * Defaults to true if not set.
     */
    public boolean isPermanentBansEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("enablePermanentBans")) {
                    return banSettings.get("enablePermanentBans").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns true if temporary bans are enabled in moderation.banSettings.enableTempBans
     * Defaults to true if not set.
     */
    public boolean isTempBansEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("enableTempBans")) {
                    return banSettings.get("enableTempBans").getAsBoolean();
                }
            }
        }
        return true;
    }
    /**
     * Returns true if autoExpireTempBans is enabled in moderation.banSettings.autoExpireTempBans
     * Defaults to true if not set.
     */
    public boolean isAutoExpireTempBansEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("autoExpireTempBans")) {
                    return banSettings.get("autoExpireTempBans").getAsBoolean();
                }
            }
        }
        return true;
    }
    /**
     * Returns true if enableParticleEffects is enabled in teleportation.generalSettings config section.
     * (teleportation.generalSettings.enableParticleEffects)
     */
    public boolean getEnableParticleEffects() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("generalSettings")) {
                JsonObject general = tp.getAsJsonObject("generalSettings");
                if (general.has("enableParticleEffects")) {
                    return general.get("enableParticleEffects").getAsBoolean();
                }
            }
        }
        return true; // Default to enabled if not set
    }
    /**
     * Returns the maxTeleportDistance from teleportation.generalSettings.maxTeleportDistance
     * Returns -1 for unlimited if not set or invalid.
     */
    public int getMaxTeleportDistance() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("generalSettings")) {
                JsonObject general = tp.getAsJsonObject("generalSettings");
                if (general.has("maxTeleportDistance")) {
                    try {
                        return general.get("maxTeleportDistance").getAsInt();
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for teleportation.generalSettings.maxTeleportDistance, using default -1 (unlimited)", e);
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Returns true if allowTeleportInCombat is enabled in teleportation.generalSettings config section.
     * (teleportation.generalSettings.allowTeleportInCombat)
     */
    public boolean isAllowTeleportInCombatEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("generalSettings")) {
                JsonObject general = tp.getAsJsonObject("generalSettings");
                if (general.has("allowTeleportInCombat")) {
                    return general.get("allowTeleportInCombat").getAsBoolean();
                }
            }
        }
        return false;
    }
    /**
     * Returns true if logTeleportRequests is enabled in teleportation.teleportRequestSettings config section.
     * (teleportation.teleportRequestSettings.logTeleportRequests)
     */
    public boolean isLogTeleportRequestsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("teleportRequestSettings")) {
                JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                if (req.has("logTeleportRequests")) {
                    return req.get("logTeleportRequests").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Returns true if autoAcceptFromFriends is enabled in teleportation.teleportRequestSettings config section.
     * (teleportation.teleportRequestSettings.autoAcceptFromFriends)
     */
    public boolean isAutoAcceptTeleportFromFriendsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("teleportRequestSettings")) {
                JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                if (req.has("autoAcceptFromFriends")) {
                    return req.get("autoAcceptFromFriends").getAsBoolean();
                }
            }
        }
        return false;
    }
    /**
     * Returns true if enableRequestNotifications is enabled in teleportation.teleportRequestSettings config section.
     * (teleportation.teleportRequestSettings.enableRequestNotifications)
     */
    public boolean isTeleportRequestNotificationsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("teleportRequestSettings")) {
                JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                if (req.has("enableRequestNotifications")) {
                    return req.get("enableRequestNotifications").getAsBoolean();
                }
            }
        }
        return true;
    }
    /**
     * Returns true if allowMultipleRequests is enabled in teleportation.teleportRequestSettings config section.
     * (teleportation.teleportRequestSettings.allowMultipleRequests)
     */
    public boolean isAllowMultipleTeleportRequestsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("teleportRequestSettings")) {
                JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                if (req.has("allowMultipleRequests")) {
                    return req.get("allowMultipleRequests").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Returns the max number of pending teleport requests per player from teleportation.teleportRequestSettings.maxPendingRequests
     * Defaults to 5 if not set or invalid.
     */
    public int getMaxPendingTeleportRequests() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("teleportRequestSettings")) {
                JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                if (req.has("maxPendingRequests")) {
                    try {
                        int val = req.get("maxPendingRequests").getAsInt();
                        if (val > 0) return val;
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for teleportation.teleportRequestSettings.maxPendingRequests, using default 5", e);
                    }
                }
            }
        }
        return 5;
    }

    /**
     * Returns the teleport request timeout (in seconds) from teleportation.teleportRequestSettings.requestTimeout
     * Defaults to 60 if not set or invalid.
     */
    public int getTeleportRequestTimeoutSeconds() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("teleportRequestSettings")) {
                JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                if (req.has("requestTimeout")) {
                    try {
                        int val = req.get("requestTimeout").getAsInt();
                        if (val > 0) return val;
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for teleportation.teleportRequestSettings.requestTimeout, using default 60", e);
                    }
                }
            }
        }
        return 60;
    }

    /**
     * Returns cooldown in seconds between sending teleport requests from teleportation.teleportRequestSettings.cooldownBetweenRequests
     * Defaults to 10 if not set or invalid.
     */
    public int getCooldownBetweenTeleportRequestsSeconds() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("teleportRequestSettings")) {
                JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                if (req.has("cooldownBetweenRequests")) {
                    try {
                        int val = req.get("cooldownBetweenRequests").getAsInt();
                        if (val >= 0) return val;
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for teleportation.teleportRequestSettings.cooldownBetweenRequests, using default 10", e);
                    }
                }
            }
        }
        return 10;
    }

    /**
     * Returns true if logSpawnActions is enabled in teleportation.spawnSettings config section.
     * (teleportation.spawnSettings.logSpawnActions)
     */
    public boolean isLogSpawnActionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("spawnSettings")) {
                JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
                if (spawnSettings.has("logSpawnActions")) {
                    return spawnSettings.get("logSpawnActions").getAsBoolean();
                }
            }
        }
        return false;
    }


    /**
     * Returns true if cancelOnDamage is enabled in teleportation.generalSettings config section.
     * (teleportation.generalSettings.cancelOnDamage)
     */
    public boolean isCancelOnDamageEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("generalSettings")) {
                JsonObject general = tp.getAsJsonObject("generalSettings");
                if (general.has("cancelOnDamage")) {
                    return general.get("cancelOnDamage").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Check if teleportation module is enabled (modules.teleportationEnabled)
     */
    public boolean isTeleportationEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("teleportationEnabled")) {
                return modules.get("teleportationEnabled").getAsBoolean();
            }
        }
        return true; // Default to enabled
    }

    /**
     * Returns true if requireConfirmationForDelete is enabled in teleportation.homeSettings config section.
     * (teleportation.homeSettings.requireConfirmationForDelete)
     */
    public boolean isRequireConfirmationForDeleteEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("homeSettings")) {
                JsonObject homeSettings = tp.getAsJsonObject("homeSettings");
                if (homeSettings.has("requireConfirmationForDelete")) {
                    return homeSettings.get("requireConfirmationForDelete").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Returns true if logHomeActions is enabled in teleportation.homeSettings config section.
     * (teleportation.homeSettings.logHomeActions)
     */
    public boolean isLogHomeActionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("homeSettings")) {
                JsonObject homeSettings = tp.getAsJsonObject("homeSettings");
                if (homeSettings.has("logHomeActions")) {
                    return homeSettings.get("logHomeActions").getAsBoolean();
                }
            }
        }
        return false;
    }
    /**
     * Returns true if newPlayerKit is enabled in kits config section.
     * (kits.newPlayerKit.enabled)
     */
    public boolean isNewPlayerKitEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("newPlayerKit")) {
                JsonObject npk = kits.getAsJsonObject("newPlayerKit");
                if (npk.has("enabled")) {
                    return npk.get("enabled").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Returns the kit name for newPlayerKit (kits.newPlayerKit.kitName), or empty string if not set.
     */
    public String getNewPlayerKitName() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("newPlayerKit")) {
                JsonObject npk = kits.getAsJsonObject("newPlayerKit");
                if (npk.has("kitName")) {
                    return npk.get("kitName").getAsString();
                }
            }
        }
        return "";
    }
    /**
     * Gets the maximum number of kits a player can have active cooldowns for (kits.maxKitsPerPlayer).
     * Returns -1 for unlimited if not set or invalid.
     */
    public int getMaxKitsPerPlayer() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("maxKitsPerPlayer")) {
                try {
                    return kits.get("maxKitsPerPlayer").getAsInt();
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for kits.maxKitsPerPlayer, using default -1 (unlimited)", e);
                }
            }
        }
        return -1;
    }
    /**
     * Check if AFK system is enabled (afk.enabled)
     */
    public boolean isAfkEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("afk")) {
            JsonObject afk = config.getAsJsonObject("afk");
            if (afk.has("enabled")) {
                return afk.get("enabled").getAsBoolean();
            }
        }
        return true; // Default to enabled
    }
    /**
     * Get the permission cache expiry in minutes (permissions.permissionCacheExpiryMinutes)
     * Returns 5 if not set or invalid.
     */
    public int getPermissionCacheExpiryMinutes() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("permissionCacheExpiryMinutes")) {
                try {
                    int val = perms.get("permissionCacheExpiryMinutes").getAsInt();
                    if (val > 0) return val;
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for permissions.permissionCacheExpiryMinutes, using default 5", e);
                }
            }
        }
        return 5; // Default to 5 minutes if not set
    }
    /**
     * Check if permission caching is enabled (permissions.cachePermissions)
     */
    public boolean isPermissionCacheEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("cachePermissions")) {
                return perms.get("cachePermissions").getAsBoolean();
            }
        }
        return true; // Default to enabled for legacy behavior
    }
    /**
     * Check if ops should bypass all permissions (permissions.opsBypassPermissions)
     */
    public boolean isOpsBypassPermissionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("opsBypassPermissions")) {
                return perms.get("opsBypassPermissions").getAsBoolean();
            }
        }
        return true; // Default to true for legacy behavior
    }

    /**
     * Whether {@link #isOpsBypassPermissionsEnabled()} and {@link #isVanillaOpFallbackEnabled()}
     * also apply to GRADED economy-modifier permission nodes — {@code
     * neoessentials.economy.sellmultiplier.*}, {@code .taxexempt}, {@code .nopaycooldown},
     * {@code .maxbalance.*}, {@code .paylimit.*} — checked by {@link
     * com.zerog.neoessentials.economy.compat.PermissionBasedModifiers}.
     * (permissions.opsBypassEconomyModifierPermissions)
     *
     * <p>Those nodes represent opt-in bonuses meant to be granted explicitly, not admin
     * capabilities, so this defaults to {@code false}: an OP still bypasses ordinary command
     * permission checks, but doesn't silently get the highest sell multiplier / free tax
     * exemption / no pay cooldown just for being OP.
     */
    public boolean isOpsBypassEconomyModifierPermissionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("opsBypassEconomyModifierPermissions")) {
                return perms.get("opsBypassEconomyModifierPermissions").getAsBoolean();
            }
        }
        return false; // Default: graded economy bonuses require an explicit grant
    }

    /**
     * Whether a wildcard permission (e.g. {@code neoessentials.*}, {@code
     * neoessentials.economy.*}) granted to a group or user is allowed to satisfy the same
     * graded economy-modifier nodes covered by {@link #isOpsBypassEconomyModifierPermissionsEnabled()}.
     * (permissions.wildcardsGrantEconomyModifierPermissions)
     *
     * <p>Defaults to {@code false}: those nodes must be listed explicitly — either the exact
     * node itself, or a wildcard scoped no broader than the node's own tier family (e.g.
     * {@code neoessentials.economy.sellmultiplier.*}) — even for a group whose permission list
     * is just {@code neoessentials.*}. Consulted by {@link
     * com.zerog.neoessentials.permissions.PermissionManager}'s wildcard-matching helpers.
     */
    public boolean isWildcardsGrantEconomyModifierPermissionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("wildcardsGrantEconomyModifierPermissions")) {
                return perms.get("wildcardsGrantEconomyModifierPermissions").getAsBoolean();
            }
        }
        return false; // Default: graded economy bonuses require an explicit grant
    }

    /**
     * Check if vanilla OP status should act as a last-resort fallback when every
     * permission system (external adapter + internal manager) is unavailable or
     * returns {@code false}. (permissions.vanillaOpFallback)
     *
     * <p>Unlike {@link #isOpsBypassPermissionsEnabled()} which skips permission
     * checks entirely, this fires <em>after</em> all checks have run — so the
     * permission system is still consulted first in normal operation.  The fallback
     * is designed to prevent admin lockouts when configs are corrupted or an
     * external permission mod crashes.
     *
     * <p>Defaults to {@code true}.
     */
    public boolean isVanillaOpFallbackEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("vanillaOpFallback")) {
                return perms.get("vanillaOpFallback").getAsBoolean();
            }
        }
        return true; // Safe default — prevents lockouts
    }

    /**
     * Check if permission-change audit logging is enabled (permissions.auditLogging).
     * Defaults to {@code true}.
     */
    public boolean isPermissionAuditEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("auditLogging")) {
                return perms.get("auditLogging").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Get the default group name from config.json (permissions.defaultGroup).
     * Returns "default" if not set or empty.
     */
    public String getDefaultGroup() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("defaultGroup")) {
                String group = perms.get("defaultGroup").getAsString();
                if (group != null && !group.trim().isEmpty()) {
                    return group.trim();
                }
            }
        }
        return "default";
    }

    /**
     * Check if a command is enabled in the config (commands section).
     * Returns true if the command is enabled or not explicitly disabled.
     */
    public boolean isCommandEnabled(String command) {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("commands")) {
            JsonObject commands = config.getAsJsonObject("commands");
            if (commands.has(command)) {
                return commands.get(command).getAsBoolean();
            }
        }
        return true; // Default to enabled if not specified
    }
    /**
     * Returns true if allowKitOverride is enabled in kits config section.
     * (kits.allowKitOverride)
     */
    public boolean isAllowKitOverrideEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("allowKitOverride")) {
                return kits.get("allowKitOverride").getAsBoolean();
            }
        }
        return false;
    }
    /**
     * Retrieve the config object for the given config file name.
     * Loads and caches the config if not already loaded.
     * Supports split configs - if config.json is requested and split configs are enabled,
     * returns merged view of all split config files.
     */
    public JsonObject getConfig(String configName) {
        lock.readLock().lock();
        FileReader reader = null;
        try {
            if (configCache.containsKey(configName)) {
                return configCache.get(configName);
            }

            // Special handling for config.json when split configs are enabled
            if (configName.equals(MAIN_CONFIG) && ConfigSplitter.isSplittingEnabled()) {
                // Always merge from split files, never from config.json
                // NOTE: must use plain LOGGER here, not NeoLog — NeoLog's category gating
                // reads logging.categories.config.debug via getLoggingCategoryEntry(), which
                // calls back into this very method (getConfig(MAIN_CONFIG)), causing infinite
                // recursion / StackOverflowError on every startup.
                LOGGER.debug("Split config mode enabled; merging split files instead of reading {}", configName);
                JsonObject merged = ConfigSplitter.mergeSplitConfigs();
                configCache.put(configName, merged);
                return merged;
            }

            // If configName is a section name (no .json extension), extract it from the
            // main config. This allows callers such as getConfig("chat") to work regardless
            // of whether split configs are enabled, without needing to know the file layout.
            if (!configName.endsWith(".json")) {
                // ReentrantReadWriteLock allows same thread to re-acquire the read lock
                JsonObject mainConfig = getConfig(MAIN_CONFIG);
                if (mainConfig != null && mainConfig.has(configName)
                        && mainConfig.get(configName).isJsonObject()) {
                    JsonObject section = mainConfig.getAsJsonObject(configName);
                    configCache.put(configName, section);
                    return section;
                }
                // Fallback: attempt to read configName.json directly (handles split-config files
                // whose section was not present in the cached MAIN_CONFIG, e.g. chat.json when
                // the merged config cache is stale or split configs were recently enabled).
                File splitFile = ResourceUtil.getConfigFile(configName + ".json");
                if (splitFile.exists()) {
                    try (FileReader splitReader = new FileReader(splitFile, StandardCharsets.UTF_8)) {
                        JsonObject fileObj = parseJsonWithComments(splitReader).getAsJsonObject();
                        // Split files wrap their content under the section key: {"chat": {...}}
                        if (fileObj.has(configName) && fileObj.get(configName).isJsonObject()) {
                            JsonObject section = fileObj.getAsJsonObject(configName);
                            configCache.put(configName, section);
                            // NOTE: plain LOGGER, not NeoLog — see recursion-risk comment above.
                            LOGGER.debug("Config section '{}' loaded directly from {}.json (fallback)", configName, configName);
                            return section;
                        }
                        // File exists but uses a flat layout – return the whole object
                        configCache.put(configName, fileObj);
                        LOGGER.debug("Config '{}' loaded directly from {}.json (flat layout fallback)", configName, configName);
                        return fileObj;
                    } catch (IOException | com.google.gson.JsonParseException fallbackEx) {
                        LOGGER.warn("Could not read fallback config file {}.json: {}", configName, fallbackEx.getMessage());
                    }
                }
                // Section missing – return empty (do not cache so it retries after reload)
                LOGGER.debug("Config section '{}' not found in main config or {}.json, returning empty object", configName, configName);
                return new JsonObject();
            }

            File file = ResourceUtil.getConfigFile(configName);
            // NOTE: plain LOGGER, not NeoLog — see comment above on the recursion risk.
            LOGGER.debug("Loading config file {} from disk (cache miss)", configName);
            reader = new FileReader(file, StandardCharsets.UTF_8);
            JsonObject obj = parseJsonWithComments(reader).getAsJsonObject();
            configCache.put(configName, obj);
            return obj;
        } catch (IOException | com.google.gson.JsonParseException e) {
            // A malformed config file (stray comma, unterminated brace/object from a manual
            // edit, a crash mid-write, etc.) must never take the whole server down with it —
            // this used to only catch IOException, but Gson's parser throws JsonSyntaxException
            // (a JsonParseException, not an IOException) for malformed JSON, which went uncaught
            // all the way up through whichever caller happened to read this config, crashing the
            // server on the very next server tick for any config read every tick (e.g. the
            // scoreboard module-enabled check). Falling back to an empty config just quietly
            // disables whatever that config drives instead.
            LOGGER.error("Failed to parse config file {} — it may contain invalid JSON (check the exact " +
                "location in the error below). Falling back to defaults for it until this is fixed; a " +
                "'.backup' copy from the last successful config upgrade may exist next to it. {}",
                configName, e.getMessage());
            JsonObject empty = new JsonObject();
            configCache.put(configName, empty);
            return empty;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) {
                    NeoLog.debug(LOGGER, com.zerog.neoessentials.logging.LogCategory.CONFIG,
                        "Failed to close reader for config file {}", configName, e);
                }
            }
            lock.readLock().unlock();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    // private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Parse a JSON file in "lenient" mode, which allows {@code //} single-line comments and
     * {@code /* ... *\/} block comments in the file.  Comments are ignored by the parser; the
     * resulting {@link JsonObject} contains only the actual data keys.
     *
     * <p>This is the sole entry-point for reading config files from disk or from JAR resources
     * so that admins can annotate their config files with comments without breaking the loader.
     *
     * <p><strong>Note:</strong> When a config is written back (e.g. after a version merge) it is
     * serialised through Gson's pretty-printer which does <em>not</em> emit comments.  Comments
     * are therefore intentionally only present in the default/template copy (first install or
     * JAR resource), not in files that have been through a round-trip write.
     */
    private static JsonObject parseJsonWithComments(java.io.Reader reader) throws IOException {
        com.google.gson.stream.JsonReader jsonReader = new com.google.gson.stream.JsonReader(reader);
        jsonReader.setLenient(true);
        return JsonParser.parseReader(jsonReader).getAsJsonObject();
    }

    // Thread-safe singleton
    private static class SingletonHolder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    // Thread-safe configuration cache
    private final ConcurrentHashMap<String, JsonObject> configCache = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    // private volatile boolean loaded = false;

    // Configuration file names
    public static final String MAIN_CONFIG = "config.json";
    public static final String ECONOMY_CONFIG = "economy.json";
    public static final String PERMISSIONS_CONFIG = "permissions.json";
    public static final String KITS_CONFIG = "kits.json";
    public static final String DISCORD_AUTH_CONFIG = "discord_auth.json";
    public static final String TABLIST_CONFIG = "tablist.json";
    public static final String ANIMATIONS_CONFIG = "animations.json";
    public static final String SCOREBOARD_CONFIG = "scoreboard.json";
    public static final String LEADERBOARD_CONFIG = "leaderboard.json";
    public static final String VOTIFIER_CONFIG = "votifier.json";
    public static final String CRATES_CONFIG = "crates.json";
    public static final String DISCORD_ROLE_SYNC_CONFIG = "discordrolesync.json";

    // Config version tracking - increment when structure changes
    private static final String CONFIG_VERSION_KEY = "_configVersion";

    // Expected versions for each config file (must match the version in JAR resources)
    private static final java.util.Map<String, Integer> EXPECTED_CONFIG_VERSIONS = new java.util.HashMap<>() {{
        put(MAIN_CONFIG, 53);          // v53 — added per-event-type nested objects
                                        //       (join/leave/mute/afk/advancement) under
                                        //       discordEmbedTemplate, each with its own
                                        //       enabled/description/color/showTimestamp — SDLink's
                                        //       explicit-channel-override sends for those events
                                        //       now get a styled embed instead of plain text.
        // v52 — added "webhookUrl" alongside "channelId" in every
                                        //       chat.channels.*.discord block and
                                        //       discordEventChannels.* entry — the new no-bot-
                                        //       required generic webhook relay adapter reads
                                        //       these; channelId-based bridge mods ignore them.
        // v51 — added "discordEventChannels": per-event-type
                                        //       Discord channel routing (join/leave/mute/afk/
                                        //       advancement/privateMessage) for the chat-bridge
                                        //       adapters (SDLink/Mc2Discord/DCIntegration) —
                                        //       previously only chat messages could be routed to
                                        //       a specific channel via chat.channels.*.discord.
        // v50 — renamed hologram.refreshInterval to
                                        //       pollIntervalTicks for clarity (it was being
                                        //       confused with each hologram's own, unrelated,
                                        //       seconds-based refreshInterval); see
                                        //       migrateHologramPollInterval()
        // v49 — added hologram.refreshInterval/animationInterval
                                        // v48 — added general.serverName
                                        //   (RTP chunk-load lag/crash fix)
        // v46 — added randomTeleportSettings.mode/biomeSearchRadius/
                                        //   biomeSearchStep/biomeMenuItems (RTP biome-select GUI)
        // v45 — added "modules.scoreboardEnabled" and
                                       //        "commands.scoreboard": the new sidebar
                                       //        scoreboard system, same restart-required
                                       //        module/command toggle pattern as tablist.
        // v44 — added "shop.pricing": dynamic ChestShop/NPC-shop
                                       //        pricing (supply/demand, time discounts, bulk
                                       //        tiers) was fully implemented and reachable in
                                       //        code (PricingEngine) but had no config section
                                       //        at all — nothing to enable/configure it with.
        // v43 — moved "localization" out from under "chat" to a proper
                                       //        top-level section. getServerLanguage()/setServerLanguage()/
                                       //        isPreserveCustomTranslationsEnabled() have always read/written
                                       //        it as top-level (matching ConfigSplitter's FILE_SECTIONS_MAP),
                                       //        so chat.localization in the template was dead documentation
                                       //        that /language never actually touched, and it made split-config
                                       //        migration permanently report "MISSING SECTION 'localization'".
                                       // v42 — added storage.mysql.autoDownloadDriver: the mysql-connector-j
                                       //        driver is no longer bundled in the jar (see
                                       //        MySqlDriverProvisioner, same split-package module
                                       //        conflict as sqlite-jdbc — seen in the wild against
                                       //        GriefLogger) — this controls whether it's allowed to
                                       //        auto-download from Maven Central on first actual use,
                                       //        default true.
        // v41 — added storage.sqlite.autoDownloadDriver: the sqlite-jdbc
                                       //        driver is no longer bundled in the jar (see
                                       //        SqliteDriverProvisioner) — this controls whether it's
                                       //        allowed to auto-download from Maven Central on first
                                       //        actual use, default true.
        // v40 — replaced logging.enableDebugLogging (single global
                                       //        toggle) with logging.categories.<name>.{normal,debug}
                                       //        (per-subsystem toggles); see migrateLoggingCategories()
                                       //        for the one-time migration of an existing true value.
        // v39 — chat.formatTemplates.templates.* still referenced
                                       //        {neoessentials_username}/{neoessentials_name} (always
                                       //        the real name) instead of {neoessentials_displayname}
                                       //        (nickname-aware), so /nick never showed up in chat for
                                       //        anyone using a formatTemplates preset. Same class of bug
                                       //        as the v38-era chat-format fix, different config block —
                                       //        this bump also value-patches any already-deployed
                                       //        chat-format/formatTemplates entries still holding the
                                       //        untouched old default strings (see
                                       //        patchLegacyNicknameChatDefaults()), since the version was
                                       //        never bumped when the chat-format defaults were fixed, so
                                       //        that fix silently never reached existing installs.
        // v38 — added webDashboard.customProfileUrlTemplate: point the
                                       //        in-chat "view profile" link at a server's own site
                                       //        instead of either NeoEssentials dashboard
        // v37 — added chat.channels.team (teamBased:true example channel)
                                       //        for FTB Teams/similar mod team chat, plus the
                                       //        "teamBased" per-channel flag it demonstrates
        // v36 — added top-level discordEmbedTemplate section: customizes
                                       //        the rich Discord embed built for chat.channels.*.discord
                                       //        channel-routed messages (currently SDLink only)
        // v35 — removed moderation.generalSettings.seeVanishedPermission;
                                       //        vanish now uses dedicated neoessentials.vanish.
                                       //        interact/.build/.hurt permission nodes instead of
                                       //        reusing the "can see vanished players" node
                                       // v34 — added permissions.wildcardsGrantEconomyModifierPermissions
                                       //        (default false): a group/user wildcard like
                                       //        neoessentials.* no longer implies the graded
                                       //        economy-modifier nodes unless scoped to their
                                       //        own tier family
                                       // v33 — added permissions.opsBypassEconomyModifierPermissions
                                       //        (default false): decouples OP command-permission
                                       //        bypass from graded economy-modifier permission nodes
                                       // v32 — removed dead weight: legacy tablist{} section (tablist.json
                                       //        always wins), duplicate/shadowed economy.currencySymbol+
                                       //        startingBalance (economy.json is authoritative), unread
                                       //        afk tablist-indicator keys, and webDashboard.serviceAccount
                                       //        (superseded by the /dashboard pair handshake)
        put(ECONOMY_CONFIG, 3);        // v3  — removed _configVersion_comment
        put(PERMISSIONS_CONFIG, 7);    // v7  — removed _configVersion_comment
        put(KITS_CONFIG, 2);           // v2  — removed _configVersion_comment
        put(DISCORD_AUTH_CONFIG, 11);  // v11 — fixed dangling trailing comma after permissionMappings'
                                       //        closing brace (malformed JSON, broke JAR-template
                                       //        reload on every boot)
                                       // v10 — removed permissionMappings_common_examples, a dead
                                       //       stub self-labeled "not loaded by the mod"
                                       // v9  — removed OAuth2 (no direct Discord API calls); dashboard login now sources identity from a Discord companion mod (SDLink/Mc2Discord)
        put(TABLIST_CONFIG, 5);        // v5  — migrated to // comment style
        put(ANIMATIONS_CONFIG, 2);     // v2  — migrated to // comment style
        put(SCOREBOARD_CONFIG, 1);     // v1  — initial sidebar scoreboard config
        put(LEADERBOARD_CONFIG, 3);    // v3  — add entryFormat/headerFormat/icon board styling fields
        put(VOTIFIER_CONFIG, 1);       // v1  — initial Votifier vote-listener config
        put(CRATES_CONFIG, 1);         // v1  — initial Crates config
        put(DISCORD_ROLE_SYNC_CONFIG, 1); // v1 — initial Discord role sync config
    }};

    /**
     * Returns the configured server language code (e.g. "fr_fr", "de_de").
     * Reads from localization.language in config.json.
     * Defaults to "en_us" if not set.
     */
    public static String getServerLanguage() {
        try {
            JsonObject config = getInstance().getConfig(MAIN_CONFIG);
            if (config.has("localization")) {
                JsonObject loc = config.getAsJsonObject("localization");
                if (loc.has("language")) {
                    String val = loc.get("language").getAsString();
                    if (val != null && !val.trim().isEmpty()) return val.trim().toLowerCase();
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for localization.language, using default en_us", e);
        }
        return "en_us";
    }

    /**
     * Sets the active server language (localization.language) and persists it to config.json.
     * Does not reload translations — call {@link com.zerog.neoessentials.util.MessageUtil#reloadTranslations()}
     * afterward to apply the change immediately.
     */
    public static void setServerLanguage(String languageCode) {
        ConfigManager instance = getInstance();
        JsonObject config = instance.getConfig(MAIN_CONFIG);
        JsonObject loc = config.has("localization") && config.get("localization").isJsonObject()
            ? config.getAsJsonObject("localization")
            : new JsonObject();
        loc.addProperty("language", languageCode.trim().toLowerCase());
        config.add("localization", loc);
        instance.saveConfig(MAIN_CONFIG, config);
    }

    /**
     * Returns whether custom-edited language files should be left completely untouched.
     * Reads from localization.preserveCustomTranslations in config.json. Defaults to false.
     * When true, the mod will not merge in new keys or auto-fix legacy placeholders on startup.
     */
    public static boolean isPreserveCustomTranslationsEnabled() {
        try {
            JsonObject config = getInstance().getConfig(MAIN_CONFIG);
            if (config.has("localization")) {
                JsonObject loc = config.getAsJsonObject("localization");
                if (loc.has("preserveCustomTranslations")) {
                    return loc.get("preserveCustomTranslations").getAsBoolean();
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for localization.preserveCustomTranslations, using default false", e);
        }
        return false;
    }

    /**
     * Returns true if the web dashboard is enabled — both {@code webDashboard.enabled}
     * (the dashboard's own detailed setting) AND {@code modules.webDashboardEnabled} (the
     * top-level module kill-switch) must be true. The two used to be entirely separate
     * keys, with {@code modules.webDashboardEnabled} silently never consulted anywhere —
     * checking both here means the module toggle actually does something without
     * removing the more specific {@code webDashboard.enabled} setting.
     * Defaults to true if neither is set.
     */
    public static boolean isWebDashboardEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);

        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("webDashboardEnabled") && !modules.get("webDashboardEnabled").getAsBoolean()) {
                return false;
            }
        }

        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("enabled")) {
                return dashboard.get("enabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the dashboard's own bundled static-file UI ("/") should be served
     * (webDashboard.mode). "internal" (default) and "both" serve it; "external" does not —
     * only the REST API under /api/* is registered, for setups where a separately-hosted
     * dashboard app (e.g. the Laravel NeoEssentials-Dashboard) is the only UI ever used.
     */
    public static boolean isDashboardInternalUiEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("mode")) {
                return !"external".equalsIgnoreCase(dashboard.get("mode").getAsString());
            }
        }
        return true;
    }

    /**
     * Returns true if the web dashboard should start automatically on server boot
     * (webDashboard.autoStart). If false, use /dashboard start to start it manually.
     * Defaults to true if not set.
     */
    public static boolean isWebDashboardAutoStartEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("autoStart")) {
                return dashboard.get("autoStart").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Base URL of the external dashboard this server is paired with (e.g. {@code https://dash.example.com}),
     * set automatically by {@code /dashboard pair} — never hand-typed into config.json. Used as the
     * target for the outbound user-sync webhook. Empty/absent means not paired yet.
     */
    public static String getExternalDashboardUrl() {
        return getExternalDashboardField("url");
    }

    public static void setExternalDashboardUrl(String url) {
        setExternalDashboardField("url", url);
    }

    /**
     * Bearer token the paired external dashboard minted for this mod during pairing, presented as
     * {@code Authorization: Bearer <token>} on the outbound user-sync webhook — replaces the old
     * HMAC-signature scheme now that both directions of this integration use a token the
     * *receiving* side generated for the *calling* side.
     */
    public static String getExternalDashboardToken() {
        return getExternalDashboardField("token");
    }

    public static void setExternalDashboardToken(String token) {
        setExternalDashboardField("token", token);
    }

    /**
     * The {@code keyId} (not the full secret) of the {@link com.zerog.neoessentials.webdashboard.security.ApiKeyManager}
     * key minted during pairing for the dashboard's own use — stored so {@code /dashboard unpair}
     * can cleanly revoke exactly that key instead of leaving it live forever.
     */
    public static String getExternalDashboardKeyId() {
        return getExternalDashboardField("keyId");
    }

    public static void setExternalDashboardKeyId(String keyId) {
        setExternalDashboardField("keyId", keyId);
    }

    /** Clears all paired-dashboard state (used by {@code /dashboard unpair}). */
    public static void clearExternalDashboard() {
        ConfigManager instance = getInstance();
        JsonObject config = instance.getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            dashboard.remove("externalDashboard");
            config.add("webDashboard", dashboard);
            instance.saveConfig(MAIN_CONFIG, config);
        }
    }

    /** Fields whose stored value is a real credential, not just an identifier/URL. */
    private static final java.util.Set<String> ENCRYPTED_EXTERNAL_DASHBOARD_FIELDS = java.util.Set.of("token");

    private static String getExternalDashboardField(String field) {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("externalDashboard")) {
                JsonObject external = dashboard.getAsJsonObject("externalDashboard");
                if (external.has(field)) {
                    String stored = external.get(field).getAsString();
                    if (!ENCRYPTED_EXTERNAL_DASHBOARD_FIELDS.contains(field)) return stored;

                    String decrypted = com.zerog.neoessentials.webdashboard.security.ConfigSecretCipher.decrypt(stored);
                    if (decrypted != null) return decrypted;

                    // Leftover plaintext from before encryption was added — use it once, then
                    // transparently migrate it to an encrypted value so it isn't re-read as
                    // plaintext next time.
                    setExternalDashboardField(field, stored);
                    return stored;
                }
            }
        }
        return "";
    }

    private static void setExternalDashboardField(String field, String value) {
        ConfigManager instance = getInstance();
        JsonObject config = instance.getConfig(MAIN_CONFIG);
        JsonObject dashboard = config.has("webDashboard") ? config.getAsJsonObject("webDashboard") : new JsonObject();
        JsonObject external = dashboard.has("externalDashboard") ? dashboard.getAsJsonObject("externalDashboard") : new JsonObject();
        String toStore = ENCRYPTED_EXTERNAL_DASHBOARD_FIELDS.contains(field)
            ? com.zerog.neoessentials.webdashboard.security.ConfigSecretCipher.encrypt(value)
            : value;
        external.addProperty(field, toStore);
        dashboard.add("externalDashboard", external);
        config.add("webDashboard", dashboard);
        instance.saveConfig(MAIN_CONFIG, config);
    }

    /**
     * Whether the permission-driven dashboard role-sync task ({@code webDashboard.roleSync.enabled})
     * is turned on. Off by default — this reconciles a dashboard account's role against the
     * player's actual in-game permission/group, so it should be an explicit opt-in rather than
     * something that silently starts changing dashboard roles on upgrade.
     */
    public static boolean isRoleSyncEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("roleSync")) {
                JsonObject roleSync = dashboard.getAsJsonObject("roleSync");
                if (roleSync.has("enabled")) return roleSync.get("enabled").getAsBoolean();
            }
        }
        return false;
    }

    /** How often ({@code webDashboard.roleSync.intervalSeconds}) the periodic reconciliation sweep runs. Default 300s. */
    public static int getRoleSyncIntervalSeconds() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("roleSync")) {
                JsonObject roleSync = dashboard.getAsJsonObject("roleSync");
                if (roleSync.has("intervalSeconds")) return roleSync.get("intervalSeconds").getAsInt();
            }
        }
        return 300;
    }

    /** Permission node ({@code webDashboard.roleSync.adminPermission}) that grants ADMIN dashboard role. Empty = not checked. */
    public static String getRoleSyncAdminPermission() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("roleSync")) {
                JsonObject roleSync = dashboard.getAsJsonObject("roleSync");
                if (roleSync.has("adminPermission")) return roleSync.get("adminPermission").getAsString();
            }
        }
        return "neoessentials.admin.dashboard";
    }

    /** Permission group name ({@code webDashboard.roleSync.adminGroup}) that grants ADMIN dashboard role. Empty = not checked. */
    public static String getRoleSyncAdminGroup() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("roleSync")) {
                JsonObject roleSync = dashboard.getAsJsonObject("roleSync");
                if (roleSync.has("adminGroup")) return roleSync.get("adminGroup").getAsString();
            }
        }
        return "admin";
    }

    /**
     * Returns the web dashboard HTTP port (webDashboard.port). Defaults to 8080.
     */
    public int getWebDashboardPort() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("port")) {
                return dashboard.get("port").getAsInt();
            }
        }
        return 8080;
    }

    /**
     * Returns the web dashboard WebSocket port (webDashboard.websocketPort). Defaults to 8081.
     */
    public int getWebDashboardWebSocketPort() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("websocketPort")) {
                return dashboard.get("websocketPort").getAsInt();
            }
        }
        return 8081;
    }

    /**
     * Returns the web dashboard bind address (webDashboard.bindAddress). Defaults to "0.0.0.0".
     */
    public String getWebDashboardBindAddress() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("bindAddress")) {
                String val = dashboard.get("bindAddress").getAsString();
                if (val != null && !val.trim().isEmpty()) return val.trim();
            }
        }
        return "0.0.0.0";
    }

    /**
     * Returns a friendly URL for accessing the web dashboard, derived from webDashboard.port.
     * Uses "localhost" since the actual reachable address depends on the server's public IP.
     */
    public String getWebDashboardUrl() {
        return "http://localhost:" + getWebDashboardPort();
    }

    /**
     * Admin-configured public-facing base URL for the mod's OWN bundled dashboard
     * (webDashboard.publicUrl) — e.g. {@code http://your.server.ip:8080}. Unlike
     * {@link #getWebDashboardUrl()}, this is meant to actually be reachable by players'
     * browsers, not just the server operator's own machine; there is no way to auto-detect
     * it reliably (NAT/port-forwarding/reverse proxies), so it must be hand-set. Empty/absent
     * if not configured.
     */
    public static String getWebDashboardPublicUrl() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("publicUrl")) {
                String val = dashboard.get("publicUrl").getAsString();
                if (val != null && !val.trim().isEmpty()) return val.trim();
            }
        }
        return "";
    }

    /**
     * Admin-set URL template pointing at a server's OWN external site instead of either
     * NeoEssentials dashboard (webDashboard.customProfileUrlTemplate) — e.g.
     * {@code https://myserver.com/players/{player}}. Supports {@code {player}} (URL-encoded
     * username) and {@code {uuid}} placeholders. Empty/absent if not configured.
     */
    public static String getCustomProfileUrlTemplate() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("customProfileUrlTemplate")) {
                String val = dashboard.get("customProfileUrlTemplate").getAsString();
                if (val != null && !val.trim().isEmpty()) return val.trim();
            }
        }
        return "";
    }

    /** @see #getPlayerProfileUrl(String, java.util.UUID) */
    public static String getPlayerProfileUrl(String username) {
        return getPlayerProfileUrl(username, null);
    }

    /**
     * Builds a public, no-login "view profile" URL for {@code username}, or {@code null} if
     * nothing is configured. Resolution order:
     * <ol>
     *   <li>{@link #getCustomProfileUrlTemplate()} — a server's own site, if set. Full control
     *       over the URL shape via {@code {player}}/{@code {uuid}} placeholders.</li>
     *   <li>The paired external dashboard's URL ({@link #getExternalDashboardUrl()}, set via
     *       {@code /dashboard pair}) — reachable by definition once paired.</li>
     *   <li>The admin-set {@link #getWebDashboardPublicUrl()}, for the mod's own bundled
     *       ("internal"/"both") UI — both build the same {@code /lookup?player=<name>} path.</li>
     * </ol>
     */
    public static String getPlayerProfileUrl(String username, java.util.UUID uuid) {
        String template = getCustomProfileUrlTemplate();
        if (!template.isEmpty()) {
            String encodedName = java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
            return template
                .replace("{player}", encodedName)
                .replace("{uuid}", uuid != null ? uuid.toString() : "");
        }

        String base = getExternalDashboardUrl();
        if (base == null || base.isEmpty()) {
            base = getWebDashboardPublicUrl();
        }
        if (base == null || base.isEmpty()) return null;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String encodedName = java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
        return base + "/lookup?player=" + encodedName;
    }

    // ── Storage backend (storage.*) ──────────────────────────────────────────

    /** Returns storage.type — "json" (default), "yaml", "sqlite", or "mysql". */
    public String getStorageType() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("storage")) {
            JsonObject storage = config.getAsJsonObject("storage");
            if (storage.has("type")) return storage.get("type").getAsString().toLowerCase().trim();
        }
        return "json";
    }

    /** Whether existing JSON data should be imported automatically on first switch to a new backend. */
    public boolean isStorageAutoMigrateEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("storage")) {
            JsonObject storage = config.getAsJsonObject("storage");
            if (storage.has("autoMigrate")) return storage.get("autoMigrate").getAsBoolean();
        }
        return true;
    }

    /** storage.sqlite.file — relative to neoessentials/store/. Defaults to "data.db". */
    public String getSqliteFile() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("storage")) {
            JsonObject storage = config.getAsJsonObject("storage");
            if (storage.has("sqlite")) {
                JsonObject sqlite = storage.getAsJsonObject("sqlite");
                if (sqlite.has("file")) return sqlite.get("file").getAsString();
            }
        }
        return "data.db";
    }

    /**
     * storage.sqlite.autoDownloadDriver — whether {@link com.zerog.neoessentials.storage.SqliteDriverProvisioner}
     * is allowed to download the sqlite-jdbc driver from Maven Central on first use (SQLite
     * storage backend, or a one-time legacy Auction House migration). Defaults to true. If
     * disabled, the driver must be placed manually at
     * {@code neoessentials/libraries/sqlite-jdbc-<version>.jar} instead.
     */
    public boolean isSqliteAutoDownloadDriverEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("storage")) {
            JsonObject storage = config.getAsJsonObject("storage");
            if (storage.has("sqlite")) {
                JsonObject sqlite = storage.getAsJsonObject("sqlite");
                if (sqlite.has("autoDownloadDriver")) return sqlite.get("autoDownloadDriver").getAsBoolean();
            }
        }
        return true;
    }

    private JsonObject getMysqlConfig() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("storage")) {
            JsonObject storage = config.getAsJsonObject("storage");
            if (storage.has("mysql")) return storage.getAsJsonObject("mysql");
        }
        return new JsonObject();
    }

    public String getMysqlHost() {
        JsonObject mysql = getMysqlConfig();
        return mysql.has("host") ? mysql.get("host").getAsString() : "localhost";
    }

    public int getMysqlPort() {
        JsonObject mysql = getMysqlConfig();
        return mysql.has("port") ? mysql.get("port").getAsInt() : 3306;
    }

    public String getMysqlDatabase() {
        JsonObject mysql = getMysqlConfig();
        return mysql.has("database") ? mysql.get("database").getAsString() : "neoessentials";
    }

    public String getMysqlUsername() {
        JsonObject mysql = getMysqlConfig();
        return mysql.has("username") ? mysql.get("username").getAsString() : "neoessentials";
    }

    public String getMysqlPassword() {
        JsonObject mysql = getMysqlConfig();
        return mysql.has("password") ? mysql.get("password").getAsString() : "";
    }

    public boolean isMysqlUseSSL() {
        JsonObject mysql = getMysqlConfig();
        return mysql.has("useSSL") && mysql.get("useSSL").getAsBoolean();
    }

    public int getMysqlPoolSize() {
        JsonObject mysql = getMysqlConfig();
        return mysql.has("poolSize") ? mysql.get("poolSize").getAsInt() : 10;
    }

    /**
     * storage.mysql.autoDownloadDriver — whether {@link com.zerog.neoessentials.storage.MySqlDriverProvisioner}
     * is allowed to download the mysql-connector-j driver from Maven Central on first use (MySQL
     * storage backend configured). Defaults to true. If disabled, the driver must be placed
     * manually at {@code neoessentials/libraries/mysql-connector-j-<version>.jar} instead.
     */
    public boolean isMysqlAutoDownloadDriverEnabled() {
        JsonObject mysql = getMysqlConfig();
        return !mysql.has("autoDownloadDriver") || mysql.get("autoDownloadDriver").getAsBoolean();
    }

    /**
     * Returns true if per-IP rate limiting is enabled for the dashboard API
     * (webDashboard.securitySettings.enableRateLimiting). Defaults to true.
     */
    public boolean isDashboardRateLimitingEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("securitySettings")) {
                JsonObject security = dashboard.getAsJsonObject("securitySettings");
                if (security.has("enableRateLimiting")) {
                    return security.get("enableRateLimiting").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns the maximum dashboard API requests per IP per minute
     * (webDashboard.securitySettings.maxRequestsPerMinute). Defaults to 60.
     */
    public int getDashboardMaxRequestsPerMinute() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("securitySettings")) {
                JsonObject security = dashboard.getAsJsonObject("securitySettings");
                if (security.has("maxRequestsPerMinute")) {
                    return security.get("maxRequestsPerMinute").getAsInt();
                }
            }
        }
        return 60;
    }

    /**
     * Returns true if Bearer token authentication is required on dashboard API endpoints
     * (webDashboard.securitySettings.requireAuthentication). Defaults to true.
     */
    public boolean isDashboardAuthRequired() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("securitySettings")) {
                JsonObject security = dashboard.getAsJsonObject("securitySettings");
                if (security.has("requireAuthentication")) {
                    return security.get("requireAuthentication").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns true if the public (no-login) player moderation lookup API is enabled
     * (webDashboard.securitySettings.publicModerationLookupEnabled). Defaults to true —
     * matches ban-management plugins' "anyone can look up a punishment" transparency page.
     * Never exposes IP bans/IP mutes regardless of this setting.
     */
    public boolean isPublicModerationLookupEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("webDashboard")) {
            JsonObject dashboard = config.getAsJsonObject("webDashboard");
            if (dashboard.has("securitySettings")) {
                JsonObject security = dashboard.getAsJsonObject("securitySettings");
                if (security.has("publicModerationLookupEnabled")) {
                    return security.get("publicModerationLookupEnabled").getAsBoolean();
                }
            }
        }
        return true;
    }

    private ConfigManager() {
        // On first construction, ensure all required config files exist
        ensureDefaultConfigs();
    }

    /**
     * Ensure all required config files exist in the config directory, copying from JAR if missing.
     * If split configs are enabled, config.json is skipped for all operations except migration/backup.
     * Internal permissions.json is not generated if external permissions are enabled.
     */
    private void ensureDefaultConfigs() {
        String[] requiredConfigs = new String[] {
            MAIN_CONFIG, ECONOMY_CONFIG, PERMISSIONS_CONFIG, KITS_CONFIG, DISCORD_AUTH_CONFIG, TABLIST_CONFIG, ANIMATIONS_CONFIG, SCOREBOARD_CONFIG, LEADERBOARD_CONFIG, VOTIFIER_CONFIG, CRATES_CONFIG, DISCORD_ROLE_SYNC_CONFIG
        };

        // Check if split configs are enabled
        boolean splitConfigsEnabled = ConfigSplitter.isSplittingEnabled();
        boolean externalPermsEnabled = false;
        try {
            externalPermsEnabled = isExternalPermissionsEnabled();
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CONFIG, "Could not determine externalPermissionsEnabled during config bootstrap, assuming false", e);
        }

        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Bootstrapping default configs: splitConfigsEnabled={}, externalPermsEnabled={}", splitConfigsEnabled, externalPermsEnabled);

        if (splitConfigsEnabled) {
            // Always ensure split configs are up to date
            NeoLog.info(LOGGER, LogCategory.CONFIG, "Split configs enabled - ensuring all split config files are up to date");
            ConfigSplitter.ensureSplitConfigsUpToDate();

            // Only check other standalone configs (economy, permissions, kits, discord_auth)
            for (String configName : requiredConfigs) {
                if (configName.equals(MAIN_CONFIG)) {
                    continue; // Skip config.json when using split configs
                }
                if (configName.equals(PERMISSIONS_CONFIG) && externalPermsEnabled) {
                    // Skip internal permissions.json if external permissions are enabled
                    continue;
                }
                File configFile = ResourceUtil.getConfigFile(configName);
                if (!configFile.exists()) {
                    copyDefaultConfig(configName, configFile);
                } else {
                    checkAndUpdateConfigVersion(configName, configFile);
                }
            }
        } else {
            // Normal monolithic config mode
            for (String configName : requiredConfigs) {
                if (configName.equals(PERMISSIONS_CONFIG) && externalPermsEnabled) {
                    // Skip internal permissions.json if external permissions are enabled
                    continue;
                }
                File configFile = ResourceUtil.getConfigFile(configName);
                if (!configFile.exists()) {
                    copyDefaultConfig(configName, configFile);
                } else {
                    checkAndUpdateConfigVersion(configName, configFile);
                }
            }
        }
    }

    /**
     * Check if a config file needs updating based on version mismatch.
     * <p>
     * Strategy:
     *  - If the on-disk version is OLDER than expected → merge new/changed keys from the JAR
     *    template into the user's file (preserve all existing values) then bump _configVersion.
     *    A backup is still created before touching the file.
     *  - If the on-disk version is NEWER than expected → warn only, do not touch.
     *  - If equal → no-op.
     * <p>
     * This prevents blowing away user-set values (role IDs, client secrets, custom settings)
     * every time the config gains a new field.
     */
    private void checkAndUpdateConfigVersion(String configName, File configFile) {
        Integer expectedVersion = EXPECTED_CONFIG_VERSIONS.get(configName);
        if (expectedVersion == null) {
            return; // No version tracking for this config
        }

        try {
            JsonObject onDisk;
            // Read-and-close BEFORE any merge/rename below — Windows (unlike POSIX) refuses to
            // rename a new file over configFile while this process still holds it open for
            // reading (AccessDeniedException), so the reader must not still be open across the
            // Files.move() further down.
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
                onDisk = parseJsonWithComments(reader).getAsJsonObject();
            }

            int currentVersion = 0;
            if (onDisk.has(CONFIG_VERSION_KEY)) {
                currentVersion = onDisk.get(CONFIG_VERSION_KEY).getAsInt();
            }
            NeoLog.debug(LOGGER, LogCategory.CONFIG, "Config file {} on-disk version {}, expected version {}", configName, currentVersion, expectedVersion);

            if (currentVersion < expectedVersion) {
                LOGGER.warn("Config file {} is outdated (version {} < {}). Merging new keys from JAR template (user values preserved)...",
                    configName, currentVersion, expectedVersion);

                // Load JAR template (may contain // comments — use lenient reader)
                JsonObject jarTemplate = null;
                try (InputStream in = ResourceUtil.getJarConfigResource(configName)) {
                    if (in != null) {
                        jarTemplate = parseJsonWithComments(
                            new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    }
                } catch (Exception e) {
                    LOGGER.error("Could not load JAR template for {}: {}", configName, e.getMessage());
                }

                if (jarTemplate == null) {
                    LOGGER.warn("JAR template not found for {}. Skipping update.", configName);
                    return;
                }

                // Create backup before modifying
                createConfigBackup(configFile, currentVersion);

                // Migrate the old single-toggle logging.enableDebugLogging into the new
                // per-category logging.categories.* structure, BEFORE mergeNewKeys fills
                // categories in with plain defaults (migration needs to see the pre-merge
                // "categories absent" state to know it hasn't run yet).
                boolean changed = false;
                if (MAIN_CONFIG.equals(configName) && migrateLoggingCategories(onDisk)) {
                    changed = true;
                    NeoLog.info(LOGGER, LogCategory.CONFIG, "Config file {}: migrated logging.enableDebugLogging into per-category logging.categories.*.", configName);
                }

                // Carry an admin's existing hologram.refreshInterval value over to the renamed
                // hologram.pollIntervalTicks key, BEFORE mergeNewKeys would otherwise fill
                // pollIntervalTicks in with the plain default and silently discard it.
                if (MAIN_CONFIG.equals(configName) && migrateHologramPollInterval(onDisk)) {
                    changed = true;
                    NeoLog.info(LOGGER, LogCategory.CONFIG, "Config file {}: migrated hologram.refreshInterval to hologram.pollIntervalTicks.", configName);
                }

                // Deep-merge: add keys that exist in JAR but are missing on disk.
                // Never overwrite existing user values.
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Merging missing keys from JAR template into {} (existing values preserved)", configName);
                changed |= mergeNewKeys(jarTemplate, onDisk);

                // mergeNewKeys() only adds a key that's missing entirely on disk — leaderboard.
                // boards is a JSON array that already exists on every upgraded install, so a new
                // default board shipped in a later version (e.g. shop_sales) would otherwise
                // never reach existing servers even though the version bump itself succeeds.
                if (LEADERBOARD_CONFIG.equals(configName) && mergeNewLeaderboardBoards(jarTemplate, onDisk)) {
                    changed = true;
                    NeoLog.info(LOGGER, LogCategory.CONFIG, "Config file {}: added new default leaderboard board(s) from the JAR template.", configName);
                }

                // Strip legacy comment keys (xxx_comment, _doc_*, _step*, etc.)
                // from the user's file as part of this upgrade.
                boolean stripped = stripLegacyCommentKeys(onDisk);
                if (stripped) {
                    changed = true;
                    NeoLog.info(LOGGER, LogCategory.CONFIG, "Config file {}: removed legacy _comment/_doc keys (comment migration).", configName);
                }

                // Value-level fixes for known-bad defaults that mergeNewKeys() can never touch
                // (the key already exists on disk, mergeNewKeys only adds missing keys).
                if (MAIN_CONFIG.equals(configName)) {
                    if (patchLegacyNicknameChatDefaults(onDisk)) {
                        changed = true;
                        NeoLog.info(LOGGER, LogCategory.CONFIG, "Config file {}: upgraded untouched chat-format defaults to show nicknames ({{neoessentials_username}}/{{neoessentials_name}} -> {{neoessentials_displayname}}).", configName);
                    }
                }

                // Always bump the version so we don't re-run this on next start
                onDisk.addProperty(CONFIG_VERSION_KEY, expectedVersion);

                // Write merged result back — atomic temp-file + rename, same pattern as
                // JsonFileDataStore, so a crash/disk-full mid-write can't leave the config
                // truncated/invalid.
                java.io.File tmpMerged = new java.io.File(configFile.getParentFile(), configFile.getName() + ".tmp-" + System.currentTimeMillis());
                try (java.io.FileWriter writer = new java.io.FileWriter(tmpMerged, StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(onDisk, writer);
                }
                java.nio.file.Files.move(tmpMerged.toPath(), configFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

                configCache.remove(configName);
                NeoLog.info(LOGGER, LogCategory.CONFIG, "Config file {} merged to version {} ({} new key(s) added).",
                    configName, expectedVersion, changed ? "some" : "no");

                com.zerog.neoessentials.util.MessageUtil.ensureLanguageFileUpToDate();

            } else if (currentVersion > expectedVersion) {
                LOGGER.warn("Config file {} has a newer version ({}) than expected ({}). This may indicate a downgrade.",
                    configName, currentVersion, expectedVersion);
            } else {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Config file {} is up to date (version {})", configName, currentVersion);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check/update version for config {}: {}", configName, e.getMessage(), e);
        }
    }

    /**
     * Value-level fix for the chat-format defaults that shipped, at various points, referencing
     * {@code {neoessentials_username}}/{@code {neoessentials_name}} — which always resolve to the
     * real game-profile name — instead of {@code {neoessentials_displayname}}, which respects an
     * active {@code /nick}. {@link #mergeNewKeys} can never fix this on its own: the keys already
     * exist on disk (they were generated with the old default the first time the server started),
     * so the "never overwrite an existing value" rule that protects genuine user customization
     * also protects this untouched-but-buggy default forever.
     * <p>
     * Only replaces a value when it is an <em>exact</em> match for a known old default — anything
     * an admin has actually customized (even trivially, e.g. reordering the message) is left alone.
     *
     * @return {@code true} if at least one value was patched
     */
    static boolean patchLegacyNicknameChatDefaults(com.google.gson.JsonObject onDisk) {
        if (!onDisk.has("chat") || !onDisk.get("chat").isJsonObject()) return false;
        com.google.gson.JsonObject chat = onDisk.getAsJsonObject("chat");
        boolean changed = false;

        if (chat.has("chat-format") && chat.get("chat-format").isJsonObject()) {
            com.google.gson.JsonObject chatFormat = chat.getAsJsonObject("chat-format");
            changed |= patchIfUnchanged(chatFormat, "default",
                "<{neoessentials_prefix} {neoessentials_username} {neoessentials_suffix}> {MESSAGE}",
                "<{neoessentials_prefix} {neoessentials_displayname} {neoessentials_suffix}> {MESSAGE}");
            changed |= patchIfUnchanged(chatFormat, "group:admin",
                "&c[Admin] {neoessentials_username}: {MESSAGE}", "&c[Admin] {neoessentials_displayname}: {MESSAGE}");
            changed |= patchIfUnchanged(chatFormat, "group:mod",
                "&2[Mod] {neoessentials_username}: {MESSAGE}", "&2[Mod] {neoessentials_displayname}: {MESSAGE}");
            changed |= patchIfUnchanged(chatFormat, "world:creative",
                "&b[C] {neoessentials_username}: {MESSAGE}", "&b[C] {neoessentials_displayname}: {MESSAGE}");
        }

        if (chat.has("formatTemplates") && chat.get("formatTemplates").isJsonObject()) {
            com.google.gson.JsonObject formatTemplates = chat.getAsJsonObject("formatTemplates");
            if (formatTemplates.has("templates") && formatTemplates.get("templates").isJsonObject()) {
                com.google.gson.JsonObject templates = formatTemplates.getAsJsonObject("templates");
                changed |= patchIfUnchanged(templates, "default",
                    "<{neoessentials_prefix} {neoessentials_username} {neoessentials_suffix}> {MESSAGE}",
                    "<{neoessentials_prefix} {neoessentials_displayname} {neoessentials_suffix}> {MESSAGE}");
                changed |= patchIfUnchanged(templates, "rpg",
                    "&7[Lv.{neoessentials_level}] {neoessentials_prefix}{neoessentials_name}&r: {MESSAGE}",
                    "&7[Lv.{neoessentials_level}] {neoessentials_prefix}{neoessentials_displayname}&r: {MESSAGE}");
                changed |= patchIfUnchanged(templates, "modern",
                    "● {neoessentials_prefix}{neoessentials_name} &8› &r{MESSAGE}",
                    "● {neoessentials_prefix}{neoessentials_displayname} &8› &r{MESSAGE}");
                changed |= patchIfUnchanged(templates, "minimal",
                    "{neoessentials_name}: {MESSAGE}", "{neoessentials_displayname}: {MESSAGE}");
                changed |= patchIfUnchanged(templates, "detailed",
                    "&7[{neoessentials_world}] &r<{neoessentials_prefix}{neoessentials_name}{neoessentials_suffix}&r> {MESSAGE}",
                    "&7[{neoessentials_world}] &r<{neoessentials_prefix}{neoessentials_displayname}{neoessentials_suffix}&r> {MESSAGE}");
                changed |= patchIfUnchanged(templates, "ranked",
                    "&7[{neoessentials_group}] {neoessentials_prefix}{neoessentials_name}&r: {MESSAGE}",
                    "&7[{neoessentials_group}] {neoessentials_prefix}{neoessentials_displayname}&r: {MESSAGE}");
                changed |= patchIfUnchanged(templates, "custom",
                    "<{neoessentials_prefix} {neoessentials_username} {neoessentials_suffix}> {MESSAGE}",
                    "<{neoessentials_prefix} {neoessentials_displayname} {neoessentials_suffix}> {MESSAGE}");
            }
        }

        return changed;
    }

    /**
     * Replaces {@code object.key} with {@code newValue} only if it currently equals exactly
     * {@code oldValue} — used to correct known-bad shipped defaults without touching any value
     * an admin has actually customized.
     */
    private static boolean patchIfUnchanged(com.google.gson.JsonObject object, String key, String oldValue, String newValue) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return false;
        if (!object.get(key).getAsString().equals(oldValue)) return false;
        object.addProperty(key, newValue);
        return true;
    }

    /**
     * One-time migration from the old global {@code logging.enableDebugLogging} boolean to
     * the new per-category {@code logging.categories.<name>.{normal,debug}} structure. Must
     * run BEFORE {@link #mergeNewKeys} — it detects "not yet migrated" by the absence of
     * {@code logging.categories}, which mergeNewKeys would otherwise fill in with plain
     * defaults first.
     * <p>
     * If the old global toggle was {@code true}, every category's {@code debug} flag is
     * seeded to {@code true} so admins who had global debug logging on don't silently lose
     * it; otherwise categories are left for {@link #mergeNewKeys} to populate with defaults.
     * The old key itself is left on disk (unused but harmless), matching how this migration
     * system doesn't otherwise strip stale keys.
     *
     * @return {@code true} if {@code logging.categories} was added by this migration
     */
    static boolean migrateLoggingCategories(com.google.gson.JsonObject onDisk) {
        if (!onDisk.has("logging") || !onDisk.get("logging").isJsonObject()) return false;
        com.google.gson.JsonObject logging = onDisk.getAsJsonObject("logging");
        if (logging.has("categories")) return false;

        boolean globalDebugWasOn = logging.has("enableDebugLogging")
            && logging.get("enableDebugLogging").isJsonPrimitive()
            && logging.get("enableDebugLogging").getAsBoolean();

        com.google.gson.JsonObject categories = new com.google.gson.JsonObject();
        for (com.zerog.neoessentials.logging.LogCategory category : com.zerog.neoessentials.logging.LogCategory.values()) {
            com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
            entry.addProperty("normal", true);
            entry.addProperty("debug", globalDebugWasOn);
            categories.add(category.configKey(), entry);
        }
        logging.add("categories", categories);
        return true;
    }

    /**
     * One-time migration from the old {@code hologram.refreshInterval} key to its clearer
     * replacement {@code hologram.pollIntervalTicks} (v50) — the old name was easy to confuse
     * with each hologram's own, unrelated, seconds-based {@code refreshInterval} (set via
     * {@code /hologram refreshinterval}), which actually gates placeholder refresh; this one
     * only controls how often the scheduler polls to check that gate. Must run BEFORE
     * {@link #mergeNewKeys} — it detects "not yet migrated" by the absence of
     * {@code hologram.pollIntervalTicks}, which mergeNewKeys would otherwise fill in with the
     * plain default first, discarding whatever the admin had customized the old key to.
     * The old key itself is left on disk (unused but harmless), matching how this migration
     * system doesn't otherwise strip stale keys.
     *
     * @return {@code true} if {@code hologram.pollIntervalTicks} was added by this migration
     */
    static boolean migrateHologramPollInterval(com.google.gson.JsonObject onDisk) {
        if (!onDisk.has("hologram") || !onDisk.get("hologram").isJsonObject()) return false;
        com.google.gson.JsonObject hologram = onDisk.getAsJsonObject("hologram");
        if (hologram.has("pollIntervalTicks")) return false;
        if (!hologram.has("refreshInterval") || !hologram.get("refreshInterval").isJsonPrimitive()) return false;

        hologram.addProperty("pollIntervalTicks", hologram.get("refreshInterval").getAsInt());
        return true;
    }

    /**
     * Deep-merge {@code source} into {@code target}: for every key in source that is missing
     * in target, add it. Recurse into nested objects. Never overwrite existing values.
     *
     * @return true if at least one key was added
     */
    /**
     * Adds any board from the JAR template's {@code leaderboard.boards} array whose {@code id}
     * isn't already present on disk (existing boards, including admin-edited ones, are left
     * untouched — this only appends new ones). Complements {@link #mergeNewKeys}, which can't
     * merge inside an already-present JSON array.
     *
     * @return {@code true} if at least one board was added
     */
    private static boolean mergeNewLeaderboardBoards(com.google.gson.JsonObject jarTemplate, com.google.gson.JsonObject onDisk) {
        if (!jarTemplate.has("leaderboard") || !jarTemplate.get("leaderboard").isJsonObject()) return false;
        com.google.gson.JsonObject jarLb = jarTemplate.getAsJsonObject("leaderboard");
        if (!jarLb.has("boards") || !jarLb.get("boards").isJsonArray()) return false;

        if (!onDisk.has("leaderboard") || !onDisk.get("leaderboard").isJsonObject()) return false;
        com.google.gson.JsonObject onDiskLb = onDisk.getAsJsonObject("leaderboard");
        com.google.gson.JsonArray onDiskBoards = onDiskLb.has("boards") && onDiskLb.get("boards").isJsonArray()
            ? onDiskLb.getAsJsonArray("boards") : new com.google.gson.JsonArray();

        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (com.google.gson.JsonElement el : onDiskBoards) {
            if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                existingIds.add(el.getAsJsonObject().get("id").getAsString().toLowerCase());
            }
        }

        boolean changed = false;
        for (com.google.gson.JsonElement el : jarLb.getAsJsonArray("boards")) {
            if (!el.isJsonObject() || !el.getAsJsonObject().has("id")) continue;
            String id = el.getAsJsonObject().get("id").getAsString().toLowerCase();
            if (existingIds.add(id)) {
                onDiskBoards.add(el.deepCopy());
                changed = true;
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "  + Added new default leaderboard board: {}", id);
            }
        }
        if (changed) onDiskLb.add("boards", onDiskBoards);
        return changed;
    }

    private boolean mergeNewKeys(com.google.gson.JsonObject source, com.google.gson.JsonObject target) {
        boolean changed = false;
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            com.google.gson.JsonElement sourceVal = entry.getValue();

            if (!target.has(key)) {
                // Missing entirely — add from template
                target.add(key, sourceVal.deepCopy());
                changed = true;
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "  + Added missing config key: {}", key);
            } else if (sourceVal.isJsonObject() && target.get(key).isJsonObject()) {
                // Both sides are objects — recurse
                changed |= mergeNewKeys(sourceVal.getAsJsonObject(), target.get(key).getAsJsonObject());
            }
            // If key exists and isn't an object, leave the user's value alone
        }
        return changed;
    }

    /**
     * Recursively remove legacy "comment" keys from a {@link JsonObject}.
     *
     * <p>A key is considered a legacy comment key if it:
     * <ul>
     *   <li>ends with {@code _comment}  (e.g. {@code currencySymbol_comment})</li>
     *   <li>ends with {@code -description} (e.g. {@code chat-format-description})</li>
     *   <li>starts with {@code _} <em>and</em> is not {@code _configVersion}
     *       (catches {@code _comment}, {@code _doc_*}, {@code _step*}, {@code _how_*},
     *        {@code _example}, {@code _role_*}, {@code _important}, etc.)</li>
     * </ul>
     *
     * @return {@code true} if at least one key was removed
     */
    private boolean stripLegacyCommentKeys(com.google.gson.JsonObject obj) {
        boolean changed = false;
        List<String> toRemove = new ArrayList<>();
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (isLegacyCommentKey(key)) {
                toRemove.add(key);
            } else if (entry.getValue().isJsonObject()) {
                changed |= stripLegacyCommentKeys(entry.getValue().getAsJsonObject());
            }
        }
        for (String key : toRemove) {
            obj.remove(key);
            changed = true;
            NeoLog.debug(LOGGER, LogCategory.CONFIG, "  - Removed legacy comment key: {}", key);
        }
        return changed;
    }

    private static boolean isLegacyCommentKey(String key) {
        if ("_configVersion".equals(key)) return false; // keep the version field
        // Pattern: ends with _comment, ends with -description, or starts with _ (doc/step/how/example...)
        return key.endsWith("_comment") || key.endsWith("-description") || key.startsWith("_");
    }

    /**
     * Create a timestamped backup of a config file.
     */
    private void createConfigBackup(File configFile, int oldVersion) {
        try {
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            String backupName = configFile.getName().replace(".json",
                String.format("_v%d_backup_%s.json", oldVersion, timestamp));
            File backupFile = new File(configFile.getParentFile(), backupName);

            java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            NeoLog.info(LOGGER, LogCategory.CONFIG, "Created backup of old config: {}", backupFile.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to create backup for {}: {}", configFile.getName(), e.getMessage());
        }
    }

    /**
     * Copy default config from JAR resources to the config directory.
     */
    private void copyDefaultConfig(String configName, File configFile) {
        try (InputStream in = ResourceUtil.getJarConfigResource(configName)) {
            if (in != null) {
                // Ensure parent directories exist
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        LOGGER.warn("Failed to create parent directories for {}", configFile.getAbsolutePath());
                    }
                }
                try (OutputStream out = new FileOutputStream(configFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                NeoLog.info(LOGGER, LogCategory.CONFIG, "Copied default config {} to {}", configName, configFile.getAbsolutePath());
            } else {
                LOGGER.warn("Default config resource not found in JAR: {}", configName);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to copy default config {}: {}", configName, e.getMessage());
        }
    }

    /**
     * Check if external permissions should be used (permissions.useExternalPermissions)
     */
    public boolean isExternalPermissionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("permissions")) {
            JsonObject perms = config.getAsJsonObject("permissions");
            if (perms.has("useExternalPermissions")) {
                return perms.get("useExternalPermissions").getAsBoolean();
            }
        }
        return false; // Default to false
    }

    /**
     * Check if input validation is enabled (security.enableInputValidation)
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInputValidationEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("security")) {
            JsonObject security = config.getAsJsonObject("security");
            if (security.has("enableInputValidation")) {
                return security.get("enableInputValidation").getAsBoolean();
            }
        }
        return true; // Default to enabled
    }

    /**
     * Check if command length enforcer is enabled (security.enableCommandLengthEnforcer).
     * This controls whether the CommandLengthEnforcer event handler validates player commands.
     * When disabled, players can use commands of any length (not recommended for security).
     * Defaults to true if not set.
     *
     * @return true if command length enforcement is enabled, false otherwise
     */
    public boolean isCommandLengthEnforcerEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("security")) {
            JsonObject security = config.getAsJsonObject("security");
            if (security.has("enableCommandLengthEnforcer")) {
                return security.get("enableCommandLengthEnforcer").getAsBoolean();
            }
        }
        return true; // Default to enabled for security
    }

    /**
     * Returns true if custom chat formatting is enabled (chat.enable-chat-formatting in config).
     * Defaults to true if not set.
     */
    public static boolean isChatFormattingEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("chat")) {
            JsonObject chat = config.getAsJsonObject("chat");
            if (chat.has("enable-chat-formatting")) {
                return chat.get("enable-chat-formatting").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if color codes (including hex) are enabled in config (chat.enable-color-codes).
     * Defaults to true if not set.
     */
    public static boolean isColorCodesEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("chat")) {
            JsonObject chat = config.getAsJsonObject("chat");
            if (chat.has("enable-color-codes")) {
                return chat.get("enable-color-codes").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if economy module is enabled (modules.economyEnabled).
     * Defaults to true if not set.
     */
    public static boolean isEconomyEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("economyEnabled")) {
                return modules.get("economyEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Use a detected external economy mod (e.g. SG Economy API) instead of NeoEssentials' own
     * internal balances — same opt-in posture as {@code permissions.useExternalPermissions}.
     * Defaults to false: money is higher-stakes than permissions to silently switch out from
     * under an existing server, so an operator has to explicitly turn this on.
     */
    public static boolean isUsingExternalEconomy() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("economy")) {
            JsonObject economy = config.getAsJsonObject("economy");
            if (economy.has("useExternalEconomy")) {
                return economy.get("useExternalEconomy").getAsBoolean();
            }
        }
        return false;
    }

    /**
     * Returns the economy starting balance from economy.json (startingBalance).
     * Defaults to 100.0 if not set.
     */
    public static double getEconomyStartingBalance() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("startingBalance")) {
            try {
                return config.get("startingBalance").getAsDouble();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json startingBalance, using default 100.0", e);
            }
        }
        return 100.0;
    }

    /**
     * Returns whether economy transactions should be logged to logs/neoessentials/transactions.log
     * (economy.json logTransactions). Defaults to true if not set.
     */
    public static boolean isLogTransactionsEnabled() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("logTransactions")) {
            try {
                return config.get("logTransactions").getAsBoolean();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json logTransactions, using default true", e);
            }
        }
        return true;
    }

    /**
     * Returns the max transactions kept per player for /history
     * (economy.json transactionHistoryLimit). Defaults to 20 if not set.
     */
    public static int getTransactionHistoryLimit() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("transactionHistoryLimit")) {
            try {
                int val = config.get("transactionHistoryLimit").getAsInt();
                if (val > 0) return val;
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json transactionHistoryLimit, using default 20", e);
            }
        }
        return 20;
    }

    /**
     * Returns the currency symbol from economy.json (currencySymbol).
     * Defaults to "$" if not set.
     */
    public static String getCurrencySymbol() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("currencySymbol")) {
            String symbol = config.get("currencySymbol").getAsString();
            if (symbol != null && !symbol.isEmpty()) {
                return symbol;
            }
        }
        return "$";
    }

    /**
     * Returns the max balance from economy.json (maxBalance).
     * Defaults to 999999999.99 if not set.
     */
    public static double getMaxBalance() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("maxBalance")) {
            try {
                return config.get("maxBalance").getAsDouble();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json maxBalance, using default 999999999.99", e);
            }
        }
        return 999999999.99;
    }

    /**
     * Returns the tax percentage from economy.json (taxPercentage).
     * Defaults to 0.0 if not set.
     */
    public static double getTaxPercentage() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("taxPercentage")) {
            try {
                return config.get("taxPercentage").getAsDouble();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json taxPercentage, using default 0.0", e);
            }
        }
        return 0.0;
    }

    /**
     * Alias for getTaxPercentage() for backwards compatibility.
     */
    public static double getEconomyTaxPercentage() {
        return getTaxPercentage();
    }

    /**
     * Returns the singular currency name from economy.json (currencyName).
     * Defaults to the currency symbol if not set.
     */
    public static String getCurrencyName() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("currencyName")) {
            try {
                String name = config.get("currencyName").getAsString();
                if (name != null && !name.isBlank()) return name;
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json currencyName, falling back to currency symbol", e);
            }
        }
        return getCurrencySymbol();
    }

    /**
     * Returns the plural currency name from economy.json (currencyNamePlural).
     * Defaults to the singular currency name if not set.
     */
    public static String getCurrencyNamePlural() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("currencyNamePlural")) {
            try {
                String name = config.get("currencyNamePlural").getAsString();
                if (name != null && !name.isBlank()) return name;
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json currencyNamePlural, falling back to singular currency name", e);
            }
        }
        return getCurrencyName();
    }

    /**
     * Returns true if negative balances are allowed from economy.json (allowNegativeBalances).
     * Defaults to false if not set.
     */
    public static boolean allowNegativeBalances() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("allowNegativeBalances")) {
            return config.get("allowNegativeBalances").getAsBoolean();
        }
        return false;
    }

    /**
     * Returns true if inactive account cleanup is enabled from economy.json (cleanupInactiveAccounts).
     * Defaults to true if not set.
     */
    public static boolean isCleanupInactiveAccountsEnabled() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("cleanupInactiveAccounts")) {
            return config.get("cleanupInactiveAccounts").getAsBoolean();
        }
        return true;
    }

    /**
     * Returns the inactive account cleanup days from economy.json (inactiveAccountCleanupDays).
     * Defaults to 30 if not set.
     */
    public static int getInactiveAccountCleanupDays() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("inactiveAccountCleanupDays")) {
            try {
                return config.get("inactiveAccountCleanupDays").getAsInt();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json inactiveAccountCleanupDays, using default 30", e);
            }
        }
        return 30;
    }

    /**
     * Returns the max transfer amount from economy.json (maxTransferAmount).
     * Defaults to 10000.0 if not set.
     */
    public static double getMaxTransferAmount() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("maxTransferAmount")) {
            try {
                return config.get("maxTransferAmount").getAsDouble();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json maxTransferAmount, using default 10000.0", e);
            }
        }
        return 10000.0;
    }

    /**
     * Returns the pay toggle default from economy.json (paytoggleDefault).
     * Defaults to true if not set.
     */
    public static boolean getPayToggleDefault() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("paytoggleDefault")) {
            return config.get("paytoggleDefault").getAsBoolean();
        }
        return true;
    }

    /**
     * Returns the cache maximum size from economy.json (cacheMaximumSize).
     * Defaults to 10000 if not set.
     */
    public static int getCacheMaximumSize() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("cacheMaximumSize")) {
            try {
                return config.get("cacheMaximumSize").getAsInt();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json cacheMaximumSize, using default 10000", e);
            }
        }
        return 10000;
    }

    /**
     * Returns the cache expire after access minutes from economy.json (cacheExpireAfterAccessMinutes).
     * Defaults to 60 if not set.
     */
    public static int getCacheExpireAfterAccessMinutes() {
        JsonObject config = getInstance().getConfig(ECONOMY_CONFIG);
        if (config.has("cacheExpireAfterAccessMinutes")) {
            try {
                return config.get("cacheExpireAfterAccessMinutes").getAsInt();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for economy.json cacheExpireAfterAccessMinutes, using default 60", e);
            }
        }
        return 60;
    }

    /**
     * Returns the pay cooldown in seconds. This method is for backwards compatibility.
     * Returns 0 (no cooldown) by default as there is no specific config for this.
     */
    public static int getPayCooldownSeconds() {
        // No specific config for pay cooldown, return 0 (no cooldown)
        return 0;
    }

    /**
     * Clears the config cache, forcing all configs to be reloaded from disk on next access.
     * This is thread-safe and will acquire a write lock.
     */
    public void clearCache() {
        lock.writeLock().lock();
        try {
            configCache.clear();
            NeoLog.info(LOGGER, LogCategory.CONFIG, "Configuration cache cleared - configs will be reloaded from disk");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Loads all config files by clearing the cache and forcing a reload.
     * This ensures that any changes made to config files on disk are picked up.
     */
    public static void loadAll() {
        NeoLog.debug(LOGGER, LogCategory.CONFIG, "loadAll() triggered (e.g. /neoessentials reload) - clearing cache and re-verifying default configs");
        getInstance().clearCache();
        // Ensure all required configs exist
        getInstance().ensureDefaultConfigs();
        NeoLog.debug(LOGGER, LogCategory.CONFIG, "loadAll() complete - configs will be lazily re-read from disk on next access");
    }

    /**
     * Returns true if chat module is enabled (modules.chatEnabled).
     * Defaults to true if not set.
     */
    public static boolean isChatEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("chatEnabled")) {
                return modules.get("chatEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if moderation module is enabled (modules.moderationEnabled).
     * Defaults to true if not set.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isModerationEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("moderationEnabled")) {
                return modules.get("moderationEnabled").getAsBoolean();
            }
        }
        return true;
    }


    /**
     * Returns true if unsafe enchantments are allowed (items.unsafe-enchantments).
     * Defaults to true if not set.
     */
    public static boolean isUnsafeEnchantsAllowed() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("items")) {
            JsonObject items = config.getAsJsonObject("items");
            if (items.has("unsafe-enchantments")) {
                return items.get("unsafe-enchantments").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns the default stack size from items.default-stack-size.
     * Returns -1 (use vanilla) if not set.
     */
    public static int getDefaultStackSize() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("items")) {
            JsonObject items = config.getAsJsonObject("items");
            if (items.has("default-stack-size")) {
                try {
                    return items.get("default-stack-size").getAsInt();
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for items.default-stack-size, using default -1 (vanilla)", e);
                }
            }
        }
        return -1;
    }

    /**
     * Returns the oversized stack size from items.oversized-stacksize.
     * Defaults to 64 if not set.
     */
    public static int getOversizedStackSize() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("items")) {
            JsonObject items = config.getAsJsonObject("items");
            if (items.has("oversized-stacksize")) {
                try {
                    return items.get("oversized-stacksize").getAsInt();
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for items.oversized-stacksize, using default 64", e);
                }
            }
        }
        return 64;
    }

    /**
     * Returns the item spawn blacklist from items.item-spawn-blacklist.
     * Defaults to empty list if not set.
     */
    public static java.util.List<String> getItemSpawnBlacklist() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("items")) {
            JsonObject items = config.getAsJsonObject("items");
            if (items.has("item-spawn-blacklist") && items.get("item-spawn-blacklist").isJsonArray()) {
                java.util.List<String> list = new java.util.ArrayList<>();
                items.getAsJsonArray("item-spawn-blacklist").forEach(e -> list.add(e.getAsString()));
                return list;
            }
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Returns true if permission-based item spawn is enabled (items.permission-based-item-spawn).
     * Defaults to false if not set.
     */
    public static boolean isPermissionBasedItemSpawn() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("items")) {
            JsonObject items = config.getAsJsonObject("items");
            if (items.has("permission-based-item-spawn")) {
                return items.get("permission-based-item-spawn").getAsBoolean();
            }
        }
        return false;
    }

    /**
     * Returns true if kits module is enabled (modules.kitsEnabled).
     * Defaults to true if not set.
     */
    public static boolean isKitModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("kitsEnabled")) {
                return modules.get("kitsEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the hologram module is enabled (modules.hologramsEnabled).
     * Defaults to true if not set.
     */
    public static boolean isHologramModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("hologramsEnabled")) {
                return modules.get("hologramsEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the (chest/NPC) shop module is enabled (modules.shopEnabled).
     * Defaults to true if not set.
     */
    public static boolean isShopModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("shopEnabled")) {
                return modules.get("shopEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the Auction House module is enabled (modules.auctionHouseEnabled).
     * Defaults to true if not set.
     */
    public static boolean isAuctionHouseModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("auctionHouseEnabled")) {
                return modules.get("auctionHouseEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the Vault economy bridge module is enabled (modules.vaultEnabled).
     * Defaults to true if not set.
     */
    public static boolean isVaultModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("vaultEnabled")) {
                return modules.get("vaultEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the tablist module is enabled (modules.tablistEnabled).
     * Defaults to true if not set.
     */
    public static boolean isTablistModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("tablistEnabled")) {
                return modules.get("tablistEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the sidebar scoreboard module is enabled (modules.scoreboardEnabled).
     * Defaults to true if not set.
     */
    public static boolean isScoreboardModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("scoreboardEnabled")) {
                return modules.get("scoreboardEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the resource pack hosting module is enabled (modules.resourcePacksEnabled).
     * Defaults to true if not set.
     */
    public static boolean isResourcePacksModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("resourcePacksEnabled")) {
                return modules.get("resourcePacksEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the Votifier vote-listener module is enabled (modules.votifierEnabled).
     * Defaults to true if not set.
     */
    /**
     * The plain, single-line server name from {@code general.serverName} — what the
     * {@code {server_name}} placeholder shows. Deliberately independent of both
     * server.properties' MOTD and NeoEssentials' own {@code /motd} system (see
     * {@code {server_motd}} for that) — a scoreboard/tablist line can't sensibly hold a
     * multi-line, heavily-formatted server-list banner.
     */
    public static String getServerName() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("general")) {
            JsonObject general = config.getAsJsonObject("general");
            if (general.has("serverName")) {
                return general.get("serverName").getAsString();
            }
        }
        return "My Server";
    }

    /** How often the hologram scheduler polls every hologram to check whether it's due for a
     *  refresh, in server ticks. Default/minimum 1. This is NOT how often placeholders actually
     *  refresh — that's each hologram's own {@code refreshInterval} (in seconds, set via
     *  {@code /hologram refreshinterval}); this only controls how promptly that per-hologram
     *  gate gets checked. */
    public static int getHologramPollIntervalTicks() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("hologram")) {
            JsonObject hologram = config.getAsJsonObject("hologram");
            if (hologram.has("pollIntervalTicks")) {
                return Math.max(1, hologram.get("pollIntervalTicks").getAsInt());
            }
        }
        return 20;
    }

    /** How often holograms advance {@code {animation:NAME}} frames and spin/hover motion, in
     *  server ticks. Default/minimum 1 — an animation's own frame duration is already clamped
     *  to a 50ms (1-tick) minimum, so nothing below 1 would ever be visible anyway. */
    public static int getHologramAnimationIntervalTicks() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("hologram")) {
            JsonObject hologram = config.getAsJsonObject("hologram");
            if (hologram.has("animationInterval")) {
                return Math.max(1, hologram.get("animationInterval").getAsInt());
            }
        }
        return 1;
    }

    public static boolean isVotifierModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("votifierEnabled")) {
                return modules.get("votifierEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the Crates module is enabled (modules.cratesEnabled).
     * Defaults to true if not set.
     */
    public static boolean isCratesModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("cratesEnabled")) {
                return modules.get("cratesEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the player tags/badges module is enabled (modules.playerTagsEnabled).
     * Defaults to true if not set.
     */
    public static boolean isPlayerTagsModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("playerTagsEnabled")) {
                return modules.get("playerTagsEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if the Discord chat integration module is enabled
     * (modules.discordIntegrationEnabled). Defaults to true if not set.
     */
    public static boolean isDiscordIntegrationModuleEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("discordIntegrationEnabled")) {
                return modules.get("discordIntegrationEnabled").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if kit system is enabled (kits config section exists and module enabled).
     * Defaults to true if not set.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isKitSystemEnabled() {
        return isKitModuleEnabled();
    }

    /**
     * Returns the cost for a kit command from kits.commandCosts.<commandName>.
     * Defaults to 0 if not set.
     */
    public static double getKitCommandCost(String commandName) {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("commandCosts") && kits.get("commandCosts").isJsonObject()) {
                JsonObject costs = kits.getAsJsonObject("commandCosts");
                if (costs.has(commandName)) {
                    try {
                        return costs.get(commandName).getAsDouble();
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for kits.commandCosts.{}, using default 0.0", commandName, e);
                    }
                }
            }
        }
        return 0.0;
    }

    /**
     * Returns true if pastebin createkit is enabled (kits.pastebinCreatekit).
     * Defaults to false if not set.
     */
    public static boolean isPastebinCreatekitEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("pastebinCreatekit")) {
                return kits.get("pastebinCreatekit").getAsBoolean();
            }
        }
        return false;
    }

    /**
     * Returns true if used one-time kits should be skipped from kit list (kits.skipUsedOneTimeKitsFromKitList).
     * Defaults to false if not set.
     */
    public static boolean isSkipUsedOneTimeKitsFromKitList() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("skipUsedOneTimeKitsFromKitList")) {
                return kits.get("skipUsedOneTimeKitsFromKitList").getAsBoolean();
            }
        }
        return false;
    }

    /**
     * Returns true if kit auto-equip is enabled (kits.kitAutoEquip).
     * Defaults to false if not set.
     */
    public static boolean isKitAutoEquipEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("kitAutoEquip")) {
                return kits.get("kitAutoEquip").getAsBoolean();
            }
        }
        return false;
    }

    /**
     * Returns true if kit usage logging is enabled (kits.logKitUsage).
     * Defaults to true if not set.
     */
    public static boolean isLogKitUsageEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("kits") && config.get("kits").isJsonObject()) {
            JsonObject kits = config.getAsJsonObject("kits");
            if (kits.has("logKitUsage")) {
                return kits.get("logKitUsage").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns true if jail location is required (moderation.jailSettings.requireJailLocation).
     * Defaults to true if not set.
     */
    public static boolean isRequireJailLocationEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("jailSettings")) {
                JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                if (jailSettings.has("requireJailLocation")) {
                    return jailSettings.get("requireJailLocation").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns the ban message format from moderation.banSettings.banMessageFormat
     * Defaults to standard message if not set.
     */
    public static String getBanMessageFormat() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("banMessageFormat")) {
                    String val = banSettings.get("banMessageFormat").getAsString();
                    if (val != null && !val.trim().isEmpty()) return val;
                }
            }
        }
        return "You have been banned from this server.\nReason: {reason}\nBanned by: {bannedBy}\n{duration}";
    }

   /**
     * Returns the temp ban message format from moderation.banSettings.tempBanMessageFormat
     * Defaults to standard message if not set.
     */
    public static String getTempBanMessageFormat() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("tempBanMessageFormat")) {
                    String val = banSettings.get("tempBanMessageFormat").getAsString();
                    if (val != null && !val.trim().isEmpty()) return val;
                }
            }
        }
        return "You have been temporarily banned from this server.\nReason: {reason}\nBanned by: {bannedBy}\nExpires: {expiry}";
    }

    /**
     * Returns the IP ban message format from moderation.banSettings.ipBanMessageFormat.
     * Defaults to standard message if not set.
     */
    public static String getIPBanMessageFormat() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("banSettings")) {
                JsonObject banSettings = moderation.getAsJsonObject("banSettings");
                if (banSettings.has("ipBanMessageFormat")) {
                    String val = banSettings.get("ipBanMessageFormat").getAsString();
                    if (val != null && !val.trim().isEmpty()) return val;
                }
            }
        }
        return "Your IP address has been banned from this server.\nReason: {reason}\nBanned by: {bannedBy}";
    }

    /**
     * Returns true if warp actions should be logged (teleportation.warpSettings.logWarpActions).
     * Defaults to true if not set.
     */
    public boolean isLogWarpActionsEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("warpSettings")) {
                JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
                if (warpSettings.has("logWarpActions")) {
                    return warpSettings.get("logWarpActions").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns true if per-warp permission checks are enabled (teleportation.warpSettings.perWarpPermission).
     * Essentials: getPerWarpPermission() — checks neoessentials.warps.<name> per warp.
     * Defaults to false if not set.
     */
    public boolean isPerWarpPermissionEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("warpSettings")) {
                JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
                if (warpSettings.has("perWarpPermission")) {
                    return warpSettings.get("perWarpPermission").getAsBoolean();
                }
            }
        }
        return false;
    }

    /**
     * Returns true if routine/normal-level logging is enabled for the given category
     * (logging.categories.&lt;category&gt;.normal). Defaults to true if not set.
     */
    public boolean isCategoryNormalEnabled(com.zerog.neoessentials.logging.LogCategory category) {
        JsonObject entry = getLoggingCategoryEntry(category);
        if (entry != null && entry.has("normal")) {
            return entry.get("normal").getAsBoolean();
        }
        return true;
    }

    /**
     * Returns true if verbose/debug-level logging is enabled for the given category
     * (logging.categories.&lt;category&gt;.debug). Defaults to false if not set.
     */
    public boolean isCategoryDebugEnabled(com.zerog.neoessentials.logging.LogCategory category) {
        JsonObject entry = getLoggingCategoryEntry(category);
        if (entry != null && entry.has("debug")) {
            return entry.get("debug").getAsBoolean();
        }
        return false;
    }

    private JsonObject getLoggingCategoryEntry(com.zerog.neoessentials.logging.LogCategory category) {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (!config.has("logging")) return null;
        JsonObject logging = config.getAsJsonObject("logging");
        if (!logging.has("categories")) return null;
        JsonObject categories = logging.getAsJsonObject("categories");
        if (!categories.has(category.configKey())) return null;
        return categories.getAsJsonObject(category.configKey());
    }

    /**
     * Returns max command length from security.maxCommandLength.
     * Defaults to 256 if not set.
     */
    public int getMaxCommandLength() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("security")) {
            JsonObject security = config.getAsJsonObject("security");
            if (security.has("maxCommandLength")) {
                try {
                    return security.get("maxCommandLength").getAsInt();
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for security.maxCommandLength, using default 256", e);
                }
            }
        }
        return 256;
    }

    /**
     * Returns max reason length from security.maxReasonLength.
     * Defaults to 500 if not set.
     */
    public int getMaxReasonLength() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("security")) {
            JsonObject security = config.getAsJsonObject("security");
            if (security.has("maxReasonLength")) {
                try {
                    return security.get("maxReasonLength").getAsInt();
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for security.maxReasonLength, using default 500", e);
                }
            }
        }
        return 500;
    }

    /**
     * Returns the max economy amount a single validated input can be — reuses
     * economy.json's maxBalance (there's no separate security-side cap; a
     * transaction amount can never usefully exceed the max a balance can hold).
     */
    public BigDecimal getMaxEconomyAmount() {
        return BigDecimal.valueOf(getMaxBalance());
    }

    /**
     * Returns the minimum economy amount a validated input must be. Not
     * separately configurable — 0.01 is the smallest unit two-decimal
     * currency formatting can display.
     */
    public BigDecimal getMinEconomyAmount() {
        return BigDecimal.valueOf(0.01);
    }

    /**
     * Returns whether unsafe commands are allowed from security.allowUnsafeCommands.
     * Defaults to true (the dangerous-pattern/character check in
     * {@link com.zerog.neoessentials.util.InputValidator#validateCommand} is opt-in via
     * this flag, not opt-out) — set it to {@code false} explicitly to enable stricter
     * command scanning for powertool-bound commands.
     */
    public boolean isUnsafeCommandsAllowed() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("security")) {
            JsonObject security = config.getAsJsonObject("security");
            if (security.has("allowUnsafeCommands")) {
                return security.get("allowUnsafeCommands").getAsBoolean();
            }
        }
        return true;
    }

    /**
     * Returns max unsafe enchantment level from items.max-unsafe-enchantment-level.
     * Defaults to 10 if not set.
     */
    public int getMaxUnsafeEnchantmentLevel() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("items")) {
            JsonObject items = config.getAsJsonObject("items");
            if (items.has("max-unsafe-enchantment-level")) {
                try {
                    return items.get("max-unsafe-enchantment-level").getAsInt();
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for items.max-unsafe-enchantment-level, using default 10", e);
                }
            }
        }
        return 10;
    }

    /**
     * Check if jail system is enabled (moderation.jailSettings.enableJailSystem)
     */
    public static boolean isJailSystemEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("jailSettings")) {
                JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
                if (jailSettings.has("enableJailSystem")) {
                    return jailSettings.get("enableJailSystem").getAsBoolean();
                }
            }
        }
        return true; // Default to enabled
    }

    /**
     * Get max jails before permanent ban from moderation.jailSettings.maxJailsBeforePermBan.
     * Defaults to 6 if not set — deliberately higher than {@link #getMaxJailsBeforeTempBan()}'s
     * default of 3, so the temp-ban tier is actually reachable rather than dead code (both used
     * to default to the same value, meaning the perm-ban check — evaluated first — always won).
     */
    public static int getMaxJailsBeforePermBan() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("jailSettings")) {
                JsonObject jail = moderation.getAsJsonObject("jailSettings");
                if (jail.has("maxJailsBeforePermBan")) {
                    return jail.get("maxJailsBeforePermBan").getAsInt();
                }
            }
        }
        return 6;
    }

    /**
     * Get temp ban duration in minutes from moderation.jailSettings.tempBanDurationMinutes
     * Defaults to 1440 (24 hours) if not set
     */
    public static int getTempBanDurationMinutes() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("moderation")) {
            JsonObject moderation = config.getAsJsonObject("moderation");
            if (moderation.has("jailSettings")) {
                JsonObject jail = moderation.getAsJsonObject("jailSettings");
                if (jail.has("tempBanDurationMinutes")) {
                    return jail.get("tempBanDurationMinutes").getAsInt();
                }
            }
        }
        return 1440; // Default 24 hours
    }

    /**
     * Check if permissions module is enabled (modules.permissionsEnabled)
     */
    public static boolean isPermissionsEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("modules")) {
            JsonObject modules = config.getAsJsonObject("modules");
            if (modules.has("permissionsEnabled")) {
                return modules.get("permissionsEnabled").getAsBoolean();
            }
        }
        return true; // Default to enabled
    }

    /**
     * Get list of protected areas from teleportation.protectedAreas
     * Returns empty list if not set
     */
    public static List<String> getProtectedAreas() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        List<String> areas = new ArrayList<>();
        if (config.has("teleportation")) {
            JsonObject teleportation = config.getAsJsonObject("teleportation");
            if (teleportation.has("protectedAreas")) {
                teleportation.getAsJsonArray("protectedAreas").forEach(element -> 
                    areas.add(element.getAsString())
                );
            }
        }
        return areas;
    }

    /**
     * Check if cancel on movement is enabled from teleportation.generalSettings.cancelOnMovement
     * Defaults to true if not set
     */
    public static boolean isCancelOnMovementEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject teleportation = config.getAsJsonObject("teleportation");
            if (teleportation.has("generalSettings")) {
                JsonObject general = teleportation.getAsJsonObject("generalSettings");
                if (general.has("cancelOnMovement")) {
                    return general.get("cancelOnMovement").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Check if sound effects are enabled from teleportation.generalSettings.enableSoundEffects
     * Defaults to true if not set
     */
    public static boolean getEnableSoundEffects() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject teleportation = config.getAsJsonObject("teleportation");
            if (teleportation.has("generalSettings")) {
                JsonObject general = teleportation.getAsJsonObject("generalSettings");
                if (general.has("enableSoundEffects")) {
                    return general.get("enableSoundEffects").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Check if debug mode is enabled from logging.enableDebugLogging
     * Defaults to false if not set
     */
    public static boolean isDebugModeEnabled() {
        JsonObject config = getInstance().getConfig(MAIN_CONFIG);
        if (config.has("logging")) {
            JsonObject logging = config.getAsJsonObject("logging");
            if (logging.has("enableDebugLogging")) {
                return logging.get("enableDebugLogging").getAsBoolean();
            }
        }
        return false;
    }

    /**
     * Returns the teleport delay (in seconds) for the /back command.
     * Reads from teleportation.backSettings.teleportDelay first, then falls back to
     * teleportation.generalSettings.teleportDelay.  Default: 3.
     */
    public int getBackTeleportDelay() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("backSettings")) {
                JsonObject bs = tp.getAsJsonObject("backSettings");
                if (bs.has("teleportDelay")) {
                    try {
                        int val = bs.get("teleportDelay").getAsInt();
                        if (val >= 0) return val;
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for teleportation.backSettings.teleportDelay, falling back to generalSettings.teleportDelay", e);
                    }
                }
            }
            if (tp.has("generalSettings")) {
                JsonObject gs = tp.getAsJsonObject("generalSettings");
                if (gs.has("teleportDelay")) {
                    try {
                        int val = gs.get("teleportDelay").getAsInt();
                        if (val >= 0) return val;
                    } catch (Exception e) {
                        NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for teleportation.generalSettings.teleportDelay, using default 3", e);
                    }
                }
            }
        }
        return 3;
    }

    /**
     * Returns whether death locations should be saved for /back.
     * Reads from teleportation.backSettings.enableDeathBack.  Default: true.
     */
    public boolean isDeathBackEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("backSettings")) {
                JsonObject bs = tp.getAsJsonObject("backSettings");
                if (bs.has("enableDeathBack")) {
                    return bs.get("enableDeathBack").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Returns whether the previous location is saved before each teleport (for /back).
     * Reads from teleportation.backSettings.enableTeleportBack.  Default: true.
     */
    public boolean isTeleportBackEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("backSettings")) {
                JsonObject bs = tp.getAsJsonObject("backSettings");
                if (bs.has("enableTeleportBack")) {
                    return bs.get("enableTeleportBack").getAsBoolean();
                }
            }
        }
        return true;
    }

    /**
     * Save config changes. If split configs are enabled, only write to split files, never to config.json.
     */
    public void saveConfig(String configName, JsonObject config) {
        lock.writeLock().lock();
        try {
            if (ConfigSplitter.isSplittingEnabled() && configName.equals(MAIN_CONFIG)) {
                NeoLog.info(LOGGER, LogCategory.CONFIG, "Split configs enabled - writing changes to split files instead of config.json");
                ConfigSplitter.saveMergedConfigToSplitFiles(config);
                configCache.put(configName, config);
                return;
            }
            File file = ResourceUtil.getConfigFile(configName);
            // Atomic temp-file + rename, same pattern as JsonFileDataStore — a crash/disk-full
            // mid-write must not leave config.json truncated/invalid.
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp-" + System.currentTimeMillis());
            try (FileWriter writer = new FileWriter(tmp, StandardCharsets.UTF_8)) {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                gson.toJson(config, writer);
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            configCache.put(configName, config);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file {}: {}", configName, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns true if back/death teleport safety is enabled in teleportation.backSettings config section.
     * Key: teleportation.backSettings.enableBackSafety (defaults to true when absent).
     */
    public boolean isBackTeleportSafetyEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("backSettings")) {
                JsonObject backSettings = tp.getAsJsonObject("backSettings");
                if (backSettings.has("enableBackSafety")) {
                    return backSettings.get("enableBackSafety").getAsBoolean();
                }
            }
        }
        return true; // Default to true for safety
    }

    /**
     * Returns true if spawn teleport safety is enabled in teleportation.spawnSettings config section.
     * (teleportation.spawnSettings.enableSpawnSafety)
     */
    public boolean isSpawnSafetyEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("spawnSettings")) {
                JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
                if (spawnSettings.has("enableSpawnSafety")) {
                    return spawnSettings.get("enableSpawnSafety").getAsBoolean();
                }
            }
        }
        return true; // Default to true for safety
    }

    /**
     * Returns true if home teleport safety is enabled in teleportation.homeSettings config section.
     * Accepts both "enableHomeTeleportSafety" (canonical) and "enableHomeSafety" (alias, consistent with enableWarpSafety).
     * (teleportation.homeSettings.enableHomeTeleportSafety or teleportation.homeSettings.enableHomeSafety)
     */
    public boolean isHomeTeleportSafetyEnabled() {
        JsonObject config = getConfig(MAIN_CONFIG);
        if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("homeSettings")) {
                JsonObject homeSettings = tp.getAsJsonObject("homeSettings");
                // Accept canonical key first
                if (homeSettings.has("enableHomeTeleportSafety")) {
                    return homeSettings.get("enableHomeTeleportSafety").getAsBoolean();
                }
                // Also accept alias key (enableHomeSafety, consistent with enableWarpSafety naming)
                if (homeSettings.has("enableHomeSafety")) {
                    return homeSettings.get("enableHomeSafety").getAsBoolean();
                }
            }
        }
        return true; // Default to true for safety
    }

    /**
     * Ensure split configs are present and up to date on startup
     */
    public static void ensureSplitConfigsOnStartup() {
        if (ConfigSplitter.isSplittingEnabled()) {
            ConfigSplitter.ensureSplitConfigsUpToDate();
        }
    }


    /**
     * Get the config directory, using ResourceUtil for centralized path management
     */
    private static File getConfigDirectory() {
        File configDir = new File(ResourceUtil.CONFIG_DIR);
        ResourceUtil.ensureDirectoryExists(ResourceUtil.CONFIG_DIR);
        return configDir;
    }

    /** Returns the backup-command string from commands.backupCommand, or null if not set. */
    public String getBackupCommand() {
        try {
            JsonObject config = getConfig(MAIN_CONFIG);
            if (config.has("commands") && config.getAsJsonObject("commands").has("backupCommand")) {
                String val = config.getAsJsonObject("commands").get("backupCommand").getAsString();
                return val.isBlank() ? null : val;
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CONFIG, "Invalid value for commands.backupCommand, treating as unset", e);
        }
        return null;
    }
}
