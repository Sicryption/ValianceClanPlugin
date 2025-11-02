package com.encryptiron.rest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.inject.Inject;

import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import com.google.gson.JsonObject;

@Slf4j
public abstract class BaseRestCommand {
    private static final MediaType APPLICATION_JSON = MediaType.parse("application/json");
    private static final String MESSAGING_PROTOCOL = "http://";
    private static final int PORT = 8080;

    private MessageSendStatus messageStatus = MessageSendStatus.None;
    private IOException lastException;

    @Inject
    private OkHttpClient httpClient;

    @Inject
    public ValianceConfig config;

    enum MessageSendStatus
    {
        Success,
        Fail,
        None
    }

    private void writeMessageToServer()
    {
        URL url;
        try
        {
            url = new URL(MESSAGING_PROTOCOL + config.valianceServerUrl() + ":" + PORT + endpoint());
        } catch (MalformedURLException e)
        {
            log.error("MalformedURL : " + e.getMessage());
            return;
        }
    
        JsonObject header = MessageHeaderData.getMessageHeaderJson();
        JsonObject body = body();
        body.add("header", header);

        RequestBody requestBody = RequestBody.create(APPLICATION_JSON, body.toString());

        Request httpRequest = new Builder()
            .url(url)
            .header("User-Agent", "ValiancePlugin - " + MessageHeaderData.getPlayerName())
            .post(requestBody)
            .build();

        httpClient.newCall(httpRequest).enqueue(new Callback() 
        {
            @Override
            public void onFailure(Call call, final IOException ex) {
                lastException = ex;
                messageStatus = MessageSendStatus.Fail;
                log.info("Failed to send request " + ex.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body())
                {
                    if (!response.isSuccessful())
                    {
                        onFailure(call, new IOException("Unexpected code: " + response.code()));
                    }
                    else
                    {
                        log.info("Successful " + requestType() + ", response: " + responseBody.string());
                        messageStatus = MessageSendStatus.Success;
                    }
                }
            }
        });
    }

    public void send()
    {
        Thread sendThread = new Thread(() -> {
            writeMessageToServer();
        });

        sendThread.start();
    }
    
    abstract String requestType();
    abstract String endpoint();
    abstract JsonObject body();

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
