package com.zerog.neoessentials.integrations.impl;

import com.zerog.neoessentials.integrations.ChatIntegrationAdapter;
import com.zerog.neoessentials.integrations.DiscordIdentityFormatter;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.PlayerLink;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Discord Integration (DCIntegration) adapter. Talks to its real public API
 * (de.erdbeerbaerlp.dcintegration.common.*), compiled against a compileOnly CurseMaven
 * dependency — only ever touched after ModList confirms DCIntegration is actually loaded,
 * so the mod remains fully optional at runtime.
 *
 * Unlike SDLink/Mc2Discord, DCIntegration mixins directly into vanilla chat/join/leave/
 * command handling (ChatMixin, PlayerManagerMixin, NetworkHandlerMixin, CommandManagerMixin)
 * and relays those events to Discord entirely on its own, to whatever channel its own config
 * (general.botChannel / advanced.chatOutputChannelID) points at — this adapter has no
 * visibility into or control over that path.
 *
 * onPlayerChat/onPlayerJoin/onPlayerQuit are all implemented, but ONLY act when an explicit
 * Discord channel override is configured (chat's own per-channel discord.channelId, or
 * discordEventChannels.join/leave in config.json) — that is additive, not duplicative, since
 * DCIntegration's own native relay has no concept of either and only ever posts to its own
 * single configured channel. When no override is configured (the common case), all three
 * deliberately do nothing, to avoid double-posting against DCIntegration's own mixin-driven
 * relay of the SAME event. onPlayerMute/onAfkStatusChange/onPlayerAdvancement remain
 * unimplemented (no override path exists for them here yet) — DCIntegration has no native
 * relay for those event types to begin with, so there'd be no double-post to avoid; they're
 * simply not built out for this adapter yet.
 *
 * <p>{@link #getDiscordRoleIds(UUID)} is also implemented here (unlike SDLink, where it's a
 * genuine public-API dead end) — DCIntegration's JDA client is directly reachable, so a linked
 * player's current Discord role list can be read via a standard member lookup. This is what
 * makes DCIntegration the adapter {@link com.zerog.neoessentials.integrations.DiscordRoleSyncTask}
 * actually works with today.
 */
