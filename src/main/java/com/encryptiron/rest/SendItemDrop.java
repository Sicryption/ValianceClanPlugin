package com.encryptiron.rest;

import java.util.Collection;

import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
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
        // The pop-up and chat message the server sends are shown by the base class
        super.onJsonResponse(request, json);

        if (config.debug())
        {
            onTextResponse(request, SERVER_SUCCESS_MESSAGE);
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
}
