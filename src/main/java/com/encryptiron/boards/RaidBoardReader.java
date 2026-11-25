package com.encryptiron.boards;

import static com.encryptiron.boards.BoardTime.OVERALL_TIME;
import static com.encryptiron.boards.BoardTime.WHOLE_RUN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InterfaceID.ToaScoreboard;
import net.runelite.api.gameval.InterfaceID.TobScoreboard;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

public class RaidBoardReader
{
    private static final int TOB_SCOREBOARD_TAB_SELECT = 4493;
    private static final int TOA_SCOREBOARD_TAB_SELECT = 6774;
    private static final int TAB_ARGUMENT = 4;

    private final Client client;
    private final BoardTimesSender boardTimesSender;
    private final List<RaidBoard> raidBoards = List.of(
        new RaidBoard(InterfaceID.TOB_SCOREBOARD, VarbitID.TOB_SCOREBOARD_TAB, TOB_SCOREBOARD_TAB_SELECT,
            Map.of(0, "Theatre of Blood", 1, "Theatre of Blood: Entry Mode", 2, "Theatre of Blood: Hard Mode"),
            new int[] {
                TobScoreboard.DATA_SOLO_P_R, TobScoreboard.DATA_2MAN_P_R, TobScoreboard.DATA_3MAN_P_R,
                TobScoreboard.DATA_4MAN_P_R, TobScoreboard.DATA_5MAN_P_R
            },
            new int[] {
                TobScoreboard.DATA_SOLO_P_O, TobScoreboard.DATA_2MAN_P_O, TobScoreboard.DATA_3MAN_P_O,
                TobScoreboard.DATA_4MAN_P_O, TobScoreboard.DATA_5MAN_P_O
            }),
        new RaidBoard(InterfaceID.TOA_SCOREBOARD, VarbitID.TOA_SCOREBOARD_TAB, TOA_SCOREBOARD_TAB_SELECT,
            Map.of(0, "Tombs of Amascut", 1, "Tombs of Amascut: Entry Mode", 2, "Tombs of Amascut: Expert Mode"),
            new int[] {
                ToaScoreboard.DATA_SOLO_P_R, ToaScoreboard.DATA_2MAN_P_R, ToaScoreboard.DATA_3MAN_P_R, ToaScoreboard.DATA_4MAN_P_R,
                ToaScoreboard.DATA_5MAN_P_R, ToaScoreboard.DATA_6MAN_P_R, ToaScoreboard.DATA_7MAN_P_R, ToaScoreboard.DATA_8MAN_P_R
            },
            new int[] {
                ToaScoreboard.DATA_SOLO_P_O, ToaScoreboard.DATA_2MAN_P_O, ToaScoreboard.DATA_3MAN_P_O, ToaScoreboard.DATA_4MAN_P_O,
                ToaScoreboard.DATA_5MAN_P_O, ToaScoreboard.DATA_6MAN_P_O, ToaScoreboard.DATA_7MAN_P_O, ToaScoreboard.DATA_8MAN_P_O
            }));

    @Inject
    RaidBoardReader(Client client, BoardTimesSender boardTimesSender)
    {
        this.client = client;
        this.boardTimesSender = boardTimesSender;
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        for (RaidBoard raidBoard : raidBoards)
        {
            if (scriptPreFired.getScriptId() == raidBoard.tabSelectScript && !isAlreadySelectedTab(scriptPreFired, raidBoard))
            {
                raidBoard.modeTabClicks.clicked();
            }
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        for (RaidBoard raidBoard : raidBoards)
        {
            if (widgetLoaded.getGroupId() == raidBoard.interfaceId)
            {
                raidBoard.modeTabClicks.boardOpened();
                raidBoard.openedThisTick = true;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        for (RaidBoard raidBoard : raidBoards)
        {
            if (!BoardWidgets.isOpen(client, raidBoard.interfaceId))
            {
                raidBoard.modeTabClicks.boardClosed();
            }
            else if (raidBoard.openedThisTick && raidBoard.modeTabClicks.isShownModeCertain())
            {
                readBoard(raidBoard);
            }
            raidBoard.openedThisTick = false;
        }
    }

    private boolean isAlreadySelectedTab(ScriptPreFired scriptPreFired, RaidBoard raidBoard)
    {
        Object[] arguments = scriptPreFired.getScriptEvent() == null ? null : scriptPreFired.getScriptEvent().getArguments();
        return arguments != null && arguments.length > TAB_ARGUMENT
            && Integer.valueOf(client.getVarbitValue(raidBoard.tabVarbit)).equals(arguments[TAB_ARGUMENT]);
    }

    private void readBoard(RaidBoard raidBoard)
    {
        String content = raidBoard.contentByTab.get(client.getVarbitValue(raidBoard.tabVarbit));
        if (content == null)
        {
            return;
        }

        List<BoardTime> boardTimes = new ArrayList<>();
        for (int players = 1; players <= raidBoard.challengeTimeComponents.length; players++)
        {
            String teamSize = BoardTime.teamSizeOf(players);
            BoardTime.ofShownTime(content, teamSize, WHOLE_RUN,
                BoardWidgets.textOf(client, raidBoard.challengeTimeComponents[players - 1])).ifPresent(boardTimes::add);
            BoardTime.ofShownTime(content, teamSize, OVERALL_TIME,
                BoardWidgets.textOf(client, raidBoard.overallTimeComponents[players - 1])).ifPresent(boardTimes::add);
        }
        boardTimesSender.send(boardTimes);
    }

    private static class RaidBoard
    {
        private final int interfaceId;
        private final int tabVarbit;
        private final int tabSelectScript;
        private final Map<Integer, String> contentByTab;
        private final int[] challengeTimeComponents;
        private final int[] overallTimeComponents;
        private final ModeTabClicks modeTabClicks = new ModeTabClicks();
        private boolean openedThisTick;

        private RaidBoard(int interfaceId, int tabVarbit, int tabSelectScript, Map<Integer, String> contentByTab,
            int[] challengeTimeComponents, int[] overallTimeComponents)
        {
            this.interfaceId = interfaceId;
            this.tabVarbit = tabVarbit;
            this.tabSelectScript = tabSelectScript;
            this.contentByTab = contentByTab;
            this.challengeTimeComponents = challengeTimeComponents;
            this.overallTimeComponents = overallTimeComponents;
        }
    }
}
