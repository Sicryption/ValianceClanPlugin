package com.encryptiron.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SendCollectionLog extends PostCommand
{
	public HashMap<Integer, Integer> collection_log_map = new HashMap<>();
	public boolean isClogOpen = false;
	public int hasClogData = 0;

    @Inject
	private Client client;

    @Override
    String endpoint() {
        return "/api/member/save_collection_log";
    }
    
    @Override
    String body()
    {
        String coll_log_list = "";

        for (Map.Entry<Integer, Integer> entry : collection_log_map.entrySet())
        {
            if (!coll_log_list.isEmpty())
            {
                coll_log_list += ", ";
            }

            Integer itemId = entry.getKey();
            Integer quantity = entry.getValue();

            coll_log_list += "\"" + itemId + "\" : " + quantity;
        }

        return "\"collection_log\" : {" + coll_log_list + "}";
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
			collection_log_map.put((int)args[1], (int)args[2]);
		}
	}

	@Subscribe
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
        // Tick 3, Collect all messages & fire them off to the server
		if (hasClogData > 0 && --hasClogData == 0)
		{
            this.send();

            // Clog isn't actually closed, but we don't want to loop submitting clog data
            isClogOpen = false;
		}
        else if (hasClogData == 0)
		{
            // Force the search menu option, and then cancel it
			collection_log_map = new HashMap<>();
			client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(2240);
			hasClogData = 3;

            if (config.debug())
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sending your Collection log to the Valiance server...", "ValianceClanPlugin");
		}
	}
    
	@Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
		if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
			return;

        if (widgetLoaded.getGroupId() == InterfaceID.COLLECTION_LOG)
        {
			isClogOpen = true;
            hasClogData = 0;
        }
    }

	@Subscribe
    public void onWidgetClosed(WidgetClosed widgetClosed)
    {
		if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
			return;

        if (widgetClosed.getGroupId() == InterfaceID.COLLECTION_LOG)
        {
			isClogOpen = false;

            if (hasClogData > 0)
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

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sent your Collection log to the Valiance server!", "ValianceClanPlugin");
    }

    @Override
    void onSendFail(IOException exception)
    {
        log.debug("Failed to send collection log data: " + exception.getMessage());

        if (!config.debug())
            return;

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send Collection log to the Valiance server.", "ValianceClanPlugin");
    }
}
