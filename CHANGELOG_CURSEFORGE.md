# NeoEssentials — Changelog

Starting from **v1.0.6** — earlier history (v1.0.5.x and before) is not carried over.

**Minecraft 1.21.1 – 1.21.11 · NeoForge 21.1.179+**

---

## [1.0.6] — 2026-08-27

### Added
- Sidebar scoreboard system — `/scoreboard` with config-driven boards, conditions,
  animation, group/player overrides, a toggle command, and a dashboard endpoint.
- General leaderboard system — `/leaderboard` (`/lb`), with `money`/`kills`/`mob_kills`/
  `playtime` boards and `{leaderboard_<board>:<rank>:name|value}` placeholders.
- Leaderboard boards are now config-driven (`leaderboard.json`) — register any vanilla
  per-player stat (blocks mined, mobs killed, distance, etc.) as a board via config, plus
  "custom" point-total boards (`/leaderboard admin set|add|reset|create|delete`) and a
  `LeaderboardAPI` for other mods to register their own boards.
- `/leaderboard hologram create <board> <id> [lines]` — generates a ranked leaderboard
  hologram in one command; the lines keep updating live on their own.
- Leaderboards can now rank non-players — a new `shop_sales` board ranks sign/chest and NPC
  shops by total revenue, shown by shop name instead of a player name.
- Per-board `refreshInterval` in `leaderboard.json` (was a fixed 60s for every board).
- Leaderboard styling: automatic per-rank medal/color placeholders (`:medal`/`:rankcolor`),
  per-board `entryFormat`/`headerFormat` chat templates, and a new paginated GUI viewer
  (`/leaderboard <board> gui`) with real player heads and per-board icons.
- `/tpr`/`/rtp` can open a biome-select chest GUI instead of teleporting instantly — opt-in
  via `randomTeleportSettings.mode: "gui"` (default unchanged). Lists every biome the
  dimension can generate (modded biomes included automatically), plus a "Random — Any Biome"
  button; picks a real spot in that biome or shows a clear error if it can't find one within
  the server border. Icons default to that biome's sapling/dedicated block; admins can pin
  biomes to fixed slots and/or override icons via `biomeMenuItems`.
- Votifier vote listener — its own TCP port, both V1 (RSA) and V2 (NuVotifier-compatible)
  protocols auto-detected, per-site reward config (commands/crate keys), offline vote queueing,
  vote party, and `/vote`/`/votes`/`/voteparty` commands.
- Crates — weighted reward-pool crates with a key balance system, opened via `/crate open` or
  a physical block, three opening animations (roulette/sequential/instant), and full admin
  commands to define crates/rewards in-game. Votifier can grant crate keys per vote site.
- `/crate admin setblock` now auto-creates a hologram above the block (named `crate_<crateId>`,
  findable in tab-completion), fully customizable via any `/hologram` subcommand.
- Left-clicking a physical crate block now opens the no-cost reward-odds preview (shift+click to
  still break it).
- `/crate key giveitem <player> <crate> <amount>` gives real, tradeable physical crate key
  items — no virtual balance required to redeem one.
- Tablist `header`/`footer` frames can now be written as their own JSON array of lines instead
  of hand-escaping `\n` inside one long string — e.g. `["Line 1", "Line 2"]` as one frame.
- Crate key item names now support `{animation:NAME}`/gradients/rainbow, not just `&`-codes —
  matching what already worked in the crate's hologram text (a snapshot per-item, since a held
  item can't be live-repainted the way a hologram can).
- Crates now support separate display names for chat and the key item —
  `chatDisplayName`/`crateKeyDisplayName` (`crates.json`), plus `/crate admin
  setchatname`/`setkeyname`, both falling back to `displayName` if unset. Previously an animated
  `displayName` flashed a different random snapshot frame in every chat message with no way to
  opt chat out of it specifically.
- `/crate keys [player]` now lists every registered crate on its own line, including ones you
  have zero keys for, instead of only comma-joining the crates you actually held keys for.
