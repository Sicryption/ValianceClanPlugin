package com.encryptiron.chat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IconID;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class RankIcons
{
    private static final String CHAT_MESSAGE_BUILDING = "chatMessageBuilding";
    private static final File CACHE_FILE = new File(new File(RuneLite.RUNELITE_DIR, "valiance"), "ranks.json");

    private static final Pattern IMG_TAG = Pattern.compile("<img=(\\d+)>");
    private static final int NEAR_BLACK = 0x010101;
    private static final int RANK_ICON_OFFSET_Y = 2;
    private static final Pattern NAME_SPACING = Pattern.compile("[\\s_\\u00A0]+");

    private static final Set<Integer> NON_RANK_ACCOUNT_ICONS = Set.of(
        IconID.PLAYER_MODERATOR.getIndex(),
        IconID.JAGEX_MODERATOR.getIndex(),
        IconID.IRONMAN.getIndex(),
        IconID.ULTIMATE_IRONMAN.getIndex(),
        IconID.HARDCORE_IRONMAN.getIndex(),
        IconID.LEAGUE.getIndex(),
        IconID.GROUP_IRONMAN.getIndex(),
        IconID.HARDCORE_GROUP_IRONMAN.getIndex(),
        IconID.UNRANKED_GROUP_IRONMAN.getIndex()
    );

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ChatIconManager chatIconManager;

    @Inject
    private ValianceSocket socket;

    @Inject
    private Gson gson;

    @Inject
    private ScheduledExecutorService executor;

    private static class RankIcon
    {
        final int iconId;
        final List<IndexedSprite> frames;
        final List<Integer> delays;
        int frame;
        long nextFrameAt;

        RankIcon(int iconId, List<IndexedSprite> frames, List<Integer> delays)
        {
            this.iconId = iconId;
            this.frames = frames;
            this.delays = delays;
        }
    }

    private Map<String, List<String>> members = Collections.emptyMap();
    private final Map<String, RankIcon> icons = new HashMap<>();
    private static final Map<String, Integer> CHAT_ICON_ID_BY_RANK = new HashMap<>();
    private JsonObject ranksJson;
    private JsonObject iconsJson;
    private int ranksRevision;
    private int iconsGeneration;
    private boolean iconSpritesWereMissing;

    private volatile String ranksVersion;
    private volatile String iconsVersion;

    public void startUp()
    {
        socket.setHandler("ranks", body -> applyRanks(body, true));
        socket.setHandler("icons", body -> applyIcons(body, true));
        executor.execute(this::loadSaved);
    }

    public String getRanksVersion()
    {
        return ranksVersion;
    }

    public String getIconsVersion()
    {
        return iconsVersion;
    }

    public void onWelcome(JsonObject welcome)
    {
        JsonElement ranks = welcome.get("ranks");
        if (ranks != null && ranks.isJsonObject())
        {
            applyRanks(ranks.getAsJsonObject(), true);
        }

        JsonElement iconsElement = welcome.get("icons");
        if (iconsElement != null && iconsElement.isJsonObject())
        {
            applyIcons(iconsElement.getAsJsonObject(), true);
        }
    }

    private void loadSaved()
    {
        if (!CACHE_FILE.exists())
        {
            return;
        }

        JsonObject saved;
        try
        {
            saved = gson.fromJson(new String(Files.readAllBytes(CACHE_FILE.toPath()), StandardCharsets.UTF_8), JsonObject.class);
        }
        catch (IOException | JsonParseException ex)
        {
            log.warn("Could not read the saved Valiance ranks", ex);
            return;
        }

        if (saved == null)
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            if (ranksJson == null && saved.has("ranks") && saved.get("ranks").isJsonObject())
            {
                applyRanks(saved.getAsJsonObject("ranks"), false);
            }
            if (iconsGeneration == 0 && saved.has("icons") && saved.get("icons").isJsonObject())
            {
                applyIcons(saved.getAsJsonObject("icons"), false);
            }
        });
    }

    private void save()
    {
        JsonObject saved = new JsonObject();
        if (ranksJson != null)
        {
            saved.add("ranks", ranksJson);
        }
        if (iconsJson != null)
        {
            saved.add("icons", iconsJson);
        }

        String json = gson.toJson(saved);
        executor.execute(() ->
        {
            try
            {
                Files.createDirectories(CACHE_FILE.getParentFile().toPath());
                Files.write(CACHE_FILE.toPath(), json.getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException ex)
            {
                log.warn("Could not save the Valiance ranks", ex);
            }
        });
    }

    private void applyRanks(JsonObject body, boolean fromServer)
    {
        JsonElement membersElement = body.get("members");
        if (membersElement == null || !membersElement.isJsonObject())
        {
            return;
        }

        Map<String, List<String>> next = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : membersElement.getAsJsonObject().entrySet())
        {
            if (!entry.getValue().isJsonArray())
            {
                continue;
            }

            List<String> ranks = new ArrayList<>();
            for (JsonElement rank : entry.getValue().getAsJsonArray())
            {
                if (rank.isJsonPrimitive())
                {
                    ranks.add(rank.getAsString());
                }
            }
            next.put(normaliseName(entry.getKey()), ranks);
        }

        members = next;
        ranksJson = body;
        ranksVersion = ValianceSocket.getString(body, "version");
        ranksRevision++;
        if (fromServer)
        {
            save();
        }
        client.refreshChat();
    }

    private void applyIcons(JsonObject body, boolean fromServer)
    {
        JsonElement iconsElement = body.get("icons");
        if (iconsElement == null || !iconsElement.isJsonObject())
        {
            return;
        }

        JsonObject iconsByRank = iconsElement.getAsJsonObject();
        int generation = ++iconsGeneration;
        executor.execute(() ->
        {
            Map<String, DecodedIcon> decoded = decodeIcons(iconsByRank);
            clientThread.invokeLater(() ->
            {
                if (generation != iconsGeneration)
                {
                    return;
                }

                installIcons(decoded);
                iconsJson = body;
                iconsVersion = ValianceSocket.getString(body, "version");
                ranksRevision++;
                if (fromServer)
                {
                    save();
                }
                client.refreshChat();
            });
        });
    }

    private static class DecodedIcon
    {
        final List<BufferedImage> frames = new ArrayList<>();
        final List<Integer> delays = new ArrayList<>();
    }

    private static Map<String, DecodedIcon> decodeIcons(JsonObject iconsByRank)
    {
        Map<String, DecodedIcon> decoded = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : iconsByRank.entrySet())
        {
            if (!entry.getValue().isJsonObject())
            {
                continue;
            }

            JsonObject icon = entry.getValue().getAsJsonObject();
            JsonElement frames = icon.get("frames");
            JsonElement delays = icon.get("delays");
            if (frames == null || !frames.isJsonArray() || delays == null || !delays.isJsonArray())
            {
                continue;
            }

            DecodedIcon result = new DecodedIcon();
            JsonArray frameArray = frames.getAsJsonArray();
            JsonArray delayArray = delays.getAsJsonArray();
            for (int i = 0; i < frameArray.size() && i < delayArray.size(); i++)
            {
                try
                {
                    byte[] png = Base64.getDecoder().decode(frameArray.get(i).getAsString());
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                    if (image != null)
                    {
                        result.frames.add(replacePureBlackWithNearBlack(image));
                        result.delays.add(delayArray.get(i).getAsInt());
                    }
                }
                catch (IOException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException ex)
                {
                    log.warn("Skipping an unreadable frame of the {} rank icon", entry.getKey(), ex);
                }
            }

            if (!result.frames.isEmpty())
            {
                decoded.put(entry.getKey(), result);
            }
        }
        return decoded;
    }

    private static BufferedImage replacePureBlackWithNearBlack(BufferedImage source)
    {
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++)
        {
            for (int x = 0; x < source.getWidth(); x++)
            {
                int argb = source.getRGB(x, y);
                boolean opaque = (argb >>> 24) != 0;
                if (opaque && (argb & 0xFFFFFF) == 0)
                {
                    argb |= NEAR_BLACK;
                }
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private void installIcons(Map<String, DecodedIcon> decoded)
    {
        icons.clear();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, DecodedIcon> entry : decoded.entrySet())
        {
            DecodedIcon icon = entry.getValue();
            int iconId = CHAT_ICON_ID_BY_RANK.computeIfAbsent(entry.getKey(), rank -> chatIconManager.reserveChatIcon());
            chatIconManager.updateChatIcon(iconId, icon.frames.get(0));

            List<IndexedSprite> frames = new ArrayList<>();
            for (BufferedImage image : icon.frames)
            {
                IndexedSprite frame = ImageUtil.getImageIndexedSprite(image, client);
                frame.setOffsetY(RANK_ICON_OFFSET_Y);
                frames.add(frame);
            }

            RankIcon rankIcon = new RankIcon(iconId, frames, icon.delays);
            rankIcon.nextFrameAt = now + icon.delays.get(0);
            icons.put(entry.getKey(), rankIcon);
        }
    }

    @Subscribe
    public void onClientTick(ClientTick tick)
    {
        if (icons.isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();
        IndexedSprite[] modIcons = client.getModIcons();
        for (RankIcon icon : icons.values())
        {
            if (icon.frames.size() < 2 || now < icon.nextFrameAt)
            {
                continue;
            }

            icon.frame = (icon.frame + 1) % icon.frames.size();
            icon.nextFrameAt = now + icon.delays.get(icon.frame);

            int index = chatIconManager.chatIconIndex(icon.iconId);
            if (modIcons != null && index >= 0 && index < modIcons.length)
            {
                modIcons[index] = icon.frames.get(icon.frame);
            }
        }

        lowerIconsAndRedrawOnceLoaded();
    }

    private void lowerIconsAndRedrawOnceLoaded()
    {
        IndexedSprite[] modIcons = client.getModIcons();
        boolean missing = modIcons == null;

        for (RankIcon icon : icons.values())
        {
            int index = chatIconManager.chatIconIndex(icon.iconId);
            if (modIcons == null || index < 0 || index >= modIcons.length || modIcons[index] == null)
            {
                missing = true;
                continue;
            }

            if (modIcons[index].getOffsetY() != RANK_ICON_OFFSET_Y)
            {
                modIcons[index].setOffsetY(RANK_ICON_OFFSET_Y);
            }
        }

        if (iconSpritesWereMissing && !missing)
        {
            log.debug("Valiance rank icons are back, redrawing the chat");
            client.refreshChat();
        }
        iconSpritesWereMissing = missing;
    }

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
        if (node == null || !isValianceClanChat(node))
        {
            return;
        }

        Object[] objectStack = client.getObjectStack();
        int objectStackSize = client.getObjectStackSize();
        Object name = objectStack[objectStackSize - 3];
        if (!(name instanceof String))
        {
            return;
        }

        objectStack[objectStackSize - 3] = rewriteName((String) name);
    }

    private boolean isValianceClanChat(MessageNode node)
    {
        ChatMessageType type = node.getType();
        if (type == ChatMessageType.CLAN_CHAT)
        {
            return ClanChatService.isValiance(client.getClanChannel());
        }
        if (type == ChatMessageType.CLAN_GUEST_CHAT)
        {
            return ClanChatService.isValiance(client.getGuestClanChannel());
        }
        return false;
    }

    private String rewriteName(String name)
    {
        StringBuilder accountIcons = new StringBuilder();
        Matcher tags = IMG_TAG.matcher(name);
        while (tags.find())
        {
            if (NON_RANK_ACCOUNT_ICONS.contains(Integer.parseInt(tags.group(1))))
            {
                accountIcons.append(tags.group());
            }
        }

        String plainName = IMG_TAG.matcher(name).replaceAll("");

        return rankIconTags(plainName) + accountIcons + plainName;
    }

    public String rankIconTags(String name)
    {
        StringBuilder tags = new StringBuilder();
        List<String> ranks = members.get(normaliseName(Text.removeTags(name)));
        if (ranks != null)
        {
            for (String rank : ranks)
            {
                RankIcon icon = icons.get(rank);
                if (icon != null)
                {
                    tags.append("<img=").append(chatIconManager.chatIconIndex(icon.iconId)).append('>');
                }
            }
        }
        return tags.toString();
    }

    public int getRanksRevision()
    {
        return ranksRevision;
    }

    static String normaliseName(String name)
    {
        return NAME_SPACING.matcher(name).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }
}
