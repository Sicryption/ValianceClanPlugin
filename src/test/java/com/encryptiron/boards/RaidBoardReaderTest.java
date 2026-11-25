package com.encryptiron.boards;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InterfaceID.ToaScoreboard;
import net.runelite.api.gameval.InterfaceID.TobScoreboard;

public class RaidBoardReaderTest
{
    private static final int TOB_TAB = 12270;
    private static final int TOA_TAB = 14320;
    private static final int NORMAL = 0;
    private static final int ENTRY = 1;
    private static final int HARD = 2;

    private final FakeGame game = new FakeGame();
    private final RaidBoardReader reader = new RaidBoardReader(game.client, game.boardTimesSender);

    private void tick()
    {
        reader.onGameTick(new GameTick());
    }

    private void clickTobTab(int tab)
    {
        reader.onScriptPreFired(FakeGame.script(4493, 23789572, 23789573, 23789574, tab));
        game.varbits.put(TOB_TAB, tab);
    }

    private void serverOpensTob(String threePlayerChallenge)
    {
        game.textByComponent.put(TobScoreboard.DATA_3MAN_P_R, threePlayerChallenge);
        reader.onWidgetLoaded(game.open(InterfaceID.TOB_SCOREBOARD));
    }

    @Test
    public void readsChallengeAndOverallTimesPerTeamSize()
    {
        game.varbits.put(TOB_TAB, HARD);
        game.textByComponent.put(TobScoreboard.DATA_SOLO_P_R, "25:00.00");
        game.textByComponent.put(TobScoreboard.DATA_5MAN_P_R, "18:00.60");
        game.textByComponent.put(TobScoreboard.DATA_5MAN_P_O, "20:15.00");
        game.textByComponent.put(TobScoreboard.DATA_5MAN_G_R, "14:00.00");
        reader.onWidgetLoaded(game.open(InterfaceID.TOB_SCOREBOARD));
        tick();
        assertEquals(List.of(
            "Theatre of Blood: Hard Mode|Solo||25:00.00",
            "Theatre of Blood: Hard Mode|5 players||18:00.60",
            "Theatre of Blood: Hard Mode|5 players|Overall time|20:15.00"), game.sentTimes());
    }

    @Test
    public void readsTombsModesUpToEightPlayers()
    {
        game.varbits.put(TOA_TAB, 2);
        game.textByComponent.put(ToaScoreboard.DATA_8MAN_P_R, "15:00.00");
        game.textByComponent.put(ToaScoreboard.DATA_8MAN_P_O, "17:30.00");
        reader.onWidgetLoaded(game.open(InterfaceID.TOA_SCOREBOARD));
        tick();
        assertEquals(List.of(
            "Tombs of Amascut: Expert Mode|8 players||15:00.00",
            "Tombs of Amascut: Expert Mode|8 players|Overall time|17:30.00"), game.sentTimes());
    }

    @Test
    public void neverReadsTheOldTimesBetweenATabClickAndTheBoardReopening()
    {
        game.varbits.put(TOB_TAB, ENTRY);
        serverOpensTob("18:10.20");
        tick();
        clickTobTab(NORMAL);
        tick();
        serverOpensTob("17:24.60");
        tick();
        assertEquals(List.of("Theatre of Blood: Entry Mode|3 players||18:10.20", "Theatre of Blood|3 players||17:24.60"), game.sentTimes());
    }

    @Test
    public void readsNothingMoreUntilClosedAfterTwoClicksBeforeTheBoardReopened()
    {
        game.varbits.put(TOB_TAB, NORMAL);
        serverOpensTob("17:24.60");
        tick();
        clickTobTab(ENTRY);
        clickTobTab(HARD);
        serverOpensTob("18:10.20");
        tick();
        serverOpensTob("20:00.00");
        tick();
        assertEquals(List.of("Theatre of Blood|3 players||17:24.60"), game.sentTimes());

        game.openInterfaces.clear();
        tick();
        serverOpensTob("20:00.00");
        tick();
        assertEquals(List.of("Theatre of Blood|3 players||17:24.60", "Theatre of Blood: Hard Mode|3 players||20:00.00"), game.sentTimes());
    }

    @Test
    public void cancelsAReadWhenATabIsClickedBeforeTheTickEnds()
    {
        game.varbits.put(TOB_TAB, NORMAL);
        serverOpensTob("17:24.60");
        clickTobTab(ENTRY);
        tick();
        assertEquals(List.of(), game.sentTimes());

        serverOpensTob("18:10.20");
        tick();
        assertEquals(List.of("Theatre of Blood: Entry Mode|3 players||18:10.20"), game.sentTimes());
    }

    @Test
    public void clickingTheSelectedTabChangesNothing()
    {
        game.varbits.put(TOB_TAB, NORMAL);
        serverOpensTob("17:24.60");
        clickTobTab(NORMAL);
        clickTobTab(NORMAL);
        tick();
        assertEquals(List.of("Theatre of Blood|3 players||17:24.60"), game.sentTimes());
    }

    @Test
    public void sendsNothingForAnUnknownMode()
    {
        game.varbits.put(TOB_TAB, 5);
        serverOpensTob("17:24.60");
        tick();
        assertEquals(List.of(), game.sentTimes());
    }
}
