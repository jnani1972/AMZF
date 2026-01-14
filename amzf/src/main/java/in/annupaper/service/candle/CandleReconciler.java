package in.annupaper.service.candle;

import in.annupaper.domain.data.TimeframeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * CandleReconciler - One-time backfill of missing candles on startup.
 */
public final class CandleReconciler {
    private static final Logger log = LoggerFactory.getLogger(CandleReconciler.class);

    private final CandleFetcher candleFetcher;
    private final CandleStore candleStore;

    // Default lookback period for backfill
    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    public CandleReconciler(CandleFetcher candleFetcher, CandleStore candleStore) {
        this.candleFetcher = candleFetcher;
        this.candleStore = candleStore;
    }

    /**
     * Reconcile candles for a watchlist.
     * Fetches missing historical data for all symbols.
     */
    public void reconcile(
        String userBrokerId,
        String brokerCode,
        List<String> watchlistSymbols
    ) {
        if (watchlistSymbols == null || watchlistSymbols.isEmpty()) {
            log.info("No symbols in watchlist, skipping reconciliation");
            return;
        }

        log.info("Starting candle reconciliation for {} symbols", watchlistSymbols.size());

        Instant to = Instant.now();
        Instant from = to.minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS);

        int reconciled = 0;

        for (String symbol : watchlistSymbols) {
            try {
                boolean needsReconciliation = false;

                // Check if candles exist for all timeframes
                for (TimeframeType tf : TimeframeType.values()) {
                    if (!candleStore.exists(symbol, tf)) {
                        log.info("Missing candles for {} {}, will fetch", symbol, tf);
                        needsReconciliation = true;
                        break;
                    }
                }

                if (needsReconciliation) {
                    reconcileSymbol(userBrokerId, brokerCode, symbol, from, to);
                    reconciled++;
                }

            } catch (Exception e) {
                log.error("Failed to reconcile {}: {}", symbol, e.getMessage());
            }
        }

        log.info("Reconciliation complete: {}/{} symbols reconciled",
                 reconciled, watchlistSymbols.size());
    }

    /**
     * Reconcile a single symbol (all timeframes).
     */
    private void reconcileSymbol(
        String userBrokerId,
        String brokerCode,
        String symbol,
        Instant from,
        Instant to
    ) {
        log.info("Reconciling symbol: {}", symbol);

        // Fetch for all timeframes
        for (TimeframeType tf : TimeframeType.values()) {
            try {
                candleFetcher.fetchHistorical(userBrokerId, brokerCode, symbol, tf, from, to).join();
            } catch (Exception e) {
                log.error("Failed to fetch {} {} during reconciliation: {}",
                          symbol, tf, e.getMessage());
            }
        }
    }

    /**
     * Reconcile with custom lookback period.
     */
    public void reconcile(
        String userBrokerId,
        String brokerCode,
        List<String> watchlistSymbols,
        int lookbackDays
    ) {
        if (watchlistSymbols == null || watchlistSymbols.isEmpty()) {
            log.info("No symbols in watchlist, skipping reconciliation");
            return;
        }

        log.info("Starting candle reconciliation for {} symbols (lookback: {} days)",
                 watchlistSymbols.size(), lookbackDays);

        Instant to = Instant.now();
        Instant from = to.minus(lookbackDays, ChronoUnit.DAYS);

        for (String symbol : watchlistSymbols) {
            try {
                reconcileSymbol(userBrokerId, brokerCode, symbol, from, to);
            } catch (Exception e) {
                log.error("Failed to reconcile {}: {}", symbol, e.getMessage());
            }
        }

        log.info("Reconciliation complete");
    }
}
