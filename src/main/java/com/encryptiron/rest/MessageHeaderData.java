package com.encryptiron.rest;

import java.time.Instant;

import com.google.gson.JsonObject;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.config.RuneScapeProfileType;

public final class MessageHeaderData 
{
    @Getter
    @Setter
    private static String playerName = null;
        
    @Getter
    @Setter
    private static RuneScapeProfileType profileType = null;

    public static void resetPlayerName()
    {
        MessageHeaderData.playerName = null;
    }

    public static JsonObject getMessageHeaderJson()
    {
        JsonObject headerData = new JsonObject();
        headerData.addProperty("player_name", getPlayerName());
        headerData.addProperty("time", Instant.now().toEpochMilli());
        headerData.addProperty("profile_type", getProfileType().name());
        
        return headerData;
    }
}
