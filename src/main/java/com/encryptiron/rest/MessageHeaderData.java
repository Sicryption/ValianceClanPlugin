package com.encryptiron.rest;

import java.time.Instant;

import com.google.gson.JsonObject;

public final class MessageHeaderData 
{
    private static String playerName = "NA";

    public static String getPlayerName()
    {
        return MessageHeaderData.playerName;
    }

    public static void setPlayerName(String playerName)
    {
        MessageHeaderData.playerName = playerName;
    }

    public static JsonObject getMessageHeaderJson()
    {
        JsonObject headerData = new JsonObject();
        headerData.addProperty("player_name", getPlayerName());
        headerData.addProperty("time", Instant.now().toEpochMilli());
        
        return headerData;
    }
}
