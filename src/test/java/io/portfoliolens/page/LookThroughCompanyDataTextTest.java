package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.portfoliolens.core.CompanyEnrichmentService.Progress;
import io.portfoliolens.core.CompanyEnrichmentService.State;

public class LookThroughCompanyDataTextTest
{
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static String text(Progress progress)
    {
        return LookThroughDataStatus.companyText(progress, NOW, ZoneOffset.UTC).orElseThrow();
    }

    @Test
    public void testNoLineWithoutCompanies()
    {
        assertTrue(LookThroughDataStatus.companyText(Progress.none(), NOW, ZoneOffset.UTC).isEmpty());
    }

    @Test
    public void testLoadingAndComplete()
    {
        assertEquals(MessageFormat.format(Texts.LookThroughCompanyDataLoading, 1234, 3975, ""),
                        text(new Progress(1234, 3975, State.LOADING, Optional.empty())));
        assertEquals(MessageFormat.format(Texts.LookThroughCompanyDataComplete, 3975, 3975, ""),
                        text(new Progress(3975, 3975, State.COMPLETE, Optional.empty())));
    }

    @Test
    public void testUnavailableNamesTheRetryTimeToday()
    {
        var retry = NOW.plusSeconds(40 * 60);
        var time = retry.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
        assertEquals(MessageFormat.format(Texts.LookThroughCompanyDataUnavailable, 12, 40, time),
                        text(new Progress(12, 40, State.UNAVAILABLE, Optional.of(retry))));
    }

    @Test
    public void testPauseUntilTomorrowNamesTheDate()
    {
        var retry = NOW.plusSeconds(24 * 3600);
        var date = retry.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT));
        assertEquals(MessageFormat.format(Texts.LookThroughCompanyDataPaused, 12, 40, date),
                        text(new Progress(12, 40, State.PAUSED, Optional.of(retry))));
        assertEquals(MessageFormat.format(Texts.LookThroughCompanyDataPausedOpen, 12, 40, ""),
                        text(new Progress(12, 40, State.PAUSED, Optional.empty())));
    }

    @Test
    public void testNoKeyAndNoAccess()
    {
        assertEquals(MessageFormat.format(Texts.LookThroughCompanyDataNoKey, 0, 40, ""),
                        text(new Progress(0, 40, State.NO_KEY, Optional.empty())));
        assertEquals(MessageFormat.format(Texts.LookThroughCompanyDataNoAccess, 5, 40, ""),
                        text(new Progress(5, 40, State.NO_ACCESS, Optional.empty())));
    }
}
