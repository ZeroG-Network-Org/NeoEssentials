package com.zerog.neoessentials.shop.entity;

import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import com.zerog.neoessentials.util.EntityTypeCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Utility helpers for NeoEssentials NPC shop entities.
 *
 * <p>The default shop NPC is a plain vanilla {@link ArmorStand} — no custom EntityType is
 * registered for it. This avoids the registry-sync disconnect that clients without
 * NeoEssentials installed would otherwise receive: <em>"The server sent registries with
 * unknown keys: ResourceKey[minecraft:entity_type / neoessentials:shop_npc]"</em>.
 *
 * <p>Admins can instead choose ANY already-registered entity type — vanilla or from another
 * installed mod — via {@link ShopEntityData#entityTypeId}. This is safe for the same reason
 * the default is safe: since it's a type some mod (possibly just vanilla) already registered,
 * every client that can even join a server with that mod present already knows about it —
 * there's no new registry key NeoEssentials itself is introducing.
 *
 * <p>The shop identity is stored in {@link net.minecraft.nbt.CompoundTag persistent data}
 * under {@link #NBT_SHOP_ID}. Interactions and AI-leash enforcement are handled by
 * {@link ShopEntityRegistry}.
 */
public final class ShopNpcEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopNpcEntity.class);

    /** Persistent-data key used to tag a shop entity (UUID stored as two longs). */
    public static final String NBT_SHOP_ID = "NeoEssentials_ShopId";

    /** Default entity type id when {@link ShopEntityData#entityTypeId} is unset. */
    public static final String DEFAULT_ENTITY_TYPE_ID = "minecraft:armor_stand";

    private ShopNpcEntity() {}

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create and configure a new entity as a shop NPC.
     *
     * <p>The returned entity has NOT been added to the level yet — call
     * {@code level.addFreshEntity(entity)} after setting its position.
     *
     * @param level        the server level
     * @param shopId       the shopId to embed in persistent data
     * @param shopName     display name shown above the NPC head
     * @param entityTypeId registered entity type id (e.g. {@code "minecraft:villager"}), or
     *                     {@code null}/blank for the default ArmorStand
     * @param aiEnabled    if the resolved type is a {@link Mob}, whether to leave its normal
     *                     AI running (leashed to its spawn point by {@link ShopEntityRegistry})
     *                     instead of freezing it in place; ignored for non-Mob types
     * @return configured entity, ready to spawn
     */
    public static Entity create(Level level, UUID shopId, String shopName, String entityTypeId, boolean aiEnabled) {
        EntityType<?> type = resolveEntityType(entityTypeId);
        Entity entity = EntityTypeCompat.create(type, level);
        if (entity == null) {
            // Type exists but refused to construct (e.g. a boss/singleton entity with special
            // spawn rules) — fall back to the always-safe default rather than returning null.
            NeoLog.warn(LOGGER, LogCategory.GENERAL,
                "Entity type '{}' refused to construct a shop NPC, falling back to armor_stand", entityTypeId);
            entity = new ArmorStand(EntityType.ARMOR_STAND, level);
        }

        if (entity instanceof LivingEntity living) {
            living.setInvulnerable(true);
            living.setSilent(true);
            living.setCustomName(com.zerog.neoessentials.util.MessageUtil.component(
                "commands.neoessentials.npcshop.entity_display_name", shopName));
            living.setCustomNameVisible(true);
        }

        if (entity instanceof ArmorStand stand) {
            // ArmorStand extends LivingEntity (not Mob) — no setNoAi(); stands are stationary
            // by default. NoGravity preserves the classic floating shop-sign look; only applied
            // here, not to other entity types, where it would look visibly wrong (a floating
            // villager, etc).
            stand.setNoGravity(true);
        } else if (entity instanceof Mob mob) {
            if (!aiEnabled) {
                mob.setNoAi(true);
                // Belt-and-braces: NoAI alone already stops goal/target ticking in vanilla, but
                // clearing the goals too means a modded Mob subclass that checks its own goals
                // directly (rather than relying on isEffectiveAi()) still can't move/attack.
                mob.goalSelector.removeAllGoals(g -> true);
                mob.targetSelector.removeAllGoals(g -> true);
            }
            // aiEnabled == true: leave default AI/goals running. ShopEntityRegistry's tick
            // handler leashes it back to its recorded spawn point so it can't wander off.
        }

        entity.getPersistentData().putUUID(NBT_SHOP_ID, shopId);
        return entity;
    }

    /** Backward-compatible overload — always spawns the default ArmorStand with no AI. */
    public static Entity create(Level level, UUID shopId, String shopName) {
        return create(level, shopId, shopName, DEFAULT_ENTITY_TYPE_ID, false);
    }

    /**
     * Resolves an entity type id string to a registered {@link EntityType}, falling back to
     * {@link EntityType#ARMOR_STAND} if the id is blank, unknown, or unparsable — the same
     * fallback {@code /summon} itself uses for a bad id, so this can never crash a shop
     * creation over an admin's typo.
     */
    public static EntityType<?> resolveEntityType(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) return EntityType.ARMOR_STAND;
        try {
            return EntityType.byString(entityTypeId).orElseGet(() -> {
                NeoLog.warn(LOGGER, LogCategory.GENERAL,
                    "Unknown entity type '{}' for a shop NPC, falling back to armor_stand", entityTypeId);
                return EntityType.ARMOR_STAND;
            });
        } catch (Exception e) {
            NeoLog.warn(LOGGER, LogCategory.GENERAL,
                "Invalid entity type id '{}' for a shop NPC, falling back to armor_stand: {}", entityTypeId, e.getMessage());
            return EntityType.ARMOR_STAND;
        }
    }

    /** {@code true} if {@code id} resolves to a real, registered entity type. */
    public static boolean isValidEntityTypeId(String id) {
        if (id == null || id.isBlank()) return true; // blank == default, always valid
        try {
            return EntityType.byString(id).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the shopId embedded in the entity's persistent data, or {@code null}. */
    public static UUID getShopId(Entity entity) {
        var data = entity.getPersistentData();
        return data.hasUUID(NBT_SHOP_ID) ? data.getUUID(NBT_SHOP_ID) : null;
    }

    /** Returns {@code true} if this entity is a NeoEssentials shop NPC. */
    public static boolean isShopNpc(Entity entity) {
        return entity.getPersistentData().hasUUID(NBT_SHOP_ID);
    }
}
