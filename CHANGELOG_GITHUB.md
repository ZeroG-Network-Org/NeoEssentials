# Changelog — NeoEssentials

> **This is the UNIVERSAL changelog** — one shared history covering all three dev
> branches (`1.21.x`, `26.1.x`, `26.2.x`), since GitHub releases bundle every branch's
> jar into a single shared release. Write entries here ONCE, describing the change
> itself — not duplicated per branch, and not restated in branch-specific terms unless
> a fix genuinely only applies to one version. This file should read identically across
> all three branches at any given point in time.
>
> `CHANGELOG_CURSEFORGE.md`/`CHANGELOG_MODRINTH.md` stay branch-specific (those platforms
> require separate uploads per Minecraft version), so keep writing those per-branch.

All notable changes to NeoEssentials are documented here, starting from
**v1.0.6** — earlier history (v1.0.5.x and before) is not carried over.  
Format: `[version+build] — date`  
Compatibility: **Minecraft 1.21.1 – 1.21.11 (`1.21.x`) · Minecraft 26.1–26.1.2 (`26.1.x`) · Minecraft 26.2 (`26.2.x`)**

> The build counter was reset alongside the v1.0.6 bump, so build numbers here start
> back at 0/1, and the shared CI build number was reset to match — no more offset
> between branches.

---

## [1.0.6] — 2026-08-27

### Added
- **2026-09-09** — Build 69 ([`3d3f059a`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/3d3f059ad1bdf769b64a0938384afe19627dcfda)) — **NPC shops can now use any registered entity type, with an AI on/off toggle** — `/npcshop create` still defaults to the classic ArmorStand, but `/npcshop entitytype <id> <type> <ai>` lets an admin switch an existing shop to any already-registered entity type (vanilla or from another installed mod, e.g. `minecraft:villager`), converting it in place. AI-enabled Mobs keep their normal goals/animations but are leashed back to their spawn point so they can't wander off; AI-disabled Mobs are frozen in place exactly like the old ArmorStand behavior. Still never registers a custom EntityType itself, so non-NeoEssentials clients can't be disconnected by an unknown registry key.
- **2026-09-08** — Build 68 ([`9e8ec571`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/9e8ec57143651437df86479a9b812dbfa6b05b8d)) — **Fixed doubled rank prefixes in Discord** — a bridge mod's own native relay (e.g. SDLink's `chat.playerMessages`) reads `Player.getDisplayName()` (which carries whatever prefix NeoEssentials wrote onto the vanilla scoreboard team for the nametag/tab-list) and separately re-resolves the same rank prefix itself, stacking both. NeoEssentials' own Discord messages (chat, join, leave, mute, AFK, advancement, private messages — across SDLink/Mc2Discord/DCIntegration) now resolve the prefix independently, straight from the permission system, so they can never double regardless of what the nametag/tab-list shows. Combined with disabling a bridge mod's own native relay (see the existing conflict-detection warnings), Discord now shows a single correct prefix with zero compromise to in-game nametag/tab-list display. Verified live against a real connected Discord server.
- **2026-09-07** — Build 67 ([`9ef44cb0`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/9ef44cb00aa1cc860dafed32712737c19917ea89)) — **Wider Discord event coverage for Mc2Discord and DCIntegration** — Mc2Discord now relays AFK status changes and private messages (previously silent no-ops), plus its chat/join/leave/advancement avatar now reads `discordEmbedTemplate.authorIconUrl` instead of a hardcoded URL. DCIntegration now relays advancement/mute/AFK events when routed to an explicit Discord channel (previously silent no-ops) — advancement specifically was confirmed via bytecode to already post natively for linked players, so this is additive-only, exactly like DCIntegration's existing join/leave override support.
- **2026-09-07** — Build 66 ([`4282e36a`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/4282e36a5b4860ec472387129788c688e6ee7cdb)) — SDLink/Mc2Discord's native-relay conflict warnings (previously console-only) are now also exposed via `GET /api/discord/status`'s per-adapter `nativeRelayWarnings` array, so the web dashboard's Discord status panel can surface them to an admin configuring integration through it.
- **2026-09-07** — Build 65 ([`c7cc2bd2`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/c7cc2bd22e025a3408004d565efa7d952ac8e33c)) — **Rich Discord embeds for join/leave/mute/AFK/advancement (SDLink)** — these events, when routed to an explicit `discordEventChannels.<event>.channelId`, previously sent a bare text line even though chat messages routed the same way already get a full styled embed via `discordEmbedTemplate`. Each event now has its own nested `discordEmbedTemplate.<event>` override with sensible built-in defaults (its own color, `{player}`/`{message}` placeholders, optional timestamp) — set an event's `enabled` to `false` to keep the old plain-text line for just that event.
- **2026-09-07** — Build 64 ([`7f95b97d`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/7f95b97d710e709438b93e65913d11a451e57c8d)) — SDLink's native-relay conflict detection now parses `simple-discord-link.toml` with a real TOML parser instead of a best-effort text scan, so a comment or unusual formatting in that file can no longer silently defeat detection. Also: any player-suppliable text sent to Discord (chat messages, private messages, mute/AFK reasons, advancement names) is now truncated before sending across all three bridge-mod adapters, so a message over Discord's ~2000-character cap can no longer fail silently or throw.
- **2026-09-07** — Build 63 ([`4e920427`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/4e9204274fc358ea72ede6a3908e1f46fe5a180b)) — **Discord role → permission group sync** — a new `discordrolesync.json` maps Discord role IDs to NeoEssentials permission groups. Implemented via DCIntegration for now (its JDA client is directly reachable for a member's role list; SDLink's role lookup has no public API for this, Mc2Discord isn't wired up yet). A linked player holding a mapped role is set to the matching group (highest-priority group wins if they hold more than one); re-checked periodically and immediately on join. Only ever grants/upgrades a group, never demotes — and only applies when NeoEssentials' own internal permission manager is active, not an external adapter like LuckPerms/FTB Ranks.
- **2026-09-07** — Build 62 ([`d1ccffca`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/d1ccffca71a30a06d1aa95c4c14b953d387a4520)) — **Mc2Discord native relay conflict detection** — NeoEssentials now reads `config/mc2discord.toml` on startup and checks whether Mc2Discord's own channel subscriptions already relay chat/`player_connect`/`player_disconnect`/`player_advancement` events natively. When they do, NeoEssentials' own default-route send for that event type is suppressed to avoid posting it twice. Explicit per-channel Discord overrides (`discordEventChannels`/`chat.channels.*.discord.channelId`) always still send, since those are assumed to target a channel Mc2Discord's native relay doesn't touch. A warning is logged naming which subscription keyword triggered the suppression, so an admin can remove it from `mc2discord.toml` if they'd rather NeoEssentials format the message instead.
- **2026-09-07** — Build 61 ([`8ccf84d9`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/8ccf84d91f95ce87d97deb1414f74bfed7e5f879)) — **Generic Discord webhook relay** — a new `webhookUrl` field alongside `channelId` in every `chat.channels.*.discord` block and `discordEventChannels.*` entry lets NeoEssentials post chat/join/leave/mute/AFK/advancement/private-message events straight to a Discord webhook URL over plain HTTPS, with **no bridge mod (SDLink/Mc2Discord/DCIntegration) installed at all** — no bot token, no gateway connection. Chat/advancement messages impersonate the sending player via Discord's per-message username/avatar override. A webhook added or changed via `/neoe reload` takes effect immediately, unlike installing a new bridge mod.
- **2026-09-07** — Build 60 ([`e7a908bc`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/e7a908bc77afcd69e0f9aeb2df46336b6b26a6ff)) — **Per-event Discord channel routing** for join/leave/mute/AFK/advancement/private-message events, via a new `discordEventChannels` config section — previously only chat messages could be routed to a specific Discord channel (`chat.channels.<name>.discord.channelId`); every other event type was locked to whichever single channel each bridge mod's own native config pointed at. Lets you, for example, send joins/leaves to a `#server-log` channel while keeping mutes in a private `#mod-log` channel. Supported by SDLink and Mc2Discord for all six event types; DCIntegration additionally gained join/leave routing (previously unavailable there at all, to avoid duplicating its native mixin-driven relay — the new routing is additive-only, so it doesn't touch that native relay).
- **Sidebar scoreboard system** — `/scoreboard toggle|reload|enable|disable|info|preview|
  board list|set title|set line|player ...|group ...`. Config-driven named boards with
  priority-ordered conditions (`perm:`, `world:`, placeholder comparisons), per-line
  animation, per-group/per-player title/line overrides, a persisted per-player toggle, and
  a dashboard endpoint (`/api/scoreboard`) for reading/editing boards. Renders entirely via
  direct per-connection packets (not the shared server scoreboard), so different players can
  see different boards or values at the same time with no cross-player interference.
