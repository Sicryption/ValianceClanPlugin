package com.encryptiron.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Which event drop broadcasts the player sees in clan chat. */
@Getter
@RequiredArgsConstructor
public enum EventDropBroadcastFilter
{
    ALL_TEAMS("All teams"),
    MY_TEAM("My team only"),
    OFF("Off");

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
