package in.annupaper.application.port.output;

import in.annupaper.domain.model.Watchlist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface WatchlistRepository {
    List<Watchlist> findAll();

    List<Watchlist> findByUserBrokerId(String userBrokerId);

    List<Watchlist> findByUserId(String userId);

    java.util.Optional<Watchlist> findById(Long id);

    void insert(Watchlist watchlist);

    void save(Watchlist watchlist);

    void delete(Long id);

    void toggleEnabled(Long id, boolean enabled);

    // FIX: Add method to update last price for Market Watch real-time updates
    void updateLastPrice(String symbol, BigDecimal lastPrice, Instant lastTickTime);
}
