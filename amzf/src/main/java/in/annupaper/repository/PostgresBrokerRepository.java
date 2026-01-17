package in.annupaper.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.domain.broker.Broker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class PostgresBrokerRepository implements BrokerRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresBrokerRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    public PostgresBrokerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // @Override
    // public List<Broker> findAll() {
    //     String sql = "SELECT * FROM brokers ORDER BY broker_code";
    //     List<Broker> brokers = new ArrayList<>();
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql);
    //          ResultSet rs = ps.executeQuery()) {
    //
    //         while (rs.next()) {
    //             brokers.add(mapRow(rs));
    //         }
    //     } catch (Exception e) {
    //         log.error("Error finding all brokers: {}", e.getMessage(), e);
    //     }
    //
    //     return brokers;
    // }
    @Override
    public List<Broker> findAll() {
        String sql = "SELECT * FROM brokers WHERE deleted_at IS NULL ORDER BY broker_code ASC";
        List<Broker> brokers = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                brokers.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Error finding all brokers: {}", e.getMessage(), e);
        }

        return brokers;
    }

    // @Override
    // public Optional<Broker> findById(String brokerId) {
    //     String sql = "SELECT * FROM brokers WHERE broker_id = ?";
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setString(1, brokerId);
    //
    //         try (ResultSet rs = ps.executeQuery()) {
    //             if (rs.next()) {
    //                 return Optional.of(mapRow(rs));
    //             }
    //         }
    //     } catch (Exception e) {
    //         log.error("Error finding broker by ID: {}", e.getMessage(), e);
    //     }
    //
    //     return Optional.empty();
    // }
    @Override
    public Optional<Broker> findById(String brokerId) {
        String sql = "SELECT * FROM brokers WHERE broker_id = ? AND deleted_at IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding broker by ID: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    // @Override
    // public Optional<Broker> findByCode(String brokerCode) {
    //     String sql = "SELECT * FROM brokers WHERE broker_code = ?";
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setString(1, brokerCode);
    //
    //         try (ResultSet rs = ps.executeQuery()) {
    //             if (rs.next()) {
    //                 return Optional.of(mapRow(rs));
    //             }
    //         }
    //     } catch (Exception e) {
    //         log.error("Error finding broker by code: {}", e.getMessage(), e);
    //     }
    //
    //     return Optional.empty();
    // }
    @Override
    public Optional<Broker> findByCode(String brokerCode) {
        String sql = "SELECT * FROM brokers WHERE broker_code = ? AND deleted_at IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerCode);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding broker by code: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    // @Override
    // public void insert(Broker broker) {
    //     String sql = """
    //         INSERT INTO brokers (
    //             broker_id, broker_code, broker_name, adapter_class,
    //             config, supported_exchanges, supported_products,
    //             lot_sizes, margin_rules, rate_limits, status
    //         ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
    //         """;
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setString(1, broker.brokerId());
    //         ps.setString(2, broker.brokerCode());
    //         ps.setString(3, broker.brokerName());
    //         ps.setString(4, broker.adapterClass());
    //         ps.setString(5, broker.config() != null ? broker.config().toString() : "{}");
    //
    //         Array exchangesArr = conn.createArrayOf("TEXT", broker.supportedExchanges().toArray());
    //         ps.setArray(6, exchangesArr);
    //
    //         Array productsArr = conn.createArrayOf("TEXT", broker.supportedProducts().toArray());
    //         ps.setArray(7, productsArr);
    //
    //         ps.setString(8, broker.lotSizes() != null ? broker.lotSizes().toString() : "{}");
    //         ps.setString(9, broker.marginRules() != null ? broker.marginRules().toString() : "{}");
    //         ps.setString(10, broker.rateLimits() != null ? broker.rateLimits().toString() : "{}");
    //         ps.setString(11, broker.status());
    //
    //         ps.executeUpdate();
    //         log.info("Broker inserted: {}", broker.brokerId());
    //
    //     } catch (Exception e) {
    //         log.error("Error inserting broker: {}", e.getMessage(), e);
    //         throw new RuntimeException("Failed to insert broker", e);
    //     }
    // }
    @Override
    public void insert(Broker broker) {
        String sql = """
            INSERT INTO brokers (
                broker_id, broker_code, broker_name, adapter_class,
                config, supported_exchanges, supported_products,
                lot_sizes, margin_rules, rate_limits, status, version
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, 1)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, broker.brokerId());
            ps.setString(2, broker.brokerCode());
            ps.setString(3, broker.brokerName());
            ps.setString(4, broker.adapterClass());
            ps.setString(5, broker.config() != null ? broker.config().toString() : "{}");

            Array exchangesArr = conn.createArrayOf("TEXT", broker.supportedExchanges().toArray());
            ps.setArray(6, exchangesArr);

            Array productsArr = conn.createArrayOf("TEXT", broker.supportedProducts().toArray());
            ps.setArray(7, productsArr);

            ps.setString(8, broker.lotSizes() != null ? broker.lotSizes().toString() : "{}");
            ps.setString(9, broker.marginRules() != null ? broker.marginRules().toString() : "{}");
            ps.setString(10, broker.rateLimits() != null ? broker.rateLimits().toString() : "{}");
            ps.setString(11, broker.status());

            ps.executeUpdate();
            log.info("Broker inserted: {} version 1", broker.brokerId());

        } catch (Exception e) {
            log.error("Error inserting broker: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to insert broker", e);
        }
    }

    // @Override
    // public void update(Broker broker) {
    //     String sql = """
    //         UPDATE brokers SET
    //             broker_name = ?,
    //             adapter_class = ?,
    //             config = ?::jsonb,
    //             supported_exchanges = ?,
    //             supported_products = ?,
    //             lot_sizes = ?::jsonb,
    //             margin_rules = ?::jsonb,
    //             rate_limits = ?::jsonb,
    //             status = ?,
    //             updated_at = NOW()
    //         WHERE broker_id = ?
    //         """;
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setString(1, broker.brokerName());
    //         ps.setString(2, broker.adapterClass());
    //         ps.setString(3, broker.config() != null ? broker.config().toString() : "{}");
    //
    //         Array exchangesArr = conn.createArrayOf("TEXT", broker.supportedExchanges().toArray());
    //         ps.setArray(4, exchangesArr);
    //
    //         Array productsArr = conn.createArrayOf("TEXT", broker.supportedProducts().toArray());
    //         ps.setArray(5, productsArr);
    //
    //         ps.setString(6, broker.lotSizes() != null ? broker.lotSizes().toString() : "{}");
    //         ps.setString(7, broker.marginRules() != null ? broker.marginRules().toString() : "{}");
    //         ps.setString(8, broker.rateLimits() != null ? broker.rateLimits().toString() : "{}");
    //         ps.setString(9, broker.status());
    //         ps.setString(10, broker.brokerId());
    //
    //         ps.executeUpdate();
    //         log.info("Broker updated: {}", broker.brokerId());
    //
    //     } catch (Exception e) {
    //         log.error("Error updating broker: {}", e.getMessage(), e);
    //         throw new RuntimeException("Failed to update broker", e);
    //     }
    // }
    @Override
    public void update(Broker broker) {
        // Immutable update: soft delete old version, insert new version
        String queryVersionSql = "SELECT version FROM brokers WHERE broker_id = ? AND deleted_at IS NULL";
        String softDeleteSql = "UPDATE brokers SET deleted_at = NOW() WHERE broker_id = ? AND version = ?";
        String insertSql = """
            INSERT INTO brokers (
                broker_id, broker_code, broker_name, adapter_class,
                config, supported_exchanges, supported_products,
                lot_sizes, margin_rules, rate_limits, status, version
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, broker.brokerId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Broker not found: " + broker.brokerId());
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, broker.brokerId());
                ps.setInt(2, currentVersion);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("Failed to soft delete broker version: " + broker.brokerId());
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, broker.brokerId());
                ps.setString(2, broker.brokerCode());
                ps.setString(3, broker.brokerName());
                ps.setString(4, broker.adapterClass());
                ps.setString(5, broker.config() != null ? broker.config().toString() : "{}");

                Array exchangesArr = conn.createArrayOf("TEXT", broker.supportedExchanges().toArray());
                ps.setArray(6, exchangesArr);

                Array productsArr = conn.createArrayOf("TEXT", broker.supportedProducts().toArray());
                ps.setArray(7, productsArr);

                ps.setString(8, broker.lotSizes() != null ? broker.lotSizes().toString() : "{}");
                ps.setString(9, broker.marginRules() != null ? broker.marginRules().toString() : "{}");
                ps.setString(10, broker.rateLimits() != null ? broker.rateLimits().toString() : "{}");
                ps.setString(11, broker.status());
                ps.setInt(12, currentVersion + 1);

                ps.executeUpdate();
            }

            conn.commit();
            log.info("Broker updated: {} version {} → {}", broker.brokerId(), currentVersion, currentVersion + 1);

        } catch (Exception e) {
            log.error("Error updating broker: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update broker", e);
        }
    }

    private Broker mapRow(ResultSet rs) throws Exception {
        String brokerId = rs.getString("broker_id");
        String brokerCode = rs.getString("broker_code");
        String brokerName = rs.getString("broker_name");
        String adapterClass = rs.getString("adapter_class");

        String configJson = rs.getString("config");
        JsonNode config = configJson != null ? MAPPER.readTree(configJson) : null;

        Array exchangesArr = rs.getArray("supported_exchanges");
        List<String> supportedExchanges = exchangesArr != null
            ? Arrays.asList((String[]) exchangesArr.getArray())
            : List.of();

        Array productsArr = rs.getArray("supported_products");
        List<String> supportedProducts = productsArr != null
            ? Arrays.asList((String[]) productsArr.getArray())
            : List.of();

        String lotSizesJson = rs.getString("lot_sizes");
        JsonNode lotSizes = lotSizesJson != null ? MAPPER.readTree(lotSizesJson) : null;

        String marginRulesJson = rs.getString("margin_rules");
        JsonNode marginRules = marginRulesJson != null ? MAPPER.readTree(marginRulesJson) : null;

        String rateLimitsJson = rs.getString("rate_limits");
        JsonNode rateLimits = rateLimitsJson != null ? MAPPER.readTree(rateLimitsJson) : null;

        String status = rs.getString("status");

        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();
        Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : Instant.now();

        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        int version = rs.getInt("version");

        return new Broker(
            brokerId, brokerCode, brokerName, adapterClass,
            config, supportedExchanges, supportedProducts,
            lotSizes, marginRules, rateLimits,
            status, createdAt, updatedAt, deletedAt, version
        );
    }
}
