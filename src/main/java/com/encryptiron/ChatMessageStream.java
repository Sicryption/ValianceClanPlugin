package com.encryptiron;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

public class ChatMessageStream implements IMessageStream {

    private Client client;

    ChatMessageStream(Client client)
    {
        this.client = client;
    }

    private String generateStringWithSeparator(StringBuilder builder)
    {
        String messageAsString = builder.toString();
        String messageSpaceSeperated = "";

        for (int i = 0; i < messageAsString.length(); i++){
            char c = messageAsString.charAt(i);

            if (c == '[')
            {
                messageSpaceSeperated += " ";
            }

            messageSpaceSeperated += c;
        }

        return messageSpaceSeperated;
    }

    @Override
    public void send(IMessage message) {
        StringBuilder stringBuilder = new StringBuilder();
        message.write(stringBuilder);

        // Break message down per line
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Drop received:" + generateStringWithSeparator(stringBuilder), null);
    }
}
