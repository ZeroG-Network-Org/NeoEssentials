package com.zerog.neoessentials.permissions;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for FTB Ranks integration using reflection to avoid a hard compile-time dependency.
 *
 * <p>Known API strategies probed in order:
 * <ol>
 *   <li>{@code FTBRanksAPI.getPermissionValue(ServerPlayer, String)} — 2101.1.x (NeoForge) ✓ confirmed</li>
 *   <li>{@code RankManager.getPermissionValue(ServerPlayer, String)} — via getInstance().getManager()</li>
 *   <li>{@code FTBRanksAPI.hasPermission(ServerPlayer, String)} — legacy static variant</li>
 *   <li>{@code FTBRanksAPI.checkPermission(ServerPlayer, String)} — possible future naming change</li>
 *   <li>{@code instance.hasPermission(UUID, String)} — oldest builds via INSTANCE/getInstance()</li>
 * </ol>
 */
public class FtbRanksAdapter implements ExternalPermissionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FtbRanksAdapter.class);

    /** Number of consecutive failures before we declare the adapter unhealthy. */
    private static final int MAX_FAILURES = 5;

    /** Last-tested FTB Ranks version.  Warn if detected version differs. */
    private static final String LAST_TESTED_VERSION = "2101.1.3";

    private final boolean ftbRanksLoaded;
    private final String  detectedVersion;

    // Resolved API method + the object to call it on (null = static)
    private Method resolvedMethod   = null;
    private Object resolvedInstance = null;
    private int    resolvedStrategy = 0;

    // Extra API resolved independently of the hasPermission-strategy probe above, used for
    // getPrefix()/getSuffix()/getPrimaryGroup()/getGroupWeight() — these all need
    // FTBRanksAPI.getPermissionValue(ServerPlayer,String) (to read the RAW STRING value of the
    // "ftbranks.name_format" node, not just a boolean) and FTBRanksAPI.manager().getRanks(player)
    // (to find the player's ranks at all — FTB Ranks has no single "primary group" the way
    // LuckPerms does). Kept separate from resolvedMethod/resolvedStrategy since those are
    // strategy-numbered fallbacks specifically for boolean hasPermission() checks across FTB
    // Ranks API versions, whereas these two methods are each looked up directly by exact
    // signature and either exist or don't — no fallback strategy needed for them.
    private Method ftbApiPermissionValueMethod = null;
    private Method ftbApiManagerMethod         = null;
    // 1 = getPermissionValue(ServerPlayer,String) [static FTBRanksAPI] — 2101.1.x confirmed
    // 2 = getPermissionValue(ServerPlayer,String) [instance RankManager]
    // 3 = hasPermission(ServerPlayer,String)      [static]
    // 4 = checkPermission(ServerPlayer,String)    [static]
    // 5 = hasPermission(UUID,String)              [instance — oldest builds]

    /** Consecutive failure counter — reset on every successful check. */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public FtbRanksAdapter() {
        this.ftbRanksLoaded = ModList.get().isLoaded("ftbranks");
        this.detectedVersion = ModList.get().getModContainerById("ftbranks")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");

        if (ftbRanksLoaded) {
            NeoLog.info(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks detected — version: {}", detectedVersion);
            if (!detectedVersion.equals("unknown")
                    && !detectedVersion.startsWith(LAST_TESTED_VERSION.substring(0, LAST_TESTED_VERSION.lastIndexOf('.')))) {
                NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"╔══════════════════════════════════════════════════════════════╗");
                NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  FTB RANKS COMPATIBILITY WARNING                              ║");
                NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Detected version : {}                              ║", padRight(detectedVersion, 30));
                NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Last tested with : {}                              ║", padRight(LAST_TESTED_VERSION, 30));
                NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  If permissions stop working, please report this version     ║");
                NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  mismatch at github.com/your-repo/neoessentials/issues       ║");
                NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"╚══════════════════════════════════════════════════════════════╝");
            }
            probeApi();
            resolveExtraApi();
        }
    }

    /**
     * Resolves {@code FTBRanksAPI.getPermissionValue(ServerPlayer, String)} and
     * {@code FTBRanksAPI.manager()} directly, independent of which hasPermission-strategy
     * {@link #probeApi()} settled on above. Both are confirmed present in the current
     * (2101.1.x) API; if a future/older FTB Ranks build lacks either, the corresponding
     * getPrefix()/getSuffix()/getPrimaryGroup()/getGroupWeight() calls just return their
     * "no opinion" default (null / Integer.MIN_VALUE) rather than failing.
     */
    private void resolveExtraApi() {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
            ftbApiPermissionValueMethod = apiClass.getMethod("getPermissionValue",
                    net.minecraft.server.level.ServerPlayer.class, String.class);
            ftbApiManagerMethod = apiClass.getMethod("manager");
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS,
                    "FTB Ranks extra API (getPermissionValue/manager) not resolvable: {}", e.getMessage());
        }
    }

    // ── API probe ────────────────────────────────────────────────────────────────

    private void probeApi() {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");

            // ── Strategy 1: static getPermissionValue(ServerPlayer, String) ──────────
            // Confirmed API in FTB Ranks 2101.1.x — returns PermissionValue
            try {
                Method m = apiClass.getMethod("getPermissionValue",
                        net.minecraft.server.level.ServerPlayer.class, String.class);
                resolvedMethod   = m;
                resolvedInstance = null;
                resolvedStrategy = 1;
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks adapter: strategy 1 — getPermissionValue(ServerPlayer, String)");
                return;
            } catch (NoSuchMethodException ignored) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks strategy 1 (static getPermissionValue) not present, trying next strategy");
            }

            // ── Strategy 2: RankManager.getPermissionValue(ServerPlayer, String) ──────
            // Via getInstance().getManager() — alternative accessor path
            try {
                Object apiInstance = apiClass.getMethod("getInstance").invoke(null);
                if (apiInstance != null) {
                    Object rankManager = apiInstance.getClass().getMethod("getManager").invoke(apiInstance);
                    if (rankManager != null) {
                        Method m = rankManager.getClass().getMethod("getPermissionValue",
                                net.minecraft.server.level.ServerPlayer.class, String.class);
                        resolvedMethod   = m;
                        resolvedInstance = rankManager;
                        resolvedStrategy = 2;
                        NeoLog.info(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks adapter: strategy 2 — RankManager.getPermissionValue(ServerPlayer, String)");
                        return;
                    }
                }
            } catch (Exception ignored) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks strategy 2 (RankManager.getPermissionValue) probe failed, trying next strategy", ignored);
            }

            // ── Strategy 3: static hasPermission(ServerPlayer, String) ───────────────
            // Legacy static variant
            try {
                Method m = apiClass.getMethod("hasPermission",
                        net.minecraft.server.level.ServerPlayer.class, String.class);
                resolvedMethod   = m;
                resolvedInstance = null;
                resolvedStrategy = 3;
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks adapter: strategy 3 — hasPermission(ServerPlayer, String)");
                return;
            } catch (NoSuchMethodException ignored) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks strategy 3 (static hasPermission) not present, trying next strategy");
            }

            // ── Strategy 4: static checkPermission(ServerPlayer, String) ─────────────
            // Alternative naming used by some FTB Ranks forks / future versions
            try {
                Method m = apiClass.getMethod("checkPermission",
                        net.minecraft.server.level.ServerPlayer.class, String.class);
                resolvedMethod   = m;
                resolvedInstance = null;
                resolvedStrategy = 4;
                NeoLog.info(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks adapter: strategy 4 — checkPermission(ServerPlayer, String)");
                return;
            } catch (NoSuchMethodException ignored) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks strategy 4 (static checkPermission) not present, trying next strategy");
            }

            // ── Strategy 5: instance hasPermission(UUID, String) ─────────────────────
            // Oldest builds via INSTANCE / getInstance()
            Object instance = null;
            try {
                instance = apiClass.getField("INSTANCE").get(null);
            } catch (NoSuchFieldException e) {
                NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks INSTANCE field not present, trying getInstance() accessor", e);
                try {
                    instance = apiClass.getMethod("getInstance").invoke(null);
                } catch (Exception ignored2) {
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks getInstance() accessor also failed for strategy 5", ignored2);
                }
            }
            if (instance != null) {
                try {
                    Method m = instance.getClass().getMethod("hasPermission", UUID.class, String.class);
                    resolvedMethod   = m;
                    resolvedInstance = instance;
                    resolvedStrategy = 5;
                    NeoLog.info(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks adapter: strategy 5 — instance.hasPermission(UUID, String)");
                    return;
                } catch (NoSuchMethodException ignored) {
                    NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks strategy 5 (instance.hasPermission) not present", ignored);
                }
            }

            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"╔══════════════════════════════════════════════════════════════╗");
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  FTB RANKS API NOT RESOLVED                                  ║");
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Version {} did not match any known API signature.  ║", padRight(detectedVersion, 24));
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Permission checks will fall back to OP / internal system.   ║");
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Please report this at the NeoEssentials issue tracker.      ║");
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"╚══════════════════════════════════════════════════════════════╝");

        } catch (ClassNotFoundException e) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks API class not found — mod may not be installed");
        } catch (Exception e) {
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks adapter init failed: {}", e.getMessage());
        }
    }

    // ── Permission check ─────────────────────────────────────────────────────────

    @Override
    public boolean hasPermission(UUID uuid, String permission) {
        if (!ftbRanksLoaded || resolvedMethod == null) return false;
        try {
            boolean result = invokeResolvedMethod(uuid, permission);
            consecutiveFailures.set(0); // reset on success
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks permission check: player={}, permission={}, strategy={}, result={}",
                    uuid, permission, resolvedStrategy, result);
            return result;
        } catch (Exception e) {
            int failures = consecutiveFailures.incrementAndGet();
            emitHealthWarnIfNeeded(failures, permission, e);
        }
        return false;
    }

    private boolean invokeResolvedMethod(UUID uuid, String permission) throws Exception {
        var server = ServerLifecycleHooks.getCurrentServer();

        if (resolvedStrategy == 1) {
            // getPermissionValue(ServerPlayer, String) — static on FTBRanksAPI
            if (server == null) return false;
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return false;
            return extractBoolean(resolvedMethod.invoke(null, player, permission));

        } else if (resolvedStrategy == 2) {
            // RankManager.getPermissionValue(ServerPlayer, String)  — instance call
            if (server == null) return false;
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return false;
            return extractBoolean(resolvedMethod.invoke(resolvedInstance, player, permission));

        } else if (resolvedStrategy == 3 || resolvedStrategy == 4) {
            // hasPermission(ServerPlayer, String) or checkPermission(ServerPlayer, String)
            if (server == null) return false;
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return false;
            return extractBoolean(resolvedMethod.invoke(null, player, permission));

        } else if (resolvedStrategy == 5) {
            // instance.hasPermission(UUID, String) — oldest builds
            Object result = resolvedMethod.invoke(resolvedInstance, uuid, permission);
            return result instanceof Boolean b && b;
        }
        return false;
    }

    /**
     * Coerce the raw return value from the FTB Ranks API into a boolean.
     * Handles {@code PermissionValue} (2101.1.x), Boolean, Optional&lt;Boolean&gt;,
     * and TriState/enum return types.
     */
    private boolean extractBoolean(Object result) {
        if (result == null) return false;
        if (result instanceof Boolean b) return b;
        // PermissionValue (FTB Ranks 2101.1.x) — try asBooleanOrFalse() first
        try {
            Object r = result.getClass().getMethod("asBooleanOrFalse").invoke(result);
            if (r instanceof Boolean b) return b;
        } catch (Exception ignored) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks result type {} has no asBooleanOrFalse(), trying next coercion", result.getClass().getName());
        }
        // Optional<Boolean> — e.g. PermissionValue.asBoolean()
        if (result instanceof java.util.Optional<?> opt) {
            Object inner = opt.orElse(null);
            if (inner instanceof Boolean b) return b;
        }
        // TriState / enum — try a get() method, then toString comparison
        try {
            return (boolean) result.getClass().getMethod("get").invoke(result);
        } catch (Exception ignored) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks result type {} has no get(), falling back to toString comparison", result.getClass().getName());
        }
        // Deny-by-default allowlist, not a grant-by-default blocklist — this class's own doc
        // comment acknowledges the exact return-value shape is unstable across FTB Ranks
        // versions/forks, so treating "anything not in a hardcoded negative list" as an
        // allow was a fail-OPEN bug: any future/incompatible build whose value stringifies to
        // something outside {FALSE, UNDEFINED, DENY, MISSING} (e.g. an enum like NOT_SET,
        // DEFAULT, INHERIT, NONE) would have silently granted every permission checked through
        // this adapter, with no exception thrown and no failure-counter trip to surface it.
        String s = result.toString().toUpperCase();
        boolean allow = s.equals("TRUE") || s.equals("ALLOW") || s.equals("ALLOWED") || s.equals("GRANTED") || s.equals("YES");
        if (!allow) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS,
                "FTB Ranks result type {} (stringified '{}') did not match any known true/allow form — denying (fail-closed)",
                result.getClass().getName(), s);
        }
        return allow;
    }

    private void emitHealthWarnIfNeeded(int failures, String permission, Exception cause) {
        if (failures == 1) {
            NeoLog.error(LOGGER, LogCategory.PERMISSIONS,"FTB Ranks permission check failed for '{}': {}", permission,
                    cause.getMessage());
        } else if (failures == MAX_FAILURES) {
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"╔══════════════════════════════════════════════════════════════╗");
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  FTB RANKS ADAPTER UNHEALTHY — {} consecutive failures    ║", MAX_FAILURES);
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Version     : {}                                   ║", padRight(detectedVersion, 21));
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Last error  : {}  ║", padRight(cause.getMessage() != null
                    ? cause.getMessage().substring(0, Math.min(cause.getMessage().length(), 42)) : "n/a", 42));
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  NeoEssentials will fall back to internal permissions.       ║");
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"║  Resolve the FTB Ranks API issue and run /neoe reload.       ║");
            NeoLog.warn(LOGGER, LogCategory.PERMISSIONS,"╚══════════════════════════════════════════════════════════════╝");
        }
    }

    // ── ExternalPermissionAdapter extras ─────────────────────────────────────────

    @Override
    public String getPrefix(UUID uuid) {
        String[] parts = splitNameFormat(uuid);
        return parts != null ? parts[0] : null;
    }

    @Override
    public String getSuffix(UUID uuid) {
        String[] parts = splitNameFormat(uuid);
        return parts != null ? parts[1] : null;
    }

    /**
     * FTB Ranks has no separate prefix/suffix concept — instead a rank sets a single
     * {@code ftbranks.name_format} permission VALUE, a template like
     * {@code "&b[VIP]&r {name}"} that FTB Ranks itself substitutes {@code {name}} into to build
     * the player's styled name elsewhere (nameplate/tablist). Splitting that template on the
     * literal {@code "{name}"} token gives an equivalent prefix/suffix pair that plugs directly
     * into NeoEssentials' existing {@code prefix + name + suffix} formatting model (chat format,
     * tablist, {@code {ftbranks_prefix}}/{@code {ftbranks_suffix}} placeholders) with no new
     * concept needed — it just reuses whatever the server already configured for
     * {@code ftbranks.name_format}.
     *
     * @return {@code {prefix, suffix}}, or {@code null} if the player has no rank with that
     *         node set (server offline lookups, or a rank that never configured it) — "no
     *         opinion", matching every other adapter method's null-means-fall-through contract.
     */
    private String[] splitNameFormat(UUID uuid) {
        if (!ftbRanksLoaded || ftbApiPermissionValueMethod == null) return null;
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return null;

            Object permissionValue = ftbApiPermissionValueMethod.invoke(null, player, "ftbranks.name_format");
            if (permissionValue == null) return null;
            Object asStringOpt = permissionValue.getClass().getMethod("asString").invoke(permissionValue);
            if (!(asStringOpt instanceof java.util.Optional<?> opt) || opt.isEmpty()) return null;
            String template = String.valueOf(opt.get());
            if (template.isBlank()) return null;

            int idx = template.indexOf("{name}");
            if (idx < 0) return new String[]{template, ""};
            return new String[]{template.substring(0, idx), template.substring(idx + "{name}".length())};
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks name_format lookup failed for {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    @Override
    public String getPrimaryGroup(UUID uuid) {
        Object rank = getPrimaryRank(uuid);
        return rank != null ? rankId(rank) : null;
    }

    @Override
    public int getGroupWeight(UUID uuid) {
        Object rank = getPrimaryRank(uuid);
        if (rank == null) return Integer.MIN_VALUE;
        Integer power = rankPower(rank);
        return power != null ? power : Integer.MIN_VALUE;
    }

    /**
     * Returns the player's highest-power FTB Rank, or {@code null} if they have none / FTB Ranks
     * couldn't be queried. FTB Ranks lets a player hold multiple ranks simultaneously — there is
     * no single exclusive "primary group" the way LuckPerms has one — so the highest
     * {@code power} value is treated as the primary rank here, matching the ordering FTB Ranks
     * itself uses when resolving conflicting permission values (a higher-power rank wins).
     */
    private Object getPrimaryRank(UUID uuid) {
        if (!ftbRanksLoaded || ftbApiManagerMethod == null) return null;
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return null;

            Object rankManager = ftbApiManagerMethod.invoke(null);
            if (rankManager == null) return null;
            Object ranksObj = rankManager.getClass()
                    .getMethod("getRanks", net.minecraft.server.level.ServerPlayer.class)
                    .invoke(rankManager, player);
            if (!(ranksObj instanceof java.util.List<?> ranks) || ranks.isEmpty()) return null;

            Object best = null;
            int bestPower = Integer.MIN_VALUE;
            for (Object rank : ranks) {
                Integer power = rankPower(rank);
                int p = power != null ? power : 0;
                if (best == null || p > bestPower) {
                    best = rank;
                    bestPower = p;
                }
            }
            return best;
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Ranks getRanks lookup failed for {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    /** Rank id (matches the rank's key in FTB Ranks' own config, e.g. "SeasonedExplorer") — falls
     *  back to the display name, then {@code toString()}, if a future API build renames the
     *  accessor. */
    private String rankId(Object rank) {
        try {
            Object id = rank.getClass().getMethod("getId").invoke(rank);
            if (id != null) return id.toString();
        } catch (Exception ignored) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Rank object has no getId(), trying getName()");
        }
        try {
            Object name = rank.getClass().getMethod("getName").invoke(rank);
            if (name != null) return name.toString();
        } catch (Exception ignored) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Rank object has no getName() either, falling back to toString()");
        }
        return rank.toString();
    }

    /** Rank power (priority) — null if the Rank object exposes no such accessor. */
    private Integer rankPower(Object rank) {
        try {
            Object power = rank.getClass().getMethod("getPower").invoke(rank);
            if (power instanceof Number n) return n.intValue();
        } catch (Exception ignored) {
            NeoLog.debug(LOGGER, LogCategory.PERMISSIONS, "FTB Rank object has no getPower()");
        }
        return null;
    }

    @Override
    public void reload() { /* FTB Ranks handles its own reload */ }

    @Override
    public String getName() { return "FTB Ranks"; }

    @Override
    public boolean isAvailable() {
        return ftbRanksLoaded && resolvedMethod != null;
    }

    @Override
    public String getVersion() { return detectedVersion; }

    @Override
    public boolean isHealthy() { return consecutiveFailures.get() < MAX_FAILURES; }

    @Override
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Right-pad a string to exactly {@code width} chars (for log alignment). */
    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}
