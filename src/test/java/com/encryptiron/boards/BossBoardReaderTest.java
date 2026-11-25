package com.encryptiron.boards;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;

public class BossBoardReaderTest
{
    private final FakeGame game = new FakeGame();
    private final BossBoardReader reader = new BossBoardReader(game.client, game.boardTimesSender);

    private void openAndTick(int interfaceId)
    {
        reader.onWidgetLoaded(game.open(interfaceId));
        reader.onGameTick(new GameTick());
    }

    @Test
    public void readsTheBestTimeOfASingleTimeBoard()
    {
        game.textByComponent.put(InterfaceID.HueyScoreboard.PBT_CONTENT, "0:53.40");
        openAndTick(InterfaceID.HUEY_SCOREBOARD);
        assertEquals(List.of("The Hueycoatl|Any||0:53.40"), game.sentTimes());
    }

    @Test
    public void readsYamasPersonalFastestVictory()
    {
        game.textByComponent.put(InterfaceID.YamaScoreboard.PBT_CONTENT, "2:18.60");
        game.textByComponent.put(InterfaceID.YamaScoreboard.GBT_CONTENT, "2:03.00");
        openAndTick(InterfaceID.YAMA_SCOREBOARD);
        assertEquals(List.of("Yama|Any||2:18.60"), game.sentTimes());
    }

