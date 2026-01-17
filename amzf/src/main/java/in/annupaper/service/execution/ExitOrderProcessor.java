package in.annupaper.service.execution;

import in.annupaper.domain.trade.ExitIntent;
import in.annupaper.domain.repository.ExitIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ExitOrderProcessor - Polls for APPROVED exit intents and places exit orders.
 *
 * Mirrors the pattern used for entry orders but for exits:
 * 1. Poll database every 5 seconds for exit intents with status=APPROVED
 * 2. For each approved intent, call ExitOrderExecutionService to place order
 * 3. Handle failures gracefully (intents marked FAILED, no retry here)
 *
 * This is a simple polling solution. Future improvement: Event-driven trigger when
 * exit intent transitions to APPROVED.
 *
 * CRITICAL: This closes the gap identified in ARCHITECTURE_STATUS_RESPONSE.md
 * "Exit Order Placement Missing" - now APPROVED exit intents will be processed.
 */
public final class ExitOrderProcessor {
    private static final Logger log = LoggerFactory.getLogger(ExitOrderProcessor.class);

    private final ExitIntentRepository exitIntentRepo;
    private final ExitOrderExecutionService exitOrderExecutionService;
    private final ScheduledExecutorService scheduler;

    // Configuration
    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int INITIAL_DELAY_SECONDS = 10;  // Wait 10 seconds on startup

    public ExitOrderProcessor(
        ExitIntentRepository exitIntentRepo,
        ExitOrderExecutionService exitOrderExecutionService
    ) {
        this.exitIntentRepo = exitIntentRepo;
        this.exitOrderExecutionService = exitOrderExecutionService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "exit-order-processor");
            t.setDaemon(true);  // Allow JVM to exit
            return t;
        });
    }

    /**
     * Start the processor (begins polling for approved exit intents).
     */
    public void start() {
        log.info("Starting ExitOrderProcessor (polling every {}s)", POLL_INTERVAL_SECONDS);
        scheduler.scheduleAtFixedRate(
            this::processApprovedExitIntents,
            INITIAL_DELAY_SECONDS,
            POLL_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    /**
     * Stop the processor (graceful shutdown).
     */
    public void stop() {
        log.info("Stopping ExitOrderProcessor...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            log.info("ExitOrderProcessor stopped");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Poll for APPROVED exit intents and process them.
     */
    private void processApprovedExitIntents() {
        try {
            // Query for APPROVED exit intents
            List<ExitIntent> approvedIntents = exitIntentRepo.findPendingIntents();

            // Filter to only APPROVED (findPendingIntents may return PENDING + APPROVED)
            List<ExitIntent> intentsToProcess = approvedIntents.stream()
                .filter(ExitIntent::isApproved)
                .toList();

            if (intentsToProcess.isEmpty()) {
                log.debug("No approved exit intents to process");
                return;
            }

            log.info("Processing {} approved exit intents", intentsToProcess.size());

            // Process each approved intent
            for (ExitIntent intent : intentsToProcess) {
                try {
                    log.debug("Processing exit intent: {} for trade {}",
                        intent.exitIntentId(), intent.tradeId());

                    // Place exit order (async - returns CompletableFuture)
                    exitOrderExecutionService.executeExitIntent(intent)
                        .exceptionally(ex -> {
                            log.error("Failed to execute exit intent {}: {}",
                                intent.exitIntentId(), ex.getMessage());
                            return null;  // Already marked FAILED by service
                        });

                } catch (Exception e) {
                    log.error("Error processing exit intent {}: {}",
                        intent.exitIntentId(), e.getMessage(), e);
                    // Continue processing other intents (don't let one failure stop all)
                }
            }

        } catch (Exception e) {
            log.error("Error in exit order processing loop: {}", e.getMessage(), e);
            // Don't throw - let scheduler continue on next interval
        }
    }
}
