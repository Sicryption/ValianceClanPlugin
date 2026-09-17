package com.encryptiron.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import com.encryptiron.screenshot.GameChatScreenshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
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

    @Inject
    private GameChatScreenshot screenshot;

    @Inject
    private SendDropScreenshot sendDropScreenshot;

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

        // Screenshot now, decide later. The moment a drop landed is gone a tick
        // from here, but whether anyone wants proof of it is only known once the
        // server answers - so the frame is captured immediately and parked under
        // this key, and thrown away unclaimed if no event took the drop.
        //
        // The key is ours alone and never leaves the plugin; it rides on the
        // request object so the response handler can find its way back to the
        // right screenshot. It has to, because this class keeps the drop in
        // fields that the next drop overwrites.
        String captureKey = UUID.randomUUID().toString();
        screenshot.capture(captureKey);

        this.send(captureKey);
    }

    @Override
    public void onJsonResponse(Request request, JsonObject json)
    {
        super.onJsonResponse(request, json);

        boolean accepted = isResponseType(json, "EventItemAccepted");
        boolean acceptedQuietly = isResponseType(json, "EventItemAcceptedNoPopUp");

        if (accepted || acceptedQuietly)
        {
            uploadScreenshot(request, json);
        }

        if (accepted)
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
            onTextResponse(request, json.toString());
        }
    }

    /**
     * Hands the parked screenshot to whichever submissions the server just made.
     *
     * The response groups by event because one drop can be credited to two
     * events at once, and an upload addresses a single event - but it is still
     * one image, uploaded once per event and linked to every submission named
     * there.
     */
    private void uploadScreenshot(Request request, JsonObject json)
    {
        String captureKey = request.tag(String.class);
        if (captureKey == null)
        {
            return;
        }

        byte[] png = screenshot.take(captureKey);
        if (png == null)
        {
            // Nothing was captured - the tick already had a screenshot, the
            // frame never arrived, or the player has the feature switched off.
            return;
        }

        if (!json.has("accepted") || !json.get("accepted").isJsonArray())
        {
            return;
        }

        for (JsonElement element : json.getAsJsonArray("accepted"))
        {
            JsonObject accept = element.getAsJsonObject();
            if (!accept.has("event_id") || !accept.has("submission_ids"))
            {
                continue;
            }

            JsonArray ids = accept.getAsJsonArray("submission_ids");
            List<String> submissionIds = new ArrayList<>(ids.size());
            for (JsonElement id : ids)
            {
                submissionIds.add(id.getAsString());
            }

            sendDropScreenshot.send(accept.get("event_id").getAsString(), submissionIds, png);
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