- **General leaderboard system** — `/leaderboard` (alias `/lb`) generalizes `/baltop`
  (still works unchanged) into a registry any stat can plug into. Ships with four boards:
  `money`, `kills`, `mob_kills`, `playtime` — the last three read Minecraft's own per-player
  stat tracking directly (online players live, offline players from their stats file), no
  new event tracking needed. Exposes `{leaderboard_<board>:<rank>:name|value}` placeholders
  (usable in the new scoreboard's line config) and a read-only dashboard endpoint
  (`/api/leaderboard`).
- **Leaderboard boards are now config-driven** (`leaderboard.json`), not hardcoded — admins
  can register any vanilla-tracked per-player stat (blocks mined by type, mobs killed by
  type, distance traveled, deaths, ...) as a board just by adding an entry, using the same
  criteria-string format vanilla's own `/scoreboard objectives add` accepts. Also adds
  "custom" boards — point totals nothing in Minecraft tracks, settable via
  `/leaderboard admin set|add|reset|create|delete`, the dashboard, or another mod (see
  `LeaderboardAPI`, a one-line integration surface for external mods to register their own
  boards, mirroring `PlaceholderAPI`).
- **`/leaderboard hologram create <board> <id> [lines]`** — generates a hologram with ranked
  leaderboard lines in one command instead of typing each line by hand. Holograms already
  resolve `{placeholder}` tokens live on a refresh timer, so the generated
  `{leaderboard_<board>:<rank>:name|value}` lines keep updating on their own — no new
  rendering mechanism, just a convenience generator on top of the existing hologram system.
- **Leaderboards can now rank things other than players.** Every board previously assumed its
  entries were real Minecraft players (a UUID resolved to a name via a Mojang profile
  lookup) — there was no way to build a "top shops by sales" board at all. Generalized the
  entry/provider contract (new `NamedStatProvider` interface) so a board can be keyed by any
  stable id with its own display name, and added a `shop_sales` board built on it, ranking
  sign/chest and NPC shops by total revenue (a field that didn't exist before — shops only
  tracked a sale count, not money moved, and NPC shops tracked nothing at all).
- **Per-board refresh interval** — `leaderboard.json` boards can now set `refreshInterval`
  (seconds a cached ranking is served before rebuilding), instead of every board sharing one
  hardcoded 60s window. Applies to boards registered by external mods via `LeaderboardAPI`
  too.
- **Leaderboard styling** — three opt-in additions, all backward-compatible (a board with none
  of these set looks exactly as before): automatic per-rank medal/color placeholders
  (`{leaderboard_<board>:<rank>:medal|rankcolor}`, usable anywhere placeholders resolve —
  holograms, scoreboard, tablist); per-board `entryFormat`/`headerFormat` line templates for
  `/leaderboard`'s own chat output (same `&`/hex/gradient support as tablist/scoreboard
  lines); and a new paginated chest-GUI viewer (`/leaderboard <board> gui`) with real player
  heads for player entries and a configurable icon for non-player entries.
