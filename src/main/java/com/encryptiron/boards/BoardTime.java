package com.encryptiron.boards;

import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

class BoardTime
{
    static final String ANY_TEAM_SIZE = "Any";
    static final String SOLO = "Solo";
    static final String WHOLE_RUN = "";
    static final String OVERALL_TIME = "Overall time";

    private static final Pattern SHOWN_TIME = Pattern.compile("\\d+(?::\\d{2}){1,2}(?:\\.\\d{1,2})?");

    private final JsonObject json = new JsonObject();

    private BoardTime(String content, String teamSize, String split)
    {
        json.addProperty("content", content);
        json.addProperty("team_size", teamSize);
        json.addProperty("split", split);
    }

    static Optional<BoardTime> ofTicks(String content, String teamSize, String split, int ticks)
    {
        if (ticks <= 0)
        {
            return Optional.empty();
        }

        BoardTime boardTime = new BoardTime(content, teamSize, split);
        boardTime.json.addProperty("ticks", ticks);
        return Optional.of(boardTime);
    }

    static Optional<BoardTime> ofShownTime(String content, String teamSize, String split, String shownTime)
    {
        if (!SHOWN_TIME.matcher(shownTime).matches())
        {
            return Optional.empty();
        }

        BoardTime boardTime = new BoardTime(content, teamSize, split);
        boardTime.json.addProperty("time", shownTime);
        return Optional.of(boardTime);
    }

    static String teamSizeOf(int players)
    {
        return players == 1 ? SOLO : players + " players";
    }

    JsonObject toJson()
    {
        return json;
    }
}
