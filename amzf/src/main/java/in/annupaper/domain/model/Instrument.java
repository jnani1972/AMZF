package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain model for instruments (stocks, derivatives, commodities).
 *
 * Normalized representation across all brokers.
 * Persisted in instruments table for fast lookups.
 */
public record Instrument(
        String instrumentId, // Internal unique ID (hash of exchange:symbol)

        // Normalized fields
        String exchange, // NSE, BSE, NFO, MCX
        String tradingSymbol, // SBIN, RELIANCE, NIFTY24JANFUT
        String isin, // ISIN code (if available)
        InstrumentType instrumentType, // EQUITY, FUTURES, OPTIONS, COMMODITY

        // Descriptive fields
        String companyName, // Full company/instrument name
        String segment, // EQ, FO, CD, CDS

        // Trading parameters
        BigDecimal tickSize, // Minimum price movement
        BigDecimal lotSize, // Lot size (1 for equity, varies for F&O)

        // Options-specific (null for non-options)
        OptionType optionType, // CE, PE, null
        BigDecimal strikePrice, // Strike price for options
        Instant expiryDate, // Expiry date for derivatives

        // Broker-specific mappings (stored as JSON)
        BrokerMapping brokerMappings, // Map of broker -> broker-specific IDs

        // Metadata
        boolean isTradeable, // Can be traded currently
        Instant lastUpdated, // Last time instrument was updated
        String dataSource, // Which broker provided this data

        // Audit
        Instant createdAt,
        int version) {
    /**
     * Instrument type enum.
     */
    public enum InstrumentType {
        EQUITY, // Stocks
        FUTURES, // Futures contracts
        OPTIONS, // Options contracts
        COMMODITY, // Commodities
        CURRENCY, // Currency pairs
        INDEX // Indices (usually non-tradeable)
    }

    /**
     * Option type enum.
     */
    public enum OptionType {
        CE, // Call European
        PE // Put European
    }

    /**
     * Broker-specific instrument mappings.
     * Stored as JSON in database, deserialized to this record.
     */
    public record BrokerMapping(
            String zerodhaInstrumentToken,
            String zerodhaExchangeToken,
            String fyersSymbol,
            String upstoxInstrumentKey,
            String upstoxInstrumentToken,
            String dhanSecurityId,
            String dhanSlug) {
        /**
         * Get mapping for a specific broker.
         */
        public String getMapping(String brokerCode) {
            return switch (brokerCode.toUpperCase()) {
                case "ZERODHA" -> zerodhaInstrumentToken;
                case "FYERS" -> fyersSymbol;
                case "UPSTOX" -> upstoxInstrumentKey;
                case "DHAN" -> dhanSecurityId;
                default -> null;
            };
        }

        /**
         * Create empty mappings.
         */
        public static BrokerMapping empty() {
            return new BrokerMapping(null, null, null, null, null, null, null);
        }
    }

    /**
     * Generate instrument ID from exchange and symbol.
     * Format: {EXCHANGE}:{SYMBOL}
     */
    public static String generateInstrumentId(String exchange, String tradingSymbol) {
        return (exchange + ":" + tradingSymbol).toUpperCase();
    }

    /**
     * Check if this is an equity instrument.
     */
    public boolean isEquity() {
        return instrumentType == InstrumentType.EQUITY;
    }

    /**
     * Check if this is a derivative (futures or options).
     */
    public boolean isDerivative() {
        return instrumentType == InstrumentType.FUTURES ||
                instrumentType == InstrumentType.OPTIONS;
    }

    /**
     * Check if this is an option.
     */
    public boolean isOption() {
        return instrumentType == InstrumentType.OPTIONS;
    }

    /**
     * Check if option is expired.
     */
    public boolean isExpired() {
        return expiryDate != null && Instant.now().isAfter(expiryDate);
    }

    /**
     * Get broker-specific mapping.
     */
    public String getBrokerMapping(String brokerCode) {
        return brokerMappings != null ? brokerMappings.getMapping(brokerCode) : null;
    }
}
