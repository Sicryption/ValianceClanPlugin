package com.encryptiron;

import net.runelite.api.Client;

public class Messenger {

    private ChatMessageStream chatMessageStream;

    Messenger(ValianceConfig config, Client client)
    {
        this.config = config;

        chatMessageStream = new ChatMessageStream(client);
    }

    void send(DropMessage message)
    {
        if (config.debug())
        {
            chatMessageStream.send(message);
            return;
        }
        
        // Upload to destination
    }
    
	private ValianceConfig config;
}
