package com.encryptiron.boards;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.encryptiron.rest.SendBoardTimes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;

class FakeGame
{
    final Map<Integer, String> textByComponent = new HashMap<>();
    final Set<Integer> hiddenComponents = new HashSet<>();
    final Map<Integer, Integer> varbits = new HashMap<>();
    final Map<Integer, Integer> varps = new HashMap<>();
    final Set<Integer> openInterfaces = new HashSet<>();
    final List<JsonArray> sent = new ArrayList<>();
    int tick;

    final Client client = fake(Client.class, (proxy, method, arguments) -> {
        switch (method.getName())
        {
            case "getWidget":
                return arguments.length == 2
                    ? (openInterfaces.contains(arguments[0]) ? widget("", false) : null)
                    : widgetOf((Integer) arguments[0]);
            case "getVarbitValue":
                return varbits.getOrDefault(arguments[0], 0);
            case "getVarpValue":
                return varps.getOrDefault(arguments[0], 0);
            case "getTickCount":
                return tick;
            default:
                throw new UnsupportedOperationException(method.getName());
        }
    });

    final BoardTimesSender boardTimesSender = new BoardTimesSender(new SendBoardTimes()
    {
        @Override
        public void send(JsonArray times)
        {
            sent.add(times);
        }
    });

    WidgetLoaded open(int interfaceId)
    {
        openInterfaces.add(interfaceId);
        WidgetLoaded widgetLoaded = new WidgetLoaded();
        widgetLoaded.setGroupId(interfaceId);
        return widgetLoaded;
    }

    static ScriptPreFired script(Object... arguments)
    {
        ScriptPreFired scriptPreFired = new ScriptPreFired((Integer) arguments[0]);
        scriptPreFired.setScriptEvent(fake(ScriptEvent.class, (proxy, method, unused) -> arguments));
        return scriptPreFired;
    }

    static MenuOptionClicked click(MenuAction menuAction, int objectId)
    {
        return new MenuOptionClicked(fake(MenuEntry.class, (proxy, method, unused) ->
            method.getName().equals("getType") ? menuAction : objectId));
    }

    List<String> sentTimes()
    {
        List<String> times = new ArrayList<>();
        for (JsonArray sentArray : sent)
        {
            for (JsonElement element : sentArray)
            {
                JsonObject time = element.getAsJsonObject();
                times.add(time.get("content").getAsString() + "|" + time.get("team_size").getAsString() + "|"
                    + time.get("split").getAsString() + "|"
                    + (time.has("ticks") ? time.get("ticks").getAsInt() + " ticks" : time.get("time").getAsString()));
            }
        }
        return times;
    }

    private Widget widgetOf(int componentId)
    {
        return openInterfaces.contains(componentId >>> 16)
            ? widget(textByComponent.getOrDefault(componentId, "-"), hiddenComponents.contains(componentId))
            : null;
    }

    private static Widget widget(String text, boolean hidden)
    {
        return fake(Widget.class, (proxy, method, arguments) -> method.getName().equals("isHidden") ? (Object) hidden : text);
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
