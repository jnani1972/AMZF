package in.annupaper.infrastructure.broker.instrument;

import in.annupaper.broker.BrokerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/**
 * Upstox instrument fetcher.
 *
 * Fetches instrument data from Upstox CSV file.
 * URL: https://assets.upstox.com/market-quote/instruments/exchange/complete.csv.gz
 *
 * CSV Format:
 * instrument_key,exchange_token,trading_symbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,option_type,exchange
 *
 * Example:
 * NSE_EQ|INE062A01020,2885,SBIN,State Bank of India,595.50,,0.05,1,EQ,,NSE
 */
public class UpstoxInstrumentFetcher implements BrokerInstrumentFetcher {
    private static final Logger log = LoggerFactory.getLogger(UpstoxInstrumentFetcher.class);

    private static final String INSTRUMENTS_URL = "https://assets.upstox.com/market-quote/instruments/exchange/complete.csv.gz";
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient httpClient;

    public UpstoxInstrumentFetcher() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }

    @Override
    public CompletableFuture<List<BrokerAdapter.Instrument>> fetchAll() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[UpstoxInstrumentFetcher] Fetching instruments from {}", INSTRUMENTS_URL);
                long startTime = System.currentTimeMillis();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(INSTRUMENTS_URL))
                    .timeout(TIMEOUT)
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to fetch instruments: HTTP " + response.statusCode());
                }

                byte[] data = response.body();
                List<BrokerAdapter.Instrument> instruments = parseCSV(data);

                long elapsed = System.currentTimeMillis() - startTime;
                log.info("[UpstoxInstrumentFetcher] Fetched {} instruments in {}ms ({}KB)",
                    instruments.size(), elapsed, data.length / 1024);

                return instruments;

            } catch (Exception e) {
                log.error("[UpstoxInstrumentFetcher] Failed to fetch instruments: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to fetch Upstox instruments", e);
            }
        });
    }

    @Override
    public CompletableFuture<FetchResult> fetchDelta(String lastUpdateHash) {
        // Upstox doesn't support delta updates, always fetch full
        return fetchAll().thenApply(instruments -> {
            try {
                String newHash = calculateHash(instruments);
                return FetchResult.full(instruments, newHash, 0);
            } catch (Exception e) {
                log.error("[UpstoxInstrumentFetcher] Failed to calculate hash", e);
                return FetchResult.full(instruments, null, 0);
            }
        });
    }

    @Override
    public String getBrokerCode() {
        return "UPSTOX";
    }

    @Override
    public boolean supportsDeltaUpdates() {
        return false;  // Upstox requires full download each time
    }

    /**
     * Parse Upstox CSV format.
     */
    private List<BrokerAdapter.Instrument> parseCSV(byte[] gzipData) throws IOException {
        List<BrokerAdapter.Instrument> instruments = new ArrayList<>();
        int lineNumber = 0;

        try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(gzipData));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {

            String line;
            String header = reader.readLine();  // Skip header
            lineNumber++;

            if (header == null) {
                throw new IOException("Empty CSV file");
            }

            log.debug("[UpstoxInstrumentFetcher] CSV Header: {}", header);

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                try {
                    BrokerAdapter.Instrument instrument = parseLine(line);
                    if (instrument != null) {
                        instruments.add(instrument);
                    }
                } catch (Exception e) {
                    log.warn("[UpstoxInstrumentFetcher] Failed to parse line {}: {}", lineNumber, e.getMessage());
                    // Continue parsing other lines
                }
            }
        }

        log.info("[UpstoxInstrumentFetcher] Parsed {} instruments from {} lines",
            instruments.size(), lineNumber);

        return instruments;
    }

    /**
     * Parse single CSV line.
     *
     * Format: instrument_key,exchange_token,trading_symbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,option_type,exchange
     * Example: NSE_EQ|INE062A01020,2885,SBIN,State Bank of India,595.50,,0.05,1,EQ,,NSE
     */
    private BrokerAdapter.Instrument parseLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String[] parts = line.split(",", -1);  // -1 to keep trailing empty strings
        if (parts.length < 12) {
            log.warn("[UpstoxInstrumentFetcher] Invalid line format (expected 12 fields): {}", line);
            return null;
        }

        try {
            String instrumentKey = parts[0].trim();
            String exchangeToken = parts[1].trim();
            String tradingSymbol = parts[2].trim();
            String name = parts[3].trim();
            // parts[4] is last_price - skip
            // parts[5] is expiry - skip for now
            // parts[6] is strike - skip for now
            String tickSizeStr = parts[7].trim();
            String lotSizeStr = parts[8].trim();
            String instrumentType = parts[9].trim();
            // parts[10] is option_type - skip for now
            String exchange = parts[11].trim();

            // Parse numeric fields
            BigDecimal tickSize = tickSizeStr.isEmpty() ? BigDecimal.valueOf(0.05) : new BigDecimal(tickSizeStr);
            int lotSize = lotSizeStr.isEmpty() ? 1 : Integer.parseInt(lotSizeStr);

            // Normalize instrument type
            String normalizedType = normalizeInstrumentType(instrumentType);

            // Create instrument record
            return new BrokerAdapter.Instrument(
                exchange,
                tradingSymbol,
                name,
                normalizedType,
                "EQ",  // segment - defaulting to EQ for now
                instrumentKey,  // token = instrument_key
                lotSize,
                tickSize,
                null,  // expiryDate - TODO: parse from CSV
                null,  // strikePrice - TODO: parse from CSV
                null   // optionType - TODO: parse from CSV
            );

        } catch (Exception e) {
            log.warn("[UpstoxInstrumentFetcher] Error parsing line: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalize Upstox instrument type to standard format.
     */
    private String normalizeInstrumentType(String upstoxType) {
        return switch (upstoxType.toUpperCase()) {
            case "EQ" -> "EQUITY";
            case "FUT" -> "FUTURES";
            case "OPT", "CE", "PE" -> "OPTIONS";
            case "COM" -> "COMMODITY";
            case "CUR" -> "CURRENCY";
            case "IDX" -> "INDEX";
            default -> upstoxType;
        };
    }

    /**
     * Calculate hash of instruments for delta detection.
     * Simple implementation: count + first 10 symbols
     */
    private String calculateHash(List<BrokerAdapter.Instrument> instruments) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");

        // Hash count
        md.update(String.valueOf(instruments.size()).getBytes());

        // Hash first 10 symbols
        int count = Math.min(10, instruments.size());
        for (int i = 0; i < count; i++) {
            BrokerAdapter.Instrument inst = instruments.get(i);
            md.update((inst.exchange() + ":" + inst.tradingSymbol()).getBytes());
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}
