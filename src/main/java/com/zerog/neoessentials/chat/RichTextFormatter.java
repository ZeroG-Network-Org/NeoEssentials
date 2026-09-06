package com.zerog.neoessentials.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rich Text Formatter - Phase 4 + Chat Formatting Options
 *
 * Provides advanced text effects:
 * - Gradient text   → {@code <gradient:START-END>text</gradient>}
 * - Rainbow text    → {@code <rainbow>text</rainbow>}
 * - Named colors    → {@code <red>text</red>}, {@code <gold>text</gold>}, …
 * - Format codes    → {@code <bold>text</bold>}, {@code <italic>text</italic>}, …
 * - Hex color span  → {@code <color:#RRGGBB>text</color>}
 * - Hover events    → {@code <hover:HOVER_TEXT>VISIBLE</hover>}
 * - Click events    → {@code <click:suggest_command:/cmd>VISIBLE</click>}
 *                     Actions: suggest_command, run_command, open_url, copy_to_clipboard
 *
 * Named-color and format tags are converted to {@code &}-codes in the String preprocessing
 * phase (so they survive the URL/mention enhancement pipeline intact).
 * Hover/click events are wrapped with internal markers that
 * {@link com.zerog.neoessentials.chat.ChatFormatter}'s component builder recognises.
 */
public class RichTextFormatter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RichTextFormatter.class);

    // ── Gradient / Rainbow ────────────────────────────────────────────────────
    // Supports 2+ color stops: <gradient:FF0000-FFFF00-00FF00>text</gradient>
    // Each stop also tolerates an optional leading '#' (e.g. <gradient:#FF0000-#FFFF00>),
    // matching the '#' convention used by <color:#RRGGBB> — stripped before parsing in
    // processGradients()/sanitizeStops().
    private static final Pattern GRADIENT_PATTERN = Pattern.compile(
        "<gradient:(#?[0-9a-fA-F]{6}(?:-#?[0-9a-fA-F]{6})+)>(.*?)</gradient>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RAINBOW_PATTERN = Pattern.compile(
        "<rainbow>(.*?)</rainbow>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // Fallback for a <gradient:...> tag with no matching </gradient> — very common when
    // the gradient is meant to cover an entire line (header/footer, a full sign line, etc.)
    // with nothing after it to mark where the gradient "ends". Applied only to whatever's
    // left after GRADIENT_PATTERN has already consumed every properly-closed occurrence,
    // and colors everything from the tag to the end of the string.
    private static final Pattern GRADIENT_UNCLOSED_PATTERN = Pattern.compile(
        "<gradient:(#?[0-9a-fA-F]{6}(?:-#?[0-9a-fA-F]{6})+)>(.*)$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // ── Named-color close tags (stripped) ────────────────────────────────────
    private static final Pattern CLOSE_COLOR_TAG_PATTERN = Pattern.compile(
        "</(black|dark_blue|dark_green|dark_aqua|dark_cyan|dark_red|dark_purple|gold|"
        + "gray|grey|dark_gray|dark_grey|blue|green|aqua|cyan|red|light_purple|pink|yellow|white|"
        + "color)>",
        Pattern.CASE_INSENSITIVE);

    // ── <color:#RRGGBB>…</color> ──────────────────────────────────────────────
    private static final Pattern COLOR_HEX_TAG_PATTERN = Pattern.compile(
        "<color:#([0-9a-fA-F]{6})>",
        Pattern.CASE_INSENSITIVE);

    // ── Format tags with closing counterparts ─────────────────────────────────
    // Closing format tags are stripped; Minecraft has no "close-bold" code.
    private static final Pattern CLOSE_FORMAT_TAG_PATTERN = Pattern.compile(
        "</(bold|b|italic|i|underline|underlined|u|strikethrough|s|obfuscated|magic|reset|r)>",
        Pattern.CASE_INSENSITIVE);

    // ── Hover/Click event tags (format-template level) ────────────────────────
    // <hover:HOVER_TEXT>VISIBLE</hover>
    private static final Pattern HOVER_TAG_PATTERN = Pattern.compile(
        "<hover:([^>]+)>(.*?)</hover>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // <click:ACTION:VALUE>VISIBLE</click>
    private static final Pattern CLICK_TAG_PATTERN = Pattern.compile(
        "<click:(suggest_command|run_command|open_url|copy_to_clipboard):([^>]+)>(.*?)</click>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Internal markers used to communicate hover/click events through the String pipeline */
    public static final String HOVER_OPEN  = "§HOVS§";
    public static final String HOVER_SEP   = "§HOVE§";
    public static final String HOVER_CLOSE = "§HOVEND§";
    public static final String CLICK_OPEN  = "§CLKS§";
    public static final String CLICK_SEP   = "§CLKV§";
    public static final String CLICK_CLOSE = "§CLKEND§";

    // ── Player-name hover marker (injected by ChatFormatter) ──────────────────
    public static final String PLAYER_HOVER_OPEN  = "§PLAYERHOVER§";
    public static final String PLAYER_HOVER_CLOSE = "§/PLAYERHOVER§";

    // Rainbow color spectrum (HSV based)
    private static final int[] RAINBOW_COLORS = {
        0xFF0000, 0xFF7F00, 0xFFFF00, 0x00FF00, 0x0000FF, 0x4B0082, 0x9400D3
    };

    // ==========================================================================
    // Public API
    // ==========================================================================

    /**
     * Pre-process all rich-text tags into either {@code &}-codes or internal markers,
     * returning the result as a plain {@code String} so that {@code &} color codes
     * are not yet consumed.
     *
     * <p>Processed:
     * <ul>
     *   <li>Gradient / rainbow → {@code &#RRGGBB} per-character</li>
     *   <li>Named color tags → {@code &X}</li>
     *   <li>Format tags (bold/italic/…) → {@code &l} / {@code &o} / …</li>
     *   <li>{@code <color:#RRGGBB>} → {@code &#RRGGBB}</li>
     *   <li>{@code <hover:…>…</hover>} → internal markers</li>
     *   <li>{@code <click:…:…>…</click>} → internal markers</li>
     * </ul>
     * </p>
     *
     * @param text input string; may contain any of the above tags plus {@code &} codes
     * @return processed string with tags replaced by codes/markers
     */
    @SuppressWarnings("unused") // Called from ChatFormatter.formatMessage
    public static String preprocessTags(String text) {
        try {
            if (isRichTextEnabled()) {
                // Rich text ON — convert all tag syntax to & codes / internal markers
                if (isGradientAllowed()) text = processGradients(text);
                if (isRainbowAllowed()) text = processRainbow(text);
                text = processNamedColorTags(text);
                text = processFormatTags(text);
                text = processColorHexTags(text);
                text = processHoverTags(text);
                text = processClickTags(text);
            } else {
                // Rich text OFF — strip all <tag> syntax without converting.
                // Legacy & codes from the format template are left intact so
                // template colours still render; only XML-style tags are removed.
                text = stripAllRichTags(text);
            }
            return text;
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.CHAT, "Error pre-processing rich text tags", e);
            return text;
        }
    }

    /**
     * Process rich text for tablist headers/footers.
     * <p>
     * Unlike {@link #processRichText(String)}, this method <em>always</em> enables
     * gradient and rainbow processing regardless of the {@code chat.richText.enabled}
     * config setting. Hover/click markers are stripped because the tablist packet
     * does not support interactive components.
     *
     * <p>Supported syntax (same as chat rich text):
     * <ul>
     *   <li>{@code <gradient:RRGGBB-RRGGBB>text</gradient>} — 2- or multi-stop gradient</li>
     *   <li>{@code <rainbow>text</rainbow>} — cycling rainbow</li>
     *   <li>{@code <red>text</red>}, {@code <gold>}, … — named colors</li>
     *   <li>{@code <bold>text</bold>}, {@code <italic>}, … — format tags</li>
     *   <li>{@code <color:#RRGGBB>text</color>} — arbitrary hex color span</li>
     *   <li>{@code &#RRGGBB} — inline hex color</li>
     *   <li>{@code &X} — legacy Minecraft color/format codes</li>
     * </ul>
     *
     * @param text raw frame text (after placeholder substitution)
     * @return fully colored Minecraft {@link Component}
     */
    public static Component processTablistText(String text) {
        try {
            // Resolve {animation:name} tokens first (so an animated frame's own &/gradient
            // syntax still gets processed below). Safe to call unconditionally — it's a no-op
            // when the text contains no "{animation:" token, and idempotent if a caller (e.g.
            // TablistManager's header/footer builder) already resolved animations upstream —
            // this is what lets {animation:...} work in permission-group prefixes/suffixes,
            // fake-player names, and hologram text, not just header/footer frames.
            text = com.zerog.neoessentials.tablist.AnimationManager.getInstance().resolveAnimations(text);
            // Gradient and rainbow are ALWAYS enabled for tablist
            text = processGradients(text);
            text = processRainbow(text);
            text = processNamedColorTags(text);
            text = processFormatTags(text);
            text = processColorHexTags(text);
            // Tablist cannot render hover/click events — strip them cleanly
            text = stripHoverClickMarkers(text);
            return com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(text);
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.CHAT, "Error processing tablist text", e);
            try {
                return com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(text);
            } catch (Exception e2) {
                return Component.literal(text);
            }
        }
    }

    /**
     * Resolves {@code {animation:NAME}} tokens to their current frame, then gradients and
     * rainbow, into plain {@code &}-coded/hex text — nothing else (no named-color tags, format
     * tags, or hover/click markers are touched).
     *
     * <p>Meant to be layered in <em>front of</em> a caller's own existing
     * {@code ChatComponentUtil.parseColorCodes(...)} call, not as a replacement for it — e.g.
     * {@link com.zerog.neoessentials.util.MessageUtil}'s {@code success}/{@code error}/{@code
     * warning}/{@code info}/{@code component} builders call this on the raw message text before
     * their own existing {@code parseColorCodes(text, baseStyle)} call, so every command reply
     * mod-wide gains {@code {animation:...}}/gradient/rainbow support (this was the actual bug
     * behind "a crate's `{animation:...}` display name shows up literally in chat when you
     * right-click it" — {@code parseColorCodes} on its own only ever understood {@code &}-codes
     * and hex, never animation tokens or gradients) without changing anything about those
     * methods' existing color-inheritance/base-style behavior, which a wholesale swap to {@link
     * #processTablistText} would have risked.
     *
     * <p>A sent chat message can't be "animated" the way a tablist/scoreboard/hologram line can
     * — those are continuously re-sent to a live packet channel; a chat message is one line
     * appended to an append-only log, gone the instant it's delivered, with no channel to ever
     * update it again. So this — like the crate key item name/lore fix — only ever resolves a
     * <em>snapshot</em> of whichever frame is current the moment the message is actually sent,
     * not something that visibly animates in someone's chat log afterward. There is no
     * equivalent of tablist/scoreboard's configurable {@code refreshInterval} to add here for
     * that reason — it wouldn't have anything to apply to.
     */
    /**
     * Unconditionally processes {@code <gradient:...>}/{@code <rainbow>} syntax — {@code
     * chat.richText.enabled}/{@code allowGradients}/{@code allowRainbow} gate a PLAYER typing
     * raw tag syntax directly into their own message (a deliberate spam/performance control,
     * checked later by {@link #preprocessTags}), not admin-authored content that already made
     * it into the message via {@code {animation:NAME}} resolution — that should render exactly
     * like tablist/hologram animation frames do, which have always processed gradients/rainbow
     * unconditionally (see {@link #processTablistText}). Without this, an animation frame
     * containing {@code <gradient:...>} silently showed up stripped/literal in chat on any
     * server that hadn't separately turned richText.enabled on, even though the exact same
     * animation rendered correctly in the tablist/hologram it was also used in.
     */
    public static String processAnimationFrameGradients(String text) {
        text = processGradients(text);
        text = processRainbow(text);
        return text;
    }

    public static String resolveDynamicTags(String text) {
        if (text == null) return null;
        try {
            text = com.zerog.neoessentials.tablist.AnimationManager.getInstance().resolveAnimations(text);
            text = processGradients(text);
            text = processRainbow(text);
            return text;
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.CHAT, "Error resolving dynamic (animation/gradient/rainbow) tags", e);
            return text;
        }
    }

    /**
     * Process rich text formatting tags and convert to colored Component.
     * Used when {@code enableChatEnhancements} is {@code false}.
     * <p>
     * NOTE: expects a string that has <em>already</em> been through
     * {@link #preprocessTags(String)}, so all tag conversion / stripping has
     * already happened.  This method only needs to call {@code parseColorCodes}
     * to convert the remaining {@code &}-codes to a Component.
     */
    public static Component processRichText(String text) {
        try {
            // Tags have already been handled by preprocessTags().
            // Strip any hover/click markers that cannot be rendered without the
            // enhancement component builder, then parse & colour codes.
            text = stripHoverClickMarkers(text);
            return com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(text);
        } catch (Exception e) {
            NeoLog.error(LOGGER, LogCategory.CHAT, "Error processing rich text", e);
            try {
                return com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(text);
            } catch (Exception e2) {
                return Component.literal(text);
            }
        }
    }

    // ==========================================================================
    // Named-color tags → & codes
    // ==========================================================================

    /**
     * Convert named-color open-tags to their {@code &X} equivalents and strip
     * matching close-tags (Minecraft formatting has no close concept).
     */
    static String processNamedColorTags(String text) {
        // Color name → & code mapping
        text = replaceTag(text, "black",        "&0");
        text = replaceTag(text, "dark_blue",    "&1");
        text = replaceTag(text, "dark_green",   "&2");
        text = replaceTag(text, "dark_aqua",    "&3");
        text = replaceTag(text, "dark_cyan",    "&3");
        text = replaceTag(text, "dark_red",     "&4");
        text = replaceTag(text, "dark_purple",  "&5");
        text = replaceTag(text, "gold",         "&6");
        text = replaceTag(text, "gray",         "&7");
        text = replaceTag(text, "grey",         "&7");
        text = replaceTag(text, "dark_gray",    "&8");
        text = replaceTag(text, "dark_grey",    "&8");
        text = replaceTag(text, "blue",         "&9");
        text = replaceTag(text, "green",        "&a");
        text = replaceTag(text, "aqua",         "&b");
        text = replaceTag(text, "cyan",         "&b");
        text = replaceTag(text, "red",          "&c");
        text = replaceTag(text, "light_purple", "&d");
        text = replaceTag(text, "pink",         "&d");
        text = replaceTag(text, "yellow",       "&e");
        text = replaceTag(text, "white",        "&f");
        // Strip all matching close-color tags
        text = CLOSE_COLOR_TAG_PATTERN.matcher(text).replaceAll("");
        return text;
    }

    /** Convert format open-tags to {@code &}-codes; strip close-tags. */
    static String processFormatTags(String text) {
        text = replaceTag(text, "bold",        "&l");
        text = replaceTag(text, "b",           "&l");
        text = replaceTag(text, "italic",      "&o");
        text = replaceTag(text, "i",           "&o");
        text = replaceTag(text, "underline",   "&n");
        text = replaceTag(text, "underlined",  "&n");
        text = replaceTag(text, "u",           "&n");
        text = replaceTag(text, "strikethrough", "&m");
        text = replaceTag(text, "s",           "&m");
        text = replaceTag(text, "obfuscated",  "&k");
        text = replaceTag(text, "magic",       "&k");
        text = replaceTag(text, "reset",       "&r");
        text = replaceTag(text, "r",           "&r");
        // Strip close format tags
        text = CLOSE_FORMAT_TAG_PATTERN.matcher(text).replaceAll("");
        return text;
    }

    /** Convert {@code <color:#RRGGBB>} to {@code &#RRGGBB} and strip {@code </color>}. */
    static String processColorHexTags(String text) {
        Matcher m = COLOR_HEX_TAG_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("&#" + m.group(1).toUpperCase()));
        }
        m.appendTail(sb);
        return sb.toString().replace("</color>", "");
    }

    // ==========================================================================
    // Hover / Click event tags → internal markers
    // ==========================================================================

    /**
     * Convert {@code <hover:HOVER_TEXT>VISIBLE</hover>} to
     * {@code §HOVS§HOVER_TEXT§HOVE§VISIBLE§HOVEND§}.
     */
    static String processHoverTags(String text) {
        Matcher m = HOVER_TAG_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hoverText = m.group(1);
            String visible   = m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                HOVER_OPEN + hoverText + HOVER_SEP + visible + HOVER_CLOSE));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Convert {@code <click:ACTION:VALUE>VISIBLE</click>} to
     * {@code §CLKS§ACTION§CLKV§VALUE§CLKEND§VISIBLE§CLKE§}.
     *
     * <p>Supported actions: {@code suggest_command}, {@code run_command},
     * {@code open_url}, {@code copy_to_clipboard}.</p>
     */
    static String processClickTags(String text) {
        Matcher m = CLICK_TAG_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String action  = m.group(1).toLowerCase();
            String value   = m.group(2);
            String visible = m.group(3);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                CLICK_OPEN + action + CLICK_SEP + value + CLICK_CLOSE + visible + "§CLKE§"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Strip hover/click markers — used when we can't render them as Components. */
    static String stripHoverClickMarkers(String text) {
        // Remove hover markers
        text = text.replace(HOVER_OPEN,  "").replace(HOVER_SEP, "").replace(HOVER_CLOSE, "");
        // Remove click markers
        text = text.replace(CLICK_OPEN,  "").replace(CLICK_SEP, "").replace(CLICK_CLOSE, "")
                   .replace("§CLKE§", "");
        // Remove player-hover markers
        text = text.replace(PLAYER_HOVER_OPEN, "").replace(PLAYER_HOVER_CLOSE, "");
        return text;
    }

    // ==========================================================================
    // Gradient / Rainbow
    // ==========================================================================

    private static String processGradients(String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String stopsRaw = matcher.group(1);   // e.g. "FF0000-FFFF00-00FF00" (stops may have a leading '#')
            String content  = matcher.group(2);
            String[] stops  = sanitizeStops(stopsRaw.split("-"));
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                createMultiStopGradient(content, stops)));
        }
        matcher.appendTail(result);
        text = result.toString();

        // Anything left with an unclosed <gradient:...> tag (no </gradient>) — treat the
        // rest of the string as the gradient's content instead of leaving the tag literal.
        Matcher unclosed = GRADIENT_UNCLOSED_PATTERN.matcher(text);
        if (unclosed.find()) {
            String stopsRaw = unclosed.group(1);
            String content  = unclosed.group(2);
            String[] stops  = sanitizeStops(stopsRaw.split("-"));
            text = text.substring(0, unclosed.start())
                + createMultiStopGradient(content, stops);
        }
        return text;
    }

    /** Strips an optional leading '#' from each gradient stop (e.g. "#FF0000" → "FF0000"). */
    private static String[] sanitizeStops(String[] stops) {
        for (int i = 0; i < stops.length; i++) {
            if (stops[i].startsWith("#")) stops[i] = stops[i].substring(1);
        }
        return stops;
    }

    /**
     * All legacy {@code &x} code characters — colors ({@code 0-9a-f}) and formats
     * ({@code k l m n o r}) — that a gradient should pass through untouched instead
     * of shredding into individually-colored characters.
     */
    private static final String LEGACY_CODE_CHARS = "0123456789abcdefklmnor";

    /**
     * Returns {@code true} if {@code text.charAt(i)} starts a legacy {@code &x} code
     * (color or format) that should be emitted as-is rather than treated as a
     * "visible" character to gradient-color. This matters most for unclosed
     * {@code <gradient:...>} tags, which swallow the rest of the line as content —
     * any manual {@code &}-color codes further down the line (e.g. {@code &8}, {@code &r})
     * would otherwise get split into two separately-colored characters, corrupting them.
     */
    private static boolean isFormatCodeAt(String text, int i) {
        if (text.charAt(i) != '&' || i + 1 >= text.length()) return false;
        return LEGACY_CODE_CHARS.indexOf(Character.toLowerCase(text.charAt(i + 1))) >= 0;
    }

    /**
     * Length of one of {@link ChatFormatter}'s internal marker tags ({@code §HNAME§},
     * {@code §/HNAME§}, {@code §ITEM§}, etc.) if {@code text} starts with one at {@code i}, or
     * {@code 0} if not.
     *
     * <p>A chat-format template wrapping {@code {neoessentials_username}} in
     * {@code <gradient:...>}/{@code <rainbow>} (e.g. {@code
     * "<gradient:9D00FF-FF00AA>{neoessentials_username}</gradient>"}) has, by the time gradient/
     * rainbow processing runs, already had that placeholder replaced with
     * {@code §HNAME§<name>§/HNAME§} — {@link ChatFormatter#formatMessage} injects the marker
     * before resolving any other placeholders or tags. Without this check, {@link
     * #createMultiStopGradient}/{@link #createRainbow} treated every character of that marker
     * (the literal {@code §}, and each letter of "HNAME") as ordinary visible text to color
     * individually, same as {@code isFormatCodeAt} already protects {@code &}-codes from — which
     * shredded the marker into fragments {@link ChatFormatter#buildComponentFromMarkup} could no
     * longer recognize as a contiguous {@code §HNAME§...§/HNAME§} span, so it fell through to
     * literal plain text instead of becoming an invisible clickable-name component (reported as
     * the player's name literally showing "HNAME&lt;name&gt;/HNAME" in chat). Skipping the whole
     * tag atomically here — the same treatment `&`-format codes already get — keeps it intact for
     * {@code buildComponentFromMarkup} to find later, while the actual name text between the open
     * and close tags still gets gradient/rainbow-colored normally.
     */
    private static int markerTagLengthAt(String text, int i) {
        if (text.charAt(i) != '§') return 0;
        for (String marker : ChatFormatter.INTERNAL_MARKUP_MARKERS) {
            if (text.regionMatches(i, marker, 0, marker.length())) {
                return marker.length();
            }
        }
        return 0;
    }

    /**
     * Creates a per-character gradient string supporting 2+ color stops.
     * Spaces are passed through without coloring to preserve word separation.
     * {@code &l}/{@code &o}/etc. format codes are passed through as an atomic 2-character
     * unit (not split into two individually-colored characters, which would corrupt the
     * code — {@code &l} would otherwise become "&" and "l" each with their own {@code &#HEX}
     * prefix inserted between them) and don't count toward the gradient's visible-length.
     */
    private static String createMultiStopGradient(String text, String[] stops) {
        if (text.isEmpty() || stops.length == 0) return text;
        if (stops.length == 1) {
            // Solid color
            String hex = stops[0].toUpperCase();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                int markerLen = markerTagLengthAt(text, i);
                if (markerLen > 0) {
                    sb.append(text, i, i + markerLen);
                    i += markerLen - 1;
                    continue;
                }
                if (isFormatCodeAt(text, i)) {
                    sb.append(text, i, i + 2);
                    i++;
                    continue;
                }
                char c = text.charAt(i);
                if (c == ' ') { sb.append(c); continue; }
                sb.append("&#").append(hex).append(c);
            }
            return sb.toString();
        }

        // Count non-space, non-format-code, non-marker-tag characters for interpolation
        int visibleLen = 0;
        for (int i = 0; i < text.length(); i++) {
            int markerLen = markerTagLengthAt(text, i);
            if (markerLen > 0) { i += markerLen - 1; continue; }
            if (isFormatCodeAt(text, i)) { i++; continue; }
            if (text.charAt(i) != ' ') visibleLen++;
        }
        if (visibleLen == 0) return text;

        int segmentCount = stops.length - 1;
        StringBuilder sb = new StringBuilder();
        int visibleIdx = 0;
        for (int i = 0; i < text.length(); i++) {
            int markerLen = markerTagLengthAt(text, i);
            if (markerLen > 0) {
                sb.append(text, i, i + markerLen);
                i += markerLen - 1;
                continue;
            }
            if (isFormatCodeAt(text, i)) {
                sb.append(text, i, i + 2);
                i++;
                continue;
            }
            char c = text.charAt(i);
            if (c == ' ') { sb.append(c); continue; }

            float globalP = visibleLen > 1 ? (float) visibleIdx / (visibleLen - 1) : 0f;
            // Which segment does this character fall in?
            float scaled = globalP * segmentCount;
            int seg = Math.min((int) scaled, segmentCount - 1);
            float segP = scaled - seg;

            int startColor = Integer.parseInt(stops[seg], 16);
            int endColor   = Integer.parseInt(stops[seg + 1], 16);
            int sR = (startColor >> 16) & 0xFF, sG = (startColor >> 8) & 0xFF, sB = startColor & 0xFF;
            int eR = (endColor   >> 16) & 0xFF, eG = (endColor   >> 8) & 0xFF, eB = endColor   & 0xFF;
            int r = (int)(sR + (eR - sR) * segP);
            int g = (int)(sG + (eG - sG) * segP);
            int b = (int)(sB + (eB - sB) * segP);
            sb.append("&#").append(String.format("%02X%02X%02X", r, g, b)).append(c);
            visibleIdx++;
        }
        return sb.toString();
    }

    // Keep the legacy 2-stop variant for any direct internal use
    private static String createGradient(String text, String startHex, String endHex) {
        return createMultiStopGradient(text, new String[]{startHex, endHex});
    }

    private static String processRainbow(String text) {
        Matcher matcher = RAINBOW_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(createRainbow(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String createRainbow(String text) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        int colorIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            // See markerTagLengthAt()'s doc comment — same §HNAME§-shredding risk as the
            // gradient colorer above applies here for <rainbow>{neoessentials_username}</rainbow>.
            int markerLen = markerTagLengthAt(text, i);
            if (markerLen > 0) {
                sb.append(text, i, i + markerLen);
                i += markerLen - 1;
                continue;
            }
            char c = text.charAt(i);
            if (c == ' ') { sb.append(c); continue; }
            int color = RAINBOW_COLORS[colorIndex % RAINBOW_COLORS.length];
            sb.append("&#").append(String.format("%06X", color)).append(c);
            colorIndex++;
        }
        return sb.toString();
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    /** Replace {@code <tagName>} with {@code replacement} (case-insensitive). */
    private static String replaceTag(String text, String tagName, String replacement) {
        return text.replace("<" + tagName + ">",        replacement)
                   .replace("<" + tagName.toUpperCase() + ">", replacement)
                   .replace("<" + tagName.toLowerCase() + ">", replacement);
    }

    /**
     * Build a hover-event Component from pre-processed hover markers.
     * Expected format: {@code §HOVS§hoverText§HOVE§visibleText§HOVEND§}
     */
    public static MutableComponent buildHoverComponent(String hoverText, String visibleText) {
        Component hover = com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(hoverText);
        MutableComponent visible = (MutableComponent)
            com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(visibleText);
        return visible.withStyle(style ->
            style.withHoverEvent(com.zerog.neoessentials.util.HoverEventCompat.create(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /**
     * Build a click-event Component from pre-processed click markers.
     * Expected action values: suggest_command, run_command, open_url, copy_to_clipboard.
     */
    public static MutableComponent buildClickComponent(String action, String value, String visibleText) {
        MutableComponent visible = (MutableComponent)
            com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(visibleText);
        ClickEvent.Action clickAction = switch (action.toLowerCase()) {
            case "run_command"        -> ClickEvent.Action.RUN_COMMAND;
            case "open_url"           -> ClickEvent.Action.OPEN_URL;
            case "copy_to_clipboard"  -> ClickEvent.Action.COPY_TO_CLIPBOARD;
            default                   -> ClickEvent.Action.SUGGEST_COMMAND;
        };
        return visible.withStyle(Style.EMPTY.withClickEvent(com.zerog.neoessentials.util.ClickEventCompat.create(clickAction, value)));
    }

    private static boolean isRichTextEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("richText")) {
                return chatConfig.getAsJsonObject("richText").get("enabled").getAsBoolean();
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CHAT, "Error reading richText.enabled, defaulting to false", e);
        }
        return false;
    }

    private static boolean isGradientAllowed() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("richText") && chatConfig.getAsJsonObject("richText").has("allowGradients")) {
                return chatConfig.getAsJsonObject("richText").get("allowGradients").getAsBoolean();
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CHAT, "Error reading richText.allowGradients, defaulting to true", e);
        }
        return true;
    }

    private static boolean isRainbowAllowed() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("richText") && chatConfig.getAsJsonObject("richText").has("allowRainbow")) {
                return chatConfig.getAsJsonObject("richText").get("allowRainbow").getAsBoolean();
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CHAT, "Error reading richText.allowRainbow, defaulting to true", e);
        }
        return true;
    }

    /**
     * Strip ALL XML-style rich text tags from {@code text} without converting them.
     * Used when {@code richText.enabled = false} so that tags are removed cleanly
     * but legacy {@code &}-codes from the format template are left intact.
     *
     * <p>For container tags (gradient, rainbow, hover, click) the inner visible
     * text is preserved; open/close simple tags are just removed.</p>
     */
    static String stripAllRichTags(String text) {
        // Container tags — keep inner text
        text = GRADIENT_PATTERN.matcher(text).replaceAll("$2");
        // Unclosed <gradient:...> (no </gradient>, e.g. meant to cover the rest of the line) —
        // strip the tag but keep the trailing text, same fallback as processGradients().
        text = GRADIENT_UNCLOSED_PATTERN.matcher(text).replaceAll("$2");
        text = RAINBOW_PATTERN.matcher(text).replaceAll("$1");
        text = HOVER_TAG_PATTERN.matcher(text).replaceAll("$2");
        text = CLICK_TAG_PATTERN.matcher(text).replaceAll("$3");
        // Named-color open and close tags
        text = processNamedColorTagsStrip(text);
        // Format open and close tags
        text = processFormatTagsStrip(text);
        // <color:#RRGGBB> and </color>
        text = COLOR_HEX_TAG_PATTERN.matcher(text).replaceAll("");
        text = text.replace("</color>", "");
        // Strip any internal hover/click markers left over
        text = stripHoverClickMarkers(text);
        return text;
    }

    /** Remove named-color open tags and their close counterparts without replacing. */
    private static String processNamedColorTagsStrip(String text) {
        for (String tag : new String[]{
            "black","dark_blue","dark_green","dark_aqua","dark_cyan","dark_red","dark_purple",
            "gold","gray","grey","dark_gray","dark_grey","blue","green","aqua","cyan",
            "red","light_purple","pink","yellow","white"}) {
            text = text.replace("<" + tag + ">", "").replace("</" + tag + ">", "")
                       .replace("<" + tag.toUpperCase() + ">", "").replace("</" + tag.toUpperCase() + ">", "");
        }
        text = CLOSE_COLOR_TAG_PATTERN.matcher(text).replaceAll("");
        return text;
    }

    /** Remove format open tags and their close counterparts without replacing. */
    private static String processFormatTagsStrip(String text) {
        for (String tag : new String[]{
            "bold","b","italic","i","underline","underlined","u",
            "strikethrough","s","obfuscated","magic","reset","r"}) {
            text = text.replace("<" + tag + ">", "").replace("</" + tag + ">", "")
                       .replace("<" + tag.toUpperCase() + ">", "").replace("</" + tag.toUpperCase() + ">", "");
        }
        text = CLOSE_FORMAT_TAG_PATTERN.matcher(text).replaceAll("");
        return text;
    }
}

