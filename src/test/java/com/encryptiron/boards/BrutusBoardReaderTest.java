package com.encryptiron.boards;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;

public class BrutusBoardReaderTest
{
    private static final String BRUTUS_SENT = "Brutus|Any||0:01.20";
    private static final String DEMONIC_SENT = "Demonic Brutus|Any||3:10.20";

    private final FakeGame game = new FakeGame();
    private final BrutusBoardReader reader = new BrutusBoardReader(game.client, game.boardTimesSender);

    @Before
    public void setKillCounts()
    {
        game.varps.put(5407, 1542);
        game.varps.put(5408, 1);
    }

    private void clickRead()
    {
        reader.onMenuOptionClicked(FakeGame.click(MenuAction.GAME_OBJECT_FIRST_OPTION, ObjectID.COWBOSS_SCOREBOARD_MULTI));
    }

    private void clickReadDemonic()
    {
        reader.onMenuOptionClicked(FakeGame.click(MenuAction.GAME_OBJECT_SECOND_OPTION, ObjectID.COWBOSS_SCOREBOARD_MULTI));
    }

    private void boardOpens(String killCount, String time)
    {
        game.textByComponent.put(InterfaceID.CowbossScoreboard.PKC_CONTENT, killCount);
        game.textByComponent.put(InterfaceID.CowbossScoreboard.PBT_CONTENT, time);
        reader.onWidgetLoaded(game.open(InterfaceID.COWBOSS_SCOREBOARD));
        reader.onGameTick(new GameTick());
    }

    private void brutusBoardOpens()
    {
        boardOpens("1,542", "0:01.20");
    }

    private void demonicBoardOpens()
    {
        boardOpens("1", "3:10.20");
    }

    @Test
    public void namesTheBossByTheOptionClicked()
    {
        clickRead();
        game.tick += 2;
        brutusBoardOpens();
        assertEquals(List.of(BRUTUS_SENT), game.sentTimes());
    }

    @Test
    public void readsADemonicBoardAfterALongWalk()
    {
        clickReadDemonic();
        game.tick += 40;
        demonicBoardOpens();
        assertEquals(List.of(DEMONIC_SENT), game.sentTimes());
    }

    @Test
    public void sendsNothingWhenTheOptionWasSwitchedBeforeTheBoardOpened()
    {
        clickRead();
        clickReadDemonic();
        game.tick += 1;
        brutusBoardOpens();
        game.tick += 1;
        demonicBoardOpens();
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void sendsNothingWhenAnOlderClickWasNeverAnswered()
    {
        clickRead();
        game.tick += 30;
        clickReadDemonic();
        game.tick += 1;
        demonicBoardOpens();
        assertEquals(List.of(), game.sentTimes());

        game.tick += 20;
        clickReadDemonic();
        game.tick += 1;
        demonicBoardOpens();
        assertEquals(List.of(DEMONIC_SENT), game.sentTimes());
    }

    @Test
    public void readsTheOtherBoardOnlyOnceTheFirstClickIsOldEnough()
    {
        clickRead();
        game.tick += 1;
        brutusBoardOpens();
        game.tick += 5;
        clickReadDemonic();
        game.tick += 1;
        demonicBoardOpens();
        assertEquals(List.of(BRUTUS_SENT), game.sentTimes());

        game.tick += 4;
        clickReadDemonic();
        game.tick += 1;
        demonicBoardOpens();
        assertEquals(List.of(BRUTUS_SENT, DEMONIC_SENT), game.sentTimes());
    }

    @Test
    public void sendsNothingWhenTheKillCountIsNotThatBosss()
    {
        clickRead();
        demonicBoardOpens();
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void sendsNothingWithoutAClick()
    {
        brutusBoardOpens();
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void forgetsClicksOnHopping()
    {
        clickRead();
        GameStateChanged hopping = new GameStateChanged();
        hopping.setGameState(GameState.HOPPING);
        reader.onGameStateChanged(hopping);
        brutusBoardOpens();
        assertEquals(List.of(), game.sentTimes());
    }

    @Test
    public void ignoresOtherObjects()
    {
        reader.onMenuOptionClicked(FakeGame.click(MenuAction.GAME_OBJECT_FIRST_OPTION, 1));
        brutusBoardOpens();
        assertEquals(List.of(), game.sentTimes());
    }
}
