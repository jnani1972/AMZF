package in.annupaper.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * EntrySignalCoordinator - Actor-based routing for entry signal operations.
 *
 * SINGLE-WRITER PER SYMBOL:
 * All operations for a specific symbol are routed to the same executor partition.
 * This ensures sequential processing and prevents race conditions.
 *
 * PARTITIONING STRATEGY:
 * - Partition count = clamp(availableProcessors(), 8, 32)
 * - Route by: hash(symbol) % partitions
 * - Below 8: risk contention with many active symbols
 * - Above 32: scheduler overhead dominates without benefit
 *
 * EXECUTION MODEL:
 * All signal mutations are submitted as async tasks to the correct partition.
 * Even if multiple ticks arrive simultaneously, they are serialized per symbol.
 *
 * Mirrors: TradeCoordinator pattern exactly
 */
public final class EntrySignalCoordinator {
    private static final Logger log = LoggerFactory.getLogger(EntrySignalCoordinator.class);

    private static final int MIN_PARTITIONS = 8;
    private static final int MAX_PARTITIONS = 32;

    private final ExecutorService[] partitions;
    private final int partitionCount;

    public EntrySignalCoordinator() {
        this.partitionCount = calculateOptimalPartitions();
        this.partitions = new ExecutorService[partitionCount];

        for (int i = 0; i < partitionCount; i++) {
            final int partitionIndex = i;  // Lambda requires final variable
            this.partitions[i] = Executors.newSingleThreadExecutor(runnable -> {
                Thread t = new Thread(runnable, "entry-signal-coordinator-" + partitionIndex);
                t.setDaemon(true);
                return t;
            });
        }

        log.info("EntrySignalCoordinator initialized with {} partitions (CPUs: {})",
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
     * Execute a task for a specific symbol on its designated partition.
     *
     * All operations for the same symbol will execute sequentially on the
     * same thread, preventing race conditions.
     *
     * @param symbol Symbol identifier
     * @param task Task to execute
     * @return CompletableFuture that completes when task finishes
     */
    public CompletableFuture<Void> execute(String symbol, Runnable task) {
        int partition = getPartition(symbol);
        return CompletableFuture.runAsync(task, partitions[partition]);
    }

    /**
     * Execute a task for a specific symbol and return a result.
     *
     * @param symbol Symbol identifier
     * @param task Task to execute (returns T)
     * @param <T> Result type
     * @return CompletableFuture with result
     */
    public <T> CompletableFuture<T> executeWithResult(String symbol, java.util.concurrent.Callable<T> task) {
        int partition = getPartition(symbol);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException("Entry signal operation failed: " + symbol, e);
            }
        }, partitions[partition]);
    }

    /**
     * Calculate partition index for a symbol.
     *
     * Uses hashCode() % partitionCount for consistent routing.
     * Math.abs() to handle negative hash codes.
     *
     * @param symbol Symbol identifier
     * @return Partition index (0 to partitionCount-1)
     */
    private int getPartition(String symbol) {
        return Math.abs(symbol.hashCode()) % partitionCount;
    }

    /**
     * Shutdown all partitions gracefully.
     *
     * Waits up to 30 seconds for pending tasks to complete.
     */
    public void shutdown() {
        log.info("Shutting down EntrySignalCoordinator with {} partitions", partitionCount);

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

        log.info("EntrySignalCoordinator shutdown complete");
    }

    /**
     * Get partition count (for testing/monitoring).
     */
    public int getPartitionCount() {
        return partitionCount;
    }
}
