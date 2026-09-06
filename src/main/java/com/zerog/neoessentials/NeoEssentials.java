package com.zerog.neoessentials;
import com.zerog.neoessentials.commands.CommandRegistry;
import com.zerog.neoessentials.config.ConfigSplitter;
import com.zerog.neoessentials.core.ManagerRegistry;
import com.zerog.neoessentials.permissions.PermissionSystem;
import net.neoforged.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import com.zerog.neoessentials.permissions.NeoEssentialsPermissionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zerog.neoessentials.logging.LogCategory;
import com.zerog.neoessentials.logging.NeoLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import com.zerog.neoessentials.util.MessageUtil;



@Mod("neoessentials")
public class NeoEssentials {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoEssentials.class);
    
    // Build and version information
    private static final String MOD_VERSION = "1.0.2.6";
    private static final String MOD_NAME = "NeoEssentials";
    private static final String BUILD_NUMBER = readBuildNumber();
    private static final String MINECRAFT_VERSION = "1.21.1-26.1.2";
    private static final String NEOFORGE_VERSION = "21.1.179+";

    @SuppressWarnings("unused") // modEventBus parameter required by NeoForge @Mod constructor
    public NeoEssentials(IEventBus modEventBus) {
        long startTime = System.currentTimeMillis();

        // ShopEntityRegistry uses @EventBusSubscriber — no manual registration needed.
        // (Previously registered a custom EntityType here, which caused client disconnects
        //  with "unknown registry key: neoessentials:shop_npc". Shop NPCs now use vanilla
        //  ArmorStand entities; interaction is handled via PlayerInteractEvent.EntityInteract.)

        // Enhanced initialization logging with version and build info
        NeoLog.info(LOGGER, LogCategory.GENERAL, "╔════════════════════════════════════════════════════════════════╗");
        NeoLog.info(LOGGER, LogCategory.GENERAL, "║         {} v{} (Build #{})         ║", MOD_NAME, MOD_VERSION, BUILD_NUMBER);
        NeoLog.info(LOGGER, LogCategory.GENERAL, "║    Minecraft {} | NeoForge {}        ║", MINECRAFT_VERSION, NEOFORGE_VERSION);
        NeoLog.info(LOGGER, LogCategory.GENERAL, "╚════════════════════════════════════════════════════════════════╝");
        NeoLog.info(LOGGER, LogCategory.GENERAL, "");
        NeoLog.info(LOGGER, LogCategory.GENERAL, "Initializing {} systems...", MOD_NAME);
        
        // Initialize PlaceholderAPI system
        try {
            NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing PlaceholderAPI system...");
            initializePlaceholderAPI();
            NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ PlaceholderAPI system initialized successfully");
        } catch (Exception e) {
            LOGGER.error("✗ PlaceholderAPI initialization failed: {}", e.getMessage(), e);
        }
        
        // Register all managers with the ManagerRegistry
        try {
            NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Registering system managers...");
            registerAllManagers();
            NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Registered {} managers across {} categories", 
                ManagerRegistry.getInstance().getManagerCount(),
                ManagerRegistry.getInstance().getManagersByCategory().size());
        } catch (Exception e) {
            LOGGER.error("✗ Manager registration failed: {}", e.getMessage(), e);
        }
        
        // Ensure custom language file is present
        MessageUtil.ensureCustomLanguageFile();

        long duration = System.currentTimeMillis() - startTime;
        NeoLog.info(LOGGER, LogCategory.GENERAL, "");
        NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ {} initialized successfully in {}ms", MOD_NAME, duration);
        NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");
        NeoLog.info(LOGGER, LogCategory.GENERAL, "");
    }
    
    /**
     * Read the build number from build_number.txt resource file.
     * 
     * @return The build number string, or "UNKNOWN" if not found
     */
    private static String readBuildNumber() {
        try (InputStream is = NeoEssentials.class.getResourceAsStream("/build_number.txt")) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String buildNumber = reader.lines().collect(Collectors.joining()).trim();
                    return buildNumber.isEmpty() ? "UNKNOWN" : buildNumber;
                }
            }
        } catch (Exception e) {
            NeoLog.debug(LOGGER, LogCategory.GENERAL, "Could not read build number: {}", e.getMessage());
        }
        return "UNKNOWN";
    }
    
    /**
     * Register all system managers with the ManagerRegistry for tracking and diagnostics.
     * This allows for centralized monitoring of all manager lifecycle and initialization status.
     * <p>
     * Note: Only managers with getInstance() singleton pattern are registered for initialization tracking.
     * Other managers are instantiated as needed and don't require centralized tracking.
     */
    private void registerAllManagers() {
        ManagerRegistry registry = ManagerRegistry.getInstance();
        
        // Economy Managers
        registry.registerManager("EconomyManager", "economy", 
            com.zerog.neoessentials.economy.managers.EconomyManager.class,
            com.zerog.neoessentials.economy.managers.EconomyManager::getInstance);
        
        // Chat Managers (only singleton managers)
        registry.registerManager("AfkManager", "chat",
            com.zerog.neoessentials.chat.AfkManager.class,
            com.zerog.neoessentials.chat.AfkManager::getInstance);
        
        // Note: MuteManager, SocialSpyManager, LastMessageManager, MsgToggleManager, ChatManager
        // are utility classes without singleton pattern - not registered here
        
        // Moderation Managers
        registry.registerManager("VanishManager", "moderation",
            com.zerog.neoessentials.moderation.VanishManager.class,
            com.zerog.neoessentials.moderation.VanishManager::getInstance);
        registry.registerManager("FreezeManager", "moderation",
            com.zerog.neoessentials.moderation.FreezeManager.class,
            com.zerog.neoessentials.moderation.FreezeManager::getInstance);
        registry.registerManager("JailManager", "moderation",
            com.zerog.neoessentials.moderation.JailManager.class,
            com.zerog.neoessentials.moderation.JailManager::getInstance);
        
        // Teleportation Managers
        registry.registerManager("HomeManager", "teleportation",
            com.zerog.neoessentials.teleportation.HomeManager.class,
            com.zerog.neoessentials.teleportation.HomeManager::getInstance);
        registry.registerManager("WarpManager", "teleportation",
            com.zerog.neoessentials.teleportation.Warp.WarpManager.class,
            com.zerog.neoessentials.teleportation.Warp.WarpManager::getInstance);
        registry.registerManager("SpawnManager", "teleportation",
            com.zerog.neoessentials.teleportation.Spawn.SpawnManager.class,
            com.zerog.neoessentials.teleportation.Spawn.SpawnManager::getInstance);
        
        // Kit Managers
        registry.registerManager("KitManager", "kits",
            com.zerog.neoessentials.kits.KitManager.class,
            com.zerog.neoessentials.kits.KitManager::getInstance);
        
        // Shop Managers
        registry.registerManager("ShopManager", "shop",
            com.zerog.neoessentials.shop.ShopManager.class,
            com.zerog.neoessentials.shop.ShopManager::getInstance);
        registry.registerManager("ShopEntityManager", "shop",
            com.zerog.neoessentials.shop.entity.ShopEntityManager.class,
            com.zerog.neoessentials.shop.entity.ShopEntityManager::getInstance);

        // API Managers
        registry.registerManager("PlaceholderManager", "api",
            com.zerog.neoessentials.api.PlaceholderManager.class,
            com.zerog.neoessentials.api.PlaceholderManager::getInstance);
        
        // Configuration Manager
        registry.registerManager("ConfigManager", "core",
            com.zerog.neoessentials.config.ConfigManager.class,
            com.zerog.neoessentials.config.ConfigManager::getInstance);
        
        // Permission System (special case - initialized in ServerStarting event)
        registry.registerManager("PermissionSystem", "core",
            com.zerog.neoessentials.permissions.PermissionSystem.class);
        
        NeoLog.debug(LOGGER, LogCategory.GENERAL, "Manager registration complete - {} managers registered", registry.getManagerCount());
    }
    
    @EventBusSubscriber(modid = "neoessentials", bus = EventBusSubscriber.Bus.GAME)
    public static class GameEvents {

        /**
         * Register NeoEssentials as an available NeoForge permission handler.
         *
         * <p>This fires on the NeoForge game event bus BEFORE the server starts,
         * during NeoForge's internal permission API initialisation.  We:
         * <ol>
         *   <li>Register the {@code neoessentials:handler} factory so admins can
         *       select it explicitly in {@code config/neoforge-server.toml}.</li>
         *   <li>When no competing permission mod (LuckPerms / FTB Ranks) is
         *       loaded AND the config still has the default NeoForge handler, we
         *       automatically switch to {@code neoessentials:handler}.  This makes
         *       permissions stored in {@code permissions.json} take effect for
         *       ALL installed mods (e.g. WorldEdit, WTHIT, etc.) out of the box.</li>
         * </ol>
         * </p>
         */
        @SubscribeEvent
        public static void onPermissionGatherHandler(PermissionGatherEvent.Handler event) {
            // 1. Register NeoEssentials' handler as an option
            event.addPermissionHandler(
                    NeoEssentialsPermissionHandler.IDENTIFIER,
                    NeoEssentialsPermissionHandler::new);
            NeoLog.debug(LOGGER, LogCategory.GENERAL, "[Permissions] Registered NeoEssentials permission handler: {}",
                    NeoEssentialsPermissionHandler.IDENTIFIER);

            // 2. Auto-activate when no competing permission mod is present
            boolean luckPermsPresent = net.neoforged.fml.ModList.get().isLoaded("luckperms");
            boolean ftbRanksPresent  = net.neoforged.fml.ModList.get().isLoaded("ftbranks");
            if (!luckPermsPresent && !ftbRanksPresent) {
                try {
                    String current = net.neoforged.neoforge.common.NeoForgeConfig.SERVER.permissionHandler.get();
                    // Only switch if the server is still on the default NeoForge handler
                    if ("neoforge:default_handler".equals(current)) {
                        net.neoforged.neoforge.common.NeoForgeConfig.SERVER.permissionHandler.set(
                                NeoEssentialsPermissionHandler.IDENTIFIER.toString());
                        NeoLog.info(LOGGER, LogCategory.GENERAL, "[Permissions] Auto-activated NeoEssentials permission handler " +
                                "(neoessentials:handler).  External mod permissions in permissions.json " +
                                "will now apply to ALL installed mods.  To revert, set " +
                                "'permissionHandler = \"neoforge:default_handler\"' in config/neoforge-server.toml.");
                    }
                } catch (Exception e) {
                    LOGGER.warn("[Permissions] Could not auto-configure NeoForge permission handler: {}", e.getMessage());
                }
            }
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");
            NeoLog.info(LOGGER, LogCategory.GENERAL, "Server starting - initializing NeoEssentials systems...");
            NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");
            
            // Check for config splitting opportunity
            try {
                ConfigSplitter.checkAndPromptMigration();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.GENERAL, "Config split check failed: {}", e.getMessage());
            }

            // Standing "found a bug or need help?" notice — every boot, not just when something
            // actually goes wrong (see SupportLinks.markProblemDetected() for that case).
            try {
                com.zerog.neoessentials.util.SupportLinks.queueGeneralHelpNotice();
            } catch (Exception e) {
                NeoLog.debug(LOGGER, LogCategory.GENERAL, "Queuing general help notice failed: {}", e.getMessage());
            }

            // Initialize permission system FIRST
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing Permission System...");
                PermissionSystem.initialize();
                ManagerRegistry.getInstance().markInitialized("PermissionSystem");
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Permission System initialized successfully");
            } catch (Exception e) {
                LOGGER.error("✗ CRITICAL: Permission system failed to initialize!", e);
                ManagerRegistry.getInstance().markFailed("PermissionSystem", e.getMessage());
            }

            // Initialize Vault API (after permissions, before chat/economy features)
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing Vault API...");
                com.zerog.neoessentials.vault.VaultManager.initialize();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Vault API initialized successfully");
            } catch (Exception e) {
                LOGGER.error("✗ Vault API initialization failed: {}", e.getMessage(), e);
            }

            // Initialize ChestShop system
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing ChestShop system...");
                com.zerog.neoessentials.shop.ShopManager.getInstance().initialize();
                ManagerRegistry.getInstance().markInitialized("ShopManager");
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ ChestShop system initialized ({} shop(s) loaded)",
                    com.zerog.neoessentials.shop.ShopManager.getInstance().getShopCount());
            } catch (Exception e) {
                LOGGER.error("✗ ChestShop system failed to initialize: {}", e.getMessage(), e);
                ManagerRegistry.getInstance().markFailed("ShopManager", e.getMessage());
            }

            // Initialize NPC shop system
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing NPC Shop system...");
                com.zerog.neoessentials.shop.entity.ShopEntityManager.getInstance().initialize();
                ManagerRegistry.getInstance().markInitialized("ShopEntityManager");
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ NPC Shop system initialized ({} NPC shop(s) loaded)",
                    com.zerog.neoessentials.shop.entity.ShopEntityManager.getInstance().getShopCount());
            } catch (Exception e) {
                LOGGER.error("✗ NPC Shop system failed to initialize: {}", e.getMessage(), e);
                ManagerRegistry.getInstance().markFailed("ShopEntityManager", e.getMessage());
            }

            // Initialize Auction House system
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing Auction House system...");
                com.zerog.neoessentials.auctionhouse.AuctionHouseManager.getInstance()
                        .initialize(event.getServer());
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Auction House initialized successfully");
            } catch (Exception e) {
                LOGGER.error("✗ Auction House failed to initialize: {}", e.getMessage(), e);
            }

            // Initialize dynamic pricing engine
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing Shop PricingEngine...");
                com.zerog.neoessentials.shop.pricing.PricingEngine.getInstance().loadConfig();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Shop PricingEngine initialized ({} rule(s) active, enabled={})",
                    com.zerog.neoessentials.shop.pricing.PricingEngine.getInstance().getRuleCount(),
                    com.zerog.neoessentials.shop.pricing.PricingEngine.getInstance().isEnabled());
            } catch (Exception e) {
                LOGGER.error("✗ Shop PricingEngine failed to initialize: {}", e.getMessage(), e);
            }

            // Initialize Hologram system
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing Hologram system...");
                com.zerog.neoessentials.hologram.HologramManager.getInstance().initialize();
                ManagerRegistry.getInstance().markInitialized("HologramManager");
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Hologram system initialized ({} hologram(s) loaded)",
                    com.zerog.neoessentials.hologram.HologramManager.getInstance().getAllHolograms().size());
            } catch (Exception e) {
                LOGGER.error("✗ Hologram system failed to initialize: {}", e.getMessage(), e);
                ManagerRegistry.getInstance().markFailed("HologramManager", e.getMessage());
            }

            // Cross-check: remove shop holograms that have no matching shop entry.
            // Both ShopManager and HologramManager are now loaded, so this is safe.
            try {
                com.zerog.neoessentials.hologram.integration.ShopHologramManager.cleanOrphanedShopHolograms();
            } catch (Exception e) {
                LOGGER.warn("Shop hologram orphan cleanup failed: {}", e.getMessage());
            }

            // Initialize custom language system
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing custom language system...");
                com.zerog.neoessentials.i18n.CustomLanguageManager.getInstance().initialize();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Custom language system initialized successfully");
            } catch (Exception e) {
                LOGGER.error("✗ Custom language system failed to initialize!", e);
            }

            // Initialize custom badge images (Phase 3) — part of the player tags/badges module
            if (com.zerog.neoessentials.config.ConfigManager.isPlayerTagsModuleEnabled()) {
                try {
                    NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Loading custom badge images...");
                    com.zerog.neoessentials.chat.BadgeManager.getInstance().loadCustomBadgeImages();
                    NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Badge images loaded successfully");
                } catch (Exception e) {
                    LOGGER.warn("⚠ Failed to load badge images: {}", e.getMessage());
                    // Non-critical, continue
                }
            } else {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Player tags/badges module is disabled via config, skipping badge image loading.");
            }

            // Initialize resource pack system (Phase 3)
            if (com.zerog.neoessentials.config.ConfigManager.isResourcePacksModuleEnabled()) {
                try {
                    NeoLog.info(LOGGER, LogCategory.GENERAL, "⚙ Initializing resource pack system...");
                    com.zerog.neoessentials.resourcepack.ResourcePackManager.getInstance().initialize();
                    NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Resource pack system initialized");
                } catch (Exception e) {
                    LOGGER.warn("⚠ Failed to initialize resource pack system: {}", e.getMessage());
                    // Non-critical, continue
                }
            } else {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Resource pack module is disabled via config, skipping resource pack system.");
            }

            // Display manager registry diagnostics
            try {
                String diagnosticReport = ManagerRegistry.getInstance().generateDiagnosticReport();
                NeoLog.info(LOGGER, LogCategory.GENERAL, diagnosticReport);
                
                // Warn about any failed managers
                int failedCount = ManagerRegistry.getInstance().getFailedCount();
                if (failedCount > 0) {
                    LOGGER.warn("⚠ {} manager(s) failed to initialize - some features may be unavailable", failedCount);
                    com.zerog.neoessentials.util.SupportLinks.markProblemDetected();
                    com.zerog.neoessentials.util.SupportLinks.logConsole(LOGGER, true);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to generate manager diagnostics: {}", e.getMessage());
            }

            // Always printed once per restart, regardless of whether anything went wrong —
            // the prominent (bordered) version above only shows up on an actual failure.
            com.zerog.neoessentials.util.SupportLinks.logConsole(LOGGER, false);

            NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");
        }
        
        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            NeoLog.info(LOGGER, LogCategory.GENERAL, "Server started - initializing chat system...");

            // Set server reference for Auction House component serialiser (registry ops)
            try {
                com.zerog.neoessentials.auctionhouse.AuctionHouseManager.getInstance()
                        .setServer(event.getServer());
            } catch (Exception e) {
                LOGGER.error("Failed to set AuctionHouse server reference", e);
            }

            // KitManager.initialize() ran during RegisterCommandsEvent, before the component
            // serializer's server reference above was set — any kit item with saved
            // DataComponentMap data failed to deserialize on that first load. Re-load kits now
            // that components are bound.
            try {
                com.zerog.neoessentials.kits.KitManager.getInstance().reloadKitItemsAfterServerStart();
            } catch (Exception e) {
                LOGGER.error("Failed to reload kit items after server start", e);
            }

            // Initialize chat integration adapters (SDLink, DCIntegration, DiscordSRV, etc.)
            if (com.zerog.neoessentials.config.ConfigManager.isDiscordIntegrationModuleEnabled()) {
                try {
                    com.zerog.neoessentials.integrations.ChatIntegrationManager.initialize();
                } catch (Exception e) {
                    LOGGER.error("Failed to initialize chat integration adapters", e);
                }
            } else {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Discord integration module is disabled via config, skipping chat integration adapters.");
            }

            // Initialize ChatManager
            try {
                com.zerog.neoessentials.config.ConfigManager configManager = com.zerog.neoessentials.config.ConfigManager.getInstance();
                com.google.gson.JsonObject config = configManager.getConfig(com.zerog.neoessentials.config.ConfigManager.MAIN_CONFIG);
                com.google.gson.JsonObject chatObj = config.has("chat") ? config.getAsJsonObject("chat") : new com.google.gson.JsonObject();
                com.google.gson.JsonObject commandsObj = config.has("commands") ? config.getAsJsonObject("commands") : new com.google.gson.JsonObject();
                
                // Create new ChatManager instance
                com.zerog.neoessentials.chat.ChatManager chatManager = new com.zerog.neoessentials.chat.ChatManager(chatObj, commandsObj);
                com.zerog.neoessentials.api.ChatAPI.setChatManager(chatManager);
                
                NeoLog.info(LOGGER, LogCategory.GENERAL, "ChatManager initialized successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to initialize ChatManager on server start", e);
            }

            // Initialize AfkManager configuration from config file
            try {
                com.zerog.neoessentials.config.ConfigManager configManager = com.zerog.neoessentials.config.ConfigManager.getInstance();
                com.google.gson.JsonObject config = configManager.getConfig(com.zerog.neoessentials.config.ConfigManager.MAIN_CONFIG);
                com.google.gson.JsonObject afkObj = config.has("afk") ? config.getAsJsonObject("afk") : new com.google.gson.JsonObject();
                com.zerog.neoessentials.chat.AfkManager.getInstance().loadConfiguration(afkObj);
                NeoLog.info(LOGGER, LogCategory.GENERAL, "AfkManager configuration loaded successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to initialize AfkManager configuration on server start", e);
            }

            NeoLog.info(LOGGER, LogCategory.GENERAL, "Server started - applying player nicknames...");
            
            // Apply nicknames to all online players
            try {
                com.zerog.neoessentials.util.commands.NickCommand.applyNicknamesToOnlinePlayers(event.getServer());
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Player nicknames applied successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to apply player nicknames on server start", e);
            }

            // Initialize Tablist system
            try {
                com.zerog.neoessentials.tablist.TablistManager.getInstance().loadConfig();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "TablistManager initialized successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to initialize TablistManager: {}", e.getMessage());
            }

            // Initialize sidebar Scoreboard system
            try {
                com.zerog.neoessentials.sidebar.ScoreboardManager.getInstance().loadConfig();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "ScoreboardManager initialized successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to initialize ScoreboardManager: {}", e.getMessage());
            }

            // Register leaderboard boards from leaderboard.json (money/kills/mob_kills/
            // playtime by default, plus whatever else an admin has enabled/added — see
            // LeaderboardConfigLoader).
            try {
                com.zerog.neoessentials.leaderboard.config.LeaderboardConfigLoader.load();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "LeaderboardManager initialized with {} board(s)",
                    com.zerog.neoessentials.leaderboard.LeaderboardManager.getInstance().getRegisteredBoardIds().size());
            } catch (Exception e) {
                LOGGER.error("Failed to initialize LeaderboardManager: {}", e.getMessage());
            }

            // Spawn holograms in their respective levels and start the scheduler.
            //
            // Delayed by a few ticks: entities (unlike terrain chunks) are loaded from
            // their own per-region storage on a separate, not-strictly-synchronous path,
            // so immediately after onServerStarted a chunk we just force-loaded via
            // level.getChunk() can still be mid-flight loading its saved entities. Running
            // cleanStaleEntities() at that exact moment can find nothing to remove (the old
            // hologram entity hasn't finished loading in yet), spawn a fresh entity anyway,
            // and then have the old one materialize moments later once loading catches up —
            // producing a visible duplicate that "looks cached" even though our chunk
            // force-load already ran. A short delay lets that settle first.
            net.minecraft.server.MinecraftServer mcServer = event.getServer();
            com.zerog.neoessentials.scheduler.DelayedTaskScheduler.schedule(40, () -> {
                try {
                    for (net.minecraft.server.level.ServerLevel level : mcServer.getAllLevels()) {
                        String dimKey = com.zerog.neoessentials.hologram.HologramRenderer.dimensionKey(level);
                        com.zerog.neoessentials.hologram.HologramRenderer.spawnAllForWorld(level, dimKey);
                    }
                    // Shop holograms lose their NBT_SHOP_KEY tag on every fresh respawn above
                    // (spawnAllForWorld doesn't know about shops) — restore it immediately so
                    // buy/sell clicks work right away instead of relying on the fallback's
                    // fragile reverse-position guess.
                    com.zerog.neoessentials.hologram.integration.ShopHologramManager.retagAllShopHolograms();
                    com.zerog.neoessentials.hologram.HologramScheduler.start();
                    NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Holograms spawned and scheduler started ({} hologram(s)).",
                        com.zerog.neoessentials.hologram.HologramManager.getInstance().getAllHolograms().size());
                } catch (Exception e) {
                    LOGGER.error("Failed to spawn holograms / start scheduler: {}", e.getMessage(), e);
                }
            });
        }
        
        @SubscribeEvent
        public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            // Any notices raised during startup (config split available, legacy data files now
            // inert, a manager failing to init, etc — see AdminNotices) are delivered together,
            // once, to the first admin who joins this session.
            // Admin check MUST come before consumeIfPending() — that call consumes the "show
            // once" flag via compareAndSet, so checking it first would burn the one opportunity
            // on a non-admin joining before any admin does.
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                    && canSeeAdminNotices(player)
                    && com.zerog.neoessentials.util.AdminNotices.consumeIfPending()) {
                com.zerog.neoessentials.util.AdminNotices.scheduleSendTo(player);
            }
        }

        /**
         * Gates admin-notice delivery on the dedicated {@code neoessentials.admin.notice}
         * permission node instead of a hand-rolled OP/wildcard check — a group can now be
         * granted (or explicitly denied) this specific node without needing full admin-wildcard
         * access, and it still resolves through {@link com.zerog.neoessentials.api.permissions.PermissionAPI}'s
         * normal chain (external adapter first, OP bypass, registry default of {@code false} —
         * so a random player never sees these unless actually granted the node or OP'd).
         */
        private static boolean canSeeAdminNotices(net.minecraft.server.level.ServerPlayer player) {
            return com.zerog.neoessentials.api.permissions.PermissionAPI.hasPermission(
                player.getUUID(), "neoessentials.admin.notice");
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");
            NeoLog.info(LOGGER, LogCategory.GENERAL, "Server stopping - shutting down NeoEssentials systems...");
            NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");

            // Shutdown Permission System
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down Permission System...");
                PermissionSystem.shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to save permissions on shutdown", e);
            }

            // Shutdown Economy Managers (these have executors that need proper shutdown)
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down Economy Manager...");
                com.zerog.neoessentials.economy.managers.EconomyManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Economy Manager", e);
            }

            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down Transaction History Manager...");
                com.zerog.neoessentials.economy.managers.TransactionHistoryManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Transaction History Manager", e);
            }

            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down Pay Toggle Manager...");
                com.zerog.neoessentials.economy.managers.PayToggleManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Pay Toggle Manager", e);
            }

            // Shutdown Auction House system
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down Auction House system...");
                com.zerog.neoessentials.auctionhouse.AuctionHouseManager.getInstance().shutdown();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Auction House system shutdown.");
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Auction House system", e);
            }

            // Shutdown Vault API
            try {
                com.zerog.neoessentials.vault.VaultManager.shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Vault API", e);
            }

            // Shutdown ChestShop system
            try {
                com.zerog.neoessentials.shop.ShopManager.getInstance().shutdown();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ ChestShop system shutdown.");
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown ChestShop system", e);
            }

            // Shutdown NPC Shop system
            try {
                com.zerog.neoessentials.shop.entity.ShopEntityManager.getInstance().shutdown();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ NPC Shop system shutdown.");
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown NPC Shop system", e);
            }

            // Shutdown Hologram system
            try {
                com.zerog.neoessentials.hologram.HologramScheduler.stop();
                com.zerog.neoessentials.hologram.HologramManager.getInstance().shutdown();
                NeoLog.info(LOGGER, LogCategory.GENERAL, "✓ Hologram system shutdown.");
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Hologram system", e);
            }

            // Shutdown Chat/AFK Managers
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down chat integration adapters...");
                com.zerog.neoessentials.integrations.ChatIntegrationManager.shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown chat integration adapters", e);
            }

            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down AFK Manager...");
                com.zerog.neoessentials.chat.AfkManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown AFK Manager", e);
            }

            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down AFK Movement Detector...");
                com.zerog.neoessentials.chat.handlers.AfkMovementDetector.shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown AFK Movement Detector", e);
            }

            // Shutdown Moderation Managers
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down Ban Manager scheduler...");
                com.zerog.neoessentials.moderation.BanManager.getInstance().shutdownScheduler();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Ban Manager", e);
            }

            // Shutdown Teleport Managers
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Shutting down Teleport Request Manager...");
                com.zerog.neoessentials.teleportation.TeleportRequests.TeleportRequestManager.getInstance().shutdown();
            } catch (Exception e) {
                LOGGER.error("Failed to shutdown Teleport Request Manager", e);
            }

            NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");
            NeoLog.info(LOGGER, LogCategory.GENERAL, "NeoEssentials shutdown complete");
            NeoLog.info(LOGGER, LogCategory.GENERAL, "════════════════════════════════════════════════════════════════");

            // Diagnostic: Check for any remaining threads
            // DISABLED: Thread diagnostics can potentially interfere with shutdown
            // Uncomment for debugging if needed
            /*
            try {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "Running thread diagnostics...");
                com.zerog.neoessentials.util.ThreadDiagnostics.logNeoEssentialsThreads();
                com.zerog.neoessentials.util.ThreadDiagnostics.logNonDaemonThreads();
            } catch (Exception e) {
                LOGGER.error("Failed to run thread diagnostics", e);
            }
            */
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            NeoLog.info(LOGGER, LogCategory.GENERAL, "Registering NeoEssentials commands...");
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            CommandRegistry registry = CommandRegistry.getInstance();
            
            // Remove vanilla /msg, /tell, /w commands so we can override them
            removeVanillaCommand(dispatcher, "msg");
            removeVanillaCommand(dispatcher, "tell");
            removeVanillaCommand(dispatcher, "w");

            // Remove vanilla moderation commands that share a name with ours — without this,
            // Brigadier's node-merge behavior on same-named literals means vanilla's own
            // BanPlayerCommands/BanListCommands/KickCommand keep handling these, completely
            // bypassing BanManager/KickManager. That meant every ban/kick went straight into
            // vanilla's banned-players.json with zero record in our own storage: invisible to
            // the dashboard's player lookup, the /api/public/moderation/lookup endpoint, and
            // any of our own /banlist or history commands. "pardon"/"pardon-ip" are also
            // removed even though their names don't collide with ours (unban/unbanip) — left
            // in place, they'd be a silent bypass that lifts a ban without ever touching
            // BanManager's own state.
            removeVanillaCommand(dispatcher, "ban");
            removeVanillaCommand(dispatcher, "banlist");
            removeVanillaCommand(dispatcher, "kick");
            removeVanillaCommand(dispatcher, "pardon");
            removeVanillaCommand(dispatcher, "pardon-ip");

            registerAllCommands(dispatcher, registry);
        }
        
        /**
         * Remove a vanilla command from the dispatcher to allow overriding
         */
        private static void removeVanillaCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
            try {
                var commands = dispatcher.getRoot().getChildren();
                commands.removeIf(node -> node.getName().equals(commandName));
                NeoLog.debug(LOGGER, LogCategory.GENERAL, "Removed vanilla command: /{}", commandName);
            } catch (Exception e) {
                LOGGER.warn("Failed to remove vanilla command /{}: {}", commandName, e.getMessage());
            }
        }
    }
    
    /**
     * All command registration and related logic was previously outside any method, causing syntax errors.
     * It has been moved here for your review. Move/refactor as needed.
     */
    private static void registerAllCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandRegistry registry) {
        // Register the root command first (/neoe and /neoessentials)
        com.zerog.neoessentials.commands.ModRootCommand.register(dispatcher);
        
        // ========== TELEPORTATION COMMANDS ==========
        // Register warp commands
        registry.registerCommandWithPermission("warp", "Teleport to a warp", "neoessentials.teleport.warp");
        registry.registerCommandWithPermission("setwarp", "Create a warp", "neoessentials.teleport.warp.create");
        registry.registerCommandWithPermission("delwarp", "Delete a warp", "neoessentials.teleport.warp.delete");
        registry.registerCommandWithPermission("warps", "List all warps", "neoessentials.teleport.warp.list");
        com.zerog.neoessentials.commands.teleportation.WarpCommands.register(dispatcher);

        // Register player warp commands if enabled
        if (com.zerog.neoessentials.teleportation.Warp.WarpManager.getInstance().isPlayerWarpsEnabled()) {
            registry.registerCommandWithPermission("pwarp", "Teleport to your player warp", "neoessentials.teleport.pwarp");
            registry.registerCommandWithPermission("setpwarp", "Create a player warp", "neoessentials.teleport.pwarp.create");
            registry.registerCommandWithPermission("delpwarp", "Delete a player warp", "neoessentials.teleport.pwarp.delete");
            registry.registerCommandWithPermission("pwarps", "List your player warps", "neoessentials.teleport.pwarp.list");
            com.zerog.neoessentials.commands.teleportation.PwarpCommands.register(dispatcher);
        }
        
        // Register home commands
        registry.registerCommandWithPermission("home", "Teleport to your home", "neoessentials.teleport.home");
        registry.registerCommandWithPermission("sethome", "Set your home location", "neoessentials.teleport.home.set");
        registry.registerCommandWithPermission("delhome", "Delete your home", "neoessentials.teleport.home.delete");
        registry.registerCommandWithPermission("deletehome", "Delete your home (alias)", "neoessentials.teleport.home.delete");
        registry.registerCommandWithPermission("homes", "List your homes", "neoessentials.teleport.home.list");
        com.zerog.neoessentials.commands.teleportation.HomeCommands.register(dispatcher);
        
        // Register spawn commands
        registry.registerCommandWithPermission("spawn", "Teleport to spawn", "neoessentials.teleport.spawn");
        registry.registerCommandWithPermission("setspawn", "Set spawn location", "neoessentials.teleport.spawn.set");
        com.zerog.neoessentials.commands.teleportation.SpawnCommands.register(dispatcher);
        
        // Register teleportation request commands
        registry.registerCommandWithPermission("tpa", "Request to teleport to a player", "neoessentials.teleport.request.tpa");
        registry.registerCommandWithPermission("tpahere", "Request a player to teleport to you", "neoessentials.teleport.request.tpahere");
        registry.registerCommandWithPermission("tpaccept", "Accept a teleport request", "neoessentials.teleport.request.accept");
        registry.registerCommandWithPermission("tpdeny", "Deny a teleport request", "neoessentials.teleport.request.deny");
        registry.registerCommandWithPermission("tpcancel", "Cancel your teleport request", "neoessentials.teleport.request.cancel");
        com.zerog.neoessentials.teleportation.TeleportRequests.TeleportRequestCommands.register(dispatcher);
        
        // Register admin teleportation commands
        registry.registerCommandWithPermission("tp", "Teleport to a player or location", "neoessentials.teleport.tp");
        registry.registerCommandWithPermission("tphere", "Teleport a player to you", "neoessentials.teleport.tphere");
        registry.registerCommandWithPermission("tpall", "Teleport all players to you", "neoessentials.teleport.admin.tpall");
        registry.registerCommandWithPermission("tppos", "Teleport to coordinates", "neoessentials.teleport.tppos");
        registry.registerCommandWithPermission("tpr", "Random teleportation", "neoessentials.teleport.tpr", "randomtp", "randomteleport");
        com.zerog.neoessentials.teleportation.DirectTeleport.DirectTeleportCommands.register(dispatcher);
        
        // Register root aliases for random teleport
        registry.registerCommand("neoe tpr", "Random teleportation (alias)");
        registry.registerCommand("neoe randomtp", "Random teleportation (alias)");
        registry.registerCommand("neoe randomteleport", "Random teleportation (alias)");

        // Register misc teleportation commands
        registry.registerCommandWithPermission("back", "Return to previous location", "neoessentials.teleport.back");
        registry.registerCommandWithPermission("top", "Teleport to highest block", "neoessentials.teleport.top");
        registry.registerCommandWithPermission("jump", "Jump through walls", "neoessentials.teleport.jump");
        registry.registerCommandWithPermission("jumpto", "Teleport to block you're looking at", "neoessentials.teleport.jumpto");
        com.zerog.neoessentials.teleportation.Misc.MiscTeleportCommands.register(dispatcher);

        // ========== ECONOMY COMMANDS ==========
        registry.registerCommandWithPermission("pay", "Send money to another player", "neoessentials.economy.pay");
        registry.registerCommandWithPermission("balance", "Check your balance", "");
        registry.registerCommandWithPermission("bal", "Check your balance (alias)", "");
        registry.registerCommandWithPermission("baltop", "View top balances", "neoessentials.economy.baltop");
        registry.registerCommandWithPermission("balancetop", "View top balances (alias)", "neoessentials.economy.baltop");
        registry.registerCommandWithPermission("eco", "Admin economy commands", "neoessentials.economy.eco");
        registry.registerCommandWithPermission("paytoggle", "Toggle receiving payments", "neoessentials.economy.paytoggle");
        registry.registerCommandWithPermission("pt", "Toggle receiving payments (alias)", "neoessentials.economy.paytoggle");
        com.zerog.neoessentials.economy.commands.EconomyCommands.register(dispatcher);

        // ========== MODERATION COMMANDS ==========
        registry.registerCommandWithPermission("ban", "Ban a player", "neoessentials.moderation.ban");
        registry.registerCommandWithPermission("unban", "Unban a player", "neoessentials.moderation.unban");
        registry.registerCommandWithPermission("banip", "Ban an IP address", "neoessentials.moderation.banip");
        registry.registerCommandWithPermission("unbanip", "Unban an IP address", "neoessentials.moderation.unbanip");
        registry.registerCommandWithPermission("banlist", "List banned players", "neoessentials.moderation.banlist");
        registry.registerCommandWithPermission("tempban", "Temporarily ban a player", "neoessentials.moderation.tempban");
        registry.registerCommandWithPermission("tempbanip", "Temporarily ban an IP address", "neoessentials.moderation.tempbanip");
        registry.registerCommandWithPermission("kick", "Kick a player", "neoessentials.moderation.kick");
        registry.registerCommandWithPermission("kickall", "Kick all players", "neoessentials.moderation.kickall");
        registry.registerCommandWithPermission("mute", "Mute a player", "");
        registry.registerCommandWithPermission("unmute", "Unmute a player", "");
        registry.registerCommandWithPermission("mutelist", "List muted players", "");
        registry.registerCommandWithPermission("jail", "Jail a player", "neoessentials.moderation.jail");
        registry.registerCommandWithPermission("jailfor", "Jail a player for a set duration", "neoessentials.moderation.jail");
        registry.registerCommandWithPermission("unjail", "Release a player from jail", "neoessentials.moderation.unjail");
        registry.registerCommandWithPermission("setjail", "Set jail location", "neoessentials.moderation.setjail");
        registry.registerCommandWithPermission("deljail", "Delete a jail location", "neoessentials.moderation.setjail");
        registry.registerCommandWithPermission("jaillist", "List all jail locations", "neoessentials.moderation.jaillist");
        registry.registerCommandWithPermission("jailinfo", "Show info about a jail", "neoessentials.moderation.jailinfo");
        registry.registerCommandWithPermission("jails", "List all jail locations (alias)", "neoessentials.moderation.jaillist");
        registry.registerCommandWithPermission("togglejail", "Toggle a player's jail state", "neoessentials.moderation.jail");
        registry.registerCommandWithPermission("freeze", "Freeze a player", "neoessentials.moderation.freeze");
        registry.registerCommandWithPermission("unfreeze", "Unfreeze a player", "neoessentials.moderation.unfreeze");
        registry.registerCommandWithPermission("freezeall", "Freeze all players", "neoessentials.moderation.freezeall");
        registry.registerCommandWithPermission("unfreezeall", "Unfreeze all players", "neoessentials.moderation.unfreezeall");
        registry.registerCommandWithPermission("freezelist", "List frozen players", "neoessentials.moderation.freezelist");
        registry.registerCommandWithPermission("vanish", "Toggle vanish mode", "neoessentials.moderation.vanish");
        registry.registerCommandWithPermission("v", "Toggle vanish mode (alias)", "neoessentials.moderation.vanish");
        registry.registerCommandWithPermission("unvanish", "Disable vanish mode", "neoessentials.moderation.vanish");
        registry.registerCommandWithPermission("vanishlist", "List vanished players", "neoessentials.moderation.vanishlist");
        com.zerog.neoessentials.moderation.commands.BanCommand.register(dispatcher);
        com.zerog.neoessentials.moderation.commands.KickCommand.register(dispatcher);
        com.zerog.neoessentials.moderation.commands.JailCommand.register(dispatcher);
        com.zerog.neoessentials.moderation.commands.FreezeCommand.register(dispatcher);
        com.zerog.neoessentials.moderation.commands.VanishCommand.register(dispatcher);
        registry.registerCommandWithPermission("warn", "Issue a warning to a player", "neoessentials.moderation.warn");
        registry.registerCommandWithPermission("warnings", "View warnings for a player", "neoessentials.moderation.warnings");
        registry.registerCommandWithPermission("clearwarnings", "Clear all warnings for a player", "neoessentials.moderation.warn");
        registry.registerCommandWithPermission("removewarn", "Remove a specific warning by ID", "neoessentials.moderation.warn");
        com.zerog.neoessentials.moderation.commands.WarnCommand.register(dispatcher);
        registry.registerCommandWithPermission("note", "Add a staff note to a player's record", "neoessentials.moderation.note");
        registry.registerCommandWithPermission("notes", "View staff notes for a player", "neoessentials.moderation.notes");
        registry.registerCommandWithPermission("removenote", "Remove a specific note by ID", "neoessentials.moderation.note");
        com.zerog.neoessentials.moderation.commands.NoteCommand.register(dispatcher);
        registry.registerCommandWithPermission("report", "Report a player for staff review", "neoessentials.moderation.report");
        registry.registerCommandWithPermission("reports", "View the pending report queue", "neoessentials.moderation.reports");
        registry.registerCommandWithPermission("reviewreport", "Accept or dismiss a player report", "neoessentials.moderation.reports");
        com.zerog.neoessentials.moderation.commands.ReportCommand.register(dispatcher);
        registry.registerCommandWithPermission("modhistory", "View a player's full moderation history (bans/mutes/kicks/warns)", "neoessentials.moderation.history");
        registry.registerCommandWithPermission("history", "View a player's moderation history (alias)", "neoessentials.moderation.history");
        com.zerog.neoessentials.moderation.commands.ModHistoryCommand.register(dispatcher);

        // ========== CHAT/MESSAGING COMMANDS ==========
        registry.registerCommandWithPermission("msg", "Send a private message", "");
        registry.registerCommandWithPermission("message", "Send a private message (alias)", "");
        registry.registerCommandWithPermission("tell", "Send a private message (alias)", "");
        registry.registerCommandWithPermission("whisper", "Send a private message (alias)", "");
        registry.registerCommandWithPermission("w", "Send a private message (alias)", "");
        registry.registerCommandWithPermission("reply", "Reply to last private message", "");
        registry.registerCommandWithPermission("r", "Reply to last private message (alias)", "");
        registry.registerCommandWithPermission("ignore", "Ignore a player", "");
        registry.registerCommandWithPermission("unignore", "Unignore a player", "");
        registry.registerCommandWithPermission("socialspy", "Spy on private messages", "");
        registry.registerCommandWithPermission("msgtoggle", "Toggle receiving private messages", "");
        registry.registerCommand("mail", "Manage mail messages");
        com.zerog.neoessentials.chat.command.MsgCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.ReplyCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.IgnoreCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.UnignoreCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.SocialSpyCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.MuteCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.UnmuteCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.MuteListCommand.register(dispatcher);
        com.zerog.neoessentials.chat.command.MsgToggleCommand.register(dispatcher);
        registry.registerCommandWithPermission("chatformat", "Manage per-player/per-group chat formats", "");
        com.zerog.neoessentials.chat.command.ChatFormatCommand.register(dispatcher);

        // Register channel commands (dynamically from config)
        com.zerog.neoessentials.chat.commands.ChannelCommands.register(dispatcher);

        // ========== LANGUAGE COMMANDS ==========
        registry.registerCommandWithPermission("language", "Manage custom language files", "vanilla:op-level-4");
        com.zerog.neoessentials.commands.LanguageCommand.register(dispatcher);

        // ========== PERMISSIONS COMMANDS ==========
        registry.registerCommandWithPermission("permissions", "Manage permissions", "");
        registry.registerCommandWithPermission("pex", "Manage permissions (alias)", "");
        com.zerog.neoessentials.permissions.command.PermissionsCommand.register(dispatcher);

        // ========== KIT COMMANDS ==========
        registry.registerCommandWithPermission("kit", "Claim a kit", "neoessentials.kits.use");
        registry.registerCommandWithPermission("kits", "List available kits", "neoessentials.kits.list");
        registry.registerCommandWithPermission("listkits", "List available kits (alias)", "neoessentials.kits.list");
        registry.registerCommandWithPermission("createkit", "Create a new kit", "neoessentials.kits.create");
        registry.registerCommandWithPermission("delkit", "Delete a kit", "neoessentials.kits.delete");
        registry.registerCommand("kitreset", "Reset a kit cooldown");
        try {
            com.zerog.neoessentials.kits.command.KitCommands.register(dispatcher);
        } catch (Throwable e) {
            LOGGER.error("Kit commands failed to register (non-fatal): {}", e.getMessage(), e);
        }

        // ========== UTILITY COMMANDS ==========
        registry.registerCommand("afk", "Toggle AFK status");
        registry.registerCommandWithPermission("away", "Toggle AFK status (alias)", "neoessentials.afk");
        registry.registerCommand("help", "Show available commands");
        registry.registerCommand("?", "Show available commands (alias)");
        registry.registerCommand("nick", "Change your nickname");
        registry.registerCommand("nickname", "Change your nickname (alias)");
        registry.registerCommand("anvil", "Open portable anvil");
        registry.registerCommandWithPermission("workbench", "Open portable crafting table", "neoessentials.crafting");
        registry.registerCommand("book", "Manage books");
        registry.registerCommand("compass", "Show your compass direction");
        registry.registerCommandWithPermission("direction", "Show your compass direction (alias)", "neoessentials.compass");
        registry.registerCommand("crafting", "Open portable crafting table");
        registry.registerCommandWithPermission("craft", "Open portable crafting table (alias)", "neoessentials.crafting");
        registry.registerCommand("depth", "Show your depth");
        registry.registerCommand("getpos", "Get your current position");
        registry.registerCommandWithPermission("coords", "Get your current position (alias)", "neoessentials.getpos");
        registry.registerCommandWithPermission("whereami", "Get your current position (alias)", "neoessentials.getpos");
        registry.registerCommand("grindstone", "Open portable grindstone");
        registry.registerCommand("helpop", "Request help from staff");
        registry.registerCommandWithPermission("ac", "Request help from staff (alias)", "neoessentials.helpop");
        registry.registerCommandWithPermission("amsg", "Request help from staff (alias)", "neoessentials.helpop");
        registry.registerCommand("list", "List online players");
        registry.registerCommandWithPermission("who", "List online players (alias)", "neoessentials.list");
        registry.registerCommandWithPermission("online", "List online players (alias)", "neoessentials.list");
        registry.registerCommand("mail", "Manage mail messages");
        registry.registerCommandWithPermission("motd", "View message of the day", "");
        registry.registerCommand("near", "Find nearby players");
        registry.registerCommand("nearby", "Find nearby players (alias)");
        registry.registerCommand("ping", "Check your ping");
        registry.registerCommand("pong", "Check your ping (alias)");
        registry.registerCommand("realname", "Find player by nickname");
        registry.registerCommand("rules", "View server rules");
        registry.registerCommand("seen", "Check when player was last seen");
        registry.registerCommand("sign", "Edit sign text");
        registry.registerCommand("smithing", "Open portable smithing table");
        registry.registerCommand("stonecutting", "Open portable stonecutter");
        registry.registerCommandWithPermission("stonecutter", "Open portable stonecutter (alias)", "neoessentials.stonecutting");
        registry.registerCommand("suicide", "Kill yourself");
        registry.registerCommandWithPermission("killme", "Kill yourself (alias)", "neoessentials.suicide");
        registry.registerCommand("whois", "Get player information");
        registry.registerCommand("info", "Get player information (alias)");
        registry.registerCommandWithPermission("gms", "Change to survival mode", "neoessentials.gamemode");
        registry.registerCommandWithPermission("gmc", "Change to creative mode", "neoessentials.gamemode");
        registry.registerCommandWithPermission("gmsp", "Change to spectator mode", "neoessentials.gamemode");
        registry.registerCommandWithPermission("gma", "Change to adventure mode", "neoessentials.gamemode");
        
        com.zerog.neoessentials.inventory.InventoryViewCommands.register(dispatcher);
        com.zerog.neoessentials.util.commands.AfkCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.HelpCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.AnvilCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.BookCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.CompassCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.CraftingCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.DepthCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.GetPosCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.GrindstoneCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.HelpopCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.ListCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.MailCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.MotdCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.NearCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.NickCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.PingCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.RealnameCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.RulesCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.SeenCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.SignCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.SmithingCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.StonecuttingCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.SuicideCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.WhoisCommand.register(dispatcher);
        com.zerog.neoessentials.util.commands.GamemodeCommand.register(dispatcher);
        

        // ========== ITEM COMMANDS ==========
        registry.registerCommandWithPermission("repair", "Repair items", "neoessentials.item.repair");
        registry.registerCommandWithPermission("fix", "Repair items (alias)", "neoessentials.item.repair");
        registry.registerCommandWithPermission("dispose", "Dispose of items", "neoessentials.item.dispose");
        registry.registerCommandWithPermission("trash", "Dispose of items (alias)", "neoessentials.item.dispose");
        registry.registerCommandWithPermission("powertool", "Bind commands to items", "neoessentials.item.powertool");
        registry.registerCommandWithPermission("pt", "Bind commands to items (alias)", "neoessentials.item.powertool");
        registry.registerCommandWithPermission("enchant", "Enchant items", "neoessentials.item.enchant");
        registry.registerCommandWithPermission("clearinventory", "Clear inventory", "neoessentials.item.clearinventory");
        registry.registerCommandWithPermission("ci", "Clear inventory (alias)", "neoessentials.item.clearinventory");
        registry.registerCommandWithPermission("clear", "Clear inventory (alias)", "neoessentials.item.clearinventory");
        registry.registerCommand("invsee", "View another player's inventory");
        registry.registerCommandWithPermission("inv", "View another player's inventory (alias)", "neoessentials.invsee");
        registry.registerCommandWithPermission("invseeedit", "View and edit another player's inventory", "neoessentials.invsee.edit");
        registry.registerCommand("enderchest", "View another player's ender chest");
        registry.registerCommandWithPermission("ec", "View another player's ender chest (alias)", "neoessentials.enderchest");
        registry.registerCommandWithPermission("enderchestedit", "View and edit another player's ender chest", "neoessentials.enderchest.edit");
        registry.registerCommandWithPermission("ecedit", "View and edit another player's ender chest (alias)", "neoessentials.enderchest.edit");
        registry.registerCommand("condense", "Compact items to their block forms");
        registry.registerCommand("showkit", "Preview kit contents without claiming");
        registry.registerCommand("powertoollist", "List all active powertool bindings");
        registry.registerCommandWithPermission("ptlist", "List all active powertool bindings (alias)", "neoessentials.powertoollist");
        registry.registerCommand("customtext", "Display a custom server text page");
        registry.registerCommandWithPermission("ctext", "Display a custom server text page (alias)", "neoessentials.customtext");
        registry.registerCommand("payconfirmtoggle", "Toggle payment confirmation prompts");
        registry.registerCommand("ciconfirmtoggle", "Toggle /ci confirmation prompts");
        registry.registerCommandWithPermission("clearinventoryconfirmtoggle", "Toggle /ci confirmation prompts (alias)", "neoessentials.ciconfirmtoggle");
        registry.registerCommand("item", "Give yourself an item by name");
        registry.registerCommandWithPermission("i", "Give yourself an item by name (alias)", "neoessentials.item");
        registry.registerCommand("rtoggle", "Toggle /r reply-to-sender direction");
        com.zerog.neoessentials.items.commands.RepairCommand.register(dispatcher);
        com.zerog.neoessentials.items.commands.DisposeCommand.register(dispatcher);
        com.zerog.neoessentials.items.commands.PowertoolCommand.register(dispatcher);
        com.zerog.neoessentials.items.commands.EnchantCommand.register(dispatcher);
        com.zerog.neoessentials.items.commands.ClearInventoryCommand.register(dispatcher);
        com.zerog.neoessentials.items.commands.MiscItemCommands.register(dispatcher);

        // ========== WORTH / SELL COMMANDS ==========
        registry.registerCommand("worth", "Check the sell value of an item");
        registry.registerCommand("sell", "Sell items for money");
        registry.registerCommand("setworth", "Set the sell price of an item");
        com.zerog.neoessentials.economy.worth.WorthManager.getInstance().initialize();
        com.zerog.neoessentials.economy.worth.WorthCommand.register(dispatcher);
        com.zerog.neoessentials.economy.worth.SellCommand.register(dispatcher);

        // ========== PLAYER STATE / ADMIN TOOL COMMANDS ==========
        registry.registerCommand("fly", "Toggle flight mode");
        registry.registerCommand("god", "Toggle god mode");
        registry.registerCommand("heal", "Restore player health and hunger");
        registry.registerCommand("feed", "Restore player hunger");
        registry.registerCommand("speed", "Set walk or fly speed");
        registry.registerCommand("ext", "Extinguish a player");
        registry.registerCommandWithPermission("extinguish", "Extinguish a player (alias)", "neoessentials.ext");
        registry.registerCommand("burn", "Set a player on fire");
        registry.registerCommand("give", "Give items to a player");
        registry.registerCommand("more", "Fill held stack to max");
        registry.registerCommand("hat", "Wear held item as helmet");
        registry.registerCommand("exp", "Manage player experience");
        registry.registerCommandWithPermission("xp", "Manage player experience (alias)", "neoessentials.exp");
        registry.registerCommand("sudo", "Run a command as another player");
        registry.registerCommand("playtime", "Check player play time");
        com.zerog.neoessentials.util.commands.PlayerStateCommands.register(dispatcher);

        // ========== SERVER ADMIN COMMANDS ==========
        registry.registerCommand("broadcast", "Broadcast a message to all players");
        registry.registerCommandWithPermission("bc", "Broadcast a message (alias)", "neoessentials.broadcast");
        registry.registerCommandWithPermission("announce", "Broadcast a message (alias)", "neoessentials.broadcast");
        registry.registerCommand("time", "Get or set world time");
        registry.registerCommandWithPermission("day", "Set time to day", "neoessentials.time.set");
        registry.registerCommandWithPermission("night", "Set time to night", "neoessentials.time.set");
        registry.registerCommand("weather", "Set world weather");
        registry.registerCommandWithPermission("sun", "Set weather to clear", "neoessentials.weather");
        registry.registerCommandWithPermission("storm", "Set weather to storm", "neoessentials.weather");
        registry.registerCommandWithPermission("thunder", "Set weather to thunder", "neoessentials.weather");
        registry.registerCommand("kill", "Kill a player");
        registry.registerCommand("gamemode", "Change player gamemode");
        registry.registerCommandWithPermission("tpo", "Teleport override (bypass tptoggle)", "neoessentials.teleport.tpo");
        registry.registerCommandWithPermission("tpohere", "Bring player here override", "neoessentials.teleport.tpohere");
        registry.registerCommandWithPermission("tpoffline", "Teleport to offline player's last position", "neoessentials.teleport.tpoffline");
        com.zerog.neoessentials.util.commands.ServerAdminCommands.register(dispatcher);

        // ========== DASHBOARD API KEYS ==========
        registry.registerCommandWithPermission("apikey", "Manage long-lived API keys for external dashboard integrations", "neoessentials.dashboard.apikeys");
        com.zerog.neoessentials.webdashboard.commands.ApiKeyCommand.register(dispatcher);

        // ========== UTILITY COMMANDS ==========
        registry.registerCommand("ptime", "Set per-player time override");
        registry.registerCommand("pweather", "Set per-player weather override");
        registry.registerCommand("effect", "Apply potion effects to players");
        registry.registerCommand("spawnmob", "Spawn entities");
        registry.registerCommandWithPermission("mob", "Spawn entities (alias)", "neoessentials.spawnmob");
        registry.registerCommand("unlimited", "Toggle unlimited item use");
        registry.registerCommand("condense", "Condense items to storage blocks");
        com.zerog.neoessentials.util.commands.UtilityCommands.register(dispatcher);

        // ========== ITEM CUSTOMISATION & MISC COMMANDS ==========
        registry.registerCommand("me", "Broadcast an action message");
        registry.registerCommand("tptoggle", "Toggle teleport request acceptance");
        registry.registerCommand("gc", "Show server memory and TPS info");
        registry.registerCommandWithPermission("mem", "Show server memory info (alias)", "neoessentials.gc");
        registry.registerCommand("lightning", "Strike lightning at a player");
        registry.registerCommandWithPermission("smite", "Strike lightning (alias)", "neoessentials.lightning");
        registry.registerCommand("skull", "Get a player head item");
        registry.registerCommand("itemname", "Rename held item");
        registry.registerCommandWithPermission("rename", "Rename held item (alias)", "neoessentials.itemname");
        registry.registerCommand("itemlore", "Edit held item lore");
        registry.registerCommand("remove", "Remove entities in radius");
        registry.registerCommand("loom", "Open portable loom");
        registry.registerCommand("cartography", "Open portable cartography table");
        registry.registerCommandWithPermission("cartographytable", "Open portable cartography table (alias)", "neoessentials.cartography");
        com.zerog.neoessentials.util.commands.ItemCustomisationCommands.register(dispatcher);

        // ========== WORLD INTERACTION & FUN COMMANDS ==========
        registry.registerCommand("fireball", "Shoot a projectile");
        registry.registerCommand("tree", "Grow a tree at look target");
        registry.registerCommandWithPermission("bigtree", "Grow a large tree (alias)", "neoessentials.tree");
        registry.registerCommand("break", "Break the looked-at block");
        registry.registerCommand("ice", "Freeze a player");
        registry.registerCommand("bottom", "Teleport to the bottom of the world");
        registry.registerCommand("tpaall", "Send tpa-here to all online players");
        registry.registerCommand("broadcastworld", "Broadcast to players in your world");
        registry.registerCommandWithPermission("bcastworld", "Broadcast to world (alias)", "neoessentials.broadcastworld");
        com.zerog.neoessentials.util.commands.WorldInteractionCommands.register(dispatcher);

        // ========== PLAYER INFO (msgtoggle only — all other commands registered above) ==========
        // /msgtoggle is solely owned by PlayerInfoCommands; all other player-info commands
        // (near, ping, seen, whois, realname, suicide, motd, rules, etc.) are registered
        // by their own dedicated command classes above and must NOT be re-registered here.
        com.zerog.neoessentials.util.commands.PlayerInfoCommands.register(dispatcher);

        // /warpinfo → WarpCommands, /world+/spawner+/recipe → ServerAdminCommands
        // (renamehome → HomeCommands, tpauto → MiscTeleportCommands — both registered in their own register() calls above)
        com.zerog.neoessentials.commands.teleportation.WarpCommands.registerWarpInfoCommand(dispatcher);
        com.zerog.neoessentials.util.commands.ServerAdminCommands.registerWorldCommands(dispatcher);

        registry.registerCommand("renamehome", "Rename a home");
        registry.registerCommand("warpinfo", "Show info about a warp");
        registry.registerCommand("world", "Teleport to a world/dimension");
        registry.registerCommand("spawner", "Change a spawner type");
        registry.registerCommand("recipe", "Show/unlock recipe for an item");
        registry.registerCommand("tpauto", "Auto-accept all teleport requests");

        // ========== FUN / MISCELLANEOUS COMMANDS ==========
        registry.registerCommand("firework", "Edit or fire held firework rockets");
        registry.registerCommand("fw", "Edit or fire held firework rockets (alias)");
        registry.registerCommand("nuke", "Rain TNT on a player");
        registry.registerCommand("antioch", "Spawn lit TNT at your look target ( easter egg)");
        registry.registerCommand("kittycannon", "Launch an exploding baby cat ");
        registry.registerCommand("beezooka", "Launch angry bees ");
        registry.registerCommand("itemdb", "Look up item registry info");
        registry.registerCommand("potion", "Edit potion effects on held potion item");
        registry.registerCommand("info", "Show server info/MOTD");
        registry.registerCommand("rest", "Reset your sleep timer (prevent phantoms)");
        registry.registerCommand("backup", "Trigger a server world save and backup");
        com.zerog.neoessentials.util.commands.FunCommands.register(dispatcher);

        // ========== VAULT API COMMANDS ==========
        registry.registerCommandWithPermission("vault", "NeoEssentials Vault API info and management", "neoessentials.vault.admin");
        com.zerog.neoessentials.vault.command.VaultCommand.register(dispatcher);

        // ========== PLACEHOLDER COMMANDS ==========
        registry.registerCommandWithPermission("placeholder", "Inspect and test the NeoEssentials placeholder system", "neoessentials.admin.placeholders");
        com.zerog.neoessentials.commands.utility.PlaceholderCommand.register(dispatcher);

        // ========== DASHBOARD COMMANDS ==========
        // DashboardCommand/DashboardRegisterCommand existed but were never actually wired to
        // the dispatcher (a real bug, not a doc gap) — /dashboard and /dashboardregister were
        // unknown commands in-game despite the wiki documenting them as working.
        registry.registerCommandWithPermission("dashboard", "Start/stop/status/URL for the web dashboard", "neoessentials.admin.dashboard");
        com.zerog.neoessentials.commands.utility.DashboardCommand.register(dispatcher);
        registry.registerCommandWithPermission("dashboardregister", "Register a web dashboard account", "neoessentials.dashboard.access");
        com.zerog.neoessentials.commands.utility.DashboardRegisterCommand.register(dispatcher);
        registry.registerCommandWithPermission("linkaccount", "Link your Minecraft account to an existing dashboard account", "");
        com.zerog.neoessentials.commands.utility.LinkAccountCommand.register(dispatcher);

        // ========== CHEST SHOP COMMANDS ==========
        registry.registerCommandWithPermission("chestshop", "Sign-based chest shop system", "");
        registry.registerCommandWithPermission("cshop", "Sign-based chest shop (alias)", "");
        com.zerog.neoessentials.shop.commands.ShopCommand.register(dispatcher);

        // ========== NPC SHOP COMMANDS ==========
        registry.registerCommandWithPermission("npcshop", "NPC entity shop management", "neoessentials.shop.npc.manage");
        com.zerog.neoessentials.shop.commands.NpcShopCommand.register(dispatcher);

        // ========== HOLOGRAM COMMANDS ==========
        registry.registerCommandWithPermission("hologram", "Manage holographic displays", "neoessentials.hologram.admin", "holo");
        com.zerog.neoessentials.hologram.command.HologramCommand.register(dispatcher);

        // ========== AUCTION HOUSE COMMANDS ==========
        registry.registerCommandWithPermission("ah", "Auction House — buy and sell items", "", "auctionhouse");
        com.zerog.neoessentials.auctionhouse.command.AuctionHouseCommand.register(dispatcher);

        // ========== TABLIST COMMANDS ==========
        // NOTE: TablistCommand existed but was never wired up here — every /tablist
        // subcommand (reload, enable/disable, set header/footer, fakeplayer add/remove,
        // group/player overrides, etc.) was unreachable ("Unknown command") even though
        // the passive per-tick header/footer rendering worked fine via TablistEventHandler.
        registry.registerCommandWithPermission("tablist", "Manage the player list header/footer/entries", "neoessentials.tablist.admin");
        com.zerog.neoessentials.tablist.TablistCommand.register(dispatcher);

        // ========== SCOREBOARD COMMANDS ==========
        registry.registerCommandWithPermission("scoreboard", "Manage the sidebar scoreboard", "neoessentials.scoreboard.admin");
        com.zerog.neoessentials.sidebar.ScoreboardCommand.register(dispatcher);

        // ========== LEADERBOARD COMMANDS ==========
        registry.registerCommandWithPermission("leaderboard", "View ranked leaderboards (money, kills, playtime, ...)", "neoessentials.leaderboard.view", "lb");
        com.zerog.neoessentials.leaderboard.commands.LeaderboardCommand.register(dispatcher);

        // ========== VOTIFIER COMMANDS ==========
        registry.registerCommandWithPermission("vote", "Show vote site links", "neoessentials.votifier.vote");
        registry.registerCommandWithPermission("votes", "Show vote totals", "neoessentials.votifier.vote");
        registry.registerCommandWithPermission("togglevotebroadcast", "Opt out of vote broadcasts", null);
        registry.registerCommandWithPermission("voteparty", "Show vote party progress", "neoessentials.votifier.vote");
        registry.registerCommandWithPermission("votifier", "Manage the Votifier vote listener", "neoessentials.votifier.admin");
        com.zerog.neoessentials.votifier.commands.VotifierCommands.register(dispatcher);

        // ========== CRATES COMMANDS ==========
        registry.registerCommandWithPermission("crate", "Open and manage crates", "neoessentials.crate.open");
        com.zerog.neoessentials.crates.commands.CrateCommands.register(dispatcher);
    }
        /*
         * All command registration and related logic that was previously outside of methods has been moved here as a block comment.
         * Please review and refactor as needed. This preserves all logic for your multi-file mod and ensures the file compiles.
         *
         * (Copy-paste all command registration code blocks here for later refactoring)
         *
         * ...
         * (See previous file version for the full logic)
         */

    /**
     * Initialize the PlaceholderAPI system with default NeoEssentials placeholders.
     * This makes placeholders available to the chat system and allows other mods to
     * register their own placeholders for cross-mod compatibility.
     * 
     * <p>The PlaceholderAPI supports:</p>
     * <ul>
     *   <li>30+ built-in NeoEssentials placeholders (player info, location, economy, etc.)</li>
     *   <li>Dynamic placeholder registration from other mods</li>
     *   <li>Placeholder expansions for organizing related placeholders</li>
     *   <li>Thread-safe placeholder resolution</li>
     * </ul>
     * 
     * <p>External mods can integrate by calling:</p>
     * <pre>{@code
     * PlaceholderAPI.registerPlaceholder("mymod_placeholder", (player, params) -> {
     *     return "value";
     * });
     * }</pre>
     * 
     * @see com.zerog.neoessentials.api.PlaceholderAPI
     * @see com.zerog.neoessentials.api.DefaultPlaceholderExpansion
     */
    private void initializePlaceholderAPI() {
        NeoLog.debug(LOGGER, LogCategory.GENERAL, "Initializing PlaceholderAPI system...");
        try {
            // Register the default NeoEssentials placeholder expansion
            com.zerog.neoessentials.api.DefaultPlaceholderExpansion defaultExpansion = 
                new com.zerog.neoessentials.api.DefaultPlaceholderExpansion();
            
            NeoLog.debug(LOGGER, LogCategory.GENERAL, "Created DefaultPlaceholderExpansion with {} placeholders", 
                defaultExpansion.getPlaceholders().size());
            
            boolean registered = com.zerog.neoessentials.api.PlaceholderAPI.registerExpansion(defaultExpansion);
            
            if (registered) {
                NeoLog.info(LOGGER, LogCategory.GENERAL, "PlaceholderAPI initialized with {} default placeholders", 
                    defaultExpansion.getPlaceholders().size());
                NeoLog.debug(LOGGER, LogCategory.GENERAL, "Available placeholders: {}", 
                    com.zerog.neoessentials.api.PlaceholderAPI.getRegisteredPlaceholders());
                
                // Register the leaderboard placeholder expansion ({leaderboard_<board>:<rank>:name|value})
                com.zerog.neoessentials.api.PlaceholderAPI.registerExpansion(
                    new com.zerog.neoessentials.leaderboard.LeaderboardPlaceholderExpansion());

                // {votifier_total} / {votifier_voteparty_progress} / {votifier_voteparty_required}
                com.zerog.neoessentials.api.PlaceholderAPI.registerExpansion(
                    new com.zerog.neoessentials.votifier.VotifierPlaceholderExpansion());

                // {crate_keys:<crateId>}
                com.zerog.neoessentials.api.PlaceholderAPI.registerExpansion(
                    new com.zerog.neoessentials.crates.CratePlaceholderExpansion());

                // Mark PlaceholderManager as initialized
                ManagerRegistry.getInstance().markInitialized("PlaceholderManager");
            } else {
                LOGGER.error("Failed to register default placeholder expansion");
                ManagerRegistry.getInstance().markFailed("PlaceholderManager", 
                    "Failed to register default expansion");
            }
            
        } catch (Exception e) {
            LOGGER.error("PlaceholderAPI initialization failed: {}", e.getMessage(), e);
            ManagerRegistry.getInstance().markFailed("PlaceholderManager", e.getMessage());
        }
    }
}

