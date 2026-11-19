package com.encryptiron.rest;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SendAdventureLog extends PostCommand
{
    private List<String> counterLines;

    @Override
    String endpoint()
    {
        return "/api/member/adventure_log";
    }

    @Override
    JsonObject body()
    {
        JsonArray lines = new JsonArray();
        counterLines.forEach(lines::add);

        JsonObject body = new JsonObject();
        body.add("lines", lines);
        return body;
    }

    public void send(List<String> counterLines)
    {
        this.counterLines = counterLines;
        send();
    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send your adventure log personal bests to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        return "Sent your adventure log personal bests to the Valiance server!";
    }
}
