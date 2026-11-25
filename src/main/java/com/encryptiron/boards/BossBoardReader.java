package com.encryptiron.boards;

import static com.encryptiron.boards.BoardTime.ANY_TEAM_SIZE;
import static com.encryptiron.boards.BoardTime.SOLO;
import static com.encryptiron.boards.BoardTime.WHOLE_RUN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

public class BossBoardReader
{
    private static final String STATISTICS = " Statistics";
    private static final String AWAKENED = " (Awakened)";
    private static final String DOOM_OF_MOKHAIOTL = "Doom of Mokhaiotl";
    private static final String SIX_OR_MORE_PLAYERS = "6+ players";
    private static final int[] DOOM_LEVEL_VARBITS = {
        VarbitID.DOM_LEVEL_1_BEST_TIME,
        VarbitID.DOM_LEVEL_2_BEST_TIME,
        VarbitID.DOM_LEVEL_3_BEST_TIME,
        VarbitID.DOM_LEVEL_4_BEST_TIME,
        VarbitID.DOM_LEVEL_5_BEST_TIME,
        VarbitID.DOM_LEVEL_6_BEST_TIME,
        VarbitID.DOM_LEVEL_7_BEST_TIME,
        VarbitID.DOM_LEVEL_8_BEST_TIME
    };

    private final Client client;
    private final BoardTimesSender boardTimesSender;
    private final Map<Integer, Consumer<List<BoardTime>>> readerByBoard = new HashMap<>();
    private final Set<Integer> boardsOpenedThisTick = new LinkedHashSet<>();

