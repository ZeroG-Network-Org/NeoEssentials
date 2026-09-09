package com.zerog.neoessentials.shop.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent data for one NPC shop entity.
 *
 * <p>Stored in {@code neoessentials/npc_shops.json}.
 * The entity in-world stores only the {@link #shopId} in its NBT; all shop
 * data lives here and is looked up when the entity is right-clicked.
 */
public class ShopEntityData {

    /** Unique identifier for this shop (also stored on the entity via NBT). */
    public UUID shopId;

    /** UUID of the Minecraft entity representing this shop in-world. */
    public UUID entityUUID;

    /** Human-readable display name shown in the GUI title. */
    public String shopName;

    /** UUID of the player who created the shop (for admin tracking). */
    public UUID ownerUUID;

    /** Dimension of the entity (e.g. {@code "minecraft:overworld"}). */
    public String dimension;

    /** Approximate spawn coordinates (informational / for /npcshop list) — also the center
     *  point an AI-enabled NPC is leashed to, see {@link ShopEntityRegistry}. */
    public double spawnX, spawnY, spawnZ;

    /** Registered entity type id (e.g. {@code "minecraft:villager"}), or {@code null}/blank
     *  for the original default of a plain {@code minecraft:armor_stand}. Any registered
     *  type — vanilla or from another installed mod — is accepted; unlike a custom
     *  NeoEssentials-registered EntityType, using an already-registered one never causes a
     *  client without NeoEssentials to see an "unknown registry key" disconnect, since the
     *  mod that owns that entity type must already be installed on both sides for it to be
     *  registered at all. */
    public String entityTypeId;

    /** Whether the NPC keeps its normal AI (movement, look-at-player, idle behavior) instead
     *  of being frozen in place. Only meaningful when {@link #entityTypeId} resolves to a
     *  {@link net.minecraft.world.entity.Mob} — ignored entirely for non-Mob types like the
     *  default ArmorStand, which has no AI concept either way. When {@code true}, the NPC is
     *  still kept from wandering off — see {@link ShopEntityRegistry}'s leash-back-to-spawn
     *  tick handler. */
    public boolean aiEnabled = false;

    /** Ordered list of item listings available in this NPC shop. Max 54. */
    public List<ShopListing> listings = new ArrayList<>();

    /** Whether this shop accepts money from the built-in economy (always true for now). */
    public boolean economyEnabled = true;

    /** Total number of successful transactions on this shop. */
    public long totalSalesCount = 0L;

    /** Total money moved through this shop (buy+sell), in the smallest currency unit
     *  (cents) — see {@link com.zerog.neoessentials.shop.entity.NpcShopMenu}, and the
     *  {@code shop_sales} leaderboard board that ranks shops by this value. */
    public long totalRevenueCents = 0L;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void addListing(ShopListing listing) {
        if (listings.size() < 54) listings.add(listing);
    }

    public boolean removeListing(int index) {
        if (index < 0 || index >= listings.size()) return false;
        listings.remove(index);
        return true;
    }

    public String toKey() {
        return shopId != null ? shopId.toString() : "unknown";
    }
}

