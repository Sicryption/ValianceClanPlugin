package com.encryptiron.runs;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
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
    private static final int TICKS_TO_RECOGNISE_A_BOARD = 10;
    private static final Pattern PERSONAL_LINE = Pattern.compile("(?im)^\\s*(?:personal|fastest|best)\\b");
    private static final Pattern TIME = Pattern.compile("\\d+(?::\\d{2}){1,2}(?:\\.\\d{1,2})?");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final int FULLY_TRANSPARENT = 255;

    @Inject
    private Client client;

    @Inject
    private SendStatisticBoard sendStatisticBoard;

    private final Map<Integer, Integer> ticksLeftToRecogniseByInterface = new HashMap<>();
    private final Set<Integer> openBoards = new HashSet<>();
    private final Map<Integer, String> lastSentBoardByInterface = new HashMap<>();
    private final Map<Integer, Map<Widget, TextAppearance>> textAppearancesByInterface = new HashMap<>();
    private long boardReads;

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (widgetLoaded.getGroupId() != client.getTopLevelInterfaceId())
        {
            ticksLeftToRecogniseByInterface.put(widgetLoaded.getGroupId(), TICKS_TO_RECOGNISE_A_BOARD);
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        openBoards.removeIf(interfaceId -> client.getWidget(interfaceId, 0) == null);
        textAppearancesByInterface.keySet().retainAll(openBoards);
        for (int interfaceId : openBoards)
        {
            readBoard(interfaceId);
        }

        ticksLeftToRecogniseByInterface.keySet().removeAll(openBoards);
        ticksLeftToRecogniseByInterface.entrySet().removeIf(interfaceTicks -> {
            if (readBoard(interfaceTicks.getKey()))
            {
                openBoards.add(interfaceTicks.getKey());
                return true;
            }

            interfaceTicks.setValue(interfaceTicks.getValue() - 1);
            return interfaceTicks.getValue() <= 0;
        });
    }

    private boolean readBoard(int interfaceId)
    {
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Widget> widgetsInDrawOrder = new ArrayList<>();
        for (int componentId = 0; componentId < MOST_COMPONENTS_PER_INTERFACE; componentId++)
        {
            Widget widget = client.getWidget(interfaceId, componentId);
            if (widget != null && widget.getParentId() >>> 16 != interfaceId)
            {
                collect(widget, visited, widgetsInDrawOrder);
            }
        }

        long readNumber = ++boardReads;
        Map<Widget, TextAppearance> previousTextAppearances = textAppearancesByInterface.getOrDefault(interfaceId, Collections.emptyMap());
        Map<Widget, TextAppearance> textAppearances = new IdentityHashMap<>();
        Map<String, BoardText> topmostTextByPosition = new HashMap<>();
        boolean hasPersonalLabel = false;
        boolean hasTime = false;
        for (Widget widget : widgetsInDrawOrder)
        {
            String text = widget.getText() == null
                ? ""
                : Text.removeTags(LINE_BREAK.matcher(widget.getText()).replaceAll("\n")).replace((char) 0xA0, ' ').trim();
            Point location = widget.getCanvasLocation();
            if (text.isEmpty() || location == null || !isDrawn(widget))
            {
                continue;
            }

            TextAppearance appearance = previousTextAppearances.get(widget);
            if (appearance == null || !appearance.text.equals(text))
            {
                appearance = new TextAppearance(text, readNumber);
            }
            textAppearances.put(widget, appearance);

            BoardText boardText = new BoardText(text, location.getX() + widget.getWidth() / 2, location.getY(), appearance.readNumber);
            String position = boardText.x + "," + boardText.y;
            BoardText covered = topmostTextByPosition.get(position);
            if (covered == null || boardText.appearedOnRead >= covered.appearedOnRead)
            {
                topmostTextByPosition.put(position, boardText);
            }
        }
        textAppearancesByInterface.put(interfaceId, textAppearances);

        List<BoardText> boardTexts = new ArrayList<>(topmostTextByPosition.values());
        for (BoardText boardText : boardTexts)
        {
            hasPersonalLabel |= PERSONAL_LINE.matcher(boardText.text).find();
            hasTime |= TIME.matcher(boardText.text).find();
        }

        if (!hasPersonalLabel || !hasTime)
        {
            textAppearancesByInterface.remove(interfaceId);
            return false;
        }

        boardTexts.sort(Comparator.comparingInt((BoardText boardText) -> boardText.y)
            .thenComparingInt(boardText -> boardText.x)
            .thenComparing(boardText -> boardText.text));

        JsonArray texts = new JsonArray();
        for (BoardText boardText : boardTexts)
        {
            JsonObject entry = new JsonObject();
            entry.addProperty("text", boardText.text);
            entry.addProperty("x", boardText.x);
            entry.addProperty("y", boardText.y);
            texts.add(entry);
        }

        String board = texts.toString();
        if (!board.equals(lastSentBoardByInterface.get(interfaceId)))
        {
            lastSentBoardByInterface.put(interfaceId, board);
            sendStatisticBoard.send(texts);
        }

        return true;
    }

    private static class BoardText
    {
        private final String text;
        private final int x;
        private final int y;
        private final long appearedOnRead;

        private BoardText(String text, int x, int y, long appearedOnRead)
        {
            this.text = text;
            this.x = x;
            this.y = y;
            this.appearedOnRead = appearedOnRead;
        }
    }

    private static class TextAppearance
    {
        private final String text;
        private final long readNumber;

        private TextAppearance(String text, long readNumber)
        {
            this.text = text;
            this.readNumber = readNumber;
        }
    }

    private static boolean isDrawn(Widget widget)
    {
        if (widget.isHidden() || widget.getOpacity() >= FULLY_TRANSPARENT)
        {
            return false;
        }

        Rectangle bounds = widget.getBounds();
        for (Widget ancestor = widget.getParent(); ancestor != null; ancestor = ancestor.getParent())
        {
            if (!bounds.intersects(ancestor.getBounds()))
            {
                return false;
            }
        }

        return true;
    }

    private static void collect(Widget widget, Set<Widget> visited, List<Widget> widgetsInDrawOrder)
    {
        if (widget == null || !visited.add(widget))
        {
            return;
        }

        widgetsInDrawOrder.add(widget);
        collectAll(widget.getStaticChildren(), visited, widgetsInDrawOrder);
        collectAll(widget.getDynamicChildren(), visited, widgetsInDrawOrder);
        collectAll(widget.getNestedChildren(), visited, widgetsInDrawOrder);
    }

    private static void collectAll(Widget[] children, Set<Widget> visited, List<Widget> widgetsInDrawOrder)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            collect(child, visited, widgetsInDrawOrder);
        }
    }
}
