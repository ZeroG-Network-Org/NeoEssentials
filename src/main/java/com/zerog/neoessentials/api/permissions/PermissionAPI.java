
    package com.zerog.neoessentials.api.permissions;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;

import com.zerog.neoessentials.permissions.ExternalPermissionAdapter;
import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;

public class PermissionAPI {
    private static PermissionManager manager;
    private static ExternalPermissionAdapter externalAdapter = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionAPI.class);

    /**
     * When {@code true} the permission system failed to initialise at startup.
     * All permission checks immediately fall back to vanilla OP status so that
     * server operators are never locked out of administrative commands.
     */
    private static volatile boolean emergencyMode = false;

    // ── Emergency mode ────────────────────────────────────────────────────────

    /**
     * Activates or deactivates emergency mode.  Called by {@link
     * com.zerog.neoessentials.permissions.PermissionSystem} when initialisation
     * fails so that OP players retain access instead of crashing the server.
     */
    @SuppressWarnings("unused") // called from PermissionSystem
    public static void setEmergencyMode(boolean active) {
        if (active != emergencyMode) {
            emergencyMode = active;
            if (active) {
                LOGGER.warn("╔══════════════════════════════════════════════════════════════╗");
                LOGGER.warn("║  PERMISSION SYSTEM — EMERGENCY MODE ACTIVE                   ║");
                LOGGER.warn("║  The permission system failed to initialise correctly.        ║");
                LOGGER.warn("║  ALL permission checks will be answered by vanilla OP status. ║");
                LOGGER.warn("║  Run /neoe reload once the config issue has been resolved.    ║");
                LOGGER.warn("╚══════════════════════════════════════════════════════════════╝");
            } else {
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "Permission system emergency mode deactivated — normal checks resumed.");
            }
        }
    }

    /** Returns {@code true} when the system is running in emergency (OP-only) mode. */
    @SuppressWarnings("unused") // called from PermissionSystem
    public static boolean isEmergencyMode() {
        return emergencyMode;
    }

    /**
     * Set the built-in permission manager (default system).
     */
    public static void setManager(PermissionManager m) {
        manager = m;
    }

    /**
     * Set an external permission adapter (e.g., LuckPerms, FTB Ranks).
     * If set, all permission checks will be delegated to this adapter.
     */
    public static void setExternalAdapter(ExternalPermissionAdapter adapter) {
        externalAdapter = adapter;
        NeoLog.info(LOGGER, LogCategory.PERMISSIONS, "External permission adapter set: " + (adapter != null ? adapter.getName() : "none"));
    }

    /**
     * Returns the current external permission adapter, or null if using built-in.
     */
    @SuppressWarnings("unused") // Public API method
    public static ExternalPermissionAdapter getExternalAdapter() {
        return externalAdapter;
    }

    /**
     * Returns true if using an external permission system.
     */
    public static boolean isUsingExternal() {
        return externalAdapter != null;
    }

    public static boolean hasPermission(UUID uuid, String permission) {
        return hasPermission(uuid, permission, com.zerog.neoessentials.permissions.PermissionContext.EMPTY);
    }

    /**
     * Context-aware permission check.  The permission node is first passed through the
     * alias resolver ({@link com.zerog.neoessentials.permissions.PermissionAliasManager})
     * before the 5-step resolution chain is executed.
     *
     * @param uuid       the player's UUID (must not be null)
     * @param permission the permission node to check
     * @param context    the player's runtime context; pass
     *                   {@link com.zerog.neoessentials.permissions.PermissionContext#EMPTY}
     *                   when no context is available
     */
    public static boolean hasPermission(UUID uuid, String permission,
                                        com.zerog.neoessentials.permissions.PermissionContext context) {
        return hasPermission(uuid, permission, context, true);
    }

    /**
     * Same 5-step resolution chain as {@link #hasPermission}, but with both OP-bypass
     * shortcuts (the {@code opsBypassPermissions} fast path and the vanilla-OP last-resort
     * fallback) disabled.
     *
     * <p>Use this for permission nodes that gate a graded/opt-in benefit rather than an
     * admin capability — e.g. per-tier sell multipliers. The regular {@link #hasPermission}
     * treats every node the same way, so an OP with {@code opsBypassPermissions} enabled
     * (the default) would silently pass a check for {@code
     * neoessentials.economy.sellmultiplier.300} despite never having been granted it,
     * tripling their sell income without anyone intending that.
     */
    public static boolean hasPermissionStrict(UUID uuid, String permission) {
        return hasPermission(uuid, permission, com.zerog.neoessentials.permissions.PermissionContext.EMPTY, false);
    }

    private static boolean hasPermission(UUID uuid, String permission,
                                        com.zerog.neoessentials.permissions.PermissionContext context,
                                        boolean allowOpBypass) {
        // Validate input parameters
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.hasPermission: UUID is null");
            return false;
        }
        if (permission == null || permission.trim().isEmpty()) {
            LOGGER.warn("PermissionAPI.hasPermission: Permission string is null or empty");
            return false;
        }

        // ── Alias resolution ──────────────────────────────────────────────────
        // Map legacy / short alias nodes to their canonical NeoEssentials equivalents
        // before running any other check.
        try {
            permission = com.zerog.neoessentials.permissions.PermissionAliasManager
                .getInstance().resolve(permission);
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Alias resolution failed for '{}': {}", permission, e.getMessage());
        }

        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══ PERMISSION CHECK ═══");
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Player UUID: {}", uuid);
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Permission: {}", permission);
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "External adapter: {}", (externalAdapter != null ? externalAdapter.getName() : "NONE"));
        if (context != null && context != com.zerog.neoessentials.permissions.PermissionContext.EMPTY) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Context: world={} time={} gamemode={}", context.worldId, context.dayTime, context.gamemode);
        }

        // ── Emergency mode — permission system failed to start ────────────────
        // Grant access immediately by OP status so admins can fix the issue.
        if (emergencyMode) {
            boolean isOp = isPlayerOpped(uuid);
            LOGGER.warn("EMERGENCY MODE active — {} for '{}' (player is OP: {})",
                    isOp ? "GRANTED" : "DENIED", permission, isOp);
            return isOp;
        }

        // ── Fast-path: OP bypass (runs BEFORE any permission system) ──────────
        // opsBypassPermissions: true means OPs skip all checks entirely.
        // Different from vanillaOpFallback (which runs AFTER all checks).
        if (allowOpBypass && com.zerog.neoessentials.config.ConfigManager.getInstance().isOpsBypassPermissionsEnabled()) {
            if (isPlayerOpped(uuid)) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Player is OP - bypassing permission check (opsBypassPermissions)");
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: TRUE (op bypass)");
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
                return true;
            }
        }

        // ── External permission adapter path ──────────────────────────────────
        // Try external first. If unhealthy or throwing, fall through to internal
        // and then to the registry-default / vanilla-OP fallbacks.
        if (externalAdapter != null) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Using external permission system: {}", externalAdapter.getName());
            boolean externalAvailable = externalAdapter.isAvailable() && externalAdapter.isHealthy();

            // explicitDeny caches the result of isExplicitlyDenied() so that we avoid
            // calling queryTristate a second time inside checkRegistryDefault.
            // null  = not yet determined (adapter unavailable or threw)
            // true  = adapter confirmed an intentional revocation (Tristate.FALSE)
            // false = adapter said UNDEFINED (no opinion) or TRUE
            Boolean explicitDeny = null;

            if (externalAvailable) {
                try {
                    boolean hasExternalPerm = externalAdapter.hasPermission(uuid, permission);
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "External system returned: {}", hasExternalPerm);
                    if (hasExternalPerm) {
                        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: TRUE (external)");
                        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
                        return true;
                    }
                    // Not explicitly granted — check once whether it is explicitly denied.
                    // Caching the result here avoids a second queryTristate call inside
                    // checkRegistryDefault, which would double-count consecutive failures.
                    try {
                        explicitDeny = externalAdapter.isExplicitlyDenied(uuid, permission);
                    } catch (Exception ex2) {
                        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "isExplicitlyDenied threw for '{}' — treating as not denied: {}",
                                permission, ex2.getMessage());
                        explicitDeny = false;
                    }
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "External '{}': no explicit grant; explicitDeny={}", permission, explicitDeny);
                } catch (Exception ex) {
                    LOGGER.warn("External permission adapter '{}' threw during hasPermission('{}') — falling back: {}",
                            externalAdapter.getName(), permission, ex.getMessage());
                    // fall through to internal then registry-default / vanilla-OP fallback
                }
            } else {
                LOGGER.warn("External permission adapter '{}' is UNHEALTHY (failures: {}) — using internal/registry fallback",
                        externalAdapter.getName(), externalAdapter.getConsecutiveFailures());
            }

            // ── Internal-manager fallback (external failed or denied) ─────────
            if (manager != null) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Using internal permission system (external adapter fallback)");
                boolean hasInternalPerm = manager.hasPermission(uuid, permission, context);
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Internal fallback returned: {}", hasInternalPerm);
                if (hasInternalPerm) {
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: TRUE (internal fallback)");
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
                    return true;
                }
                // Internal also said "no" — try registry defaults before vanilla-OP fallback.
            }

            // ── Registry-default fallback ─────────────────────────────────────
            // Apply NeoEssentials documented defaults unconditionally so that users
            // are never locked out simply because LuckPerms became temporarily
            // unhealthy or has no explicit node for a permission.
            //
            // • When the adapter is healthy we already know whether it explicitly denied
            //   the node (cached in explicitDeny above) — pass that result directly to
            //   avoid a second LuckPerms queryTristate call and the double-failure-count
            //   bug that went with it.
            //
            // • When the adapter is unhealthy / unavailable (explicitDeny == null) we
            //   cannot distinguish "denied" from "unknown", so we conservatively treat
            //   the node as not explicitly denied and still honour the registry default.
            //   This is the safer choice: grant defaults rather than lock everyone out.
            boolean explicitlyDenied = Boolean.TRUE.equals(explicitDeny); // false when null (unknown) or false
            if (!explicitlyDenied) {
                boolean registryDefault = checkRegistryDefaultNoAdapterCall(permission);
                if (registryDefault) {
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: TRUE (registry default — external had no opinion or was unavailable)");
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
                    return true;
                }
            } else {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Registry default suppressed: external adapter explicitly denied '{}'", permission);
            }

            // ── Vanilla OP fallback (last resort after external+internal both failed/denied) ──
            return allowOpBypass && checkVanillaOpFallback(uuid, permission, "external+internal");
        }

        // ── Pure-internal path (no external adapter configured) ───────────────
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Using INTERNAL permission system");
        if (manager == null) {
            LOGGER.warn("PermissionAPI.hasPermission: PermissionManager is null");
            // No manager at all — fall straight to vanilla-OP fallback
            return allowOpBypass && checkVanillaOpFallback(uuid, permission, "no-manager");
        }

        boolean hasInternalPerm = manager.hasPermission(uuid, permission, context);
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Internal system returned: {}", hasInternalPerm);
        if (hasInternalPerm) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: TRUE (internal)");
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
            return true;
        }

        // ── Registry-default fallback (internal-only path) ────────────────────
        // No external adapter is active; check whether the permission has
        // defaultValue=true in the registry and grant it if so.
        boolean registryDefault = checkRegistryDefaultNoAdapterCall(permission);
        if (registryDefault) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: TRUE (registry default — internal had no entry)");
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
            return true;
        }

        // Internal said "no" — vanilla-OP fallback is the last resort.
        return allowOpBypass && checkVanillaOpFallback(uuid, permission, "internal");
    }

    /**
     * Registry-default fallback — <em>without</em> calling back into the external adapter.
     *
     * <p>Used by both:
     * <ul>
     *   <li>The external-adapter path in {@link #hasPermission} where the
     *       explicit-deny status is already known (cached as {@code explicitDeny}) so
     *       a second {@code queryTristate} call is unnecessary and would double-count
     *       consecutive failures, potentially flipping the adapter to "unhealthy" faster.</li>
     *   <li>The pure-internal path where no external adapter is configured at all.</li>
     * </ul>
     *
     * <p>The caller is responsible for checking explicit-deny <em>before</em>
     * calling this method and skipping it when an explicit deny is confirmed.
     *
     * @param permission the permission node to check
     * @return {@code true} when the permission is registered with {@code defaultValue=true}
     */
    private static boolean checkRegistryDefaultNoAdapterCall(String permission) {
        try {
            PermissionRegistry registry = PermissionRegistry.getInstance();
            PermissionRegistry.PermissionInfo info = registry.getPermissionInfo(permission);
            if (info == null || !info.getDefaultValue()) {
                return false;
            }
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Registry default applies for '{}' (defaultValue=true, explicit-deny already confirmed as false)", permission);
            return true;
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Error checking registry default (no-adapter path) for '{}': {}", permission, e.getMessage());
            return false;
        }
    }

    /**
     * Vanilla-OP last-resort fallback.
     *
     * <p>Fires after <em>all</em> permission systems have been consulted and
     * none granted the requested node.  If {@code vanillaOpFallback} is enabled
     * in config and the player holds vanilla OP status, permission is granted and
     * a {@code DEBUG} message is logged (first occurrence logged at {@code WARN}
     * to alert admins that the fallback is in use).
     *
     * @param source short label for log messages, e.g. {@code "internal"} or
     *               {@code "external+internal"}
     */
    private static boolean checkVanillaOpFallback(UUID uuid, String permission, String source) {
        if (com.zerog.neoessentials.config.ConfigManager.getInstance().isVanillaOpFallbackEnabled()) {
            if (isPlayerOpped(uuid)) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Vanilla OP fallback (after {}): granting '{}' to OP {}", source, permission, uuid);
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: TRUE (vanillaOpFallback)");
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
                return true;
            }
        }
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Result: FALSE ({} denied, no OP fallback triggered)", source);
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "═══════════════════════");
        return false;
    }


    /**
     * Checks if a player is opped by their UUID.
     */
    private static boolean isPlayerOpped(UUID uuid) {
        try {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                // Try to get the player directly and check their permission level
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    return com.zerog.neoessentials.util.PermissionLevelCompat.hasPermission(player, 2); // Op level 2 or higher
                }
                
                // If player is offline, check the ops file
                var profileCache = server.services().nameToIdCache();
                if (profileCache != null) {
                    net.minecraft.server.players.NameAndId profile = profileCache.get(uuid).orElse(null);
                    if (profile != null) {
                        return server.getPlayerList().isOp(profile);
                    }
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "Could not check op status for UUID {}: {}", uuid, e.getMessage());
        }
        return false;
    }

    public static PermissionManager getManager() {
        return manager;
    }

    public static String getPrefix(UUID uuid) {
        // Validate input parameters
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.getPrefix: UUID is null");
            return "";
        }

        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, ">>> PermissionAPI.getPrefix() called for UUID: {}", uuid);
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, ">>> Using external adapter: {}", (externalAdapter != null ? externalAdapter.getName() : "NONE"));

        // Same fall-through contract as getGroupWeight()/getPrimaryGroup() below: the external
        // adapter goes first, but a null response means "this adapter has no opinion" (e.g.
        // FtbRanksAdapter.getPrefix() always returns null — FTB Ranks has no prefix concept in
        // this integration at all — and a LuckPerms group with no "prefix" meta node set also
        // returns null), not "this player has no prefix". This used to return "" immediately in
        // that case ("do NOT fall back to internal when external is enabled"), which meant an
        // FTB Ranks server could never show a prefix through this mod at all, and a LuckPerms
        // group relying on NeoEssentials' own internal permissions.json prefix as a fallback for
        // groups with no LuckPerms-side meta configured silently never got it either.
        if (externalAdapter != null) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, ">>> Querying external adapter for prefix...");
            String prefix = externalAdapter.getPrefix(uuid);
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, ">>> External adapter returned: [{}]", prefix);
            if (prefix != null) return prefix;
            // Adapter had no opinion — fall through to internal.
        }

        // Internal system: either no external adapter is configured, or the external adapter
        // had no opinion for this player/group.
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, ">>> Using internal permission system");

        if (manager == null) {
            // When an external adapter IS configured (e.g. FTB Ranks, which never implements a
            // prefix concept at all), landing here on every single call is the permanent,
            // documented, expected state — PermissionSystem deliberately leaves the internal
            // manager unset in that mode ("internal groups loaded but NOT USED"). Warning here
            // used to fire on every tablist/placeholder refresh for every player for the entire
            // life of the server, flooding the log with a line that looks like a failure but
            // isn't one. Only warn when there is truly no permission system backing this call
            // at all (no external adapter either) — that IS a real misconfiguration.
            if (externalAdapter == null) {
                LOGGER.warn("PermissionAPI.getPrefix: PermissionManager is null");
            }
            return "";
        }
        PermissionUser user = manager.getUser(uuid);
        String groupName = (user != null && user.getGroup() != null) ? user.getGroup() : manager.getDefaultGroup();
        if (groupName == null) {
            LOGGER.warn("PermissionAPI.getPrefix: Default group name is null");
            return "";
        }
        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            LOGGER.warn("PermissionAPI.getPrefix: No PermissionGroup found for group '" + groupName + "'");
            return "";
        }
        String prefix = group.getPrefix();
        NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, ">>> Internal system prefix: [{}]", prefix);
        return prefix != null ? prefix : "";
    }

    /**
     * Returns the group weight/priority used for rank comparisons (tablist sort order,
     * vanish see-priority, etc). Checks the external adapter (LuckPerms) first, exactly like
     * {@link #getPrefix}/{@link #getSuffix} — callers that went straight to the internal
     * {@link PermissionManager} instead (as TablistManager/TablistLayout/ChatFormatter
     * previously did) would silently get 0 for every player whenever an external adapter is
     * configured, since the internal group registry is typically unpopulated for the
     * external system's actual groups.
     */
    public static int getGroupWeight(UUID uuid) {
        if (uuid == null) return 0;

        if (externalAdapter != null) {
            int weight = externalAdapter.getGroupWeight(uuid);
            if (weight != Integer.MIN_VALUE) return weight;
            // Adapter had no opinion (e.g. player not cached) — fall through to internal.
        }

        if (manager == null) return 0;
        PermissionUser user = manager.getUser(uuid);
        String groupName = (user != null && user.getGroup() != null) ? user.getGroup() : manager.getDefaultGroup();
        if (groupName == null) return 0;
        PermissionGroup group = manager.getGroup(groupName);
        return group != null ? group.getPriority() : 0;
    }

    /**
     * Returns the player's primary group name, checking the external adapter (LuckPerms, etc)
     * first when one is configured, falling back to the internal permission system's group
     * assignment otherwise. Used by features that key formatting/styling off a group name
     * (chat format's {@code group:<name>} keys, tablist per-group styling), as opposed to
     * {@link #hasPermission} / {@link #getGroupWeight} which operate on nodes/rank.
     */
    public static String getPrimaryGroup(UUID uuid) {
        if (uuid == null) return null;

        if (externalAdapter != null) {
            String group = externalAdapter.getPrimaryGroup(uuid);
            if (group != null) return group;
            // Adapter had no opinion (e.g. player not cached) — fall through to internal.
        }

        if (manager == null) return null;
        PermissionUser user = manager.getUser(uuid);
        return (user != null && user.getGroup() != null) ? user.getGroup() : manager.getDefaultGroup();
    }

    public static String getSuffix(UUID uuid) {
        // Validate input parameters
        if (uuid == null) {
            LOGGER.warn("PermissionAPI.getSuffix: UUID is null");
            return "";
        }
        
        // See getPrefix()'s comment above — same fall-through contract as
        // getGroupWeight()/getPrimaryGroup(): a null response from the external adapter means
        // "no opinion" (FTB Ranks never implements suffix; a LuckPerms group with no "suffix"
        // meta node also returns null), not "this player has no suffix", so it must fall
        // through to the internal system rather than returning "" immediately.
        if (externalAdapter != null) {
            String suffix = externalAdapter.getSuffix(uuid);
            if (suffix != null) return suffix;
            // Adapter had no opinion — fall through to internal.
        }

        // Internal system: either no external adapter is configured, or the external adapter
        // had no opinion for this player/group.
        if (manager == null) {
            // See getPrefix()'s identical comment — this is the permanent, expected state on an
            // external-adapter server (e.g. FTB Ranks never implements suffix), not a failure.
            if (externalAdapter == null) {
                LOGGER.warn("PermissionAPI.getSuffix: PermissionManager is null");
            }
            return "";
        }
        PermissionUser user = manager.getUser(uuid);
        if (user == null) {
            LOGGER.warn("PermissionAPI.getSuffix: No PermissionUser found for UUID " + uuid);
        }
        String groupName = (user != null && user.getGroup() != null) ? user.getGroup() : manager.getDefaultGroup();
        if (groupName == null) {
            LOGGER.warn("PermissionAPI.getSuffix: Default group name is null");
            return "";
        }
        PermissionGroup group = manager.getGroup(groupName);
        if (group == null) {
            LOGGER.warn("PermissionAPI.getSuffix: No PermissionGroup found for group '" + groupName + "'");
            return "";
        }
        String suffix = group.getSuffix();
        return suffix != null ? suffix : "";
    }

    /**
     * Reloads all permissions and groups from disk at runtime.
     */
    public static void reload() throws Exception {
        if (externalAdapter != null) {
            externalAdapter.reload();
        } else if (manager != null) {
            manager.reload();
        } else {
            LOGGER.warn("PermissionAPI.reload: Both externalAdapter and manager are null - nothing to reload");
            throw new IllegalStateException("Permission system not initialized - cannot reload");
        }
    }
}