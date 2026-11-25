package com.encryptiron.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SendBoardTimes extends PostCommand
{
    private JsonArray times;

    @Override
    String endpoint()
    {
        return "/api/member/board_times";
    }

    @Override
    JsonObject body()
    {
        JsonObject body = new JsonObject();
        body.add("times", times);
        return body;
    }

    public void send(JsonArray times)
    {
        this.times = times;
        send();
    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send the board's personal bests to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        return "Sent the board's personal bests to the Valiance server!";
    }
}
