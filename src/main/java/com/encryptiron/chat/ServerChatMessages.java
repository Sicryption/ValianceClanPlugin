package com.encryptiron.chat;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

/**
 * Shows chat messages the server sends, exactly as it words them.
 *
 * The server decides everything about the line: its chat type, the name and
 * sender (the channel name in brackets) and the message itself, tags such as
 * col and img included. Anything the server took from players, such as names,
 * is escaped there, so the text here is used as it is.
 *
 * Every field but the message may be left out. Without a chat type, or with one
 * this client does not know, the line is a broadcast in whichever Valiance
 * channel the player is in.
 */
@Slf4j
@Singleton
public class ServerChatMessages
{
    @Inject
    private Client client;

    @Inject
    private ValianceSocket socket;

    @Inject
    private ClanChatService clanChatService;

    public void startUp()
    {
        socket.setHandler("chat_message", this::onChatMessage);
    }

    private void onChatMessage(JsonObject chatMessage)
    {
        String message = ValianceSocket.getString(chatMessage, "message");
        if (message == null)
        {
            return;
        }

        String name = ValianceSocket.getString(chatMessage, "name");
        String sender = ValianceSocket.getString(chatMessage, "sender");

        client.addChatMessage(
            chatType(ValianceSocket.getString(chatMessage, "chat_type")),
            name == null ? "" : name,
            message,
            sender == null ? ClanChatService.CLAN_NAME : sender);
    }

    private ChatMessageType chatType(String typeName)
    {
        if (typeName != null)
        {
            try
            {
                return ChatMessageType.valueOf(typeName);
            }
            catch (IllegalArgumentException ex)
            {
                log.debug("Unknown chat type {} from the Valiance server", typeName);
            }
        }

        return clanChatService.broadcastType();
    }
}
