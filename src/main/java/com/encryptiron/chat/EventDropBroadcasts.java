package com.encryptiron.chat;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.runelite.api.Client;
import net.runelite.client.util.Text;

/**
 * Shows the server's event drop broadcasts in clan chat.
 *
 * The server sends one whenever an event accepts anyone's drop, and words it
 * itself; this only picks a colour for how the submission went, so a completed
 * tile reads differently at a glance from progress towards one.
 */
@Singleton
public class EventDropBroadcasts
{
    private static final String EVENT_NAME_COLOR = "5555FF";
    private static final String DEFAULT_COLOR = "ff0000";
    private static final Map<String, String> STATUS_COLORS = Map.of(
        "complete", "00a000",
        "progress", "e08a00",
        "missed", "7f7f7f"
    );

    @Inject
    private Client client;

    @Inject
    private ValianceConfig config;

    @Inject
    private ValianceSocket socket;

    @Inject
    private ClanChatService clanChatService;

    public void startUp()
    {
        socket.setHandler("event_drop", this::onEventDrop);
    }

    private void onEventDrop(JsonObject broadcast)
    {
        EventDropBroadcastFilter filter = config.eventDropBroadcasts();
        if (filter == EventDropBroadcastFilter.OFF)
        {
            return;
        }

        if (filter == EventDropBroadcastFilter.MY_TEAM && !isTrue(broadcast.get("yourTeam")))
        {
            return;
        }

        String eventName = ValianceSocket.getString(broadcast, "eventName");
        String message = ValianceSocket.getString(broadcast, "message");
        if (eventName == null || message == null)
        {
            return;
        }

        String color = STATUS_COLORS.getOrDefault(ValianceSocket.getString(broadcast, "status"), DEFAULT_COLOR);

        // Escaped because the text carries player names, item names and tile
        // descriptions, none of which should be able to add tags of their own.
        String line = "[<col=" + EVENT_NAME_COLOR + ">" + Text.escapeJagex(eventName) + "</col>] "
            + "<col=" + color + ">" + Text.escapeJagex(message) + "</col>";

        client.addChatMessage(clanChatService.broadcastType(), "", line, ClanChatService.CLAN_NAME);
    }

    private static boolean isTrue(JsonElement element)
    {
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
