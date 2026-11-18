package com.encryptiron.chat;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
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
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * The plugin's side of the Valiance clan chat.
 *
 * Holds the server connection open while the player is in the Valiance clan
 * channel, as a member or as a guest. While it is open:
 * - every clan chat message the player themselves sends is reported to the
 *   server, which keeps the clan's chat log;
 * - lines the server sends (event drop broadcasts, anything staff send) are
 *   shown as they arrive, exactly as the server words them;
 * - on log-in, the last few lines from the log are shown, so the chat has
 *   some context before anything new is said. The log holds both kinds of
 *   line, and each is shown the same way it would have been live.
 *
 * Only the player's own messages are reported. Everyone else's are reported by
 * their own plugin, if they have one.
 *
 * A server line decides everything about itself: its chat type, the name and
 * sender (the channel name in brackets) and the message, tags such as col and
 * img included. Anything the server took from players, such as names, is
 * escaped there, so the text is used as it is. Every field but the message may
 * be left out. Without a chat type, or with one this client does not know, the
 * line is a broadcast in whichever Valiance channel the player is in.
 */
@Slf4j
@Singleton
public class ClanChatService
{
    public static final String CLAN_NAME = "Valiance";

    // How long the player can be out of the clan channel before the connection
    // is closed. Covers the moments during log-in and world hops when the
    // channel has not loaded yet, so those do not reconnect each time.
    private static final int TICKS_BEFORE_DISCONNECT = 10;

    // A message older than this when it reaches us was not just said: it is
    // being replayed, such as by the Chat History plugin after a log-in.
    private static final int MAX_MESSAGE_AGE_SECONDS = 10;

    // The game escapes a typed angle bracket as <lt> or <gt>, so any other tag
    // in a stored message did not come from the player typing it.
    private static final Pattern NON_ESCAPE_TAG = Pattern.compile("<(?!lt>|gt>)[^>]*>");
    private static final Pattern NON_NAME_CHARACTER = Pattern.compile("[^A-Za-z0-9 _-]");

    private static final String HISTORY_END_MESSAGE = "<col=7f7f7f>End of recent clan chat</col>";

    private static final String CHAT_MESSAGE_BUILDING = "chatMessageBuilding";
    private static final Pattern CLAN_NAME_TEXT = Pattern.compile(Pattern.quote(CLAN_NAME), Pattern.CASE_INSENSITIVE);

    @Inject
    private Client client;

    @Inject
    private ValianceSocket socket;

    @Inject
    private ValianceConfig config;

    @Inject
    private RankIcons rankIcons;

    private int ticksOutsideClan;
    // History is shown once per log-in, not on every reconnect or world hop.
    private boolean historyRequested;
    // Set while this adds lines of its own (history, or a line from the server),
    // so they are not taken for the player speaking. The lines themselves are
    // remembered too, in case their ChatMessage event arrives after
    // addChatMessage has returned.
    private boolean addingLines;
    private final Set<MessageNode> addedNodes = Collections.newSetFromMap(new WeakHashMap<>());
    // The server's lines, live or replayed, so their sender can be drawn
    private final Set<MessageNode> serverNodes = Collections.newSetFromMap(new WeakHashMap<>());

    public void startUp()
    {
        socket.setHandler("welcome", this::onWelcome);
        socket.setHandler("chat_message", this::onServerLine);
    }

    public void shutDown()
    {
        socket.stop();
        ticksOutsideClan = 0;
        historyRequested = false;
    }

    public static boolean isValiance(ClanChannel channel)
    {
        return channel != null && CLAN_NAME.equalsIgnoreCase(Text.removeTags(channel.getName()).trim());
    }

    /** The broadcast message type for whichever Valiance channel the player is in. */
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
        else if (socket.isRunning() && ++ticksOutsideClan >= TICKS_BEFORE_DISCONNECT)
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

        // Clan chat is only tracked on the main game, the same as everything
        // else the plugin sends.
        RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
        if (profileType != RuneScapeProfileType.STANDARD)
        {
            return;
        }

        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        // As a string: a JSON number would round a 64-bit hash on the server.
        hello.addProperty("account_hash", Long.toString(client.getAccountHash()));
        hello.addProperty("rsn", Text.sanitize(localPlayer.getName()));
        hello.addProperty("profile_type", profileType.name());
        // So this player's replayed messages carry their ironman icon.
        hello.addProperty("account_type", client.getVarbitValue(VarbitID.IRONMAN));

        // So the welcome only carries the ranks and icons if they have changed
        // since they were saved.
        if (rankIcons.getRanksVersion() != null)
        {
            hello.addProperty("ranks_version", rankIcons.getRanksVersion());
        }
        if (rankIcons.getIconsVersion() != null)
        {
            hello.addProperty("icons_version", rankIcons.getIconsVersion());
        }

        if (!historyRequested)
        {
            hello.addProperty("history", config.clanChatHistoryCount());
            hello.addProperty("history_after", newestClanChatMillis());
            historyRequested = true;
        }

