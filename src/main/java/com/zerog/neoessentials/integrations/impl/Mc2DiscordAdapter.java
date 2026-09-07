package com.zerog.neoessentials.integrations.impl;

import com.zerog.neoessentials.integrations.ChatIntegrationAdapter;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import fr.denisd3d.mc2discord.core.Mc2Discord;
import fr.denisd3d.mc2discord.core.MessageManager;
import fr.denisd3d.mc2discord.core.storage.LinkedPlayerEntry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public String getName() {
        return "Mc2Discord";
    }

    @Override
    public boolean initialize() {
        loaded = ModList.get().isLoaded("mc2discord");
        if (loaded) {
            NeoLog.info(LOGGER, LogCategory.DISCORD, "Mc2Discord mod detected, integration enabled.");
        } else {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord mod not found, integration disabled.");
        }
        return loaded;
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
            String cleanMessage = com.zerog.neoessentials.integrations.DiscordTextSanitizer.sanitizeMentions(
                message.replaceAll("§[0-9a-fk-or]", ""));
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                // Same rationale as SDLinkAdapter's equivalent fix: sendChatMessage() always
                // posts wherever Mc2Discord's OWN config routes chat, ignoring this parameter
                // entirely. Route directly to the configured channel instead, or a channel
                // NeoEssentials intends to be distinct (e.g. a private staff channel) would
                // silently end up wherever Mc2Discord's default chat channel is instead.
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord: relaying chat from '{}' directly to Discord channel '{}'",
                    player.getName().getString(), discordChannelId);
                sendToChannel(discordChannelId, player.getName().getString() + ": " + cleanMessage);
            } else {
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "Mc2Discord: relaying chat from '{}' via default chat route",
                    player.getName().getString());
                MessageManager.sendChatMessage(cleanMessage, player.getName().getString(), avatarFor(player)).subscribe();
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
            String text = player.getName().getString() + " joined the server";
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else {
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
            String text = player.getName().getString() + " left the server";
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else {
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
            String text = player.getName().getString() + " earned the advancement " + advancementName;
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, text);
            } else {
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
            String text = String.format("%s has been %s%s", player.getName().getString(), action,
                reason != null && !reason.isEmpty() ? " (Reason: " + reason + ")" : "");
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

    private String avatarFor(ServerPlayer player) {
        return "https://mc-heads.net/avatar/" + player.getUUID();
    }

    @Override
    public void shutdown() {
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Mc2Discord integration shut down.");
    }
}
