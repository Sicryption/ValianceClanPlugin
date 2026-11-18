package com.encryptiron.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IconID;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class ClanChatService
{
    public static final String CLAN_NAME = "Valiance";

    private static final int TICKS_OUTSIDE_CLAN_BEFORE_DISCONNECT = 10;

    private static final int MAX_REPORTED_MESSAGE_AGE_SECONDS = 10;

    private static final Pattern NON_ESCAPE_TAG = Pattern.compile("<(?!lt>|gt>)[^>]*>");
    private static final Pattern NON_NAME_CHARACTER = Pattern.compile("[^A-Za-z0-9 _-]");

    private static final String HISTORY_END_MESSAGE = "<col=7f7f7f>End of recent clan chat</col>";

    private static final int MAX_SECONDS_BETWEEN_SAME_LINE = 60;

    @Inject
    private Client client;

    @Inject
    private ValianceSocket socket;

    @Inject
    private ValianceConfig config;

    @Inject
    private RankIcons rankIcons;

    private int ticksOutsideClan;
    private boolean historyRequestedThisLogin;
    private boolean addingOwnLines;
    private final Set<MessageNode> ownLineNodes = Collections.newSetFromMap(new WeakHashMap<>());

    public void startUp()
    {
        socket.setHandler("welcome", this::onWelcome);
        socket.setHandler("chat_message", this::onServerLine);
    }

    public void shutDown()
    {
        socket.stop();
        ticksOutsideClan = 0;
        historyRequestedThisLogin = false;
    }

    public static boolean isValiance(ClanChannel channel)
    {
        return channel != null && CLAN_NAME.equalsIgnoreCase(Text.removeTags(channel.getName()).trim());
    }

    public ChatMessageType broadcastType()
    {
        return isValiance(client.getClanChannel())
            ? ChatMessageType.CLAN_MESSAGE
            : ChatMessageType.CLAN_GUEST_MESSAGE;
    }

    private boolean isInValianceChannel()
    {
        return isValiance(client.getClanChannel()) || isValiance(client.getGuestClanChannel());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            shutDown();
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (isInValianceChannel())
        {
            ticksOutsideClan = 0;
            if (!socket.isRunning())
            {
                startSocket();
            }
        }
        else if (socket.isRunning() && ++ticksOutsideClan >= TICKS_OUTSIDE_CLAN_BEFORE_DISCONNECT)
        {
            socket.stop();
        }
    }

    private void startSocket()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getName() == null)
        {
            return;
        }

        RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
        if (profileType != RuneScapeProfileType.STANDARD)
        {
            return;
        }

        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("account_hash", Long.toString(client.getAccountHash()));
        hello.addProperty("rsn", Text.sanitize(localPlayer.getName()));
        hello.addProperty("profile_type", profileType.name());
        hello.addProperty("account_type", client.getVarbitValue(VarbitID.IRONMAN));

        if (rankIcons.getRanksVersion() != null)
        {
            hello.addProperty("ranks_version", rankIcons.getRanksVersion());
        }
        if (rankIcons.getIconsVersion() != null)
        {
            hello.addProperty("icons_version", rankIcons.getIconsVersion());
        }

        if (!historyRequestedThisLogin)
        {
            hello.addProperty("history", config.clanChatHistoryCount());
            historyRequestedThisLogin = true;
        }

        socket.start(hello);
    }

    private Map<String, List<Integer>> chatboxLineTimesByKey()
    {
        Map<String, List<Integer>> lines = new HashMap<>();
        for (ChatLineBuffer buffer : client.getChatLineMap().values())
        {
            if (buffer == null)
            {
                continue;
            }

            for (MessageNode node : buffer.getLines())
            {
                if (node != null)
                {
                    lines.computeIfAbsent(plainNameAndMessage(node.getName(), node.getValue()), key -> new ArrayList<>())
                        .add(node.getTimestamp());
                }
            }
        }
        return lines;
    }

    private static String plainNameAndMessage(String name, String message)
    {
        String plainName = name == null ? "" : Text.sanitize(name).toLowerCase(Locale.ROOT);
        String plainMessage = message == null ? "" : Text.removeTags(message);
        return plainName + "\n" + plainMessage;
    }

    private static boolean isAlreadyInChatbox(Map<String, List<Integer>> chatbox, String name, String message, long sentAtMillis)
    {
        List<Integer> times = chatbox.get(plainNameAndMessage(name, message));
        if (times == null)
        {
            return false;
        }

        long sentAt = sentAtMillis / 1000;
        for (int time : times)
        {
            if (Math.abs(time - sentAt) <= MAX_SECONDS_BETWEEN_SAME_LINE)
            {
                return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (addingOwnLines || ownLineNodes.contains(event.getMessageNode()))
        {
            return;
        }

        ClanChannel channel;
        if (event.getType() == ChatMessageType.CLAN_CHAT)
        {
            channel = client.getClanChannel();
        }
        else if (event.getType() == ChatMessageType.CLAN_GUEST_CHAT)
        {
            channel = client.getGuestClanChannel();
        }
        else
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        if (!isValiance(channel) || localPlayer == null || localPlayer.getName() == null)
        {
            return;
        }

        if (!Text.sanitize(event.getName()).equalsIgnoreCase(Text.sanitize(localPlayer.getName())))
        {
            return;
        }

        if (Instant.now().getEpochSecond() - event.getTimestamp() > MAX_REPORTED_MESSAGE_AGE_SECONDS)
        {
            return;
        }

        JsonObject chat = new JsonObject();
        chat.addProperty("type", "chat");
        chat.addProperty("message", event.getMessage());
        socket.send(chat);
    }

    private void onWelcome(JsonObject welcome)
    {
        rankIcons.onWelcome(welcome);

        JsonElement historyElement = welcome.get("history");
        if (historyElement == null || !historyElement.isJsonArray())
        {
            return;
        }

        JsonArray history = historyElement.getAsJsonArray();
        if (history.size() == 0 || !isInValianceChannel())
        {
            return;
        }

        ChatMessageType chatType = isValiance(client.getClanChannel())
            ? ChatMessageType.CLAN_CHAT
            : ChatMessageType.CLAN_GUEST_CHAT;

        Map<String, List<Integer>> chatbox = chatboxLineTimesByKey();
        boolean added = false;

        addingOwnLines = true;
        try
        {
            for (JsonElement element : history)
            {
                if (!element.isJsonObject())
                {
                    continue;
                }

                JsonObject entry = element.getAsJsonObject();
                boolean serverLine = "chat_message".equals(ValianceSocket.getString(entry, "type"));
                JsonElement sentAt = entry.get("sent_at");
                if (sentAt != null && sentAt.isJsonPrimitive() && isAlreadyInChatbox(chatbox,
                    serverLine ? "" : ValianceSocket.getString(entry, "rsn"),
                    ValianceSocket.getString(entry, "message"),
                    sentAt.getAsLong()))
                {
                    continue;
                }

                if (serverLine)
                {
                    addServerLine(entry);
                }
                else
                {
                    addReplayedPlayerLine(chatType, entry);
                }
                added = true;
            }

            if (added)
            {
                client.addChatMessage(broadcastType(), "", HISTORY_END_MESSAGE, CLAN_NAME);
            }
        }
        finally
        {
            addingOwnLines = false;
        }
    }

    private void onServerLine(JsonObject line)
    {
        addingOwnLines = true;
        try
        {
            addServerLine(line);
        }
        finally
        {
            addingOwnLines = false;
        }
    }

    private void addServerLine(JsonObject line)
    {
        String message = ValianceSocket.getString(line, "message");
        if (message == null)
        {
            return;
        }

        MessageNode node = client.addChatMessage(broadcastType(), "", message, CLAN_NAME);
        if (node == null)
        {
            return;
        }

        ownLineNodes.add(node);

        JsonElement sentAt = line.get("sent_at");
        if (sentAt != null && sentAt.isJsonPrimitive())
        {
            node.setTimestamp((int) (sentAt.getAsLong() / 1000));
        }
    }

    private static String accountTypeIconTag(JsonElement accountType)
    {
        if (accountType == null || !accountType.isJsonPrimitive())
        {
            return "";
        }

        IconID icon;
        switch (accountType.getAsInt())
        {
            case 1:
                icon = IconID.IRONMAN;
                break;
            case 2:
                icon = IconID.ULTIMATE_IRONMAN;
                break;
            case 3:
                icon = IconID.HARDCORE_IRONMAN;
                break;
            case 4:
                icon = IconID.GROUP_IRONMAN;
                break;
            case 5:
                icon = IconID.HARDCORE_GROUP_IRONMAN;
                break;
            case 6:
                icon = IconID.UNRANKED_GROUP_IRONMAN;
                break;
            default:
                return "";
        }
        return "<img=" + icon.getIndex() + ">";
    }

    private void addReplayedPlayerLine(ChatMessageType chatType, JsonObject entry)
    {
        String name = ValianceSocket.getString(entry, "rsn");
        String message = ValianceSocket.getString(entry, "message");
        JsonElement sentAt = entry.get("sent_at");
        if (name == null || message == null || sentAt == null || !sentAt.isJsonPrimitive())
        {
            return;
        }

        String cleanName = NON_NAME_CHARACTER.matcher(Text.removeTags(name)).replaceAll("");
        String cleanMessage = NON_ESCAPE_TAG.matcher(message).replaceAll("");
        if (cleanName.isEmpty() || cleanMessage.isEmpty())
        {
            return;
        }

        String nameWithIcon = accountTypeIconTag(entry.get("account_type")) + cleanName;

        MessageNode node = client.addChatMessage(chatType, nameWithIcon, cleanMessage, CLAN_NAME);
        if (node != null)
        {
            ownLineNodes.add(node);
            node.setTimestamp((int) (sentAt.getAsLong() / 1000));
        }
    }
}
