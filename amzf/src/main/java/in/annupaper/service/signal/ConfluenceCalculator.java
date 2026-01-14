package in.annupaper.service.signal;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import in.annupaper.domain.signal.MtfGlobalConfig;
import in.annupaper.service.candle.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Confluence Calculator - Analyzes multi-timeframe zones to find trading opportunities.
 *
 * Uses 3 timeframes:
 * - HTF (Higher Timeframe): 125-minute candles
 * - ITF (Intermediate Timeframe): 25-minute candles
 * - LTF (Lower Timeframe): 1-minute candles
 *
 * Confluence validation based on MTF configuration:
 * - TRIPLE: All 3 timeframes in buy zone (score = 1.00)
 * - DOUBLE: HTF + ITF in buy zone (score = 0.80)
 * - SINGLE: HTF only in buy zone (score = 0.50)
 */
public final class ConfluenceCalculator {
    private static final Logger log = LoggerFactory.getLogger(ConfluenceCalculator.class);

    private final CandleStore candleStore;
    private final in.annupaper.repository.MtfConfigRepository mtfConfigRepo;

    public ConfluenceCalculator(CandleStore candleStore, in.annupaper.repository.MtfConfigRepository mtfConfigRepo) {
        this.candleStore = candleStore;
        this.mtfConfigRepo = mtfConfigRepo;
    }

