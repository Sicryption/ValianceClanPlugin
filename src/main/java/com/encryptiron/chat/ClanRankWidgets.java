package com.encryptiron.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Takes the in-game clan ranks off what the game draws, once it has drawn it.
 *
 * The game draws a member's in-game clan rank as its own graphic beside the
 * name, both on clan chat lines and on each row of the clan tab's member list,
 * so it cannot be removed from the name RankIcons rewrites. Instead, once the
 * game has drawn:
 * - on Valiance clan chat lines, the in-game rank graphic is hidden, leaving the
 *   Valiance rank icons RankIcons put into the name;
 * - in the clan tab, every in-game rank graphic is hidden, and each name is
 *   widened into the space it leaves and given the member's Valiance icons.
 *
 * In-game rank graphics are recognised by their sprite: the game's enum of clan
 * rank icons lists them all.
 */
@Slf4j
@Singleton
public class ClanRankWidgets
{
    private static final Pattern IMG_TAG = Pattern.compile("<img=(\\d+)>");
    private static final String CHANNEL_PREFIX = "[" + ClanChatService.CLAN_NAME + "]";

    // The game's enum from clan rank to the sprite drawn for it; RuneLite's
    // ChatIconManager reads its clan rank icons from the same one.
    private static final int CLAN_RANK_SPRITES_ENUM = 3798;

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

    private Set<Integer> clanRankSprites;
    // Set whenever the chat may have been rebuilt; the lines are cleaned once,
    // just before the next frame, rather than after every script.
    private boolean chatDirty;
    private int drawnRevision = -1;
    // Chat lines already logged in debug, so each is only logged once.
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
                hideChatRankGraphics();
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
        hideChatRankGraphics();

        // The clan tab only redraws when the game says so, so a change in ranks
        // is drawn into it here.
        if (rankIcons.getRevision() != drawnRevision)
        {
            drawnRevision = rankIcons.getRevision();
            decoratePlayerLists();
        }
    }

    private boolean isClanRankGraphic(Widget widget)
    {
        if (widget.getType() != WidgetType.GRAPHIC)
        {
            return false;
        }

        if (clanRankSprites == null)
        {
            EnumComposition sprites = client.getEnum(CLAN_RANK_SPRITES_ENUM);
            clanRankSprites = new HashSet<>();
            for (int sprite : sprites.getIntVals())
            {
                clanRankSprites.add(sprite);
            }
        }
        return clanRankSprites.contains(widget.getSpriteId());
    }

    private void hideChatRankGraphics()
    {
        Widget area = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (area == null)
        {
            return;
        }

        List<Widget> texts = new ArrayList<>();
        List<Widget> rankGraphics = new ArrayList<>();
        collect(area.getDynamicChildren(), texts, rankGraphics);
        collect(area.getStaticChildren(), texts, rankGraphics);

        if (config.debug() && rankGraphics.isEmpty() && loggedLines.add("summary"))
        {
            Set<Integer> graphicSprites = new HashSet<>();
            for (Widget[] children : new Widget[][]{area.getDynamicChildren(), area.getStaticChildren(), area.getNestedChildren()})
            {
                for (Widget child : children == null ? new Widget[0] : children)
                {
                    if (child != null && child.getType() == WidgetType.GRAPHIC)
                    {
                        graphicSprites.add(child.getSpriteId());
                    }
                }
            }
            log.info("Valiance chat: no rank graphics found; {} dynamic, {} static, {} nested children; graphic sprites {}",
                length(area.getDynamicChildren()), length(area.getStaticChildren()), length(area.getNestedChildren()), graphicSprites);
        }

        for (Widget graphic : rankGraphics)
        {
            Widget line = findLineText(graphic, texts);
            if (config.debug())
            {
                String key = graphic.getSpriteId() + "|" + (line == null ? null : line.getText());
                if (loggedLines.add(key))
                {
                    log.info("Valiance chat rank graphic: sprite {} at {},{} on line \"{}\"",
                        graphic.getSpriteId(), graphic.getRelativeX(), graphic.getRelativeY(),
                        line == null ? null : line.getText());
                }
            }

            if (line != null && Text.removeTags(line.getText()).contains(CHANNEL_PREFIX))
            {
                graphic.setHidden(true);
            }
        }
    }

    private static int length(Widget[] children)
    {
        return children == null ? 0 : children.length;
    }

    private void collect(Widget[] children, List<Widget> texts, List<Widget> rankGraphics)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            if (child == null || child.isHidden())
            {
                continue;
            }
            if (child.getType() == WidgetType.TEXT && child.getText() != null && !child.getText().isEmpty())
            {
                texts.add(child);
            }
            else if (isClanRankGraphic(child))
            {
                rankGraphics.add(child);
            }
        }
    }

    /**
     * The text of the chat line a graphic sits on: the text whose row covers
     * the middle of the graphic, preferring one that names the channel.
     */
    private static Widget findLineText(Widget graphic, List<Widget> texts)
    {
        int middle = graphic.getRelativeY() + graphic.getHeight() / 2;
        Widget found = null;
        for (Widget text : texts)
        {
            if (middle >= text.getRelativeY() && middle < text.getRelativeY() + text.getHeight())
            {
                if (Text.removeTags(text.getText()).contains(CHANNEL_PREFIX))
                {
                    return text;
                }
                if (found == null)
                {
                    found = text;
                }
            }
        }
        return found;
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
        for (Widget child : children)
        {
            if (child == null)
            {
                continue;
            }

            if (isClanRankGraphic(child))
            {
                child.setHidden(true);
            }
            else if (child.getType() == WidgetType.TEXT
                && child.getXTextAlignment() == WidgetTextAlignment.LEFT
                && child.getText() != null && !child.getText().isEmpty())
            {
                decorateName(child);
            }
        }
    }

    private void decorateName(Widget name)
    {
        // Each name sits right-aligned in the row at the row's width less room
        // for the rank graphic. With the graphic hidden it takes the full width,
        // so the Valiance icons start where the graphic was.
        if (name.getXPositionMode() == WidgetPositionMode.ABSOLUTE_RIGHT
            && name.getWidthMode() == WidgetSizeMode.MINUS
            && name.getOriginalWidth() != 0)
        {
            name.setOriginalWidth(0);
            name.revalidate();
        }

        // Icons from an earlier pass are taken off first, so redrawing never
        // stacks them up.
        String plainName = IMG_TAG.matcher(name.getText()).replaceAll("");
        name.setText(rankIcons.rankIconTags(plainName) + plainName);
    }
}
