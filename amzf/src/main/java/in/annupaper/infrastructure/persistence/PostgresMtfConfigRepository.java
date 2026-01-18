package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;

import in.annupaper.domain.model.MtfGlobalConfig;
import in.annupaper.domain.model.MtfSymbolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of MtfConfigRepository.
 */
public final class PostgresMtfConfigRepository implements MtfConfigRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresMtfConfigRepository.class);

    private final DataSource dataSource;

    public PostgresMtfConfigRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<MtfGlobalConfig> getGlobalConfig() {
        String sql = "SELECT * FROM mtf_global_config WHERE config_id = 'DEFAULT'";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return Optional.of(mapGlobalConfigRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to get global config: {}", e.getMessage());
            throw new RuntimeException("Failed to get global config", e);
        }
        return Optional.empty();
    }

    @Override
    public void updateGlobalConfig(MtfGlobalConfig config) {
        String sql = """
                UPDATE mtf_global_config SET
                    htf_candle_count = ?, htf_candle_minutes = ?, htf_weight = ?,
                    itf_candle_count = ?, itf_candle_minutes = ?, itf_weight = ?,
                    ltf_candle_count = ?, ltf_candle_minutes = ?, ltf_weight = ?,
                    buy_zone_pct = ?,
                    min_confluence_type = ?,
                    strength_threshold_very_strong = ?, strength_threshold_strong = ?,
                    strength_threshold_moderate = ?,
                    multiplier_very_strong = ?, multiplier_strong = ?,
                    multiplier_moderate = ?, multiplier_weak = ?,
                    max_position_log_loss = ?, max_portfolio_log_loss = ?,
                    kelly_fraction = ?, max_kelly_multiplier = ?,
                    use_limit_orders = ?, entry_offset_pct = ?,
                    min_profit_pct = ?, target_r_multiple = ?, stretch_r_multiple = ?,
                    use_trailing_stop = ?, trailing_stop_activation_pct = ?,
                    trailing_stop_distance_pct = ?,
                    updated_at = NOW()
                WHERE config_id = 'DEFAULT'
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setInt(idx++, config.htfCandleCount());
            ps.setInt(idx++, config.htfCandleMinutes());
            ps.setBigDecimal(idx++, config.htfWeight());

            ps.setInt(idx++, config.itfCandleCount());
            ps.setInt(idx++, config.itfCandleMinutes());
            ps.setBigDecimal(idx++, config.itfWeight());

            ps.setInt(idx++, config.ltfCandleCount());
            ps.setInt(idx++, config.ltfCandleMinutes());
            ps.setBigDecimal(idx++, config.ltfWeight());

            ps.setBigDecimal(idx++, config.buyZonePct());

            ps.setString(idx++, config.minConfluenceType());
            ps.setBigDecimal(idx++, config.strengthThresholdVeryStrong());
            ps.setBigDecimal(idx++, config.strengthThresholdStrong());
            ps.setBigDecimal(idx++, config.strengthThresholdModerate());
            ps.setBigDecimal(idx++, config.multiplierVeryStrong());
            ps.setBigDecimal(idx++, config.multiplierStrong());
            ps.setBigDecimal(idx++, config.multiplierModerate());
            ps.setBigDecimal(idx++, config.multiplierWeak());

            ps.setBigDecimal(idx++, config.maxPositionLogLoss());
            ps.setBigDecimal(idx++, config.maxPortfolioLogLoss());

            ps.setBigDecimal(idx++, config.kellyFraction());
            ps.setBigDecimal(idx++, config.maxKellyMultiplier());

            ps.setBoolean(idx++, config.useLimitOrders());
            ps.setBigDecimal(idx++, config.entryOffsetPct());

            ps.setBigDecimal(idx++, config.minProfitPct());
            ps.setBigDecimal(idx++, config.targetRMultiple());
            ps.setBigDecimal(idx++, config.stretchRMultiple());
            ps.setBoolean(idx++, config.useTrailingStop());
            ps.setBigDecimal(idx++, config.trailingStopActivationPct());
            ps.setBigDecimal(idx++, config.trailingStopDistancePct());

            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("Global config not found");
            }

            log.info("Updated global MTF config");

        } catch (Exception e) {
            log.error("Failed to update global config: {}", e.getMessage());
            throw new RuntimeException("Failed to update global config", e);
        }
    }

    @Override
    public Optional<MtfSymbolConfig> getSymbolConfig(String symbol, String userBrokerId) {
        String sql = "SELECT * FROM mtf_symbol_config WHERE symbol = ? AND user_broker_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setString(2, userBrokerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSymbolConfigRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get symbol config for {}/{}: {}", symbol, userBrokerId, e.getMessage());
            throw new RuntimeException("Failed to get symbol config", e);
        }
        return Optional.empty();
    }

    @Override
    public List<MtfSymbolConfig> getAllSymbolConfigs() {
        String sql = "SELECT * FROM mtf_symbol_config ORDER BY symbol ASC, user_broker_id ASC";

        List<MtfSymbolConfig> configs = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                configs.add(mapSymbolConfigRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to get all symbol configs: {}", e.getMessage());
            throw new RuntimeException("Failed to get symbol configs", e);
        }
        return configs;
    }

    @Override
    public void upsertSymbolConfig(MtfSymbolConfig config) {
        String sql = """
                INSERT INTO mtf_symbol_config (
                    symbol_config_id, symbol, user_broker_id,
                    htf_candle_count, htf_candle_minutes, htf_weight,
                    itf_candle_count, itf_candle_minutes, itf_weight,
                    ltf_candle_count, ltf_candle_minutes, ltf_weight,
                    buy_zone_pct,
                    min_confluence_type,
                    strength_threshold_very_strong, strength_threshold_strong,
                    strength_threshold_moderate,
                    multiplier_very_strong, multiplier_strong,
                    multiplier_moderate, multiplier_weak,
                    max_position_log_loss, max_portfolio_log_loss,
                    kelly_fraction, max_kelly_multiplier,
                    use_limit_orders, entry_offset_pct,
                    min_profit_pct, target_r_multiple, stretch_r_multiple,
                    use_trailing_stop, trailing_stop_activation_pct,
                    trailing_stop_distance_pct
                ) VALUES (
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?,
                    ?,
                    ?,
                    ?, ?,
                    ?,
                    ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?, ?,
                    ?, ?,
                    ?
                )
                ON CONFLICT (symbol, user_broker_id) DO UPDATE SET
                    htf_candle_count = EXCLUDED.htf_candle_count,
                    htf_candle_minutes = EXCLUDED.htf_candle_minutes,
                    htf_weight = EXCLUDED.htf_weight,
                    itf_candle_count = EXCLUDED.itf_candle_count,
                    itf_candle_minutes = EXCLUDED.itf_candle_minutes,
                    itf_weight = EXCLUDED.itf_weight,
                    ltf_candle_count = EXCLUDED.ltf_candle_count,
                    ltf_candle_minutes = EXCLUDED.ltf_candle_minutes,
                    ltf_weight = EXCLUDED.ltf_weight,
                    buy_zone_pct = EXCLUDED.buy_zone_pct,
                    min_confluence_type = EXCLUDED.min_confluence_type,
                    strength_threshold_very_strong = EXCLUDED.strength_threshold_very_strong,
                    strength_threshold_strong = EXCLUDED.strength_threshold_strong,
                    strength_threshold_moderate = EXCLUDED.strength_threshold_moderate,
                    multiplier_very_strong = EXCLUDED.multiplier_very_strong,
                    multiplier_strong = EXCLUDED.multiplier_strong,
                    multiplier_moderate = EXCLUDED.multiplier_moderate,
                    multiplier_weak = EXCLUDED.multiplier_weak,
                    max_position_log_loss = EXCLUDED.max_position_log_loss,
                    max_portfolio_log_loss = EXCLUDED.max_portfolio_log_loss,
                    kelly_fraction = EXCLUDED.kelly_fraction,
                    max_kelly_multiplier = EXCLUDED.max_kelly_multiplier,
                    use_limit_orders = EXCLUDED.use_limit_orders,
                    entry_offset_pct = EXCLUDED.entry_offset_pct,
                    min_profit_pct = EXCLUDED.min_profit_pct,
                    target_r_multiple = EXCLUDED.target_r_multiple,
                    stretch_r_multiple = EXCLUDED.stretch_r_multiple,
                    use_trailing_stop = EXCLUDED.use_trailing_stop,
                    trailing_stop_activation_pct = EXCLUDED.trailing_stop_activation_pct,
                    trailing_stop_distance_pct = EXCLUDED.trailing_stop_distance_pct,
                    updated_at = NOW()
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setString(idx++, config.symbolConfigId());
            ps.setString(idx++, config.symbol());
            ps.setString(idx++, config.userBrokerId());

            setIntOrNull(ps, idx++, config.htfCandleCount());
            setIntOrNull(ps, idx++, config.htfCandleMinutes());
            setBigDecimalOrNull(ps, idx++, config.htfWeight());

            setIntOrNull(ps, idx++, config.itfCandleCount());
            setIntOrNull(ps, idx++, config.itfCandleMinutes());
            setBigDecimalOrNull(ps, idx++, config.itfWeight());

            setIntOrNull(ps, idx++, config.ltfCandleCount());
            setIntOrNull(ps, idx++, config.ltfCandleMinutes());
            setBigDecimalOrNull(ps, idx++, config.ltfWeight());

            setBigDecimalOrNull(ps, idx++, config.buyZonePct());

            setStringOrNull(ps, idx++, config.minConfluenceType());
            setBigDecimalOrNull(ps, idx++, config.strengthThresholdVeryStrong());
            setBigDecimalOrNull(ps, idx++, config.strengthThresholdStrong());
            setBigDecimalOrNull(ps, idx++, config.strengthThresholdModerate());
            setBigDecimalOrNull(ps, idx++, config.multiplierVeryStrong());
            setBigDecimalOrNull(ps, idx++, config.multiplierStrong());
            setBigDecimalOrNull(ps, idx++, config.multiplierModerate());
            setBigDecimalOrNull(ps, idx++, config.multiplierWeak());

            setBigDecimalOrNull(ps, idx++, config.maxPositionLogLoss());
            setBigDecimalOrNull(ps, idx++, config.maxPortfolioLogLoss());

            setBigDecimalOrNull(ps, idx++, config.kellyFraction());
            setBigDecimalOrNull(ps, idx++, config.maxKellyMultiplier());

            setBooleanOrNull(ps, idx++, config.useLimitOrders());
            setBigDecimalOrNull(ps, idx++, config.entryOffsetPct());

            setBigDecimalOrNull(ps, idx++, config.minProfitPct());
            setBigDecimalOrNull(ps, idx++, config.targetRMultiple());
            setBigDecimalOrNull(ps, idx++, config.stretchRMultiple());
            setBooleanOrNull(ps, idx++, config.useTrailingStop());
            setBigDecimalOrNull(ps, idx++, config.trailingStopActivationPct());
            setBigDecimalOrNull(ps, idx++, config.trailingStopDistancePct());

            ps.executeUpdate();
            log.info("Upserted symbol config for {}/{}", config.symbol(), config.userBrokerId());

        } catch (Exception e) {
            log.error("Failed to upsert symbol config: {}", e.getMessage());
            throw new RuntimeException("Failed to upsert symbol config", e);
        }
    }

    @Override
    public void deleteSymbolConfig(String symbol, String userBrokerId) {
        String sql = "DELETE FROM mtf_symbol_config WHERE symbol = ? AND user_broker_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setString(2, userBrokerId);

            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                log.info("Deleted symbol config for {}/{}", symbol, userBrokerId);
            }

        } catch (Exception e) {
            log.error("Failed to delete symbol config: {}", e.getMessage());
            throw new RuntimeException("Failed to delete symbol config", e);
        }
    }

    @Override
    public MtfGlobalConfig getEffectiveConfig(String symbol, String userBrokerId) {
        MtfGlobalConfig global = getGlobalConfig().orElseThrow(
                () -> new RuntimeException("Global config not found"));

        Optional<MtfSymbolConfig> symbolConfig = getSymbolConfig(symbol, userBrokerId);
        if (symbolConfig.isEmpty()) {
            return global;
        }

        return symbolConfig.get().resolveEffective(global);
    }

    // Helper methods for nullable fields
    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private void setBigDecimalOrNull(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, Types.NUMERIC);
        }
    }

    private void setBooleanOrNull(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value != null) {
            ps.setBoolean(index, value);
        } else {
            ps.setNull(index, Types.BOOLEAN);
        }
    }

    private void setStringOrNull(PreparedStatement ps, int index, String value) throws SQLException {
        if (value != null) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
    }

    private Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal getBigDecimalOrNull(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean getBooleanOrNull(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private String getStringOrNull(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return rs.wasNull() ? null : value;
    }

    private MtfGlobalConfig mapGlobalConfigRow(ResultSet rs) throws SQLException {
        return new MtfGlobalConfig(
                rs.getString("config_id"),
                rs.getInt("htf_candle_count"),
                rs.getInt("htf_candle_minutes"),
                rs.getBigDecimal("htf_weight"),
                rs.getInt("itf_candle_count"),
                rs.getInt("itf_candle_minutes"),
                rs.getBigDecimal("itf_weight"),
                rs.getInt("ltf_candle_count"),
                rs.getInt("ltf_candle_minutes"),
                rs.getBigDecimal("ltf_weight"),
                rs.getBigDecimal("buy_zone_pct"),
                rs.getBigDecimal("htf_buy_zone_pct"),
                rs.getBigDecimal("itf_buy_zone_pct"),
                rs.getBigDecimal("ltf_buy_zone_pct"),
                rs.getString("min_confluence_type"),
                rs.getBigDecimal("strength_threshold_very_strong"),
                rs.getBigDecimal("strength_threshold_strong"),
                rs.getBigDecimal("strength_threshold_moderate"),
                rs.getBigDecimal("multiplier_very_strong"),
                rs.getBigDecimal("multiplier_strong"),
                rs.getBigDecimal("multiplier_moderate"),
                rs.getBigDecimal("multiplier_weak"),
                rs.getBigDecimal("max_position_log_loss"),
                rs.getBigDecimal("max_portfolio_log_loss"),
                rs.getBigDecimal("max_symbol_log_loss"),
                rs.getBigDecimal("kelly_fraction"),
                rs.getBigDecimal("max_kelly_multiplier"),
                rs.getBoolean("use_limit_orders"),
                rs.getBigDecimal("entry_offset_pct"),
                rs.getBigDecimal("min_profit_pct"),
                rs.getBigDecimal("target_r_multiple"),
                rs.getBigDecimal("stretch_r_multiple"),
                rs.getBoolean("use_trailing_stop"),
                rs.getBigDecimal("trailing_stop_activation_pct"),
                rs.getBigDecimal("trailing_stop_distance_pct"),
                rs.getBigDecimal("min_reentry_spacing_atr_multiplier"),
                rs.getBigDecimal("range_atr_threshold_wide"),
                rs.getBigDecimal("range_atr_threshold_healthy"),
                rs.getBigDecimal("range_atr_threshold_tight"),
                rs.getBigDecimal("velocity_multiplier_wide"),
                rs.getBigDecimal("velocity_multiplier_healthy"),
                rs.getBigDecimal("velocity_multiplier_tight"),
                rs.getBigDecimal("velocity_multiplier_compressed"),
                rs.getBigDecimal("body_ratio_threshold_low"),
                rs.getBigDecimal("body_ratio_threshold_critical"),
                rs.getBigDecimal("body_ratio_penalty_low"),
                rs.getBigDecimal("body_ratio_penalty_critical"),
                rs.getInt("range_lookback_bars"),
                rs.getBoolean("stress_throttle_enabled"),
                rs.getBigDecimal("max_stress_drawdown"),
                rs.getBigDecimal("utility_alpha"),
                rs.getBigDecimal("utility_beta"),
                rs.getBigDecimal("utility_lambda"),
                rs.getBigDecimal("min_advantage_ratio"),
                rs.getBoolean("utility_gate_enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private MtfSymbolConfig mapSymbolConfigRow(ResultSet rs) throws SQLException {
        return new MtfSymbolConfig(
                rs.getString("symbol_config_id"),
                rs.getString("symbol"),
                rs.getString("user_broker_id"),
                getIntOrNull(rs, "htf_candle_count"),
                getIntOrNull(rs, "htf_candle_minutes"),
                getBigDecimalOrNull(rs, "htf_weight"),
                getIntOrNull(rs, "itf_candle_count"),
                getIntOrNull(rs, "itf_candle_minutes"),
                getBigDecimalOrNull(rs, "itf_weight"),
                getIntOrNull(rs, "ltf_candle_count"),
                getIntOrNull(rs, "ltf_candle_minutes"),
                getBigDecimalOrNull(rs, "ltf_weight"),
                getBigDecimalOrNull(rs, "buy_zone_pct"),
                getStringOrNull(rs, "min_confluence_type"),
                getBigDecimalOrNull(rs, "strength_threshold_very_strong"),
                getBigDecimalOrNull(rs, "strength_threshold_strong"),
                getBigDecimalOrNull(rs, "strength_threshold_moderate"),
                getBigDecimalOrNull(rs, "multiplier_very_strong"),
                getBigDecimalOrNull(rs, "multiplier_strong"),
                getBigDecimalOrNull(rs, "multiplier_moderate"),
                getBigDecimalOrNull(rs, "multiplier_weak"),
                getBigDecimalOrNull(rs, "max_position_log_loss"),
                getBigDecimalOrNull(rs, "max_portfolio_log_loss"),
                getBigDecimalOrNull(rs, "kelly_fraction"),
                getBigDecimalOrNull(rs, "max_kelly_multiplier"),
                getBooleanOrNull(rs, "use_limit_orders"),
                getBigDecimalOrNull(rs, "entry_offset_pct"),
                getBigDecimalOrNull(rs, "min_profit_pct"),
                getBigDecimalOrNull(rs, "target_r_multiple"),
                getBigDecimalOrNull(rs, "stretch_r_multiple"),
                getBooleanOrNull(rs, "use_trailing_stop"),
                getBigDecimalOrNull(rs, "trailing_stop_activation_pct"),
                getBigDecimalOrNull(rs, "trailing_stop_distance_pct"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
