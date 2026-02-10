package com.encryptiron;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

import net.runelite.client.ui.overlay.OverlayPanel;

import com.encryptiron.rest.MessageHeaderData;
import com.google.inject.Inject;

import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.components.TitleComponent;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class RSNOverlay extends OverlayPanel
{
    private final ValianceClanPlugin plugin;

    @Inject
    public RSNOverlay(ValianceClanPlugin plugin)
    {
        super(plugin);

        this.plugin = plugin;
        addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Valiance RSN Overlay");
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        String text = MessageHeaderData.getPlayerName() == null ? "No RSN set" : MessageHeaderData.getPlayerName();

        panelComponent.getChildren().add(TitleComponent.builder()
            .text(text)
            .color(Color.GREEN)
            .build());

        return super.render(graphics);
    }
}
