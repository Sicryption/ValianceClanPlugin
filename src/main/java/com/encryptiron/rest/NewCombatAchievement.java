package com.encryptiron.rest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class NewCombatAchievement extends PostCommand
{
    private static final int CA_IDS_PER_VARP = 32;

    private int[] knownCaVarpValues;
    private final List<Integer> combatAchievementIdsToSend = new ArrayList<>();

    @Override
    String endpoint()
    {
        return "/api/member/new_combat_achievement";
    }

    @Override
    JsonObject body()
    {
        JsonArray combatAchievementIds = new JsonArray();
        for (int combatAchievementId : combatAchievementIdsToSend)
        {
            combatAchievementIds.add(combatAchievementId);
        }

        JsonObject json = new JsonObject();
        json.add("combatAchievementIds", combatAchievementIds);

        return json;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN ||
            event.getGameState() == GameState.HOPPING)
        {
            knownCaVarpValues = null;
            combatAchievementIdsToSend.clear();
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        if (knownCaVarpValues == null)
        {
            if (isPlayerLoggedIn())
            {
                rememberCaVarpValues();
            }
            return;
        }

        if (!combatAchievementIdsToSend.isEmpty())
        {
            send();
            combatAchievementIdsToSend.clear();
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        if (knownCaVarpValues == null)
        {
            return;
        }

        int varpIndex = caVarpIndexOf(varbitChanged.getVarpId());
        if (varpIndex < 0)
        {
            return;
        }

        int varpValue = client.getVarpValue(varbitChanged.getVarpId());
        int newlyCompletedBits = varpValue & ~knownCaVarpValues[varpIndex];
        knownCaVarpValues[varpIndex] = varpValue;

        while (newlyCompletedBits != 0)
        {
            int bit = Integer.numberOfTrailingZeros(newlyCompletedBits);
            newlyCompletedBits &= newlyCompletedBits - 1;

            int combatAchievementId = varpIndex * CA_IDS_PER_VARP + bit;
            log.debug("Combat achievement {} completed", combatAchievementId);
            combatAchievementIdsToSend.add(combatAchievementId);
        }
    }

    private void rememberCaVarpValues()
    {
        knownCaVarpValues = new int[SendCombatAchievements.caVarpIds.length];
        for (int varpIndex = 0; varpIndex < knownCaVarpValues.length; varpIndex++)
        {
            knownCaVarpValues[varpIndex] = client.getVarpValue(SendCombatAchievements.caVarpIds[varpIndex]);
        }
    }

    private boolean isPlayerLoggedIn()
    {
        return client.getGameState() == GameState.LOGGED_IN
            && client.getLocalPlayer() != null
            && client.getLocalPlayer().getName() != null;
    }

    private static int caVarpIndexOf(int varpId)
    {
        for (int varpIndex = 0; varpIndex < SendCombatAchievements.caVarpIds.length; varpIndex++)
        {
            if (SendCombatAchievements.caVarpIds[varpIndex] == varpId)
            {
                return varpIndex;
            }
        }

        return -1;
    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send the newly completed Combat Achievement to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        return "Sent the newly completed Combat Achievement to the Valiance server!";
    }
}
