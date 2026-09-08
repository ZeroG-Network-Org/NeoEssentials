package com.zerog.neoessentials.integrations;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import net.minecraft.server.level.ServerPlayer;

/**
 * Resolves the "[Prefix] PlayerName[Suffix]" identity NeoEssentials' own Discord sends use for a
 * player, deliberately sourced from {@link PermissionAPI} — the exact same prefix/suffix chat
 * formatting already reads — instead of {@code ServerPlayer.getDisplayName()} (the vanilla
 * scoreboard-team-decorated name).
 *
 * <p>Why this distinction matters: a bridge mod's own NATIVE relay (SDLink's
 * {@code chat.playerMessages}/{@code playerJoin}/etc, independent of anything this mod's
 * adapters send) reads {@code getDisplayName()} directly for its own "who sent this" text, and
 * separately resolves its own rank prefix via its own LuckPerms/FTBRanks integration — so when
 * NeoEssentials ALSO writes a prefix onto the vanilla scoreboard team (for the nametag/tab-list,
 * see {@code TablistManager}), a bridge mod's native relay picks up BOTH copies, producing a
 * doubled prefix (confirmed by decompiling SDLink; there is no hook to change this from our
 * side — it's the bridge mod's own closed native behavior). NeoEssentials' own adapter-driven
 * sends (this class) are a completely separate path that never reads the scoreboard team at
 * all, so using it consistently here means our own messages can never double no matter what the
 * team prefix is set to, and — paired with a bridge mod's native relay being disabled or
 * suppressed (see each adapter's {@code detectNativeRelayConflicts()}) — Discord still shows a
 * single correct prefix even though the floating nametag/tab-list keep showing their own (see
 * DISCORD_INTEGRATION_PLAN.md for the full investigation — decoupling the floating nametag
 * itself from the vanilla team is not possible for a server-only mod).
 */
public final class DiscordIdentityFormatter {
    private DiscordIdentityFormatter() {}

    /**
     * @return "{@code [Prefix] PlayerName[Suffix]}" (colour codes/MiniMessage tags stripped,
     *         since Discord doesn't render either), trimmed. Never null.
     */
    public static String resolveNameWithRank(ServerPlayer player) {
        String prefix = stripFormatting(PermissionAPI.getPrefix(player.getUUID()));
        String suffix = stripFormatting(PermissionAPI.getSuffix(player.getUUID()));
        String name = player.getName().getString();

        StringBuilder sb = new StringBuilder();
        if (!prefix.isEmpty()) sb.append(prefix).append(' ');
        sb.append(name);
        if (!suffix.isEmpty()) sb.append(' ').append(suffix);
        return sb.toString().trim();
    }

    /** Strips {@code &}/{@code §} colour codes and MiniMessage-style {@code <tag>} markup. */
    private static String stripFormatting(String s) {
        if (s == null || s.isEmpty()) return "";
        return s
            .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
            .replaceAll("&[0-9a-fk-orA-FK-OR]", "")
            .replaceAll("<[^>]*>", "")
            .trim();
    }
}
