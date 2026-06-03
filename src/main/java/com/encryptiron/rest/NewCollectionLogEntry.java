package com.encryptiron.rest;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

import com.google.gson.JsonObject;

import net.runelite.api.events.VarbitChanged;

@Slf4j
public class NewCollectionLogEntry extends PostCommand
{
    public int collectionLogEntryId = 0;
    private int lastKnownCollectionLogEntryId = 0;

    @Override
    String endpoint() {
        return "/api/member/new_collection_log";
    }
    
    @Override
    JsonObject body()
    {
        JsonObject json = new JsonObject();
        json.addProperty("itemId", collectionLogEntryId);

        return json;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
            event.getGameState() == GameState.HOPPING)
		{
            lastKnownCollectionLogEntryId = 0;
		}
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        // On log-in the game sends:
        // Varbit 4623 = 0
        // Varbit 4623 = <our last clog>
        // Varbit 4623 = <new clog> (when we get one)

        if (varbitChanged.getVarpId() == 4623)
        {
            int itemId = client.getVarpValue(varbitChanged.getVarpId());

            if (itemId == 0)
            {
                // The first pass on log-in
                return;
            }

            if (lastKnownCollectionLogEntryId == 0)
            {
                // On log-in, we load the last clog we've had, so this isn't actually a new clog.
                // Let's ignore it
                lastKnownCollectionLogEntryId = itemId;
                return;
            }

            lastKnownCollectionLogEntryId = itemId;

            if (lastKnownCollectionLogEntryId > 0)
            {
                collectionLogEntryId = lastKnownCollectionLogEntryId;
                send();
            }
        }

    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send the newly obtained collection log item to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        return "Sent the newly obtained collection log item to the Valiance server!";
    }
}
