package com.encryptiron.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SendStatisticBoard extends PostCommand
{
    private JsonArray boardTexts;

    @Override
    String endpoint()
    {
        return "/api/member/statistic_board";
    }

    @Override
    JsonObject body()
    {
        JsonObject body = new JsonObject();
        body.add("texts", boardTexts);
        return body;
    }

    public void send(JsonArray boardTexts)
    {
        this.boardTexts = boardTexts;
        send();
    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send the statistics board to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        return "Sent the statistics board to the Valiance server!";
    }
}