        socket.start(hello);
    }

    /**
     * When the newest clan chat line already in the chatbox was sent, in epoch
     * ms, or 0 if there is none. History older than this is still on screen.
     */
    private long newestClanChatMillis()
    {
        long newestSeconds = 0;
        for (ChatMessageType type : new ChatMessageType[]{ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT})
        {
            ChatLineBuffer buffer = client.getChatLineMap().get(type.getType());
            if (buffer == null)
            {
                continue;
            }

            for (MessageNode node : buffer.getLines())
            {
                if (node != null)
                {
                    newestSeconds = Math.max(newestSeconds, node.getTimestamp());
                }
            }
        }

        // Timestamps here are whole seconds, so everything within the newest
        // one counts as already shown.
        return newestSeconds == 0 ? 0 : newestSeconds * 1000 + 999;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (addingLines || addedNodes.contains(event.getMessageNode()))
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

        if (Instant.now().getEpochSecond() - event.getTimestamp() > MAX_MESSAGE_AGE_SECONDS)
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
        // First, so the history below is drawn with up-to-date ranks.
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

        addingLines = true;
        try
        {
            for (JsonElement element : history)
            {
                if (!element.isJsonObject())
                {
                    continue;
                }

                JsonObject entry = element.getAsJsonObject();
                if ("chat_message".equals(ValianceSocket.getString(entry, "type")))
                {
                    addServerLine(entry);
                }
                else
                {
                    addHistoryMessage(chatType, entry);
                }
            }

            client.addChatMessage(broadcastType(), "", HISTORY_END_MESSAGE, CLAN_NAME);
        }
        finally
        {
            addingLines = false;
        }
    }

    private void onServerLine(JsonObject line)
    {
        addingLines = true;
        try
        {
            addServerLine(line);
        }
        finally
        {
            addingLines = false;
        }
    }

    /**
     * Adds a line the server sent, as the class comment describes. A replayed
     * one carries sent_at, and is shown at that time.
     */
    private void addServerLine(JsonObject line)
    {
        String message = ValianceSocket.getString(line, "message");
        if (message == null)
        {
            return;
        }

        String name = ValianceSocket.getString(line, "name");
        String sender = ValianceSocket.getString(line, "sender");

        MessageNode node = client.addChatMessage(
            serverLineType(ValianceSocket.getString(line, "chat_type")),
            name == null ? "" : name,
            message,
            sender == null ? CLAN_NAME : sender);
        if (node == null)
        {
            return;
        }

        addedNodes.add(node);
        serverNodes.add(node);

        JsonElement sentAt = line.get("sent_at");
        if (sentAt != null && sentAt.isJsonPrimitive())
        {
            node.setTimestamp((int) (sentAt.getAsLong() / 1000));
        }
    }

    private ChatMessageType serverLineType(String typeName)
    {
        if (typeName != null)
        {
            try
            {
                return ChatMessageType.valueOf(typeName);
            }
            catch (IllegalArgumentException ex)
            {
                log.debug("Unknown chat type {} from the Valiance server", typeName);
            }
        }

        return broadcastType();
    }

    /**
     * Draws a server line's sender where the game would put the channel's name.
     *
     * The game names a clan line by the channel it belongs to and ignores the
     * line's own sender, so without this every one reads [Valiance]. An empty
     * sender leaves the channel off. Lines whose type has no channel, such as
     * game messages, are left as they are.
     */
    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!CHAT_MESSAGE_BUILDING.equals(event.getEventName()))
        {
            return;
        }

        int[] intStack = client.getIntStack();
        int intStackSize = client.getIntStackSize();
        MessageNode node = client.getMessages().get(intStack[intStackSize - 1]);
        if (node == null || !serverNodes.contains(node))
        {
            return;
        }

        Object[] objectStack = client.getObjectStack();
        int objectStackSize = client.getObjectStackSize();
        Object channel = objectStack[objectStackSize - 4];
        if (!(channel instanceof String) || ((String) channel).isEmpty())
        {
            return;
        }

        String sender = node.getSender() == null ? "" : node.getSender();
        objectStack[objectStackSize - 4] = sender.isEmpty()
            ? ""
            : CLAN_NAME_TEXT.matcher((String) channel).replaceAll(Matcher.quoteReplacement(sender));
    }

    /**
     * The icon the game shows beside an account's name, by the game's ironman
     * setting, or nothing for a regular account or an unknown one.
     */
    private static String accountIconTag(JsonElement accountType)
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

    private void addHistoryMessage(ChatMessageType chatType, JsonObject entry)
    {
        String name = ValianceSocket.getString(entry, "rsn");
        String message = ValianceSocket.getString(entry, "message");
        JsonElement sentAt = entry.get("sent_at");
        if (name == null || message == null || sentAt == null || !sentAt.isJsonPrimitive())
        {
            return;
        }

        // The server already strips these; stripped again because whatever is
        // added here is drawn straight into the chatbox.
        String cleanName = NON_NAME_CHARACTER.matcher(Text.removeTags(name)).replaceAll("");
        String cleanMessage = NON_ESCAPE_TAG.matcher(message).replaceAll("");
        if (cleanName.isEmpty() || cleanMessage.isEmpty())
        {
            return;
        }

        // The game puts the account's icon into the name of a live message, so
        // a replayed one is given the same.
        String nameWithIcon = accountIconTag(entry.get("account_type")) + cleanName;

        MessageNode node = client.addChatMessage(chatType, nameWithIcon, cleanMessage, CLAN_NAME);
        if (node != null)
        {
            addedNodes.add(node);
            node.setTimestamp((int) (sentAt.getAsLong() / 1000));
        }
    }
}
