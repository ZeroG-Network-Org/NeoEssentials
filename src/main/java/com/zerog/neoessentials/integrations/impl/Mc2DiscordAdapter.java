package com.zerog.neoessentials.integrations.impl;

import com.zerog.neoessentials.integrations.ChatIntegrationAdapter;
import com.zerog.neoessentials.integrations.DiscordIdentityFormatter;
import com.zerog.neoessentials.integrations.DiscordTextSanitizer;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import fr.denisd3d.mc2discord.core.Mc2Discord;
import fr.denisd3d.mc2discord.core.MessageManager;
import fr.denisd3d.mc2discord.core.storage.LinkedPlayerEntry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mc2Discord integration adapter. Talks to Mc2Discord's real public API
 * (fr.denisd3d.mc2discord.core.*), compiled against a compileOnly CurseMaven dependency —
 * only ever touched after ModList confirms Mc2Discord is actually loaded, so the mod
 * remains fully optional at runtime.
 *
 * MessageManager's methods return a lazy Reactor Mono — nothing is sent until
 * .subscribe() is called, so every call here subscribes to actually fire the message.
 */
public class Mc2DiscordAdapter implements ChatIntegrationAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Mc2DiscordAdapter.class);

    private boolean loaded = false;

    // Whether Mc2Discord's OWN native broadcaster is enabled for each event type — detected once
    // at startup by detectNativeRelayConflicts() and used to SKIP this adapter's own default-route
    // send for that event, instead of double-posting. See that method's Javadoc for why, and
    // SDLinkAdapter's equivalent fields/detectNativeRelayConflicts() for the identical pattern —
    // this is the same fix, adapted to Mc2Discord's very different (per-channel subscription-list,
    // not flat boolean) config shape.
    private boolean nativeChatEnabled = false;
    private boolean nativeJoinEnabled = false;
    private boolean nativeLeaveEnabled = false;
    private boolean nativeAdvancementEnabled = false;
    // Human-readable form of whichever of the above got set — see SDLinkAdapter's equivalent
    // field and getNativeRelayWarnings() for the shared rationale (dashboard visibility).
    private final List<String> nativeRelayWarnings = new ArrayList<>();

    @Override
    public String getName() {
        return "Mc2Discord";
    }

    @Override
    public boolean initialize() {
        loaded = ModList.get().isLoaded("mc2discord");
        if (loaded) {
            NeoLog.info(LOGGER, LogCategory.DISCORD, "Mc2Discord mod detected, integration enabled.");
            detectNativeRelayConflicts();
        } else {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord mod not found, integration disabled.");
        }
        return loaded;
    }

    /**
     * Mc2Discord relays chat/join/leave/advancement to Discord entirely on its own, independent
     * of NeoEssentials, whenever ANY configured Discord channel subscribes to the matching event
     * type — unlike SDLink's flat per-event booleans, Mc2Discord's {@code config/mc2discord.toml}
     * has a {@code [[channels.channels]]} array of tables, each with its own
     * {@code subscriptions = [...]} list of type strings ({@code "chat"}, {@code
     * "player_connect"}/{@code "player_disconnect"} for join/leave, {@code "player_advancement"}
     * — confirmed directly from Mc2Discord's own bytecode, not guessed). Running both this
     * adapter's default-route send AND Mc2Discord's own native relay for the same event posts it
     * to Discord twice — the exact same class of bug fixed for SDLink, just never covered here.
     * <p>
     * This is a best-effort scan (not a real TOML parse): it isolates each {@code
     * [[channels.channels]]} block's raw lines by tracking table-header boundaries, then checks
     * the WHOLE block's text for each subscription keyword as a quoted string — correct for both
     * an inline {@code subscriptions = ["chat"]} and a multi-line array, since every line inside
     * the block gets concatenated before the keyword check runs. Fails safe (both sides could
     * send) if the file is missing or the scan throws, rather than silently going dark.
     */
    private void detectNativeRelayConflicts() {
        try {
            java.nio.file.Path cfg = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("config").resolve("mc2discord.toml");
            if (!java.nio.file.Files.exists(cfg)) return;

            List<String> channelBlocks = new ArrayList<>();
            StringBuilder current = null;
            for (String line : java.nio.file.Files.readAllLines(cfg)) {
                String trimmed = line.trim();
                if (trimmed.equals("[[channels.channels]]")) {
                    if (current != null) channelBlocks.add(current.toString());
                    current = new StringBuilder();
                    continue;
                }
                if (current != null && trimmed.startsWith("[")) {
                    // A different table header — this channel block has ended.
                    channelBlocks.add(current.toString());
                    current = null;
                    continue;
                }
                if (current != null) current.append(line).append('\n');
            }
            if (current != null) channelBlocks.add(current.toString());

            String allSubscriptions = String.join("\n", channelBlocks);
            record ConflictingKey(String subscriptionKey, String description, Runnable onDetected) {}
            List<ConflictingKey> checks = List.of(
                new ConflictingKey("\"chat\"",
                    "relays EVERY Minecraft chat message to its own subscribed Discord channel(s), " +
                    "entirely independent of NeoEssentials' chat.channels.*.discord relay — NeoEssentials' " +
                    "own DEFAULT-route chat relay (no explicit per-channel Discord ID) is now suppressed to " +
                    "avoid a duplicate; per-channel overrides to a specific Discord ID still send normally",
                    () -> nativeChatEnabled = true),
                new ConflictingKey("\"player_connect\"",
                    "posts its own player-join message natively — NeoEssentials' own join relay through " +
                    "this adapter is now suppressed to avoid a duplicate",
                    () -> nativeJoinEnabled = true),
                new ConflictingKey("\"player_disconnect\"",
                    "posts its own player-leave message natively — NeoEssentials' own leave relay through " +
                    "this adapter is now suppressed to avoid a duplicate",
                    () -> nativeLeaveEnabled = true),
                new ConflictingKey("\"player_advancement\"",
                    "posts its own advancement message natively — NeoEssentials' own advancement relay " +
                    "through this adapter is now suppressed to avoid a duplicate",
                    () -> nativeAdvancementEnabled = true)
            );
            for (ConflictingKey check : checks) {
                if (allSubscriptions.contains(check.subscriptionKey())) {
                    check.onDetected().run();
                    NeoLog.warn(LOGGER, LogCategory.DISCORD, "Mc2Discord has a channel subscribed to {} in " +
                        "config/mc2discord.toml. That {}. If you'd rather NeoEssentials be the one formatting " +
                        "this instead, remove {} from that channel's subscriptions list and restart.",
                        check.subscriptionKey(), check.description(), check.subscriptionKey());
                    nativeRelayWarnings.add("Mc2Discord has a channel subscribed to " + check.subscriptionKey() +
                        " — " + check.description());
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Could not check Mc2Discord's config for a conflicting native chat relay: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return loaded;
    }

    @Override
    public boolean isReady() {
        return loaded && Mc2Discord.INSTANCE != null
            && Mc2Discord.INSTANCE.client != null
            && Mc2Discord.INSTANCE.errors.isEmpty();
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
        if (!isReady()) return;
        try {
            String cleanMessage = DiscordTextSanitizer.sanitizeMentions(
                message.replaceAll("§[0-9a-fk-or]", ""));
            cleanMessage = DiscordTextSanitizer.truncate(cleanMessage, DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                // Same rationale as SDLinkAdapter's equivalent fix: sendChatMessage() always
                // posts wherever Mc2Discord's OWN config routes chat, ignoring this parameter
                // entirely. Route directly to the configured channel instead, or a channel
                // NeoEssentials intends to be distinct (e.g. a private staff channel) would
                // silently end up wherever Mc2Discord's default chat channel is instead.
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord: relaying chat from '{}' directly to Discord channel '{}'",
                    player.getName().getString(), discordChannelId);
                sendToChannel(discordChannelId, DiscordIdentityFormatter.resolveNameWithRank(player) + ": " + cleanMessage);
            } else if (!nativeChatEnabled) {
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord: relaying chat from '{}' via default chat route",
                    player.getName().getString());
                MessageManager.sendChatMessage(cleanMessage, DiscordIdentityFormatter.resolveNameWithRank(player), avatarFor(player)).subscribe();
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay chat message via Mc2Discord", e);
        }
    }

    @Override
    public boolean sendToChannel(String channelId, String message) {
        if (!isReady()) return false;
        try {
            boolean sent = Discord4jChannelSender.send(channelId, message);
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord: send to Discord channel '{}' {}",
                channelId, sent ? "succeeded" : "failed");
            return sent;
        } catch (Throwable e) {
            // Catches Errors too — see Discord4jChannelSender's Javadoc for why a missing/
            // incompatible Discord4J on the classpath surfaces as a LinkageError here.
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to send message to Discord channel " + channelId + " via Mc2Discord", e);
            return false;
        }
    }

    /**
     * Isolates every direct reference to Discord4J's {@code Snowflake}/{@code Possible} types in
     * a class of its own, only ever loaded — triggering classloading/verification of those
     * types — the moment {@link #send} actually runs, by which point {@link #isReady()} has
     * already confirmed Mc2Discord is genuinely present. See
     * {@link DCIntegrationAdapter.JdaChannelSender}'s Javadoc for the full rationale: the JVM's
     * bytecode verifier resolves every type referenced in a method's StackMapTable at class-LOAD
     * time, not lazily on first invocation, regardless of any {@code ModList.isLoaded()} guard in
     * the SAME class — a lesson learned the hard way when the equivalent un-isolated code in
     * {@code DCIntegrationAdapter} crashed an entire server at startup on a pack without
     * DCIntegration installed.
     */
    private static final class Discord4jChannelSender {
        static boolean send(String channelId, String message) {
            // createPlainTextMessage's 3rd parameter (Possible<String>) is NOT a channel/target
            // override — verified by reading the compiled method body — passing it non-absent
            // instead re-runs the message through Mc2Discord's OWN discord_chat_format template
            // (meant for console/system-style broadcasts with a synthetic zero-UUID "player").
            // Possible.absent() sends the message text completely unmodified to the given
            // channel via the underlying Discord4J client, which is what we want here.
            fr.denisd3d.mc2discord.shadow.discord4j.common.util.Snowflake snowflake =
                fr.denisd3d.mc2discord.shadow.discord4j.common.util.Snowflake.of(channelId);
            MessageManager.createPlainTextMessage(snowflake, message,
                fr.denisd3d.mc2discord.shadow.discord4j.discordjson.possible.Possible.absent(), false).subscribe();
            return true;
        }
    }

    @Override
    public void onPlayerJoin(ServerPlayer player, String discordChannelId) {
        if (!isReady()) return;
        try {
            String text = DiscordIdentityFormatter.resolveNameWithRank(player) + " joined the server";
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else if (!nativeJoinEnabled) {
                MessageManager.sendInfoMessage("join", text).subscribe();
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay join event via Mc2Discord", e);
        }
    }

    @Override
    public void onPlayerQuit(ServerPlayer player, String discordChannelId) {
        if (!isReady()) return;
        try {
            String text = DiscordIdentityFormatter.resolveNameWithRank(player) + " left the server";
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else if (!nativeLeaveEnabled) {
                MessageManager.sendInfoMessage("leave", text).subscribe();
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay quit event via Mc2Discord", e);
        }
    }

    @Override
    public void onPlayerAdvancement(ServerPlayer player, String advancementName, String discordChannelId) {
        if (!isReady()) return;
        try {
            String text = DiscordIdentityFormatter.resolveNameWithRank(player) + " earned the advancement " +
                DiscordTextSanitizer.truncate(advancementName, DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else if (!nativeAdvancementEnabled) {
                MessageManager.sendInfoMessage("advancement", text).subscribe();
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay advancement event via Mc2Discord", e);
        }
    }

    @Override
    public void onPlayerMute(ServerPlayer player, String reason, boolean isMuted, String discordChannelId) {
        if (!isReady()) return;
        try {
            String action = isMuted ? "muted" : "unmuted";
            String safeReason = DiscordTextSanitizer.truncate(reason, DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            String text = String.format("%s has been %s%s", DiscordIdentityFormatter.resolveNameWithRank(player), action,
                safeReason != null && !safeReason.isEmpty() ? " (Reason: " + safeReason + ")" : "");
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else {
                MessageManager.sendInfoMessage("moderation", text).subscribe();
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay mute event via Mc2Discord", e);
        }
    }

    @Override
    public void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason, String discordChannelId) {
        if (!isReady()) return;
        try {
            String status = isAfk ? "is now AFK" : "is no longer AFK";
            String safeReason = DiscordTextSanitizer.truncate(reason, DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            String text = String.format("%s %s%s", DiscordIdentityFormatter.resolveNameWithRank(player), status,
                (isAfk && safeReason != null && !safeReason.isEmpty()) ? " (" + safeReason + ")" : "");
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else {
                // "afk" is just a subscription-matching keyword to sendInfoMessage — same
                // mechanism already proven by the "moderation" category above, not a category
                // Mc2Discord has special built-in knowledge of. An admin adds "afk" to a
                // channel's subscriptions list in mc2discord.toml to receive these.
                MessageManager.sendInfoMessage("afk", text).subscribe();
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay AFK event via Mc2Discord", e);
        }
    }

    @Override
    public void onPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message, String discordChannelId) {
        if (!isReady()) return;
        try {
            String text = String.format("Private message to %s: %s", DiscordIdentityFormatter.resolveNameWithRank(recipient),
                DiscordTextSanitizer.truncate(DiscordTextSanitizer.sanitizeMentions(message), DiscordTextSanitizer.DISCORD_TEXT_LIMIT));
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, DiscordIdentityFormatter.resolveNameWithRank(sender) + ": " + text);
            } else {
                MessageManager.sendInfoMessage("privateMessage", DiscordIdentityFormatter.resolveNameWithRank(sender) + ": " + text).subscribe();
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay private message via Mc2Discord", e);
        }
    }

    @Override
    public Optional<String> getLinkedDiscordId(UUID minecraftUuid) {
        if (!isReady()) return Optional.empty();
        try {
            LinkedPlayerEntry entry = Mc2Discord.INSTANCE.linkedPlayerList.get(minecraftUuid);
            return entry != null ? Optional.of(entry.getDiscordId().asString()) : Optional.empty();
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord linked-account lookup failed for {}: {}", minecraftUuid, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> getLinkedMinecraftUuid(String discordId) {
        if (!isReady()) return Optional.empty();
        try {
            for (LinkedPlayerEntry entry : Mc2Discord.INSTANCE.linkedPlayerList.getEntries()) {
                if (entry.getDiscordId().asString().equals(discordId)) {
                    return Optional.of(entry.getPlayerUuid());
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord reverse-account lookup failed for {}: {}", discordId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> getNativeRelayWarnings() {
        return List.copyOf(nativeRelayWarnings);
    }

    /**
     * Reuses SDLink's {@code discordEmbedTemplate.authorIconUrl} config key (same top-level
     * section, same {@code {uuid}} placeholder convention as {@code EmbedTemplate} there) rather
     * than a hardcoded URL, so an admin who already changed it for SDLink doesn't need a second,
     * Mc2Discord-specific setting to get the same effect — and so a self-hosted avatar service
     * can replace mc-heads.net for both adapters at once.
     */
    private String avatarFor(ServerPlayer player) {
        String template = "https://mc-heads.net/avatar/{uuid}";
        try {
            com.google.gson.JsonObject cfg = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("discordEmbedTemplate");
            if (cfg.has("authorIconUrl")) {
                String configured = cfg.get("authorIconUrl").getAsString();
                if (configured != null && !configured.isBlank()) template = configured;
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Could not read discordEmbedTemplate.authorIconUrl, using default avatar URL", e);
        }
        return template.replace("{uuid}", player.getUUID().toString());
    }

    @Override
    public void shutdown() {
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Mc2Discord integration shut down.");
    }
}
