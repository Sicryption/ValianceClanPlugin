package com.encryptiron.runs;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
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
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

public class StatisticBoardReader
{
    private static final int MOST_COMPONENTS_PER_INTERFACE = 512;
    private static final int TICKS_TO_RECOGNISE_A_BOARD = 10;
    private static final Pattern PERSONAL_LINE = Pattern.compile("(?im)^\\s*(?:personal|fastest|best|your best)\\b");
    private static final Pattern TIME = Pattern.compile("\\d+(?::\\d{2}){1,2}(?:\\.\\d{1,2})?");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final int FULLY_TRANSPARENT = 255;
    private static final int COVER_TO_TEXT_AREA = 9;
    private static final Set<Integer> NEVER_BOARDS = Set.of(InterfaceID.CHATBOX, InterfaceID.PM_CHAT, InterfaceID.CHATCHANNEL_CURRENT);
    private static final String BRUTUS = "Brutus";
    private static final String DEMONIC_BRUTUS = "Demonic Brutus";
    private static final Pattern ONLY_DIGITS = Pattern.compile("[0-9]+");
    private static final Map<Integer, String> BOSS_BY_SCOREBOARD_OBJECT = Map.of(
        ObjectID.COWBOSS_SCOREBOARD, BRUTUS,
        ObjectID.COWBOSS_SCOREBOARD_UBER, DEMONIC_BRUTUS);
    private static final Set<Integer> BOARDS_NAMED_BY_THE_OBJECT_CLICKED = Set.of(InterfaceID.COWBOSS_SCOREBOARD);
    private static final int MOST_TICKS_FROM_CLICK_TO_BOARD = 50;
    private static final Set<MenuAction> GAME_OBJECT_OPTIONS = EnumSet.of(
        MenuAction.GAME_OBJECT_FIRST_OPTION,
        MenuAction.GAME_OBJECT_SECOND_OPTION,
        MenuAction.GAME_OBJECT_THIRD_OPTION,
        MenuAction.GAME_OBJECT_FOURTH_OPTION,
        MenuAction.GAME_OBJECT_FIFTH_OPTION);

    @Inject
    private Client client;

    @Inject
    private SendStatisticBoard sendStatisticBoard;

    private final Map<Integer, Integer> ticksLeftToRecogniseByInterface = new HashMap<>();
    private final Set<Integer> openBoards = new HashSet<>();
    private final Map<Integer, String> lastSentBoardByInterface = new HashMap<>();
    private final Map<Integer, String> lastReadBoardByInterface = new HashMap<>();
    private final Map<Integer, Map<Widget, WidgetAppearance>> widgetAppearancesByInterface = new HashMap<>();
    private final Map<Integer, String> bossByBoard = new HashMap<>();
    private String clickedScoreboardBoss;
    private int clickedScoreboardTick;
    private long boardReads;

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
    {
        if (GAME_OBJECT_OPTIONS.contains(menuOptionClicked.getMenuAction()))
        {
            clickedScoreboardBoss = scoreboardBoss(menuOptionClicked.getId());
            clickedScoreboardTick = client.getTickCount();
        }
    }