- **`/tpr`/`/rtp` can now open a biome-select GUI instead of teleporting instantly** — opt-in
  via `teleportation.randomTeleportSettings.mode: "gui"` (default `"command"` is unchanged
  today's behavior). The GUI lists every biome the current dimension can generate — read live
  from the dimension's own generator, so modded biomes show up automatically with zero
  config — plus a "Random — Any Biome" button. Picking a biome searches for it using the same
  engine `/locate biome` uses, then finds a safe spot the same way plain RTP does; if it can't
  find that biome within the configured search radius or the world border, the player gets a
  clear error instead of a bad teleport. Biome icons default to that biome's own
  sapling/propagule/fungus (or a dedicated block for biomes with no tree), and admins can pin
  specific biomes to fixed GUI slots and/or override their icon via a new
  `biomeMenuItems` config list — everything not pinned still auto-fills the remaining slots,
  including biomes this mod doesn't specifically recognize.
- **Votifier vote listener** — a TCP listener on its own port, compatible with both Votifier
  protocol versions (V1/RSA and V2/NuVotifier-compatible, auto-detected per connection so vote
  sites using either just work). Per-vote-site reward config (console commands, crate keys),
  offline votes are queued and delivered on next login, a vote-party threshold bonus, and
  `/vote`/`/votes`/`/togglevotebroadcast`/`/voteparty` player commands plus admin reload/
  testvote/genkeys. Wire protocol verified byte-for-byte against a real reference
  implementation and round-trip tested live (RSA-encrypted V1 vote and HMAC-signed V2 vote
  both received, decrypted/verified, and rewarded correctly) rather than only unit-tested.
- **Crates system** — weighted reward-pool crates opened with a key (a virtual per-player
  balance, not just an item, so a duplicated key item alone can never grant an extra open),
  either virtually (`/crate open`) or by right-clicking a physical crate block placed in the
  world. Three opening animations (a CS:GO-style spinning-roulette reveal, a lighter sequential
  flicker, or instant), full reward items (enchantments/custom names/etc., same serialization
  `kits.json` already uses), rare-reward server broadcasts, a no-cost `/crate preview` odds
  viewer, and admin commands to define crates/rewards/key items/animation/physical blocks
  entirely in-game. Votifier can grant crate keys as a per-site vote reward — the common "vote
  crate" server setup — but both systems work fully independently too.
- **`/crate admin setblock` now auto-creates a floating hologram above the crate block**
  (display name + a "Right-click to open!" hint by default). It's a completely ordinary
  hologram — same registry, same `/hologram` commands — just given a predictable id based on
  the crate's own name (`crate_<crateId>`, e.g. `crate_common`, disambiguated with a numeric
  suffix if that crate has more than one physical block), so it's actually findable in
  `/hologram` tab-completion and immediately customizable with any existing `/hologram`
  subcommand (text, scale, spin, background color, hover animation, etc.) instead of needing
  separate crate-specific appearance config. The block ↔ hologram link is tracked internally, so
  it stays correct even after moving the hologram with `/hologram moveto`/`movehere`.
  `removeblock` deletes it again; `reload`/`delete` sweep away any hologram left over a block
  whose crate no longer exists.
- **Left-clicking a physical crate block now opens the no-cost reward-odds preview** (same as
  `/crate preview`), matching the right-click-to-act/left-click-to-look pattern shop signs
  already use. Shift+left-click still falls through as a normal break attempt.
- **`/crate key giveitem <player> <crate> <amount>`** — gives real, physical crate key items.
  Holding a valid one is enough on its own to open that crate (right-click a crate block, or
  hold it when using `/crate open`) with no virtual balance required, so keys can now genuinely
  be given away, dropped, or traded between players.
- Tablist `header`/`footer` frames can now be written as their own JSON array of lines (joined
  with `\n` automatically) instead of hand-escaping `\n` inside one long string — e.g.
  `["Line 1", "Line 2"]` as one element of the outer frames array. A flat array of plain strings
  keeps meaning what it always has (multiple single-line frames); the existing `{newline}`
  placeholder still works too.
- Crate key item names now support `{animation:NAME}`/`<gradient:...>`/`<rainbow>`, not just
  `&`-codes — matching what already worked in the crate's hologram text. A held item can't be
  live-repainted the way a hologram/tablist/scoreboard can, so an animated name is a snapshot of
  whichever frame was current the moment that specific key item was minted, not something that
  visibly animates while held.
- **Crates now support separate display names for chat and the key item** —
  `chatDisplayName`/`crateKeyDisplayName` (`crates.json`), each falling back to `displayName` if
  not set, plus `/crate admin setchatname`/`setkeyname` to edit them in-game. Previously every
  chat/command-feedback message (no keys, reward added, etc.) reused the same `displayName` as
  the hologram — fine for a plain name, but an animated `{animation:NAME}` `displayName` meant
  every chat message resolved a different random snapshot frame, reading as inconsistent flicker
  rather than animation with no way to opt chat out of it specifically.
- `/crate keys [player]` now lists every registered crate on its own line, including any crate
  the player has zero keys for — previously it comma-joined only the crates they actually held
  keys for onto one line, so a crate you had none of didn't show up at all, making it unclear
  whether it existed. Also now uses each crate's chat display name for consistency.
