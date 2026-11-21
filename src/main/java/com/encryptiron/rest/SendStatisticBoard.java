package com.encryptiron.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SendStatisticBoard extends PostCommand
{
    private JsonArray boardTexts;
    private String boardBoss;

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
        if (boardBoss != null)
        {
            body.addProperty("content", boardBoss);
        }
        return body;
    }

    public void send(JsonArray boardTexts, String boardBoss)
    {
        this.boardTexts = boardTexts;
        this.boardBoss = boardBoss;
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
