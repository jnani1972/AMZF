package in.annupaper.feedrelay;

import in.annupaper.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RelayBroadcastTickListener implements BrokerAdapter.TickListener {
    private static final Logger log = LoggerFactory.getLogger(RelayBroadcastTickListener.class);

    private final TickRelayServer relay;

    public RelayBroadcastTickListener(TickRelayServer relay) {
        this.relay = relay;
    }

    @Override
    public void onTick(Tick tick) {
        try {
            relay.broadcast(TickJsonMapper.toJson(tick));
        } catch (Exception e) {
            log.warn("[RELAY] Tick broadcast failed for symbol={}", tick.symbol(), e);
        }
    }

    @Override
    public void onError(Throwable error) {
        log.error("[RELAY] Tick listener error", error);
    }
}