- Fixed the console being flooded with `PermissionManager is null` warnings on servers using an
  external permission adapter with no prefix/suffix support (e.g. FTB Ranks) — that's the
  expected state there, not a failure, and no longer logs a warning.
- Hologram refresh/animation tick rates were hardcoded — now configurable via
  `hologram.refreshInterval`/`animationInterval` in `config.json`, same convention as tablist/
  scoreboard's `refreshInterval`. Applies with `/neoe reload`.
- New per-board `refreshMultiplier` in `scoreboard.json` (default 1) lets one board cycle its
  animation frames slower than the rest without touching the global refresh rate. Default
  `refreshInterval` for tablist/scoreboard/hologram tightened from 20 to 10 ticks (smoother out
  of the box) — only affects fresh installs, existing configs are untouched.

### Fixed
- **2026-09-06** — Build 59 ([`ffaa874f`](https://github.com/ZeroG-Network-PTY-LTD/NeoEssentials/commit/ffaa874f416187d0ec7b9845bbf42d80d9fa3f09)) — Fixed a player's name showing as literal `HNAME<name>/HNAME` in chat instead of a clean clickable name when a chat-format template wrapped `{neoessentials_username}` in `<gradient:...>`/`<rainbow>` (the common way to give an FTB Ranks/LuckPerms rank a colored name). Fixes #70.
- The startup admin-notice chat block (bug-report links, config split available, etc.) is now
  gated by a dedicated `neoessentials.admin.notice` permission node instead of a hardcoded
  OP/wildcard check, and all pending notices render as one combined block. Also removed the
  "LEGACY DATA FILE(S) NO LONGER READ" notice entirely.
- FTB Ranks servers can now use `"group:<rank>"` chat-format keys and the
  `{ftbranks_prefix}`/`{ftbranks_suffix}`/`{ftbranks_rank}`/`{ftbranks_group}` placeholders —
  these never actually worked on FTB Ranks before (only LuckPerms). Group now resolves from the
  player's highest-power rank; prefix/suffix come from that rank's `ftbranks.name_format` value.
  Also fixed `"group:<name>"` keys being case-sensitive against the (always-lowercased) resolved
  group name, which affected every permission backend, not just FTB Ranks.
- `/permissions group <group> setprefix|setsuffix` no longer shows a raw "unexpected
  error" when the internal permission manager isn't active (e.g. an external permissions
  plugin like LuckPerms is in use) — it now explains why instead.
- `{luckperms_group}`/`{luckperms_primary_group}`/`{ftbranks_rank}`/`{ftbranks_group}`
  placeholders no longer resolve to an empty string when LuckPerms/FTB Ranks is actually
  active — `prefix`/`suffix`/`displayname` variants were unaffected.
- Nicknamed players (`/nick`) now keep their permission-group prefix/suffix in the tab list —
  a nickname used to silently drop it the instant it was set, on any group.
- `PermissionAPI.getPrefix()`/`getSuffix()` now fall back to the internal permission system
  when LuckPerms/FTB Ranks has no opinion, matching `getGroupWeight()`/`getPrimaryGroup()`'s
  existing behavior. FTB Ranks servers previously got no prefix/suffix at all through this
  mod, ever; LuckPerms groups with no `prefix`/`suffix` meta set now fall back to an internal
  one too instead of showing nothing.
- Five more places had the identical internal-manager-only bug (found by auditing every
  remaining call site): the public `NeoEssentialsAPI` group lookup, the `{group}` placeholder,
  per-group tablist column bucketing, chat badge group gating, and player tags all silently
  fell back to `""`/`"default"` under LuckPerms/FTB Ranks — now fixed the same way.
- Config-version upgrades (the merge that adds new default keys/boards to an existing config
  on update) were silently failing on Windows — fixed. Also fixed new default boards (like
  `shop_sales`) not reaching an already-upgraded install even after that fix.
- `/tpr`/`/rtp` could lag or crash a server, especially without pre-generated terrain — it was
  force-generating far more chunks per teleport than necessary (an unneeded 9-chunk safety
  grid for RTP specifically, plus a 10-chunk burst refilling the background cache right after
  every teleport). Both fixed; new `prewarmBatchSize` config caps the refill burst, and lower
  default range/attempts reduce worst-case cost on fresh servers. `/tpr` also now announces
  its warmup delay and that moving cancels it — that already worked, it just wasn't visible.
- Splitting your config (`/neoe config split`) silently dropped web dashboard settings and your
  storage backend choice entirely — neither was ever registered in the split-config section
  list, so both always fell back to defaults after splitting (dashboard settings, and storage
  reverting to `"json"` even if you had SQLite/MySQL configured). Both fixed — `webDashboard`
  now gets its own `dashboard.json`, `storage` now lives in `main.json`. See the Split Config
  wiki page if you already hit this — your original values are recoverable from
  `config.json.backup`.
- Right-clicking a physical crate block ran the whole open flow twice per click (duplicated
  "no keys" message, could double-consume keys) — fixed.
- A crate with no rewards configured yet silently ate a key and reported the misleading "no
  keys" error instead of "no rewards configured" — fixed; the key is no longer spent unless a
  reward actually exists.
- Tab-completion was silently missing on ~25 command arguments across 16 files (crate/hologram/
  home/board/group names, item ids, player targets, etc.) — full audit, all fixed.
- A crate's `&`-coded display name showed up as literal text (e.g. `&7Common Crate`) instead of
  being colored, in chat, GUI titles, and the key item's name — fixed mod-wide (every command
  reply built via `MessageUtil`, not just crates).
