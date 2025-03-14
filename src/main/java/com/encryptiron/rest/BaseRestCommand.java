package com.encryptiron.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.encryptiron.rest.BaseRestCommand.MessageSendStatus;

import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

public abstract class BaseRestCommand {

    private MessageSendStatus messageStatus = MessageSendStatus.None;
    private IOException lastException;

    final String DEVELOPMENT_URL = "http://127.0.0.1:5000";
    final String PRODUCTION_URL = "http://valianceosrs.com";

    enum MessageSendStatus
    {
        Success,
        Fail,
        None
    }

    private void writeMessageToServer() throws IOException
    {
        URL url = new URL(DEVELOPMENT_URL + endpoint());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    
        connection.setRequestMethod(requestType());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
    
        String headerBody = MessageHeaderData.getMessageHeaderJson();
        String requestBody = body();

        String body = "{ " + headerBody + ", " + requestBody + " }";
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = body.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
    
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8")))
        {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println("Successful " + requestType() + ", response: " + response.toString());
        }
    }

    public void send()
    {
        Thread sendThread = new Thread(() -> {
            try
            {
                writeMessageToServer();
                messageStatus = MessageSendStatus.Success;
            }
            catch (IOException ex)
            {
                lastException = ex;
                messageStatus = MessageSendStatus.Fail;
            }
        });

        sendThread.start();
    }
    
    abstract String requestType();
    abstract String endpoint();
    abstract String body();

	@Subscribe
    public void onGameTick(GameTick gameTick)
    {
        if (messageStatus == MessageSendStatus.Success)
        {
            onSendSuccess();
        }
        else if (messageStatus == MessageSendStatus.Fail)
        {
            onSendFail(lastException);
        }

        messageStatus = MessageSendStatus.None;
    }
    
    abstract void onSendSuccess();
    abstract void onSendFail(IOException exception);
}
