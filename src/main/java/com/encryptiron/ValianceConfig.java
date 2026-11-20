package com.encryptiron;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ValianceConfig.GROUP)
public interface ValianceConfig extends Config
{
    String GROUP = "encryptiron";

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

    @ConfigItem(
        keyName = "shareClanChat",
        name = "Share my clan chat",
        description = "Sends the messages you type in the Valiance clan chat to the Valiance server, so members who log in later see them in chat history and staff can review the chat log. Off unless you turn it on.",
        warning = "This feature submits the messages you type in the Valiance clan chat to a 3rd-party server not controlled or verified by RuneLite developers.",
        position = 6,
        section = clanChatSection
    )
    default boolean shareClanChat()
    {
        return false;
    }

    @Range(
        min = 0,
        max = 100
    )
    @ConfigItem(
        keyName = "clanChatHistoryCount",
        name = "Messages on log-in",
        description = "How many recent Valiance clan chat messages to show when you log in. 0 shows none.",
        position = 7,
        section = clanChatSection
    )
    default int clanChatHistoryCount()
    {
        return 10;
    }
}
