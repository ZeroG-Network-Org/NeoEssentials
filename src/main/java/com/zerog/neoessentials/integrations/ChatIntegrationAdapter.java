package com.zerog.neoessentials.integrations;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for chat integration adapters.
 * Allows external NeoForge mods to hook into NeoEssentials chat events.
 */
public interface ChatIntegrationAdapter {
    
    /**
     * Get the name of this integration adapter
     * @return The adapter name
     */
    String getName();
    
    /**
     * Called when a player sends a chat message in a channel
     * @param player The player sending the message
     * @param channel The channel name (e.g., "local", "global", "staff")
     * @param message The message content
     * @param formattedMessage The fully formatted message with colors/placeholders resolved
     * @param discordChannelId Optional Discord channel ID to send to (null = use default)
     */
    default void onPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
        // Default implementation does nothing
    }

    /**
     * Called when a private message is sent between players
     * @param sender The message sender
     * @param recipient The message recipient
     * @param message The message content
     */
    default void onPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message) {
        onPrivateMessage(sender, recipient, message, null);
    }

    /**
     * Same as {@link #onPrivateMessage(ServerPlayer, ServerPlayer, String)}, with an optional
     * Discord channel override — see {@link #onPlayerChat}'s {@code discordChannelId} parameter
     * for the same convention this mirrors. Override THIS method (not the 3-arg one) to support
     * per-event channel routing; the 3-arg version above delegates here with a null channel so
     * it stays a valid override point for adapters that don't care about routing. This one's own
     * default does nothing — same as the 3-arg version always did — so an adapter that overrides
     * neither still gets the original no-op behavior with no risk of the two defaults looping.
     * @param discordChannelId Optional Discord channel ID to send to (null = adapter/mod default)
     */
    default void onPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message, String discordChannelId) {
        // Default implementation does nothing
    }

    /**
     * Called when a player's mute status changes
     * @param player The affected player
     * @param reason The mute/unmute reason
     * @param isMuted true if being muted, false if being unmuted
     */
    default void onPlayerMute(ServerPlayer player, String reason, boolean isMuted) {
        onPlayerMute(player, reason, isMuted, null);
    }

    /** See {@link #onPrivateMessage(ServerPlayer, ServerPlayer, String, String)}'s javadoc — same
     *  delegate-down convention and the same reason for it. */
    default void onPlayerMute(ServerPlayer player, String reason, boolean isMuted, String discordChannelId) {
        // Default implementation does nothing
    }

    /**
     * Called when a player's AFK status changes
     * @param player The affected player
     * @param isAfk true if going AFK, false if returning
     * @param reason The AFK reason (may be null)
     */
    default void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason) {
        onAfkStatusChange(player, isAfk, reason, null);
    }

    /** See {@link #onPrivateMessage(ServerPlayer, ServerPlayer, String, String)}'s javadoc — same
     *  delegate-down convention and the same reason for it. */
    default void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason, String discordChannelId) {
        // Default implementation does nothing
    }

    /**
     * Called when a player joins the server
     * @param player The joining player
     */
    default void onPlayerJoin(ServerPlayer player) {
        onPlayerJoin(player, null);
    }

    /** See {@link #onPrivateMessage(ServerPlayer, ServerPlayer, String, String)}'s javadoc — same
     *  delegate-down convention and the same reason for it. */
    default void onPlayerJoin(ServerPlayer player, String discordChannelId) {
        // Default implementation does nothing
    }

    /**
     * Called when a player quits the server
     * @param player The quitting player
     */
    default void onPlayerQuit(ServerPlayer player) {
        onPlayerQuit(player, null);
    }

    /** See {@link #onPrivateMessage(ServerPlayer, ServerPlayer, String, String)}'s javadoc — same
     *  delegate-down convention and the same reason for it. */
    default void onPlayerQuit(ServerPlayer player, String discordChannelId) {
        // Default implementation does nothing
    }
    
    /**
     * Called to check if this adapter is enabled and functional
     * @return true if the adapter is ready to receive events
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Whether the underlying Discord bot/gateway connection is actually up right now.
     * Distinct from {@link #isEnabled()}, which only means the companion mod is present.
     * @return true if messages sent now are actually expected to reach Discord
     */
    default boolean isReady() {
        return isEnabled();
    }

    /**
     * Resolve the Discord user ID linked to a Minecraft player, if the companion mod
     * has that link and it's verified.
     * @param minecraftUuid The player's Minecraft UUID
     * @return The linked Discord snowflake ID, or empty if not linked/unsupported
     */
    default Optional<String> getLinkedDiscordId(UUID minecraftUuid) {
        return Optional.empty();
    }

    /**
     * Human-readable warnings about a detected conflict between this adapter's own relay and the
     * companion mod's native one (e.g. SDLink's {@code chat.playerMessages} already relaying chat
     * natively) — surfaced by the web dashboard's Discord status panel so an admin configuring
     * integration through it sees the same thing the startup log already warns about, without
     * needing console access. Empty if this adapter doesn't do this kind of detection, or found
     * no conflicts.
     * @return Zero or more warning strings, safe to display as-is
     */
    default List<String> getNativeRelayWarnings() {
        return List.of();
    }

    /**
     * Resolve the Discord role IDs held by a linked player, if the companion mod exposes them.
     * @param minecraftUuid The player's Minecraft UUID
     * @return The player's Discord role IDs, or an empty list if not linked/unsupported
     */
    default List<String> getDiscordRoleIds(UUID minecraftUuid) {
        return List.of();
    }

    /**
     * Reverse of {@link #getLinkedDiscordId(UUID)} — resolve the Minecraft account linked to a
     * Discord user ID, if the companion mod has that link and it's verified.
     * @param discordId The Discord snowflake ID
     * @return The linked Minecraft UUID, or empty if not linked/unsupported
     */
    default Optional<UUID> getLinkedMinecraftUuid(String discordId) {
        return Optional.empty();
    }

    /**
     * Sends a raw message to an arbitrary Discord channel by its snowflake ID — used by the
     * dashboard's "send test message" feature, which needs to target a specific channel
     * directly rather than going through this mod's own event-type routing
     * ({@link #onPlayerChat}/{@link #onPlayerMute}/etc, which route to whatever channel the
     * companion mod has configured for that event type).
     * @param channelId Discord channel snowflake ID
     * @param message   Plain text to send
     * @return true if the message was actually sent, false if unsupported/channel not
     *         found/adapter not ready — callers should try the next adapter or report failure.
     */
    default boolean sendToChannel(String channelId, String message) {
        return false;
    }

    /**
     * Called when a player earns an advancement.
     * @param player The player
     * @param advancementName The advancement's display/title text
     */
    default void onPlayerAdvancement(ServerPlayer player, String advancementName) {
        onPlayerAdvancement(player, advancementName, null);
    }

    /** See {@link #onPrivateMessage(ServerPlayer, ServerPlayer, String, String)}'s javadoc — same
     *  delegate-down convention and the same reason for it. */
    default void onPlayerAdvancement(ServerPlayer player, String advancementName, String discordChannelId) {
        // Default implementation does nothing
    }

    /**
     * Called when the adapter should initialize itself
     * @return true if initialization was successful
     */
    default boolean initialize() {
        return true;
    }

    /**
     * Called when the adapter should clean up resources
     */
    default void shutdown() {
        // Default implementation does nothing
    }
}