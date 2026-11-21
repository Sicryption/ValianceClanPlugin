package com.encryptiron.runs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
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
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
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
    private static final long LONGEST_COLOSSEUM_RUN_MS = TimeUnit.HOURS.toMillis(3);
    private static final Pattern COLOSSEUM_WAVE_COMPLETE = Pattern.compile("(?i)^(?:<[^>]*>)*wave (\\d+) completed! wave duration:");
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
    private static final Set<String> TIMED_FROM_FIRST_DAMAGE = Set.of(
        "Corporeal Beast",
        "Giant Mole",
        "Kalphite Queen");
    private static final Set<String> TIMED_FROM_SPAWN = Set.of(
        "King Black Dragon",
        "General Graardor",
        "Commander Zilyana",
        "Kree'arra",
        "K'ril Tsutsaroth");
    private static final long MS_PER_TICK = 600;
    private static final int LONGEST_PLUGIN_TIMED_FIGHT_TICKS = 3000;
    private static final int NO_KILL_COUNT = -1;
    private static final Pattern PERSONAL_BEST = Pattern.compile("(?i)personal best");
    private static final Pattern TARGET_TIME = Pattern.compile("(?i)target time");
    private static final Pattern ROOM_DURATION = Pattern.compile("(?i)duration:\\s*(?:<[^>]*>)*\\s*(?<time>\\d+(?::\\d{2}){1,2}(?:\\.\\d{1,2})?)");
    private static final Pattern RAID_ROOM_COMPLETE = Pattern.compile("(?i)^(?:<[^>]*>)*(?:wave '[^']+'.* complete!|challenge complete: |wave \\d+ completed! wave duration:)");
    private static final Pattern CHAMBERS_ROOM_COMPLETE = Pattern.compile("(?i)^(?:<[^>]*>|@[a-z0-9_]+@)*(?:(?:combat room|puzzle) `[^`]+` complete!|(?:upper|middle|lower) level complete!)");
    private static final Pattern TZHAAR_WAVE_START = Pattern.compile("Wave: (\\d+)");
    private static final String TZHAAR_WAVE_COMPLETED = "Wave completed!";
    private static final String TZHAAR_DEFEATED = "You have been defeated!";
    private static final int INFERNO_REGION = 9043;
    private static final int FIGHT_CAVES_REGION = 9551;
    private static final String INFERNO = "The Inferno";
    private static final String FIGHT_CAVES = "Fight Caves";
    private static final int INFERNO_TICKS_BEFORE_FIRST_WAVE_LINE = 10;
    private static final int[] TOB_PARTY_SLOTS = {
        VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4,
    };
    private static final int[] TOA_PARTY_SLOTS = {
        VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
        VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7,
    };
    private static final int[] TFA_PARTY_SLOTS = {
        VarbitID.TFA_CLIENT_P0, VarbitID.TFA_CLIENT_P1, VarbitID.TFA_CLIENT_P2, VarbitID.TFA_CLIENT_P3,
        VarbitID.TFA_CLIENT_P4, VarbitID.TFA_CLIENT_P5, VarbitID.TFA_CLIENT_P6, VarbitID.TFA_CLIENT_P7,
    };

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
    private final Map<String, Integer> pluginTimerStartTickByNpcName = new HashMap<>();
    private final Deque<GameLine> gameLines = new ArrayDeque<>();
    private final Deque<KillCountChange> killCountChanges = new ArrayDeque<>();
    private PendingRun pendingRun;
    private TzhaarWave tzhaarWave;

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        int tick = client.getTickCount();
        long nowMs = System.currentTimeMillis();

        recordSightings(nowMs);
        startPluginTimers(tick);
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
        if (followTzhaarWaves(message))
        {
            return;
        }

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

            String timedNpcName = timedNpcNameIn(message);
            Integer timerStartTick = timedNpcName == null ? null : pluginTimerStartTickByNpcName.remove(timedNpcName);
            if (timerStartTick != null && gameLine.tick - timerStartTick > LONGEST_PLUGIN_TIMED_FIGHT_TICKS)
            {
                timerStartTick = null;
            }

            pendingRun = newPendingRun(
                gameLine,
                Integer.parseInt(killCountMatcher.group("count").replace(",", "")),
                timerStartTick == null ? null : timedNpcName,
                timerStartTick == null ? 0 : (gameLine.tick - timerStartTick) * MS_PER_TICK,
                finishTzhaarWave(gameLine.tick));
        }
        else if ((PERSONAL_BEST.matcher(message).find() || RAID_ROOM_COMPLETE.matcher(message).find())
            && (pendingRun == null || pendingRun.killCountLine.tick != gameLine.tick))
        {
            if (pendingRun != null)
            {
                sendPendingRun();
            }

            pendingRun = newPendingRun(gameLine, NO_KILL_COUNT, null, 0, null);
        }
    }

    private boolean followTzhaarWaves(String message)
    {
        String text = Text.removeTags(message).trim();
        Matcher waveStart = TZHAAR_WAVE_START.matcher(text);
        if (waveStart.matches())
        {
            String content = tzhaarContentHere();
            if (content != null)
            {
                int wave = Integer.parseInt(waveStart.group(1));
                int ticksBeforeLine = INFERNO.equals(content) && wave == 1 ? INFERNO_TICKS_BEFORE_FIRST_WAVE_LINE : 0;
                tzhaarWave = new TzhaarWave(content, wave, client.getTickCount() - ticksBeforeLine);
            }
            return true;
        }

        if (text.equals(TZHAAR_DEFEATED))
        {
            tzhaarWave = null;
            return true;
        }

        if (!text.equals(TZHAAR_WAVE_COMPLETED) || tzhaarWave == null)
        {
            return false;
        }

        GameLine gameLine = new GameLine(client.getTickCount(), System.currentTimeMillis(), message);
        TimedWave timedWave = finishTzhaarWave(gameLine.tick);
        if (pendingRun != null)
        {
            sendPendingRun();
        }
        pendingRun = newPendingRun(gameLine, NO_KILL_COUNT, null, 0, timedWave);
        return true;
    }

    private TimedWave finishTzhaarWave(int tick)
    {
        if (tzhaarWave == null)
        {
            return null;
        }

        TimedWave timedWave = new TimedWave(tzhaarWave.content, tzhaarWave.wave, (tick - tzhaarWave.startTick) * MS_PER_TICK);
        tzhaarWave = null;
        return timedWave;
    }

    private String tzhaarContentHere()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return null;
        }

        int region = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation()).getRegionID();
        if (region == INFERNO_REGION)
        {
            return INFERNO;
        }
        return region == FIGHT_CAVES_REGION ? FIGHT_CAVES : null;
    }

    private PendingRun newPendingRun(GameLine gameLine, int killCount, String pluginTimerNpcName, long pluginTimerMs, TimedWave timedWave)
    {
        return new PendingRun(
            gameLine,
            killCount,
            client.getAccountHash(),
            client.getWorld(),
            client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL),
            localPlayerName(),
            pluginTimerNpcName,
            pluginTimerMs,
            new int[] {partySize(TOB_PARTY_SLOTS), partySize(TOA_PARTY_SLOTS), partySize(TFA_PARTY_SLOTS)},
            timedWave);
    }

    private int partySize(int[] partySlotVarbits)
    {
        int partySize = 0;
        for (int partySlotVarbit : partySlotVarbits)
        {
            if (client.getVarbitValue(partySlotVarbit) > 0)
            {
                partySize++;
            }
        }

        return partySize;
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned)
    {
        String npcName = timedNpcName(npcSpawned.getNpc().getName(), TIMED_FROM_SPAWN);
        if (npcName != null)
        {
            pluginTimerStartTickByNpcName.put(npcName, client.getTickCount());
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

    private void startPluginTimers(int tick)
    {
        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            String npcName = timedNpcName(npc.getName(), TIMED_FROM_FIRST_DAMAGE);
            if (npcName == null || pluginTimerStartTickByNpcName.containsKey(npcName))
            {
                continue;
            }

            boolean belowFullHealth = npc.getHealthScale() > 0
                && npc.getHealthRatio() > 0
                && npc.getHealthRatio() < npc.getHealthScale()
                && !npc.isDead();
            if (belowFullHealth)
            {
                pluginTimerStartTickByNpcName.put(npcName, tick);
            }
        }
    }

    private static String timedNpcName(String npcName, Set<String> timedNpcNames)
    {
        String cleaned = cleanName(npcName);
        if (cleaned == null)
        {
            return null;
        }

        for (String timedNpcName : timedNpcNames)
        {
            if (timedNpcName.equalsIgnoreCase(cleaned))
            {
                return timedNpcName;
            }
        }

        return null;
    }

    private String timedNpcNameIn(String killCountMessage)
    {
        String text = Text.removeTags(killCountMessage).toLowerCase();
        for (String npcName : pluginTimerStartTickByNpcName.keySet())
        {
            if (text.contains(npcName.toLowerCase()))
            {
                return npcName;
            }
        }

        return null;
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
            if (gameLine.tick == killCountLine.tick && !TARGET_TIME.matcher(gameLine.message).find())
            {
                longestCompletionTimeMs = Math.max(longestCompletionTimeMs, longestTimeIn(gameLine.message));
            }
        }

        if (longestCompletionTimeMs == 0)
        {
            longestCompletionTimeMs = run.timedWave != null && run.killCount == NO_KILL_COUNT ? run.timedWave.durationMs : run.pluginTimerMs;
        }

        if (longestCompletionTimeMs == 0)
        {
            return;
        }

        long endMs = killCountLine.timeMs;
        long runStartMs = Math.min(endMs - longestCompletionTimeMs, colosseumRunStartMs(killCountLine));
        long startMs = runStartMs - RUN_WINDOW_MARGIN_MS;

        JsonArray lines = new JsonArray();
        for (GameLine gameLine : gameLines)
        {
            if (gameLine.timeMs >= startMs && gameLine.tick <= killCountLine.tick)
            {
                JsonObject line = new JsonObject();
                line.addProperty("offset_ms", gameLine.timeMs - endMs);
                line.addProperty("tick_offset", gameLine.tick - killCountLine.tick);
                line.addProperty("message", gameLine.message);
                JsonObject roomSetup = roomSetupFor(gameLine);
                if (roomSetup != null)
                {
                    line.add("setup", roomSetup);
                }
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
        varbits.addProperty("tob_party_size", run.raidPartySizes[0]);
        varbits.addProperty("toa_party_size", run.raidPartySizes[1]);
        varbits.addProperty("tfa_party_size", run.raidPartySizes[2]);

        JsonObject completedRun = new JsonObject();
        completedRun.addProperty("account_hash", String.valueOf(run.accountHash));
        completedRun.addProperty("local_player", run.localPlayerName);
        completedRun.addProperty("world", run.world);
        completedRun.addProperty("kill_count", run.killCount);
        completedRun.add("lines", lines);
        completedRun.add("kill_count_changes", killCounts);
        completedRun.add("varbits", varbits);
        completedRun.add("players", players);

        if (run.pluginTimerNpcName != null)
        {
            JsonObject pluginTimer = new JsonObject();
            pluginTimer.addProperty("npc", run.pluginTimerNpcName);
            pluginTimer.addProperty("duration_ms", run.pluginTimerMs);
            completedRun.add("plugin_timer", pluginTimer);
        }

        if (run.timedWave != null)
        {
            JsonObject waveTimer = new JsonObject();
            waveTimer.addProperty("content", run.timedWave.content);
            waveTimer.addProperty("wave", run.timedWave.wave);
            waveTimer.addProperty("duration_ms", run.timedWave.durationMs);
            completedRun.add("wave_timer", waveTimer);
        }
        completedRun.add("setup", setupSnapshots.setupAt(runStartMs));

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

    private long colosseumRunStartMs(GameLine endLine)
    {
        boolean endsOnColosseumWave = gameLines.stream()
            .anyMatch(gameLine -> gameLine.tick == endLine.tick && COLOSSEUM_WAVE_COMPLETE.matcher(gameLine.message).find());
        if (!endsOnColosseumWave)
        {
            return Long.MAX_VALUE;
        }

        Iterator<GameLine> newestFirst = gameLines.descendingIterator();
        while (newestFirst.hasNext())
        {
            GameLine gameLine = newestFirst.next();
            if (gameLine.timeMs > endLine.timeMs)
            {
                continue;
            }
            if (gameLine.timeMs < endLine.timeMs - LONGEST_COLOSSEUM_RUN_MS)
            {
                break;
            }

            Matcher wave = COLOSSEUM_WAVE_COMPLETE.matcher(gameLine.message);
            Matcher duration = ROOM_DURATION.matcher(gameLine.message);
            if (wave.find() && Integer.parseInt(wave.group(1)) == 1 && duration.find())
            {
                return gameLine.timeMs - timeToMs(duration.group("time"));
            }
        }

        return Long.MAX_VALUE;
    }

    private JsonObject roomSetupFor(GameLine gameLine)
    {
        if (!RAID_ROOM_COMPLETE.matcher(gameLine.message).find() && !CHAMBERS_ROOM_COMPLETE.matcher(gameLine.message).find())
        {
            return null;
        }

        Matcher duration = ROOM_DURATION.matcher(gameLine.message);
        return duration.find() ? setupSnapshots.setupAt(gameLine.timeMs - timeToMs(duration.group("time"))) : null;
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
        private final String pluginTimerNpcName;
        private final long pluginTimerMs;
        private final int[] raidPartySizes;
        private final TimedWave timedWave;

        private PendingRun(GameLine killCountLine, int killCount, long accountHash, int world, int toaRaidLevel, String localPlayerName,
            String pluginTimerNpcName, long pluginTimerMs, int[] raidPartySizes, TimedWave timedWave)
        {
            this.killCountLine = killCountLine;
            this.killCount = killCount;
            this.accountHash = accountHash;
            this.world = world;
            this.toaRaidLevel = toaRaidLevel;
            this.localPlayerName = localPlayerName;
            this.pluginTimerNpcName = pluginTimerNpcName;
            this.pluginTimerMs = pluginTimerMs;
            this.raidPartySizes = raidPartySizes;
            this.timedWave = timedWave;
        }
    }

    private static class TzhaarWave
    {
        private final String content;
        private final int wave;
        private final int startTick;

        private TzhaarWave(String content, int wave, int startTick)
        {
            this.content = content;
            this.wave = wave;
            this.startTick = startTick;
        }
    }

    private static class TimedWave
    {
        private final String content;
        private final int wave;
        private final long durationMs;

        private TimedWave(String content, int wave, long durationMs)
        {
            this.content = content;
            this.wave = wave;
            this.durationMs = durationMs;
        }
    }
}
