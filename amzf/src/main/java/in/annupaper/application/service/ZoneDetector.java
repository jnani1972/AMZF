package in.annupaper.application.service;

import in.annupaper.domain.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Zone Detector - Calculates buy and sell zones based on price range.
 */
public final class ZoneDetector {

    /**
     * Zone definition result.
     */
    public record Zone(
            BigDecimal high,
            BigDecimal low,
            BigDecimal range,
            BigDecimal buyZoneTop, // Bottom X%
            BigDecimal sellZoneBottom // Top X%
    ) {
        /**
         * Check if price is in buy zone.
         */
        public boolean isInBuyZone(BigDecimal price) {
            return price.compareTo(low) >= 0 && price.compareTo(buyZoneTop) <= 0;
        }

        /**
         * Check if price is in sell zone.
         */
        public boolean isInSellZone(BigDecimal price) {
            return price.compareTo(sellZoneBottom) >= 0 && price.compareTo(high) <= 0;
        }

        /**
         * Check if price is in neutral zone.
         */
        public boolean isInNeutralZone(BigDecimal price) {
            return !isInBuyZone(price) && !isInSellZone(price);
        }

        /**
         * Get zone name for price.
         */
        public String getZoneName(BigDecimal price) {
            if (isInBuyZone(price))
                return "BUY";
            if (isInSellZone(price))
                return "SELL";
            return "NEUTRAL";
        }

        /**
         * Get distance from buy zone top (as percentage).
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
     */
    public static Zone calculateZone(List<HistoricalCandle> candles, BigDecimal buyZonePct) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        BigDecimal high = BigDecimal.ZERO;
        BigDecimal low = new BigDecimal("1000000000"); // Max value

        for (HistoricalCandle candle : candles) {
            if (candle.high().compareTo(high) > 0)
                high = candle.high();
            if (candle.low().compareTo(low) < 0)
                low = candle.low();
        }

        return calculateZoneFromRange(high, low, buyZonePct);
    }

    public static Zone calculateZone(List<HistoricalCandle> candles) {
        return calculateZone(candles, new BigDecimal("0.35"));
    }

    public static Zone calculateZoneFromRange(BigDecimal high, BigDecimal low, BigDecimal buyZonePct) {
        if (high == null || low == null) {
            return null;
        }

        BigDecimal range = high.subtract(low);
        BigDecimal buyZoneSize = range.multiply(buyZonePct);
        BigDecimal buyZoneTop = low.add(buyZoneSize);
        BigDecimal sellZoneBottom = high.subtract(buyZoneSize);

        return new Zone(high, low, range, buyZoneTop, sellZoneBottom);
    }

    /**
     * Multi-timeframe zone result.
     */
    public record MultiTimeframeZones(
            Zone htf, // 125-min zone
            Zone itf, // 25-min zone
            Zone ltf, // 1-min zone
            TimeframeType htfTimeframe,
            TimeframeType itfTimeframe,
            TimeframeType ltfTimeframe) {
        public boolean isTripleBuyConfluence(BigDecimal currentPrice) {
            if (htf == null || itf == null || ltf == null) {
                return false;
            }

            return htf.isInBuyZone(currentPrice) &&
                    itf.isInBuyZone(currentPrice) &&
                    ltf.isInBuyZone(currentPrice);
        }

        public boolean isTripleSellConfluence(BigDecimal currentPrice) {
            if (htf == null || itf == null || ltf == null) {
                return false;
            }

            return htf.isInSellZone(currentPrice) &&
                    itf.isInSellZone(currentPrice) &&
                    ltf.isInSellZone(currentPrice);
        }

        public BigDecimal getBuyConfluenceScore(BigDecimal currentPrice) {
            BigDecimal score = BigDecimal.ZERO;

            if (htf != null && htf.isInBuyZone(currentPrice)) {
                score = score.add(new BigDecimal("0.50"));
            }

            if (itf != null && itf.isInBuyZone(currentPrice)) {
                score = score.add(new BigDecimal("0.30"));
            }

            if (ltf != null && ltf.isInBuyZone(currentPrice)) {
                score = score.add(new BigDecimal("0.20"));
            }

            return score;
        }

        public String getConfluenceDescription(BigDecimal currentPrice) {
            if (isTripleBuyConfluence(currentPrice)) {
                return "TRIPLE_BUY_CONFLUENCE";
            }
            if (isTripleSellConfluence(currentPrice)) {
                return "TRIPLE_SELL_CONFLUENCE";
            }

            StringBuilder sb = new StringBuilder();
            if (htf != null)
                sb.append("HTF:").append(htf.getZoneName(currentPrice)).append(" ");
            if (itf != null)
                sb.append("ITF:").append(itf.getZoneName(currentPrice)).append(" ");
            if (ltf != null)
                sb.append("LTF:").append(ltf.getZoneName(currentPrice));

            return sb.toString().trim();
        }
    }
}
