package com.zerog.neoessentials.shop.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.util.ResourceUtil;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for NPC shop persistence.
 * Stores data via the active {@link com.zerog.neoessentials.storage.DataStore}, keyed by
 * the stable {@code shopId} (NOT the in-world entity UUID, which changes across
 * despawn/respawn — see {@link #updateEntityUUID(UUID, UUID)}).
 */
public class ShopEntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopEntityManager.class);
    // Only used to parse the legacy npc_shops.json file during one-time migration.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String COLLECTION = "npc_shops";

    private static final ShopEntityManager INSTANCE = new ShopEntityManager();
    public static ShopEntityManager getInstance() { return INSTANCE; }
    private ShopEntityManager() {}

    private final com.zerog.neoessentials.storage.DataStore store =
        com.zerog.neoessentials.storage.StorageManager.getInstance().getStore();

    /** shopId → ShopEntityData */
    private final ConcurrentHashMap<UUID, ShopEntityData> byShopId   = new ConcurrentHashMap<>();
    /** entityUUID → ShopEntityData */
    private final ConcurrentHashMap<UUID, ShopEntityData> byEntityId = new ConcurrentHashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void initialize() {
        try {
            migrateLegacyFilesIfNeeded();
            load();
            NeoLog.info(LOGGER, LogCategory.GENERAL, "[NpcShop] Loaded {} NPC shop(s).", byShopId.size());
        } catch (Exception e) {
            LOGGER.error("[NpcShop] Failed to load NPC shops from storage", e);
        }
    }

    public void shutdown() {
        // Every mutation is already persisted immediately via the DataStore — nothing to flush.
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public void register(ShopEntityData data) {
        byShopId.put(data.shopId, data);
        if (data.entityUUID != null) byEntityId.put(data.entityUUID, data);
        store.put(COLLECTION, data.shopId.toString(), toJson(data));
    }

    public boolean remove(UUID shopId) {
        ShopEntityData data = byShopId.remove(shopId);
        if (data == null) return false;
        if (data.entityUUID != null) byEntityId.remove(data.entityUUID);
        store.delete(COLLECTION, shopId.toString());
        return true;
    }

    public ShopEntityData getByShopId(UUID shopId)   { return byShopId.get(shopId);    }
    public ShopEntityData getByShopId(String shopId) {
        try {
            return byShopId.get(UUID.fromString(shopId));
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.GENERAL, "Invalid shop id '{}' passed to getByShopId", shopId, e);
            return null;
        }
    }
    public ShopEntityData getByEntityUUID(UUID entityUUID) { return byEntityId.get(entityUUID); }

    public Collection<ShopEntityData> getAll() { return Collections.unmodifiableCollection(byShopId.values()); }

    public List<ShopEntityData> getByOwner(UUID ownerUUID) {
        return byShopId.values().stream()
                .filter(d -> ownerUUID.equals(d.ownerUUID))
                .toList();
    }

    public int getShopCount() { return byShopId.size(); }

    /** Update entity UUID when an entity is spawned/loaded. */
    public void updateEntityUUID(UUID shopId, UUID newEntityUUID) {
        ShopEntityData data = byShopId.get(shopId);
        if (data == null) return;
        if (data.entityUUID != null) byEntityId.remove(data.entityUUID);
        data.entityUUID = newEntityUUID;
        byEntityId.put(newEntityUUID, data);
        store.put(COLLECTION, data.shopId.toString(), toJson(data));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /** Load all NPC shops from the active DataStore and rebuild both in-memory indices. */
    private void load() {
        for (JsonObject obj : store.getAll(COLLECTION).values()) {
            ShopEntityData d = fromJson(obj);
            if (d.shopId == null) d.shopId = UUID.randomUUID();
            byShopId.put(d.shopId, d);
            if (d.entityUUID != null) byEntityId.put(d.entityUUID, d);
        }
    }

    /**
     * One-time import of the legacy {@code npc_shops.json} file (in the config dir) into
     * the active DataStore, if it's still empty and storage.autoMigrate is enabled.
     */
    private void migrateLegacyFilesIfNeeded() {
        if (store.hasAnyData(COLLECTION)) return;
        if (!com.zerog.neoessentials.config.ConfigManager.getInstance().isStorageAutoMigrateEnabled()) return;

        Path file = ResourceUtil.getConfigPath("npc_shops.json");
        if (!Files.exists(file)) return;

        int migrated = 0;
        try (Reader r = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<ShopEntityData>>(){}.getType();
            List<ShopEntityData> shops = GSON.fromJson(r, listType);
            if (shops != null) {
                for (ShopEntityData d : shops) {
                    if (d.shopId == null) d.shopId = UUID.randomUUID();
                    store.put(COLLECTION, d.shopId.toString(), toJson(d));
                    migrated++;
                }
            }
        } catch (IOException e) {
            LOGGER.error("[NpcShop] Failed to migrate legacy npc_shops.json: {}", e.getMessage());
        }

        if (migrated > 0) {
            NeoLog.info(LOGGER, LogCategory.GENERAL, "[NpcShop] migrated {} NPC shop record(s) from legacy npc_shops.json into the '{}' storage backend.",
                migrated, com.zerog.neoessentials.storage.StorageManager.getInstance().getActiveType());
        }
    }

    private JsonObject toJson(ShopEntityData d) {
        JsonObject o = new JsonObject();
        o.addProperty("shopId", d.shopId != null ? d.shopId.toString() : null);
        o.addProperty("entityUUID", d.entityUUID != null ? d.entityUUID.toString() : null);
        o.addProperty("shopName", d.shopName);
        o.addProperty("ownerUUID", d.ownerUUID != null ? d.ownerUUID.toString() : null);
        o.addProperty("dimension", d.dimension);
        o.addProperty("spawnX", d.spawnX);
        o.addProperty("spawnY", d.spawnY);
        o.addProperty("spawnZ", d.spawnZ);
        o.addProperty("economyEnabled", d.economyEnabled);
        o.addProperty("entityTypeId", d.entityTypeId);
        o.addProperty("aiEnabled", d.aiEnabled);
        JsonArray listings = new JsonArray();
        for (ShopListing l : d.listings) {
            JsonObject lo = new JsonObject();
            lo.addProperty("itemId", l.itemId());
            lo.addProperty("buyPrice", l.buyPrice() != null ? l.buyPrice().toPlainString() : null);
            lo.addProperty("sellPrice", l.sellPrice() != null ? l.sellPrice().toPlainString() : null);
            lo.addProperty("quantity", l.quantity());
            listings.add(lo);
        }
        o.add("listings", listings);
        return o;
    }

    private ShopEntityData fromJson(JsonObject o) {
        ShopEntityData d = new ShopEntityData();
        d.shopId = has(o, "shopId") ? UUID.fromString(o.get("shopId").getAsString()) : null;
        d.entityUUID = has(o, "entityUUID") ? UUID.fromString(o.get("entityUUID").getAsString()) : null;
        d.shopName = str(o, "shopName");
        d.ownerUUID = has(o, "ownerUUID") ? UUID.fromString(o.get("ownerUUID").getAsString()) : null;
        d.dimension = str(o, "dimension");
        d.spawnX = o.has("spawnX") ? o.get("spawnX").getAsDouble() : 0;
        d.spawnY = o.has("spawnY") ? o.get("spawnY").getAsDouble() : 0;
        d.spawnZ = o.has("spawnZ") ? o.get("spawnZ").getAsDouble() : 0;
        d.economyEnabled = !o.has("economyEnabled") || o.get("economyEnabled").getAsBoolean();
        d.entityTypeId = str(o, "entityTypeId");
        d.aiEnabled = o.has("aiEnabled") && o.get("aiEnabled").getAsBoolean();
        d.listings = new ArrayList<>();
        if (o.has("listings") && o.get("listings").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("listings")) {
                JsonObject lo = el.getAsJsonObject();
                d.listings.add(new ShopListing(
                    str(lo, "itemId"),
                    has(lo, "buyPrice") ? new BigDecimal(lo.get("buyPrice").getAsString()) : null,
                    has(lo, "sellPrice") ? new BigDecimal(lo.get("sellPrice").getAsString()) : null,
                    lo.has("quantity") ? lo.get("quantity").getAsInt() : 0
                ));
            }
        }
        return d;
    }

    private static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() ? e.getAsString() : null;
    }

    public void reload() {
        byShopId.clear(); byEntityId.clear();
        try { load(); NeoLog.info(LOGGER, LogCategory.GENERAL, "[NpcShop] Reloaded {} shop(s).", byShopId.size()); }
        catch (Exception e) { LOGGER.error("[NpcShop] Reload failed", e); }
    }
}
