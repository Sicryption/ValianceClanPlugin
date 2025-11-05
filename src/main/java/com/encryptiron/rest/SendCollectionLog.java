package com.encryptiron.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SendCollectionLog extends PostCommand
{
    private static final Map<Integer, Integer> DUPLICATE_CLOG_ITEMS = Map.of(
        12013, 29472, // Prospector helmet
        12014, 29474, // Prospector jacket
        12015, 29476, // Prospector legs
        12016, 29478  // Prospector boots
    );

    public HashMap<Integer, Integer> collectionLogMap = new HashMap<>();
    public boolean isClogOpen = false;
    public boolean collectingClogData = false;

    private int numClogsAccordingToVarp = -1;

    @Inject
    private Client client;

    // Some items appear multiple times in the collection log, under different
    // item ids, and they do not get counted towards our collection log total.
    private int getCollectionLogMapCount()
    {
        int count = collectionLogMap.size();

        for (Map.Entry<Integer, Integer> entry : DUPLICATE_CLOG_ITEMS.entrySet())
        {
            if (collectionLogMap.containsKey(entry.getKey()) && collectionLogMap.containsKey(entry.getValue()))
            {
                count--;
            }
        }

        return count;
    }

    @Override
    String endpoint() {
        return "/api/member/save_collection_log";
    }
    
    @Override
    JsonObject body()
    {
        JsonObject collectionLogBody = new JsonObject();

        for (Map.Entry<Integer, Integer> entry : collectionLogMap.entrySet())
        {
            collectionLogBody.addProperty(Integer.toString(entry.getKey()), entry.getValue());
        }

        JsonObject collectionLog = new JsonObject();
        collectionLog.add("collection_log", collectionLogBody);

        return collectionLog;
    }
    

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        if (varbitChanged.getVarpId() == VarPlayerID.COLLECTION_COUNT)
        {
            numClogsAccordingToVarp = client.getVarpValue(varbitChanged.getVarpId());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
            event.getGameState() == GameState.HOPPING)
		{
            numClogsAccordingToVarp = -1;
		}
    }
    
    @Subscribe
    public void onScriptPreFired(ScriptPreFired preFired)
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        if (preFired.getScriptId() == 4100)
        {
            var args = preFired.getScriptEvent().getArguments();

            // 0 -> Script Id
            // 1 -> Item Id
            // 2 -> Quantity
            // 3 & 4 -> ???
            collectionLogMap.put((int)args[1], (int)args[2]);
        }
    }

    @Subscribe
    @Override
    public void onGameTick(GameTick gameTick)
    {
        super.onGameTick(gameTick);

        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD || !isClogOpen)
            return;

        // When searching, all clogs get fired through a script we can capture
        // We don't really know when it ends, it seems to always come 1 tick after a search start or end
        // So we will capture everything within a 3 tick window.
        // Tick 0, This tick
        // Tick 1, Search opens & closes
        // Tick 2, Messages are fired
        // Tick 3, Collect all messages
        // Tick 4, Fire them off to the server
        if (!collectingClogData)
        {
            // Force the search menu option, and then cancel it
            collectionLogMap = new HashMap<>();
            client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
            client.runScript(2240);
            collectingClogData = true;

            if (config.debug())
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sending your Collection log to the Valiance server...", "ValianceClanPlugin");
        }
        else
        {
            if (numClogsAccordingToVarp == -1)
            {
                if (config.debug())
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Unable to send Colletion Log to Valiance, Relog and Open Clog", "ValianceClanPlugin");
                    
                isClogOpen = false;
                collectingClogData = false;
                return;
            }

            if (numClogsAccordingToVarp != getCollectionLogMapCount())
            {
                // Clogs are loading, we must wait for them to all load in
                return;
            }

            // We have the same number of clogs as the varp says we should have, let's send them
            this.send();
            isClogOpen = false;
            collectingClogData = false;
        }
    }
    
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        if (widgetLoaded.getGroupId() == InterfaceID.COLLECTION)
        {
            isClogOpen = true;
            collectingClogData = false;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed widgetClosed)
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        if (widgetClosed.getGroupId() == InterfaceID.COLLECTION)
        {
            isClogOpen = false;

            if (collectingClogData && config.debug())
            {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Aborting sending Collection log data to the Valiance server.", "ValianceClanPlugin");
            }
        }
    }

    @Override
    void onSendSuccess()
    {
        if (!config.debug())
            return;

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sent Collection log progress to the Valiance server!", "ValianceClanPlugin");
    }

    @Override
    void onSendFail(IOException exception)
    {
        log.debug("Failed to send collection log data: " + exception.getMessage());

        if (!config.debug())
            return;

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send Collection log progress to the Valiance server.", "ValianceClanPlugin");
    }
}