- `{server_name}` is now a plain, independently-configurable name (`general.serverName`), no
  longer tied to any MOTD — a new `{server_motd}` placeholder covers the "show the actual
  configured MOTD" case instead.
- `/neoe reload` silently did nothing for the scoreboard system (only tablist was fixed for
  this before) — scoreboard.json edits now actually take effect on reload.
- Some inventory-utility client mods' "pull items from the open GUI" button could steal (and
  duplicate) items straight out of `/crate preview`/the opening reveal GUI — an initial fix
  missed the exact method these mods use to blank the slot; now every mutation path on the
  container is locked down, closing it regardless of which mod is doing the pulling. Confirmed
  (via Quark) that some mods don't even need a mutation method — they just read and copy the
  item — so both crate GUIs now also mark every displayed item and sweep it out of your real
  inventory if it ever ends up there, regardless of how it got there. The preview's close/prev/
  next buttons and filler panes were missed by the first pass of that marking; fixed too.
- Physical crate key items were never actually giveable, and even a key item obtained some
  other way still required virtual balance to redeem — both fixed (see `/crate key giveitem`
  above).
- A malformed config file (e.g. a stray brace from a manual edit) could crash the entire server
  repeatedly — a JSON parse error now falls back to defaults for whatever that file drives
  instead of taking the server down.
- A nicknamed player still showed their real IGN in tab to anyone who joined the server after
  the nickname was set — every join now re-syncs everyone's nickname overrides, not just the
  joining player's own.
- `/neoe reload` (and `/scoreboard reload`) could kick every player seeing the sidebar
  scoreboard with a fatal "Network Protocol Error" — fixed.
- The permission system couldn't recover from a failed boot init without a full restart (kept
  logging `PermissionManager is null` all session) — `/neoe reload` now re-initializes it from
  scratch when that happens instead of failing the same way forever.
- The "config splitting available"/"legacy data files" admin notices nagged on every single
  restart forever — each now shows once per install instead of once per session.
- Closing the crate GUI immediately after using a pull-mod extraction button could still win the
  race and keep the item — closing now sweeps a few more times over the next ~4 seconds instead
  of just once, closing the timing window entirely.
- `/crate admin create` didn't say where the new crate went — now points to
  `config/neoessentials/crates.json`.
- `/scoreboard reload` never refreshed `animations.json` — only `/tablist reload` did. Editing
  an animation and reloading via the scoreboard command left the old frames showing (raw
  `{animation:...}`/gradient text, unformatted) until a `/tablist reload` also happened to run.
