package in.annupaper.service.mtf;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import in.annupaper.domain.signal.MTFConfig;
import in.annupaper.domain.signal.TimeframeAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Multi-Timeframe Analysis Service.
 * Analyzes HTF, ITF, and LTF to determine buy zones and confluence.
 */
public final class MtfAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(MtfAnalysisService.class);
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal BUY_ZONE_MAX_PCT = new BigDecimal("0.35");
    
    // Candle provider: (symbol, timeframeType) -> List<Candle>
    private final BiFunction<String, TimeframeType, List<Candle>> candleProvider;
    
    public MtfAnalysisService(BiFunction<String, TimeframeType, List<Candle>> candleProvider) {
        this.candleProvider = candleProvider;
    }
    
    /**
     * Analyze all three timeframes for a symbol.
     */
    public MTFConfig analyze(String symbol, BigDecimal currentPrice) {
        log.debug("Analyzing MTF for {} @ {}", symbol, currentPrice);
        
        TimeframeAnalysis htf = analyzeTimeframe(symbol, TimeframeType.HTF, currentPrice);
        TimeframeAnalysis itf = analyzeTimeframe(symbol, TimeframeType.ITF, currentPrice);
        TimeframeAnalysis ltf = analyzeTimeframe(symbol, TimeframeType.LTF, currentPrice);
        
        MTFConfig config = MTFConfig.from(htf, itf, ltf, currentPrice);
        
        log.info("MTF Analysis: {} confluence={} score={} htfBuy={} itfBuy={} ltfBuy={}",
                 symbol, config.confluenceType(), config.confluenceScore(),
                 config.htfInBuyZone(), config.itfInBuyZone(), config.ltfInBuyZone());
        
        return config;
    }
    
    /**
     * Analyze a single timeframe.
     */
    public TimeframeAnalysis analyzeTimeframe(String symbol, TimeframeType tfType, BigDecimal currentPrice) {
        List<Candle> candles = candleProvider.apply(symbol, tfType);
        
        if (candles == null || candles.isEmpty()) {
            log.warn("No candles for {} {}", symbol, tfType);
            return createEmptyAnalysis(symbol, tfType);
        }
        
        // Calculate boundaries
        BigDecimal tfLow = candles.stream()
            .map(Candle::low)
            .min(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);
        
        BigDecimal tfHigh = candles.stream()
            .map(Candle::high)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);
        
        BigDecimal range = tfHigh.subtract(tfLow);
        
        // Calculate max drop from historical data
        BigDecimal maxDrop = calculateMaxDrop(candles);
        BigDecimal maxDropPct = tfHigh.compareTo(BigDecimal.ZERO) > 0
            ? maxDrop.divide(tfHigh, MC)
            : BigDecimal.ZERO;
        
        // Calculate number of zones
        int numZones = 1;
        if (maxDropPct.compareTo(BigDecimal.ZERO) > 0) {
            numZones = BigDecimal.ONE.divide(maxDropPct, 0, RoundingMode.CEILING).intValue();
            numZones = Math.max(1, Math.min(numZones, 50));
        }
        
        // Calculate current zone
        int currentZone = 1;
        if (tfLow.compareTo(BigDecimal.ZERO) > 0 && maxDropPct.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal distFromFloor = currentPrice.subtract(tfLow);
            BigDecimal pctFromFloor = distFromFloor.divide(tfLow, MC);
            currentZone = pctFromFloor.divide(maxDropPct, 0, RoundingMode.FLOOR).intValue() + 1;
            currentZone = Math.max(1, Math.min(currentZone, numZones));
        }
        
        // Check buy zone status (bottom 35% of range)
        boolean inBuyZone = false;
        BigDecimal buyZoneScore = BigDecimal.ONE;
        
        if (range.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal distFromFloor = currentPrice.subtract(tfLow);
            BigDecimal pctFromFloor = distFromFloor.divide(range, MC);
            
            inBuyZone = pctFromFloor.compareTo(BUY_ZONE_MAX_PCT) <= 0;
            buyZoneScore = pctFromFloor.divide(BUY_ZONE_MAX_PCT, MC);
            if (buyZoneScore.compareTo(BigDecimal.ONE) > 0) {
                buyZoneScore = BigDecimal.ONE;
            }
        }
        
        // Calculate drop distribution for P(fill)
        List<BigDecimal> dropDistribution = calculateDropDistribution(candles, maxDropPct, numZones);
        
        return TimeframeAnalysis.builder()
            .symbol(symbol)
            .timeframeType(tfType)
            .tfLow(tfLow)
            .tfHigh(tfHigh)
            .maxDrop(maxDrop)
            .maxDropPct(maxDropPct)
            .numZones(numZones)
            .currentZone(currentZone)
            .inBuyZone(inBuyZone)
            .buyZoneScore(buyZoneScore)
            .dropDistribution(dropDistribution)
            .build();
    }
    
    /**
     * Calculate maximum drop from candle history.
     */
    private BigDecimal calculateMaxDrop(List<Candle> candles) {
        BigDecimal maxDrop = BigDecimal.ZERO;
        BigDecimal runningHigh = BigDecimal.ZERO;
        
        for (Candle candle : candles) {
            if (candle.high().compareTo(runningHigh) > 0) {
                runningHigh = candle.high();
            }
            BigDecimal drop = runningHigh.subtract(candle.low());
            if (drop.compareTo(maxDrop) > 0) {
                maxDrop = drop;
            }
        }
        
        return maxDrop;
    }
    
    /**
     * Calculate drop distribution for P(fill) estimation.
     * Returns probability of reaching each zone based on historical drops.
     */
    private List<BigDecimal> calculateDropDistribution(List<Candle> candles, BigDecimal maxDropPct, int numZones) {
        List<BigDecimal> distribution = new ArrayList<>();
        
        if (candles.isEmpty() || maxDropPct.compareTo(BigDecimal.ZERO) == 0) {
            for (int i = 0; i < numZones; i++) {
                distribution.add(BigDecimal.ONE);
            }
            return distribution;
        }
        
        // Count drops reaching each zone level
        int[] zoneCounts = new int[numZones];
        BigDecimal runningHigh = BigDecimal.ZERO;
        
        for (Candle candle : candles) {
            if (candle.high().compareTo(runningHigh) > 0) {
                runningHigh = candle.high();
            }
            
            if (runningHigh.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dropPct = runningHigh.subtract(candle.low()).divide(runningHigh, MC);
                int zoneReached = dropPct.divide(maxDropPct, 0, RoundingMode.FLOOR).intValue();
                zoneReached = Math.min(zoneReached, numZones - 1);
                
                for (int i = 0; i <= zoneReached; i++) {
                    zoneCounts[i]++;
                }
            }
        }
        
        // Convert counts to probabilities
        int totalCandles = candles.size();
        for (int i = 0; i < numZones; i++) {
            BigDecimal prob = totalCandles > 0
                ? BigDecimal.valueOf(zoneCounts[i]).divide(BigDecimal.valueOf(totalCandles), MC)
                : BigDecimal.ZERO;
            distribution.add(prob);
        }
        
        return distribution;
    }
    
    private TimeframeAnalysis createEmptyAnalysis(String symbol, TimeframeType tfType) {
        return TimeframeAnalysis.builder()
            .symbol(symbol)
            .timeframeType(tfType)
            .tfLow(BigDecimal.ZERO)
            .tfHigh(BigDecimal.ZERO)
            .maxDrop(BigDecimal.ZERO)
            .maxDropPct(BigDecimal.ZERO)
            .numZones(1)
            .currentZone(1)
            .inBuyZone(false)
            .buyZoneScore(BigDecimal.ONE)
            .dropDistribution(List.of())
            .build();
    }
}
