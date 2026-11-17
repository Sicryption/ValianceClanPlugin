package com.encryptiron.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
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

    private int drawnRevision = -1;

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (!config.showValianceRanks())
        {
            return;
        }

        if (event.getScriptId() == ScriptID.BUILD_CHATBOX)
        {
            cleanChatLines();
        }
        else if (event.getScriptId() == ScriptID.CLAN_SIDEPANEL_DRAW)
        {
            decoratePlayerLists();
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        // The chat redraws itself when ranks change; the clan tab only redraws
        // when the game says so, so it is redone here.
        if (config.showValianceRanks() && rankIcons.getRevision() != drawnRevision)
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
            if (text == null || !text.contains("<img=") || !Text.removeTags(text).contains(CHANNEL_PREFIX))
            {
                continue;
            }

            String cleaned = removeInGameRankIcons(text);
            if (!cleaned.equals(text))
            {
                if (config.debug())
                {
                    log.info("Valiance chat line: \"{}\" -> \"{}\"", text, cleaned);
                }
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
            if (children == null)
            {
                continue;
            }

            // A row is every child at the same height: the rank graphic, the
            // name and the world.
            Map<Integer, List<Widget>> rows = new TreeMap<>();
            for (Widget child : children)
            {
                if (child != null)
                {
                    rows.computeIfAbsent(child.getOriginalY(), y -> new ArrayList<>()).add(child);
                }
            }

            for (List<Widget> row : rows.values())
            {
                decorateRow(row);
            }
        }
    }

    private void decorateRow(List<Widget> row)
    {
        Widget name = null;
        for (Widget widget : row)
        {
            if (widget.getType() == WidgetType.TEXT && (name == null || widget.getOriginalX() < name.getOriginalX()))
            {
                name = widget;
            }
        }
        if (name == null || name.getText() == null)
        {
            return;
        }

        // The rank graphic is whatever is drawn left of the name. The name
        // moves into its place, and is widened by as much, so the Valiance
        // icons in front of it fit.
        for (Widget widget : row)
        {
            if (widget.getType() == WidgetType.GRAPHIC && widget.getOriginalX() < name.getOriginalX() && !widget.isHidden())
            {
                int shift = name.getOriginalX() - widget.getOriginalX();
                widget.setHidden(true);
                name.setOriginalX(widget.getOriginalX());
                name.setOriginalWidth(name.getOriginalWidth() + shift);
                name.revalidate();
            }
        }

        // Icons from an earlier pass are taken off first, so redrawing never
        // stacks them up.
        String plainName = IMG_TAG.matcher(name.getText()).replaceAll("");
        name.setText(rankIcons.rankIconTags(plainName) + plainName);
    }
}
