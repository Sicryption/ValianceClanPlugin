package com.encryptiron.rest;

import java.util.Collection;

import javax.inject.Inject;

import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import okhttp3.Request;

@Slf4j
public class SendItemDrop extends PostCommand
{
    private Collection<ItemStack> items;
    private String npcName;
    private Integer npcQuantity;

    @Override
    String endpoint() {
        return "/api/member/send_item_drop";
    }
    
    @Override
    JsonObject body()
    {
        JsonObject itemsJson = new JsonObject();
        
        for (ItemStack item : items)
        {
            itemsJson.addProperty(Integer.toString(item.getId()), item.getQuantity());
        }

        JsonObject itemDrop = new JsonObject();
        itemDrop.addProperty("name", npcName);
        itemDrop.addProperty("quantity", npcQuantity);
        itemDrop.add("items", itemsJson);

        JsonObject itemDropObject = new JsonObject();
        itemDropObject.add("item_drop", itemDrop);

        log.info("Sending item drop: " + itemDropObject.toString());

        return itemDropObject;
    }
    
    @Subscribe
    public void onLootReceived(final LootReceived event)
    {
        npcName = event.getName();
        npcQuantity = event.getAmount();
        items = event.getItems();

        this.send();
    }

    @Override
    public void onJsonResponse(Request request, JsonObject json)
    {
        super.onJsonResponse(request, json);

        if (json.get("type").getAsString().equals("EventItemAccepted"))
        {
            String eventName = json.get("eventName").getAsString();
            
            if (config.popupOnEventItemAccepted())
            {
                sendPopUp(eventName, "You submitted an item which progressed the event!");
            }

            if (config.chatMessageOnEventItemAccepted())
            {
                String eventChatMessage = "[<col=5555FF>" + eventName + "</col>] <col=ff0000>You submitted an item which progressed the event!</col>";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "ValianceCC", eventChatMessage, eventName);
            }
        }

        if (config.debug())
        {
            onTextResponse(request, null);
        }
    }
    
    @Override
    String onSuccessResponseMessage()
    {
        return "Sent item drop to the Valiance Server!";
    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send item drop to the Valiance server.";
    }

    public void sendPopUp(String title, String description)
    {
        final int RESIZABLE_CLASSIC_LAYOUT = (161 << 16) | 13;
        final int RESIZABLE_MODERN_LAYOUT = (164 << 16) | 13;
        final int FIXED_CLASSIC_LAYOUT = 35913770;
        final int componentId = client.isResized()
                ? client.getVarbitValue(Varbits.SIDE_PANELS) == 1
                ? RESIZABLE_MODERN_LAYOUT
                : RESIZABLE_CLASSIC_LAYOUT
                : FIXED_CLASSIC_LAYOUT;

        client.openInterface(componentId, 660, WidgetModalMode.MODAL_CLICKTHROUGH);
        client.runScript(3343, title, description, -1);
    }
}
