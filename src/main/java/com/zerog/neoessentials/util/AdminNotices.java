package com.zerog.neoessentials.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central queue for one-time, admin-facing startup notices — config splitting available,
 * legacy data files no longer being read, a manager failing to initialize, etc.
 *
 * <p>Before this existed, each subsystem that wanted to tell an admin something on their first
 * join tracked its own "have I shown this yet" flag and sent its own independently-formatted,
 * independently-timed chat block (see {@code ConfigSplitter}'s old {@code shouldNotifyAdmins}
 * and {@code SupportLinks}' old {@code shouldAlertJoiningAdmin} — both now route through here
 * instead). This collects every notice raised during boot and delivers them all together, in one
 * consistent format, to the first admin who joins.
 *
 * <p>Notices queued with an ID via {@link #queue(String, String, String...)} are also tracked in
 * a small persisted marker file ({@code neoessentials/admin_notices_shown.json}) once actually
 * delivered, and never queued again after that — without this, a condition that's true every
 * single boot (config.json still not split, a legacy data file still present, ...) would nag
 * with the full chat block on every server restart forever, not just until an admin has actually
 * seen it once. The underlying state stays checkable anytime via the relevant status command
 * ({@code /neoe config status}, etc.) — this only stops the unprompted repeat delivery.
 */
public final class AdminNotices {
    private AdminNotices() {}

    private record Notice(String id, Component title, List<Component> body) {}

    private static final ConcurrentLinkedQueue<Notice> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean CONSUMED = new AtomicBoolean(false);

    private static final Path SHOWN_FILE = ResourceUtil.getDataPath("admin_notices_shown.json");
    private static final Gson GSON = new Gson();
    private static volatile Set<String> shownIds;

    private static synchronized Set<String> shownIds() {
        if (shownIds != null) return shownIds;
        Set<String> loaded = new HashSet<>();
        if (Files.exists(SHOWN_FILE)) {
            try (Reader reader = Files.newBufferedReader(SHOWN_FILE, StandardCharsets.UTF_8)) {
                List<String> ids = GSON.fromJson(reader, new TypeToken<List<String>>() {}.getType());
                if (ids != null) loaded.addAll(ids);
            } catch (IOException | com.google.gson.JsonParseException e) {
                // Corrupt/unreadable marker file — treat as "nothing shown yet" rather than
                // failing boot over what's ultimately just a nag-suppression cache.
            }
        }
        shownIds = loaded;
        return shownIds;
    }

    private static synchronized void markShown(String id) {
        Set<String> ids = shownIds();
        if (!ids.add(id)) return; // already recorded
        try {
            Files.createDirectories(SHOWN_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(SHOWN_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(ids, writer);
            }
        } catch (IOException ignored) {
            // Best-effort — worst case this notice nags again next boot.
        }
    }

    /**
     * Queue a notice built from localization keys — title first, then one line per body key.
     *
     * @param id stable identifier for this notice (e.g. {@code "config_split"}) — once actually
     *           delivered to an admin, this exact notice is never queued again on future boots.
     */
    public static void queue(String id, String titleKey, String... bodyKeys) {
        if (shownIds().contains(id)) return;
        List<Component> body = new ArrayList<>();
        for (String key : bodyKeys) body.add(MessageUtil.component(key));
        PENDING.add(new Notice(id, MessageUtil.component(titleKey), body));
    }

    /** Queue a notice built from already-constructed components (e.g. clickable links) — shown
     *  every boot the underlying condition holds (no persisted id to suppress repeats). */
    public static void queue(Component title, Component... body) {
        PENDING.add(new Notice(null, title, List.of(body)));
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /**
     * True at most once per server session, and only when there's something to show. Callers
     * MUST have already verified the joining player is an admin before calling this — it
     * consumes the one-shot "show it now" opportunity regardless of who's asking, so checking
     * permission afterward would silently burn it on a non-admin.
     */
    public static boolean consumeIfPending() {
        return hasPending() && CONSUMED.compareAndSet(false, true);
    }

    /**
     * Sends every queued notice to {@code player} after a short delay — long enough that the
     * message doesn't get lost while the client is still finishing its own join sequence — then
     * clears the queue. The delay runs on a background thread; sleeping on the server thread
     * would freeze the whole server for the duration.
     */
    public static void scheduleSendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Thread notifyThread = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            server.execute(() -> sendAllTo(player));
        }, "NeoEssentials-AdminNotify");
        notifyThread.setDaemon(true);
        notifyThread.start();
    }

    /**
     * Renders every pending notice as ONE combined block — a single outer border/header, each
     * notice's own title as a sub-heading underneath, rather than a full bordered block per
     * notice. Previously each notice got its own border+title+border, so an admin joining after
     * several conditions fired at once (config split available AND legacy data files AND a real
     * error, say) saw three separate bordered blocks stacked back to back, reading as three
     * unrelated alerts rather than one consolidated "here's what's up" summary.
     */
    private static void sendAllTo(ServerPlayer player) {
        List<Notice> notices = new ArrayList<>();
        Notice notice;
        while ((notice = PENDING.poll()) != null) notices.add(notice);
        if (notices.isEmpty()) return;

        player.sendSystemMessage(MessageUtil.component("commands.neoessentials.admin_notice.border"));
        player.sendSystemMessage(MessageUtil.component("commands.neoessentials.admin_notice.header"));
        player.sendSystemMessage(MessageUtil.component("commands.neoessentials.admin_notice.border"));

        for (Notice n : notices) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(n.title());
            if (n.id() != null) markShown(n.id());
            for (Component line : n.body()) {
                player.sendSystemMessage(line);
            }
        }
    }
}
