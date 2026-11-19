package com.encryptiron.runs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CompletedRunTrackerTest
{
    @Test
    public void readsPreciseAndWholeSecondTimes()
    {
        assertEquals(83_000, CompletedRunTracker.timeToMs("1:23"));
        assertEquals(83_400, CompletedRunTracker.timeToMs("1:23.40"));
        assertEquals(3_723_600, CompletedRunTracker.timeToMs("1:02:03.60"));
    }

    @Test
    public void takesTheLongestTimeOnALine()
    {
        assertEquals(1_290_000, CompletedRunTracker.longestTimeIn(
            "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>21:30</col> Personal best: </col><col=ff0000>20:00</col>"));
        assertEquals(0, CompletedRunTracker.longestTimeIn("Your Dagannoth Rex kill count is: <col=ff0000>12</col>."));
    }
}
