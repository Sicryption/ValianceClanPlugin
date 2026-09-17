package com.encryptiron.screenshot;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.DrawManager;

/**
 * A screenshot of the game taken the moment a drop lands, held until we know
 * whether anyone wants it.
 *
 * Almost every drop is rejected by every event, so uploading on sight would mean
 * shipping a picture of someone's gameplay to our servers for nothing, thousands
 * of times a day. Instead the image is captured immediately - it has to be, the
 * moment is gone a tick later - and parked here under a key. If the accept
 * response comes back naming that drop, {@link #take} hands the bytes over;
 * otherwise they expire where they sit and never leave the machine.
 *
 * <h2>Only the game chat</h2>
 *
 * A screenshot of the client shows the chatbox, and the chatbox is full of other
 * people's conversations. What makes it safe to send is the game's own
 * per-message filter: while the client rebuilds the chatbox it fires a
 * "chatFilterCheck" script callback once per line, and writing 0 into the third
 * int on the stack drops that line from the render. RuneLite's own Chat Filter
 * plugin works exactly this way.
 *
 * So the capture rebuilds the chatbox with everything but game messages blocked,
 * grabs one frame, and rebuilds it again as it was. The player sees a single
 * frame of a filtered chatbox - the same one-frame flicker Dink accepts when it
 * hides the chat area for a screenshot.
 *
 * Two things the filter alone does not cover, both handled below: the tab the
 * player is on is applied *before* the callback runs, so sitting on the Public
 * tab would have left us with an empty chatbox rather than a game-only one; and
 * split private chat renders outside the chatbox entirely, where no chatbox
 * filter can reach it.
 */
@Slf4j
@Singleton
public class GameChatScreenshot
{
    /**
     * What a screenshot may show. Everything else is blocked.
     *
     * An allowlist rather than a blocklist on purpose: a message type we have
     * never heard of - a new chat channel, a future Jagex addition - is refused
     * by default. Getting that backwards would mean the first person to use a
     * feature we have not accounted for uploads their private conversation to us.
     */
    private static final Set<ChatMessageType> GAME_CHAT = EnumSet.of(
        ChatMessageType.GAMEMESSAGE,
        ChatMessageType.SPAM,
        ChatMessageType.ENGINE,
        ChatMessageType.CONSOLE,
        ChatMessageType.BROADCAST,
        ChatMessageType.ITEM_EXAMINE,
        ChatMessageType.NPC_EXAMINE,
        ChatMessageType.OBJECT_EXAMINE
    );

    /**
     * The "All" chat tab.
     *
     * The tab filter runs ahead of chatFilterCheck, so a message the player's
     * current tab already rejected never reaches our callback to be let through.
     * Showing everything and then filtering it ourselves is the only way to get
     * the same screenshot regardless of which tab they happen to be sitting on.
     */
    private static final int CHAT_VIEW_ALL = 0;

    /** How long an unclaimed screenshot is kept before it is dropped. */
    private static final long PENDING_TTL_MS = 30_000;

    /**
     * How many screenshots may be waiting at once.
     *
     * A cap rather than a TTL alone because the TTL only helps if time passes.
     * A full-resolution PNG is around a megabyte, and nothing should be able to
     * grow the client's heap without bound just by killing things quickly.
     */
    private static final int MAX_PENDING = 8;

    /** Ticks to wait for a frame before giving up and putting the chat back. */
    private static final int RESTORE_DEADLINE_TICKS = 2;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private DrawManager drawManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ValianceConfig config;

    /** Insertion-ordered so the oldest entry is the one evicted when full. */
    private final Map<String, Pending> pending = new LinkedHashMap<>();

    /** Set only for the frame being captured; read by the filter callback. */
    private volatile boolean filterToGameChat = false;

    private boolean chatStateSaved = false;
    private int savedChatView = CHAT_VIEW_ALL;
    private int savedScrollPos = 0;
    private boolean hidPrivateChat = false;
    private int captureDeadlineTick = -1;

    /** The last tick we captured on, so we capture at most once per tick. */
    private int lastCaptureTick = -1;

    private static final class Pending
    {
        // Written on the executor thread that encodes the frame, read on the
        // client thread when the accept response comes back.
        private volatile byte[] png;
        private final long capturedAt = System.currentTimeMillis();
    }

