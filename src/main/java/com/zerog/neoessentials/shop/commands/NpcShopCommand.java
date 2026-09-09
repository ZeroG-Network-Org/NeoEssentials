package com.zerog.neoessentials.shop.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.shop.entity.*;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code /npcshop} — create, manage, and inspect NPC entity shops.
 *
 * <pre>
 *   /npcshop create <name>                       — spawn NPC at current position
 *   /npcshop remove                              — remove the nearest NPC shop
 *   /npcshop additem <shopId> <item> <buy> <sell> <qty>
 *   /npcshop removeitem <shopId> <index>
 *   /npcshop list                                — list all NPC shops
 *   /npcshop info <shopId>                       — info about one shop
 *   /npcshop reload                              — reload npc_shops.json
 *   /npcshop respawn <shopId>                    — re-summon a lost NPC entity
 *   /npcshop entitytype <shopId> <entityType> <ai> — change entity type / AI mode (respawns it)
 * </pre>
 *
 * <p>{@code <entityType>} accepts any registered entity type id, vanilla or from another
 * installed mod (e.g. {@code minecraft:villager}), not just the default {@code minecraft:armor_stand}
 * — see {@link ShopNpcEntity}'s javadoc for why that's safe. {@code <ai>} only matters when the
 * type is a {@link net.minecraft.world.entity.Mob}: {@code true} leaves its normal AI running
 * (leashed to its spawn point so it can't wander off), {@code false} freezes it in place exactly
 * like the default ArmorStand.
 *
 * All sub-commands require {@code neoessentials.shop.npc.manage}.
 */
public class NpcShopCommand {

    private static final String PERM = "neoessentials.shop.npc.manage";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!com.zerog.neoessentials.config.ConfigManager.isShopModuleEnabled()) {
            return;
        }
        if (!com.zerog.neoessentials.config.ConfigManager.getInstance().isCommandEnabled("npcshop")) {
            return;
        }

        var node = Commands.literal("npcshop")
                .requires(src -> src.hasPermission(3) ||
                        (src.getEntity() != null && PermissionAPI.hasPermission(src.getEntity().getUUID(), PERM)))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeCreate(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("remove")
                        .executes(ctx -> executeRemove(ctx.getSource())))
                .then(Commands.literal("additem")
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .suggests(NpcShopCommand::suggestShopIds)
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("buyPrice", DoubleArgumentType.doubleArg(-1))
                                                .then(Commands.argument("sellPrice", DoubleArgumentType.doubleArg(-1))
                                                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> executeAddItem(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "shopId"),
                                                                        StringArgumentType.getString(ctx, "item"),
                                                                        DoubleArgumentType.getDouble(ctx, "buyPrice"),
                                                                        DoubleArgumentType.getDouble(ctx, "sellPrice"),
                                                                        IntegerArgumentType.getInteger(ctx, "quantity")))))))))
                .then(Commands.literal("removeitem")
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .suggests(NpcShopCommand::suggestShopIds)
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(ctx -> executeRemoveItem(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "shopId"),
                                                IntegerArgumentType.getInteger(ctx, "index"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> executeList(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .suggests(NpcShopCommand::suggestShopIds)
                                .executes(ctx -> executeInfo(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "shopId")))))
                .then(Commands.literal("reload")
                        .executes(ctx -> executeReload(ctx.getSource())))
                .then(Commands.literal("respawn")
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .suggests(NpcShopCommand::suggestShopIds)
                                .executes(ctx -> executeRespawn(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "shopId")))))
                .then(Commands.literal("entitytype")
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .suggests(NpcShopCommand::suggestShopIds)
                                .then(Commands.argument("entityType", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString), builder))
                                        .then(Commands.argument("ai", BoolArgumentType.bool())
                                                .executes(ctx -> executeEntityType(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "shopId"),
                                                        StringArgumentType.getString(ctx, "entityType"),
                                                        BoolArgumentType.getBool(ctx, "ai")))))))
                .executes(ctx -> executeHelp(ctx.getSource()));

        dispatcher.register(node);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestShopIds(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
            ShopEntityManager.getInstance().getAll().stream().map(d -> d.shopId.toString()), builder);
    }

    // ── /npcshop create <name> ────────────────────────────────────────────────

    private static int executeCreate(CommandSourceStack src, String name) {
        try {
            ServerPlayer player = src.getPlayerOrException();

            ShopEntityData shopData = new ShopEntityData();
            shopData.shopId    = UUID.randomUUID();
            shopData.ownerUUID = player.getUUID();
            shopData.shopName  = name;
            shopData.dimension = player.level().dimension().location().toString();
            shopData.spawnX    = player.getX();
            shopData.spawnY    = player.getY();
            shopData.spawnZ    = player.getZ();

            // Spawn a vanilla ArmorStand by default — no custom EntityType needed client-side.
            // Use /npcshop entitytype afterward to switch to a different (vanilla or modded)
            // entity type and/or enable its AI.
            Entity npc = ShopNpcEntity.create(com.zerog.neoessentials.util.LevelCompat.of(player), shopData.shopId, name,
                    shopData.entityTypeId, shopData.aiEnabled);
            npc.setPos(player.getX(), player.getY(), player.getZ());
            shopData.entityUUID = npc.getUUID();

            com.zerog.neoessentials.util.LevelCompat.of(player).addFreshEntity(npc);
            ShopEntityManager.getInstance().register(shopData);

            src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.created",
                    name, shopData.shopId, shopData.shopId.toString().substring(0, 8)), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.error", e.getMessage()));
            return 0;
        }
    }

    // ── /npcshop remove ───────────────────────────────────────────────────────

    private static int executeRemove(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();

            // Find nearest NeoEssentials shop entity (any type) within 5 blocks
            List<Entity> nearby = com.zerog.neoessentials.util.LevelCompat.of(player).getEntitiesOfClass(
                    Entity.class,
                    new AABB(player.getX() - 5, player.getY() - 5, player.getZ() - 5,
                             player.getX() + 5, player.getY() + 5, player.getZ() + 5),
                    ShopNpcEntity::isShopNpc);

            if (nearby.isEmpty()) {
                src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.remove_none_nearby"));
                return 0;
            }

            Entity target = nearby.getFirst();
            UUID shopId = ShopNpcEntity.getShopId(target);
            target.discard();

            if (shopId != null) {
                ShopEntityManager.getInstance().remove(shopId);
                src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.removed"), true);
            } else {
                src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.entity_removed_unlinked"), true);
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.error", e.getMessage()));
            return 0;
        }
    }

    // ── /npcshop additem ──────────────────────────────────────────────────────

    private static int executeAddItem(CommandSourceStack src, String shopIdStr,
                                       String itemId, double buyPrice, double sellPrice, int qty) {
        ShopEntityData shop = resolve(src, shopIdStr);
        if (shop == null) return 0;

        BigDecimal buy  = buyPrice  >= 0 ? BigDecimal.valueOf(buyPrice)  : null;
        BigDecimal sell = sellPrice >= 0 ? BigDecimal.valueOf(sellPrice) : null;

        if (buy == null && sell == null) {
            src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.additem_both_disabled"));
            return 0;
        }

        shop.addListing(new ShopListing("minecraft:" + itemId.replace("minecraft:", ""), buy, sell, qty));
        ShopEntityManager.getInstance().register(shop);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.additem_success",
                itemId, shop.shopName, shop.listings.size() - 1), false);
        return 1;
    }

    // ── /npcshop removeitem ───────────────────────────────────────────────────

    private static int executeRemoveItem(CommandSourceStack src, String shopIdStr, int index) {
        ShopEntityData shop = resolve(src, shopIdStr);
        if (shop == null) return 0;
        if (!shop.removeListing(index)) {
            src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.removeitem_invalid_index",
                    index, shop.listings.size()));
            return 0;
        }
        ShopEntityManager.getInstance().register(shop);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.removeitem_success", index), false);
        return 1;
    }

    // ── /npcshop list ─────────────────────────────────────────────────────────

    private static int executeList(CommandSourceStack src) {
        var all = ShopEntityManager.getInstance().getAll();
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.list_header", all.size()), false);
        if (all.isEmpty()) {
            src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.list_empty"), false);
        } else {
            for (ShopEntityData d : all) {
                src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.list_entry",
                        d.shopName,
                        d.shopId.toString().substring(0, 8),
                        d.listings.size(),
                        String.format("%.0f", d.spawnX), String.format("%.0f", d.spawnY), String.format("%.0f", d.spawnZ),
                        d.dimension.replace("minecraft:", "")
                ), false);
            }
        }
        return all.size();
    }

    // ── /npcshop info <shopId> ────────────────────────────────────────────────

    private static int executeInfo(CommandSourceStack src, String shopIdStr) {
        ShopEntityData shop = resolve(src, shopIdStr);
        if (shop == null) return 0;
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.info_header"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.info_name", shop.shopName), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.info_id", shop.shopId), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.info_pos",
                (int) shop.spawnX, (int) shop.spawnY, (int) shop.spawnZ), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.info_items", shop.listings.size()), false);
        for (int i = 0; i < shop.listings.size(); i++) {
            ShopListing l = shop.listings.get(i);
            final int idx = i;
            src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.info_listing",
                    idx, l.quantity(), l.itemId().replace("minecraft:", ""),
                    l.canBuy()  ? l.buyPrice().toPlainString()  : "§7—",
                    l.canSell() ? l.sellPrice().toPlainString() : "§7—"
            ), false);
        }
        return 1;
    }

    // ── /npcshop reload ───────────────────────────────────────────────────────

    private static int executeReload(CommandSourceStack src) {
        ShopEntityManager.getInstance().reload();
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.reload_success",
                ShopEntityManager.getInstance().getShopCount()), true);
        return 1;
    }

    // ── /npcshop respawn <shopId> ─────────────────────────────────────────────

    /**
     * Re-summon the NPC entity for an existing shop whose entity was lost
     * (e.g. killed by void damage, which bypasses {@code setInvulnerable}, or removed
     * by an unrelated admin/anticheat command) without losing its listings — the
     * listings live in {@link ShopEntityData}, keyed by {@code shopId}, independent
     * of the in-world entity. Re-summons using the shop's current
     * {@link ShopEntityData#entityTypeId}/{@link ShopEntityData#aiEnabled}.
     */
    private static int executeRespawn(CommandSourceStack src, String shopIdStr) {
        ShopEntityData shop = resolve(src, shopIdStr);
        if (shop == null) return 0;

        var server = src.getServer();
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(shop.dimension)) { level = l; break; }
        }
        if (level == null) {
            src.sendFailure(MessageUtil.component(
                    "commands.neoessentials.npcshop.respawn_dimension_missing", shop.dimension));
            return 0;
        }

        if (shop.entityUUID != null && level.getEntity(shop.entityUUID) != null) {
            src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.respawn_already_exists"));
            return 0;
        }

        Entity npc = ShopNpcEntity.create(level, shop.shopId, shop.shopName, shop.entityTypeId, shop.aiEnabled);
        npc.setPos(shop.spawnX, shop.spawnY, shop.spawnZ);
        level.addFreshEntity(npc);
        ShopEntityManager.getInstance().updateEntityUUID(shop.shopId, npc.getUUID());

        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.respawn_success", shop.shopName), true);
        return 1;
    }

    // ── /npcshop entitytype <shopId> <entityType> <ai> ───────────────────────────

    /**
     * Changes an existing shop's entity type and/or AI mode — vanilla or from any other
     * installed mod, see {@link ShopNpcEntity}'s javadoc for why that's safe. Despawns the
     * shop's current in-world entity (if present) and immediately respawns it with the new
     * settings at the same position, so the change is visible without a separate
     * {@code /npcshop respawn}.
     */
    private static int executeEntityType(CommandSourceStack src, String shopIdStr, String entityTypeId, boolean aiEnabled) {
        ShopEntityData shop = resolve(src, shopIdStr);
        if (shop == null) return 0;

        if (!ShopNpcEntity.isValidEntityTypeId(entityTypeId)) {
            src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.entitytype_unknown", entityTypeId));
            return 0;
        }

        shop.entityTypeId = entityTypeId;
        shop.aiEnabled = aiEnabled;
        ShopEntityManager.getInstance().register(shop);

        var server = src.getServer();
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(shop.dimension)) { level = l; break; }
        }
        if (level == null) {
            // Data saved either way — the new type/AI will apply next time the shop is
            // respawned in a loaded dimension.
            src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.entitytype_success_no_respawn",
                    shop.shopName, entityTypeId), true);
            return 1;
        }

        if (shop.entityUUID != null) {
            Entity old = level.getEntity(shop.entityUUID);
            if (old != null) old.discard();
        }

        Entity npc = ShopNpcEntity.create(level, shop.shopId, shop.shopName, shop.entityTypeId, shop.aiEnabled);
        npc.setPos(shop.spawnX, shop.spawnY, shop.spawnZ);
        level.addFreshEntity(npc);
        ShopEntityManager.getInstance().updateEntityUUID(shop.shopId, npc.getUUID());

        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.entitytype_success",
                shop.shopName, entityTypeId, aiEnabled), true);
        return 1;
    }

    // ── /npcshop help ─────────────────────────────────────────────────────────

    private static int executeHelp(CommandSourceStack src) {
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_header"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_create"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_remove"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_additem"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_removeitem"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_list"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_info"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_reload"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_respawn"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_entitytype"), false);
        src.sendSuccess(() -> MessageUtil.component("commands.neoessentials.npcshop.help_price_hint"), false);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ShopEntityData resolve(CommandSourceStack src, String shopIdStr) {
        ShopEntityData shop = ShopEntityManager.getInstance().getByShopId(shopIdStr);
        if (shop == null) {
            // Allow prefix matching (first 8 chars)
            for (ShopEntityData d : ShopEntityManager.getInstance().getAll()) {
                if (d.shopId.toString().startsWith(shopIdStr)) { shop = d; break; }
            }
        }
        if (shop == null) {
            src.sendFailure(MessageUtil.component("commands.neoessentials.npcshop.not_found", shopIdStr));
        }
        return shop;
    }
}

