package in.annupaper.service.signal;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.domain.signal.Signal;
import in.annupaper.domain.data.Watchlist;
import in.annupaper.repository.WatchlistRepository;
import in.annupaper.service.MarketDataCache;
import in.annupaper.service.candle.SessionClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MTF Signal Generator - Tick-based Near Real-Time Signal Analysis
 *
 * Listens to incoming ticks and analyzes symbols for MTF confluence signals when:
 * 1. Price moves a minimum distance from last analyzed price (default: 0.3%)
 * 2. Market is open (NSE 9:15 AM - 3:30 PM IST)
 * 3. Sufficient candle data exists for MTF analysis
 *
 * Also provides scheduled fallback analysis every minute during market hours.
 *
 * Flow:
 * 1. Receive tick event
 * 2. Check if price moved minimum threshold from last analysis
 * 3. If yes, analyze MTF confluence using SignalService
 * 4. If triple buy confluence found, generate and broadcast signal
 */
public final class MtfSignalGenerator implements BrokerAdapter.TickListener {
    private static final Logger log = LoggerFactory.getLogger(MtfSignalGenerator.class);

    private final SignalService signalService;
    private final WatchlistRepository watchlistRepo;
    private final MarketDataCache marketDataCache;
    private final in.annupaper.repository.UserBrokerRepository userBrokerRepo;

    // Minimum price movement (%) before re-analyzing (default: 0.3%)
    private static final BigDecimal MIN_PRICE_MOVE_PCT = new BigDecimal("0.003");

    // Track last analyzed price per symbol to avoid excessive analysis
    private final ConcurrentHashMap<String, BigDecimal> lastAnalyzedPrice = new ConcurrentHashMap<>();

    // Track last analysis time to avoid duplicate signals within same minute
    private Instant lastAnalysisTime = Instant.EPOCH;

    public MtfSignalGenerator(
        SignalService signalService,
        WatchlistRepository watchlistRepo,
        MarketDataCache marketDataCache,
        in.annupaper.repository.UserBrokerRepository userBrokerRepo
    ) {
        this.signalService = signalService;
        this.watchlistRepo = watchlistRepo;
        this.marketDataCache = marketDataCache;
        this.userBrokerRepo = userBrokerRepo;
    }

