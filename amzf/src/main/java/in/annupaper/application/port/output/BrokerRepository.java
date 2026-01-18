package in.annupaper.application.port.output;

import in.annupaper.domain.model.Broker;

import java.util.List;
import java.util.Optional;

public interface BrokerRepository {
    List<Broker> findAll();

    Optional<Broker> findById(String brokerId);

    Optional<Broker> findByCode(String brokerCode);

    void insert(Broker broker);

    void update(Broker broker);
}
