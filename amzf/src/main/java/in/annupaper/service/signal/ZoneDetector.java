package in.annupaper.service.signal;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Zone Detector - Calculates buy and sell zones based on price range.
 *
 * Buy Zone: Bottom 35% of price range
 * Sell Zone: Top 35% of price range
 * Neutral Zone: Middle 30%
 */
public final class ZoneDetector {

    /**
     * Zone definition result.
     */
    public record Zone(
        BigDecimal high,
        BigDecimal low,
        BigDecimal range,
        BigDecimal buyZoneTop,    // 35% from bottom
        BigDecimal sellZoneBottom  // 35% from top
    ) {
        /**
         * Check if price is in buy zone (bottom 35%).
         */
        public boolean isInBuyZone(BigDecimal price) {
            return price.compareTo(low) >= 0 && price.compareTo(buyZoneTop) <= 0;
        }

        /**
         * Check if price is in sell zone (top 35%).
         */
        public boolean isInSellZone(BigDecimal price) {
            return price.compareTo(sellZoneBottom) >= 0 && price.compareTo(high) <= 0;
        }

        /**
         * Check if price is in neutral zone (middle 30%).
         */
        public boolean isInNeutralZone(BigDecimal price) {
            return !isInBuyZone(price) && !isInSellZone(price);
        }

        /**
         * Get zone name for price.
         */
        public String getZoneName(BigDecimal price) {
            if (isInBuyZone(price)) return "BUY";
            if (isInSellZone(price)) return "SELL";
            return "NEUTRAL";
        }

        /**
         * Get distance from buy zone top (as percentage).
         * Positive = above buy zone, Negative = below buy zone.
         */
        public BigDecimal getDistanceFromBuyZonePercent(BigDecimal price) {
            if (range.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return price.subtract(buyZoneTop)
                .divide(range, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }
    }

    /**
     * Calculate zone for a given set of candles with custom buy zone percentage.
     *
     * @param candles List of candles (should cover lookback period)
     * @param buyZonePct Buy zone percentage (e.g., 0.50 for 50%)
     * @return Zone definition
     */
    public static Zone calculateZone(List<Candle> candles, BigDecimal buyZonePct) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        // Find high and low from candle data
        BigDecimal high = candles.stream()
            .map(Candle::high)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        BigDecimal low = candles.stream()
            .map(Candle::low)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        return calculateZoneFromRange(high, low, buyZonePct);
    }

    /**
     * Calculate zone for a given set of candles with default 35% buy zone.
     * (Kept for backward compatibility)
     *
     * @param candles List of candles (should cover lookback period)
     * @return Zone definition
     */
    public static Zone calculateZone(List<Candle> candles) {
        return calculateZone(candles, new BigDecimal("0.35"));
    }

    /**
     * Calculate zone from high/low range with custom buy zone percentage.
     *
     * @param high Highest price in range
     * @param low Lowest price in range
     * @param buyZonePct Buy zone percentage (e.g., 0.35 for 35%, 0.50 for 50%)
     * @return Zone definition
     */
    public static Zone calculateZoneFromRange(BigDecimal high, BigDecimal low, BigDecimal buyZonePct) {
        if (high == null || low == null) {
            return null;
        }

        BigDecimal range = high.subtract(low);

        // Buy zone = bottom X% (configurable per timeframe)
        BigDecimal buyZoneSize = range.multiply(buyZonePct);
        BigDecimal buyZoneTop = low.add(buyZoneSize);

        // Sell zone = top X% (same percentage, symmetric)
        BigDecimal sellZoneSize = range.multiply(buyZonePct);
        BigDecimal sellZoneBottom = high.subtract(sellZoneSize);

        return new Zone(high, low, range, buyZoneTop, sellZoneBottom);
    }

    /**
     * Calculate zone from high/low range with default 35% buy zone.
     * (Kept for backward compatibility)
     *
     * @param high Highest price in range
     * @param low Lowest price in range
     * @return Zone definition
     */
    public static Zone calculateZoneFromRange(BigDecimal high, BigDecimal low) {
        return calculateZoneFromRange(high, low, new BigDecimal("0.35"));
    }

    /**
     * Multi-timeframe zone result.
     */
    public record MultiTimeframeZones(
        Zone htf,           // 125-min zone
        Zone itf,           // 25-min zone
        Zone ltf,           // 1-min zone
        TimeframeType htfTimeframe,
        TimeframeType itfTimeframe,
        TimeframeType ltfTimeframe
    ) {
        /**
         * Check if current price is in buy zone across all timeframes (triple confluence).
         */
        public boolean isTripleBuyConfluence(BigDecimal currentPrice) {
            if (htf == null || itf == null || ltf == null) {
                return false;
            }

            return htf.isInBuyZone(currentPrice) &&
                   itf.isInBuyZone(currentPrice) &&
                   ltf.isInBuyZone(currentPrice);
        }

        /**
         * Check if current price is in sell zone across all timeframes.
         */
        public boolean isTripleSellConfluence(BigDecimal currentPrice) {
            if (htf == null || itf == null || ltf == null) {
                return false;
            }

            return htf.isInSellZone(currentPrice) &&
                   itf.isInSellZone(currentPrice) &&
                   ltf.isInSellZone(currentPrice);
        }

        /**
         * Get confluence score (0.0 to 1.0).
         * Based on how many timeframes are in buy zone with weighted scoring.
         * HTF = 50%, ITF = 30%, LTF = 20%
         */
        public BigDecimal getBuyConfluenceScore(BigDecimal currentPrice) {
            BigDecimal score = BigDecimal.ZERO;

            if (htf != null && htf.isInBuyZone(currentPrice)) {
                score = score.add(new BigDecimal("0.50"));  // HTF weight
            }

            if (itf != null && itf.isInBuyZone(currentPrice)) {
                score = score.add(new BigDecimal("0.30"));  // ITF weight
            }

            if (ltf != null && ltf.isInBuyZone(currentPrice)) {
                score = score.add(new BigDecimal("0.20"));  // LTF weight
            }

            return score;
        }

        /**
         * Get textual representation of confluence state.
         */
        public String getConfluenceDescription(BigDecimal currentPrice) {
            if (isTripleBuyConfluence(currentPrice)) {
                return "TRIPLE_BUY_CONFLUENCE";
            }
            if (isTripleSellConfluence(currentPrice)) {
                return "TRIPLE_SELL_CONFLUENCE";
            }

            StringBuilder sb = new StringBuilder();
            if (htf != null) sb.append("HTF:").append(htf.getZoneName(currentPrice)).append(" ");
            if (itf != null) sb.append("ITF:").append(itf.getZoneName(currentPrice)).append(" ");
            if (ltf != null) sb.append("LTF:").append(ltf.getZoneName(currentPrice));

            return sb.toString().trim();
        }
    }
}
