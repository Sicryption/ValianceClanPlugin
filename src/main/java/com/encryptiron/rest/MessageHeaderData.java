package com.encryptiron.rest;

import java.time.Instant;

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

    public static String getMessageHeaderJson()
    {
        return "\"header\" : { \"player_name\" : \"" + getPlayerName() + "\", \"time\" : " + Instant.now().toEpochMilli() + " }";
    }
}
