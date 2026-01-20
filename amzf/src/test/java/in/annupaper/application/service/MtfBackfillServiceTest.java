package in.annupaper.application.service;

import in.annupaper.application.port.output.MtfConfigRepository;
import in.annupaper.application.port.output.WatchlistRepository;
import in.annupaper.domain.model.MtfGlobalConfig;
import in.annupaper.domain.model.TimeframeType;
import in.annupaper.service.candle.CandleAggregator;
import in.annupaper.service.candle.HistoryBackfiller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MtfBackfillServiceTest {

    @Mock
    private HistoryBackfiller historyBackfiller;
    @Mock
    private CandleAggregator candleAggregator;
    @Mock
    private WatchlistRepository watchlistRepo;
    @Mock
    private MtfConfigRepository mtfConfigRepo;

    private MtfBackfillService service;

    @BeforeEach
    void setUp() {
        service = new MtfBackfillService(historyBackfiller, candleAggregator, watchlistRepo, mtfConfigRepo);
    }

    @Test
    void backfillSymbol_usesDynamicLookbackCalculation() {
        // Arrange
        String symbol = "TEST_SYMBOL";
        String userBrokerId = "test-user";
        Instant now = Instant.now();

        // 175 candles, 125 min each = 21,875 mins
        // 21875 / (6.25*60) = 58.33 trading days
        // *2.0 safety = 117 days
        MtfGlobalConfig config = mock(MtfGlobalConfig.class);
        when(config.htfCandleCount()).thenReturn(175);
        when(config.htfCandleMinutes()).thenReturn(125);
        when(mtfConfigRepo.getGlobalConfig()).thenReturn(Optional.of(config));

        when(historyBackfiller.backfillRange(any(), any(), any(), any())).thenReturn(100);

        // Act
        service.backfillSymbol(symbol, userBrokerId);

        // Assert
        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(historyBackfiller).backfillRange(eq(symbol), eq(TimeframeType.LTF), startCaptor.capture(), any());

        Instant capturedStart = startCaptor.getValue();
        long daysDiff = ChronoUnit.DAYS.between(capturedStart, now);

        // Should be around 117 days
        assertTrue(daysDiff >= 115 && daysDiff <= 120, "Lookback days should be approx 117, got: " + daysDiff);
    }

    @Test
    void backfillSymbol_usesDefaultWhenConfigMissing() {
        // Arrange
        String symbol = "TEST_SYMBOL";
        String userBrokerId = "test-user";
        Instant now = Instant.now();

        when(mtfConfigRepo.getGlobalConfig()).thenReturn(Optional.empty());
        when(historyBackfiller.backfillRange(any(), any(), any(), any())).thenReturn(100);

        // Act
        service.backfillSymbol(symbol, userBrokerId);

        // Assert
        // Default: 175 candles * 125 min
        // Same calculation logic applies in the code if defaults match the config above
        // verify calculation is consistent
        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(historyBackfiller).backfillRange(eq(symbol), eq(TimeframeType.LTF), startCaptor.capture(), any());

        Instant capturedStart = startCaptor.getValue();
        long daysDiff = ChronoUnit.DAYS.between(capturedStart, now);
        assertTrue(daysDiff >= 115 && daysDiff <= 120, "Lookback days should be approx 117, got: " + daysDiff);
    }
}
