package in.annupaper.service.candle;

import java.time.*;
import java.time.temporal.ChronoUnit;

/**
 * Session Clock - Manages market session boundaries and candle alignment.
 *
 * NSE Regular Session: 09:15 AM - 03:30 PM IST (6.25 hours = 375 minutes)
 * Alignment: All multi-minute candles align from session start (09:15 IST), not unix epoch.
 */
public final class SessionClock {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // NSE regular session times (IST)
    private static final LocalTime SESSION_START = LocalTime.of(9, 15);
    private static final LocalTime SESSION_END = LocalTime.of(15, 30);

    /**
     * Get session start for a given date (09:15 IST).
     */
    public static Instant getSessionStart(LocalDate date) {
        return ZonedDateTime.of(date, SESSION_START, IST).toInstant();
    }

    /**
     * Get session end for a given date (15:30 IST).
     */
    public static Instant getSessionEnd(LocalDate date) {
        return ZonedDateTime.of(date, SESSION_END, IST).toInstant();
    }

    /**
     * Get session start for today (09:15 IST).
     */
    public static Instant getTodaySessionStart() {
        return getSessionStart(LocalDate.now(IST));
    }

    /**
     * Get session end for today (15:30 IST).
     */
    public static Instant getTodaySessionEnd() {
        return getSessionEnd(LocalDate.now(IST));
    }

    /**
     * Check if current time is within market hours.
     */
    public static boolean isMarketOpen() {
        Instant now = Instant.now();
        Instant start = getTodaySessionStart();
        Instant end = getTodaySessionEnd();

        return !now.isBefore(start) && !now.isAfter(end);
    }

    /**
     * Check if given timestamp is within session for its date.
     */
    public static boolean isWithinSession(Instant timestamp) {
        LocalDate date = timestamp.atZone(IST).toLocalDate();
        Instant start = getSessionStart(date);
        Instant end = getSessionEnd(date);

        return !timestamp.isBefore(start) && !timestamp.isAfter(end);
    }

    /**
     * Floor timestamp to minute boundary.
     * Example: 09:15:37.123 -> 09:15:00.000
     */
    public static Instant floorToMinute(Instant timestamp) {
        return timestamp.truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * Floor timestamp to interval boundary aligned from session start.
     *
     * For 25-minute intervals:
     * Session start: 09:15
     * Buckets: 09:15-09:40, 09:40-10:05, 10:05-10:30, ...
     *
     * For 125-minute intervals:
     * Session start: 09:15
     * Buckets: 09:15-11:20, 11:20-13:25, 13:25-15:30
     *
     * @param timestamp The timestamp to floor
     * @param intervalMinutes The interval size in minutes (25 or 125)
     * @return Floored timestamp aligned to session start
     */
    public static Instant floorToIntervalFromSessionStart(Instant timestamp, int intervalMinutes) {
        LocalDate date = timestamp.atZone(IST).toLocalDate();
        Instant sessionStart = getSessionStart(date);

        // Calculate elapsed minutes from session start
        long elapsedMinutes = ChronoUnit.MINUTES.between(sessionStart, timestamp);

        // Floor to interval boundary
        long bucketIndex = elapsedMinutes / intervalMinutes;
        long flooredMinutes = bucketIndex * intervalMinutes;

        return sessionStart.plus(flooredMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Get the next candle start time.
     *
     * @param currentCandleStart Current candle start timestamp
     * @param intervalMinutes Interval size (1, 25, or 125 minutes)
     * @return Next candle start timestamp
     */
    public static Instant getNextCandleStart(Instant currentCandleStart, int intervalMinutes) {
        return currentCandleStart.plus(intervalMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Calculate number of candles between two timestamps.
     *
     * @param from Start timestamp
     * @param to End timestamp
     * @param intervalMinutes Interval size (1, 25, or 125 minutes)
     * @return Number of complete candles in range
     */
    public static long getCandleCount(Instant from, Instant to, int intervalMinutes) {
        long elapsedMinutes = ChronoUnit.MINUTES.between(from, to);
        return elapsedMinutes / intervalMinutes;
    }

    /**
     * Check if timestamp falls on a candle boundary.
     *
     * @param timestamp Timestamp to check
     * @param intervalMinutes Interval size (1, 25, or 125 minutes)
     * @return True if timestamp is exactly on a candle start boundary
     */
    public static boolean isOnCandleBoundary(Instant timestamp, int intervalMinutes) {
        LocalDate date = timestamp.atZone(IST).toLocalDate();
        Instant sessionStart = getSessionStart(date);

        long elapsedMinutes = ChronoUnit.MINUTES.between(sessionStart, timestamp);
        return elapsedMinutes % intervalMinutes == 0;
    }

    /**
     * Format timestamp as IST time string for logging.
     */
    public static String formatIST(Instant timestamp) {
        return timestamp.atZone(IST).toString();
    }
}
