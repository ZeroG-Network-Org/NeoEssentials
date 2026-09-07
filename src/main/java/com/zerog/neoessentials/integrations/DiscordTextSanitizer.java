package com.zerog.neoessentials.integrations;

/**
 * Shared text sanitization for player-supplied content relayed to Discord by any
 * {@link ChatIntegrationAdapter} implementation (SDLink, Mc2Discord, DCIntegration).
 */
public final class DiscordTextSanitizer {
    private DiscordTextSanitizer() {}

    /**
     * Conservative cap for a chunk of player-supplied text (chat message, mute/AFK reason,
     * advancement name, private message body) going into a Discord message. Discord's actual
     * plain-message-content cap is 2000 characters, but this mod's own adapters often prepend a
     * player name or other fixed text to whatever gets truncated here (e.g.
     * {@code player.getName() + ": " + message}) — capping well under 2000 leaves headroom for
     * that prefix so the final composed string still can't exceed Discord's real limit.
     */
    public static final int DISCORD_TEXT_LIMIT = 1900;

    /**
     * Truncates {@code text} to at most {@code maxLength} characters, replacing the last
     * character with an ellipsis when it was cut short, so the API doesn't silently reject (or
     * throw on) a message a player made too long — e.g. a very long chat line, or a custom
     * advancement/mute-reason string. No-op if already within the limit.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 1) + "…";
    }

    /**
     * Neutralizes Discord mention syntax before player-supplied text is relayed — none of the
     * chat-bridge mods' own send APIs (or this mod's own direct JDA calls) restrict allowed
     * mention types on outgoing messages, so an unsanitized "@everyone"/"@here" or a pasted
     * role/user mention ({@code <@123>}/{@code <@&123>}) in a player's chat message or /msg
     * would actually ping the whole server/role if the bridge bot has that permission — a
     * griefing vector reachable by any player who can type in a bridged channel, not just
     * staff. Inserting a zero-width space breaks Discord's mention parser while leaving the
     * text visually identical to a human reader.
     */
    public static String sanitizeMentions(String text) {
        if (text == null || text.isEmpty()) return text;
        return text
            .replace("@everyone", "@​everyone")
            .replace("@here", "@​here")
            .replaceAll("<(@[!&]?\\d+)>", "<​$1>");
    }
}
