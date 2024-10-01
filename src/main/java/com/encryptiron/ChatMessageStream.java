package com.encryptiron;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

public class ChatMessageStream implements IMessageStream {

    private Client client;

    ChatMessageStream(Client client)
    {
        this.client = client;
    }

    @Override
    public void send(IMessage message) {
        StringBuilder stringBuilder = new StringBuilder();
        message.write(stringBuilder);

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Drop received: " + stringBuilder.toString(), null);
    }
}
