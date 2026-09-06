package com.zerog.neoessentials.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central home for the mod's support/help links (website, Discord, GitHub) — shared by the
 * always-on startup console banner and the "something actually went wrong" alert (console,
 * prominent form, plus a clickable in-game message queued into {@link AdminNotices} after a
 * real detected problem: a manager failing to initialize, or the permission system falling
 * back to emergency mode).
 */
public final class SupportLinks {
    private SupportLinks() {}

    public static final String SUPPORT_URL = "https://support.zerognetwork.co.za";
    public static final String DISCORD_URL = "https://discord.gg/dUGAQF2Mga";
    public static final String GITHUB_URL = "https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials";

    // Session-scoped (not persisted) — reset naturally on every server restart. Guards against
    // queuing a duplicate AdminNotices entry if multiple managers each report a problem.
    private static final AtomicBoolean QUEUED = new AtomicBoolean(false);

    /** Marks that something actually went wrong this session — queues the join alert once. */
    public static void markProblemDetected() {
        if (QUEUED.compareAndSet(false, true)) {
            AdminNotices.queue(Component.literal("§e§l  NEED HELP?"), chatMessage());
        }
    }

    /**
     * Queues the standing "found a bug or need help?" notice — unlike {@link
     * #markProblemDetected()}, this doesn't require anything to have actually gone wrong; it's
     * just where to go for support. Queued fresh every boot (no persisted id) via {@link
     * AdminNotices#queue(Component, Component...)}, same as the console banner — this is
     * standing reference information worth an admin seeing again on a later restart, not a
     * one-time nag like the legacy-data/config-split notices.
     */
    public static void queueGeneralHelpNotice() {
        AdminNotices.queue(Component.literal("§e§l  NEED HELP OR FOUND A BUG?"), generalHelpChatMessage());
    }

    /** Clickable in-game chat message for {@link #queueGeneralHelpNotice()} — same three links
     *  as {@link #chatMessage()}, worded for "just checking in" rather than "something broke". */
    private static Component generalHelpChatMessage() {
        MutableComponent msg = Component.literal("[NE] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Found a bug, or have a question? ")
                .withStyle(ChatFormatting.YELLOW));
        msg.append(link("[Support]", SUPPORT_URL));
        msg.append(Component.literal(" "));
        msg.append(link("[Discord]", DISCORD_URL));
        msg.append(Component.literal(" "));
        msg.append(link("[GitHub]", GITHUB_URL));
        return msg;
    }

    /**
     * Plain-text console line(s) — terminals don't support click events, so this is just the
     * bare URLs. {@code prominent} switches between a quiet one-liner (always printed once at
     * startup) and a bordered warning block (printed additionally when a real problem is
     * detected, right at the point of failure).
     */
    public static void logConsole(Logger logger, boolean prominent) {
        if (prominent) {
            logger.warn("╔══════════════════════════════════════════════════════════════╗");
            logger.warn("║  NeoEssentials ran into a problem — need help fixing it?      ║");
            logger.warn("╚══════════════════════════════════════════════════════════════╝");
            logger.warn("  Support: {}", SUPPORT_URL);
            logger.warn("  Discord: {}", DISCORD_URL);
            logger.warn("  GitHub:  {}", GITHUB_URL);
        } else {
            logger.info("Need help with NeoEssentials? Support: {} | Discord: {} | GitHub: {}",
                SUPPORT_URL, DISCORD_URL, GITHUB_URL);
        }
    }

    /** Clickable in-game chat message shown to the first admin joining after a detected problem. */
    public static Component chatMessage() {
        MutableComponent msg = Component.literal("[NE] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("NeoEssentials ran into a problem on startup — need help? ")
                .withStyle(ChatFormatting.YELLOW));
        msg.append(link("[Support]", SUPPORT_URL));
        msg.append(Component.literal(" "));
        msg.append(link("[Discord]", DISCORD_URL));
        msg.append(Component.literal(" "));
        msg.append(link("[GitHub]", GITHUB_URL));
        return msg;
    }

    private static Component link(String label, String url) {
        return Component.literal(label).withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withClickEvent(ClickEventCompat.create(ClickEvent.Action.OPEN_URL, url)));
    }
}
