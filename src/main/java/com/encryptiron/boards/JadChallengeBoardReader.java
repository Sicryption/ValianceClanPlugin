package com.encryptiron.boards;

import static com.encryptiron.boards.BoardTime.ANY_TEAM_SIZE;
import static com.encryptiron.boards.BoardTime.WHOLE_RUN;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

public class JadChallengeBoardReader
{
    private static final int JAD_CHALLENGE_SETUP = 4023;
    private static final int BOARD_TEXT_ARGUMENT = 1;
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern CHALLENGE = Pattern.compile("TzHaar-Ket-Rak's \\w+ Challenge");
    private static final Pattern PERSONAL_BEST = Pattern.compile("Personal Best Time: (.+)");

    private final BoardTimesSender boardTimesSender;

    @Inject
    JadChallengeBoardReader(BoardTimesSender boardTimesSender)
    {
        this.boardTimesSender = boardTimesSender;
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        if (scriptPreFired.getScriptId() != JAD_CHALLENGE_SETUP || scriptPreFired.getScriptEvent() == null)
        {
            return;
        }

        Object[] arguments = scriptPreFired.getScriptEvent().getArguments();
        if (arguments != null && arguments.length > BOARD_TEXT_ARGUMENT && arguments[BOARD_TEXT_ARGUMENT] instanceof String)
        {
            boardTimesSender.send(readChallenges((String) arguments[BOARD_TEXT_ARGUMENT]));
        }
    }

    private static List<BoardTime> readChallenges(String boardText)
    {
        List<BoardTime> boardTimes = new ArrayList<>();
        String challenge = null;
        for (String taggedLine : LINE_BREAK.split(boardText))
        {
            String line = Text.removeTags(taggedLine).trim();
            Matcher personalBest = PERSONAL_BEST.matcher(line);
            if (CHALLENGE.matcher(line).matches())
            {
                challenge = line;
            }
            else if (challenge != null && personalBest.matches())
            {
                BoardTime.ofShownTime(challenge, ANY_TEAM_SIZE, WHOLE_RUN, personalBest.group(1)).ifPresent(boardTimes::add);
                challenge = null;
            }
        }
        return boardTimes;
    }
}
