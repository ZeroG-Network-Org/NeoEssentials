package com.zerog.neoessentials.integrations.impl;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.integrations.ChatIntegrationAdapter;
import com.zerog.neoessentials.integrations.DiscordTextSanitizer;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Generic Discord webhook relay — the "no bridge mod required" alternative to SDLink/Mc2Discord/
 * DCIntegration. A webhook is a plain HTTPS POST endpoint Discord itself provides per-channel
 * (Server Settings → Integrations → Webhooks); there's no bot token, no gateway connection, no
 * companion mod to install, and therefore no {@code compileOnly} dependency or {@code
 * ModList.isLoaded()} guard needed here at all — this adapter is always a candidate, and simply
 * does nothing for any event whose corresponding config entry has no {@code webhookUrl} set.
 *
 * <p>Trade-off for that simplicity: webhooks are one-directional (Discord → webhook only) and
 * carry no account-linking/role concept, so {@link #getLinkedDiscordId}/{@link
 * #getDiscordRoleIds}/{@link #getLinkedMinecraftUuid} all stay at their interface defaults
 * (unsupported), and {@link #sendToChannel} — which addresses a target by Discord *channel ID*,
 * not a webhook URL — can't be meaningfully implemented here either.
 *
 * <p>Reads {@code chat.channels.<name>.discord.webhookUrl} for chat messages (keyed by the same
 * {@code channel} parameter the bot-based adapters already receive) and {@code
 * discordEventChannels.<event>.webhookUrl} for everything else — see those config sections'
 * comments in {@code config.json} for the full field reference. Both coexist independently with
 * the existing {@code channelId} field used by the bot-based adapters; an admin can configure
 * one, the other, or both for the same event without conflict, since every adapter only acts on
 * the field(s) it understands.
 */
public class WebhookAdapter implements ChatIntegrationAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookAdapter.class);

    // Discord hard-caps message content at 2000 UTF-16 code units; anything longer is rejected
    // outright rather than truncated server-side, so truncate proactively instead of silently
    // losing the whole message to a 400 response.
    private static final int DISCORD_MESSAGE_LIMIT = 2000;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public String getName() {
        return "Generic Webhook";
    }

    @Override
    public boolean initialize() {
        // Unlike the bot-based adapters, this one has no external mod/connection to probe —
        // ChatIntegrationManager.initialize() only registers an adapter into its list when
        // initialize() returns true, and that registration is a one-time, startup-only decision
        // (a config reload later doesn't re-run it). Registering unconditionally here means a
        // webhookUrl added via /neoe reload after boot works immediately — isEnabled()/isReady()
        // and each event handler all re-read config live, not from a boot-time snapshot, so there
        // is nothing this method could usefully cache anyway.
        if (scanForAnyWebhook()) {
            NeoLog.info(LOGGER, LogCategory.DISCORD,
                "Generic Discord webhook relay active — at least one webhookUrl is configured.");
        } else {
            NeoLog.debug(LOGGER, LogCategory.DISCORD,
                "No discord.webhookUrl / discordEventChannels.*.webhookUrl configured yet — generic webhook relay idle (will pick up a webhookUrl added via /neoe reload with no restart needed).");
        }
        return true;
    }

    /** True if any chat channel or event type currently has a non-blank {@code webhookUrl} —
     *  read live every time, so it reflects the current config, not just what was set at boot. */
    private boolean scanForAnyWebhook() {
        try {
            JsonObject config = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (config.has("chat")) {
                JsonObject chat = config.getAsJsonObject("chat");
                if (chat.has("channels")) {
                    for (var entry : chat.getAsJsonObject("channels").entrySet()) {
                        if (!entry.getValue().isJsonObject()) continue;
                        JsonObject channelObj = entry.getValue().getAsJsonObject();
                        if (channelObj.has("discord") && channelObj.getAsJsonObject("discord").has("webhookUrl")) {
                            String url = channelObj.getAsJsonObject("discord").get("webhookUrl").getAsString();
                            if (url != null && !url.isBlank()) return true;
                        }
                    }
                }
            }
            if (config.has("discordEventChannels")) {
                for (var entry : config.getAsJsonObject("discordEventChannels").entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject eventObj = entry.getValue().getAsJsonObject();
                    if (eventObj.has("webhookUrl")) {
                        String url = eventObj.get("webhookUrl").getAsString();
                        if (url != null && !url.isBlank()) return true;
                    }
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Error scanning for configured webhooks: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean isEnabled() {
        return scanForAnyWebhook();
    }

    @Override
    public boolean isReady() {
        // No gateway/bot connection to be "up" or "down" — a webhook POST either succeeds or
        // fails per-call, so "ready" just means "at least one webhook is configured right now".
        return scanForAnyWebhook();
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
        String webhookUrl = resolveChatWebhook(channel);
        if (webhookUrl == null) return;
        String clean = DiscordTextSanitizer.sanitizeMentions(message.replaceAll("§[0-9a-fk-or]", ""));
        postWebhook(webhookUrl, player.getName().getString(), avatarFor(player), clean);
    }

    @Override
    public void onPlayerJoin(ServerPlayer player, String discordChannelId) {
        String webhookUrl = resolveEventWebhook("join");
        if (webhookUrl == null) return;
        postWebhook(webhookUrl, "Server", null, player.getName().getString() + " joined the server");
    }

    @Override
    public void onPlayerQuit(ServerPlayer player, String discordChannelId) {
        String webhookUrl = resolveEventWebhook("leave");
        if (webhookUrl == null) return;
        postWebhook(webhookUrl, "Server", null, player.getName().getString() + " left the server");
    }

    @Override
    public void onPlayerMute(ServerPlayer player, String reason, boolean isMuted, String discordChannelId) {
        String webhookUrl = resolveEventWebhook("mute");
        if (webhookUrl == null) return;
        String action = isMuted ? "muted" : "unmuted";
        String text = String.format("%s has been %s%s", player.getName().getString(), action,
            reason != null && !reason.isEmpty() ? " (Reason: " + reason + ")" : "");
        postWebhook(webhookUrl, "Server", null, text);
    }

    @Override
    public void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason, String discordChannelId) {
        String webhookUrl = resolveEventWebhook("afk");
        if (webhookUrl == null) return;
        String status = isAfk ? "is now AFK" : "is no longer AFK";
        String text = String.format("%s %s%s", player.getName().getString(), status,
            (isAfk && reason != null && !reason.isEmpty()) ? " (" + reason + ")" : "");
        postWebhook(webhookUrl, "Server", null, text);
    }

    @Override
    public void onPlayerAdvancement(ServerPlayer player, String advancementName, String discordChannelId) {
        String webhookUrl = resolveEventWebhook("advancement");
        if (webhookUrl == null) return;
        postWebhook(webhookUrl, player.getName().getString(), avatarFor(player),
            player.getName().getString() + " earned the advancement " + advancementName);
    }

    @Override
    public void onPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message, String discordChannelId) {
        String webhookUrl = resolveEventWebhook("privateMessage");
        if (webhookUrl == null) return;
        String clean = DiscordTextSanitizer.sanitizeMentions(message);
        postWebhook(webhookUrl, sender.getName().getString(), avatarFor(sender),
            "Private message to " + recipient.getName().getString() + ": " + clean);
    }

    /** {@code chat.channels.<channel>.discord.webhookUrl}, or {@code null} if unset/blank/the
     *  channel's discord block is disabled. */
    private String resolveChatWebhook(String channel) {
        try {
            JsonObject config = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (!config.has("chat")) return null;
            JsonObject chat = config.getAsJsonObject("chat");
            if (!chat.has("channels")) return null;
            JsonObject channels = chat.getAsJsonObject("channels");
            if (channel == null || !channels.has(channel)) return null;
            JsonObject channelObj = channels.getAsJsonObject(channel);
            if (!channelObj.has("discord")) return null;
            JsonObject discord = channelObj.getAsJsonObject("discord");
            if (!discord.has("enabled") || !discord.get("enabled").getAsBoolean()) return null;
            if (!discord.has("webhookUrl")) return null;
            String url = discord.get("webhookUrl").getAsString();
            return (url != null && !url.isBlank()) ? url : null;
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Could not read chat.channels.{}.discord.webhookUrl: {}", channel, e.getMessage());
            return null;
        }
    }

    /** {@code discordEventChannels.<eventKey>.webhookUrl}, or {@code null} if unset/blank/that
     *  event's entry is disabled. */
    private String resolveEventWebhook(String eventKey) {
        try {
            JsonObject config = ConfigManager.getInstance().getConfig(ConfigManager.MAIN_CONFIG);
            if (!config.has("discordEventChannels")) return null;
            JsonObject section = config.getAsJsonObject("discordEventChannels");
            if (!section.has(eventKey)) return null;
            JsonObject event = section.getAsJsonObject(eventKey);
            if (!event.has("enabled") || !event.get("enabled").getAsBoolean()) return null;
            if (!event.has("webhookUrl")) return null;
            String url = event.get("webhookUrl").getAsString();
            return (url != null && !url.isBlank()) ? url : null;
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Could not read discordEventChannels.{}.webhookUrl: {}", eventKey, e.getMessage());
            return null;
        }
    }

    private String avatarFor(ServerPlayer player) {
        return "https://mc-heads.net/avatar/" + player.getUUID();
    }

    /**
     * Posts one message to a Discord webhook URL, asynchronously — never blocks the calling
     * (server) thread. {@code username}/{@code avatarUrl} override the webhook's own configured
     * name/avatar for just this message (Discord's per-message override fields), letting chat
     * messages impersonate the sending player without needing a bot identity at all.
     */
    private void postWebhook(String webhookUrl, String username, String avatarUrl, String content) {
        try {
            if (content != null && content.length() > DISCORD_MESSAGE_LIMIT) {
                content = content.substring(0, DISCORD_MESSAGE_LIMIT - 1) + "…";
            }
            JsonObject body = new JsonObject();
            body.addProperty("content", content);
            if (username != null) body.addProperty("username", username);
            if (avatarUrl != null) body.addProperty("avatar_url", avatarUrl);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 300) {
                        NeoLog.warn(LOGGER, LogCategory.DISCORD,
                            "Webhook post failed (HTTP {}): {}", response.statusCode(), response.body());
                    }
                })
                .exceptionally(e -> {
                    NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to post to Discord webhook", e);
                    return null;
                });
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to build/post webhook message", e);
        }
    }

    @Override
    public void shutdown() {
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Generic webhook relay shut down.");
    }
}
