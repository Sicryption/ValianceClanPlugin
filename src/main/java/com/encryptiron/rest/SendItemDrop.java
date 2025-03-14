package com.encryptiron.rest;

import java.io.IOException;
import java.util.Collection;

import javax.inject.Inject;

import com.encryptiron.ValianceConfig;

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

	@Inject
	public ValianceConfig config;

    private Collection<ItemStack> items;
    private String npcName;
    private Integer npcQuantity;

    @Override
    String endpoint() {
        return "/api/member/send_item_drop";
    }
    
    @Override
    String body()
    {
        String item_list = "";

        for (ItemStack item : items)
        {
            if (!item_list.isEmpty())
            {
                item_list += ", ";
            }

            item_list += "\"" + item.getId() + "\" : " + item.getQuantity();
        }

        return "\"item_drop\" : { \"name\" : \"" + npcName + "\", \"quantity\" : " + npcQuantity + ", \"items\" : { " + item_list + " } }";
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
        if (!config.debug())
            return;
            
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send item drop to the Valiance server.", "ValianceClanPlugin");
        log.debug("Failed to send item drop data: " + exception.getMessage());
    }
}
