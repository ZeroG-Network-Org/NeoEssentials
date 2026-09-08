# Custom Player Tablist System

> **Config file:** `config/neoessentials/tablist.json`  
> **Reload in-game:** `/tablist reload`  
> **Admin permission:** `neoessentials.tablist.admin`  
> **Introduced:** build.74 — BungeeTabListPlus-inspired rewrite

---

## Overview

The NeoEssentials tablist system replaces the vanilla player list header and footer with a fully
customizable, animated display inspired by **BungeeTabListPlus** (BTLP). It operates in
**independent mode** by default — NeoEssentials owns the tablist entirely, no proxy plugin needed —
while optionally integrating with a BungeeCord/Velocity proxy for cross-server data.

| Feature | Details |
|---------|---------|
| **Independent mode** | NeoEssentials owns header/footer/player-rows — no proxy plugin required |
| **Animated frames** | Header and footer cycle through an array of strings at a configurable tick rate |
| **20+ placeholders** | Standard, proxy-aware, economy, session, stats, and AFK tokens |
| **Per-group / per-player** | Different header/footer per permission group or per player UUID |
| **Group-weight sorting** | Players sorted by rank weight (admins first) then alphabetically — BTLP `ContextAwareOrdering` |
| **Fake player entries** | Decorative separator rows, section labels, padding, and custom skins/heads — BTLP `fakePlayers` concept |
| **Layout configuration** | 1–4 column grid with per-group column sections + headers, playersByServer grouping, excludeServers/hiddenServers |
| **Proxy integration** | Optional BungeeCord bridge for `{network_online}` / `{server_online:X}` data |
| **Hex / gradients / rainbow** | Full `RichTextFormatter` syntax support — `&#RRGGBB`, `<gradient:…>`, `<rainbow>` |

