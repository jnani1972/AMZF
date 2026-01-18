package in.annupaper.application.port.output;

import in.annupaper.domain.model.WatchlistTemplate;
import in.annupaper.domain.model.WatchlistTemplateSymbol;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Watchlist Templates (Level 1).
 */
public interface WatchlistTemplateRepository {

    /**
     * Find all active templates.
     */
    List<WatchlistTemplate> findAllActive();

    /**
     * Find template by ID.
     */
    Optional<WatchlistTemplate> findById(String templateId);

    /**
     * Find all symbols for a template.
     */
    List<WatchlistTemplateSymbol> findSymbolsByTemplateId(String templateId);

    /**
     * Insert a new template.
     */
    void insert(WatchlistTemplate template);

    /**
     * Update an existing template.
     */
    void update(WatchlistTemplate template);

    /**
     * Soft delete a template.
     */
    void delete(String templateId);

    /**
     * Insert a symbol into a template.
     */
    void insertSymbol(WatchlistTemplateSymbol symbol);

    /**
     * Delete a symbol from a template.
     */
    void deleteSymbol(long symbolId);
}
