package com.encryptiron.runs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.encryptiron.rest.SendAdventureLog;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

public class AdventureLogReader
{
    private static final Pattern ADVENTURE_LOG_OWNER = Pattern.compile("The Exploits of (.+)");
    private static final String PERSONAL_BEST_LINE_START = "Fastest ";
    private static final int JOURNAL_SCROLL_FINALISE = 6845;

    @Inject
    private Client client;

    @Inject
    private SendAdventureLog sendAdventureLog;

    private boolean adventureLogMenuLoaded;
    private boolean journalScrollFinalised;
    private String adventureLogOwner;

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded)
    {
        switch (widgetLoaded.getGroupId())
        {
            case InterfaceID.MENU:
            case InterfaceID.MENU_NEW:
                adventureLogMenuLoaded = true;
                break;
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired)
    {
        if (scriptPreFired.getScriptId() == JOURNAL_SCROLL_FINALISE)
        {
            journalScrollFinalised = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        if (adventureLogMenuLoaded)
        {
            adventureLogMenuLoaded = false;
            adventureLogOwner = readAdventureLogOwner();
        }

        if (journalScrollFinalised)
        {
            journalScrollFinalised = false;
            if (isLocalPlayer(adventureLogOwner) && isLocalPlayer(readJournalScrollTitle()))
            {
                sendPersonalBestCounters();
            }
        }
    }

    private void sendPersonalBestCounters()
    {
        Widget textLayer = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);
        if (textLayer == null)
        {
            return;
        }

        List<String> lines = new ArrayList<>();
        boolean hasPersonalBest = false;
        for (Widget line : textLayer.getStaticChildren())
        {
            String text = line.getText() == null ? "" : line.getText();
            lines.add(text);
            hasPersonalBest |= Text.removeTags(text).startsWith(PERSONAL_BEST_LINE_START);
        }

        if (hasPersonalBest)
        {
            sendAdventureLog.send(lines);
        }
    }

    private String readJournalScrollTitle()
    {
        Widget title = client.getWidget(InterfaceID.Journalscroll.TITLE);
        return title == null || title.getText() == null ? null : Text.removeTags(title.getText()).trim();
    }

    private String readAdventureLogOwner()
    {
        String owner = readAdventureLogOwner(InterfaceID.MenuNew.TITLE);
        return owner != null ? owner : readAdventureLogOwner(InterfaceID.Menu.LJ_LAYER2);
    }

    private String readAdventureLogOwner(int componentId)
    {
        Widget title = client.getWidget(componentId);
        if (title == null)
        {
            return null;
        }

        String owner = matchAdventureLogOwner(title.getText());
        if (owner != null || title.getChildren() == null)
        {
            return owner;
        }

        for (Widget child : title.getChildren())
        {
            owner = child == null ? null : matchAdventureLogOwner(child.getText());
            if (owner != null)
            {
                return owner;
            }
        }

        return null;
    }

    private static String matchAdventureLogOwner(String text)
    {
        if (text == null)
        {
            return null;
        }

        Matcher matcher = ADVENTURE_LOG_OWNER.matcher(Text.removeTags(text));
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isLocalPlayer(String playerName)
    {
        Player localPlayer = client.getLocalPlayer();
        return playerName != null && localPlayer != null && localPlayer.getName() != null
            && Text.toJagexName(playerName).equalsIgnoreCase(Text.toJagexName(localPlayer.getName()));
    }
}