- Servers using an external permission adapter with no prefix/suffix concept (FTB Ranks) had
  the console permanently flooded with `PermissionAPI.getPrefix/getSuffix: PermissionManager is
  null` WARN lines — one pair per player on every tablist/placeholder refresh, for the entire
  life of the server. That's the expected, documented state when an external adapter is active
  (the internal permission manager is deliberately never loaded in that mode), not a failure —
  the warning now only fires when there's genuinely no permission system backing the call at all.
- Holograms' placeholder-refresh and animation/spin/hover tick rates were hardcoded (1s / 50ms)
  — now configurable via `hologram.refreshInterval`/`animationInterval` in `config.json` (in
  server ticks, same convention as tablist/scoreboard's `refreshInterval`), defaulting to `20`/
  `1` to match the previous hardcoded behavior exactly. Applies with `/neoe reload`.
- New per-board `refreshMultiplier` (`scoreboard.json`, default `1`) lets one scoreboard board
  cycle its own title/line animation frames slower than every other board (e.g. `3` = a third as
  often) without touching the global `refreshInterval` that still governs everything else — the
  same per-item override precedent `animations.json`'s per-animation `frameDuration` and
  `leaderboard.json`'s per-board `refreshInterval` already set. Holograms already had an
  equivalent per-hologram `refreshInterval`/`refreshinterval` command and per-line
  `animFrameIntervalTicks` (`/hologram addframes`) — nothing new needed there.
- Default `refreshInterval` for tablist/scoreboard/hologram tightened from 20 ticks (1/sec) to 10
  ticks (2/sec) — noticeably smoother out of the box, still cheap. Only affects fresh installs;
  an existing config keeps whatever value it already has, same as any other config default.

### Fixed
- **2026-09-06** — ([`e0b60fb9`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/e0b60fb95f3b916a046b3925cf2d519fd35272ed), docs only, no build bump) — Documented `tablist.json`'s existing `nametagSettings.enabled: false` as the fix for Discord chat-bridge mods (e.g. SimpleDiscordLink reading a synced FTB Ranks/LuckPerms rank prefix) showing the rank prefix twice — vanilla folds the scoreboard-team prefix into `Player.getDisplayName()`, which some bridges then re-decorate with the same prefix a second time. In-game chat's own prefix (from `chat.json`) is unaffected either way; no code change was needed, this setting already existed.
- **2026-09-06** — Build 59 ([`ffaa874f`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/ffaa874f416187d0ec7b9845bbf42d80d9fa3f09)) — Fixed a player's name showing as literal `HNAME<name>/HNAME` in chat instead of a clean clickable name when a chat-format template wrapped `{neoessentials_username}` in `<gradient:...>`/`<rainbow>` (the common way to give an FTB Ranks/LuckPerms rank a colored name) — the internal clickable-name marker was getting shredded character-by-character by the gradient/rainbow colorer, which didn't know to skip over it as one atomic unit the way it already did for `&`-format codes. Fixes #70.
- **The startup admin-notice chat block (bug-report links, config split available, etc.) is now
  gated by a dedicated `neoessentials.admin.notice` permission node** instead of a hardcoded
  OP-level-4/wildcard check — a group can be granted (or denied) visibility into these without
  needing full admin-wildcard access. All pending notices for a boot also now render as one
  combined block instead of a separate bordered section per notice. Also removed the "LEGACY
  DATA FILE(S) NO LONGER READ" notice entirely.
- **FTB Ranks servers can now use `"group:<rank>"` chat-format keys and the
  `{ftbranks_prefix}`/`{ftbranks_suffix}`/`{ftbranks_rank}`/`{ftbranks_group}` placeholders** —
  the FTB Ranks adapter never actually implemented a primary-group/prefix/suffix lookup (always
  returned "no opinion"), so these silently never matched on FTB Ranks despite matching fine on
  LuckPerms. Group is now resolved from the player's highest-power rank; prefix/suffix are read
  from that rank's `ftbranks.name_format` permission value and split on the `{name}` token —
  reusing whatever the server already configured there, no new setting needed. Also fixed
  `"group:<name>"` keys being case-sensitive against the resolved group name (which is always
  lowercased) — a key written with any uppercase, e.g. `"group:SeasonedExplorer"`, could never
  match on any permission backend, not just FTB Ranks.
- `/permissions group <group> setprefix|setsuffix` no longer surfaces a raw, unhelpful
  "unexpected error" when the internal permission manager isn't initialized (e.g. an
  external permissions plugin like LuckPerms is active) — it now explains why instead of
  crashing into a generic error, matching the error handling every other group-editing
  subcommand already had.
- `{luckperms_group}`/`{luckperms_primary_group}`/`{ftbranks_rank}`/`{ftbranks_group}`
  placeholders no longer silently resolve to an empty string when an external permissions
  plugin (LuckPerms/FTB Ranks) is actually active — they went straight to the internal-only
  permission manager instead of checking the active external adapter first, exactly the
  scenario these placeholders exist for. `{luckperms_prefix}`/`{suffix}`/`{displayname}` and
  the `{ftbranks_}` equivalents were unaffected.
- **Nicknamed players (`/nick`) now keep their permission-group prefix/suffix in the tab
  list.** Vanilla's tab-list rendering only wraps a row with the scoreboard team's prefix/
  suffix when there's no display-name override — a nickname was sent as a bare display-name
  override, so it was shown completely verbatim with the prefix/suffix silently dropped the
  instant a nickname was set (reported as "the nickname overrides it and only shows the
  nickname in tab", and appeared group-dependent purely because whichever test player
  happened to be nicknamed lost their prefix). The override now always contains prefix +
  nickname + suffix, and stays in sync automatically if the prefix/suffix changes later
  (promotion, AFK toggle, config reload) without needing to re-run `/nick`.
