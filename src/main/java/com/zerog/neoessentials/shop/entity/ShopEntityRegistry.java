package com.zerog.neoessentials.shop.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.UUID;

/**
 * Intercepts player interactions with NPC shop entities, and leashes AI-enabled ones back to
 * their spawn point so they can't wander off.
 *
 * <p>NeoEssentials avoids registering a custom EntityType so that vanilla clients (and clients
 * without NeoEssentials installed) are never disconnected by an unknown registry key. Instead,
 * shop NPCs are ordinary entities — by default an {@link net.minecraft.world.entity.decoration.ArmorStand},
 * but any already-registered type an admin picks (see {@link ShopNpcEntity}) — whose persistent
 * data carries a {@code NeoEssentials_ShopId} UUID tag that the server reads on interact.
 *
 * <p>Previously this class registered a {@code DeferredRegister<EntityType<?>>} which caused:
 * <em>"The server sent registries with unknown keys:
 * ResourceKey[minecraft:entity_type / neoessentials:shop_npc]"</em>.
 */
@EventBusSubscriber(modid = "neoessentials")
public class ShopEntityRegistry {

    /** How far (in blocks) an AI-enabled shop NPC may stray from its recorded spawn point
     *  before being snapped back — see {@link #onEntityTick}. A fixed constant rather than a
     *  per-shop setting keeps the command surface simple; large enough that a Mob's normal
     *  wander/look-around AI still looks natural, small enough it can't leave the immediate
     *  shop area. */
    private static final double LEASH_RADIUS = 6.0;
    private static final double LEASH_RADIUS_SQR = LEASH_RADIUS * LEASH_RADIUS;

    private ShopEntityRegistry() {}

    /**
     * Intercept right-click on any entity that carries our shop NBT tag.
     * Cancel the event to suppress the vanilla interaction (equip GUI, trading, etc.) and open
     * the shop instead.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Only handle main-hand interact and server-side
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer sp)) return;

        Entity target = event.getTarget();
        CompoundTag persistentData = target.getPersistentData();
        if (!persistentData.hasUUID(ShopNpcEntity.NBT_SHOP_ID)) return;

        // It's a NeoEssentials NPC shop — cancel vanilla interaction
        event.setCanceled(true);

        UUID shopId = persistentData.getUUID(ShopNpcEntity.NBT_SHOP_ID);
        ShopEntityData shopData = ShopEntityManager.getInstance().getByShopId(shopId);

        if (shopData == null) {
            sp.sendSystemMessage(com.zerog.neoessentials.util.MessageUtil.component(
                    "commands.neoessentials.npcshop.entity_unlinked"));
            return;
        }
        if (shopData.listings.isEmpty()) {
            sp.sendSystemMessage(com.zerog.neoessentials.util.MessageUtil.component(
                    "commands.neoessentials.npcshop.no_items"));
            return;
        }
        if (!com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(
                sp.getUUID(), "neoessentials.shop.use")) {
            sp.sendSystemMessage(com.zerog.neoessentials.util.MessageUtil.component(
                    "commands.neoessentials.shop.no_permission_use"));
            return;
        }

        sp.openMenu(new NpcShopMenu.NpcShopMenuProvider(shopData),
                buf -> buf.writeUtf(shopId.toString()));
    }

    /**
     * Leashes an AI-enabled shop NPC back to its recorded spawn point once it strays past
     * {@link #LEASH_RADIUS} — a normal Mob's own AI (wander, look-at-player, idle behavior) is
     * otherwise left completely alone, so it still looks/behaves like a real mob up close.
     *
     * <p>A no-AI shop NPC ({@code mob.isNoAi()}) is skipped entirely here — {@link ShopNpcEntity}
     * already froze it in place at creation time, so there's nothing to clamp. Checking
     * {@code instanceof Mob} first (before touching persistent data at all) keeps this cheap for
     * the vast majority of entities in a world that were never a shop NPC to begin with.
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.isNoAi()) return;
        if (!ShopNpcEntity.isShopNpc(mob)) return;

        ShopEntityData shopData = ShopEntityManager.getInstance().getByEntityUUID(mob.getUUID());
        if (shopData == null) return;

        double dx = mob.getX() - shopData.spawnX;
        double dy = mob.getY() - shopData.spawnY;
        double dz = mob.getZ() - shopData.spawnZ;
        if (dx * dx + dy * dy + dz * dz > LEASH_RADIUS_SQR) {
            mob.teleportTo(shopData.spawnX, shopData.spawnY, shopData.spawnZ);
            mob.getNavigation().stop();
        }
    }
}
