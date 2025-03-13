package com.encryptiron.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;

public class SendCollectionLog extends PostCommand {
	public HashMap<Integer, Integer> collection_log_map = new HashMap<>();
	private Client client;

    public SendCollectionLog(Client client)
    {
        this.client = client;
    }

    @Override
    String endpoint() {
        return "/api/member/save_collection_log";
    }
    
    @Override
    String body() {
        String coll_log_list = "";

        for (Map.Entry<Integer, Integer> entry : collection_log_map.entrySet()) {
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
	public void onScriptPreFired(ScriptPreFired preFired) {
		if (preFired.getScriptId() == 4100) {
			var args = preFired.getScriptEvent().getArguments();

			// 0 -> Script Id
			// 1 -> Item Id
			// 2 -> Quantity
			// 3 & 4 -> ???
			collection_log_map.put((int)args[1], (int)args[2]);
		}
	}

	public boolean isClogOpen = false;
	public int hasClogData = 0;

    @Subscribe
	public void onGameTick(GameTick gameTick) {
        // Only trigger on tick 3, see below comment for details
		if (hasClogData > 0 && --hasClogData == 0)
		{
            try
            {
                this.send();
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sent your Collection log to the Valiance server!", "ValianceClanPlugin");
            }
            catch (IOException exception)
            {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send Collection log to the Valiance server2.", "ValianceClanPlugin");
                System.out.println("Failed to send collection log data: " + exception.getMessage());
            }
		}

		if (isClogOpen)
		{
            // Force the search menu option, and then cancel it
            // When searching, all clogs get fired through a script we can capture
            // We don't really know when it ends, it seems to always come 1 tick after a search start or end
            // So we will capture everything within a 3 tick window.
            // Tick 0, This tick
            // Tick 1, Search opens & closes
            // Tick 2, Messages are fired
            // Tick 3, Collect all messages & fire them off to the server
			collection_log_map = new HashMap<>();
			client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(2240);
			isClogOpen = false;
			hasClogData = 3;

            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sending your Collection log to the Valiance server...", "ValianceClanPlugin");
		}
	}
	
    private boolean isValidWorldType() {
        List<WorldType> invalidTypes = ImmutableList.of(
                WorldType.DEADMAN,
                WorldType.NOSAVE_MODE,
                WorldType.SEASONAL,
                WorldType.TOURNAMENT_WORLD,
                WorldType.BETA_WORLD
        );

        for (WorldType worldType : invalidTypes) {
            if (client.getWorldType().contains(worldType)) {
                return false;
            }
        }

        return true;
    }
    
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
        if (!isValidWorldType()) {
            return;
        }

        if (widgetLoaded.getGroupId() == InterfaceID.COLLECTION_LOG) {
			isClogOpen = true;
        }
    }
}
