package com.encryptiron;

import java.util.ArrayDeque;
import java.util.Queue;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class PopUpQueue
{
    private static final int RESIZABLE_CLASSIC_LAYOUT = (161 << 16) | 13;
    private static final int RESIZABLE_MODERN_LAYOUT = (164 << 16) | 13;
    private static final int FIXED_CLASSIC_LAYOUT = 35913770;
    private static final int NOTIFICATION_DISPLAY_SCRIPT = 3343;

    private static final int EMPTY_TICKS_BEFORE_NEXT = 2;
    private static final int MAX_TICKS_TO_APPEAR = 5;

    private static final int[] POP_UP_BOXES = {
        InterfaceID.NotificationDisplay.CONTAINER,
        InterfaceID.NotificationDisplay.CONTENT,
    };

    @Value
    private static class PopUp
    {
        String title;
        String description;
    }

    private final Queue<PopUp> pending = new ArrayDeque<>();
    private int emptyTicks = EMPTY_TICKS_BEFORE_NEXT;
    private boolean waitingToAppear;
    private int ticksWaitedToAppear;

    @Inject
    private Client client;

    public void add(String title, String description)
    {
        pending.add(new PopUp(title, description));
        showNextIfIdle();
    }

    public void clear()
    {
        pending.clear();
        emptyTicks = EMPTY_TICKS_BEFORE_NEXT;
        waitingToAppear = false;
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        if (isOnScreen())
        {
            emptyTicks = 0;
            waitingToAppear = false;
        }
        else if (waitingToAppear)
        {
            if (++ticksWaitedToAppear >= MAX_TICKS_TO_APPEAR)
            {
                waitingToAppear = false;
                emptyTicks = EMPTY_TICKS_BEFORE_NEXT;
            }
        }
        else
        {
            emptyTicks++;
        }

        showNextIfIdle();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        {
            clear();
        }
    }

    private boolean isOnScreen()
    {
        for (int boxId : POP_UP_BOXES)
        {
            Widget box = client.getWidget(boxId);
            if (box != null && !box.isHidden() && box.getWidth() > 0 && box.getHeight() > 0)
            {
                return true;
            }
        }
        return false;
    }

    private void showNextIfIdle()
    {
        if (pending.isEmpty() || waitingToAppear || emptyTicks < EMPTY_TICKS_BEFORE_NEXT || isOnScreen())
        {
            return;
        }

        PopUp popUp = pending.poll();

        if (client.getWidget(InterfaceID.NotificationDisplay.UNIVERSE) == null)
        {
            final int componentId = client.isResized()
                    ? client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
                    ? RESIZABLE_MODERN_LAYOUT
                    : RESIZABLE_CLASSIC_LAYOUT
                    : FIXED_CLASSIC_LAYOUT;

            client.openInterface(componentId, InterfaceID.NOTIFICATION_DISPLAY, WidgetModalMode.MODAL_CLICKTHROUGH);
        }

        client.runScript(NOTIFICATION_DISPLAY_SCRIPT, popUp.getTitle(), popUp.getDescription(), -1);
        waitingToAppear = true;
        ticksWaitedToAppear = 0;
        emptyTicks = 0;
    }
}
