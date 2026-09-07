package com.zerog.neoessentials.integrations;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionStorage;
import com.zerog.neoessentials.permissions.PermissionUser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Grants a linked player's NeoEssentials permission group from a mapped Discord role
 * ({@code discordrolesync.json}'s {@code roleGroupMap}) — Direction A ("Discord -> Minecraft")
 * of the role-sync idea in DISCORD_INTEGRATION_PLAN.md §3.1. Only ever grants/upgrades a group;
 * never demotes a player who no longer holds a mapped role back to a default group, since
 * NeoEssentials' single-group-per-user model has no reliable way to tell "this group was set
 * by role sync" apart from "an admin set this group on purpose" (unlike
 * {@link com.zerog.neoessentials.webdashboard.security.PermissionRoleSyncTask}, which tracks
 * that distinction explicitly for dashboard roles).
 *
 * <p>Only operates when {@link PermissionAPI#isUsingExternal()} is false — this can only write
 * into NeoEssentials' own internal permission groups, not push into an external plugin
 * (LuckPerms/FTB Ranks) NeoEssentials doesn't own.
 *
 * <p>Currently only ever resolves roles through {@link ChatIntegrationManager#findDiscordRoleIds}
 * checking whichever registered adapter is ready — in practice that means DCIntegration only for
 * now, since SDLink's role lookup is a public-API dead end and Mc2Discord's isn't implemented yet
 * (see DISCORD_INTEGRATION_PLAN.md §2.2/§4.4).
 *
 * <p>Runs a periodic sweep over online players (self-heals a role change made outside this
 * server) plus an immediate check on join, mirroring {@code PermissionRoleSyncTask}'s shape.
 */
@EventBusSubscriber(modid = "neoessentials")
public class DiscordRoleSyncTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordRoleSyncTask.class);

    private static ScheduledExecutorService scheduler;

    private DiscordRoleSyncTask() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        start();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stop();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        // Shared bounded pool, not a fresh Thread per join — see DelayedTaskExecutor.
        com.zerog.neoessentials.util.DelayedTaskExecutor.schedule(() -> syncPlayer(uuid), 0);
    }

    /** Starts the periodic sweep. No-op if disabled in config or already running. */
    public static synchronized void start() {
        if (!isEnabled()) return;
        if (scheduler != null && !scheduler.isShutdown()) return;

        int intervalSeconds = getIntervalSeconds();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NeoEssentials-DiscordRoleSync");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(DiscordRoleSyncTask::syncAll, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Discord role sync started (every {}s)", intervalSeconds);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static void syncAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncPlayer(player.getUUID());
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Discord role-sync sweep failed", e);
        }
    }

    private static void syncPlayer(UUID minecraftUuid) {
        try {
            if (!isEnabled()) return;
            if (PermissionAPI.isUsingExternal()) return; // Can't write into an external plugin's groups.

            Map<String, String> roleGroupMap = getRoleGroupMap();
            if (roleGroupMap.isEmpty()) return;

            Optional<String> discordId = ChatIntegrationManager.findLinkedDiscordId(minecraftUuid);
            if (discordId.isEmpty()) return;

            List<String> roleIds = ChatIntegrationManager.findDiscordRoleIds(minecraftUuid);
            if (roleIds.isEmpty()) return;

            String bestGroup = null;
            int bestPriority = Integer.MIN_VALUE;
            for (String roleId : roleIds) {
                String candidateGroup = roleGroupMap.get(roleId);
                if (candidateGroup == null) continue;
                PermissionGroup group = PermissionAPI.getManager().getGroup(candidateGroup);
                if (group == null) continue; // Configured group name doesn't exist — skip it.
                if (bestGroup == null || group.getPriority() > bestPriority) {
                    bestGroup = group.getName();
                    bestPriority = group.getPriority();
                }
            }
            if (bestGroup == null) return; // Player holds none of the mapped roles — leave them alone.

            String targetGroup = bestGroup;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                try {
                    PermissionManager manager = PermissionAPI.getManager();
                    PermissionUser user = manager.getUser(minecraftUuid);
                    if (targetGroup.equalsIgnoreCase(user.getGroup())) return;

                    user.setGroup(targetGroup);
                    manager.clearCache();
                    PermissionStorage.save(manager);
                    NeoLog.info(LOGGER, LogCategory.DISCORD, "Discord role sync: set group '{}' for {} (matched a mapped Discord role)", targetGroup, minecraftUuid);
                } catch (Exception e) {
                    NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to apply Discord role sync group change for " + minecraftUuid, e);
                }
            });
        } catch (Exception e) {
            NeoLog.warn(LOGGER, LogCategory.DISCORD, "Discord role sync failed for {}: {}", minecraftUuid, e.getMessage());
        }
    }

    private static JsonObject getConfigRoot() {
        try {
            JsonObject root = ConfigManager.getInstance().getConfig(ConfigManager.DISCORD_ROLE_SYNC_CONFIG);
            return root.has("discordRoleSync") ? root.getAsJsonObject("discordRoleSync") : null;
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Failed to read discordrolesync.json", e);
            return null;
        }
    }

    private static boolean isEnabled() {
        JsonObject config = getConfigRoot();
        return config != null && config.has("enabled") && config.get("enabled").getAsBoolean();
    }

    private static int getIntervalSeconds() {
        JsonObject config = getConfigRoot();
        if (config != null && config.has("intervalSeconds")) return config.get("intervalSeconds").getAsInt();
        return 300;
    }

    private static Map<String, String> getRoleGroupMap() {
        JsonObject config = getConfigRoot();
        if (config == null || !config.has("roleGroupMap")) return Map.of();
        JsonObject mapObj = config.getAsJsonObject("roleGroupMap");
        Map<String, String> map = new java.util.HashMap<>();
        for (String key : mapObj.keySet()) {
            map.put(key, mapObj.get(key).getAsString());
        }
        return map;
    }
}
