package com.zerog.neoessentials.chat;

import net.minecraft.server.level.ServerPlayer;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * ChatFormatter handles chat message formatting with proper color code support.
 * Supports both legacy (&) and section (§) color codes, plus hex colors (&#RRGGBB).
 * Phase 2 Enhancements:
 * - Clickable URLs (auto-detection)
 * - @mention system with sound notifications
 * - [item] links showing held items
 */
public class ChatFormatter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatFormatter.class);
    
    // Pre-compiled regex patterns for performance
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern AMPERSAND_CODE_PATTERN = Pattern.compile("&([0-9a-fk-or])");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-f])");
    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("&([k-or])");
    // Named-color / format tags (stripped from player messages without permission)
    private static final Pattern NAMED_TAG_PATTERN = Pattern.compile(
        "<(black|dark_blue|dark_green|dark_aqua|dark_cyan|dark_red|dark_purple|gold|"
        + "gray|grey|dark_gray|dark_grey|blue|green|aqua|cyan|red|light_purple|pink|yellow|white|"
        + "bold|b|italic|i|underline|underlined|u|strikethrough|s|obfuscated|magic|reset|r|color"
        + "|hover|click|gradient|rainbow)([^>]*)>",
        Pattern.CASE_INSENSITIVE);
    
    // Phase 2: Enhancement patterns
    @SuppressWarnings("RegExpDuplicateCharacterInClass") // Period in char class is intentional
    private static final Pattern URL_PATTERN = Pattern.compile(
        "\\b(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%.]+)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]{3,16})\\b");
    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[item]", Pattern.CASE_INSENSITIVE);
    /**
     * Formats a chat message using the provided template and player context.
     */
    public static Component formatMessage(String template, ServerPlayer player, String message) {
        return formatMessage(template, player, message, null);
    }

    /**
     * Formats a chat message using the provided template and player context.
     *
     * @param resolvedChannel the channel THIS message actually routed to (may differ from the
     *                        player's persistent channel state due to a one-off prefix override,
     *                        e.g. typing {@code @text} to hit staff without switching channels) —
     *                        used to resolve {@code {channel}}. Pass {@code null} to fall back to
     *                        {@link ChatHandler#getEffectiveChannel} (the player's persistent channel).
     */
    public static Component formatMessage(String template, ServerPlayer player, String message, String resolvedChannel) {
        try {
            boolean debugEnabled = com.zerog.neoessentials.logging.NeoLog.isDebugEnabled(com.zerog.neoessentials.logging.LogCategory.CHAT);

            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "=== CHAT FORMATTING DEBUG ===");
                NeoLog.info(LOGGER, LogCategory.CHAT, "Player: {}, OP: {}", player.getName().getString(), com.zerog.neoessentials.util.PermissionLevelCompat.hasPermission(player, 2));
                NeoLog.info(LOGGER, LogCategory.CHAT, "Original message: [{}]", message);
                NeoLog.info(LOGGER, LogCategory.CHAT, "Template: [{}]", template);
            }

            // Normalize placeholders to new format
            String normalizedTemplate = normalizePlaceholders(template);
            if (debugEnabled) {
                NeoLog.debug(LOGGER, LogCategory.CHAT, "After normalization: {}", normalizedTemplate);
            }

            // Phase 3: Apply badges and icons to template.
            // MUST run before the clickable-name marker injection below — BadgeManager's
            // "before_name"/"after_name" icon-position logic works by string-replacing the
            // literal {neoessentials_username}/{neoessentials_displayname} tokens, and once
            // those tokens are replaced with §HNAME§/§HDNAME§ markers, that replace() call
            // silently finds nothing to match. Since chat.clickablePlayerNames defaults to
            // true and statusIcons' default iconPosition is "after_name", this ordering used
            // to make AFK/vanished/muted status icons a no-op by default for anyone using
            // clickable names — rank badges only survived because their default position
            // (before_prefix) never touches the username token in the first place.
            normalizedTemplate = BadgeManager.getInstance().applyBadgesAndIcons(player, normalizedTemplate);
            if (debugEnabled) {
                NeoLog.debug(LOGGER, LogCategory.CHAT, "After badges/icons: {}", normalizedTemplate);
            }

            // Inject clickable player-name markers when both features are enabled.
            // We substitute the placeholder with an internal §HNAME§/§HDNAME§ marker
            // so that buildComponentFromMarkup() can create proper hover+click Components.
            // When enhancements are disabled the placeholder is left alone and resolved
            // to plain text by PlaceholderAPI below.
            if (isClickablePlayerNamesEnabled() && isChatEnhancementsEnabled()) {
                String uname = player.getName().getString();
                // Use the player's nickname for displayname hover, falling back to the raw name —
                // NOT getDisplayName(), which (e.g. under LuckPerms' vanilla team-based name
                // formatting) already has the group prefix/suffix baked in, doubling up with the
                // template's own {neoessentials_prefix}/{neoessentials_suffix} placeholders. See
                // DefaultPlaceholderExpansion.getNickOrDisplayName() for the same fix.
                String nickRaw = com.zerog.neoessentials.util.commands.NickCommand.getNickname(player.getUUID());
                String dname = (nickRaw != null && !nickRaw.isEmpty())
                    ? nickRaw.replace("&", "§")
                    : uname;
                normalizedTemplate = normalizedTemplate
                    .replace("{neoessentials_username}", "§HNAME§" + uname + "§/HNAME§")
                    .replace("{neoessentials_displayname}", "§HDNAME§" + dname + "§/HDNAME§");
            }

            // Strip any literal occurrences of our internal markup markers from the raw
            // player message BEFORE anything else touches it — see stripInjectedMarkupMarkers().
            String sanitizedMessage = stripInjectedMarkupMarkers(message);

            // Restrict colors in message BEFORE inserting into template
            String restrictedMessage = restrictPlayerMessageColors(sanitizedMessage, player);
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "After color restriction: [{}]", restrictedMessage);
            }

            // Directly replace {MESSAGE} before PlaceholderAPI processing
            String preFormatted = normalizedTemplate.replace("{MESSAGE}", restrictedMessage);
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "After message insertion: [{}]", preFormatted);
            }

            // Resolve all other placeholders via PlaceholderAPI
            String formatted = com.zerog.neoessentials.api.PlaceholderAPI.setPlaceholders(player, preFormatted);
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "After placeholder resolution: [{}]", formatted);
            }

            // Tablist-style short-form tokens ({tps}, {online}, {animation:name}, etc.) that
            // have no {neoessentials_*} equivalent — lets tablist/hologram snippets be reused in chat.
            formatted = resolveShortPlaceholders(formatted, player, resolvedChannel);
            formatted = com.zerog.neoessentials.tablist.AnimationManager.getInstance().resolveAnimations(formatted);
            // Gradients/rainbow that came IN via an animation frame are admin-authored content
            // (from animations.json), not something the player typed — render them the same way
            // tablist/hologram already do, regardless of chat.richText.enabled (that config only
            // gates a player's own raw <gradient:...> typed directly, handled later below by
            // preprocessTags()). Without this an animation using gradients showed up stripped in
            // chat on any server that hadn't separately opted into richText.enabled.
            formatted = RichTextFormatter.processAnimationFrameGradients(formatted);
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "After short-form placeholders/animations: [{}]", formatted);
            }

            // Phase 4: Apply conditional formatting
            formatted = ConditionalFormatter.processConditionals(player, formatted);
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "After conditional formatting: [{}]", formatted);
            }

            // Clean up formatting
            formatted = cleanupFormatting(formatted);
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "After cleanup: [{}]", formatted);
            }

            // Phase 4: Pre-process rich text tags.
            // When richText.enabled=true  → converts <tag> syntax to & codes / internal markers.
            // When richText.enabled=false → strips <tag> syntax, leaving legacy & codes intact.
            if (debugEnabled) {
                boolean richTextOn = isRichTextEnabled();
                NeoLog.info(LOGGER, LogCategory.CHAT, "Rich text enabled: {}", richTextOn);
            }
            String richPreProcessed = RichTextFormatter.preprocessTags(formatted);
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "After rich text pre-processing: [{}]", richPreProcessed);
            }

            // Apply Phase 2 enhancements if enabled
            Component result;
            if (isChatEnhancementsEnabled()) {
                // Pass the string with & color codes intact; buildComponentFromMarkup will
                // call parseColorCodes on each plain-text segment so all & and &#RRGGBB
                // codes are honoured correctly.
                result = enhanceMessage(richPreProcessed, player, player.level().getServer());
            } else {
                // No enhancements — richPreProcessed already has tags stripped/converted;
                // use it directly instead of re-processing the original formatted string.
                result = RichTextFormatter.processRichText(richPreProcessed);
            }

            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, "=== END CHAT FORMATTING DEBUG ===");
            }
            return result;

        } catch (Exception e) {
            LOGGER.error("Failed to format chat message for player {}: {}",
                player.getName().getString(), e.getMessage(), e);
            // Fallback
            return MessageUtil.component("commands.neoessentials.chat.fallback_format", player.getName().getString(), message);
        }
    }

    // Literal marker sequences buildComponentFromMarkup() treats as its own internal
    // syntax for interactive components. § is an ordinary character in a chat packet —
    // nothing stops a player from sending one of these literally — so they must be
    // stripped from raw player input before it ever reaches processItemLinks/markupUrls/
    // markupMentions, or a forged marker would be rendered as a real, trusted-looking
    // clickable/hoverable component (e.g. impersonating another player's clickable name).
    // Package-private (not private) so RichTextFormatter's gradient/rainbow character-by-character
    // colorers (same package) can skip over these atomically too — see their use there.
    static final String[] INTERNAL_MARKUP_MARKERS = {
        "§ITEM§", "§/ITEM§", "§URL§", "§/URL§", "§MENTION§", "§/MENTION§",
        "§HNAME§", "§/HNAME§", "§HDNAME§", "§/HDNAME§"
    };

    private static String stripInjectedMarkupMarkers(String message) {
        String result = message;
        for (String marker : INTERNAL_MARKUP_MARKERS) {
            result = result.replace(marker, "");
        }
        return result;
    }

    /**
     * Restrict color codes in player's message based on config and permissions.
     * Returns the message with disallowed color codes removed.
     */
    private static String restrictPlayerMessageColors(String message, ServerPlayer player) {
        UUID uuid = player.getUUID();
        String result = message;
        boolean debugEnabled = com.zerog.neoessentials.logging.NeoLog.isDebugEnabled(com.zerog.neoessentials.logging.LogCategory.CHAT);

        if (debugEnabled) {
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>> Restricting colors for player {} (UUID: {})", player.getName().getString(), uuid);
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>> Original message: [{}]", message);
        }

        // First check if color codes are enabled globally in config
        boolean colorCodesEnabled = com.zerog.neoessentials.config.ConfigManager.isColorCodesEnabled();
        if (debugEnabled) {
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>> Config enable-color-codes: {}", colorCodesEnabled);
        }

        if (!colorCodesEnabled) {
            // Strip ALL color codes if disabled in config
            result = HEX_PATTERN.matcher(result).replaceAll("");
            result = AMPERSAND_CODE_PATTERN.matcher(result).replaceAll("");
            if (debugEnabled) {
                NeoLog.info(LOGGER, LogCategory.CHAT, ">>> Color codes DISABLED in config - Stripped all codes: [{}]", result);
            }
            return result;
        }
        
        // Config allows colors, now check permissions
        boolean hasHexPerm = PermissionAPI.hasPermission(uuid, "neoessentials.chat.color.hex");
        boolean hasColorPerm = PermissionAPI.hasPermission(uuid, "neoessentials.chat.color");
        boolean hasFormatPerm = PermissionAPI.hasPermission(uuid, "neoessentials.chat.format");
        
        if (debugEnabled) {
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>> Permission Check Results:");
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>>   - neoessentials.chat.color.hex: {}", hasHexPerm);
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>>   - neoessentials.chat.color: {}", hasColorPerm);
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>>   - neoessentials.chat.format: {}", hasFormatPerm);
        }

        if (!hasHexPerm) {
            if (debugEnabled) {
                String before = result;
                result = HEX_PATTERN.matcher(result).replaceAll("");
                NeoLog.info(LOGGER, LogCategory.CHAT, ">>>   Stripped hex codes: [{}] -> [{}]", before, result);
            } else {
                result = HEX_PATTERN.matcher(result).replaceAll("");
            }
        }
        
        if (!hasColorPerm) {
            if (debugEnabled) {
                String before = result;
                result = COLOR_CODE_PATTERN.matcher(result).replaceAll("");
                NeoLog.info(LOGGER, LogCategory.CHAT, ">>>   Stripped color codes: [{}] -> [{}]", before, result);
            } else {
                result = COLOR_CODE_PATTERN.matcher(result).replaceAll("");
            }
        }
        
        if (!hasFormatPerm) {
            if (debugEnabled) {
                String before = result;
                result = FORMAT_CODE_PATTERN.matcher(result).replaceAll("");
                NeoLog.info(LOGGER, LogCategory.CHAT, ">>>   Stripped format codes: [{}] -> [{}]", before, result);
            } else {
                result = FORMAT_CODE_PATTERN.matcher(result).replaceAll("");
            }
        }

        // Strip named color/format tags (e.g. <red>, <bold>) unless player has appropriate perm
        boolean hasNamedTagPerm = PermissionAPI.hasPermission(uuid, "neoessentials.chat.namedcolors");
        if (!hasNamedTagPerm) {
            // Also strip hover/click tags (those always require namedcolors perm in player messages)
            result = NAMED_TAG_PATTERN.matcher(result).replaceAll("");
            // Strip matching close-tags
            result = result.replaceAll("</(\\w+)>", "");
        }
        
        if (debugEnabled) {
            NeoLog.info(LOGGER, LogCategory.CHAT, ">>> Final restricted message: [{}]", result);
        }
        return result;
    }
    
    /**
     * Parse text with color codes to Minecraft Component.
     * Supports: §/& color codes (0-9, a-f), format codes (k-o, r), and hex (&#RRGGBB)
     * Note: This method is kept for fallback scenarios when ChatEnhancer is disabled.
     */
    @SuppressWarnings("unused") // Used as fallback when enhancements disabled
    private static Component parseToComponent(String text) {
        MutableComponent result = Component.empty();
        
        // First convert & to § for uniform processing (using pre-compiled pattern)
        text = AMPERSAND_CODE_PATTERN.matcher(text).replaceAll("§$1");
        
        // Handle hex colors: &#RRGGBB -> RGB color
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (hexMatcher.find()) {
            try {
                String hex = hexMatcher.group(1);
                // Replace with placeholder that we'll process later
                hexMatcher.appendReplacement(sb, "§#" + hex + "§");
            } catch (Exception e) {
                hexMatcher.appendReplacement(sb, "");
            }
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();
        
        // Now parse the text character by character, building Components
        StringBuilder currentText = new StringBuilder();
        net.minecraft.network.chat.Style currentStyle = net.minecraft.network.chat.Style.EMPTY;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c == '§' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                
                // Handle hex color: §#RRGGBB§
                if (code == '#' && i + 8 < text.length() && text.charAt(i + 8) == '§') {
                    // Flush current text
                    if (!currentText.isEmpty()) {
                        result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                        currentText = new StringBuilder();
                    }
                    
                    try {
                        String hex = text.substring(i + 2, i + 8);
                        int rgb = Integer.parseInt(hex, 16);
                        currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
                    } catch (Exception e) {
                        // Ignore invalid hex
                    }
                    i += 8; // Skip the hex color code
                    continue;
                }
                
                // Handle standard color codes
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting != null) {
                    // Flush current text
                    if (!currentText.isEmpty()) {
                        result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                        currentText = new StringBuilder();
                    }
                    
                    // Apply the formatting
                    if (formatting == ChatFormatting.RESET) {
                        currentStyle = net.minecraft.network.chat.Style.EMPTY;
                    } else if (formatting.isColor()) {
                        currentStyle = net.minecraft.network.chat.Style.EMPTY.applyFormat(formatting);
                    } else {
                        // Format codes (bold, italic, etc)
                        currentStyle = currentStyle.applyFormat(formatting);
                    }
                    
                    i++; // Skip the code character
                    continue;
                }
            }
            
            currentText.append(c);
        }
        
        // Append any remaining text
        if (!currentText.isEmpty()) {
            result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
        }
        
        return result;
    }
    
    /**
     * Resolves tablist-style short-form tokens that have no {@code {neoessentials_*}}
     * equivalent, so header/footer-style snippets can be copy-pasted into chat formats.
     * Values reflect the sending player's own context (e.g. {@code {ping}} is the
     * sender's latency — there's only one recipient-agnostic broadcast, unlike tablist
     * where each viewer sees their own ping).
     *
     * <p>Tokens already covered by {@code {neoessentials_*}} PlaceholderAPI expansions
     * (prefix/suffix/group/balance/ping/world/x/y/z/level/health/afk/time/server_name/server_motd)
     * are intentionally NOT duplicated here — this only fills the gap.
     */
    private static String resolveShortPlaceholders(String text, ServerPlayer player, String resolvedChannel) {
        if (text.indexOf('{') < 0) return text;
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) return text;

        if (text.contains("{tps}")) {
            double tps = com.zerog.neoessentials.tablist.TablistManager.getInstance().getTps(server);
            String tpsStr = tps >= 19.0 ? "&a" + String.format("%.1f", tps)
                          : tps >= 15.0 ? "&e" + String.format("%.1f", tps)
                          : "&c" + String.format("%.1f", tps);
            text = text.replace("{tps}", tpsStr);
        }
        if (text.contains("{online}")) {
            int online = com.zerog.neoessentials.tablist.TablistManager.getInstance()
                .countOnlineExcludingVanish(server, player);
            text = text.replace("{online}", String.valueOf(online));
        }
        if (text.contains("{max}")) {
            text = text.replace("{max}", String.valueOf(server.getMaxPlayers()));
        }
        if (text.contains("{channel}")) {
            String channel = resolvedChannel != null ? resolvedChannel : ChatHandler.getEffectiveChannel(player.getUUID());
            text = text.replace("{channel}", ChatHandler.getChannelDisplayName(channel));
        }
        if (text.contains("{rank_weight}")) {
            int weight = com.zerog.neoessentials.tablist.TablistManager.getInstance().getGroupWeight(player);
            text = text.replace("{rank_weight}", String.valueOf(weight));
        }
        if (text.contains("{network_online}") || text.contains("{current_server}") || text.contains("{server_label}")) {
            var proxy = com.zerog.neoessentials.tablist.ProxyIntegration.getInstance();
            int networkOnline = proxy.isProxyEnabled() ? proxy.getNetworkOnline()
                : com.zerog.neoessentials.tablist.TablistManager.getInstance().countOnlineExcludingVanish(server, player);
            String currentServer = proxy.isProxyEnabled() ? proxy.getPlayerServer(player.getUUID()) : proxy.getServerLabel();
            text = text.replace("{network_online}", String.valueOf(networkOnline))
                       .replace("{current_server}", currentServer)
                       .replace("{server_label}", proxy.getServerLabel());
        }
        if (text.contains("{session_minutes}") || text.contains("{session_hours}")) {
            var tablist = com.zerog.neoessentials.tablist.TablistManager.getInstance();
            text = text.replace("{session_minutes}", String.valueOf(tablist.getSessionMinutes(player.getUUID())))
                       .replace("{session_hours}", String.valueOf(tablist.getSessionHours(player.getUUID())));
        }
        text = text.replace("{newline}", "\n").replace("{bar}", "&8&m──────────");
        return text;
    }

    /**
     * Convert legacy uppercase placeholders to lowercase format.
     */
    private static String normalizePlaceholders(String template) {
        return template
            .replace("{DISPLAYNAME}", "{neoessentials_displayname}")
            .replace("{USERNAME}", "{neoessentials_username}")
            .replace("{PREFIX}", "{neoessentials_prefix}")
            .replace("{SUFFIX}", "{neoessentials_suffix}")
            .replace("{GROUP}", "{neoessentials_group}")
            .replace("{WORLD}", "{neoessentials_world}")
            .replace("{X}", "{neoessentials_x}")
            .replace("{Y}", "{neoessentials_y}")
            .replace("{Z}", "{neoessentials_z}")
            .replace("{HEALTH}", "{neoessentials_health}")
            .replace("{LEVEL}", "{neoessentials_level}")
            .replace("{BALANCE}", "{neoessentials_balance}")
            .replace("{GAMEMODE}", "{neoessentials_gamemode}")
            .replace("{BIOME}", "{neoessentials_biome}")
            .replace("{AFK}", "{neoessentials_afk}")
            .replace("{PING}", "{neoessentials_ping}")
            .replace("{SERVER_NAME}", "{neoessentials_server_name}")
            .replace("{SERVER_MOTD}", "{neoessentials_server_motd}")
            .replace("{ONLINE_PLAYERS}", "{neoessentials_online_players}")
            .replace("{MAX_PLAYERS}", "{neoessentials_max_players}");
    }
    
    /**
     * Clean up extra spaces from empty prefixes/suffixes.
     */
    private static String cleanupFormatting(String formatted) {
        formatted = formatted.replaceAll("\\s+", " ");
        formatted = formatted.replaceAll("< >", "");
        formatted = formatted.replaceAll("<\\s+", "<");
        formatted = formatted.replaceAll("\\s+>", ">");
        return formatted.trim();
    }
    
    /**
     * Validate if a format template is well-formed.
     */
    @SuppressWarnings("unused") // Public API method - may be used by other plugins/mods
    public static boolean isValidTemplate(String template) {
        if (template == null || template.trim().isEmpty()) {
            return false;
        }
        
        // Check balanced braces
        int openBraces = 0;
        for (char c : template.toCharArray()) {
            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            if (openBraces < 0) return false;
        }
        
        return openBraces == 0;
    }
    
    /**
     * Get the default chat format template.
     */
    @SuppressWarnings("unused") // Public API method - may be used by other plugins/mods
    public static String getDefaultFormat() {
        return "{neoessentials_prefix}{neoessentials_displayname}{neoessentials_suffix}: {MESSAGE}";
    }

    // ============================================================
    // Phase 2: Interactive Chat Enhancements
    // ============================================================

    /**
     * Check if chat enhancements are enabled in config.
     */
    private static boolean isChatEnhancementsEnabled() {
        try {
            return com.zerog.neoessentials.config.ConfigManager.getInstance()
                .getConfig("chat").get("enableChatEnhancements").getAsBoolean();
        } catch (Exception e) {
            return true; // Default enabled
        }
    }

    /**
     * Enhance a formatted message with interactive components.
     */
    private static Component enhanceMessage(String formattedMessage, ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        try {
            // Process [item] links first
            String processed = processItemLinks(formattedMessage, player);
            // Then build interactive component with URLs and mentions
            return buildInteractiveComponent(processed, player, server);
        } catch (Exception e) {
            LOGGER.error("Error enhancing chat message: {}", e.getMessage(), e);
            return com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(formattedMessage);
        }
    }

    /**
     * Process [item] placeholders and replace with item name.
     */
    private static String processItemLinks(String message, ServerPlayer player) {
        if (!isItemLinksEnabled()) {
            return message;
        }

        Matcher matcher = ITEM_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            var mainHandItem = player.getMainHandItem();
            if (!mainHandItem.isEmpty()) {
                String itemName = mainHandItem.getHoverName().getString();
                matcher.appendReplacement(result, "§ITEM§" + itemName + "§/ITEM§");
            } else {
                matcher.appendReplacement(result, "[Empty Hand]");
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Build interactive component with clickable URLs, mentions, and item links.
     */
    private static Component buildInteractiveComponent(String text, ServerPlayer sender, net.minecraft.server.MinecraftServer server) {
        String processed = text;

        // Process URLs first
        if (isUrlDetectionEnabled()) {
            processed = markupUrls(processed);
        }

        // Then process mentions
        if (isMentionsEnabled()) {
            processed = markupMentions(processed, sender, server);
        }

        // Build final component from marked-up text
        return buildComponentFromMarkup(processed, sender);
    }

    /**
     * Markup URLs for later component creation.
     */
    private static String markupUrls(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String url = matcher.group(1);
            matcher.appendReplacement(result, "§URL§" + url + "§/URL§");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Markup mentions and play sounds.
     */
    private static String markupMentions(String text, ServerPlayer sender, net.minecraft.server.MinecraftServer server) {
        if (isMentionPermissionRequired()
                && !com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), getMentionPermission())) {
            return text;
        }

        Matcher matcher = MENTION_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            ServerPlayer mentioned = server.getPlayerList().getPlayerByName(mentionedName);

            if (mentioned != null) {
                matcher.appendReplacement(result, "§MENTION§" + mentionedName + "§/MENTION§");

                // Play sound if enabled and not self-mention
                if (isMentionSoundEnabled() && !mentioned.getUUID().equals(sender.getUUID())) {
                    playMentionSound(mentioned);
                }
            } else {
                matcher.appendReplacement(result, "@" + mentionedName);
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Build final component from marked-up text.
     */
    private static Component buildComponentFromMarkup(String markup, ServerPlayer sender) {
        MutableComponent result = Component.empty();
        int index = 0;
        // Tracks the color/format style still active at the current position — e.g. a template
        // like "&c{neoessentials_username}" needs the clickable-name component (built as its own
        // sibling, not inline text) to know red is "active" here, since appended sibling
        // components never inherit a preceding sibling's color on their own. Updated after every
        // plain-text segment; carries forward unchanged through non-text markers (items/urls/
        // mentions/names), same as how a color code stays active in the template string itself.
        net.minecraft.network.chat.Style ambientStyle = net.minecraft.network.chat.Style.EMPTY;

        while (index < markup.length()) {
            // Check for ITEM marker
            int itemStart = markup.indexOf("§ITEM§", index);
            if (itemStart == index) {
                int itemEnd = markup.indexOf("§/ITEM§", itemStart);
                if (itemEnd != -1) {
                    String itemName = markup.substring(itemStart + 6, itemEnd);
                    result.append(createItemComponent(itemName, sender));
                    index = itemEnd + 7;
                    continue;
                }
            }

            // Check for URL marker
            int urlStart = markup.indexOf("§URL§", index);
            if (urlStart == index) {
                int urlEnd = markup.indexOf("§/URL§", urlStart);
                if (urlEnd != -1) {
                    String url = markup.substring(urlStart + 5, urlEnd);
                    result.append(com.zerog.neoessentials.util.ChatComponentUtil.createClickableUrl(url, url, "Click to open in browser\n" + url));
                    index = urlEnd + 6;
                    continue;
                }
            }

            // Check for MENTION marker
            int mentionStart = markup.indexOf("§MENTION§", index);
            if (mentionStart == index) {
                int mentionEnd = markup.indexOf("§/MENTION§", mentionStart);
                if (mentionEnd != -1) {
                    String playerName = markup.substring(mentionStart + 9, mentionEnd);
                    result.append(createMentionComponent(playerName));
                    index = mentionEnd + 10;
                    continue;
                }
            }

            // Check for HNAME (clickable username) marker
            int hnameStart = markup.indexOf("§HNAME§", index);
            if (hnameStart == index) {
                int hnameEnd = markup.indexOf("§/HNAME§", hnameStart);
                if (hnameEnd != -1) {
                    String name = markup.substring(hnameStart + 7, hnameEnd);
                    result.append(createClickablePlayerNameComponent(name, sender, ambientStyle));
                    index = hnameEnd + 8;
                    continue;
                }
            }

            // Check for HDNAME (clickable displayname) marker
            int hdnameStart = markup.indexOf("§HDNAME§", index);
            if (hdnameStart == index) {
                int hdnameEnd = markup.indexOf("§/HDNAME§", hdnameStart);
                if (hdnameEnd != -1) {
                    String name = markup.substring(hdnameStart + 8, hdnameEnd);
                    result.append(createClickablePlayerNameComponent(name, sender, ambientStyle));
                    index = hdnameEnd + 9;
                    continue;
                }
            }

            // Find next marker
            int nextMarker = markup.length();
            int[] markers = {
                markup.indexOf("§ITEM§", index),
                markup.indexOf("§URL§", index),
                markup.indexOf("§MENTION§", index),
                markup.indexOf("§HNAME§", index),
                markup.indexOf("§HDNAME§", index)
            };

            for (int m : markers) {
                if (m != -1 && m < nextMarker) nextMarker = m;
            }

            // Add plain text segment
            if (nextMarker > index) {
                String plainText = markup.substring(index, nextMarker);
                result.append(com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(plainText));
                ambientStyle = com.zerog.neoessentials.util.ChatComponentUtil.getTrailingStyle(plainText);
                index = nextMarker;
            } else {
                break;
            }
        }

        return result;
    }

    /**
     * Create item component using ChatComponentUtil as base.
     */
    private static Component createItemComponent(String itemName, ServerPlayer player) {
        var mainHandItem = player.getMainHandItem();

        MutableComponent component = Component.literal("[" + itemName + "]")
            .withStyle(ChatFormatting.AQUA)
            .withStyle(ChatFormatting.UNDERLINE);

        if (!mainHandItem.isEmpty()) {
            component.setStyle(component.getStyle()
                .withHoverEvent(com.zerog.neoessentials.util.HoverEventCompat.create(HoverEvent.Action.SHOW_ITEM,
                    mainHandItem))
            );
        }

        return component;
    }

    /**
     * Create mention component using consistent styling.
     */
    private static Component createMentionComponent(String playerName) {
        ChatFormatting color = getMentionColor();

        return Component.literal("@" + playerName)
            .withStyle(color)
            .withStyle(ChatFormatting.BOLD)
            .withStyle(style -> style
                .withClickEvent(com.zerog.neoessentials.util.ClickEventCompat.create(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + playerName + " "))
                .withHoverEvent(com.zerog.neoessentials.util.HoverEventCompat.create(HoverEvent.Action.SHOW_TEXT,
                    ((MutableComponent) MessageUtil.component("commands.neoessentials.chat.click_to_message", playerName)).withStyle(ChatFormatting.GRAY)))
            );
    }

    /**
     * Create a clickable player-name component (hover = player info, click = /msg).
     * Used when the {@code clickablePlayerNames} config option is enabled.
     *
     * @param ambientStyle the color/format style active in the template immediately before this
     *                     marker (see {@code buildComponentFromMarkup}) — applied as the name's
     *                     base style so e.g. {@code "&c{neoessentials_username}"} actually renders
     *                     red, since this component is appended as its own sibling and would
     *                     otherwise default to no color regardless of a preceding color code.
     *                     Any color code inside {@code displayText} itself (e.g. a colored
     *                     nickname) still overrides this, same as normal color-code precedence.
     */
    private static Component createClickablePlayerNameComponent(String displayText, ServerPlayer player, net.minecraft.network.chat.Style ambientStyle) {
        Component base = com.zerog.neoessentials.util.ChatComponentUtil.parseColorCodes(displayText, ambientStyle);
        // Wrap into a MutableComponent so we can attach events
        MutableComponent comp = Component.empty().append(base);
        comp.withStyle(style -> style
            .withClickEvent(com.zerog.neoessentials.util.ClickEventCompat.create(ClickEvent.Action.SUGGEST_COMMAND,
                "/msg " + player.getName().getString() + " "))
            .withHoverEvent(com.zerog.neoessentials.util.HoverEventCompat.create(HoverEvent.Action.SHOW_TEXT,
                ((MutableComponent) MessageUtil.component("commands.neoessentials.chat.click_to_message_icon", player.getName().getString()))
                    .withStyle(ChatFormatting.GRAY)))
        );

        // A Style can only carry one ClickEvent, and the name itself already carries the
        // SUGGEST_COMMAND "/msg" action above — so the "view profile" link is a separate,
        // adjacent component (a small icon) rather than replacing that behavior.
        if (isProfileLinkInChatEnabled()) {
            String profileUrl = com.zerog.neoessentials.config.ConfigManager.getPlayerProfileUrl(player.getName().getString(), player.getUUID());
            if (profileUrl != null) {
                MutableComponent linkIcon = Component.literal(" ↗").withStyle(style -> style
                    .withColor(ChatFormatting.BLUE)
                    .withClickEvent(com.zerog.neoessentials.util.ClickEventCompat.create(ClickEvent.Action.OPEN_URL, profileUrl))
                    .withHoverEvent(com.zerog.neoessentials.util.HoverEventCompat.create(HoverEvent.Action.SHOW_TEXT,
                        ((MutableComponent) MessageUtil.component("commands.neoessentials.chat.click_to_view_profile", player.getName().getString()))
                            .withStyle(ChatFormatting.GRAY)))
                );
                comp.append(linkIcon);
            }
        }

        return comp;
    }

    /**
     * Play mention sound to a player.
     */
    private static void playMentionSound(ServerPlayer player) {
        try {
            float volume = getMentionSoundVolume();
            com.zerog.neoessentials.util.SoundCompat.playNotifySound(
                player,
                net.minecraft.core.Holder.direct(getMentionSoundEvent()),
                net.minecraft.sounds.SoundSource.PLAYERS,
                volume,
                1.0f
            );
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.CHAT, "Failed to play mention sound: {}", e.getMessage());
        }
    }

    // Config helper methods

    private static boolean isItemLinksEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("allowItemLinks")) {
                return chatConfig.get("allowItemLinks").getAsBoolean();
            }
        } catch (Exception e) {
            // Ignore
        }
        return true;
    }

    private static boolean isUrlDetectionEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("autoLinkUrls")) {
                return chatConfig.get("autoLinkUrls").getAsBoolean();
            }
        } catch (Exception e) {
            // Ignore
        }
        return true;
    }

    private static boolean isMentionsEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("enabled")) {
                return chatConfig.getAsJsonObject("mentions").get("enabled").getAsBoolean();
            }
        } catch (Exception e) {
            // Ignore
        }
        return true;
    }

    private static boolean isMentionSoundEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("playSound")) {
                return chatConfig.getAsJsonObject("mentions").get("playSound").getAsBoolean();
            }
        } catch (Exception e) {
            // Ignore
        }
        return true;
    }

    private static boolean isMentionPermissionRequired() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("requirePermission")) {
                return chatConfig.getAsJsonObject("mentions").get("requirePermission").getAsBoolean();
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private static String getMentionPermission() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("permission")) {
                return chatConfig.getAsJsonObject("mentions").get("permission").getAsString();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "neoessentials.chat.mention";
    }

    private static ChatFormatting getMentionColor() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("highlightColor")) {
                String color = chatConfig.getAsJsonObject("mentions").get("highlightColor").getAsString();
                if (color.startsWith("&") && color.length() == 2) {
                    char code = color.charAt(1);
                    ChatFormatting formatting = ChatFormatting.getByCode(code);
                    if (formatting != null) return formatting;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return ChatFormatting.YELLOW;
    }

    private static float getMentionSoundVolume() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("soundVolume")) {
                return chatConfig.getAsJsonObject("mentions").get("soundVolume").getAsFloat();
            }
        } catch (Exception e) {
            // Ignore
        }
        return 1.0f;
    }

    /** Resolves chat.mentions.soundName to a registered SoundEvent, falling back if unset/invalid. */
    private static net.minecraft.sounds.SoundEvent getMentionSoundEvent() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("soundName")) {
                String soundName = chatConfig.getAsJsonObject("mentions").get("soundName").getAsString();
                net.minecraft.resources.Identifier id = soundName.contains(":")
                    ? net.minecraft.resources.Identifier.parse(soundName)
                    : net.minecraft.resources.Identifier.withDefaultNamespace(soundName);
                var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
                if (sound.isPresent()) return sound.get().value();
            }
        } catch (Exception e) {
            // Ignore
        }
        return net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP;
    }

    /**
     * Returns true if clickable player names are enabled in the chat config.
     * Config key: chat.clickablePlayerNames
     */
    private static boolean isClickablePlayerNamesEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("clickablePlayerNames")) {
                return chatConfig.get("clickablePlayerNames").getAsBoolean();
            }
        } catch (Exception ignored) {
            // Default to enabled on any error
        }
        return true;
    }

    /**
     * Returns true if the in-chat "view profile" link icon is enabled.
     * Config key: chat.showProfileLinkInChat
     */
    private static boolean isProfileLinkInChatEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("showProfileLinkInChat")) {
                return chatConfig.get("showProfileLinkInChat").getAsBoolean();
            }
        } catch (Exception ignored) {
            // Default to enabled on any error
        }
        return true;
    }

    /**
     * Returns true if rich text tag processing ({@code <red>}, {@code <gradient:…>} etc.)
     * is enabled.  Delegates to {@link RichTextFormatter}'s own check so the two stay
     * in sync.  Only used for debug-logging in this class.
     */
    private static boolean isRichTextEnabled() {
        try {
            var chatConfig = com.zerog.neoessentials.config.ConfigManager.getInstance().getConfig("chat");
            if (chatConfig.has("richText")) {
                return chatConfig.getAsJsonObject("richText").get("enabled").getAsBoolean();
            }
        } catch (Exception ignored) { /* ignore */ }
        return false;
    }
}
