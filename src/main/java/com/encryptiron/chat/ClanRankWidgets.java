package com.encryptiron.chat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class ClanRankWidgets
{
    private static final Pattern IMG_TAG = Pattern.compile("<img=(\\d+)>");
    private static final Pattern RANK_TITLE_BEFORE_NAME = Pattern.compile("^(?:<col=[0-9a-fA-F]+>)?[^<]*</col> ");
    private static final String CHANNEL_PREFIX = "[" + ClanChatService.CLAN_NAME + "]";

    private static final int CLAN_RANK_SPRITES_ENUM = 3798;

    private static final int CHAT_LINE_LAYOUT_SCRIPT = 4483;
    private static final int CHAT_LINE_LAYOUT_STRING_ARGUMENTS = 3;
    private static final int CHAT_LINE_LAYOUT_INT_ARGUMENTS = 14;
    private static final int NO_GRAPHIC = -1;
    private static final int GRAPHIC_WIDTH_OFFSET = 1;
    private static final int GRAPHIC_HEIGHT_OFFSET = 2;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RankIcons rankIcons;

    private Set<Integer> clanRankSprites;
    private int decoratedRanksRevision = -1;

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() != CHAT_LINE_LAYOUT_SCRIPT
            || client.getObjectStackSize() < CHAT_LINE_LAYOUT_STRING_ARGUMENTS
            || client.getIntStackSize() < CHAT_LINE_LAYOUT_INT_ARGUMENTS)
        {
            return;
        }

        Object[] strings = client.getObjectStack();
        int prefixIndex = client.getObjectStackSize() - CHAT_LINE_LAYOUT_STRING_ARGUMENTS;
        int nameIndex = prefixIndex + 1;
        if (!(strings[prefixIndex] instanceof String) || !(strings[nameIndex] instanceof String))
        {
            return;
        }

        String prefix = (String) strings[prefixIndex];
        String name = (String) strings[nameIndex];
        if (!Text.removeTags(prefix).contains(CHANNEL_PREFIX) && !rankIcons.showsRankIcon(name))
        {
            return;
        }

        int[] ints = client.getIntStack();
        int graphicIndex = client.getIntStackSize() - CHAT_LINE_LAYOUT_INT_ARGUMENTS;
        String nameWithoutTitle = RANK_TITLE_BEFORE_NAME.matcher(name).replaceFirst("");
        RankIcons.RankSlot rankSlot = rankIcons.rankSlot(nameWithoutTitle);
        if (rankSlot == null)
        {
            ints[graphicIndex] = NO_GRAPHIC;
            strings[nameIndex] = nameWithoutTitle;
            return;
        }

        ints[graphicIndex] = rankSlot.spriteId;
        ints[graphicIndex + GRAPHIC_WIDTH_OFFSET] = rankSlot.width;
        ints[graphicIndex + GRAPHIC_HEIGHT_OFFSET] = rankSlot.height;
        strings[nameIndex] = rankSlot.name;
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == ScriptID.CLAN_SIDEPANEL_DRAW)
        {
            decoratePlayerLists();
        }
    }

    public void redrawRanks()
    {
        clientThread.invokeLater(() ->
        {
            redrawPlayerList(InterfaceID.ClansSidepanel.UNIVERSE);
            redrawPlayerList(InterfaceID.ClansGuestSidepanel.UNIVERSE);
            client.refreshChat();
        });
    }

    private void redrawPlayerList(int panelId)
    {
        Widget panel = client.getWidget(panelId);
        if (panel != null && panel.getOnVarTransmitListener() != null)
        {
            client.runScript(panel.getOnVarTransmitListener());
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (rankIcons.getRanksRevision() != decoratedRanksRevision)
        {
            decoratedRanksRevision = rankIcons.getRanksRevision();
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

    private void decoratePlayerLists()
    {
        if (ClanChatService.isValiance(client.getClanChannel()))
        {
            decoratePlayerList(InterfaceID.ClansSidepanel.PLAYERLIST);
        }
        if (ClanChatService.isValiance(client.getGuestClanChannel()))
        {
            decoratePlayerList(InterfaceID.ClansGuestSidepanel.PLAYERLIST);
        }
    }

    private void decoratePlayerList(int listId)
    {
        Widget list = client.getWidget(listId);
        Widget[] children = list == null ? null : list.getChildren();
        if (children == null)
        {
            return;
        }

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
                addRankIconsToPlayerListName(child);
            }
        }
    }

    private void addRankIconsToPlayerListName(Widget name)
    {
        if (name.getXPositionMode() == WidgetPositionMode.ABSOLUTE_RIGHT
            && name.getWidthMode() == WidgetSizeMode.MINUS
            && name.getOriginalWidth() != 0)
        {
            name.setOriginalWidth(0);
            name.revalidate();
        }

        String plainName = IMG_TAG.matcher(name.getText()).replaceAll("");
        name.setText(rankIcons.rankIconTags(plainName) + plainName);
    }
}
