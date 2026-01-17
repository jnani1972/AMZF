package in.annupaper.service.candle;

import in.annupaper.domain.broker.BrokerAdapter;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * CandleFetcher - Orchestrates historical candle fetching from brokers.
 */
public final class CandleFetcher {
    private static final Logger log = LoggerFactory.getLogger(CandleFetcher.class);

    private final BrokerAdapterFactory brokerFactory;
    private final CandleStore candleStore;

    public CandleFetcher(BrokerAdapterFactory brokerFactory, CandleStore candleStore) {
        this.brokerFactory = brokerFactory;
        this.candleStore = candleStore;
    }

    /**
     * Fetch historical candles for a symbol and timeframe.
     */
    public CompletableFuture<Void> fetchHistorical(
        String userBrokerId,
        String brokerCode,
        String symbol,
        TimeframeType timeframe,
        Instant from,
        Instant to
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Fetching historical candles: {} {} from {} to {}",
                         symbol, timeframe, from, to);

                // Get broker adapter
                BrokerAdapter adapter = brokerFactory.getOrCreate(userBrokerId, brokerCode);
                if (adapter == null || !adapter.isConnected()) {
                    log.warn("Broker adapter not available or not connected: {}", brokerCode);
                    return;
                }

                // Map timeframe to interval minutes
                int intervalMinutes = mapTimeframeToInterval(timeframe);

                // Fetch from broker
                List<BrokerAdapter.HistoricalCandle> historicalCandles = adapter
                    .getHistoricalCandles(symbol, intervalMinutes, from.getEpochSecond(), to.getEpochSecond())
                    .get();

                // Convert to domain candles
                List<Candle> candles = new ArrayList<>();
                for (BrokerAdapter.HistoricalCandle hc : historicalCandles) {
                    Candle candle = Candle.of(
                        symbol,
                        timeframe,
                        Instant.ofEpochSecond(hc.timestamp()),
                        hc.open().doubleValue(),
                        hc.high().doubleValue(),
                        hc.low().doubleValue(),
                        hc.close().doubleValue(),
                        hc.volume()
                    );
                    candles.add(candle);
                }

                // Store in batch
                candleStore.addBatch(candles);

                log.info("Fetched and stored {} candles for {} {}", candles.size(), symbol, timeframe);

            } catch (Exception e) {
                log.error("Failed to fetch historical candles for {} {}: {}",
                          symbol, timeframe, e.getMessage());
            }
        });
    }

    /**
     * Fetch historical candles for multiple symbols.
     */
    public void fetchForWatchlist(
        String userBrokerId,
        String brokerCode,
        List<String> symbols,
        Instant from,
        Instant to
    ) {
        log.info("Fetching historical candles for {} symbols", symbols.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String symbol : symbols) {
            // Fetch for all timeframes (including DAILY for close price fallback)
            futures.add(fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.DAILY, from, to));
            futures.add(fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.HTF, from, to));
            futures.add(fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.ITF, from, to));
            futures.add(fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.LTF, from, to));
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Completed fetching historical candles for {} symbols", symbols.size());
    }

    /**
     * Map timeframe type to interval minutes.
     */
    private int mapTimeframeToInterval(TimeframeType timeframe) {
        return switch (timeframe) {
            case DAILY -> 1440; // 24 hours in minutes (broker API daily interval)
            case LTF, MINUTE_1 -> 1;
            case ITF, MINUTE_25 -> 25;
            case HTF, MINUTE_125 -> 125;
        };
    }
}
