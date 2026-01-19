package in.annupaper.service.candle;

import in.annupaper.domain.model.TimeframeType;
import in.annupaper.service.candle.CandleStore;
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
    private final in.annupaper.application.port.output.MtfConfigRepository mtfConfigRepo;

    public CandleReconciler(
            CandleFetcher candleFetcher,
            CandleStore candleStore,
            in.annupaper.application.port.output.MtfConfigRepository mtfConfigRepo) {
        this.candleFetcher = candleFetcher;
        this.candleStore = candleStore;
        this.mtfConfigRepo = mtfConfigRepo;
    }

    /**
     * Reconcile candles for a watchlist.
     * Fetches missing historical data for all symbols.
     */
    public void reconcile(
            String userBrokerId,
            String brokerCode,
            List<String> watchlistSymbols) {
        if (watchlistSymbols == null || watchlistSymbols.isEmpty()) {
            log.info("No symbols in watchlist, skipping reconciliation");
            return;
        }

        log.info("Starting candle reconciliation for {} symbols", watchlistSymbols.size());

        int reconciled = 0;

        for (String symbol : watchlistSymbols) {
            try {
                // Get effective config for this symbol (global + symbol override)
                var config = mtfConfigRepo.getEffectiveConfig(symbol, userBrokerId);
                reconcileSymbol(userBrokerId, brokerCode, symbol, config);
                reconciled++;

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
            in.annupaper.domain.model.MtfGlobalConfig config) {
        log.info("Reconciling symbol: {}", symbol);

        Instant now = Instant.now();

        // Fetch for all timeframes using configured lookbacks
        for (TimeframeType tf : TimeframeType.values()) {
            try {
                int lookbackMinutes;

                switch (tf) {
                    case HTF:
                    case MINUTE_125:
                        lookbackMinutes = config.htfCandleCount() * config.htfCandleMinutes();
                        break;
                    case ITF:
                    case MINUTE_25:
                        lookbackMinutes = config.itfCandleCount() * config.itfCandleMinutes();
                        break;
                    case LTF:
                    case MINUTE_1:
                        lookbackMinutes = config.ltfCandleCount() * config.ltfCandleMinutes();
                        break;
                    case DAILY:
                        // Default to 1 year approx (252 trading days * 1440 min/day is roughly 365
                        // calendar days logic)
                        // Or utilize a "dailyLookback" if added to MtfConfig later.
                        // For now, hardcode to 365 days (~525600 min) as per previous App.java logic
                        lookbackMinutes = 525600;
                        break;
                    default:
                        // Skip unsupported timeframes for now or use enum default
                        continue;
                }

                // Add buffer (e.g. 10%)
                lookbackMinutes = (int) (lookbackMinutes * 1.1);

                Instant from = now.minus(lookbackMinutes, ChronoUnit.MINUTES);

                // Optimization: Check if we really need to fetch?
                // For now, rely on fetchHistorical's internal checks or just overwrite/fill
                // gaps
                // Since this is reconciliation on startup, ensuring we have the data is safer.

                candleFetcher.fetchHistorical(userBrokerId, brokerCode, symbol, tf, from, now).join();

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
            int lookbackDays) {
        if (watchlistSymbols == null || watchlistSymbols.isEmpty()) {
            log.info("No symbols in watchlist, skipping reconciliation");
            return;
        }

        log.info("Starting candle reconciliation for {} symbols (lookback: {} days)",
                watchlistSymbols.size(), lookbackDays);

        for (String symbol : watchlistSymbols) {
            try {
                // Get effective config for this symbol (global + symbol override)
                var config = mtfConfigRepo.getEffectiveConfig(symbol, userBrokerId);
                reconcileSymbol(userBrokerId, brokerCode, symbol, config);
            } catch (Exception e) {
                log.error("Failed to reconcile {}: {}", symbol, e.getMessage());
            }
        }

        log.info("Reconciliation complete");
    }
}
