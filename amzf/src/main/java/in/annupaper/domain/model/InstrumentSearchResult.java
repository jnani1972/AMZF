package in.annupaper.domain.model;

/**
 * Instrument search result.
 */
public record InstrumentSearchResult(
        String symbol,
        String name,
        String exchange,
        String instrumentType) {
}
