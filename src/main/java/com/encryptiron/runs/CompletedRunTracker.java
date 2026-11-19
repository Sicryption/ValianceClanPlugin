package com.encryptiron.runs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import com.encryptiron.rest.OnBossKilled;
import com.encryptiron.rest.SendCompletedRun;
import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

public class CompletedRunTracker
{
    private static final long SIGHTING_RETENTION_MS = TimeUnit.HOURS.toMillis(6);
    private static final long RUN_WINDOW_MARGIN_MS = 6000;
    private static final int KILL_COUNT_VARP_TICKS_AROUND_LINE = 10;
    private static final int MOST_TICKS_TO_WAIT_FOR_KILL_COUNT_VARP = 3;
    private static final int TICKS_BETWEEN_PRUNES = 100;

    private static final Set<ChatMessageType> GAME_ONLY_MESSAGE_TYPES = EnumSet.of(
        ChatMessageType.GAMEMESSAGE,
        ChatMessageType.SPAM,
        ChatMessageType.FRIENDSCHATNOTIFICATION);

    private static final Pattern TIME = Pattern.compile("(?<![0-9:.])\\d+(?::\\d{2}){1,2}(?:\\.\\d{1,2})?(?![0-9:])");
    private static final Pattern KILL_COUNT = Pattern.compile("Your (?:completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?.+?(?:</col>)? (?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?is: ?(?:<col=[0-9a-f]{6}>|@.+?@)(?<count>[0-9,]+)</col>");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9 _-]{1,12}");
    private static final Pattern ONLY_DIGITS = Pattern.compile("[0-9]+");

    private static final int[] PARTY_NAME_VARCS = {
        VarClientID.TOB_CLIENT_NAME0,
        VarClientID.TOB_CLIENT_NAME1,
        VarClientID.TOB_CLIENT_NAME2,
        VarClientID.TOB_CLIENT_NAME3,
        VarClientID.TOB_CLIENT_NAME4,
        VarClientID.TOA_CLIENT_NAME0,
        VarClientID.TOA_CLIENT_NAME1,
        VarClientID.TOA_CLIENT_NAME2,
        VarClientID.TOA_CLIENT_NAME3,
        VarClientID.TOA_CLIENT_NAME4,
        VarClientID.TOA_CLIENT_NAME5,
        VarClientID.TOA_CLIENT_NAME6,
        VarClientID.TOA_CLIENT_NAME7,
    };

    @Inject
    private Client client;

    @Inject
    private SendCompletedRun sendCompletedRun;

    @Inject
    private SetupSnapshots setupSnapshots;

    private final Map<String, PlayerSightings> sightingsByPlayerName = new HashMap<>();
    private final Deque<GameLine> gameLines = new ArrayDeque<>();
    private final Deque<KillCountChange> killCountChanges = new ArrayDeque<>();
    private PendingRun pendingRun;

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        int tick = client.getTickCount();
        long nowMs = System.currentTimeMillis();

        recordSightings(nowMs);
        setupSnapshots.record(nowMs);

        if (pendingRun != null && tick > pendingRun.killCountLine.tick
            && (killCountVarpConfirmsPendingRun() || tick >= pendingRun.killCountLine.tick + MOST_TICKS_TO_WAIT_FOR_KILL_COUNT_VARP))
        {
            sendPendingRun();
        }

        if (tick % TICKS_BETWEEN_PRUNES == 0)
        {
            dropSightingsOlderThan(nowMs - SIGHTING_RETENTION_MS);
        }
    }

    @Subscribe(priority = 1)
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        {
            sendPendingRun();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        if (!GAME_ONLY_MESSAGE_TYPES.contains(chatMessage.getType())
            || !Strings.isNullOrEmpty(chatMessage.getName())
            || chatMessage.getSender() != null)
        {
            return;
        }

        String message = chatMessage.getMessage();
        Matcher killCountMatcher = KILL_COUNT.matcher(message);
        boolean isKillCountLine = killCountMatcher.find();
        if (!isKillCountLine && !TIME.matcher(message).find())
        {
            return;
        }

        GameLine gameLine = new GameLine(client.getTickCount(), System.currentTimeMillis(), message);
        gameLines.addLast(gameLine);

        if (isKillCountLine)
        {
            if (pendingRun != null && pendingRun.killCountLine.tick != gameLine.tick)
            {
                sendPendingRun();
            }

            pendingRun = new PendingRun(
                gameLine,
                Integer.parseInt(killCountMatcher.group("count").replace(",", "")),
                client.getAccountHash(),
                client.getWorld(),
                client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL),
                localPlayerName());
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        if (varbitChanged.getVarbitId() != -1 || client.getLocalPlayer() == null)
        {
            return;
        }

        String killCountName = OnBossKilled.bossNameForVarp(varbitChanged.getVarpId());
        if (killCountName == null)
        {
            return;
        }

        killCountChanges.addLast(new KillCountChange(
            client.getTickCount(),
            System.currentTimeMillis(),
            killCountName,
            client.getVarpValue(varbitChanged.getVarpId())));
    }

    private void recordSightings(long nowMs)
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        for (Player player : client.getTopLevelWorldView().players())
        {
            String playerName = cleanName(player.getName());
            if (playerName == null)
            {
                continue;
            }

            PlayerSightings sightings = sightingsOf(playerName);
            sightings.seenPresent(nowMs);

            Actor target = player.getInteracting();
            String targetName = target instanceof NPC ? cleanName(target.getName()) : null;
            if (targetName != null)
            {
                sightings.seenTargeting(targetName, nowMs);
            }
        }

        for (int partyNameVarc : PARTY_NAME_VARCS)
        {
            String partyMemberName = cleanName(client.getVarcStrValue(partyNameVarc));
            if (partyMemberName != null)
            {
                sightingsOf(partyMemberName).seenInParty(nowMs);
            }
        }

        recordChambersPartyPanel(nowMs);
    }

    private void recordChambersPartyPanel(long nowMs)
    {
        Widget partyList = client.getWidget(InterfaceID.RaidsSidepanel.LIST);
        if (partyList == null || partyList.isHidden())
        {
            return;
        }

        for (Widget row : partyList.getDynamicChildren())
        {
            String text = cleanName(row.getText());
            if (text != null && PLAYER_NAME.matcher(text).matches() && !ONLY_DIGITS.matcher(text).matches())
            {
                sightingsOf(text).seenInParty(nowMs);
            }
        }
    }

    private boolean killCountVarpConfirmsPendingRun()
    {
        int fromTick = pendingRun.killCountLine.tick - KILL_COUNT_VARP_TICKS_AROUND_LINE;
        return killCountChanges.stream()
            .anyMatch(change -> change.tick >= fromTick && change.count == pendingRun.killCount);
    }

    private void sendPendingRun()
    {
        if (pendingRun == null)
        {
            return;
        }

        PendingRun run = pendingRun;
        pendingRun = null;

        GameLine killCountLine = run.killCountLine;
        long longestCompletionTimeMs = 0;
        for (GameLine gameLine : gameLines)
        {
            if (gameLine.tick == killCountLine.tick)
            {
                longestCompletionTimeMs = Math.max(longestCompletionTimeMs, longestTimeIn(gameLine.message));
            }
        }

        if (longestCompletionTimeMs == 0)
        {
            return;
        }

        long endMs = killCountLine.timeMs;
        long startMs = endMs - longestCompletionTimeMs - RUN_WINDOW_MARGIN_MS;

        JsonArray lines = new JsonArray();
        for (GameLine gameLine : gameLines)
        {
            if (gameLine.timeMs >= startMs && gameLine.tick <= killCountLine.tick)
            {
                JsonObject line = new JsonObject();
                line.addProperty("offset_ms", gameLine.timeMs - endMs);
                line.addProperty("tick_offset", gameLine.tick - killCountLine.tick);
                line.addProperty("message", gameLine.message);
                lines.add(line);
            }
        }

        JsonArray killCounts = new JsonArray();
        for (KillCountChange change : killCountChanges)
        {
            if (change.tick >= killCountLine.tick - KILL_COUNT_VARP_TICKS_AROUND_LINE)
            {
                JsonObject killCount = new JsonObject();
                killCount.addProperty("name", change.name);
                killCount.addProperty("count", change.count);
                killCount.addProperty("tick_offset", change.tick - killCountLine.tick);
                killCounts.add(killCount);
            }
        }

        JsonArray players = new JsonArray();
        sightingsByPlayerName.forEach((playerName, sightings) -> {
            JsonObject player = sightings.toJsonWithin(playerName, startMs, endMs);
            if (player != null)
            {
                players.add(player);
            }
        });

        JsonObject varbits = new JsonObject();
        varbits.addProperty("toa_raid_level", run.toaRaidLevel);

        JsonObject completedRun = new JsonObject();
        completedRun.addProperty("account_hash", String.valueOf(run.accountHash));
        completedRun.addProperty("local_player", run.localPlayerName);
        completedRun.addProperty("world", run.world);
        completedRun.addProperty("kill_count", run.killCount);
        completedRun.add("lines", lines);
        completedRun.add("kill_count_changes", killCounts);
        completedRun.add("varbits", varbits);
        completedRun.add("players", players);
        completedRun.add("setup", setupSnapshots.setupAt(endMs - longestCompletionTimeMs));

        sendCompletedRun.send(completedRun);
    }

    private void dropSightingsOlderThan(long cutoffMs)
    {
        while (!gameLines.isEmpty() && gameLines.peekFirst().timeMs < cutoffMs)
        {
            gameLines.removeFirst();
        }

        while (!killCountChanges.isEmpty() && killCountChanges.peekFirst().timeMs < cutoffMs)
        {
            killCountChanges.removeFirst();
        }

        setupSnapshots.dropOlderThan(cutoffMs);
        sightingsByPlayerName.values().forEach(sightings -> sightings.dropEndedBefore(cutoffMs));
        sightingsByPlayerName.values().removeIf(PlayerSightings::isEmpty);
    }

    private PlayerSightings sightingsOf(String playerName)
    {
        return sightingsByPlayerName.computeIfAbsent(playerName, name -> new PlayerSightings());
    }

    private String localPlayerName()
    {
        Player localPlayer = client.getLocalPlayer();
        return localPlayer == null ? null : cleanName(localPlayer.getName());
    }

    private static String cleanName(String name)
    {
        if (name == null)
        {
            return null;
        }

        String cleaned = Text.removeTags(name).replace((char) 0xA0, ' ').trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    static long longestTimeIn(String message)
    {
        long longestMs = 0;
        Matcher timeMatcher = TIME.matcher(message);
        while (timeMatcher.find())
        {
            longestMs = Math.max(longestMs, timeToMs(timeMatcher.group()));
        }

        return longestMs;
    }

    static long timeToMs(String time)
    {
        String[] parts = time.split(":");
        double seconds = Double.parseDouble(parts[parts.length - 1]);
        long minutes = Long.parseLong(parts[parts.length - 2]);
        long hours = parts.length == 3 ? Long.parseLong(parts[0]) : 0;
        return Math.round(((hours * 60 + minutes) * 60 + seconds) * 1000);
    }

    private static class GameLine
    {
        private final int tick;
        private final long timeMs;
        private final String message;

        private GameLine(int tick, long timeMs, String message)
        {
            this.tick = tick;
            this.timeMs = timeMs;
            this.message = message;
        }
    }

    private static class KillCountChange
    {
        private final int tick;
        private final long timeMs;
        private final String name;
        private final int count;

        private KillCountChange(int tick, long timeMs, String name, int count)
        {
            this.tick = tick;
            this.timeMs = timeMs;
            this.name = name;
            this.count = count;
        }
    }

    private static class PendingRun
    {
        private final GameLine killCountLine;
        private final int killCount;
        private final long accountHash;
        private final int world;
        private final int toaRaidLevel;
        private final String localPlayerName;

        private PendingRun(GameLine killCountLine, int killCount, long accountHash, int world, int toaRaidLevel, String localPlayerName)
        {
            this.killCountLine = killCountLine;
            this.killCount = killCount;
            this.accountHash = accountHash;
            this.world = world;
            this.toaRaidLevel = toaRaidLevel;
            this.localPlayerName = localPlayerName;
        }
    }
}
