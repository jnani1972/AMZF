package in.annupaper.domain.model;

import java.time.LocalDate;

/**
 * Daily trading performance metrics.
 */
public final class DailyPerformance {
    private final LocalDate date;
    private final int tradesClosed;
    private final int winningTrades;
    private final int losingTrades;
    private final double winRatePercent;
    private final double totalPnl;
    private final double avgPnlPerTrade;
    private final double bestTrade;
    private final double worstTrade;

    public DailyPerformance(LocalDate date, int tradesClosed, int winningTrades, int losingTrades,
            double winRatePercent, double totalPnl, double avgPnlPerTrade,
            double bestTrade, double worstTrade) {
        this.date = date;
        this.tradesClosed = tradesClosed;
        this.winningTrades = winningTrades;
        this.losingTrades = losingTrades;
        this.winRatePercent = winRatePercent;
        this.totalPnl = totalPnl;
        this.avgPnlPerTrade = avgPnlPerTrade;
        this.bestTrade = bestTrade;
        this.worstTrade = worstTrade;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getTradesClosed() {
        return tradesClosed;
    }

    public int getWinningTrades() {
        return winningTrades;
    }

    public int getLosingTrades() {
        return losingTrades;
    }

    public double getWinRatePercent() {
        return winRatePercent;
    }

    public double getTotalPnl() {
        return totalPnl;
    }

    public double getAvgPnlPerTrade() {
        return avgPnlPerTrade;
    }

    public double getBestTrade() {
        return bestTrade;
    }

    public double getWorstTrade() {
        return worstTrade;
    }
}