    @Inject
    BossBoardReader(Client client, BoardTimesSender boardTimesSender)
    {
        this.client = client;
        this.boardTimesSender = boardTimesSender;

        addSingleTimeBoard(InterfaceID.MUSPAH_SCOREBOARD, InterfaceID.MuspahScoreboard.PBT_CONTENT, "Phantom Muspah");
        addSingleTimeBoard(InterfaceID.HUEY_SCOREBOARD, InterfaceID.HueyScoreboard.PBT_CONTENT, "The Hueycoatl");
        addSingleTimeBoard(InterfaceID.YAMA_SCOREBOARD, InterfaceID.YamaScoreboard.PBT_CONTENT, "Yama");
        addSingleTimeBoard(InterfaceID.NEX_SCOREBOARD, InterfaceID.NexScoreboard.PBT_CONTENT, "Nex");
        addSingleTimeBoard(InterfaceID.ARAXXOR_SCOREBOARD, InterfaceID.AraxxorScoreboard.PBT_CONTENT, "Araxxor");
        addSingleTimeBoard(InterfaceID.AMOXLIATL_SCOREBOARD, InterfaceID.AmoxliatlScoreboard.PBT_CONTENT, "Amoxliatl");
        addSingleTimeBoard(InterfaceID.ROYAL_TITANS_SCOREBOARD, InterfaceID.RoyalTitansScoreboard.PBT_CONTENT, "The Royal Titans");
        addSingleTimeBoard(InterfaceID.MAGGOT_KING_SCOREBOARD, InterfaceID.MaggotKingScoreboard.PBT_CONTENT, "Maggot King");
        addSingleTimeBoard(InterfaceID.GRYPHON_BOSS, InterfaceID.GryphonBoss.PBT_CONTENT, "Shellbane Gryphon");

        addAwakenableBoard(InterfaceID.DUKE_SUCELLUS_SCOREBOARD, InterfaceID.DukeSucellusScoreboard.TITLE_TEXT,
            InterfaceID.DukeSucellusScoreboard.PBT_CONTENT, "Duke Sucellus", "Duke Sucellus");
        addAwakenableBoard(InterfaceID.WHISPERER_SCOREBOARD, InterfaceID.WhispererScoreboard.TITLE_TEXT,
            InterfaceID.WhispererScoreboard.PBT_CONTENT, "Whisperer", "The Whisperer");
        addAwakenableBoard(InterfaceID.LEVIATHAN_SCOREBOARD, InterfaceID.LeviathanScoreboard.TITLE_TEXT,
            InterfaceID.LeviathanScoreboard.PBT_CONTENT, "Leviathan", "The Leviathan");
        addAwakenableBoard(InterfaceID.VARDORVIS_SCOREBOARD, InterfaceID.VardorvisScoreboard.TITLE_TEXT,
            InterfaceID.VardorvisScoreboard.PBT_CONTENT, "Vardorvis", "Vardorvis");

        readerByBoard.put(InterfaceID.NIGHTMARE_SCOREBOARD, this::readNightmareBoard);
        readerByBoard.put(InterfaceID.GAUNTLET_SCOREBOARD, this::readGauntletBoard);
        readerByBoard.put(InterfaceID.DOM_SCOREBOARD, this::readDoomBoard);
        readerByBoard.put(InterfaceID.COLOSSEUM_SCOREBOARD, this::readColosseumBoard);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (readerByBoard.containsKey(widgetLoaded.getGroupId()))
        {
            boardsOpenedThisTick.add(widgetLoaded.getGroupId());
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        for (int interfaceId : boardsOpenedThisTick)
        {
            if (BoardWidgets.isOpen(client, interfaceId))
            {
                List<BoardTime> boardTimes = new ArrayList<>();
                readerByBoard.get(interfaceId).accept(boardTimes);
                boardTimesSender.send(boardTimes);
            }
        }

        boardsOpenedThisTick.clear();
    }

    private void addSingleTimeBoard(int interfaceId, int timeComponentId, String content)
    {
        readerByBoard.put(interfaceId, boardTimes -> addShownTime(boardTimes, content, timeComponentId));
    }

    private void addAwakenableBoard(int interfaceId, int titleComponentId, int timeComponentId, String nameInTitle, String content)
    {
        readerByBoard.put(interfaceId, boardTimes -> {
            String title = BoardWidgets.textOf(client, titleComponentId);
            if (title.equals(nameInTitle + STATISTICS))
            {
                addShownTime(boardTimes, content, timeComponentId);
            }
            else if (title.equals(nameInTitle + AWAKENED + STATISTICS))
            {
                addShownTime(boardTimes, content + AWAKENED, timeComponentId);
            }
        });
    }

    private void readNightmareBoard(List<BoardTime> boardTimes)
    {
        switch (BoardWidgets.textOf(client, InterfaceID.NightmareScoreboard.TITLE_TEXT))
        {
            case "The Nightmare" + STATISTICS:
                addShownTime(boardTimes, "Nightmare", InterfaceID.NightmareScoreboard.MS6);
                BoardTime.ofTicks("Nightmare", SIX_OR_MORE_PLAYERS, WHOLE_RUN,
                    client.getVarbitValue(VarbitID.NIGHTMARE_KILLTIME_BEST_6PLUS)).ifPresent(boardTimes::add);
                break;
            case "Phosani's Nightmare" + STATISTICS:
                addShownTime(boardTimes, "Phosani's Nightmare", InterfaceID.NightmareScoreboard.MS6);
                break;
        }
    }

    private void readGauntletBoard(List<BoardTime> boardTimes)
    {
        addShownTime(boardTimes, "The Gauntlet", InterfaceID.GauntletScoreboard.PERSONAL_TIME);
        addShownTime(boardTimes, "The Corrupted Gauntlet", InterfaceID.GauntletScoreboard.PERSONAL_TIME_HM);
    }

    private void readDoomBoard(List<BoardTime> boardTimes)
    {
        BoardTime.ofShownTime(DOOM_OF_MOKHAIOTL, SOLO, WHOLE_RUN,
            BoardWidgets.visibleTextOf(client, InterfaceID.DomScoreboard.P_TIME_FULL_VAL)).ifPresent(boardTimes::add);
        for (int level = 1; level <= DOOM_LEVEL_VARBITS.length; level++)
        {
            addDoomLevel(boardTimes, "Level " + level, DOOM_LEVEL_VARBITS[level - 1]);
        }
        addDoomLevel(boardTimes, "Level 8+", VarbitID.DOM_LEVEL_8_PLUS_BEST_TIME);
    }

    private void addDoomLevel(List<BoardTime> boardTimes, String level, int ticksVarbit)
    {
        BoardTime.ofTicks(DOOM_OF_MOKHAIOTL, SOLO, level, client.getVarbitValue(ticksVarbit)).ifPresent(boardTimes::add);
    }

    private void readColosseumBoard(List<BoardTime> boardTimes)
    {
        BoardTime.ofTicks("Fortis Colosseum", SOLO, WHOLE_RUN,
            client.getVarbitValue(VarbitID.COLOSSEUM_KILLTIME_BEST)).ifPresent(boardTimes::add);
    }

    private void addShownTime(List<BoardTime> boardTimes, String content, int timeComponentId)
    {
        BoardTime.ofShownTime(content, ANY_TEAM_SIZE, WHOLE_RUN,
            BoardWidgets.textOf(client, timeComponentId)).ifPresent(boardTimes::add);
    }
}
