package com.encryptiron.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Takes the in-game clan ranks off what the game draws, once it has drawn it.
 *
 * The game adds a member's in-game rank icon to a clan chat line from its own
 * member list, outside the name RankIcons rewrites, and draws the clan tab's
 * member list with a rank graphic per row. Neither can be changed before it is
 * drawn, so both are corrected straight after:
 * - Valiance clan chat lines lose any icon in front of the name that is
 *   neither a Valiance rank nor an account icon such as ironman;
 * - the clan tab's rank graphics are hidden, and each name is given the
 *   member's Valiance rank icons instead.
 */
@Slf4j
@Singleton
public class ClanRankWidgets
{
    private static final Pattern IMG_TAG = Pattern.compile("<img=(\\d+)>");
    private static final String CHANNEL_PREFIX = "[" + ClanChatService.CLAN_NAME + "]";

    // The script that builds each chat line, which RuneLite's chatMessageBuilding
    // callback runs inside. The chatbox as a whole is rebuilt by BUILD_CHATBOX.
    private static final int CHAT_LINE_BUILD = 84;

    private static final int[] PLAYER_LISTS = {
        InterfaceID.ClansSidepanel.PLAYERLIST,
        InterfaceID.ClansGuestSidepanel.PLAYERLIST,
    };

    @Inject
    private Client client;

    @Inject
    private RankIcons rankIcons;

    @Inject
    private ValianceConfig config;

    // Set whenever the chat may have been rebuilt; the lines are cleaned once,
    // just before the next frame, rather than after every script.
    private boolean chatDirty;
    private int drawnRevision = -1;
    // Lines already logged in debug, so a line is only logged once.
    private final Set<String> loggedLines = new HashSet<>();

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        int scriptId = event.getScriptId();
        if (scriptId == ScriptID.BUILD_CHATBOX || scriptId == CHAT_LINE_BUILD)
        {
            chatDirty = true;
        }
        else if (scriptId == ScriptID.CLAN_SIDEPANEL_DRAW && config.showValianceRanks())
        {
            decoratePlayerLists();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        chatDirty = true;
    }

    @Subscribe
    public void onBeforeRender(BeforeRender event)
    {
        if (chatDirty)
        {
            chatDirty = false;
            if (config.showValianceRanks())
            {
                cleanChatLines();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (!config.showValianceRanks())
        {
            return;
        }

        // A fallback for any rebuild the events above miss.
        cleanChatLines();

        // The clan tab only redraws when the game says so, so a change in ranks
        // is drawn into it here.
        if (rankIcons.getRevision() != drawnRevision)
        {
            drawnRevision = rankIcons.getRevision();
            decoratePlayerLists();
        }
    }

    private void cleanChatLines()
    {
        for (int id = InterfaceID.Chatbox.LINE0; id <= InterfaceID.Chatbox.LINE499; id++)
        {
            Widget line = client.getWidget(id);
            if (line == null || line.isHidden())
            {
                continue;
            }

            String text = line.getText();
            if (text == null || !text.contains("<img="))
            {
                continue;
            }

            if (config.debug() && loggedLines.add(text))
            {
                log.info("Valiance chat line {}: \"{}\"", id - InterfaceID.Chatbox.LINE0, text);
            }

            if (!Text.removeTags(text).contains(CHANNEL_PREFIX))
            {
                continue;
            }

            String cleaned = removeInGameRankIcons(text);
            if (!cleaned.equals(text))
            {
                line.setText(cleaned);
            }
        }
    }

    /**
     * The line without in-game rank icons. Only the part up to the name's colon
     * is touched, so icons in the message itself (emoji and the like) stay.
     */
    private String removeInGameRankIcons(String text)
    {
        int prefixEnd = text.indexOf(']');
        int nameEnd = prefixEnd < 0 ? -1 : text.indexOf(':', prefixEnd);
        int end = nameEnd < 0 ? text.length() : nameEnd;

        StringBuffer head = new StringBuffer();
        Matcher tags = IMG_TAG.matcher(text.substring(0, end));
        while (tags.find())
        {
            boolean keep = rankIcons.isKeptIcon(Integer.parseInt(tags.group(1)));
            tags.appendReplacement(head, keep ? Matcher.quoteReplacement(tags.group()) : "");
        }
        tags.appendTail(head);

        return head + text.substring(end);
    }

    private void decoratePlayerLists()
    {
        for (int listId : PLAYER_LISTS)
        {
            Widget list = client.getWidget(listId);
            Widget[] children = list == null ? null : list.getChildren();
            if (children != null)
            {
                decoratePlayerList(children);
            }
        }
    }

    private void decoratePlayerList(Widget[] children)
    {
        List<Widget> texts = new ArrayList<>();
        List<Widget> graphics = new ArrayList<>();
        for (Widget child : children)
        {
            if (child == null)
            {
                continue;
            }
            if (child.getType() == WidgetType.TEXT)
            {
                texts.add(child);
            }
            else if (child.getType() == WidgetType.GRAPHIC)
            {
                graphics.add(child);
            }
        }

        if (texts.isEmpty())
        {
            return;
        }

        if (config.debug())
        {
            for (Widget child : children)
            {
                if (child != null)
                {
                    log.info("Valiance clan list child: type {} at {},{} size {}x{} sprite {} hidden {} text \"{}\"",
                        child.getType(), child.getOriginalX(), child.getOriginalY(),
                        child.getOriginalWidth(), child.getOriginalHeight(),
                        child.getSpriteId(), child.isHidden(), child.getText());
                }
            }
        }

        // Names are the leftmost column of text; the worlds sit to their right.
        int nameX = Integer.MAX_VALUE;
        for (Widget text : texts)
        {
            nameX = Math.min(nameX, text.getOriginalX());
        }

        // The rank graphics are the column left of the names. Once hidden, the
        // names move into their place, widened by as much, so the Valiance icons
        // in front of them fit. Already-hidden graphics mean this list was done
        // before and only the icons need redrawing.
        int graphicX = Integer.MAX_VALUE;
        for (Widget graphic : graphics)
        {
            if (graphic.getOriginalX() < nameX && !graphic.isHidden())
            {
                graphicX = Math.min(graphicX, graphic.getOriginalX());
                graphic.setHidden(true);
            }
        }

        for (Widget text : texts)
        {
            if (text.getOriginalX() != nameX || text.getText() == null)
            {
                continue;
            }

            if (graphicX != Integer.MAX_VALUE)
            {
                text.setOriginalWidth(text.getOriginalWidth() + nameX - graphicX);
                text.setOriginalX(graphicX);
                text.revalidate();
            }

            // Icons from an earlier pass are taken off first, so redrawing never
            // stacks them up.
            String plainName = IMG_TAG.matcher(text.getText()).replaceAll("");
            text.setText(rankIcons.rankIconTags(plainName) + plainName);
        }
    }
}