    /**
     * Calculate multi-timeframe zones for a symbol.
     *
     * @param symbol Symbol to analyze
     * @return Multi-timeframe zones or null if insufficient data
     */
    public ZoneDetector.MultiTimeframeZones calculateZones(String symbol) {
        try {
            // Get MTF config for per-timeframe buy zone percentages
            MtfGlobalConfig config = mtfConfigRepo.getGlobalConfig()
                .orElseThrow(() -> new RuntimeException("MTF global config not found"));

            // Fetch candles for all 3 timeframes based on lookback periods
            // HTF: 125-min, lookback 175 candles
            List<Candle> htfCandles = candleStore.getFromMemory(symbol, TimeframeType.HTF);
            if (htfCandles.isEmpty()) {
                htfCandles = candleStore.getFromPostgres(symbol, TimeframeType.HTF, TimeframeType.HTF.getLookback());
            }

            // ITF: 25-min, lookback 75 candles
            List<Candle> itfCandles = candleStore.getFromMemory(symbol, TimeframeType.ITF);
            if (itfCandles.isEmpty()) {
                itfCandles = candleStore.getFromPostgres(symbol, TimeframeType.ITF, TimeframeType.ITF.getLookback());
            }

            // LTF: 1-min, lookback 375 candles
            List<Candle> ltfCandles = candleStore.getFromMemory(symbol, TimeframeType.LTF);
            if (ltfCandles.isEmpty()) {
                ltfCandles = candleStore.getFromPostgres(symbol, TimeframeType.LTF, TimeframeType.LTF.getLookback());
            }

            // Calculate zones for each timeframe with per-TF buy zone percentages
            // HTF: 50%, ITF: 35%, LTF: 20%
            ZoneDetector.Zone htfZone = ZoneDetector.calculateZone(htfCandles, config.htfBuyZonePct());
            ZoneDetector.Zone itfZone = ZoneDetector.calculateZone(itfCandles, config.itfBuyZonePct());
            ZoneDetector.Zone ltfZone = ZoneDetector.calculateZone(ltfCandles, config.ltfBuyZonePct());

            // Check which timeframes failed and provide detailed diagnostic
            if (htfZone == null || itfZone == null || ltfZone == null) {
                StringBuilder missing = new StringBuilder();
                if (htfZone == null) {
                    missing.append(String.format("HTF(125m): %d/%d candles", htfCandles.size(), TimeframeType.HTF.getLookback()));
                }
                if (itfZone == null) {
                    if (missing.length() > 0) missing.append(", ");
                    missing.append(String.format("ITF(25m): %d/%d candles", itfCandles.size(), TimeframeType.ITF.getLookback()));
                }
                if (ltfZone == null) {
                    if (missing.length() > 0) missing.append(", ");
                    missing.append(String.format("LTF(1m): %d/%d candles", ltfCandles.size(), TimeframeType.LTF.getLookback()));
                }
                log.warn("Insufficient candle data for {} - Missing: {}", symbol, missing.toString());
                return null;
            }

            return new ZoneDetector.MultiTimeframeZones(
                htfZone,
                itfZone,
                ltfZone,
                TimeframeType.HTF,
                TimeframeType.ITF,
                TimeframeType.LTF
            );

        } catch (Exception e) {
            log.error("Failed to calculate zones for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Check if triple buy confluence exists for a symbol at current price.
     *
     * @param symbol Symbol to check
     * @param currentPrice Current price
     * @return True if all 3 timeframes are in buy zone
     */
    public boolean hasTripleBuyConfluence(String symbol, BigDecimal currentPrice) {
        ZoneDetector.MultiTimeframeZones zones = calculateZones(symbol);
        if (zones == null) {
            return false;
        }

        return zones.isTripleBuyConfluence(currentPrice);
    }

    /**
     * Get confluence score for a symbol at current price.
     *
     * @param symbol Symbol to check
     * @param currentPrice Current price
     * @return Confluence score (0.0 to 1.0), or null if zones can't be calculated
     */
    public BigDecimal getConfluenceScore(String symbol, BigDecimal currentPrice) {
        ZoneDetector.MultiTimeframeZones zones = calculateZones(symbol);
        if (zones == null) {
            return null;
        }

        return zones.getBuyConfluenceScore(currentPrice);
    }

    /**
     * Confluence analysis result.
     */
    public record ConfluenceResult(
        String symbol,
        BigDecimal currentPrice,
        ZoneDetector.MultiTimeframeZones zones,
        boolean hasTripleBuyConfluence,
        boolean hasTripleSellConfluence,
        BigDecimal confluenceScore,
        String confluenceDescription,
        String confluenceStrength,  // VERY_STRONG, STRONG, MODERATE, WEAK
        BigDecimal strengthMultiplier,  // 1.20, 1.00, 0.75, 0.50
        String minConfluenceTypeMet  // TRIPLE, DOUBLE, SINGLE, or NONE
    ) {
        /**
         * Check if this is a valid BUY signal based on minimum confluence type requirement.
         */
        public boolean isBuySignal() {
            return !minConfluenceTypeMet.equals("NONE");
        }

        /**
         * Check if this is a valid SELL signal.
         */
        public boolean isSellSignal() {
            return hasTripleSellConfluence;
        }

        /**
         * Get signal type as string.
         */
        public String getSignalType() {
            if (isBuySignal()) return "BUY";
            if (isSellSignal()) return "SELL";
            return "NEUTRAL";
        }
    }

    /**
     * Perform complete confluence analysis for a symbol.
     *
     * @param symbol Symbol to analyze
     * @param currentPrice Current price
     * @return Confluence analysis result or null if zones can't be calculated
     */
    public ConfluenceResult analyze(String symbol, BigDecimal currentPrice) {
        ZoneDetector.MultiTimeframeZones zones = calculateZones(symbol);
        if (zones == null) {
            return null;
        }

        // Get MTF config (use global config for now)
        MtfGlobalConfig config = mtfConfigRepo.getGlobalConfig()
            .orElseThrow(() -> new RuntimeException("MTF global config not found"));

        boolean hasTripleBuy = zones.isTripleBuyConfluence(currentPrice);
        boolean hasTripleSell = zones.isTripleSellConfluence(currentPrice);
        BigDecimal score = zones.getBuyConfluenceScore(currentPrice);
        String description = zones.getConfluenceDescription(currentPrice);

        // Determine confluence strength and multiplier based on score
        String strength = config.getConfluenceStrength(score);
        BigDecimal multiplier = config.getConfluenceMultiplier(score);

        // Check if minimum confluence type requirement is met
        String minConfluenceType = config.minConfluenceType();
        String metConfluenceType = determineMetConfluenceType(zones, currentPrice, minConfluenceType, score);

        log.debug("[CONFLUENCE] {} @ {}: score={}, strength={}, multiplier={}, required={}, met={}",
            symbol, currentPrice, score, strength, multiplier, minConfluenceType, metConfluenceType);

        return new ConfluenceResult(
            symbol,
            currentPrice,
            zones,
            hasTripleBuy,
            hasTripleSell,
            score,
            description,
            strength,
            multiplier,
            metConfluenceType
        );
    }

    /**
     * Determine which confluence type requirement is met.
     *
     * @param zones Multi-timeframe zones
     * @param currentPrice Current price
     * @param minRequired Minimum required type (TRIPLE, DOUBLE, or SINGLE)
     * @param score Composite confluence score
     * @return Met confluence type or "NONE" if requirement not met
     */
    private String determineMetConfluenceType(
        ZoneDetector.MultiTimeframeZones zones,
        BigDecimal currentPrice,
        String minRequired,
        BigDecimal score
    ) {
        // Check what confluence level we have
        boolean htfInZone = zones.htf().isInBuyZone(currentPrice);
        boolean itfInZone = zones.itf().isInBuyZone(currentPrice);
        boolean ltfInZone = zones.ltf().isInBuyZone(currentPrice);

        String actualType;
        if (htfInZone && itfInZone && ltfInZone) {
            actualType = "TRIPLE";
        } else if (htfInZone && itfInZone) {
            actualType = "DOUBLE";
        } else if (htfInZone) {
            actualType = "SINGLE";
        } else {
            return "NONE";  // HTF not in zone = no signal
        }

        // Check if actual type meets minimum requirement
        return switch (minRequired) {
            case "TRIPLE" -> actualType.equals("TRIPLE") ? "TRIPLE" : "NONE";
            case "DOUBLE" -> actualType.equals("TRIPLE") || actualType.equals("DOUBLE") ? actualType : "NONE";
            case "SINGLE" -> actualType;  // SINGLE, DOUBLE, or TRIPLE all acceptable
            default -> "NONE";
        };
    }
}