- **`PermissionAPI.getPrefix()`/`getSuffix()` now fall back to the internal permission system
  when the active external adapter (LuckPerms/FTB Ranks) has no opinion**, matching the
  fall-through contract `getGroupWeight()`/`getPrimaryGroup()` already correctly had. Two
  concrete effects: FTB Ranks servers previously got no prefix/suffix through this mod at
  all, ever (`FtbRanksAdapter` never implemented them, and the old code refused to fall back
  once any external adapter was active) — now they correctly fall back to NeoEssentials' own
  `permissions.json` prefix/suffix. And on LuckPerms, a group with no `prefix`/`suffix` meta
  node set now also falls back to an internal prefix for that same group name if one's
  configured, instead of silently showing nothing — this was very likely the cause of an
  earlier "one group shows a prefix, another doesn't" report.
- **Five more places had the identical "goes straight to the internal-only permission manager"
  bug** as the fixes above, found by auditing every remaining call site: the public
  `NeoEssentialsAPI.getPermissionsService().getGroup()` contract, the `{group}`/
  `{neoessentials_group}` placeholder (the most widely-used group token — chat, MOTD,
  holograms), `TablistLayout`'s BTLP-style per-group column bucketing, chat badge group
  gating, and player tags — all silently returned `""`/`"default"` for every player whenever
  LuckPerms/FTB Ranks was active. All five now correctly check the active external adapter
  first. Also hardened Discord-role permission sync to fail with a clear message instead of a
  generic error when an external adapter is active (it writes to the internal group directly,
  which has no external-adapter equivalent to redirect to).
- **Config-version upgrades (the mechanism that merges new default keys/boards into an
  existing config file on update) were silently failing on Windows.** The merge held a file
  reader open on the config file while trying to rename a new version over it — Windows
  blocks that rename while the file's still open for reading (POSIX allows it, which is why
  this never surfaced before). Every config using version-tracked upgrades was affected, not
  just leaderboards.