    private String scoreboardBoss(int objectId)
    {
        ObjectComposition object = client.getObjectDefinition(objectId);
        if (object != null && object.getImpostorIds() != null)
        {
            ObjectComposition shownObject = object.getImpostor();
            if (shownObject != null)
            {
                object = shownObject;
            }
        }

        return object == null ? null : BOSS_BY_SCOREBOARD_OBJECT.get(object.getId());
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        if (widgetLoaded.getGroupId() != client.getTopLevelInterfaceId() && !NEVER_BOARDS.contains(widgetLoaded.getGroupId()))
        {
            ticksLeftToRecogniseByInterface.put(widgetLoaded.getGroupId(), TICKS_TO_RECOGNISE_A_BOARD);
        }

        if (BOARDS_NAMED_BY_THE_OBJECT_CLICKED.contains(widgetLoaded.getGroupId()))
        {
            boolean clickedJustBefore = client.getTickCount() - clickedScoreboardTick <= MOST_TICKS_FROM_CLICK_TO_BOARD;
            bossByBoard.put(widgetLoaded.getGroupId(), clickedJustBefore ? clickedScoreboardBoss : null);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.HOPPING)
        {
            ticksLeftToRecogniseByInterface.clear();
            openBoards.clear();
            lastSentBoardByInterface.clear();
            lastReadBoardByInterface.clear();
            widgetAppearancesByInterface.clear();
            bossByBoard.clear();
            clickedScoreboardBoss = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        openBoards.removeIf(interfaceId -> client.getWidget(interfaceId, 0) == null);
        widgetAppearancesByInterface.keySet().retainAll(openBoards);
        lastReadBoardByInterface.keySet().retainAll(openBoards);
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

        if (widgetsInDrawOrder.stream().noneMatch(widget -> PERSONAL_LINE.matcher(textOf(widget)).find()))
        {
            widgetAppearancesByInterface.remove(interfaceId);
            return false;
        }

        long readNumber = ++boardReads;
        Map<Widget, WidgetAppearance> previousAppearances = widgetAppearancesByInterface.getOrDefault(interfaceId, Collections.emptyMap());
        Map<Widget, WidgetAppearance> appearances = new IdentityHashMap<>();
        List<Widget> textWidgets = new ArrayList<>();
        List<Widget> covers = new ArrayList<>();
        for (Widget widget : widgetsInDrawOrder)
        {
            if (widget.getCanvasLocation() == null || !isDrawn(widget))
            {
                continue;
            }

            String text = textOf(widget);
            WidgetAppearance appearance = previousAppearances.get(widget);
            if (appearance == null || !appearance.text.equals(text))
            {
                appearance = new WidgetAppearance(text, readNumber);
            }
            appearances.put(widget, appearance);

            if (widget.getType() == WidgetType.TEXT)
            {
                textWidgets.add(widget);
            }
            else if (canCover(widget))
            {
                covers.add(widget);
            }
        }
        widgetAppearancesByInterface.put(interfaceId, appearances);

        Map<String, BoardText> topmostTextByPosition = new HashMap<>();
        boolean hasPersonalLabel = false;
        boolean hasTime = false;
        for (Widget widget : textWidgets)
        {
            WidgetAppearance appearance = appearances.get(widget);
            if (isCoveredByNewerWidget(widget, appearance.readNumber, covers, appearances))
            {
                continue;
            }

            Point location = widget.getCanvasLocation();
            BoardText boardText = new BoardText(appearance.text, location.getX() + widget.getWidth() / 2, location.getY(), appearance.readNumber,
                widget.getTextColor(), backgroundOf(widget, covers));
            String position = boardText.x + "," + boardText.y;
            BoardText covered = topmostTextByPosition.get(position);
            if (covered == null || boardText.appearedOnRead >= covered.appearedOnRead)
            {
                topmostTextByPosition.put(position, boardText);
            }
        }

        List<BoardText> boardTexts = new ArrayList<>(topmostTextByPosition.values());
        boardTexts.removeIf(boardText -> boardText.text.isEmpty());
        for (BoardText boardText : boardTexts)
        {
            hasPersonalLabel |= PERSONAL_LINE.matcher(boardText.text).find();
            hasTime |= TIME.matcher(boardText.text).find();
        }

        if (!hasPersonalLabel || !hasTime)
        {
            widgetAppearancesByInterface.remove(interfaceId);
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
            entry.addProperty("c", boardText.textColor);
            if (boardText.background != null)
            {
                entry.addProperty("bg", boardText.background);
            }
            texts.add(entry);
        }

        String boss = boardBoss(interfaceId);
        String board = boss + "|" + boardContent(boardTexts);
        boolean unchangedSinceLastRead = board.equals(lastReadBoardByInterface.put(interfaceId, board));
        if (unchangedSinceLastRead && !board.equals(lastSentBoardByInterface.get(interfaceId)))
        {
            lastSentBoardByInterface.put(interfaceId, board);
            sendStatisticBoard.send(texts, boss);
        }

        return true;
    }

    private String boardBoss(int interfaceId)
    {
        if (interfaceId != InterfaceID.COWBOSS_SCOREBOARD)
        {
            return bossByBoard.get(interfaceId);
        }

        Widget personalKillCount = client.getWidget(InterfaceID.CowbossScoreboard.PKC_CONTENT);
        String killCountText = personalKillCount == null ? "" : Text.removeTags(personalKillCount.getText()).replace(",", "").trim();
        if (!ONLY_DIGITS.matcher(killCountText).matches())
        {
            return bossByBoard.get(interfaceId);
        }

        int killCount = Integer.parseInt(killCountText);
        boolean isBrutus = killCount == client.getVarpValue(VarPlayerID.TOTAL_COWBOSS_KILLS);
        boolean isDemonicBrutus = killCount == client.getVarpValue(VarPlayerID.TOTAL_COWBOSS_HARDMODE_KILLS);
        if (isBrutus != isDemonicBrutus)
        {
            return isBrutus ? BRUTUS : DEMONIC_BRUTUS;
        }
        return bossByBoard.get(interfaceId);
    }

    private static String boardContent(List<BoardText> boardTexts)
    {
        List<String> content = new ArrayList<>();
        for (BoardText boardText : boardTexts)
        {
            content.add(boardText.text + "|" + boardText.textColor + "|" + boardText.background);
        }
        Collections.sort(content);
        return String.join("\n", content);
    }

    private static class BoardText
    {
        private final String text;
        private final int x;
        private final int y;
        private final long appearedOnRead;
        private final int textColor;
        private final String background;

        private BoardText(String text, int x, int y, long appearedOnRead, int textColor, String background)
        {
            this.text = text;
            this.x = x;
            this.y = y;
            this.appearedOnRead = appearedOnRead;
            this.textColor = textColor;
            this.background = background;
        }
    }

    private static class WidgetAppearance
    {
        private final String text;
        private final long readNumber;

        private WidgetAppearance(String text, long readNumber)
        {
            this.text = text;
            this.readNumber = readNumber;
        }
    }

    private static String textOf(Widget widget)
    {
        return widget.getType() != WidgetType.TEXT || widget.getText() == null
            ? ""
            : Text.removeTags(LINE_BREAK.matcher(widget.getText()).replaceAll("\n")).replace((char) 0xA0, ' ').trim();
    }

    private static boolean canCover(Widget widget)
    {
        switch (widget.getType())
        {
            case WidgetType.LAYER:
                return true;
            case WidgetType.RECTANGLE:
                return widget.isFilled() && widget.getOpacity() == 0;
            case WidgetType.GRAPHIC:
                return widget.getSpriteId() >= 0 && widget.getOpacity() == 0;
            default:
                return false;
        }
    }

    private static String backgroundOf(Widget widget, List<Widget> covers)
    {
        Rectangle bounds = widget.getBounds();
        int centreX = bounds.x + bounds.width / 2;
        int centreY = bounds.y + bounds.height / 2;
        Widget background = null;
        long backgroundArea = Long.MAX_VALUE;
        for (Widget cover : covers)
        {
            Rectangle coverBounds = cover.getBounds();
            long area = (long) coverBounds.width * coverBounds.height;
            if (cover.getType() != WidgetType.LAYER && area < backgroundArea && coverBounds.contains(centreX, centreY))
            {
                background = cover;
                backgroundArea = area;
            }
        }

        if (background == null)
        {
            return null;
        }

        return background.getType() == WidgetType.GRAPHIC
            ? "s" + background.getSpriteId()
            : "r" + Integer.toHexString(background.getTextColor());
    }

    private static boolean isCoveredByNewerWidget(Widget widget, long appearedOnRead, List<Widget> covers,
        Map<Widget, WidgetAppearance> appearances)
    {
        Set<Widget> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Widget ancestor = widget.getParent(); ancestor != null; ancestor = ancestor.getParent())
        {
            ancestors.add(ancestor);
        }

        Rectangle bounds = widget.getBounds();
        long textArea = (long) bounds.width * bounds.height;
        int centreX = bounds.x + bounds.width / 2;
        int centreY = bounds.y + bounds.height / 2;
        for (Widget cover : covers)
        {
            Rectangle coverBounds = cover.getBounds();
            if (appearances.get(cover).readNumber > appearedOnRead
                && !ancestors.contains(cover)
                && (long) coverBounds.width * coverBounds.height >= COVER_TO_TEXT_AREA * textArea
                && coverBounds.contains(centreX, centreY))
            {
                return true;
            }
        }

        return false;
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
            if (ancestor.getScrollHeight() > 0)
            {
                return true;
            }

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
