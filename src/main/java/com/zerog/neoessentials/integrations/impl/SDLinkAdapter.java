package com.zerog.neoessentials.integrations.impl;

import com.google.gson.JsonObject;
import com.hypherionmc.craterlib.api.game.authlib.CraterGameProfile;
import com.hypherionmc.sdlink.api.accounts.DiscordAuthor;
import com.hypherionmc.sdlink.api.accounts.DiscordUser;
import com.hypherionmc.sdlink.api.accounts.MinecraftAccount;
import com.hypherionmc.sdlink.api.messaging.MessageType;
import com.hypherionmc.sdlink.api.messaging.discord.DiscordMessageBuilder;
import com.hypherionmc.sdlink.core.discord.BotController;
import com.zerog.neoessentials.integrations.ChatIntegrationAdapter;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Simple Discord Link (SDLink) integration adapter.
 * Talks to SDLink's real public API (com.hypherionmc.sdlink.api.*), compiled against
 * a compileOnly CurseMaven dependency — only ever touched after ModList confirms SDLink
 * is actually loaded, so the mod remains fully optional at runtime.
 */
public class SDLinkAdapter implements ChatIntegrationAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SDLinkAdapter.class);

    private boolean loaded = false;

    // Whether SDLink's OWN native broadcaster is enabled for each event type — detected once at
    // startup by detectNativeRelayConflicts() and used to SKIP NeoEssentials' own default-route
    // send for that event, instead of just warning about it. See that method's Javadoc for why.
    private boolean nativeChatEnabled = false;
    private boolean nativeJoinEnabled = false;
    private boolean nativeLeaveEnabled = false;
    private boolean nativeAdvancementEnabled = false;
    // Human-readable form of whichever of the above got set, for the dashboard's Discord status
    // panel — see getNativeRelayWarnings(). Populated once, at the same time as the booleans.
    private final List<String> nativeRelayWarnings = new java.util.ArrayList<>();

    @Override
    public String getName() {
        return "Simple Discord Link";
    }

    @Override
    public boolean initialize() {
        loaded = ModList.get().isLoaded("sdlink");
        if (loaded) {
            NeoLog.info(LOGGER, LogCategory.DISCORD, "Simple Discord Link mod detected, integration enabled.");
            detectNativeRelayConflicts();
        } else {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Simple Discord Link mod not found, integration disabled.");
        }
        return loaded;
    }

    /**
     * SDLink ships several independent, config-driven native broadcasters — chat relay
     * ({@code chat.playerMessages}), join/leave ({@code chat.playerJoin}/{@code chat.playerLeave}),
     * and advancements ({@code chat.advancementMessages}) — all default-enabled in its own config
     * file, completely separate from, and unaware of, the equivalent events NeoEssentials sends
     * through this SAME adapter's {@link #onPlayerChat}/{@link #onPlayerJoin}/
     * {@link #onPlayerQuit}/{@link #onPlayerAdvancement}. Running both at once means every one of
     * those events posts to Discord TWICE with a SINGLE bridge mod installed — no second Discord
     * bridge mod required, despite how that looks from the Discord side (SDLink's native message
     * uses its own phrasing/formatting, e.g. "*Player has left the server!*", so the two posts
     * don't even look alike).
     * <p>
     * Rather than just warning and leaving the duplicate in place, the detected flags here make
     * NeoEssentials the passive side by default: {@link #onPlayerJoin}/{@link #onPlayerQuit}/
     * {@link #onPlayerAdvancement} skip their SDLink send entirely when SDLink's native equivalent
     * is on, and {@link #onPlayerChat}'s DEFAULT route (no explicit per-channel Discord ID) does
     * the same for {@code playerMessages} — a channel-specific override still always sends, since
     * that targets a different channel than SDLink's native relay ever touches, so it's not a
     * duplicate.
     * <p>
     * Reads the file through a real TOML parse ({@link TomlConflictReader}, isolated the same way
     * as {@link JdaBridge} below), not a line/regex scan — a regex scan is fragile to anything
     * that changes the file's exact formatting (a comment containing "playerMessages", a value
     * written as {@code true # note}, non-standard nesting), which would silently defeat detection
     * and cause NeoEssentials to double-send, exactly the bug this adapter exists to avoid. Never
     * writes to SDLink's config file, and fails safe (both sides could send) if the parse fails
     * for any reason, rather than silently going dark on join/leave/chat.
     */
    private void detectNativeRelayConflicts() {
        try {
            java.nio.file.Path cfg = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("config").resolve("simple-discord-link").resolve("simple-discord-link.toml");
            if (!java.nio.file.Files.exists(cfg)) return;

            TomlConflictReader.ChatSectionFlags flags = TomlConflictReader.read(cfg);

            record ConflictingKey(String key, boolean detected, String description, Runnable onDetected) {}
            List<ConflictingKey> checks = List.of(
                new ConflictingKey("playerMessages", flags.playerMessages(),
                    "relays EVERY Minecraft chat message to its own configured Discord channel, " +
                    "entirely independent of NeoEssentials' chat.channels.*.discord relay — NeoEssentials' " +
                    "own DEFAULT-route chat relay (no explicit per-channel Discord ID) is now suppressed to " +
                    "avoid a duplicate; per-channel overrides to a specific Discord ID still send normally",
                    () -> nativeChatEnabled = true),
                new ConflictingKey("playerJoin", flags.playerJoin(),
                    "posts its own player-join message natively — NeoEssentials' own join relay through " +
                    "this adapter is now suppressed to avoid a duplicate",
                    () -> nativeJoinEnabled = true),
                new ConflictingKey("playerLeave", flags.playerLeave(),
                    "posts its own player-leave message natively — NeoEssentials' own leave relay through " +
                    "this adapter is now suppressed to avoid a duplicate",
                    () -> nativeLeaveEnabled = true),
                new ConflictingKey("advancementMessages", flags.advancementAlways(),
                    "posts its own advancement message natively — NeoEssentials' own advancement relay " +
                    "through this adapter is now suppressed to avoid a duplicate",
                    () -> nativeAdvancementEnabled = true)
            );

            for (ConflictingKey check : checks) {
                if (check.detected()) {
                    check.onDetected().run();
                    NeoLog.warn(LOGGER, LogCategory.DISCORD, "Simple Discord Link's own 'chat.{}' is enabled in " +
                        "config/simple-discord-link/simple-discord-link.toml. That {}. If you'd rather " +
                        "NeoEssentials be the one formatting these instead, set '{}' to a non-conflicting " +
                        "value under [chat] in that file and restart.",
                        check.key(), check.description(), check.key());
                    nativeRelayWarnings.add("SDLink's own 'chat." + check.key() + "' is enabled — " + check.description());
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Could not check Simple Discord Link's config for a conflicting native chat relay: {}", e.getMessage());
        }
    }

    /**
     * Isolates every direct reference to NightConfig's TOML parser types
     * ({@code com.electronwill.nightconfig.*}) in a class of its own — same rationale as
     * {@link JdaBridge}'s Javadoc, even though NightConfig is always present at runtime here
     * (it's a transitive dependency of NeoForge's own {@code ModConfigSpec}, not of SDLink):
     * defense-in-depth against a future NeoForge version bumping it to an incompatible major
     * version. {@link ChatSectionFlags} exposes only primitive {@code boolean} fields, so the
     * NightConfig types touched inside {@link #read} never appear in this class's own
     * StackMapTable either.
     */
    private static final class TomlConflictReader {
        record ChatSectionFlags(boolean playerMessages, boolean playerJoin, boolean playerLeave, boolean advancementAlways) {}

        static ChatSectionFlags read(java.nio.file.Path file) throws java.io.IOException {
            try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file)) {
                com.electronwill.nightconfig.core.CommentedConfig config =
                    new com.electronwill.nightconfig.toml.TomlParser().parse(reader);
                boolean playerMessages = config.getOrElse("chat.playerMessages", false);
                boolean playerJoin = config.getOrElse("chat.playerJoin", false);
                boolean playerLeave = config.getOrElse("chat.playerLeave", false);
                String advancementMessages = config.getOrElse("chat.advancementMessages", "");
                return new ChatSectionFlags(playerMessages, playerJoin, playerLeave,
                    "ALWAYS".equalsIgnoreCase(advancementMessages));
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return loaded;
    }

    @Override
    public boolean isReady() {
        return loaded && BotController.INSTANCE != null && BotController.INSTANCE.isBotReady();
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
        if (!isReady()) return;
        try {
            String cleanMessage = com.zerog.neoessentials.integrations.DiscordTextSanitizer.sanitizeMentions(message.replaceAll("§[0-9a-fk-or]", ""));
            cleanMessage = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(cleanMessage,
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                // A specific Discord channel was configured for this NeoEssentials chat channel
                // (e.g. a private staff channel). SDLink's DiscordMessageBuilder has no API to
                // target an arbitrary channel — MessageType.CHAT always posts wherever SDLink's
                // OWN messageDestinations.chat config says, ignoring this parameter entirely.
                // Build the same author+avatar look manually via JDA's own EmbedBuilder instead
                // of settling for an unstyled "PlayerName: message" line — customizable via the
                // top-level discordEmbedTemplate config section (see readEmbedTemplate()).
                EmbedTemplate template = readEmbedTemplate();
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "SDLink: relaying chat from '{}' directly to Discord channel '{}' ({})",
                    player.getName().getString(), discordChannelId, template.enabled ? "templated embed" : "plain text");
                if (template.enabled) {
                    JdaBridge.sendTemplatedEmbed(discordChannelId,
                        template.resolve(template.authorName, player, channel, cleanMessage),
                        template.resolve(template.authorIconUrl, player, channel, cleanMessage),
                        template.resolve(template.description, player, channel, cleanMessage),
                        template.color,
                        template.resolve(template.footerText, player, channel, cleanMessage),
                        template.footerIconUrl,
                        template.showTimestamp);
                } else {
                    JdaBridge.sendPlain(discordChannelId, com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player) + ": " + cleanMessage);
                }
            } else if (!nativeChatEnabled) {
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "SDLink: relaying chat from '{}' via default chat route",
                    player.getName().getString());
                send(MessageType.CHAT, authorFor(player), cleanMessage);
            } else {
                NeoLog.debug(LOGGER, LogCategory.DISCORD, "SDLink: skipping default-route relay for '{}' — " +
                    "SDLink's own native chat.playerMessages is already relaying it", player.getName().getString());
            }
        } catch (Throwable e) {
            // Catches Errors too — see JdaBridge's Javadoc for why a missing/incompatible JDA
            // on the classpath surfaces as a LinkageError here, not a plain Exception.
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay chat message via SDLink", e);
        }
    }

    /**
     * Plain data holder for the {@code discordEmbedTemplate} config section (top-level in
     * {@code config.json}, or its own {@code templates/discord_embed.json} split file — see
     * {@code ConfigSplitter.FILE_SECTIONS_MAP}). Deliberately holds only {@link String}/
     * {@code boolean} fields — no JDA/Discord4J types — so reading config never risks the
     * classloading issue {@link JdaBridge} exists to avoid.
     * <p>
     * Every event type (chat, join, leave, mute, afk, advancement) that routes to an explicit
     * per-channel Discord ID gets one of these — chat's own top-level fields are unchanged for
     * backward compatibility; the other five read from a same-shaped nested object
     * ({@code discordEmbedTemplate.join}, etc.), each with its own sensible defaults so a server
     * that never touches the new sections still gets a styled embed instead of a bare text line.
     */
    private static final class EmbedTemplate {
        boolean enabled = true;
        String authorName = "{player}";
        String authorIconUrl = "https://mc-heads.net/avatar/{uuid}";
        String description = "{message}";
        String color = "#5865F2";
        String footerText = "";
        String footerIconUrl = "";
        boolean showTimestamp = false;

        String resolve(String template, ServerPlayer player, String channel, String message) {
            if (template == null) return "";
            return template
                .replace("{player}", com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player))
                .replace("{uuid}", player.getUUID().toString())
                .replace("{message}", message)
                .replace("{channel}", channel != null ? channel : "");
        }
    }

    /** Built-in per-event-type defaults, used whenever {@code discordEmbedTemplate.<eventKey>} doesn't override a field. */
    private static EmbedTemplate defaultTemplateFor(String eventKey) {
        EmbedTemplate t = new EmbedTemplate();
        switch (eventKey) {
            case "join" -> {
                t.description = "**{player}** joined the server";
                t.color = "#57F287"; // Discord green
                t.showTimestamp = true;
            }
            case "leave" -> {
                t.description = "**{player}** left the server";
                t.color = "#ED4245"; // Discord red
                t.showTimestamp = true;
            }
            case "mute" -> {
                t.description = "{message}";
                t.color = "#FEE75C"; // Discord yellow
            }
            case "afk" -> {
                t.description = "{message}";
            }
            case "advancement" -> {
                t.description = "**{player}** earned the advancement **{message}**";
                t.color = "#FAA61A"; // Discord orange
                t.showTimestamp = true;
            }
            default -> { /* "chat" — the built-in defaults above already match its prior behavior. */ }
        }
        return t;
    }

    private static EmbedTemplate readEmbedTemplate() {
        return readEmbedTemplate("chat");
    }

    /**
     * @param eventKey "chat" reads {@code discordEmbedTemplate}'s own top-level fields (unchanged
     *                 shape/behavior); anything else reads the nested
     *                 {@code discordEmbedTemplate.<eventKey>} object, if present.
     */
    private static EmbedTemplate readEmbedTemplate(String eventKey) {
        EmbedTemplate t = defaultTemplateFor(eventKey);
        try {
            JsonObject root = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("discordEmbedTemplate");
            JsonObject cfg = "chat".equals(eventKey) ? root
                : (root.has(eventKey) && root.get(eventKey).isJsonObject() ? root.getAsJsonObject(eventKey) : null);
            if (cfg != null) {
                if (cfg.has("enabled")) t.enabled = cfg.get("enabled").getAsBoolean();
                if (cfg.has("authorName")) t.authorName = cfg.get("authorName").getAsString();
                if (cfg.has("authorIconUrl")) t.authorIconUrl = cfg.get("authorIconUrl").getAsString();
                if (cfg.has("description")) t.description = cfg.get("description").getAsString();
                if (cfg.has("color")) t.color = cfg.get("color").getAsString();
                if (cfg.has("footerText")) t.footerText = cfg.get("footerText").getAsString();
                if (cfg.has("footerIconUrl")) t.footerIconUrl = cfg.get("footerIconUrl").getAsString();
                if (cfg.has("showTimestamp")) t.showTimestamp = cfg.get("showTimestamp").getAsBoolean();
            }
        } catch (Exception e) {
            // Config missing/malformed — fall back to the built-in defaults above.
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "Could not read discordEmbedTemplate.{} config, using defaults", eventKey, e);
        }
        return t;
    }

    /**
     * Routes a non-chat event's explicit channel-override send through the same embed machinery
     * {@link #onPlayerChat} already uses for its own override path, instead of a bare text line —
     * see {@link #defaultTemplateFor} for each event's built-in look. Falls back to
     * {@code plainText} when {@code discordEmbedTemplate.<eventKey>.enabled} is false.
     */
    private void sendEventEmbedOrPlain(String eventKey, String discordChannelId, ServerPlayer player, String messageContent, String plainText) {
        EmbedTemplate template = readEmbedTemplate(eventKey);
        if (template.enabled) {
            JdaBridge.sendTemplatedEmbed(discordChannelId,
                template.resolve(template.authorName, player, null, messageContent),
                template.resolve(template.authorIconUrl, player, null, messageContent),
                template.resolve(template.description, player, null, messageContent),
                template.color,
                template.resolve(template.footerText, player, null, messageContent),
                template.footerIconUrl,
                template.showTimestamp);
        } else {
            JdaBridge.sendPlain(discordChannelId, plainText);
        }
    }

    @Override
    public void onPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message, String discordChannelId) {
        if (!isReady()) return;
        try {
            String text = String.format("Private message to %s: %s", recipient.getName().getString(),
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(
                    com.zerog.neoessentials.integrations.DiscordTextSanitizer.sanitizeMentions(message),
                    com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT));
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendToChannel(discordChannelId, sender.getName().getString() + ": " + text);
            } else {
                send(MessageType.CUSTOM, authorFor(sender), text);
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay private message via SDLink", e);
        }
    }

    @Override
    public void onPlayerMute(ServerPlayer player, String reason, boolean isMuted, String discordChannelId) {
        if (!isReady()) return;
        try {
            String action = isMuted ? "muted" : "unmuted";
            String safeReason = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(reason,
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            String text = String.format("%s has been %s%s", com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player), action,
                safeReason != null && !safeReason.isEmpty() ? " (Reason: " + safeReason + ")" : "");
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendEventEmbedOrPlain("mute", discordChannelId, player, text, text);
            } else {
                send(MessageType.CUSTOM, DiscordAuthor.getServer(), text);
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay mute event via SDLink", e);
        }
    }

    @Override
    public void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason, String discordChannelId) {
        if (!isReady()) return;
        try {
            String status = isAfk ? "is now AFK" : "is no longer AFK";
            String safeReason = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(reason,
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            String text = String.format("%s %s%s", com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player), status,
                (isAfk && safeReason != null && !safeReason.isEmpty()) ? " (" + safeReason + ")" : "");
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendEventEmbedOrPlain("afk", discordChannelId, player, text, text);
            } else {
                send(MessageType.CUSTOM, DiscordAuthor.getServer(), text);
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay AFK event via SDLink", e);
        }
    }

    @Override
    public void onPlayerJoin(ServerPlayer player, String discordChannelId) {
        if (!isReady()) return;
        try {
            String text = com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player) + " joined the server";
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                // Explicit channel override always sends, same convention as onPlayerChat's
                // channel-override path — see detectNativeRelayConflicts()'s Javadoc for why an
                // override is assumed to target a channel SDLink's native relay doesn't touch.
                sendEventEmbedOrPlain("join", discordChannelId, player, "", text);
            } else if (!nativeJoinEnabled) {
                send(MessageType.JOIN, authorFor(player), text);
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay join event via SDLink", e);
        }
    }

    @Override
    public void onPlayerQuit(ServerPlayer player, String discordChannelId) {
        if (!isReady()) return;
        try {
            String text = com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player) + " left the server";
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendEventEmbedOrPlain("leave", discordChannelId, player, "", text);
            } else if (!nativeLeaveEnabled) {
                send(MessageType.LEAVE, authorFor(player), text);
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay quit event via SDLink", e);
        }
    }

    @Override
    public void onPlayerAdvancement(ServerPlayer player, String advancementName, String discordChannelId) {
        if (!isReady()) return;
        try {
            String safeAdvancementName = com.zerog.neoessentials.integrations.DiscordTextSanitizer.truncate(advancementName,
                com.zerog.neoessentials.integrations.DiscordTextSanitizer.DISCORD_TEXT_LIMIT);
            String text = com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player) + " earned the advancement " + safeAdvancementName;
            if (discordChannelId != null && !discordChannelId.isBlank()) {
                sendEventEmbedOrPlain("advancement", discordChannelId, player, safeAdvancementName, text);
            } else if (!nativeAdvancementEnabled) {
                send(MessageType.ADVANCEMENTS, authorFor(player), text);
            }
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to relay advancement event via SDLink", e);
        }
    }

    @Override
    public Optional<String> getLinkedDiscordId(UUID minecraftUuid) {
        if (!isReady()) return Optional.empty();
        try {
            MinecraftAccount account = MinecraftAccount.of(CraterGameProfile.fromGame(minecraftUuid.toString(), minecraftUuid));
            if (account == null || !account.isAccountVerified()) return Optional.empty();
            DiscordUser user = account.getDiscordUser();
            return user != null ? Optional.of(Long.toUnsignedString(user.getUserId())) : Optional.empty();
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "SDLink linked-account lookup failed for {}: {}", minecraftUuid, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> getLinkedMinecraftUuid(String discordId) {
        if (!isReady()) return Optional.empty();
        try {
            MinecraftAccount account = MinecraftAccount.fromDiscordId(discordId);
            return account != null && account.isAccountVerified() ? Optional.of(account.getUuid()) : Optional.empty();
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "SDLink reverse-account lookup failed for {}: {}", discordId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> getDiscordRoleIds(UUID minecraftUuid) {
        // SDLink's public API doesn't expose the linked member's role ID list, only
        // display identity (DiscordUser: name/avatar/mention/role color) — role-based
        // permission sync isn't achievable through this adapter without reaching into
        // SDLink's internal (non-API) classes, which is exactly what this rewrite avoids.
        return List.of();
    }

    @Override
    public List<String> getNativeRelayWarnings() {
        return List.copyOf(nativeRelayWarnings);
    }

    /**
     * The first argument here is SDLink's own {@code %display_name%} source (see
     * {@code DiscordAuthor.getDisplayName()} — a plain passthrough of whatever we supply, not
     * independently resolved by SDLink itself). Deliberately NOT {@code player.getDisplayName()}
     * — see {@link com.zerog.neoessentials.integrations.DiscordIdentityFormatter}'s Javadoc for
     * why. The third argument ({@code %mc_name%}) stays the bare username on purpose, so admins
     * who want the raw Minecraft name specifically (e.g. for @-mentions or search) still have it.
     */
    private DiscordAuthor authorFor(ServerPlayer player) {
        return DiscordAuthor.of(
            com.zerog.neoessentials.integrations.DiscordIdentityFormatter.resolveNameWithRank(player),
            player.getUUID().toString(),
            player.getName().getString());
    }

    @Override
    public boolean sendToChannel(String channelId, String message) {
        if (!isReady()) return false;
        try {
            boolean sent = JdaBridge.sendPlain(channelId, message);
            NeoLog.debug(LOGGER, LogCategory.DISCORD, "SDLink: send to Discord channel '{}' {}",
                channelId, sent ? "succeeded" : "failed");
            return sent;
        } catch (Throwable e) {
            NeoLog.error(LOGGER, LogCategory.DISCORD, "Failed to send message to Discord channel " + channelId + " via SDLink", e);
            return false;
        }
    }

    private void send(MessageType type, DiscordAuthor author, String message) {
        new DiscordMessageBuilder(type)
            .author(author)
            .message(message)
            .build()
            .sendMessage();
    }

    /**
     * Isolates every direct reference to SDLink's shaded JDA channel/embed types
     * ({@code com.hypherionmc.sdlink.shaded.dv8tion.jda.*}) in a class of its own. This class is
     * only ever loaded — triggering classloading/verification of those types — the moment one
     * of its methods is actually invoked, by which point {@link #isReady()} has already
     * confirmed SDLink is genuinely present and its bot connection is up.
     *
     * <p>This split is required, not just tidy: the JVM's bytecode verifier resolves every type
     * mentioned in a method's StackMapTable at class-LOAD time (as part of linking), not lazily
     * on first invocation — and this happens regardless of any {@code ModList.isLoaded()} guard
     * inside the SAME class, since the guard is only checked once the method actually runs, long
     * after the class (and everything it references) was already required to resolve. This
     * class previously referenced these shaded types directly inside {@code SDLinkAdapter}
     * itself, which would have crashed the entire server on startup on any pack without SDLink
     * installed — exactly what happened with the equivalent un-isolated code in
     * {@code DCIntegrationAdapter} (see its {@code JdaChannelSender} for the full incident).
     */
    private static final class JdaBridge {
        /** Plain-text send — used by the dashboard's generic "send test message" feature. */
        static boolean sendPlain(String channelId, String message) {
            var channel = BotController.INSTANCE.getJDA().getTextChannelById(channelId);
            if (channel == null) {
                NeoLog.warn(LOGGER, LogCategory.DISCORD, "SDLink: no text channel found with ID '{}' (bot may not be in that server, or the ID is wrong)", channelId);
                return false;
            }
            channel.sendMessage(message).queue();
            return true;
        }

        /**
         * Rich-embed send for an actual player chat message — recreates SDLink's own chat-message
         * look manually via JDA's EmbedBuilder, since posting to an arbitrary channel by ID
         * bypasses SDLink's own MessageType.CHAT builder entirely (that builder has no
         * channel-override API — see {@link #onPlayerChat} for the full rationale). Every field
         * is already fully resolved (placeholders substituted) by the caller — this method only
         * touches the JDA embed API itself, keeping the risky types confined here.
         */
        static boolean sendTemplatedEmbed(String channelId, String authorName, String authorIconUrl,
                                           String description, String colorHex, String footerText,
                                           String footerIconUrl, boolean showTimestamp) {
            var channel = BotController.INSTANCE.getJDA().getTextChannelById(channelId);
            if (channel == null) {
                NeoLog.warn(LOGGER, LogCategory.DISCORD, "SDLink: no text channel found with ID '{}' (bot may not be in that server, or the ID is wrong)", channelId);
                return false;
            }
            var builder = new com.hypherionmc.sdlink.shaded.dv8tion.jda.api.EmbedBuilder();
            if (authorName != null && !authorName.isBlank()) {
                builder.setAuthor(authorName, null, (authorIconUrl != null && !authorIconUrl.isBlank()) ? authorIconUrl : null);
            }
            if (description != null && !description.isBlank()) {
                builder.setDescription(description);
            }
            if (colorHex != null && !colorHex.isBlank()) {
                try {
                    builder.setColor(java.awt.Color.decode(colorHex));
                } catch (NumberFormatException e) {
                    NeoLog.debug(LOGGER, LogCategory.DISCORD, "Invalid discordEmbedTemplate.color '{}': {}", colorHex, e.getMessage());
                }
            }
            if (footerText != null && !footerText.isBlank()) {
                builder.setFooter(footerText, (footerIconUrl != null && !footerIconUrl.isBlank()) ? footerIconUrl : null);
            }
            if (showTimestamp) {
                builder.setTimestamp(java.time.Instant.now());
            }
            channel.sendMessageEmbeds(builder.build()).queue();
            return true;
        }
    }

    @Override
    public void shutdown() {
        NeoLog.info(LOGGER, LogCategory.DISCORD, "Simple Discord Link integration shut down.");
    }
}
