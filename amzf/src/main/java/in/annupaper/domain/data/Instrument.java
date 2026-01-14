package in.annupaper.domain.data;

import java.math.BigDecimal;

/**
 * Financial instrument definition.
 * Extracted from BrokerAdapter to domain layer.
 */
public record Instrument(
    String exchange,           // NSE, BSE, NFO, MCX
    String tradingSymbol,      // e.g., NSE:RELIANCE-EQ
    String name,               // Company/instrument name
    String instrumentType,     // EQ, FUT, CE, PE
    String segment,            // EQUITY, DERIVATIVE, COMMODITY
    String token,              // Broker-specific token/instrument_token
    int lotSize,               // Lot size for derivatives, 1 for equity
    BigDecimal tickSize,       // Minimum price movement
    String expiryDate,         // For derivatives (YYYY-MM-DD)
    BigDecimal strikePrice,    // For options
    String optionType          // CE or PE for options
) {}
