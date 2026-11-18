package com.encryptiron;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

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

    @ConfigSection(
        name = "Clan Chat",
        description = "Valiance clan chat, history and event broadcasts.",
        position = 5
    )
    String clanChatSection = "clanChat";

    @Range(
        min = 0,
        max = 100
    )
    @ConfigItem(
        keyName = "clanChatHistoryCount",
        name = "Messages on log-in",
        description = "How many recent Valiance clan chat messages to show when you log in. 0 shows none.",
        position = 6,
        section = clanChatSection
    )
    default int clanChatHistoryCount()
    {
        return 10;
    }

    @ConfigItem(
        keyName = "showValianceRanks",
        name = "Valiance ranks",
        description = "Shows every Valiance rank a member holds beside their name in clan chat, in place of their in-game clan rank.",
        position = 8,
        section = clanChatSection
    )
    default boolean showValianceRanks()
    {
        return true;
    }
}
