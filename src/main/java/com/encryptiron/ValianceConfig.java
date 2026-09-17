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
        keyName = "sendDropScreenshots",
        name = "Send Drop Screenshots",
        description = "Sends a screenshot as proof when an event accepts one of your drops. Only the game world is captured - the interface and every chat window are cropped or painted out, and a screenshot for a drop no event wanted is discarded without being sent.",
        position = 4
    )
    default boolean sendDropScreenshots()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenshotScale",
        name = "Screenshot Scale (%)",
        description = "Shrinks drop screenshots before sending them. 100 sends the world view at its own size.",
        position = 5
    )
    default int screenshotScale()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "valianceServerUrl",
        name = "Server URL",
        description = "URL to the Valiance Server (don't change).",
        position = 6
    )
    default String valianceServerUrl()
    {
        return "valianceosrs.com";
    }
}
