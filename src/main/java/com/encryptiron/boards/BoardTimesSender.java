package com.encryptiron.boards;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.rest.SendBoardTimes;
import com.google.gson.JsonArray;

import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class BoardTimesSender
{
    private final SendBoardTimes sendBoardTimes;
    private final Set<String> timesSentThisLogin = new HashSet<>();

    @Inject
    BoardTimesSender(SendBoardTimes sendBoardTimes)
    {
        this.sendBoardTimes = sendBoardTimes;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
        {
            timesSentThisLogin.clear();
        }
    }

    void send(List<BoardTime> boardTimes)
    {
        JsonArray times = new JsonArray();
        boolean hasNewTime = false;
        for (BoardTime boardTime : boardTimes)
        {
            times.add(boardTime.toJson());
            hasNewTime |= timesSentThisLogin.add(boardTime.toJson().toString());
        }

        if (hasNewTime)
        {
            sendBoardTimes.send(times);
        }
    }
}
