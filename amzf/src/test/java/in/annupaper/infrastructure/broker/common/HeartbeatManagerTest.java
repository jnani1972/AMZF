package in.annupaper.infrastructure.broker.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HeartbeatManager.
 *
 * Tests:
 * - Periodic ping sending
 * - Pong receipt and health status
 * - Timeout detection (no pong received)
 * - Health callback notification
 * - Connection recovery
 * - Thread safety
 * - Lifecycle management
 */
class HeartbeatManagerTest {

    private HeartbeatManager heartbeat;

    @AfterEach
    void tearDown() {
        if (heartbeat != null) {
            heartbeat.stop();
        }
    }

    @Test
    void testInitialState() {
        AtomicInteger pingCount = new AtomicInteger(0);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            pingCount::incrementAndGet,
            healthy -> {}
        );

        assertEquals(0, pingCount.get(), "No pings sent before start");
        assertNull(heartbeat.getTimeSinceLastPong(), "No pongs received yet");
    }

    @Test
    void testPeriodicPingSending() throws InterruptedException {
        CountDownLatch pingLatch = new CountDownLatch(3);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(200),  // Ping every 200ms
            Duration.ofSeconds(1),
            pingLatch::countDown,
            healthy -> {}
        );

        heartbeat.start();

        // Wait for at least 3 pings
        assertTrue(pingLatch.await(2, TimeUnit.SECONDS), "Should send 3 pings within 2 seconds");
    }

    @Test
    void testPongReceptionUpdatesHealth() throws InterruptedException {
        AtomicBoolean healthStatus = new AtomicBoolean(true);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            () -> {},
            healthStatus::set
        );

        heartbeat.start();
        assertTrue(heartbeat.isHealthy(), "Should be healthy initially");

        // Record pong
        heartbeat.recordPong();
        assertTrue(heartbeat.isHealthy(), "Should remain healthy after pong");
        assertNotNull(heartbeat.getTimeSinceLastPong(), "Should track last pong time");
        assertTrue(heartbeat.getTimeSinceLastPong().toMillis() < 100,
            "Time since last pong should be very recent");
    }

    @Test
    void testTimeoutDetectionMarksUnhealthy() throws InterruptedException {
        CountDownLatch unhealthyLatch = new CountDownLatch(1);
        AtomicBoolean becameUnhealthy = new AtomicBoolean(false);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),  // Ping every 100ms
            Duration.ofMillis(300),  // Timeout after 300ms
            () -> {},  // Ping function
            healthy -> {
                if (!healthy) {
                    becameUnhealthy.set(true);
                    unhealthyLatch.countDown();
                }
            }
        );

        heartbeat.start();
        assertTrue(heartbeat.isHealthy(), "Should be healthy initially");

        // Don't record any pongs - should timeout
        assertTrue(unhealthyLatch.await(1, TimeUnit.SECONDS),
            "Should mark as unhealthy after timeout");
        assertTrue(becameUnhealthy.get(), "Health callback should indicate unhealthy");
        assertFalse(heartbeat.isHealthy(), "Should be unhealthy after timeout");
    }

    @Test
    void testConnectionRecoveryAfterTimeout() throws InterruptedException {
        CountDownLatch unhealthyLatch = new CountDownLatch(1);
        CountDownLatch healthyAgainLatch = new CountDownLatch(1);
        AtomicInteger healthChanges = new AtomicInteger(0);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofMillis(300),
            () -> {},
            healthy -> {
                healthChanges.incrementAndGet();
                if (!healthy) {
                    unhealthyLatch.countDown();
                } else if (unhealthyLatch.getCount() == 0) {
                    // Only count as recovery if we were previously unhealthy
                    healthyAgainLatch.countDown();
                }
            }
        );

        heartbeat.start();

        // Wait for timeout
        assertTrue(unhealthyLatch.await(1, TimeUnit.SECONDS), "Should become unhealthy");
        assertFalse(heartbeat.isHealthy());

        // Record pong to recover
        heartbeat.recordPong();
        assertTrue(healthyAgainLatch.await(1, TimeUnit.SECONDS), "Should become healthy again");
        assertTrue(heartbeat.isHealthy(), "Should be healthy after receiving pong");
        assertEquals(2, healthChanges.get(), "Health should change twice: unhealthy then healthy");
    }

    @Test
    void testHealthCallbackOnlyTriggeredOnChange() throws InterruptedException {
        AtomicInteger callbackCount = new AtomicInteger(0);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            () -> {},
            healthy -> callbackCount.incrementAndGet()
        );

        heartbeat.start();
        Thread.sleep(200);

        // Record multiple pongs - callback should not be triggered
        heartbeat.recordPong();
        heartbeat.recordPong();
        heartbeat.recordPong();

        Thread.sleep(200);
        assertEquals(0, callbackCount.get(),
            "Callback should not be triggered when health doesn't change");
    }

    @Test
    void testStopCancelsScheduledTasks() throws InterruptedException {
        AtomicInteger pingCount = new AtomicInteger(0);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofSeconds(1),
            pingCount::incrementAndGet,
            healthy -> {}
        );

        heartbeat.start();
        Thread.sleep(250);  // Allow a few pings
        int pingsBeforeStop = pingCount.get();
        assertTrue(pingsBeforeStop >= 2, "Should have sent some pings");

        heartbeat.stop();
        Thread.sleep(300);  // Wait longer than ping interval
        int pingsAfterStop = pingCount.get();

        assertEquals(pingsBeforeStop, pingsAfterStop,
            "No more pings should be sent after stop");
    }

    @Test
    void testMultipleStartCallsIgnored() throws InterruptedException {
        AtomicInteger pingCount = new AtomicInteger(0);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(200),
            Duration.ofSeconds(1),
            pingCount::incrementAndGet,
            healthy -> {}
        );

        heartbeat.start();
        Thread.sleep(100);
        int pingsAfterFirstStart = pingCount.get();

        // Second start should be ignored
        heartbeat.start();
        Thread.sleep(100);

        // Ping rate should not change
        assertTrue(pingCount.get() > pingsAfterFirstStart,
            "Pings should continue normally");
    }

    @Test
    void testPingFunctionException() throws InterruptedException {
        CountDownLatch unhealthyLatch = new CountDownLatch(1);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            () -> { throw new RuntimeException("Ping failed"); },
            healthy -> {
                if (!healthy) {
                    unhealthyLatch.countDown();
                }
            }
        );

        heartbeat.start();

        // Exception in ping function should mark as unhealthy
        assertTrue(unhealthyLatch.await(1, TimeUnit.SECONDS),
            "Should mark as unhealthy when ping function throws exception");
        assertFalse(heartbeat.isHealthy());
    }

    @Test
    void testHealthCallbackException() throws InterruptedException {
        AtomicInteger pingCount = new AtomicInteger(0);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofMillis(300),
            pingCount::incrementAndGet,
            healthy -> { throw new RuntimeException("Callback failed"); }
        );

        heartbeat.start();

        // Exception in callback should not break heartbeat manager
        Thread.sleep(500);
        assertTrue(pingCount.get() > 0, "Pings should continue despite callback exception");
    }

    @Test
    void testTimeSinceLastPongTracking() throws InterruptedException {
        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofSeconds(1),
            () -> {},
            healthy -> {}
        );

        heartbeat.start();
        assertNull(heartbeat.getTimeSinceLastPong(), "No pongs before recordPong()");

        heartbeat.recordPong();
        assertNotNull(heartbeat.getTimeSinceLastPong(), "Should track time after pong");
        Duration timeSincePong1 = heartbeat.getTimeSinceLastPong();
        assertTrue(timeSincePong1.toMillis() < 100, "Should be very recent");

        Thread.sleep(200);
        Duration timeSincePong2 = heartbeat.getTimeSinceLastPong();
        assertTrue(timeSincePong2.toMillis() >= 200,
            "Time since pong should increase");
        assertTrue(timeSincePong2.toMillis() > timeSincePong1.toMillis(),
            "Time should keep increasing");
    }

    @Test
    void testFactoryMethodForWebSocket() throws InterruptedException {
        CountDownLatch pingLatch = new CountDownLatch(1);
        AtomicBoolean receivedHealthCallback = new AtomicBoolean(false);

        heartbeat = HeartbeatManager.forWebSocket(
            "ZERODHA",
            "user123",
            pingLatch::countDown,
            healthy -> receivedHealthCallback.set(true)
        );

        heartbeat.start();

        // Verify default settings work
        assertTrue(pingLatch.await(35, TimeUnit.SECONDS),
            "Should send ping with default 30s interval");

        // Test timeout (default 60s) - this would take too long for unit test
        // Just verify the heartbeat was created successfully
        assertTrue(heartbeat.isHealthy(), "Should be healthy after start");
    }

    @Test
    void testIsHealthyChecksTimeout() throws InterruptedException {
        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(50),
            Duration.ofMillis(200),
            () -> {},
            healthy -> {}
        );

        heartbeat.start();
        heartbeat.recordPong();
        assertTrue(heartbeat.isHealthy(), "Should be healthy after pong");

        // Wait longer than timeout
        Thread.sleep(300);
        assertFalse(heartbeat.isHealthy(),
            "Should be unhealthy when last pong exceeds timeout");
    }

    @Test
    void testPongCancelsExistingTimeoutCheck() throws InterruptedException {
        AtomicInteger unhealthyCount = new AtomicInteger(0);

        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofMillis(100),
            Duration.ofMillis(400),
            () -> {},
            healthy -> {
                if (!healthy) {
                    unhealthyCount.incrementAndGet();
                }
            }
        );

        heartbeat.start();

        // Keep sending pongs before timeout
        for (int i = 0; i < 5; i++) {
            Thread.sleep(200);  // Less than 400ms timeout
            heartbeat.recordPong();
        }

        // Should never become unhealthy
        assertEquals(0, unhealthyCount.get(),
            "Should not become unhealthy when pongs received regularly");
        assertTrue(heartbeat.isHealthy());
    }

    @Test
    void testStopWhenNotRunning() {
        heartbeat = new HeartbeatManager(
            "ZERODHA",
            "user123",
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            () -> {},
            healthy -> {}
        );

        // Stop without start should not throw exception
        assertDoesNotThrow(() -> heartbeat.stop());
    }
}