    /**
     * Tick listener - Near real-time signal analysis on incoming ticks.
     * Analyzes symbol if price moved minimum threshold from last analysis.
     */
    @Override
    public void onTick(BrokerAdapter.Tick tick) {
        String symbol = tick.symbol();
        BigDecimal currentPrice = tick.lastPrice();
        Instant timestamp = Instant.ofEpochMilli(tick.timestamp());

        // Skip if outside market hours
        if (!SessionClock.isWithinSession(timestamp)) {
            return;
        }

        // Check if price moved enough from last analysis
        BigDecimal lastPrice = lastAnalyzedPrice.get(symbol);
        if (lastPrice != null) {
            BigDecimal priceChange = currentPrice.subtract(lastPrice).abs();
            BigDecimal priceChangePct = priceChange.divide(lastPrice, 6, RoundingMode.HALF_UP);

            if (priceChangePct.compareTo(MIN_PRICE_MOVE_PCT) < 0) {
                // Price hasn't moved enough, skip analysis
                return;
            }
        }

        // Update last analyzed price
        lastAnalyzedPrice.put(symbol, currentPrice);

        // Analyze and generate signal if confluence found
        try {
            Signal signal = signalService.analyzeAndGenerateSignal(symbol, currentPrice);

            if (signal != null) {
                log.info("[MTF SIGNAL TICK] ✓ Signal generated for {} @ {}: {} (score={})",
                    symbol, currentPrice, signal.signalId(), signal.confluenceScore());
            } else {
                log.debug("[MTF SIGNAL TICK] No confluence found for {} @ {}", symbol, currentPrice);
            }

        } catch (Exception e) {
            log.error("[MTF SIGNAL TICK] Error analyzing {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Scheduled analysis fallback - runs every minute during market hours.
     * Analyzes all symbols even if ticks are slow or missing.
     */
    public void performSignalAnalysis() {
        Instant now = Instant.now();

        // Skip if outside market hours
        if (!SessionClock.isWithinSession(now)) {
            log.debug("[MTF SIGNAL] Outside market hours, skipping signal analysis");
            return;
        }

        // Skip if already analyzed in current minute (prevent duplicate runs)
        if (now.getEpochSecond() / 60 == lastAnalysisTime.getEpochSecond() / 60) {
            log.debug("[MTF SIGNAL] Already analyzed in current minute, skipping");
            return;
        }

        lastAnalysisTime = now;

        log.info("[MTF SIGNAL] ════════════════════════════════════════════════════════");
        log.info("[MTF SIGNAL] Starting signal analysis for all symbols");
        log.info("[MTF SIGNAL] ════════════════════════════════════════════════════════");

        try {
            // Get data broker to retrieve watchlist
            java.util.Optional<in.annupaper.domain.broker.UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                log.debug("[MTF SIGNAL] No data broker configured, skipping");
                return;
            }

            String userBrokerId = dataBrokerOpt.get().userBrokerId();

            // Get all enabled watchlist symbols for data broker
            List<Watchlist> watchlists = watchlistRepo.findByUserBrokerId(userBrokerId);
            List<String> symbols = watchlists.stream()
                .filter(w -> w.enabled())
                .map(w -> w.symbol())
                .distinct()
                .toList();

            if (symbols.isEmpty()) {
                log.info("[MTF SIGNAL] No enabled watchlist symbols found");
                return;
            }

            log.info("[MTF SIGNAL] Analyzing {} symbols for MTF confluence", symbols.size());

            int signalsGenerated = 0;
            int symbolsAnalyzed = 0;
            int symbolsSkipped = 0;

            for (String symbol : symbols) {
                try {
                    // Get current price from market data cache
                    MarketDataCache.TickData tickData = marketDataCache.getLatestTick(symbol);

                    if (tickData == null || tickData.lastPrice() == null) {
                        log.debug("[MTF SIGNAL] No price data for {}, skipping", symbol);
                        symbolsSkipped++;
                        continue;
                    }

                    BigDecimal currentPrice = tickData.lastPrice();

                    symbolsAnalyzed++;

                    // Analyze and generate signal if confluence found
                    Signal signal = signalService.analyzeAndGenerateSignal(symbol, currentPrice);

                    if (signal != null) {
                        signalsGenerated++;
                        log.info("[MTF SIGNAL] ✓ Signal generated for {} @ {}: {} (score={})",
                            symbol, currentPrice, signal.signalId(), signal.confluenceScore());
                    } else {
                        log.debug("[MTF SIGNAL] No confluence found for {} @ {}", symbol, currentPrice);
                    }

                } catch (Exception e) {
                    log.error("[MTF SIGNAL] Error analyzing {}: {}", symbol, e.getMessage());
                    symbolsSkipped++;
                }
            }

            log.info("[MTF SIGNAL] ════════════════════════════════════════════════════════");
            log.info("[MTF SIGNAL] Analysis complete: {} analyzed, {} signals generated, {} skipped",
                symbolsAnalyzed, signalsGenerated, symbolsSkipped);
            log.info("[MTF SIGNAL] ════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("[MTF SIGNAL] Fatal error during signal analysis: {}", e.getMessage(), e);
        }
    }

    /**
     * Force immediate signal analysis for a specific symbol (for testing or manual trigger).
     */
    public Signal analyzeSymbol(String symbol, BigDecimal currentPrice) {
        log.info("[MTF SIGNAL] Manual analysis triggered for {} @ {}", symbol, currentPrice);
        try {
            Signal signal = signalService.analyzeAndGenerateSignal(symbol, currentPrice);
            if (signal != null) {
                log.info("[MTF SIGNAL] ✓ Manual signal generated: {}", signal.signalId());
            } else {
                log.info("[MTF SIGNAL] No confluence found for {}", symbol);
            }
            return signal;
        } catch (Exception e) {
            log.error("[MTF SIGNAL] Error in manual analysis for {}: {}", symbol, e.getMessage(), e);
            return null;
        }
    }
}
