package in.annupaper.domain.model;

import java.math.BigDecimal;

/**
 * Technical details for an instrument from a broker.
 */
public record BrokerInstrument(
        String exchange,
        String tradingSymbol,
        String name,
        String instrumentType,
        String segment,
        String expiry,
        int lotSize,
        BigDecimal tickSize,
        String strike,
        BigDecimal margin,
        String description) {
}
