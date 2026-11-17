package com.encryptiron.rest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.inject.Inject;

import com.encryptiron.PopUpQueue;
import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.RuneScapeProfileType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Slf4j
public abstract class BaseRestCommand {
    private static final MediaType APPLICATION_JSON = MediaType.parse("application/json");
    private static final String MESSAGING_PROTOCOL = "http://";
    private static final int PORT = 8080;
    protected static final String SERVER_SUCCESS_MESSAGE = "Success";

    @Inject
    private OkHttpClient httpClient;

    @Inject
    public ValianceConfig config;

    @Inject
    public Client client;

    @Inject
    public ClientThread clientThread;

    @Inject
    private PopUpQueue popUpQueue;

    private void writeMessageToServer(JsonObject body)
    {
        if (MessageHeaderData.getPlayerName() == null)
        {
            // The player hasn't fully logged in yet, this means this message is the server catching us up
            // on what the player has, after login / hop. We can skip sending this message as we don't care about it.
            return;
        }

        MessageHeaderData.setProfileType(RuneScapeProfileType.getCurrent(client));

        URL url;
        try
        {
            url = new URI(MESSAGING_PROTOCOL + config.valianceServerUrl() + ":" + PORT + endpoint()).toURL();
        } catch (MalformedURLException | URISyntaxException e)
        {
            log.error("MalformedURL : " + e.getMessage());
            return;
        }
    
        JsonObject header = MessageHeaderData.getMessageHeaderJson();
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
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body())
                {
                    if (!response.isSuccessful())
                    {
                        clientThread.invokeLater(() -> {
                            onFailCodeResponse(httpRequest, response);
                        });
                    }
                    else
                    {
                        String bodyString = responseBody.string();

                        // Our responses are normally not JSON,
                        // but if they are, we want the client to do something in response
                        try
                        {
                            JsonParser parser = new JsonParser();
                            JsonObject json = parser.parse(bodyString).getAsJsonObject();

                            clientThread.invokeLater(() -> {
                                onJsonResponse(httpRequest, json);
                            });
                        }
                        catch (Exception ex)
                        {
                            clientThread.invokeLater(() -> {
                                onTextResponse(httpRequest, bodyString);
                            });
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                clientThread.invokeLater(() -> {
                    onRequestFailed(httpRequest, e);
                });
            }
        });
    }

    public void send()
    {
        // Prepare the body outside of the thread, in case there are any delays
        JsonObject body = body();
        
        // We want to send the message in a separate thread so that we 
        // don't block the game client while waiting for the response
        Thread sendThread = new Thread(() -> {
            writeMessageToServer(body);
        });

        sendThread.start();
    }
    
    abstract String requestType();
    abstract String endpoint();
    abstract JsonObject body();
    abstract String onRequestFailedMessage();
    // Server responds with OK and a successful response
    abstract String onSuccessResponseMessage();
    // Server responds with OK but something might be off, let the user know
    private String onTextResponseMessage(String serverResponse) {
        return "Valiance Server: " + serverResponse;
    }
    
    public void onJsonResponse(Request request, JsonObject json)
    {
        log.info("Received json response: " + json.toString());

        showServerMessages(json);

        // The parent class should override this function if they want to do something with the response
    }

    private void showServerMessages(JsonObject json)
    {
        if (!json.has("messages") || !json.get("messages").isJsonArray())
        {
            showServerMessage(json);
            return;
        }

        for (JsonElement message : json.getAsJsonArray("messages"))
        {
            if (message.isJsonObject())
            {
                showServerMessage(message.getAsJsonObject());
            }
        }
    }

    private void showServerMessage(JsonObject message)
    {
        showServerPopUp(message);
        showServerChatMessage(message);
    }

    private void showServerPopUp(JsonObject json)
    {
        if (!config.popupOnEventItemAccepted() || !hasServerPopUp(json))
        {
            return;
        }

        JsonObject popUp = json.getAsJsonObject("popUp");
        if (popUp.has("title") && popUp.has("description"))
        {
            sendPopUp(popUp.get("title").getAsString(), popUp.get("description").getAsString());
        }
    }

    private boolean hasServerPopUp(JsonObject json)
    {
        return json != null && json.has("popUp") && json.get("popUp").isJsonObject();
    }

    private void showServerChatMessage(JsonObject json)
    {
        if (!config.chatMessageOnEventItemAccepted() || !hasServerChatMessage(json))
        {
            return;
        }

        JsonObject chatMessage = json.getAsJsonObject("chatMessage");
        if (chatMessage.has("title") && chatMessage.has("message"))
        {
            String title = chatMessage.get("title").getAsString();
            String eventChatMessage = "[<col=5555FF>" + title + "</col>] <col=ff0000>"
                    + chatMessage.get("message").getAsString() + "</col>";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "ValianceCC", eventChatMessage, title);
        }
    }

    private boolean hasServerChatMessage(JsonObject json)
    {
        return json != null && json.has("chatMessage") && json.get("chatMessage").isJsonObject();
    }

    public void onTextResponse(Request request, String response)
    {
        log.info("Received text response: " + response);

        if (!config.debug())
            return;

        if (response.equals(SERVER_SUCCESS_MESSAGE))
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", onSuccessResponseMessage(), "ValianceClanPlugin");
        }
        else
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", onTextResponseMessage(response), "ValianceClanPlugin");
        }
    }

    public void onFailCodeResponse(Request request, Response response)
    {
        log.info("Received failed code response: " + response.code());

        try
        {
            // If we sent back an error, let's print it for debug purposes.
            String bodyString = response.body().string();            
            JsonParser parser = new JsonParser();
            JsonObject json = parser.parse(bodyString).getAsJsonObject();
            String error = json.get("string").getAsString();

            log.debug("Error received when sending message: " + error);
        }
        catch(Exception ex)
        {
            // Json parsing error, no error given
        }

        sendFailMessage();
    }

    public void onRequestFailed(Request request, IOException exception)
    {
        log.info("Failed to send request " + exception.getMessage());
        sendFailMessage();
    }

    public void sendFailMessage()
    {
        if (!config.debug())
            return;

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", onRequestFailedMessage(), "ValianceClanPlugin");
    }

    public void sendPopUp(String title, String description)
    {
        popUpQueue.add(title, description);
    }

    protected boolean isResponseType(JsonObject json, String type)
    {
        return json != null && json.has("type") && type.equals(json.get("type").getAsString());
    }
}
