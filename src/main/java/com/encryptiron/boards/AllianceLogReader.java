package com.encryptiron.boards;

import static com.encryptiron.boards.BoardTime.OVERALL_TIME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID.TfaStatsCategory;
import net.runelite.api.gameval.DBTableID.TfaStatsRow;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;

public class AllianceLogReader
{
    private static final int TFA_STATS_ALL_FINISHED_LOADING = 10124;
    private static final int TFA_STATS_ALL_ADD_ROW = 10125;
    private static final int CATEGORY_ARGUMENT = 1;
    private static final int ROW_ARGUMENT = 2;
    private static final int PLAYERS_ARGUMENT = 3;
    private static final int PERSONAL_TICKS_ARGUMENT = 6;
    private static final int LONGEST_RECORDED_TICKS = 30_000;
    private static final String THE_FRACTURED_ARCHIVE = "The Fractured Archive";
    private static final Map<Integer, String> SPLIT_BY_CATEGORY = Map.of(
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_OVERALL, OVERALL_TIME,
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_GUARDIAN, "Archive Guardian",
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_BORK, "Bork",
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_DAGONHAI, "The Dagon'hai",
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_EZEKIEL, "Ezekiel",
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_MOIA, "Moia",
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_OCELLUS, "Ocellus",
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_JELLY, "Dreadnoth the Jellied",
        TfaStatsCategory.Row.TFA_STATS_CATEGORY_XARTH, "Xarth");

    private final BoardTimesSender boardTimesSender;
    private final List<BoardTime> loadedTimes = new ArrayList<>();

    @Inject
    AllianceLogReader(BoardTimesSender boardTimesSender)
    {
        this.boardTimesSender = boardTimesSender;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (widgetLoaded.getGroupId() == InterfaceID.TFA_STATS)
        {
            loadedTimes.clear();
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        switch (scriptPreFired.getScriptId())
        {
            case TFA_STATS_ALL_ADD_ROW:
                if (scriptPreFired.getScriptEvent() != null)
                {
                    addRow(scriptPreFired.getScriptEvent().getArguments());
                }
                break;
            case TFA_STATS_ALL_FINISHED_LOADING:
                boardTimesSender.send(loadedTimes);
                loadedTimes.clear();
                break;
        }
    }

    private void addRow(Object[] arguments)
    {
        if (arguments == null || arguments.length <= PERSONAL_TICKS_ARGUMENT
            || !Integer.valueOf(TfaStatsRow.Row.TFA_STATS_ROW_TIME).equals(arguments[ROW_ARGUMENT]))
        {
            return;
        }

        String split = SPLIT_BY_CATEGORY.get(arguments[CATEGORY_ARGUMENT]);
        int players = (Integer) arguments[PLAYERS_ARGUMENT];
        int personalTicks = (Integer) arguments[PERSONAL_TICKS_ARGUMENT];
        if (split != null && players > 0 && personalTicks < LONGEST_RECORDED_TICKS)
        {
            BoardTime.ofTicks(THE_FRACTURED_ARCHIVE, BoardTime.teamSizeOf(players), split, personalTicks).ifPresent(loadedTimes::add);
        }
    }
}
