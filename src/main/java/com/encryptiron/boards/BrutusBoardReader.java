package com.encryptiron.boards;

import static com.encryptiron.boards.BoardTime.ANY_TEAM_SIZE;
import static com.encryptiron.boards.BoardTime.WHOLE_RUN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;

public class BrutusBoardReader
{
    private static final String BRUTUS = "Brutus";
    private static final String DEMONIC_BRUTUS = "Demonic Brutus";
    private static final Set<Integer> SCOREBOARD_OBJECTS = Set.of(
        ObjectID.COWBOSS_SCOREBOARD_MULTI, ObjectID.COWBOSS_SCOREBOARD, ObjectID.COWBOSS_SCOREBOARD_UBER);
    private static final Map<String, Integer> KILL_COUNT_VARP_BY_BOSS = Map.of(
        BRUTUS, VarPlayerID.TOTAL_COWBOSS_KILLS,
        DEMONIC_BRUTUS, VarPlayerID.TOTAL_COWBOSS_HARDMODE_KILLS);

    private final Client client;
    private final BoardTimesSender boardTimesSender;
    private final ScoreboardClicks scoreboardClicks = new ScoreboardClicks();
    private String bossOfBoardOpenedThisTick;

    @Inject
    BrutusBoardReader(Client client, BoardTimesSender boardTimesSender)
    {
        this.client = client;
        this.boardTimesSender = boardTimesSender;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
    {
        if (!SCOREBOARD_OBJECTS.contains(menuOptionClicked.getId()))
        {
            return;
        }

        switch (menuOptionClicked.getMenuAction())
        {
            case GAME_OBJECT_FIRST_OPTION:
                scoreboardClicks.clicked(BRUTUS, client.getTickCount());
                break;
            case GAME_OBJECT_SECOND_OPTION:
                scoreboardClicks.clicked(DEMONIC_BRUTUS, client.getTickCount());
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (widgetLoaded.getGroupId() == InterfaceID.COWBOSS_SCOREBOARD)
        {
            bossOfBoardOpenedThisTick = scoreboardClicks.bossOfBoardOpenedAt(client.getTickCount());
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        String boss = bossOfBoardOpenedThisTick;
        bossOfBoardOpenedThisTick = null;
        if (boss == null || !showsKillCountOf(boss))
        {
            return;
        }

        List<BoardTime> boardTimes = new ArrayList<>();
        BoardTime.ofShownTime(boss, ANY_TEAM_SIZE, WHOLE_RUN,
            BoardWidgets.textOf(client, InterfaceID.CowbossScoreboard.PBT_CONTENT)).ifPresent(boardTimes::add);
        boardTimesSender.send(boardTimes);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.HOPPING)
        {
            scoreboardClicks.clear();
            bossOfBoardOpenedThisTick = null;
        }
    }

    private boolean showsKillCountOf(String boss)
    {
        String shownKillCount = BoardWidgets.textOf(client, InterfaceID.CowbossScoreboard.PKC_CONTENT).replace(",", "");
        return shownKillCount.equals(Integer.toString(client.getVarpValue(KILL_COUNT_VARP_BY_BOSS.get(boss))));
    }
}
