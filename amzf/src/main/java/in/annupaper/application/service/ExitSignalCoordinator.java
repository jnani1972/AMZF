package in.annupaper.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ExitSignalCoordinator - Actor-based routing for exit signal operations.
 *
 * SINGLE-WRITER PER TRADE:
 * All operations for a specific trade are routed to the same executor partition.
 * This ensures sequential exit detection and prevents episode races.
 *
 * PARTITIONING STRATEGY:
 * - Partition count = clamp(availableProcessors(), 8, 32)
 * - Route by: hash(tradeId) % partitions
 * - Mirrors: TradeCoordinator routing exactly
 *
 * EXECUTION MODEL:
 * All exit signal mutations are submitted as async tasks to the correct partition.
 * Multiple exit reasons (TARGET_HIT, STOP_LOSS, etc.) for same trade are serialized.
 *
 * ALIGNMENT:
 * Routes by trade_id (same as TradeManagementService) for perfect handoff.
 */
public final class ExitSignalCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ExitSignalCoordinator.class);

    private static final int MIN_PARTITIONS = 8;
    private static final int MAX_PARTITIONS = 32;

    private final ExecutorService[] partitions;
    private final int partitionCount;

    public ExitSignalCoordinator() {
        this.partitionCount = calculateOptimalPartitions();
        this.partitions = new ExecutorService[partitionCount];

        for (int i = 0; i < partitionCount; i++) {
            final int partitionIndex = i;  // Lambda requires final variable
            this.partitions[i] = Executors.newSingleThreadExecutor(runnable -> {
                Thread t = new Thread(runnable, "exit-signal-coordinator-" + partitionIndex);
                t.setDaemon(true);
                return t;
            });
        }

        log.info("ExitSignalCoordinator initialized with {} partitions (CPUs: {})",
            partitionCount, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Calculate optimal partition count with clamping.
     *
     * Rule: clamp(availableProcessors(), 8, 32)
     *
     * @return Partition count
     */
    private static int calculateOptimalPartitions() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(MIN_PARTITIONS, Math.min(MAX_PARTITIONS, processors));
    }

    /**
     * Execute a task for a specific trade on its designated partition.
     *
     * All operations for the same tradeId will execute sequentially on the
     * same thread, preventing race conditions.
     *
     * @param tradeId Trade identifier
     * @param task Task to execute
     * @return CompletableFuture that completes when task finishes
     */
    public CompletableFuture<Void> execute(String tradeId, Runnable task) {
        int partition = getPartition(tradeId);
        return CompletableFuture.runAsync(task, partitions[partition]);
    }

    /**
     * Execute a task for a specific trade and return a result.
     *
     * @param tradeId Trade identifier
     * @param task Task to execute (returns T)
     * @param <T> Result type
     * @return CompletableFuture with result
     */
    public <T> CompletableFuture<T> executeWithResult(String tradeId, java.util.concurrent.Callable<T> task) {
        int partition = getPartition(tradeId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException("Exit signal operation failed: " + tradeId, e);
            }
        }, partitions[partition]);
    }

    /**
     * Calculate partition index for a trade ID.
     *
     * Uses hashCode() % partitionCount for consistent routing.
     * Math.abs() to handle negative hash codes.
     *
     * @param tradeId Trade identifier
     * @return Partition index (0 to partitionCount-1)
     */
    private int getPartition(String tradeId) {
        return Math.abs(tradeId.hashCode()) % partitionCount;
    }

    /**
     * Shutdown all partitions gracefully.
     *
     * Waits up to 30 seconds for pending tasks to complete.
     */
    public void shutdown() {
        log.info("Shutting down ExitSignalCoordinator with {} partitions", partitionCount);

        for (int i = 0; i < partitionCount; i++) {
            partitions[i].shutdown();
        }

        try {
            for (int i = 0; i < partitionCount; i++) {
                if (!partitions[i].awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Partition {} did not terminate in time, forcing shutdown", i);
                    partitions[i].shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted", e);
            for (ExecutorService partition : partitions) {
                partition.shutdownNow();
            }
            Thread.currentThread().interrupt();
        }

        log.info("ExitSignalCoordinator shutdown complete");
    }

    /**
     * Get partition count (for testing/monitoring).
     */
    public int getPartitionCount() {
        return partitionCount;
    }
}
