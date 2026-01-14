package in.annupaper.repository;

import in.annupaper.domain.user.Portfolio;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository {
    List<Portfolio> findByUserId(String userId);
    Optional<Portfolio> findById(String portfolioId);
    void insert(Portfolio portfolio);
    void update(Portfolio portfolio);
    void delete(String portfolioId);
}
