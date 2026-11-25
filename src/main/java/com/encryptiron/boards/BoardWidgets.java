package com.encryptiron.boards;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

class BoardWidgets
{
    private BoardWidgets()
    {
    }

    static boolean isOpen(Client client, int interfaceId)
    {
        return client.getWidget(interfaceId, 0) != null;
    }

    static String textOf(Client client, int componentId)
    {
        Widget widget = client.getWidget(componentId);
        return widget == null ? "" : plainText(widget);
    }

    static String visibleTextOf(Client client, int componentId)
    {
        Widget widget = client.getWidget(componentId);
        return widget == null || widget.isHidden() ? "" : plainText(widget);
    }

    private static String plainText(Widget widget)
    {
        return widget.getText() == null ? "" : Text.removeTags(widget.getText()).replace('\u00a0', ' ').trim();
    }
}
