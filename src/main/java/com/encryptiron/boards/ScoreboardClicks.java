package com.encryptiron.boards;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class ScoreboardClicks
{
    private static final int TICKS_A_CLICK_STAYS_IN_DOUBT = 10;

    private final Set<String> bossesClickedSinceLastBoard = new HashSet<>();
    private final Map<String, Integer> lastClickTickByBoss = new HashMap<>();

    void clicked(String boss, int tick)
    {
        bossesClickedSinceLastBoard.add(boss);
        lastClickTickByBoss.put(boss, tick);
    }

    String bossOfBoardOpenedAt(int tick)
    {
        Set<String> possibleBosses = new HashSet<>(bossesClickedSinceLastBoard);
        bossesClickedSinceLastBoard.clear();
        if (possibleBosses.isEmpty())
        {
            return null;
        }

        lastClickTickByBoss.forEach((boss, clickTick) -> {
            if (tick - clickTick <= TICKS_A_CLICK_STAYS_IN_DOUBT)
            {
                possibleBosses.add(boss);
            }
        });
        return possibleBosses.size() == 1 ? possibleBosses.iterator().next() : null;
    }

    void clear()
    {
        bossesClickedSinceLastBoard.clear();
        lastClickTickByBoss.clear();
    }
}
