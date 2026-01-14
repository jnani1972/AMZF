package in.annupaper.repository;

import in.annupaper.domain.data.Watchlist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface WatchlistRepository {
    List<Watchlist> findByUserBrokerId(String userBrokerId);
    List<Watchlist> findByUserId(String userId);
    void insert(Watchlist watchlist);
    void delete(Long id);
    void toggleEnabled(Long id, boolean enabled);

    // FIX: Add method to update last price for Market Watch real-time updates
    void updateLastPrice(String symbol, BigDecimal lastPrice, Instant lastTickTime);
}
