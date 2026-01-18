package in.annupaper.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import in.annupaper.domain.model.*;
import in.annupaper.application.port.output.*;
import in.annupaper.service.MarketDataCache;
import in.annupaper.service.candle.CandleFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final BrokerRepository brokerRepo;
    private final PortfolioRepository portfolioRepo;
    private final WatchlistRepository watchlistRepo;
    private final UserBrokerRepository userBrokerRepo;
    private final WatchlistTemplateRepository watchlistTemplateRepo;
    private final WatchlistSelectedRepository watchlistSelectedRepo;
    private final DataSource dataSource;
    private final MarketDataCache marketDataCache;
    private final CandleFetcher candleFetcher;

    // OLD: public AdminService(BrokerRepository brokerRepo,
    // PortfolioRepository portfolioRepo,
    // WatchlistRepository watchlistRepo,
    // UserBrokerRepository userBrokerRepo,
    // WatchlistTemplateRepository watchlistTemplateRepo,
    // WatchlistSelectedRepository watchlistSelectedRepo,
    // DataSource dataSource) {

    // FIX: Add MarketDataCache for in-memory LTP access
    // FIX: Add CandleFetcher for historical data fetching when symbols are added to
    // watchlist
    public AdminService(BrokerRepository brokerRepo,
            PortfolioRepository portfolioRepo,
            WatchlistRepository watchlistRepo,
            UserBrokerRepository userBrokerRepo,
            WatchlistTemplateRepository watchlistTemplateRepo,
            WatchlistSelectedRepository watchlistSelectedRepo,
            DataSource dataSource,
            MarketDataCache marketDataCache,
            CandleFetcher candleFetcher) {
        this.brokerRepo = brokerRepo;
        this.portfolioRepo = portfolioRepo;
        this.watchlistRepo = watchlistRepo;
        this.userBrokerRepo = userBrokerRepo;
        this.watchlistTemplateRepo = watchlistTemplateRepo;
        this.watchlistSelectedRepo = watchlistSelectedRepo;
        this.dataSource = dataSource;
        this.marketDataCache = marketDataCache;
        this.candleFetcher = candleFetcher;
    }

    public List<Broker> getAllBrokers() {
        return brokerRepo.findAll();
    }

    public List<UserBroker> getAllUserBrokers() {
        return userBrokerRepo.findAll();
    }

    public void deleteUserBroker(String userBrokerId) {
        Optional<UserBroker> ubOpt = userBrokerRepo.findById(userBrokerId);
        if (ubOpt.isEmpty()) {
            throw new IllegalArgumentException("User-broker not found: " + userBrokerId);
        }

        UserBroker ub = ubOpt.get();
        UserBroker deleted = new UserBroker(
                ub.userBrokerId(),
                ub.userId(),
                ub.brokerId(),
                ub.role(),
                ub.credentials(),
                ub.connected(),
                ub.lastConnected(),
                ub.connectionError(),
                ub.capitalAllocated(),
                ub.maxExposure(),
                ub.maxPerTrade(),
                ub.maxOpenTrades(),
                ub.allowedSymbols(),
                ub.blockedSymbols(),
                ub.allowedProducts(),
                ub.maxDailyLoss(),
                ub.maxWeeklyLoss(),
                ub.cooldownMinutes(),
                ub.status(),
                ub.enabled(),
                ub.createdAt(),
                ub.updatedAt(),
                Instant.now(), // deleted_at
                ub.version());

        userBrokerRepo.save(deleted);
        log.info("User-broker deleted (soft): {}", userBrokerId);
    }

    public void toggleUserBrokerEnabled(String userBrokerId) {
        Optional<UserBroker> ubOpt = userBrokerRepo.findById(userBrokerId);
        if (ubOpt.isEmpty()) {
            throw new IllegalArgumentException("User-broker not found: " + userBrokerId);
        }

        UserBroker ub = ubOpt.get();
        UserBroker toggled = new UserBroker(
                ub.userBrokerId(),
                ub.userId(),
                ub.brokerId(),
                ub.role(),
                ub.credentials(),
                ub.connected(),
                ub.lastConnected(),
                ub.connectionError(),
                ub.capitalAllocated(),
                ub.maxExposure(),
                ub.maxPerTrade(),
                ub.maxOpenTrades(),
                ub.allowedSymbols(),
                ub.blockedSymbols(),
                ub.allowedProducts(),
                ub.maxDailyLoss(),
                ub.maxWeeklyLoss(),
                ub.cooldownMinutes(),
                ub.status(),
                !ub.enabled(), // toggle enabled
                ub.createdAt(),
                Instant.now(), // updated_at
                ub.deletedAt(),
                ub.version());

        userBrokerRepo.save(toggled);
        log.info("User-broker enabled toggled: {} -> {}", userBrokerId, !ub.enabled());
    }

    /**
     * Update user broker role and/or enabled status.
     */
    public UserBroker updateUserBroker(String userBrokerId, BrokerRole role, Boolean enabled) {
        Optional<UserBroker> ubOpt = userBrokerRepo.findById(userBrokerId);
        if (ubOpt.isEmpty()) {
            throw new IllegalArgumentException("User-broker not found: " + userBrokerId);
        }

        UserBroker ub = ubOpt.get();
        UserBroker updated = new UserBroker(
                ub.userBrokerId(),
                ub.userId(),
                ub.brokerId(),
                role != null ? role : ub.role(), // Update role if provided
                ub.credentials(),
                ub.connected(),
                ub.lastConnected(),
                ub.connectionError(),
                ub.capitalAllocated(),
                ub.maxExposure(),
                ub.maxPerTrade(),
                ub.maxOpenTrades(),
                ub.allowedSymbols(),
                ub.blockedSymbols(),
                ub.allowedProducts(),
                ub.maxDailyLoss(),
                ub.maxWeeklyLoss(),
                ub.cooldownMinutes(),
                ub.status(),
                enabled != null ? enabled : ub.enabled(), // Update enabled if provided
                ub.createdAt(),
                Instant.now(), // updated_at
                ub.deletedAt(),
                ub.version());

        userBrokerRepo.save(updated);
        log.info("User-broker updated: {} (role={}, enabled={})", userBrokerId, updated.role(), updated.enabled());
        return updated;
    }

    public void createBroker(String brokerId, String brokerCode, String brokerName,
            String adapterClass, JsonNode config,
            List<String> supportedExchanges, List<String> supportedProducts,
            JsonNode lotSizes, JsonNode marginRules, JsonNode rateLimits) {
        Broker broker = new Broker(
                brokerId, brokerCode, brokerName, adapterClass,
                config, supportedExchanges, supportedProducts,
                lotSizes, marginRules, rateLimits,
                "ACTIVE", Instant.now(), Instant.now(), null, 1);
        brokerRepo.insert(broker);
        log.info("Broker created: {}", brokerId);
    }

    public String createUserBroker(String userId, String brokerId, JsonNode credentials, boolean isDataBroker) {
        // Validate user is ACTIVE before creating user-broker connection
        validateUserIsActive(userId);

        String userBrokerId = "UB" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        BrokerRole role = isDataBroker ? BrokerRole.DATA : BrokerRole.EXEC;

        UserBroker userBroker = new UserBroker(
                userBrokerId, userId, brokerId, role, credentials,
                false, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                List.of(), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, 0,
                "ACTIVE", true, Instant.now(), Instant.now(), null, 1);

        userBrokerRepo.save(userBroker);
        log.info("User-broker created: {} (user={}, broker={}, role={})", userBrokerId, userId, brokerId, role);
        return userBrokerId;
    }

    public String createPortfolio(String userId, String name, BigDecimal totalCapital) {
        // Validate user is ACTIVE before creating portfolio
        validateUserIsActive(userId);

        String portfolioId = "P-" + userId;

        Portfolio portfolio = new Portfolio(
                portfolioId, userId, name, totalCapital, BigDecimal.ZERO,
                new BigDecimal("-0.05"), new BigDecimal("0.10"), 20,
                "EQUAL_WEIGHT", "ACTIVE", false,
                Instant.now(), Instant.now(), null, 1);

        portfolioRepo.insert(portfolio);
        log.info("Portfolio created: {} for user {}", portfolioId, userId);
        return portfolioId;
    }

    public List<Portfolio> getUserPortfolios(String userId) {
        return portfolioRepo.findByUserId(userId);
    }

    /**
     * Update portfolio name and/or capital.
     */
    public Portfolio updatePortfolio(String portfolioId, String name, BigDecimal capital) {
        Optional<Portfolio> portOpt = portfolioRepo.findById(portfolioId);
        if (portOpt.isEmpty()) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

        Portfolio port = portOpt.get();
        Portfolio updated = new Portfolio(
                port.portfolioId(),
                port.userId(),
                name != null ? name : port.name(), // Update name if provided
                capital != null ? capital : port.totalCapital(), // Update capital if provided
                port.reservedCapital(),
                port.maxPortfolioLogLoss(),
                port.maxSymbolWeight(),
                port.maxSymbols(),
                port.allocationMode(),
                port.status(),
                port.paused(),
                port.createdAt(),
                Instant.now(), // updated_at
                port.deletedAt(),
                port.version());

        portfolioRepo.update(updated);
        log.info("Portfolio updated: {} (name={}, capital={})", portfolioId, updated.name(), updated.totalCapital());
        return updated;
    }

    /**
     * Soft delete portfolio by setting deleted_at timestamp.
     */
    public void deletePortfolio(String portfolioId) {
        Optional<Portfolio> portOpt = portfolioRepo.findById(portfolioId);
        if (portOpt.isEmpty()) {
            throw new IllegalArgumentException("Portfolio not found: " + portfolioId);
        }

        portfolioRepo.delete(portfolioId);
        log.info("Portfolio deleted (soft): {}", portfolioId);
    }

    // public void addWatchlistSymbol(String userBrokerId, String symbol) {
    // Watchlist watchlist = new Watchlist(
    // null, userBrokerId, symbol, true,
    // Instant.now(), Instant.now(), null, 1
    // );
    //
    // watchlistRepo.insert(watchlist);
    // log.info("Watchlist symbol added: {} for {}", symbol, userBrokerId);
    // }

    public void addWatchlistSymbol(String userBrokerId, String symbol) {
        // Strip -EQ suffix if present
        String cleanSymbol = symbol.replace("-EQ", "");

        // Fetch lot_size and tick_size from instruments table
        Integer lotSize = getLotSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);
        java.math.BigDecimal tickSize = getTickSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);

        Watchlist watchlist = new Watchlist(
                null, userBrokerId, cleanSymbol,
                lotSize,
                tickSize,
                true, // is_custom = true (manually added by admin)
                true, // enabled
                Instant.now(), Instant.now(),
                null, // last_synced_at = null (not synced, manually added)
                null, // deleted_at
                1,
                null, // lastPrice
                null // lastTickTime
        );

        watchlistRepo.insert(watchlist);
        log.info("Watchlist symbol added: {} for {} (lot_size={}, tick_size={}, custom)", cleanSymbol, userBrokerId,
                lotSize, tickSize);

        // FIX: Fetch historical DAILY candles for this symbol (async, non-blocking)
        fetchHistoricalDailyCandles(userBrokerId, cleanSymbol);
    }

    public List<Watchlist> getAllWatchlists() {
        return watchlistRepo.findAll();
    }

    public List<Watchlist> getUserWatchlist(String userId) {
        return watchlistRepo.findByUserId(userId);
    }

    public void deleteWatchlistItem(Long id) {
        watchlistRepo.delete(id);
        log.info("Watchlist item deleted: {}", id);
    }

    public void toggleWatchlistItem(Long id, boolean enabled) {
        watchlistRepo.toggleEnabled(id, enabled);
        log.info("Watchlist item {} enabled set to: {}", id, enabled);
    }

    /**
     * Update watchlist item lot size, tick size, and/or enabled status.
     */
    public Watchlist updateWatchlistItem(Long id, Integer lotSize, BigDecimal tickSize, Boolean enabled) {
        Optional<Watchlist> watchlistOpt = watchlistRepo.findById(id);
        if (watchlistOpt.isEmpty()) {
            throw new IllegalArgumentException("Watchlist item not found: " + id);
        }

        Watchlist item = watchlistOpt.get();
        Watchlist updated = new Watchlist(
                item.id(),
                item.userBrokerId(),
                item.symbol(),
                lotSize != null ? lotSize : item.lotSize(), // Update lot size if provided
                tickSize != null ? tickSize : item.tickSize(), // Update tick size if provided
                item.isCustom(),
                enabled != null ? enabled : item.enabled(), // Update enabled if provided
                item.addedAt(),
                Instant.now(), // updated_at
                item.lastSyncedAt(),
                item.deletedAt(),
                item.version(),
                item.lastPrice(),
                item.lastTickTime());

        watchlistRepo.save(updated);
        log.info("Watchlist item updated: {} (lot_size={}, tick_size={}, enabled={})",
                id, updated.lotSize(), updated.tickSize(), updated.enabled());
        return updated;
    }

    public List<User> getAllUsers() {
        String sql = "SELECT user_id, email, display_name, role, status, created_at FROM users ORDER BY created_at DESC";
        List<User> users = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(new User(
                        rs.getString("user_id"),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()));
            }
        } catch (Exception e) {
            log.error("Error getting all users: {}", e.getMessage(), e);
        }

        return users;
    }

    /**
     * Get current system-wide data broker configuration.
     */
    public DataBrokerInfo getDataBroker() {
        return userBrokerRepo.findDataBroker()
                .map(ub -> {
                    Broker broker = brokerRepo.findById(ub.brokerId()).orElse(null);
                    return new DataBrokerInfo(
                            ub.userBrokerId(),
                            ub.userId(),
                            ub.brokerId(),
                            broker != null ? broker.brokerName() : ub.brokerId(),
                            ub.connected(),
                            ub.lastConnected(),
                            ub.connectionError(),
                            ub.status());
                })
                .orElse(null);
    }

    /**
     * Configure system-wide data broker (creates or updates).
     * Only ONE data broker should exist system-wide.
     */
    public String configureDataBroker(String adminUserId, String brokerId, JsonNode credentials) {
        // Check if data broker already exists
        var existingDataBroker = userBrokerRepo.findDataBroker();

        if (existingDataBroker.isPresent()) {
            // Update existing data broker
            UserBroker existing = existingDataBroker.get();
            UserBroker updated = new UserBroker(
                    existing.userBrokerId(),
                    adminUserId, // Update to current admin
                    brokerId,
                    BrokerRole.DATA,
                    credentials,
                    false, // Will reconnect
                    null,
                    null,
                    existing.capitalAllocated(),
                    existing.maxExposure(),
                    existing.maxPerTrade(),
                    existing.maxOpenTrades(),
                    existing.allowedSymbols(),
                    existing.blockedSymbols(),
                    existing.allowedProducts(),
                    existing.maxDailyLoss(),
                    existing.maxWeeklyLoss(),
                    existing.cooldownMinutes(),
                    "ACTIVE",
                    true,
                    existing.createdAt(),
                    Instant.now(),
                    null,
                    existing.version());
            userBrokerRepo.save(updated);
            log.info("Data broker updated: {} (broker={}, admin={})", existing.userBrokerId(), brokerId, adminUserId);
            return existing.userBrokerId();
        } else {
            // Create new data broker
            String userBrokerId = "UB_DATA_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            UserBroker dataBroker = new UserBroker(
                    userBrokerId,
                    adminUserId,
                    brokerId,
                    BrokerRole.DATA,
                    credentials,
                    false,
                    null,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    "ACTIVE",
                    true,
                    Instant.now(),
                    Instant.now(),
                    null,
                    1);
            userBrokerRepo.save(dataBroker);
            log.info("Data broker created: {} (broker={}, admin={})", userBrokerId, brokerId, adminUserId);
            return userBrokerId;
        }
    }

    /**
     * Test broker connection using OAuth session token.
     * Returns connection test result with status and error details.
     */
    public ConnectionTestResult testBrokerConnection(String userBrokerId) {
        log.info("Testing broker connection for userBrokerId={}", userBrokerId);

        try {
            // Get user broker
            Optional<UserBroker> userBrokerOpt = userBrokerRepo.findById(userBrokerId);
            if (userBrokerOpt.isEmpty()) {
                return new ConnectionTestResult(false, "User broker not found", null, null);
            }

            UserBroker userBroker = userBrokerOpt.get();

            // Get broker details
            Optional<Broker> brokerOpt = brokerRepo.findById(userBroker.brokerId());
            if (brokerOpt.isEmpty()) {
                return new ConnectionTestResult(false, "Broker definition not found", null, null);
            }

            Broker broker = brokerOpt.get();
            log.info("Testing connection to broker: {} ({})", broker.brokerName(), broker.brokerCode());

            // For now, return success with session info
            // The actual connection test would be done by FyersAdapter when it's used
            return new ConnectionTestResult(
                    true,
                    "Broker credentials and session configured. Connection will be established when fetching data.",
                    broker.brokerCode(),
                    broker.brokerName());

        } catch (Exception e) {
            log.error("Failed to test broker connection: {}", e.getMessage(), e);
            return new ConnectionTestResult(false, "Connection test failed: " + e.getMessage(), null, null);
        }
    }

    public record User(String userId, String email, String displayName, String role, String status, Instant createdAt) {
    }

    public record DataBrokerInfo(
            String userBrokerId,
            String userId,
            String brokerId,
            String brokerName,
            boolean connected,
            Instant lastConnected,
            String connectionError,
            String status) {
    }

    public record ConnectionTestResult(
            boolean success,
            String message,
            String brokerCode,
            String brokerName) {
    }

    /**
     * Get user broker by ID.
     */
    public Optional<UserBroker> getUserBrokerById(String userBrokerId) {
        return userBrokerRepo.findById(userBrokerId);
    }

    /**
     * Get broker by ID.
     */
    public Optional<Broker> getBrokerById(String brokerId) {
        return brokerRepo.findById(brokerId);
    }

    /**
     * Update user broker.
     */
    public void updateUserBroker(UserBroker userBroker) {
        userBrokerRepo.save(userBroker);
    }

    // ============================================================
    // Watchlist Template Management (Level 1)
    // ============================================================

    /**
     * Get all active watchlist templates.
     */
    public List<WatchlistTemplate> getAllTemplates() {
        return watchlistTemplateRepo.findAllActive();
    }

    /**
     * Get symbols for a specific template.
     */
    public List<WatchlistTemplateSymbol> getTemplateSymbols(String templateId) {
        return watchlistTemplateRepo.findSymbolsByTemplateId(templateId);
    }

    /**
     * Get template by ID.
     */
    public Optional<WatchlistTemplate> getTemplateById(String templateId) {
        return watchlistTemplateRepo.findById(templateId);
    }

    /**
     * Create a new watchlist template.
     */
    public String createTemplate(String templateName, String description, int displayOrder) {
        String templateId = "TPL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WatchlistTemplate template = new WatchlistTemplate(
                templateId,
                templateName,
                description,
                displayOrder,
                true,
                Instant.now(),
                Instant.now(),
                null,
                1);
        watchlistTemplateRepo.insert(template);
        return templateId;
    }

    /**
     * Add a symbol to a template.
     */
    public void addSymbolToTemplate(String templateId, String symbol, int displayOrder) {
        WatchlistTemplateSymbol templateSymbol = new WatchlistTemplateSymbol(
                0L, // Auto-generated
                templateId,
                symbol,
                displayOrder,
                Instant.now());
        watchlistTemplateRepo.insertSymbol(templateSymbol);
    }

    /**
     * Delete a symbol from a template.
     */
    public void deleteSymbolFromTemplate(long symbolId) {
        watchlistTemplateRepo.deleteSymbol(symbolId);
    }

    /**
     * Delete a template (soft delete).
     */
    public void deleteTemplate(String templateId) {
        watchlistTemplateRepo.delete(templateId);
    }

    // ============================================================
    // Watchlist Selected Management (Level 2)
    // ============================================================

    /**
     * Create a selected watchlist from a template.
     * Copies symbols from the template to the selected watchlist.
     */
    public String createSelectedWatchlist(String sourceTemplateId, List<String> selectedSymbols) {
        // Get source template
        Optional<WatchlistTemplate> templateOpt = watchlistTemplateRepo.findById(sourceTemplateId);
        if (templateOpt.isEmpty()) {
            throw new IllegalArgumentException("Template not found: " + sourceTemplateId);
        }

        WatchlistTemplate template = templateOpt.get();
        String selectedId = "SEL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // OLD: String name = template.templateName() + "-selected";
        // FIX: Append selectedId suffix to ensure uniqueness (avoid duplicate key
        // constraint)
        String name = template.templateName() + "-selected-" + selectedId;

        // Create selected watchlist record
        WatchlistSelected selected = new WatchlistSelected(
                selectedId,
                name,
                sourceTemplateId,
                "Selected from " + template.templateName(),
                true,
                Instant.now(),
                Instant.now(),
                null,
                1);

        watchlistSelectedRepo.insert(selected);
        log.info("Created selected watchlist: {} from template {}", selectedId, sourceTemplateId);

        // Add selected symbols
        int displayOrder = 0;
        for (String symbol : selectedSymbols) {
            WatchlistSelectedSymbol symbolRecord = new WatchlistSelectedSymbol(
                    0L, // Auto-generated
                    selectedId,
                    symbol,
                    displayOrder++,
                    Instant.now());
            watchlistSelectedRepo.insertSymbol(symbolRecord);
        }

        log.info("Added {} symbols to selected watchlist {}", selectedSymbols.size(), selectedId);
        return selectedId;
    }

    /**
     * Get all active selected watchlists.
     */
    public List<WatchlistSelected> getAllSelectedWatchlists() {
        return watchlistSelectedRepo.findAllActive();
    }

    /**
     * Get symbols for a selected watchlist.
     */
    public List<WatchlistSelectedSymbol> getSelectedWatchlistSymbols(String selectedId) {
        return watchlistSelectedRepo.findSymbolsBySelectedId(selectedId);
    }

    /**
     * Update symbols in a selected watchlist (replace all).
     */
    public void updateSelectedWatchlistSymbols(String selectedId, List<String> newSymbols) {
        // Delete all existing symbols
        watchlistSelectedRepo.deleteAllSymbols(selectedId);

        // Add new symbols
        int displayOrder = 0;
        for (String symbol : newSymbols) {
            WatchlistSelectedSymbol symbolRecord = new WatchlistSelectedSymbol(
                    0L, // Auto-generated
                    selectedId,
                    symbol,
                    displayOrder++,
                    Instant.now());
            watchlistSelectedRepo.insertSymbol(symbolRecord);
        }

        log.info("Updated selected watchlist {} with {} symbols", selectedId, newSymbols.size());
    }

    /**
     * Delete a selected watchlist (soft delete).
     */
    public void deleteSelectedWatchlist(String selectedId) {
        watchlistSelectedRepo.delete(selectedId);
        log.info("Deleted selected watchlist: {}", selectedId);
    }

    // ============================================================
    // Level 3: System Default (Auto-Merged)
    // ============================================================

    /**
     * Get merged default watchlist (Level 3).
     * This is the union of all active selected watchlists, deduplicated.
     */
    public List<String> getMergedDefaultWatchlist() {
        return watchlistSelectedRepo.findMergedDefaultSymbols();
    }

    // ============================================================
    // Level 4: User-Broker Watchlists (Auto-Sync)
    // ============================================================

    /**
     * Sync Level 3 default watchlist to a user-broker's watchlist (Level 4).
     * Creates auto-named watchlist for the user-broker.
     * Populates lot_size from instruments table.
     */
    public void syncDefaultToUserBroker(String userBrokerId) {
        // Get merged default symbols
        List<String> defaultSymbols = getMergedDefaultWatchlist();

        // Get existing watchlist for this user-broker
        List<Watchlist> existing = watchlistRepo.findByUserBrokerId(userBrokerId);

        // Delete all non-custom watchlist entries
        for (Watchlist w : existing) {
            if (!w.isCustom()) {
                watchlistRepo.delete(w.id());
            }
        }

        // Get broker_id for this user-broker to look up instruments
        String brokerId = BrokerIds.FYERS; // Default, can be made dynamic later

        // Add all default symbols with lot_size from instruments table
        for (String symbol : defaultSymbols) {
            // Strip -EQ suffix if present
            String cleanSymbol = symbol.replace("-EQ", "");

            // Fetch lot_size and tick_size from instruments table
            Integer lotSize = getLotSizeFromInstruments(brokerId, cleanSymbol);
            java.math.BigDecimal tickSize = getTickSizeFromInstruments(brokerId, cleanSymbol);

            Watchlist watchlist = new Watchlist(
                    null, // Auto-generated
                    userBrokerId,
                    cleanSymbol,
                    lotSize,
                    tickSize,
                    false, // is_custom = false (synced from default)
                    true, // enabled
                    Instant.now(),
                    Instant.now(),
                    Instant.now(), // last_synced_at
                    null, // deleted_at
                    1,
                    null, // lastPrice
                    null // lastTickTime
            );
            watchlistRepo.insert(watchlist);

            // FIX: Fetch historical DAILY candles for this symbol (async, non-blocking)
            fetchHistoricalDailyCandles(userBrokerId, cleanSymbol);
        }

        log.info("Synced {} default symbols to user-broker {} (historical data fetch triggered)", defaultSymbols.size(),
                userBrokerId);
    }

    /**
     * Helper method to fetch lot_size from instruments table.
     */
    // OLD: private Integer getLotSizeFromInstruments(String brokerId, String
    // symbol) {
    // FIX: Made public so API handlers can enrich watchlist responses
    public Integer getLotSizeFromInstruments(String brokerId, String symbol) {
        String sql = "SELECT lot_size FROM instruments WHERE broker_id = ? AND UPPER(trading_symbol) LIKE ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerId);
            ps.setString(2, "%:" + symbol.toUpperCase() + "%");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("lot_size", Integer.class);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch lot_size for symbol {}: {}", symbol, e.getMessage());
        }

        return 1; // Default lot size if not found
    }

    // OLD: private java.math.BigDecimal getTickSizeFromInstruments(String brokerId,
    // String symbol) {
    // FIX: Made public so API handlers can enrich watchlist responses
    public java.math.BigDecimal getTickSizeFromInstruments(String brokerId, String symbol) {
        String sql = "SELECT tick_size FROM instruments WHERE broker_id = ? AND UPPER(trading_symbol) LIKE ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerId);
            ps.setString(2, "%:" + symbol.toUpperCase() + "%");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("tick_size");
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch tick_size for symbol {}: {}", symbol, e.getMessage());
        }

        return new java.math.BigDecimal("0.05"); // Default tick size if not found
    }

    /**
     * Sync Level 3 default to ALL user-brokers.
     * Call this after any change to selected watchlists.
     */
    public void syncDefaultToAllUserBrokers() {
        // Get all user-brokers
        String sql = "SELECT user_broker_id FROM user_brokers WHERE status = 'ACTIVE' AND deleted_at IS NULL";
        List<String> userBrokerIds = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                userBrokerIds.add(rs.getString("user_broker_id"));
            }
        } catch (Exception e) {
            log.error("Error getting user-brokers for sync: {}", e.getMessage(), e);
            return;
        }

        // Sync to each user-broker
        for (String userBrokerId : userBrokerIds) {
            try {
                syncDefaultToUserBroker(userBrokerId);
            } catch (Exception e) {
                log.error("Failed to sync to user-broker {}: {}", userBrokerId, e.getMessage(), e);
            }
        }

        log.info("Synced default watchlist to {} user-brokers", userBrokerIds.size());
    }

    /**
     * Get Market Watch data for a specific user.
     * Returns all enabled watchlist entries for user's brokers, enriched with
     * latest prices from cache.
     */
    public List<Watchlist> getMarketWatchForUser(String userId) {
        List<Watchlist> watchlists = watchlistRepo.findByUserId(userId);

        // FIX: Enrich with latest prices from in-memory cache
        return watchlists.stream()
                .map(this::enrichWithLatestPrice)
                .toList();
    }

    /**
     * Get Market Watch data for admin.
     * Returns all unique enabled watchlist entries across all users (superset),
     * enriched with latest prices from cache.
     */
    public List<Watchlist> getMarketWatchForAdmin() {
        // Query all enabled watchlist entries, grouped by symbol to get unique entries
        String sql = """
                SELECT DISTINCT ON (symbol)
                    id, user_broker_id, symbol, lot_size, tick_size, is_custom, enabled,
                    added_at, updated_at, last_synced_at, deleted_at, version,
                    last_price, last_tick_time
                FROM watchlist
                WHERE enabled = true AND deleted_at IS NULL
                ORDER BY symbol ASC, last_tick_time DESC NULLS LAST
                """;

        List<Watchlist> watchlists = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                watchlists.add(mapWatchlistRow(rs));
            }
        } catch (Exception e) {
            log.error("Error getting market watch for admin: {}", e.getMessage(), e);
        }

        // FIX: Enrich with latest prices from in-memory cache
        return watchlists.stream()
                .map(this::enrichWithLatestPrice)
                .toList();
    }

    /**
     * Get comprehensive Market Watch data for a specific user.
     * Returns all enabled watchlist entries with full market stats (52-week
     * high/low, OHLC, volume).
     */
    public List<MarketWatchEntry> getMarketWatchEntriesForUser(String userId) {
        List<Watchlist> watchlists = watchlistRepo.findByUserId(userId);

        // Enrich with comprehensive market stats
        return watchlists.stream()
                .map(this::enrichWithMarketStats)
                .toList();
    }

    /**
     * Get comprehensive Market Watch data for admin.
     * Returns all unique enabled watchlist entries with full market stats (52-week
     * high/low, OHLC, volume).
     */
    public List<MarketWatchEntry> getMarketWatchEntriesForAdmin() {
        // Query all enabled watchlist entries, grouped by symbol to get unique entries
        String sql = """
                SELECT DISTINCT ON (symbol)
                    id, user_broker_id, symbol, lot_size, tick_size, is_custom, enabled,
                    added_at, updated_at, last_synced_at, deleted_at, version,
                    last_price, last_tick_time
                FROM watchlist
                WHERE enabled = true AND deleted_at IS NULL
                ORDER BY symbol ASC, last_tick_time DESC NULLS LAST
                """;

        List<Watchlist> watchlists = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                watchlists.add(mapWatchlistRow(rs));
            }
        } catch (Exception e) {
            log.error("Error getting market watch for admin: {}", e.getMessage(), e);
        }

        // Enrich with comprehensive market stats
        return watchlists.stream()
                .map(this::enrichWithMarketStats)
                .toList();
    }

    /**
     * Enrich watchlist entry with latest price from in-memory cache.
     * Fallback: If market is closed or no ticks yet, use previous day's close
     * price.
     */
    private Watchlist enrichWithLatestPrice(Watchlist w) {
        // Try to get real-time tick from cache (if market is open and ticks are
        // flowing)
        MarketDataCache.TickData latestTick = marketDataCache.getLatestTick(w.symbol());

        if (latestTick != null) {
            // Use latest price from cache (real-time during market hours)
            return new Watchlist(
                    w.id(), w.userBrokerId(), w.symbol(), w.lotSize(), w.tickSize(),
                    w.isCustom(), w.enabled(), w.addedAt(), w.updatedAt(),
                    w.lastSyncedAt(), w.deletedAt(), w.version(),
                    latestTick.lastPrice(), latestTick.timestamp());
        }

        // FALLBACK: Market closed or no ticks yet - use previous day's close price
        try {
            PreviousClose prevClose = getLastClosePrice(w.symbol());
            if (prevClose != null) {
                return new Watchlist(
                        w.id(), w.userBrokerId(), w.symbol(), w.lotSize(), w.tickSize(),
                        w.isCustom(), w.enabled(), w.addedAt(), w.updatedAt(),
                        w.lastSyncedAt(), w.deletedAt(), w.version(),
                        prevClose.closePrice(), prevClose.timestamp());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch previous close for {}: {}", w.symbol(), e.getMessage());
        }

        // Final fallback: return DB values (may have null lastPrice)
        return w;
    }

    /**
     * Enrich watchlist entry with comprehensive market statistics.
     * Includes: lastPrice (real-time or close), 52-week high/low, daily
     * OHLC+Volume.
     */
    private MarketWatchEntry enrichWithMarketStats(Watchlist w) {
        // 1. Get latest price (real-time or fallback to previous close)
        BigDecimal lastPrice = null;
        Instant lastTickTime = null;

        // Try real-time cache first
        MarketDataCache.TickData latestTick = marketDataCache.getLatestTick(w.symbol());
        if (latestTick != null) {
            lastPrice = latestTick.lastPrice();
            lastTickTime = latestTick.timestamp();
        } else {
            // Fallback to previous day's close
            try {
                PreviousClose prevClose = getLastClosePrice(w.symbol());
                if (prevClose != null) {
                    lastPrice = prevClose.closePrice();
                    lastTickTime = prevClose.timestamp();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch previous close for {}: {}", w.symbol(), e.getMessage());
            }
        }

        // 2. Get 52-week high/low
        WeekStats52 weekStats = get52WeekStats(w.symbol());
        BigDecimal weekHigh52 = weekStats != null ? weekStats.weekHigh52() : null;
        BigDecimal weekLow52 = weekStats != null ? weekStats.weekLow52() : null;

        // 3. Get today's DAILY candle OHLC+Volume
        DailyOHLCV daily = getTodayDailyCandle(w.symbol());
        BigDecimal dailyOpen = daily != null ? daily.open() : null;
        BigDecimal dailyHigh = daily != null ? daily.high() : null;
        BigDecimal dailyLow = daily != null ? daily.low() : null;
        BigDecimal dailyClose = daily != null ? daily.close() : null;
        Long dailyVolume = daily != null ? daily.volume() : null;

        // 4. Calculate overnight change (today's open - yesterday's close)
        BigDecimal overnightChange = null;
        BigDecimal overnightChangePercent = null;

        if (dailyOpen != null) {
            BigDecimal yesterdayClose = getYesterdayClose(w.symbol());
            if (yesterdayClose != null && yesterdayClose.compareTo(BigDecimal.ZERO) != 0) {
                overnightChange = dailyOpen.subtract(yesterdayClose);
                overnightChangePercent = overnightChange
                        .divide(yesterdayClose, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
        }

        return new MarketWatchEntry(
                w.id(), w.userBrokerId(), w.symbol(), w.lotSize(), w.tickSize(),
                w.isCustom(), w.enabled(),
                lastPrice, lastTickTime,
                weekHigh52, weekLow52,
                dailyOpen, dailyHigh, dailyLow, dailyClose, dailyVolume,
                overnightChange, overnightChangePercent);
    }

    /**
     * Get the most recent DAILY candle close price for a symbol.
     * Used as fallback when market is closed or no real-time data available.
     *
     * Returns the previous trading day's close price, which is the standard
     * LTP when market is closed.
     */
    private PreviousClose getLastClosePrice(String symbol) {
        String sql = """
                SELECT close, ts
                FROM candles
                WHERE symbol = ? AND timeframe = 'DAILY'
                ORDER BY ts DESC
                LIMIT 1
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal closePrice = rs.getBigDecimal("close");
                    Instant timestamp = rs.getTimestamp("ts").toInstant();
                    return new PreviousClose(closePrice, timestamp);
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching last close for {}: {}", symbol, e.getMessage());
        }

        return null;
    }

    /**
     * Previous close price record for fallback.
     */
    private record PreviousClose(BigDecimal closePrice, Instant timestamp) {
    }

    /**
     * Comprehensive market watch entry with all required fields.
     * Includes real-time/close price, 52-week stats, daily OHLC+Volume, and
     * overnight change.
     */
    public record MarketWatchEntry(
            Long id,
            String userBrokerId,
            String symbol,
            Integer lotSize,
            BigDecimal tickSize,
            boolean isCustom,
            boolean enabled,
            BigDecimal lastPrice,
            Instant lastTickTime,
            BigDecimal weekHigh52,
            BigDecimal weekLow52,
            BigDecimal dailyOpen,
            BigDecimal dailyHigh,
            BigDecimal dailyLow,
            BigDecimal dailyClose,
            Long dailyVolume,
            BigDecimal overnightChange,
            BigDecimal overnightChangePercent) {
    }

    /**
     * Today's DAILY candle OHLC and Volume.
     */
    private record DailyOHLCV(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume) {
    }

    /**
     * Get 52-week high and low from daily candles.
     * Fetches last 252 trading days (approximately 52 weeks).
     */
    private WeekStats52 get52WeekStats(String symbol) {
        String sql = """
                SELECT MAX(high) as week_high_52, MIN(low) as week_low_52
                FROM (
                    SELECT high, low
                    FROM candles
                    WHERE symbol = ? AND timeframe = 'DAILY'
                    ORDER BY ts DESC
                    LIMIT 252
                ) AS recent_candles
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal weekHigh52 = rs.getBigDecimal("week_high_52");
                    BigDecimal weekLow52 = rs.getBigDecimal("week_low_52");
                    if (weekHigh52 != null && weekLow52 != null) {
                        return new WeekStats52(weekHigh52, weekLow52);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching 52-week stats for {}: {}", symbol, e.getMessage());
        }

        return null;
    }

    /**
     * 52-week high/low statistics.
     */
    private record WeekStats52(BigDecimal weekHigh52, BigDecimal weekLow52) {
    }

    /**
     * Get yesterday's close price for overnight change calculation.
     * Returns null if no previous candle exists.
     */
    private BigDecimal getYesterdayClose(String symbol) {
        String sql = """
                SELECT close
                FROM candles
                WHERE symbol = ? AND timeframe = 'DAILY'
                AND ts < CURRENT_DATE
                ORDER BY ts DESC
                LIMIT 1
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("close");
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching yesterday's close for {}: {}", symbol, e.getMessage());
        }

        return null;
    }

    /**
     * Get today's DAILY candle OHLC and Volume.
     * Returns null if no candle exists for today.
     */
    private DailyOHLCV getTodayDailyCandle(String symbol) {
        String sql = """
                SELECT open, high, low, close, volume
                FROM candles
                WHERE symbol = ? AND timeframe = 'DAILY'
                AND ts >= CURRENT_DATE
                ORDER BY ts DESC
                LIMIT 1
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DailyOHLCV(
                            rs.getBigDecimal("open"),
                            rs.getBigDecimal("high"),
                            rs.getBigDecimal("low"),
                            rs.getBigDecimal("close"),
                            rs.getLong("volume"));
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching today's daily candle for {}: {}", symbol, e.getMessage());
        }

        return null;
    }

    /**
     * Fetch historical DAILY candles for a symbol when it's added to watchlist.
     * Fetches ~252 TRADING DAY candles (approximately 52 weeks of trading)
     * excluding today.
     * Note: Fetches 380 calendar days to account for weekends/holidays to ensure
     * 252 trading candles.
     * Runs asynchronously to avoid blocking watchlist operations.
     *
     * @param userBrokerId The user-broker ID to use for fetching data
     * @param symbol       The symbol to fetch historical data for
     */
    private void fetchHistoricalDailyCandles(String userBrokerId, String symbol) {
        CompletableFuture.runAsync(() -> {
            try {
                // Get broker code from userBrokerId
                Optional<UserBroker> ubOpt = userBrokerRepo.findById(userBrokerId);
                if (ubOpt.isEmpty()) {
                    log.warn("Cannot fetch historical data: user-broker not found: {}", userBrokerId);
                    return;
                }

                UserBroker ub = ubOpt.get();
                Optional<Broker> brokerOpt = brokerRepo.findById(ub.brokerId());
                if (brokerOpt.isEmpty()) {
                    log.warn("Cannot fetch historical data: broker not found: {}", ub.brokerId());
                    return;
                }

                String brokerCode = brokerOpt.get().brokerCode();

                // Calculate date range: We need 252 TRADING DAY candles (not calendar days)
                // Markets trade ~252 days per year out of 365 calendar days
                // To get 252 trading candles, fetch 380 calendar days to account for
                // weekends/holidays
                Instant to = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS);
                Instant from = to.minus(380, ChronoUnit.DAYS);

                log.info(
                        "Fetching historical DAILY candles for {} via {} (from {} to {}) - target: 252 trading candles",
                        symbol, brokerCode, from, to);

                // Fetch DAILY candles (CandleFetcher will store them in DB)
                candleFetcher.fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.DAILY, from, to);

                log.info("Successfully fetched historical DAILY candles for {}", symbol);

            } catch (Exception e) {
                log.error("Failed to fetch historical DAILY candles for {}: {}", symbol, e.getMessage(), e);
            }
        });
    }

    private Watchlist mapWatchlistRow(ResultSet rs) throws Exception {
        return new Watchlist(
                rs.getLong("id"),
                rs.getString("user_broker_id"),
                rs.getString("symbol"),
                rs.getObject("lot_size", Integer.class),
                rs.getBigDecimal("tick_size"),
                rs.getBoolean("is_custom"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("added_at") != null ? rs.getTimestamp("added_at").toInstant() : Instant.now(),
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : Instant.now(),
                rs.getTimestamp("last_synced_at") != null ? rs.getTimestamp("last_synced_at").toInstant() : null,
                rs.getTimestamp("deleted_at") != null ? rs.getTimestamp("deleted_at").toInstant() : null,
                rs.getInt("version"),
                rs.getBigDecimal("last_price"),
                rs.getTimestamp("last_tick_time") != null ? rs.getTimestamp("last_tick_time").toInstant() : null);
    }

    // ═══════════════════════════════════════════════════════════════
    // USER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update user details (display name, role).
     */
    public void updateUser(String userId, String displayName, String role) {
        String sql = "UPDATE users SET display_name = ?, role = ?, updated_at = ?, version = version + 1 WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, displayName);
            stmt.setString(2, role);
            stmt.setTimestamp(3, java.sql.Timestamp.from(Instant.now()));
            stmt.setString(4, userId);

            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("User not found: " + userId);
            }

            log.info("Updated user: {} - displayName: {}, role: {}", userId, displayName, role);
        } catch (Exception e) {
            log.error("Failed to update user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to update user", e);
        }
    }

    /**
     * Toggle user status between ACTIVE and SUSPENDED.
     */
    public String toggleUserStatus(String userId) {
        String selectSql = "SELECT status FROM users WHERE user_id = ?";
        String updateSql = "UPDATE users SET status = ?, updated_at = ?, version = version + 1 WHERE user_id = ?";

        try (Connection conn = dataSource.getConnection()) {
            String currentStatus;

            // Get current status
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("User not found: " + userId);
                    }
                    currentStatus = rs.getString("status");
                }
            }

            // Toggle status
            String newStatus = "ACTIVE".equals(currentStatus) ? "SUSPENDED" : "ACTIVE";

            // Update status
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, newStatus);
                stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()));
                stmt.setString(3, userId);
                stmt.executeUpdate();
            }

            log.info("Toggled user {} status: {} -> {}", userId, currentStatus, newStatus);
            return newStatus;

        } catch (Exception e) {
            log.error("Failed to toggle user {} status: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to toggle user status", e);
        }
    }

    /**
     * Soft delete user by setting deleted_at and status to DELETED.
     */
    public void deleteUser(String userId) {
        String sql = "UPDATE users SET status = 'DELETED', deleted_at = ?, updated_at = ?, version = version + 1 WHERE user_id = ? AND deleted_at IS NULL";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant now = Instant.now();
            stmt.setTimestamp(1, java.sql.Timestamp.from(now));
            stmt.setTimestamp(2, java.sql.Timestamp.from(now));
            stmt.setString(3, userId);

            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("User not found or already deleted: " + userId);
            }

            log.info("Soft deleted user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to delete user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // USER STATUS VALIDATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate that a user is ACTIVE before allowing data creation.
     * Throws IllegalStateException if user is SUSPENDED or DELETED.
     */
    public void validateUserIsActive(String userId) {
        String sql = "SELECT status FROM users WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("User not found: " + userId);
                }

                String status = rs.getString("status");
                if (!"ACTIVE".equals(status)) {
                    throw new IllegalStateException(
                            "Cannot create data for user with status: " + status
                                    + ". Only ACTIVE users can have data created.");
                }
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate user status for {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to validate user status", e);
        }
    }
}
