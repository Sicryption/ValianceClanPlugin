package com.encryptiron.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class ClanRankWidgets
{
    private static final Pattern IMG_TAG = Pattern.compile("<img=(\\d+)>");
    private static final String CHANNEL_PREFIX = "[" + ClanChatService.CLAN_NAME + "]";

    private static final int CLAN_RANK_SPRITES_ENUM = 3798;

    private static final int CHAT_LINE_BUILD_SCRIPT = 84;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RankIcons rankIcons;

    private Set<Integer> clanRankSprites;
    private boolean chatRebuilt;
    private int decoratedRanksRevision = -1;

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        int scriptId = event.getScriptId();
        if (scriptId == ScriptID.BUILD_CHATBOX || scriptId == CHAT_LINE_BUILD_SCRIPT)
        {
            chatRebuilt = true;
        }
        else if (scriptId == ScriptID.CLAN_SIDEPANEL_DRAW)
        {
            decoratePlayerLists();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        chatRebuilt = true;
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
    public void onBeforeRender(BeforeRender event)
    {
        if (chatRebuilt)
        {
            chatRebuilt = false;
            hideChatRankGraphics();
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        hideChatRankGraphics();

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

    private void hideChatRankGraphics()
    {
        Widget area = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (area == null)
        {
            return;
        }

        List<Widget> texts = new ArrayList<>();
        List<Widget> rankGraphics = new ArrayList<>();
        collectTextsAndRankGraphics(area.getDynamicChildren(), texts, rankGraphics);
        collectTextsAndRankGraphics(area.getStaticChildren(), texts, rankGraphics);

        for (Widget graphic : rankGraphics)
        {
            Widget line = findLineTextForGraphic(graphic, texts);
            if (line != null && Text.removeTags(line.getText()).contains(CHANNEL_PREFIX))
            {
                graphic.setHidden(true);
                moveLineIntoHiddenGraphicSpace(graphic, texts);
            }
        }
    }

    private void moveLineIntoHiddenGraphicSpace(Widget graphic, List<Widget> texts)
    {
        if (graphic.getXPositionMode() != WidgetPositionMode.ABSOLUTE_LEFT)
        {
            return;
        }

        List<Widget> rest = new ArrayList<>();
        Widget name = null;
        for (Widget text : texts)
        {
            if (isOnRow(graphic, text)
                && text.getXPositionMode() == WidgetPositionMode.ABSOLUTE_LEFT
                && text.getOriginalX() >= graphic.getOriginalX())
            {
                rest.add(text);
                if (name == null || text.getOriginalX() < name.getOriginalX())
                {
                    name = text;
                }
            }
        }

        if (name == null)
        {
            return;
        }

        int shift = name.getOriginalX() - graphic.getOriginalX();
        if (shift <= 0)
        {
            return;
        }

        for (Widget text : rest)
        {
            text.setOriginalX(text.getOriginalX() - shift);
            text.revalidate();
        }
    }

    private static boolean isOnRow(Widget graphic, Widget text)
    {
        int middle = graphic.getRelativeY() + graphic.getHeight() / 2;
        return middle >= text.getRelativeY() && middle < text.getRelativeY() + text.getHeight();
    }

    private void collectTextsAndRankGraphics(Widget[] children, List<Widget> texts, List<Widget> rankGraphics)
    {
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
            if (child.getType() == WidgetType.TEXT && !child.isHidden() && child.getText() != null && !child.getText().isEmpty())
            {
                texts.add(child);
            }
            else if (isClanRankGraphic(child))
            {
                rankGraphics.add(child);
            }
        }
    }

    private static Widget findLineTextForGraphic(Widget graphic, List<Widget> texts)
    {
        Widget found = null;
        for (Widget text : texts)
        {
            if (isOnRow(graphic, text))
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
