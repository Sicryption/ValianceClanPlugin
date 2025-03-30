package com.encryptiron.rest;

import java.io.IOException;
import java.util.HashMap;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SendCombatAchievements extends PostCommand
{
    public static int COMBAT_ACHIEVEMENTS_OVERVIEW_INTERFACE_ID = 717;
    
    // This will continue to grow as more CAs get added
    // https://github.com/runelite/cs2-scripts/blob/7efea5b51540e8d35875152b323e19d7e52faf10/scripts/%5Bproc%2Cscript4834%5D.cs2#L4
    public static int[] caVarpIds = new int[] {
        3116, 
        3117, 
        3118, 
        3119, 
        3120, 
        3121, 
        3122, 
        3123, 
        3124, 
        3125, 
        3126, 
        3127, 
        3128, 
        3387, 
        3718, 
        3773, 
        3774, 
        4204, 
        4496
    };

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

    @Inject
    private Client client;

    @Override
    String endpoint() {
        return "/api/member/save_combat_achievements";
    }
    
    @Override
    String body()
    {
        String caList = "";

        for (HashMap.Entry<Integer, Boolean> entry : caCompletedMap.entrySet())
        {
            Integer id = entry.getKey();
            Boolean completed = entry.getValue();

            if (!completed)
            {
                continue;
            }

            if (!caList.isEmpty())
            {
                caList += ", ";
            }

            caList += id;
        }

        return "\"combat_achievements\" : [" + caList + "]";
    }
    
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (RuneScapeProfileType.getCurrent(client) != RuneScapeProfileType.STANDARD)
            return;

        if (widgetLoaded.getGroupId() == COMBAT_ACHIEVEMENTS_OVERVIEW_INTERFACE_ID)
        {
            collectCombatAchievementDataFromVarbits();
            send();
        }
    }

    void collectCombatAchievementDataFromVarbits()
    {
        caCompletedMap = new HashMap<>();

        for (int enumId : caTierEnums) {
            // Enum containing all CAs in that tier of achievement
            var e = client.getEnum(enumId);

            for (int structId : e.getIntVals()) {
                var struct = client.getStructComposition(structId);
                
                // Get the ID of the combat achievement
                int id = struct.getIntValue(1306);
                
                // Determine if a specific CA is enabled
                boolean unlocked = (client.getVarpValue(caVarpIds[id / 32]) & (1 << (id % 32))) != 0;
                
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
