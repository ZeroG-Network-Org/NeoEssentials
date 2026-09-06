package com.zerog.neoessentials.api.permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all NeoEssentials permission nodes.
 * This class automatically collects and manages all permission nodes used by the mod
 * for integration with permission plugins like PermissionsEX, LuckPerms, etc.
 */
public class PermissionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionRegistry.class);
    
    // Singleton pattern
    private static class SingletonHolder {
        private static final PermissionRegistry INSTANCE = new PermissionRegistry();
    }
    
    public static PermissionRegistry getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    // Storage for all permission nodes
    private final Set<String> registeredPermissions = ConcurrentHashMap.newKeySet();
    private final Map<String, PermissionInfo> permissionInfo = new ConcurrentHashMap<>();
    
    // Permission categories for organization
    public enum PermissionCategory {
        ADMIN("admin", "Administrative commands"),
    ECONOMY("economy", "Economy system"),
    TELEPORT("teleport", "Teleportation commands"),
    CHAT("chat", "Chat and messaging"),
    KITS("kits", "Kit system"),
    ITEMS("items", "Item management"),
    MODERATION("moderation", "Moderation commands"),
    PLAYER("player", "Player state commands"),
    MISC("misc", "Miscellaneous commands"),
    CORE("core", "Core functionality");

        private final String key;
        private final String description;
        
        PermissionCategory(String key, String description) {
            this.key = key;
            this.description = description;
        }
        
        public String getKey() { return key; }
        public String getDescription() { return description; }
    }
    
    // Permission info class
    public static class PermissionInfo {
        private final String permission;
        private final String description;
        private final PermissionCategory category;
        private final boolean defaultValue;
        
        public PermissionInfo(String permission, String description, PermissionCategory category, boolean defaultValue) {
            this.permission = permission;
            this.description = description;
            this.category = category;
            this.defaultValue = defaultValue;
        }
        
        public String getPermission() { return permission; }
        public String getDescription() { return description; }
        public PermissionCategory getCategory() { return category; }
        public boolean getDefaultValue() { return defaultValue; }
    }
    
    private PermissionRegistry() {
        // Initialize with all known permission nodes
        registerAllPermissions();
        
        // Automatically discover and register ALL permissions from the codebase
        autoDiscoverPermissions();
    }
    
    /**
     * Register a permission node with metadata
     */
    public void register(String permission, String description, PermissionCategory category, boolean defaultValue) {
        if (permission == null || permission.trim().isEmpty()) {
            LOGGER.warn("Attempted to register empty or null permission");
            return;
        }
        
        permission = permission.trim();
        
        // Validate permission format
        if (!isValidPermission(permission)) {
            LOGGER.warn("Invalid permission format: {}", permission);
            return;
        }
        
        registeredPermissions.add(permission);
        permissionInfo.put(permission, new PermissionInfo(permission, description, category, defaultValue));
        
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Registered permission: {} ({})", permission, category.getKey());
    }
    
    /**
     * Register a permission node with default settings
     */
    public void register(String permission, String description, PermissionCategory category) {
        register(permission, description, category, false);
    }
    
    /**
     * Register a permission node with minimal info
     */
    public void register(String permission) {
        register(permission, "Permission for " + permission, PermissionCategory.MISC, false);
    }
    
    /**
     * Get all registered permissions
     */
    public Set<String> getAllPermissions() {
        return Collections.unmodifiableSet(registeredPermissions);
    }
    
    /**
     * Get all permissions for a specific category
     */
    public Set<String> getPermissionsByCategory(PermissionCategory category) {
        return permissionInfo.values().stream()
                .filter(info -> info.getCategory() == category)
                .map(PermissionInfo::getPermission)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
    }
    
    /**
     * Get permission info
     */
    public PermissionInfo getPermissionInfo(String permission) {
        return permissionInfo.get(permission);
    }
    
    /**
     * Check if a permission is registered
     */
    public boolean isRegistered(String permission) {
        return registeredPermissions.contains(permission);
    }
    
    /**
     * Get all permissions starting with a prefix (for tab completion)
     */
    public List<String> getPermissionsStartingWith(String prefix) {
        return registeredPermissions.stream()
                .filter(perm -> perm.startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }
    
    /**
     * Get all NeoEssentials permissions (for tab completion)
     * Includes both registered and discovered permissions
     */
    public List<String> getNeoEssentialsPermissions() {
        // Get discovered permissions from scanner as well
        PermissionScanner scanner = PermissionScanner.getInstance();
        scanner.scanForPermissions();
        
        // Combine registered and discovered permissions
        java.util.Set<String> allNeoEssentialsPermissions = new java.util.HashSet<>(getPermissionsStartingWith("neoessentials."));
        allNeoEssentialsPermissions.addAll(scanner.getDiscoveredPermissions().stream()
                .filter(perm -> perm.startsWith("neoessentials."))
                .toList());
        
        return allNeoEssentialsPermissions.stream().sorted().toList();
    }
    
    /**
     * Validate permission format.
     *
     * <p>Accepts any well-formed dot-separated permission node, including those
     * belonging to external mods (e.g. {@code worldedit.edit}, {@code ftbchunks.map.*}).
     * The registry previously restricted auto-discovery to {@code neoessentials.*}
     * nodes only; external nodes are now allowed so that they can be stored in
     * {@code permissions.json} and enforced via the
     * {@link com.zerog.neoessentials.permissions.NeoEssentialsPermissionHandler}.</p>
     *
     * <p>Valid formats:
     * <ul>
     *   <li>Fully-qualified: {@code neoessentials.spawn}, {@code worldedit.edit}</li>
     *   <li>Wildcard suffix: {@code neoessentials.*}, {@code worldedit.*}, {@code ftbchunks.map.*}</li>
     * </ul>
     * </p>
     */
    private boolean isValidPermission(String permission) {
        if (permission == null || permission.isEmpty()) return false;

        // Negative permissions: strip the leading '-' and validate the rest
        if (permission.startsWith("-")) {
            return isValidPermission(permission.substring(1));
        }

        // Wildcard suffix (e.g. neoessentials.*, worldedit.*, modid.category.*)
        if (permission.endsWith(".*")) {
            String prefix = permission.substring(0, permission.length() - 2);
            return !prefix.isEmpty()
                && prefix.matches("^[a-z0-9._-]+$")
                && !prefix.startsWith(".")
                && !prefix.endsWith(".")
                && !prefix.contains("..");
        }

        // Standard node: any lowercase alphanumeric + dots/underscores/hyphens
        return permission.matches("^[a-z0-9._-]+$")
            && !permission.startsWith(".")
            && !permission.endsWith(".")
            && !permission.contains("..");
    }
    
    /**
     * Register all known permission nodes
     */
    private void registerAllPermissions() {
        NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Registering NeoEssentials permission nodes...");
        
        // Core permissions
        register("neoessentials.use", "Basic mod usage", PermissionCategory.CORE, true);
        register("neoessentials.admin", "Administrative access", PermissionCategory.ADMIN, false);
        register("neoessentials.dashboard.apikeys", "Create/list/revoke API keys for external dashboard integrations (/apikey)", PermissionCategory.ADMIN, false);
        register("neoessentials.dashboard.pair", "Pair/unpair this server with an external dashboard (/dashboard pair, /dashboard unpair)", PermissionCategory.ADMIN, false);
        register("neoessentials.reload", "Reload configuration", PermissionCategory.ADMIN, false);
        
        // Economy permissions
        register("neoessentials.economy.balance", "Check own balance", PermissionCategory.ECONOMY, true);
        register("neoessentials.economy.balance.others", "Check others' balance", PermissionCategory.ECONOMY, false);
        register("neoessentials.economy.pay", "Send payments to online players", PermissionCategory.ECONOMY, true);
        register("neoessentials.economy.pay.offline", "Send payments to offline players", PermissionCategory.ECONOMY, false);
        register("neoessentials.economy.pay.toggle", "Toggle payment acceptance", PermissionCategory.ECONOMY, true);
        register("neoessentials.economy.baltop", "View balance leaderboard", PermissionCategory.ECONOMY, true);
        register("neoessentials.economy.baltop.exempt", "Exclude self from baltop ranking", PermissionCategory.ECONOMY, false);
        register("neoessentials.economy.admin", "Economy administration", PermissionCategory.ECONOMY, false);
        register("neoessentials.economy.admin.give", "Give money to players", PermissionCategory.ECONOMY, false);
        register("neoessentials.economy.admin.take", "Take money from players", PermissionCategory.ECONOMY, false);
        register("neoessentials.economy.eco", "Run /eco admin commands (give/take/set/reset)", PermissionCategory.ECONOMY, false);
        register("neoessentials.economy.admin.set", "Set player balance", PermissionCategory.ECONOMY, false);
        // Worth / Sell
        register("neoessentials.worth", "Check the sell value of an item (/worth)", PermissionCategory.ECONOMY, true);
        register("neoessentials.sell", "Use the /sell command", PermissionCategory.ECONOMY, true);
        register("neoessentials.sell.hand", "Sell item in hand (/sell hand)", PermissionCategory.ECONOMY, true);
        register("neoessentials.sell.bulk", "Sell entire inventory (/sell inventory|all)", PermissionCategory.ECONOMY, true);
        register("neoessentials.setworth", "Set item sell prices (/setworth)", PermissionCategory.ECONOMY, false);

        // ChestShop
        register("neoessentials.shop.use",                 "Use (buy/sell at) shops",                        PermissionCategory.ECONOMY, true);
        register("neoessentials.shop.create",              "Create player chest shops",                       PermissionCategory.ECONOMY, true);
        register("neoessentials.shop.create.admin",        "Create admin chest shops",                        PermissionCategory.ECONOMY, false);
        register("neoessentials.shop.setprice",            "Set price on own shop sign (/chestshop setprice)",PermissionCategory.ECONOMY, true);
        register("neoessentials.shop.list.others",         "List shops owned by other players",               PermissionCategory.ECONOMY, false);
        register("neoessentials.shop.admin.remove",        "Force-remove any shop",                           PermissionCategory.ECONOMY, false);
        register("neoessentials.shop.admin.setprice",      "Set price on any shop sign",                      PermissionCategory.ECONOMY, false);
        register("neoessentials.shop.admin.reload",        "Reload shop data from disk",                      PermissionCategory.ECONOMY, false);
        register("neoessentials.shop.admin.csv.export",    "Export shops to CSV file",                        PermissionCategory.ECONOMY, false);
        register("neoessentials.shop.admin.csv.import",    "Import shops from CSV file",                      PermissionCategory.ECONOMY, false);
        register("neoessentials.shop.npc.manage",          "Create/remove/manage NPC shops (/npcshop)",       PermissionCategory.ECONOMY, false);

        // Player-state / admin tool permissions
        register("neoessentials.fly", "Toggle flight mode", PermissionCategory.PLAYER, false);
        register("neoessentials.fly.others", "Toggle flight for other players", PermissionCategory.PLAYER, false);
        register("neoessentials.god", "Toggle god mode (invincibility)", PermissionCategory.PLAYER, false);
        register("neoessentials.god.others", "Toggle god mode for other players", PermissionCategory.PLAYER, false);
        register("neoessentials.heal", "Restore own health and hunger", PermissionCategory.PLAYER, false);
        register("neoessentials.heal.others", "Restore another player's health", PermissionCategory.PLAYER, false);
        register("neoessentials.feed", "Restore own hunger", PermissionCategory.PLAYER, false);
        register("neoessentials.feed.others", "Restore another player's hunger", PermissionCategory.PLAYER, false);
        register("neoessentials.speed", "Set walk/fly speed", PermissionCategory.PLAYER, false);
        register("neoessentials.speed.others", "Set another player's speed", PermissionCategory.PLAYER, false);
        register("neoessentials.ext", "Extinguish self", PermissionCategory.PLAYER, true);
        register("neoessentials.ext.others", "Extinguish another player", PermissionCategory.PLAYER, false);
        register("neoessentials.burn", "Set a player on fire", PermissionCategory.PLAYER, false);
        register("neoessentials.give", "Give items to players", PermissionCategory.PLAYER, false);
        register("neoessentials.more", "Fill held stack to max", PermissionCategory.PLAYER, false);
        register("neoessentials.hat", "Wear held item as helmet", PermissionCategory.PLAYER, false);
        register("neoessentials.exp", "View XP info", PermissionCategory.PLAYER, true);
        register("neoessentials.exp.set", "Set own XP", PermissionCategory.PLAYER, false);
        register("neoessentials.exp.set.others", "Set another player's XP", PermissionCategory.PLAYER, false);
        register("neoessentials.exp.give", "Give XP to self", PermissionCategory.PLAYER, false);
        register("neoessentials.exp.give.others", "Give XP to another player", PermissionCategory.PLAYER, false);
        register("neoessentials.sudo", "Run commands as another player", PermissionCategory.PLAYER, false);
        register("neoessentials.sudo.exempt", "Cannot be sudo'd by non-console", PermissionCategory.PLAYER, false);
        register("neoessentials.playtime", "View own playtime", PermissionCategory.PLAYER, true);
        register("neoessentials.playtime.others", "View another player's playtime", PermissionCategory.PLAYER, false);
        // Server admin commands
        register("neoessentials.broadcast", "Broadcast a message to all players", PermissionCategory.ADMIN, false);
        register("neoessentials.time", "View current world time", PermissionCategory.ADMIN, false);
        register("neoessentials.time.set", "Set or add world time", PermissionCategory.ADMIN, false);
        register("neoessentials.weather", "Set world weather", PermissionCategory.ADMIN, false);
        register("neoessentials.kill", "Kill players", PermissionCategory.ADMIN, false);
        register("neoessentials.kill.exempt", "Exempt from being killed by /kill", PermissionCategory.ADMIN, false);
        register("neoessentials.kill.force", "Force kill even exempt players", PermissionCategory.ADMIN, false);
        register("neoessentials.gamemode", "Change own gamemode", PermissionCategory.ADMIN, false);
        register("neoessentials.gamemode.others", "Change another player's gamemode", PermissionCategory.ADMIN, false);
        register("neoessentials.teleport.tpo", "Teleport to player ignoring tptoggle", PermissionCategory.ADMIN, false);
        register("neoessentials.teleport.tpohere", "Bring player here ignoring tptoggle", PermissionCategory.ADMIN, false);
        register("neoessentials.teleport.tpoffline", "Teleport to offline player's last location", PermissionCategory.ADMIN, false);
        // Utility commands
        register("neoessentials.ptime", "Set own per-player time override", PermissionCategory.PLAYER, false);
        register("neoessentials.ptime.others", "Set another player's time override", PermissionCategory.ADMIN, false);
        register("neoessentials.pweather", "Set own per-player weather override", PermissionCategory.PLAYER, false);
        register("neoessentials.pweather.others", "Set another player's weather override", PermissionCategory.ADMIN, false);
        register("neoessentials.effect", "Apply potion effects to players", PermissionCategory.ADMIN, false);
        register("neoessentials.spawnmob", "Spawn entities at a player", PermissionCategory.ADMIN, false);
        register("neoessentials.spawnmob.others", "Spawn entities at another player", PermissionCategory.ADMIN, false);
        register("neoessentials.unlimited", "Toggle unlimited item use", PermissionCategory.ADMIN, false);
        register("neoessentials.unlimited.others", "Toggle unlimited items for another player", PermissionCategory.ADMIN, false);
        register("neoessentials.condense", "Condense items to storage blocks", PermissionCategory.PLAYER, false);
        // Item customisation & misc
        register("neoessentials.me", "Broadcast action messages (/me)", PermissionCategory.CHAT, true);
        register("neoessentials.tptoggle", "Toggle teleport request acceptance", PermissionCategory.TELEPORT, true);
        register("neoessentials.tptoggle.others", "Toggle tptoggle for another player", PermissionCategory.TELEPORT, false);
        register("neoessentials.gc", "View server memory and TPS info", PermissionCategory.ADMIN, false);
        register("neoessentials.lightning", "Strike lightning at look target", PermissionCategory.ADMIN, false);
        register("neoessentials.lightning.others", "Strike lightning at a named player", PermissionCategory.ADMIN, false);
        register("neoessentials.skull", "Get a player head item", PermissionCategory.PLAYER, false);
        register("neoessentials.itemname", "Rename held item", PermissionCategory.ITEMS, false);
        register("neoessentials.itemlore", "Edit held item lore", PermissionCategory.ITEMS, false);
        register("neoessentials.remove", "Remove entities in a radius", PermissionCategory.ADMIN, false);
        register("neoessentials.loom", "Open portable loom", PermissionCategory.PLAYER, false);
        register("neoessentials.cartography", "Open portable cartography table", PermissionCategory.PLAYER, false);
        // Home & Warp Enhancements
        register("neoessentials.renamehome", "Rename own homes", PermissionCategory.TELEPORT, true);
        register("neoessentials.renamehome.others", "Rename another player's homes", PermissionCategory.ADMIN, false);
        register("neoessentials.warpinfo", "Show warp location info", PermissionCategory.TELEPORT, true);
        register("neoessentials.world", "Teleport to a world/dimension", PermissionCategory.ADMIN, false);
        register("neoessentials.world.others", "Teleport another player to a world", PermissionCategory.ADMIN, false);
        register("neoessentials.spawner", "Change a mob spawner type", PermissionCategory.ADMIN, false);
        register("neoessentials.spawner.*", "Change spawner to any mob type", PermissionCategory.ADMIN, false);
        register("neoessentials.recipe", "Show/unlock crafting recipe for an item", PermissionCategory.PLAYER, true);
        register("neoessentials.tpauto", "Auto-accept all incoming teleport requests", PermissionCategory.TELEPORT, true);
        // Fun / miscellaneous commands
        register("neoessentials.firework", "Edit held firework rockets", PermissionCategory.PLAYER, false);
        register("neoessentials.firework.fire", "Launch firework rockets with /firework fire", PermissionCategory.PLAYER, false);
        register("neoessentials.nuke", "Rain TNT on a player (/nuke)", PermissionCategory.ADMIN, false);
        register("neoessentials.antioch", "Spawn lit TNT at look target (/antioch)", PermissionCategory.ADMIN, false);
        register("neoessentials.kittycannon", "Launch exploding baby cat (/kittycannon)", PermissionCategory.ADMIN, false);
        register("neoessentials.beezooka", "Launch angry bees (/beezooka)", PermissionCategory.ADMIN, false);
        register("neoessentials.itemdb", "Look up item registry information (/itemdb)", PermissionCategory.PLAYER, false);
        register("neoessentials.potion", "Edit potion effects on held potion item", PermissionCategory.ITEMS, false);
        register("neoessentials.info", "View server info/MOTD (/info)", PermissionCategory.PLAYER, true);
        register("neoessentials.rest", "Reset sleep timer to prevent phantom spawning", PermissionCategory.PLAYER, true);
        register("neoessentials.rest.others", "Reset another player's sleep timer", PermissionCategory.ADMIN, false);
        register("neoessentials.backup", "Trigger server world save and backup", PermissionCategory.ADMIN, false);
        register("neoessentials.tpauto.others", "Toggle tpauto for another player", PermissionCategory.ADMIN, false);
        // World Interaction & Fun Commands
        register("neoessentials.fireball", "Shoot projectiles", PermissionCategory.ADMIN, false);
        register("neoessentials.fireball.*", "Shoot any projectile type", PermissionCategory.ADMIN, false);
        register("neoessentials.fireball.ride", "Ride a shot projectile", PermissionCategory.ADMIN, false);
        register("neoessentials.tree", "Grow a tree at look target", PermissionCategory.ADMIN, false);
        register("neoessentials.break", "Break the looked-at block instantly", PermissionCategory.ADMIN, false);
        register("neoessentials.break.bedrock", "Break bedrock blocks", PermissionCategory.ADMIN, false);
        register("neoessentials.ice", "Freeze self with ice", PermissionCategory.PLAYER, false);
        register("neoessentials.ice.others", "Freeze another player", PermissionCategory.ADMIN, false);
        register("neoessentials.bottom", "Teleport to world bottom at current XZ", PermissionCategory.PLAYER, false);
        register("neoessentials.tpaall", "Send tpa-here to all online players", PermissionCategory.ADMIN, false);
        register("neoessentials.tpaall.others", "Send tpaall on behalf of another player", PermissionCategory.ADMIN, false);
        register("neoessentials.broadcastworld", "Broadcast to players in your current world", PermissionCategory.ADMIN, false);
        // Player Info & Admin Tools
        register("neoessentials.seen", "View when a player was last online", PermissionCategory.PLAYER, true);
        register("neoessentials.near", "List nearby players", PermissionCategory.PLAYER, true);
        register("neoessentials.ping", "View your ping", PermissionCategory.PLAYER, true);
        register("neoessentials.ping.others", "View another player's ping", PermissionCategory.PLAYER, true);
        register("neoessentials.playtime", "View your total play time", PermissionCategory.PLAYER, true);
        register("neoessentials.playtime.others", "View another player's play time", PermissionCategory.PLAYER, true);
        register("neoessentials.whois", "View detailed player info", PermissionCategory.ADMIN, false);
        register("neoessentials.realname", "Look up real name from nickname", PermissionCategory.PLAYER, true);
        register("neoessentials.sudo", "Force a player to run a command", PermissionCategory.ADMIN, false);
        register("neoessentials.sudo.exempt", "Be immune to /sudo", PermissionCategory.ADMIN, false);
        register("neoessentials.suicide", "Kill yourself with /suicide", PermissionCategory.PLAYER, true);
        register("neoessentials.msgtoggle", "Toggle your incoming private messages", PermissionCategory.PLAYER, true);
        register("neoessentials.msgtoggle.others", "Toggle another player's messages", PermissionCategory.ADMIN, false);
        register("neoessentials.rtoggle", "Toggle reply-to-last-sender", PermissionCategory.PLAYER, true);
        register("neoessentials.rtoggle.others", "Toggle rtoggle for another player", PermissionCategory.ADMIN, false);
        register("neoessentials.motd", "View the message of the day", PermissionCategory.PLAYER, true);
        register("neoessentials.rules", "View server rules", PermissionCategory.PLAYER, true);

        // Teleportation permissions
        register("neoessentials.teleport.admin", "Administrative teleportation", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.admin.tp", "Teleport players", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.admin.tphere", "Teleport players to you", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.admin.tpall", "Teleport all players", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.admin.tppos", "Teleport to coordinates", PermissionCategory.TELEPORT, false);
        
        // Teleport requests
        register("neoessentials.teleport.request.tpa", "Send teleport requests", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.request.tpahere", "Request players teleport to you", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.request.accept", "Accept teleport requests", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.request.deny", "Deny teleport requests", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.request.cancel", "Cancel sent teleport requests", PermissionCategory.TELEPORT, true);
        
        // Home system
        register("neoessentials.teleport.home", "Use home system", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.home.set", "Set home locations", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.home.delete", "Delete home locations", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.home.list", "List home locations", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.home.others", "Access others' homes", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.home.bypass.cooldown", "Bypass /home teleport & set cooldowns", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.home.bypass.warmup", "Bypass /home warmup delay", PermissionCategory.TELEPORT, false);
        
        // Dynamic home limit permissions
        // Pattern: neoessentials.home.<amount> where <amount> is 1-100
        // Example: neoessentials.home.5 allows 5 homes
        // Note: These are checked dynamically, not registered individually
        // The highest matching permission wins, or config default is used
        
        // Warp system
        register("neoessentials.teleport.warp", "Use warp system", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.warp.list", "List all available warps", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.warp.others", "Warp another player to a warp (/warp <name> <player>)", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.warp.create", "Create warps", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.warp.delete", "Delete warps", PermissionCategory.TELEPORT, false);
        register("neoessentials.warps.*", "Access ALL warps regardless of per-warp permissions", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.warp.bypass.cooldown", "Bypass /warp use cooldown", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.warp.bypass.warmup", "Bypass /warp warmup delay", PermissionCategory.TELEPORT, false);

        // Dynamic player warp limit permissions
        // Pattern: neoessentials.warp.limit.<amount> where <amount> is 1-100
        // Example: neoessentials.warp.limit.10 allows 10 player warps
        // Special: neoessentials.warp.limit.unlimited allows unlimited player warps
        register("neoessentials.warp.limit.unlimited", "Unlimited player warps", PermissionCategory.TELEPORT, false);
        
        // Spawn system
        register("neoessentials.teleport.spawn", "Use spawn teleportation", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.spawn.set", "Set spawn location", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.spawn.info", "View spawn information", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.spawn.clear", "Clear spawn location", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.spawn.bypass.cooldown", "Bypass /spawn cooldown", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.spawn.bypass.warmup", "Bypass /spawn warmup delay", PermissionCategory.TELEPORT, false);
        
        // Misc teleport
        register("neoessentials.teleport.back", "Use back teleportation", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.back.bypass.cooldown", "Bypass /back cooldown", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.back.bypass.warmup", "Bypass /back warmup delay", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.death", "Teleport to death location", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.top", "Teleport to highest block", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.jump", "Teleport through walls", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.jumpto", "Teleport to looking at", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.tpr", "Random teleportation", PermissionCategory.TELEPORT, true);

        // Global teleport bypass permissions (admin-level)
        register("neoessentials.teleport.bypass.cooldown", "Bypass ALL teleport cooldowns", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.bypass.warmup", "Bypass ALL teleport warmups (instant teleport)", PermissionCategory.TELEPORT, false);

        // Per-command teleport bypass permissions
        register("neoessentials.teleport.home.bypass.cooldown", "Bypass /home teleport & set cooldowns", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.home.bypass.warmup", "Bypass /home warmup delay (instant teleport)", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.warp.bypass.cooldown", "Bypass /warp use cooldown", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.warp.bypass.warmup", "Bypass /warp warmup delay (instant teleport)", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.spawn.bypass.cooldown", "Bypass /spawn cooldown", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.spawn.bypass.warmup", "Bypass /spawn warmup delay (instant teleport)", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.back.bypass.cooldown", "Bypass /back cooldown", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.back.bypass.warmup", "Bypass /back warmup delay (instant teleport)", PermissionCategory.TELEPORT, false);
        
        // Direct teleport - others access
        register("neoessentials.teleport.admin.tpo", "Teleport other players to locations", PermissionCategory.TELEPORT, false);
        
        // Kit system
        register("neoessentials.kits.use", "Use kit system", PermissionCategory.KITS, true);
        register("neoessentials.kits.list", "List available kits", PermissionCategory.KITS, true);
        register("neoessentials.kits.nocooldown", "Bypass kit cooldowns", PermissionCategory.KITS, false);
        register("neoessentials.kit.others", "Give a kit to another player (/kit <name> <player>)", PermissionCategory.KITS, false);
        register("neoessentials.kitreset", "Reset own kit cooldown", PermissionCategory.KITS, false);
        register("neoessentials.kitreset.others", "Reset another player's kit cooldown", PermissionCategory.KITS, false);

        // Individual kit permissions (will be added dynamically)
        // These follow the pattern: neoessentials.kits.<kitname>
        // Cooldown exemption can also be per-kit: neoessentials.kits.<kitname>.nocooldown
        
        // Item management
        register("neoessentials.item.repair", "Repair items", PermissionCategory.ITEMS, false);
        register("neoessentials.item.enchant", "Enchant items", PermissionCategory.ITEMS, false);
        register("neoessentials.item.enchant.unsafe", "Unsafe enchanting", PermissionCategory.ITEMS, false);
        register("neoessentials.item.enchant.others", "Enchant others' items", PermissionCategory.ITEMS, false);
        register("neoessentials.item.powertool", "Use powertools", PermissionCategory.ITEMS, false);
        register("neoessentials.item.powertool.toggle", "Toggle powertools", PermissionCategory.ITEMS, false);
        register("neoessentials.item.dispose", "Use disposal system", PermissionCategory.ITEMS, true);
        register("neoessentials.item.clearinventory", "Clear inventory", PermissionCategory.ITEMS, false);
        register("neoessentials.item.clearinventory.others", "Clear others' inventory", PermissionCategory.ITEMS, false);
        // Inventory viewing/editing permissions
        register("neoessentials.invsee", "View other players' inventories", PermissionCategory.ITEMS, false);
        register("neoessentials.invsee.edit", "Edit other players' inventories", PermissionCategory.ITEMS, false);
        register("neoessentials.enderchest", "View other players' ender chests", PermissionCategory.ITEMS, false);
        register("neoessentials.enderchest.edit", "Edit other players' ender chests", PermissionCategory.ITEMS, false);

        // Chat system
        register("neoessentials.chat.msg", "Send private messages", PermissionCategory.CHAT, true);
        register("neoessentials.chat.reply", "Reply to messages", PermissionCategory.CHAT, true);
        register("neoessentials.chat.ignore", "Ignore players", PermissionCategory.CHAT, true);
        register("neoessentials.chat.unignore", "Unignore players", PermissionCategory.CHAT, true);
        register("neoessentials.chat.msgtoggle", "Toggle message acceptance", PermissionCategory.CHAT, true);
        register("neoessentials.chat.msgtoggle.bypass", "Message players who have toggled off message receipt", PermissionCategory.CHAT, false);
        register("neoessentials.chat.socialspy", "Use social spy", PermissionCategory.CHAT, false);
        register("neoessentials.chat.mute", "Mute, unmute, and view the mute list (/mute, /unmute, /mutelist)", PermissionCategory.CHAT, false);
        register("neoessentials.chat.exempt", "Exempt from muting", PermissionCategory.CHAT, false);
        
        // Chat formatting and colors
        register("neoessentials.chat.color", "Use basic color codes in chat (&0-9, &a-f)", PermissionCategory.CHAT, false);
        register("neoessentials.chat.color.hex", "Use hex colors in chat (&#RRGGBB)", PermissionCategory.CHAT, false);
        register("neoessentials.chat.format", "Use formatting codes in chat (&k-o, &r)", PermissionCategory.CHAT, false);

        // Chat channels and features
        register("neoessentials.chat.channel.local", "Use local chat channel", PermissionCategory.CHAT, true);
        register("neoessentials.chat.channel.global", "Use global chat channel", PermissionCategory.CHAT, true);
        register("neoessentials.chat.staff", "Access to staff chat channel", PermissionCategory.CHAT, false);
        register("neoessentials.chat.mention", "Mention other players with @name", PermissionCategory.CHAT, true);
        register("neoessentials.chat.mention.all", "Mention everyone with @everyone", PermissionCategory.CHAT, false);
        register("neoessentials.chat.itemlink", "Show held item in chat with [item]", PermissionCategory.CHAT, true);

        // Chat anti-spam bypasses (Phase 3)
        register("neoessentials.chat.caps.bypass", "Bypass caps filter", PermissionCategory.CHAT, false);
        register("neoessentials.chat.repeat.bypass", "Bypass repeat message filter", PermissionCategory.CHAT, false);
        register("neoessentials.chat.links.bypass", "Bypass link filter", PermissionCategory.CHAT, false);
        register("neoessentials.chat.spam.bypass", "Bypass spam rate limit", PermissionCategory.CHAT, false);

        // Rich text effects (Phase 4)
        register("neoessentials.chat.richtext", "Use rich text effects (gradients, rainbow)", PermissionCategory.CHAT, false);
        register("neoessentials.chat.gradient", "Use gradient text effects", PermissionCategory.CHAT, false);
        register("neoessentials.chat.rainbow", "Use rainbow text effects", PermissionCategory.CHAT, false);

        // AFK system
        register("neoessentials.afk", "Use AFK system", PermissionCategory.MISC, true);
        register("neoessentials.afk.exempt", "Exempt from AFK kick", PermissionCategory.MISC, false);
        
        // Portable workstations
        register("neoessentials.anvil", "Open portable anvil", PermissionCategory.MISC, true);
        register("neoessentials.crafting", "Open portable crafting table", PermissionCategory.MISC, true);
        register("neoessentials.grindstone", "Open portable grindstone", PermissionCategory.MISC, true);
        register("neoessentials.smithing", "Open portable smithing table", PermissionCategory.MISC, true);
        register("neoessentials.stonecutting", "Open portable stonecutter", PermissionCategory.MISC, true);

        // Utility commands (non-duplicate entries only; seen/near/ping/whois/realname/
        // rules/suicide are already registered correctly in the Player Info section above)
        register("neoessentials.whois.detailed", "View detailed player information", PermissionCategory.ADMIN, false);
        register("neoessentials.sign", "Edit sign text", PermissionCategory.MISC, true);
        register("neoessentials.sign.colors", "Use colors in signs", PermissionCategory.MISC, false);
        register("neoessentials.rules.admin", "Manage server rules", PermissionCategory.ADMIN, false);
        register("neoessentials.book", "Give yourself a writable book", PermissionCategory.MISC, true);
        register("neoessentials.book.unlock", "Unlock a written book for editing", PermissionCategory.MISC, false);
        register("neoessentials.book.title", "Set the title of a written book", PermissionCategory.MISC, false);
        register("neoessentials.book.author", "Set the author of a written book", PermissionCategory.MISC, false);
        register("neoessentials.depth", "View depth/Y-level information", PermissionCategory.MISC, true);
        register("neoessentials.depth.others", "View others' depth information", PermissionCategory.MISC, false);
        register("neoessentials.compass", "View compass / bearing / coordinates info (/compass)", PermissionCategory.MISC, true);
        register("neoessentials.compass.others", "View compass info for another player", PermissionCategory.MISC, false);
        register("neoessentials.gamemode", "Change own gamemode", PermissionCategory.MISC, false);
        register("neoessentials.gamemode.others", "Change others' gamemode", PermissionCategory.ADMIN, false);
        register("neoessentials.helpop", "Send a help request to staff", PermissionCategory.MISC, true);
        register("neoessentials.helpop.receive", "Receive help-op requests", PermissionCategory.MISC, false);

        // Permission system
        register("neoessentials.permissions.admin", "Permission system administration", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.reload", "Reload permissions", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.list", "List permissions", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.user", "User permission management", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.user.context", "Manage contextual overrides for users", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group", "Group permission management", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.context", "Manage contextual overrides for groups", PermissionCategory.ADMIN, false);
        
        // Debug and info
        register("neoessentials.debug", "Debug mode access", PermissionCategory.ADMIN, false);
        register("neoessentials.info", "View mod information", PermissionCategory.MISC, true);

        // ── Moderation commands (actual permission nodes, not lang keys) ─────
        register("neoessentials.moderation.ban", "Ban players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.banip", "Ban IP addresses", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.banlist", "View ban list", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.tempban", "Temporarily ban players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.unban", "Unban players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.unbanip", "Unban IP addresses", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.kick", "Kick players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.kickall", "Kick all players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.freeze", "Freeze players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.unfreeze", "Unfreeze players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.freezeall", "Freeze all players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.unfreezeall", "Unfreeze all players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.freezelist", "View frozen players list", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.jail", "Jail players, indefinitely or for a set duration (/jail, /jailfor)", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.unjail", "Unjail players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.setjail", "Create jail locations", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.deljail", "Delete jail locations", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.jaillist", "View jailed players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.jailinfo", "View jail info", PermissionCategory.MODERATION, false);
        register("neoessentials.jail.allow-break", "Break blocks while jailed", PermissionCategory.MODERATION, false);
        register("neoessentials.jail.allow-place", "Place blocks while jailed", PermissionCategory.MODERATION, false);
        register("neoessentials.jail.allow-interact", "Interact with blocks/items while jailed", PermissionCategory.MODERATION, false);
        register("neoessentials.jail.allow-attack", "Attack entities while jailed", PermissionCategory.MODERATION, false);
        register("neoessentials.jail.wand", "Use the jail-region selection wand", PermissionCategory.MODERATION, false);
        register("neoessentials.jail.bypass", "Build/break inside jail cells (maintenance)", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.vanish", "Vanish self", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.vanish.others", "Vanish other players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.seevanished", "See vanished players", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.vanishlist", "View vanished players list", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.notify", "Receive moderation notifications", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.notifications", "Receive moderation event broadcasts", PermissionCategory.MODERATION, false);
        register("neoessentials.vanish.see", "See vanished players (alias)", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.warn", "Issue warnings to players (/warn)", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.warnings", "View player warning history (/warnings)", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.clearwarnings", "Clear all warnings for a player", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.note", "Add/remove staff notes on a player (/note, /removenote)", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.notes", "View a player's staff notes (/notes)", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.report", "Report another player (/report)", PermissionCategory.MODERATION, true);
        register("neoessentials.moderation.reports", "View and resolve the report queue (/reports, /reviewreport)", PermissionCategory.MODERATION, false);

        // ── Utility / misc commands ───────────────────────────────────────────
        // (near, motd base node already registered in Player Info section above)
        register("neoessentials.list", "View online player list", PermissionCategory.MISC, true);
        register("neoessentials.nick", "Change own nickname", PermissionCategory.MISC, true);
        register("neoessentials.nick.color", "Use colour codes in nickname", PermissionCategory.MISC, false);
        register("neoessentials.nick.others", "Change other players' nicknames", PermissionCategory.MISC, false);
        register("neoessentials.staff", "Access staff chat and staff features", PermissionCategory.MISC, false);
        register("neoessentials.motd.set", "Set MOTD on the active profile", PermissionCategory.ADMIN, false);
        register("neoessentials.motd.broadcast", "Broadcast MOTD to all online players", PermissionCategory.ADMIN, false);
        register("neoessentials.motd.reload", "Reload MOTD profiles from disk", PermissionCategory.ADMIN, false);
        register("neoessentials.motd.profile", "Manage MOTD profiles (create/delete/switch/info)", PermissionCategory.ADMIN, false);
        register("neoessentials.motd.rotation", "Control MOTD auto-rotation (enable/disable/next)", PermissionCategory.ADMIN, false);

        // ── Mail system ───────────────────────────────────────────────────────
        register("neoessentials.mail", "Use mail system (read, delete, status)", PermissionCategory.CHAT, true);
        register("neoessentials.mail.send", "Send mail to players", PermissionCategory.CHAT, true);
        register("neoessentials.mail.sendtemp", "Send timed/expiring mail to a player", PermissionCategory.CHAT, true);
        register("neoessentials.mail.sendall", "Broadcast mail to all players", PermissionCategory.CHAT, false);
        register("neoessentials.mail.sendtempall", "Broadcast timed mail to all players", PermissionCategory.CHAT, false);
        register("neoessentials.mail.clear", "Clear own mail", PermissionCategory.CHAT, true);
        register("neoessentials.mail.clear.others", "Clear another player's mail (admin)", PermissionCategory.CHAT, false);
        register("neoessentials.mail.clearall", "Wipe every player's mailbox (admin)", PermissionCategory.CHAT, false);

        // ── Item system additions ─────────────────────────────────────────────
        register("neoessentials.item.enchant.any", "Enchant any item (ignore restrictions)", PermissionCategory.ITEMS, false);
        register("neoessentials.item.spawn", "Use /spawnitem command", PermissionCategory.ITEMS, false);

        // ── Teleport additions ────────────────────────────────────────────────
        register("neoessentials.teleport.settpr", "Set random teleport centre", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.tp", "Teleport self (alias)", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.tphere", "Teleport others to self (alias)", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.tppos", "Teleport to coordinates (alias)", PermissionCategory.TELEPORT, false);
        register("neoessentials.teleport.pwarp", "Use player warps", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.pwarp.create", "Create player warps", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.pwarp.delete", "Delete player warps", PermissionCategory.TELEPORT, true);
        register("neoessentials.teleport.pwarp.list", "List player warps", PermissionCategory.TELEPORT, true);

        // ── Kits additions ────────────────────────────────────────────────────
        register("neoessentials.kits.create", "Create kits via /createkit", PermissionCategory.KITS, false);
        register("neoessentials.kits.delete", "Delete kits via /delkit", PermissionCategory.KITS, false);
        register("neoessentials.kits.override", "Override kit restrictions", PermissionCategory.KITS, false);

        // ── Permissions sub-command nodes ─────────────────────────────────────
        register("neoessentials.permissions.check", "Check a player's permissions", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.debug", "Full permission resolution trace for a player", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.search", "Search permissions", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.list.groups", "List permission groups", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.list.users", "List permission users", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.info.user", "View user permission info", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.info.group", "View group permission info", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.user.permissions", "Manage user permission nodes", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.user.groups", "Manage user group membership", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.user.clear", "Clear all user permissions", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.create", "Create permission groups", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.delete", "Delete permission groups", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.rename", "Rename permission groups", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.clone", "Clone permission groups", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.inherit", "Set group inheritance", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.permissions", "Manage group permission nodes", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.modify", "Modify group settings (prefix, suffix, priority)", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.priority", "Set/get group priority (used to order inheritance resolution)", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.clear", "Clear all group permissions", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.user.temp", "Grant/revoke time-limited permissions for a user", PermissionCategory.ADMIN, false);
        register("neoessentials.permissions.group.temp", "Grant/revoke time-limited permissions for a group", PermissionCategory.ADMIN, false);

        // ── Player-state / admin tool commands ───────────────────────────────
        register("neoessentials.fly", "Toggle flight mode", PermissionCategory.PLAYER, false);
        register("neoessentials.fly.others", "Toggle flight for other players", PermissionCategory.PLAYER, false);
        register("neoessentials.god", "Toggle god mode (invincibility)", PermissionCategory.PLAYER, false);
        register("neoessentials.god.others", "Toggle god mode for other players", PermissionCategory.PLAYER, false);
        register("neoessentials.heal", "Restore own health and hunger", PermissionCategory.PLAYER, false);
        register("neoessentials.heal.others", "Restore another player's health", PermissionCategory.PLAYER, false);
        register("neoessentials.feed", "Restore own hunger", PermissionCategory.PLAYER, false);
        register("neoessentials.feed.others", "Restore another player's hunger", PermissionCategory.PLAYER, false);
        register("neoessentials.speed", "Set walk/fly speed", PermissionCategory.PLAYER, false);
        register("neoessentials.speed.others", "Set another player's speed", PermissionCategory.PLAYER, false);
        register("neoessentials.ext", "Extinguish self", PermissionCategory.PLAYER, true);
        register("neoessentials.ext.others", "Extinguish another player", PermissionCategory.PLAYER, false);
        register("neoessentials.burn", "Set a player on fire", PermissionCategory.PLAYER, false);
        register("neoessentials.give", "Give items to players", PermissionCategory.PLAYER, false);
        register("neoessentials.more", "Fill held stack to max", PermissionCategory.PLAYER, false);
        register("neoessentials.hat", "Wear held item as helmet", PermissionCategory.PLAYER, false);
        register("neoessentials.exp", "View XP info", PermissionCategory.PLAYER, true);
        register("neoessentials.exp.set", "Set own XP", PermissionCategory.PLAYER, false);
        register("neoessentials.exp.set.others", "Set another player's XP", PermissionCategory.PLAYER, false);
        register("neoessentials.exp.give", "Give XP to self", PermissionCategory.PLAYER, false);
        register("neoessentials.exp.give.others", "Give XP to another player", PermissionCategory.PLAYER, false);
        register("neoessentials.sudo", "Run commands as another player", PermissionCategory.PLAYER, false);
        register("neoessentials.sudo.exempt", "Cannot be sudo'd by non-console", PermissionCategory.PLAYER, false);
        register("neoessentials.playtime", "View own playtime", PermissionCategory.PLAYER, true);
        register("neoessentials.playtime.others", "View another player's playtime", PermissionCategory.PLAYER, false);


        register("neoessentials.item", "Give yourself an item by name (/item)", PermissionCategory.ITEMS, false);
        register("neoessentials.rtoggle", "Toggle /r reply direction", PermissionCategory.CHAT, true);
        register("neoessentials.rtoggle.others", "Toggle /r reply direction for another player", PermissionCategory.ADMIN, false);
        register("neoessentials.help", "View command help list", PermissionCategory.MISC, true);
        register("neoessentials.moderation.tempbanip", "Temporarily ban an IP address", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.togglejail", "Toggle a player's jail state", PermissionCategory.MODERATION, false);
        register("neoessentials.moderation.jailinfo", "View jail location info", PermissionCategory.MODERATION, false);
        register("neoessentials.powertooltoggle", "Toggle all powertools on/off globally", PermissionCategory.ITEMS, true);
        register("neoessentials.tablist.admin", "Manage the custom tablist system", PermissionCategory.ADMIN, false);
        register("neoessentials.scoreboard.admin", "Manage the sidebar scoreboard system", PermissionCategory.ADMIN, false);
        register("neoessentials.scoreboard.toggle", "Toggle your own sidebar scoreboard on/off", PermissionCategory.PLAYER, true);
        register("neoessentials.leaderboard.view", "View leaderboards (/leaderboard, /lb)", PermissionCategory.PLAYER, true);
        register("neoessentials.leaderboard.admin", "Manage leaderboards: reload, create/delete custom boards, set values", PermissionCategory.ADMIN, false);
        register("neoessentials.leaderboard.kills.exempt", "Excluded from the kills leaderboard", PermissionCategory.PLAYER, false);
        register("neoessentials.leaderboard.mob_kills.exempt", "Excluded from the mob kills leaderboard", PermissionCategory.PLAYER, false);
        register("neoessentials.leaderboard.playtime.exempt", "Excluded from the playtime leaderboard", PermissionCategory.PLAYER, false);

        // ── Placeholder system ────────────────────────────────────────────────
        register("neoessentials.admin.placeholders", "Manage and test the placeholder system (/placeholder)", PermissionCategory.ADMIN, false);

        // ── Admin notices ────────────────────────────────────────────────────
        register("neoessentials.admin.notice", "Receive the startup admin-notice chat block on join (bug-report links, config split available, legacy data files, etc.)", PermissionCategory.ADMIN, false);

        // ── Fun / miscellaneous commands ─────────────────────────────────────
        register("neoessentials.firework", "Edit held firework rockets", PermissionCategory.PLAYER, false);
        register("neoessentials.firework.fire", "Launch firework rockets with /firework fire", PermissionCategory.PLAYER, false);
        register("neoessentials.nuke", "Rain TNT on a player (/nuke)", PermissionCategory.ADMIN, false);
        register("neoessentials.antioch", "Spawn lit TNT at look target (/antioch)", PermissionCategory.ADMIN, false);
        register("neoessentials.kittycannon", "Launch exploding baby cat (/kittycannon)", PermissionCategory.ADMIN, false);
        register("neoessentials.beezooka", "Launch angry bees (/beezooka)", PermissionCategory.ADMIN, false);
        register("neoessentials.itemdb", "Look up item registry information", PermissionCategory.PLAYER, false);
        register("neoessentials.potion", "Edit potion effects on held potion item", PermissionCategory.ITEMS, false);
        register("neoessentials.info", "View server info/MOTD (/info)", PermissionCategory.PLAYER, true);
        register("neoessentials.rest", "Reset sleep timer to prevent phantom spawning", PermissionCategory.PLAYER, true);
        register("neoessentials.rest.others", "Reset another player's sleep timer", PermissionCategory.ADMIN, false);
        register("neoessentials.backup", "Trigger server world save and backup", PermissionCategory.ADMIN, false);
        register("neoessentials.showkit", "Preview kit contents without claiming", PermissionCategory.PLAYER, true);
        register("neoessentials.powertoollist", "List all active powertool bindings", PermissionCategory.PLAYER, true);
        register("neoessentials.customtext", "Display custom server text pages", PermissionCategory.PLAYER, true);
        register("neoessentials.payconfirmtoggle", "Toggle payment confirmation prompts", PermissionCategory.PLAYER, true);
        register("neoessentials.ciconfirmtoggle", "Toggle /clearinventory confirmation prompts", PermissionCategory.PLAYER, true);

        NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Registered {} permission nodes", registeredPermissions.size());
    }
    
    /**
     * Add kit permission dynamically when a kit is created
     */
    public void registerKitPermission(String kitName) {
        if (kitName == null || kitName.trim().isEmpty()) return;
        
        String permission = "neoessentials.kits." + kitName.toLowerCase();
        String nocooldownPermission = permission + ".nocooldown";
        
        register(permission, "Use kit: " + kitName, PermissionCategory.KITS, false);
        register(nocooldownPermission, "Bypass cooldown for kit: " + kitName, PermissionCategory.KITS, false);
    }
    
    /**
     * Remove kit permission when a kit is deleted
     */
    public void unregisterKitPermission(String kitName) {
        if (kitName == null || kitName.trim().isEmpty()) return;
        
        String permission = "neoessentials.kits." + kitName.toLowerCase();
        String nocooldownPermission = permission + ".nocooldown";
        
        registeredPermissions.remove(permission);
        permissionInfo.remove(permission);
        registeredPermissions.remove(nocooldownPermission);
        permissionInfo.remove(nocooldownPermission);
        
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Unregistered kit permissions: {} and {}", permission, nocooldownPermission);
    }
    
    /**
     * Get summary of registered permissions by category
     */
    @SuppressWarnings("unused") // Public API method
    public Map<PermissionCategory, Integer> getPermissionSummary() {
        Map<PermissionCategory, Integer> summary = new EnumMap<>(PermissionCategory.class);
        
        for (PermissionCategory category : PermissionCategory.values()) {
            summary.put(category, getPermissionsByCategory(category).size());
        }
        
        return summary;
    }
    
    /**
     * Automatically discover and register permissions from the codebase
     */
    private void autoDiscoverPermissions() {
        NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Starting automatic permission discovery...");
        
        try {
            // Get the permission scanner and scan for permissions
            PermissionScanner scanner = PermissionScanner.getInstance();
            scanner.scanForPermissions();
            
            // Register all discovered permissions
            Set<String> discoveredPermissions = scanner.getDiscoveredPermissions();
            
            for (String permission : discoveredPermissions) {
                if (!isRegistered(permission)) {
                    // Auto-categorize based on permission structure
                    PermissionCategory category = categorizePermission(permission);
                    register(permission, "Auto-discovered permission", category, false);
                }
            }
            
            NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Auto-discovery completed: {} permissions discovered, {} new permissions registered", 
                discoveredPermissions.size(), 
                discoveredPermissions.stream().mapToInt(p -> isRegistered(p) ? 0 : 1).sum());
                
        } catch (Exception e) {
            LOGGER.error("Error during automatic permission discovery", e);
        }
    }
    
    /**
     * Categorize a permission based on its structure
     */
    private PermissionCategory categorizePermission(String permission) {
        String[] parts = permission.split("\\.");
        
        if (parts.length >= 2) {
            String category = parts[1].toLowerCase();
            
        return switch (category) {
            case "economy", "eco", "balance", "pay", "money" -> PermissionCategory.ECONOMY;
            case "teleport", "tp", "tpa", "home", "warp", "spawn" -> PermissionCategory.TELEPORT;
            case "chat", "msg", "message", "reply", "socialspy", "mute", "ignore" -> PermissionCategory.CHAT;
            case "kit", "kits" -> PermissionCategory.KITS;
            case "item", "items", "give", "enchant", "repair" -> PermissionCategory.ITEMS;
            case "moderation", "mod", "ban", "kick", "freeze", "jail", "vanish" -> PermissionCategory.MODERATION;
            case "admin", "reload", "permissions", "debug" -> PermissionCategory.ADMIN;
            case "fly", "god", "heal", "feed", "speed", "ext", "burn", "more", "hat", "exp", "sudo", "playtime" -> PermissionCategory.PLAYER;
            default -> PermissionCategory.MISC;
        };
        }
        
        return PermissionCategory.CORE;
    }
    
    /**
     * Refresh permissions by re-scanning the codebase (useful for development)
     */
    public void refreshPermissions() {
        NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Refreshing permission registry...");
        
        int initialCount = registeredPermissions.size();
        autoDiscoverPermissions();
        int finalCount = registeredPermissions.size();
        
        NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Permission refresh completed: {} -> {} permissions (+" + (finalCount - initialCount) + " new)", 
            initialCount, finalCount);
    }
    
    /**
     * Get all auto-discovered permissions (separate from manual registrations)
     */
    public Set<String> getAutoDiscoveredPermissions() {
        try {
            PermissionScanner scanner = PermissionScanner.getInstance();
            return scanner.getDiscoveredPermissions();
        } catch (Exception e) {
            LOGGER.error("Error getting auto-discovered permissions", e);
            return Collections.emptySet();
        }
    }
    
    /**
     * Export permissions to a readable format (for documentation)
     */
    public List<String> exportPermissions() {
        List<String> export = new ArrayList<>();
        export.add("# NeoEssentials Permission Nodes");
        export.add("# Total: " + registeredPermissions.size() + " permissions");
        export.add("");
        
        for (PermissionCategory category : PermissionCategory.values()) {
            Set<String> categoryPerms = getPermissionsByCategory(category);
            if (categoryPerms.isEmpty()) continue;
            
            export.add("## " + category.getDescription() + " (" + categoryPerms.size() + ")");
            export.add("");
            
            categoryPerms.stream()
                    .sorted()
                    .forEach(perm -> {
                        PermissionInfo info = permissionInfo.get(perm);
                        export.add("- `" + perm + "` - " + info.getDescription() + 
                                  " (default: " + info.getDefaultValue() + ")");
                    });
            export.add("");
        }
        
        return export;
    }

    /**
     * Sync all registered permissions with LuckPerms (if available).
     * This makes NeoEssentials permissions appear in LuckPerms autocomplete and web UI.
     * Call this after all permissions are registered.
     */
    @SuppressWarnings("unused")
    public void syncWithLuckPerms() {
        try {
            // Check if we're using LuckPerms
            var externalAdapter = com.zerog.neoessentials.api.permissions.PermissionAPI.getExternalAdapter();

            if (externalAdapter instanceof com.zerog.neoessentials.permissions.LuckPermsAdapter luckPermsAdapter) {
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Syncing {} permissions with LuckPerms...", registeredPermissions.size());
                luckPermsAdapter.registerPermissions(registeredPermissions);

                NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "✓ Permissions synced with LuckPerms");
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "  - Permissions will now appear in LuckPerms autocomplete");
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "  - Use '/lp info' to see registered permissions");
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "  - Web editor will show NeoEssentials permissions");

            } else {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "LuckPerms not detected - skipping permission sync");
            }

        } catch (Exception e) {
            LOGGER.warn("Could not sync permissions with LuckPerms: {}", e.getMessage());
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "LuckPerms sync error details", e);
        }
    }

    /**
     * Export permissions in LuckPerms import format.
     * Can be used with /lp import command.
     *
     * @return YAML-formatted string for LuckPerms import
     */
    @SuppressWarnings("unused") // Public API method for LuckPerms integration
    public String exportForLuckPerms() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# NeoEssentials Permissions for LuckPerms\n");
        yaml.append("# Generated on: ").append(java.time.LocalDateTime.now()).append("\n");
        yaml.append("# Total permissions: ").append(registeredPermissions.size()).append("\n");
        yaml.append("#\n");
        yaml.append("# To import: Save this file and run: /lp import <filename>\n");
        yaml.append("#\n\n");

        yaml.append("groups:\n");
        yaml.append("  default:\n");
        yaml.append("    permissions:\n");

        // Add all permissions that default to true
        for (String permission : registeredPermissions) {
            PermissionInfo info = permissionInfo.get(permission);
            if (info != null && info.getDefaultValue()) {
                yaml.append("      - ").append(permission).append("\n");
            }
        }

        yaml.append("\n");
        yaml.append("  admin:\n");
        yaml.append("    permissions:\n");
        yaml.append("      - neoessentials.*  # Grant all NeoEssentials permissions\n");

        return yaml.toString();
    }
}