- `/eco give|set|take`/`/pay` never invalidated the leaderboard "money" board (only `/baltop`'s
  own cache), so a balance change could leave a scoreboard "richest player" line stale for up
  to 60s — fixed. `/baltop` and `/leaderboard <board>` also both misreported "no data" on the
  very first call ever, even with real data present — fixed.
- The economy system had no way to recover if it never finished initializing at boot (silently
  stopped persisting balances for the rest of that session) — `/neoe reload` now retries it,
  same as the permission system's existing self-heal.
- `/pay` and `/eco give|take` crashed on every use whenever `security.enableInputValidation`
  was turned off — a validator returned the wrong type in that bypass path — fixed.
- `{animation:NAME}`/gradient/rainbow tags never resolved in chat — every command reply used a
  color-code-only parser that didn't know those tags existed, so they showed as raw literal text
  (reported for a crate's name showing `{animation:animation}` unresolved when opened). Fixed
  mod-wide at the shared message-building code, not just for crates. Note: a sent chat message is
  a permanent snapshot of whichever animation frame was current the instant it was sent — chat
  has no live-update channel like tablist/scoreboard/holograms do, so there's no
  `intervalMs`/`refreshInterval` equivalent for it.
- Crate GUI titles and a crate key's lore/hover text now also resolve animation/gradient tags,
  matching the key's name (fixed earlier) — same one-time-snapshot behavior.
- Confirmed, not a bug: crate keys (`/crate admin setkey`) already support any vanilla or modded
  item, full NBT/data included — no fixed item list.
- An animation frame's gradient/rainbow tags were silently stripped in chat unless
  `chat.richText.enabled` was separately turned on (off by default) — tablist/hologram always
  rendered them regardless of that setting. Fixed — animation gradients/rainbow now always render
  in chat too; the config still gates a player typing raw gradient tags themselves.
- `/neoe reload` never actually reloaded `leaderboard.json` (only the dedicated `/leaderboard
  reload` did) — every sibling system's refresh interval already worked via `/neoe reload` except
  this one. Fixed.
- `{animation:NAME}` froze on its first frame forever, mod-wide (holograms, scoreboard, chat,
  crate keys), on any server with the tablist module disabled — the only thing that ever advanced
  the animation clock was tablist's own tick handler, which never ran once tablist was off. No
  hologram/scoreboard interval setting could fix this since the clock itself never moved. Fixed —
  animations now advance every tick regardless of whether tablist is enabled.
- Renamed `hologram.refreshInterval` (`config.json`) to `hologram.pollIntervalTicks` — it was
  easy to confuse with each hologram's own, separate, seconds-based `refreshInterval`
  (`/hologram setrefresh`), which is the one that actually controls how often placeholders
  refresh. Lowering the old key below that value did nothing visible. Existing values carry over
  automatically on upgrade.
- Hologram animations advanced in visible bursts ("jumpy"/slow) no matter how low the animation's
  own `frameDuration` was set, even though the same change applied instantly and smoothly in
  tablist/scoreboard — hologram refresh and animation ticking shared one background thread, so a
  slow refresh cycle delayed the next animation tick behind it. Fixed — they now run on
  independent threads. Also promoted a silently-swallowed hologram render error to a visible
  warning.
- Crate rewards/keys with saved item components (custom name, lore, enchantments) could silently
  lose that data on server start due to a startup ordering race between two separate listeners.
  Fixed — crate loading now always happens after the fix that binds the registry access it needs.
- Hologram animations could still burst under load even after the thread split above — the
  scheduler caught up on a delayed cycle by firing back-to-back instead of skipping. Fixed by
  switching to a schedule that waits from when the previous cycle finished, so it can never
  build a backlog to burst through.
- A color-only `{animation:NAME}` (a gradient cycling hex stops over the same literal words —
  the most common animation shape) could freeze on its first frame forever in holograms
  specifically, even with the fixes above. The change-detection compared plain text, which a
  color-only change never affects, so it never saw a change to react to. Fixed.

---