- **A new default board (like the `shop_sales` board above) never actually reached an
  existing install even after that fix** — the generic config-merge logic only adds a key
  that's missing entirely; it doesn't know how to merge a new entry into a JSON array that
  already exists on disk (`leaderboard.json`'s `boards` list). Added a dedicated merge step
  that appends new default boards by id without touching or duplicating any board an admin
  already has, including ones they've customized.
- **`/tpr`/`/rtp` could lag or watchdog-crash a server, especially one without pre-generated
  terrain.** Reported as "every time I rtp it lags out the whole server, and then crash" — root
  cause was excessive main-thread-blocking chunk generation per teleport: the safety-preload
  step force-generated a 3×3 grid (9 chunks) around every destination unconditionally, even
  for RTP, which already verifies its own exact landing spot and never reads the 8 neighbour
  chunks — that's now skipped for RTP specifically. Refilling the background location cache
  after a successful teleport also used to fire the entire gap up to `cacheThreshold` (up to
  10 forced generations) as one burst immediately after teleporting, stacking a second lag
  spike right on top of the first — now capped via a new `prewarmBatchSize` config (default 2),
  spreading the refill across several `/tpr` uses instead of bursting it all at once. Also
  lowered `defaultMaxRange`/`findAttempts`/`cacheThreshold` shipped defaults (10000→5000,
  10→6, 10→5) for new installs, and `/tpr` now actually announces its warmup delay
  ("Teleporting in 3 second(s) — move to cancel.") — the move-to-cancel behavior already
  existed (same warmup every teleport command shares) but was never visible, reported as
  "no way of escaping it."
- **Splitting your config (`/neoe config split`) silently dropped web dashboard settings and
  your storage backend choice entirely** — reported as "settings reverted to default, can't
  find them in any config file" after splitting. Root cause: `webDashboard` and `storage` were
  never registered in `ConfigSplitter`'s section list at all, so neither was ever written to
  any split file — every dashboard/storage getter just silently fell back to its hard-coded
  default forever after (dashboard port/auth/UI settings back to defaults; storage backend
  reverting to `"json"` regardless of a configured SQLite/MySQL setup). `webDashboard` now gets
  its own dedicated `dashboard.json`; `storage` now lives in `main.json`. Existing split
  installs get both auto-created/repaired on next startup (with default values, since neither
  ever existed in a split file to recover real values from) — your original customized values
  are still recoverable from `config.json.backup` (created automatically by every
  `/neoe config split`), see the Split Config wiki page for the exact recovery steps.
- **Right-clicking a physical crate block ran the whole open flow twice per click** —
  `PlayerInteractEvent.RightClickBlock` fires once per hand for a single right-click, and the
  handler wasn't filtering to the main hand, so every interaction was processed twice: most
  visibly a duplicated "you don't have a key" error message, but it could also consume two keys
  and open two reward GUIs from one click. Now guarded the same way `ShopInteractHandler`
  already guards against the identical issue.
- **A crate with no rewards configured yet silently ate a key and reported the misleading "no
  keys" error.** Reported as "I gave myself a key but it still asks me to have a key" right
  after creating a new crate — `/crate admin create` makes an empty crate (no rewards until
  `/crate admin addreward` is used), and the open flow decremented the key balance *before*
  picking a reward, so the empty pool's `null` result got reported as if the key had never
  existed at all. Reordered to resolve the reward first — a key is now never spent unless a
  reward actually exists — and added a distinct "no rewards configured yet" message so this no
  longer looks identical to actually being out of keys.
- **Tab-completion was silently missing on ~25 command arguments across 16 files** — anywhere
  an argument required typing an exact name from a known set (crate id, hologram id/world/
  billboard mode/spin axis, home name, scoreboard/tablist group or board, Vault economy
  provider, API key id/role, leaderboard admin player, item id, MOTD profile, mail/nick/note
  target player, shop price type, NPC shop id, sellable item) with no `.suggests(...)`, so
  pressing Tab did nothing and the value had to be typed from memory. Full audit found and fixed
  every instance of the class of bug just found in `/crate admin setanimation`. Also removed a
  dead duplicate `/condense` command registration that could never have taken effect anyway
  (Brigadier merges same-named nodes but keeps the first-registered suggestions provider).
- **A crate's `&`-coded display name (e.g. `"&7Common Crate"`) showed up as the literal text
  `&7Common Crate` in chat, GUI titles, and the key item's name** instead of being colored.
  Root cause: `MessageUtil.success()`/`error()`/`warning()`/`info()`/`component()` — used for
  essentially every command reply in the mod — built their message via a plain
  `Component.literal(...)`, which never translates `&`-codes; any config-supplied display name
  with its own color code (crate names being the first real-world case to actually hit it) just
  showed the raw code. All five now route through the same `&`-code parser
  (`ChatComponentUtil.parseColorCodes`) chat/tablist/hologram text already uses, so this is fixed
  everywhere those methods are called, not just crates — plus three crate-specific spots that
  built a raw `Component.literal(...)` directly instead (the key item's name, and the two crate
  GUI titles) got the same fix.
- **`{server_name}` no longer means "MOTD," and there's a new `{server_motd}` for when you
  actually want that.** `{server_name}` (scoreboard, tablist, and the
  `{neoessentials_server_name}` placeholder used everywhere else) used to hardcode vanilla
  `server.getMotd()` — a real `server.properties` MOTD is typically multi-line and heavily
  §-formatted for the server list, which breaks when dropped into a single scoreboard/tablist
  row. `{server_name}` is now a plain, independently-configurable string (`general.serverName`
  in `config.json`/`main.json`) with no MOTD involvement at all. A new `{server_motd}`
  placeholder covers the old behavior for anyone who actually wants the configured MOTD
  somewhere — NeoEssentials' own `/motd` profile if one is set, otherwise the vanilla
  `server.properties` value.
- **`/neoe reload` silently did nothing for the scoreboard system.** Reported as "changed
  scoreboard.json, `/neoe reload` did nothing." `ConfigManager.loadAll()` only clears the JSON
  cache — it never told `ScoreboardManager`'s own in-memory board list to actually re-parse
  `scoreboard.json`, so edited boards/titles/lines stayed stale until an explicit
  `/scoreboard reload` or a full restart. Tablist had this exact same gap once (already fixed,
  visible in the code as a "was missing" comment) but the identical fix was never applied to
  scoreboard — it now reloads and pushes to all online players the same way.
- **An inventory-utility client mod's "pull items out of the current chest/storage GUI" button
  could steal items straight out of `/crate preview` and the crate-opening reveal GUI**,
  duplicating them from nothing — those slots were only ever meant to be look-at-only reward
  icons. The existing protections (`Slot#mayPickup` returning false, an overridden
  `quickMoveStack()`, a no-op `clicked()`) only cover the normal click-packet path, and an initial
  fix that also refused `Container#removeItem`/`removeItemNoUpdate` still wasn't enough — the mod
  was actually reading a slot's item and then blanking it via `Container#setItem`, the same path
  `Slot#set()` uses, which neither of those covers. Both crate GUIs' backing container is now a
  `ReadOnlyContainer` whose `removeItem`/`removeItemNoUpdate`/`setItem` all refuse from the
  outside — the owning menu redraws through a separate `forceSetItem` the container exposes only
  to itself — closing every mutation path a container has, regardless of which mod or mechanism
  is doing the pulling. Confirmed (via Quark's own item-pull feature) that even this wasn't the
  full story: it doesn't call any `Container` mutation method at all, it just reads a slot's item
  and copies it straight into the player's real inventory — a path no container-level defense can
  close without also breaking the ability to render the preview in the first place. Every
  displayed reward stack in both crate GUIs is now tagged with a hidden marker, and both GUIs
  sweep the viewer's real inventory for marked stacks on a short timer while open plus once more
  on close, deleting any that made it out — mod-agnostic by design, since it doesn't matter which
  mod or mechanism did the copying. The close/prev/next buttons and filler panes in
  `/crate preview` (built separately from the reward icons) were missed by the first pass of
  this marking and were just as stealable — now marked too.
- Physical crate key items were effectively non-functional — `CrateManager.buildKeyItem` had no
  command that ever called it, so there was no way to actually get one into a player's
  inventory, and even a key item obtained some other way still silently required virtual
  balance to redeem — a traded or gifted key wouldn't have worked for whoever received it. Both
  fixed: see `/crate key giveitem` above.
- **A malformed config file (e.g. a stray/unterminated brace from a manual edit) could crash the
  entire server**, repeatedly, on the very next tick — `ConfigManager.getConfig()` only caught
  `IOException` around the JSON parse, but Gson throws `JsonSyntaxException` (a
  `JsonParseException`, not an `IOException`) for invalid JSON, which went uncaught all the way
  up through whatever caller happened to read that config. For a config read every tick (the
  scoreboard module-enabled check), that meant an unrecoverable crash loop. It now falls back to
  an empty config for whatever that file drives and logs a clear, actionable error instead of
  bringing the server down.
- **A nicknamed player still showed their real IGN in tab to anyone who joined the server after
  the nickname was set** (or after their last prefix/suffix change) — the tab-list override
  packet was only ever broadcast to whoever was already connected at that moment, and a
  player's own `onPlayerJoin` handler only restored *their own* nickname, never re-sent everyone
  *else's* already-active overrides to them. Every join now re-broadcasts every currently
  nicknamed player's override (a harmless no-op for clients already in sync), the same sweep
  already used on server start.
- **`/neoe reload` (and `/scoreboard reload`) could kick every player currently seeing the
  sidebar scoreboard with a fatal "Network Protocol Error"** — reload cleared the server-side
  tracking of which clients already had the "ne_sidebar" objective registered, along with the
  content caches, so the very next update thought those clients needed the objective added for
  the first time and sent a duplicate `METHOD_ADD` — vanilla's client throws on that and
  disconnects. The tracking now survives reload; only the content caches are cleared, so a
  reload correctly refreshes what's displayed without re-adding anything.
- **The permission system had no way to recover from a failed boot initialization without a
  full server restart.** If `PermissionSystem.initialize()` failed partway through at boot (e.g.
  a config that couldn't be parsed yet), the internal manager stayed permanently `null` with no
  external adapter either — every prefix/suffix/permission check kept silently failing all
  session (logged repeatedly as `PermissionManager is null`), and `/neoe reload`'s call into
  `PermissionAPI.reload()` could never fix it, since that only re-reads data into an
  *already-initialized* manager. `/neoe reload` now falls back to a full re-initialization
  (re-running the same detection `initialize()` does at boot) specifically when there's nothing
  to reload into yet.
- The "config splitting available" and "legacy data file(s) no longer read" admin startup
  notices were queued unconditionally on every single boot for as long as the underlying
  condition held — for a server that hasn't acted on either yet, that's the same multi-line chat
  block on every restart forever. Each is now persisted once actually shown to an admin
  (`neoessentials/admin_notices_shown.json`) and never queued again after that; the underlying
  state is still checkable anytime via the relevant status command.
- **Closing the crate GUI immediately after using a pull-mod extraction button could still win
  the race and keep the item.** The ghost-item sweep from the previous fix stops the instant the
  GUI closes, and the single sweep that ran at close time assumed the extraction was already
  fully processed server-side by then — pressing E/Esc right after clicking a pull button could
  land the copy a tick or two *after* that close-triggered sweep, with no menu left open for the
  periodic sweep to ever catch it. Closing now runs a few extra sweeps over the following ~4
  seconds instead of just one, closing the window regardless of how fast the close follows the
  extraction.
- `/crate admin create` didn't say where the new crate's definition actually went — it's saved
  to `config/neoessentials/crates.json` (a standalone file, unaffected by split-config mode),
  separate from the runtime data (keys/blocks/history) under `neoessentials/store/`. The success
  message now says so.
- `/scoreboard reload` (and the scoreboard-only block inside `/neoe reload`) never refreshed
  `animations.json` — only `/tablist reload` did, since that's the only place that happened to
  call `AnimationManager.loadConfig()`. Editing an animation's frames and reloading specifically
  via the scoreboard command left the old frames in memory (showing the raw `<gradient:...>` /
  `{animation:...}` text unformatted) until a `/tablist reload` also happened to run. Holograms
  needed no separate fix — they already read `AnimationManager` live with no caching of their
  own, so they pick this up automatically too.
- **`/eco give|set|take` and `/pay` never invalidated the generalized leaderboard system's
  "money" board** — only `/baltop`'s own dedicated cache — so a balance change updated `/baltop`
  immediately but left any `{leaderboard_money:...}` placeholder (e.g. a scoreboard "richest
  player" line) showing stale data for up to that board's own 60s refresh interval. Reported as
  "the scoreboard shows the wrong richest player." Centralized the fix so every existing (and
  future) balance-changing command gets it for free.
- **`/baltop` and `/leaderboard <board>` both reported "no data"/"no entries" on the very first
  call ever**, even with real balances/stats present — both kick off an async cache rebuild when
  the cache is empty, then immediately read the still-empty cache and report it as genuinely
  empty rather than "still building." Both now distinguish the two cases.
- **`EconomyManager` had no way to recover if it never finished initializing at boot** (same
  class of bug already fixed for `PermissionSystem` earlier — audited for economy on request).
  If `modules.economyEnabled` happened to read as disabled at the exact moment the lazy
  singleton was first touched (e.g. `config.json` not finished parsing yet during a rocky boot),
  balances kept working perfectly *in memory* for the rest of that session, with no error of any
  kind — they just silently never persisted, reverting every account to its starting balance on
  the next restart. `/neoe reload` now retries initialization if it never completed, alongside
  the existing permission self-heal.
- **`/pay` and `/eco give|take` crashed with a `ClassCastException` on every single use whenever
  `security.enableInputValidation` was turned off in config.** The bypass path in
  `InputValidator.validateEconomyAmount()` returned the raw input as a `Double`, but every
  caller unconditionally reads it back as a `BigDecimal` — found while auditing the rest of the
  economy system for bugs/gaps after the fixes above. Still guards against `NaN`/infinite input
  (which `BigDecimal.valueOf()` itself throws on) either way; only the actual min/max/positivity
  checks are skipped when validation is disabled, which is what "disabled" is supposed to mean.
- Deleted two dead economy classes (`EconomyCache`, `EconomyLeaderboard`) found during the same
  audit — early prototypes with zero references anywhere, both fully superseded before ever
  being wired up (by `EconomyManager`'s own balance cache, and by `BaltopCommand`'s async
  cached/paginated leaderboard, respectively). No behavior change.
- **`{animation:NAME}`/`<gradient:...>`/`<rainbow>` tags never resolved in chat** — every command
  reply (`/pay`, `/crate open`, etc.) built its message through a color-code-only parser that had
  no idea those tags existed, so they showed up in chat as raw literal text instead of animating.
  Reported specifically for a crate's `displayName` (set to `{animation:animation}`) showing
  unresolved when a crate was opened. Fixed at the shared message-building foundation used by
  nearly every command in the mod, so this is fixed mod-wide, not just for crates — each message
  now resolves to whichever animation frame is current at the exact moment it's sent. Chat has no
  live-update channel (unlike tablist/scoreboard/holograms, which get continuously re-sent), so a
  sent message is a permanent snapshot of that instant, not a continuously-animating one — there's
  no `intervalMs`/`refreshInterval` equivalent for chat, and there can't be.
- Crate GUI titles (preview and opening screens) and a crate key item's lore/hover text now also
  resolve `{animation:NAME}`/gradients, matching the key item's name (fixed earlier). Same
  one-time-snapshot caveat as above applies — the title/lore is fixed to whatever frame was
  current the moment the GUI opened or the key was minted.
- Confirmed (not a bug, documented for clarity): `/crate admin setkey` already supports any
  vanilla **or modded** item as a crate's key — it copies the exact `ItemStack` you're holding,
  full NBT/data components included, via the same serializer crate rewards use. No restriction to
  a fixed item list.
- **An animation frame's `<gradient:...>`/`<rainbow>` syntax was silently stripped in chat** unless
  `chat.richText.enabled` was separately turned on (off by default) — that config exists to gate a
  *player* typing raw tag syntax directly into their own message, but was also blocking
  admin-authored `animations.json` content, which tablist/hologram have always rendered
  unconditionally. An animation using gradients would render correctly in the tablist/hologram it
  was also used in, but show up flattened/stripped the moment the same `{animation:NAME}` was used
  in chat. Fixed — animation-frame gradients/rainbow now always render in chat, matching
  tablist/hologram; the config still gates a player's own raw `<gradient:...>` typed directly.
- **`/neoe reload` never actually reloaded `leaderboard.json`** — every sibling system's refresh
  interval/config was already live-reloadable via `/neoe reload` (tablist, scoreboard, hologram),
  but leaderboard boards only picked up config edits via the separate `/leaderboard reload`
  command. Editing a board's `refreshInterval` (or anything else in `leaderboard.json`) and
  running `/neoe reload` silently did nothing until `/leaderboard reload` was run specifically.
  Fixed — `/neoe reload` now reloads leaderboard config too.
- **`{animation:NAME}` froze on its first frame forever, mod-wide (holograms, scoreboard, chat,
  crate keys), on any server with `modules.tablistEnabled: false`.** The single clock driving
  every animation's frame timing was only ever advanced from inside the tablist system's own
  per-tick handler — which itself never ran at all once the tablist *module* was disabled, a
  perfectly reasonable choice for a server that only wants holograms/scoreboard, not custom
  tablist. No `refreshInterval`/`animationInterval` setting on any other system could ever fix
  this, since the underlying frame clock itself was never advancing in the first place — lowering
  a hologram's poll rate just polled a permanently frozen state faster. Fixed — the animation
  clock now advances every server tick unconditionally, independent of the tablist module.
- **Renamed `hologram.refreshInterval` (`config.json`) to `hologram.pollIntervalTicks`** — the
  old name was too easy to confuse with each hologram's own, unrelated, seconds-based
  `refreshInterval` (`/hologram setrefresh`), which is the one that actually gates
  placeholder refresh; this key only controls how often the scheduler polls to check that gate.
  Lowering it below the per-hologram value did nothing visible, reported as "reducing the tick
  doesn't do proper animation." Existing custom values are carried over automatically to the new
  key name on upgrade.
- **Hologram animations advanced in visible bursts ("jumpy"/slow) no matter how low the
  animation's own `frameDuration` was set** — reported after lowering an animation from 100ms to
  50ms fixed it instantly in the tablist and scoreboard but not in holograms. Root cause:
  hologram placeholder-refresh and animation-tick both ran on the *same* single background
  thread; any refresh cycle that took a non-trivial amount of time (external placeholder
  providers, gradient/rainbow parsing) delayed the next animation tick behind it, since a
  fixed-rate schedule on one thread runs a late task immediately after the one blocking it
  rather than in parallel. The animation clock itself was always ticking correctly — delivery to
  the hologram entity was what stalled and caught up in bursts. Fixed — refresh and animation now
  run on two independent threads. Also promoted a silently-swallowed per-cycle error log to a
  visible warning, so any further hologram render failure shows up without enabling debug logging.
- **Crate rewards/keys with saved item components (custom name, lore, enchantments, etc.) could
  silently lose that data on server start**, throwing "[AuctionHouse] Server not set on
  ComponentSerializer" once per affected reward in the log. Crate loading and the line that binds
  the registry access needed to deserialize those components were two separate
  `ServerStartedEvent` listeners racing each other — whichever fired first won, and
  `event.getServer().execute(...)` doesn't actually defer past sibling listeners the way it
  looks like it should (it runs inline when already on the server thread, not on a later tick).
  Fixed by running crate loading after the registry binding, unconditionally, regardless of
  listener registration order.
- **Hologram animations could still advance in visible bursts under load, even after the
  refresh/animation thread split above.** The scheduler used `scheduleAtFixedRate`, which
  anchors to its original schedule and fires back-to-back to catch up once a cycle is delayed
  (a slow refresh pass, a main-thread hiccup) — exactly what a visible "jump" looks like. Now
  uses `scheduleWithFixedDelay`, which waits the full delay from when the previous cycle actually
  finished instead, so a delay can never build into a backlog to burst through.
- **A color-only `{animation:NAME}` — a gradient cycling through hex stops over the same
  literal words, the single most common animation shape — could freeze on its first frame
  forever in holograms specifically**, even with the fixes above and every interval already at
  its fastest setting. The hologram's frame-change detection compared each frame's plain-text
  content to decide whether to re-render, but a color-only change resolves to the exact same
  plain text on every frame, so the comparison never saw anything change — the shared animation
  clock kept advancing correctly the entire time, tablist/scoreboard never had this bug (they
  don't do this comparison at all), only holograms' extra "did it actually change" check did.
  Fixed to compare the pre-color-stripped resolved text instead.

---
