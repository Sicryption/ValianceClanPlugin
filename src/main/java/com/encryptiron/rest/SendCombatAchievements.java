package com.encryptiron.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import javax.inject.Inject;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SendCombatAchievements extends PostCommand
{
    public static int COMBAT_ACHIEVEMENTS_OVERVIEW_INTERFACE_ID = 717;
    
    // This will continue to grow as more CAs get added
    // https://github.com/runelite/cs2-scripts/blob/7efea5b51540e8d35875152b323e19d7e52faf10/scripts/%5Bproc%2Cscript4834%5D.cs2#L4
    public static Set<Integer> caVarpIds = Set.of(
        VarPlayerID.CA_TASK_COMPLETED_0, 
        VarPlayerID.CA_TASK_COMPLETED_1, 
        VarPlayerID.CA_TASK_COMPLETED_2, 
        VarPlayerID.CA_TASK_COMPLETED_3, 
        VarPlayerID.CA_TASK_COMPLETED_4, 
        VarPlayerID.CA_TASK_COMPLETED_5, 
        VarPlayerID.CA_TASK_COMPLETED_6, 
        VarPlayerID.CA_TASK_COMPLETED_7, 
        VarPlayerID.CA_TASK_COMPLETED_8, 
        VarPlayerID.CA_TASK_COMPLETED_9, 
        VarPlayerID.CA_TASK_COMPLETED_10, 
        VarPlayerID.CA_TASK_COMPLETED_11, 
        VarPlayerID.CA_TASK_COMPLETED_12, 
        VarPlayerID.CA_TASK_COMPLETED_13, 
        VarPlayerID.CA_TASK_COMPLETED_14, 
        VarPlayerID.CA_TASK_COMPLETED_15, 
        VarPlayerID.CA_TASK_COMPLETED_16, 
        VarPlayerID.CA_TASK_COMPLETED_17, 
        VarPlayerID.CA_TASK_COMPLETED_18,
        VarPlayerID.CA_TASK_COMPLETED_19
    );

    // https://github.com/runelite/cs2-scripts/blob/7efea5b51540e8d35875152b323e19d7e52faf10/scripts/%5Bproc%2Cscript4834%5D.cs2#L4
    public static int[] caTierEnums = new int[] {
        3981, // Easy
        3982, // Medium
        3983, // Hard
        3984, // Elite
        3985, // Master
        3986  // Grandmaster
    };

    private HashMap<Integer, Boolean> caCompletedMap;

    private boolean shouldSendCADataOnNextTick = false;

    @Inject
    private Client client;

    @Override
    String endpoint() {
        return "/api/member/save_combat_achievements";
    }
    
    @Override
    JsonObject body()
    {
        JsonArray caArray = new JsonArray();

        for (HashMap.Entry<Integer, Boolean> entry : caCompletedMap.entrySet())
        {
            Boolean completed = entry.getValue();

            if (!completed)
            {
                continue;
            }

            caArray.add(entry.getKey());
        }

        JsonObject cas = new JsonObject();
        cas.add("combat_achievements", caArray);

        return cas;
    }
    
    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        // We want to broadcast CA data every time any of our CA state change. However,
        // on log-in, all of these CA varbits get loaded, and we only want a single broadcast.
        // So we will wait until the next game tick, when all CA data has been loaded, to send our data.
        if (caVarpIds.contains(varbitChanged.getVarpId()))
        {
            shouldSendCADataOnNextTick = true;
        }
    }

    @Subscribe
    public void onGameTick()
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        if (shouldSendCADataOnNextTick)
        {
            shouldSendCADataOnNextTick = false;
            collectCombatAchievementDataFromVarbits();
            send();
        }
    }

    void collectCombatAchievementDataFromVarbits()
    {
        caCompletedMap = new HashMap<>();

        int[] caVarpIdsArray = caVarpIds.stream().mapToInt(Integer::intValue).toArray();

        for (int enumId : caTierEnums) {
            // Enum containing all CAs in that tier of achievement
            var e = client.getEnum(enumId);

            for (int structId : e.getIntVals()) {
                var struct = client.getStructComposition(structId);
                
                // Get the ID of the combat achievement
                int id = struct.getIntValue(1306);
                
                // Determine if a specific CA is enabled
                boolean unlocked = (client.getVarpValue(caVarpIdsArray[id / 32]) & (1 << (id % 32))) != 0;
                
                caCompletedMap.put(id, unlocked);
            }
        }
    }

    @Override
    void onSendSuccess()
    {
        if (!config.debug())
            return;

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sent Combat Achievement progress to the Valiance server!", "ValianceClanPlugin");
    }

    @Override
    void onSendFail(IOException exception)
    {
        log.debug("Failed to send Combat Achievement progress: " + exception.getMessage());

        if (!config.debug())
            return;

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send Combat Achievement progress to the Valiance server.", "ValianceClanPlugin");
    }
}
