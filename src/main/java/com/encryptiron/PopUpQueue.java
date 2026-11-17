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
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class PopUpQueue
{
    private static final int RESIZABLE_CLASSIC_LAYOUT = (161 << 16) | 13;
    private static final int RESIZABLE_MODERN_LAYOUT = (164 << 16) | 13;
    private static final int FIXED_CLASSIC_LAYOUT = 35913770;
    private static final int NOTIFICATION_DISPLAY_SCRIPT = 3343;

    @Value
    private static class PopUp
    {
        String title;
        String description;
    }

    private final Queue<PopUp> pending = new ArrayDeque<>();
    private boolean showing;

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
        showing = false;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.NOTIFICATION_DISPLAY)
        {
            showing = true;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() == InterfaceID.NOTIFICATION_DISPLAY)
        {
            showing = false;
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        showing = client.getWidget(InterfaceID.NotificationDisplay.UNIVERSE) != null;

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

    private void showNextIfIdle()
    {
        if (showing || pending.isEmpty())
        {
            return;
        }

        PopUp popUp = pending.poll();
        final int componentId = client.isResized()
                ? client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
                ? RESIZABLE_MODERN_LAYOUT
                : RESIZABLE_CLASSIC_LAYOUT
                : FIXED_CLASSIC_LAYOUT;

        client.openInterface(componentId, InterfaceID.NOTIFICATION_DISPLAY, WidgetModalMode.MODAL_CLICKTHROUGH);
        client.runScript(NOTIFICATION_DISPLAY_SCRIPT, popUp.getTitle(), popUp.getDescription(), -1);
        showing = true;
    }
}