    @Test
    public void sendsNothingWithoutATime()
    {
        openAndTick(InterfaceID.MUSPAH_SCOREBOARD);
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void sendsABoardOnceUntilItsTimeChangesOrThePlayerLogsInAgain()
    {
        game.textByComponent.put(InterfaceID.MuspahScoreboard.PBT_CONTENT, "1:20.40");
        openAndTick(InterfaceID.MUSPAH_SCOREBOARD);
        openAndTick(InterfaceID.MUSPAH_SCOREBOARD);
        assertEquals(1, game.sent.size());

        game.textByComponent.put(InterfaceID.MuspahScoreboard.PBT_CONTENT, "1:19.80");
        openAndTick(InterfaceID.MUSPAH_SCOREBOARD);
        assertEquals(2, game.sent.size());

        GameStateChanged loggedOut = new GameStateChanged();
        loggedOut.setGameState(GameState.LOGIN_SCREEN);
        game.boardTimesSender.onGameStateChanged(loggedOut);
        openAndTick(InterfaceID.MUSPAH_SCOREBOARD);
        assertEquals(3, game.sent.size());
    }

    @Test
    public void readsABoardOnlyOnTheTickItOpened()
    {
        openAndTick(InterfaceID.MUSPAH_SCOREBOARD);
        game.textByComponent.put(InterfaceID.MuspahScoreboard.PBT_CONTENT, "1:20.40");
        reader.onGameTick(new GameTick());
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void tellsAwakenedBossesApartByTheTitle()
    {
        game.textByComponent.put(InterfaceID.LeviathanScoreboard.TITLE_TEXT, "Leviathan Statistics");
        game.textByComponent.put(InterfaceID.LeviathanScoreboard.PBT_CONTENT, "0:57.00");
        openAndTick(InterfaceID.LEVIATHAN_SCOREBOARD);

        game.textByComponent.put(InterfaceID.LeviathanScoreboard.TITLE_TEXT, "Leviathan (Awakened) Statistics");
        game.textByComponent.put(InterfaceID.LeviathanScoreboard.PBT_CONTENT, "5:06.60");
        openAndTick(InterfaceID.LEVIATHAN_SCOREBOARD);

        assertEquals(List.of("The Leviathan|Any||0:57.00", "The Leviathan (Awakened)|Any||5:06.60"), game.sentTimes());
    }

    @Test
    public void sendsNothingForAnUnknownTitle()
    {
        game.textByComponent.put(InterfaceID.DukeSucellusScoreboard.TITLE_TEXT, "Duke Sucellus (Nightmare) Statistics");
        game.textByComponent.put(InterfaceID.DukeSucellusScoreboard.PBT_CONTENT, "0:35.40");
        openAndTick(InterfaceID.DUKE_SUCELLUS_SCOREBOARD);
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void readsTheNightmareBoardsSoloTimeAndItsSixOrMorePlayerVarbit()
    {
        game.textByComponent.put(InterfaceID.NightmareScoreboard.TITLE_TEXT, "The Nightmare Statistics");
        game.textByComponent.put(InterfaceID.NightmareScoreboard.MS6, "15:25.80");
        game.varbits.put(12961, 363);
        openAndTick(InterfaceID.NIGHTMARE_SCOREBOARD);
        assertEquals(List.of("Nightmare|Solo||15:25.80", "Nightmare|6+ players||363 ticks"), game.sentTimes());
    }

    @Test
    public void readsPhosanisBoardWithoutTheNightmareVarbit()
    {
        game.textByComponent.put(InterfaceID.NightmareScoreboard.TITLE_TEXT, "Phosani's Nightmare Statistics");
        game.textByComponent.put(InterfaceID.NightmareScoreboard.MS6, "4:23.40");
        game.varbits.put(12961, 363);
        openAndTick(InterfaceID.NIGHTMARE_SCOREBOARD);
        assertEquals(List.of("Phosani's Nightmare|Any||4:23.40"), game.sentTimes());
    }

    @Test
    public void readsBothGauntlets()
    {
        game.textByComponent.put(InterfaceID.GauntletScoreboard.PERSONAL_TIME, "3:28.20");
        game.textByComponent.put(InterfaceID.GauntletScoreboard.PERSONAL_TIME_HM, "5:15.00");
        openAndTick(InterfaceID.GAUNTLET_SCOREBOARD);
        assertEquals(List.of("The Gauntlet|Any||3:28.20", "The Corrupted Gauntlet|Any||5:15.00"), game.sentTimes());
    }

    @Test
    public void readsTheColosseumVarbitOnlyWhenItsBoardOpens()
    {
        game.varbits.put(9811, 1762);
        openAndTick(InterfaceID.MUSPAH_SCOREBOARD);
        assertEquals(List.of(), game.sentTimes());

        openAndTick(InterfaceID.COLOSSEUM_SCOREBOARD);
        assertEquals(List.of("Fortis Colosseum|Solo||1762 ticks"), game.sentTimes());
    }

    @Test
    public void readsDoomsLevelVarbitsAndItsShownOverallTime()
    {
        game.textByComponent.put(InterfaceID.DomScoreboard.P_TIME_FULL_VAL, "6:47.40");
        game.varbits.put(17759, 36);
        game.varbits.put(17766, 86);
        game.varbits.put(17767, 94);
        openAndTick(InterfaceID.DOM_SCOREBOARD);
        assertEquals(List.of(
            "Doom of Mokhaiotl|Solo||6:47.40",
            "Doom of Mokhaiotl|Solo|Level 1|36 ticks",
            "Doom of Mokhaiotl|Solo|Level 8|86 ticks",
            "Doom of Mokhaiotl|Solo|Level 8+|94 ticks"), game.sentTimes());
    }

    @Test
    public void sendsTheWholeBoardWhenAnyTimeIsNewAndNothingWhenNoneIs()
    {
        game.textByComponent.put(InterfaceID.DomScoreboard.P_TIME_FULL_VAL, "6:47.40");
        game.hiddenComponents.add(InterfaceID.DomScoreboard.P_TIME_FULL_VAL);
        game.varbits.put(17759, 36);
        game.varbits.put(17760, 46);
        openAndTick(InterfaceID.DOM_SCOREBOARD);
        assertEquals(2, game.sent.get(0).size());

        game.hiddenComponents.clear();
        openAndTick(InterfaceID.DOM_SCOREBOARD);
        assertEquals(3, game.sent.get(1).size());

        game.hiddenComponents.add(InterfaceID.DomScoreboard.P_TIME_FULL_VAL);
        openAndTick(InterfaceID.DOM_SCOREBOARD);
        assertEquals(2, game.sent.size());
    }

    @Test
    public void skipsDoomsOverallTimeWhileItIsHidden()
    {
        game.textByComponent.put(InterfaceID.DomScoreboard.P_TIME_FULL_VAL, "6:47.40");
        game.hiddenComponents.add(InterfaceID.DomScoreboard.P_TIME_FULL_VAL);
        game.varbits.put(17759, 36);
        openAndTick(InterfaceID.DOM_SCOREBOARD);
        assertEquals(List.of("Doom of Mokhaiotl|Solo|Level 1|36 ticks"), game.sentTimes());
    }
}
