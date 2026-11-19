package com.encryptiron.runs;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.encryptiron.rest.SendStatisticBoard;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

public class StatisticBoardReader
{
    private static final int MOST_COMPONENTS_PER_INTERFACE = 512;
    private static final Pattern PERSONAL_LINE = Pattern.compile("(?im)^\\s*personal\\b");
    private static final Pattern TIME = Pattern.compile("\\d+(?::\\d{2}){1,2}(?:\\.\\d{1,2})?");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");

    @Inject
    private Client client;

    @Inject
    private SendStatisticBoard sendStatisticBoard;

    private final Set<Integer> interfacesLoadedThisTick = new HashSet<>();
    private final Map<Integer, String> lastSentBoardByInterface = new HashMap<>();

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        interfacesLoadedThisTick.add(widgetLoaded.getGroupId());
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        for (int interfaceId : interfacesLoadedThisTick)
        {
            readBoard(interfaceId);
        }

        interfacesLoadedThisTick.clear();
    }

    private void readBoard(int interfaceId)
    {
        Set<Widget> widgets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int componentId = 0; componentId < MOST_COMPONENTS_PER_INTERFACE; componentId++)
        {
            collect(client.getWidget(interfaceId, componentId), widgets);
        }

        JsonArray texts = new JsonArray();
        boolean hasPersonalLabel = false;
        boolean hasTime = false;
        for (Widget widget : widgets)
        {
            String text = widget.getText() == null
                ? ""
                : Text.removeTags(LINE_BREAK.matcher(widget.getText()).replaceAll("\n")).replace((char) 0xA0, ' ').trim();
            Point location = widget.getCanvasLocation();
            if (text.isEmpty() || widget.isHidden() || location == null)
            {
                continue;
            }

            hasPersonalLabel |= PERSONAL_LINE.matcher(text).find();
            hasTime |= TIME.matcher(text).find();

            JsonObject entry = new JsonObject();
            entry.addProperty("text", text);
            entry.addProperty("x", location.getX() + widget.getWidth() / 2);
            entry.addProperty("y", location.getY());
            texts.add(entry);
        }

        String boardText = texts.toString();
        if (!hasPersonalLabel || !hasTime || boardText.equals(lastSentBoardByInterface.get(interfaceId)))
        {
            return;
        }

        lastSentBoardByInterface.put(interfaceId, boardText);
        sendStatisticBoard.send(texts);
    }

    private static void collect(Widget widget, Set<Widget> widgets)
    {
        if (widget == null || !widgets.add(widget))
        {
            return;
        }

        collectAll(widget.getStaticChildren(), widgets);
        collectAll(widget.getDynamicChildren(), widgets);
        collectAll(widget.getNestedChildren(), widgets);
    }

    private static void collectAll(Widget[] children, Set<Widget> widgets)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            collect(child, widgets);
        }
    }
}