public class DCIntegrationAdapter implements ChatIntegrationAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DCIntegrationAdapter.class);

    private boolean loaded = false;

    @Override
    public String getName() {
        return "DCIntegration";
    }

    @Override
    public boolean initialize() {
        loaded = ModList.get().isLoaded("dcintegration");
        if (loaded) {
            NeoLog.info(LOGGER, LogCategory.DISCORD, "DCIntegration mod detected, integration enabled.");
        } else {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration mod not found, integration disabled.");
        }
        return loaded;
    }

    @Override
    public boolean isEnabled() {
        return loaded;
    }

    @Override
    public boolean isReady() {
        return loaded && DiscordIntegration.INSTANCE != null && DiscordIntegration.INSTANCE.getJDA() != null;
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
        if (!isReady() || discordChannelId == null || discordChannelId.isBlank()) return;
        try {
            String cleanMessage = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.sanitizeMentions(message.replaceAll("§[0-9a-fk-or]", "")),
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration: relaying chat from '{}' in channel '{}' to Discord channel '{}'",
                player.getName().getString(), channel, discordChannelId);
            sendToChannel(discordChannelId, DiscordIdentityFormatter.resolveNameWithRank(player) + ": " + cleanMessage);
        } catch (Throwable e) {
            // Catches Errors too — see JdaChannelSender's Javadoc for why a missing/incompatible
            // JDA on the classpath surfaces as a LinkageError here, not a plain Exception.
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay chat message via DCIntegration", e);
        }
    }

    @Override
    public void onPlayerJoin(ServerPlayer player, String discordChannelId) {
        if (!isReady() || discordChannelId == null || discordChannelId.isBlank()) return;
        try {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration: relaying join for '{}' to Discord channel '{}' (additive override — native relay untouched)",
                player.getName().getString(), discordChannelId);
            sendToChannel(discordChannelId, DiscordIdentityFormatter.resolveNameWithRank(player) + " joined the server");
        } catch (Throwable e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay join event via DCIntegration", e);
        }
    }

    @Override
    public void onPlayerQuit(ServerPlayer player, String discordChannelId) {
        if (!isReady() || discordChannelId == null || discordChannelId.isBlank()) return;
        try {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration: relaying quit for '{}' to Discord channel '{}' (additive override — native relay untouched)",
                player.getName().getString(), discordChannelId);
            sendToChannel(discordChannelId, DiscordIdentityFormatter.resolveNameWithRank(player) + " left the server");
        } catch (Throwable e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay quit event via DCIntegration", e);
        }
    }

    /**
     * Confirmed via bytecode ({@code DiscordIntegrationMod.advancement()}, subscribed to
     * NeoForge's {@code AdvancementEvent.AdvancementEarnEvent}) that DCIntegration already
     * relays advancements natively for linked players whenever its own
     * {@code Localization.advancementMessage} template is non-blank — same additive-only
     * treatment as {@link #onPlayerJoin}/{@link #onPlayerQuit}, never a default-route send.
     */
    @Override
    public void onPlayerAdvancement(ServerPlayer player, String advancementName, String discordChannelId) {
        if (!isReady() || discordChannelId == null || discordChannelId.isBlank()) return;
        try {
            String safeAdvancementName = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(advancementName,
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration: relaying advancement for '{}' to Discord channel '{}' (additive override — native relay untouched)",
                player.getName().getString(), discordChannelId);
            sendToChannel(discordChannelId, DiscordIdentityFormatter.resolveNameWithRank(player) + " earned the advancement " + safeAdvancementName);
        } catch (Throwable e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay advancement event via DCIntegration", e);
        }
    }

    /**
     * DCIntegration has no native concept of mutes at all (confirmed via bytecode — no mixin or
     * event handler references anything mute-related), so unlike advancement this isn't
     * suppressing a duplicate — it's simply additive, same as {@link #onPlayerJoin}. Still
     * override-only, since DCIntegration has no general-purpose "send to my own default channel"
     * API this adapter can call into (unlike SDLink's {@code DiscordMessageBuilder} or
     * Mc2Discord's {@code MessageManager.sendInfoMessage}) — only {@link #sendToChannel} exists.
     */
    @Override
    public void onPlayerMute(ServerPlayer player, String reason, boolean isMuted, String discordChannelId) {
        if (!isReady() || discordChannelId == null || discordChannelId.isBlank()) return;
        try {
            String action = isMuted ? "muted" : "unmuted";
            String safeReason = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(reason,
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            String text = String.format("%s has been %s%s", DiscordIdentityFormatter.resolveNameWithRank(player), action,
                safeReason != null && !safeReason.isEmpty() ? " (Reason: " + safeReason + ")" : "");
            sendToChannel(discordChannelId, text);
        } catch (Throwable e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay mute event via DCIntegration", e);
        }
    }

    /** See {@link #onPlayerMute}'s Javadoc — same rationale (no native concept, override-only). */
    @Override
    public void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason, String discordChannelId) {
        if (!isReady() || discordChannelId == null || discordChannelId.isBlank()) return;
        try {
            String status = isAfk ? "is now AFK" : "is no longer AFK";
            String safeReason = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(reason,
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            String text = String.format("%s %s%s", DiscordIdentityFormatter.resolveNameWithRank(player), status,
                (isAfk && safeReason != null && !safeReason.isEmpty()) ? " (" + safeReason + ")" : "");
            sendToChannel(discordChannelId, text);
        } catch (Throwable e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay AFK event via DCIntegration", e);
        }
    }

    @Override
    public boolean sendToChannel(String channelId, String message) {
        if (!isReady()) return false;
        try {
            boolean sent = JdaChannelSender.send(channelId, message);
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration: send to Discord channel '{}' {}",
                channelId, sent ? "succeeded" : "failed (channel not found)");
            return sent;
        } catch (Throwable e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to send message to Discord channel " + channelId + " via DCIntegration", e);
            return false;
        }
    }

    /**
     * Isolates every direct reference to JDA's channel-hierarchy types (GuildMessageChannel /
     * TextChannel / MessageChannel) in a class of its own. This class is only ever loaded —
     * triggering classloading/verification of those types — the moment {@link #send} is
     * actually invoked, by which point {@link #isReady()} has already confirmed DCIntegration
     * (and therefore its JDA) is genuinely present and version-compatible.
     *
     * <p>This split is required, not just tidy: the JVM's bytecode verifier resolves every type
     * mentioned in a method's StackMapTable at class-LOAD time (as part of linking), not lazily
     * on first invocation — and this happens regardless of any {@code ModList.isLoaded()} guard
     * inside the SAME class, since the guard is only checked once the method actually runs, long
     * after the class (and everything it references) was already required to resolve. Before
     * this split, simply constructing {@code new DCIntegrationAdapter()} in
     * {@code ChatIntegrationManager.initialize()} — which happens unconditionally on every
     * server start — crashed the entire server with a {@code NoClassDefFoundError} on any pack
     * where DCIntegration isn't installed (or an older/incompatible JDA elsewhere on the
     * classpath lacks this newer channel-type package), because DCIntegrationAdapter itself
     * used to reference these types directly.
     */
    private static final class JdaChannelSender {
        static boolean send(String channelId, String message) {
            var channel = DiscordIntegration.INSTANCE.getJDA().getTextChannelById(channelId);
            if (channel == null) {
                NeoLog.warn(LOGGER, LogCategory.DISCORD, "DCIntegration: no text channel found with ID '{}' (bot may not be in that server, or the ID is wrong)", channelId);
                return false;
            }
            DiscordIntegration.INSTANCE.sendMessage(message, channel);
            return true;
        }
    }

    @Override
    public Optional<String> getLinkedDiscordId(UUID minecraftUuid) {
        if (!isReady()) return Optional.empty();
        try {
            PlayerLink link = LinkManager.getLink(null, minecraftUuid);
            return link != null ? Optional.of(link.discordID) : Optional.empty();
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration linked-account lookup failed for {}: {}", minecraftUuid, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> getDiscordRoleIds(UUID minecraftUuid) {
        if (!isReady()) return List.of();
        Optional<String> discordId = getLinkedDiscordId(minecraftUuid);
        if (discordId.isEmpty()) return List.of();
        try {
            return JdaRoleFetcher.getRoleIds(discordId.get());
        } catch (Throwable e) {
            // Catches Errors too — same rationale as JdaChannelSender's Javadoc.
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration role lookup failed for Discord ID {}: {}", discordId.get(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Isolates every direct reference to JDA's {@code Member}/{@code Role} types — same
     * rationale as {@link JdaChannelSender}'s Javadoc: the bytecode verifier resolves every
     * type in this class's methods at class-LOAD time, so keeping them out of the containing
     * class means they're never touched unless this method actually runs, by which point
     * {@link #isReady()} already confirmed DCIntegration (and its JDA) is genuinely present.
     */
    private static final class JdaRoleFetcher {
        static List<String> getRoleIds(String discordId) {
            // getMemberById() checks DCIntegration's own member cache first, falling back to a
            // blocking lookup — preferred over a raw JDA guild/member query for consistency with
            // the rest of this mod's Discord access. Its own JDABuilder already requests
            // GatewayIntent.GUILD_MEMBERS (confirmed via bytecode), so role data is populated.
            net.dv8tion.jda.api.entities.Member member = DiscordIntegration.INSTANCE.getMemberById(discordId);
            if (member == null) return List.of();
            return member.getRoles().stream()
                .map(net.dv8tion.jda.api.entities.Role::getId)
                .toList();
        }
    }

    @Override
    public Optional<UUID> getLinkedMinecraftUuid(String discordId) {
        if (!isReady()) return Optional.empty();
        try {
            PlayerLink link = LinkManager.getLink(discordId, null);
            return link != null ? Optional.of(UUID.fromString(link.mcPlayerUUID)) : Optional.empty();
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "DCIntegration reverse-account lookup failed for {}: {}", discordId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void shutdown() {
        NeoLog.info(LOGGER, LogCategory.DISCORD, "DCIntegration integration shut down.");
    }
}