    /**
     * Grab the game view now, against a key the response handler can look up.
     *
     * Silently does nothing if this tick already produced a screenshot. Several
     * NPCs dying on one tick is one moment as far as proof is concerned, and
     * each capture forces a pixel readback off the GPU.
     */
    public void capture(String captureKey)
    {
        if (!config.sendDropScreenshots() || captureKey == null)
        {
            return;
        }

        int tick = client.getTickCount();
        if (tick == lastCaptureTick)
        {
            return;
        }

        // A capture is already waiting on a frame. Starting another would have
        // it save the chat state we have already swapped out, and restore that
        // instead of the player's real settings.
        if (filterToGameChat)
        {
            return;
        }

        lastCaptureTick = tick;

        final Pending slot = new Pending();
        expirePending();
        if (pending.size() >= MAX_PENDING)
        {
            Iterator<String> oldest = pending.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        pending.put(captureKey, slot);

        hideOtherChat();

        drawManager.requestNextFrameListener(image ->
        {
            // Off the client thread immediately: PNG encoding a full-resolution
            // frame is tens of milliseconds, which is a visible stutter if it
            // happens between game ticks.
            executor.execute(() -> slot.png = encode(image));

            clientThread.invokeLater(this::restoreChat);
        });
    }

    /**
     * Hands over the bytes for this key and forgets them.
     *
     * Returns null when there is nothing - the capture may have been skipped for
     * the tick, the frame may never have arrived, or the encode may still be
     * running. All three mean the same thing to the caller: send the drop
     * without a screenshot.
     */
    public byte[] take(String captureKey)
    {
        expirePending();
        Pending slot = pending.remove(captureKey);
        return slot == null ? null : slot.png;
    }

    public void reset()
    {
        pending.clear();
        lastCaptureTick = -1;
        if (filterToGameChat)
        {
            restoreChat();
        }
    }

    /**
     * The game's own chatbox filter, borrowed for one frame.
     *
     * Only ever blocks, never lets something through that the client would have
     * hidden - the player's own filters still apply on top of ours.
     */
    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!filterToGameChat || !"chatFilterCheck".equals(event.getEventName()))
        {
            return;
        }

        int[] intStack = client.getIntStack();
        int intStackSize = client.getIntStackSize();

        ChatMessageType type = ChatMessageType.of(intStack[intStackSize - 2]);

        if (!GAME_CHAT.contains(type))
        {
            intStack[intStackSize - 3] = 0;
        }
    }

    /**
     * Puts the chat back if the frame never came.
     *
     * requestNextFrameListener only fires when a frame is actually drawn, and
     * nothing guarantees one will be - a minimised client, a stalled renderer, a
     * disconnect mid-capture. Leaving somebody's chat filtered and their split
     * PMs hidden because we were waiting on a frame that never arrived is far
     * worse than losing the screenshot.
     */
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (filterToGameChat && client.getTickCount() >= captureDeadlineTick)
        {
            log.debug("No frame arrived for the drop screenshot; restoring chat.");
            restoreChat();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
        {
            // Screenshots are proof of a drop by a particular account. Anything
            // still waiting when that account goes away cannot be claimed, and
            // the varcs we saved belong to a session that no longer exists.
            pending.clear();
            filterToGameChat = false;
            chatStateSaved = false;
            hidPrivateChat = false;
        }
    }

    private void hideOtherChat()
    {
        savedChatView = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        savedScrollPos = client.getVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS);
        chatStateSaved = true;

        filterToGameChat = true;
        captureDeadlineTick = client.getTickCount() + RESTORE_DEADLINE_TICKS;

        // Split private chat draws above the chatbox rather than inside it, so
        // the chatbox filter never sees those lines. Hiding the widget for the
        // frame is the only thing that keeps them out.
        Widget privateChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (privateChat != null && !privateChat.isHidden())
        {
            privateChat.setHidden(true);
            hidPrivateChat = true;
        }

        client.setVarcIntValue(VarClientID.CHAT_VIEW, CHAT_VIEW_ALL);
        client.runScript(ScriptID.BUILD_CHATBOX);
    }

    private void restoreChat()
    {
        filterToGameChat = false;

        if (hidPrivateChat)
        {
            Widget privateChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
            if (privateChat != null)
            {
                privateChat.setHidden(false);
            }
            hidPrivateChat = false;
        }

        if (chatStateSaved)
        {
            client.setVarcIntValue(VarClientID.CHAT_VIEW, savedChatView);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, savedScrollPos);
            chatStateSaved = false;
        }

        client.runScript(ScriptID.BUILD_CHATBOX);
    }

    /** Anything older than the TTL was for a drop nothing wanted. */
    private void expirePending()
    {
        long cutoff = System.currentTimeMillis() - PENDING_TTL_MS;
        pending.values().removeIf(slot -> slot.capturedAt < cutoff);
    }

    private byte[] encode(Image image)
    {
        try
        {
            BufferedImage source = toBufferedImage(image);
            BufferedImage scaled = rescale(source, config.screenshotScale());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", out);
            return out.toByteArray();
        }
        catch (Exception ex)
        {
            log.warn("Failed to encode drop screenshot", ex);
            return null;
        }
    }

    /**
     * TYPE_INT_RGB rather than ARGB: the frame is opaque, and an alpha channel
     * only makes the PNG bigger.
     */
    private static BufferedImage toBufferedImage(Image image)
    {
        BufferedImage buffered = new BufferedImage(
            image.getWidth(null),
            image.getHeight(null),
            BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = buffered.createGraphics();
        try
        {
            graphics.drawImage(image, 0, 0, null);
        }
        finally
        {
            graphics.dispose();
        }

        return buffered;
    }

    private static BufferedImage rescale(BufferedImage source, int scalePercent)
    {
        if (scalePercent >= 100 || scalePercent <= 0)
        {
            return source;
        }

        int width = Math.max(1, source.getWidth() * scalePercent / 100);
        int height = Math.max(1, source.getHeight() * scalePercent / 100);

        BufferedImage scaled = new BufferedImage(width, height, source.getType());
        Graphics2D graphics = scaled.createGraphics();
        try
        {
            graphics.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            graphics.dispose();
        }

        return scaled;
    }
}
