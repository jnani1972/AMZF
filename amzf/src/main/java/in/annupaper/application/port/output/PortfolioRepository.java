package in.annupaper.application.port.output;

import in.annupaper.domain.model.Portfolio;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {
    List<Portfolio> findByUserId(String userId);

    Optional<Portfolio> findById(String portfolioId);

    List<Portfolio> findAll();

    void insert(Portfolio portfolio);

    void update(Portfolio portfolio);

    void delete(String portfolioId);
}
