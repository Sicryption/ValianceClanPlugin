package com.encryptiron;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("encryptiron")
public interface ValianceConfig extends Config
{
    @ConfigItem(
        keyName = "chatMessageOnEventItemAccepted",
        name = "Event Accepted Chat Message",
        description = "Puts a message into the users chatbox when an item used in an event was accepted.",
        position = 1
    )
    default boolean chatMessageOnEventItemAccepted()
    {
        return true;
    }
    
    @ConfigItem(
        keyName = "popupOnEventItemAccepted",
        name = "Event Accepted Pop-up",
        description = "Triggers a pop-up when an item used in an event was accepted.",
        position = 2
    )
    default boolean popupOnEventItemAccepted()
    {
        return true;
    }
    
    @ConfigItem(
        keyName = "debug",
        name = "Enable debug",
        description = "Adds debug logging to the users chatbox.",
        position = 3
    )
    default boolean debug()
    {
        return false;
    }

    @ConfigItem(
        keyName = "valianceServerUrl",
        name = "Server URL",
        description = "URL to the Valiance Server (don't change).",
        position = 4
    )
    default String valianceServerUrl()
    {
        return "valianceosrs.com";
    }
}
