package in.annupaper.application.port.output;

import in.annupaper.domain.model.WatchlistSelected;
import in.annupaper.domain.model.WatchlistSelectedSymbol;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Admin Selected Watchlists (Level 2).
 */
public interface WatchlistSelectedRepository {

    /**
     * Find all active selected watchlists.
     */
    List<WatchlistSelected> findAllActive();

    /**
     * Find selected watchlist by ID.
     */
    Optional<WatchlistSelected> findById(String selectedId);

    /**
     * Find all symbols for a selected watchlist.
     */
    List<WatchlistSelectedSymbol> findSymbolsBySelectedId(String selectedId);

    /**
     * Get all unique symbols from all active selected watchlists (Level 3 merge).
     */
    List<String> findMergedDefaultSymbols();

    /**
     * Insert a new selected watchlist.
     */
    void insert(WatchlistSelected selected);

    /**
     * Update an existing selected watchlist.
     */
    void update(WatchlistSelected selected);

    /**
     * Soft delete a selected watchlist.
     */
    void delete(String selectedId);

    /**
     * Insert a symbol into a selected watchlist.
     */
    void insertSymbol(WatchlistSelectedSymbol symbol);

    /**
     * Delete a symbol from a selected watchlist.
     */
    void deleteSymbol(long symbolId);

    /**
     * Delete all symbols for a selected watchlist.
     */
    void deleteAllSymbols(String selectedId);
}
