package in.annupaper.infrastructure.broker.instrument;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.broker.BrokerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Zerodha Kite instrument fetcher.
 *
 * Fetches instrument data from Zerodha instruments JSON file.
 * URL: https://api.kite.trade/instruments
 *
 * CSV Format (despite .csv extension, it's actually CSV):
 * instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
 *
 * Example:
 * 3861249,15083,SBIN-EQ,STATE BANK OF INDIA,604.20,,,0.05,1,EQ,NSE,NSE
 */
public class ZerodhaInstrumentFetcher implements BrokerInstrumentFetcher {
    private static final Logger log = LoggerFactory.getLogger(ZerodhaInstrumentFetcher.class);

    private static final String INSTRUMENTS_URL = "https://api.kite.trade/instruments";
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ZerodhaInstrumentFetcher() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<List<BrokerAdapter.Instrument>> fetchAll() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[ZerodhaInstrumentFetcher] Fetching instruments from {}", INSTRUMENTS_URL);
                long startTime = System.currentTimeMillis();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(INSTRUMENTS_URL))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to fetch instruments: HTTP " + response.statusCode());
                }

                String data = response.body();
                List<BrokerAdapter.Instrument> instruments = parseCSV(data);

                long elapsed = System.currentTimeMillis() - startTime;
                log.info("[ZerodhaInstrumentFetcher] Fetched {} instruments in {}ms ({}KB)",
                    instruments.size(), elapsed, data.length() / 1024);

                return instruments;

            } catch (Exception e) {
                log.error("[ZerodhaInstrumentFetcher] Failed to fetch instruments: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to fetch Zerodha instruments", e);
            }
        });
    }

    @Override
    public CompletableFuture<FetchResult> fetchDelta(String lastUpdateHash) {
        // Zerodha doesn't support delta updates, always fetch full
        return fetchAll().thenApply(instruments -> {
            try {
                String newHash = calculateHash(instruments);
                return FetchResult.full(instruments, newHash, 0);
            } catch (Exception e) {
                log.error("[ZerodhaInstrumentFetcher] Failed to calculate hash", e);
                return FetchResult.full(instruments, null, 0);
            }
        });
    }

    @Override
    public String getBrokerCode() {
        return "ZERODHA";
    }

    @Override
    public boolean supportsDeltaUpdates() {
        return false;  // Zerodha requires full download each time
    }

    /**
     * Parse Zerodha CSV format.
     * Despite the URL, the data is CSV format, not JSON.
     */
    private List<BrokerAdapter.Instrument> parseCSV(String csvData) {
        List<BrokerAdapter.Instrument> instruments = new ArrayList<>();
        String[] lines = csvData.split("\n");
        int lineNumber = 0;

        if (lines.length == 0) {
            throw new RuntimeException("Empty CSV data");
        }

        // Skip header
        String header = lines[0];
        log.debug("[ZerodhaInstrumentFetcher] CSV Header: {}", header);

        for (int i = 1; i < lines.length; i++) {
            lineNumber = i + 1;
            String line = lines[i].trim();

            if (line.isEmpty()) {
                continue;
            }

            try {
                BrokerAdapter.Instrument instrument = parseLine(line);
                if (instrument != null) {
                    instruments.add(instrument);
                }
            } catch (Exception e) {
                log.warn("[ZerodhaInstrumentFetcher] Failed to parse line {}: {}", lineNumber, e.getMessage());
            }
        }

        log.info("[ZerodhaInstrumentFetcher] Parsed {} instruments from {} lines",
            instruments.size(), lineNumber);

        return instruments;
    }

    /**
     * Parse single CSV line.
     *
     * Format: instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
     * Example: 3861249,15083,SBIN-EQ,STATE BANK OF INDIA,604.20,,,0.05,1,EQ,NSE,NSE
     */
    private BrokerAdapter.Instrument parseLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String[] parts = line.split(",", -1);
        if (parts.length < 12) {
            log.warn("[ZerodhaInstrumentFetcher] Invalid line format (expected 12 fields): {}", line);
            return null;
        }

        try {
            String instrumentToken = parts[0].trim();
            String exchangeToken = parts[1].trim();
            String tradingSymbol = parts[2].trim();
            String name = parts[3].trim();
            // parts[4] is last_price - skip
            // parts[5] is expiry - skip
            // parts[6] is strike - skip
            String tickSizeStr = parts[7].trim();
            String lotSizeStr = parts[8].trim();
            String instrumentType = parts[9].trim();
            String segment = parts[10].trim();
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
                segment,
                instrumentToken,  // token
                lotSize,
                tickSize,
                null,  // expiryDate - TODO: parse from CSV
                null,  // strikePrice - TODO: parse from CSV
                null   // optionType - TODO: parse from CSV
            );

        } catch (Exception e) {
            log.warn("[ZerodhaInstrumentFetcher] Error parsing line: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalize Zerodha instrument type to standard format.
     */
    private String normalizeInstrumentType(String zerodhaType) {
        return switch (zerodhaType.toUpperCase()) {
            case "EQ" -> "EQUITY";
            case "FUT" -> "FUTURES";
            case "CE", "PE" -> "OPTIONS";
            case "COM" -> "COMMODITY";
            case "CUR" -> "CURRENCY";
            case "IDX" -> "INDEX";
            default -> zerodhaType;
        };
    }

    /**
     * Calculate hash of instruments for delta detection.
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
