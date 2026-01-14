package in.annupaper.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * MTF Config Migration - Creates MTF configuration tables on startup.
 *
 * Creates two tables:
 * - mtf_global_config: Global default configuration (single row)
 * - mtf_symbol_config: Per-symbol configuration overrides (nullable fields)
 */
public final class MtfConfigMigration {
    private static final Logger log = LoggerFactory.getLogger(MtfConfigMigration.class);

    private final DataSource dataSource;

    public MtfConfigMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Run migration - creates tables if they don't exist, adds new columns.
     */
    public void migrate() {
        log.info("[MTF MIGRATION] Starting MTF config tables migration");

        try (Connection conn = dataSource.getConnection()) {
            boolean globalTableExists = tableExists(conn, "mtf_global_config");
            boolean symbolTableExists = tableExists(conn, "mtf_symbol_config");

            if (!globalTableExists) {
                log.info("[MTF MIGRATION] Creating mtf_global_config table...");
                createGlobalConfigTable(conn);
                insertDefaultGlobalConfig(conn);
                log.info("[MTF MIGRATION] ✓ mtf_global_config table created");
            } else {
                log.info("[MTF MIGRATION] mtf_global_config table already exists");
                // Run schema updates for existing table
                updateGlobalConfigSchema(conn);
            }

            if (!symbolTableExists) {
                log.info("[MTF MIGRATION] Creating mtf_symbol_config table...");
                createSymbolConfigTable(conn);
                log.info("[MTF MIGRATION] ✓ mtf_symbol_config table created");
            } else {
                log.info("[MTF MIGRATION] mtf_symbol_config table already exists");
                // Run schema updates for existing table
                updateSymbolConfigSchema(conn);
            }

            log.info("[MTF MIGRATION] Migration completed successfully");

        } catch (Exception e) {
            log.error("[MTF MIGRATION] Migration failed: {}", e.getMessage(), e);
            throw new RuntimeException("MTF migration failed", e);
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet rs = metadata.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private void createGlobalConfigTable(Connection conn) throws Exception {
        String sql = """
            CREATE TABLE mtf_global_config (
                config_id VARCHAR(50) PRIMARY KEY,

                -- HTF (Higher Timeframe) Config
                htf_candle_count INT NOT NULL DEFAULT 175,
                htf_candle_minutes INT NOT NULL DEFAULT 125,
                htf_weight NUMERIC(5,2) NOT NULL DEFAULT 0.50,

                -- ITF (Intermediate Timeframe) Config
                itf_candle_count INT NOT NULL DEFAULT 75,
                itf_candle_minutes INT NOT NULL DEFAULT 25,
                itf_weight NUMERIC(5,2) NOT NULL DEFAULT 0.30,

                -- LTF (Lower Timeframe) Config
                ltf_candle_count INT NOT NULL DEFAULT 375,
                ltf_candle_minutes INT NOT NULL DEFAULT 1,
                ltf_weight NUMERIC(5,2) NOT NULL DEFAULT 0.20,

                -- Zone Detection
                buy_zone_pct NUMERIC(5,2) NOT NULL DEFAULT 0.35,

                -- Confluence Settings
                min_confluence_type VARCHAR(20) NOT NULL DEFAULT 'TRIPLE',
                strength_threshold_very_strong NUMERIC(5,2) NOT NULL DEFAULT 1.00,
                strength_threshold_strong NUMERIC(5,2) NOT NULL DEFAULT 0.80,
                strength_threshold_moderate NUMERIC(5,2) NOT NULL DEFAULT 0.50,
                multiplier_very_strong NUMERIC(5,2) NOT NULL DEFAULT 1.20,
                multiplier_strong NUMERIC(5,2) NOT NULL DEFAULT 1.00,
                multiplier_moderate NUMERIC(5,2) NOT NULL DEFAULT 0.75,
                multiplier_weak NUMERIC(5,2) NOT NULL DEFAULT 0.50,

                -- Log-Utility Constraints
                max_position_log_loss NUMERIC(5,4) NOT NULL DEFAULT -0.08,
                max_portfolio_log_loss NUMERIC(5,4) NOT NULL DEFAULT -0.05,

                -- Kelly Sizing
                kelly_fraction NUMERIC(5,4) NOT NULL DEFAULT 0.25,
                max_kelly_multiplier NUMERIC(5,2) NOT NULL DEFAULT 1.50,

                -- Entry Pricing
                use_limit_orders BOOLEAN NOT NULL DEFAULT FALSE,
                entry_offset_pct NUMERIC(5,4) NOT NULL DEFAULT 0.0010,

                -- Exit Targets
                min_profit_pct NUMERIC(5,4) NOT NULL DEFAULT 0.005,
                target_r_multiple NUMERIC(5,2) NOT NULL DEFAULT 2.0,
                stretch_r_multiple NUMERIC(5,2) NOT NULL DEFAULT 3.0,
                use_trailing_stop BOOLEAN NOT NULL DEFAULT TRUE,
                trailing_stop_activation_pct NUMERIC(5,4) NOT NULL DEFAULT 0.01,
                trailing_stop_distance_pct NUMERIC(5,4) NOT NULL DEFAULT 0.005,

                -- Timestamps
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void createSymbolConfigTable(Connection conn) throws Exception {
        String sql = """
            CREATE TABLE mtf_symbol_config (
                symbol_config_id VARCHAR(50) PRIMARY KEY,
                symbol VARCHAR(50) NOT NULL,
                user_broker_id VARCHAR(50) NOT NULL,

                -- All fields nullable - NULL means "use global default"

                -- HTF Config (nullable overrides)
                htf_candle_count INT,
                htf_candle_minutes INT,
                htf_weight NUMERIC(5,2),

                -- ITF Config (nullable overrides)
                itf_candle_count INT,
                itf_candle_minutes INT,
                itf_weight NUMERIC(5,2),

                -- LTF Config (nullable overrides)
                ltf_candle_count INT,
                ltf_candle_minutes INT,
                ltf_weight NUMERIC(5,2),

                -- Zone Detection (nullable override)
                buy_zone_pct NUMERIC(5,2),

                -- Confluence Settings (nullable overrides)
                min_confluence_type VARCHAR(20),
                strength_threshold_very_strong NUMERIC(5,2),
                strength_threshold_strong NUMERIC(5,2),
                strength_threshold_moderate NUMERIC(5,2),
                multiplier_very_strong NUMERIC(5,2),
                multiplier_strong NUMERIC(5,2),
                multiplier_moderate NUMERIC(5,2),
                multiplier_weak NUMERIC(5,2),

                -- Log-Utility Constraints (nullable overrides)
                max_position_log_loss NUMERIC(5,4),
                max_portfolio_log_loss NUMERIC(5,4),

                -- Kelly Sizing (nullable overrides)
                kelly_fraction NUMERIC(5,4),
                max_kelly_multiplier NUMERIC(5,2),

                -- Entry Pricing (nullable overrides)
                use_limit_orders BOOLEAN,
                entry_offset_pct NUMERIC(5,4),

                -- Exit Targets (nullable overrides)
                min_profit_pct NUMERIC(5,4),
                target_r_multiple NUMERIC(5,2),
                stretch_r_multiple NUMERIC(5,2),
                use_trailing_stop BOOLEAN,
                trailing_stop_activation_pct NUMERIC(5,4),
                trailing_stop_distance_pct NUMERIC(5,4),

                -- Timestamps
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                UNIQUE(symbol, user_broker_id)
            )
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void insertDefaultGlobalConfig(Connection conn) throws Exception {
        String sql = """
            INSERT INTO mtf_global_config (config_id) VALUES ('DEFAULT')
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            log.info("[MTF MIGRATION] Inserted default global config row");
        }
    }

    /**
     * Update mtf_global_config schema with new constitutional parameters.
     * Adds columns from V004 (position sizing), V005 (velocity), V006 (utility).
     */
    private void updateGlobalConfigSchema(Connection conn) throws Exception {
        log.info("[MTF MIGRATION] Checking for schema updates on mtf_global_config");

        // V004: Position Sizing Constitution
        addColumnIfNotExists(conn, "mtf_global_config", "max_symbol_log_loss",
            "NUMERIC(6,4) DEFAULT -0.1000",
            "Max per-symbol log-loss budget (Phase 1)");

        addColumnIfNotExists(conn, "mtf_global_config", "min_reentry_spacing_atr_multiplier",
            "NUMERIC(3,2) DEFAULT 2.00",
            "Averaging gate ATR spacing multiplier (Phase 1)");

        // V005: Velocity Throttling
        addColumnIfNotExists(conn, "mtf_global_config", "range_atr_threshold_wide",
            "NUMERIC(5,2) DEFAULT 8.00",
            "Range/ATR threshold for WIDE regime (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "range_atr_threshold_healthy",
            "NUMERIC(5,2) DEFAULT 5.00",
            "Range/ATR threshold for HEALTHY regime (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "range_atr_threshold_tight",
            "NUMERIC(5,2) DEFAULT 3.00",
            "Range/ATR threshold for TIGHT regime (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "velocity_multiplier_wide",
            "NUMERIC(4,2) DEFAULT 1.00",
            "Velocity multiplier for WIDE regime (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "velocity_multiplier_healthy",
            "NUMERIC(4,2) DEFAULT 0.75",
            "Velocity multiplier for HEALTHY regime (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "velocity_multiplier_tight",
            "NUMERIC(4,2) DEFAULT 0.50",
            "Velocity multiplier for TIGHT regime (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "velocity_multiplier_compressed",
            "NUMERIC(4,2) DEFAULT 0.25",
            "Velocity multiplier for COMPRESSED regime (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "body_ratio_threshold_low",
            "NUMERIC(4,2) DEFAULT 0.30",
            "Body ratio threshold for low conviction (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "body_ratio_threshold_critical",
            "NUMERIC(4,2) DEFAULT 0.15",
            "Body ratio threshold for critical conviction (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "body_ratio_penalty_low",
            "NUMERIC(4,2) DEFAULT 0.90",
            "Body ratio penalty multiplier for low conviction (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "body_ratio_penalty_critical",
            "NUMERIC(4,2) DEFAULT 0.75",
            "Body ratio penalty multiplier for critical conviction (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "range_lookback_bars",
            "INT DEFAULT 100",
            "Lookback period for Range/ATR calculation (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "stress_throttle_enabled",
            "BOOLEAN DEFAULT TRUE",
            "Enable stress-based velocity throttling (Phase 2)");

        addColumnIfNotExists(conn, "mtf_global_config", "max_stress_drawdown",
            "NUMERIC(6,4) DEFAULT -0.05",
            "Maximum portfolio drawdown before stress throttle (Phase 2)");

        // V006: Utility Asymmetry
        addColumnIfNotExists(conn, "mtf_global_config", "utility_alpha",
            "NUMERIC(4,2) DEFAULT 0.60",
            "Upside utility concavity parameter α (Phase 3)");

        addColumnIfNotExists(conn, "mtf_global_config", "utility_beta",
            "NUMERIC(4,2) DEFAULT 1.40",
            "Downside utility convexity parameter β (Phase 3)");

        addColumnIfNotExists(conn, "mtf_global_config", "utility_lambda",
            "NUMERIC(4,2) DEFAULT 1.00",
            "Loss aversion multiplier λ (Phase 3)");

        addColumnIfNotExists(conn, "mtf_global_config", "min_advantage_ratio",
            "NUMERIC(4,2) DEFAULT 3.00",
            "Minimum utility advantage ratio (Phase 3)");

        addColumnIfNotExists(conn, "mtf_global_config", "utility_gate_enabled",
            "BOOLEAN DEFAULT TRUE",
            "Enable utility asymmetry gate (Phase 3)");

        log.info("[MTF MIGRATION] ✓ Schema updates completed for mtf_global_config");
    }

    /**
     * Update mtf_symbol_config schema with new constitutional parameters (nullable).
     */
    private void updateSymbolConfigSchema(Connection conn) throws Exception {
        log.info("[MTF MIGRATION] Checking for schema updates on mtf_symbol_config");

        // Symbol config has nullable overrides - no defaults needed
        // (V004, V005, V006 only affect global config per user requirement)

        log.info("[MTF MIGRATION] ✓ Schema updates completed for mtf_symbol_config");
    }

    /**
     * Add column to table if it doesn't already exist.
     */
    private void addColumnIfNotExists(Connection conn, String tableName, String columnName,
                                      String columnDef, String description) throws Exception {
        if (!columnExists(conn, tableName, columnName)) {
            String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s",
                tableName, columnName, columnDef);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
                log.info("[MTF MIGRATION] Added column: {}.{} - {}", tableName, columnName, description);
            }
        }
    }

    /**
     * Check if a column exists in a table.
     */
    private boolean columnExists(Connection conn, String tableName, String columnName) throws Exception {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }
}
