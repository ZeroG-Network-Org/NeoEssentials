# Chat System

> **Version:** 1.0.5+build.54 · **Config:** `config.json` → `chat` section

---

## Overview

Full-featured chat system with format templates, rich text (gradients/rainbow/hover/click), per-group and per-player formatting, channel routing, Discord relay, mute/ignore, social spy, and per-player time/weather. All chat is logged to the server console.

---

## Config (`config.json` → `chat`)

| Key | Default | Description |
|---|---|---|
| `modules.chatEnabled` | `true` | Master switch for chat handling (top-level `modules` section, not under `chat`) |
| `chat-format` | *(object)* | Per-group/world/default format templates — see [Format Priority](#format-priority) below |
| `enableChatEnhancements` | `true` | Clickable names, mentions, URL linking, `[item]` display |
| `clickablePlayerNames` | `true` | Make player names clickable (hover for stats, click to message) |
| `autoLinkUrls` | `true` | Automatically linkify URLs in chat |
| `allowItemLinks` | `true` | Allow `[item]` placeholder to show the held item |
| `mentions.enabled` | `true` | `@PlayerName` mention system (highlight color, sound, permission gate — see `mentions.*`) |
| `badges.enabled` | `true` | Rank badges/status icons — see [Rank Badges & Status Icons](#rank-badges--status-icons) |
| `richText.enabled` | **`false`** | Gradient/rainbow rich-text tags. **Off by default** — enable this to use `<gradient>`/`<rainbow>` (hover/click tags aren't gated by this flag) |
| `conditionalFormatting.enabled` | `false` | `<if:time=...>`/`<if:health<50>`/`<if:afk>`-style conditional format tags — a separate feature from `richText`, not otherwise documented on this page yet |
| `channels` | *(object)* | Per-channel definitions — see [Chat Channels](ChatChannels) |
| `enable-chat-formatting` | `true` | Apply the `chat-format` templates at all — `false` falls back to vanilla chat formatting |
| `enable-color-codes` | `true` | Allow `&`-color codes (including hex) to render in formatted chat at all — a prerequisite gate above the per-player [Colour Permissions](#player-message-colour-permissions) below |
| `logChatToConsole` | `true` | Print formatted messages to server console |
| `customJoinMessage` | `"none"` | Custom join broadcast (placeholders supported via PlaceholderAPI). `"none"` = use vanilla join message |
| `customQuitMessage` | `"none"` | Custom quit broadcast. `"none"` = use vanilla quit message |

> There is no global `localChatRadius`/`joinMessage`/`quitMessage` key — proximity-based chat is
> configured per-channel (see below), and join/quit broadcasts are controlled by
> `customJoinMessage` / `customQuitMessage`.

---

## Chat Channels

Channels are defined under `config.json` → `chat.channels` and are resolved (in order) by
explicit prefix, per-player channel state (set via channel-switch commands), then the channel
flagged `"default": true`, falling back to `"global"` if none matches.

```json
"channels": {
  "enabled": true,
  "global": {
    "enabled": true,
    "default": true
  },
  "local": {
    "enabled": true,
    "prefix": "!",
    "radius": 100
  },
  "staff": {
    "enabled": true,
    "prefix": "@",
    "permission": "neoessentials.chat.staff",
    "discord": {
      "enabled": true,
      "channelId": "123456789012345678"
    }
  }
}
```

| Key (per channel) | Description |
|---|---|
| `enabled` | Enable this channel |
| `prefix` | Message prefix that switches to this channel for a single message (e.g. `!hello`) |
| `default` | Marks the channel used when the player has no explicit channel and typed no prefix |
| `radius` | If set, makes the channel proximity-based (blocks); only players within `radius` blocks in the same dimension receive the message |
| `permission` | If set, only players holding this permission receive the message (and it gates Discord relay for the channel) |
| `discord.enabled` / `discord.channelId` | Relay this channel's messages to a specific Discord channel (see [Discord Integration](#discord-integration-simple-discord-link)) |

A channel with neither `radius` nor `permission` behaves as global chat. `chat.channels.enabled: false` disables the whole channel system (falls back to plain global chat).

---

## Chat Format Placeholders

| Placeholder | Value |
|---|---|
| `{prefix}` | Player's permission group prefix |
| `{suffix}` | Player's permission group suffix |
| `{name}` | Player's real username |
| `{displayname}` | Player's nickname or real name |
| `{message}` | The chat message content |
| `{world}` | Current world/dimension name |
| `{neoessentials_prefix}` | Alias for `{prefix}` |
| `{neoessentials_suffix}` | Alias for `{suffix}` |
| `{neoessentials_username}` | Alias for `{name}` |
| `{neoessentials_displayname}` | Alias for `{displayname}` |
| `{neoessentials_channel}` | The channel this message is being sent in — that channel's `displayName` if set (see [Chat Channels](ChatChannels)), else the raw channel key (`local`, `global`, `staff`, or any custom channel key from `chat.channels`) |
| `{MESSAGE}` | Alias for `{message}` (case-insensitive) |

> **`{displayname}`/`{neoessentials_displayname}` never includes the rank prefix/suffix** — it only
> ever resolves to the player's `/nick` nickname (if set) or their real username, nothing else.
> Use `{prefix}`/`{suffix}` explicitly in your template if you want the rank shown, as the shipped
> default does (`<{neoessentials_prefix} {neoessentials_displayname} {neoessentials_suffix}>`).
> Prior to build.45/build.46, `{displayname}` could fall back to the platform's raw
> `getDisplayName()` value, which — under a permissions plugin that formats names via vanilla
> scoreboard teams (LuckPerms does this on Forge/NeoForge) — already had the prefix baked in,
> doubling it up when combined with an explicit `{prefix}` token (e.g. `[Owner] [Owner] Name`).
> Fixed; no config changes needed.
>
> **A color code placed right before `{neoessentials_username}`/`{neoessentials_displayname}`
> now actually colors the name** (fixed in build.60). With `chat.clickablePlayerNames` enabled
> (the default), the name is built as its own component for its hover/click behavior — before
> build.60 this component always started uncolored, so e.g. `"&c{neoessentials_username}"`
> rendered the name in default white no matter what color preceded it, even though everything
> else in the template colored correctly. An explicit color inside the name itself (e.g. a
> colored `/nick`) still takes priority over a preceding template color, same as normal color
> code precedence.

### Tablist-Style Short Tokens

These tokens have no `{neoessentials_*}` equivalent — they were previously tablist/hologram-only,
but now also resolve in chat formats (per-player override, per-group format, or the global
`chat-format` default). Values reflect the **sending player's own context** — e.g. `{ping}`
(already available as `{neoessentials_ping}`) means the sender's own latency, since a broadcast
chat message has one sender but many recipients, unlike the tablist where each viewer sees their
own ping.

| Placeholder | Value |
|---|---|
| `{tps}` | Server TPS — auto-coloured green (≥19) / yellow (≥15) / red (<15) |
| `{online}` | Online player count (vanish-aware, from the sender's permission level) |
| `{max}` | Server max player slots |
| `{channel}` | Short alias for `{neoessentials_channel}` — the channel this message is being sent in |
| `{rank_weight}` | Sender's numeric permission group weight/priority |
| `{network_online}` | Total players on the proxy network (requires `tablist.proxy.enabled`; falls back to local `{online}` otherwise) |
| `{current_server}` | Proxy server name the sender is on (falls back to `tablist.proxy.serverLabel` if proxy is off) |
| `{server_label}` | This server's configured display label (`tablist.proxy.serverLabel`) |
| `{session_minutes}` | Total minutes elapsed in the sender's current session (not capped to 0–59 — pair with `{session_hours}`, same semantics as tablist) |
| `{session_hours}` | Full hours elapsed in the sender's current session |
| `{newline}` | Line break `\n` |
| `{bar}` | Decorative strikethrough separator (`&8&m──────────`) |

### Animations

`{animation:name}` tokens (defined in `animations.json`, the same ones usable in tablist
headers/footers) also resolve in chat formats — e.g. `{animation:network_pulse}` in a
per-group chat format. Animation frames advance on a global wall-clock timer independent of the
tablist system, so this works even if the tablist feature itself is disabled.

---

## Format Priority

When a chat message is sent, NeoEssentials selects the format using this priority order (highest to lowest):

```
1. Per-player override    (/chatformat set <player> <format>)
2. Per-group + per-world  (key: "group:admin:world:overworld")
3. Per-group              (key: "group:admin")
4. Per-world              (key: "world:overworld")
5. Default format         (key: "default")
```

---

## Color Codes in `chat-format`

Color codes go in the **value** (the format string), **not** in the key.
The key must remain a plain group identifier such as `group:fondateur`.

### Legacy `&` codes

| Code | Color/Effect |
|---|---|
| `&0`–`&9`, `&a`–`&f` | Standard Minecraft colors |
| `&l` `&m` `&n` `&o` `&k` | Bold / Strikethrough / Underline / Italic / Obfuscated |
| `&r` | Reset all formatting |

### Hex colors

```
&#RRGGBB
```

Example: `&#FF5500` for orange, `&#00FFCC` for mint.

### Per-group and per-world format examples

```json
"chat-format": {
  "default":                          "&f[&7Member&f] &f{neoessentials_username}&7: &f{MESSAGE}",
  "group:vip":                        "&f[&#FFD700VIP&f] &f{neoessentials_username}&7: &f{MESSAGE}",
  "group:moderateur":                 "&f[&cModérateur&f] &f{neoessentials_username}&7: &f{MESSAGE}",
  "group:fondateur":                  "&f[&4Fondateur&f] &f{neoessentials_username}&7: &f{MESSAGE}",
  "group:fondateur:world:overworld":  "&f[&4Fondateur&f|&aOverworld&f] &f{neoessentials_username}&7: &f{MESSAGE}",
  "world:the_nether":                 "&f[&6Nether&f] &f{neoessentials_username}&7: &f{MESSAGE}"
}
```

> **Note** – Before v1.0.2.7 there was a bug where `&` color codes in format strings were
> silently stripped when `enableChatEnhancements` was `true` (the default), causing all chat
> text to appear white. This is fixed in v1.0.2.7.

### Common mistakes to avoid

| ❌ Wrong | ✅ Correct |
|---|---|
| Color code in the **key**: `"group:&cFondateur"` | Keep the key as `"group:fondateur"` |
| Unicode escapes in the key: `"\u0026cgroup:fondateur"` | Color codes belong in the value string |
| Missing reset after colored text | Add `&f` (white) or `&r` (reset) after the group name |

> **Note** – Group keys are matched case-insensitively (`"group:VIP"` and `"group:vip"` both
> resolve the same player), so casing in the key never matters — write it however's readable.

---

## Chat Formatting with FTB Ranks

`"group:<name>"` keys work with **FTB Ranks** the same way they do with LuckPerms — `<name>`
is the **rank's id** (the key it's defined under in FTB Ranks' own rank config, e.g. a rank
block written as `SeasonedExplorer: { ... }` is matched by `"group:SeasonedExplorer"`, or
equally `"group:seasonedexplorer"` — matching is case-insensitive). If a player holds more than
one rank at once (FTB Ranks allows this, unlike LuckPerms' single primary group), the
**highest-power** rank is used.

```json
"chat-format": {
  "default":                "&7{neoessentials_username}&7: &f{MESSAGE}",
  "group:seasonedexplorer": "✈&bSeasoned Explorer&r✈&b {neoessentials_username}&7: &f{MESSAGE}"
}
```

### The `{ftbranks_prefix}` / `{ftbranks_suffix}` placeholders

FTB Ranks doesn't have separate "prefix" and "suffix" fields the way LuckPerms does — instead a
rank sets one `ftbranks.name_format` permission **value**, a template such as
`"✈&bSeasoned Explorer&r✈&b {name}"` that FTB Ranks substitutes `{name}` into to build the
player's styled name elsewhere (nameplate/tablist). NeoEssentials reads that same value and
splits it on the `{name}` token — everything before it becomes `{ftbranks_prefix}`, everything
after becomes `{ftbranks_suffix}` — so you can reuse a rank's existing `name_format` directly in
chat without redefining it:

```json
"default": "{ftbranks_prefix}{neoessentials_username}{ftbranks_suffix}&7: &f{MESSAGE}"
```

For the example rank above (`name_format: "✈&bSeasoned Explorer&r✈&b {name}"`), this resolves
to `{ftbranks_prefix}` = `"✈&bSeasoned Explorer&r✈&b "` and `{ftbranks_suffix}` = `""` (nothing
follows `{name}` in that template). A rank with no `ftbranks.name_format` set at all falls
through with empty prefix/suffix, same as a player with no LuckPerms meta configured.

`{ftbranks_rank}` / `{ftbranks_group}` (equivalent aliases) resolve to the same rank id used for
`"group:<name>"` key matching above.

---

## Per-Player Format Overrides

Admins can assign a completely custom chat format to any individual player. The per-player format takes the **highest priority** and overrides all group and world formats for that player.

### Commands

| Command | Permission | Description |
|---|---|---|
| `/chatformat set <player> <format>` | `neoessentials.chat.format.set` | Assign a custom format to a player |
| `/chatformat clear <player>` | `neoessentials.chat.format.set` | Remove the custom format (reverts to group/default) |
| `/chatformat check <player>` | `neoessentials.chat.format.set` | Show the active per-player override |
| `/chatformat list` | `neoessentials.chat.format.set` | List all currently active per-player overrides |
| `/chatformat reload` | `neoessentials.chat.format.set` | Reload per-player formats from disk |

### Examples

```
/chatformat set Notch &f[&#FFD700Owner&f] &6{neoessentials_username}&7: &f{MESSAGE}
/chatformat set Steve <gradient:ff0000-0000ff>{neoessentials_username}</gradient>&7: &f{MESSAGE}
/chatformat clear Notch
```

### Persistence

Per-player formats are saved to `config/neoessentials/player_chat_formats.json` and survive server restarts.

---

## Rich Text

When `richText.enabled` is `true`, the format string (and messages by players with the appropriate permission) can use advanced text effects.

### Gradients

```
<gradient:RRGGBB-RRGGBB>text</gradient>
```

| Example | Effect |
|---|---|
| `<gradient:ff0000-0000ff>text</gradient>` | Red → blue gradient |
| `<gradient:FFD700-FF8C00>VIP</gradient>` | Gold → dark-orange gradient |
| `<gradient:00c6ff-0072ff>text</gradient>` | Sky-blue gradient |

Gradients work in both format templates and in player messages (if the player has the `neoessentials.chat.namedcolors` permission — see [Player Message Colour Permissions](#player-message-colour-permissions)).

**Format template example with gradient prefix:**
```json
"group:vip": "<gradient:FFD700-FF8C00>[VIP]</gradient> &f{neoessentials_username}&7: &f{MESSAGE}"
```

> **Always close your `<gradient:...>` tags.** An unclosed `<gradient:...>` (no matching
> `</gradient>`) is treated as "gradient the rest of the line" — everything after the tag,
> including any `&`-color codes you meant to reset back to normal color (e.g. `&r`, `&8`), gets
> swallowed into the gradient region. Single legacy `&`-codes inside that region are passed
> through atomically rather than being shredded character-by-character, so this degrades
> gracefully, but closing the tag explicitly is still the clearest way to control exactly where
> a gradient starts and stops:
> ```
> <gradient:00FFC8-0080FF>&lGradiented Text</gradient>&r &8| &enormal text again
> ```

### Rainbow

```
<rainbow>text</rainbow>
```

Applies a cycling rainbow colour to each character.

### Hex colour span

```
<color:#RRGGBB>text</color>
```

Example: `<color:#00FFCC>Hello world</color>`

### Named colours

```
<red>text</red>    <gold>text</gold>    <aqua>text</aqua>    <white>text</white>
<dark_red>text</dark_red>   <dark_blue>text</dark_blue>   <yellow>text</yellow>
```

### Format tags

```
<bold>text</bold>               <italic>text</italic>
<underline>text</underline>     <strikethrough>text</strikethrough>
<obfuscated>text</obfuscated>
```

### Hover events

Show a tooltip when a player hovers their cursor over part of the message:

```
<hover:HOVER_TEXT>VISIBLE_TEXT</hover>
```

**Examples:**
```
<hover:Click to join our Discord!>[Discord]</hover>
<hover:This player is a donator!><gradient:FFD700-FF8C00>[VIP]</gradient></hover>
```

> **Tip** – Hover text is plain text only (no color codes inside the hover tooltip value).

### Click events

Make text clickable in chat:

```
<click:ACTION:VALUE>VISIBLE_TEXT</click>
```

| Action | Effect |
|---|---|
| `suggest_command` | Populates the player's chat bar with a command (does not run it) |
| `run_command` | Executes a command when clicked |
| `open_url` | Opens a URL in the player's browser |
| `copy_to_clipboard` | Copies the value to clipboard |

**Examples:**
```
<click:open_url:https://discord.gg/myserver>[Discord]</click>
<click:suggest_command:/help>[Help]</click>
<click:run_command:/spawn>[Spawn]</click>
```

### Combining hover + click

```
<hover:Visit our website!><click:open_url:https://example.com>[Website]</click></hover>
```

### Full format template examples

**VIP group with gradient prefix and hover tooltip:**
```json
"group:vip": "<hover:VIP Member><gradient:FFD700-FF8C00>[VIP]</gradient></hover> &f{neoessentials_username}&7: &f{MESSAGE}"
```

**Admin group with clickable rank badge:**
```json
"group:admin": "<hover:Server Administrator><click:suggest_command:/list>[&cAdmin&r]</click></hover> &f{neoessentials_username}&7: &f{MESSAGE}"
```

**Founder with rainbow name:**
```json
"group:fondateur": "[&4Fondateur&r] <rainbow>{neoessentials_username}</rainbow>&7: &f{MESSAGE}"
```

**Hex per-player format (set via /chatformat set):**
```
[&#FF5500Custom&r] &#FFD700{neoessentials_username}&7: &f{MESSAGE}
```

---

## Rank Badges & Status Icons

Config: `config.json` → `chat.badges` (split configs: `chat.json` → `chat.badges`). Independent
of `richText`/`enableChatEnhancements` — badges and status icons are inserted directly into the
format template text, not the `<tag>` rich-text system. This is the "badges" feature seen in
config — the working part is **emoji/text badges**; see the callout below for the custom-PNG
path, which isn't fully wired up yet.

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch for both rank badges and status icons |
| `badgePosition` | `"before_prefix"` | Where the rank badge is inserted: `before_prefix` \| `after_prefix` \| `before_name` \| `after_name` |
| `rankBadges` | *(per-group emoji map)* | Emoji/text badge shown for each permission group (looked up by the player's primary group, lowercased — this correctly checks LuckPerms/FTB Ranks first when one is active, same as every other group lookup in the mod). A group with no entry (or an empty string) gets no badge |
| `useCustomImages` | `false` | See [Custom PNG Badge Images](#custom-png-badge-images-current-limitations) below — **not fully functional yet** |
| `customImageSize` | `16` | Pixel size baked into the generated resource pack's font entries — `16`, `24`, or `32` |
| `customImagePath` | `"config/neoessentials/badges"` | Where the mod looks for `<rank>.png` files and writes the generated pack |
| `autoSendResourcePack` | `false` | See [Custom PNG Badge Images](#custom-png-badge-images-current-limitations) — does **not** actually push the pack to clients despite the name |
| `requireResourcePack` | `false` | Reserved for when auto-send is implemented — currently unused |
| `resourcePackUrl` | *(empty)* | Public URL to host the generated pack at, for manual `server.properties` setup — see below |
| `resourcePackPrompt` | *(default prompt text)* | Reserved for when auto-send is implemented — currently unused |
| `statusIcons.enabled` | `true` | Show AFK/vanished/muted status icons in chat |
| `statusIcons.iconPosition` | `"after_name"` | Where the status icon is inserted: `before_name` \| `after_name` \| `after_message` |
| `statusIcons.afk` | *(empty)* | Icon/text shown when the sender is AFK |
| `statusIcons.vanished` | *(empty)* | Icon/text shown when the sender is vanished (`/vanish`) |
| `statusIcons.muted` | *(empty)* | Icon/text shown when the sender is muted |
| `statusIcons.streaming` | *(empty)* | **Present in config but not currently checked by anything** — there's no "streaming" player status anywhere in the mod yet, so this key is a no-op regardless of value. Left as a placeholder for a future feature rather than removed. |

> **Set the actual icon text yourself.** Every `rankBadges`/`statusIcons` entry ships empty by
> default (except `rankBadges.admin`, which defaults to `⭐`) — pick your own emoji or `&`-coded
> text, e.g. `"vanished": "&7[Vanished]"`.

`badgePosition`/`iconPosition` work by inserting text next to the `{neoessentials_prefix}` /
`{neoessentials_username}`/`{neoessentials_name}`/`{neoessentials_displayname}` tokens in your
`chat-format` template — so a position that targets a token your template doesn't actually use
(e.g. `after_name` when your format only has `{neoessentials_displayname}`, never
`{neoessentials_username}`) won't show anything. `after_message` always works regardless of which
name token your template uses, since it just appends to the very end of the formatted line.

**Full example — an emoji badge per rank, plus a vanished icon:**
```json
"badges": {
  "enabled": true,
  "badgePosition": "before_prefix",
  "rankBadges": {
    "owner": "👑",
    "admin": "⭐",
    "moderator": "🛡️",
    "helper": "🔧",
    "vip": "💎",
    "default": ""
  },
  "statusIcons": {
    "enabled": true,
    "iconPosition": "after_name",
    "afk": "&7[AFK]",
    "vanished": "&7[Vanished]",
    "muted": "&c[Muted]"
  }
}
```
This is the entire setup — no resource pack, no restart-sensitive asset files, just edit the
JSON and `/neoe reload` (or restart). Any Unicode emoji or `&`-coded text string works.

### Custom PNG Badge Images — current limitations

`useCustomImages` is a genuinely bigger feature — swap the emoji for your own artwork — but as
shipped today only the first half of that pipeline is finished:

- ✅ **Works:** drop `<rank>.png` files into `config/neoessentials/badges/` (auto-created with a
  `README.txt` the first time the server starts with the `playerTags` module enabled — a
  separate toggle from `badges.enabled`, `modules.playerTagsEnabled`). With `useCustomImages: true`
  and `autoSendResourcePack: true`, the mod generates a real resource pack —
  `config/neoessentials/NeoEssentials-Badges.zip` plus a matching `.sha1` — mapping each PNG to
  a font glyph, on next server start.
- ❌ **Not implemented — the mod cannot push that pack to players.** Despite the name,
  `autoSendResourcePack` does not send anything: on player join the mod only logs the URL/hash
  and a reminder to configure it yourself. To actually deliver the pack you'd have to host the
  generated ZIP somewhere public and set vanilla's own `resource-pack`/`resource-pack-sha1` in
  `server.properties` — a manual, server-wide resource pack, unrelated to this mod's own
  delivery (which doesn't exist yet).
- ❌ **Even then, the custom image never actually appears in chat.** The chat badge shown is
  always the `rankBadges` emoji/text, regardless of `useCustomImages` — nothing in the current
  chat-formatting code inserts the generated pack's glyph character into a message. The
  resource pack it generates is real and structurally valid, but nothing in live chat renders
  it yet.

**In short: stick to `rankBadges` emoji/text for now.** `useCustomImages` is safe to leave off;
turning it on doesn't break anything, it just doesn't currently change what shows up in chat.

---

## Player Message Colour Permissions

Players must have the appropriate permissions to use color/formatting in their own chat *messages*:

| Permission | Effect |
|---|---|
| `neoessentials.chat.color` | Allow `&`-color codes in messages |
| `neoessentials.chat.color.hex` | Allow `&#RRGGBB` hex colors in messages |
| `neoessentials.chat.format` | Allow `&`-format codes (bold, italic, etc.) in messages |
| `neoessentials.chat.namedcolors` | Allow `<tag>`-style rich text (named colors, gradient, rainbow, hover, click) in messages |

> Format strings set by admins (in `config.json` or via `/chatformat set`) are **not** subject to these restrictions — they always render fully.
>
> `neoessentials.chat.richtext`, `neoessentials.chat.gradient`, and `neoessentials.chat.rainbow` are
> registered permission nodes (visible in `/permissions search`) but are not currently consulted
> anywhere in the chat pipeline — `neoessentials.chat.namedcolors` is the node that actually gates
> `<gradient>`/`<rainbow>`/`<hover>`/`<click>`/named-color tags in a player's own chat message.

---

## Commands

### Private Messaging

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/msg` | `/msg <player> <message>` | `neoessentials.chat.msg` | Send a private message |
| `/message`, `/tell`, `/pm`, `/w` | aliases | same | Aliases (there is no `/whisper` or `/m` alias) |
| `/reply` | `/reply <message>` | `neoessentials.chat.reply` | Reply to last private message |
| `/r` | alias | same | Alias |
| `/msgtoggle` | `/msgtoggle [on\|off]` | `neoessentials.chat.msgtoggle` | Toggle receiving private messages |
| `/rtoggle` | `/rtoggle [on\|off]` | `neoessentials.rtoggle` | Toggle receiving replies |
| `/socialspy` | `/socialspy [on\|off]` | `neoessentials.chat.socialspy` | Spy on all private messages |

### Ignore System

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/ignore` | `/ignore <player>` | `neoessentials.chat.ignore` | Ignore a player's messages |
| `/block` | alias | same | Alias for `/ignore` |
| `/unignore` | `/unignore <player>` | `neoessentials.chat.ignore` | Unignore a player |

> **No `/ignorelist` command exists.** There is no registered command to list your currently
> ignored players (`IgnoreManager.getIgnoreList()` exists internally but nothing in the command
> layer exposes it). Treat any reference to `/ignorelist` as stale/unverified until such a command
> is actually added.

> A player holding `neoessentials.chat.ignore.exempt` cannot be ignored. A player holding
> `neoessentials.chat.mute.exempt` cannot be muted with `/mute`.

---

## Discord Integration (Simple Discord Link)

When **Simple Discord Link** is installed, NeoEssentials relays chat to/from Discord **per
channel**, using the `discord` object nested inside that channel's entry under
`chat.channels` (see [Chat Channels](#chat-channels)):

```json
"channels": {
  "global": {
    "enabled": true,
    "default": true,
    "discord": {
      "enabled": true,
      "channelId": "123456789012345678",
      "webhookUrl": ""
    }
  }
}
```

| Key | Description |
|---|---|
| `discord.enabled` | Relay this channel's Minecraft chat to the given Discord channel |
| `discord.channelId` | Discord channel ID to relay to — used by SDLink/Mc2Discord/DCIntegration |
| `discord.webhookUrl` | Discord webhook URL to relay to — used by the built-in Generic Webhook relay (see below); no bridge mod required |

You can set `channelId`, `webhookUrl`, both, or neither — each is only read by the adapter(s) that
understand it, and setting both just means both fire independently for the same message.

Chat's own relay settings live under each channel, not a separate top-level section — but
non-chat events (join/leave/mute/AFK/advancement/private messages) aren't tied to any one
NeoEssentials channel, so they get their own top-level `discordEventChannels` section instead:

```json
"discordEventChannels": {
  "join":           { "enabled": true,  "channelId": "123456789012345678", "webhookUrl": "" },
  "leave":          { "enabled": true,  "channelId": "123456789012345678", "webhookUrl": "" },
  "mute":           { "enabled": true,  "channelId": "987654321098765432", "webhookUrl": "" },
  "afk":            { "enabled": false, "channelId": "", "webhookUrl": "" },
  "advancement":    { "enabled": false, "channelId": "", "webhookUrl": "" },
  "privateMessage": { "enabled": false, "channelId": "", "webhookUrl": "" }
}
```

Each entry works the same way as a chat channel's `discord.*` fields above —
`enabled: false` or a blank `channelId`/`webhookUrl` means "let whichever bridge mod is installed
route this event to its own natively-configured default channel instead," exactly as before this
section existed. Setting both lets you, for example, send joins/leaves to a `#server-log` channel
while keeping mutes in a private `#mod-log` channel, independent of chat's own per-channel
routing. Works the same way across SDLink/Mc2Discord for all six event types. **DCIntegration
supports every event through this explicit-channel-override path too** (as of build 67) —
join/leave/mute/AFK/advancement — but only ever additively: it never has a default-route
fallback for any of them, and for advancement specifically it's suppressing a real duplicate
(DCIntegration relays advancements to its own default channel natively on its own, independent
of NeoEssentials, whenever a player is linked and its `advancementMessage` template is set).

If a chat channel has a `permission` requirement, players without it are excluded from the
Discord relay as well as in-game delivery.

Works standalone (no relay) if none of SDLink/Mc2Discord/DCIntegration/a configured webhook is
present.

### Rich embeds for non-chat events (SDLink)

`discordEmbedTemplate` (see above) already builds a full styled embed for chat messages routed
to a specific Discord channel. As of build 65, the same treatment extends to join/leave/mute/
AFK/advancement events — each gets its own nested override with sensible built-in defaults:

```json
"discordEmbedTemplate": {
  "enabled": true,
  "authorName": "{player}",
  "...": "... (chat's own top-level fields, unchanged) ...",
  "join":        { "enabled": true, "description": "**{player}** joined the server", "color": "#57F287", "showTimestamp": true },
  "leave":       { "enabled": true, "description": "**{player}** left the server", "color": "#ED4245", "showTimestamp": true },
  "mute":        { "enabled": true, "description": "{message}", "color": "#FEE75C" },
  "afk":         { "enabled": true, "description": "{message}" },
  "advancement": { "enabled": true, "description": "**{player}** earned the advancement **{message}**", "color": "#FAA61A", "showTimestamp": true }
}
```

Only applies to a channel-override send (a specific `discordEventChannels.<event>.channelId`
configured) — same condition as chat's own embed. `{message}` means something different per
event: unused for join/leave, the full built status line for mute/AFK, and just the advancement's
name for advancement. Set an event's `enabled` to `false` to fall back to a plain text line for
just that event type. Currently SDLink-only — Mc2Discord/DCIntegration still send plain text for
these events.

### Generic Webhook relay (no bridge mod required)

Every `channelId` field above needs a real Discord bot (SDLink, Mc2Discord, or DCIntegration)
actually running on the server. If you don't want to run a bot at all, set the matching
**`webhookUrl`** field instead — a Discord webhook is a plain URL Discord itself generates per
channel (Server Settings → Integrations → Webhooks → New Webhook → Copy Webhook URL), and
NeoEssentials posts to it directly over HTTPS with no companion mod, bot token, or gateway
connection needed. This works even with zero of SDLink/Mc2Discord/DCIntegration installed.

Trade-offs versus a real bot-based relay: webhooks are one-way (Minecraft → Discord only — there's
no way to relay a Discord reply back into chat this way) and have no concept of linked
accounts/roles. Chat messages sent via webhook still impersonate the sending player (their name
and Minecraft-head avatar as the message's displayed sender), same as SDLink's own default look,
using Discord's per-message `username`/`avatar_url` override — you don't need to rename the
webhook itself per player.

A webhook added or changed via `/neoe reload` takes effect immediately — no server restart
needed, unlike a newly-installed bridge mod.

> **SDLink has its own native chat/join/leave/advancement broadcasters, independent of
> NeoEssentials.** SDLink's own config (`config/simple-discord-link/simple-discord-link.toml`,
> under `[chat]`) ships with `playerMessages`, `playerJoin`, `playerLeave`, and
> `advancementMessages` all **enabled by default** — each of these can post to Discord natively,
> completely separately from the equivalent event NeoEssentials also sends through the same
> SDLink bot. **As of build.59, NeoEssentials detects this automatically at startup and skips its
> own send for whichever event(s) SDLink's native config already handles** (as of build 64, via
> a real TOML parse of `simple-discord-link.toml` rather than a best-effort text scan) — a normal
> SDLink install no longer double-posts join/leave/advancement/default-route chat messages, with nothing
> to configure. A chat message routed to a specific per-channel Discord ID
> (`chat.channels.<name>.discord.channelId`) always still sends through NeoEssentials regardless,
> since that targets a channel SDLink's native relay never touches. NeoEssentials logs a startup
> warning naming which SDLink key(s) it detected as active/suppressed — if you'd rather
> NeoEssentials be the one formatting a given event instead of SDLink's native phrasing, set the
> corresponding key under `[chat]` in SDLink's own config to `false` (`advancementMessages` to
> `"NEVER"`) and restart.

> **Mc2Discord has the same kind of native relay, and gets the same conflict detection.** If any
> channel in `config/mc2discord.toml` is subscribed to `"chat"`/`"player_connect"`/
> `"player_disconnect"`/`"player_advancement"`, Mc2Discord relays that event to Discord on its
> own — **as of build 62, NeoEssentials detects this at startup and skips its own default-route
> send for that event type**, exactly like the SDLink handling above. A channel-override send
> (`discordChannelId` set) always still goes through regardless. Remove the relevant subscription
> from `mc2discord.toml` and restart if you'd rather NeoEssentials format that event instead.

### Rank prefix showing twice in Discord?

If a player's rank prefix appears doubled in Discord (e.g. `[Owner] [Owner] Name`) even though
it's correct in-game and in the tab-list, this is caused by a bridge mod's own **native** relay
(not anything NeoEssentials sends) — it independently reads `Player.getDisplayName()` (which
already carries whatever prefix NeoEssentials wrote onto the vanilla scoreboard team for the
nametag/tab-list, see [TablistSystem](TablistSystem.md)) and *also* separately re-resolves the
same rank prefix itself (SDLink does this via its own LuckPerms/FTBRanks integration) — stacking
both.

As of build 68, NeoEssentials' own Discord messages resolve the prefix independently — straight
from the permission system, the same source chat formatting already uses — instead of the
vanilla team, so they can never double no matter what the nametag/tab-list shows. Combined with
the native-relay conflict detection above (which suppresses NeoEssentials' own send once a
bridge mod's native relay is confirmed active for that event), the fix is:

1. Check the startup log (or the dashboard's Discord status panel) for a native-relay-conflict
   warning naming the event that's doubling.
2. Disable that specific native relay key in the bridge mod's own config (see the two notes
   above for SDLink/Mc2Discord's exact config keys) and restart.
3. NeoEssentials' own send — now with the correctly single-resolved prefix — becomes the sole
   source for that event, unaffected by whatever the vanilla team/nametag is doing.

The floating nametag itself can't be decoupled from the vanilla scoreboard team for a
server-only mod (this was investigated directly against Minecraft's own decompiled rendering
code) — `nametagSettings.enabled: false` is only relevant if you want a different mod/plugin to
own nametags entirely, not as a Discord workaround.

### Discord Role → Permission Group Sync

Separate from the chat/event relay above — see `discordrolesync.json`. Maps a Discord role's
snowflake ID to a NeoEssentials permission group:

```json
"discordRoleSync": {
  "enabled": false,
  "intervalSeconds": 300,
  "roleGroupMap": {
    "123456789012345678": "vip"
  }
}
```

When enabled, a linked player holding one of the mapped Discord roles is set to the matching
permission group (highest `priority` wins if they hold more than one mapped role). Re-checked
periodically (`intervalSeconds`) and immediately on join — this only ever grants/upgrades a
group, it never demotes a player back to default if they no longer hold a mapped role.

Requires NeoEssentials' own internal permission manager (not LuckPerms/FTB Ranks — this can't
write into an external plugin's groups) and **DCIntegration specifically** — it's currently the
only bridge mod this can read a linked player's Discord role list through (SDLink's role lookup
is a dead end without a public API for it; Mc2Discord isn't wired up yet).

---

## Data Files

Ignore lists and mutes are persisted through the pluggable **DataStore** backend (JSON by
default — see [Storage Backend](Storage)), not dedicated bespoke files:

| Collection / File | Contents |
|---|---|
| `ignore_lists` | Per-player ignore lists |
| `mutes` / `ip_mutes` | Active player mutes / IP mutes |
| `config/neoessentials/player_chat_formats.json` | Per-player format overrides (still a bespoke file, not DataStore-backed) |

> **Legacy files:** `neoessentials/ignore_data.json` and `neoessentials/muted_players.json` are
> the pre-DataStore on-disk formats. They're only read once, automatically, to migrate their
> contents into the collections above — never written to again afterward.

---

*Back to [Wiki Home](Home)*
