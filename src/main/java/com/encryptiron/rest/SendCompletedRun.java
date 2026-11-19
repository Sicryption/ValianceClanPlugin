package com.encryptiron.rest;

import com.google.gson.JsonObject;

public class SendCompletedRun extends PostCommand
{
    private JsonObject completedRun;

    @Override
    String endpoint()
    {
        return "/api/member/completed_run";
    }

    @Override
    JsonObject body()
    {
        return completedRun;
    }

    public void send(JsonObject completedRun)
    {
        this.completedRun = completedRun;
        send();
    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send the completed run to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        return "Sent the completed run to the Valiance server!";
    }
}
