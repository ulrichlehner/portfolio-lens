package io.portfoliolens.page;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ChartColorsTest
{
    @Test
    public void testBlendFromFundsBlueToDirectGreen()
    {
        assertEquals("#1F77B4", ChartColors.tileColor(0));
        assertEquals("#2CA02C", ChartColors.tileColor(1));
        // linear per channel: (31+44)/2, (119+160)/2, (180+44)/2, rounded half up
        assertEquals("#268C70", ChartColors.tileColor(0.5));
    }

    @Test
    public void testTwentyStepsAndClamping()
    {
        assertEquals(ChartColors.tileColor(0.5), ChartColors.tileColor(0.51));
        assertEquals(ChartColors.tileColor(1), ChartColors.tileColor(3));
        assertEquals(ChartColors.tileColor(0), ChartColors.tileColor(-1));
    }
}
