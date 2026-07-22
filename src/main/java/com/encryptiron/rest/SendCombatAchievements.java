package com.encryptiron.rest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class SendCombatAchievements extends PostCommand
{
    public static final int COMBAT_ACHIEVEMENTS_OVERVIEW_INTERFACE_ID = 717;
    
    // This will continue to grow as more CAs get added
    // https://github.com/runelite/cs2-scripts/blob/7efea5b51540e8d35875152b323e19d7e52faf10/scripts/%5Bproc%2Cscript4834%5D.cs2#L4
    public static final int[] caVarpIds = new int[] {
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
        VarPlayerID.CA_TASK_COMPLETED_19,
        5673, // CA_TASK_COMPLETED_20
    };

    // https://github.com/runelite/cs2-scripts/blob/7efea5b51540e8d35875152b323e19d7e52faf10/scripts/%5Bproc%2Cscript4834%5D.cs2#L4
    public static final int[] caTierEnums = new int[] {
        3981, // Easy
        3982, // Medium
        3983, // Hard
        3984, // Elite
        3985, // Master
        3986  // Grandmaster
    };
    
    public static final Set<Integer> caInterfaceIds = new HashSet<>(
        Arrays.asList(
            InterfaceID.CA_TASKS,
            InterfaceID.CA_BOSS,
            InterfaceID.CA_BOSSES,
            InterfaceID.CA_OVERVIEW,
            InterfaceID.CA_REWARDS
        ));

    private HashMap<Integer, Boolean> caCompletedMap;

    Set<Integer> openCaInterfaceIds = new HashSet<>();

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
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (caInterfaceIds.contains(widgetLoaded.getGroupId()))
        {
            log.info("CA widget loaded");
            if (openCaInterfaceIds.isEmpty())
            {
                collectCombatAchievementDataFromVarbits();
                send();
            }

            // When switching CA menus, each menu is in its own group, so we want
            // to collect the open interface ids and remove them when closed.
            // Only shooting out the message when any of the CA menus are first loaded.
            openCaInterfaceIds.add(widgetLoaded.getGroupId());
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed widgetClosed)
    {
        // We remove these widgets on the next tick such that
        // openCaInterfaceIds is not empty when the next CA submenu is opened.
        clientThread.invokeLater(() -> {
            openCaInterfaceIds.remove(widgetClosed.getGroupId());
        });
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
    String onRequestFailedMessage()
    {
        return "Failed to send Combat Achievement progress to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        return "Sent Combat Achievement progress to the Valiance server!";
    }
}
