package com.encryptiron.rest;

import java.io.IOException;
import java.util.Collection;

import javax.inject.Inject;

import com.formdev.flatlaf.json.Json;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;

@Slf4j
public class SendItemDrop extends PostCommand
{
    @Inject
    private Client client;

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

        return itemDropObject;
    }
    
    @Subscribe
    public void onLootReceived(final LootReceived event)
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        npcName = event.getName();
        npcQuantity = event.getAmount();
        items = event.getItems();

        this.send();
    }

    @Override
    void onSendSuccess()
    {
        if (!config.debug())
            return;
            
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sent item drop to the Valiance Server!", "ValianceClanPlugin");
    }

    @Override
    void onSendFail(IOException exception)
    {
        log.debug("Failed to send item drop data: " + exception.getMessage());
        
        if (!config.debug())
            return;
            
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send item drop to the Valiance server.", "ValianceClanPlugin");
    }
}
