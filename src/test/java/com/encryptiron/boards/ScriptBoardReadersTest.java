package com.encryptiron.boards;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class ScriptBoardReadersTest
{
    private static final int NONE = Integer.MIN_VALUE;

    private final FakeGame game = new FakeGame();
    private final AllianceLogReader allianceLogReader = new AllianceLogReader(game.boardTimesSender);
    private final JadChallengeBoardReader jadChallengeBoardReader = new JadChallengeBoardReader(game.boardTimesSender);

    private void allianceLogRow(Object... argumentsAfterScript)
    {
        Object[] arguments = new Object[argumentsAfterScript.length + 1];
        arguments[0] = 10125;
        System.arraycopy(argumentsAfterScript, 0, arguments, 1, argumentsAfterScript.length);
        allianceLogReader.onScriptPreFired(FakeGame.script(arguments));
    }

    @Test
    public void readsTheAllianceLogsPersonalTicksPerTeamSize()
    {
        allianceLogRow(17141, 17142, -1, NONE, 0, 273, 2097003, 16602, 0);
        allianceLogRow(17141, 17144, -1, NONE, 0, 3, NONE, NONE, NONE);
        allianceLogRow(17141, 17148, 1, NONE, 0, 414, 287, 292, 0);
        allianceLogRow(17141, 17148, 2, NONE, 0, 173, 136, 142, 0);
        allianceLogRow(17141, 17148, 8, NONE, 0, 0, 140, 163, 0);
        assertEquals(List.of(), game.sentTimes());

        allianceLogReader.onScriptPreFired(FakeGame.script(10124));
        assertEquals(List.of(
            "The Fractured Archive|Solo|Xarth|414 ticks",
            "The Fractured Archive|2 players|Xarth|173 ticks"), game.sentTimes());
    }

    @Test
    public void filesTheAllianceLogsOverallTabAsOverallTime()
    {
        allianceLogRow(17133, 17148, 3, NONE, 0, 1818, 1251, 1300, 0);
        allianceLogRow(17140, 17148, 2, NONE, 0, 303, 129, 130, 0);
        allianceLogReader.onScriptPreFired(FakeGame.script(10124));
        assertEquals(List.of(
            "The Fractured Archive|3 players|Overall time|1818 ticks",
            "The Fractured Archive|2 players|Dreadnoth the Jellied|303 ticks"), game.sentTimes());
    }

    @Test
    public void readsThePersonalTicksDuringARaidToo()
    {
        allianceLogRow(17141, 17148, 1, 250, 1, 414, 287, 292, 0);
        allianceLogReader.onScriptPreFired(FakeGame.script(10124));
        assertEquals(List.of("The Fractured Archive|Solo|Xarth|414 ticks"), game.sentTimes());
    }

    @Test
    public void ignoresATimeTooLongForTheGameToRecord()
    {
        allianceLogRow(17133, 17148, 1, NONE, 0, 30_000, 2460, 2500, 0);
        allianceLogReader.onScriptPreFired(FakeGame.script(10124));
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void readsEachJadChallengesPersonalBest()
    {
        jadChallengeBoardReader.onScriptPreFired(FakeGame.script(4023,
            "TzHaar-Ket-Rak's First Challenge<br><br>Personal Completions: 3<br>Global Completions: 573,706<br><br>"
                + "Personal Best Time: 0:39.60<br>Global Best Time: 0:06.00<br><br>Personal Deaths: 12<br>Global Deaths: 992,158<br><br><br>"
                + "TzHaar-Ket-Rak's Second Challenge<br><br>Personal Completions: 0<br><br>Personal Best Time: -<br>Global Best Time: 0:08.00<br><br><br>"
                + "TzHaar-Ket-Rak's Sixth Challenge<br><br>Personal Best Time: 3:15.60<br>Global Best Time: 1:10.00"));
        assertEquals(List.of(
            "TzHaar-Ket-Rak's First Challenge|Any||0:39.60",
            "TzHaar-Ket-Rak's Sixth Challenge|Any||3:15.60"), game.sentTimes());
    }
}