References: [BungeeTabListPlus](https://github.com/CodeCrafter47/BungeeTabListPlus), [TAB](https://github.com/NEZNAMY/TAB), [Simple TabList](https://modrinth.com/plugin/simple-tablist)

---

## Color & Formatting Syntax

All color and formatting tags are supported in `header`, `footer`, `playerFormat`, `afkSuffix`,
`groupColors`, `fakePlayers[].name`, and every per-group / per-player override field.

### Legacy Codes (`&`)
```
&0–&9 &a–&f    — color codes
&k  obfuscated   &l  bold   &m  strikethrough
&n  underline    &o  italic  &r  reset
```

### Hex Colors
```
&#RRGGBB                       — inline  e.g.  &#FF5500
<color:#RRGGBB>text</color>    — span with explicit close
```

### Gradients
```
<gradient:FF0000-0000FF>text</gradient>            — 2-stop
<gradient:FF0000-FFFF00-00FF00>text</gradient>     — multi-stop
```

### Rainbow
```
<rainbow>text</rainbow>
```

### Named Colors & Format Tags
```
<red>  <gold>  <green>  <aqua>  <blue>  <white>  <gray>  …
<bold>  <italic>  <underline>  <strikethrough>  <reset>
```

> **Always close your `<gradient:...>` tags.** An unclosed `<gradient:...>` (no matching
> `</gradient>`) is treated as "gradient the rest of the line" — handy when you genuinely want a
> gradient to run to the end of a header/footer line, but if you follow it with plain `&`-codes
> (e.g. `&r`, `&8`, `&e`) intending them to reset back to normal color, those codes get folded
> into the swallowed gradient region instead. As of the current build, single legacy `&`-codes
> (`&0`–`&f`, `&k`–`&r`) inside a gradient region are passed through atomically rather than being
> split character-by-character, so this degrades gracefully — but closing the tag explicitly is
> still the clearest way to control exactly where a gradient starts and stops:
> ```
> <gradient:00FFC8-0080FF>&lGradiented Text</gradient>&r &8| &enormal text again
> ```

---

## Placeholders

### Standard

| Placeholder | Description |
|-------------|-------------|
| `{player}` | Viewing player's username |
| `{displayname}` | Display name (group colour applied) |
| `{online}` | Online player count (respects `hideVanished`) |
| `{max}` | Server max slots |
| `{ping}` | Viewing player's ping (ms) |
| `{world}` | Current dimension path (`overworld`, `the_nether`, `the_end`) |
| `{tps}` | Server TPS — auto-coloured green/yellow/red |
| `{time}` | Server real-world time (`HH:mm`) |
| `{server_name}` | Plain server name (`general.serverName` in config.json) |
| `{server_motd}` | Configured MOTD (`/motd` if set, otherwise `server.properties`) |
| `{x}` `{y}` `{z}` | Viewing player's block coordinates |
| `{balance}` | Economy balance (requires EconomyManager) |
| `{prefix}` | Permission group prefix |
| `{suffix}` | Permission group suffix |
| `{group}` | Permission group name |
| `{newline}` | Line break `\n` |
| `{bar}` | Decorative strikethrough separator |

### Multi-line frames

Vanilla's tab list header/footer already renders embedded `\n` as a real line break — you can
either drop `{newline}` into a plain string frame:

```json
"header": "&6&lWelcome!{newline}&7{online}/{max} online"
```

or, for a frame with several lines, write it as its own JSON array of lines instead of one long
string — the outer array is still the list of animation frames (unchanged), but any *element* of
it can itself be an array of lines, which get joined with `\n` for you:

```json
"header": [
  ["&6&lWelcome!", "&7{online}/{max} online"],
  ["&b&lHave fun!", "&7TPS: {tps}"]
]
```

That's two animation frames, each two lines tall. A flat array of plain strings (no nesting)
keeps meaning exactly what it always has — multiple single-line frames, not lines of one frame.

### BTLP-Style (Proxy & Network)

| Placeholder | Description |
|-------------|-------------|
| `{network_online}` | Total players on proxy network (requires `proxy.enabled=true`) |
| `{server_online:NAME}` | Players on a specific proxy server by name |
| `{current_server}` | Proxy server name the viewing player is on |
| `{server_label}` | This server's configured display label (`proxy.serverLabel`) |

### BTLP-Style (Player Stats & Session)

| Placeholder | Description |
|-------------|-------------|
| `{rank_weight}` | Numeric permission group weight |
| `{session_minutes}` | Minutes elapsed in current session (0–59) |
| `{session_hours}` | Full hours elapsed in current session |
| `{level}` | Player XP level |
| `{health}` | Current HP (integer) |
| `{max_health}` | Maximum HP (integer) |
| `{afk}` | Blank when active; shows `afkSuffix` text when AFK |

### PlaceholderAPI / NeoEssentials Expansion Tokens

Any `{neoessentials_*}` token registered with the PlaceholderAPI system is also resolved in tablist header, footer, and player row fields. This includes all built-in tokens and any custom expansion tokens registered by other mods.

**Stat examples usable in tablist:**

| Placeholder | Description |
|-------------|-------------|
| `{neoessentials_deaths}` | Player's total death count |
| `{neoessentials_player_kills}` | Player's total player kills |
| `{neoessentials_mob_kills}` | Player's total mob kills |
| `{neoessentials_play_time}` | Player's formatted play time (e.g. `3d 2h 15m`) |
| `{neoessentials_balance}` | Economy balance |
| `{neoessentials_prefix}` | Permission prefix |

> **Resolution order:** Native tablist tokens (e.g. `{tps}`, `{ping}`) are resolved first, then `PlaceholderManager.setPlaceholders()` handles all remaining `{…}` tokens. Both can be mixed in the same line.

> **TPS colouring:** `{tps}` is automatically formatted — `&a` (≥19), `&e` (≥15), `&c` (<15). Use `{tps}` directly; no manual colour needed.

---

## Configuration

### Full `tablist.json` structure (v5)

```json
{
  "_configVersion": 3,
  "tablist": {

    "enabled": true,
    "refreshInterval": 20,

    "independentMode": true,

    "header": [
      "<gradient:FFD700-FF8C00>&l✦ {server_name} ✦&r &8| &e{online}&8/&e{max} &7players",
      "<gradient:FF8C00-FFD700>&l✦ {server_name} ✦&r &8| &eTPS: {tps}",
      "<gradient:FFD700-FF8C00>&l✦ {server_name} ✦&r &8| &e{time}"
    ],

    "footer": [
      "&7TPS: {tps} &8| &7Ping: &a{ping}&7ms &8| <green>{world}",
      "&7Coords: &e{x}&7, &e{y}&7, &e{z} &8| &7World: &e{world}",
      "&7Balance: &a{balance} &8| &7Group: &#FFD700{group}"
    ],

    "playerFormat": "&f{prefix}&r{player}{suffix}",

    "hideVanished": true,
    "showAfkIndicator": true,
    "afkSuffix": " &7[AFK]",

    "groupColors": {
      "owner":   "&#FF4444",
      "admin":   "&c",
      "mod":     "&6",
      "vip":     "&#00CFFF",
      "default": "&f"
    },

    "groups": {
      "admin": {
        "header": [
          "<gradient:FF0000-AA0000>&l⚑ ADMIN PANEL ⚑&r {newline}&8{player} — ping &e{ping}ms",
          "<gradient:AA0000-FF0000>&l⚑ ADMIN PANEL ⚑&r {newline}&8Coords &e{x}&8, &e{y}&8, &e{z}"
        ],
        "footer": "&cAdmin tools: &f/vanish /kick /ban /mute &8| &7{world}"
      },
      "vip": {
        "header": [
          "<gradient:00CFFF-0080FF>&l★ VIP ★&r &8| &e{online}&8/&e{max} &7players",
          "<gradient:0080FF-00CFFF>&l★ VIP ★&r &8| &7Welcome back, &b{player}!"
        ],
        "footer": "&#00CFFF{player}&7 — &aBalance: &f{balance} &8| &7Ping: &a{ping}ms"
      }
    },

    "players": {
      "00000000-0000-0000-0000-000000000000": {
        "header": "<rainbow>Hello, {player}!</rainbow>",
        "footer": "&#FF5500Your special footer"
      }
    },

    "proxy": {
      "enabled": false,
      "serverLabel": "Main",
      "pollIntervalTicks": 100,
      "showNetworkPlayers": false,
      "knownServers": ["Lobby", "Survival", "Creative"]
    },

    "fakePlayers": [
      { "id": "sep1",    "name": "&8&m──────────────────────", "latency": -1 },
      { "id": "header1", "name": "&e&lOur Network",            "latency": 0  },
      { "id": "sep2",    "name": "&8&m──────────────────────", "latency": -1 }
    ],

    "layout": {
      "columns": 1,
      "sortByGroupWeight": true,
      "groupSections": false,
      "playersByServer": false,
      "excludeServers": [],
      "hiddenServers": [],
      "maxSlotsPerColumn": 20
    }
  }
}
```

### Core Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable the whole system |
| `refreshInterval` | int | `20` | Update interval in ticks (20 = 1 s) |
| `independentMode` | boolean | `true` | NeoEssentials owns the tablist entirely |
| `header` | string or array | — | Header frame(s) — see [Multi-line frames](#multi-line-frames) |
| `footer` | string or array | — | Footer frame(s) — see [Multi-line frames](#multi-line-frames) |
| `playerFormat` | string | `"&f{prefix}&r{player}{suffix}"` | Player row display format |
| `hideVanished` | boolean | `true` | Exclude vanished players from `{online}` |
| `showAfkIndicator` | boolean | `true` | Append `afkSuffix` to AFK players |
| `afkSuffix` | string | `" &7[AFK]"` | AFK display suffix |
| `groupColors` | object | — | Quick `{displayname}` colour prefix per group |
| `groups` | object | — | Per-group `header`/`footer`/`nametagPrefix`/`nametagSuffix` overrides |
| `players` | object | — | Per-player UUID `header`/`footer`/`nametagPrefix`/`nametagSuffix` overrides |
| `nametagSettings.enabled` | boolean | `true` | Whether NeoEssentials manages the above-head nametag prefix/suffix at all |

---

## Priority

When selecting which header/footer to show, the system uses:

```
per-player override  >  per-group override  >  global default
```

A player in the `vip` group with a per-player override will always see their personal
header, not the VIP group header.

Nametag prefix/suffix (below) use the same priority order.

---

## Above-Head Nametag

The text shown above a player's head (and, since Minecraft ties both to the same scoreboard
team property, their tab-list row) is a prefix/suffix pair. **By default this is exactly the
same prefix/suffix chat already uses** — whatever your permission system (internal groups, or
an external adapter like LuckPerms) has set — so there's nothing to configure to get a nametag
that matches chat.

To make the nametag say something *different* from chat, set an override in `tablist.json`:

```jsonc
"groups": {
  "admin": {
    "nametagPrefix": "&c[Admin] ",
    "nametagSuffix": ""
  }
}
```

Or per-player, under `players` (keyed by UUID), with the same two keys. Priority is
per-player > per-group > the permission-system default, same as header/footer.

`nametagPrefix`/`nametagSuffix` (and a permission-system default value, if it contains any)
are resolved through the **same placeholder pipeline as header/footer** — every token listed
under [Placeholders](#placeholders) works here too, e.g.:

```jsonc
"groups": {
  "vip": {
    "nametagPrefix": "&#00CFFF[VIP] ",
    "nametagSuffix": " &7({balance})"
  }
}
```

The only tokens that don't make sense here are `{prefix}`/`{suffix}` themselves (a nametag
override referencing its own value) — if one somehow does, it's returned unresolved rather
than looping forever.

In-game (runtime, not persisted — add to `tablist.json` manually to survive `/tablist reload`):

```
/tablist player <name> nametag prefix <text>
/tablist player <name> nametag suffix <text>
/tablist group <group> nametag prefix <text>
/tablist group <group> nametag suffix <text>
/tablist player <name> reset   # also clears nametag overrides
/tablist group <group> reset   # also clears nametag overrides
```

If you'd rather have a different mod/plugin manage nametags entirely, set
`nametagSettings.enabled` to `false` — NeoEssentials will still use the scoreboard team for
tablist sorting/columns, it just won't set any prefix/suffix on it.

> **Discord chat-bridge mods showing the rank prefix twice?** As of build 68, this no longer
> requires disabling the nametag prefix at all — see
> [Discord Integration](ChatSystem.md#discord-integration-simple-discord-link) for the real fix.
> The short version: vanilla Minecraft folds a scoreboard team's prefix/suffix into
> `Player.getDisplayName()` for any mod to read, and some Discord bridges (e.g. SDLink's
> `%display_name%`/rank-sync) read that already-prefixed name AND separately re-resolve the
> same rank prefix themselves, doubling it in Discord even though it's correct in-game and in
> the nametag/tab-list. NeoEssentials' own Discord messages now resolve the prefix independently
> (via the permission system directly, not the vanilla team), so they can never double — pair
> that with disabling the bridge mod's own *native* relay (its conflict detection already warns
> you when this applies) and Discord shows a single correct prefix with the nametag/tab-list
> completely unaffected. `nametagSettings.enabled: false` is only still relevant if you want a
> *different* mod/plugin to own nametags entirely — not as a Discord workaround anymore.

---

## Independent Mode

`independentMode: true` (default) means NeoEssentials is the **sole owner** of the tablist.
No proxy plugin should attempt to manage this server's tab simultaneously.

With independent mode on, proxy integration (`proxy`) is used **for data only** — it can
supply `{network_online}` and `{server_online:X}` counts without taking over header/footer
rendering. The actual display is always controlled by NeoEssentials.

Toggle in-game:
```
/tablist independent on
/tablist independent off
```

---

## Proxy Integration

When running behind a BungeeCord proxy, enable the `proxy` section to surface cross-server
player counts in your header/footer.

```json
"proxy": {
  "enabled": true,
  "serverLabel": "Survival",
  "pollIntervalTicks": 100,
  "knownServers": ["Lobby", "Survival", "Creative"]
}
```

| Field | Description |
|-------|-------------|
| `enabled` | Activates BungeeCord plugin-messaging queries |
| `serverLabel` | Display name for this server (used by `{server_label}`) |
| `pollIntervalTicks` | How often to query the proxy (100 ticks = 5 s) |
| `showNetworkPlayers` | Include network players in the tab row list |
| `knownServers` | Servers to poll for player counts; auto-populated from `GetServers` reply |

Once enabled, `{network_online}`, `{current_server}`, and `{server_online:NAME}` will
be populated. If the proxy hasn't replied yet or proxy is unreachable, `{network_online}`
falls back to the local player count.

> **Note (NeoForge 1.21.1):** Outbound BungeeCord plugin-messaging (needed to *query* the proxy)
> requires NeoForge `StreamCodec` registration which is pending a future build. The proxy
> integration currently receives data sent by the proxy but cannot actively poll.
> Check `/tablist proxy status` to see detected state.

---

## Fake Players (BTLP `fakePlayers`)

Fake players are decorative tab-list entries that don't correspond to real players. Use them
for separator lines, section headers, or grid padding.

```json
"fakePlayers": [
  { "id": "sep_top",    "name": "&8&m──────────────────────", "latency": -1 },
  { "id": "net_label",  "name": "&e&lNetwork Players",        "latency": 0  },
  { "id": "sep_bottom", "name": "&8&m──────────────────────", "latency": -1 }
]
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | `slot_N` | Unique slot identifier (used to derive stable UUID) |
| `name` | string | `""` | Display text — supports all color/format syntax |
| `latency` | int | `0` | Displayed ping bar ms. `-1` = disconnected icon, `0` = green |
| `listed` | boolean | `true` | Whether the entry appears in the visible tab list |
| `skinOwner` | string | — | Mirror a real player's current skin (resolved async, see below) |
| `skinTexture` | string | — | Raw base64 `textures` property value for a fully custom skin/head |
| `skinSignature` | string | — | Signature paired with `skinTexture` (optional — vanilla doesn't re-verify it for tab-list rendering) |

Each fake entry uses a **stable UUID** derived from its `id` via `UUID.nameUUIDFromBytes()`,
so the same entry is always represented by the same UUID across server restarts and reloads.

### Custom Skins / Heads

Fake entries show the default Steve/Alex head unless given a skin:

```json
{ "id": "npc1", "name": "&bNotch", "latency": 0, "listed": true, "skinOwner": "Notch" }
```
```json
{ "id": "npc2", "name": "&aCustom", "latency": 0, "listed": true,
  "skinTexture": "<base64 value from mineskin.org or minecraft-heads.com>",
  "skinSignature": "<base64 signature>" }
```

- `skinTexture`/`skinSignature` take priority over `skinOwner` when both are present, and apply
  immediately (no network call needed).
- `skinOwner` is resolved **asynchronously** via the server's profile cache + session service —
  the entry briefly shows the default skin until resolution completes, after which the tab list
  refreshes automatically. Results are cached until the next `/tablist reload`.
- If `skinOwner` names an unknown player, a warning is logged and the entry keeps the default skin.

Manage at runtime with `/tablist fakeplayer`:
```
/tablist fakeplayer list
/tablist fakeplayer add <id> <display text>
/tablist fakeplayer remove <id>
/tablist fakeplayer refresh
```

---

## Layout & Sorting (BTLP `layout`)

```json
"layout": {
  "columns": 4,
  "sortByGroupWeight": true,
  "groupSections": true,
  "playersByServer": false,
  "excludeServers": [],
  "hiddenServers": [],
  "maxSlotsPerColumn": 20,
  "fillEmptySlots": true,
  "sectionHeaders": {
    "owner":  "&c&l⚑ OWNERS",
    "admin":  "&6&l⚑ ADMINS",
    "mod":    "&6✪ STAFF",
    "vip":    "&b★ VIP",
    "member": "&7MEMBERS"
  }
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `columns` | int (1–4) | `1` | Visual columns (BTLP supports up to 4×20 = 80 slots) |
| `sortByGroupWeight` | boolean | `true` | Sort players by descending group weight then alphabetically |
| `groupSections` | boolean | `false` | Enable the BTLP-style column grid: each permission group gets its own column(s), with padding + optional header row (see below) |
| `playersByServer` | boolean | `false` | Bucket players by proxy server name (requires `proxy.enabled`) |
| `excludeServers` | array | `[]` | Server names fully excluded from this tab (BTLP `excludeServers`) |
| `hiddenServers` | array | `[]` | Server names whose players are hidden (BTLP `hiddenServers`) |
| `maxSlotsPerColumn` | int | `20` | Rows per column; total grid size = `columns × maxSlotsPerColumn` |
| `fillEmptySlots` | boolean | `true` | Pad the grid out to the full `columns × maxSlotsPerColumn` size with invisible filler rows (only relevant when `groupSections` is on) |
| `sectionHeaders` | object | `{}` | Per-group header row text shown at the top of that group's column (only relevant when `groupSections` is on) |

**Sorting** (`sortByGroupWeight`, `groupSections` off) encodes group weight into scoreboard team
names (`ne_<sortKey>_<group>`) so the Minecraft client renders players in the correct order
without packet interception — the same approach BungeeTabListPlus uses via `ContextAwareOrdering`.

### The Column Grid (`groupSections`)

Vanilla Minecraft has **no packet field for "use N columns"** on this mod's target version
(1.21.1) — the client always computes the column count itself from the total number of tab
entries (real players + any decorative ones). BungeeTabListPlus's classic trick, and the one used
here, is to **pad the tab list to a fixed total entry count** so the client's auto-computed
column count stays stable no matter how many real players are online.

When `groupSections: true`:

1. Online players are bucketed by permission group, highest weight first (same order as
   `groupColors`).
2. Each group is packed into consecutive slots. Before a **new** group starts, the grid is padded
   with invisible filler rows up to the next column boundary — so a group's players never
   straddle two columns.
3. If `sectionHeaders` has an entry for that group, a header row is inserted at the very top of
   its column, using the same `&`/hex/gradient syntax as everything else.
4. If `fillEmptySlots` is true (default), any slots remaining after the last group are padded
   with invisible fillers too, so the grid is always exactly `columns × maxSlotsPerColumn` entries
   — keeping the client's column count fixed even when very few players are online.

Example with the config above (4 columns × 20 rows = 80 slots) and players in `owner`, `vip`,
and `member`:

```
Column 1          Column 2          Column 3          Column 4
⚑ OWNERS          ★ VIP             MEMBERS           (empty, filler-padded)
Steve                Alex              Bob
(17 filler rows)  (19 filler rows) (17 filler rows)  (20 filler rows)
```

Ordering is enforced the same way as plain weight-sorting — via scoreboard team names — but each
slot (including headers and fillers) gets a **unique, zero-padded position key** so the client's
alphabetical team sort reproduces the exact grid layout computed above. This is recomputed once
per tick cycle (not per-viewer, since scoreboard teams are global state) so it stays in sync as
players join, leave, or switch groups.

**Caveats carried over from BTLP-era plugins:**
- If real players + header rows exceed `columns × maxSlotsPerColumn`, the overflow simply spills
  past the last column — size the grid generously for your expected peak population.
- Groups with zero online players are skipped entirely (no empty header row for an empty group).
- This feature and `playersByServer` are independent — you can combine per-group columns with
  proxy server bucketing, though the two aren't merged into a single grid automatically.

---

## Commands

All sub-commands require `neoessentials.tablist.admin` (or operator level 4).

### Core
| Command | Description |
|---------|-------------|
| `/tablist` | Show help |
| `/tablist reload` | Reload `tablist.json` and push changes to all players |
| `/tablist enable` | Enable the tablist system |
| `/tablist disable` | Disable; restores vanilla appearance |
| `/tablist preview` | Force-send your own current header/footer |
| `/tablist info` | Full status — mode, proxy, layout, fake players, group overrides |
| `/tablist set header <text>` | Set global header at runtime (lost on reload) |
| `/tablist set footer <text>` | Set global footer at runtime (lost on reload) |

### Per-Player Overrides
| Command | Description |
|---------|-------------|
| `/tablist player <name> header <text>` | Set custom header for a player |
| `/tablist player <name> footer <text>` | Set custom footer for a player |
| `/tablist player <name> reset` | Clear all per-player overrides |

### Per-Group Overrides
| Command | Description |
|---------|-------------|
| `/tablist group <group> header <text>` | Set header for all players in a group |
| `/tablist group <group> footer <text>` | Set footer for all players in a group |
| `/tablist group <group> reset` | Clear group overrides |

### Proxy (BTLP-style)
| Command | Description |
|---------|-------------|
| `/tablist proxy status` | Show proxy state, server list, and per-server counts |
| `/tablist proxy setserver <name> <count>` | Manually override a server's player count |

### Fake Players (BTLP-style)
| Command | Description |
|---------|-------------|
| `/tablist fakeplayer list` | List all configured fake entries |
| `/tablist fakeplayer add <id> <display>` | Add a runtime fake entry |
| `/tablist fakeplayer remove <id>` | Remove a fake entry by id |
| `/tablist fakeplayer refresh` | Remove and re-inject all fake entries for all players |

### Layout & Independent Mode
| Command | Description |
|---------|-------------|
| `/tablist layout info` | Show columns, sort, server config |
| `/tablist independent` | Show current independent mode state |
| `/tablist independent on` | NeoEssentials owns the tablist |
| `/tablist independent off` | Hand tablist control to proxy plugin |

> Runtime overrides via `/tablist player` and `/tablist group` are **not persisted** to disk.
> Add them to `tablist.json → players` / `tablist.json → groups` to survive reloads.

---

## Animation

Set `header` or `footer` to a JSON **array** to enable frame cycling:

```json
"header": [
  "<gradient:FFD700-FF8C00>&l★ My Server ★",
  "<gradient:FF8C00-FFD700>&l★ My Server ★",
  "<rainbow>&lMy Server"
]
```

Each frame is displayed for `refreshInterval` ticks before advancing.
`{session_minutes}` and other stat tokens update each frame.

**Lower `refreshInterval` → smoother animation, more network packets.**  
Recommended: `10` (½ s per frame), `20` (1 s, default), `40` (2 s, very light).

> **Flicker-free at low intervals:** As of build.184, the tablist system uses a dirty-check cache for scoreboard team prefix/suffix packets. Team update packets are only sent when the displayed value actually changes, so using `refreshInterval: 1` (20 fps animation) will not cause the player prefix to flicker or disappear.

---

## Integration with Other Systems

### Nick System
When a player is nicknamed via `/nick`, `TablistManager.setCustomName(uuid, nick)` is called
and the tablist row updates automatically on the next tick.

### AFK System
The AFK system calls `TablistManager.getAfkSuffix()` and appends it to the player's scoreboard
team suffix when they go AFK, removing it when they return. The `{afk}` placeholder also
becomes non-empty when a player is AFK.

### Vanish System
Vanished players are excluded from `{online}` counts when `hideVanished: true`.
Staff with `neoessentials.vanish.see` always see all players.

### Permission System
- `{prefix}`, `{suffix}`, `{group}` pull from the permission group system.
- `{rank_weight}` and sorting use `PermissionGroup.getPriority()`.
- Group prefix/suffix support full color syntax.

### Economy System
`{balance}` pulls from `EconomyManager.getBalance(uuid)` and is formatted to two decimal places.

---

## Examples

### Proxy-aware header showing network players
```json
"header": [
  "<gradient:FFD700-FF8C00>&l✦ {server_name} ✦&r &8| &eNetwork: {network_online} players",
  "<gradient:FF8C00-FFD700>&l✦ {server_name} ✦&r &8| &eLobby: {server_online:Lobby} online"
]
```

### Session stats in footer
```json
"footer": "&7Session: &e{session_hours}h {session_minutes}m &8| &7HP: &c{health}&8/&c{max_health} &8| &a{ping}ms"
```

### Fake player separators with section label
```json
"fakePlayers": [
  { "id": "sep1",   "name": "&8&m──────────────────────────", "latency": -1 },
  { "id": "title1", "name": "&6&l   ★  Online Players  ★",   "latency": 0  },
  { "id": "sep2",   "name": "&8&m──────────────────────────", "latency": -1 }
]
```

### Gradient server name + rainbow
```json
"header": [
  "<gradient:FF0000-FFFF00-00FF00>&l{server_name}</gradient>",
  "<rainbow>&l{server_name}"
]
```

### Per-player VIP badge
```json
"players": {
  "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx": {
    "header": "<gradient:FFD700-FF8C00>&l✦ Super VIP ✦&r{newline}&7Welcome, &6{player}!",
    "footer": "&#FFD700★ Beta Tester &8| &7{ping}ms &8| &aHP: {health}/{max_health}"
  }
}
```

### Minimal static setup
```json
"header": "&6&l{server_name} &8| &e{online}&8/&e{max}",
"footer": "&7{tps} TPS &8| &7{ping}ms &8| &7{world}"
```
