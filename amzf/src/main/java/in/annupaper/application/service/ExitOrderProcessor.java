package in.annupaper.application.service;

import in.annupaper.domain.model.*;
import in.annupaper.application.port.output.ExitIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ExitOrderProcessor - Polls for APPROVED exit intents and places exit orders.
 */
public final class ExitOrderProcessor {
    private static final Logger log = LoggerFactory.getLogger(ExitOrderProcessor.class);

    private final ExitIntentRepository exitIntentRepo;
    private final ExitOrderExecutionService exitOrderExecutionService;
    private final ScheduledExecutorService scheduler;

    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int INITIAL_DELAY_SECONDS = 10;

    public ExitOrderProcessor(
            ExitIntentRepository exitIntentRepo,
            ExitOrderExecutionService exitOrderExecutionService) {
        this.exitIntentRepo = exitIntentRepo;
        this.exitOrderExecutionService = exitOrderExecutionService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "exit-order-processor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        log.info("Starting ExitOrderProcessor (polling every {}s)", POLL_INTERVAL_SECONDS);
        scheduler.scheduleAtFixedRate(
                this::processApprovedExitIntents,
                INITIAL_DELAY_SECONDS,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public void stop() {
        log.info("Stopping ExitOrderProcessor...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void processApprovedExitIntents() {
        try {
            List<ExitIntent> approvedIntents = exitIntentRepo.findPendingIntents();

            List<ExitIntent> intentsToProcess = approvedIntents.stream()
                    .filter(ExitIntent::isApproved)
                    .toList();

            if (intentsToProcess.isEmpty()) {
                return;
            }

            log.info("Processing {} approved exit intents", intentsToProcess.size());

            for (ExitIntent intent : intentsToProcess) {
                try {
                    exitOrderExecutionService.executeExitIntent(intent)
                            .exceptionally(ex -> {
                                log.error("Failed to execute exit intent {}: {}",
                                        intent.exitIntentId(), ex.getMessage());
                                return null;
                            });

                } catch (Exception e) {
                    log.error("Error processing exit intent {}: {}",
                            intent.exitIntentId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in exit order processing loop: {}", e.getMessage(), e);
        }
    }
}
