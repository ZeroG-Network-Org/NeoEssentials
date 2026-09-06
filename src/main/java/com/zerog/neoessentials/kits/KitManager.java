package com.zerog.neoessentials.kits;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all kit operations including creation, deletion, usage tracking, and cooldowns.
 * Thread-safe for concurrent access from multiple players.
 */
public class KitManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitManager.class);
    private static final KitManager INSTANCE = new KitManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String KIT_COLLECTION = "kits";
    private static final String COOLDOWN_COLLECTION = "kit_cooldowns";
    private static final String USAGE_COLLECTION = "kit_usages";

    private final com.zerog.neoessentials.storage.DataStore store;

    private final Map<String, Kit> kits = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> playerUsages = new ConcurrentHashMap<>();
    // Guards against a double /kit claim: canUseKit() (read cooldown/usage) and giveKit()'s
    // actual item-giving + cooldown/usage update are a check-then-act sequence with no lock
    // held across it, so two rapid /kit <name> invocations (double-tap, macro) could both pass
    // canUseKit() before either write landed — same class of bug as the click-spam issue fixed
    // in ShopInteractHandler and the stale-listing issue fixed in AuctionHouseManager.
    private final Set<UUID> claimsInProgress = ConcurrentHashMap.newKeySet();
    private volatile boolean initialized = false;

    private KitManager() {
        this.store = com.zerog.neoessentials.storage.StorageManager.getInstance().getStore();
    }

    public static KitManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the kit manager by loading all kits from configuration.
     */
    public synchronized void initialize() {
        if (initialized) return;

        try {
            NeoLog.info(LOGGER, LogCategory.KITS, "Initializing Kit Manager...");
            migrateLegacyFilesIfNeeded();
            loadKits();
            loadPlayerData();
            initialized = true;
            NeoLog.info(LOGGER, LogCategory.KITS, "Kit Manager initialized with {} kits", kits.size());
        } catch (Throwable e) {
            LOGGER.error("Failed to initialize Kit Manager: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Re-deserializes every kit's items from storage. {@link #initialize()} runs during
     * {@code RegisterCommandsEvent}, which fires before {@code AuctionComponentSerializer}'s
     * server reference is set (that happens at {@code ServerStartedEvent}) — so any kit item
     * carrying saved {@code DataComponentMap} data (enchantments, custom names, etc.) silently
     * fails to deserialize on that first load and is dropped from the kit. Call this once the
     * server has started to re-load every kit with components now correctly bound; loadKits()
     * naturally overwrites each kit's map entry in place, so this is safe to call even if the
     * first load already partially succeeded.
     */
    public void reloadKitItemsAfterServerStart() {
        if (!initialized) return;
        NeoLog.debug(LOGGER, LogCategory.KITS, "Re-loading kits now that item components are bound...");
        loadKits();
    }

    /**
     * Loads all kits from the active {@link com.zerog.neoessentials.storage.DataStore}.
     */
    private void loadKits() {
        try {
            int loadedCount = 0;
            for (JsonObject obj : store.getAll(KIT_COLLECTION).values()) {
                try {
                    Kit kit = Kit.fromJson(obj);
                    kits.put(kit.getName(), kit);

                    // Register kit permission with the permission registry for tab completion
                    try {
                        com.zerog.neoessentials.api.permissions.PermissionRegistry.getInstance()
                            .registerKitPermission(kit.getName());
                    } catch (Throwable e) {
                        LOGGER.warn("Failed to register kit permission for '{}': {}", kit.getName(), e.getMessage());
                    }

                    loadedCount++;
                } catch (Throwable e) {
                    LOGGER.warn("Failed to load kit from storage: {}", e.getMessage());
                }
            }
            NeoLog.info(LOGGER, LogCategory.KITS, "Loaded {} kits from storage", loadedCount);
        } catch (Throwable e) {
            LOGGER.error("Failed to load kits from storage: {}", e.getMessage(), e);
        }
    }

    /**
     * Persists a single kit definition to storage. Replaces the previous behavior of
     * rewriting the entire kits.json file on every create/update — only the changed
     * kit's record is written now.
     */
    private void saveKit(Kit kit) {
        try {
            store.put(KIT_COLLECTION, kit.getName(), kit.toJson());
        } catch (Exception e) {
            LOGGER.error("Failed to save kit '{}': {}", kit.getName(), e.getMessage(), e);
        }
    }

    /**
     * Loads player cooldown and usage data from the active DataStore.
     */
    private void loadPlayerData() {
        try {
            for (Map.Entry<String, JsonObject> entry : store.getAll(COOLDOWN_COLLECTION).entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey());
                    JsonObject record = entry.getValue();
                    Map<String, Long> cooldowns = new HashMap<>();
                    if (record.has("cooldowns")) {
                        for (Map.Entry<String, JsonElement> kitEntry : record.getAsJsonObject("cooldowns").entrySet()) {
                            cooldowns.put(kitEntry.getKey(), kitEntry.getValue().getAsLong());
                        }
                    }
                    this.playerCooldowns.put(playerId, cooldowns);
                } catch (Exception e) {
                    LOGGER.warn("Failed to load cooldown data for player: {}", e.getMessage());
                }
            }

            for (Map.Entry<String, JsonObject> entry : store.getAll(USAGE_COLLECTION).entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey());
                    JsonObject record = entry.getValue();
                    Map<String, Integer> usages = new HashMap<>();
                    if (record.has("usages")) {
                        for (Map.Entry<String, JsonElement> kitEntry : record.getAsJsonObject("usages").entrySet()) {
                            usages.put(kitEntry.getKey(), kitEntry.getValue().getAsInt());
                        }
                    }
                    this.playerUsages.put(playerId, usages);
                } catch (Exception e) {
                    LOGGER.warn("Failed to load usage data for player: {}", e.getMessage());
                }
            }

            NeoLog.debug(LOGGER, LogCategory.KITS, "Loaded player data for {} players",
                       Math.max(playerCooldowns.size(), playerUsages.size()));
        } catch (Exception e) {
            LOGGER.error("Failed to load player kit data: {}", e.getMessage(), e);
        }
    }

    /**
     * Persists a single player's cooldown map to the "kit_cooldowns" collection.
     * Deletes the record if the player no longer has any cooldowns tracked.
     */
    private void saveCooldowns(UUID playerId) {
        try {
            Map<String, Long> map = playerCooldowns.get(playerId);
            if (map == null || map.isEmpty()) {
                store.delete(COOLDOWN_COLLECTION, playerId.toString());
                return;
            }
            JsonObject cooldownsJson = new JsonObject();
            for (Map.Entry<String, Long> kitEntry : map.entrySet()) {
                cooldownsJson.addProperty(kitEntry.getKey(), kitEntry.getValue());
            }
            JsonObject record = new JsonObject();
            record.add("cooldowns", cooldownsJson);
            store.put(COOLDOWN_COLLECTION, playerId.toString(), record);
        } catch (Exception e) {
            LOGGER.error("Failed to save cooldown data for player {}: {}", playerId, e.getMessage(), e);
        }
    }

    /**
     * Persists a single player's usage-count map to the "kit_usages" collection.
     * Deletes the record if the player no longer has any usages tracked.
     */
    private void saveUsages(UUID playerId) {
        try {
            Map<String, Integer> map = playerUsages.get(playerId);
            if (map == null || map.isEmpty()) {
                store.delete(USAGE_COLLECTION, playerId.toString());
                return;
            }
            JsonObject usagesJson = new JsonObject();
            for (Map.Entry<String, Integer> kitEntry : map.entrySet()) {
                usagesJson.addProperty(kitEntry.getKey(), kitEntry.getValue());
            }
            JsonObject record = new JsonObject();
            record.add("usages", usagesJson);
            store.put(USAGE_COLLECTION, playerId.toString(), record);
        } catch (Exception e) {
            LOGGER.error("Failed to save usage data for player {}: {}", playerId, e.getMessage(), e);
        }
    }

    /**
     * One-time import of the legacy kits.json (config dir) and kit_player_data.json (data
     * dir) files into the active DataStore, if all three collections are still empty and
     * storage.autoMigrate is enabled. Old files are left in place, just no longer written to.
     */
    private void migrateLegacyFilesIfNeeded() {
        if (store.hasAnyData(KIT_COLLECTION) || store.hasAnyData(COOLDOWN_COLLECTION) || store.hasAnyData(USAGE_COLLECTION)) return;
        if (!com.zerog.neoessentials.config.ConfigManager.getInstance().isStorageAutoMigrateEnabled()) return;

        int migrated = 0;
        migrated += migrateLegacyKitsFile();
        migrated += migrateLegacyPlayerDataFile();

        if (migrated > 0) {
            NeoLog.info(LOGGER, LogCategory.KITS, "KitManager: migrated {} record(s) from legacy files into the '{}' storage backend.",
                migrated, com.zerog.neoessentials.storage.StorageManager.getInstance().getActiveType());
        }
    }

    private int migrateLegacyKitsFile() {
        File kitsFile = com.zerog.neoessentials.util.ResourceUtil.getConfigFile("kits.json");
        if (!kitsFile.exists()) return 0;

        int count = 0;
        try (Reader reader = new FileReader(kitsFile)) {
            JsonObject config = GSON.fromJson(reader, JsonObject.class);
            if (config != null && config.has("kits")) {
                JsonElement kitsElement = config.get("kits");
                if (kitsElement != null && kitsElement.isJsonArray()) {
                    for (JsonElement element : kitsElement.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        try {
                            JsonObject obj = element.getAsJsonObject().deepCopy();
                            // Reuse Kit.fromJson()'s name sanitization so the storage id matches
                            // exactly what loadKits()/Kit.getName() will key it under.
                            String name = obj.has("name")
                                ? obj.get("name").getAsString().toLowerCase().replaceAll("[^a-z0-9_]", "")
                                : UUID.randomUUID().toString();
                            store.put(KIT_COLLECTION, name, obj);
                            count++;
                        } catch (Exception e) {
                            LOGGER.warn("Failed to migrate legacy kit entry: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to migrate legacy kits.json: {}", e.getMessage());
        }
        return count;
    }

    private int migrateLegacyPlayerDataFile() {
        File playerDataFile = com.zerog.neoessentials.util.ResourceUtil.getDataFile("kit_player_data.json");
        if (!playerDataFile.exists()) return 0;

        int count = 0;
        try (Reader reader = new FileReader(playerDataFile)) {
            JsonObject data = GSON.fromJson(reader, JsonObject.class);
            if (data != null) {
                if (data.has("cooldowns")) {
                    JsonObject cooldownsJson = data.getAsJsonObject("cooldowns");
                    for (Map.Entry<String, JsonElement> playerEntry : cooldownsJson.entrySet()) {
                        try {
                            JsonObject record = new JsonObject();
                            record.add("cooldowns", playerEntry.getValue().getAsJsonObject());
                            store.put(COOLDOWN_COLLECTION, playerEntry.getKey(), record);
                            count++;
                        } catch (Exception e) {
                            LOGGER.warn("Failed to migrate legacy cooldown entry: {}", e.getMessage());
                        }
                    }
                }
                if (data.has("usages")) {
                    JsonObject usagesJson = data.getAsJsonObject("usages");
                    for (Map.Entry<String, JsonElement> playerEntry : usagesJson.entrySet()) {
                        try {
                            JsonObject record = new JsonObject();
                            record.add("usages", playerEntry.getValue().getAsJsonObject());
                            store.put(USAGE_COLLECTION, playerEntry.getKey(), record);
                            count++;
                        } catch (Exception e) {
                            LOGGER.warn("Failed to migrate legacy usage entry: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to migrate legacy kit_player_data.json: {}", e.getMessage());
        }
        return count;
    }
    
    // Kit Management Methods
    
    /**
     * Creates a new kit or updates an existing one.
     */
    public boolean createKit(String name, String displayName, String description, 
                           List<ItemStack> items, long cooldownMillis, String permission) {
        try {
            Kit kit = new Kit(name, displayName, description, items, cooldownMillis, permission, -1, true);
            kits.put(kit.getName(), kit);
            saveKit(kit);
            
            // Register kit permission with the permission registry for tab completion
            try {
                com.zerog.neoessentials.api.permissions.PermissionRegistry.getInstance()
                    .registerKitPermission(kit.getName());
            } catch (Exception e) {
                LOGGER.warn("Failed to register kit permission for '{}': {}", kit.getName(), e.getMessage());
            }
            
            NeoLog.info(LOGGER, LogCategory.KITS, "Created/updated kit: {}", kit.getName());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to create kit '{}': {}", name, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Deletes a kit.
     */
    public boolean deleteKit(String name) {
        String normalizedName = name.toLowerCase();
        if (kits.remove(normalizedName) != null) {
            store.delete(KIT_COLLECTION, normalizedName);
            
            // Unregister kit permission from the permission registry
            try {
                com.zerog.neoessentials.api.permissions.PermissionRegistry.getInstance()
                    .unregisterKitPermission(normalizedName);
            } catch (Exception e) {
                LOGGER.warn("Failed to unregister kit permission for '{}': {}", normalizedName, e.getMessage());
            }
            
            NeoLog.info(LOGGER, LogCategory.KITS, "Deleted kit: {}", normalizedName);
            return true;
        }
        return false;
    }
    
    /**
     * Gets a kit by name.
     * Performs lazy initialization if not already initialized.
     */
    public Kit getKit(String name) {
        // Lazy initialization for safety
        if (!initialized) {
            LOGGER.warn("KitManager accessed before initialization - performing lazy init");
            initialize();
        }
        return kits.get(name.toLowerCase());
    }
    
    /**
     * Gets all registered kit names.
     */
    public Set<String> getKitNames() {
        return new HashSet<>(kits.keySet());
    }
    
    /**
     * Gets all registered kit names (alias for getKitNames).
     */
    public Set<String> getAllKitNames() {
        return getKitNames();
    }
    
    /**
     * Gets all available kits.
     */
    public Collection<Kit> getAllKits() {
        return new ArrayList<>(kits.values());
    }
    
    /**
     * Gets kits available to a specific player (considering permissions).
     * Performs lazy initialization if not already initialized.
     */
    public List<Kit> getAvailableKits(ServerPlayer player) {
        // Lazy initialization for safety
        if (!initialized) {
            LOGGER.warn("KitManager accessed before initialization - performing lazy init");
            initialize();
        }
        return kits.values().stream()
                .filter(Kit::isEnabled)
                .filter(kit -> kit.getPermission() == null ||
                              PermissionAPI.hasPermission(player.getUUID(), kit.getPermission()))
                .toList();
    }
    
    /**
     * Checks if a player can use a kit right now.
     */
    public KitUsageResult canUseKit(ServerPlayer player, String kitName) {
        // If allowKitOverride is enabled and player has override permission, skip all restrictions
        if (com.zerog.neoessentials.config.ConfigManager.getInstance().isAllowKitOverrideEnabled() &&
            com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.override")) {
            NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility bypassed via override permission: player={} kit={}",
                player.getName().getString(), kitName);
            return new KitUsageResult(true, MessageUtil.localize("commands.neoessentials.kits.util.usable_override"));
        }

        Kit kit = getKit(kitName);
        if (kit == null) {
            NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility denied: player={} kit={} reason=kit not found",
                player.getName().getString(), kitName);
            return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.not_found"));
        }

        if (!kit.isEnabled()) {
            NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility denied: player={} kit={} reason=kit disabled",
                player.getName().getString(), kitName);
            return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.disabled"));
        }

        // Check permission
        if (kit.getPermission() != null) {
            if (!PermissionAPI.hasPermission(player.getUUID(), kit.getPermission())) {
                NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility denied: player={} kit={} reason=missing permission '{}'",
                    player.getName().getString(), kitName, kit.getPermission());
                return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.no_permission"));
            }
        }

        // Check cooldown (unless player has exemption)
        if (!isCooldownExempt(player, kitName)) {
            long remainingCooldown = getRemainingCooldown(player.getUUID(), kitName);
            if (remainingCooldown > 0) {
                NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility denied: player={} kit={} reason=on cooldown ({} ms remaining)",
                    player.getName().getString(), kitName, remainingCooldown);
                return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.on_cooldown", formatTime(remainingCooldown)));
            }
        }

        // Check usage limit
        if (kit.getMaxUses() > 0) {
            int usageCount = getUsageCount(player.getUUID(), kitName);
            if (usageCount >= kit.getMaxUses()) {
                NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility denied: player={} kit={} reason=max uses reached ({}/{})",
                    player.getName().getString(), kitName, usageCount, kit.getMaxUses());
                return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.max_uses_reached"));
            }
        }

        // Enforce maxKitsPerPlayer (active cooldowns)
        int maxKits = com.zerog.neoessentials.config.ConfigManager.getInstance().getMaxKitsPerPlayer();
        if (maxKits > 0 && !isCooldownExempt(player, kitName)) {
            // Count number of kits with active cooldowns for this player
            int activeCooldowns = 0;
            Map<String, Long> cooldownMap = playerCooldowns.get(player.getUUID());
            if (cooldownMap != null) {
                long now = System.currentTimeMillis();
                for (Long cooldownEnd : cooldownMap.values()) {
                    if (cooldownEnd != null && cooldownEnd > now) {
                        activeCooldowns++;
                    }
                }
            }
            // If this kit is not already on cooldown, using it would add a new cooldown
            boolean alreadyOnCooldown = false;
            if (cooldownMap != null && cooldownMap.containsKey(kitName.toLowerCase())) {
                long cooldownEnd = cooldownMap.get(kitName.toLowerCase());
                if (cooldownEnd > System.currentTimeMillis()) {
                    alreadyOnCooldown = true;
                }
            }
            if (!alreadyOnCooldown && activeCooldowns >= maxKits) {
                NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility denied: player={} kit={} reason=max kits on cooldown ({}/{})",
                    player.getName().getString(), kitName, activeCooldowns, maxKits);
                return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.max_kits_on_cooldown", maxKits));
            }
        }

        NeoLog.debug(LOGGER, LogCategory.KITS, "Kit eligibility check passed: player={} kit={}", player.getName().getString(), kitName);
        return new KitUsageResult(true, MessageUtil.localize("commands.neoessentials.kits.util.usable"));
    }
    
    /**
     * Gives a kit to a player.
     */
    public KitUsageResult giveKit(ServerPlayer player, String kitName) {
        NeoLog.debug(LOGGER, LogCategory.KITS, "Kit claim requested: player={} kit={}", player.getName().getString(), kitName);
        if (!claimsInProgress.add(player.getUUID())) {
            NeoLog.debug(LOGGER, LogCategory.KITS, "Kit claim rejected: player={} kit={} reason=claim already in progress",
                player.getName().getString(), kitName);
            return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.claim_in_progress"));
        }
        try {
            return doGiveKit(player, kitName);
        } finally {
            claimsInProgress.remove(player.getUUID());
        }
    }

    private KitUsageResult doGiveKit(ServerPlayer player, String kitName) {
        KitUsageResult canUse = canUseKit(player, kitName);
        if (!canUse.isAllowed()) {
            return canUse;
        }
        // Double-check maxKitsPerPlayer after giving kit (in case of race conditions), unless override
        if (!(com.zerog.neoessentials.config.ConfigManager.getInstance().isAllowKitOverrideEnabled() &&
              com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.override"))) {
            int maxKits = com.zerog.neoessentials.config.ConfigManager.getInstance().getMaxKitsPerPlayer();
            if (maxKits > 0 && !isCooldownExempt(player, kitName)) {
                Map<String, Long> cooldownMap = playerCooldowns.get(player.getUUID());
                int activeCooldowns = 0;
                long now = System.currentTimeMillis();
                if (cooldownMap != null) {
                    for (Long cooldownEnd : cooldownMap.values()) {
                        if (cooldownEnd != null && cooldownEnd > now) {
                            activeCooldowns++;
                        }
                    }
                }
                // If this kit is not already on cooldown, using it would add a new cooldown
                boolean alreadyOnCooldown = false;
                if (cooldownMap != null && cooldownMap.containsKey(kitName.toLowerCase())) {
                    long cooldownEnd = cooldownMap.get(kitName.toLowerCase());
                    if (cooldownEnd > now) {
                        alreadyOnCooldown = true;
                    }
                }
                if (!alreadyOnCooldown && activeCooldowns >= maxKits) {
                    return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.max_kits_on_cooldown", maxKits));
                }
            }
        }

        Kit kit = getKit(kitName);
        if (kit == null) {
            return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.not_found"));
        }

        try {
            Inventory inventory = player.getInventory();
            List<ItemStack> itemsGiven = new ArrayList<>();
            List<ItemStack> itemsDropped = new ArrayList<>();
            List<String> deniedItems = new ArrayList<>();

            // 26.1 port: Inventory.armor/offhand were folded into a separate EntityEquipment
            // object and are no longer directly accessible fields — use getItemBySlot()/
            // setItemSlot() on the player (LivingEntity) instead, in the same
            // feet/legs/chest/head order the old armor list used.
            net.minecraft.world.entity.EquipmentSlot[] armorSlots = {
                net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD
            };

            boolean autoEquip = com.zerog.neoessentials.config.ConfigManager.isKitAutoEquipEnabled();
            // Check if all armor slots are empty
            boolean armorSlotsEmpty = java.util.Arrays.stream(armorSlots).allMatch(slot -> player.getItemBySlot(slot).isEmpty());

            // Separate armor and non-armor items
            List<ItemStack> armorItems = new ArrayList<>();
            List<ItemStack> otherItems = new ArrayList<>();
            for (ItemStack item : kit.getItems()) {
                if (item.isEmpty()) continue;
                if (item.getItem().getDescriptionId().contains("helmet") ||
                    item.getItem().getDescriptionId().contains("chestplate") ||
                    item.getItem().getDescriptionId().contains("leggings") ||
                    item.getItem().getDescriptionId().contains("boots")) {
                    armorItems.add(item);
                } else {
                    otherItems.add(item);
                }
            }

            // Give armor items (auto-equip if enabled and slots are empty)
            if (autoEquip && armorSlotsEmpty && !armorItems.isEmpty()) {
                for (ItemStack armor : armorItems) {
                    // Find first empty armor slot
                    boolean equipped = false;
                    for (net.minecraft.world.entity.EquipmentSlot slot : armorSlots) {
                        if (player.getItemBySlot(slot).isEmpty()) {
                            player.setItemSlot(slot, armor.copy());
                            itemsGiven.add(armor.copy());
                            equipped = true;
                            break;
                        }
                    }
                    if (!equipped) {
                        // If no empty slot, add to inventory as fallback
                        if (inventory.add(armor.copy())) {
                            itemsGiven.add(armor.copy());
                        } else {
                            player.drop(armor.copy(), false);
                            itemsDropped.add(armor.copy());
                        }
                    }
                }
            } else {
                // Add armor items to inventory as normal
                for (ItemStack armor : armorItems) {
                    if (inventory.add(armor.copy())) {
                        itemsGiven.add(armor.copy());
                    } else {
                        player.drop(armor.copy(), false);
                        itemsDropped.add(armor.copy());
                    }
                }
            }

            // Give non-armor items
            for (ItemStack item : otherItems) {
                // Enforce permission-based item spawn for each item
                com.zerog.neoessentials.items.ItemSpawnHelper.SpawnResult spawnResult =
                    com.zerog.neoessentials.items.ItemSpawnHelper.canSpawnItem(player, item);
                if (!spawnResult.isSuccess()) {
                    deniedItems.add(item.getDisplayName().getString() + ": " + spawnResult.getErrorMessage());
                    LOGGER.warn("Denied item '{}' from kit '{}' for player '{}': {}", item.getDisplayName().getString(), kitName, player.getName().getString(), spawnResult.getErrorMessage());
                    continue;
                }
                if (inventory.add(item.copy())) {
                    itemsGiven.add(item.copy());
                } else {
                    player.drop(item.copy(), false);
                    itemsDropped.add(item.copy());
                }
            }

            // Update cooldown and usage tracking
            // Only set cooldown if player doesn't have exemption
            if (!isCooldownExempt(player, kitName)) {
                setCooldown(player.getUUID(), kitName, System.currentTimeMillis() + kit.getCooldownMillis());
            }
            incrementUsage(player.getUUID(), kitName);

            saveCooldowns(player.getUUID());
            saveUsages(player.getUUID());

            runKitCommands(kit, player);

            String result = MessageUtil.localize("commands.neoessentials.kits.util.given", kit.getDisplayName(), itemsGiven.size());
            if (!itemsDropped.isEmpty()) {
                result += MessageUtil.localize("commands.neoessentials.kits.util.items_dropped_suffix", itemsDropped.size());
            }
            if (!deniedItems.isEmpty()) {
                result += MessageUtil.localize("commands.neoessentials.kits.util.items_denied_suffix", deniedItems.size(), String.join(", ", deniedItems));
            }

            if (com.zerog.neoessentials.config.ConfigManager.isLogKitUsageEnabled()) {
                NeoLog.info(LOGGER, LogCategory.KITS, "Player {} used kit {}", player.getName().getString(), kitName);
            }
            NeoLog.debug(LOGGER, LogCategory.KITS,
                "Kit granted: player={} kit={} itemsGiven={} itemsDropped={} itemsDenied={}",
                player.getName().getString(), kitName, itemsGiven.size(), itemsDropped.size(), deniedItems.size());
            return new KitUsageResult(true, result);

        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.KITS,
                "Failed to give kit '" + kitName + "' to player " + player.getName().getString(), e);
            return new KitUsageResult(false, MessageUtil.localize("commands.neoessentials.kits.util.give_error"));
        }
    }

    /**
     * Runs this kit's console commands (kits.json's per-kit "commands" array) once the items
     * have actually been handed over — e.g. granting a permission or broadcasting a message
     * alongside the items. {player} is replaced with the claiming player's name. Same
     * server.execute()-wrapped console-dispatch pattern as {@link com.zerog.neoessentials.scheduler.TaskScheduler}.
     */
    private void runKitCommands(Kit kit, ServerPlayer player) {
        List<String> commands = kit.getCommands();
        if (commands.isEmpty()) return;

        var server = player.level().getServer();
        if (server == null) return;

        for (String command : commands) {
            String cmd = (command.startsWith("/") ? command.substring(1) : command)
                .replace("{player}", player.getName().getString());
            server.execute(() -> {
                try {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
                } catch (Exception e) {
                    LOGGER.error("Failed to execute kit command '{}' from kit '{}': {}", cmd, kit.getName(), e.getMessage(), e);
                }
            });
        }
    }

    // Cooldown and Usage Tracking

    private long getRemainingCooldown(UUID playerId, String kitName) {
        Map<String, Long> playerCooldownMap = playerCooldowns.get(playerId);
        if (playerCooldownMap == null) return 0;
        Long cooldownEnd = playerCooldownMap.get(kitName.toLowerCase());
        if (cooldownEnd == null) return 0;
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /** Public alias — used by KitCommand list display and KitResetCommand. */
    public long getRemainingCooldownPublic(UUID playerId, String kitName) {
        return getRemainingCooldown(playerId, kitName);
    }

    /**
     * Reset the cooldown for a player on a specific kit.
     * Essentials: User.setKitTimestamp(kitName, 0)
     */
    public void resetCooldown(UUID playerId, String kitName) {
        Map<String, Long> map = playerCooldowns.get(playerId);
        if (map != null) {
            map.remove(kitName.toLowerCase());
        }
        saveCooldowns(playerId);
    }

    /**
     * Reset ALL kit cooldowns for a player.
     */
    @SuppressWarnings("unused") // Public API — may be called by external integrations
    public void resetAllCooldowns(UUID playerId) {
        playerCooldowns.remove(playerId);
        store.delete(COOLDOWN_COLLECTION, playerId.toString());
    }

    private void setCooldown(UUID playerId, String kitName, long cooldownEnd) {
        playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                      .put(kitName.toLowerCase(), cooldownEnd);
    }
    
    public int getUsageCount(UUID playerId, String kitName) {
        Map<String, Integer> playerUsageMap = playerUsages.get(playerId);
        if (playerUsageMap == null) return 0;
        return playerUsageMap.getOrDefault(kitName.toLowerCase(), 0);
    }

    /**
     * Reset the use count for a player on a specific kit. Previously there was no way to do
     * this at all — {@link #resetCooldown} only clears the cooldown timestamp, so a player who
     * hit a kit's {@code maxUses} cap stayed permanently blocked by {@link #canUseKit}'s usage
     * check regardless of cooldown state, with no admin command able to clear it.
     */
    public void resetUsage(UUID playerId, String kitName) {
        Map<String, Integer> map = playerUsages.get(playerId);
        if (map != null) {
            map.remove(kitName.toLowerCase());
        }
        saveUsages(playerId);
    }

    /**
     * Reset ALL kit use counts for a player.
     */
    @SuppressWarnings("unused") // Public API — may be called by external integrations
    public void resetAllUsages(UUID playerId) {
        playerUsages.remove(playerId);
        store.delete(USAGE_COLLECTION, playerId.toString());
    }

    private void incrementUsage(UUID playerId, String kitName) {
        playerUsages.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                   .merge(kitName.toLowerCase(), 1, Integer::sum);
    }
    
    /**
     * Checks if a player has cooldown exemption for a kit.
     * Checks both global cooldown exemption and per-kit exemption.
     */
    private boolean isCooldownExempt(ServerPlayer player, String kitName) {
        UUID playerId = player.getUUID();
        // Check override permission if allowKitOverride is enabled
        if (com.zerog.neoessentials.config.ConfigManager.getInstance().isAllowKitOverrideEnabled()) {
            if (com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(playerId, "neoessentials.kits.override")) {
                return true;
            }
        }
        // Check global cooldown exemption
        if (com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(playerId, "neoessentials.kits.nocooldown")) {
            return true;
        }
        // Check per-kit cooldown exemption
        String kitNocooldownPermission = "neoessentials.kits." + kitName.toLowerCase() + ".nocooldown";
        return com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(playerId, kitNocooldownPermission);
    }
    
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Reloads all kit data from configuration.
     */
    public void reload() {
        kits.clear();
        playerCooldowns.clear();
        playerUsages.clear();
        initialized = false;
        initialize();
    }
    
    /**
     * Result of a kit usage attempt.
     */
    public static class KitUsageResult {
        private final boolean allowed;
        private final String message;
        
        public KitUsageResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getMessage() { return message; }
    }
}

