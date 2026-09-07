package com.zerog.neoessentials.webdashboard.endpoints;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.integrations.ChatIntegrationManager;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import com.zerog.neoessentials.util.ResourceUtil;
import com.zerog.neoessentials.webdashboard.security.DiscordAuthConfig;
import com.zerog.neoessentials.webdashboard.security.DiscordAuthProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST endpoint for Discord bot integration status and event log.
 *
 * Routes (all require auth, test/config require admin):
 *   GET    /api/discord/status       – loaded adapter list + anyActive flag
 *   GET    /api/discord/events       – recent relay event log (rolling buffer)
 *   POST   /api/discord/test         – send a test message via all active adapters [ADMIN]
 *   DELETE /api/discord/events       – clear the event log [ADMIN]
 *   GET    /api/discord/auth-config  – read discord_auth.json login/role settings
 *   POST   /api/discord/auth-config  – update discord_auth.json login/role settings [ADMIN]
 *   GET    /api/discord/link-lookup  – resolve a Discord ID to its linked Minecraft account,
 *                                       for an external app's own OAuth2 login flow (this mod
 *                                       never performs Discord OAuth2 itself — see DiscordAuthProvider)
 */
public class DiscordEndpoint implements HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordEndpoint.class);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS preflight
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        NeoLog.debug(LOGGER, LogCategory.WEB_DASHBOARD, "DiscordEndpoint request: {} {}", method, path);

        try {
            if (path.endsWith("/status") && "GET".equals(method)) {
                handleStatus(exchange);
            } else if (path.endsWith("/events") && "GET".equals(method)) {
                handleGetEvents(exchange);
            } else if (path.endsWith("/events") && "DELETE".equals(method)) {
                handleClearEvents(exchange);
            } else if (path.endsWith("/test") && "POST".equals(method)) {
                handleTest(exchange);
            } else if (path.endsWith("/auth-config") && "GET".equals(method)) {
                handleGetAuthConfig(exchange);
            } else if (path.endsWith("/auth-config") && "POST".equals(method)) {
                handlePostAuthConfig(exchange);
            } else if (path.endsWith("/link-lookup") && "GET".equals(method)) {
                handleLinkLookup(exchange);
            } else {
                sendJson(exchange, 404, "{\"success\":false,\"error\":\"Unknown discord endpoint\"}");
            }
        } catch (Exception e) {
            LOGGER.error("Error in DiscordEndpoint", e);
            sendJson(exchange, 500, "{\"success\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    // ── GET /api/discord/status ───────────────────────────────────────────────

    private void handleStatus(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> adapterList = ChatIntegrationManager.getAdapterStatus();
        boolean anyActive = adapterList.stream()
            .anyMatch(a -> Boolean.TRUE.equals(a.get("enabled")));

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,");
        sb.append("\"anyActive\":").append(anyActive).append(",");
        sb.append("\"adapterCount\":").append(adapterList.size()).append(",");
        sb.append("\"eventCount\":").append(ChatIntegrationManager.getRecentEvents().size()).append(",");
        sb.append("\"adapters\":[");
        for (int i = 0; i < adapterList.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> a = adapterList.get(i);
            sb.append("{\"name\":\"").append(escape(String.valueOf(a.get("name")))).append("\",");
            sb.append("\"enabled\":").append(a.get("enabled")).append(",");
            sb.append("\"ready\":").append(a.get("ready")).append(",");
            sb.append("\"nativeRelayWarnings\":[");
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) a.getOrDefault("nativeRelayWarnings", List.of());
            for (int w = 0; w < warnings.size(); w++) {
                if (w > 0) sb.append(",");
                sb.append("\"").append(escape(warnings.get(w))).append("\"");
            }
            sb.append("]}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // ── GET /api/discord/events ───────────────────────────────────────────────

    private void handleGetEvents(HttpExchange exchange) throws IOException {
        // Optional ?limit=N query param
        int limit = 100;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("limit=")) {
                    try { limit = Integer.parseInt(param.substring(6)); } catch (NumberFormatException e) {
                        NeoLog.debug(LOGGER, LogCategory.WEB_DASHBOARD, "Invalid limit query param '{}', using default", param, e);
                    }
                }
            }
        }

        List<Map<String, Object>> events = ChatIntegrationManager.getRecentEvents();
        // Return most-recent first, up to limit
        int from = Math.max(0, events.size() - limit);
        List<Map<String, Object>> slice = events.subList(from, events.size());
        List<Map<String, Object>> reversed = new ArrayList<>(slice);
        Collections.reverse(reversed);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"total\":").append(events.size()).append(",\"events\":[");
        for (int i = 0; i < reversed.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> e = reversed.get(i);
            sb.append("{");
            sb.append("\"type\":\"").append(escape(str(e, "type"))).append("\",");
            sb.append("\"actor\":\"").append(escape(str(e, "actor"))).append("\",");
            sb.append("\"target\":").append(e.get("target") == null ? "null" : "\"" + escape(str(e, "target")) + "\"").append(",");
            sb.append("\"channel\":\"").append(escape(str(e, "channel"))).append("\",");
            sb.append("\"message\":\"").append(escape(str(e, "message"))).append("\",");
            sb.append("\"timestamp\":\"").append(escape(str(e, "timestamp"))).append("\"");
            sb.append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // ── DELETE /api/discord/events ────────────────────────────────────────────

    private void handleClearEvents(HttpExchange exchange) throws IOException {
        Boolean isAdmin = (Boolean) exchange.getAttribute("auth-admin");
        if (!Boolean.TRUE.equals(isAdmin)) {
            sendJson(exchange, 403, "{\"success\":false,\"error\":\"Admin access required\"}");
            return;
        }
        ChatIntegrationManager.clearEventLog();
        sendJson(exchange, 200, "{\"success\":true,\"message\":\"Event log cleared\"}");
    }

    // ── POST /api/discord/test ────────────────────────────────────────────────

    private static final java.util.regex.Pattern DISCORD_SNOWFLAKE = java.util.regex.Pattern.compile("^\\d{15,25}$");

    private void handleTest(HttpExchange exchange) throws IOException {
        Boolean isAdmin = (Boolean) exchange.getAttribute("auth-admin");
        if (!Boolean.TRUE.equals(isAdmin)) {
            sendJson(exchange, 403, "{\"success\":false,\"error\":\"Admin access required\"}");
            return;
        }

        // Parse body: { "channel": "123456789012345678", "message": "Test!" }
        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        String channel = extractJsonString(body, "channel", "");
        String message = extractJsonString(body, "message", "Test message from NeoEssentials Dashboard");

        if (channel.isBlank()) {
            sendJson(exchange, 400, "{\"success\":false,\"error\":\"Missing channel — this must be the target Discord channel's numeric ID, not a name (right-click the channel in Discord with Developer Mode on, then 'Copy Channel ID').\"}");
            return;
        }
        if (!DISCORD_SNOWFLAKE.matcher(channel).matches()) {
            sendJson(exchange, 400, "{\"success\":false,\"error\":\"'" + escape(channel) + "' doesn't look like a Discord channel ID (should be a 15-25 digit number, not a channel name).\"}");
            return;
        }

        if (!ChatIntegrationManager.hasIntegrations()) {
            sendJson(exchange, 503, "{\"success\":false,\"error\":\"No Discord adapters are currently loaded on this server.\"}");
            return;
        }

        boolean sent = ChatIntegrationManager.sendToChannel(channel, message);
        NeoLog.info(LOGGER, LogCategory.WEB_DASHBOARD, "[Discord Test] Admin sent test message to channel '{}': {} (delivered: {})", channel, message, sent);

        if (sent) {
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"Message sent to channel " + escape(channel) + ".\"}");
        } else {
            sendJson(exchange, 502, "{\"success\":false,\"error\":\"Could not deliver the message — the bot may not be in that channel/server, or the channel ID is wrong.\"}");
        }
    }

    // ── GET /api/discord/auth-config ─────────────────────────────────────────
    // Returns the current discord_auth.json login/role settings. [AUTH] tier, not
    // [ADMIN]: every field here is a non-secret boolean/enum (no tokens or
    // passwords), and callers that only need to know "is Discord login possible
    // right now" — e.g. a dashboard's own guest-facing login page, via its shared
    // service account — shouldn't need an admin-tier credential just to read it.
    // Writing these settings (below) still requires ADMIN.

    private void handleGetAuthConfig(HttpExchange exchange) throws IOException {
        DiscordAuthConfig cfg = DiscordAuthConfig.load();
        DiscordAuthProvider provider = DiscordAuthProvider.getInstance();

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        resp.addProperty("enabled", cfg.isEnabled());
        resp.addProperty("requireLinkedAccount", cfg.requiresLinkedAccount());
        resp.addProperty("allowAutoRegistration", cfg.allowsAutoRegistration());
        resp.addProperty("defaultRole", cfg.getDefaultRole().name());
        resp.addProperty("linkAdapterAvailable", provider.isAvailable());

        sendJson(exchange, 200, new GsonBuilder().disableHtmlEscaping().create().toJson(resp));
    }

    // ── POST /api/discord/auth-config ────────────────────────────────────────
    // Saves changed discord_auth.json settings (ADMIN only).
    // Sends partial updates: only keys present in the body are changed.
    // clientSecret is skipped if blank (keep the current value).

    private void handlePostAuthConfig(HttpExchange exchange) throws IOException {
        Boolean isAdmin = (Boolean) exchange.getAttribute("auth-admin");
        if (!Boolean.TRUE.equals(isAdmin)) {
            sendJson(exchange, 403, "{\"success\":false,\"error\":\"Admin access required\"}");
            return;
        }

        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        JsonObject submitted;
        try {
            submitted = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"success\":false,\"error\":\"Invalid JSON body\"}");
            return;
        }

        // Load the raw on-disk config to preserve role mappings, hierarchy, blacklists, etc.
        java.io.File configFile = ResourceUtil.getConfigFile(ConfigManager.DISCORD_AUTH_CONFIG);
        JsonObject onDisk;
        try (java.io.FileReader reader = new java.io.FileReader(configFile, StandardCharsets.UTF_8)) {
            onDisk = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"success\":false,\"error\":\"Failed to read discord_auth.json: " + escape(e.getMessage()) + "\"}");
            return;
        }

        // Apply top-level toggles
        if (submitted.has("enabled"))
            onDisk.addProperty("enabled", submitted.get("enabled").getAsBoolean());
        if (submitted.has("requireLinkedAccount"))
            onDisk.addProperty("requireLinkedAccount", submitted.get("requireLinkedAccount").getAsBoolean());
        if (submitted.has("allowAutoRegistration"))
            onDisk.addProperty("allowAutoRegistration", submitted.get("allowAutoRegistration").getAsBoolean());
        if (submitted.has("defaultRole")) {
            String role = submitted.get("defaultRole").getAsString().toUpperCase();
            if (role.equals("ADMIN") || role.equals("MODERATOR") || role.equals("VIEWER"))
                onDisk.addProperty("defaultRole", role);
        }

        // Write back
        try (java.io.FileWriter writer = new java.io.FileWriter(configFile, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(onDisk, writer);
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"success\":false,\"error\":\"Failed to write discord_auth.json: " + escape(e.getMessage()) + "\"}");
            return;
        }

        // Invalidate ConfigManager cache so next DiscordAuthConfig.load() re-reads the file
        ConfigManager.getInstance().clearCache();
        NeoLog.info(LOGGER, LogCategory.WEB_DASHBOARD, "[Discord] Admin updated discord_auth.json via dashboard");

        sendJson(exchange, 200, "{\"success\":true,\"message\":\"Discord auth config saved. Changes take effect immediately.\"}");
    }

    // ── GET /api/discord/link-lookup?discordId=<id> ─────────────────────────
    // Reverse of the mod's own UUID->Discord lookups: given a real Discord ID (obtained by
    // an external app's own OAuth2 flow — this mod never talks to Discord directly), resolve
    // the Minecraft account linked to it, if any. [AUTH] — same tier as /status and /events.

    private void handleLinkLookup(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String discordId = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("discordId=")) {
                    discordId = java.net.URLDecoder.decode(param.substring("discordId=".length()), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        if (discordId == null || discordId.isEmpty()) {
            sendJson(exchange, 400, "{\"success\":false,\"error\":\"Missing discordId parameter\"}");
            return;
        }

        Optional<UUID> linkedUuid = ChatIntegrationManager.findLinkedMinecraftUuid(discordId);
        if (linkedUuid.isEmpty()) {
            NeoLog.debug(LOGGER, LogCategory.WEB_DASHBOARD, "Discord link-lookup: no linked account for discordId={}", discordId);
            sendJson(exchange, 200, "{\"success\":true,\"linked\":false}");
            return;
        }

        UUID uuid = linkedUuid.get();
        String username = resolveUsername(uuid);

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        resp.addProperty("linked", true);
        resp.addProperty("minecraftUuid", uuid.toString());
        resp.addProperty("minecraftUsername", username);
        sendJson(exchange, 200, new GsonBuilder().disableHtmlEscaping().create().toJson(resp));
    }

    private String resolveUsername(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return uuid.toString();

        // Reading the live player list/profile cache must happen on the main thread, not
        // this HTTP worker thread.
        try {
            return server.submit(() -> {
                var online = server.getPlayerList().getPlayer(uuid);
                if (online != null) return online.getName().getString();

                try {
                    GameProfileCache cache = server.getProfileCache();
                    if (cache != null) {
                        var profile = cache.get(uuid);
                        if (profile.isPresent() && profile.get().getName() != null) return profile.get().getName();
                    }
                } catch (Exception e) {
                    NeoLog.debug(LOGGER, LogCategory.WEB_DASHBOARD, "Failed to resolve username for uuid={} via profile cache", uuid, e);
                }
                return uuid.toString();
            }).get();
        } catch (Exception e) {
            LOGGER.error("Error resolving username on main thread for uuid=" + uuid, e);
            return uuid.toString();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    /** Very small JSON string extractor — no external deps. */
    private String extractJsonString(String json, String key, String fallback) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return fallback;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return fallback;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return fallback;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return fallback;
        return json.substring(start + 1, end);
    }
}


